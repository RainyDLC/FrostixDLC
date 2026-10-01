package mod.runtime;












public final class NativeBridge {
    private NativeBridge() {}
    private static final ThreadLocal<java.util.Set<String>> lateTargets = new ThreadLocal<>();


    public static void prepareLateConfigs(String[] names) throws Throwable {
        try { prepareLateConfigsChecked(names); }
        catch (Throwable failure) {
            recordFailure("prepareConfigs " + java.util.Arrays.toString(names), failure);
            throw failure;
        }
    }

    private static void prepareLateConfigsChecked(String[] names) throws Throwable {
        java.util.Objects.requireNonNull(names, "config names");
        java.util.Set<String> unique = new java.util.HashSet<>();
        for (String name : names)
            if (name == null || name.isBlank() || !unique.add(name))
                throw new IllegalArgumentException("Empty or duplicate Mixin config: " + name);
        ClassLoader loader = NativeBridge.class.getClassLoader();
        Class<?> environmentClass = Class.forName("org.spongepowered.asm.mixin.MixinEnvironment", false, loader);
        Object environment = environmentClass.getMethod("getDefaultEnvironment").invoke(null);
        Object transformer = environmentClass.getMethod("getActiveTransformer").invoke(environment);
        if (transformer == null) throw new IllegalStateException("No active Mixin transformer");
        Class<?> configClass = Class.forName("org.spongepowered.asm.mixin.transformer.MixinConfig", false, loader);
        Class<?> serviceClass = Class.forName("org.spongepowered.asm.service.IMixinService", false, loader);
        Class<?> sourceClass = Class.forName("org.spongepowered.asm.mixin.extensibility.IMixinConfigSource", false, loader);
        Object service = Class.forName("org.spongepowered.asm.service.MixinService", false, loader)
                .getMethod("getService").invoke(null);
        java.lang.reflect.Method onLoad = configClass.getDeclaredMethod("onLoad", serviceClass,
                String.class, environmentClass, sourceClass);
        java.lang.reflect.Method onSelect = configClass.getDeclaredMethod("onSelect");
        onLoad.setAccessible(true);
        onSelect.setAccessible(true);
        synchronized (transformer) {
            Object processor = fieldValue(transformer, "processor");
            synchronized (processor) {
                @SuppressWarnings("unchecked")
                java.util.List<Object> pending = (java.util.List<Object>) fieldValue(processor, "pendingConfigs");
                java.util.List<Object> snapshot = new java.util.ArrayList<>(pending);
                java.lang.reflect.Method prepare = null;
                for (java.lang.reflect.Method method : processor.getClass().getDeclaredMethods()) {
                    if (method.getName().equals("prepareConfigs")
                            && (method.getParameterCount() == 1 || method.getParameterCount() == 2)
                            && method.getParameterTypes()[0] == environmentClass) {
                        if (prepare != null) throw new IllegalStateException("Ambiguous prepareConfigs");
                        prepare = method;
                    }
                }
                if (prepare == null) throw new NoSuchMethodException("MixinProcessor.prepareConfigs");
                prepare.setAccessible(true);
                java.util.Set<String> previousTargets = lateTargets.get();
                lateTargets.set(embeddedTargets());
                try {
                    for (String name : names) {
                        byte[] data = getMixinResource0(name);
                        if (data == null) throw new IllegalStateException("Config not found in DLL: " + name);
                        Object config;
                        try (java.io.Reader reader = new java.io.InputStreamReader(
                                new java.io.ByteArrayInputStream(data), java.nio.charset.StandardCharsets.UTF_8)) {
                            config = new org.spongepowered.include.com.google.gson.Gson().fromJson(reader, configClass);
                        } finally { java.util.Arrays.fill(data, (byte) 0); }
                        if (config == null) throw new IllegalStateException("Null Mixin config: " + name);
                        if (Boolean.FALSE.equals(invokeUnwrapped(onLoad, config, service, name, environment, null)))
                            throw new IllegalStateException("Mixin onLoad rejected config: " + name);
                        invokeUnwrapped(onSelect, config);
                        pending.clear();
                        pending.add(config);
                        try {
                            if (prepare.getParameterCount() == 2)
                                invokeUnwrapped(prepare, processor, environment, fieldValue(processor, "extensions"));
                            else invokeUnwrapped(prepare, processor, environment);
                        } catch (Throwable failure) {

                            if (!failure.getClass().getName().contains("MixinTargetAlreadyLoadedException")) throw failure;
                            System.err.println("[MixinConfig] loaded target: " + failure);
                        }
                        @SuppressWarnings("unchecked")
                        java.util.List<Object> active = (java.util.List<Object>) fieldValue(processor, "configs");
                        if (!active.contains(config))
                            throw new IllegalStateException("Config preparation did not activate " + name);
                        System.err.println("[LateConfig] prepared before capture: " + name);
                    }
                } finally {

                    pending.clear();
                    pending.addAll(snapshot);
                    if (previousTargets == null) lateTargets.remove();
                    else lateTargets.set(previousTargets);
                }
            }
        }
    }

    private static Object fieldValue(Object receiver, String name) throws ReflectiveOperationException {
        for (Class<?> type = receiver.getClass(); type != null; type = type.getSuperclass()) {
            try {
                java.lang.reflect.Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(receiver);
            } catch (NoSuchFieldException missing) {  }
        }
        throw new NoSuchFieldException(receiver.getClass().getName() + "." + name);
    }

    static boolean isPreparingLateTarget(String name) {
        java.util.Set<String> names = lateTargets.get();
        return names != null && names.contains(name.replace('/', '.'));
    }

    private static java.util.Set<String> embeddedTargets() {
        java.util.Set<String> names = new java.util.HashSet<>();
        for (String resource : listMixinResources0()) {
            if (!resource.endsWith(".class")) continue;
            byte[] bytes = getMixinResource0(resource);
            if (bytes == null) continue;
            try { java.util.Collections.addAll(names, readMixinTargetNames(bytes)); }
            catch (IllegalArgumentException notMixin) {
                if (!notMixin.getMessage().startsWith("No @Mixin targets in ")) throw notMixin;
            }
        }
        return names;
    }





    public static byte[] transformOwned(Object transformer, String name, byte[] bytes,
            String[] configNames) throws Throwable {
        try {
            return transformOwnedChecked(transformer, name, bytes, configNames);
        } catch (Throwable failure) {
            recordFailure(name, failure);
            throw failure;
        }
    }

    private static void recordFailure(String name, Throwable failure) {
        try {
            java.nio.file.Path log = java.nio.file.Path.of(System.getProperty("java.io.tmpdir"),
                    "mod_mixin_failure_" + ProcessHandle.current().pid() + ".log");
            try (java.io.PrintWriter writer = new java.io.PrintWriter(
                    java.nio.file.Files.newBufferedWriter(log, java.nio.charset.StandardCharsets.UTF_8,
                            java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND))) {
                writer.println(java.time.Instant.now() + " target=" + name);
                failure.printStackTrace(writer);
            }
        } catch (Exception loggingFailure) { failure.addSuppressed(loggingFailure); }
    }

    private static byte[] transformOwnedChecked(Object transformer, String name, byte[] bytes,
            String[] configNames) throws Throwable {
        java.util.Set<String> owned = new java.util.HashSet<>(java.util.Arrays.asList(configNames));
        if (owned.isEmpty()) throw new IllegalArgumentException("No owned Mixin configs");
        synchronized (transformer) {
            java.lang.reflect.Field processorField = transformer.getClass().getDeclaredField("processor");
            processorField.setAccessible(true);
            Object processor = processorField.get(transformer);
            synchronized (processor) {
                Class<?> processorClass = processor.getClass();
                java.lang.reflect.Field configsField = processorClass.getDeclaredField("configs");
                configsField.setAccessible(true);
                @SuppressWarnings("unchecked")
                java.util.List<Object> configs = (java.util.List<Object>) configsField.get(processor);


                Object delegate = null;
                java.lang.reflect.Method postMixin = null;
                try {
                    delegate = fieldValue(NativeBridge.class.getClassLoader(), "delegate");
                    postMixin = delegate.getClass().getDeclaredMethod(
                            "getPostMixinClassByteArray", String.class, boolean.class);
                    postMixin.setAccessible(true);
                } catch (NoSuchFieldException | NoSuchMethodException unavailable) {

                }
                if (postMixin != null) {
                    System.err.println("[LateTransform] Knot post-Mixin target=" + name);
                    byte[] result = (byte[]) invokeUnwrapped(postMixin, delegate, name, false);
                    if (result == null) throw new IllegalStateException("Null Knot transformed bytes: " + name);
                    return result;
                }
                java.util.List<Object> snapshot = new java.util.ArrayList<>(configs);
                try {
                    java.util.Iterator<Object> iterator = configs.iterator();
                    while (iterator.hasNext()) {
                        Object config = iterator.next();
                        java.lang.reflect.Method getName = config.getClass().getDeclaredMethod("getName");
                        getName.setAccessible(true);
                        String configName = (String) invokeUnwrapped(getName, config);
                        if (!owned.contains(configName)) iterator.remove();
                    }
                    if (configs.size() != owned.size())
                        throw new IllegalStateException("Owned Mixin configs not prepared: expected="
                                + owned + " selected=" + configs.size());
                    java.lang.reflect.Method transform = transformer.getClass().getDeclaredMethod(
                            "transformClassBytes", String.class, String.class, byte[].class);
                    transform.setAccessible(true);
                    byte[] result = (byte[]) invokeUnwrapped(transform, transformer, name, name, bytes);
                    if (result == null) throw new IllegalStateException("Null transformed bytes: " + name);
                    return result;
                } finally {
                    configs.clear();
                    configs.addAll(snapshot);
                }
            }
        }
    }

    private static Object invokeUnwrapped(java.lang.reflect.Method method, Object receiver,
            Object... arguments) throws Throwable {
        try { return method.invoke(receiver, arguments); }
        catch (java.lang.reflect.InvocationTargetException failure) { throw failure.getCause(); }
    }


    public static String[] getMixinTargetNames0(Class<?> mixinClass) {
        java.util.Objects.requireNonNull(mixinClass, "mixinClass");
        String resource = mixinClass.getName().replace('.', '/') + ".class";
        byte[] embedded = getMixinResource0(resource);
        if (embedded != null) return readMixinTargetNames(embedded);
        ClassLoader loader = mixinClass.getClassLoader();
        try (java.io.InputStream stream = loader != null
                ? loader.getResourceAsStream(resource)
                : mixinClass.getResourceAsStream("/" + resource)) {
            if (stream == null) throw new IllegalStateException("Missing mixin bytecode: " + resource);
            return readMixinTargetNames(stream.readAllBytes());
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("Cannot read mixin bytecode: " + resource, failure);
        }
    }

    static String[] readMixinTargetNames(byte[] bytes) {
        org.objectweb.asm.tree.ClassNode node = new org.objectweb.asm.tree.ClassNode();
        new org.objectweb.asm.ClassReader(java.util.Objects.requireNonNull(bytes, "bytes"))
                .accept(node, org.objectweb.asm.ClassReader.SKIP_CODE
                        | org.objectweb.asm.ClassReader.SKIP_DEBUG | org.objectweb.asm.ClassReader.SKIP_FRAMES);
        java.util.LinkedHashSet<String> result = new java.util.LinkedHashSet<>();
        java.util.List<org.objectweb.asm.tree.AnnotationNode> annotations = new java.util.ArrayList<>();
        if (node.visibleAnnotations != null) annotations.addAll(node.visibleAnnotations);
        if (node.invisibleAnnotations != null) annotations.addAll(node.invisibleAnnotations);
        for (org.objectweb.asm.tree.AnnotationNode annotation : annotations) {
            if (!"Lorg/spongepowered/asm/mixin/Mixin;".equals(annotation.desc) || annotation.values == null) continue;
            for (int i = 0; i + 1 < annotation.values.size(); i += 2) {
                String key = (String) annotation.values.get(i);
                Object value = annotation.values.get(i + 1);
                if ("targets".equals(key) && value instanceof java.util.List<?> names) {
                    for (Object name : names) result.add(((String) name).replace('/', '.'));
                } else if ("value".equals(key) && value instanceof java.util.List<?> types) {
                    for (Object type : types) {
                        if (!(type instanceof org.objectweb.asm.Type asmType)
                                || asmType.getSort() != org.objectweb.asm.Type.OBJECT) {
                            throw new IllegalArgumentException("Invalid @Mixin target in " + node.name);
                        }
                        result.add(asmType.getClassName());
                    }
                }
            }
        }
        if (result.isEmpty()) throw new IllegalArgumentException("No @Mixin targets in " + node.name);
        return result.toArray(String[]::new);
    }


    public static int clearMixinCacheSafely() throws ReflectiveOperationException {
        ClassLoader loader = NativeBridge.class.getClassLoader();
        Class<?> environmentClass = Class.forName("org.spongepowered.asm.mixin.MixinEnvironment", false, loader);
        Object environment = environmentClass.getMethod("getCurrentEnvironment").invoke(null);
        Object transformer = environmentClass.getMethod("getActiveTransformer").invoke(environment);
        Class<?> classInfo = Class.forName("org.spongepowered.asm.mixin.transformer.ClassInfo", false, loader);
        return clearClassInfoCache(classInfo, transformer);
    }

    private static int clearClassInfoCache(Class<?> classInfo, Object transformer)
            throws ReflectiveOperationException {
        if (transformer == null) {
            throw new IllegalStateException("Cannot invalidate ClassInfo without the active transformer monitor");
        }

        synchronized (transformer) {
            java.lang.reflect.Field cacheField = classInfo.getDeclaredField("cache");
            java.lang.reflect.Field objectField = classInfo.getDeclaredField("OBJECT");
            cacheField.setAccessible(true);
            objectField.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> cache = (java.util.Map<String, Object>) cacheField.get(null);
            Object root = objectField.get(null);
            if (root == null || cache == null || cache.get("java/lang/Object") != root) {

                throw new IllegalStateException("ClassInfo.OBJECT invariant already broken; restart Minecraft");
            }
            int removed = 0;
            java.util.Iterator<java.util.Map.Entry<String, Object>> entries = cache.entrySet().iterator();
            while (entries.hasNext()) {



                if (entries.next().getValue() == null) {
                    entries.remove();
                    removed++;
                }
            }
            if (cache.get("java/lang/Object") != root) {
                throw new IllegalStateException("ClassInfo cache invariant failed after invalidation");
            }
            return removed;
        }
    }











    public static native byte[] getMixinResource0(String name);







    public static native String[] listMixinResources0();










    public static native byte[] getLoadedClassBytes0(Class<?> cls);








    public static native void redefineClass0(Class<?> cls, byte[] bytes);







    public static native void retransformClasses0(Class<?>[] classes);









    public static native Class<?> defineClassNative(String name, byte[] bytes, ClassLoader loader);





    public static native void clearMixinCache0();








    public static native Class<?> findLoadedClass0(String className);







    public static void clearMixinCache() {
        try {
            clearMixinCache0();
        } catch (UnsatisfiedLinkError e) {

            try {
                Class<?> envCls = Class.forName(
                    "org.spongepowered.asm.mixin.MixinEnvironment",
                    false, Thread.currentThread().getContextClassLoader());
                Object env = envCls.getMethod("getCurrentEnvironment").invoke(null);
                if (env != null) {
                    java.lang.reflect.Method audit = env.getClass().getMethod("audit");
                    audit.invoke(env);
                }
            } catch (Throwable ignored) {}
        }
    }





    public static boolean isAvailable() {
        try {
            listMixinResources0();
            return true;
        } catch (UnsatisfiedLinkError e) {
            return false;
        }
    }
}

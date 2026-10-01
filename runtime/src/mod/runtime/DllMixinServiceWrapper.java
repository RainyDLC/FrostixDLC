package mod.runtime;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.launch.platform.container.IContainerHandle;
import org.spongepowered.asm.logging.ILogger;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.service.*;
import org.spongepowered.asm.util.ReEntranceLock;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.*;









public final class DllMixinServiceWrapper
        implements IMixinService, org.spongepowered.asm.service.IClassBytecodeProvider, IClassTracker, IClassProvider {



    private final IMixinService delegate;

    private static final Map<String, String> remapTable;

    static {
        remapTable = new HashMap<>();
    }

    public static void initRemapTable(Map<String, String> entries) {
        remapTable.putAll(entries);
    }




    public DllMixinServiceWrapper(IMixinService delegate) {
        this.delegate = delegate;
    }




    @Override
    public ClassNode getClassNode(String name) throws ClassNotFoundException, IOException {
        return getClassNode(name, true, 0);
    }




    @Override
    public ClassNode getClassNode(String name, boolean runTransformers)
            throws ClassNotFoundException, IOException {
        return getClassNode(name, runTransformers, 0);
    }






























































































    public ClassNode getClassNode(String name, boolean runTransformers, int readerFlags)
            throws ClassNotFoundException, IOException {


        String internalName = name.replace('.', '/');



        String resourcePath = internalName + ".class";


        byte[] bytes = null;
        try {
            bytes = NativeBridge.getMixinResource0(resourcePath);
        } catch (UnsatisfiedLinkError ignored) {}

        ClassNode node;

        if (bytes != null) {

            ClassReader reader = new ClassReader(bytes);
            node = new ClassNode();
            reader.accept(node, readerFlags);

        } else {

            node = delegateClassNode(name, runTransformers, readerFlags);

            if (node == null) return null;
        }


        int count = 0;



        if (bytes != null && node.methods != null) {

            for (MethodNode method : node.methods) {

                count += disableInjectorRemapping(method.visibleAnnotations);

                count += disableInjectorRemapping(method.invisibleAnnotations);
            }
        }





        if (bytes == null) return node;



        if (node.visibleAnnotations == null) return node;

        for (AnnotationNode ann : node.visibleAnnotations) {

            if (ann.desc == null) continue;



            if (!ann.desc.contains("Mixin")) continue;


            boolean found = false;


            if (ann.values != null) {


                for (int i = 0; i < ann.values.size() - 1; i += 2) {

                    if (!"require".equals(ann.values.get(i))) continue;


                    Object val = ann.values.get(i + 1);
                    if (val instanceof List && ((List<?>) val).isEmpty()) {

                        found = true;
                    }

                    break;
                }
            }


            if (found) continue;


            for (Map.Entry<String, String> entry : remapTable.entrySet()) {


                if (!node.name.endsWith(entry.getKey())) continue;


                boolean patched = false;


                if (ann.values != null) {

                    for (int j = 0; j < ann.values.size() - 1; j += 2) {

                        if (!"value".equals(ann.values.get(j))) continue;



                        ann.values.set(j + 1,
                            new ArrayList<>(Collections.singletonList(
                                entry.getValue().replace('/', '.'))));

                        patched = true;

                        break;
                    }
                }


                if (patched) continue;



                if (ann.values == null) {

                    ann.values = new ArrayList<>();
                }


                ann.values.add("value");



                ann.values.add(
                    new ArrayList<>(Collections.singletonList(
                        entry.getValue().replace('/', '.'))));

            }

        }


        return node;
    }






    static int disableInjectorRemapping(List<AnnotationNode> annotations) {
        if (annotations == null) return 0;
        int count = 0;
        for (AnnotationNode annotation : annotations) {
            if (annotation.desc == null || !annotation.desc.endsWith("Inject;")) continue;
            if (annotation.values == null) annotation.values = new ArrayList<>();
            boolean found = false;
            for (int i = 0; i + 1 < annotation.values.size(); i += 2) {
                if ("remap".equals(annotation.values.get(i))) {
                    annotation.values.set(i + 1, Boolean.FALSE);
                    found = true;
                    break;
                }
            }
            if (!found) {
                annotation.values.add("remap");
                annotation.values.add(Boolean.FALSE);
            }
            count++;
        }
        return count;
    }

    private ClassNode delegateClassNode(String name, boolean runTransformers, int readerFlags)
            throws ClassNotFoundException, IOException {
        org.spongepowered.asm.service.IClassBytecodeProvider provider = delegate.getBytecodeProvider();
        try {

            java.lang.reflect.Method method = org.spongepowered.asm.service.IClassBytecodeProvider.class.getMethod(
                    "getClassNode", String.class, boolean.class, int.class);
            return (ClassNode) method.invoke(provider, name, runTransformers, readerFlags);
        } catch (NoSuchMethodException oldMixin) {
            if (readerFlags != 0) throw new IOException("Mixin provider cannot accept readerFlags=" + readerFlags, oldMixin);
            return provider.getClassNode(name, runTransformers);
        } catch (java.lang.reflect.InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof ClassNotFoundException e) throw e;
            if (cause instanceof IOException e) throw e;
            if (cause instanceof RuntimeException e) throw e;
            if (cause instanceof Error e) throw e;
            throw new IOException("Mixin bytecode provider failed for " + name, cause);
        } catch (IllegalAccessException failure) {
            throw new IOException("Cannot invoke Mixin bytecode provider for " + name, failure);
        }
    }





    @Override
    public Class<?> findClass(String name) throws ClassNotFoundException {
        try {
            return Class.forName(name, false, NativeBridge.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            IClassProvider cp = delegate.getClassProvider();
            if (cp != null) return cp.findClass(name);
            throw e;
        }
    }


    @Override
    public Class<?> findClass(String name, boolean initialize) throws ClassNotFoundException {
        return Class.forName(name, initialize, NativeBridge.class.getClassLoader());
    }


    @Override
    public Class<?> findAgentClass(String name, boolean initialize) throws ClassNotFoundException {
        return delegate.getClassProvider().findAgentClass(name, initialize);
    }


    @Override
    public URL[] getClassPath() {
        return delegate.getClassProvider().getClassPath();
    }




    @Override
    public boolean isClassLoaded(String name) {
        if (name == null) return false;
        if (NativeBridge.isPreparingLateTarget(name)) return false;


        if (name.contains("mod/runtime")         ||
            name.contains("mod.runtime")          ||
            name.contains("NativeBridge")         ||
            name.contains("DllMixinService")      ||
            name.contains("DllClassLoader")       ||
            name.contains("IClassBytecodeProvider")) {
            return false;
        }
        IClassTracker t = delegate.getClassTracker();
        return t != null && t.isClassLoaded(name);
    }





    @Override
    public String getClassRestrictions(String name) {
        try {
            IClassTracker t = delegate.getClassTracker();
            if (t == null) return null;
            String r = t.getClassRestrictions(name);
            return r != null ? r : "";
        } catch (Throwable e) {
            return "";
        }
    }


    @Override
    public void registerInvalidClass(String name) {
        IClassTracker t = delegate.getClassTracker();
        if (t != null) t.registerInvalidClass(name);
    }



    @Override public IClassProvider    getClassProvider()    { return this; }
    @Override public IClassTracker     getClassTracker()     { return this; }
    @Override public org.spongepowered.asm.service.IClassBytecodeProvider getBytecodeProvider() { return this; }



    @Override
    public InputStream getResourceAsStream(String name) {
        byte[] bytes = null;
        try { bytes = NativeBridge.getMixinResource0(name); }
        catch (UnsatisfiedLinkError ignored) {}
        if (bytes != null) return new ByteArrayInputStream(bytes);
        return delegate.getResourceAsStream(name);
    }



    @Override public String getName()                                    { return delegate.getName(); }
    @Override public boolean isValid()                                   { return delegate.isValid(); }
    @Override public void prepare()                                      { delegate.prepare(); }
    @Override public MixinEnvironment.Phase getInitialPhase()           { return delegate.getInitialPhase(); }
    @Override public void offer(IMixinInternal internal)                 { delegate.offer(internal); }
    @Override public void init()                                         { delegate.init(); }
    @Override public void beginPhase()                                   { delegate.beginPhase(); }
    @Override public void checkEnv(Object bootSource)                    { delegate.checkEnv(bootSource); }
    @Override public ReEntranceLock getReEntranceLock()                  { return delegate.getReEntranceLock(); }
    @Override public ITransformerProvider getTransformerProvider()       { return delegate.getTransformerProvider(); }
    @Override public IMixinAuditTrail getAuditTrail()                    { return delegate.getAuditTrail(); }
    @Override public IFeatureValidator getFeatureValidator()            { return delegate.getFeatureValidator(); }
    @Override public IAdviceProvider getAdviceProvider()                  { return delegate.getAdviceProvider(); }
    @Override public Collection<String> getPlatformAgents()              { return delegate.getPlatformAgents(); }
    @Override public IContainerHandle getPrimaryContainer()              { return delegate.getPrimaryContainer(); }
    @Override public Collection<IContainerHandle> getMixinContainers()  { return delegate.getMixinContainers(); }
    @Override public String getSideName()                                { return delegate.getSideName(); }
    @Override public MixinEnvironment.CompatibilityLevel getMinCompatibilityLevel() { return delegate.getMinCompatibilityLevel(); }
    @Override public MixinEnvironment.CompatibilityLevel getMaxCompatibilityLevel() { return delegate.getMaxCompatibilityLevel(); }
    @Override public ILogger getLogger(String name)                      { return delegate.getLogger(name); }
}

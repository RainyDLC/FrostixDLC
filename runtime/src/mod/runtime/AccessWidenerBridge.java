package mod.runtime;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;




















public final class AccessWidenerBridge {

    private AccessWidenerBridge() {}








    public static boolean apply(String resourcePath, String declaredNs) {

        byte[] bytes;
        try {
            bytes = NativeBridge.getMixinResource0(resourcePath);
        } catch (UnsatisfiedLinkError e) {
            bytes = null;
        }
        if (bytes == null || bytes.length == 0) {
            System.err.println("[AccessWidenerBridge] resource not found: " + resourcePath);
            return false;
        }

        ClassLoader cl = Thread.currentThread().getContextClassLoader();



        String targetNs = "named";
        if (declaredNs != null && !declaredNs.isEmpty() && !declaredNs.equals(targetNs)) {
            bytes = remapAccessWidener(bytes, declaredNs, targetNs, cl);
            if (bytes == null) {
                System.err.println("[AccessWidenerBridge] remap failed, applying as-is");
                try {
                    bytes = NativeBridge.getMixinResource0(resourcePath);
                } catch (Throwable e) { bytes = new byte[0]; }
            }
        }


        return applyViaFabricLoader(bytes, cl);
    }














    private static byte[] remapAccessWidener(byte[] original, String srcNs,
                                              String dstNs, ClassLoader cl) {
        try {

            Object resolver = getMappingResolver(cl);
            if (resolver == null) return null;

            Class<?> resolverCls = resolver.getClass();

            Method mapClass = null;
            Method mapMethod = null;
            Method mapField  = null;
            for (Method m : resolverCls.getMethods()) {
                switch (m.getName()) {
                    case "mapClassName"    -> mapClass  = m;
                    case "mapMethodName"   -> mapMethod = m;
                    case "mapFieldName"    -> mapField  = m;
                }
            }

            String text = new String(original, StandardCharsets.UTF_8);
            String[] lines = text.split("\\n", -1);
            StringBuilder out = new StringBuilder();

            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];

                if (i == 0) {
                    out.append(line.replace(srcNs, dstNs)).append('\n');
                    continue;
                }

                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    out.append(line).append('\n');
                    continue;
                }

                String[] parts = trimmed.split("\\s+");
                if (parts.length < 3) { out.append(line).append('\n'); continue; }

                String kind   = parts[0];
                String type   = parts[1];
                String clName = parts[2].replace('.', '/');


                String mappedClass = clName;
                if (mapClass != null) {
                    try {
                        Object mc = mapClass.invoke(resolver, srcNs, dstNs,
                            clName.replace('/', '.'));
                        if (mc instanceof String) mappedClass = ((String)mc).replace('.', '/');
                    } catch (Throwable e) {  }
                }

                if (type.equals("class")) {
                    out.append(kind).append('\t').append(type).append('\t')
                       .append(mappedClass).append('\n');
                } else if ((type.equals("method") || type.equals("field")) && parts.length >= 5) {
                    String memberName = parts[3];
                    String desc       = parts[4];
                    String mappedMember = memberName;

                    if (type.equals("method") && mapMethod != null) {
                        try {
                            Object mm = mapMethod.invoke(resolver, srcNs, dstNs,
                                clName.replace('/', '.'), memberName, desc);
                            if (mm instanceof String) mappedMember = (String) mm;
                        } catch (Throwable e) {  }
                    } else if (type.equals("field") && mapField != null) {
                        try {
                            Object mf = mapField.invoke(resolver, srcNs, dstNs,
                                clName.replace('/', '.'), memberName, desc);
                            if (mf instanceof String) mappedMember = (String) mf;
                        } catch (Throwable e) {  }
                    }
                    out.append(kind).append('\t').append(type).append('\t')
                       .append(mappedClass).append('\t')
                       .append(mappedMember).append('\t')
                       .append(desc).append('\n');
                } else {
                    out.append(line).append('\n');
                }
            }
            return out.toString().getBytes(StandardCharsets.UTF_8);
        } catch (Throwable e) {
            System.err.println("[AccessWidenerBridge] remapAccessWidener failed: " + e.getMessage());
            return null;
        }
    }





    private static Object getMappingResolver(ClassLoader cl) {
        try {

            Class<?> fabricLoaderApi = Class.forName(
                "net.fabricmc.loader.api.FabricLoader", false, cl);
            Method getInstance = fabricLoaderApi.getMethod("getInstance");
            Object loaderInst  = getInstance.invoke(null);
            Method getResolver = fabricLoaderApi.getMethod("getMappingResolver");
            return getResolver.invoke(loaderInst);
        } catch (Throwable e) {

            try {
                Class<?> implCls = Class.forName(
                    "net.fabricmc.loader.impl.FabricLoaderImpl", false, cl);
                Field instField = implCls.getDeclaredField("INSTANCE");
                instField.setAccessible(true);
                Object impl = instField.get(null);
                Method getRes = implCls.getMethod("getMappingResolver");
                return getRes.invoke(impl);
            } catch (Throwable e2) {
                System.err.println("[AccessWidenerBridge] MappingResolver not available: "
                    + e2.getMessage());
                return null;
            }
        }
    }









    private static boolean applyViaFabricLoader(byte[] bytes, ClassLoader cl) {
        try {

            Class<?> implCls = Class.forName(
                "net.fabricmc.loader.impl.FabricLoaderImpl", false, cl);
            Field instField = implCls.getDeclaredField("INSTANCE");
            instField.setAccessible(true);
            Object impl = instField.get(null);
            if (impl == null) throw new IllegalStateException("FabricLoaderImpl.INSTANCE is null");


            Method getAw;
            try {
                getAw = implCls.getDeclaredMethod("getAccessWidener");
            } catch (NoSuchMethodException e) {

                Field awField = null;
                for (Field f : implCls.getDeclaredFields()) {
                    if (f.getName().equals("accessWidener") ||
                        f.getType().getSimpleName().equals("AccessWidener")) {
                        awField = f; break;
                    }
                }
                if (awField == null) throw new NoSuchFieldException("accessWidener field not found");
                awField.setAccessible(true);
                Object aw = awField.get(impl);
                return readIntoAccessWidener(aw, bytes, cl);
            }
            getAw.setAccessible(true);
            Object aw = getAw.invoke(impl);
            return readIntoAccessWidener(aw, bytes, cl);

        } catch (Throwable e) {
            System.err.println("[AccessWidenerBridge] applyViaFabricLoader failed: "
                + e.getMessage());
            return false;
        }
    }





    private static boolean readIntoAccessWidener(Object aw, byte[] bytes,
                                                   ClassLoader cl) throws Throwable {
        if (aw == null) throw new IllegalArgumentException("AccessWidener is null");

        Class<?> awCls = aw.getClass();
        BufferedReader reader = new BufferedReader(
            new InputStreamReader(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8));


        for (Method m : awCls.getMethods()) {
            if (!m.getName().equals("read")) continue;
            Class<?>[] params = m.getParameterTypes();

            try {
                if (params.length == 1 && params[0].isAssignableFrom(BufferedReader.class)) {

                    m.invoke(aw, reader);
                    System.out.println("[AccessWidenerBridge] applied via read(BufferedReader)");
                    return true;
                }
                if (params.length == 2 && params[0].isAssignableFrom(BufferedReader.class)
                        && params[1] == String.class) {

                    m.invoke(aw, reader, "named");
                    System.out.println("[AccessWidenerBridge] applied via read(BufferedReader, ns)");
                    return true;
                }
            } catch (Throwable e) {

                reader = new BufferedReader(
                    new InputStreamReader(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8));
            }
        }


        try {
            Class<?> readerCls = Class.forName(
                "net.fabricmc.accesswidener.AccessWidenerReader", false, cl);
            Object readerInst = readerCls.getDeclaredConstructor(awCls).newInstance(aw);
            Method readM = readerCls.getDeclaredMethod("read", BufferedReader.class);
            readM.invoke(readerInst, reader);
            System.out.println("[AccessWidenerBridge] applied via AccessWidenerReader.read()");
            return true;
        } catch (Throwable e2) {
            System.err.println("[AccessWidenerBridge] all read() paths failed: " + e2.getMessage());
            return false;
        }
    }







    public static String parseNamespace(byte[] bytes) {
        try {
            String first = new String(bytes, 0,
                Math.min(bytes.length, 256), StandardCharsets.UTF_8)
                .split("\\n")[0].trim();
            String[] parts = first.split("\\s+");
            return parts.length >= 3 ? parts[2] : "named";
        } catch (Throwable e) {
            return "named";
        }
    }
}

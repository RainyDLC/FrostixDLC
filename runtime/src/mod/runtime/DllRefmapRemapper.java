package mod.runtime;

import org.spongepowered.asm.mixin.extensibility.IRemapper;
import org.spongepowered.asm.mixin.refmap.IReferenceMapper;
import org.spongepowered.asm.mixin.refmap.ReferenceMapper;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.*;




















public final class DllRefmapRemapper implements IReferenceMapper, IRemapper {


    private final IReferenceMapper delegate;
    private final String refmapPath;

    private String namespace;


    private DllRefmapRemapper(IReferenceMapper delegate, String refmapPath, String namespace) {
        this.delegate   = delegate;
        this.refmapPath = refmapPath;
        this.namespace  = namespace;
    }







    public static DllRefmapRemapper build(String refmapPath) {

        byte[] bytes;
        try {
            bytes = NativeBridge.getMixinResource0(refmapPath);
        } catch (UnsatisfiedLinkError e) {
            bytes = null;
        }
        if (bytes == null || bytes.length == 0) {
            System.err.println("[DllRefmapRemapper] resource not found: " + refmapPath);
            return null;
        }



        IReferenceMapper mapper = null;
        try {

            Class<?> rmCls = Class.forName(
                "org.spongepowered.asm.mixin.refmap.ReferenceMapper",
                false,
                Thread.currentThread().getContextClassLoader());


            Method readMethod = null;
            for (Method m : rmCls.getMethods()) {
                if ((m.getName().equals("read") || m.getName().equals("fromStream"))
                        && m.getParameterCount() == 1) {
                    readMethod = m;
                    break;
                }
            }

            if (readMethod != null) {
                Class<?> paramType = readMethod.getParameterTypes()[0];
                Object arg;
                if (paramType.isAssignableFrom(java.io.Reader.class)) {
                    arg = new InputStreamReader(
                        new ByteArrayInputStream(bytes), StandardCharsets.UTF_8);
                } else {
                    arg = new ByteArrayInputStream(bytes);
                }
                mapper = (IReferenceMapper) readMethod.invoke(null, arg);
            } else {

                String json = new String(bytes, StandardCharsets.UTF_8);
                for (var ctor : rmCls.getDeclaredConstructors()) {
                    if (ctor.getParameterCount() == 1
                            && ctor.getParameterTypes()[0] == String.class) {
                        ctor.setAccessible(true);
                        mapper = (IReferenceMapper) ctor.newInstance(json);
                        break;
                    }
                }
            }
        } catch (Throwable e) {
            System.err.println("[DllRefmapRemapper] failed to build mapper: " + e.getMessage());
            return null;
        }

        if (mapper == null) {
            System.err.println("[DllRefmapRemapper] mapper is null after build");
            return null;
        }


        String ns = "named";
        try {

            Method getNs = mapper.getClass().getMethod("getDefaultNamespace");
            Object nsVal = getNs.invoke(mapper);
            if (nsVal instanceof String) ns = (String) nsVal;
        } catch (Throwable e) {

        }

        return new DllRefmapRemapper(mapper, refmapPath, ns);
    }






    public void install(String configPath) {
        try {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();


            Class<?> cfgCls = Class.forName(
                "org.spongepowered.asm.mixin.transformer.MixinConfig", false, cl);


            Method getConfig = null;
            for (Method m : cfgCls.getDeclaredMethods()) {
                if (m.getName().equals("getConfig") && m.getParameterCount() == 1
                        && m.getParameterTypes()[0] == String.class) {
                    getConfig = m;
                    break;
                }
            }
            if (getConfig == null) {
                System.err.println("[DllRefmapRemapper] MixinConfig.getConfig not found");
                return;
            }
            getConfig.setAccessible(true);
            Object cfg = getConfig.invoke(null, configPath);
            if (cfg == null) {
                System.err.println("[DllRefmapRemapper] config not registered yet: " + configPath);
                return;
            }


            Class<?> iRmCls = Class.forName(
                "org.spongepowered.asm.mixin.refmap.IReferenceMapper", false, cl);
            Method setRm = null;
            for (Method m : cfgCls.getDeclaredMethods()) {
                if (m.getName().equals("setReferenceMapper")) {
                    setRm = m; break;
                }
            }
            if (setRm == null) {

                for (Field f : cfgCls.getDeclaredFields()) {
                    if (f.getType().isAssignableFrom(this.getClass())
                            || f.getType() == iRmCls) {
                        f.setAccessible(true);
                        f.set(cfg, this);
                        System.out.println("[DllRefmapRemapper] installed via field: "
                            + f.getName() + " for " + configPath);
                        return;
                    }
                }
                System.err.println("[DllRefmapRemapper] setReferenceMapper not found");
                return;
            }
            setRm.setAccessible(true);
            setRm.invoke(cfg, this);
            System.out.println("[DllRefmapRemapper] installed for " + configPath
                + " (refmap=" + refmapPath + " ns=" + namespace + ")");


            installRemapperIfNeeded(cfg, cfgCls);

        } catch (Throwable e) {
            System.err.println("[DllRefmapRemapper] install failed: " + e.getMessage());
        }
    }





    private void installRemapperIfNeeded(Object cfg, Class<?> cfgCls) {
        try {
            String prop = System.getProperty("mixin.env.remapRefMap", "false");
            if (!"true".equals(prop)) return;

            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            Class<?> remapperCls = Class.forName(
                "org.spongepowered.asm.mixin.extensibility.IRemapper", false, cl);

            Method setRemapper = null;
            for (Method m : cfgCls.getDeclaredMethods()) {
                if (m.getName().contains("Remapper") && m.getParameterCount() == 1) {
                    setRemapper = m; break;
                }
            }
            if (setRemapper == null) return;
            setRemapper.setAccessible(true);
            setRemapper.invoke(cfg, this);
            System.out.println("[DllRefmapRemapper] IRemapper installed (remapRefMap=true)");
        } catch (Throwable e) {

        }
    }



    @Override
    public boolean isDefault() {
        try { return delegate.isDefault(); }
        catch (Throwable e) { return false; }
    }

    @Override
    public String getResourceName() {
        try { return delegate.getResourceName(); }
        catch (Throwable e) { return refmapPath; }
    }

    @Override
    public String getStatus() {
        try { return delegate.getStatus(); }
        catch (Throwable e) { return "DllRefmapRemapper[" + refmapPath + "]"; }
    }

    @Override
    public String getContext() {
        try { return delegate.getContext(); }
        catch (Throwable e) { return ""; }
    }

    @Override
    public void setContext(String context) {
        try { delegate.setContext(context); }
        catch (Throwable e) {  }
    }

    @Override
    public String remap(String context, String reference) {
        try { return delegate.remap(context, reference); }
        catch (Throwable e) { return reference; }
    }

    @Override
    public String remapWithContext(String context, String className, String reference) {
        try { return delegate.remapWithContext(context, className, reference); }
        catch (Throwable e) { return reference; }
    }




    @Override
    public String mapMethodName(String owner, String name, String desc) {
        String remapped = remap(namespace, name + desc);
        return remapped != null ? remapped : name;
    }

    @Override
    public String mapFieldName(String owner, String name, String desc) {
        String remapped = remap(namespace, name + ":" + desc);
        return remapped != null ? remapped : name;
    }

    @Override
    public String map(String typeName) {
        String remapped = remap(namespace, typeName);
        return remapped != null ? remapped : typeName;
    }

    @Override
    public String unmap(String typeName) {
        return typeName;
    }

    @Override
    public String mapDesc(String desc) {
        return desc;
    }

    @Override
    public String unmapDesc(String desc) {
        return desc;
    }
}

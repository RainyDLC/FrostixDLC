package mod.runtime;

import org.spongepowered.asm.mixin.MixinEnvironment;

import java.lang.reflect.Field;
import java.lang.reflect.Method;



























public final class MixinVersionAdapter {

    private MixinVersionAdapter() {}










    public static String knotFingerprint(ClassLoader knotLoader) {
        if (knotLoader == null) return "null";
        String name = knotLoader.getClass().getName();
        if (!name.contains("KnotClassLoader") && !name.contains("LaunchClassLoader"))
            return "foreign:" + name;


        Class<?> cls = knotLoader.getClass();
        boolean hasDelegate   = hasField(cls, "delegate");
        boolean hasParent     = hasField(cls, "parent");
        boolean hasUrlLoader  = hasField(cls, "urlLoader");
        boolean hasCodeSource = hasField(cls, "codeSource");

        if (hasDelegate && hasUrlLoader) return "knot-0.14.x";
        if (hasDelegate && hasCodeSource) return "knot-0.15.x";
        if (hasParent) return "knot-0.16.x+";
        return "knot-unknown";
    }





    public static boolean knotSupports(ClassLoader knotLoader) {
        if (knotLoader == null) return false;
        String n = knotLoader.getClass().getName();
        return n.contains("KnotClassLoader") || n.contains("LaunchClassLoader");
    }





    public static boolean knotSelfTest(ClassLoader knotLoader) {
        if (!knotSupports(knotLoader)) return false;
        try {
            Class<?> obj = knotLoader.loadClass("java.lang.Object");
            return obj != null;
        } catch (Throwable e) {
            return false;
        }
    }






    public static void installKnotAsTCCL(ClassLoader knotLoader) {
        Thread.currentThread().setContextClassLoader(knotLoader);
    }








    public static String fabricFingerprint() {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        try {

            Class<?> flCls = Class.forName("net.fabricmc.loader.api.FabricLoader", false, cl);
            Object inst = flCls.getMethod("getInstance").invoke(null);

            boolean hasGameProvider = hasMethod(flCls, "getGameProvider");
            boolean hasModList      = hasMethod(flCls, "getAllMods");
            boolean hasGameDir      = hasMethod(flCls, "getGameDir");

            if (hasGameProvider && hasGameDir) return "fabric-0.15.x+";
            if (hasGameProvider) return "fabric-0.14.x";
            if (hasModList) return "fabric-0.12.x";
            return "fabric-unknown";
        } catch (Throwable e) {
            return "fabric-unavailable:" + e.getClass().getSimpleName();
        }
    }







    public static Field getAccessWidenerField(Object fabricLoaderImpl) {
        Class<?> cls = fabricLoaderImpl.getClass();

        for (String fieldName : new String[]{"accessWidener", "widener", "aw",
                                              "accessWidener_", "mAccessWidener"}) {
            try {
                Field f = cls.getDeclaredField(fieldName);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException e) {  }
        }

        for (Field f : cls.getDeclaredFields()) {
            String typeName = f.getType().getSimpleName();
            if (typeName.contains("AccessWidener") || typeName.contains("Widener")) {
                f.setAccessible(true);
                return f;
            }
        }
        return null;
    }







    public static String mixinFingerprint() {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        try {
            Class<?> envCls = Class.forName(
                "org.spongepowered.asm.mixin.MixinEnvironment", false, cl);

            boolean hasGetDefault   = hasMethod(envCls, "getDefaultEnvironment");
            boolean hasGetCurrent   = hasMethod(envCls, "getCurrentEnvironment");
            boolean hasAudit        = hasMethod(envCls, "audit");
            boolean hasSide         = hasMethod(envCls, "getSide");

            if (hasGetCurrent && hasAudit) return "mixin-0.8.x+";
            if (hasGetDefault && hasSide) return "mixin-0.7.x";
            if (hasGetDefault) return "mixin-0.6.x";
            return "mixin-unknown";
        } catch (Throwable e) {
            return "mixin-unavailable:" + e.getClass().getSimpleName();
        }
    }











    public static boolean applyCompatibilityLevel(String level) {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        try {
            Class<?> envCls = Class.forName(
                "org.spongepowered.asm.mixin.MixinEnvironment", false, cl);



            Object env = null;
            for (Method m : envCls.getDeclaredMethods()) {
                if ((m.getName().equals("getCurrentEnvironment") ||
                     m.getName().equals("getDefaultEnvironment"))
                        && m.getParameterCount() == 0) {
                    m.setAccessible(true);
                    try { env = m.invoke(null); } catch (Throwable ignored) {}
                    if (env != null) break;
                }
            }
            if (env == null) return false;


            Class<?> compatCls = Class.forName(
                "org.spongepowered.asm.mixin.MixinEnvironment$CompatibilityLevel",
                false, cl);


            Object compatValue = null;
            try {
                compatValue = Enum.valueOf(
                    compatCls.asSubclass(Enum.class), level);
            } catch (IllegalArgumentException e) {

                String[] fallbacks = {"JAVA_21","JAVA_17","JAVA_16","JAVA_11","JAVA_8"};
                for (String fb : fallbacks) {
                    try {
                        compatValue = Enum.valueOf(compatCls.asSubclass(Enum.class), fb);
                        break;
                    } catch (IllegalArgumentException ignored) {}
                }
            }
            if (compatValue == null) return false;



            Class<?> envInstCls = env.getClass();
            for (Method m : envInstCls.getDeclaredMethods()) {
                if (m.getName().contains("setCompatibilityLevel")
                        || m.getName().contains("CompatibilityLevel")) {
                    if (m.getParameterCount() != 1) continue;
                    if (!m.getParameterTypes()[0].isAssignableFrom(compatCls)) continue;
                    m.setAccessible(true);
                    m.invoke(env, compatValue);
                    System.out.println("[MixinVersionAdapter] CompatibilityLevel set to "
                        + compatValue + " (method: " + m.getName() + ")");
                    return true;
                }
            }
            return false;
        } catch (Throwable e) {
            System.err.println("[MixinVersionAdapter] applyCompatibilityLevel failed: "
                + e.getMessage());
            return false;
        }
    }










    public static void install(ClassLoader knotLoader) {
        System.out.println("[MixinVersionAdapter] knot fingerprint: "
            + knotFingerprint(knotLoader));
        System.out.println("[MixinVersionAdapter] knot selfTest: "
            + knotSelfTest(knotLoader));
        System.out.println("[MixinVersionAdapter] mixin fingerprint: "
            + mixinFingerprint());
        System.out.println("[MixinVersionAdapter] fabric fingerprint: "
            + fabricFingerprint());


        boolean applied = applyCompatibilityLevel("JAVA_21");
        if (!applied) {
            applied = applyCompatibilityLevel("JAVA_17");
        }
        if (!applied) {
            System.err.println("[MixinVersionAdapter] WARNING: could not set CompatibilityLevel");
        }
    }



    private static boolean hasField(Class<?> cls, String name) {
        try { cls.getDeclaredField(name); return true; }
        catch (NoSuchFieldException e) { return false; }
    }

    private static boolean hasMethod(Class<?> cls, String name) {
        for (Method m : cls.getDeclaredMethods())
            if (m.getName().equals(name)) return true;
        return false;
    }
}

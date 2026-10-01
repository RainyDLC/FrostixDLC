import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.transformer.ClassInfo;
import org.spongepowered.asm.service.*;
import org.spongepowered.asm.logging.LoggerAdapterDefault;


public class ClassInfoCacheRegression {
    static Object field(Class<?> c, Object o, String name) throws Exception {
        Field f=c.getDeclaredField(name); f.setAccessible(true); return f.get(o);
    }
    static void set(Class<?> c, Object o, String name, Object value) throws Exception {
        Field f=c.getDeclaredField(name); f.setAccessible(true); f.set(o,value);
    }
    public static void main(String[] args) throws Exception {
        IClassBytecodeProvider provider=(IClassBytecodeProvider)Proxy.newProxyInstance(
            ClassInfoCacheRegression.class.getClassLoader(),new Class<?>[]{IClassBytecodeProvider.class},(p,m,a)->{
                ClassNode node=new ClassNode();
                try(var stream=ClassLoader.getSystemResourceAsStream(((String)a[0]).replace('.','/')+".class")) {
                    if(stream==null) throw new ClassNotFoundException((String)a[0]);
                    new ClassReader(stream).accept(node,0);
                }
                return node;
            });
        IMixinService service=(IMixinService)Proxy.newProxyInstance(ClassInfoCacheRegression.class.getClassLoader(),
            new Class<?>[]{IMixinService.class},(p,m,a)->switch(m.getName()) {
                case "getLogger" -> new LoggerAdapterDefault((String)a[0]);
                case "getBytecodeProvider" -> provider;
                case "getName" -> "regression-fixture";
                case "isValid" -> true;
                case "getSideName" -> "CLIENT";
                default -> null;
            });
        Constructor<MixinService> constructor=MixinService.class.getDeclaredConstructor(); constructor.setAccessible(true);
        MixinService manager=constructor.newInstance();
        set(MixinService.class,manager,"service",service);
        set(MixinService.class,null,"instance",manager);
        Map<Object,Object> properties=new HashMap<>();
        IGlobalPropertyService globals=(IGlobalPropertyService)Proxy.newProxyInstance(ClassInfoCacheRegression.class.getClassLoader(),
            new Class<?>[]{IGlobalPropertyService.class},(p,m,a)->switch(m.getName()) {
                case "resolveKey" -> new IPropertyKey(){ public String toString(){return (String)a[0];} };
                case "getProperty" -> "mixin.initialised".equals(a[0].toString()) ? "0.8.7" : properties.get(a[0].toString());
                case "getPropertyString", "getPropertyKeys" -> a.length>1 ? a[1] : null;
                case "getPropertyWithDefault" -> properties.getOrDefault(a[0].toString(),a[1]);
                case "setProperty" -> {properties.put(a[0].toString(),a[1]); yield null;}
                default -> null;
            });
        set(MixinService.class,manager,"propertyService",globals);
        @SuppressWarnings("unchecked") Map<String,ClassInfo> cache=(Map<String,ClassInfo>)field(ClassInfo.class,null,"cache");
        ClassInfo root=(ClassInfo)field(ClassInfo.class,null,"OBJECT");
        if(cache.get("java/lang/Object")!=root) throw new AssertionError("Initial root invariant missing");
        cache.clear();
        ClassInfo rebuilt=ClassInfo.forName("java/lang/Object");
        if(rebuilt==null) throw new AssertionError("Object rebuild failed");
        if(rebuilt==root || rebuilt.getSuperClass()!=rebuilt) throw new AssertionError("Expected self-parent after clear");
        System.out.println("REPRODUCED: cache.clear recreates Object with itself as superclass");
        Thread stuck=new Thread(rebuilt::hasMixinTargetInHierarchy,"old-hierarchy-walk"); stuck.setDaemon(true); stuck.start(); stuck.join(300);
        if(!stuck.isAlive()) throw new AssertionError("Old hierarchy traversal unexpectedly terminated");
        System.out.println("REPRODUCED: actual hasMixinTargetInHierarchy does not terminate");
        Method clear = mod.runtime.NativeBridge.class.getDeclaredMethod("clearClassInfoCache", Class.class, Object.class);
        clear.setAccessible(true);
        Object transformerMonitor = new Object();
        try {
            clear.invoke(null, ClassInfo.class, transformerMonitor);
            throw new AssertionError("Production invalidation accepted a corrupt hierarchy");
        } catch (InvocationTargetException expected) {
            if (!(expected.getCause() instanceof IllegalStateException)) throw expected;
        }
        System.out.println("PASS: production helper rejects an already-corrupted cache");

        cache.clear(); cache.put("java/lang/Object",root);
        if(ClassInfo.forName("java/lang/Object")!=root || root.getSuperClass()!=null) throw new AssertionError("Root not restored");
        ExecutorService worker=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r);t.setDaemon(true);return t;});
        ClassInfo existing=ClassInfo.forName("ClassInfoCacheRegression");
        Method addInterface=ClassInfo.class.getDeclaredMethod("addInterface",String.class);
        addInterface.setAccessible(true);
        addInterface.invoke(existing,"java/lang/Runnable");
        for (int i=0; i<100; i++) {
            cache.put("regression/missing/OptionalType",null);
            int expected = 1;
            int removed = (Integer)clear.invoke(null, ClassInfo.class, transformerMonitor);
            ClassInfo fresh=ClassInfo.forName("ClassInfoCacheRegression");
            if(fresh!=existing || !fresh.getInterfaces().contains("java/lang/Runnable"))
                throw new AssertionError("Cache invalidation destroyed prepared accessor/interface metadata");
            if (removed != expected || cache.containsKey("regression/missing/OptionalType") || cache.get("java/lang/Object")!=root)
                throw new AssertionError("Production invalidation deleted the sentinel or retained a negative lookup");
            worker.submit(fresh::hasMixinTargetInHierarchy).get(2,TimeUnit.SECONDS);
        }
        CountDownLatch started = new CountDownLatch(1);
        Future<?> pending;
        synchronized (transformerMonitor) {
            pending=worker.submit(()->{
                started.countDown();
                try { clear.invoke(null, ClassInfo.class, transformerMonitor); }
                catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
            });
            if (!started.await(2,TimeUnit.SECONDS)) throw new AssertionError("Worker not started");
            try {
                pending.get(200,TimeUnit.MILLISECONDS);
                throw new AssertionError("Cache mutation did not acquire transformer monitor");
            } catch (TimeoutException expected) {}
        }
        pending.get(2,TimeUnit.SECONDS);
        if(worker.submit(root::hasMixinTargetInHierarchy).get(2,TimeUnit.SECONDS)) throw new AssertionError();
        worker.shutdownNow();
        System.out.println("PASS: production helper preserves OBJECT and hierarchy termination over 100 invalidations");
        System.out.println("PASS: production helper serializes mutation with transformer monitor");
        System.out.println("PASS: prepared interface metadata survives; missing-class lookups are invalidated");
    }
}

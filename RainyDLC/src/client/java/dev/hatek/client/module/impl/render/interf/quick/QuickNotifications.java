package dev.hatek.client.module.impl.render.interf.quick;
import java.util.ArrayList;
import java.util.List;
public final class QuickNotifications {
    public record Notice(String text,boolean enabled,long created) {}
    private static final List<Notice> ITEMS=new ArrayList<>();
    private QuickNotifications() {}
    public static synchronized void post(String name,boolean enabled) {
        ITEMS.add(new Notice("«"+name+"» "+(enabled?"включен!":"выключен!"),enabled,System.nanoTime()));
        while(ITEMS.size()>6) ITEMS.remove(0);
    }
    public static synchronized List<Notice> current() {
        long now=System.nanoTime();ITEMS.removeIf(n->now-n.created>3500000000L);return List.copyOf(ITEMS);
    }
    public static synchronized void clear() { ITEMS.clear(); }
}

package ru.white.manager.rotation;

import ru.white.Client;
import ru.white.manager.DragComponent;

import java.util.HashMap;

public final class ComponentManager extends HashMap<Class<? extends Component>, Component> {

    public void init() {
        add(new RotationProcess(), new FreeLookUtil(), new DragComponent(), new TestRotation());
        this.values().forEach(component -> Client.eventHandler().subscribe(component));
    }

    public void add(Component... components) {
        for (Component component : components) {
            this.put(component.getClass(), component);
        }
    }

    public void unregister(Component... components) {
        for (Component component : components) {
            Client.eventHandler().unsubscribe(component);
            this.remove(component.getClass());
        }
    }

    public <T extends Component> T get(final Class<T> clazz) {
        Component direct = super.get(clazz);
        if (direct != null) return clazz.cast(direct);

        for (Component component : values()) {
            if (clazz.isAssignableFrom(component.getClass())) {
                return clazz.cast(component);
            }
        }
        return null;
    }
}

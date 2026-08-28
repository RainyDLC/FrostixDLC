package ru.white.manager.events.orbit.listeners;

public interface IListener {
    void call(Object event);

    Class<?> getTarget();

    int getPriority();

    boolean isStatic();
}

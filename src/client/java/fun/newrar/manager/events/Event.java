package fun.newrar.manager.events;

import fun.newrar.Client;

public class Event {
    public String getName() {
        return this.getClass().getSimpleName().toLowerCase();
    }

    public void hook() {
        Client.eventHandler().post(this);
    }
}


package fun.newrar.manager.events;

import fun.newrar.Client;

public abstract class Handler {
    public Handler() {
        Client.eventHandler().subscribe(this);
    }
}


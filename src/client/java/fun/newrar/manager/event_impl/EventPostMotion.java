package fun.newrar.manager.event_impl;

import fun.newrar.manager.events.Event;

/**
 * Хукается хвостом ClientPlayerEntity#sendMovementPackets, т.е. после того как
 * позиция/ротация уже ушли на сервер в этом тике. Удар отсюда сервер считает
 * по свежему положению игрока, а не по прошлому тику.
 */
public class EventPostMotion extends Event {
}

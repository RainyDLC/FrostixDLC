package ru.white.manager.event_impl;

import ru.white.manager.events.Event;

/**
 * Точка сразу после {@code ClientPlayerEntity.sendMovementPackets()}.
 *
 * Нужна для атаки в правильном порядке пакетов. Ванильный порядок внутри одного
 * тика такой: {@code ClientPlayerEntity.tick()} HEAD (там висит EventUpdate) →
 * движение → {@code sendMovementPackets()}, где и уходит на сервер текущий
 * yaw/pitch. Если бить в EventUpdate, пакет атаки уходит РАНЬШЕ пакета поворота,
 * и сервер валидирует удар углом предыдущего тика — античиты с проверкой
 * направления взгляда такой удар отклоняют.
 *
 * Наведение делается в {@link MotionEvent} (HEAD того же метода), удар — здесь,
 * поэтому сервер сначала получает угол, а потом удар этим углом.
 */
public class PostMotionEvent extends Event {
}

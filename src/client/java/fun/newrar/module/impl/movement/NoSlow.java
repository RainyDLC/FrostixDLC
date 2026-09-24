package fun.newrar.module.impl.movement;

import fun.newrar.manager.event_impl.EventType;
import fun.newrar.manager.event_impl.UsingItemEvent;
import fun.newrar.manager.events.orbit.EventHandler;
import fun.newrar.module.api.Category;
import fun.newrar.module.api.Module;
import fun.newrar.module.api.ModuleInfo;

/**
 * Снятие замедления при использовании предметов (еда, зелья, щит, лук, арбалет).
 *
 * Замедление предмета — чисто КЛИЕНТСКАЯ механика: в 1.21.11 множитель активного предмета
 * применяется в ClientPlayerEntity#applyMovementSpeedFactors, и в common-джарнике (серверная
 * часть) этого кода нет вообще. Проверено по байткоду: applyMovementSpeedFactors и
 * getActiveItemSpeedMultiplier существуют только в clientOnly-джарнике.
 *
 * Значит сервер скорость от поедания не считает в принципе — ему важно только, чтобы позиция
 * не выглядела телепортом. Поэтому достаточно отменить множитель на клиенте: никаких пакетов,
 * spoof'ов и дрыгания состоянием использования (именно это ломало ходьбу и вызывало флаги).
 *
 * Redirect в ClientPlayerEntityMixin бьёт ровно во множитель предмета (ordinal 1 в
 * applyMovementSpeedFactors), так что отмена события = полная скорость с едой, зельями,
 * щитом, луком и арбалетом. Никаких настроек: либо работает, либо модуль выключен.
 */
@ModuleInfo(
        name = "No Slow",
        category = Category.MOVEMENT,
        desc = "Устранение замедления при использовании предметов, еды и стрельбе из лука"
)
public class NoSlow extends Module {
    @EventHandler
    public void onSlow(UsingItemEvent e) {
        if (e.getType() == EventType.ON) {
            e.cancel();
        }
    }
}

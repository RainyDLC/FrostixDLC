package fun.newrar.module.impl.render;

import fun.newrar.manager.event_impl.EventUpdate;
import fun.newrar.manager.events.orbit.EventHandler;
import fun.newrar.module.api.Category;
import fun.newrar.module.api.Module;
import fun.newrar.module.api.ModuleInfo;
import net.minecraft.entity.effect.StatusEffects;

@ModuleInfo(
        name = "Gamma",
        desc = "Максимальное осветление темных зон и пещер для идеальной видимости",
        category = Category.RENDER
)
public class Gamma extends Module {
    @Override
    public void onEnable() {
        super.onEnable();
        if (mc.worldRenderer != null) mc.worldRenderer.reload();
    }

    @Override
    public void onDisable() {
        super.onDisable();
        if (mc.worldRenderer != null) mc.worldRenderer.reload();
    }
    @EventHandler
    public void onUpdate(EventUpdate e) {
        if (mc.player == null) return;
        mc.player.removeStatusEffect(StatusEffects.NIGHT_VISION);
    }
}


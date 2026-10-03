package rtx.kimiko.api.modules.impl.Utils;

import net.minecraft.entity.Entity;
import org.jetbrains.annotations.NotNull;
import rtx.kimiko.api.events.EventHandler;
import rtx.kimiko.api.events.impl.player.AttackEntityEvent;
import rtx.kimiko.api.liteapi.Feature;
import rtx.kimiko.api.modules.Category;
import rtx.kimiko.api.modules.Module;
import rtx.kimiko.utils.storage.friend.FriendUtils;

@Feature(value={"nofrienddamage"})
public final class NoFriendDamage extends Module {

    public NoFriendDamage() {
        super("No Friend Damage", "Блокирует атаку по друзьям, чтобы не наносить им урон.", Category.COMBAT, rtx.kimiko.api.modules.SubCategory.PVP);
    }

    @EventHandler
    private void onAttack(@NotNull AttackEntityEvent event) {
        if (event.isSynthetic()) {
            return;
        }
        Entity target = event.getTarget();
        if (FriendUtils.isFriend(target)) {
            event.cancel();
        }
    }
}

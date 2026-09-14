package fun.newrar.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.util.AssetInfo;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import fun.newrar.Client;

import fun.newrar.cosmetics.LocalCosmetics;
import fun.newrar.module.impl.render.Cosmetics;

@Mixin(AbstractClientPlayerEntity.class)
public abstract class PlayerListEntryMixin {
    @Unique
    private static final Identifier CAPE_ID = Identifier.of("client", "cape");
    @Unique
    private static final AssetInfo.TextureAssetInfo CAPE_ASSET = new AssetInfo.TextureAssetInfo(CAPE_ID);

    @Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
    private void replaceCape(CallbackInfoReturnable<SkinTextures> cir) {
        AbstractClientPlayerEntity player = (AbstractClientPlayerEntity) (Object) this;
        MinecraftClient client = MinecraftClient.getInstance();

        if (client.player == null) return;

        boolean isLocal  = player.getUuid().equals(client.player.getUuid());
        boolean isFriend = Client.get().friendManager().isFriend(player.getName().getString());

        if (!isLocal && !isFriend) return;

        AssetInfo.TextureAssetInfo capeAsset = CAPE_ASSET;
        if (isLocal) {
            Cosmetics mod = Cosmetics.getInstance();
            if (mod != null && mod.isEnabled()) {
                Identifier customCape = LocalCosmetics.selectedCapeTexture();
                if (customCape != null) {
                    capeAsset = new AssetInfo.TextureAssetInfo(customCape);
                }
            }
        }

        SkinTextures old = cir.getReturnValue();
        cir.setReturnValue(new SkinTextures(
                old.body(),
                capeAsset,
                capeAsset,
                old.model(),
                old.secure()
        ));
    }
}


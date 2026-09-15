package fun.newrar.utils.render;

import fun.newrar.module.impl.render.ChainTargetEspRenderer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.Identifier;

import java.util.function.Function;

public final class ClientPipelines {
    private ClientPipelines() {
    }

    public static final Function<Identifier, RenderLayer> CHAIN_ESP = ChainTargetEspRenderer.CHAIN_ESP;
}

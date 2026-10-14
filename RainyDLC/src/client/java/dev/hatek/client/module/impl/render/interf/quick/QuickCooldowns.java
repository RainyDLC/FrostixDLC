package dev.hatek.client.module.impl.render.interf.quick;
import net.minecraft.resources.Identifier;
import java.util.Map;
/** Exact remaining server cooldown ticks, including groups whose item is no longer in inventory. */
public interface QuickCooldowns { Map<Identifier,Integer> quick$remainingTicks(); }

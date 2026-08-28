package ru.white.utils.other;

import baritone.api.BaritoneAPI;
import baritone.api.Settings;
import baritone.api.behavior.IPathingBehavior;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalBlock;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;

import java.util.List;

/**
 * Тонкая обёртка над Baritone для пасфайндинга модулей.
 * Если мода Baritone в сборке нет, все методы становятся заглушками,
 * и вызывающий модуль откатывается на свой простой ходок.
 */
public final class Pathing {
    private static final boolean AVAILABLE = loaded();

    private Pathing() {
    }

    private static boolean loaded() {
        try {
            FabricLoader loader = FabricLoader.getInstance();
            return loader.isModLoaded("baritone-meteor") || loader.isModLoaded("baritone");
        } catch (Throwable error) {
            return false;
        }
    }

    public static boolean available() {
        return AVAILABLE;
    }

    /** Ставит цель и запускает расчёт пути. */
    public static void goTo(BlockPos pos) {
        if (AVAILABLE && pos != null) Impl.goTo(pos);
    }

    /** Останавливает движение, если Baritone сейчас идёт. */
    public static void cancel() {
        if (AVAILABLE) Impl.cancel();
    }

    public static boolean pathing() {
        return AVAILABLE && Impl.pathing();
    }

    /** true, если текущая цель Baritone — ровно эта точка. */
    public static boolean hasGoal(BlockPos pos) {
        return AVAILABLE && pos != null && Impl.hasGoal(pos);
    }

    public static String status() {
        return AVAILABLE ? Impl.status() : "мода нет";
    }

    /** Профиль настроек под ферму: не ломать и не ставить блоки, обходить свечи, не бояться высоты. */
    public static void farmMode(boolean on) {
        if (AVAILABLE) Impl.farmMode(on);
    }

    /** Вынесено в отдельный класс, чтобы классы Baritone грузились только когда мод реально есть. */
    private static final class Impl {
        static void goTo(BlockPos pos) {
            BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(new GoalBlock(pos));
        }

        static void cancel() {
            IPathingBehavior pathing = BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior();
            if (pathing.isPathing() || pathing.hasPath()) pathing.cancelEverything();
        }

        static boolean pathing() {
            return BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().isPathing();
        }

        static boolean hasGoal(BlockPos pos) {
            Goal goal = BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().getGoal();
            return goal instanceof GoalBlock block
                    && block.x == pos.getX() && block.y == pos.getY() && block.z == pos.getZ();
        }

        static String status() {
            IPathingBehavior pathing = BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior();
            return pathing.isPathing() ? String.valueOf(pathing.getGoal()) : "стоит";
        }

        static void farmMode(boolean on) {
            Settings settings = BaritoneAPI.getSettings();
            settings.allowBreak.value = false;
            settings.allowPlace.value = false;
            settings.avoidance.value = on;
            settings.maxFallHeightNoWater.value = on ? 256 : 3;

            if (on) {
                settings.blockFreeLook.value = true;
                settings.randomLooking.value = 1.0;
                settings.randomLooking113.value = 1.0;
            }
            candles(on);
        }

        /** Свечи в варден зоне стопорят движение, поэтому Baritone обходит их стороной. */
        static void candles(boolean add) {
            List<Block> avoid = BaritoneAPI.getSettings().blocksToAvoid.value;

            try {
                for (Block block : Registries.BLOCK) {
                    if (!block.getDefaultState().isIn(BlockTags.CANDLES)) continue;
                    if (!add) avoid.remove(block);
                    else if (!avoid.contains(block)) avoid.add(block);
                }
            } catch (IllegalStateException ignored) {
            }
        }
    }
}

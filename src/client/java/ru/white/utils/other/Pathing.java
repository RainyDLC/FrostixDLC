package ru.white.utils.other;

import baritone.api.BaritoneAPI;
import baritone.api.Settings;
import baritone.api.behavior.IPathingBehavior;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.pathing.goals.GoalNear;
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

    /** Ставит цель и запускает расчёт пути ровно в этот блок. */
    public static void goTo(BlockPos pos) {
        goTo(pos, 0);
    }

    /**
     * Ставит цель с допуском в range блоков. Ноль — встать ровно в этот блок; такую цель
     * Baritone часто не может выполнить (в блоке нельзя стоять, на сундук нельзя встать),
     * и тогда он доходит до ближайшей точки, а дальше каждый пересчёт падает и он бросает цель.
     */
    public static void goTo(BlockPos pos, int range) {
        if (AVAILABLE && pos != null) Impl.goTo(pos, range);
    }

    /** Останавливает движение, если Baritone сейчас идёт. */
    public static void cancel() {
        if (AVAILABLE) Impl.cancel();
    }

    public static boolean pathing() {
        return AVAILABLE && Impl.pathing();
    }

    /** true, если Baritone идёт по пути или считает новый. */
    public static boolean busy() {
        return AVAILABLE && Impl.busy();
    }

    /** true, если текущая цель Baritone — ровно эта точка. */
    public static boolean hasGoal(BlockPos pos) {
        return hasGoal(pos, 0);
    }

    /** true, если текущая цель Baritone — эта точка с этим допуском. */
    public static boolean hasGoal(BlockPos pos, int range) {
        return AVAILABLE && pos != null && Impl.hasGoal(pos, range);
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
        static void goTo(BlockPos pos, int range) {
            BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(goal(pos, range));
        }

        static Goal goal(BlockPos pos, int range) {
            return range > 0 ? new GoalNear(pos, range) : new GoalBlock(pos);
        }

        static void cancel() {
            IPathingBehavior pathing = BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior();
            if (pathing.isPathing() || pathing.hasPath()) pathing.cancelEverything();
        }

        static boolean pathing() {
            return BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().isPathing();
        }

        static boolean busy() {
            IPathingBehavior pathing = BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior();
            return pathing.isPathing() || pathing.hasPath() || pathing.getInProgress().isPresent();
        }

        static boolean hasGoal(BlockPos pos, int range) {
            Goal goal = BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().getGoal();
            return goal != null && goal.equals(goal(pos, range));
        }

        static String status() {
            IPathingBehavior pathing = BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior();
            if (pathing.isPathing()) return "идёт " + pathing.getGoal();
            if (pathing.getInProgress().isPresent()) return "считает " + pathing.getGoal();
            return pathing.getGoal() == null ? "стоит" : "стоит, цель " + pathing.getGoal();
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

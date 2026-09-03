package fun.newrar.utils.other;

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

    public static void goTo(BlockPos pos) {
        goTo(pos, 0);
    }

    public static void goTo(BlockPos pos, int range) {
        if (AVAILABLE && pos != null) Impl.goTo(pos, range);
    }

    public static void cancel() {
        if (AVAILABLE) Impl.cancel();
    }

    public static boolean pathing() {
        return AVAILABLE && Impl.pathing();
    }

    public static boolean busy() {
        return AVAILABLE && Impl.busy();
    }

    public static boolean hasGoal(BlockPos pos) {
        return hasGoal(pos, 0);
    }

    public static boolean hasGoal(BlockPos pos, int range) {
        return AVAILABLE && pos != null && Impl.hasGoal(pos, range);
    }

    public static String status() {
        return AVAILABLE ? Impl.status() : "мода нет";
    }

    public static void farmMode(boolean on) {
        if (AVAILABLE) Impl.farmMode(on);
    }

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


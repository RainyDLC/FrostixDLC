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
            if (pos == null) return;
            Goal target = goal(pos, range);
            Goal current = BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().getGoal();
            if (current != null && current.equals(target) && busy()) return;
            BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(target);
        }

        static Goal goal(BlockPos pos, int range) {
            return range > 0 ? new GoalNear(pos, range) : new GoalBlock(pos);
        }

        static void cancel() {
            IPathingBehavior pathing = BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior();
            if (pathing.isPathing() || pathing.hasPath()) pathing.cancelEverything();
            BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoal(null);
        }

        static boolean pathing() {
            return BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().isPathing();
        }

        static boolean busy() {
            IPathingBehavior pathing = BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior();
            return pathing.isPathing() || pathing.hasPath() || pathing.getInProgress().isPresent();
        }

        static boolean hasGoal(BlockPos pos, int range) {
            if (pos == null) return false;
            Goal target = goal(pos, range);
            Goal customGoal = BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().getGoal();
            if (customGoal != null && customGoal.equals(target)) return true;
            Goal pathGoal = BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().getGoal();
            return pathGoal != null && pathGoal.equals(target);
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
            settings.allowParkour.value = false;
            settings.allowParkourPlace.value = false;
            settings.allowParkourAscend.value = false;
            settings.avoidance.value = on;
            settings.antiCheatCompatibility.value = true;
            settings.freeLook.value = true;
            settings.blockFreeLook.value = false;
            settings.allowInventory.value = false;
            settings.inventoryMoveOnlyIfStationary.value = true;
            settings.allowSprint.value = true;
            settings.sprintAscends.value = false;
            settings.allowDiagonalAscend.value = false;
            settings.allowDiagonalDescend.value = false;
            settings.jumpPenalty.value = on ? 10.0 : 2.0;
            settings.maxFallHeightNoWater.value = on ? 3 : 20;
            settings.randomLooking.value = 0.0;
            settings.randomLooking113.value = 0.0;
            settings.smoothLook.value = false;
            settings.primaryTimeoutMS.value = 250L;
            settings.failureTimeoutMS.value = 1000L;
            settings.planAheadPrimaryTimeoutMS.value = 800L;
            settings.planAheadFailureTimeoutMS.value = 1500L;
            settings.costHeuristic.value = 4.0;
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


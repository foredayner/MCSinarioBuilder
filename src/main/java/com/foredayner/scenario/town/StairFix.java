package com.foredayner.scenario.town;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.block.enums.StairShape;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * Stairs the AI turned back to front, put right after a building is placed: in a run of steps (each
 * one block up and on from the last) every step faces the way the run rises, its tall back on the
 * high side. And a step up to each outside door whose ground is one block lower than its floor.
 */
public final class StairFix {

    private StairFix() {
    }

    static boolean step(BlockState s) {
        return s.getBlock() instanceof StairsBlock && s.get(StairsBlock.HALF) == BlockHalf.BOTTOM;
    }

    /** How many steps go on from pos, one up and one on along dir each. */
    private static int up(ServerWorld world, BlockPos pos, Direction dir) {
        int n = 0;
        BlockPos cur = pos.offset(dir).up();
        while (n < 32 && step(world.getBlockState(cur))) {
            n++;
            cur = cur.offset(dir).up();
        }
        return n;
    }

    /** How many steps go on below pos, one down and one back against dir each. */
    private static int down(ServerWorld world, BlockPos pos, Direction dir) {
        int n = 0;
        BlockPos cur = pos.offset(dir.getOpposite()).down();
        while (n < 32 && step(world.getBlockState(cur))) {
            n++;
            cur = cur.offset(dir.getOpposite()).down();
        }
        return n;
    }

    /** The way a step's run rises (null if it is on its own). */
    static Direction rise(ServerWorld world, BlockPos pos) {
        Direction best = null;
        int bestLen = 0;
        for (Direction d : Direction.Type.HORIZONTAL) {
            int len = up(world, pos, d) + down(world, pos, d);
            if (len > bestLen) {
                best = d;
                bestLen = len;
            }
        }
        return best;
    }

    /** Turn every step of a run between min and max (inclusive) to face the way it rises. Returns how many were turned. */
    public static int runs(ServerWorld world, BlockPos min, BlockPos max) {
        int turned = 0;
        for (BlockPos pos : BlockPos.iterate(min, max)) {
            BlockState s = world.getBlockState(pos);
            if (!step(s)) {
                continue;
            }
            Direction r = rise(world, pos);
            if (r == null || s.get(StairsBlock.FACING) == r) {
                continue;
            }
            world.setBlockState(pos.toImmutable(), s.with(StairsBlock.FACING, r).with(StairsBlock.SHAPE, StairShape.STRAIGHT), Block.NOTIFY_ALL);
            turned++;
        }
        return turned;
    }

    /**
     * A step outside a door of an outside wall (lower half at {@code door}, the outside toward
     * {@code out}) when the ground there is exactly one block below the floor: a stair block facing
     * the door, its back against the building. Not when something is there already.
     */
    public static boolean doorStep(ServerWorld world, BlockPos door, Direction out) {
        BlockPos front = door.offset(out);
        BlockPos stepAt = front.down();
        BlockState floor = world.getBlockState(door.down());
        if (floor.isAir() || !floor.isSideSolidFullSquare(world, door.down(), Direction.UP)) {
            return false; // the door is not on a floor
        }
        BlockState here = world.getBlockState(stepAt);
        BlockState ground = world.getBlockState(stepAt.down());
        if (!world.getBlockState(front).isAir() || !(here.isAir() || here.isReplaceable() && !here.isLiquid())
            || !ground.isSideSolidFullSquare(world, stepAt.down(), Direction.UP)) {
            return false; // level with the floor, more than a block lower, or something in the way
        }
        world.setBlockState(stepAt, stairsFor(floor).with(StairsBlock.FACING, out.getOpposite()), Block.NOTIFY_LISTENERS);
        return true;
    }

    /** Stairs of the floor's own material if there are any (oak planks: oak stairs), else cobblestone stairs. */
    static BlockState stairsFor(BlockState floor) {
        Identifier id = Registries.BLOCK.getId(floor.getBlock());
        String n = id.getPath();
        String[] tries = { n + "_stairs", n.replaceAll("_planks$", "") + "_stairs", n.replaceAll("s$", "") + "_stairs",
            n.replace("_bricks", "_brick") + "_stairs", n.replaceAll("^(polished_|smooth_)", "") + "_stairs" };
        for (String t : tries) {
            Block b = Registries.BLOCK.get(Identifier.of(id.getNamespace(), t));
            if (b instanceof StairsBlock) {
                return b.getDefaultState();
            }
        }
        return Blocks.COBBLESTONE_STAIRS.getDefaultState();
    }
}

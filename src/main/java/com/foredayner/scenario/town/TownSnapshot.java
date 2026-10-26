package com.foredayner.scenario.town;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/**
 * The blocks of the town's box as they were before anything was done, to put the land back.
 * Taken and put back a few columns at a time so the server does not stall. Kept in memory only.
 */
public final class TownSnapshot {

    private final ServerWorld world;
    private final BlockPos min;
    private final int sx, sy, sz;
    private final BlockState[] states;
    private int next;

    public TownSnapshot(ServerWorld world, BlockPos min, BlockPos max) {
        this.world = world;
        this.min = min;
        this.sx = max.getX() - min.getX() + 1;
        this.sy = max.getY() - min.getY() + 1;
        this.sz = max.getZ() - min.getZ() + 1;
        this.states = new BlockState[sx * sy * sz];
    }

    public ServerWorld world() {
        return world;
    }

    private int index(int x, int y, int z) {
        return (x * sz + z) * sy + y;
    }

    /** Copy up to {@code columns} more columns; true when all are copied. */
    public boolean capture(int columns) {
        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (int n = 0; n < columns && next < sx * sz; n++, next++) {
            int x = next / sz, z = next % sz;
            for (int y = 0; y < sy; y++) {
                states[index(x, y, z)] = world.getBlockState(pos.set(min.getX() + x, min.getY() + y, min.getZ() + z));
            }
        }
        if (next >= sx * sz) {
            next = 0;
            return true;
        }
        return false;
    }

    /** Put back up to {@code columns} more columns; true when all are back. */
    public boolean restore(int columns) {
        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (int n = 0; n < columns && next < sx * sz; n++, next++) {
            int x = next / sz, z = next % sz;
            for (int y = 0; y < sy; y++) {
                BlockState old = states[index(x, y, z)];
                pos.set(min.getX() + x, min.getY() + y, min.getZ() + z);
                if (old != null && world.getBlockState(pos) != old) {
                    world.setBlockState(pos, old, Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
                }
            }
        }
        if (next >= sx * sz) {
            next = 0;
            return true;
        }
        return false;
    }
}

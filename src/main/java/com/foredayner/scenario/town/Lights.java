package com.foredayner.scenario.town;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LightBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * After a building is placed: the dark spots inside get invisible light blocks (vanilla's
 * minecraft:light), so every room is lit and no mob appears there. Light is worked out from the
 * blocks themselves (each lamp, torch or glowing block lights as far as its brightness reaches,
 * one less per block), not from the game's light engine, which is still catching up right after
 * placing. A spot is dark when it would get light 7 or less.
 */
public final class Lights {

    private Lights() {
    }

    /** Light at or below this is dark (mobs spawn at 0; 8+ reads as a lit room). */
    static final int DARK = 7;
    /** The level of each light block put in. */
    static final int LEVEL = 15;

    /** Light up the dark floor spots inside the building between min and max (inclusive). Returns how many lights went in. */
    public static int fill(ServerWorld world, BlockPos min, BlockPos max) {
        int w = max.getX() - min.getX() + 1, h = max.getY() - min.getY() + 1, d = max.getZ() - min.getZ() + 1;
        if (w <= 0 || h <= 0 || d <= 0) {
            return 0;
        }
        // Where the light comes from: every block that shines.
        List<int[]> sources = new ArrayList<>();
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                for (int z = 0; z < d; z++) {
                    int lum = world.getBlockState(min.add(x, y, z)).getLuminance();
                    if (lum > DARK) {
                        sources.add(new int[] { x, y, z, lum });
                    }
                }
            }
        }
        // The floor spots inside: air with a solid floor under it, under a roof or ceiling.
        List<int[]> spots = new ArrayList<>();
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < d; z++) {
                for (int y = 1; y < h; y++) {
                    BlockPos at = min.add(x, y, z);
                    BlockState here = world.getBlockState(at);
                    BlockState below = world.getBlockState(at.down());
                    if (!here.isAir() || below.isAir() || below.getCollisionShape(world, at.down()).isEmpty()) {
                        continue;
                    }
                    if (Doors.covered(world, at, max.getY())) {
                        spots.add(new int[] { x, y, z });
                    }
                }
            }
        }
        int added = 0;
        for (int[] s : spots) {
            if (lightAt(sources, s) > DARK) {
                continue;
            }
            // A light just under the ceiling above the spot (or 2 up when the room is low).
            BlockPos at = min.add(s[0], s[1], s[2]);
            BlockPos put = null;
            for (int up = 2; up >= 0; up--) {
                BlockPos p = at.up(up);
                if (p.getY() <= max.getY() && world.getBlockState(p).isAir()) {
                    put = p;
                    break;
                }
            }
            if (put == null) {
                continue;
            }
            world.setBlockState(put, Blocks.LIGHT.getDefaultState().with(LightBlock.LEVEL_15, LEVEL), Block.NOTIFY_LISTENERS);
            sources.add(new int[] { put.getX() - min.getX(), put.getY() - min.getY(), put.getZ() - min.getZ(), LEVEL });
            added++;
        }
        return added;
    }

    /** The light a spot gets from the sources: the brightest, less one per block of distance (walls ignored). */
    static int lightAt(List<int[]> sources, int[] s) {
        int best = 0;
        for (int[] src : sources) {
            int dist = Math.abs(src[0] - s[0]) + Math.abs(src[1] - s[1]) + Math.abs(src[2] - s[2]);
            best = Math.max(best, src[3] - dist);
        }
        return best;
    }
}

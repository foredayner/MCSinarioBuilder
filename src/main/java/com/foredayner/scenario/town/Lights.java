package com.foredayner.scenario.town;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LightBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * After a building is placed: the dim spots inside get invisible light blocks (vanilla's
 * minecraft:light) at head height, until every floor spot is properly lit. Light is worked out from
 * the blocks themselves, spreading like the game's own block light (one less per step, stopped by
 * solid blocks, so a lamp behind a wall does not light this room), not from the game's light
 * engine, which is still catching up right after placing. Sky light is not counted: rooms are lit
 * at night too.
 */
public final class Lights {

    private Lights() {
    }

    /** Light every floor spot gets in an ordinary building (10 reads as a lit room, not just "no mobs"). */
    public static final int BRIGHT = 10;
    /** In an old, worn or abandoned building: dim but never pitch dark (and still no mobs). */
    public static final int DIM = 7;
    /** The level of each light block put in. */
    static final int LEVEL = 15;

    /** Light up the floor spots inside the building between min and max (inclusive) to at least {@code want}. Returns how many lights went in. */
    public static int fill(ServerWorld world, BlockPos min, BlockPos max, int want) {
        int w = max.getX() - min.getX() + 1, h = max.getY() - min.getY() + 1, d = max.getZ() - min.getZ() + 1;
        if (w <= 0 || h <= 0 || d <= 0) {
            return 0;
        }
        int[][][] light = new int[w][h][d];
        boolean[][][] solid = new boolean[w][h][d];
        Deque<int[]> queue = new ArrayDeque<>();
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                for (int z = 0; z < d; z++) {
                    BlockPos at = min.add(x, y, z);
                    BlockState state = world.getBlockState(at);
                    solid[x][y][z] = state.isSolidBlock(world, at);
                    int lum = state.getLuminance();
                    if (lum > 0) {
                        light[x][y][z] = lum;
                        queue.add(new int[] { x, y, z });
                    }
                }
            }
        }
        spread(light, solid, queue);
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
            if (light[s[0]][s[1]][s[2]] >= want) {
                continue;
            }
            // At head height over the spot (out of the way), or on the spot itself.
            int[] put = null;
            for (int up = 1; up >= 0; up--) {
                int y = s[1] + up;
                if (y < h && world.getBlockState(min.add(s[0], y, s[2])).isAir()) {
                    put = new int[] { s[0], y, s[2] };
                    break;
                }
            }
            if (put == null) {
                continue;
            }
            world.setBlockState(min.add(put[0], put[1], put[2]), Blocks.LIGHT.getDefaultState().with(LightBlock.LEVEL_15, LEVEL),
                Block.NOTIFY_LISTENERS);
            light[put[0]][put[1]][put[2]] = LEVEL;
            queue.add(put);
            spread(light, solid, queue);
            added++;
        }
        return added;
    }

    /** Spread light from the queued cells: one less per step, through anything that is not solid. */
    static void spread(int[][][] light, boolean[][][] solid, Deque<int[]> queue) {
        int w = light.length, h = light[0].length, d = light[0][0].length;
        int[][] steps = { { 1, 0, 0 }, { -1, 0, 0 }, { 0, 1, 0 }, { 0, -1, 0 }, { 0, 0, 1 }, { 0, 0, -1 } };
        while (!queue.isEmpty()) {
            int[] c = queue.poll();
            int next = light[c[0]][c[1]][c[2]] - 1;
            if (next <= 0) {
                continue;
            }
            for (int[] s : steps) {
                int x = c[0] + s[0], y = c[1] + s[1], z = c[2] + s[2];
                if (x < 0 || y < 0 || z < 0 || x >= w || y >= h || z >= d || solid[x][y][z] || light[x][y][z] >= next) {
                    continue;
                }
                light[x][y][z] = next;
                queue.add(new int[] { x, y, z });
            }
        }
    }
}

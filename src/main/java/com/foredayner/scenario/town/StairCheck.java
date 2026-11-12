package com.foredayner.scenario.town;

import com.example.ai.build.BuildPlan;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Can every storey of a designed building be walked to from the ground outside? The design is laid
 * out as blocks in memory (in the AI's frame: x 0..w-1, z -d..-1, flat ground at y = -1) and walked
 * the way a person walks: on anything solid, through doors, up one block at a time (a step, a
 * stair, a slab), down a few, up and down ladders, with 2 blocks of room. Storey k stands on its
 * floor at y = 5k. If one cannot be reached, {@link #addStairs} can put in a straight flight.
 */
public final class StairCheck {

    private StairCheck() {
    }

    /** Floor to floor. */
    public static final int STOREY = 5;

    static final byte AIR = 0, SOLID = 1, PASS = 2, CLIMB = 3, TALL = 4;

    private static final Pattern AIRS = Pattern.compile("(cave_|void_)?air|light|structure_void");
    private static final Pattern CLIMBS = Pattern.compile("ladder|vine|scaffolding|.*_vines(_plant)?");
    /** Things you walk through: doors, gates and trapdoors (opened), thin things on floors and walls. */
    private static final Pattern PASSES = Pattern.compile(".*_door|.*_fence_gate|.*_trapdoor|.*_carpet|.*_pressure_plate|.*_button"
        + "|.*torch|.*_sign|.*_banner|.*_wall_sign|.*_hanging_sign|lever|.*rail|redstone_wire|tripwire.*|cobweb|snow"
        + "|short_grass|tall_grass|fern|large_fern|dead_bush|.*_sapling|dandelion|poppy|.*_tulip|.*orchid|allium|azure_bluet"
        + "|oxeye_daisy|cornflower|lily_of_the_valley|sunflower|lilac|rose_bush|peony|.*_mushroom|flower_pot|potted_.*"
        + "|.*candle|lantern|soul_lantern|chain|.*_coral_fan|item_frame|painting|.*_head|.*_skull|string");
    /** Things too tall to step onto: fences, walls, bars, panes. */
    private static final Pattern TALLS = Pattern.compile(".*_fence|.*_wall|iron_bars|.*glass_pane|glass_pane");

    static byte kind(String block) {
        if (block == null) {
            return AIR;
        }
        String id = block;
        int b = id.indexOf('[');
        if (b >= 0) {
            id = id.substring(0, b);
        }
        int c = id.indexOf(':');
        if (c >= 0) {
            id = id.substring(c + 1);
        }
        if (AIRS.matcher(id).matches()) {
            return AIR;
        }
        if (CLIMBS.matcher(id).matches()) {
            return CLIMB;
        }
        if (PASSES.matcher(id).matches()) {
            return PASS;
        }
        if (TALLS.matcher(id).matches()) {
            return TALL;
        }
        return SOLID;
    }

    /** The design as blocks: x -1..w, z -d-1..0, y -2..h+2 (outside the ops: ground below y 0, air above). */
    static final class Grid {
        final int x0 = -1, z0, y0 = -2, sx, sy, sz;
        final byte[] cells;

        Grid(int w, int d, int h) {
            z0 = -d - 1;
            sx = w + 2;
            sz = d + 2;
            sy = h + 5;
            cells = new byte[sx * sy * sz];
            for (int x = 0; x < sx; x++) {
                for (int z = 0; z < sz; z++) {
                    put(x + x0, -2, z + z0, SOLID);
                    put(x + x0, -1, z + z0, SOLID);
                }
            }
        }

        boolean in(int x, int y, int z) {
            return x >= x0 && x < x0 + sx && y >= y0 && y < y0 + sy && z >= z0 && z < z0 + sz;
        }

        byte at(int x, int y, int z) {
            if (!in(x, y, z)) {
                return y < 0 ? SOLID : AIR;
            }
            return cells[((y - y0) * sz + (z - z0)) * sx + (x - x0)];
        }

        void put(int x, int y, int z, byte k) {
            if (in(x, y, z)) {
                cells[((y - y0) * sz + (z - z0)) * sx + (x - x0)] = k;
            }
        }
    }

    static Grid grid(BuildPlan plan, int w, int d, int h) {
        Grid g = new Grid(w, d, h);
        for (BuildPlan.Op op : plan.ops) {
            byte k = kind(op.block);
            int x1 = Math.min(op.x1, op.x2), x2 = Math.max(op.x1, op.x2);
            int y1 = Math.min(op.y1, op.y2), y2 = Math.max(op.y1, op.y2);
            int z1 = Math.min(op.z1, op.z2), z2 = Math.max(op.z1, op.z2);
            boolean hollow = "hollow".equals(op.mode), outline = "outline".equals(op.mode), keep = "keep".equals(op.mode);
            for (int x = Math.max(x1, g.x0); x <= Math.min(x2, g.x0 + g.sx - 1); x++) {
                for (int y = Math.max(y1, g.y0); y <= Math.min(y2, g.y0 + g.sy - 1); y++) {
                    for (int z = Math.max(z1, g.z0); z <= Math.min(z2, g.z0 + g.sz - 1); z++) {
                        boolean shell = x == x1 || x == x2 || y == y1 || y == y2 || z == z1 || z == z2;
                        if (outline && !shell) {
                            continue;
                        }
                        if (keep && g.at(x, y, z) != AIR) {
                            continue;
                        }
                        g.put(x, y, z, hollow && !shell ? AIR : k);
                    }
                }
            }
        }
        return g;
    }

    private static boolean open(byte k) {
        return k == AIR || k == PASS || k == CLIMB;
    }

    /** Somewhere a person stands: room for feet and head, something under the feet (or a ladder). */
    static boolean stand(Grid g, int x, int y, int z) {
        if (!open(g.at(x, y, z)) || !open(g.at(x, y + 1, z))) {
            return false;
        }
        byte under = g.at(x, y - 1, z);
        return under == SOLID || under == TALL || under == PASS && y > 0 || g.at(x, y, z) == CLIMB || under == CLIMB;
    }

    /** Every spot that can be walked to from outside the site, as feet positions. */
    static boolean[] reach(Grid g) {
        boolean[] seen = new boolean[g.cells.length];
        ArrayDeque<int[]> q = new ArrayDeque<>();
        for (int x = g.x0; x < g.x0 + g.sx; x++) {
            for (int z = g.z0; z < g.z0 + g.sz; z++) {
                boolean border = x == g.x0 || x == g.x0 + g.sx - 1 || z == g.z0 || z == g.z0 + g.sz - 1;
                if (border && stand(g, x, 0, z)) {
                    mark(g, seen, q, x, 0, z);
                }
            }
        }
        int[][] dirs = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
        while (!q.isEmpty()) {
            int[] c = q.poll();
            int x = c[0], y = c[1], z = c[2];
            for (int[] dd : dirs) {
                int nx = x + dd[0], nz = z + dd[1];
                if (stand(g, nx, y, nz)) {
                    mark(g, seen, q, nx, y, nz);
                }
                // Up a step (not onto a fence or wall top), with head room over both.
                if (g.at(nx, y, nz) != TALL && open(g.at(x, y + 2, z)) && stand(g, nx, y + 1, nz)) {
                    mark(g, seen, q, nx, y + 1, nz);
                }
                // Down: off an edge, a few blocks at most.
                if (open(g.at(nx, y, nz)) && open(g.at(nx, y + 1, nz))) {
                    for (int dy = 1; dy <= 3; dy++) {
                        if (!open(g.at(nx, y - dy + 1, nz))) {
                            break;
                        }
                        if (stand(g, nx, y - dy, nz)) {
                            mark(g, seen, q, nx, y - dy, nz);
                            break;
                        }
                    }
                }
            }
            // Ladders.
            if (g.at(x, y, z) == CLIMB || g.at(x, y + 1, z) == CLIMB) {
                if (stand(g, x, y + 1, z) || g.at(x, y + 1, z) == CLIMB && open(g.at(x, y + 2, z))) {
                    mark(g, seen, q, x, y + 1, z);
                }
            }
            if (g.at(x, y - 1, z) == CLIMB && open(g.at(x, y - 1, z))) {
                mark(g, seen, q, x, y - 1, z);
            }
        }
        return seen;
    }

    private static void mark(Grid g, boolean[] seen, ArrayDeque<int[]> q, int x, int y, int z) {
        if (!g.in(x, y, z)) {
            return;
        }
        int i = ((y - g.y0) * g.sz + (z - g.z0)) * g.sx + (x - g.x0);
        if (!seen[i]) {
            seen[i] = true;
            q.add(new int[] { x, y, z });
        }
    }

    private static boolean seen(Grid g, boolean[] seen, int x, int y, int z) {
        return g.in(x, y, z) && seen[((y - g.y0) * g.sz + (z - g.z0)) * g.sx + (x - g.x0)];
    }

    /** Spots of storey k (feet on its floor at y = 5k, inside the site): {reached, there}. */
    static int[] storey(Grid g, boolean[] seen, int k, int w, int d) {
        int reached = 0, there = 0;
        int y = k * STOREY + 1;
        for (int x = 0; x < w; x++) {
            for (int z = -d; z <= -1; z++) {
                if (stand(g, x, y, z) && g.at(x, y - 1, z) != AIR) {
                    there++;
                    if (seen(g, seen, x, y, z)) {
                        reached++;
                    }
                }
            }
        }
        return new int[] { reached, there };
    }

    /**
     * What is wrong with getting about the building (null if nothing): the ground floor must be
     * reachable from outside (a door), and each floor above from the one below (stairs).
     */
    public static String problem(BuildPlan plan, int w, int d, int h, int floors, int stairWidth) {
        if (floors < 1) {
            return null;
        }
        Grid g = grid(plan, w, d, h);
        boolean[] seen = reach(g);
        List<String> out = new ArrayList<>();
        for (int k = 0; k < floors; k++) {
            int[] s = storey(g, seen, k, w, d);
            if (k == 0 && s[1] < 4) {
                continue; // a ground floor laid at another height: nothing to say about it here
            }
            if (s[1] < 4) {
                out.add(String.format(Locale.ROOT,
                    "storey %d has no floor at y = %d to stand on (the building should have %d floors, %d blocks apart)", k + 1,
                    k * STOREY, floors, STOREY));
            } else if (s[0] == 0) {
                out.add(k == 0 ? "the ground floor cannot be entered from outside: it needs a door (2 high) in an outer wall with"
                    + " open ground in front of it"
                    : String.format(Locale.ROOT, "storey %d (its floor at y = %d) cannot be reached from the floor below: build ONE"
                        + " STRAIGHT staircase %d block%s wide (4 stair blocks rising one by one from the floor at y = %d, then step"
                        + " onto the floor at y = %d), with 3 blocks of head room over every step, the hole in the floor above"
                        + " over the steps, and a railing round the hole", k + 1, k * STOREY, stairWidth, stairWidth > 1 ? "s" : "",
                        (k - 1) * STOREY, k * STOREY));
            }
        }
        return out.isEmpty() ? null : "GETTING ABOUT THE BUILDING:\n- " + String.join("\n- ", out);
    }

    /**
     * Last resort when the AI's design still has a storey nobody can reach: a straight flight put
     * into the design, where the floor below has room for it and the floor above is there to step
     * onto. Returns how many flights were added.
     */
    public static int addStairs(BuildPlan plan, int w, int d, int h, int floors, int stairWidth, String stairBlock) {
        int added = 0;
        for (int k = 1; k < floors; k++) {
            Grid g = grid(plan, w, d, h);
            boolean[] seen = reach(g);
            int[] s = storey(g, seen, k, w, d);
            if (s[1] < 4 || s[0] > 0) {
                continue;
            }
            int[] spot = findFlight(g, seen, k, w, d, stairWidth);
            if (spot == null && stairWidth > 1) {
                spot = findFlight(g, seen, k, w, d, 1);
                if (spot != null) {
                    stairWidth = 1;
                }
            }
            if (spot == null) {
                continue;
            }
            putFlight(plan, spot, k, stairWidth, stairBlock);
            added++;
        }
        return added;
    }

    /** Cells: {x, z, dx, dz, sx, sz}: index 0 is the top (on storey k), index 5 the foot (on storey k-1). */
    private static int[] findFlight(Grid g, boolean[] seen, int k, int w, int d, int width) {
        int top = k * STOREY + 1, foot = (k - 1) * STOREY + 1;
        int[][] runs = { { 1, 0, 0, 1 }, { -1, 0, 0, 1 }, { 0, 1, 1, 0 }, { 0, -1, 1, 0 } };
        for (int x = 0; x < w; x++) {
            for (int z = -d; z <= -1; z++) {
                for (int[] r : runs) {
                    if (fits(g, seen, x, z, r, width, top, foot)) {
                        return new int[] { x, z, r[0], r[1], r[2], r[3] };
                    }
                }
            }
        }
        return null;
    }

    private static boolean fits(Grid g, boolean[] seen, int x, int z, int[] r, int width, int top, int foot) {
        for (int lane = 0; lane < width; lane++) {
            for (int i = 0; i <= 5; i++) {
                int cx = x + r[0] * i + r[2] * lane, cz = z + r[1] * i + r[3] * lane;
                if (cx < 0 || cz > -1 || cx >= g.sx - 2 || cz < g.z0 + 1) {
                    return false;
                }
                if (i == 0) {
                    // The top: standing on the floor above.
                    if (!stand(g, cx, top, cz)) {
                        return false;
                    }
                } else {
                    // Below: room on the lower floor (feet and up to the floor above), with floor under it.
                    for (int y = foot; y < top - 1; y++) {
                        if (!open(g.at(cx, y, cz))) {
                            return false;
                        }
                    }
                    if (g.at(cx, foot - 1, cz) == AIR) {
                        return false;
                    }
                }
            }
            // The foot is somewhere people already get to.
            int fx = x + r[0] * 5 + r[2] * lane, fz = z + r[1] * 5 + r[3] * lane;
            if (!seen(g, seen, fx, foot, fz)) {
                return false;
            }
        }
        return true;
    }

    private static void putFlight(BuildPlan plan, int[] s, int k, int width, String stairBlock) {
        int top = k * STOREY + 1;
        int dx = s[2], dz = s[3];
        String facing = -dx > 0 ? "east" : -dx < 0 ? "west" : -dz > 0 ? "south" : "north"; // up, toward the top
        for (int lane = 0; lane < width; lane++) {
            for (int i = 1; i <= 4; i++) {
                int x = s[0] + dx * i + s[4] * lane, z = s[1] + dz * i + s[5] * lane;
                int stairY = top - 1 - i;
                plan.ops.add(new BuildPlan.Op("fill", x, stairY + 1, z, x, Math.min(stairY + 3, top + 1), z, "minecraft:air", "replace"));
                plan.ops.add(new BuildPlan.Op("set", x, stairY, z, x, stairY, z, stairBlock + "[facing=" + facing + "]", null));
            }
        }
    }
}

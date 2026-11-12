package com.foredayner.scenario.town;

import com.foredayner.scenario.layout.TownLayout;
import com.foredayner.scenario.layout.Underground;
import com.foredayner.scenario.sheet.Period;
import com.foredayner.scenario.sheet.ScenarioSheet;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LadderBlock;
import net.minecraft.block.LanternBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.List;

/**
 * Digs what is under the town (Underground): cellars under buildings, landings under places a
 * tunnel reaches, and the tunnels, a few blocks per tick, after the town's ground. Walls, floors
 * and ceilings are of the period's stone; open tunnels are lit, secret ones are dark and end in
 * a false wall (a cracked block in the cellar's wall that can be broken). After the buildings
 * stand, each cellar gets its way down: straight stairs (1 or 2 wide by the building's size) from
 * the ground floor, through a railed opening, to each cellar floor; a landing under a place
 * without a cellar, or a cellar too small for stairs, gets a trapdoor and a ladder.
 * Where the world is too shallow, it digs as many floors as fit, or none, and says so.
 */
public final class UndergroundWork {

    private static final int FLAGS = Block.NOTIFY_LISTENERS | Block.FORCE_STATE;
    /** Which way a ladder faces, by number. */
    private static final Direction[] DIRS = { Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.NORTH };

    private final ServerWorld world;
    private final BlockPos origin;
    private final int y0;
    private final List<Underground.Cellar> cellars = new ArrayList<>();
    /** Each cellar's stairs (none: a ladder). */
    private final java.util.Map<String, Underground.Stairwell> stairs = new java.util.HashMap<>();
    private BlockState stairBlock = Blocks.STONE_BRICK_STAIRS.getDefaultState();
    private BlockState railBlock = Blocks.SPRUCE_FENCE.getDefaultState();
    private BlockState floorBlock = Blocks.STONE_BRICKS.getDefaultState();
    private final List<int[]> boxes = new ArrayList<>();
    private final List<BlockState> fills = new ArrayList<>();
    /** Single blocks after the boxes (lights, false walls, barrels). */
    private final List<int[]> singles = new ArrayList<>();
    private final List<BlockState> singleStates = new ArrayList<>();
    private int box;
    private int cell;
    private int single;
    private final List<String> notes = new ArrayList<>();
    private final BlockPos.Mutable pos = new BlockPos.Mutable();

    public UndergroundWork(ServerWorld world, BlockPos origin, TownLayout layout, ScenarioSheet sheet) {
        this.world = world;
        this.origin = origin;
        this.y0 = origin.getY();
        String period = sheet.period();
        boolean old = Period.medieval(period);
        boolean now = Period.contemporary(period);
        BlockState wall = (old ? Blocks.COBBLESTONE : now ? Blocks.POLISHED_ANDESITE : Blocks.STONE_BRICKS).getDefaultState();
        BlockState fake = (old ? Blocks.MOSSY_COBBLESTONE : now ? Blocks.ANDESITE : Blocks.CRACKED_STONE_BRICKS).getDefaultState();
        BlockState floor = (old ? Blocks.STONE : now ? Blocks.SMOOTH_STONE : Blocks.STONE_BRICKS).getDefaultState();
        BlockState air = Blocks.AIR.getDefaultState();
        this.stairBlock = (old ? Blocks.COBBLESTONE_STAIRS : now ? Blocks.POLISHED_ANDESITE_STAIRS : Blocks.STONE_BRICK_STAIRS).getDefaultState();
        this.railBlock = (now ? Blocks.DARK_OAK_FENCE : Blocks.SPRUCE_FENCE).getDefaultState();
        this.floorBlock = floor;
        int deepest = (y0 - 2 - world.getBottomY()) / Underground.LEVEL_H; // floors that fit above the bottom of the world
        for (Underground.Cellar c : Underground.cellars(layout, sheet)) {
            int levels = Math.min(c.levels(), deepest);
            if (levels <= 0) {
                notes.add(name(sheet, c.id()) + ": 땅이 얕아 지하를 못 지었어요");
                continue;
            }
            if (levels < c.levels()) {
                notes.add(name(sheet, c.id()) + ": 땅이 얕아 지하를 " + levels + "층만 지었어요");
            }
            Underground.Cellar dug = new Underground.Cellar(c.id(), c.x1(), c.z1(), c.x2(), c.z2(), levels, c.landing());
            cellars.add(dug);
            TownLayout.Site site = layout.site(c.id());
            // Placed as the building's design was told (by the cellar's planned floors), dug as deep as it went.
            Underground.Stairwell planned = site == null ? null : Underground.stairwell(c, site.w, site.d);
            Underground.Stairwell sw = planned == null ? null : new Underground.Stairwell(planned.id(), planned.x0(), planned.z0(),
                planned.dx(), planned.dz(), planned.sx(), planned.sz(), planned.width(), levels);
            if (sw != null) {
                stairs.put(c.id(), sw);
            }
            int bottom = y0 - 1 - Underground.LEVEL_H * levels;
            // The shell, then the air inside each floor and the floors between them.
            box(c.x1(), bottom, c.z1(), c.x2(), y0 - 2, c.z2(), wall);
            for (int k = 1; k <= levels; k++) {
                int f = y0 - 1 - Underground.LEVEL_H * k;
                box(c.x1() + 1, f, c.z1() + 1, c.x2() - 1, f, c.z2() - 1, floor);
                box(c.x1() + 1, f + 1, c.z1() + 1, c.x2() - 1, f + Underground.LEVEL_H - 1, c.z2() - 1, air);
                // A light in the middle of the ceiling; barrels and cobwebs along the walls of real cellars.
                int[] m = lightSpot(dug, sw);
                single(m[0], f + Underground.LEVEL_H - 1, m[1],
                    (sinister(sheet, c.id()) ? Blocks.SOUL_LANTERN : Blocks.LANTERN).getDefaultState().with(LanternBlock.HANGING, true));
                if (!c.landing()) {
                    decorate(dug, f, sheet);
                }
            }
        }
        for (Underground.Tunnel t : Underground.tunnels(layout, sheet)) {
            if (deepest < 1) {
                notes.add("땅이 얕아 지하 통로를 못 지었어요");
                break;
            }
            int f = y0 - 1 - Underground.LEVEL_H; // the first cellar floor's level
            for (long k : t.walls()) {
                box(Underground.keyX(k), f, Underground.keyZ(k), Underground.keyX(k), y0 - 2, Underground.keyZ(k), wall);
            }
            for (long k : t.inside()) {
                int x = Underground.keyX(k), z = Underground.keyZ(k);
                box(x, f, z, x, f, z, floor);
                box(x, f + 1, z, x, f + 3, z, air);
                box(x, f + 4, z, x, y0 - 2, z, wall);
            }
            for (long k : t.doorways()) {
                // Through a cellar's wall: open, or a false wall that looks almost like the rest of it.
                int x = Underground.keyX(k), z = Underground.keyZ(k);
                box(x, f + 1, z, x, f + 3, z, t.secret() ? fake : air);
            }
            if (!t.secret()) {
                // Open tunnels are lit every 8 blocks; secret ones are dark.
                for (int i = 4; i < t.line().size(); i += 8) {
                    int[] p = t.line().get(i);
                    if (t.inside().contains(Underground.key(p[0], p[1]))) {
                        single(p[0], f + 3, p[1], Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING, true));
                    }
                }
            }
        }
    }

    private static String name(ScenarioSheet sheet, String id) {
        ScenarioSheet.Place p = sheet.place(id);
        return p == null ? id : p.name;
    }

    private static boolean sinister(ScenarioSheet sheet, String id) {
        ScenarioSheet.Place p = sheet.place(id);
        return p != null && "sinister".equals(p.condition);
    }

    private void box(int x1, int y1, int z1, int x2, int y2, int z2, BlockState state) {
        boxes.add(new int[] { Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2), Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2) });
        fills.add(state);
    }

    private void single(int x, int y, int z, BlockState state) {
        singles.add(new int[] { x, y, z });
        singleStates.add(state);
    }

    /** Barrels now and then along the walls of a cellar floor; cobwebs in the corners of worn ones. */
    private void decorate(Underground.Cellar c, int floor, ScenarioSheet sheet) {
        ScenarioSheet.Place p = sheet.place(c.id());
        boolean old = p != null && !"kept".equals(p.condition);
        for (int x = c.x1() + 1; x <= c.x2() - 1; x++) {
            for (int z = c.z1() + 1; z <= c.z2() - 1; z++) {
                boolean edge = x == c.x1() + 1 || x == c.x2() - 1 || z == c.z1() + 1 || z == c.z2() - 1;
                boolean corner = (x == c.x1() + 1 || x == c.x2() - 1) && (z == c.z1() + 1 || z == c.z2() - 1);
                // Keep the corner by the way down (x1+1, z1+1) and the next cells clear.
                if (!edge || x <= c.x1() + 2 && z <= c.z1() + 2) {
                    continue;
                }
                int n = Math.floorMod((x * 73856093) ^ (z * 19349663) ^ floor, 100);
                if (corner && old) {
                    single(x, floor + Underground.LEVEL_H - 1, z, Blocks.COBWEB.getDefaultState());
                } else if (n < 22) {
                    single(x, floor + 1, z, Blocks.BARREL.getDefaultState());
                } else if (n < 26) {
                    single(x, floor + 1, z, Blocks.CHEST.getDefaultState());
                }
            }
        }
    }

    public boolean done() {
        return box >= boxes.size() && single >= singles.size();
    }

    /** What could not be dug (for the keeper). */
    public List<String> notes() {
        return notes;
    }

    /** Set up to {@code blocks} blocks. */
    public void step(int blocks) {
        int n = 0;
        while (n < blocks && box < boxes.size()) {
            int[] b = boxes.get(box);
            int w = b[3] - b[0] + 1, h = b[4] - b[1] + 1, d = b[5] - b[2] + 1;
            int total = w * h * d;
            while (n < blocks && cell < total) {
                int x = b[0] + cell % w, y = b[1] + (cell / w) % h, z = b[2] + cell / (w * h);
                set(x, y, z, fills.get(box));
                cell++;
                n++;
            }
            if (cell >= total) {
                box++;
                cell = 0;
            }
        }
        while (n < blocks && box >= boxes.size() && single < singles.size()) {
            int[] s = singles.get(single);
            set(s[0], s[1], s[2], singleStates.get(single));
            single++;
            n++;
        }
    }

    private void set(int x, int y, int z, BlockState state) {
        if (y <= world.getBottomY() || y > world.getTopYInclusive()) {
            return;
        }
        pos.set(origin.getX() + x, y, origin.getZ() + z);
        if (world.getBlockState(pos) != state) {
            world.setBlockState(pos, state, FLAGS);
        }
    }

    /**
     * After the buildings stand: each cellar's way down, a trapdoor in the ground floor and a ladder
     * along the cellar's wall to its lowest floor, where the floor above it is free to stand on.
     */
    public void hatches() {
        for (Underground.Cellar c : cellars) {
            Underground.Stairwell sw = stairs.get(c.id());
            if (sw != null) {
                stairsDown(sw);
                continue;
            }
            int[] spot = freeSpot(c);
            int x = spot[0], z = spot[1];
            Direction facing = DIRS[spot[2]];
            int bottom = y0 - 1 - Underground.LEVEL_H * c.levels();
            set(x, y0 - 1, z, Blocks.SPRUCE_TRAPDOOR.getDefaultState().with(TrapdoorBlock.HALF, BlockHalf.TOP)
                .with(TrapdoorBlock.FACING, facing));
            for (int y = y0 - 2; y > bottom; y--) {
                set(x, y, z, Blocks.LADDER.getDefaultState().with(LadderBlock.FACING, facing));
            }
        }
    }

    /** The cellar's stairs, if it has them (else a ladder). */
    public Underground.Stairwell stairwell(String id) {
        return stairs.get(id);
    }

    /** The middle of the cellar for its light, or beside the stairs when they run through the middle. */
    private static int[] lightSpot(Underground.Cellar c, Underground.Stairwell sw) {
        int[] m = c.middle();
        if (sw == null || !inStairs(sw, m[0], m[1])) {
            return m;
        }
        for (int lane = sw.width() * sw.levels() + 1; lane <= sw.width() * sw.levels() + 4; lane++) {
            int[] at = sw.cell(Underground.Stairwell.LENGTH / 2, lane);
            if (c.inside(at[0], at[1])) {
                return at;
            }
        }
        for (int lane = -2; lane >= -5; lane--) {
            int[] at = sw.cell(Underground.Stairwell.LENGTH / 2, lane);
            if (c.inside(at[0], at[1])) {
                return at;
            }
        }
        return m;
    }

    private static boolean inStairs(Underground.Stairwell sw, int x, int z) {
        for (int i = 0; i <= Underground.Stairwell.LENGTH; i++) {
            for (int lane = -1; lane <= sw.width() * sw.levels(); lane++) {
                int[] c = sw.cell(i, lane);
                if (c[0] == x && c[1] == z) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Straight stairs from the ground floor down to each cellar floor: the first flight in lanes
     * 0..w-1 from index 0, the second (to a second cellar floor) beside it running back. Head room
     * of 3 over every step, an opening in the floor above with a railing round it.
     */
    private void stairsDown(Underground.Stairwell sw) {
        int w = sw.width();
        int top = groundFeet(sw);
        int first = y0 - Underground.LEVEL_H; // feet on the first cellar floor
        int n = flight(sw, 0, w, 0, 1, top, first, top + 2);
        if (sw.levels() >= 2) {
            int second = first - Underground.LEVEL_H;
            flight(sw, w, w, n + 1, -1, first, second, first + Underground.LEVEL_H - 2);
        }
    }

    /** The feet height on the ground floor at the top of the stairs (over the building's floor). */
    private int groundFeet(Underground.Stairwell sw) {
        int best = y0 + 1;
        for (int lane = 0; lane < sw.width(); lane++) {
            int[] c = sw.cell(0, lane);
            for (int y = y0 + 3; y >= y0 - 1; y--) {
                if (!air(c[0], y, c[1]) && air(c[0], y + 1, c[1]) && air(c[0], y + 2, c[1])) {
                    return Math.min(y + 1, y0 + 1);
                }
            }
        }
        return best;
    }

    /**
     * One straight flight in lanes lane0..lane0+width-1, its top at index start (feet at top),
     * going down along +dir to its landing (feet at bottom); nothing is cleared above ceiling.
     * Returns the number of steps.
     */
    private int flight(Underground.Stairwell sw, int lane0, int width, int start, int dir, int top, int bottom, int ceiling) {
        int n = Math.max(1, top - bottom - 1);
        int ux = -sw.dx() * dir, uz = -sw.dz() * dir; // up the stairs: back toward the top
        Direction up = ux > 0 ? Direction.EAST : ux < 0 ? Direction.WEST : uz > 0 ? Direction.SOUTH : Direction.NORTH;
        BlockState step = stairBlock.with(StairsBlock.FACING, up);
        BlockState air = Blocks.AIR.getDefaultState();
        boolean[] opened = new boolean[n + 2];
        for (int lane = lane0; lane < lane0 + width; lane++) {
            for (int k = 0; k <= n + 1; k++) {
                int[] c = sw.cell(start + dir * k, lane);
                int feet = k == 0 ? top : k == n + 1 ? bottom : top - k;
                if (k >= 1 && k <= n) {
                    set(c[0], feet - 1, c[1], step);
                }
                if (k == n + 1 && air(c[0], bottom - 1, c[1])) {
                    set(c[0], bottom - 1, c[1], floorBlock);
                }
                for (int y = feet; y <= Math.min(feet + 2, ceiling); y++) {
                    set(c[0], y, c[1], air);
                }
                if (k >= 1 && feet <= top - 1 && feet + 2 >= top - 1) {
                    opened[k] = true; // the floor above this step was cut
                }
            }
        }
        // A railing on the upper floor round the opening: both sides, and across its far end.
        for (int k = 1; k <= n + 1; k++) {
            if (opened[k]) {
                for (int lane : new int[] { lane0 - 1, lane0 + width }) {
                    rail(sw.cell(start + dir * k, lane), top);
                }
            } else if (opened[k - 1]) {
                for (int lane = lane0 - 1; lane <= lane0 + width; lane++) {
                    rail(sw.cell(start + dir * k, lane), top);
                }
                break;
            }
        }
        return n;
    }

    /** A railing post at feet height where there is floor to stand it on and nothing in the way. */
    private void rail(int[] c, int feet) {
        if (air(c[0], feet, c[1]) && !air(c[0], feet - 1, c[1])) {
            set(c[0], feet, c[1], railBlock);
        }
    }

    /**
     * A cell inside the cellar next to its wall, with room to stand above it in the building (air at
     * the ground floor and over it): {x, z, which way the ladder faces (away from the wall): 0 east, 1 south, 2 west, 3 north}.
     */
    private int[] freeSpot(Underground.Cellar c) {
        List<int[]> spots = new ArrayList<>();
        for (int x = c.x1() + 1; x <= c.x2() - 1; x++) {
            spots.add(new int[] { x, c.z1() + 1, 1 });
            spots.add(new int[] { x, c.z2() - 1, 3 });
        }
        for (int z = c.z1() + 1; z <= c.z2() - 1; z++) {
            spots.add(new int[] { c.x1() + 1, z, 0 });
            spots.add(new int[] { c.x2() - 1, z, 2 });
        }
        for (int[] s : spots) {
            if (air(s[0], y0, s[1]) && air(s[0], y0 + 1, s[1])) {
                return s;
            }
        }
        // Nowhere free: the corner, cleared to stand in.
        int[] s = { c.x1() + 1, c.z1() + 1, 0 };
        set(s[0], y0, s[1], Blocks.AIR.getDefaultState());
        set(s[0], y0 + 1, s[1], Blocks.AIR.getDefaultState());
        return s;
    }

    private boolean air(int x, int y, int z) {
        return world.getBlockState(pos.set(origin.getX() + x, y, origin.getZ() + z)).isAir();
    }
}

package com.foredayner.scenario.town;

import com.foredayner.scenario.layout.TownLayout;
import com.foredayner.scenario.sheet.ScenarioSheet;
import net.minecraft.block.BlockState;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.List;

/**
 * The flights of stairs in a built town, found in the world (the AI drew them, the code dug the
 * cellar ones): a straight run of stair blocks (facing any way), each a block up and on from the last, that rises a
 * whole storey and stands under a ceiling (not a roof's slope, not steps up to a porch). For the
 * game table: shown on its map and walked up and down.
 */
public final class Flights {

    private Flights() {
    }

    /** One flight: its floors, the way it rises, its steps, where one stands at its foot and its top (world blocks). */
    public static final class Flight {
        public int from;
        public int to;
        public String dir;
        public List<int[]> steps = new ArrayList<>();
        public int[] bottom;
        public int[] top;
    }

    /** At least this many steps (a storey is 5 up). */
    static final int MIN_STEPS = 4;

    /** Something solid over the foot of the flight within a storey or two: it is indoors. */
    private static boolean underCeiling(ServerWorld world, BlockPos foot) {
        for (int y = 2; y <= 12; y++) {
            BlockPos p = foot.up(y);
            if (world.getBlockState(p).isSolidBlock(world, p)) {
                return true;
            }
        }
        return false;
    }

    /** The storey a character standing at feet height y is on (0 the ground floor, -1 the first cellar). */
    static int level(int feet, int originY) {
        return Math.round((feet - (originY + 1)) / 5.0f);
    }

    /** Every flight in the town's buildings (and their cellars). */
    public static List<Flight> scan(ServerWorld world, BlockPos origin, TownLayout layout, ScenarioSheet sheet) {
        List<Flight> out = new ArrayList<>();
        if (layout == null || sheet == null || layout.building()) {
            return out;
        }
        for (TownLayout.Site s : layout.sites) {
            ScenarioSheet.Place p = sheet.place(s.id);
            if (p == null || s.outdoor) {
                continue;
            }
            int y1 = origin.getY() - 5 * Math.max(0, p.cellars) - 2;
            int y2 = origin.getY() + 5 * Math.max(1, p.floors) + 2;
            for (int x = origin.getX() + s.x; x < origin.getX() + s.x + s.w; x++) {
                for (int z = origin.getZ() + s.z; z < origin.getZ() + s.z + s.d; z++) {
                    for (int y = y1; y <= y2; y++) {
                        BlockPos at = new BlockPos(x, y, z);
                        BlockState st = world.getBlockState(at);
                        if (!(st.getBlock() instanceof StairsBlock) || st.get(StairsBlock.HALF) != BlockHalf.BOTTOM) {
                            continue;
                        }
                        // The way the run rises, whichever way its steps face (a flight built back to front is a flight).
                        Direction f = null;
                        List<int[]> steps = null;
                        for (Direction r : Direction.Type.HORIZONTAL) {
                            if (StairFix.step(world.getBlockState(at.offset(r.getOpposite()).down()))) {
                                continue; // not the foot of a run this way
                            }
                            List<int[]> run = new ArrayList<>();
                            BlockPos cur = at;
                            while (StairFix.step(world.getBlockState(cur)) && run.size() < 32) {
                                run.add(new int[] { cur.getX(), cur.getY(), cur.getZ() });
                                cur = cur.offset(r).up();
                            }
                            if (run.size() >= MIN_STEPS && (steps == null || run.size() > steps.size())) {
                                f = r;
                                steps = run;
                            }
                        }
                        if (steps == null || !underCeiling(world, at)) {
                            continue;
                        }
                        int[] first = steps.get(0), last = steps.get(steps.size() - 1);
                        Flight fl = new Flight();
                        fl.dir = f.asString();
                        fl.steps = steps;
                        fl.bottom = new int[] { first[0], first[1] + 1, first[2] };
                        fl.top = new int[] { last[0], last[1] + 1, last[2] };
                        fl.from = level(fl.bottom[1], origin.getY());
                        fl.to = level(fl.top[1], origin.getY());
                        if (fl.from == fl.to || beside(out, fl, f)) {
                            continue;
                        }
                        out.add(fl);
                    }
                }
            }
        }
        return out;
    }

    /** A lane of a wide flight already found (the same run, a block to the side). */
    private static boolean beside(List<Flight> found, Flight fl, Direction f) {
        Direction side = f.rotateYClockwise();
        for (Flight o : found) {
            if (!o.dir.equals(fl.dir) || o.steps.size() != fl.steps.size() || o.bottom[1] != fl.bottom[1]) {
                continue;
            }
            int dx = fl.bottom[0] - o.bottom[0], dz = fl.bottom[2] - o.bottom[2];
            int along = dx * f.getOffsetX() + dz * f.getOffsetZ();
            int across = dx * side.getOffsetX() + dz * side.getOffsetZ();
            if (along == 0 && Math.abs(across) <= 3) {
                return true;
            }
        }
        return false;
    }
}

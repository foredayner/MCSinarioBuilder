package com.foredayner.scenario.town;

import com.foredayner.scenario.layout.IndoorPlan;
import com.foredayner.scenario.layout.TownLayout;
import com.foredayner.scenario.sheet.Period;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.PaneBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the shell of a building stage from its floor plan, exactly, by code: the floors (a cellar
 * dug out), the walls of every room (outside walls in the outside material with windows), the
 * doors (a secret one as a false wall), the stairs (with their holes in the floor above) and the roof over the top rooms. The
 * rooms are furnished afterwards, one AI design each. A few hundred blocks per tick.
 */
public final class ShellWork {

    private static final int FLAGS = Block.NOTIFY_LISTENERS | Block.FORCE_STATE;

    /** The materials of a period. */
    record Palette(BlockState outside, BlockState inside, BlockState floor, BlockState roof, BlockState trim, Block door,
                   Block stairs, BlockState window) {
    }

    static Palette palette(String period) {
        return switch (period == null ? "" : period) {
            case Period.WEST_MEDIEVAL -> new Palette(Blocks.STONE_BRICKS.getDefaultState(), Blocks.SPRUCE_PLANKS.getDefaultState(),
                Blocks.SPRUCE_PLANKS.getDefaultState(), Blocks.DARK_OAK_PLANKS.getDefaultState(), Blocks.STRIPPED_DARK_OAK_LOG.getDefaultState(),
                Blocks.SPRUCE_DOOR, Blocks.SPRUCE_STAIRS, Blocks.GLASS_PANE.getDefaultState());
            case Period.WEST_CONTEMPORARY, Period.EAST_CONTEMPORARY -> new Palette(Blocks.WHITE_CONCRETE.getDefaultState(),
                Blocks.SMOOTH_QUARTZ.getDefaultState(), Blocks.BIRCH_PLANKS.getDefaultState(), Blocks.GRAY_CONCRETE.getDefaultState(),
                Blocks.LIGHT_GRAY_CONCRETE.getDefaultState(), Blocks.BIRCH_DOOR, Blocks.QUARTZ_STAIRS, Blocks.GLASS_PANE.getDefaultState());
            case Period.EAST_MEDIEVAL -> new Palette(Blocks.WHITE_TERRACOTTA.getDefaultState(), Blocks.WHITE_TERRACOTTA.getDefaultState(),
                Blocks.OAK_PLANKS.getDefaultState(), Blocks.DEEPSLATE_TILES.getDefaultState(), Blocks.STRIPPED_OAK_LOG.getDefaultState(),
                Blocks.SPRUCE_DOOR, Blocks.OAK_STAIRS, Blocks.WHITE_STAINED_GLASS_PANE.getDefaultState());
            case Period.EAST_MODERN -> new Palette(Blocks.BRICKS.getDefaultState(), Blocks.OAK_PLANKS.getDefaultState(),
                Blocks.OAK_PLANKS.getDefaultState(), Blocks.DEEPSLATE_TILES.getDefaultState(), Blocks.STRIPPED_SPRUCE_LOG.getDefaultState(),
                Blocks.SPRUCE_DOOR, Blocks.SPRUCE_STAIRS, Blocks.GLASS_PANE.getDefaultState());
            case Period.EAST_POSTWAR -> new Palette(Blocks.STONE.getDefaultState(), Blocks.WHITE_TERRACOTTA.getDefaultState(),
                Blocks.OAK_PLANKS.getDefaultState(), Blocks.CYAN_TERRACOTTA.getDefaultState(), Blocks.SMOOTH_STONE.getDefaultState(),
                Blocks.OAK_DOOR, Blocks.OAK_STAIRS, Blocks.GLASS_PANE.getDefaultState());
            default -> new Palette(Blocks.BRICKS.getDefaultState(), Blocks.BIRCH_PLANKS.getDefaultState(),
                Blocks.DARK_OAK_PLANKS.getDefaultState(), Blocks.DEEPSLATE_TILES.getDefaultState(), Blocks.STRIPPED_DARK_OAK_LOG.getDefaultState(),
                Blocks.DARK_OAK_DOOR, Blocks.DARK_OAK_STAIRS, Blocks.GLASS_PANE.getDefaultState());
        };
    }

    private final ServerWorld world;
    private final List<long[]> where = new ArrayList<>();
    private final List<BlockState> what = new ArrayList<>();
    private int next;
    private final BlockPos.Mutable pos = new BlockPos.Mutable();

    public ShellWork(ServerWorld world, BlockPos origin, TownLayout layout, String period) {
        this.world = world;
        plan(origin, layout, palette(period));
    }

    public boolean done() {
        return next >= where.size();
    }

    public int percent() {
        return where.isEmpty() ? 100 : (int) Math.round(100.0 * next / where.size());
    }

    /** Blocks in all (tests). */
    public int size() {
        return where.size();
    }

    public void step(int blocks) {
        for (int n = 0; n < blocks && next < where.size(); n++, next++) {
            long[] p = where.get(next);
            pos.set((int) p[0], (int) p[1], (int) p[2]);
            if (world.getBlockState(pos) != what.get(next)) {
                world.setBlockState(pos, what.get(next), FLAGS);
            }
        }
    }

    private void put(BlockPos origin, int x, int y, int z, BlockState state) {
        where.add(new long[] { origin.getX() + x, y, origin.getZ() + z });
        what.add(state);
    }

    /** The level of the floor blocks of a level (the ground floor's floor is the ground surface). */
    public static int floorY(BlockPos origin, int level) {
        return origin.getY() - 1 + level * IndoorPlan.FLOOR_H;
    }

    private static boolean isWall(TownLayout.Site s, int x, int z) {
        return x == s.x || z == s.z || x == s.x + s.w - 1 || z == s.z + s.d - 1;
    }

    private void plan(BlockPos origin, TownLayout l, Palette pal) {
        int low = Integer.MAX_VALUE, high = Integer.MIN_VALUE;
        for (TownLayout.Site s : l.sites) {
            low = Math.min(low, s.level);
            high = Math.max(high, s.level);
        }
        if (l.sites.isEmpty()) {
            return;
        }
        BlockState air = Blocks.AIR.getDefaultState();
        for (int level = low; level <= high; level++) {
            int fy = floorY(origin, level);
            for (TownLayout.Site s : l.sites) {
                if (s.level != level) {
                    continue;
                }
                for (int x = s.x; x < s.x + s.w; x++) {
                    for (int z = s.z; z < s.z + s.d; z++) {
                        // The floor (a basement's earth is dug out above it).
                        put(origin, x, fy, z, level < 0 ? Blocks.STONE_BRICKS.getDefaultState() : pal.floor());
                        boolean wall = isWall(s, x, z);
                        boolean outside = wall && outside(l, level, x, z);
                        for (int y = fy + 1; y < fy + IndoorPlan.FLOOR_H; y++) {
                            if (!wall) {
                                put(origin, x, y, z, air);
                            } else if (outside && corner(l, level, x, z)) {
                                put(origin, x, y, z, pal.trim());
                            } else {
                                // A cellar's walls are all of stone.
                                put(origin, x, y, z, level < 0 ? Blocks.STONE_BRICKS.getDefaultState() : outside ? pal.outside() : pal.inside());
                            }
                        }
                        // Windows in the outside walls, every third block, two high.
                        if (outside && level >= 0 && !corner(l, level, x, z) && Math.floorMod(x + z, 3) == 1 && !nearDoor(l, level, x, z)) {
                            put(origin, x, fy + 2, z, pane(pal.window(), l, level, x, z));
                            put(origin, x, fy + 3, z, pane(pal.window(), l, level, x, z));
                        }
                    }
                }
            }
        }
        // Ceilings and roof: over a column with no room above, the top of the room; a low parapet round the edge.
        for (TownLayout.Site s : l.sites) {
            int top = floorY(origin, s.level + 1);
            for (int x = s.x; x < s.x + s.w; x++) {
                for (int z = s.z; z < s.z + s.d; z++) {
                    if (covered(l, s.level + 1, x, z)) {
                        continue;
                    }
                    put(origin, x, top, z, s.level < 0 ? pal.floor() : pal.roof());
                    if (s.level >= 0 && edgeOfRoof(l, s.level, x, z)) {
                        put(origin, x, top + 1, z, pal.trim());
                    }
                }
            }
        }
        // Doors, after the walls.
        for (TownLayout.Door d : l.doors) {
            int fy = floorY(origin, d.level);
            if (d.secret) {
                // A false wall: plain stone in a cellar (a patch newer than the bricks), the room wall above ground.
                BlockState fake = d.level < 0 ? Blocks.STONE.getDefaultState() : pal.inside();
                put(origin, d.x, fy + 1, d.z, fake);
                put(origin, d.x, fy + 2, d.z, fake);
                continue;
            }
            boolean vertical = verticalWall(l, d);
            Direction facing = vertical ? Direction.EAST : Direction.SOUTH;
            BlockState lower = pal.door().getDefaultState().with(DoorBlock.FACING, facing).with(DoorBlock.HALF, DoubleBlockHalf.LOWER);
            BlockState upper = lower.with(DoorBlock.HALF, DoubleBlockHalf.UPPER);
            put(origin, d.x, fy + 1, d.z, lower);
            put(origin, d.x, fy + 2, d.z, upper);
            if (d.entrance) {
                // A step of floor outside the way in.
                int[] out = outsideOf(l, d);
                if (out != null) {
                    put(origin, out[0], fy, out[1], pal.floor());
                    put(origin, out[0], fy + 1, out[1], air);
                    put(origin, out[0], fy + 2, out[1], air);
                }
            }
        }
        // Stairs: steps rising toward their direction, filled under, and the hole in the floor above.
        for (TownLayout.Stair st : l.stairs) {
            int fy = floorY(origin, st.level);
            int[] step = stepOf(st.dir);
            Direction up = switch (st.dir) {
                case "east" -> Direction.EAST;
                case "south" -> Direction.SOUTH;
                case "west" -> Direction.WEST;
                default -> Direction.NORTH;
            };
            BlockState stair = pal.stairs().getDefaultState().with(StairsBlock.FACING, up);
            for (int i = 0; i < IndoorPlan.STAIR_RUN; i++) {
                int x = st.x + step[0] * i, z = st.z + step[1] * i;
                for (int y = fy + 1; y < fy + 1 + i; y++) {
                    put(origin, x, y, z, pal.floor());
                }
                put(origin, x, fy + 1 + i, z, stair);
                if (i >= 1) {
                    put(origin, x, fy + IndoorPlan.FLOOR_H, z, air);
                }
            }
            // The last step is the landing itself, set in the floor above: no full block to jump at the top.
            put(origin, st.x + step[0] * IndoorPlan.STAIR_RUN, fy + IndoorPlan.FLOOR_H, st.z + step[1] * IndoorPlan.STAIR_RUN, stair);
        }
    }

    private static int[] stepOf(String dir) {
        return switch (dir == null ? "" : dir) {
            case "east" -> new int[] { 1, 0 };
            case "south" -> new int[] { 0, 1 };
            case "west" -> new int[] { -1, 0 };
            default -> new int[] { 0, -1 };
        };
    }

    private static boolean covered(TownLayout l, int level, int x, int z) {
        for (TownLayout.Site s : l.sites) {
            if (s.level == level && x >= s.x && z >= s.z && x < s.x + s.w && z < s.z + s.d) {
                return true;
            }
        }
        return false;
    }

    /** A wall cell with the outside next to it. */
    private static boolean outside(TownLayout l, int level, int x, int z) {
        return !covered(l, level, x - 1, z) || !covered(l, level, x + 1, z) || !covered(l, level, x, z - 1) || !covered(l, level, x, z + 1);
    }

    /** An outside corner of the floor's outline. */
    private static boolean corner(TownLayout l, int level, int x, int z) {
        boolean ew = !covered(l, level, x - 1, z) || !covered(l, level, x + 1, z);
        boolean ns = !covered(l, level, x, z - 1) || !covered(l, level, x, z + 1);
        return ew && ns;
    }

    private static boolean edgeOfRoof(TownLayout l, int level, int x, int z) {
        return outside(l, level, x, z);
    }

    private static boolean nearDoor(TownLayout l, int level, int x, int z) {
        for (TownLayout.Door d : l.doors) {
            if (d.level == level && Math.abs(d.x - x) + Math.abs(d.z - z) <= 1) {
                return true;
            }
        }
        return false;
    }

    /** The door's wall runs north-south (the door's room sides are east and west). */
    private static boolean verticalWall(TownLayout l, TownLayout.Door d) {
        for (TownLayout.Site s : l.sites) {
            if (s.level == d.level && d.z > s.z && d.z < s.z + s.d - 1 && (d.x == s.x || d.x == s.x + s.w - 1)) {
                return true;
            }
        }
        return false;
    }

    private static int[] outsideOf(TownLayout l, TownLayout.Door d) {
        for (int[] o : new int[][] { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } }) {
            if (!covered(l, d.level, d.x + o[0], d.z + o[1])) {
                return new int[] { d.x + o[0], d.z + o[1] };
            }
        }
        return null;
    }

    /** A window pane joined along its wall. */
    private static BlockState pane(BlockState pane, TownLayout l, int level, int x, int z) {
        boolean alongX = covered(l, level, x - 1, z) && covered(l, level, x + 1, z) && !(covered(l, level, x, z - 1) && covered(l, level, x, z + 1));
        return alongX ? pane.with(PaneBlock.EAST, true).with(PaneBlock.WEST, true)
            : pane.with(PaneBlock.NORTH, true).with(PaneBlock.SOUTH, true);
    }
}

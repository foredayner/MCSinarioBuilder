package com.foredayner.scenario.town;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CropBlock;
import net.minecraft.block.FarmlandBlock;
import net.minecraft.block.LanternBlock;
import net.minecraft.block.LeavesBlock;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/**
 * Lays the town's ground from a {@link GroundPlan}, a few columns per tick: clears everything
 * above the ground level, lays the surface (grass, gravel streets, dirt paths, sea, sand, fields,
 * paving, dock decks) with soil under it, then adds trees, crops, flowers, gravestones, fences and
 * lanterns, and finally joins fences and walls to their neighbours.
 * <p>
 * The ground surface is {@code y0 - 1}; buildings stand from {@code y0} up.
 */
public final class GroundWork {

    /** How high above the ground everything is cleared (hills, trees). */
    public static final int CLEAR = 48;
    /** How deep holes (caves, ravines) under the ground are filled. */
    public static final int FILL = 12;
    private static final int FLAGS = Block.NOTIFY_LISTENERS | Block.FORCE_STATE;

    private enum Phase { PAINT, DECOR, CONNECT, DONE }

    private final ServerWorld world;
    private final int ox, y0, oz;
    private final GroundPlan plan;
    private final int x0, z0, x1, z1;
    private Phase phase = Phase.PAINT;
    private int next;
    private final BlockPos.Mutable pos = new BlockPos.Mutable();

    /**
     * @param origin world position of plan cell (0, 0) at the level buildings stand on
     * @param x0 first plan column (inclusive) to work on, and so on (a whole town or one site)
     */
    public GroundWork(ServerWorld world, BlockPos origin, GroundPlan plan, int x0, int z0, int x1, int z1) {
        this.world = world;
        this.ox = origin.getX();
        this.y0 = origin.getY();
        this.oz = origin.getZ();
        this.plan = plan;
        this.x0 = Math.max(0, x0);
        this.z0 = Math.max(0, z0);
        this.x1 = Math.min(plan.size - 1, x1);
        this.z1 = Math.min(plan.size - 1, z1);
    }

    public boolean done() {
        return phase == Phase.DONE;
    }

    /** 0-100. */
    public int percent() {
        int cols = (x1 - x0 + 1) * (z1 - z0 + 1);
        int phaseIndex = Math.min(phase.ordinal(), 3);
        double within = cols == 0 ? 1 : next / (double) cols;
        // Painting is most of the work.
        double[] start = { 0, 0.75, 0.95, 1 };
        double[] span = { 0.75, 0.2, 0.05, 0 };
        return (int) Math.round(100 * (start[phaseIndex] + span[phaseIndex] * within));
    }

    /** Do up to {@code columns} columns of the current phase. */
    public void step(int columns) {
        if (phase == Phase.DONE) {
            return;
        }
        int w = x1 - x0 + 1, total = w * (z1 - z0 + 1);
        int budget = phase == Phase.PAINT ? columns : columns * 4;
        for (int n = 0; n < budget && next < total; n++, next++) {
            int x = x0 + next % w, z = z0 + next / w;
            switch (phase) {
                case PAINT -> paint(x, z);
                case DECOR -> decorate(x, z);
                case CONNECT -> connect(x, z);
                default -> {
                }
            }
        }
        if (next >= total) {
            next = 0;
            phase = Phase.values()[phase.ordinal() + 1];
        }
    }

    private void set(int x, int y, int z, BlockState state) {
        if (y < world.getBottomY()) {
            return; // a shallow world (superflat): fill only down to its bottom
        }
        pos.set(ox + x, y, oz + z);
        if (world.getBlockState(pos) != state) {
            world.setBlockState(pos, state, FLAGS);
        }
    }

    private BlockState get(int x, int y, int z) {
        return world.getBlockState(pos.set(ox + x, y, oz + z));
    }

    // ------------------------------------------------------------------ ground

    private void paint(int x, int z) {
        for (int y = y0; y < y0 + CLEAR; y++) {
            if (!get(x, y, z).isAir()) {
                set(x, y, z, Blocks.AIR.getDefaultState());
            }
        }
        GroundPlan.Cell cell = plan.cells[x][z];
        String kind = plan.outdoorKind[x][z];
        BlockState top;
        BlockState soil = Blocks.DIRT.getDefaultState();
        int depth = 3; // soil under the surface
        switch (cell) {
            case ROAD -> {
                top = roadEdge(x, z) ? Blocks.COBBLESTONE.getDefaultState() : Blocks.GRAVEL.getDefaultState();
                soil = Blocks.COBBLESTONE.getDefaultState(); // gravel must not fall into holes
            }
            case PATH -> top = Blocks.DIRT_PATH.getDefaultState();
            case WATER -> {
                for (int y = y0 - 4; y < y0; y++) {
                    set(x, y, z, Blocks.WATER.getDefaultState());
                }
                fillBelow(x, z, y0 - 5, Blocks.SAND.getDefaultState(), Blocks.STONE.getDefaultState());
                return;
            }
            case BEACH -> {
                top = Blocks.SAND.getDefaultState();
                soil = Blocks.SANDSTONE.getDefaultState();
            }
            case FIELD -> top = x % 9 == 4 ? Blocks.WATER.getDefaultState()
                : Blocks.FARMLAND.getDefaultState().with(FarmlandBlock.MOISTURE, 7);
            case SQUARE -> top = paving(x, z);
            case WOODS -> top = GroundPlan.noise(x, z, 11) < 25 ? Blocks.PODZOL.getDefaultState() : Blocks.GRASS_BLOCK.getDefaultState();
            case CEMETERY -> top = GroundPlan.noise(x, z, 12) < 30 ? Blocks.COARSE_DIRT.getDefaultState() : Blocks.GRASS_BLOCK.getDefaultState();
            case OUTDOOR -> {
                switch (kind == null ? "" : kind) {
                    case "dock", "beach" -> {
                        if (plan.under[x][z] == GroundPlan.Cell.WATER) {
                            set(x, y0 - 1, z, Blocks.SPRUCE_PLANKS.getDefaultState());
                            BlockState below = edge(x, z) && (x + z) % 3 == 0 ? Blocks.SPRUCE_LOG.getDefaultState() : Blocks.WATER.getDefaultState();
                            for (int y = y0 - 4; y < y0 - 1; y++) {
                                set(x, y, z, below);
                            }
                            fillBelow(x, z, y0 - 5, Blocks.SAND.getDefaultState(), Blocks.STONE.getDefaultState());
                            return;
                        }
                        top = Blocks.SPRUCE_PLANKS.getDefaultState();
                    }
                    case "cemetery" -> top = GroundPlan.noise(x, z, 12) < 30 ? Blocks.COARSE_DIRT.getDefaultState() : Blocks.GRASS_BLOCK.getDefaultState();
                    case "woods" -> top = GroundPlan.noise(x, z, 11) < 25 ? Blocks.PODZOL.getDefaultState() : Blocks.GRASS_BLOCK.getDefaultState();
                    case "square" -> top = paving(x, z);
                    default -> top = Blocks.GRASS_BLOCK.getDefaultState();
                }
            }
            default -> top = Blocks.GRASS_BLOCK.getDefaultState();
        }
        set(x, y0 - 1, z, top);
        for (int y = y0 - 1 - depth; y < y0 - 1; y++) {
            set(x, y, z, soil);
        }
        fillBelow(x, z, y0 - 2 - depth, Blocks.STONE.getDefaultState(), Blocks.STONE.getDefaultState());
    }

    /** Fill air and fluids under the ground (caves, ravines, the sea floor) down to the fill depth. */
    private void fillBelow(int x, int z, int from, BlockState first, BlockState rest) {
        for (int y = from; y >= Math.max(world.getBottomY(), y0 - FILL); y--) {
            BlockState s = get(x, y, z);
            if (!s.isAir() && s.getFluidState().isEmpty()) {
                break;
            }
            set(x, y, z, y == from ? first : rest);
        }
    }

    private BlockState paving(int x, int z) {
        return (x + z) % 2 == 0 ? Blocks.STONE_BRICKS.getDefaultState() : Blocks.POLISHED_ANDESITE.getDefaultState();
    }

    private boolean roadEdge(int x, int z) {
        for (int[] d : new int[][] { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } }) {
            GroundPlan.Cell c = plan.at(x + d[0], z + d[1]);
            if (c != GroundPlan.Cell.ROAD && c != GroundPlan.Cell.PATH) {
                return true;
            }
        }
        return false;
    }

    /** At the edge of its outdoor place (a neighbour belongs to something else). */
    private boolean edge(int x, int z) {
        String id = plan.outdoor[x][z];
        for (int[] d : new int[][] { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } }) {
            int nx = x + d[0], nz = z + d[1];
            String other = nx < 0 || nz < 0 || nx >= plan.size || nz >= plan.size ? null : plan.outdoor[nx][nz];
            if (id == null || !id.equals(other)) {
                return true;
            }
        }
        return false;
    }

    /** Next to a street (the gate side of a walled place). */
    private boolean byStreet(int x, int z) {
        for (int[] d : new int[][] { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } }) {
            GroundPlan.Cell c = plan.at(x + d[0], z + d[1]);
            if (c == GroundPlan.Cell.ROAD || c == GroundPlan.Cell.PATH) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ on the ground

    private void decorate(int x, int z) {
        GroundPlan.Cell cell = plan.cells[x][z];
        String kind = plan.outdoorKind[x][z];
        if (cell == GroundPlan.Cell.WOODS || cell == GroundPlan.Cell.OUTDOOR && "woods".equals(kind)) {
            if (treeHere(x, z, 5, cell == GroundPlan.Cell.WOODS ? 85 : 60)) {
                tree(x, z, GroundPlan.noise(x, z, 13) < 55);
            } else if (GroundPlan.noise(x, z, 14) < 10) {
                setIfAir(x, y0, z, Blocks.FERN.getDefaultState());
            } else if (GroundPlan.noise(x, z, 14) < 16) {
                setIfAir(x, y0, z, Blocks.SHORT_GRASS.getDefaultState());
            }
        } else if (cell == GroundPlan.Cell.GRASS) {
            if (lampHere(x, z)) {
                lamp(x, z);
            } else if (GroundPlan.noise(x, z, 15) < 7) {
                set(x, y0, z, Blocks.SHORT_GRASS.getDefaultState());
            } else if (GroundPlan.noise(x, z, 15) < 8) {
                set(x, y0, z, flower(x, z));
            } else if (treeHere(x, z, 13, 25)) {
                tree(x, z, false);
            }
        } else if (cell == GroundPlan.Cell.GARDEN) {
            int n = GroundPlan.noise(x, z, 16);
            if (lampHere(x, z)) {
                lamp(x, z);
            } else if (GroundPlan.noise(x, z, 17) < 3) {
                set(x, y0, z, Blocks.OAK_LEAVES.getDefaultState().with(LeavesBlock.PERSISTENT, true));
            } else if (n < 18) {
                set(x, y0, z, flower(x, z));
            } else if (n < 35) {
                set(x, y0, z, Blocks.SHORT_GRASS.getDefaultState());
            }
        } else if (cell == GroundPlan.Cell.FIELD) {
            if (x % 9 != 4) {
                set(x, y0, z, Blocks.WHEAT.getDefaultState().with(CropBlock.AGE, 3 + GroundPlan.noise(x, z, 18) % 5));
            }
        } else if (cell == GroundPlan.Cell.CEMETERY) {
            grave(x, z);
        } else if (cell == GroundPlan.Cell.OUTDOOR && "cemetery".equals(kind)) {
            if (edge(x, z)) {
                if (!byStreet(x, z)) {
                    set(x, y0, z, Blocks.COBBLESTONE_WALL.getDefaultState());
                }
            } else {
                grave(x, z);
            }
        } else if (cell == GroundPlan.Cell.OUTDOOR && ("dock".equals(kind) || "beach".equals(kind))) {
            if (edge(x, z) && plan.under[x][z] == GroundPlan.Cell.WATER && !byStreet(x, z)) {
                set(x, y0, z, Blocks.SPRUCE_FENCE.getDefaultState());
                if ((x + z) % 6 == 0) {
                    set(x, y0 + 1, z, Blocks.SPRUCE_FENCE.getDefaultState());
                    set(x, y0 + 2, z, Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING, false));
                }
            }
        }
    }

    private void setIfAir(int x, int y, int z, BlockState state) {
        if (get(x, y, z).isAir()) {
            set(x, y, z, state);
        }
    }

    private BlockState flower(int x, int z) {
        return switch (GroundPlan.noise(x, z, 19) % 5) {
            case 0 -> Blocks.POPPY.getDefaultState();
            case 1 -> Blocks.DANDELION.getDefaultState();
            case 2 -> Blocks.AZURE_BLUET.getDefaultState();
            case 3 -> Blocks.OXEYE_DAISY.getDefaultState();
            default -> Blocks.CORNFLOWER.getDefaultState();
        };
    }

    private void grave(int x, int z) {
        if (x % 3 == 1 && z % 4 == 1 && GroundPlan.noise(x, z, 20) < 85) {
            int n = GroundPlan.noise(x, z, 21);
            set(x, y0, z, n < 40 ? Blocks.STONE_BRICK_WALL.getDefaultState()
                : n < 75 ? Blocks.ANDESITE_WALL.getDefaultState() : Blocks.MOSSY_COBBLESTONE_WALL.getDefaultState());
        }
    }

    /** One tree per {@code cell} x {@code cell} square at a fixed spot in it, with the given chance (0-100). */
    private boolean treeHere(int x, int z, int cell, int chance) {
        int cx = Math.floorDiv(x, cell), cz = Math.floorDiv(z, cell);
        int tx = cx * cell + 1 + GroundPlan.noise(cx, cz, 22) % Math.max(1, cell - 2);
        int tz = cz * cell + 1 + GroundPlan.noise(cx, cz, 23) % Math.max(1, cell - 2);
        if (x != tx || z != tz || GroundPlan.noise(cx, cz, 24) >= chance) {
            return false;
        }
        // Keep the crown off lots and streets.
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                GroundPlan.Cell c = plan.at(x + dx, z + dz);
                if (c == GroundPlan.Cell.LOT || c == GroundPlan.Cell.ROAD || c == GroundPlan.Cell.PATH || c == GroundPlan.Cell.WATER
                    || c == GroundPlan.Cell.OUTDOOR && !"woods".equals(plan.outdoorKind[x + dx][z + dz])) {
                    return false;
                }
            }
        }
        return true;
    }

    private void tree(int x, int z, boolean spruce) {
        int height = 5 + GroundPlan.noise(x, z, 25) % 3;
        BlockState log = (spruce ? Blocks.SPRUCE_LOG : Blocks.OAK_LOG).getDefaultState();
        BlockState leaves = (spruce ? Blocks.SPRUCE_LEAVES : Blocks.OAK_LEAVES).getDefaultState().with(LeavesBlock.PERSISTENT, true);
        for (int y = 0; y < height; y++) {
            set(x, y0 + y, z, log);
        }
        if (spruce) {
            // A cone: wide at the bottom, a point on top.
            for (int y = 2; y <= height; y++) {
                int r = y >= height - 1 ? (y == height ? 0 : 1) : (y % 2 == 0 ? 2 : 1);
                for (int dx = -r; dx <= r; dx++) {
                    for (int dz = -r; dz <= r; dz++) {
                        if ((dx != 0 || dz != 0 || y == height) && Math.abs(dx) + Math.abs(dz) <= r + 1) {
                            setIfAir(x + dx, y0 + y, z + dz, leaves);
                        }
                    }
                }
            }
        } else {
            for (int y = height - 3; y <= height; y++) {
                int r = y >= height - 1 ? 1 : 2;
                for (int dx = -r; dx <= r; dx++) {
                    for (int dz = -r; dz <= r; dz++) {
                        boolean corner = Math.abs(dx) == r && Math.abs(dz) == r;
                        if (!corner || GroundPlan.noise(x + dx, z + dz, y) < 40) {
                            setIfAir(x + dx, y0 + y, z + dz, leaves);
                        }
                    }
                }
            }
        }
    }

    /** Street lamps beside main roads, every so often. */
    private boolean lampHere(int x, int z) {
        boolean byRoad = false;
        for (int[] d : new int[][] { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } }) {
            byRoad |= plan.at(x + d[0], z + d[1]) == GroundPlan.Cell.ROAD;
        }
        return byRoad && Math.floorMod(x * 7 + z * 13, 17) == 0;
    }

    private void lamp(int x, int z) {
        set(x, y0, z, Blocks.COBBLESTONE_WALL.getDefaultState());
        set(x, y0 + 1, z, Blocks.SPRUCE_FENCE.getDefaultState());
        set(x, y0 + 2, z, Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING, false));
    }

    // ------------------------------------------------------------------ joining

    /** Fences and walls placed without neighbour updates stand alone; join them now. */
    private void connect(int x, int z) {
        for (int y = y0; y < y0 + 2; y++) {
            BlockState s = get(x, y, z);
            if (s.isIn(BlockTags.WALLS) || s.isIn(BlockTags.FENCES)) {
                BlockState joined = Block.postProcessState(s, world, pos.set(ox + x, y, oz + z));
                if (joined != s) {
                    world.setBlockState(pos, joined, FLAGS);
                }
            }
        }
    }
}

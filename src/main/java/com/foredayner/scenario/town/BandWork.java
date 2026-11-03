package com.foredayner.scenario.town;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LeavesBlock;
import net.minecraft.block.PillarBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.Heightmap;

/**
 * Lays the {@link OuterBand} around a town, a few columns per tick, after the town's ground: each
 * column's surface goes from the town's level at the town's edge to the land's own height just
 * outside the ring (plus the hills of a closed stage or of the woods), with the land above it
 * cleared and holes under it filled; then the trees.
 */
public final class BandWork {

    private static final int FLAGS = Block.NOTIFY_LISTENERS | Block.FORCE_STATE;
    /** Most blocks cleared above, and filled below, a column's new surface. */
    private static final int CLEAR_ABOVE = 64;
    private static final int FILL_BELOW = 40;

    private final ServerWorld world;
    private final int ox, y0, oz;
    private final OuterBand band;
    private final int size;
    private final boolean modern;
    private final boolean unpaved;
    private final int[] cols;
    private int next;
    private boolean trees;
    private final BlockPos.Mutable pos = new BlockPos.Mutable();

    public BandWork(ServerWorld world, BlockPos origin, GroundPlan plan, OuterBand band, String period) {
        this.world = world;
        this.ox = origin.getX();
        this.y0 = origin.getY();
        this.oz = origin.getZ();
        this.band = band;
        this.size = plan.size;
        this.modern = com.foredayner.scenario.sheet.Period.contemporary(period);
        // Earth roads, as in the town (GroundWork).
        this.unpaved = com.foredayner.scenario.sheet.Period.medieval(period) || com.foredayner.scenario.sheet.Period.EAST_POSTWAR.equals(period);
        int w = band.width();
        int n = 0;
        int[] list = new int[((size + 2 * w) * (size + 2 * w) - size * size) * 2];
        for (int x = -w; x < size + w; x++) {
            for (int z = -w; z < size + w; z++) {
                if (band.contains(x, z)) {
                    list[n++] = x;
                    list[n++] = z;
                }
            }
        }
        this.cols = java.util.Arrays.copyOf(list, n);
    }

    public boolean done() {
        return trees && next >= cols.length;
    }

    /** 0-100. */
    public int percent() {
        double part = cols.length == 0 ? 1 : next / (double) cols.length;
        return (int) Math.round(100 * (trees ? 0.85 + 0.15 * part : 0.85 * part));
    }

    public void step(int columns) {
        for (int n = 0; n < columns && next < cols.length; n++, next += 2) {
            int x = cols[next], z = cols[next + 1];
            if (trees) {
                decorate(x, z);
            } else {
                paint(x, z);
            }
        }
        if (next >= cols.length && !trees) {
            trees = true;
            next = 0;
        }
    }

    /** The surface level (y of the top block) of a column of the ring. */
    int surface(int x, int z, OuterBand.Column c) {
        if (c.kind() == OuterBand.Kind.WATER) {
            return y0 - 1;
        }
        int[] out = band.outside(x, z);
        int wx = ox + out[0], wz = oz + out[1];
        world.getChunk(wx >> 4, wz >> 4);
        int land = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, wx, wz) - 1;
        // The ground, not the tree trunk or the plant standing on it.
        for (int k = 0; k < 24 && land > world.getBottomY(); k++) {
            BlockState s = world.getBlockState(pos.set(wx, land, wz));
            boolean standing = s.isIn(net.minecraft.registry.tag.BlockTags.LOGS) || s.isIn(net.minecraft.registry.tag.BlockTags.LEAVES)
                || s.isReplaceable() && s.getFluidState().isEmpty();
            if (!standing) {
                break;
            }
            land--;
        }
        // Water or the void beyond: blend to the town's level.
        if (land < world.getBottomY() + 2) {
            land = y0 - 1;
        }
        int blend = (int) Math.round((y0 - 1) + (land - (y0 - 1)) * c.t());
        return blend + c.rise();
    }

    private void set(int x, int y, int z, BlockState state) {
        if (y < world.getBottomY() || y > world.getTopYInclusive()) {
            return;
        }
        pos.set(ox + x, y, oz + z);
        if (world.getBlockState(pos) != state) {
            world.setBlockState(pos, state, FLAGS);
        }
    }

    private BlockState get(int x, int y, int z) {
        return world.getBlockState(pos.set(ox + x, y, oz + z));
    }

    private void paint(int x, int z) {
        OuterBand.Column c = band.at(x, z);
        int top = surface(x, z, c);
        int high = Math.max(top, y0) + CLEAR_ABOVE;
        for (int y = top + 1; y <= high; y++) {
            if (!get(x, y, z).isAir()) {
                set(x, y, z, Blocks.AIR.getDefaultState());
            }
        }
        BlockState surface;
        BlockState soil = Blocks.DIRT.getDefaultState();
        switch (c.kind()) {
            case WATER -> {
                for (int y = top - 3; y <= top; y++) {
                    set(x, y, z, Blocks.WATER.getDefaultState());
                }
                fillBelow(x, z, top - 4, Blocks.SAND.getDefaultState());
                return;
            }
            case ROAD, BARRIER -> {
                if (modern) {
                    surface = Blocks.GRAY_CONCRETE.getDefaultState();
                    soil = Blocks.STONE.getDefaultState();
                } else if (unpaved) {
                    surface = GroundPlan.noise(x, z, 66) < 15 ? Blocks.COARSE_DIRT.getDefaultState() : Blocks.DIRT_PATH.getDefaultState();
                } else {
                    surface = Blocks.GRAVEL.getDefaultState();
                    soil = Blocks.COBBLESTONE.getDefaultState();
                }
            }
            case PATH -> surface = modern ? Blocks.POLISHED_ANDESITE.getDefaultState() : Blocks.DIRT_PATH.getDefaultState();
            case WOODS -> {
                int n = GroundPlan.noise(x, z, 67);
                surface = c.rise() > 10 && n < 12 ? Blocks.STONE.getDefaultState()
                    : n < 30 ? Blocks.PODZOL.getDefaultState() : n < 36 ? Blocks.COARSE_DIRT.getDefaultState() : Blocks.GRASS_BLOCK.getDefaultState();
            }
            default -> surface = Blocks.GRASS_BLOCK.getDefaultState();
        }
        set(x, top, z, surface);
        for (int y = top - 3; y < top; y++) {
            set(x, y, z, soil);
        }
        fillBelow(x, z, top - 4, Blocks.STONE.getDefaultState());
        if (c.kind() == OuterBand.Kind.BARRIER) {
            // A rockfall and fallen trunks across the road.
            int n = GroundPlan.noise(x, z, 68);
            set(x, top + 1, z, n < 45 ? Blocks.COBBLESTONE.getDefaultState() : n < 75 ? Blocks.MOSSY_COBBLESTONE.getDefaultState()
                : Blocks.OAK_LOG.getDefaultState().with(PillarBlock.AXIS, Direction.Axis.X));
            set(x, top + 2, z, n < 60 ? Blocks.STONE.getDefaultState() : Blocks.MOSSY_COBBLESTONE.getDefaultState());
            if (n < 30) {
                set(x, top + 3, z, Blocks.COBBLESTONE.getDefaultState());
            }
        }
    }

    /** Fill air and fluids under the new surface (valleys, caves) with stone, so nothing floats. */
    private void fillBelow(int x, int z, int from, BlockState first) {
        for (int y = from; y >= Math.max(world.getBottomY(), from - FILL_BELOW); y--) {
            BlockState s = get(x, y, z);
            if (!s.isAir() && s.getFluidState().isEmpty()) {
                break;
            }
            set(x, y, z, y == from ? first : Blocks.STONE.getDefaultState());
        }
    }

    private void decorate(int x, int z) {
        OuterBand.Column c = band.at(x, z);
        int top = surface(x, z, c);
        if (c.tree()) {
            tree(x, top + 1, z, c.spruce());
        } else if (c.kind() == OuterBand.Kind.MEADOW || c.kind() == OuterBand.Kind.WOODS) {
            int n = GroundPlan.noise(x, z, 69);
            if (n < (c.kind() == OuterBand.Kind.WOODS ? 8 : 10)) {
                setIfAir(x, top + 1, z, c.kind() == OuterBand.Kind.WOODS ? Blocks.FERN.getDefaultState() : Blocks.SHORT_GRASS.getDefaultState());
            } else if (n < 11 && c.kind() == OuterBand.Kind.MEADOW) {
                setIfAir(x, top + 1, z, Blocks.DANDELION.getDefaultState());
            }
        }
    }

    private void setIfAir(int x, int y, int z, BlockState state) {
        if (get(x, y, z).isAir()) {
            set(x, y, z, state);
        }
    }

    private void tree(int x, int base, int z, boolean spruce) {
        int height = (spruce ? 7 : 5) + GroundPlan.noise(x, z, 65) % 3;
        BlockState log = (spruce ? Blocks.SPRUCE_LOG : Blocks.OAK_LOG).getDefaultState();
        BlockState leaves = (spruce ? Blocks.SPRUCE_LEAVES : Blocks.OAK_LEAVES).getDefaultState().with(LeavesBlock.PERSISTENT, true);
        for (int y = 0; y < height; y++) {
            set(x, base + y, z, log);
        }
        if (spruce) {
            for (int y = 2; y <= height; y++) {
                int r = y >= height - 1 ? (y == height ? 0 : 1) : (y % 2 == 0 ? 2 : 1);
                for (int dx = -r; dx <= r; dx++) {
                    for (int dz = -r; dz <= r; dz++) {
                        if ((dx != 0 || dz != 0 || y == height) && Math.abs(dx) + Math.abs(dz) <= r + 1) {
                            setIfAir(x + dx, base + y, z + dz, leaves);
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
                            setIfAir(x + dx, base + y, z + dz, leaves);
                        }
                    }
                }
            }
        }
    }
}

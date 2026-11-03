package com.foredayner.scenario.town;

/**
 * The ring of land around the town's ground (the "바깥 띠"), so the stage does not end in a cut:
 * it carries on what the town's edge shows (a road leaving the map runs on, the sea stays sea,
 * woods and the hill behind the village rise into wooded slopes, open ground turns into meadow
 * and then woods) and blends into the land around at its outer edge.
 *
 * <p>A closed stage ({@link #closed}) ends in steep wooded hills all round instead, and the roads
 * that leave are blocked by a rockfall: the investigators cannot simply walk away. Pure, so it can
 * be tested without a world; {@link BandWork} lays it.
 */
public final class OuterBand {

    /** How wide the ring is (blocks). */
    public static final int WIDTH = 28;
    /** How much higher than the blend the closed stage's hills rise at the outer edge. */
    public static final int CLOSED_HILL = 16;
    /** How much the woods (the hill behind a village) rise on an open stage. */
    public static final int WOODS_HILL = 9;

    public enum Kind { MEADOW, WOODS, WATER, ROAD, PATH, BARRIER }

    /**
     * One column of the ring: what it is, how far out (0 at the town's edge, 1 at the ring's outer
     * edge), how many blocks it rises above the blend, and whether a tree grows here (spruce or not).
     */
    public record Column(Kind kind, double t, int rise, boolean tree, boolean spruce) {
    }

    private final GroundPlan inner;
    private final boolean closed;
    private final int width;

    public OuterBand(GroundPlan inner, boolean closed) {
        this(inner, closed, WIDTH);
    }

    public OuterBand(GroundPlan inner, boolean closed, int width) {
        this.inner = inner;
        this.closed = closed;
        this.width = width;
    }

    public int width() {
        return width;
    }

    public boolean closed() {
        return closed;
    }

    /** Whether (x, z) (town coordinates) is in the ring, not in the town. */
    public boolean contains(int x, int z) {
        boolean inTown = x >= 0 && z >= 0 && x < inner.size && z < inner.size;
        return !inTown && x >= -width && z >= -width && x < inner.size + width && z < inner.size + width;
    }

    /** Blocks out from the town's edge (1 = just outside). */
    public int distance(int x, int z) {
        int dx = x < 0 ? -x : x >= inner.size ? x - inner.size + 1 : 0;
        int dz = z < 0 ? -z : z >= inner.size ? z - inner.size + 1 : 0;
        return Math.max(dx, dz);
    }

    /** The point just outside the ring, straight out from (x, z): the land there is left as it is. */
    public int[] outside(int x, int z) {
        int ox = x < 0 ? -width - 1 : x >= inner.size ? inner.size + width : x;
        int oz = z < 0 ? -width - 1 : z >= inner.size ? inner.size + width : z;
        return new int[] { ox, oz };
    }

    public Column at(int x, int z) {
        double t = distance(x, z) / (double) width;
        int ex = Math.max(0, Math.min(inner.size - 1, x));
        int ez = Math.max(0, Math.min(inner.size - 1, z));
        GroundPlan.Cell edge = inner.at(ex, ez);
        String edgeKind = inner.outdoorKind[ex][ez];
        Kind kind;
        if (edge == GroundPlan.Cell.WATER) {
            kind = Kind.WATER;
        } else if (edge == GroundPlan.Cell.ROAD || edge == GroundPlan.Cell.PATH) {
            // A road leaving the town runs on; on a closed stage a rockfall blocks it in the hills.
            if (closed && t > 0.68) {
                kind = t <= 0.78 ? Kind.BARRIER : Kind.WOODS;
            } else {
                kind = edge == GroundPlan.Cell.PATH ? Kind.PATH : Kind.ROAD;
            }
        } else if (edge == GroundPlan.Cell.WOODS || edge == GroundPlan.Cell.OUTDOOR && "woods".equals(edgeKind)) {
            kind = Kind.WOODS;
        } else {
            kind = t > 0.6 || closed && t > 0.45 ? Kind.WOODS : Kind.MEADOW;
        }
        int rise = 0;
        if (kind != Kind.WATER) {
            if (closed) {
                rise = (int) Math.round(CLOSED_HILL * smooth((t - 0.5) / 0.5));
            }
            if (edge == GroundPlan.Cell.WOODS || edge == GroundPlan.Cell.OUTDOOR && "woods".equals(edgeKind)) {
                rise = Math.max(rise, (int) Math.round(WOODS_HILL * smooth(t)));
            }
            if (kind == Kind.ROAD || kind == Kind.PATH) {
                rise = 0; // the road keeps to the blend (the hills close in on both sides)
            }
        }
        boolean tree = false;
        if (kind == Kind.WOODS || kind == Kind.MEADOW) {
            int chance = kind == Kind.WOODS ? (closed && t > 0.6 ? 90 : 75) : (int) Math.round(6 + 40 * t);
            tree = treeHere(x, z, kind == Kind.WOODS ? 5 : 7, chance) && !nearRoadOrWater(x, z);
        }
        return new Column(kind, t, rise, tree, GroundPlan.noise(x, z, 61) < (kind == Kind.WOODS ? 65 : 35));
    }

    private static double smooth(double v) {
        double c = Math.max(0, Math.min(1, v));
        return c * c * (3 - 2 * c);
    }

    /** One tree per cell x cell square at a fixed spot in it, with the given chance (0-100). */
    private static boolean treeHere(int x, int z, int cell, int chance) {
        int cx = Math.floorDiv(x, cell), cz = Math.floorDiv(z, cell);
        int tx = cx * cell + 1 + GroundPlan.noise(cx, cz, 62) % Math.max(1, cell - 2);
        int tz = cz * cell + 1 + GroundPlan.noise(cx, cz, 63) % Math.max(1, cell - 2);
        return x == tx && z == tz && GroundPlan.noise(cx, cz, 64) < chance;
    }

    /** Keep tree crowns off the roads that run on and off the water. */
    private boolean nearRoadOrWater(int x, int z) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                int nx = x + dx, nz = z + dz;
                int ex = Math.max(0, Math.min(inner.size - 1, nx));
                int ez = Math.max(0, Math.min(inner.size - 1, nz));
                GroundPlan.Cell c = inner.at(ex, ez);
                if (c == GroundPlan.Cell.ROAD || c == GroundPlan.Cell.PATH || c == GroundPlan.Cell.WATER || c == GroundPlan.Cell.LOT) {
                    return true;
                }
            }
        }
        return false;
    }
}

package com.foredayner.scenario.town;

import com.foredayner.scenario.layout.TownLayout;
import com.foredayner.scenario.sheet.ScenarioSheet;

/**
 * What the ground of each block column of the town is, from the layout: streets over everything,
 * then sites (a building's lot is plain grass, an outdoor place has its own ground), then the
 * zones; grass where nothing is. Pure, so it can be tested without a world.
 */
public final class GroundPlan {

    public enum Cell {
        GRASS, ROAD, PATH, WATER, BEACH, WOODS, FIELD, GARDEN, SQUARE, CEMETERY,
        /** A building's lot (grass, kept clear for the building). */
        LOT,
        /** An outdoor place: dock deck, cemetery, woods, square or plain ground (see {@link #outdoor}). */
        OUTDOOR
    }

    public final int size;
    public final Cell[][] cells;
    /** For OUTDOOR cells: the place's id. */
    public final String[][] outdoor;
    /** For OUTDOOR cells: the place's kind (dock, cemetery, woods, square...). */
    public final String[][] outdoorKind;
    /** What the zones made a column before a site or street covered it (a dock knows it stands in water). */
    public final Cell[][] under;
    /** The middle line of main roads (5 wide or more), for road markings. */
    public final boolean[][] centre;

    private GroundPlan(int size) {
        this.size = size;
        this.cells = new Cell[size][size];
        this.outdoor = new String[size][size];
        this.outdoorKind = new String[size][size];
        this.under = new Cell[size][size];
        this.centre = new boolean[size][size];
    }

    public Cell at(int x, int z) {
        return x < 0 || z < 0 || x >= size || z >= size ? Cell.GRASS : cells[x][z];
    }

    public static GroundPlan of(TownLayout layout, ScenarioSheet sheet) {
        GroundPlan g = new GroundPlan(layout.size);
        for (int x = 0; x < g.size; x++) {
            for (int z = 0; z < g.size; z++) {
                g.cells[x][z] = Cell.GRASS;
            }
        }
        for (TownLayout.Zone zone : layout.zones) {
            Cell c = switch (zone.kind) {
                case "water" -> Cell.WATER;
                case "beach" -> Cell.BEACH;
                case "woods" -> Cell.WOODS;
                case "field" -> Cell.FIELD;
                case "garden" -> Cell.GARDEN;
                case "square" -> Cell.SQUARE;
                case "cemetery" -> Cell.CEMETERY;
                default -> Cell.GRASS;
            };
            // Water and beach go under the rest of the zones (a field never floods).
            for (int x = Math.max(0, zone.x); x < Math.min(g.size, zone.x + zone.w); x++) {
                for (int z = Math.max(0, zone.z); z < Math.min(g.size, zone.z + zone.d); z++) {
                    if (c == Cell.WATER || g.cells[x][z] != Cell.WATER) {
                        g.cells[x][z] = c;
                    }
                }
            }
        }
        for (int x = 0; x < g.size; x++) {
            for (int z = 0; z < g.size; z++) {
                g.under[x][z] = g.cells[x][z];
            }
        }
        for (TownLayout.Site s : layout.sites) {
            ScenarioSheet.Place p = sheet.place(s.id);
            for (int x = Math.max(0, s.x); x < Math.min(g.size, s.x + s.w); x++) {
                for (int z = Math.max(0, s.z); z < Math.min(g.size, s.z + s.d); z++) {
                    if (s.outdoor) {
                        g.cells[x][z] = Cell.OUTDOOR;
                        g.outdoor[x][z] = s.id;
                        g.outdoorKind[x][z] = p == null ? "other" : p.kind;
                    } else {
                        g.cells[x][z] = Cell.LOT;
                    }
                }
            }
        }
        for (TownLayout.Road r : layout.roads) {
            if (!"path".equals(r.kind) && r.width >= 5) {
                for (int i = 0; i + 1 < r.points.size(); i++) {
                    int[] a = r.points.get(i), b = r.points.get(i + 1);
                    int steps = Math.max(Math.abs(b[0] - a[0]), Math.abs(b[1] - a[1]));
                    for (int k = 0; k <= steps; k++) {
                        int cx = a[0] + (steps == 0 ? 0 : (b[0] - a[0]) * k / steps);
                        int cz = a[1] + (steps == 0 ? 0 : (b[1] - a[1]) * k / steps);
                        if (cx >= 0 && cz >= 0 && cx < g.size && cz < g.size) {
                            g.centre[cx][cz] = true;
                        }
                    }
                }
            }
            TownLayout one = new TownLayout();
            one.size = layout.size;
            one.roads.add(r);
            boolean[][] mine = one.roadGrid();
            for (int x = 0; x < g.size; x++) {
                for (int z = 0; z < g.size; z++) {
                    if (mine[x][z]) {
                        // A main road wins over a path where they cross.
                        boolean path = "path".equals(r.kind);
                        if (!(path && g.cells[x][z] == Cell.ROAD)) {
                            g.cells[x][z] = path ? Cell.PATH : Cell.ROAD;
                        }
                    }
                }
            }
        }
        return g;
    }

    /** A number from 0 to 99 for a column, the same every time (trees, flowers, crops...). */
    public static int noise(int x, int z, int salt) {
        long h = x * 73856093L ^ z * 19349663L ^ salt * 83492791L;
        h ^= h >>> 13;
        h *= 0x5bd1e995L;
        h ^= h >>> 15;
        return (int) Math.floorMod(h, 100L);
    }
}

package com.foredayner.scenario.layout;

import com.foredayner.scenario.sheet.ScenarioSheet;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * What is dug under a town (pure, so it can be tested): cellars under buildings that have them,
 * a small landing under a place that a tunnel reaches but that has no cellar, and the tunnels
 * between them. Positions are town blocks (x, z); heights are counted down from the ground:
 * a cellar floor is LEVEL_H blocks deep (its floor 1 + 4 of air), a tunnel runs on the first
 * cellar floor's level.
 */
public final class Underground {

    /** Blocks from one cellar floor to the next (a floor block and 4 of air). */
    public static final int LEVEL_H = 5;
    /** A tunnel's inside is this wide (and 3 high). */
    public static final int TUNNEL_W = 2;

    private Underground() {
    }

    /** A dug room: its walls on the edge of x1..x2, z1..z2; levels floors deep; a landing has no cellar of its own. */
    public record Cellar(String id, int x1, int z1, int x2, int z2, int levels, boolean landing) {

        public boolean inside(int x, int z) {
            return x > x1 && x < x2 && z > z1 && z < z2;
        }

        public boolean onWall(int x, int z) {
            return x >= x1 && x <= x2 && z >= z1 && z <= z2 && !inside(x, z);
        }

        public int[] middle() {
            return new int[] { (x1 + x2) / 2, (z1 + z2) / 2 };
        }
    }

    /**
     * A tunnel: its inside cells (TUNNEL_W wide along its line), the wall cells beside them, and
     * the cells where it goes through a cellar's wall (open, or a false wall when secret).
     */
    public record Tunnel(String from, String to, boolean secret, Set<Long> inside, Set<Long> walls, Set<Long> doorways,
                         List<int[]> line) {
    }

    public static long key(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    public static int keyX(long k) {
        return (int) (k >> 32);
    }

    public static int keyZ(long k) {
        return (int) k;
    }

    /** True when the town has anything to dig. */
    public static boolean any(TownLayout layout, ScenarioSheet sheet) {
        return !cellars(layout, sheet).isEmpty();
    }

    /** Cellars of buildings that have them, and landings at the ends of tunnels (places without a cellar). */
    public static List<Cellar> cellars(TownLayout layout, ScenarioSheet sheet) {
        List<Cellar> out = new ArrayList<>();
        if (layout == null || sheet == null || layout.building()) {
            return out;
        }
        Set<String> ends = new HashSet<>();
        for (ScenarioSheet.Link l : sheet.underground()) {
            ends.add(l.from);
            ends.add(l.to);
        }
        for (TownLayout.Site s : layout.sites) {
            ScenarioSheet.Place p = sheet.place(s.id);
            if (p == null) {
                continue;
            }
            if (p.cellars > 0 && !p.outdoor && s.w >= 5 && s.d >= 5) {
                // Under the lot, one block in from its edge (the building's walls stand over the cellar's).
                out.add(new Cellar(s.id, s.x + 1, s.z + 1, s.x + s.w - 2, s.z + s.d - 2, p.cellars, false));
            } else if (ends.contains(s.id)) {
                // A landing (3 x 3 inside) under the middle of the place, with a ladder up.
                int cx = s.x + s.w / 2, cz = s.z + s.d / 2;
                out.add(new Cellar(s.id, cx - 2, cz - 2, cx + 2, cz + 2, 1, true));
            }
        }
        return out;
    }

    static Cellar cellarOf(List<Cellar> cellars, String id) {
        for (Cellar c : cellars) {
            if (c.id().equals(id)) {
                return c;
            }
        }
        return null;
    }

    /** The line a tunnel follows: from one end's middle straight along x, then along z to the other's middle. */
    public static List<int[]> route(int[] a, int[] b) {
        List<int[]> line = new ArrayList<>();
        int x = a[0], z = a[1];
        line.add(new int[] { x, z });
        while (x != b[0]) {
            x += Integer.signum(b[0] - x);
            line.add(new int[] { x, z });
        }
        while (z != b[1]) {
            z += Integer.signum(b[1] - z);
            line.add(new int[] { x, z });
        }
        return line;
    }

    public static List<Tunnel> tunnels(TownLayout layout, ScenarioSheet sheet) {
        List<Tunnel> out = new ArrayList<>();
        List<Cellar> cellars = cellars(layout, sheet);
        for (ScenarioSheet.Link l : sheet == null ? List.<ScenarioSheet.Link>of() : sheet.underground()) {
            Cellar a = cellarOf(cellars, l.from), b = cellarOf(cellars, l.to);
            if (a == null || b == null) {
                continue; // a place not on the layout
            }
            List<int[]> line = route(a.middle(), b.middle());
            Set<Long> inside = new LinkedHashSet<>();
            for (int i = 0; i < line.size(); i++) {
                int[] p = line.get(i);
                // TUNNEL_W wide: the line and the cells beside it (to its right, whichever way it runs).
                int[] next = i + 1 < line.size() ? line.get(i + 1) : i > 0 ? line.get(i - 1) : new int[] { p[0] + 1, p[1] };
                boolean alongX = next[1] == p[1];
                for (int w = 0; w < TUNNEL_W; w++) {
                    inside.add(alongX ? key(p[0], p[1] + w) : key(p[0] + w, p[1]));
                }
            }
            // Inside a cellar the tunnel is the cellar; on its wall it is a doorway (or a false wall).
            Set<Long> doorways = new LinkedHashSet<>();
            Set<Long> open = new LinkedHashSet<>();
            for (long k : inside) {
                int x = keyX(k), z = keyZ(k);
                boolean inCellar = false, wall = false;
                for (Cellar c : cellars) {
                    inCellar |= c.inside(x, z);
                    wall |= c.onWall(x, z);
                }
                if (wall && !inCellar) {
                    doorways.add(k);
                } else if (!inCellar) {
                    open.add(k);
                }
            }
            Set<Long> walls = new LinkedHashSet<>();
            for (long k : open) {
                int x = keyX(k), z = keyZ(k);
                for (int[] d : new int[][] { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 }, { 1, 1 }, { 1, -1 }, { -1, 1 }, { -1, -1 } }) {
                    long n = key(x + d[0], z + d[1]);
                    if (inside.contains(n)) {
                        continue;
                    }
                    boolean inCellar = false, onWall = false;
                    for (Cellar c : cellars) {
                        inCellar |= c.inside(x + d[0], z + d[1]);
                        onWall |= c.onWall(x + d[0], z + d[1]);
                    }
                    if (!inCellar && !onWall) {
                        walls.add(n);
                    }
                }
            }
            out.add(new Tunnel(l.from, l.to, "secret".equals(l.kind), open, walls, doorways, line));
        }
        return out;
    }
}

package com.foredayner.scenario.layout;

import com.foredayner.scenario.sheet.ScenarioSheet;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Where everything goes on the town ground (SIZE x SIZE blocks, x east, z south, 0,0 = north-west
 * corner): each place's site (footprint and which way its front faces), the roads and the areas
 * of water, woods, fields... Checked by code (inside the ground, no overlaps, fronts on a street,
 * one connected street network) whoever made it: the AI, the automatic layout or the GM.
 */
public final class TownLayout {

    public static final int SIZE = 160;
    public static final int MARGIN = 2;
    public static final int GAP = 2;
    public static final List<String> FRONTS = List.of("north", "east", "south", "west");
    public static final List<String> ZONES = List.of("water", "woods", "field", "cemetery", "square", "garden", "beach");

    public int size = SIZE;
    public List<Site> sites = new ArrayList<>();
    public List<Road> roads = new ArrayList<>();
    public List<Zone> zones = new ArrayList<>();

    public static final class Site {
        public String id = "";
        /** North-west corner. */
        public int x;
        public int z;
        /** Footprint along x and z. */
        public int w;
        public int d;
        public String front = "south";
        public boolean outdoor;
    }

    public static final class Road {
        /** Centre line through these points (straight pieces between them). */
        public List<int[]> points = new ArrayList<>();
        public int width = 3;
        /** road (gravel) or path (dirt path). */
        public String kind = "road";
    }

    public static final class Zone {
        public String kind = "field";
        public int x;
        public int z;
        public int w;
        public int d;
    }

    // ------------------------------------------------------------------ JSON

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public String toJson() {
        return GSON.toJson(this);
    }

    public static TownLayout fromJson(String json) {
        TownLayout l = GSON.fromJson(json, TownLayout.class);
        return l == null ? new TownLayout() : l;
    }

    /**
     * The AI's answer: {"places": [{"id", "x", "z", "front"}], "roads": [...], "zones": [...]}. Site
     * sizes come from the sheet, not the AI.
     */
    public static TownLayout fromAnswer(String answer, ScenarioSheet sheet) {
        int start = answer.indexOf('{');
        int end = answer.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalArgumentException("no JSON object in the answer");
        }
        var root = JsonParser.parseString(answer.substring(start, end + 1)).getAsJsonObject();
        TownLayout l = new TownLayout();
        if (root.has("places")) {
            for (var e : root.getAsJsonArray("places")) {
                var o = e.getAsJsonObject();
                String id = ScenarioSheet.slug(o.get("id").getAsString());
                ScenarioSheet.Place p = sheet.place(id);
                if (p == null) {
                    throw new IllegalArgumentException("place '" + id + "' is not in the sheet");
                }
                Site s = new Site();
                s.id = id;
                s.x = o.get("x").getAsInt();
                s.z = o.get("z").getAsInt();
                s.front = o.has("front") ? o.get("front").getAsString().toLowerCase(Locale.ROOT) : "south";
                s.w = s.d = ScenarioSheet.footprint(p.size);
                s.outdoor = p.outdoor;
                l.sites.add(s);
            }
        }
        if (root.has("roads")) {
            for (var e : root.getAsJsonArray("roads")) {
                var o = e.getAsJsonObject();
                Road r = new Road();
                for (var pt : o.getAsJsonArray("points")) {
                    var a = pt.getAsJsonArray();
                    r.points.add(new int[] { a.get(0).getAsInt(), a.get(1).getAsInt() });
                }
                r.width = o.has("width") ? o.get("width").getAsInt() : 3;
                r.kind = o.has("kind") ? o.get("kind").getAsString() : "road";
                l.roads.add(r);
            }
        }
        if (root.has("zones")) {
            for (var e : root.getAsJsonArray("zones")) {
                Zone z = GSON.fromJson(e, Zone.class);
                l.zones.add(z);
            }
        }
        return l;
    }

    // ------------------------------------------------------------------ checks

    public Site site(String id) {
        for (Site s : sites) {
            if (s.id.equals(id)) {
                return s;
            }
        }
        return null;
    }

    private static boolean overlaps(int ax, int az, int aw, int ad, int bx, int bz, int bw, int bd, int gap) {
        return ax < bx + bw + gap && bx < ax + aw + gap && az < bz + bd + gap && bz < az + ad + gap;
    }

    /** The middle of the front edge, one block outside the site. */
    public static int[] frontPoint(Site s) {
        return switch (s.front) {
            case "north" -> new int[] { s.x + s.w / 2, s.z - 1 };
            case "east" -> new int[] { s.x + s.w, s.z + s.d / 2 };
            case "west" -> new int[] { s.x - 1, s.z + s.d / 2 };
            default -> new int[] { s.x + s.w / 2, s.z + s.d };
        };
    }

    /** Which blocks are street (true) on the ground. */
    public boolean[][] roadGrid() {
        boolean[][] g = new boolean[size][size];
        for (Road r : roads) {
            int half = Math.max(1, r.width) / 2;
            for (int i = 0; i + 1 < r.points.size(); i++) {
                int[] a = r.points.get(i), b = r.points.get(i + 1);
                int steps = Math.max(Math.abs(b[0] - a[0]), Math.abs(b[1] - a[1]));
                for (int k = 0; k <= steps; k++) {
                    int cx = a[0] + (steps == 0 ? 0 : (b[0] - a[0]) * k / steps);
                    int cz = a[1] + (steps == 0 ? 0 : (b[1] - a[1]) * k / steps);
                    for (int dx = -half; dx <= half; dx++) {
                        for (int dz = -half; dz <= half; dz++) {
                            int x = cx + dx, z = cz + dz;
                            if (x >= 0 && z >= 0 && x < size && z < size) {
                                g[x][z] = true;
                            }
                        }
                    }
                }
            }
        }
        return g;
    }

    /**
     * Normalize what can be (unknown fronts, sites missing from the sheet are dropped) and list
     * the problems: outside the ground, overlapping sites, a site on water or in the woods, a front
     * that does not reach a street, streets in more than one piece, sheet places with no site.
     */
    public List<String> check(ScenarioSheet sheet) {
        List<String> problems = new ArrayList<>();
        sites.removeIf(s -> sheet.place(s.id) == null);
        for (Site s : sites) {
            if (!FRONTS.contains(s.front)) {
                s.front = "south";
            }
            ScenarioSheet.Place p = sheet.place(s.id);
            s.w = s.d = ScenarioSheet.footprint(p.size);
            s.outdoor = p.outdoor;
            if (s.x < MARGIN || s.z < MARGIN || s.x + s.w > size - MARGIN || s.z + s.d > size - MARGIN) {
                problems.add(String.format(Locale.ROOT, "%s (%d,%d size %dx%d) leaves the ground; keep it inside %d..%d",
                    s.id, s.x, s.z, s.w, s.d, MARGIN, size - MARGIN));
            }
        }
        for (ScenarioSheet.Place p : sheet.places) {
            if (site(p.id) == null) {
                problems.add("place " + p.id + " has no site");
            }
        }
        for (int i = 0; i < sites.size(); i++) {
            for (int j = i + 1; j < sites.size(); j++) {
                Site a = sites.get(i), b = sites.get(j);
                if (overlaps(a.x, a.z, a.w, a.d, b.x, b.z, b.w, b.d, GAP)) {
                    problems.add(a.id + " and " + b.id + " overlap or are closer than " + GAP + " blocks");
                }
            }
        }
        boolean[][] road = roadGrid();
        for (Site s : sites) {
            for (Zone z : zones) {
                if (("water".equals(z.kind) || "woods".equals(z.kind)) && !"dock".equals(sheet.place(s.id).kind)
                    && overlaps(s.x, s.z, s.w, s.d, z.x, z.z, z.w, z.d, 0)) {
                    problems.add(s.id + " stands in the " + z.kind);
                }
            }
            for (int x = s.x; x < s.x + s.w; x++) {
                for (int z = s.z; z < s.z + s.d; z++) {
                    if (x >= 0 && z >= 0 && x < size && z < size && road[x][z]) {
                        problems.add(s.id + " is built over a street");
                        x = s.x + s.w;
                        break;
                    }
                }
            }
            if (!s.outdoor && !nearRoad(road, frontPoint(s), 4)) {
                problems.add(s.id + "'s front (" + s.front + ") does not reach a street within 4 blocks");
            }
        }
        if (!roads.isEmpty() && pieces(road) > 1) {
            problems.add("the streets are in " + pieces(road) + " separate pieces; join them into one network");
        }
        for (Zone z : zones) {
            if (!ZONES.contains(z.kind)) {
                z.kind = "field";
            }
        }
        return problems;
    }

    private boolean nearRoad(boolean[][] road, int[] p, int range) {
        for (int dx = -range; dx <= range; dx++) {
            for (int dz = -range; dz <= range; dz++) {
                int x = p[0] + dx, z = p[1] + dz;
                if (x >= 0 && z >= 0 && x < size && z < size && road[x][z]) {
                    return true;
                }
            }
        }
        return false;
    }

    private int pieces(boolean[][] road) {
        boolean[][] seen = new boolean[size][size];
        int count = 0;
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                if (road[x][z] && !seen[x][z]) {
                    count++;
                    ArrayDeque<int[]> q = new ArrayDeque<>();
                    q.add(new int[] { x, z });
                    seen[x][z] = true;
                    while (!q.isEmpty()) {
                        int[] c = q.poll();
                        for (int[] dlt : new int[][] { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } }) {
                            int nx = c[0] + dlt[0], nz = c[1] + dlt[1];
                            if (nx >= 0 && nz >= 0 && nx < size && nz < size && road[nx][nz] && !seen[nx][nz]) {
                                seen[nx][nz] = true;
                                q.add(new int[] { nx, nz });
                            }
                        }
                    }
                }
            }
        }
        return count;
    }

    // ------------------------------------------------------------------ automatic layout

    /**
     * A plain layout without AI: the sea and the woods along their edges, a main street across the
     * town parallel to the coast, buildings in rows on both sides with their fronts on the street
     * (a second street further in when a row is full), outdoor places at the ends.
     */
    public static TownLayout auto(ScenarioSheet sheet) {
        TownLayout l = new TownLayout();
        String coast = sheet.terrain.coast;
        String woods = sheet.terrain.woods;
        // Work in a frame where the coast (if any) is south; turn the result at the end.
        int turns = switch (coast) {
            case "east" -> 1;
            case "north" -> 2;
            case "west" -> 3;
            default -> 0;
        };
        int south = SIZE;
        if (!"none".equals(coast)) {
            Zone sea = new Zone();
            sea.kind = "water";
            sea.x = 0;
            sea.z = SIZE - 18;
            sea.w = SIZE;
            sea.d = 18;
            l.zones.add(sea);
            Zone beach = new Zone();
            beach.kind = "beach";
            beach.x = 0;
            beach.z = SIZE - 22;
            beach.w = SIZE;
            beach.d = 4;
            l.zones.add(beach);
            south = SIZE - 22;
        }
        int north = 0;
        String woodsLocal = rotateSide(woods, -turns);
        if (!"none".equals(woods) && !woods.equals(coast)) {
            Zone forest = new Zone();
            forest.kind = "woods";
            switch (woodsLocal) {
                case "north" -> {
                    forest.x = 0;
                    forest.z = 0;
                    forest.w = SIZE;
                    forest.d = 16;
                    north = 16;
                }
                case "east" -> {
                    forest.x = SIZE - 14;
                    forest.z = north;
                    forest.w = 14;
                    forest.d = south - north;
                }
                case "west" -> {
                    forest.x = 0;
                    forest.z = north;
                    forest.w = 14;
                    forest.d = south - north;
                }
                default -> {
                }
            }
            if (forest.w > 0) {
                l.zones.add(forest);
            }
        }
        List<ScenarioSheet.Place> buildings = new ArrayList<>();
        List<ScenarioSheet.Place> outdoor = new ArrayList<>();
        for (ScenarioSheet.Place p : sheet.places) {
            (p.outdoor ? outdoor : buildings).add(p);
        }
        buildings.sort(Comparator.comparingInt((ScenarioSheet.Place p) -> -ScenarioSheet.footprint(p.size)));
        // Streets: rows of buildings on both sides, from the coast side inward.
        int west = "west".equals(woodsLocal) ? 16 : MARGIN + 2;
        int east = "east".equals(woodsLocal) ? SIZE - 16 : SIZE - MARGIN - 2;
        int streetZ = Math.min(south - 36, (north + south) / 2 + 12);
        List<int[]> streets = new ArrayList<>();
        int cursorA = west, cursorB = west; // A: north of the street, B: south of it
        int depthA = 0, depthB = 0;
        for (ScenarioSheet.Place p : buildings) {
            int f = ScenarioSheet.footprint(p.size);
            boolean toA = cursorA <= cursorB;
            if ((toA ? cursorA : cursorB) + f > east) {
                toA = !toA;
            }
            if ((toA ? cursorA : cursorB) + f > east) {
                // Row full: start a new street further inland.
                streets.add(new int[] { streetZ, west, Math.max(cursorA, cursorB) });
                streetZ = streetZ - Math.max(depthA, 21) - 10;
                cursorA = cursorB = west;
                depthA = depthB = 0;
                toA = true;
            }
            Site s = new Site();
            s.id = p.id;
            s.w = s.d = f;
            if (toA) {
                s.x = cursorA;
                s.z = streetZ - 3 - f;
                s.front = "south";
                cursorA += f + 5;
                depthA = Math.max(depthA, f);
            } else {
                s.x = cursorB;
                s.z = streetZ + 4;
                s.front = "north";
                cursorB += f + 5;
                depthB = Math.max(depthB, f);
            }
            l.sites.add(s);
        }
        streets.add(new int[] { streetZ, west, Math.max(cursorA, cursorB) });
        // Centre the buildings between the west and east edges.
        int minX = SIZE, maxX = 0, minZ = SIZE;
        for (Site s : l.sites) {
            minX = Math.min(minX, s.x);
            maxX = Math.max(maxX, s.x + s.w);
            minZ = Math.min(minZ, s.z);
        }
        int shift = l.sites.isEmpty() ? 0 : Math.max(0, (west + east) / 2 - (minX + maxX) / 2);
        for (Site s : l.sites) {
            s.x += shift;
        }
        // Outdoor places: docks and beaches on the shore, the rest in a band inland of all
        // buildings; each where a straight path reaches the nearest street without crossing a building.
        int mainZ = streets.get(0)[0];
        int innerZ = streets.get(streets.size() - 1)[0];
        List<Road> paths = new ArrayList<>();
        List<Site> buildingSites = new ArrayList<>(l.sites);
        for (ScenarioSheet.Place p : outdoor) {
            int f = ScenarioSheet.footprint(p.size);
            boolean water = ("dock".equals(p.kind) || "beach".equals(p.kind)) && south < SIZE;
            Site s = new Site();
            s.id = p.id;
            s.w = s.d = f;
            s.outdoor = true;
            s.front = water ? "north" : "south";
            s.z = water ? south - f + 6 : Math.max(north + MARGIN, (l.sites.isEmpty() ? innerZ : minZ) - 5 - f);
            int target = water ? mainZ : innerZ;
            int best = -1;
            int bestScore = Integer.MAX_VALUE;
            for (int x = west; x + f <= east; x++) {
                s.x = x;
                int[] fp = frontPoint(s);
                boolean free = true;
                for (Site o : l.sites) {
                    if (overlaps(s.x, s.z, s.w, s.d, o.x, o.z, o.w, o.d, GAP + 1)) {
                        free = false;
                        break;
                    }
                }
                int z0 = Math.min(fp[1], target), z1 = Math.max(fp[1], target);
                for (int i = 0; free && i < buildingSites.size(); i++) {
                    Site o = buildingSites.get(i);
                    if (overlaps(fp[0] - 1, z0, 3, z1 - z0 + 1, o.x, o.z, o.w, o.d, 1)) {
                        free = false;
                    }
                }
                int score = Math.abs(x + f / 2 - (west + east) / 2);
                if (free && score < bestScore) {
                    best = x;
                    bestScore = score;
                }
            }
            s.x = best >= 0 ? best : Math.max(west, Math.min(east - f, (west + east) / 2 - f / 2));
            l.sites.add(s);
            Road path = new Road();
            path.kind = "path";
            path.width = 3;
            int[] fp = frontPoint(s);
            path.points.add(new int[] { fp[0], water ? fp[1] - 1 : fp[1] + 1 });
            path.points.add(new int[] { fp[0], target });
            paths.add(path);
        }
        // Streets run across the whole town; a lane along the west joins them.
        int spineX = west - 1 + Math.max(0, shift - 3);
        for (int[] st : streets) {
            Road r = new Road();
            r.width = 5;
            r.points.add(new int[] { Math.max(MARGIN + 1, west - 2), st[0] });
            r.points.add(new int[] { Math.min(SIZE - MARGIN - 2, east + 1), st[0] });
            l.roads.add(r);
        }
        if (streets.size() > 1) {
            Road spine = new Road();
            spine.width = 3;
            spine.points.add(new int[] { spineX, streets.get(0)[0] });
            spine.points.add(new int[] { spineX, streets.get(streets.size() - 1)[0] });
            l.roads.add(spine);
        }
        l.roads.addAll(paths);
        l.turn(turns);
        return l;
    }

    private static String rotateSide(String side, int turns) {
        int i = FRONTS.indexOf(side);
        return i < 0 ? side : FRONTS.get(Math.floorMod(i + turns, 4));
    }

    /** Turn the whole layout clockwise by quarter turns around the middle of the ground. */
    public void turn(int turns) {
        for (int t = 0; t < Math.floorMod(turns, 4); t++) {
            for (Site s : sites) {
                int nx = size - (s.z + s.d);
                int nz = s.x;
                s.x = nx;
                s.z = nz;
                int w = s.w;
                s.w = s.d;
                s.d = w;
                s.front = rotateSide(s.front, 1);
            }
            for (Zone z : zones) {
                int nx = size - (z.z + z.d);
                int nz = z.x;
                z.x = nx;
                z.z = nz;
                int w = z.w;
                z.w = z.d;
                z.d = w;
            }
            for (Road r : roads) {
                List<int[]> pts = new ArrayList<>();
                for (int[] p : r.points) {
                    pts.add(new int[] { size - 1 - p[1], p[0] });
                }
                r.points = pts;
            }
        }
    }
}

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

    public static final int SIZE = 240;
    public static final int MARGIN = 2;
    public static final int GAP = 2;
    public static final List<String> FRONTS = List.of("north", "east", "south", "west");
    public static final List<String> ZONES = List.of("water", "woods", "field", "cemetery", "square", "garden", "beach", "parking", "park");

    /** A town's ground is set from its places (240 up to 400); building stages keep the old 160. */
    public int size = 160;
    /** "town", or "building": the sites are rooms of one building on floors (see {@link IndoorPlan}). */
    public String stage = "town";
    public List<Site> sites = new ArrayList<>();
    /** Building stage: doors in the room walls. */
    public List<Door> doors = new ArrayList<>();
    /** Building stage: flights of stairs. */
    public List<Stair> stairs = new ArrayList<>();

    public boolean building() {
        return "building".equals(stage);
    }

    /** A door in a wall (building stage): the wall block at (x, z) on that floor. */
    public static final class Door {
        public int x;
        public int z;
        public int level;
        /** The way in from outside. */
        public boolean entrance;
    }

    /** A straight flight of stairs (building stage) from {@code level} up to the next, starting at (x, z) and climbing toward dir. */
    public static final class Stair {
        public int x;
        public int z;
        public int level;
        public String dir = "north";
    }
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
        /** Put where the keeper's map shows it (false: the AI chose the spot). */
        public boolean fromMap;
        /** Building stage: the floor of this room (its w and d are its own, walls included). */
        public int level;
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
        l.size = ScenarioSheet.groundSize(sheet);
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
                s.fromMap = o.has("from_map") && o.get("from_map").getAsBoolean();
                s.w = s.d = ScenarioSheet.footprint(p);
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
    /** How far past the map's edge the keeper may put places: a quarter of its side each way. */
    public int reach() {
        return size / 4;
    }

    /** A place the keeper put (partly) past the edge of the map: only its building is made there. */
    public boolean outside(Site s) {
        return s.x < 0 || s.z < 0 || s.x + s.w > size || s.z + s.d > size;
    }

    /** Past the edge, but no further than the keeper may go. */
    public boolean withinReach(Site s) {
        int r = reach();
        return s.x >= -r && s.z >= -r && s.x + s.w <= size + r && s.z + s.d <= size + r;
    }

    public List<String> check(ScenarioSheet sheet) {
        if (building()) {
            return IndoorPlan.check(this, sheet);
        }
        List<String> problems = new ArrayList<>();
        sites.removeIf(s -> sheet.place(s.id) == null);
        for (Site s : sites) {
            if (!FRONTS.contains(s.front)) {
                s.front = "south";
            }
            ScenarioSheet.Place p = sheet.place(s.id);
            s.w = s.d = ScenarioSheet.footprint(p);
            s.outdoor = p.outdoor;
            if (outside(s)) {
                if (!withinReach(s)) {
                    problems.add(String.format(Locale.ROOT, "%s (%d,%d size %dx%d) is too far past the edge; keep it inside %d..%d",
                        s.id, s.x, s.z, s.w, s.d, -reach(), size + reach()));
                }
            } else if (s.x < MARGIN || s.z < MARGIN || s.x + s.w > size - MARGIN || s.z + s.d > size - MARGIN) {
                problems.add(String.format(Locale.ROOT, "%s (%d,%d size %dx%d) leaves the ground; keep it inside %d..%d",
                    s.id, s.x, s.z, s.w, s.d, MARGIN, size - MARGIN));
            }
        }
        for (ScenarioSheet.Place p : sheet.places) {
            if (site(p.id) == null) {
                problems.add("place " + p.id + " has no site (no free spot: make places smaller or fewer)");
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
            if (outside(s)) {
                continue; // past the map: only its building is made, with no street to it
            }
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

    /** Summed road cells, for counting the street blocks under any rectangle at once. */
    int[][] roadSums() {
        boolean[][] road = roadGrid();
        int[][] sum = new int[size + 1][size + 1];
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                sum[x + 1][z + 1] = sum[x][z + 1] + sum[x + 1][z] - sum[x][z] + (road[x][z] ? 1 : 0);
            }
        }
        return sum;
    }

    private int roadsIn(int[][] sum, int x, int z, int w, int d) {
        int x0 = Math.max(0, x), z0 = Math.max(0, z), x1 = Math.min(size, x + w), z1 = Math.min(size, z + d);
        if (x1 <= x0 || z1 <= z0) {
            return 0;
        }
        return sum[x1][z1] - sum[x0][z1] - sum[x1][z0] + sum[x0][z0];
    }

    /**
     * Whether a site may stand where it is, among the sites already in the layout: inside the
     * ground, apart from the others, off the streets, out of water and woods (docks may reach into
     * water), with its front by a street unless it is outdoors.
     */
    boolean fits(Site s, ScenarioSheet.Place place, int[][] roadSum) {
        if (s.x < MARGIN || s.z < MARGIN || s.x + s.w > size - MARGIN || s.z + s.d > size - MARGIN) {
            return false;
        }
        for (Site o : sites) {
            if (o != s && overlaps(s.x, s.z, s.w, s.d, o.x, o.z, o.w, o.d, GAP)) {
                return false;
            }
        }
        if (roadsIn(roadSum, s.x, s.z, s.w, s.d) > 0) {
            return false;
        }
        boolean dock = place != null && "dock".equals(place.kind);
        for (Zone z : zones) {
            if (("water".equals(z.kind) && !dock || "woods".equals(z.kind)) && overlaps(s.x, s.z, s.w, s.d, z.x, z.z, z.w, z.d, 0)) {
                return false;
            }
        }
        if (s.outdoor) {
            return true;
        }
        int[] f = frontPoint(s);
        return roadsIn(roadSum, f[0] - 4, f[1] - 4, 9, 9) > 0;
    }

    /**
     * Move a site (keeping its size) to the free spot nearest the middle of the ground, turning its
     * front to the street; false when there is none.
     */
    public boolean placeFree(Site s, ScenarioSheet.Place place, int[][] roadSum) {
        int bestX = 0, bestZ = 0, best = Integer.MAX_VALUE;
        String bestFront = s.front;
        String keep = s.front;
        int ox = s.x, oz = s.z;
        for (int x = MARGIN; x + s.w <= size - MARGIN; x++) {
            for (int z = MARGIN; z + s.d <= size - MARGIN; z++) {
                int score = Math.abs(x + s.w / 2 - size / 2) + Math.abs(z + s.d / 2 - size / 2);
                if (score >= best) {
                    continue;
                }
                s.x = x;
                s.z = z;
                for (String front : FRONTS) {
                    s.front = front;
                    if (fits(s, place, roadSum)) {
                        best = score;
                        bestX = x;
                        bestZ = z;
                        bestFront = front;
                        break;
                    }
                }
            }
        }
        if (best == Integer.MAX_VALUE) {
            s.x = ox;
            s.z = oz;
            s.front = keep;
            return false;
        }
        s.x = bestX;
        s.z = bestZ;
        s.front = bestFront;
        return true;
    }

    /** Put a place that has no site yet (or a new one) on the first free spot; false when there is none. */
    public boolean addFree(ScenarioSheet.Place place) {
        Site s = new Site();
        s.id = place.id;
        s.w = s.d = ScenarioSheet.footprint(place);
        s.outdoor = place.outdoor;
        if (placeFree(s, place, roadSums())) {
            sites.add(s);
            return true;
        }
        return false;
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

    // ------------------------------------------------------------------ repair

    /** A site on its own, among {@code others}: inside, apart, off streets, out of water and woods; with needFront also by a street. */
    private boolean fitsAmong(Site s, ScenarioSheet.Place place, int[][] roadSum, List<Site> others, boolean needFront) {
        if (s.x < MARGIN || s.z < MARGIN || s.x + s.w > size - MARGIN || s.z + s.d > size - MARGIN) {
            return false;
        }
        for (Site o : others) {
            if (o != s && overlaps(s.x, s.z, s.w, s.d, o.x, o.z, o.w, o.d, GAP)) {
                return false;
            }
        }
        if (roadsIn(roadSum, s.x, s.z, s.w, s.d) > 0) {
            return false;
        }
        boolean dock = place != null && "dock".equals(place.kind);
        for (Zone z : zones) {
            if (("water".equals(z.kind) && !dock || "woods".equals(z.kind)) && overlaps(s.x, s.z, s.w, s.d, z.x, z.z, z.w, z.d, 0)) {
                return false;
            }
        }
        if (!needFront || s.outdoor) {
            return true;
        }
        int[] f = frontPoint(s);
        return roadsIn(roadSum, f[0] - 4, f[1] - 4, 9, 9) > 0;
    }

    /**
     * Fix what code can fix in a layout (the AI's, made from a map, or the keeper's) without moving
     * anything more than needed, and say what was done (Korean, for the keeper):
     * <ul>
     *   <li>a road that runs on past a building standing on it is cut short before it;</li>
     *   <li>a site off the ground, on a street, in water or woods, or too close to another moves to
     *       the nearest free spot (not to the middle of town);</li>
     *   <li>a place with no site gets the free spot nearest the middle;</li>
     *   <li>a building whose front is not by a street turns to one, or gets a footpath to it;</li>
     *   <li>separate pieces of street are joined by footpaths.</li>
     * </ul>
     */
    public List<String> repair(ScenarioSheet sheet) {
        if (building()) {
            return IndoorPlan.repair(this, sheet);
        }
        List<String> notes = new ArrayList<>();
        sites.removeIf(s -> sheet.place(s.id) == null);
        for (Site s : sites) {
            if (!FRONTS.contains(s.front)) {
                s.front = "south";
            }
            ScenarioSheet.Place p = sheet.place(s.id);
            s.w = s.d = ScenarioSheet.footprint(p);
            s.outdoor = p.outdoor;
        }
        for (Zone z : zones) {
            if (!ZONES.contains(z.kind)) {
                z.kind = "field";
            }
        }
        // Roads that only run on past a building (to the edge of the ground) stop before it.
        for (Site s : sites) {
            if (trimRoadEndsAt(s)) {
                notes.add(name(sheet, s) + " 앞에서 길을 끊었어요");
            }
        }
        // Sites that cannot stand where they are move as little as possible.
        int[][] roadSum = roadSums();
        List<Site> placed = new ArrayList<>();
        for (Site s : new ArrayList<>(sites)) {
            ScenarioSheet.Place p = sheet.place(s.id);
            if (outside(s) && withinReach(s)) {
                placed.add(s); // the keeper put it past the map on purpose
                continue;
            }
            if (!fitsAmong(s, p, roadSum, placed, false)) {
                int ox = s.x, oz = s.z;
                if (placeNear(s, p, roadSum, placed)) {
                    notes.add(name(sheet, s) + "을(를) " + (Math.abs(s.x - ox) + Math.abs(s.z - oz)) + "칸 옮겼어요");
                } else {
                    sites.remove(s);
                    continue;
                }
            }
            placed.add(s);
        }
        for (ScenarioSheet.Place p : sheet.places) {
            if (site(p.id) == null) {
                Site s = new Site();
                s.id = p.id;
                s.w = s.d = ScenarioSheet.footprint(p);
                s.outdoor = p.outdoor;
                s.x = (size - s.w) / 2;
                s.z = (size - s.d) / 2;
                if (placeNear(s, p, roadSum, sites)) {
                    sites.add(s);
                    notes.add(name(sheet, s) + "을(를) 빈자리에 놓았어요");
                }
            }
        }
        // Fronts: face a street within reach, or get a footpath to the nearest one.
        int lanes = 0;
        for (Site s : sites) {
            if (s.outdoor || outside(s) || nearRoad(roadGrid(), frontPoint(s), 4)) {
                continue;
            }
            String keep = s.front;
            boolean turned = false;
            for (String f : FRONTS) {
                s.front = f;
                if (nearRoad(roadGrid(), frontPoint(s), 4)) {
                    turned = true;
                    break;
                }
            }
            if (turned) {
                continue;
            }
            s.front = towardRoad(s, keep);
            if (laneFrom(s)) {
                lanes++;
            }
        }
        if (lanes > 0) {
            notes.add("길에서 떨어진 건물 " + lanes + "곳에 오솔길을 이었어요");
        }
        int joined = joinRoadPieces();
        if (joined > 0) {
            notes.add("떨어진 길 " + joined + "곳을 오솔길로 이었어요");
        }
        return notes;
    }

    /**
     * Tidy everything but the places (no AI, nothing moved): the streets, footpaths and ground
     * zones are thrown away and made again around the places where they now stand, as the
     * automatic layout would: the sea and the woods along their edges, a main street across the
     * town where it crosses the fewest lots, a footpath from every front to it, every piece joined,
     * and the open ground filled with gardens and fields (parks in a modern town). Overlapping
     * places are only reported. Returns what was done, for the keeper.
     */
    public List<String> tidy(ScenarioSheet sheet) {
        List<String> notes = new ArrayList<>();
        if (building()) {
            return notes;
        }
        check(sheet); // fronts, sizes and dropped places, as for a build
        List<Site> inside = new ArrayList<>();
        for (Site s : sites) {
            if (!outside(s)) {
                inside.add(s);
            }
        }
        roads.clear();
        zones.clear();
        boolean modern = com.foredayner.scenario.sheet.Period.contemporary(sheet.period());
        // 1. The sea and the woods along their edges, taken out from under the lots (a dock keeps its water).
        terrainZones(sheet.terrain.coast, sheet.terrain.woods);
        for (Site s : inside) {
            ScenarioSheet.Place p = sheet.place(s.id);
            boolean dock = p != null && ("dock".equals(p.kind) || "beach".equals(p.kind));
            List<Zone> next = new ArrayList<>();
            for (Zone z : zones) {
                boolean clear = "woods".equals(z.kind) || !dock;
                if (!clear || !overlaps(s.x, s.z, s.w, s.d, z.x, z.z, z.w, z.d, 0)) {
                    next.add(z);
                    continue;
                }
                next.addAll(minus(z, s.x - 1, s.z - 1, s.x + s.w + 1, s.z + s.d + 1));
            }
            zones.clear();
            zones.addAll(next);
        }
        // 2. A main street across the town, parallel to the coast, where it crosses the fewest lots.
        mainStreet(sheet.terrain.coast, inside, modern ? 7 : 5);
        cutUnderLots(inside);
        // 3. A footpath from every front far from a street.
        int lanes = 0;
        for (Site s : inside) {
            if (!nearRoad(roadGrid(), frontPoint(s), 4) && laneFrom(s)) {
                lanes++;
            }
        }
        // 4. One network of streets.
        int joined = joinRoadPieces();
        // 5. The open ground.
        fillOpen(modern);
        notes.add("길과 땅을 지우고 지금 장소 자리에 맞춰 다시 만들었어요 (오솔길 " + (lanes + joined) + "곳)");
        // 6. Overlapping places are the keeper's to move.
        for (int i = 0; i < sites.size(); i++) {
            for (int j = i + 1; j < sites.size(); j++) {
                Site a = sites.get(i), b = sites.get(j);
                if (overlaps(a.x, a.z, a.w, a.d, b.x, b.z, b.w, b.d, 0)) {
                    notes.add(name(sheet, a) + "와(과) " + name(sheet, b) + "이(가) 겹쳐 있어요 (직접 옮겨 주세요)");
                }
            }
        }
        return notes;
    }

    /** The sea (with its beach) along the coast's edge and the woods along theirs, in this ground's own frame. */
    private void terrainZones(String coast, String woods) {
        int n = size;
        if (coast != null && FRONTS.contains(coast)) {
            zones.add(edgeZone("water", coast, 0, 18));
            zones.add(edgeZone("beach", coast, 18, 4));
        }
        if (woods != null && FRONTS.contains(woods) && !woods.equals(coast)) {
            boolean coastSide = coast != null && FRONTS.contains(coast);
            Zone forest = edgeZone("woods", woods, 0, "north".equals(woods) || "south".equals(woods) ? 16 : 14);
            if (coastSide) {
                // Woods along a side stop at the beach.
                Zone shore = edgeZone("beach", coast, 0, 22);
                if (overlaps(forest.x, forest.z, forest.w, forest.d, shore.x, shore.z, shore.w, shore.d, 0)) {
                    List<Zone> cut = minus(forest, shore.x, shore.z, shore.x + shore.w, shore.z + shore.d);
                    zones.addAll(cut);
                    return;
                }
            }
            zones.add(forest);
        }
    }

    /** A strip {@code depth} deep along a side of the ground, {@code in} blocks in from that edge. */
    private Zone edgeZone(String kind, String side, int in, int depth) {
        Zone z = new Zone();
        z.kind = kind;
        switch (side) {
            case "north" -> { z.x = 0; z.z = in; z.w = size; z.d = depth; }
            case "east" -> { z.x = size - in - depth; z.z = 0; z.w = depth; z.d = size; }
            case "west" -> { z.x = in; z.z = 0; z.w = depth; z.d = size; }
            default -> { z.x = 0; z.z = size - in - depth; z.w = size; z.d = depth; }
        }
        return z;
    }

    /** A straight main street across the ground (along the coast), on the line that crosses the fewest lots near the middle. */
    private void mainStreet(String coast, List<Site> inside, int width) {
        boolean alongX = !"east".equals(coast) && !"west".equals(coast);
        int half = width / 2;
        int lo = MARGIN + 4, hi = size - MARGIN - 5;
        for (Zone z : zones) {
            if ("water".equals(z.kind) || "beach".equals(z.kind)) {
                // Stay inland of the shore.
                if (alongX) {
                    if (z.z > size / 2) {
                        hi = Math.min(hi, z.z - half - 3);
                    } else {
                        lo = Math.max(lo, z.z + z.d + half + 3);
                    }
                } else if (z.x > size / 2) {
                    hi = Math.min(hi, z.x - half - 3);
                } else {
                    lo = Math.max(lo, z.x + z.w + half + 3);
                }
            }
        }
        // The middle of the fronts, so the street runs where the town is.
        long sum = 0;
        for (Site s : inside) {
            int[] f = frontPoint(s);
            sum += alongX ? f[1] : f[0];
        }
        int centre = inside.isEmpty() ? size / 2 : (int) (sum / inside.size());
        int best = Math.max(lo, Math.min(hi, centre));
        long bestScore = Long.MAX_VALUE;
        for (int p = lo; p <= hi; p++) {
            int crossed = 0;
            for (Site s : inside) {
                boolean hit = alongX ? p + half + 1 >= s.z && p - half - 1 < s.z + s.d : p + half + 1 >= s.x && p - half - 1 < s.x + s.w;
                if (hit) {
                    crossed++;
                }
            }
            long score = crossed * 10_000L + Math.abs(p - centre);
            if (score < bestScore) {
                bestScore = score;
                best = p;
            }
        }
        Road r = new Road();
        r.width = width;
        if (alongX) {
            r.points.add(new int[] { MARGIN + 1, best });
            r.points.add(new int[] { size - MARGIN - 2, best });
        } else {
            r.points.add(new int[] { best, MARGIN + 1 });
            r.points.add(new int[] { best, size - MARGIN - 2 });
        }
        roads.add(r);
    }

    /** Streets that run over a lot are cut there (with the block around it); stubs left by the cut go. */
    private int cutUnderLots(List<Site> inside) {
        int cut = 0;
        List<Road> kept = new ArrayList<>();
        for (Road r : roads) {
            int half = Math.max(1, r.width) / 2;
            List<int[]> cells = new ArrayList<>();
            for (int i = 0; i + 1 < r.points.size(); i++) {
                int[] a = r.points.get(i), b = r.points.get(i + 1);
                int steps = Math.max(Math.abs(b[0] - a[0]), Math.abs(b[1] - a[1]));
                for (int k = 0; k <= steps; k++) {
                    int[] c = { a[0] + (steps == 0 ? 0 : (b[0] - a[0]) * k / steps), a[1] + (steps == 0 ? 0 : (b[1] - a[1]) * k / steps) };
                    int[] last = cells.isEmpty() ? null : cells.get(cells.size() - 1);
                    if (last == null || last[0] != c[0] || last[1] != c[1]) {
                        cells.add(c);
                    }
                }
            }
            List<List<int[]>> runs = new ArrayList<>();
            List<int[]> run = new ArrayList<>();
            boolean hit = false;
            for (int[] c : cells) {
                boolean under = false;
                for (Site s : inside) {
                    if (overlaps(c[0] - half, c[1] - half, 2 * half + 1, 2 * half + 1, s.x, s.z, s.w, s.d, 0)) {
                        under = true;
                        break;
                    }
                }
                if (under) {
                    hit = true;
                    if (!run.isEmpty()) {
                        runs.add(run);
                        run = new ArrayList<>();
                    }
                } else {
                    run.add(c);
                }
            }
            if (!run.isEmpty()) {
                runs.add(run);
            }
            if (!hit) {
                kept.add(r);
                continue;
            }
            cut++;
            for (List<int[]> piece : runs) {
                if (piece.size() < 3) {
                    continue;
                }
                Road part = new Road();
                part.width = r.width;
                part.kind = r.kind;
                part.points = corners(piece);
                kept.add(part);
            }
        }
        roads.clear();
        roads.addAll(kept);
        return cut;
    }

    /**
     * Fill the open ground (no lot, street or zone near) in cells of 12 blocks: gardens near the
     * streets and fields further out (a modern town: parking by the street, parks beyond).
     */
    private void fillOpen(boolean modern) {
        final int cell = 12;
        boolean[][] road = roadGrid();
        // Distance of every cell to the nearest street (4-way steps).
        int[][] dist = new int[size][size];
        ArrayDeque<int[]> q = new ArrayDeque<>();
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                dist[x][z] = road[x][z] ? 0 : Integer.MAX_VALUE;
                if (road[x][z]) {
                    q.add(new int[] { x, z });
                }
            }
        }
        while (!q.isEmpty()) {
            int[] c = q.poll();
            for (int[] d : new int[][] { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } }) {
                int nx = c[0] + d[0], nz = c[1] + d[1];
                if (nx >= 0 && nz >= 0 && nx < size && nz < size && dist[nx][nz] == Integer.MAX_VALUE) {
                    dist[nx][nz] = dist[c[0]][c[1]] + 1;
                    q.add(new int[] { nx, nz });
                }
            }
        }
        List<Zone> added = new ArrayList<>();
        for (int z = MARGIN; z + cell <= size - MARGIN; z += cell) {
            Zone run = null;
            for (int x = MARGIN; x + cell <= size - MARGIN; x += cell) {
                boolean free = true;
                for (Site s : sites) {
                    if (overlaps(x, z, cell, cell, s.x, s.z, s.w, s.d, 3)) {
                        free = false;
                        break;
                    }
                }
                for (Zone o : zones) {
                    if (free && overlaps(x, z, cell, cell, o.x, o.z, o.w, o.d, 0)) {
                        free = false;
                    }
                }
                for (int dx = -2; free && dx < cell + 2; dx++) {
                    for (int dz = -2; dz < cell + 2; dz++) {
                        int gx = x + dx, gz = z + dz;
                        if (gx >= 0 && gz >= 0 && gx < size && gz < size && road[gx][gz]) {
                            free = false;
                            break;
                        }
                    }
                }
                if (!free) {
                    run = null;
                    continue;
                }
                int near = dist[x + cell / 2][z + cell / 2];
                String kind = modern ? (near < 14 && Math.floorMod(x / cell * 3 + z / cell * 2, 7) == 0 ? "parking" : "park")
                    : near < 34 ? "garden" : "field";
                if (run != null && run.kind.equals(kind)) {
                    run.w += cell;
                } else {
                    run = new Zone();
                    run.kind = kind;
                    run.x = x;
                    run.z = z;
                    run.w = cell;
                    run.d = cell;
                    added.add(run);
                }
            }
        }
        zones.addAll(added);
    }

    /**
     * The keeper chose another ground size: every place keeps its spot in proportion (its middle moved by the ratio,
     * its lot the same size), kept inside the ground if it was inside; then the streets and zones are made again
     * around them (tidy). Returns what was done, for the keeper.
     */
    public List<String> rescale(int newSize, ScenarioSheet sheet) {
        List<String> notes = new ArrayList<>();
        if (building() || newSize <= 0 || newSize == size) {
            return notes;
        }
        double f = newSize / (double) size;
        for (Site s : sites) {
            boolean in = !outside(s);
            double cx = s.x + s.w / 2.0, cz = s.z + s.d / 2.0;
            s.x = (int) Math.round(cx * f - s.w / 2.0);
            s.z = (int) Math.round(cz * f - s.d / 2.0);
            if (in) {
                s.x = Math.max(MARGIN, Math.min(newSize - MARGIN - s.w, s.x));
                s.z = Math.max(MARGIN, Math.min(newSize - MARGIN - s.d, s.z));
            }
        }
        notes.add("마을 땅을 " + size + "칸에서 " + newSize + "칸으로 바꾸고 장소를 비율대로 옮겼어요");
        size = newSize;
        notes.addAll(tidy(sheet));
        return notes;
    }

    /** What is left of a zone after taking out the box x1..x2, z1..z2 (end exclusive): up to four strips. */
    private static List<Zone> minus(Zone z, int x1, int z1, int x2, int z2) {
        List<Zone> out = new ArrayList<>();
        int zx2 = z.x + z.w, zz2 = z.z + z.d;
        int ix1 = Math.max(z.x, x1), ix2 = Math.min(zx2, x2), iz1 = Math.max(z.z, z1), iz2 = Math.min(zz2, z2);
        if (ix1 >= ix2 || iz1 >= iz2) {
            out.add(z);
            return out;
        }
        addZone(out, z.kind, z.x, z.z, zx2, iz1);      // north strip, full width
        addZone(out, z.kind, z.x, iz2, zx2, zz2);      // south strip, full width
        addZone(out, z.kind, z.x, iz1, ix1, iz2);      // west, between them
        addZone(out, z.kind, ix2, iz1, zx2, iz2);      // east, between them
        return out;
    }

    private static void addZone(List<Zone> out, String kind, int x1, int z1, int x2, int z2) {
        if (x2 - x1 >= 2 && z2 - z1 >= 2) {
            Zone n = new Zone();
            n.kind = kind;
            n.x = x1;
            n.z = z1;
            n.w = x2 - x1;
            n.d = z2 - z1;
            out.add(n);
        }
    }

    private static String name(ScenarioSheet sheet, Site s) {
        ScenarioSheet.Place p = sheet.place(s.id);
        return p == null || p.name == null || p.name.isBlank() ? s.id : p.name;
    }

    /**
     * A road whose first or last piece runs through the site and on to the edge of the ground (a
     * road traced "to the edge" past a building at its end) is cut back so it stops in front of it.
     */
    boolean trimRoadEndsAt(Site s) {
        boolean cut = false;
        for (Road r : roads) {
            for (int end = 0; end < 2 && r.points.size() >= 2; end++) {
                List<int[]> pts = r.points;
                int last = end == 0 ? pts.size() - 1 : 0;
                int prev = end == 0 ? pts.size() - 2 : 1;
                int[] tip = pts.get(last), from = pts.get(prev);
                boolean atEdge = tip[0] <= MARGIN + 4 || tip[1] <= MARGIN + 4 || tip[0] >= size - MARGIN - 5 || tip[1] >= size - MARGIN - 5;
                if (!atEdge) {
                    continue;
                }
                int half = Math.max(1, r.width) / 2;
                int steps = Math.max(Math.abs(tip[0] - from[0]), Math.abs(tip[1] - from[1]));
                // Walk from the inner point toward the tip; stop just before the road would touch the site.
                int keepX = -1, keepZ = -1;
                boolean hits = false;
                for (int k = 0; k <= steps; k++) {
                    int cx = from[0] + (steps == 0 ? 0 : (tip[0] - from[0]) * k / steps);
                    int cz = from[1] + (steps == 0 ? 0 : (tip[1] - from[1]) * k / steps);
                    if (overlaps(cx - half, cz - half, 2 * half + 1, 2 * half + 1, s.x, s.z, s.w, s.d, 1)) {
                        hits = true;
                        break;
                    }
                    keepX = cx;
                    keepZ = cz;
                }
                if (!hits) {
                    continue;
                }
                if (keepX < 0 || (keepX == from[0] && keepZ == from[1] && pts.size() == 2)) {
                    continue; // nothing of the road would be left: the site moves instead
                }
                pts.set(last, new int[] { keepX, keepZ });
                cut = true;
                // The site now faces the road's end.
                int cx = s.x + s.w / 2, cz = s.z + s.d / 2;
                int dx = keepX - cx, dz = keepZ - cz;
                s.front = Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? "east" : "west") : (dz > 0 ? "south" : "north");
            }
        }
        return cut;
    }

    /** Move a site to the free spot nearest where it is (fronts by a street preferred); false when there is none. */
    boolean placeNear(Site s, ScenarioSheet.Place place, int[][] roadSum, List<Site> others) {
        int ox = s.x, oz = s.z;
        String keep = s.front;
        int bestX = 0, bestZ = 0, best = Integer.MAX_VALUE;
        String bestFront = keep;
        for (int x = MARGIN; x + s.w <= size - MARGIN; x++) {
            for (int z = MARGIN; z + s.d <= size - MARGIN; z++) {
                int dist = Math.abs(x - ox) + Math.abs(z - oz);
                if (dist * 2 >= best) {
                    continue;
                }
                s.x = x;
                s.z = z;
                s.front = keep;
                if (!fitsAmong(s, place, roadSum, others, false)) {
                    continue;
                }
                // A spot whose front can reach a street costs less than one that needs a footpath.
                int score = dist * 2 + 40;
                String front = keep;
                if (s.outdoor) {
                    score = dist * 2;
                } else {
                    for (String f : FRONTS) {
                        s.front = f;
                        if (fitsAmong(s, place, roadSum, others, true)) {
                            score = dist * 2;
                            front = f;
                            break;
                        }
                    }
                }
                if (score < best) {
                    best = score;
                    bestX = x;
                    bestZ = z;
                    bestFront = front;
                }
            }
        }
        if (best == Integer.MAX_VALUE) {
            s.x = ox;
            s.z = oz;
            s.front = keep;
            return false;
        }
        s.x = bestX;
        s.z = bestZ;
        s.front = bestFront;
        return true;
    }

    /** The side of the site that looks toward the nearest street (or keep, when there is none). */
    private String towardRoad(Site s, String keep) {
        boolean[][] road = roadGrid();
        int cx = s.x + s.w / 2, cz = s.z + s.d / 2;
        int best = Integer.MAX_VALUE, bx = cx, bz = cz;
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                if (road[x][z]) {
                    int d = Math.abs(x - cx) + Math.abs(z - cz);
                    if (d < best) {
                        best = d;
                        bx = x;
                        bz = z;
                    }
                }
            }
        }
        if (best == Integer.MAX_VALUE) {
            return keep;
        }
        int dx = bx - cx, dz = bz - cz;
        return Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? "east" : "west") : (dz > 0 ? "south" : "north");
    }

    /**
     * Cells a footpath centre line may use: on the ground, out of water, and (for a 3-wide path)
     * at least 2 blocks from every site, so the path never covers a building.
     */
    private boolean[][] laneFree(int clearance) {
        boolean[][] free = new boolean[size][size];
        for (int x = MARGIN; x < size - MARGIN; x++) {
            for (int z = MARGIN; z < size - MARGIN; z++) {
                free[x][z] = true;
            }
        }
        for (Site o : sites) {
            for (int x = o.x - clearance; x < o.x + o.w + clearance; x++) {
                for (int z = o.z - clearance; z < o.z + o.d + clearance; z++) {
                    if (x >= 0 && z >= 0 && x < size && z < size) {
                        free[x][z] = false;
                    }
                }
            }
        }
        for (Zone zn : zones) {
            if ("water".equals(zn.kind)) {
                for (int x = zn.x; x < zn.x + zn.w; x++) {
                    for (int z = zn.z; z < zn.z + zn.d; z++) {
                        if (x >= 0 && z >= 0 && x < size && z < size) {
                            free[x][z] = false;
                        }
                    }
                }
            }
        }
        return free;
    }

    /** A footpath from in front of the site to the nearest street; false when none can be laid. */
    boolean laneFrom(Site s) {
        int[] f = frontPoint(s);
        int[] out = switch (s.front) {
            case "north" -> new int[] { 0, -1 };
            case "east" -> new int[] { 1, 0 };
            case "west" -> new int[] { -1, 0 };
            default -> new int[] { 0, 1 };
        };
        boolean[][] road = roadGrid();
        // A 3-wide path where there is room, else a 1-wide one.
        for (int width : new int[] { 3, 1 }) {
            int clearance = width == 3 ? 2 : 0;
            int[] start = width == 3 ? new int[] { f[0] + out[0], f[1] + out[1] } : f;
            List<int[]> path = bfs(List.of(start), road, laneFree(clearance));
            if (path != null) {
                Road lane = new Road();
                lane.kind = "path";
                lane.width = width;
                lane.points = corners(path);
                if (lane.points.size() == 1) {
                    lane.points.add(lane.points.get(0));
                }
                roads.add(lane);
                return true;
            }
        }
        return false;
    }

    /** Join every separate piece of street to the biggest one with footpaths; how many were joined. */
    int joinRoadPieces() {
        int joined = 0;
        for (int guard = 0; guard < 12; guard++) {
            boolean[][] road = roadGrid();
            List<List<int[]>> pieces = pieceCells(road);
            if (pieces.size() <= 1) {
                break;
            }
            pieces.sort(Comparator.comparingInt((List<int[]> p) -> -p.size()));
            boolean[][] main = new boolean[size][size];
            for (int[] c : pieces.get(0)) {
                main[c[0]][c[1]] = true;
            }
            boolean done = false;
            for (int width : new int[] { 3, 1 }) {
                boolean[][] free = laneFree(width == 3 ? 2 : 0);
                List<int[]> path = bfs(pieces.get(1), main, free);
                if (path != null) {
                    Road lane = new Road();
                    lane.kind = "path";
                    lane.width = width;
                    lane.points = corners(path);
                    if (lane.points.size() == 1) {
                        lane.points.add(lane.points.get(0));
                    }
                    roads.add(lane);
                    joined++;
                    done = true;
                    break;
                }
            }
            if (!done) {
                break;
            }
        }
        return joined;
    }

    private List<List<int[]>> pieceCells(boolean[][] road) {
        boolean[][] seen = new boolean[size][size];
        List<List<int[]>> out = new ArrayList<>();
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                if (road[x][z] && !seen[x][z]) {
                    List<int[]> cells = new ArrayList<>();
                    ArrayDeque<int[]> q = new ArrayDeque<>();
                    q.add(new int[] { x, z });
                    seen[x][z] = true;
                    while (!q.isEmpty()) {
                        int[] c = q.poll();
                        cells.add(c);
                        for (int[] d : new int[][] { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } }) {
                            int nx = c[0] + d[0], nz = c[1] + d[1];
                            if (nx >= 0 && nz >= 0 && nx < size && nz < size && road[nx][nz] && !seen[nx][nz]) {
                                seen[nx][nz] = true;
                                q.add(new int[] { nx, nz });
                            }
                        }
                    }
                    out.add(cells);
                }
            }
        }
        return out;
    }

    /**
     * Shortest 4-way path from any start to a target cell, through free cells (starts and targets
     * may be anywhere); the cells from start to target, or null.
     */
    private List<int[]> bfs(List<int[]> starts, boolean[][] target, boolean[][] free) {
        int[][] from = new int[size][size];
        for (int[] row : from) {
            java.util.Arrays.fill(row, -2);
        }
        ArrayDeque<int[]> q = new ArrayDeque<>();
        for (int[] s : starts) {
            if (s[0] < 0 || s[1] < 0 || s[0] >= size || s[1] >= size || from[s[0]][s[1]] != -2) {
                continue;
            }
            if (target[s[0]][s[1]] && starts.size() == 1) {
                return List.of(s);
            }
            from[s[0]][s[1]] = -1;
            q.add(s);
        }
        while (!q.isEmpty()) {
            int[] c = q.poll();
            for (int[] d : new int[][] { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } }) {
                int nx = c[0] + d[0], nz = c[1] + d[1];
                if (nx < 0 || nz < 0 || nx >= size || nz >= size || from[nx][nz] != -2) {
                    continue;
                }
                if (target[nx][nz]) {
                    from[nx][nz] = c[0] * size + c[1];
                    List<int[]> path = new ArrayList<>();
                    int cur = nx * size + nz;
                    while (cur >= 0) {
                        int px = cur / size, pz = cur % size;
                        path.add(0, new int[] { px, pz });
                        cur = from[px][pz];
                    }
                    return path;
                }
                if (!free[nx][nz]) {
                    continue;
                }
                from[nx][nz] = c[0] * size + c[1];
                q.add(new int[] { nx, nz });
            }
        }
        return null;
    }

    /** A cell path as the points where it turns. */
    private static List<int[]> corners(List<int[]> path) {
        List<int[]> out = new ArrayList<>();
        for (int i = 0; i < path.size(); i++) {
            if (i == 0 || i == path.size() - 1) {
                out.add(path.get(i));
                continue;
            }
            int[] a = path.get(i - 1), b = path.get(i), c = path.get(i + 1);
            if ((b[0] - a[0]) != (c[0] - b[0]) || (b[1] - a[1]) != (c[1] - b[1])) {
                out.add(b);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ automatic layout

    /**
     * A plain layout without AI: the sea and the woods along their edges, a main street across the
     * town parallel to the coast, buildings in rows on both sides with their fronts on the street
     * (a second street further in when a row is full) and spread out along it, outdoor places
     * joined by paths, and the ground left over filled with gardens and fields.
     */
    public static TownLayout auto(ScenarioSheet sheet) {
        TownLayout l = new TownLayout();
        // The ground grows when the lots need it.
        final int n = ScenarioSheet.groundSize(sheet);
        l.size = n;
        String coast = sheet.terrain.coast;
        String woods = sheet.terrain.woods;
        // Work in a frame where the coast (if any) is south; turn the result at the end.
        int turns = switch (coast) {
            case "east" -> 1;
            case "north" -> 2;
            case "west" -> 3;
            default -> 0;
        };
        int south = n;
        if (!"none".equals(coast)) {
            Zone sea = new Zone();
            sea.kind = "water";
            sea.x = 0;
            sea.z = n - 18;
            sea.w = n;
            sea.d = 18;
            l.zones.add(sea);
            Zone beach = new Zone();
            beach.kind = "beach";
            beach.x = 0;
            beach.z = n - 22;
            beach.w = n;
            beach.d = 4;
            l.zones.add(beach);
            south = n - 22;
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
                    forest.w = n;
                    forest.d = 16;
                    north = 16;
                }
                case "east" -> {
                    forest.x = n - 14;
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
        buildings.sort(Comparator.comparingInt((ScenarioSheet.Place p) -> -ScenarioSheet.footprint(p)));
        // Streets: rows of buildings on both sides, from the coast side inward.
        int west = "west".equals(woodsLocal) ? 16 : MARGIN + 2;
        int east = "east".equals(woodsLocal) ? n - 16 : n - MARGIN - 2;
        int streetZ = Math.min(south - 36, (north + south) / 2 + 12);
        // Modern towns get wider main streets: curb, two lanes, a middle line, two lanes, curb.
        boolean modern = com.foredayner.scenario.sheet.Period.contemporary(sheet.period());
        int roadWidth = modern ? 7 : 5;
        int half = roadWidth / 2;
        List<int[]> streets = new ArrayList<>();
        int cursorA = west, cursorB = west; // A: north of the street, B: south of it
        int depthA = 0, depthB = 0;
        java.util.Map<Integer, List<Site>> rows = new java.util.TreeMap<>(); // street * 2 + side -> sites
        for (ScenarioSheet.Place p : buildings) {
            int f = ScenarioSheet.footprint(p);
            boolean toA = cursorA <= cursorB;
            if ((toA ? cursorA : cursorB) + f > east) {
                toA = !toA;
            }
            if ((toA ? cursorA : cursorB) + f > east) {
                // Row full: start a new street further inland.
                streets.add(new int[] { streetZ, west, Math.max(cursorA, cursorB) });
                streetZ = streetZ - Math.max(depthA, 21) - 10 - (half - 2) * 2;
                cursorA = cursorB = west;
                depthA = depthB = 0;
                toA = true;
            }
            Site s = new Site();
            s.id = p.id;
            s.w = s.d = f;
            if (toA) {
                s.x = cursorA;
                s.z = streetZ - half - 1 - f;
                s.front = "south";
                cursorA += f + 5;
                depthA = Math.max(depthA, f);
                rows.computeIfAbsent(streets.size() * 2, k -> new ArrayList<>()).add(s);
            } else {
                s.x = cursorB;
                s.z = streetZ + half + 2;
                s.front = "north";
                cursorB += f + 5;
                depthB = Math.max(depthB, f);
                rows.computeIfAbsent(streets.size() * 2 + 1, k -> new ArrayList<>()).add(s);
            }
            l.sites.add(s);
        }
        streets.add(new int[] { streetZ, west, Math.max(cursorA, cursorB) });
        // Spread each row along its street (wider gaps when there are few buildings), centred.
        for (List<Site> row : rows.values()) {
            int sum = 0;
            for (Site s : row) {
                sum += s.w;
            }
            int lo = west + 3; // clear of the lane along the west
            int gap = Math.max(5, Math.min(22, (east - lo - sum) / (row.size() + 1)));
            int x = (lo + east) / 2 - (sum + gap * (row.size() - 1)) / 2;
            for (Site s : row) {
                s.x = Math.max(lo, x);
                x = s.x + s.w + gap;
            }
        }
        int minZ = n;
        for (Site s : l.sites) {
            minZ = Math.min(minZ, s.z);
        }
        // Outdoor places: docks and beaches on the shore, the rest in a band inland of all
        // buildings; each where a straight path reaches the nearest street without crossing a building.
        int mainZ = streets.get(0)[0];
        int innerZ = streets.get(streets.size() - 1)[0];
        List<Road> paths = new ArrayList<>();
        List<Site> buildingSites = new ArrayList<>(l.sites);
        for (ScenarioSheet.Place p : outdoor) {
            int f = ScenarioSheet.footprint(p);
            boolean water = ("dock".equals(p.kind) || "beach".equals(p.kind)) && south < n;
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
        int spineX = west - 1;
        for (int[] st : streets) {
            Road r = new Road();
            r.width = roadWidth;
            r.points.add(new int[] { Math.max(MARGIN + 1, west - 2), st[0] });
            r.points.add(new int[] { Math.min(n - MARGIN - 2, east + 1), st[0] });
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
        // Anything that did not fit (a crowded town) goes to the free spot nearest the middle; with no
        // free spot left it gets no site, and the check says so.
        List<Site> placed = new ArrayList<>(l.sites);
        l.sites.clear();
        int[][] roadSum = l.roadSums();
        for (Site s : placed) {
            if (l.fits(s, sheet.place(s.id), roadSum) || l.placeFree(s, sheet.place(s.id), roadSum)) {
                l.sites.add(s);
            }
        }
        l.fillGround(west, east, north, south, streets, modern);
        l.turn(turns);
        return l;
    }

    /**
     * Fill the open ground (no site, street or zone near) with gardens near the streets and
     * fields further out, in cells of 12 blocks joined along each row.
     */
    private void fillGround(int west, int east, int north, int south, List<int[]> streets, boolean modern) {
        final int cell = 12;
        boolean[][] road = roadGrid();
        List<Zone> added = new ArrayList<>();
        for (int z = north + MARGIN; z + cell <= south - MARGIN; z += cell) {
            Zone run = null;
            for (int x = west; x + cell <= east; x += cell) {
                boolean free = true;
                for (Site s : sites) {
                    if (overlaps(x, z, cell, cell, s.x, s.z, s.w, s.d, 3)) {
                        free = false;
                        break;
                    }
                }
                for (Zone o : zones) {
                    if (free && overlaps(x, z, cell, cell, o.x, o.z, o.w, o.d, 0)) {
                        free = false;
                    }
                }
                for (int dx = -2; free && dx < cell + 2; dx++) {
                    for (int dz = -2; dz < cell + 2; dz++) {
                        int gx = x + dx, gz = z + dz;
                        if (gx >= 0 && gz >= 0 && gx < size && gz < size && road[gx][gz]) {
                            free = false;
                            break;
                        }
                    }
                }
                if (!free) {
                    run = null;
                    continue;
                }
                int near = Integer.MAX_VALUE;
                for (int[] st : streets) {
                    near = Math.min(near, Math.abs(z + cell / 2 - st[0]));
                }
                // Old towns: gardens by the street, fields beyond. Modern towns: parking lots and
                // little parks by the street, parks beyond.
                String kind = modern ? (near < 14 && Math.floorMod(x / cell * 3 + z / cell * 2, 7) == 0 ? "parking" : "park")
                    : near < 34 ? "garden" : "field";
                if (run != null && run.kind.equals(kind)) {
                    run.w += cell;
                } else {
                    run = new Zone();
                    run.kind = kind;
                    run.x = x;
                    run.z = z;
                    run.w = cell;
                    run.d = cell;
                    added.add(run);
                }
            }
        }
        zones.addAll(added);
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

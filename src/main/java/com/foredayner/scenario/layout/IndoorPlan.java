package com.foredayner.scenario.layout;

import com.foredayner.scenario.sheet.ScenarioSheet;
import com.google.gson.JsonParser;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The floor plan of a building stage: the sheet's places are rooms on floors, kept as the sites of
 * a {@link TownLayout} (with their level, and their own width and depth, walls included), plus the
 * doors in the walls and the flights of stairs between floors. Neighbouring rooms share a wall: the
 * next room starts on the last column of the one before. Corridors and halls that are not places
 * have ids starting with "_" (built, not furnished).
 *
 * <p>Checked and repaired by code (rooms apart, doors where they can be, every room reachable from
 * the entrance, stairs between floors), and set in the middle of the ground with a path to the edge.
 */
public final class IndoorPlan {

    /** A floor's height: the floor block and 4 blocks of room above it. */
    public static final int FLOOR_H = 5;
    /** Steps in a flight (they climb one floor). */
    public static final int STAIR_RUN = FLOOR_H - 1;
    /** Most blocks a building may span. */
    public static final int MAX_SIDE = 100;
    public static final int MIN_SIDE = 4;

    private IndoorPlan() {
    }

    /** A room's side (walls included) from its sheet size. */
    public static int side(String size) {
        return switch (size == null ? "M" : size) {
            case "S" -> 6;
            case "L" -> 11;
            case "XL" -> 15;
            default -> 8;
        };
    }

    static int[] step(String dir) {
        return switch (dir == null ? "" : dir) {
            case "east" -> new int[] { 1, 0 };
            case "south" -> new int[] { 0, 1 };
            case "west" -> new int[] { -1, 0 };
            default -> new int[] { 0, -1 };
        };
    }

    // ------------------------------------------------------------------ the AI's answer

    /** {"rooms": [...], "doors": [...], "stairs": [...]} in the building's own frame; set on the ground. */
    public static TownLayout fromAnswer(String answer, ScenarioSheet sheet) {
        int start = answer.indexOf('{');
        int end = answer.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalArgumentException("no JSON object in the answer");
        }
        var root = JsonParser.parseString(answer.substring(start, end + 1)).getAsJsonObject();
        TownLayout l = new TownLayout();
        l.stage = "building";
        if (root.has("rooms")) {
            for (var e : root.getAsJsonArray("rooms")) {
                var o = e.getAsJsonObject();
                String raw = o.get("id").getAsString().trim();
                String id = raw.startsWith("_") ? "_" + ScenarioSheet.slug(raw.substring(1)) : ScenarioSheet.slug(raw);
                if (!id.startsWith("_") && sheet.place(id) == null) {
                    throw new IllegalArgumentException("room '" + id + "' is not in the list (corridors and halls get ids starting with _)");
                }
                TownLayout.Site s = new TownLayout.Site();
                s.id = id;
                s.x = o.get("x").getAsInt();
                s.z = o.get("z").getAsInt();
                s.w = o.has("w") ? o.get("w").getAsInt() : side(id.startsWith("_") ? "S" : sheet.place(id).size);
                s.d = o.has("d") ? o.get("d").getAsInt() : s.w;
                s.level = o.has("level") ? o.get("level").getAsInt() : id.startsWith("_") ? 0 : sheet.place(id).level;
                s.fromMap = true;
                l.sites.add(s);
            }
        }
        if (root.has("doors")) {
            for (var e : root.getAsJsonArray("doors")) {
                var o = e.getAsJsonObject();
                TownLayout.Door d = new TownLayout.Door();
                d.x = o.get("x").getAsInt();
                d.z = o.get("z").getAsInt();
                d.level = o.has("level") ? o.get("level").getAsInt() : 0;
                d.entrance = o.has("entrance") && o.get("entrance").getAsBoolean();
                l.doors.add(d);
            }
        }
        if (root.has("stairs")) {
            for (var e : root.getAsJsonArray("stairs")) {
                var o = e.getAsJsonObject();
                TownLayout.Stair st = new TownLayout.Stair();
                st.x = o.get("x").getAsInt();
                st.z = o.get("z").getAsInt();
                st.level = o.has("level") ? o.get("level").getAsInt() : 0;
                st.dir = o.has("dir") ? o.get("dir").getAsString().toLowerCase(Locale.ROOT) : "north";
                l.stairs.add(st);
            }
        }
        return l;
    }

    /** The plan in the answer's shape, in the building's own frame (for asking the AI to change it). */
    public static String answerJson(TownLayout l) {
        int[] b = bounds(l);
        StringBuilder sb = new StringBuilder("{\"rooms\":[");
        for (int i = 0; i < l.sites.size(); i++) {
            TownLayout.Site s = l.sites.get(i);
            sb.append(i == 0 ? "" : ",").append("\n {\"id\":\"").append(s.id).append("\",\"level\":").append(s.level)
                .append(",\"x\":").append(s.x - b[0]).append(",\"z\":").append(s.z - b[1]).append(",\"w\":").append(s.w)
                .append(",\"d\":").append(s.d).append('}');
        }
        sb.append("],\n\"doors\":[");
        for (int i = 0; i < l.doors.size(); i++) {
            TownLayout.Door d = l.doors.get(i);
            sb.append(i == 0 ? "" : ",").append("\n {\"x\":").append(d.x - b[0]).append(",\"z\":").append(d.z - b[1])
                .append(",\"level\":").append(d.level).append(d.entrance ? ",\"entrance\":true" : "").append('}');
        }
        sb.append("],\n\"stairs\":[");
        for (int i = 0; i < l.stairs.size(); i++) {
            TownLayout.Stair st = l.stairs.get(i);
            sb.append(i == 0 ? "" : ",").append("\n {\"x\":").append(st.x - b[0]).append(",\"z\":").append(st.z - b[1])
                .append(",\"level\":").append(st.level).append(",\"dir\":\"").append(st.dir).append("\"}");
        }
        return sb.append("]}").toString();
    }

    /** {minX, minZ, maxX, maxZ} (inclusive) of all rooms on all floors. */
    public static int[] bounds(TownLayout l) {
        int[] b = { Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE };
        for (TownLayout.Site s : l.sites) {
            b[0] = Math.min(b[0], s.x);
            b[1] = Math.min(b[1], s.z);
            b[2] = Math.max(b[2], s.x + s.w - 1);
            b[3] = Math.max(b[3], s.z + s.d - 1);
        }
        return l.sites.isEmpty() ? new int[] { 0, 0, -1, -1 } : b;
    }

    // ------------------------------------------------------------------ geometry

    /** The room on that floor whose inside (walls excluded) holds (x, z), or null. */
    static TownLayout.Site roomInside(TownLayout l, int level, int x, int z) {
        for (TownLayout.Site s : l.sites) {
            if (s.level == level && x > s.x && x < s.x + s.w - 1 && z > s.z && z < s.z + s.d - 1) {
                return s;
            }
        }
        return null;
    }

    /** Rooms on that floor whose walls (not corners) hold (x, z). */
    static List<TownLayout.Site> wallsAt(TownLayout l, int level, int x, int z) {
        List<TownLayout.Site> out = new ArrayList<>();
        for (TownLayout.Site s : l.sites) {
            if (s.level != level || x < s.x || z < s.z || x > s.x + s.w - 1 || z > s.z + s.d - 1) {
                continue;
            }
            boolean onX = x == s.x || x == s.x + s.w - 1;
            boolean onZ = z == s.z || z == s.z + s.d - 1;
            if (onX != onZ) {
                out.add(s);
            }
        }
        return out;
    }

    /** Inside some room's rectangle on that floor (walls included). */
    static boolean covered(TownLayout l, int level, int x, int z) {
        for (TownLayout.Site s : l.sites) {
            if (s.level == level && x >= s.x && z >= s.z && x < s.x + s.w && z < s.z + s.d) {
                return true;
            }
        }
        return false;
    }

    /** The two cells a door joins: {room on one side, room on the other or null for outside}. */
    static TownLayout.Site[] joins(TownLayout l, TownLayout.Door d) {
        List<TownLayout.Site> walls = wallsAt(l, d.level, d.x, d.z);
        if (walls.isEmpty()) {
            return null;
        }
        TownLayout.Site a = walls.get(0);
        boolean vertical = d.x == a.x || d.x == a.x + a.w - 1; // a wall running north-south
        int[][] sides = vertical ? new int[][] { { d.x - 1, d.z }, { d.x + 1, d.z } } : new int[][] { { d.x, d.z - 1 }, { d.x, d.z + 1 } };
        TownLayout.Site one = roomInside(l, d.level, sides[0][0], sides[0][1]);
        TownLayout.Site two = roomInside(l, d.level, sides[1][0], sides[1][1]);
        boolean outOne = !covered(l, d.level, sides[0][0], sides[0][1]);
        boolean outTwo = !covered(l, d.level, sides[1][0], sides[1][1]);
        if (one != null && two != null) {
            return new TownLayout.Site[] { one, two };
        }
        if (one != null && outTwo) {
            return new TownLayout.Site[] { one, null };
        }
        if (two != null && outOne) {
            return new TownLayout.Site[] { two, null };
        }
        return null;
    }

    /** The cells of a flight: steps 0..STAIR_RUN-1 then the landing on the floor above. */
    static int[][] flight(TownLayout.Stair st) {
        int[] s = step(st.dir);
        int[][] out = new int[STAIR_RUN + 1][];
        for (int i = 0; i <= STAIR_RUN; i++) {
            out[i] = new int[] { st.x + s[0] * i, st.z + s[1] * i };
        }
        return out;
    }

    /** {room below, room above} a flight joins, or null when it does not fit. */
    static TownLayout.Site[] stairJoins(TownLayout l, TownLayout.Stair st) {
        int[][] f = flight(st);
        TownLayout.Site below = roomInside(l, st.level, f[0][0], f[0][1]);
        TownLayout.Site above = roomInside(l, st.level + 1, f[STAIR_RUN][0], f[STAIR_RUN][1]);
        if (below == null || above == null) {
            return null;
        }
        for (int i = 0; i < STAIR_RUN; i++) {
            if (roomInside(l, st.level, f[i][0], f[i][1]) != below) {
                return null;
            }
        }
        for (int i = 1; i <= STAIR_RUN; i++) {
            if (roomInside(l, st.level + 1, f[i][0], f[i][1]) != above) {
                return null; // the hole in the floor above must not cut its walls
            }
        }
        return new TownLayout.Site[] { below, above };
    }

    private static String key(TownLayout.Site s) {
        return s.level + ":" + s.id;
    }

    /** Rooms reachable from the entrance through doors and stairs (by "level:id"). */
    static Set<String> reachable(TownLayout l) {
        Map<String, List<TownLayout.Site>> next = new HashMap<>();
        TownLayout.Site start = null;
        for (TownLayout.Door d : l.doors) {
            TownLayout.Site[] j = joins(l, d);
            if (j == null) {
                continue;
            }
            if (j[1] == null) {
                if (d.entrance) {
                    start = j[0];
                }
                continue;
            }
            next.computeIfAbsent(key(j[0]), k -> new ArrayList<>()).add(j[1]);
            next.computeIfAbsent(key(j[1]), k -> new ArrayList<>()).add(j[0]);
        }
        for (TownLayout.Stair st : l.stairs) {
            TownLayout.Site[] j = stairJoins(l, st);
            if (j != null) {
                next.computeIfAbsent(key(j[0]), k -> new ArrayList<>()).add(j[1]);
                next.computeIfAbsent(key(j[1]), k -> new ArrayList<>()).add(j[0]);
            }
        }
        Set<String> seen = new HashSet<>();
        if (start == null) {
            return seen;
        }
        ArrayDeque<TownLayout.Site> q = new ArrayDeque<>();
        q.add(start);
        seen.add(key(start));
        while (!q.isEmpty()) {
            TownLayout.Site s = q.poll();
            for (TownLayout.Site n : next.getOrDefault(key(s), List.of())) {
                if (seen.add(key(n))) {
                    q.add(n);
                }
            }
        }
        return seen;
    }

    // ------------------------------------------------------------------ checks

    public static List<String> check(TownLayout l, ScenarioSheet sheet) {
        List<String> problems = new ArrayList<>();
        l.sites.removeIf(s -> !s.id.startsWith("_") && sheet.place(s.id) == null);
        for (TownLayout.Site s : l.sites) {
            s.outdoor = false;
            if (s.w < MIN_SIDE || s.d < MIN_SIDE) {
                problems.add(s.id + " is " + s.w + " x " + s.d + "; a room is at least " + MIN_SIDE + " x " + MIN_SIDE + " (walls included)");
            }
            if (s.x < TownLayout.MARGIN || s.z < TownLayout.MARGIN || s.x + s.w > l.size - TownLayout.MARGIN
                || s.z + s.d > l.size - TownLayout.MARGIN) {
                problems.add(s.id + " leaves the ground");
            }
        }
        for (ScenarioSheet.Place p : sheet.places) {
            if (l.site(p.id) == null) {
                problems.add("room " + p.id + " is not in the plan");
            }
        }
        for (int i = 0; i < l.sites.size(); i++) {
            for (int j = i + 1; j < l.sites.size(); j++) {
                TownLayout.Site a = l.sites.get(i), b = l.sites.get(j);
                if (a.level == b.level && overlap(a, b)) {
                    problems.add(a.id + " and " + b.id + " overlap (neighbouring rooms may only share one wall)");
                }
            }
        }
        boolean entrance = false;
        for (TownLayout.Door d : l.doors) {
            TownLayout.Site[] j = joins(l, d);
            if (j == null) {
                problems.add("the door at (" + d.x + "," + d.z + ") on level " + d.level + " is not in a wall between two rooms or to the outside");
            } else if (d.entrance && j[1] == null) {
                entrance = true;
            }
        }
        if (!entrance) {
            problems.add("there is no entrance: a door with \"entrance\": true in an outside wall");
        }
        for (TownLayout.Stair st : l.stairs) {
            if (stairJoins(l, st) == null) {
                problems.add("the stairs at (" + st.x + "," + st.z + ") on level " + st.level + " do not fit: " + STAIR_RUN
                    + " steps inside one room, landing inside a room on the floor above");
            }
        }
        if (entrance && problems.isEmpty()) {
            Set<String> reached = reachable(l);
            for (TownLayout.Site s : l.sites) {
                if (!reached.contains(key(s))) {
                    problems.add(s.id + " cannot be reached from the entrance (add a door or stairs)");
                }
            }
        }
        return problems;
    }

    /** More than a shared wall in common. */
    static boolean overlap(TownLayout.Site a, TownLayout.Site b) {
        int ox = Math.min(a.x + a.w, b.x + b.w) - Math.max(a.x, b.x);
        int oz = Math.min(a.z + a.d, b.z + b.d) - Math.max(a.z, b.z);
        return ox > 1 && oz > 1;
    }

    // ------------------------------------------------------------------ repair

    /**
     * Fix what code can: rooms too small grow, rooms off the ground move on, doors that are not in a
     * wall go, an entrance is added if there is none, rooms that cannot be reached get a door to a
     * neighbour, floors with no stairs get a flight where two rooms overlap, and the building is set
     * in the middle of the ground with a path out from its entrance. What was done, in Korean.
     */
    public static List<String> repair(TownLayout l, ScenarioSheet sheet) {
        List<String> notes = new ArrayList<>();
        l.stage = "building";
        l.sites.removeIf(s -> !s.id.startsWith("_") && sheet.place(s.id) == null);
        for (TownLayout.Site s : l.sites) {
            s.outdoor = false;
            s.w = Math.max(MIN_SIDE, Math.min(40, s.w));
            s.d = Math.max(MIN_SIDE, Math.min(40, s.d));
        }
        int before = l.doors.size();
        l.doors.removeIf(d -> joins(l, d) == null);
        if (l.doors.size() < before) {
            notes.add("벽에 있지 않은 문 " + (before - l.doors.size()) + "개를 뺐어요");
        }
        int stairsBefore = l.stairs.size();
        l.stairs.removeIf(st -> stairJoins(l, st) == null);
        if (l.stairs.size() < stairsBefore) {
            notes.add("맞지 않는 계단 " + (stairsBefore - l.stairs.size()) + "곳을 뺐어요");
        }
        if (l.doors.stream().noneMatch(d -> d.entrance && joins(l, d) != null && joins(l, d)[1] == null)) {
            if (addEntrance(l)) {
                notes.add("입구를 만들었어요");
            }
        }
        int added = 0;
        for (int guard = 0; guard < 40; guard++) {
            Set<String> reached = reachable(l);
            TownLayout.Site lonely = null;
            for (TownLayout.Site s : l.sites) {
                if (!reached.contains(key(s))) {
                    lonely = s;
                    break;
                }
            }
            if (lonely == null || reached.isEmpty()) {
                break;
            }
            if (!connect(l, lonely, reached)) {
                break;
            }
            added++;
        }
        if (added > 0) {
            notes.add("막힌 방에 문이나 계단 " + added + "곳을 이었어요");
        }
        center(l);
        return notes;
    }

    /** A door on the south-most outside wall of a ground floor room. */
    static boolean addEntrance(TownLayout l) {
        int ground = lowestLevelWithRooms(l, 0);
        TownLayout.Site best = null;
        for (TownLayout.Site s : l.sites) {
            if (s.level == ground && (best == null || s.z + s.d > best.z + best.d)) {
                best = s;
            }
        }
        if (best == null) {
            return false;
        }
        // Try the four sides, south first, from the middle out.
        String[] order = { "south", "east", "west", "north" };
        for (TownLayout.Site s : sortedBy(l, ground, best)) {
            for (String side : order) {
                for (int[] c : sideCells(s, side)) {
                    TownLayout.Door d = new TownLayout.Door();
                    d.x = c[0];
                    d.z = c[1];
                    d.level = ground;
                    d.entrance = true;
                    TownLayout.Site[] j = joins(l, d);
                    if (j != null && j[1] == null) {
                        l.doors.add(d);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static List<TownLayout.Site> sortedBy(TownLayout l, int level, TownLayout.Site first) {
        List<TownLayout.Site> out = new ArrayList<>();
        out.add(first);
        for (TownLayout.Site s : l.sites) {
            if (s.level == level && s != first) {
                out.add(s);
            }
        }
        return out;
    }

    /** The ground floor if it has rooms, else the lowest floor that has. */
    static int lowestLevelWithRooms(TownLayout l, int preferred) {
        boolean any = false;
        int low = Integer.MAX_VALUE;
        for (TownLayout.Site s : l.sites) {
            any |= s.level == preferred;
            low = Math.min(low, s.level);
        }
        return any ? preferred : low;
    }

    /** Wall cells of one side of a room, the middle first, corners left out. */
    static List<int[]> sideCells(TownLayout.Site s, String side) {
        List<int[]> cells = new ArrayList<>();
        boolean alongX = "north".equals(side) || "south".equals(side);
        int len = alongX ? s.w : s.d;
        int mid = len / 2;
        for (int k = 0; k < len; k++) {
            int i = mid + ((k % 2 == 0) ? k / 2 : -(k + 1) / 2);
            if (i <= 0 || i >= len - 1) {
                continue;
            }
            cells.add(switch (side) {
                case "north" -> new int[] { s.x + i, s.z };
                case "south" -> new int[] { s.x + i, s.z + s.d - 1 };
                case "west" -> new int[] { s.x, s.z + i };
                default -> new int[] { s.x + s.w - 1, s.z + i };
            });
        }
        return cells;
    }

    /** Join a room nobody can reach: a door to a reached neighbour, or stairs to a reached room above or below. */
    static boolean connect(TownLayout l, TownLayout.Site lonely, Set<String> reached) {
        for (String side : new String[] { "south", "north", "east", "west" }) {
            for (int[] c : sideCells(lonely, side)) {
                TownLayout.Door d = new TownLayout.Door();
                d.x = c[0];
                d.z = c[1];
                d.level = lonely.level;
                TownLayout.Site[] j = joins(l, d);
                if (j != null && j[1] != null && (reached.contains(key(j[0])) || reached.contains(key(j[1])))) {
                    l.doors.add(d);
                    return true;
                }
            }
        }
        // Stairs: from a reached room below, or up to... a reached room above (the flight then belongs below).
        for (TownLayout.Site other : l.sites) {
            if (!reached.contains(key(other)) || Math.abs(other.level - lonely.level) != 1) {
                continue;
            }
            TownLayout.Site below = other.level < lonely.level ? other : lonely;
            TownLayout.Site above = other.level < lonely.level ? lonely : other;
            TownLayout.Stair st = fitStairs(l, below, above);
            if (st != null) {
                l.stairs.add(st);
                return true;
            }
        }
        return false;
    }

    /** A flight from inside {@code below} landing inside {@code above}, along a wall where they overlap; or null. */
    static TownLayout.Stair fitStairs(TownLayout l, TownLayout.Site below, TownLayout.Site above) {
        for (String dir : new String[] { "north", "east", "south", "west" }) {
            for (int x = below.x + 1; x < below.x + below.w - 1; x++) {
                for (int z = below.z + 1; z < below.z + below.d - 1; z++) {
                    TownLayout.Stair st = new TownLayout.Stair();
                    st.x = x;
                    st.z = z;
                    st.level = below.level;
                    st.dir = dir;
                    TownLayout.Site[] j = stairJoins(l, st);
                    if (j != null && j[0] == below && j[1] == above && !blocksDoor(l, st)) {
                        return st;
                    }
                }
            }
        }
        return null;
    }

    /** A flight in front of a door would block it. */
    private static boolean blocksDoor(TownLayout l, TownLayout.Stair st) {
        for (int[] c : flight(st)) {
            for (TownLayout.Door d : l.doors) {
                if ((d.level == st.level || d.level == st.level + 1) && Math.abs(d.x - c[0]) + Math.abs(d.z - c[1]) <= 1) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Set the building in the middle of the ground (a little to the north, so the yard in front is
     * bigger) and lay a path from the entrance out to the edge, with a garden around the house.
     */
    static void center(TownLayout l) {
        int[] b = bounds(l);
        if (b[2] < b[0]) {
            return;
        }
        int w = b[2] - b[0] + 1, d = b[3] - b[1] + 1;
        int dx = (l.size - w) / 2 - b[0];
        int dz = Math.max(TownLayout.MARGIN + 6, (l.size - d) / 2 - 12) - b[1];
        for (TownLayout.Site s : l.sites) {
            s.x += dx;
            s.z += dz;
        }
        for (TownLayout.Door door : l.doors) {
            door.x += dx;
            door.z += dz;
        }
        for (TownLayout.Stair st : l.stairs) {
            st.x += dx;
            st.z += dz;
        }
        l.roads.clear();
        l.zones.clear();
        int[] nb = bounds(l);
        TownLayout.Zone garden = new TownLayout.Zone();
        garden.kind = "garden";
        garden.x = Math.max(0, nb[0] - 6);
        garden.z = Math.max(0, nb[1] - 6);
        garden.w = Math.min(l.size, nb[2] + 7) - garden.x;
        garden.d = Math.min(l.size, nb[3] + 7) - garden.z;
        l.zones.add(garden);
        for (TownLayout.Door door : l.doors) {
            if (!door.entrance) {
                continue;
            }
            TownLayout.Site[] j = joins(l, door);
            if (j == null || j[1] != null) {
                continue;
            }
            // Out through the wall, away from the room, then on to the nearest edge of the ground.
            TownLayout.Site room = j[0];
            int ox = door.x == room.x ? -1 : door.x == room.x + room.w - 1 ? 1 : 0;
            int oz = ox != 0 ? 0 : door.z == room.z ? -1 : 1;
            int sx = door.x + ox * 2, sz = door.z + oz * 2;
            int ex = ox < 0 ? 1 : ox > 0 ? l.size - 2 : sx;
            int ez = oz < 0 ? 1 : oz > 0 ? l.size - 2 : sz;
            TownLayout.Road path = new TownLayout.Road();
            path.kind = "path";
            path.width = 3;
            path.points.add(new int[] { sx, sz });
            path.points.add(new int[] { ex, ez });
            l.roads.add(path);
            break;
        }
    }

    // ------------------------------------------------------------------ automatic plan

    /**
     * A plain plan without AI: on each floor a corridor 5 wide from west to east with the rooms in
     * a row on each side, a door from each room to it, the entrance at the corridor's west end on the
     * ground floor, and stairs at the corridor's east end to the floor above.
     */
    public static TownLayout auto(ScenarioSheet sheet) {
        TownLayout l = new TownLayout();
        l.stage = "building";
        Map<Integer, List<ScenarioSheet.Place>> byLevel = new java.util.TreeMap<>();
        for (ScenarioSheet.Place p : sheet.places) {
            byLevel.computeIfAbsent(p.level, k -> new ArrayList<>()).add(p);
        }
        // Every floor's corridor runs the same length, so the stairs line up.
        int length = 0;
        for (List<ScenarioSheet.Place> rooms : byLevel.values()) {
            int north = 1, south = 1;
            List<ScenarioSheet.Place> sorted = new ArrayList<>(rooms);
            sorted.sort(Comparator.comparingInt((ScenarioSheet.Place p) -> -side(p.size)));
            for (ScenarioSheet.Place p : sorted) {
                if (north <= south) {
                    north += side(p.size) - 1;
                } else {
                    south += side(p.size) - 1;
                }
            }
            length = Math.max(length, Math.max(north, south) + STAIR_RUN + 4);
        }
        int hallD = 5;
        int rowD = 9;
        for (var e : byLevel.entrySet()) {
            int level = e.getKey();
            List<ScenarioSheet.Place> sorted = new ArrayList<>(e.getValue());
            sorted.sort(Comparator.comparingInt((ScenarioSheet.Place p) -> -side(p.size)));
            TownLayout.Site hall = new TownLayout.Site();
            hall.id = "_hall" + (level < 0 ? "_b" + (-level) : String.valueOf(level));
            hall.level = level;
            hall.x = 0;
            hall.z = rowD - 1;
            hall.w = length;
            hall.d = hallD;
            l.sites.add(hall);
            int north = 0, south = 0;
            for (ScenarioSheet.Place p : sorted) {
                int w = side(p.size);
                TownLayout.Site s = new TownLayout.Site();
                s.id = p.id;
                s.level = level;
                s.w = w;
                s.d = rowD;
                boolean toNorth = north <= south;
                s.x = toNorth ? north : south;
                s.z = toNorth ? 0 : rowD - 1 + hallD - 1;
                if (toNorth) {
                    north += w - 1;
                } else {
                    south += w - 1;
                }
                l.sites.add(s);
                TownLayout.Door d = new TownLayout.Door();
                d.x = s.x + w / 2;
                d.z = toNorth ? s.z + rowD - 1 : s.z;
                d.level = level;
                l.doors.add(d);
            }
        }
        int ground = byLevel.containsKey(0) ? 0 : byLevel.keySet().iterator().next();
        TownLayout.Door entrance = new TownLayout.Door();
        entrance.x = 0;
        entrance.z = rowD - 1 + hallD / 2;
        entrance.level = ground;
        entrance.entrance = true;
        l.doors.add(entrance);
        for (int level : byLevel.keySet()) {
            if (byLevel.containsKey(level + 1)) {
                TownLayout.Stair st = new TownLayout.Stair();
                st.x = length - STAIR_RUN - 2;
                st.z = rowD;
                st.level = level;
                st.dir = "east";
                l.stairs.add(st);
            }
        }
        center(l);
        return l;
    }
}

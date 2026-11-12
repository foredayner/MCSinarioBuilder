package com.foredayner.scenario.town;

import com.example.ai.build.BuildPlan;
import com.foredayner.scenario.layout.TownLayout;
import com.foredayner.scenario.sheet.ScenarioSheet;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Places the keeper put over each other are built to fit together instead of over each other:
 * <ul>
 * <li>a building in (or partly in) an outdoor place keeps its whole lot; the outdoor scene leaves
 * that part empty (told to the AI, and cut out of its design whatever it does);</li>
 * <li>two buildings, or two outdoor places, share the overlap: it is split down the middle and each
 * gets the smaller lot on its own side. Buildings become a row of joined buildings (the shared
 * wall right on the edge, no doors or windows in it); outdoor scenes run on into each other.</li>
 * </ul>
 * Done on the build's own copy of the layout (the saved one keeps where the keeper put things).
 */
public final class Overlaps {

    /** A lot smaller than this along either side is not split further (the two simply overlap). */
    static final int MIN_SIDE = 7;

    private Overlaps() {
    }

    /** A rectangle of layout cells, both ends included. */
    public record Rect(int x1, int z1, int x2, int z2) {
        boolean empty() {
            return x1 > x2 || z1 > z2;
        }

        Rect and(Rect o) {
            return new Rect(Math.max(x1, o.x1), Math.max(z1, o.z1), Math.min(x2, o.x2), Math.min(z2, o.z2));
        }

        static Rect of(TownLayout.Site s) {
            return new Rect(s.x, s.z, s.x + s.w - 1, s.z + s.d - 1);
        }
    }

    /** One side of a lot that meets another place (side: north, east, south or west). */
    public record Join(String side, String other, boolean building) {
    }

    /** A part of an outdoor lot another place stands on. */
    public record Hole(Rect rect, String other, String front) {
    }

    public static final class Result {
        /** Outdoor place -> the parts of its lot buildings stand on. */
        public final Map<String, List<Hole>> holes = new HashMap<>();
        /** Place -> the sides it shares with a neighbour it was split from. */
        public final Map<String, List<Join>> joins = new HashMap<>();
        /** What was done, for the log. */
        public final List<String> told = new ArrayList<>();

        public List<Hole> holes(String id) {
            return holes.getOrDefault(id, List.of());
        }

        public List<Join> joins(String id) {
            return joins.getOrDefault(id, List.of());
        }
    }

    /** Fit the overlapping places of this (build-time) layout to each other. */
    public static Result resolve(TownLayout layout, ScenarioSheet sheet) {
        Result r = new Result();
        if (layout.building()) {
            return r;
        }
        List<TownLayout.Site> sites = layout.sites;
        Set<String> stuck = new HashSet<>();
        for (int round = 0; round < 100; round++) {
            boolean changed = false;
            for (int i = 0; i < sites.size() && !changed; i++) {
                for (int j = i + 1; j < sites.size() && !changed; j++) {
                    TownLayout.Site a = sites.get(i), b = sites.get(j);
                    if (a.outdoor != b.outdoor || stuck.contains(a.id + "|" + b.id)) {
                        continue;
                    }
                    Rect o = Rect.of(a).and(Rect.of(b));
                    if (o.empty()) {
                        continue;
                    }
                    if (split(a, b, o, r, sheet)) {
                        changed = true;
                    } else {
                        stuck.add(a.id + "|" + b.id);
                        r.told.add(name(sheet, a) + "와(과) " + name(sheet, b) + "은(는) 너무 많이 겹쳐서 나누지 못했어요 (겹친 채로 지어요)");
                    }
                }
            }
            if (!changed) {
                break;
            }
        }
        // Buildings standing in outdoor places: holes in the scene.
        for (TownLayout.Site out : sites) {
            if (!out.outdoor) {
                continue;
            }
            for (TownLayout.Site in : sites) {
                if (in.outdoor) {
                    continue;
                }
                Rect o = Rect.of(out).and(Rect.of(in));
                if (!o.empty()) {
                    r.holes.computeIfAbsent(out.id, k -> new ArrayList<>()).add(new Hole(o, name(sheet, in), in.front));
                    r.told.add(name(sheet, out) + " 안의 " + name(sheet, in) + " 자리는 비워 두고 지어요");
                }
            }
        }
        return r;
    }

    /** Split the overlap o between a and b down its thinner side; false if a lot would get too small. */
    private static boolean split(TownLayout.Site a, TownLayout.Site b, Rect o, Result r, ScenarioSheet sheet) {
        int ow = o.x2() - o.x1() + 1, od = o.z2() - o.z1() + 1;
        double ax = a.x + a.w / 2.0, bx = b.x + b.w / 2.0, az = a.z + a.d / 2.0, bz = b.z + b.d / 2.0;
        boolean alongX = ow < od || ow == od && Math.abs(ax - bx) >= Math.abs(az - bz);
        if (alongX) {
            TownLayout.Site west = ax <= bx ? a : b, east = west == a ? b : a;
            int m = o.x1() + ow / 2;
            int westW = m - west.x, eastEnd = east.x + east.w;
            if (westW < MIN_SIDE || eastEnd - m < MIN_SIDE) {
                return false;
            }
            west.w = Math.min(west.w, westW);
            east.w = eastEnd - m;
            east.x = m;
            join(r, west, "east", east, sheet);
            join(r, east, "west", west, sheet);
        } else {
            TownLayout.Site north = az <= bz ? a : b, south = north == a ? b : a;
            int m = o.z1() + od / 2;
            int northD = m - north.z, southEnd = south.z + south.d;
            if (northD < MIN_SIDE || southEnd - m < MIN_SIDE) {
                return false;
            }
            north.d = Math.min(north.d, northD);
            south.d = southEnd - m;
            south.z = m;
            join(r, north, "south", south, sheet);
            join(r, south, "north", north, sheet);
        }
        r.told.add(name(sheet, a) + "와(과) " + name(sheet, b) + "이(가) 겹쳐서 "
            + (a.outdoor ? "땅을 나눠 이어지게" : "붙은 건물로") + " 지어요");
        return true;
    }

    private static void join(Result r, TownLayout.Site s, String side, TownLayout.Site other, ScenarioSheet sheet) {
        List<Join> list = r.joins.computeIfAbsent(s.id, k -> new ArrayList<>());
        list.removeIf(j -> j.side().equals(side) && j.other().equals(name(sheet, other)));
        list.add(new Join(side, name(sheet, other), !other.outdoor));
    }

    static String name(ScenarioSheet sheet, TownLayout.Site s) {
        ScenarioSheet.Place p = sheet == null ? null : sheet.place(s.id);
        return p == null || p.name == null || p.name.isBlank() ? s.id : p.name;
    }

    // ------------------------------------------------------------------ the design's frame

    /** Quarter turns from the design frame (front to the south) to the site's front. */
    static int turns(TownLayout.Site s) {
        return BuildPlan.turnsBetween("south", BuildPlan.normalizeFacing(s.front));
    }

    /** The AI's site for this lot: width runs along the front, so it is the lot's depth when the front is east or west. */
    public static int[] designSize(TownLayout.Site s) {
        return turns(s) % 2 == 0 ? new int[] { s.w, s.d } : new int[] { s.d, s.w };
    }

    /** Where the design frame's (0, 0) lands, relative to the lot's north-west corner (as AiBuildApi.placeBuilding does). */
    private static int[] offset(TownLayout.Site s) {
        int[] size = designSize(s);
        int t = turns(s);
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        for (int[] c : new int[][] { { 0, -size[1] }, { size[0] - 1, -size[1] }, { 0, -1 }, { size[0] - 1, -1 } }) {
            int[] q = BuildPlan.rotate(c[0], c[1], t);
            minX = Math.min(minX, q[0]);
            minZ = Math.min(minZ, q[1]);
        }
        return new int[] { -minX, -minZ };
    }

    /** A rectangle of layout cells in the lot's design frame: {x1, z1, x2, z2}. */
    public static int[] toDesign(TownLayout.Site s, Rect r) {
        int[] off = offset(s);
        int back = Math.floorMod(4 - turns(s), 4);
        int[] p = BuildPlan.rotate(r.x1() - s.x - off[0], r.z1() - s.z - off[1], back);
        int[] q = BuildPlan.rotate(r.x2() - s.x - off[0], r.z2() - s.z - off[1], back);
        return new int[] { Math.min(p[0], q[0]), Math.min(p[1], q[1]), Math.max(p[0], q[0]), Math.max(p[1], q[1]) };
    }

    /** A design cell back on the layout (tests). */
    static int[] toLayout(TownLayout.Site s, int x, int z) {
        int[] off = offset(s);
        int[] q = BuildPlan.rotate(x, z, turns(s));
        return new int[] { s.x + off[0] + q[0], s.z + off[1] + q[1] };
    }

    /** A side of the lot (north, east...) named as the AI sees its site. */
    static String edge(TownLayout.Site s, String side) {
        int[] size = designSize(s);
        int t = turns(s);
        int[] want = switch (side) {
            case "north" -> new int[] { 0, -1 };
            case "east" -> new int[] { 1, 0 };
            case "west" -> new int[] { -1, 0 };
            default -> new int[] { 0, 1 };
        };
        String[][] edges = {
            { "1", "0", "the right edge (x = " + (size[0] - 1) + ")" },
            { "-1", "0", "the left edge (x = 0)" },
            { "0", "1", "the front edge (z = -1)" },
            { "0", "-1", "the back edge (z = -" + size[1] + ")" } };
        for (String[] e : edges) {
            int[] q = BuildPlan.rotate(Integer.parseInt(e[0]), Integer.parseInt(e[1]), t);
            if (q[0] == want[0] && q[1] == want[1]) {
                return e[2];
            }
        }
        return "an edge";
    }

    /** Which way a building's door faces, as the outdoor lot's AI sees it. */
    static String facing(TownLayout.Site outdoor, String front) {
        String e = edge(outdoor, front == null ? "south" : front);
        return e.substring(e.indexOf(' ') + 1, e.indexOf(" edge")); // right, left, front, back
    }

    /** What the AI is told about this lot's neighbours (empty if none). */
    public static String note(Result r, TownLayout.Site s) {
        if (r == null) {
            return "";
        }
        StringBuilder b = new StringBuilder();
        List<Hole> holes = r.holes(s.id);
        if (!holes.isEmpty()) {
            b.append("\nOTHER PLACES STAND INSIDE THIS SITE. Leave each of these areas COMPLETELY EMPTY (no blocks at any height,"
                + " not even ground; it is built separately and anything you put there is removed). Bring a path to each and plant"
                + " and decorate round it so it sits naturally in the scene:\n");
            for (Hole h : holes) {
                int[] d = toDesign(s, h.rect());
                b.append(String.format(Locale.ROOT, "- %s (a building, its door facing your %s side): x %d..%d, z %d..%d\n",
                    h.other(), facing(s, h.front()), d[0], d[2], d[1], d[3]));
            }
        }
        Map<String, List<String>> bySide = new LinkedHashMap<>();
        for (Join j : r.joins(s.id)) {
            bySide.computeIfAbsent(j.side(), k -> new ArrayList<>()).add(j.other());
        }
        for (Map.Entry<String, List<String>> e : bySide.entrySet()) {
            String others = String.join(", ", e.getValue());
            String where = edge(s, e.getKey());
            if (s.outdoor) {
                b.append("\n").append(where.toUpperCase(Locale.ROOT)).append(" RUNS ON INTO ").append(others)
                    .append(" (another outdoor place right next to it): no fence, wall or hedge along it; let the ground,")
                    .append(" paths and plants run on to that edge so the two scenes meet.\n");
            } else {
                b.append("\n").append(where.toUpperCase(Locale.ROOT)).append(" TOUCHES THE BUILDING ").append(others)
                    .append(" (a row of joined buildings): build that outer wall right ON the edge (no margin there), with no")
                    .append(" doors or windows in it; keep the roof line on that side tidy so the two roofs meet.\n");
            }
        }
        return b.toString();
    }

    // ------------------------------------------------------------------ cutting

    /** Cut the holes of an outdoor lot out of its design (every height). Returns how many commands were cut. */
    public static int carve(BuildPlan plan, TownLayout.Site s, Result r) {
        int cut = 0;
        for (Hole h : r == null ? List.<Hole>of() : r.holes(s.id)) {
            int[] d = toDesign(s, h.rect());
            cut += carve(plan, d[0], d[1], d[2], d[3]);
        }
        return cut;
    }

    /** Remove the cells x1..x2, z1..z2 (all heights) from every block command of the plan. */
    static int carve(BuildPlan plan, int hx1, int hz1, int hx2, int hz2) {
        return carve(plan, hx1, hz1, hx2, hz2, Integer.MIN_VALUE / 2, Integer.MAX_VALUE / 2);
    }

    /** Remove the cells x1..x2, z1..z2, heights ylo..yhi, from every block command of the plan. */
    public static int carve(BuildPlan plan, int hx1, int hz1, int hx2, int hz2, int ylo, int yhi) {
        List<BuildPlan.Op> out = new ArrayList<>(plan.ops.size());
        int cut = 0;
        for (BuildPlan.Op op : plan.ops) {
            int minX = Math.min(op.x1, op.x2), maxX = Math.max(op.x1, op.x2);
            int minY = Math.min(op.y1, op.y2), maxY = Math.max(op.y1, op.y2);
            int minZ = Math.min(op.z1, op.z2), maxZ = Math.max(op.z1, op.z2);
            if (maxX < hx1 || minX > hx2 || maxZ < hz1 || minZ > hz2 || maxY < ylo || minY > yhi) {
                out.add(op);
                continue;
            }
            cut++;
            List<int[]> pieces = new ArrayList<>();
            List<String> blocks = new ArrayList<>();
            boolean hollow = "hollow".equals(op.mode), outline = "outline".equals(op.mode);
            if ("fill".equals(op.kind) && (hollow || outline)) {
                if (hollow && maxX - minX > 1 && maxY - minY > 1 && maxZ - minZ > 1) {
                    pieces.add(new int[] { minX + 1, minY + 1, minZ + 1, maxX - 1, maxY - 1, maxZ - 1 });
                    blocks.add("minecraft:air");
                }
                int[][] faces = {
                    { minX, minY, minZ, minX, maxY, maxZ }, { maxX, minY, minZ, maxX, maxY, maxZ },
                    { minX, minY, minZ, maxX, minY, maxZ }, { minX, maxY, minZ, maxX, maxY, maxZ },
                    { minX, minY, minZ, maxX, maxY, minZ }, { minX, minY, maxZ, maxX, maxY, maxZ } };
                for (int[] f : faces) {
                    pieces.add(f);
                    blocks.add(op.block);
                }
            } else {
                pieces.add(new int[] { minX, minY, minZ, maxX, maxY, maxZ });
                blocks.add(op.block);
            }
            for (int i = 0; i < pieces.size(); i++) {
                int[] whole = pieces.get(i);
                // Above and below the cut heights stays as it is; the middle loses the column.
                List<int[]> parts = new ArrayList<>();
                if (whole[1] < ylo) {
                    parts.add(new int[] { whole[0], whole[1], whole[2], whole[3], Math.min(whole[4], ylo - 1), whole[5] });
                }
                if (whole[4] > yhi) {
                    parts.add(new int[] { whole[0], Math.max(whole[1], yhi + 1), whole[2], whole[3], whole[4], whole[5] });
                }
                List<int[]> kept = new ArrayList<>(parts);
                if (whole[4] >= ylo && whole[1] <= yhi) {
                    int[] mid = { whole[0], Math.max(whole[1], ylo), whole[2], whole[3], Math.min(whole[4], yhi), whole[5] };
                    kept.addAll(minus(mid, hx1, hz1, hx2, hz2));
                }
                for (int[] k : kept) {
                    boolean one = k[0] == k[3] && k[1] == k[4] && k[2] == k[5];
                    out.add(new BuildPlan.Op(one && "set".equals(op.kind) ? "set" : "fill", k[0], k[1], k[2], k[3], k[4], k[5],
                        blocks.get(i), hollow || outline ? "replace" : op.mode));
                }
            }
        }
        plan.ops.clear();
        plan.ops.addAll(out);
        return cut;
    }

    /** A box {x1,y1,z1,x2,y2,z2} without the column x hx1..hx2, z hz1..hz2: up to four boxes. */
    static List<int[]> minus(int[] p, int hx1, int hz1, int hx2, int hz2) {
        List<int[]> out = new ArrayList<>();
        if (p[3] < hx1 || p[0] > hx2 || p[5] < hz1 || p[2] > hz2) {
            out.add(p);
            return out;
        }
        if (p[0] < hx1) {
            out.add(new int[] { p[0], p[1], p[2], hx1 - 1, p[4], p[5] });
        }
        if (p[3] > hx2) {
            out.add(new int[] { hx2 + 1, p[1], p[2], p[3], p[4], p[5] });
        }
        int x1 = Math.max(p[0], hx1), x2 = Math.min(p[3], hx2);
        if (p[2] < hz1) {
            out.add(new int[] { x1, p[1], p[2], x2, p[4], hz1 - 1 });
        }
        if (p[5] > hz2) {
            out.add(new int[] { x1, p[1], hz2 + 1, x2, p[4], p[5] });
        }
        return out;
    }

    /** A layout rectangle without the holes of this lot (for clearing an outdoor place without its buildings). */
    public static List<int[]> without(int[] rect, List<Hole> holes) {
        List<int[]> parts = new ArrayList<>();
        parts.add(new int[] { rect[0], 0, rect[1], rect[2], 0, rect[3] });
        for (Hole h : holes) {
            List<int[]> next = new ArrayList<>();
            for (int[] p : parts) {
                next.addAll(minus(p, h.rect().x1(), h.rect().z1(), h.rect().x2(), h.rect().z2()));
            }
            parts = next;
        }
        List<int[]> out = new ArrayList<>();
        for (int[] p : parts) {
            out.add(new int[] { p[0], p[2], p[3], p[5] });
        }
        return out;
    }
}

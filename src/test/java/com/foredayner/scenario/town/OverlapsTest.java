package com.foredayner.scenario.town;

import com.example.ai.build.BuildPlan;
import com.foredayner.scenario.layout.TownLayout;
import com.foredayner.scenario.sheet.ScenarioSheet;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OverlapsTest {

    private static TownLayout.Site site(String id, int x, int z, int w, int d, boolean outdoor, String front) {
        TownLayout.Site s = new TownLayout.Site();
        s.id = id;
        s.x = x;
        s.z = z;
        s.w = w;
        s.d = d;
        s.outdoor = outdoor;
        s.front = front;
        return s;
    }

    private static boolean apart(TownLayout.Site a, TownLayout.Site b) {
        return a.x + a.w <= b.x || b.x + b.w <= a.x || a.z + a.d <= b.z || b.z + b.d <= a.z;
    }

    @Test
    void twoBuildingsBecomeJoinedNeighbours() {
        TownLayout l = new TownLayout();
        TownLayout.Site a = site("a", 10, 10, 30, 30, false, "south");
        TownLayout.Site b = site("b", 30, 12, 30, 30, false, "south");
        l.sites.add(a);
        l.sites.add(b);
        Overlaps.Result r = Overlaps.resolve(l, new ScenarioSheet());
        assertTrue(apart(a, b));
        assertEquals(a.x + a.w, b.x, "they touch");
        assertEquals("east", r.joins("a").get(0).side());
        assertEquals("west", r.joins("b").get(0).side());
        String note = Overlaps.note(r, a);
        assertTrue(note.contains("RIGHT EDGE") && note.contains("TOUCHES THE BUILDING b"), note);
    }

    @Test
    void aBuildingInAWoodLeavesAHole() {
        TownLayout l = new TownLayout();
        TownLayout.Site wood = site("wood", 0, 0, 42, 42, true, "south");
        TownLayout.Site hut = site("hut", 10, 10, 12, 12, false, "east");
        l.sites.add(wood);
        l.sites.add(hut);
        Overlaps.Result r = Overlaps.resolve(l, new ScenarioSheet());
        assertEquals(42, wood.w, "the wood keeps its lot");
        assertEquals(1, r.holes("wood").size());
        int[] d = Overlaps.toDesign(wood, r.holes("wood").get(0).rect());
        // Front south: design x = layout x, design z = layout z - 42.
        assertArrayEquals(new int[] { 10, -32, 21, -21 }, d);
        assertTrue(Overlaps.note(r, wood).contains("door facing your right side"), Overlaps.note(r, wood));

        BuildPlan plan = new BuildPlan();
        plan.ops.add(new BuildPlan.Op("fill", 0, -1, -42, 41, -1, -1, "minecraft:grass_block", "replace"));
        plan.ops.add(new BuildPlan.Op("set", 15, 0, -25, 15, 0, -25, "minecraft:oak_log", null));
        Overlaps.carve(plan, wood, r);
        for (BuildPlan.Op op : plan.ops) {
            boolean inHole = Math.max(op.x1, op.x2) >= 10 && Math.min(op.x1, op.x2) <= 21
                && Math.max(op.z1, op.z2) >= -32 && Math.min(op.z1, op.z2) <= -21;
            assertFalse(inHole, "nothing left in the hole");
        }
        assertEquals(4, plan.ops.size(), "the ground around the hole stays");
    }

    @Test
    void theDesignFrameMatchesTheLotForEveryFront() {
        for (String front : List.of("north", "east", "south", "west")) {
            TownLayout.Site s = site("s", 100, 50, 20, 14, true, front);
            int[] size = Overlaps.designSize(s);
            for (int[] c : new int[][] { { 0, -1 }, { size[0] - 1, -size[1] }, { 3, -5 } }) {
                int[] at = Overlaps.toLayout(s, c[0], c[1]);
                assertTrue(at[0] >= 100 && at[0] < 120 && at[1] >= 50 && at[1] < 64, front + " " + at[0] + "," + at[1]);
                int[] back = Overlaps.toDesign(s, new Overlaps.Rect(at[0], at[1], at[0], at[1]));
                assertEquals(c[0], back[0], front);
                assertEquals(c[1], back[1], front);
            }
        }
    }

    @Test
    void outdoorClearingKeepsTheBuildings() {
        List<int[]> parts = Overlaps.without(new int[] { 0, 0, 41, 41 },
            List.of(new Overlaps.Hole(new Overlaps.Rect(10, 10, 21, 21), "hut", "south")));
        int cells = 0;
        for (int[] p : parts) {
            cells += (p[2] - p[0] + 1) * (p[3] - p[1] + 1);
        }
        assertEquals(42 * 42 - 12 * 12, cells);
    }
}

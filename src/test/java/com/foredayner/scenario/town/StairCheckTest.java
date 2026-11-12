package com.foredayner.scenario.town;

import com.example.ai.build.BuildPlan;
import com.foredayner.scenario.layout.Underground;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StairCheckTest {

    /** A 12 x 12 house on a 16 x 16 site: two storeys (floors at 0 and 5), roof at 10, a door in the front wall. */
    private static BuildPlan house(boolean stairs, boolean door) {
        BuildPlan p = new BuildPlan();
        p.ops.add(new BuildPlan.Op("fill", 2, 0, -14, 13, 10, -3, "minecraft:spruce_planks", "hollow"));
        p.ops.add(new BuildPlan.Op("fill", 3, 5, -13, 12, 5, -4, "minecraft:oak_planks", "replace")); // upper floor
        if (door) {
            p.ops.add(new BuildPlan.Op("fill", 7, 1, -3, 7, 2, -3, "minecraft:oak_door", "replace"));
        }
        if (stairs) {
            // From the floor below, 4 steps along +x rising to the upper floor at x 4..8, z -12; the hole over them.
            for (int i = 0; i < 4; i++) {
                int x = 4 + i;
                p.ops.add(new BuildPlan.Op("set", x, 1 + i, -12, x, 1 + i, -12, "minecraft:oak_stairs[facing=east]", null));
            }
            p.ops.add(new BuildPlan.Op("fill", 3, 5, -12, 7, 5, -12, "minecraft:air", "replace"));
        }
        return p;
    }

    @Test
    void aHouseWithStairsAndADoorPasses() {
        assertNull(StairCheck.problem(house(true, true), 16, 16, 14, 2, 1));
    }

    @Test
    void noStairsUpIsFound() {
        String problem = StairCheck.problem(house(false, true), 16, 16, 14, 2, 2);
        assertNotNull(problem);
        assertTrue(problem.contains("storey 2") && problem.contains("2 blocks wide"), problem);
    }

    @Test
    void noDoorIsFound() {
        String problem = StairCheck.problem(house(true, false), 16, 16, 14, 2, 1);
        assertNotNull(problem);
        assertTrue(problem.contains("door"), problem);
    }

    @Test
    void stairsAreAddedWhereThereIsRoom() {
        BuildPlan p = house(false, true);
        assertEquals(1, StairCheck.addStairs(p, 16, 16, 14, 2, 1, "minecraft:oak_stairs"));
        assertNull(StairCheck.problem(p, 16, 16, 14, 2, 1));
    }

    @Test
    void cellarStairsSitInTheMiddleAndWidenForBigBuildings() {
        Underground.Cellar small = new Underground.Cellar("a", 1, 1, 12, 12, 1, false);
        Underground.Stairwell s = Underground.stairwell(small, 14, 14);
        assertNotNull(s);
        assertEquals(1, s.width());
        Underground.Cellar big = new Underground.Cellar("b", 1, 1, 20, 20, 2, false);
        Underground.Stairwell b = Underground.stairwell(big, 22, 22);
        assertEquals(2, b.width());
        assertEquals(2, b.levels());
        int[] r = b.reserved();
        assertTrue(r[0] > 1 && r[2] < 20 && r[1] > 1 && r[3] < 20, "inside the cellar's walls");
        assertNull(Underground.stairwell(new Underground.Cellar("c", 1, 1, 6, 6, 1, false), 8, 8), "too small: a ladder");
        assertNull(Underground.stairwell(new Underground.Cellar("d", 1, 1, 5, 5, 1, true), 8, 8), "a landing: a ladder");
    }

    @Test
    void carvingKeepsTheFloorAndWhatIsAbove() {
        BuildPlan p = new BuildPlan();
        p.ops.add(new BuildPlan.Op("fill", 0, 0, -10, 9, 8, -1, "minecraft:stone", "replace"));
        Overlaps.carve(p, 3, -6, 5, -4, 1, 4);
        StairCheck.Grid g = StairCheck.grid(p, 10, 10, 10);
        assertEquals(StairCheck.SOLID, g.at(4, 0, -5), "the floor stays");
        assertEquals(StairCheck.AIR, g.at(4, 2, -5), "cut");
        assertEquals(StairCheck.SOLID, g.at(4, 6, -5), "above stays");
        assertEquals(StairCheck.SOLID, g.at(2, 2, -5), "beside stays");
    }
}

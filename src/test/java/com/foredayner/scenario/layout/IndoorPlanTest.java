package com.foredayner.scenario.layout;

import com.foredayner.scenario.sheet.ScenarioSheet;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class IndoorPlanTest {

    static ScenarioSheet mansion() {
        ScenarioSheet sheet = new ScenarioSheet();
        sheet.stage = "building";
        String[][] rooms = { { "hall", "L", "0" }, { "study", "M", "0" }, { "kitchen", "M", "0" }, { "dining", "L", "0" },
            { "bedroom", "M", "1" }, { "nursery", "S", "1" }, { "attic_store", "M", "1" }, { "cellar", "M", "-1" } };
        for (String[] r : rooms) {
            ScenarioSheet.Place p = new ScenarioSheet.Place();
            p.id = r[0];
            p.name = r[0];
            p.size = r[1];
            p.level = Integer.parseInt(r[2]);
            sheet.places.add(p);
        }
        assertTrue(sheet.normalize().isEmpty());
        return sheet;
    }

    @Test
    void roomsOfABuildingStageStayRooms() {
        ScenarioSheet sheet = mansion();
        assertEquals("room", sheet.place("cellar").kind);
        assertEquals(-1, sheet.place("cellar").level);
        assertFalse(sheet.place("cellar").outdoor);
    }

    @Test
    void theAutomaticPlanPassesItsChecks() {
        ScenarioSheet sheet = mansion();
        TownLayout l = IndoorPlan.auto(sheet);
        assertTrue(l.building());
        assertEquals(List.of(), l.check(sheet));
        assertEquals(2, l.stairs.size(), "cellar to ground, ground to first floor");
        assertEquals(1, l.roads.size(), "a path from the entrance to the edge");
        int[] b = IndoorPlan.bounds(l);
        assertTrue(b[0] > 10 && b[2] < 150, "in the middle of the ground");
    }

    @Test
    void repairAddsWhatIsMissing() {
        ScenarioSheet sheet = mansion();
        // Rooms only, as an AI might draw them from a floor plan: no doors, no stairs.
        String answer = "{\"rooms\": ["
            + "{\"id\":\"hall\",\"level\":0,\"x\":0,\"z\":0,\"w\":11,\"d\":11},"
            + "{\"id\":\"study\",\"level\":0,\"x\":10,\"z\":0,\"w\":8,\"d\":11},"
            + "{\"id\":\"kitchen\",\"level\":0,\"x\":0,\"z\":10,\"w\":11,\"d\":8},"
            + "{\"id\":\"dining\",\"level\":0,\"x\":10,\"z\":10,\"w\":8,\"d\":8},"
            + "{\"id\":\"bedroom\",\"level\":1,\"x\":0,\"z\":0,\"w\":11,\"d\":11},"
            + "{\"id\":\"nursery\",\"level\":1,\"x\":10,\"z\":0,\"w\":8,\"d\":11},"
            + "{\"id\":\"attic_store\",\"level\":1,\"x\":0,\"z\":10,\"w\":18,\"d\":8},"
            + "{\"id\":\"cellar\",\"level\":-1,\"x\":0,\"z\":0,\"w\":11,\"d\":11}"
            + "], \"doors\": [{\"x\": 50, \"z\": 50, \"level\": 0}]}";
        TownLayout l = IndoorPlan.fromAnswer(answer, sheet);
        assertFalse(l.check(sheet).isEmpty());
        List<String> notes = l.repair(sheet);
        assertEquals(List.of(), l.check(sheet), "repaired: " + notes);
        assertTrue(l.doors.stream().anyMatch(d -> d.entrance), "an entrance");
        assertEquals(2, l.stairs.size(), "stairs down to the cellar and up to the first floor");
        String json = IndoorPlan.answerJson(l);
        TownLayout again = IndoorPlan.fromAnswer(json, sheet);
        again.repair(sheet);
        assertEquals(List.of(), again.check(sheet), "the plan reads back");
    }

    @Test
    void overlappingRoomsAreAProblem() {
        ScenarioSheet sheet = mansion();
        TownLayout l = IndoorPlan.auto(sheet);
        TownLayout.Site study = l.site("study");
        TownLayout.Site kitchen = l.site("kitchen");
        kitchen.x = study.x + 2;
        kitchen.z = study.z;
        assertTrue(l.check(sheet).stream().anyMatch(p -> p.contains("overlap")), l.check(sheet).toString());
    }
}

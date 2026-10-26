package com.foredayner.scenario.town;

import com.foredayner.scenario.layout.TownLayout;
import com.foredayner.scenario.sheet.ScenarioSheet;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class GroundPlanTest {

    private static ScenarioSheet greymouth() throws Exception {
        ScenarioSheet sheet = ScenarioSheet.fromJson(Files.readString(Path.of("src/gametest/resources/scenarios/greymouth.sheet.json")));
        sheet.normalize();
        return sheet;
    }

    @Test
    void streetsSitesAndZonesEachGetTheirGround() throws Exception {
        ScenarioSheet sheet = greymouth();
        TownLayout layout = TownLayout.auto(sheet);
        GroundPlan plan = GroundPlan.of(layout, sheet);
        boolean[][] road = layout.roadGrid();
        int roads = 0;
        for (int x = 0; x < plan.size; x++) {
            for (int z = 0; z < plan.size; z++) {
                if (road[x][z]) {
                    roads++;
                    GroundPlan.Cell c = plan.cells[x][z];
                    assertTrue(c == GroundPlan.Cell.ROAD || c == GroundPlan.Cell.PATH, "street at " + x + "," + z + " is " + c);
                }
            }
        }
        assertTrue(roads > 200, "there are streets");
        TownLayout.Site inn = layout.site("inn");
        assertEquals(GroundPlan.Cell.LOT, plan.cells[inn.x + inn.w / 2][inn.z + inn.d / 2], "a building's lot is kept for it");
        TownLayout.Site dock = layout.site("dock");
        int dx = dock.x + dock.w / 2, dz = dock.z + dock.d - 2;
        assertEquals(GroundPlan.Cell.OUTDOOR, plan.cells[dx][dz]);
        assertEquals("dock", plan.outdoorKind[dx][dz]);
        assertEquals(GroundPlan.Cell.WATER, plan.under[dx][dz], "the dock's far end stands in the sea");
        assertEquals(GroundPlan.Cell.WATER, plan.cells[5][158], "the sea along the south edge");
        assertEquals(GroundPlan.Cell.WOODS, plan.cells[80][3], "the woods along the north edge");
    }

    @Test
    void fieldsNeverFlood() {
        TownLayout layout = new TownLayout();
        TownLayout.Zone field = new TownLayout.Zone();
        field.kind = "field";
        field.x = 10;
        field.z = 10;
        field.w = 20;
        field.d = 20;
        TownLayout.Zone sea = new TownLayout.Zone();
        sea.kind = "water";
        sea.x = 0;
        sea.z = 15;
        sea.w = 160;
        sea.d = 30;
        layout.zones.add(sea);
        layout.zones.add(field);
        GroundPlan plan = GroundPlan.of(layout, new ScenarioSheet());
        assertEquals(GroundPlan.Cell.WATER, plan.cells[15][20], "a zone laid over the sea leaves the water");
        assertEquals(GroundPlan.Cell.FIELD, plan.cells[15][12]);
        assertEquals(GroundPlan.Cell.GRASS, plan.cells[100][5]);
    }

    @Test
    void noiseIsStableAndSpread() {
        assertEquals(GroundPlan.noise(3, 4, 5), GroundPlan.noise(3, 4, 5));
        int[] buckets = new int[10];
        for (int x = 0; x < 100; x++) {
            for (int z = 0; z < 100; z++) {
                int n = GroundPlan.noise(x, z, 7);
                assertTrue(n >= 0 && n < 100);
                buckets[n / 10]++;
            }
        }
        for (int b : buckets) {
            assertTrue(b > 700 && b < 1300, "roughly even: " + java.util.Arrays.toString(buckets));
        }
    }
}

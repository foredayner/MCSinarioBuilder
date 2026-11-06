package com.foredayner.scenario.town;

import com.foredayner.scenario.layout.TownLayout;
import com.foredayner.scenario.sheet.ScenarioSheet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OuterBandTest {

    /** A road leaving to the south, woods along the north, the sea along the east. */
    private static GroundPlan plan() {
        ScenarioSheet sheet = new ScenarioSheet();
        TownLayout l = new TownLayout();
        TownLayout.Road road = new TownLayout.Road();
        road.width = 5;
        road.points.add(new int[] { 80, 60 });
        road.points.add(new int[] { 80, 159 });
        l.roads.add(road);
        TownLayout.Zone woods = new TownLayout.Zone();
        woods.kind = "woods";
        woods.w = 160;
        woods.d = 16;
        l.zones.add(woods);
        TownLayout.Zone sea = new TownLayout.Zone();
        sea.kind = "water";
        sea.x = 150;
        sea.z = 20;
        sea.w = 10;
        sea.d = 100;
        l.zones.add(sea);
        return GroundPlan.of(l, sheet);
    }

    @Test
    void theRingIsOutsideTheTown() {
        OuterBand band = new OuterBand(plan(), false);
        assertFalse(band.contains(10, 10));
        assertTrue(band.contains(-1, 10));
        assertTrue(band.contains(170, 170));
        assertFalse(band.contains(-OuterBand.WIDTH - 1, 10));
        assertEquals(1, band.distance(-1, 50));
        assertEquals(OuterBand.WIDTH, band.distance(159 + OuterBand.WIDTH, 50));
        assertArrayEquals(new int[] { -OuterBand.WIDTH - 1, 50 }, band.outside(-3, 50));
    }

    @Test
    void anOpenRingCarriesOnTheTownsEdge() {
        OuterBand band = new OuterBand(plan(), false);
        assertEquals(OuterBand.Kind.ROAD, band.at(80, 170).kind(), "the road leaving the town runs on");
        assertEquals(OuterBand.Kind.ROAD, band.at(80, 159 + OuterBand.WIDTH).kind(), "to the end of the ring");
        assertEquals(OuterBand.Kind.WATER, band.at(165, 60).kind(), "the sea stays sea");
        OuterBand.Column hill = band.at(40, -OuterBand.WIDTH);
        assertEquals(OuterBand.Kind.WOODS, hill.kind(), "the woods go on");
        assertEquals(OuterBand.WOODS_HILL, hill.rise(), "and rise into the hill behind the village");
        assertEquals(0, band.at(-OuterBand.WIDTH, 100).rise(), "open ground does not rise");
        assertEquals(OuterBand.Kind.MEADOW, band.at(-2, 100).kind(), "meadow near the town");
        assertEquals(OuterBand.Kind.WOODS, band.at(-OuterBand.WIDTH, 100).kind(), "woods further out");
    }

    @Test
    void aClosedRingEndsInHillsAndBlocksTheRoad() {
        OuterBand band = new OuterBand(plan(), true);
        assertEquals(OuterBand.CLOSED_HILL, band.at(-OuterBand.WIDTH, 100).rise(), "a steep hill at the outer edge");
        assertEquals(0, band.at(-3, 100).rise(), "flat next to the town");
        boolean blocked = false;
        for (int d = 1; d <= OuterBand.WIDTH; d++) {
            if (band.at(80, 159 + d).kind() == OuterBand.Kind.BARRIER) {
                blocked = true;
            }
        }
        assertTrue(blocked, "a rockfall blocks the road out");
        assertNotEquals(OuterBand.Kind.ROAD, band.at(80, 159 + OuterBand.WIDTH).kind(), "the road does not reach the outside");
        assertEquals(0, band.at(165, 60).rise(), "the sea stays at sea level");
    }

    @Test
    void aPlacePutFarOutWidensItsSideAndGetsAFlatLotAndAPath() {
        GroundPlan plan = plan();
        // A lot 20 wide put 50 blocks west of the town, in the wooded north-west corner's latitude.
        int[] pad = { -50, 40, 20, 20 };
        OuterBand band = new OuterBand(plan, true, OuterBand.WIDTH, java.util.List.of(pad));
        assertEquals(50 + OuterBand.PAD_MARGIN, band.west(), "the west side reaches past the lot");
        assertEquals(OuterBand.WIDTH, band.east(), "the other sides stay as they were");
        assertTrue(band.contains(-55, 50));
        OuterBand.Column lot = band.at(-40, 50);
        assertEquals(0, lot.rise(), "the lot is flat");
        assertEquals(0.0, lot.t(), "at the town's level");
        assertFalse(lot.tree(), "no tree on the lot");
        assertEquals(OuterBand.Kind.PATH, band.at(-15, 50).kind(), "a path back to the town");
        assertTrue(band.at(-58, 50).rise() > 0, "the closed stage's hills rise again past it");
    }
}

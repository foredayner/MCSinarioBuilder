package com.foredayner.scenario.layout;

import com.foredayner.scenario.sheet.ScenarioSheet;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class UndergroundTest {

    private static ScenarioSheet sheet() {
        ScenarioSheet sheet = new ScenarioSheet();
        ScenarioSheet.Place mansion = sheet.newPlace("저택", "L", "named", 0, "");
        mansion.cellars = 2;
        sheet.places.add(mansion);
        sheet.places.add(sheet.newPlace("교회", "M", "named", 0, ""));
        sheet.places.add(sheet.newPlace("공터", "S", "outdoor", 0, ""));
        ScenarioSheet.Link link = new ScenarioSheet.Link();
        link.from = mansion.id;
        link.to = "교회";
        link.kind = "secret";
        sheet.links.add(link);
        sheet.normalize();
        return sheet;
    }

    private static TownLayout layout(ScenarioSheet sheet) {
        TownLayout l = new TownLayout();
        l.size = 160;
        int x = 20;
        for (ScenarioSheet.Place p : sheet.places) {
            TownLayout.Site s = new TownLayout.Site();
            s.id = p.id;
            s.w = s.d = ScenarioSheet.footprint(p);
            s.x = x;
            s.z = 40;
            s.outdoor = p.outdoor;
            x += s.w + 30;
            l.sites.add(s);
        }
        return l;
    }

    @Test
    void cellarsUnderBuildingsAndLandingsAtTunnelEnds() {
        ScenarioSheet sheet = sheet();
        assertEquals(2, sheet.place("저택").cellars);
        assertTrue(sheet.place("저택").basement, "a cellar is a basement");
        assertEquals(1, sheet.underground().size());
        TownLayout layout = layout(sheet);
        List<Underground.Cellar> cellars = Underground.cellars(layout, sheet);
        assertEquals(2, cellars.size(), "the mansion's cellar and a landing under the church");
        Underground.Cellar mansion = Underground.cellarOf(cellars, "저택");
        TownLayout.Site site = layout.site("저택");
        assertEquals(site.x + 1, mansion.x1(), "one block in from the lot's edge");
        assertEquals(2, mansion.levels());
        assertFalse(mansion.landing());
        assertTrue(Underground.cellarOf(cellars, "교회").landing());
        assertNull(Underground.cellarOf(cellars, "공터"), "nothing under open ground without a tunnel");
    }

    @Test
    void aTunnelRunsBetweenThemAndGoesThroughTheirWalls() {
        ScenarioSheet sheet = sheet();
        TownLayout layout = layout(sheet);
        List<Underground.Cellar> cellars = Underground.cellars(layout, sheet);
        Underground.Tunnel t = Underground.tunnels(layout, sheet).get(0);
        assertTrue(t.secret());
        assertFalse(t.inside().isEmpty());
        assertEquals(2 * Underground.TUNNEL_W, t.doorways().size(), "through each cellar's wall, as wide as the tunnel");
        for (long k : t.inside()) {
            for (Underground.Cellar c : cellars) {
                assertFalse(c.inside(Underground.keyX(k), Underground.keyZ(k)) || c.onWall(Underground.keyX(k), Underground.keyZ(k)),
                    "the tunnel's own cells are outside the cellars");
            }
            assertFalse(t.walls().contains(k), "walls are beside the tunnel, not in it");
        }
        assertEquals(List.of(0, 0, 3, 3), List.of(Underground.route(new int[] { 0, 0 }, new int[] { 3, 3 }).get(0)[0],
            Underground.route(new int[] { 0, 0 }, new int[] { 3, 3 }).get(0)[1],
            Underground.route(new int[] { 0, 0 }, new int[] { 3, 3 }).get(6)[0],
            Underground.route(new int[] { 0, 0 }, new int[] { 3, 3 }).get(6)[1]), "along x, then along z");
    }

    @Test
    void cellarsAreKeptToWhatMakesSense() {
        ScenarioSheet sheet = new ScenarioSheet();
        ScenarioSheet.Place yard = sheet.newPlace("마당", "S", "outdoor", 0, "");
        yard.cellars = 2;
        ScenarioSheet.Place deep = sheet.newPlace("탑", "S", "named", 0, "");
        deep.cellars = 9;
        sheet.places.add(yard);
        sheet.places.add(deep);
        sheet.normalize();
        assertEquals(0, yard.cellars, "no cellar under open ground");
        assertEquals(ScenarioSheet.MAX_CELLARS, deep.cellars);
        ScenarioSheet.Place old = new ScenarioSheet.Place();
        old.id = "old";
        old.basement = true;
        sheet.places.add(old);
        sheet.normalize();
        assertEquals(1, old.cellars, "an older sheet's basement is one cellar floor");
    }
}

package com.foredayner.scenario.layout;

import com.foredayner.scenario.sheet.ScenarioSheet;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TownLayoutTest {

    private static ScenarioSheet greymouth() throws Exception {
        ScenarioSheet sheet = ScenarioSheet.fromJson(Files.readString(Path.of("src/gametest/resources/scenarios/greymouth.sheet.json")));
        assertTrue(sheet.normalize().isEmpty());
        return sheet;
    }

    private static ScenarioSheet crowded() {
        ScenarioSheet sheet = new ScenarioSheet();
        String[] sizes = { "XL", "XL", "L", "L", "L", "M", "M", "M", "M", "S", "S", "S" };
        for (int i = 0; i < sizes.length; i++) {
            ScenarioSheet.Place p = new ScenarioSheet.Place();
            p.id = "p" + i;
            p.size = sizes[i];
            sheet.places.add(p);
        }
        ScenarioSheet.Place cemetery = new ScenarioSheet.Place();
        cemetery.id = "cemetery";
        cemetery.kind = "cemetery";
        cemetery.size = "L";
        cemetery.outdoor = true;
        sheet.places.add(cemetery);
        ScenarioSheet.Place dock = new ScenarioSheet.Place();
        dock.id = "dock";
        dock.kind = "dock";
        dock.outdoor = true;
        sheet.places.add(dock);
        sheet.normalize();
        return sheet;
    }

    @Test
    void placesPutPastTheMapStayThereAndPass() throws Exception {
        ScenarioSheet sheet = greymouth();
        TownLayout layout = TownLayout.auto(sheet);
        assertTrue(layout.check(sheet).isEmpty());
        TownLayout.Site pick = layout.sites.get(0);
        for (TownLayout.Site o : layout.sites) {
            if (o.w < pick.w) {
                pick = o; // the smallest, so it fits wholly past the edge
            }
        }
        TownLayout.Site s = pick;
        // Wholly past the west edge, as far as the keeper may go, in the middle of that side.
        s.x = -layout.reach();
        s.z = layout.size / 2 - s.d / 2;
        assertTrue(s.w <= layout.reach() && layout.outside(s) && layout.withinReach(s));
        List<String> problems = layout.check(sheet);
        assertTrue(problems.stream().noneMatch(p -> p.contains(s.id)), "no street or edge rule for it: " + problems);
        layout.repair(sheet);
        assertEquals(-layout.reach(), s.x, "repair leaves it where the keeper put it");
        s.x = -layout.reach() - 1;
        assertTrue(layout.check(sheet).stream().anyMatch(p -> p.contains(s.id) && p.contains("too far")), "but not further than the reach");
        assertEquals(layout.size / 4, layout.reach(), "a quarter of the side each way");
    }

    @Test
    void tidyingFixesStreetsAroundBuildingsWithoutMovingThem() throws Exception {
        ScenarioSheet sheet = greymouth();
        TownLayout layout = TownLayout.auto(sheet);
        assertTrue(layout.tidy(sheet).isEmpty(), "a good layout has nothing to tidy");
        assertTrue(layout.check(sheet).isEmpty());
        // The keeper drags a building into the middle of a street.
        TownLayout.Road main = layout.roads.get(0);
        int[] a = main.points.get(0), b = main.points.get(main.points.size() - 1);
        TownLayout.Site s = null;
        for (TownLayout.Site o : layout.sites) {
            if (!o.outdoor) {
                s = o;
                break;
            }
        }
        s.x = (a[0] + b[0]) / 2 - s.w / 2;
        s.z = (a[1] + b[1]) / 2 - s.d / 2;
        int x = s.x, z = s.z;
        String front = s.front;
        TownLayout.Site moved = s;
        assertTrue(layout.check(sheet).stream().anyMatch(p -> p.contains(moved.id) && p.contains("street")));
        List<String> notes = layout.tidy(sheet);
        assertFalse(notes.isEmpty());
        assertEquals(x, s.x);
        assertEquals(z, s.z);
        assertEquals(front, s.front, "the building is not turned either");
        List<String> problems = layout.check(sheet);
        assertTrue(problems.stream().noneMatch(p -> p.contains("over a street") || p.contains("separate pieces")
            || p.contains("stands in the")), notes + " -> " + problems);
    }

    @Test
    void theAutomaticLayoutPassesTheChecksForEveryCoastAndWoods() throws Exception {
        for (ScenarioSheet sheet : List.of(greymouth(), crowded())) {
            for (String coast : ScenarioSheet.SIDES) {
                for (String woods : ScenarioSheet.SIDES) {
                    sheet.terrain.coast = coast;
                    sheet.terrain.woods = woods;
                    TownLayout layout = TownLayout.auto(sheet);
                    List<String> problems = layout.check(sheet);
                    assertTrue(problems.isEmpty(), sheet.places.size() + " places, coast " + coast + ", woods " + woods + ": " + problems);
                    assertEquals(sheet.places.size(), layout.sites.size());
                }
            }
        }
    }

    @Test
    void placesThatDoNotFitAreLeftOutAndReported() {
        ScenarioSheet sheet = new ScenarioSheet();
        for (int i = 0; i < 20; i++) {
            ScenarioSheet.Place p = new ScenarioSheet.Place();
            p.id = "big" + i;
            p.size = "XL";
            sheet.places.add(p);
        }
        sheet.normalize();
        sheet.terrain.coast = "south";
        sheet.terrain.woods = "north";
        TownLayout layout = TownLayout.auto(sheet);
        List<String> problems = layout.check(sheet);
        assertTrue(layout.sites.size() < 20, "20 XL places cannot all fit");
        assertTrue(layout.sites.size() >= 4, "but some do: " + layout.sites.size());
        for (String problem : problems) {
            assertTrue(problem.contains("has no site"), "everything placed is fine; only missing places are reported: " + problem);
        }
        assertEquals(20 - layout.sites.size(), problems.size());
    }

    @Test
    void aMissingPlaceCanBePutOnAFreeSpot() throws Exception {
        ScenarioSheet sheet = greymouth();
        TownLayout layout = TownLayout.auto(sheet);
        layout.sites.remove(layout.site("church"));
        assertFalse(layout.check(sheet).isEmpty());
        assertTrue(layout.addFree(sheet.place("church")));
        assertTrue(layout.check(sheet).isEmpty(), "the church found a spot by a street: " + layout.check(sheet));
    }

    @Test
    void readsTheAiAnswerWithSizesFromTheSheet() throws Exception {
        ScenarioSheet sheet = greymouth();
        String answer = "Here: ```json\n" + LayoutPrompt.answerJson(TownLayout.auto(sheet)) + "\n```";
        TownLayout layout = TownLayout.fromAnswer(answer, sheet);
        assertTrue(layout.check(sheet).isEmpty(), "the draft read back is still fine");
        assertEquals(ScenarioSheet.footprint(sheet.place("mansion")), layout.site("mansion").w);
        assertEquals(Math.min(60, 2 * ScenarioSheet.footprint(sheet.place("mansion").size)), layout.site("mansion").w,
            "the scenario's own buildings get lots twice as wide");
        assertEquals(ScenarioSheet.groundSize(sheet), layout.size, "the ground grows with the lots");
        assertTrue(layout.size >= 160 && layout.size <= 320);
        assertThrows(IllegalArgumentException.class, () -> TownLayout.fromAnswer("{\"places\":[{\"id\":\"castle\",\"x\":1,\"z\":1}]}", sheet));
    }

    @Test
    void reportsWhatIsWrong() throws Exception {
        ScenarioSheet sheet = greymouth();
        TownLayout layout = TownLayout.auto(sheet);
        TownLayout.Site inn = layout.site("inn");
        TownLayout.Site church = layout.site("church");
        church.x = inn.x + 3;
        church.z = inn.z + 3;
        layout.site("mansion").x = 150;
        layout.sites.remove(layout.site("general_store"));
        TownLayout.Road lonely = new TownLayout.Road();
        lonely.points.add(new int[] { 5, 5 });
        lonely.points.add(new int[] { 9, 5 });
        layout.roads.add(lonely);
        String all = String.join("\n", layout.check(sheet));
        assertTrue(all.contains("inn and church overlap") || all.contains("church and inn overlap"), all);
        assertTrue(all.contains("mansion") && all.contains("too far past the edge"), all);
        assertTrue(all.contains("general_store has no site"), all);
        assertTrue(all.contains("separate pieces"), all);
    }

    @Test
    void aFrontAwayFromTheStreetIsAProblem() throws Exception {
        ScenarioSheet sheet = greymouth();
        TownLayout layout = TownLayout.auto(sheet);
        TownLayout.Site inn = layout.site("inn");
        inn.front = "north".equals(inn.front) ? "south" : "north";
        assertTrue(String.join("\n", layout.check(sheet)).contains("inn's front"));
    }

    @Test
    void turningKeepsTheLayoutValid() throws Exception {
        ScenarioSheet sheet = greymouth();
        TownLayout layout = TownLayout.auto(sheet);
        layout.turn(1);
        assertTrue(layout.check(sheet).isEmpty(), "a quarter turn moves everything together");
        layout.turn(3);
        TownLayout again = TownLayout.auto(sheet);
        assertEquals(again.site("inn").x, layout.site("inn").x);
        assertEquals(again.site("inn").front, layout.site("inn").front);
    }

    private static ScenarioSheet village() {
        ScenarioSheet sheet = new ScenarioSheet();
        for (String id : new String[] { "shrine", "far_house", "house_a", "house_b", "store" }) {
            ScenarioSheet.Place p = new ScenarioSheet.Place();
            p.id = id;
            p.size = "S";
            sheet.places.add(p);
        }
        sheet.normalize();
        return sheet;
    }

    private static TownLayout.Site site(String id, int x, int z, String front) {
        TownLayout.Site s = new TownLayout.Site();
        s.id = id;
        s.x = x;
        s.z = z;
        s.front = front;
        return s;
    }

    private static TownLayout.Road road(int width, int... xz) {
        TownLayout.Road r = new TownLayout.Road();
        r.width = width;
        for (int i = 0; i + 1 < xz.length; i += 2) {
            r.points.add(new int[] { xz[i], xz[i + 1] });
        }
        return r;
    }

    @Test
    void repairKeepsAMapLayoutAndFixesWhatCodeCan() {
        ScenarioSheet sheet = village();
        TownLayout l = new TownLayout();
        // One road from the entrance (south edge) up through the mountain to the north edge, as traced from a map.
        l.roads.add(road(5, 80, 158, 80, 1));
        // A separate lane the AI drew, not joined to the road.
        l.roads.add(road(3, 110, 120, 150, 120));
        l.sites.add(site("shrine", 75, 8, "south"));        // on the road's far end, on the mountain
        l.sites.add(site("far_house", 12, 60, "east"));     // among fields, far from the road
        l.sites.add(site("house_a", 86, 70, "west"));       // fine
        l.sites.add(site("house_b", 88, 72, "west"));       // on top of house_a
        l.sites.add(site("store", 120, 108, "south"));      // by the lane
        assertFalse(l.check(sheet).isEmpty(), "the raw layout has problems");
        List<String> notes = l.repair(sheet);
        assertEquals(List.of(), l.check(sheet), "repaired: " + notes);
        assertEquals(5, l.sites.size(), "nothing dropped");
        TownLayout.Site a = l.site("house_a");
        assertEquals(86, a.x, "a place that was fine stays put");
        TownLayout.Site far = l.site("far_house");
        assertEquals(12, far.x, "a house far from the road keeps its spot and gets a footpath");
        assertEquals(60, far.z);
        TownLayout.Site shrine = l.site("shrine");
        assertTrue(Math.abs(shrine.x - 75) + Math.abs(shrine.z - 8) <= 4, "the shrine stays where it was: " + shrine.x + "," + shrine.z);
        assertTrue(notes.stream().anyMatch(n -> n.contains("길을 끊었어요")), notes.toString());
        assertTrue(notes.stream().anyMatch(n -> n.contains("오솔길")), notes.toString());
    }

    @Test
    void readsTheMapKindAndTheMapsUnusedBuildings() {
        String answer = "{\"map_kind\": \"town\", \"places\": [{\"id\": \"shrine\", \"x\": 20, \"z\": 10, \"front\": \"south\", \"from_map\": false}],"
            + " \"roads\": [], \"zones\": [], \"extra\": [{\"x\": 40, \"z\": 50, \"size\": \"M\", \"front\": \"east\"}, {\"x\": 70, \"z\": 90}]}";
        assertEquals("town", LayoutPrompt.mapKind(answer));
        assertEquals("", LayoutPrompt.mapKind("{}"));
        List<int[]> extras = LayoutPrompt.extras(answer);
        assertEquals(2, extras.size());
        assertArrayEquals(new int[] { 40, 50, 15, 1 }, extras.get(0));
        assertArrayEquals(new int[] { 70, 90, 11, 2 }, extras.get(1));
        TownLayout l = TownLayout.fromAnswer(answer, village());
        assertFalse(l.site("shrine").fromMap);
        ScenarioSheet sheet = village();
        ScenarioSheet.Place filler = new ScenarioSheet.Place();
        filler.id = "map_house_1";
        filler.mapFiller = true;
        sheet.places.add(filler);
        assertEquals(5, sheet.counted(), "map houses do not count toward the limit");
    }
}

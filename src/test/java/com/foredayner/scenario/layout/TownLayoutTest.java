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
    void readsTheAiAnswerWithSizesFromTheSheet() throws Exception {
        ScenarioSheet sheet = greymouth();
        String answer = "Here: ```json\n" + LayoutPrompt.answerJson(TownLayout.auto(sheet)) + "\n```";
        TownLayout layout = TownLayout.fromAnswer(answer, sheet);
        assertTrue(layout.check(sheet).isEmpty(), "the draft read back is still fine");
        assertEquals(ScenarioSheet.footprint(sheet.place("mansion").size), layout.site("mansion").w);
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
        assertTrue(all.contains("mansion") && all.contains("leaves the ground"), all);
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
}

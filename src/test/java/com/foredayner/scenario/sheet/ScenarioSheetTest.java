package com.foredayner.scenario.sheet;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ScenarioSheetTest {

    private static final String ANSWER = """
        Here is the sheet:
        ```json
        {"title": "t", "era": "1920s", "places": [
          {"id": "Old Mill", "name": "물레방앗간", "size": "huge", "floors": 9},
          {"id": "inn", "name": "여관", "size": "l"}],
         "npcs": [{"name": "A", "place": "inn"}, {"name": "B", "place": "nowhere"}],
         "clues": [{"name": "편지 {중요}", "place": "old_mill", "form": "scroll"}],
         "links": [{"from": "inn", "to": "Old Mill"}],
         "terrain": {"coast": "South"}}
        ```
        """;

    @Test
    void readsTheJsonOutOfTheAnswer() {
        ScenarioSheet sheet = ScenarioSheet.fromAnswer(ANSWER);
        assertEquals(2, sheet.places.size());
        assertEquals("편지 {중요}", sheet.clues.get(0).name, "braces inside strings do not end the object");
    }

    @Test
    void normalizesAndReportsWhatTheAiMustFix() {
        ScenarioSheet sheet = ScenarioSheet.fromAnswer(ANSWER);
        List<String> problems = sheet.normalize();
        assertEquals("old_mill", sheet.places.get(0).id);
        assertEquals("M", sheet.places.get(0).size, "unknown size -> M");
        assertEquals(4, sheet.places.get(0).floors, "floors are capped");
        assertEquals("L", sheet.places.get(1).size);
        assertEquals("note", sheet.clues.get(0).form, "unknown clue form -> note");
        assertEquals("south", sheet.terrain.coast);
        assertEquals("old_mill", sheet.links.get(0).to);
        assertEquals(1, problems.size(), "only the NPC in an unknown place: " + problems);
        assertTrue(problems.get(0).contains("nowhere"));
    }

    @Test
    void placesUnderTheGroundBecomeOutdoorCaveEntrances() {
        ScenarioSheet sheet = new ScenarioSheet();
        ScenarioSheet.Place cave = new ScenarioSheet.Place();
        cave.id = "cave";
        cave.name = "등대 아래 동굴";
        cave.kind = "other";
        ScenarioSheet.Place mine = new ScenarioSheet.Place();
        mine.id = "old_mine";
        mine.name = "Old Mine";
        mine.kind = "other";
        ScenarioSheet.Place inn = new ScenarioSheet.Place();
        inn.id = "inn";
        inn.name = "Minerva Inn";
        inn.kind = "inn";
        sheet.places.addAll(List.of(cave, mine, inn));
        sheet.normalize();
        assertEquals("cave", cave.kind);
        assertTrue(cave.outdoor);
        assertTrue(mine.outdoor, "a mine is under the ground too");
        assertFalse(inn.outdoor, "a name that only starts like 'mine' stays a building");
    }

    @Test
    void tooManyPlacesIsAProblem() {
        ScenarioSheet sheet = new ScenarioSheet();
        for (int i = 0; i < ScenarioSheet.MAX_PLACES + 1; i++) {
            ScenarioSheet.Place p = new ScenarioSheet.Place();
            p.id = "p" + i;
            sheet.places.add(p);
        }
        assertTrue(sheet.normalize().stream().anyMatch(s -> s.contains("at most")));
    }
}

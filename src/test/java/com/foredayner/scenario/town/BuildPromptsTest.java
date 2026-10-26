package com.foredayner.scenario.town;

import com.foredayner.scenario.sheet.ScenarioSheet;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BuildPromptsTest {

    private static ScenarioSheet greymouth() throws Exception {
        ScenarioSheet sheet = ScenarioSheet.fromJson(Files.readString(Path.of("src/gametest/resources/scenarios/greymouth.sheet.json")));
        sheet.normalize();
        return sheet;
    }

    @Test
    void erasAreToldApart() {
        assertFalse(Archetypes.modern("1920s"));
        assertFalse(Archetypes.modern("1890s Victorian"));
        assertTrue(Archetypes.modern("modern"));
        assertTrue(Archetypes.modern("1980년대"));
        assertTrue(Archetypes.modern("현대"));
    }

    @Test
    void roomsGetFurnitureFromTheirUseInEitherLanguage() {
        assertTrue(Archetypes.furniture("서재").contains("desk"));
        assertTrue(Archetypes.furniture("Kitchen: where the cook works").contains("stove"));
        assertTrue(Archetypes.furniture("예배당 본당").contains("pews"));
        assertEquals("", Archetypes.furniture("비밀 통로"));
    }

    @Test
    void buildingsAreToldWhatTheyAreHowTheyLookAndWhatIsAroundThem() throws Exception {
        ScenarioSheet sheet = greymouth();
        ScenarioSheet.Place church = sheet.place("church");
        String extra = BuildPrompts.extra(sheet, church, 21);
        assertTrue(extra.contains("steeple"), "a church gets its steeple: " + extra);
        assertTrue(extra.contains("Condition: worn"), extra);
        assertTrue(extra.contains("Around the building"), extra);
        assertTrue(BuildPrompts.extra(sheet, sheet.place("mansion"), 31).contains("Condition: sinister"));
        assertEquals(sheet.styleGuide, BuildPrompts.style(sheet), "the guide replaces the short style notes");
        List<String> rooms = BuildPrompts.rooms(sheet.place("inn"));
        assertTrue(rooms.stream().anyMatch(r -> r.contains("furnish with")), "rooms come with furniture: " + rooms);
    }

    @Test
    void ordinaryHousesVaryButStayTheSame() throws Exception {
        ScenarioSheet sheet = greymouth();
        ScenarioSheet.Place store = sheet.place("general_store");
        String a = Archetypes.variant(sheet, store);
        assertEquals(a, Archetypes.variant(sheet, store), "the same place gets the same variation every time");
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (int i = 0; i < 12; i++) {
            ScenarioSheet.Place p = new ScenarioSheet.Place();
            p.id = "house" + i;
            p.kind = "house";
            seen.add(Archetypes.variant(sheet, p));
        }
        assertTrue(seen.size() >= 5, "a street of houses is not all alike: " + seen.size());
        assertEquals("", Archetypes.variant(sheet, sheet.place("church")), "landmarks are not varied");
    }
}

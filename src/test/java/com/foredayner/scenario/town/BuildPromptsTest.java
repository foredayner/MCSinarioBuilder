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
        assertTrue(extra.contains("CONDITION worn"), extra);
        assertTrue(extra.contains("mossy_cobblestone"), "the condition names its blocks: " + extra);
        assertTrue(extra.contains("FACADE RULES"), extra);
        assertTrue(extra.contains("\n- tall pointed (lancet) windows of stained glass along the sides"), "features are a checklist: " + extra);
        assertTrue(extra.contains("Around the building"), extra);
        assertTrue(BuildPrompts.extra(sheet, sheet.place("mansion"), 31).contains("CONDITION sinister"));
        assertEquals(sheet.styleGuide, BuildPrompts.style(sheet), "the guide replaces the short style notes");
        List<String> rooms = BuildPrompts.rooms(sheet.place("inn"));
        assertTrue(rooms.stream().anyMatch(r -> r.contains("furnish with")), "rooms come with furniture: " + rooms);
    }

    @Test
    void koreanPlacesGetReferenceCards() throws Exception {
        ScenarioSheet korea = ScenarioSheet.fromJson(Files.readString(Path.of("src/gametest/resources/scenarios/modern_korea.sheet.json")));
        korea.normalize();
        assertTrue(References.korean(korea));
        assertTrue(References.card(korea, korea.place("police")).contains("파출소"));
        assertTrue(References.card(korea, korea.place("convenience_store")).contains("convenience store"));
        assertTrue(References.card(korea, korea.place("church")).contains("red cross"));
        assertTrue(References.card(korea, korea.place("villa_a")).contains("pilotis"));
        assertTrue(BuildPrompts.extra(korea, korea.place("police"), 15).contains("REFERENCE"));
        ScenarioSheet harbour = greymouth();
        assertFalse(References.korean(harbour), "a New England town gets no Korean cards");
        assertEquals("", References.card(harbour, harbour.place("church")));
        korea.place("police").refDescription = "A 2-storey white tile box with a navy band.";
        String withPhoto = BuildPrompts.extra(korea, korea.place("police"), 15);
        assertTrue(withPhoto.contains("PHOTO REFERENCE") && withPhoto.contains("navy band"), "the keeper's photo is followed");
        assertFalse(withPhoto.contains("REFERENCE (how such"), "and replaces the general card");
        korea.era = "1920s";
        assertTrue(References.card(korea, korea.place("church")).contains("Gothic"), "colonial-era cards for the 1920s");
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

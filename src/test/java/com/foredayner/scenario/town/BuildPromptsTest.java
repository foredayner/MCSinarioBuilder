package com.foredayner.scenario.town;

import com.foredayner.scenario.sheet.Period;
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
        assertFalse(Archetypes.modern("1950s (early 1950s)"), "a 1950s village has no asphalt or street lights");
    }

    @Test
    void aMountainVillageBuildsLogAndShingleHousesNotWesternOnes() throws Exception {
        ScenarioSheet sheet = ScenarioSheet.fromJson(Files.readString(Path.of("src/gametest/resources/scenarios/meigol.sheet.json")));
        assertEquals(List.of(), sheet.normalize());
        assertTrue(References.mountainVillage(sheet));
        String extra = BuildPrompts.extra(sheet, sheet.place("jang_house"), 15);
        assertTrue(extra.contains("너와집"), extra);
        assertTrue(extra.contains("장독대"), extra);
        assertFalse(extra.contains("shutters (trapdoors)"), "no western facade rules: " + extra);
        assertFalse(extra.contains("mailbox by the path"), extra);
        assertEquals("house_w1", sheet.place("house_e2").sameAs);
        assertTrue(BuildPrompts.prop(sheet, sheet.place("pyeongsang")).contains("평상"));
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
        assertTrue(extra.contains("no lines or stripes") && extra.contains("73856093"), "weathered blocks are scattered by a hash");
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
        korea.period = Period.EAST_MODERN;
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

    @Test
    void thePeriodIsGuessedForSheetsWithoutOne() {
        assertEquals(Period.WEST_MODERN, Period.guess("1920s", "Greymouth, a harbour town in Massachusetts", "", ""));
        assertEquals(Period.WEST_CONTEMPORARY, Period.guess("modern (2020s)", "a suburb of Ohio", "", ""));
        assertEquals(Period.WEST_MEDIEVAL, Period.guess("1340s", "a village in Bavaria", "", ""));
        assertEquals(Period.EAST_MEDIEVAL, Period.guess("조선 후기", "a village in Joseon", "", ""));
        assertEquals(Period.EAST_MODERN, Period.guess("1930s", "경성, Korea", "", ""));
        assertEquals(Period.EAST_POSTWAR, Period.guess("1950s", "a mountain village in Gangwon-do, Korea", "", ""));
        assertEquals(Period.EAST_POSTWAR, Period.guess("1960년대", "서울 변두리", "", ""));
        assertEquals(Period.EAST_CONTEMPORARY, Period.guess("modern (2020s)", "Gyeonggi-do, South Korea", "", ""));
        assertEquals(Period.OTHER, Period.guess("unknown", "a fantasy kingdom of elves", "", ""));
        ScenarioSheet sheet = new ScenarioSheet();
        sheet.era = "1920s";
        sheet.period = "nonsense";
        sheet.normalize();
        assertEquals(Period.WEST_MODERN, sheet.period, "an unknown period is replaced by a guess");
    }

    @Test
    void eachPeriodHasItsOwnChecklistAndOtherHasNone() throws Exception {
        ScenarioSheet sheet = greymouth();
        ScenarioSheet.Place church = sheet.place("church");
        java.util.Map<String, String> seen = new java.util.HashMap<>();
        for (String period : Period.ALL) {
            sheet.period = period;
            String extra = BuildPrompts.extra(sheet, church, 21);
            if (Period.OTHER.equals(period)) {
                assertFalse(extra.contains("MUST HAVE"), "no checklist: " + extra);
                assertFalse(extra.contains("FACADE RULES"), extra);
                assertFalse(extra.contains("Around the building"), extra);
                assertTrue(extra.contains("CONDITION"), "the condition still shows: " + extra);
            } else {
                assertTrue(extra.contains("MUST HAVE"), period + ": " + extra);
                assertTrue(extra.contains("FACADE RULES"), period + ": " + extra);
                seen.put(Archetypes.checklist("church", period), period);
            }
        }
        // Contemporary west and east share the modern checklist; the other five differ.
        assertEquals(6, seen.size(), seen.toString());
        assertTrue(Archetypes.checklist("church", Period.EAST_MEDIEVAL).contains("temple"));
        assertTrue(Archetypes.checklist("house", Period.EAST_POSTWAR).contains("slate"));
        assertTrue(Archetypes.checklist("house", Period.WEST_MEDIEVAL).contains("timber-framed"));
        assertFalse(Archetypes.checklist("house", Period.EAST_MEDIEVAL).contains("mailbox"));
        assertTrue(Archetypes.furniture("안방", Period.EAST_POSTWAR).contains("no bed blocks"));
        assertTrue(Archetypes.furniture("bedroom", Period.WEST_MODERN).contains("a bed"));
        assertTrue(Archetypes.yard(church, Period.EAST_MEDIEVAL).contains("jar terrace"));
        ScenarioSheet.Place graves = new ScenarioSheet.Place();
        graves.kind = "cemetery";
        graves.outdoor = true;
        sheet.period = Period.EAST_MEDIEVAL;
        assertTrue(BuildPrompts.prop(sheet, graves).contains("mound"), "eastern graves are mounds");
        sheet.period = Period.WEST_MODERN;
        assertTrue(BuildPrompts.prop(sheet, graves).contains("angel"));
    }

    @Test
    void nothingIsAFigureAndOutdoorThingsAreBlocks() throws Exception {
        ScenarioSheet sheet = greymouth();
        assertTrue(BuildPrompts.extra(sheet, sheet.place("inn"), 21).contains("NEVER build people"));
        String prop = BuildPrompts.propExtra();
        assertTrue(prop.contains("NOT a building") && prop.contains("NEVER build people"), prop);
        ScenarioSheet.Place well = new ScenarioSheet.Place();
        well.name = "우물";
        well.kind = "landmark";
        assertTrue(BuildPrompts.propDescription(sheet, well).startsWith("outdoor object"));
        assertTrue(Archetypes.checklist("ruins", Period.EAST_POSTWAR).contains("no roof"));
    }

    @Test
    void sameAsMustNameAnotherPlace() throws Exception {
        ScenarioSheet sheet = greymouth();
        sheet.places.get(0).sameAs = sheet.places.get(0).id;
        sheet.places.get(1).sameAs = "nowhere";
        List<String> problems = sheet.normalize();
        assertEquals("", sheet.places.get(0).sameAs);
        assertEquals("", sheet.places.get(1).sameAs);
        assertEquals(2L, problems.stream().filter(p -> p.contains("same as")).count(), problems.toString());
    }
}

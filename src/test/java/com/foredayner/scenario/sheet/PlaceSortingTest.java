package com.foredayner.scenario.sheet;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PlaceSortingTest {

    private static ScenarioSheet.Place place(String id, String name, String kind) {
        ScenarioSheet.Place p = new ScenarioSheet.Place();
        p.id = id;
        p.name = name;
        p.kind = kind;
        p.size = "M";
        return p;
    }

    @Test
    void placesThatAreNotBuildingsAreSortedOut() {
        ScenarioSheet sheet = new ScenarioSheet();
        sheet.places.add(place("accident", "사고 현장 (숲길 내리막)", "other"));
        sheet.places.add(place("village", "배산리 마을", "other"));
        sheet.places.add(place("shaman", "신씨 할머니의 무당집", "house"));
        sheet.places.add(place("well", "마을 구석의 버려진 우물", "other"));
        sheet.places.add(place("shrine", "손의 신당", "other"));
        sheet.places.add(place("forest", "범이 사는 배산 숲", "other"));
        sheet.places.add(place("graves", "뒷산 공동묘지", "other"));
        List<String> notes = sheet.sortOutPlaces();

        assertNull(sheet.place("village"), "the village itself is the map, not a place: " + notes);
        assertEquals("scene", sheet.place("accident").kind);
        assertTrue(sheet.place("accident").outdoor);
        assertEquals("S", sheet.place("accident").size);
        assertEquals("landmark", sheet.place("well").kind);
        assertTrue(sheet.place("well").outdoor);
        assertEquals("woods", sheet.place("forest").kind);
        assertTrue(sheet.place("forest").outdoor);
        assertEquals("cemetery", sheet.place("graves").kind);
        assertFalse(sheet.place("shaman").outdoor, "a house stays a building");
        assertFalse(sheet.place("shrine").outdoor, "a shrine (신당) stays a building");
        assertEquals(5, notes.size(), notes.toString());
        assertTrue(sheet.normalize().isEmpty(), "the sorted sheet is consistent");
    }

    @Test
    void aVillageWithThingsInItBecomesTheCommonAndKeepersChangesStay() {
        ScenarioSheet sheet = new ScenarioSheet();
        sheet.places.add(place("village", "배산리 마을", "other"));
        sheet.places.add(place("inn", "주막", "inn"));
        ScenarioSheet.Npc npc = new ScenarioSheet.Npc();
        npc.name = "촌장";
        npc.place = "village";
        sheet.npcs.add(npc);
        sheet.sortOutPlaces();
        assertEquals("square", sheet.place("village").kind, "NPCs stand there, so it stays as the village common");
        assertTrue(sheet.place("village").outdoor);
        // The keeper turned a road spot into a building: normalizing never undoes it.
        ScenarioSheet.Place spot = place("pass", "고갯마루 고개", "house");
        sheet.places.add(spot);
        sheet.normalize();
        assertEquals("house", spot.kind);
        assertFalse(spot.outdoor);
    }
}

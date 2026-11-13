package com.foredayner.scenario.sheet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ClueKindTest {

    @Test
    void cluesKnowHowFoundryTreatsThem() {
        ScenarioSheet.Clue c = new ScenarioSheet.Clue();
        c.name = "책장";
        assertEquals("normal", c.kind());
        c.carry = true;
        c.name = "피 묻은 열쇠";
        assertEquals("item", c.kind());
        assertEquals("tripwire_hook", c.item());
        c.hidden = true;
        assertEquals("hidden", c.kind(), "spotted first");
        c.name = "일기";
        c.form = "book";
        assertEquals("written_book", c.item());
    }

    @Test
    void emptyChecksAreDropped() {
        ScenarioSheet sheet = ScenarioSheet.fromAnswer("{\"title\":\"t\",\"places\":[{\"id\":\"a\",\"name\":\"집\",\"kind\":\"house\"}],"
            + "\"clues\":[{\"id\":\"c\",\"name\":\"편지\",\"place\":\"a\",\"carry\":true,"
            + "\"checks\":[{\"label\":\"필체\",\"skill\":\"library\",\"text\":\"\"},{\"label\":\"냄새\",\"skill\":\"spot\",\"text\":\"향수\"}]}]}");
        sheet.normalize();
        assertEquals(1, sheet.clues.get(0).checks.size());
        assertTrue(sheet.clues.get(0).carry);
    }
}

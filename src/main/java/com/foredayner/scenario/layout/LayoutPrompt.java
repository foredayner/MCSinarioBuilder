package com.foredayner.scenario.layout;

import com.example.ai.AiPrompt;
import com.foredayner.scenario.sheet.ScenarioSheet;

/** The instructions for laying out the town (fixed part, cached by Claude). */
public final class LayoutPrompt {

    private LayoutPrompt() {
    }

    static final String SYSTEM = """
        You lay out a Call of Cthulhu town on a Minecraft ground of 160 x 160 blocks (1 block = 1 metre).
        x grows to the east, z grows to the south, (0,0) is the north-west corner.
        You get the places (with their square footprint), how they are linked, the terrain, and a DRAFT layout
        made by code that already passes every check. Improve the draft so the town fits the story, then answer
        with ONLY one JSON object (no markdown, no talk):

        {
          "places": [ { "id": "place id", "x": west edge, "z": north edge, "front": "north|east|south|west" } ],
          "roads": [ { "points": [[x,z],[x,z],...], "width": 3-5, "kind": "road|path" } ],
          "zones": [ { "kind": "water|woods|field|cemetery|square|garden|beach", "x": .., "z": .., "w": .., "d": .. } ]
        }

        Rules (checked by code; a layout that breaks one is sent back):
        - Every place gets exactly one entry. A place covers x..x+size-1 and z..z+size-1, inside 2..158.
        - Places stay at least 2 blocks apart and never stand on a road, in water or in woods (a dock may
          reach into the water).
        - "front" is the side with the entrance. The block just outside the middle of the front side must be
          within 4 blocks of a road (outdoor places are exempt but should still have a path).
        - Roads are centre lines through points (straight pieces, use right angles), width 5 for main streets,
          3 for side streets, "path" for dirt paths. All roads together form one connected network.
        - Zones: the sea along the coast edge, woods along the woods edge, plus fields, gardens, a square in
          front of the main public building, a cemetery zone under a cemetery place.

        Make it feel like the story: the harbour and lighthouse at the water, the church on the square, the
        rich house apart on its own road, the inn and shops on the main street, lonely or sinister places at the
        edge of town, places the scenario links by "path" or "secret" further out. Keep what already works in
        the draft; move things only for a reason.
        """;

    static String places(ScenarioSheet sheet) {
        StringBuilder sb = new StringBuilder();
        for (ScenarioSheet.Place p : sheet.places) {
            int f = ScenarioSheet.footprint(p.size);
            sb.append("- ").append(p.id).append(" \"").append(p.name).append("\" ").append(p.kind).append(", ")
                .append(f).append('x').append(f).append(p.outdoor ? ", outdoor" : "").append(p.filler ? ", filler house" : "");
            if (!p.notes.isBlank()) {
                sb.append(" - ").append(p.notes);
            }
            sb.append('\n');
        }
        sb.append("\nLINKS:\n");
        for (ScenarioSheet.Link l : sheet.links) {
            sb.append("- ").append(l.from).append(" -> ").append(l.to).append(" (").append(l.kind).append(")\n");
        }
        sb.append("\nTERRAIN: coast ").append(sheet.terrain.coast).append(", woods ").append(sheet.terrain.woods);
        if (!sheet.terrain.notes.isBlank()) {
            sb.append(" - ").append(sheet.terrain.notes);
        }
        sb.append("\nSETTING: ").append(sheet.setting).append(" / ").append(sheet.mood);
        return sb.toString();
    }

    /** Lay out the town, starting from the code's draft. */
    public static AiPrompt layout(ScenarioSheet sheet, TownLayout draft) {
        return new AiPrompt(SYSTEM, "PLACES:\n" + places(sheet) + "\n\nDRAFT LAYOUT:\n" + answerJson(draft)
            + "\n\nAnswer with the JSON object now.");
    }

    /** Change a layout as the keeper asks. */
    public static AiPrompt revise(ScenarioSheet sheet, TownLayout current, String instruction) {
        return new AiPrompt(SYSTEM, "PLACES:\n" + places(sheet) + "\n\nCURRENT LAYOUT:\n" + answerJson(current)
            + "\n\nTHE KEEPER ASKS: " + instruction + "\n\nAnswer with the whole changed JSON object now.");
    }

    public static AiPrompt fix(AiPrompt base, String answer, String problems) {
        String bad = answer.length() > 12_000 ? answer.substring(0, 12_000) : answer;
        return new AiPrompt(base.system(), base.user() + "\n\nYOUR PREVIOUS ANSWER HAD PROBLEMS:\n" + problems
            + "\n\nPrevious answer:\n" + bad + "\n\nAnswer with the whole corrected JSON object only.");
    }

    /** A layout in the answer's shape (sizes left out: they come from the sheet). */
    static String answerJson(TownLayout l) {
        StringBuilder sb = new StringBuilder("{\"places\":[");
        for (int i = 0; i < l.sites.size(); i++) {
            TownLayout.Site s = l.sites.get(i);
            sb.append(i == 0 ? "" : ",").append("\n {\"id\":\"").append(s.id).append("\",\"x\":").append(s.x)
                .append(",\"z\":").append(s.z).append(",\"front\":\"").append(s.front).append("\"}");
        }
        sb.append("],\n\"roads\":[");
        for (int i = 0; i < l.roads.size(); i++) {
            TownLayout.Road r = l.roads.get(i);
            sb.append(i == 0 ? "" : ",").append("\n {\"points\":[");
            for (int k = 0; k < r.points.size(); k++) {
                sb.append(k == 0 ? "" : ",").append('[').append(r.points.get(k)[0]).append(',').append(r.points.get(k)[1]).append(']');
            }
            sb.append("],\"width\":").append(r.width).append(",\"kind\":\"").append(r.kind).append("\"}");
        }
        sb.append("],\n\"zones\":[");
        for (int i = 0; i < l.zones.size(); i++) {
            TownLayout.Zone z = l.zones.get(i);
            sb.append(i == 0 ? "" : ",").append("\n {\"kind\":\"").append(z.kind).append("\",\"x\":").append(z.x)
                .append(",\"z\":").append(z.z).append(",\"w\":").append(z.w).append(",\"d\":").append(z.d).append('}');
        }
        return sb.append("]}").toString();
    }
}

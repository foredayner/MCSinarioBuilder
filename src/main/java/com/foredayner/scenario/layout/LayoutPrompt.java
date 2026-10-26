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
        - Outdoor places of kind "scene" (a spot by a road) sit right beside a road; "landmark" ones (a well, a
          cairn) in the open where people pass.
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

    /** What to do with the keeper's map picture, sent with the request. */
    static final String MAP = """
        A MAP of the town is attached (a picture drawn by the keeper). Follow the map, not the draft:
        - Stretch the picture over the whole ground: its left edge is x=0, its right edge x=160, its top z=0 and its
          bottom z=160 (the top of the picture is north).
        - Every place whose name (or a clearly matching name) is written on the map goes where the map shows it: put
          the middle of its square on that spot, then shift it as little as needed to pass the checks. Its front
          faces the nearest road.
        - Roads: trace the map's roads and paths as centre lines through points along them. Diagonal and curved
          roads are fine: use several points close together. Keep widths 3-5, all connected; roads that leave the
          map run on to the edge of the ground.
        - Water, woods and fields drawn on the map become zones in the same places.
        - Places that are not on the map go to free spots that suit the story.
        - Also add "unnamed": the number of buildings drawn on the map whose name is not in the place list.
        """;

    /** Lay out the town, starting from the code's draft (or after the keeper's map). */
    public static AiPrompt layout(ScenarioSheet sheet, TownLayout draft) {
        return layout(sheet, draft, false);
    }

    public static AiPrompt layout(ScenarioSheet sheet, TownLayout draft, boolean map) {
        return new AiPrompt(SYSTEM, "PLACES:\n" + places(sheet) + "\n\nDRAFT LAYOUT:\n" + answerJson(draft)
            + (map ? "\n\n" + MAP : "") + "\n\nAnswer with the JSON object now.");
    }

    /** Change a layout as the keeper asks. */
    public static AiPrompt revise(ScenarioSheet sheet, TownLayout current, String instruction) {
        return revise(sheet, current, instruction, false);
    }

    public static AiPrompt revise(ScenarioSheet sheet, TownLayout current, String instruction, boolean map) {
        return new AiPrompt(SYSTEM, "PLACES:\n" + places(sheet) + "\n\nCURRENT LAYOUT:\n" + answerJson(current)
            + (map ? "\n\nThe keeper's MAP of the town is attached: keep the layout true to it." : "")
            + "\n\nTHE KEEPER ASKS: " + instruction + "\n\nAnswer with the whole changed JSON object now.");
    }

    /** The "unnamed" count from an answer made with a map, or -1. */
    public static int unnamed(String answer) {
        var m = java.util.regex.Pattern.compile("\"unnamed\"\\s*:\\s*(\\d+)").matcher(answer == null ? "" : answer);
        return m.find() ? Integer.parseInt(m.group(1)) : -1;
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

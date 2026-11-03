package com.foredayner.scenario.layout;

import com.example.ai.AiPrompt;
import com.foredayner.scenario.sheet.ScenarioSheet;

/** The instructions for laying out the town (fixed part, cached by Claude). */
public final class LayoutPrompt {

    private LayoutPrompt() {
    }

    static final String SYSTEM = """
        You lay out a Call of Cthulhu town on a square Minecraft ground (its size is given below; 1 block = 1 metre).
        x grows to the east, z grows to the south, (0,0) is the north-west corner.
        You get the places (with their square footprint), how they are linked, the terrain, and a DRAFT layout
        made by code that already passes every check. Improve the draft so the town fits the story, then answer
        with ONLY one JSON object (no markdown, no talk):

        {
          "places": [ { "id": "place id", "x": west edge, "z": north edge, "front": "north|east|south|west", "from_map": true|false } ],
          "roads": [ { "points": [[x,z],[x,z],...], "width": 3-5, "kind": "road|path" } ],
          "zones": [ { "kind": "water|woods|field|cemetery|square|garden|beach", "x": .., "z": .., "w": .., "d": .. } ]
        }

        Rules (checked by code; a layout that breaks one is sent back):
        - Every place gets exactly one entry. A place covers x..x+size-1 and z..z+size-1, inside the ground with a 2-block margin.
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
            int f = ScenarioSheet.footprint(p);
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
        - First say what it shows, as "map_kind": "town" (buildings seen from above), "outdoor" (paths, woods, water,
          landmarks; few or no buildings) or "indoor" (the rooms of one building: a floor plan).
        - Stretch the picture over the whole ground: its left edge is x=0, its right edge x=GROUND, its top z=0 and its
          bottom z=GROUND (the top of the picture is north).
        - Every place whose name (or a clearly matching name) is written on the map goes where the map shows it: put
          the middle of its square on that spot and mark it "from_map": true. Its front faces the nearest road.
          Do not move a map place away from its spot to reach a road: code lays a footpath from its front to the
          nearest road, and moves anything standing on a road or another place by the least amount.
        - Buildings drawn on the map whose name is not in the place list (or that have no name): give them first to
          ordinary places that are not on the map (a house, a shop, a farm...), one fitting place each ("from_map":
          true).
        - Places still without a spot ("from_map": false): kinds that belong outside the village (a shrine, a cave,
          a grave, ruins, a mountain or forest spot) go near the matching edge or feature of the map (the mountain,
          the woods, the water); the others near the places their notes tie them to. Never on the map's roads,
          water, fields or buildings.
        - Roads: trace the map's roads and paths as centre lines through points along them. Diagonal and curved
          roads are fine: use several points close together. Keep widths 3-5, all connected; roads that leave the
          map run on to the edge of the ground.
        - Water, woods and fields drawn on the map become zones in the same places.
        - Also add "extra": the buildings drawn on the map that still have no place, each as
          {"x": west edge, "z": north edge, "size": "S" (up to 11 blocks) or "M" (up to 15), "front": "north|east|south|west"}.
        """;

    /** Lay out the town, starting from the code's draft (or after the keeper's map). */
    public static AiPrompt layout(ScenarioSheet sheet, TownLayout draft) {
        return layout(sheet, draft, false);
    }

    public static AiPrompt layout(ScenarioSheet sheet, TownLayout draft, boolean map) {
        int n = ScenarioSheet.groundSize(sheet);
        return new AiPrompt(SYSTEM, "GROUND: " + n + " x " + n + " blocks.\n\nPLACES:\n" + places(sheet) + "\n\nDRAFT LAYOUT:\n" + answerJson(draft)
            + (map ? "\n\n" + MAP.replace("GROUND", String.valueOf(n)) : "") + "\n\nAnswer with the JSON object now.");
    }

    /** Change a layout as the keeper asks. */
    public static AiPrompt revise(ScenarioSheet sheet, TownLayout current, String instruction) {
        return revise(sheet, current, instruction, false);
    }

    public static AiPrompt revise(ScenarioSheet sheet, TownLayout current, String instruction, boolean map) {
        int n = ScenarioSheet.groundSize(sheet);
        return new AiPrompt(SYSTEM, "GROUND: " + n + " x " + n + " blocks.\n\nPLACES:\n" + places(sheet) + "\n\nCURRENT LAYOUT:\n" + answerJson(current)
            + (map ? "\n\nThe keeper's MAP of the town is attached: keep the layout true to it." : "")
            + "\n\nTHE KEEPER ASKS: " + instruction + "\n\nAnswer with the whole changed JSON object now.");
    }

    /** What the map shows, from an answer made with a map: town, outdoor, indoor, or "" when not said. */
    public static String mapKind(String answer) {
        var m = java.util.regex.Pattern.compile("\"map_kind\"\\s*:\\s*\"(town|outdoor|indoor)\"").matcher(answer == null ? "" : answer);
        return m.find() ? m.group(1) : "";
    }

    /** The map's buildings that no place took: {x, z, footprint, front index}. */
    public static java.util.List<int[]> extras(String answer) {
        java.util.List<int[]> out = new java.util.ArrayList<>();
        try {
            int start = answer.indexOf('{'), end = answer.lastIndexOf('}');
            var root = com.google.gson.JsonParser.parseString(answer.substring(start, end + 1)).getAsJsonObject();
            if (!root.has("extra") || !root.get("extra").isJsonArray()) {
                return out;
            }
            for (var e : root.getAsJsonArray("extra")) {
                var o = e.getAsJsonObject();
                String size = o.has("size") ? o.get("size").getAsString() : "S";
                String front = o.has("front") ? o.get("front").getAsString().toLowerCase(java.util.Locale.ROOT) : "south";
                out.add(new int[] { o.get("x").getAsInt(), o.get("z").getAsInt(), ScenarioSheet.footprint("M".equals(size) ? "M" : "S"),
                    Math.max(0, TownLayout.FRONTS.indexOf(front)) });
            }
        } catch (RuntimeException e) {
            return out;
        }
        return out;
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

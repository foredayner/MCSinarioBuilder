package com.foredayner.scenario.sheet;

import com.example.ai.AiPrompt;

/** The instructions for reading a scenario into a sheet (fixed part, cached by Claude). */
public final class SheetPrompt {

    private SheetPrompt() {
    }

    static final String SYSTEM = """
        You help a Call of Cthulhu keeper turn a scenario into a Minecraft town map of 160 x 160 blocks
        (1 block = 1 metre). Read the scenario and answer with ONLY one JSON object (no markdown, no talk).

        JSON shape (all keys required; use [] for none):
        {
          "title": "...", "era": "1920s" or "modern" (or the era the scenario names), "setting": "where and when, one line",
          "period": "west_medieval|west_modern|west_contemporary|east_medieval|east_modern|east_postwar|east_contemporary|other",
          "mood": "a few words", "timeOfDay": "day" | "dusk" | "night", "weather": "clear" | "rain" | "thunder",
          "style": "building style rules for the whole town, 3-6 short lines, naming Minecraft blocks",
          "places": [ { "id": "short_ascii_id", "name": "name as in the scenario", "kind": "house|inn|shop|church|library|
                        police|newspaper|school|hospital|mansion|farm|mill|factory|warehouse|dock|lighthouse|cemetery|woods|
                        square|cave|scene|landmark|other", "size": "S|M|L|XL", "floors": 1-3, "basement": true|false,
                        "rooms": [ { "name": "...", "purpose": "..." } ], "scenes": [ "short scene names here" ],
                        "notes": "what matters for building it", "filler": false, "outdoor": false,
                        "condition": "kept|worn|abandoned|sinister" } ],
          "npcs": [ { "name": "...", "role": "...", "place": "place id", "room": "room name or ''", "notes": "..." } ],
          "clues": [ { "id": "short_ascii_id", "name": "...", "text": "what it says or shows, 1-3 sentences",
                       "form": "book|note|map|item", "place": "place id", "room": "room name", "hidden": true|false } ],
          "links": [ { "from": "place id", "to": "place id", "kind": "road|path|secret" } ],
          "terrain": { "coast": "none|north|east|south|west", "woods": "none|north|east|south|west", "notes": "..." }
        }

        Rules:
        - Places: every place the investigators visit or that matters to the story; merge tiny ones into a bigger one.
          Only buildings are built as buildings. The town or village itself is NOT a place (it is the whole map: put
          its look in style, mood and terrain). Woods, hills, fields and valleys are kind "woods" (or "square" for an
          open common), "outdoor": true. A spot on a road or path (an accident site, a slope, a pass, a crossroads,
          a bridge) is kind "scene", size S, "outdoor": true. A small thing standing on its own (a well, a cairn, a
          stone tablet, a totem pole, an old tree) is kind "landmark", size S, "outdoor": true. Graves are "cemetery".
          4-12 places from the scenario, plus up to 3 "filler": true ordinary houses or shops so the town feels lived in.
        - Sizes: S = 11x11 (shed, cottage), M = 15x15 (house, shop, small church), L = 21x21 (inn, church, police
          station, library), XL = 31x31 (mansion, factory). Outdoor places (cemetery, woods, dock) set "outdoor": true.
          Places under the ground (a cave, a crypt below, a tunnel, a mine) are kind "cave", "outdoor": true: the town shows
          their entrance only.
        - Rooms: list the rooms of each building with their use, always including the rooms where scenes happen, NPCs
          stand and clues lie. A basement room only if "basement": true. Secret rooms and passages are rooms too.
        - Clues: the physical things the investigators can find. form: book (journal, ledger, letters), note (one
          page), map (chart, plan), item (object). Give the text the clue reveals. hidden = behind or inside something.
        - Links: roads between places, paths to outdoor places, and secret passages (kind "secret") the scenario names.
        - Period: the period and culture of the buildings. west_medieval = Europe before 1800; west_modern = the West
          1800-1959 (the classic 1920s town); west_contemporary = the West from 1960; east_medieval = East Asia before 1900
          (Joseon, Edo); east_modern = East Asia 1900-1945 (Korea under Japanese rule); east_postwar = East Asia 1945 to
          the 1980s (Korea of the 1950s-60s); east_contemporary = East Asia from 1990; other = fantasy, science fiction or
          anything none of these fits.
        - Condition: how the place looks as the investigators find it: kept (lived in, cared for), worn (old, shabby),
          abandoned (empty, decaying), sinister (something is wrong here). Follow the scenario.
        - Style: fit the era and place. For a 1920s New England town for example: clapboard houses of spruce and birch
          planks with dark oak trim, red brick shops, dark slate roofs (deepslate tiles), cobblestone foundations,
          gravel streets, lanterns, white picket fences.
        - Write names and texts in the language of the scenario. ids are short lowercase ascii with underscores.
        """;

    /** Read a scenario. */
    public static AiPrompt analyze(String scenarioText) {
        return new AiPrompt(SYSTEM, "SCENARIO:\n" + scenarioText + "\n\nAnswer with the JSON object now.");
    }

    /** Change an existing sheet as the keeper asks. */
    public static AiPrompt revise(String sheetJson, String instruction, String scenarioText) {
        String excerpt = scenarioText.length() > 20_000 ? scenarioText.substring(0, 20_000) : scenarioText;
        return new AiPrompt(SYSTEM, "SCENARIO (for reference):\n" + excerpt + "\n\nCURRENT SHEET:\n" + sheetJson
            + "\n\nTHE KEEPER ASKS: " + instruction + "\n\nAnswer with the whole changed JSON object now.");
    }

    /** The same request again with what was wrong. */
    public static AiPrompt fix(AiPrompt base, String answer, String problems) {
        String bad = answer.length() > 12_000 ? answer.substring(0, 12_000) : answer;
        return new AiPrompt(base.system(), base.user() + "\n\nYOUR PREVIOUS ANSWER HAD PROBLEMS:\n" + problems
            + "\n\nPrevious answer:\n" + bad + "\n\nAnswer with the whole corrected JSON object only.");
    }
}

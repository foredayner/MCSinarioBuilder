package com.foredayner.scenario.layout;

import com.example.ai.AiPrompt;
import com.foredayner.scenario.sheet.ScenarioSheet;

/** The instructions for drawing the floor plan of a building stage (fixed part, cached by Claude). */
public final class IndoorPrompt {

    private IndoorPrompt() {
    }

    static final String SYSTEM = """
        You draw the floor plan of ONE building where a Call of Cthulhu scenario happens, in Minecraft blocks
        (1 block = 1 metre). x grows to the east, z to the south; the building's north-west corner is (0,0).
        Answer with ONLY one JSON object (no markdown, no talk):

        {
          "rooms": [ { "id": "room id", "level": 0, "x": west edge, "z": north edge, "w": width, "d": depth } ],
          "doors": [ { "x": .., "z": .., "level": 0, "entrance": false, "secret": false } ],
          "stairs": [ { "x": .., "z": .., "level": 0, "dir": "north|east|south|west" } ]
        }

        Rules (checked by code):
        - Every room of the list gets exactly one entry, on its level (0 = ground floor, 1 = the floor above,
          -1 = basement). Corridors, halls and landings that are not in the list get ids starting with "_"
          ("_hall", "_corridor1"); add them where the rooms need a way between them.
        - A room covers x..x+w-1 and z..z+d-1 WITH its walls: its inside is one block in from each side. Rooms
          next to each other SHARE a wall: the next room starts on the last column of the one before (a room at
          x=0 with w=8 and its neighbour at x=7). Rooms of one level never overlap more than that.
        - Sizes: small rooms 6 x 6, rooms 8 x 8 to 10 x 12, halls bigger, corridors 4-5 wide. A floor at most 60 x 60.
          Upper floors stand over the floor below (inside its outline); a basement under the ground floor.
        - Doors are wall blocks: a door between two rooms is on their shared wall (not on a corner), so one side is
          inside each room. The entrance ("entrance": true) is a door in an outside wall of the ground floor, best
          on the south side. A hidden way into a secret room is a door with "secret": true (built as a false wall).
        - Stairs: a straight flight of 4 steps from level L up to L+1, starting at (x,z) and climbing toward dir.
          The 4 step cells are inside one room of level L; the cell after them (the landing) and the 3 cells under
          it are inside one room of level L+1. Put them along a wall, not in front of a door. One flight between
          each pair of floors that are used.
        - Every room must be reachable from the entrance through doors and stairs.

        Make it feel like the story: the rooms where scenes happen where the scenario puts them, servants' rooms
        by the kitchen, private rooms upstairs, secret rooms behind others (still with a door: it can be hidden).
        """;

    /** If a floor plan picture is attached. */
    static final String PICTURES = """
        FLOOR PLANS of the building are attached (pictures from the scenario). Follow them, not your own idea:
        - The same rooms in the same arrangement and proportions; match the rooms of the list to the labels on the
          plans (a label in another language or a number from a legend counts). Rooms on the plans that are not in
          the list become "_" rooms (corridors, halls, small rooms) so the building is complete.
        - Doors and stairs where the plans draw them. Each picture is one floor (or several floors side by side,
          labelled); read which floor each is.
        - Scale the plan so the rooms get the sizes above (the smallest room at least 5 x 5).
        """;

    static String rooms(ScenarioSheet sheet) {
        StringBuilder sb = new StringBuilder();
        for (ScenarioSheet.Place p : sheet.places) {
            int s = IndoorPlan.side(p.size);
            sb.append("- ").append(p.id).append(" \"").append(p.name).append("\" level ").append(p.level)
                .append(", about ").append(s).append('x').append(s);
            if (!p.notes.isBlank()) {
                sb.append(" - ").append(p.notes);
            }
            sb.append('\n');
        }
        for (ScenarioSheet.Link l : sheet.links) {
            if ("secret".equals(l.kind) && sheet.place(l.from) != null && sheet.place(l.to) != null) {
                sb.append("- a SECRET door between ").append(l.from).append(" and ").append(l.to).append('\n');
            }
        }
        sb.append("\nSETTING: ").append(sheet.setting).append(" / ").append(sheet.mood).append(" / period ").append(sheet.period());
        return sb.toString();
    }

    public static AiPrompt plan(ScenarioSheet sheet, TownLayout draft, boolean pictures) {
        return new AiPrompt(SYSTEM, "ROOMS:\n" + rooms(sheet) + "\n\nA PLAIN DRAFT (it passes the checks; improve it, or follow "
            + "the floor plans):\n" + IndoorPlan.answerJson(draft) + (pictures ? "\n\n" + PICTURES : "")
            + "\n\nAnswer with the JSON object now.");
    }

    public static AiPrompt revise(ScenarioSheet sheet, TownLayout current, String instruction, boolean pictures) {
        return new AiPrompt(SYSTEM, "ROOMS:\n" + rooms(sheet) + "\n\nCURRENT PLAN:\n" + IndoorPlan.answerJson(current)
            + (pictures ? "\n\nThe floor plans are attached: keep the plan true to them." : "")
            + "\n\nTHE KEEPER ASKS: " + instruction + "\n\nAnswer with the whole changed JSON object now.");
    }
}

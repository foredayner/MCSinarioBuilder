package com.foredayner.scenario.town;

import com.foredayner.scenario.sheet.ScenarioSheet;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** What each place asks the AI Build Assistant for: a building for a lot, or a prop for an outdoor place. */
public final class BuildPrompts {

    private BuildPrompts() {
    }

    /** Old size of an outdoor place's square (it now uses its whole lot). */
    public static final int PROP_LOT = 7;
    public static final int PROP_HEIGHT = 6;

    /** Never: figures of people or animals (the scenario's characters are played, not built). */
    public static final String NO_FIGURES = "NEVER build people, figures, bodies, ghosts, animals or statues and dolls shaped like"
        + " them (no armor stands, no heads, no mannequins, no skeletons), even when the scenario's characters are named"
        + " here: the characters are played at the table, not built. Everything is made of ordinary Minecraft blocks.";

    /** How high an outdoor place may go. */
    public static int outdoorHeight(ScenarioSheet.Place p) {
        return switch (Outdoors.type(p)) {
            case "tree" -> 24;
            case "cliff" -> Outdoors.terrain(p) ? 16 : 12;
            case "woods" -> 22;
            case "cave" -> 14;
            case "beach" -> 8;
            default -> Outdoors.terrain(p) ? 14 : 12;
        };
    }

    /** What an outdoor place is: a whole scene on its lot. */
    public static String propDescription(ScenarioSheet sheet, ScenarioSheet.Place p) {
        return "outdoor place \"" + p.name + "\": " + prop(sheet, p);
    }

    /** Rules for an outdoor place (the base ground of the lot is already laid). */
    public static String propExtra() {
        return propExtra(null);
    }

    public static String propExtra(ScenarioSheet.Place p) {
        int h = p == null ? 12 : outdoorHeight(p);
        boolean land = p != null && Outdoors.terrain(p);
        return "THIS IS AN OUTDOOR PLACE, NOT A BUILDING: no house, no walls around the site, no roof over it, no floor slab"
            + " under it. Make it a WHOLE SCENE that fills the site (up to " + h + " blocks high), as carefully made as a"
            + " building:\n"
            + (land
                ? "- THE LAND: the scenario asks for it to rise or fall here, so shape it: raise it with overlapping ai.mound and"
                    + " ai.rock (a hill, a bank, a crag, a cliff), dig down to y = -2 for water or a pit; the site's ground"
                    + " surface is y = -1.\n"
                : "- THE LAND STAYS FLAT: the site's ground surface is y = -1; do not raise or dig the land itself (single rocks,"
                    + " bushes and burial mounds standing on it are fine).\n")
            + "- The ground's surface is part of the work: paths (dirt_path, gravel, coarse_dirt, stone slabs) where people walk"
            + " and mixed patches (moss_block, podzol, rooted_dirt, mud, coarse_dirt) at y = -1, not one flat colour.\n"
            + "- Use the outdoor helpers, never trees or rocks block by block: ai.tree for EVERY tree (kind, height and"
            + " trunk as fits), ai.rock for rocks and boulders, ai.bush for bushes, ai.mound for heaps of earth.\n"
            + "- Plants in layers: trees, bushes, tall grass, ferns, flowers, vines, moss carpets.\n"
            + "- Everything under MUST HAVE (below) is required; add MAY ALSO HAVE things to make it rich. Small details round"
            + " them (lanterns, signs, fences, benches, stones, scattered things).\n"
            + "- Keep a walkable way in from the front edge (z = -1 side) to the middle, 2 wide, and room for the"
            + " investigators to stand.\n"
            + "- Use most of the site: spread the scene over it, not one lump in the middle.\n"
            + "- Nothing natural runs straight: paths, hedges, banks and edges turn at least every 7 blocks; paths 2-3 wide,"
            + " edged with a different block and wider where they meet; worn spots of coarse_dirt and dirt_path where people"
            + " walk. Water edges of mud and dirt with granite and gravel patches, reeds, lily pads and a few stones.\n"
            + "- Built things in the scene follow the same craft as buildings: a base, a body and a top (lamp posts with a"
            + " stone base, an arm and a hanging lantern; benches of stairs with trapdoor arms; bridges with a railing and"
            + " posts; wells with a stone ring, two posts and a small roof).\n" + NO_FIGURES;
    }

    /** A building with no rooms listed: one open space on each floor. */
    public static final String OPEN_PLAN = "INSIDE: ONE OPEN SPACE on each floor. No inner walls, partitions, corridors or small rooms:"
        + " the whole floor is one big room, with its furniture set out in areas (by the walls, around a table, a counter)"
        + " and the middle free to walk. Only stairs (and their railing) between floors.";

    /** A building with rooms listed: only those rooms, big. */
    public static final String ONLY_LISTED_ROOMS = "INSIDE: only the rooms listed are walled off (each at least 6 x 6 inside, the rooms"
        + " where scenes happen 8 x 8 or more); everything else on the floor stays ONE OPEN SPACE (no extra small rooms or"
        + " corridors).";

    /** Every way in: a double door with free ground in front of and behind it. */
    public static final String DOORS = "DOORS: every door in an outside wall is a DOUBLE door (two doors side by side, 2 blocks wide,"
        + " hinges on the outer sides). Keep the 2 blocks in front of each double door (outside) and the 2 blocks behind it"
        + " (inside) free: no blocks, furniture, fences, crates, plants or steps up there, only the floor or ground.";

    /** An old, abandoned or haunted building: textured walls (only these; others keep one wall block). */
    public static final String WORN = "THIS BUILDING IS OLD AND WORN: texture its walls with ai.wallMix - one main block with 2-3"
        + " near-colour blocks in PATCHES (not evenly sprinkled), the cracked and mossy ones low near the ground and under the"
        + " eaves, darker at the bottom and lighter toward the top; a few broken window panes, cobwebs in corners, a sagging"
        + " fence, weeds and vines at the foot of the walls.";

    /** Lights inside, fitting the period (dark spots left over get invisible light blocks after building). */
    public static final String LIGHTS = "LIGHT: every room has lights that fit the period, at least one per room and more in big"
        + " rooms: lanterns hung on chains, wall torches or lanterns, candles on tables and shelves (old times); glowstone,"
        + " sea_lantern or froglights set into the ceiling, lamps on desks (modern times).";

    /** How high a building may go (blocks above the ground). */
    public static int maxHeight(ScenarioSheet.Place p) {
        int h = Math.min(30, p.floors * 5 + 8);
        return switch (p.kind) {
            case "ruins" -> 6;
            case "lighthouse" -> 36;
            case "church" -> Math.max(h, 26);
            default -> h;
        };
    }

    public static String description(ScenarioSheet sheet, ScenarioSheet.Place p) {
        StringBuilder sb = new StringBuilder();
        sb.append(p.kind).append(" \"").append(p.name).append("\" in ").append(sheet.setting.isBlank() ? "a small town" : sheet.setting)
            .append(" (").append(sheet.era).append(", a Call of Cthulhu scenario). ")
            .append(p.floors).append(p.floors == 1 ? " floor" : " floors").append('.');
        if (!p.notes.isBlank()) {
            sb.append(' ').append(p.notes);
        }
        if (!sheet.mood.isBlank()) {
            sb.append(" Mood: ").append(sheet.mood).append('.');
        }
        return sb.toString();
    }

    /** Building stage: one room to furnish (its walls, floor, ceiling, doors and windows are built by code). */
    public static String roomDescription(ScenarioSheet sheet, ScenarioSheet.Place p) {
        StringBuilder sb = new StringBuilder();
        sb.append("the furnishing of the room \"").append(p.name).append("\" inside ").append(sheet.title.isBlank() ? "the building" : sheet.title)
            .append(" (").append(sheet.setting).append(", ").append(sheet.era).append(", a Call of Cthulhu scenario).");
        if (!p.notes.isBlank()) {
            sb.append(' ').append(p.notes);
        }
        if (!p.scenes.isEmpty()) {
            sb.append(" Scenes here: ").append(String.join("; ", p.scenes)).append('.');
        }
        sb.append(" Condition: ").append(p.condition).append('.');
        if (!sheet.mood.isBlank()) {
            sb.append(" Mood: ").append(sheet.mood).append('.');
        }
        return sb.toString();
    }

    /** What a room's furnishing must and must not do. */
    public static final String ROOM_EXTRA = """
        THIS IS NOT A BUILDING: it is the inside of ONE ROOM whose walls, floor, ceiling, doors and windows already
        stand just outside this space. Do NOT build walls, a floor, a ceiling, a roof, doors or windows.
        - The site is the room's empty inside, from the floor (y = 0) to just under the ceiling. Fill it with the
          furniture and things this room is for, mostly along the walls (beds, tables and chairs, shelves, cupboards,
          a stove, rugs of carpet, paintings and banners on the walls, plants), in the period's style.
        - The door is in the middle of the FRONT edge (z = -1 side): keep a clear path 1 block wide from there into
          the room, and the middle of the room walkable.
        - Light it: a lantern, candles or lamps (one or two light sources).
        - In a modern or contemporary setting, use the ready-made furniture (ai.prop) for what blocks cannot show
          well (sofa, armchair, TV, fridge, lamps, clock, phone, computer, curtains, partitions); never in older periods.
        - Doors to other rooms may be in the other walls too: keep the middle block of each wall free on the floor.
        """;

    /** The town's style for every building: the keeper's guide if there is one, else the sheet's short style notes. */
    public static String style(ScenarioSheet sheet) {
        return sheet.styleGuide == null || sheet.styleGuide.isBlank() ? sheet.style : sheet.styleGuide;
    }

    public static List<String> rooms(ScenarioSheet.Place p) {
        return rooms(com.foredayner.scenario.sheet.Period.WEST_MODERN, p);
    }

    public static List<String> rooms(ScenarioSheet sheet, ScenarioSheet.Place p) {
        return rooms(sheet.period(), p);
    }

    private static List<String> rooms(String period, ScenarioSheet.Place p) {
        List<String> out = new ArrayList<>();
        for (ScenarioSheet.Room r : p.rooms) {
            String room = r.purpose.isBlank() ? r.name : r.name + ": " + r.purpose;
            String furniture = com.foredayner.scenario.sheet.Period.OTHER.equals(period) ? ""
                : Archetypes.furniture(r.name + " " + r.purpose, period);
            out.add(furniture.isEmpty() ? room : room + " (furnish with " + furniture + ")");
        }
        return out;
    }

    /** The spots to mark: where each clue lies and where each NPC waits. */
    public static List<String> marks(ScenarioSheet sheet, ScenarioSheet.Place p) {
        List<String> out = new ArrayList<>();
        for (ScenarioSheet.Clue c : sheet.clues) {
            if (c.place.equals(p.id)) {
                out.add(clueMark(c) + " (" + c.name + ", a " + c.form + ")" + (c.room.isBlank() || p.rooms.isEmpty() ? "" : " in the " + c.room)
                    + (c.hidden ? ", hidden inside or behind something" : ", where it can be seen"));
            }
        }
        for (ScenarioSheet.Npc n : sheet.npcs) {
            if (n.place.equals(p.id)) {
                out.add(npcMark(n) + " where " + n.name + " (" + n.role + ") stands" + (n.room.isBlank() || p.rooms.isEmpty() ? "" : ", in the " + n.room)
                    + ", a floor block with 2 blocks of air above");
            }
        }
        return out;
    }

    public static String clueMark(ScenarioSheet.Clue c) {
        return "clue:" + c.id;
    }

    public static String npcMark(ScenarioSheet.Npc n) {
        String s = ScenarioSheet.slug(n.name);
        return "npc:" + (s.isEmpty() ? "npc" : s);
    }

    public static String extra(ScenarioSheet sheet, ScenarioSheet.Place p, int size) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.ROOT, "The building takes most of the %dx%d site (leave 2-3 blocks around it for the yard); it may be"
            + " up to %d blocks high.", size, size, maxHeight(p)));
        if (ScenarioSheet.named(p)) {
            sb.append("\nThis is one of the scenario's own buildings, where the investigators play: its site is twice the usual size so that"
                + " it is roomy inside. Use most of the site.");
        }
        if (!"ruins".equals(p.kind)) {
            sb.append('\n').append(p.rooms.isEmpty() ? OPEN_PLAN : ONLY_LISTED_ROOMS);
            sb.append('\n').append(DOORS);
            sb.append('\n').append(LIGHTS);
            if (Outdoors.worn(p, sheet)) {
                sb.append('\n').append(WORN);
            }
        }
        if (p.refDescription != null && !p.refDescription.isBlank()) {
            // The keeper's own picture beats the general card.
            sb.append("\nPHOTO REFERENCE (the keeper's picture of a real building; follow it closely, scaled to the site):\n")
                .append(p.refDescription.strip());
        } else {
            String reference = References.card(sheet, p);
            if (!reference.isEmpty()) {
                sb.append('\n').append(reference.strip());
            }
        }
        // A mountain-village house follows its card, not the general (western) house checklist and variations.
        boolean village = References.villageHouse(sheet, p);
        String period = sheet.period();
        String checklist = village ? "" : Archetypes.checklist(p.kind, period);
        if (!checklist.isEmpty()) {
            sb.append('\n').append(checklist);
        }
        String facade = village ? "" : Archetypes.facade(period);
        if (!facade.isEmpty()) {
            sb.append('\n').append(facade);
        }
        String variant = village ? "" : Archetypes.variant(sheet, p);
        if (!variant.isEmpty()) {
            sb.append('\n').append(variant);
        }
        sb.append('\n').append(Archetypes.condition(p.condition));
        String yard = village ? References.VILLAGE_YARD : Archetypes.yard(p, period);
        if (!yard.isEmpty()) {
            sb.append('\n').append(yard);
        }
        if (!p.scenes.isEmpty()) {
            sb.append("\nScenes that happen here (leave room for the investigators): ").append(String.join("; ", p.scenes)).append('.');
        }
        if (!p.notes.isBlank()) {
            sb.append("\nColours named in the description (sign bands, walls, awnings) are the keeper's choice: use exactly those.");
        }
        if (p.cellars > 0) {
            sb.append("\nA cellar is dug under this building by code (with a hatch and ladder down): build nothing below the ground")
                .append(" floor and keep the ground floor's floor whole.");
        }
        if (p.filler) {
            sb.append("\nAn ordinary lived-in town building; nothing in it matters to the story.");
        }
        sb.append('\n').append(NO_FIGURES);
        return sb.toString();
    }

    /** What an outdoor place holds: the keeper's notes first, then the checklist for its kind and period. */
    public static String prop(ScenarioSheet sheet, ScenarioSheet.Place p) {
        String period = sheet.period();
        StringBuilder sb = new StringBuilder();
        sb.append("(").append(sheet.era).append(sheet.mood.isBlank() ? "" : ", " + sheet.mood).append(")");
        if (p.notes != null && !p.notes.isBlank()) {
            sb.append(" THE KEEPER'S NOTES (follow them first, they beat the checklist): ").append(p.notes.strip());
        }
        if (!p.scenes.isEmpty()) {
            sb.append(" Scenes here: ").append(String.join("; ", p.scenes)).append('.');
        }
        sb.append('\n').append(Outdoors.checklist(p, period));
        return sb.toString();
    }
}

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
        String what = (p.name + " " + p.notes).toLowerCase(Locale.ROOT);
        if (tree(what)) {
            return 22;
        }
        return switch (p.kind) {
            case "woods" -> 18;
            case "cave", "landmark", "scene" -> 14;
            case "beach" -> 8;
            default -> 12;
        };
    }

    static boolean tree(String what) {
        return what.contains("tree") || what.contains("나무") || what.contains("고목") || what.contains("당산") || what.contains("느티")
            || what.contains("willow") || what.contains("버드");
    }

    static boolean cliff(String what) {
        return what.contains("cliff") || what.contains("절벽") || what.contains("전망") || what.contains("overlook") || what.contains("벼랑")
            || what.contains("lookout") || what.contains("viewpoint");
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
        return "THIS IS AN OUTDOOR PLACE, NOT A BUILDING: no house, no walls around the site, no roof over it, no floor slab"
            + " under it. Make it a WHOLE SCENE that fills the site (up to " + h + " blocks high), as carefully made as a"
            + " building:\n"
            + "- The ground is part of the work: the site's ground surface is y = -1 (replace it, dig down to y = -2 for a"
            + " pond, a ditch or a hollow, and raise it with full blocks for mounds, banks, rocks and slopes). Lay paths"
            + " (dirt_path, gravel, coarse_dirt, stone slabs or steps) where people walk and mixed patches (moss_block,"
            + " podzol, rooted_dirt, mud, gravel), not one flat colour.\n"
            + "- Height and shape: rocks of mixed stone (stone, andesite, cobblestone, mossy_cobblestone, tuff) in natural"
            + " lumps, slopes with stairs and slabs, nothing a plain box.\n"
            + "- Plants in layers: trees built by hand where they fit (a trunk of logs, branches of logs, a full crown of"
            + " leaves in several overlapping blobs), bushes of leaves, tall grass, ferns, flowers, vines, moss carpets.\n"
            + "- The things that make it THIS place (below), big enough to read from afar, plus small details around them"
            + " (lanterns, signs, fences, benches, stones, scattered items).\n"
            + "- Keep a walkable way in from the front edge (z = -1 side) to the middle, 2 wide, and room for the"
            + " investigators to stand.\n"
            + "- Use most of the site: spread the scene over it, not one lump in the middle.\n" + NO_FIGURES;
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

    /** What an outdoor place holds: the scene for its kind, then the keeper's own notes (which win). */
    public static String prop(ScenarioSheet sheet, ScenarioSheet.Place p) {
        String period = sheet.period();
        boolean east = com.foredayner.scenario.sheet.Period.east(period);
        boolean other = com.foredayner.scenario.sheet.Period.OTHER.equals(period);
        String told = (p.name + " " + p.notes).toLowerCase(Locale.ROOT);
        String what;
        if (tree(told) && !"woods".equals(p.kind)) {
            what = "a great old tree that the place is about, the centre of the site: a thick trunk (2x2 or 3x3 of logs, wider"
                + " at the foot), roots of logs and rooted_dirt spreading over the ground, big branches of logs reaching out"
                + " and up, a wide uneven crown of leaves (several overlapping blobs, 9-15 blocks across, some hanging vines),"
                + " and around it what makes it special"
                + (east ? " (a straw rope with white paper strips tied round the trunk, a stone altar or a small shrine at"
                    + " its foot, stones piled in little towers, ribbons of coloured wool on the branches)"
                    : " (carvings or a hollow in the trunk, a bench or low wall round it, lanterns hung by chains)");
        } else if (cliff(told) || "scene".equals(p.kind) && told.contains("view")) {
            what = "a cliff-top lookout path: the ground rises toward the back of the site to a high rocky edge (stone,"
                + " andesite, mossy cobblestone in uneven layers 4-8 blocks high) that drops away at the back; a winding path"
                + " of dirt_path and stone steps climbs to a flat lookout at the top with a wooden railing (fences) along the"
                + " edge, a bench, a lamp or lantern post, a small sign; wind-bent trees and bushes, tall grass and flowers on"
                + " the slopes, loose rocks by the path";
        } else {
            what = switch (p.kind) {
                case "dock" -> "a harbour dock: a wooden pier of planks on log posts reaching out over the water edge of the"
                    + " site, mooring posts with rope (chains), a small rowing boat tied up, piles of crates, barrels and fishing"
                    + " nets, a lantern post, a shed or a fish-drying rack, a crane beam";
                case "cemetery" -> east ? "a burial ground on a gentle slope: several low round grassy mounds (grass and moss"
                    + " blocks), each with a small stone tablet and a stone offering table in front, stone lanterns and posts, pine"
                    + " trees around, a path between the mounds"
                    : other ? "a fitting burial place for " + p.name + ", with several graves along paths"
                    : "an old cemetery: rows of weathered gravestones (stone, cobblestone walls, mossy stone) and a few tall"
                    + " grave monuments and crosses, a low stone wall with an iron gate at the front, a gravel path between"
                    + " the rows, a crypt or mausoleum with a small door, a big dead or old tree, iron lanterns, overgrown"
                    + " grass and moss";
                case "woods" -> "a dense patch of forest around a small clearing: many tall trees built by hand (different"
                    + " heights, trunks of logs, full crowns of leaves), fallen logs, mossy boulders, ferns and bushes, a"
                    + " winding path into the clearing, and in the clearing "
                    + (east ? "a small cairn of piled stones and a straw rope on a tree" : other ? "a fitting landmark"
                        : "an old moss-covered standing stone with strange carvings");
                case "square" -> east ? "a village common: a big old tree with a stone bench ring, a village well with a stone"
                    + " rim, a wooden frame and a bucket, a 평상 platform, paths of packed earth"
                    : other ? "a fitting square for " + p.name
                    : "a town square: paving of stone bricks and gravel in a pattern, a stone fountain or statue-free"
                    + " monument in the middle, benches, lamp posts, flower beds and a few trees";
                case "beach" -> "a strip of beach: sand and gravel down to the water, rocks and pools, driftwood logs, an"
                    + " overturned wooden rowing boat with oars, a few posts and a torn net, dune grass";
                case "cave" -> "a cave mouth in a rocky hill: the ground rises into a mound of stone, andesite and tuff, with a"
                    + " dark opening 3 wide going in and down, moss and hanging roots over it, loose rocks and gravel at the"
                    + " foot, a path leading to it";
                case "scene" -> "a stretch of road or path where something happened: the road (gravel, dirt_path) across"
                    + " the site with its verges, ditches and bushes, a fence or low wall along it, and the things that"
                    + " show what happened (a dropped bundle, a broken basket or cart wheel, scattered stones, a torn cloth"
                    + " on a bush, skid marks of coarse_dirt), each item separate";
                case "landmark" -> "the " + p.name + " itself as the centre, well made and big enough to see from afar, as"
                    + " it looks in the setting, with its surroundings (a path to it, stones, plants, a fence or low wall)";
                case "pyeongsang" -> "a Korean 평상: a low, wide wooden platform (about 4x3, 1 block high: spruce or oak"
                    + " slabs on fence posts) where villagers sit, with a woven straw mat and a small low wooden table, under"
                    + " a big shady tree, with a path and a few pots around";
                default -> "a fitting scene for " + p.kind + " \"" + p.name + "\"";
            };
        }
        return what + " (" + sheet.era + (sheet.mood.isBlank() ? "" : ", " + sheet.mood) + ")"
            + (p.notes.isBlank() ? "" : ". The keeper's notes (follow them first): " + p.notes);
    }
}

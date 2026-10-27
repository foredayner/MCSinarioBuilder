package com.foredayner.scenario.town;

import com.foredayner.scenario.sheet.ScenarioSheet;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** What each place asks the AI Build Assistant for: a building for a lot, or a prop for an outdoor place. */
public final class BuildPrompts {

    private BuildPrompts() {
    }

    /** An outdoor place's object is built of blocks on a square this wide in the middle of its lot. */
    public static final int PROP_LOT = 7;
    /** And at most this high. */
    public static final int PROP_HEIGHT = 6;

    /** Never: figures of people or animals (the scenario's characters are played, not built). */
    public static final String NO_FIGURES = "NEVER build people, figures, bodies, ghosts, animals or statues and dolls shaped like"
        + " them (no armor stands, no heads, no mannequins, no skeletons), even when the scenario's characters are named"
        + " here: the characters are played at the table, not built. Everything is made of ordinary Minecraft blocks.";

    /** What an outdoor place's object is: built of blocks on a small square in the middle of its lot. */
    public static String propDescription(ScenarioSheet sheet, ScenarioSheet.Place p) {
        return "outdoor object \"" + p.name + "\": " + prop(sheet, p);
    }

    /** Rules for an outdoor object (it stands in the open; the ground around it is already made). */
    public static String propExtra() {
        return "This is NOT a building: build only the object itself, of Minecraft blocks, standing on the open ground of"
            + " the small square (keep it inside the square, at most " + PROP_HEIGHT + " blocks high). No house, no"
            + " walls around the square, no roof over it, no floor slab under it, no fence around it; do not change the ground"
            + " (no grass, dirt or paving). Several small separate pieces are better than one big lump.\n" + NO_FIGURES;
    }

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
                out.add(clueMark(c) + " (" + c.name + ", a " + c.form + ")" + (c.room.isBlank() ? "" : " in the " + c.room)
                    + (c.hidden ? ", hidden inside or behind something" : ", where it can be seen"));
            }
        }
        for (ScenarioSheet.Npc n : sheet.npcs) {
            if (n.place.equals(p.id)) {
                out.add(npcMark(n) + " where " + n.name + " (" + n.role + ") stands" + (n.room.isBlank() ? "" : ", in the " + n.room)
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
        if (p.basement) {
            sb.append("\nThe scenario has a cellar here. The site cannot go below ground, so put the cellar rooms at the back of the")
                .append(" ground floor behind a heavy door, built like a cellar (stone walls, no windows, dim light).");
        }
        if (p.filler) {
            sb.append("\nAn ordinary lived-in town building; nothing in it matters to the story.");
        }
        sb.append('\n').append(NO_FIGURES);
        return sb.toString();
    }

    /** The prop for an outdoor place (stands in its middle). */
    public static String prop(ScenarioSheet sheet, ScenarioSheet.Place p) {
        String period = sheet.period();
        boolean east = com.foredayner.scenario.sheet.Period.east(period);
        boolean other = com.foredayner.scenario.sheet.Period.OTHER.equals(period);
        String what = switch (p.kind) {
            case "dock" -> "a pile of weathered wooden crates, barrels, a coil of rope and a fishing net for a harbour dock";
            case "cemetery" -> east ? "a burial mound: a low round grassy mound (grass and moss blocks) with a small stone tablet and a"
                + " stone offering table in front of it" : other ? "a fitting grave marker for " + p.name
                : "a weathered stone angel statue on a mossy pedestal for an old cemetery";
            case "woods" -> east ? "a small cairn of piled stones (cobblestone walls and stone buttons) by a forest path"
                : other ? "a fitting landmark for " + p.name : "an old moss-covered standing stone with strange carvings in a forest clearing";
            case "square" -> east ? "a village well with a stone rim, a wooden frame and a bucket" : other ? "a fitting centrepiece for " + p.name
                : "a stone fountain for a town square";
            case "beach" -> "an overturned wooden rowing boat with oars on the sand";
            case "cave" -> "a cave mouth: a low mound of grey stone with a dark opening, moss and hanging roots over it";
            case "scene" -> "a few small loose things by a road that show something happened here (a dropped bundle, a broken"
                + " basket or cart wheel, scattered stones, a torn cloth on a bush), each item separate and small; no people, no"
                + " animals, nothing taller than 2 blocks";
            case "landmark" -> "the " + p.name + " itself, small and standing on its own (at most 4 blocks), as it looks in the setting";
            case "pyeongsang" -> "a Korean 평상: a low, wide wooden platform (about 4x3, 1 block high: spruce or oak slabs on fence"
                + " posts) where villagers sit, with a woven straw mat on it and a small low wooden table";
            default -> "a fitting centrepiece for " + p.kind + " \"" + p.name + "\"";
        };
        return what + " (" + sheet.era + (sheet.mood.isBlank() ? "" : ", " + sheet.mood) + ")" + (p.notes.isBlank() ? "" : ". " + p.notes);
    }
}

package com.foredayner.scenario.town;

import com.foredayner.scenario.sheet.ScenarioSheet;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** What each place asks the AI Build Assistant for: a building for a lot, or a prop for an outdoor place. */
public final class BuildPrompts {

    private BuildPrompts() {
    }

    /** How high a building may go (blocks above the ground). */
    public static int maxHeight(ScenarioSheet.Place p) {
        int h = Math.min(30, p.floors * 5 + 8);
        return switch (p.kind) {
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
        List<String> out = new ArrayList<>();
        for (ScenarioSheet.Room r : p.rooms) {
            String room = r.purpose.isBlank() ? r.name : r.name + ": " + r.purpose;
            String furniture = Archetypes.furniture(r.name + " " + r.purpose);
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
        String checklist = Archetypes.checklist(p.kind, sheet.era);
        if (!checklist.isEmpty()) {
            sb.append('\n').append(checklist);
        }
        sb.append('\n').append(Archetypes.FACADE);
        String variant = Archetypes.variant(sheet, p);
        if (!variant.isEmpty()) {
            sb.append('\n').append(variant);
        }
        sb.append('\n').append(Archetypes.condition(p.condition));
        sb.append('\n').append(Archetypes.yard(p));
        if (!p.scenes.isEmpty()) {
            sb.append("\nScenes that happen here (leave room for the investigators): ").append(String.join("; ", p.scenes)).append('.');
        }
        if (p.basement) {
            sb.append("\nThe scenario has a cellar here. The site cannot go below ground, so put the cellar rooms at the back of the")
                .append(" ground floor behind a heavy door, built like a cellar (stone walls, no windows, dim light).");
        }
        if (p.filler) {
            sb.append("\nAn ordinary lived-in town building; nothing in it matters to the story.");
        }
        return sb.toString();
    }

    /** The prop for an outdoor place (stands in its middle). */
    public static String prop(ScenarioSheet sheet, ScenarioSheet.Place p) {
        String what = switch (p.kind) {
            case "dock" -> "a pile of weathered wooden crates, barrels, a coil of rope and a fishing net for a harbour dock";
            case "cemetery" -> "a weathered stone angel statue on a mossy pedestal for an old cemetery";
            case "woods" -> "an old moss-covered standing stone with strange carvings in a forest clearing";
            case "square" -> "a stone fountain for a town square";
            case "beach" -> "an overturned wooden rowing boat with oars on the sand";
            default -> "a fitting centrepiece for " + p.kind + " \"" + p.name + "\"";
        };
        return what + " (" + sheet.era + (sheet.mood.isBlank() ? "" : ", " + sheet.mood) + ")" + (p.notes.isBlank() ? "" : ". " + p.notes);
    }
}

package com.foredayner.scenario.town;

import com.foredayner.scenario.sheet.ScenarioSheet;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What makes each kind of building read as what it is (the outside features it must have), the
 * furniture a room's use calls for, how a place's condition shows, and small variations so the
 * ordinary houses of a street are not all the same.
 */
public final class Archetypes {

    private Archetypes() {
    }

    /** Whether the era is from the 1960s on (cars, concrete, electric light everywhere). */
    public static boolean modern(String era) {
        String e = era == null ? "" : era.toLowerCase(Locale.ROOT);
        if (e.contains("modern") || e.contains("현대") || e.contains("contemporary")) {
            return true;
        }
        var m = java.util.regex.Pattern.compile("(1[89]|20)(\\d)0").matcher(e);
        if (m.find()) {
            int decade = Integer.parseInt(m.group(1) + m.group(2) + "0");
            return decade >= 1960;
        }
        return false;
    }

    /** The outside (and key inside) features a kind of building needs, for the era. */
    public static String features(String kind, String era) {
        boolean modern = modern(era);
        return switch (kind == null ? "" : kind) {
            case "house" -> modern
                ? "a family house: porch or carport, front door with a small roof over it, windows with frames, a chimney or vent, a mailbox by the path"
                : "a family house: a front porch with posts and steps, framed windows with shutters, a chimney, a pitched roof with overhanging eaves";
            case "inn" -> "an inn: a wide front with a porch, a hanging sign or banner over the door, a common room with a fireplace, a bar"
                + " counter and tables on the ground floor, a staircase, guest rooms along a corridor upstairs, lit windows";
            case "shop" -> modern
                ? "a shop or convenience store: a wide glass front with a glass door, a bright coloured sign band above the"
                    + " glass, a counter with a till by the door, rows of low shelves (aisles), drink fridges (glass in front of"
                    + " quartz or white concrete) along the back wall, bright light everywhere, a back store room"
                : "a shop: a false front (a tall flat facade hiding the roof), display windows either side of the door, an awning,"
                    + " a counter, shelves and barrels of goods, a back store room";
            case "church" -> modern
                ? "a modern church: a slim tower on the front corner, on its very top a red cross that stands out against"
                    + " the sky (red_concrete, an upright 4 blocks tall and arms 3 blocks wide, a redstone_lamp or red light inside"
                    + " it), a wide glass or double-door entrance under a canopy, tall narrow windows, a sign"
                    + " band with the church name, inside a hall with rows of pews, an aisle, a raised stage with a pulpit and a"
                    + " cross on the wall"
                : "a church: a steeple or bell tower over the entrance with a bell, tall pointed (lancet) windows of"
                    + " stained glass along the sides, a steep roof, double doors, inside a nave with rows of pews, an aisle, an altar"
                    + " on a raised step at the far end with candles";
            case "library" -> "a library: a stone or brick front with steps and columns or pilasters, tall windows, a reading"
                + " room with long tables and lamps, rows of tall bookshelves, a librarian's desk, a locked special collection room";
            case "police" -> modern
                ? "a police station: a 2-storey building of light concrete or tile with a blue and white sign band over a"
                    + " glass entrance, a blue light by the door, a parking bay beside the entrance on the ground of the lot"
                    + " (gray concrete with white_concrete lines, never on the steps), a front desk,"
                    + " offices with desks, a holding cell with iron bars"
                : "a police station: a solid brick or stone building, a lamp over the door, a front desk, an office,"
                    + " holding cells with iron bars";
            case "newspaper" -> "a newspaper office: a sign board over a display window, desks with typewriters (use lecterns or"
                + " looms as stand-ins), filing cabinets, a press room at the back with a big printing press";
            case "school" -> "a school: a bell on the roof, a wide entrance with steps, rows of big windows, classrooms with"
                + " rows of desks and a blackboard, a teacher's desk";
            case "hospital" -> "a hospital or clinic: a plain clean facade with a clear sign, a waiting room, an examination room"
                + " with a bed and cabinets, wards with rows of beds, white and light materials inside";
            case "mansion" -> modern
                ? "a large rich house: a grand entrance, wide windows, a big hall with a staircase, a long drive to the door"
                : "a mansion: symmetrical facade, a columned portico over the front door, gables or a tower, a widow's walk on"
                    + " the roof, tall windows, a grand hall with a double staircase, wood panelling, fireplaces, a gated front";
            case "farm" -> "a farmhouse with a barn: a big barn door, a hay loft, animal stalls, fences, hay bales, a water trough";
            case "mill" -> "a mill: a tall building of stone and timber, a water wheel or sails, machinery (gears as grindstones),"
                + " sacks of grain (hay bales), a loading door high up with a hoist beam";
            case "factory" -> "a factory: brick walls with rows of tall windows, a tall chimney stack, a loading dock, big open"
                + " floor with machines (furnaces, anvils, pistons), an office on a mezzanine";
            case "warehouse" -> "a warehouse: plain walls, big loading doors, a hoist beam, stacks of crates and barrels inside,"
                + " a small office";
            case "lighthouse" -> "a lighthouse: a round tower that narrows as it rises (striped or white), a gallery with a railing"
                + " near the top, a glass lantern room with a bright light, a spiral stair inside, a small keeper's cottage at its foot";
            case "hotel" -> "a hotel: several floors, a lobby with a reception desk, rooms along corridors, a sign over the entrance";
            case "station" -> "a railway station: a platform with a canopy, a ticket office, a waiting room with benches, a clock";
            default -> "";
        };
    }

    /** Furniture a room calls for, from words in its name and use (English or Korean). */
    public static String furniture(String room) {
        String r = room == null ? "" : room.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        if (has(r, "study", "office", "서재", "사무", "집무")) {
            out.add("a desk with papers and a lamp, bookshelves, an armchair");
        }
        if (has(r, "librar", "archive", "도서", "서고", "기록")) {
            out.add("rows of bookshelves, reading tables, lecterns, lamps");
        }
        if (has(r, "kitchen", "부엌", "주방")) {
            out.add("a stove (furnace or smoker), a sink (cauldron), barrels, a work table, hanging lanterns");
        }
        if (has(r, "bed", "침실", "객실", "guest", "숙소")) {
            out.add("a bed, a chest, a wardrobe, a small table with a candle");
        }
        if (has(r, "dining", "parlor", "parlour", "living", "lounge", "sitting", "식당", "응접", "거실", "휴게", "common")) {
            out.add("a table with chairs, a fireplace, a carpet, a bookshelf or cabinet");
        }
        if (has(r, "bar", "pub", "taproom", "tavern", "술", "주점")) {
            out.add("a bar counter, barrels, stools, bottles on shelves (brewing stands, flower pots)");
        }
        if (has(r, "chapel", "nave", "예배", "성당", "제단", "altar")) {
            out.add("rows of pews (stairs blocks), an altar with candles, stained glass windows");
        }
        if (has(r, "storage", "store", "창고", "cellar", "basement", "지하", "저장")) {
            out.add("barrels, chests, crates, cobwebs in the corners");
        }
        if (has(r, "lab", "실험", "작업실", "workshop")) {
            out.add("work tables, brewing stands, a cauldron, shelves of jars, a lectern with notes");
        }
        if (has(r, "shop", "store front", "매장", "상점", "counter", "계산")) {
            out.add("a counter, shelves of goods, barrels and crates");
        }
        if (has(r, "attic", "다락")) {
            out.add("old crates and chests, cobwebs, a small window");
        }
        if (has(r, "cell", "감방", "유치장", "jail")) {
            out.add("iron bars, a plain bench, a bucket");
        }
        if (has(r, "hall", "lobby", "foyer", "entrance", "복도", "로비", "현관", "홀")) {
            out.add("a carpet runner, a coat stand, a side table with a lamp");
        }
        if (has(r, "lamp room", "lantern room", "등실", "등명")) {
            out.add("a big light in the middle (glowstone or sea lantern), glass all around");
        }
        return String.join("; ", out);
    }

    private static boolean has(String text, String... words) {
        for (String w : words) {
            if (text.contains(w)) {
                return true;
            }
        }
        return false;
    }

    /** A way to pick blocks spread evenly and the same every time, for the script. */
    private static final String PICK = "scatter them with a hash so they form no lines or stripes: int h = (x * 73856093) ^ (y * 19349663)"
        + " ^ (z * 83492791); h = (h ^ (h >>> 13)) * 1274126177; if (((h ^ (h >>> 16)) & 0x7fffffff) % N == 0) { ... }";

    /** How the place's condition shows: which blocks, how many, where. */
    public static String condition(String condition) {
        return switch (condition == null ? "" : condition) {
            case "worn" -> "CONDITION worn (old and shabby), which MUST show:\n"
                + "- about 1 wall block in 6 swapped for a weathered one: stone -> mossy_cobblestone, mossy_stone_bricks or"
                + " cracked_stone_bricks; bricks -> a few cobblestone or granite patches (bricks have no mossy form); planks -> a darker or mismatched plank or a"
                + " stripped log; concrete, terracotta or tile -> a slightly darker or stained shade of the same colour"
                + " (white_concrete -> white_terracotta or light_gray_concrete, light_gray -> stone or andesite), never black,"
                + " so it reads as grime, not a pattern (" + PICK + ", N = 6)\n"
                + "- a foundation row mixing cobblestone and mossy_cobblestone\n"
                + "- 2-3 cobwebs in upper inside corners, one patched spot on the roof in a different stair block\n"
                + "- fewer lights: one lantern per room";
            case "abandoned" -> "CONDITION abandoned (empty, decaying), which MUST show:\n"
                + "- about 1 wall block in 3 weathered (mossy, cracked, stripped) (" + PICK + ", N = 3)\n"
                + "- broken windows: leave about 1 pane in 3 empty (air)\n"
                + "- 2-4 holes in the roof (air), vines on two outer walls, leaves and cobwebs in many corners inside\n"
                + "- overturned or missing furniture, dust (gray carpet scraps), the yard overgrown (tall grass, bushes)\n"
                + "- no lights at all";
            case "sinister" -> "CONDITION sinister (something is wrong here), which MUST show:\n"
                + "- the town style in its darkest materials, with dark accents (deepslate, blackstone, dark_oak, polished_basalt)"
                + " at the corners, trim and foundation\n"
                + "- small, few windows on the street side, some covered by dark trapdoors as shutters\n"
                + "- odd details: carved or chiseled blocks in a pattern, one door sealed with iron_bars, a dead tree in the yard\n"
                + "- 1 wall block in 8 weathered (" + PICK + ", N = 8)\n"
                + "- only dim light: soul_lantern or candles, few of them";
            default -> "CONDITION kept (lived in, cared for): tidy, clean, a lantern in every room, flower boxes under some windows.";
        };
    }

    /** Facade rules every building follows, so walls are not blank. */
    public static final String FACADE = "FACADE RULES (every outside wall):\n"
        + "- no blank wall longer than 4 blocks: a window every 2-3 blocks along every side, on every floor\n"
        + "- corner posts (logs or a darker block) and a post every 4-6 blocks on long walls\n"
        + "- a trim band (slabs or stairs of the trim block) between floors and under the roof\n"
        + "- the roof overhangs the walls by 1 block, made of stairs, with a visible ridge\n"
        + "- a 1-block foundation course of the foundation block, visible above the ground\n"
        + "- on the street side: shutters (trapdoors) or sills under windows, a lamp by the door, steps up to the door\n"
        + "- towers (bell towers, spires, lighthouses): a window or opening at least every 4 blocks of height, and an open"
        + " top storey (a belfry with arches and a bell, or a lantern room) under the roof; never a blank column";

    /** The kind's features as a checklist the script must tick off. */
    public static String checklist(String kind, String era) {
        String features = features(kind, era);
        if (features.isEmpty()) {
            return "";
        }
        int colon = features.indexOf(':');
        String what = colon < 0 ? kind : features.substring(0, colon).trim();
        String list = colon < 0 ? features : features.substring(colon + 1);
        StringBuilder sb = new StringBuilder("MUST HAVE so it reads as " + what + " (check every item before finishing):");
        for (String item : list.split(",| and (?=a |an |the |rows |tall |double |inside )")) {
            String t = item.trim();
            if (!t.isEmpty()) {
                sb.append("\n- ").append(t);
            }
        }
        return sb.toString();
    }

    private static final String[] WALLS = {
        "light walls (pale planks or painted wood)", "dark weathered walls", "brick walls", "a stone ground floor with a timber upper floor",
        "shingle-clad walls in a muted colour"
    };
    private static final String[] ROOFS = { "a gable roof", "a hip roof", "a gambrel roof", "a saltbox roof (long slope at the back)" };
    private static final String[] MODERN_ROOFS = { "a gable roof", "a hip roof", "a low flat roof with a parapet" };

    /**
     * For ordinary houses (and filler places): which variation of the town style this one is, the
     * same every time for the same place.
     */
    public static String variant(ScenarioSheet sheet, ScenarioSheet.Place p) {
        if (!"house".equals(p.kind) && !p.filler) {
            return "";
        }
        int h = Math.abs((sheet.title + "/" + p.id).hashCode());
        String[] roofs = modern(sheet.era) ? MODERN_ROOFS : ROOFS;
        return "Variation (keep within the town style, so this house differs from its neighbours): " + WALLS[h % WALLS.length]
            + ", " + roofs[(h / 7) % roofs.length] + ((h / 31) % 2 == 0 ? ", a front porch" : ", no porch, a small roof over the door")
            + ", the chimney on the " + ((h / 61) % 2 == 0 ? "left" : "right") + ".";
    }

    /** What to put around the building on its lot. */
    public static String yard(ScenarioSheet.Place p) {
        String extra = switch (p.kind) {
            case "shop", "inn", "newspaper", "hotel" -> "a hanging sign or banner over the door, crates or barrels by the entrance";
            case "church" -> "a few gravestones or a bench, a lamp post by the gate";
            case "farm" -> "hay bales, a water trough, a fenced animal pen";
            case "mansion" -> "a gate and gate posts at the front, hedges, a garden with flowers, lamp posts";
            case "police", "library", "school", "hospital" -> "a lamp post by the steps, a bench";
            default -> "a garden bed or a wood pile, a mailbox by the path";
        };
        return "Around the building, inside the lot: a low fence or hedge along the lot edge (open at the front path), a short"
            + " path from the door to the front edge, and " + extra + ".";
    }
}

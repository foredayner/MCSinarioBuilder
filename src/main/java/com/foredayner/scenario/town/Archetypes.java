package com.foredayner.scenario.town;

import com.foredayner.scenario.sheet.Period;
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

    /** A period id, or an era text (older callers) turned into one. */
    static String periodOf(String periodOrEra) {
        return Period.known(periodOrEra) ? periodOrEra : Period.guess(periodOrEra, "", "", "");
    }

    /** The outside (and key inside) features a kind of building needs, for the period ("" when none). */
    public static String features(String kind, String periodOrEra) {
        String period = periodOf(periodOrEra);
        String k = kind == null ? "" : kind;
        return switch (period) {
            case Period.WEST_MEDIEVAL -> westMedieval(k);
            case Period.WEST_MODERN -> west(k, false);
            case Period.WEST_CONTEMPORARY, Period.EAST_CONTEMPORARY -> west(k, true);
            case Period.EAST_MEDIEVAL -> eastMedieval(k);
            case Period.EAST_MODERN -> eastModern(k);
            case Period.EAST_POSTWAR -> eastPostwar(k);
            default -> "";
        };
    }

    private static String west(String kind, boolean modern) {
        return switch (kind) {
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

    private static String westMedieval(String kind) {
        return switch (kind) {
            case "house" -> "a medieval cottage or town house: timber-framed walls (dark oak or spruce logs as the frame, white"
                + " or tan plaster between: white_terracotta or mud_bricks), a steep thatched or slate roof (hay_block or deepslate"
                + " tile stairs), small windows with wooden shutters, a stone chimney, the upper floor jutting out 1 block over the front";
            case "inn", "hotel" -> "a medieval tavern: a painted sign hanging from an iron bracket over the door, a big hearth in"
                + " the common room, long tables and benches, barrels and a serving counter, a stable yard at the side, rooms upstairs";
            case "shop" -> "a craftsman's shop: an open front with a fold-down wooden counter (trapdoors) under a shutter, goods"
                + " hung up and on the counter, a workshop behind (anvil, crafting table, smithing table), living rooms upstairs";
            case "church" -> "a stone church: a square bell tower with a bell, small round-arched or pointed windows, thick walls"
                + " with buttresses, a heavy wooden door in an arched porch, inside a nave with benches, an altar with candles, a"
                + " churchyard wall";
            case "police" -> "a watch house and gaol: thick stone walls, a guard room with a table and a weapon rack, cells"
                + " with iron bars, a torch by the door";
            case "library", "newspaper" -> "a scriptorium or archive: stone walls, tall narrow windows, lecterns, shelves of"
                + " books and scrolls, candles on the tables";
            case "school" -> "a schoolroom: a timber room with benches, a teacher's lectern, shelves";
            case "hospital" -> "an infirmary or apothecary: beds along a long hall, shelves of jars and herbs (flower pots,"
                + " brewing stands), a cauldron, a work table";
            case "mansion" -> "a manor house: stone walls, a walled yard with a gatehouse, a great hall with a long table and a"
                + " big fireplace, a private room upstairs, a tower or turret, banners on the walls";
            case "farm" -> "a farmstead: a long house with a byre for animals under the same roof, a barn, hay stacks, a fenced"
                + " yard, a well";
            case "mill" -> "a mill: a stone and timber building with a water wheel or sails, a grindstone, sacks of grain";
            case "factory" -> "a smithy or workshop: an open-fronted shed with a forge (blast_furnace or campfire), anvils, a"
                + " water trough, tools on the wall";
            case "warehouse" -> "a storehouse: a stone ground floor, a timber upper floor with a hoist beam, sacks and barrels";
            case "lighthouse" -> "a beacon tower: a round stone tower with a fire basket (campfire) on its top";
            case "station" -> "a coaching yard: a stable, a coach shed, a courtyard with a well";
            default -> "";
        };
    }

    private static String eastMedieval(String kind) {
        return switch (kind) {
            case "house" -> "a hanok: a single-storey wooden house on a raised stone base with stone steps, white plaster walls"
                + " between dark wooden posts, a roof with deep eaves (grey tiles with raised corners for the well-off, rounded"
                + " thatch of hay_block for the poor), latticed paper doors and windows, a wooden maru floor, a walled yard with a gate";
            case "inn", "hotel" -> "a jumak (roadside inn): a thatched hanok with a wide wooden platform (평상) in the yard where"
                + " travellers eat, big earthen jars, a kitchen with iron pots over fire holes, a few small guest rooms, a lantern on a"
                + " pole at the gate";
            case "shop" -> "a market shop: an open front under deep eaves, goods on low wooden platforms, in baskets and jars,"
                + " a cloth banner as the sign, a small back room";
            case "church" -> "a temple hall: a raised stone platform with steps, red pillars, a big tiled roof with upturned"
                + " corners, painted eaves (green and red trim), an altar with a statue niche and candles inside, a stone pagoda or"
                + " a bell pavilion in the yard";
            case "police" -> "a magistrate's office (관아): a walled compound with a gate, a main hall on a stone platform with a"
                + " tiled roof, a yard in front of it, a jail with wooden bars";
            case "library", "newspaper" -> "an academy hall (서원): a tiled hall with a wide maru, low writing tables, shelves of"
                + " books and scrolls, a quiet walled courtyard";
            case "school" -> "a village schoolroom (서당): a small hanok room with low desks, books and a teacher's cushion";
            case "hospital" -> "a herbal medicine house: a wall of small herb drawers (barrels or chests), a mortar, iron pots,"
                + " a sick room with bedding on the floor";
            case "mansion" -> "a yangban manor: several tiled hanok buildings around courtyards, a tall gate with a raised roof,"
                + " stone walls topped with tiles, a men's hall with a wide maru, women's quarters at the back, a jar terrace";
            case "farm" -> "a farmhouse: a thatched hanok, an ox stall, a threshing yard, straw stacks, a jar terrace, vegetable plots";
            case "mill" -> "a water mill: a wooden water wheel beside a thatched shed with a grindstone inside";
            case "warehouse" -> "a granary: a raised wooden storehouse on stone posts with a tiled roof, sacks and jars inside";
            case "factory" -> "a workshop: an open shed with a forge, anvils, tools and stacks of wood";
            case "lighthouse" -> "a beacon platform (봉수대): a stone platform on a height with fire pits (campfires)";
            case "station" -> "a post station: stables and a tiled hall for travelling officials";
            default -> "";
        };
    }

    private static String eastModern(String kind) {
        return switch (kind) {
            case "house" -> "a 1920s-30s city house: a tiled hanok in an L or U plan around a small courtyard, glass sliding"
                + " doors on the maru, a gate on the street; or a Japanese-style house of dark wooden boards with a grey tile roof";
            case "inn", "hotel" -> "a 여관 (inn): a 2-storey wooden building with a glass front and a vertical signboard, small"
                + " rooms off a corridor, a reception counter";
            case "shop" -> "a 1920s-30s shop: a 2-storey front of wood or brick with a painted signboard, glass display windows,"
                + " goods on shelves inside, living rooms upstairs";
            case "church" -> "a church: red brick with a square bell tower and spire, pointed arched windows, buttresses, a dark"
                + " roof; inside rows of pews, an aisle, an altar";
            case "police" -> "a police station or substation (주재소): a 1-2 storey wooden or brick building with a hip roof, a"
                + " flagpole in front, a high counter, wooden desks, a cell with iron bars";
            case "library", "newspaper", "hospital", "bank", "school", "station" -> "a public building of the period: red brick or"
                + " grey stone with white stone bands, tall arched or rectangular windows in rows, a cornice, an entrance with steps,"
                + " a clear signboard";
            case "mansion" -> "a rich family's house: a large tiled hanok compound with a tall gate, or a Western-style brick"
                + " villa with a porch, a garden and a wall";
            case "farm" -> "a farmhouse: a thatched or tiled hanok, an ox stall, a threshing yard, straw stacks, a jar terrace";
            case "factory", "warehouse", "mill" -> "a brick factory or warehouse: brick walls, a corrugated or tiled roof, a tall"
                + " chimney, big doors, machines or sacks inside";
            case "lighthouse" -> "a lighthouse: a white round tower with a lantern room, a keeper's house beside it";
            default -> "";
        };
    }

    private static String eastPostwar(String kind) {
        return switch (kind) {
            case "house" -> "a 1950s-70s Korean house: single storey, walls of cement blocks or plaster on a low stone base, a"
                + " low roof of grey corrugated slate or painted tin (blue or red), a wooden-framed glass sliding door onto a narrow"
                + " maru, a small yard with a hand water pump and a jar terrace";
            case "inn", "hotel" -> "a 여인숙 (cheap inn): a 1-2 storey plastered building with a signboard, small rooms along a"
                + " corridor, a small counter by the door";
            case "shop" -> "a corner store (구멍가게): a single-storey shop with a wooden-framed glass sliding front, a painted"
                + " signboard, goods on wooden shelves and in boxes, a bench by the door";
            case "church" -> "a small church: a white plastered or cement hall with a pitched roof, a wooden or steel bell tower"
                + " beside it with a cross on top, arched windows; inside wooden pews and a pulpit";
            case "police" -> "a police box (지서): a single-storey plastered building with a sign over the door, a low wall in"
                + " front, a desk, a cell with iron bars";
            case "school" -> "a country school: a long single-storey building of wood or cement with a row of classrooms and big"
                + " windows, a dirt playground with a flagpole";
            case "hospital" -> "a clinic (의원): a plastered 1-2 storey building with a red cross sign, a waiting bench, an"
                + " examination room with a bed";
            case "library", "newspaper" -> "an office building: a 2-storey cement building with rows of windows and a signboard";
            case "mansion" -> "a rich family's house: a large tiled hanok, or a 2-storey Western-style brick house, behind a"
                + " garden wall with a gate";
            case "farm" -> "a farmhouse: a slate- or thatch-roofed house, an ox stall, a threshing yard, straw stacks, a jar terrace";
            case "mill" -> "a rice mill (정미소): a big shed with a corrugated roof, machines (pistons, grindstones), sacks of rice";
            case "factory", "warehouse" -> "a factory or warehouse: cement block or brick walls, a corrugated roof, a chimney,"
                + " big doors";
            case "station" -> "a small railway station: a single-storey building with a station sign, a waiting room with"
                + " benches, a platform with a canopy";
            case "lighthouse" -> "a lighthouse: a white concrete tower with a lantern room";
            default -> "";
        };
    }

    /** Furniture a room calls for, from words in its name and use (English or Korean), in a 1920s western town. */
    public static String furniture(String room) {
        return furniture(room, Period.WEST_MODERN);
    }

    /** Furniture a room calls for, for the period (East Asian homes before the 1990s live on the floor). */
    public static String furniture(String room, String period) {
        String r = room == null ? "" : room.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        boolean floor = Period.east(period) && !Period.contemporary(period);
        if (floor) {
            // Floor living: bedding and low tables, no beds or chairs.
            if (has(r, "bed", "침실", "객실", "guest", "숙소", "안방", "웃방", "건넌방", "사랑방", "방")) {
                out.add("bedding folded in a corner or laid on the floor (white or coloured carpet on wool), a low chest, a small"
                    + " low table (a trapdoor on a block), an oil lamp or candle; no bed blocks");
            }
            if (has(r, "kitchen", "부엌", "주방", "정지")) {
                out.add("an earth floor a step lower, fire holes with iron pots (a furnace or campfire under a cauldron), water"
                    + " jars (decorated pots), a shelf of bowls, firewood");
            }
            if (has(r, "maru", "마루", "대청", "living", "거실", "응접")) {
                out.add("a wooden floor (planks), a low table with floor cushions (carpet), a chest, a folding screen");
            }
            if (!out.isEmpty()) {
                return String.join("; ", out);
            }
        }
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

    /** Facade rules for the period ("" when it has none). */
    public static String facade(String periodOrEra) {
        return switch (periodOf(periodOrEra)) {
            case Period.WEST_MODERN -> FACADE;
            case Period.WEST_CONTEMPORARY, Period.EAST_CONTEMPORARY -> FACADE_CONTEMPORARY;
            case Period.WEST_MEDIEVAL -> FACADE_MEDIEVAL;
            case Period.EAST_MEDIEVAL, Period.EAST_MODERN, Period.EAST_POSTWAR -> FACADE_EAST;
            default -> "";
        };
    }

    static final String FACADE_CONTEMPORARY = "FACADE RULES (every outside wall):\n"
        + "- windows in even rows on every floor, no blank wall longer than 4 blocks\n"
        + "- a clear base course and a parapet or roof edge in a contrasting block\n"
        + "- a canopy or recessed entrance with a step or ramp, a light by the door\n"
        + "- signs, railings, outdoor units and pipes where a real building has them\n"
        + "- towers: an opening at least every 4 blocks of height; never a blank column";

    static final String FACADE_MEDIEVAL = "FACADE RULES (every outside wall):\n"
        + "- a timber frame: posts at the corners and every 3-4 blocks, a beam at each floor, plaster or stone between\n"
        + "- small windows with wooden shutters (trapdoors), no glass panes wider than 1 block\n"
        + "- a steep roof overhanging 1 block, made of stairs, with a visible ridge\n"
        + "- a 1-block stone foundation course and a stone step at every door\n"
        + "- a torch or lantern by the main door only";

    static final String FACADE_EAST = "FACADE RULES (every outside wall):\n"
        + "- the house stands on a 1-block stone base; a stone step in front of every door\n"
        + "- wooden posts at the corners and every 3-4 blocks; no blank wall longer than 4 blocks (a post, window or door)\n"
        + "- deep eaves overhanging 1-2 blocks; tiled roofs have raised corners, thatch is soft and rounded\n"
        + "- latticed paper doors and windows (birch trapdoors, white panes), small; no shutters, no porch railings\n"
        + "- the eaves at least 4 blocks above where people walk in";

    /** Facade rules of the classic 1920s western town. */
    public static final String FACADE = "FACADE RULES (every outside wall):\n"
        + "- no blank wall longer than 4 blocks: a window every 2-3 blocks along every side, on every floor\n"
        + "- corner posts (logs or a darker block) and a post every 4-6 blocks on long walls\n"
        + "- a trim band (slabs or stairs of the trim block) between floors and under the roof\n"
        + "- the roof overhangs the walls by 1 block, made of stairs, with a visible ridge\n"
        + "- a 1-block foundation course of the foundation block, visible above the ground\n"
        + "- on the street side: shutters (trapdoors) or sills under windows, a lamp by the door, steps up to the door\n"
        + "- towers (bell towers, spires, lighthouses): a window or opening at least every 4 blocks of height, and an open"
        + " top storey (a belfry with arches and a bell, or a lantern room) under the roof; never a blank column";

    /** The kind's features as a checklist the script must tick off ("" for a period without checklists). */
    public static String checklist(String kind, String periodOrEra) {
        String features = features(kind, periodOrEra);
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
    private static final String[] MEDIEVAL_WALLS = { "white plaster between a dark timber frame", "tan plaster between an oak frame",
        "a stone ground floor with a timber-framed upper floor", "rough fieldstone walls" };
    private static final String[] MEDIEVAL_ROOFS = { "a thatched roof (hay_block)", "a slate roof (deepslate tiles)", "a steep gable roof of dark stairs" };
    private static final String[] EAST_OLD_WALLS = { "white plaster between dark posts", "mud walls (packed_mud) with a stone lower course",
        "plaster with a band of stone at the bottom" };
    private static final String[] EAST_OLD_ROOFS = { "a rounded thatched roof (hay_block)", "a grey tiled roof with raised corners",
        "a thatched roof with rope lines (hay_block and dark stairs)" };
    private static final String[] EAST_MODERN_WALLS = { "white plaster between dark posts with glass sliding doors", "dark wooden boards",
        "red brick" };
    private static final String[] EAST_MODERN_ROOFS = { "a grey tiled roof with raised corners", "a dark tiled hip roof" };
    private static final String[] POSTWAR_WALLS = { "cement blocks (light_gray_concrete or stone_bricks)", "white plaster (white_terracotta)",
        "mud walls with a stone lower course" };
    private static final String[] POSTWAR_ROOFS = { "a grey corrugated slate roof (andesite or cobblestone stairs)",
        "a tin roof painted blue (blue_terracotta stairs)", "a tin roof painted red (red_terracotta stairs)", "a thatched roof (hay_block)" };

    /**
     * For ordinary houses (and filler places): which variation of the town style this one is, the
     * same every time for the same place.
     */
    public static String variant(ScenarioSheet sheet, ScenarioSheet.Place p) {
        if (!"house".equals(p.kind) && !p.filler) {
            return "";
        }
        int h = Math.abs((sheet.title + "/" + p.id).hashCode());
        String period = sheet.period();
        String head = "Variation (keep within the town style, so this house differs from its neighbours): ";
        String side = (h / 61) % 2 == 0 ? "left" : "right";
        return switch (period) {
            case Period.WEST_MEDIEVAL -> head + MEDIEVAL_WALLS[h % MEDIEVAL_WALLS.length] + ", " + MEDIEVAL_ROOFS[(h / 7) % MEDIEVAL_ROOFS.length]
                + ((h / 31) % 2 == 0 ? ", the upper floor jutting out over the front" : ", a single storey with a loft") + ", the chimney on the " + side + ".";
            case Period.EAST_MEDIEVAL -> head + EAST_OLD_WALLS[h % EAST_OLD_WALLS.length] + ", " + EAST_OLD_ROOFS[(h / 7) % EAST_OLD_ROOFS.length]
                + ((h / 31) % 2 == 0 ? ", a straight plan" : ", an L plan") + ", the kitchen on the " + side + ".";
            case Period.EAST_MODERN -> head + EAST_MODERN_WALLS[h % EAST_MODERN_WALLS.length] + ", " + EAST_MODERN_ROOFS[(h / 7) % EAST_MODERN_ROOFS.length]
                + ((h / 31) % 2 == 0 ? ", an L plan around a small courtyard" : ", a straight plan") + ", the gate on the " + side + ".";
            case Period.EAST_POSTWAR -> head + POSTWAR_WALLS[h % POSTWAR_WALLS.length] + ", " + POSTWAR_ROOFS[(h / 7) % POSTWAR_ROOFS.length]
                + ((h / 31) % 2 == 0 ? ", a straight plan" : ", an L plan") + ", the kitchen on the " + side + ".";
            case Period.OTHER -> "";
            default -> head + WALLS[h % WALLS.length] + ", " + (Period.contemporary(period) ? MODERN_ROOFS : ROOFS)[(h / 7)
                % (Period.contemporary(period) ? MODERN_ROOFS : ROOFS).length]
                + ((h / 31) % 2 == 0 ? ", a front porch" : ", no porch, a small roof over the door") + ", the chimney on the " + side + ".";
        };
    }

    /** What to put around the building on its lot, for the period ("" for a period without rules). */
    public static String yard(ScenarioSheet.Place p, String periodOrEra) {
        String period = periodOf(periodOrEra);
        String lot = "Around the building, inside the lot: ";
        return switch (period) {
            case Period.WEST_MODERN, Period.WEST_CONTEMPORARY -> yard(p);
            case Period.WEST_MEDIEVAL -> lot + "a wattle or wooden fence (fence) along the lot edge, open at the front, a beaten earth"
                + " path to the door, a vegetable plot, a woodpile, a well or a water barrel.";
            case Period.EAST_MEDIEVAL -> lot + "a stone or earth wall (topped with tiles or straw) along the lot edge with a wooden gate"
                + " at the front, a packed-earth yard (coarse_dirt), a jar terrace (decorated pots on a stone platform), a woodpile, a"
                + " vegetable plot.";
            case Period.EAST_MODERN -> lot + "a wall with a gate onto the street, a small courtyard, a jar terrace, a well or water"
                + " pump, a few potted plants.";
            case Period.EAST_POSTWAR -> lot + "a cement block wall or brushwood fence with an opening at the front, a packed-earth"
                + " yard, a hand water pump, a jar terrace, a clothes line on two posts, a woodpile.";
            case Period.EAST_CONTEMPORARY -> lot + "a low wall or railing along the lot edge, a paved path to the door, a parking"
                + " space, potted plants, a gas meter and pipes on the wall.";
            default -> "";
        };
    }

    /** What to put around the building on its lot (a western town). */
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

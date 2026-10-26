package com.foredayner.scenario.town;

import com.foredayner.scenario.sheet.ScenarioSheet;

import java.util.Locale;

/**
 * Reference cards: what common buildings of a particular country and era really look like,
 * written from real ones (proportions, materials, signs, what is outside and inside), for places
 * the AI would otherwise build as a generic box. Text only; no pictures are shipped. Used when the
 * sheet's setting is in Korea (modern, the 1920s-30s colonial period, or a mountain village before
 * the 1960s).
 */
public final class References {

    private References() {
    }

    /** Whether the scenario is set in Korea. */
    public static boolean korean(ScenarioSheet sheet) {
        String s = (sheet.setting + " " + sheet.title + " " + sheet.style + " " + sheet.terrain.notes).toLowerCase(Locale.ROOT);
        for (String w : new String[] { "korea", "한국", "서울", "seoul", "경기", "gyeonggi", "부산", "busan", "인천", "대구", "대전", "광주",
            "경성", "gyeongseong", "keijo", "조선", "joseon", "chosen", "강원", "gangwon" }) {
            if (s.contains(w)) {
                return true;
            }
        }
        return false;
    }

    /**
     * A Korean mountain village before the 1960s (log and shingle houses, dirt lanes): its houses
     * follow their own card instead of the general house checklist, variations and yard.
     */
    public static boolean mountainVillage(ScenarioSheet sheet) {
        if (!korean(sheet) || Archetypes.modern(sheet.era)) {
            return false;
        }
        String s = (sheet.setting + " " + sheet.title + " " + sheet.style + " " + sheet.terrain.notes).toLowerCase(Locale.ROOT);
        return has(s, "산골", "산속", "산간", "두메", "너와", "강원", "gangwon", "mountain village", "mountain hamlet");
    }

    /** Whether a place of a mountain village is a dwelling (it follows the 너와집 card). */
    public static boolean villageHouse(ScenarioSheet sheet, ScenarioSheet.Place p) {
        return mountainVillage(sheet) && ("house".equals(p.kind) || "farm".equals(p.kind) || "mansion".equals(p.kind) || p.filler);
    }

    /** Around a mountain-village house, inside its lot. */
    public static final String VILLAGE_YARD = "Around the house, inside the lot: a packed-earth front yard (마당: coarse_dirt and dirt"
        + " paths, no paving), a low brushwood fence (싸리 울타리: spruce_fence or oak_fence, 1 high) along the lot edge with an opening"
        + " at the front, a 장독대 (a raised stone platform of cobblestone with 3-6 earthen jars: decorated_pot, flower_pot) beside the"
        + " house, a stacked firewood pile (나뭇단: spruce_log or oak_log on their side, under a slab roof), and a small separate"
        + " outhouse (변소: a 2x2 plank hut with a door) in a back corner. No mailbox, no lamp post, no garden flowers in rows.";

    private static boolean has(String text, String... words) {
        for (String w : words) {
            if (text.contains(w)) {
                return true;
            }
        }
        return false;
    }

    /** The card for a place, or "" when none fits. */
    public static String card(ScenarioSheet sheet, ScenarioSheet.Place p) {
        if (!korean(sheet)) {
            return "";
        }
        String t = (p.kind + " " + p.name + " " + p.notes).toLowerCase(Locale.ROOT);
        boolean modern = Archetypes.modern(sheet.era);
        String card = villageHouse(sheet, p) ? NEOWAJIP : modern ? modernKorea(p.kind, t) : colonialKorea(p.kind, t);
        return card.isEmpty() ? "" : "REFERENCE (how such a building really looks; follow it, scaled to the site):\n" + card;
    }

    private static String modernKorea(String kind, String t) {
        if ("police".equals(kind) || has(t, "파출소", "지구대", "경찰", "police", "치안센터")) {
            return """
                Korean police box (파출소 / 지구대): a 2-storey flat-roofed box, about 12-15 wide.
                - Walls: light grey or white stone tile (smooth_quartz, white_concrete, light_gray_concrete), a darker grey
                  base course; a few vertical fins or a recessed corner.
                - A navy blue sign band (blue_concrete) across the top of the front with a white strip (white_concrete)
                  where the name would be; a small police emblem block (gold_block or yellow_concrete) at its left end.
                - Glass entrance (glass_pane in iron or gray frames) with a flat canopy; beside it a blue-and-red light
                  (blue_stained_glass and red_stained_glass over lamps) on the wall.
                - A flagpole by the entrance (iron_bars or end_rod pole, a white_banner flag).
                - Front: a patrol car bay marked with white_concrete lines on gray_concrete, a low steel fence (iron_bars)
                  with a gap at the entrance, a CCTV box (iron_block + black_concrete) on a corner.
                - Windows: horizontal strip windows with dark frames (gray_stained_glass_pane, black_concrete frames).
                - Inside: a long civil counter (민원 창구) facing the door with a few waiting chairs, desks with
                  monitors (black_concrete/black_carpet on tables) behind it, filing cabinets, a holding room with
                  iron_bars and a bench, a staircase to the upper floor (rest room, lockers).
                """;
        }
        if (has(t, "편의점", "convenience", "24시", "마트", "mart")) {
            return """
                Korean convenience store (편의점): the ground floor of a small building, a shopfront about 8-12 wide.
                - Full-height glass front (glass_pane) in thin grey frames, a glass door slightly to one side.
                - Over the glass a bright sign band in 2-3 brand colour stripes (e.g. green, white and blue concrete or
                  wool stripes, lit with sea lanterns or froglights behind) with no real logo.
                - Outside: 1-2 plastic tables with chairs and a parasol (white_concrete table, stairs as chairs, a
                  coloured banner as parasol), an ice chest, a few boxes or a delivery cart.
                - Inside, very bright (sea_lantern or froglight flush in a white ceiling): a counter by the door with a
                  till and a cigarette rack behind it, 3-4 rows of low shelves (aisles), glass-door drink fridges along
                  the back wall (glass in front of white_concrete or quartz), an ice-cream freezer chest, an ATM by the door.
                - Floor: white or light grey tiles (white_concrete, smooth_quartz). Upper floors, if any: plain homes or
                  offices with windows.
                """;
        }
        if ("church".equals(kind) || has(t, "교회", "church", "성당")) {
            return """
                Korean neighbourhood church (동네 교회): a 3-4 storey box of red brick or white render with the worship
                hall on an upper floor, and a slim tower at a front corner.
                - The tower: a narrow concrete or steel frame (iron_bars, stone_brick_wall, white_concrete) rising well
                  above the roof (about 1.5 times the building), with a big red cross on the very top (red_concrete,
                  glowing: redstone_lamp or shroomlight inside) that is the landmark of the street at night.
                - A sign band with the church name on the front, a glass entrance up a few steps with a canopy.
                - The hall floor has tall narrow windows (sometimes coloured glass panes); other floors plain windows.
                - Inside the hall: rows of pews (stairs), a carpeted aisle, a raised stage with a pulpit (lectern),
                  a piano or drum set (note blocks, jukebox), a large cross on the back wall, speakers on the walls.
                - Lower floors: a fellowship dining room (long tables), an office, a small kitchen.
                """;
        }
        if (has(t, "빌라", "villa", "다세대", "연립", "맨션", "mansion") && ("house".equals(kind) || has(t, "빌라", "villa", "다세대"))) {
            return """
                Korean multi-family villa (빌라 / 다세대주택): 4-5 storeys, about 12-15 wide, flat roof.
                - Ground floor on pilotis: open parking between square columns (stone or concrete), the building
                  standing over it, a narrow entrance with a keypad door and a small canopy.
                - Walls: red brick (bricks) or beige-white brick (smooth_sandstone, birch) with ivory stucco bands
                  (white_terracotta, smooth_quartz); storeys about 3 blocks high.
                - A stairwell on the front or side with a tall vertical strip window (glass_pane) running up all floors.
                - Small balconies or window guards with metal railings (iron_bars), air-conditioner outdoor units on
                  wall brackets (iron_block or white_concrete with iron_trapdoor grille), a gas pipe line (iron_bars).
                - Roof: a railing or parapet, a blue water tank (light_blue_concrete or blue_concrete drum on legs),
                  maybe a small rooftop room or laundry line.
                """;
        }
        if ("school".equals(kind) || has(t, "학교", "school")) {
            return """
                Korean school (초·중·고등학교): a long 3-4 storey block of cream or beige render (sandstone, smooth_sandstone,
                white_terracotta) with rows of identical windows, a central stair tower with a clock on top.
                - A big dirt playground (운동장, coarse_dirt) in front with goal posts and a flag pole, a main gate with
                  the school name on a stone post, trees along the fence.
                - Inside: long corridors on one side, classrooms with rows of desks, a blackboard (green or black
                  concrete), lockers at the back, shoe lockers at the entrance.
                """;
        }
        if ("hospital".equals(kind) || has(t, "병원", "의원", "hospital", "clinic", "약국", "pharmacy")) {
            return """
                Korean clinic or small hospital (의원 / 병원): often floors of a shop block, a white and glass facade with a
                green cross sign (green_concrete) or a red cross for a hospital, clean white interior (white_concrete,
                quartz), a reception counter with chairs, examination rooms with beds and cabinets.
                """;
        }
        if ("shop".equals(kind) || has(t, "상가", "가게", "식당", "카페", "cafe", "restaurant", "shop")) {
            return """
                Korean street shop block (근린생활시설 / 상가주택): 3-5 storeys, shops on the ground floor behind glass fronts
                with large colourful sign boards, more signs stacked on the facade for the upper floors (cafes, academies,
                offices), plain windows above, a flat roof with a water tank; awnings and goods outside the shops.
                """;
        }
        return "";
    }

    /** 너와집: the log and shingle houses of the Gangwon mountains (1950s and before). */
    private static final String NEOWAJIP = """
        Neowajip (너와집), the mountain house of Gangwon-do: a single-storey house, nearly square in plan (a "double-row"
        겹집 plan: rooms on both sides of the ridge, under one roof), about 9-11 wide and 8-10 deep on a 15 site.
        - Base: a low stone footing (cobblestone or mossy_cobblestone, 1 block) the whole floor stands on; one stone
          step (cobblestone_stairs or a stone slab) in front of every door, so nobody climbs a full block.
        - Walls: log-cabin walls of stacked logs laid on their side (stripped_spruce_log or spruce_log, axis along the
          wall) with mud plaster in the gaps on some courses (packed_mud, mud_bricks, light_gray_terracotta). Always full
          blocks; never slabs or stairs as wall.
        - Roof: a big low hip-and-gable roof of wooden shingles (너와): dark_oak or spruce stairs and slabs, weathered grey
          in places (some stripped_dark_oak or mossy stone pieces), with loose fist-sized stones on it to hold the shingles
          (a few cobblestone_slab or stone_button on the roof slopes), a small smoke hole under each gable end (까치구멍:
          a 1x1 opening or a spruce_trapdoor). Eaves overhang 1 block. The underside of the eaves is at least 4 blocks
          above the yard where people walk in, so the doorway never feels cramped.
        - Front: a wooden-floored veranda (마루: spruce_planks floor, 1 block above the yard, 2-3 wide) under the eaves,
          reached by the stone step; sliding paper doors and windows: spruce_door or birch_door, windows of
          white_stained_glass_pane or birch_trapdoor lattice, small (1 wide, 1 high) and few. No glass windows, no shutters.
        - Inside (rooms at least 4x4, ceiling 3 high, a 2-wide passage where you walk): a kitchen (정지/부엌) with an earth
          floor a step lower than the rooms (coarse_dirt or packed_mud), 1-2 fire holes (아궁이: a furnace or campfire
          set into a stone block wall) with iron pots on them (cauldron), a shelf of bowls (barrel, decorated_pot);
          an earth-floored entry hall (봉당); 안방 and 웃방 with warm yellow oiled-paper floors (orange or yellow
          terracotta or birch_planks) - bedding folded in a corner (white or light_gray carpet and wool), never bed
          blocks; a low wooden chest (chest or barrel) and a small low table (a spruce_trapdoor on top of 1 block); a
          corner fire nook for light (코클: a small stone niche with a lantern or candle). Some houses keep a cow stall
          (외양간) inside one corner under the same roof (hay_block, fence). Doors between rooms are real doors.
        - Light: no electricity. Lanterns, candles and fire only.
        """;

    private static String colonialKorea(String kind, String t) {
        if ("police".equals(kind) || has(t, "주재소", "경찰", "police", "헌병")) {
            return """
                Colonial-era Korean police station (경찰서 / 주재소, 1910s-30s): a 1-2 storey building of red brick or grey
                stone with white stone trim, a hipped roof of dark slate or tiles, tall arched windows in rows, a central
                entrance porch with steps and a small pediment, a flagpole in front, a walled front yard with a gate.
                Inside: a high counter, wooden desks, a cell block with iron bars at the back.
                """;
        }
        if ("church".equals(kind) || has(t, "교회", "성당", "church")) {
            return """
                Early 20th-century Korean church: a red brick Gothic-revival church with a square bell tower and spire
                over the entrance, pointed arched windows, buttresses, a dark slate roof; some country churches are
                hanok-style halls with tiled roofs and a small bell tower beside them.
                """;
        }
        if (has(t, "너와", "neowa", "귀틀", "산골", "mountain")) {
            return NEOWAJIP;
        }
        if ("house".equals(kind) || "inn".equals(kind) || has(t, "한옥", "hanok", "주막", "여관")) {
            return """
                Hanok (한옥): a single-storey wooden house on a raised stone base, white plaster walls between dark wooden
                posts and beams (dark_oak_log, stripped_dark_oak_log, white_terracotta), deep eaves of a curved dark
                tile roof (deepslate_tile_stairs, raised at the corners), latticed paper doors and windows (birch
                trapdoors, white panes), a wooden floor veranda (maru), an L or U plan around a dirt courtyard, a
                stone wall with a tiled top and a wooden gate.
                """;
        }
        if ("shop".equals(kind) || "newspaper".equals(kind) || "library".equals(kind) || "hospital".equals(kind) || "bank".equals(kind)) {
            return """
                Colonial-era Gyeongseong commercial or public building: 2-3 storeys of red brick or stone with white
                stone bands, tall rectangular or arched windows in rows, a cornice and parapet, a corner entrance, large
                shop windows on the ground floor with painted signboards; next to old tiled hanok shops along the street.
                """;
        }
        return "";
    }
}

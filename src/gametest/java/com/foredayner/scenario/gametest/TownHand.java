package com.foredayner.scenario.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Hand-written answers for the buildings and outdoor scenes of the "밀브룩의 물레방아" test town,
 * written the way the AI is asked to (BuildPrompts): in the site's frame (x 0..w-1, z -1 at the
 * front to -d at the back), storeys with their floor blocks at y = 0, 5, 10, a double door in the
 * front wall with free ground before and behind it, straight stairs between floors with a railing
 * round the hole, the cellar stairs' cells kept free, lights in every room, and each clue and NPC
 * marked. The building fills most of its site, so that it is not sent back as too small.
 */
final class TownHand {

    private TownHand() {
    }

    /** One script, op by op. */
    static final class Script {
        final StringBuilder s = new StringBuilder();
        /** Cells (x, z) on the ground floor that must stay free (stairs, the cellar stairs, before doors). */
        final List<int[]> keep = new ArrayList<>();
        /** The NPC asked to be marked here (the request's own name for the mark). */
        String npc = "";

        boolean kept(int x, int z) {
            for (int[] k : keep) {
                if (x >= k[0] && x <= k[2] && z >= k[1] && z <= k[3]) {
                    return true;
                }
            }
            return false;
        }

        Script block(int x, int y, int z, String b) {
            s.append(String.format(Locale.ROOT, "ai.placeBlock(%d, %d, %d, '%s')\n", x, y, z, b));
            return this;
        }

        /** A block unless the cell must stay free (furniture on the ground floor). */
        Script thing(int x, int y, int z, String b) {
            return y < 5 && kept(x, z) ? this : block(x, y, z, b);
        }

        /** From (x1, z1) to (x2, z2) inclusive, at height y. */
        Script layer(int x1, int z1, int x2, int z2, int y, String b) {
            int ax = Math.min(x1, x2), az = Math.min(z1, z2);
            s.append(String.format(Locale.ROOT, "ai.placeFloor(%d, %d, %d, %d, %d, '%s')\n", ax, y, az,
                Math.abs(x2 - x1) + 1, Math.abs(z2 - z1) + 1, b));
            return this;
        }

        Script npc(int x, int y, int z) {
            return npc.isEmpty() ? this : mark(npc, x, y, z);
        }

        Script mark(String name, int x, int y, int z) {
            s.append(String.format(Locale.ROOT, "ai.mark('%s', %d, %d, %d)\n", name, x, y, z));
            return this;
        }

        @Override
        public String toString() {
            return s.toString();
        }
    }

    /** The cellar stairs' cells, as the request tells them: {x1, z1, x2, z2}, or null. */
    static int[] cellarStairs(String extra) {
        Matcher m = Pattern.compile("STAIRS DOWN TO THE CELLAR go in at x (-?\\d+)\\.\\.(-?\\d+), z (-?\\d+)\\.\\.(-?\\d+)").matcher(extra);
        if (!m.find()) {
            return null;
        }
        int a = Integer.parseInt(m.group(1)), b = Integer.parseInt(m.group(2)), c = Integer.parseInt(m.group(3)), d = Integer.parseInt(m.group(4));
        return new int[] { Math.min(a, b), Math.min(c, d), Math.max(a, b), Math.max(c, d) };
    }

    /** How wide the stairs between floors must be (the request says), 1 by default. */
    static int stairWidth(String extra) {
        Matcher m = Pattern.compile("ONE STRAIGHT flight \\(no turns, no landings\\) (\\d+) block").matcher(extra);
        return m.find() ? Integer.parseInt(m.group(1)) : 1;
    }

    static int floors(String description) {
        Matcher m = Pattern.compile("\\. (\\d+) floors?\\.").matcher(description);
        return m.find() ? Integer.parseInt(m.group(1)) : 1;
    }

    /** Looks per building: wall, corner posts, floor, upper floor, roof stairs, roof slab. */
    private record Look(String wall, String post, String floor, String upper, String roof, String ridge, String base) {
    }

    private static Look look(String name) {
        return switch (name) {
            case "마을 회관" -> new Look("oak_planks", "birch_log[axis=y]", "spruce_planks", "oak_planks", "dark_oak_stairs", "dark_oak_slab", "cobblestone");
            case "물레방앗간" -> new Look("stone_bricks", "spruce_log[axis=y]", "stone_bricks", "spruce_planks", "spruce_stairs", "spruce_slab", "cobblestone");
            case "잡화점 창고" -> new Look("spruce_planks", "dark_oak_log[axis=y]", "coarse_dirt", "spruce_planks", "spruce_stairs", "spruce_slab", "cobblestone");
            default -> new Look("spruce_planks", "dark_oak_log[axis=y]", "oak_planks", "oak_planks", "spruce_stairs", "spruce_slab", "cobblestone");
        };
    }

    /**
     * A building: the shell (storeys, walls with windows, a double door, a gabled roof), stairs to
     * the floor above, lights, then its own furniture and marks. Null when it has no script.
     */
    static String building(String name, int w, int d, int high, String description, String extra, List<String> marks) {
        Look k = look(name);
        int floors = floors(description);
        int m = w >= 12 ? 2 : 1;
        // A side that touches a joined building: the wall right on that edge, no windows, no eaves past it.
        String up = extra.toUpperCase(Locale.ROOT);
        boolean joinL = up.contains("THE LEFT EDGE (X = 0) TOUCHES THE BUILDING");
        boolean joinR = Pattern.compile("THE RIGHT EDGE \\(X = \\d+\\) TOUCHES THE BUILDING").matcher(up).find();
        boolean joinB = Pattern.compile("THE BACK EDGE \\(Z = -\\d+\\) TOUCHES THE BUILDING").matcher(up).find();
        int x0 = joinL ? 0 : m, x1 = joinR ? w - 1 : w - 1 - m;   // outer walls along x
        int zf = -1 - m - 1, zb = joinB ? -d : -d + m;            // front wall (2 free before the door at least) and back wall
        if (zf - zb < 5) {
            zf = -2;
        }
        int ex0 = joinL ? x0 : x0 - 1, ex1 = joinR ? x1 : x1 + 1, ezb = joinB ? zb : zb - 1; // the eaves
        int cx = (x0 + x1) / 2;
        Script s = new Script();
        for (String mk : marks == null ? List.<String>of() : marks) {
            String id = mk.trim().split(" ")[0];
            if (id.startsWith("npc:") && s.npc.isEmpty()) {
                s.npc = id;
            }
        }
        int[] cellar = cellarStairs(extra);
        if (cellar != null) {
            s.keep.add(cellar);
        }
        // Before and behind the door: free.
        s.keep.add(new int[] { cx - 1, zf - 2, cx, zf - 1 });
        // Storeys: floor, walls (posts at the corners), windows.
        for (int f = 0; f < floors; f++) {
            int y = f * 5;
            s.layer(x0, zb, x1, zf, y, f == 0 ? k.floor : k.upper);
            for (int h = 1; h <= 4; h++) {
                s.layer(x0, zb, x1, zb, y + h, k.wall).layer(x0, zf, x1, zf, y + h, k.wall)
                    .layer(x0, zb, x0, zf, y + h, k.wall).layer(x1, zb, x1, zf, y + h, k.wall);
                for (int[] c : new int[][] { { x0, zb }, { x1, zb }, { x0, zf }, { x1, zf } }) {
                    s.block(c[0], y + h, c[1], k.post);
                }
            }
            for (int x = x0 + 2; x <= x1 - 2; x += 3) {
                if (f > 0 || Math.abs(x - cx) > 2) {
                    s.block(x, y + 2, zf, "glass_pane").block(x, y + 3, zf, "glass_pane");
                }
                if (!joinB) {
                    s.block(x, y + 2, zb, "glass_pane").block(x, y + 3, zb, "glass_pane");
                }
            }
            for (int z = zb + 2; z <= zf - 2; z += 3) {
                if (!joinL) {
                    s.block(x0, y + 2, z, "glass_pane").block(x0, y + 3, z, "glass_pane");
                }
                if (!joinR) {
                    s.block(x1, y + 2, z, "glass_pane").block(x1, y + 3, z, "glass_pane");
                }
            }
        }
        // The foundation band under the walls (on the ground at y = -1 the floor sits a step above it).
        s.layer(x0, zb, x1, zb, 0, k.base).layer(x0, zf, x1, zf, 0, k.base).layer(x0, zb, x0, zf, 0, k.base).layer(x1, zb, x1, zf, 0, k.base);
        // The double door, and a step up to it.
        s.block(cx - 1, 0, zf, k.floor).block(cx, 0, zf, k.floor);
        s.block(cx - 1, 1, zf, "spruce_door[facing=south,half=lower,hinge=left]").block(cx - 1, 2, zf, "spruce_door[facing=south,half=upper,hinge=left]");
        s.block(cx, 1, zf, "spruce_door[facing=south,half=lower,hinge=right]").block(cx, 2, zf, "spruce_door[facing=south,half=upper,hinge=right]");
        s.block(cx - 1, 0, zf + 1, "cobblestone_slab[type=bottom]").block(cx, 0, zf + 1, "cobblestone_slab[type=bottom]");
        // The top storey's own ceiling, and a gabled roof over it (ridge along x).
        int top = floors * 5;
        s.layer(x0, zb, x1, zf, top, k.upper);
        int rows = Math.min((zf - zb) / 2 + 1, Math.max(1, high - top - 1));
        for (int i = 0; i < rows; i++) {
            int y = top + 1 + i;
            int north = ezb + i, south = zf + 1 - i;
            if (north >= south) {
                break;
            }
            s.layer(ex0, north, ex1, north, y, k.roof + "[facing=south]");
            s.layer(ex0, south, ex1, south, y, k.roof + "[facing=north]");
            if (north + 1 < south) {
                // The gable ends under the next row.
                s.layer(x0, north + 1, x0, south - 1, y, k.wall).layer(x1, north + 1, x1, south - 1, y, k.wall);
            }
            if (i == rows - 1 && north + 1 <= south - 1) {
                s.layer(ex0, north + 1, ex1, south - 1, y, k.ridge);
            }
        }
        // Stairs to each floor above: along the inside of the west wall, rising toward the back.
        int width = Math.max(1, stairWidth(extra));
        for (int f = 0; f + 1 < floors; f++) {
            int y = f * 5;
            int z0 = zf - 2;
            for (int lane = 0; lane < width; lane++) {
                int x = x0 + 1 + lane;
                for (int i = 0; i <= 4; i++) {
                    // The fifth step is set in the floor above: no full block to climb at the top.
                    s.block(x, y + 1 + i, z0 - i, "spruce_stairs[facing=north]");
                    for (int under = y + 1; under < y + 1 + i; under++) {
                        s.block(x, under, z0 - i, k.floor);
                    }
                    if (i < 4) {
                        s.block(x, y + 5, z0 - i, "air");
                    }
                }
            }
            // A railing round the hole upstairs: along its open side and its foot end.
            for (int i = 0; i < 4; i++) {
                s.block(x0 + 1 + width, y + 6, z0 - i, "spruce_fence");
            }
            for (int lane = 0; lane <= width; lane++) {
                s.block(x0 + 1 + lane, y + 6, z0 + 1, "spruce_fence");
            }
            s.keep.add(new int[] { x0 + 1, z0 - 5, x0 + width + 1, z0 + 1 });
        }
        // Lights: lanterns hung under every ceiling.
        for (int f = 0; f < floors; f++) {
            for (int x = x0 + 3; x <= x1 - 2; x += 5) {
                for (int z = zf - 3; z >= zb + 2; z -= 5) {
                    if (!s.kept(x, z)) {
                        s.block(x, f * 5 + 4, z, "lantern[hanging=true]");
                    }
                }
            }
        }
        Inside in = new Inside(x0 + 1, x1 - 1, zf - 1, zb + 1);
        switch (name) {
            case "마을 회관" -> hall(s, in);
            case "잡화점" -> store(s, in);
            case "잡화점 창고" -> shed(s, in);
            case "에블린의 집" -> house(s, in);
            case "물레방앗간" -> mill(s, in);
            default -> {
                return null;
            }
        }
        return s.toString();
    }

    /** The inside of a building: x from l to r, z from f (just inside the front) back to b. */
    record Inside(int l, int r, int f, int b) {
        int midX() {
            return (l + r) / 2;
        }

        int midZ() {
            return (f + b) / 2;
        }
    }

    private static void hall(Script s, Inside in) {
        // Ground floor: the meeting room, a long table with benches; the clerk upstairs among the records.
        int z = in.midZ();
        for (int x = in.midX() - 2; x <= in.midX() + 3; x++) {
            s.thing(x, 1, z, "spruce_slab[type=top]");
            s.thing(x, 1, z + 1, "oak_stairs[facing=north]");
            s.thing(x, 1, z - 1, "oak_stairs[facing=south]");
        }
        s.thing(in.r(), 1, in.b(), "bookshelf").thing(in.r() - 1, 1, in.b(), "bookshelf");
        // Upstairs (floor at y 5): shelves of records along the back wall, the clerk's desk with the record book.
        for (int x = in.l() + 3; x <= in.r(); x++) {
            s.block(x, 6, in.b(), "bookshelf").block(x, 7, in.b(), "bookshelf");
        }
        int dx = in.midX() + 1, dz = in.b() + 3;
        s.block(dx, 6, dz, "spruce_slab[type=top]").block(dx + 1, 6, dz, "spruce_slab[type=top]");
        s.block(dx + 1, 7, dz, "lantern");
        s.mark("clue:town_records", dx, 7, dz);
        s.block(dx, 6, dz + 1, "spruce_stairs[facing=north]");
        s.npc(dx + 2, 6, dz + 1);
        s.block(in.r(), 6, in.f(), "barrel[facing=up]");
    }

    private static void store(Script s, Inside in) {
        // A counter across the front left with the ledger on it, shelves and barrels along the back.
        int cz = in.f() - 3;
        for (int x = in.l(); x <= in.l() + 4; x++) {
            s.thing(x, 1, cz, "spruce_slab[type=top]");
        }
        s.mark("clue:credit_ledger", in.l() + 2, 2, cz);
        s.npc(in.l() + 2, 1, cz - 1);
        for (int x = in.l(); x <= in.r(); x++) {
            s.thing(x, 1, in.b(), "barrel[facing=south]");
            s.thing(x, 2, in.b(), (x % 2 == 0) ? "chest[facing=south]" : "barrel[facing=south]");
        }
        for (int z = in.b() + 2; z <= in.f() - 5; z += 2) {
            s.thing(in.r(), 1, z, "barrel[facing=west]");
        }
        s.thing(in.r() - 1, 1, in.f(), "oak_sign[rotation=8]");
    }

    private static void shed(Script s, Inside in) {
        // Sacks of flour (wool) and oil barrels; some of the sacks wet at the bottom.
        for (int x = in.l(); x <= in.r(); x++) {
            s.thing(x, 1, in.b(), "white_wool");
            if (x % 2 == 0) {
                s.thing(x, 2, in.b(), "white_wool");
            }
        }
        s.thing(in.l(), 1, in.b() + 1, "white_wool").thing(in.l(), 1, in.b() + 2, "white_wool");
        s.mark("clue:wet_sacks", in.l() + 1, 1, in.b() + 1);
        s.thing(in.r(), 1, in.midZ(), "barrel[facing=up]").thing(in.r(), 2, in.midZ(), "barrel[facing=up]");
    }

    private static void house(Script s, Inside in) {
        // One room: the stove and table at the front, the bed and a bedside shelf (the diary) at the back.
        s.thing(in.r(), 1, in.f(), "furnace[facing=west]");
        s.thing(in.r() - 2, 1, in.f() - 1, "spruce_fence").thing(in.r() - 2, 2, in.f() - 1, "spruce_pressure_plate");
        s.thing(in.r() - 3, 1, in.f() - 1, "spruce_stairs[facing=east]");
        s.thing(in.r(), 1, in.b() + 1, "red_bed[facing=north,part=foot]").thing(in.r(), 1, in.b(), "red_bed[facing=north,part=head]");
        s.thing(in.r() - 1, 1, in.b(), "spruce_slab[type=top]");
        s.mark("clue:evelyn_diary", in.r() - 1, 2, in.b());
        s.thing(in.l(), 1, in.b(), "bookshelf");
        s.thing(in.l() + 1, 1, in.b(), "chest[facing=south]");
    }

    private static void mill(Script s, Inside in) {
        // The millstone in the middle, sacks of grain along the walls, one new plank in the floor.
        int x = in.midX(), z = in.midZ();
        s.thing(x - 1, 1, z, "smooth_stone_slab").thing(x, 1, z, "smooth_stone_slab").thing(x + 1, 1, z, "smooth_stone_slab");
        s.thing(x, 2, z, "grindstone[face=floor,facing=south]");
        for (int i = in.b(); i <= in.b() + 3; i++) {
            s.thing(in.r(), 1, i, "hay_block").thing(in.r() - 1, 1, i, "white_wool");
        }
        int px = in.l() + 2, pz = in.f() - 2;
        if (s.kept(px, pz)) {
            px = in.r() - 2;
        }
        s.block(px, 0, pz, "birch_planks");
        s.mark("clue:new_plank", px, 1, pz);
        s.npc(x, 1, z + 2);
    }

    // ------------------------------------------------------------------ outdoor scenes

    /** An outdoor scene over its whole site (ground surface at y = -1). Null when it has no script. */
    static String outdoor(String name, int w, int d) {
        Script s = new Script();
        int cx = w / 2, cz = -(d + 1) / 2;
        switch (name) {
            case "마을 우물" -> {
                // A worn path round a stone ring with water, two posts and a little roof; bare feet in the mud.
                s.layer(1, -d + 1, w - 2, -2, -1, "coarse_dirt");
                s.layer(cx - 2, cz - 2, cx + 2, cz + 2, -1, "dirt_path");
                s.layer(cx - 1, -2, cx, -1, -1, "dirt_path");
                for (int x = cx - 1; x <= cx + 1; x++) {
                    for (int z = cz - 1; z <= cz + 1; z++) {
                        if (x == cx && z == cz) {
                            s.block(x, -1, z, "water").block(x, -2, z, "water");
                        } else {
                            s.block(x, 0, z, "cobblestone_wall");
                        }
                    }
                }
                s.block(cx - 1, 1, cz, "oak_fence").block(cx - 1, 2, cz, "oak_fence");
                s.block(cx + 1, 1, cz, "oak_fence").block(cx + 1, 2, cz, "oak_fence");
                s.layer(cx - 1, cz - 1, cx + 1, cz + 1, 3, "spruce_slab[type=bottom]");
                s.block(cx, 2, cz, "chain");
                s.block(cx + 2, -1, cz + 2, "mud").block(cx + 2, -1, cz + 1, "mud");
                s.mark("clue:footprints", cx + 2, 0, cz + 2);
                s.block(1, 0, -2, "lantern");
            }
            case "장터 천막" -> {
                // A canopy of striped wool on posts (a roof, no walls) over stalls; the flyer on a stall, the key by a barrel.
                int x0 = 1, x1 = w - 2, zf = -2, zb = -d + 1;
                s.layer(x0, zb, x1, zf, -1, "dirt_path");
                for (int[] p : new int[][] { { x0, zf }, { x1, zf }, { x0, zb }, { x1, zb }, { (x0 + x1) / 2, zf }, { (x0 + x1) / 2, zb } }) {
                    s.block(p[0], 0, p[1], "oak_fence").block(p[0], 1, p[1], "oak_fence").block(p[0], 2, p[1], "oak_fence");
                }
                for (int z = zb; z <= zf; z++) {
                    s.layer(x0, z, x1, z, 3, (z - zb) % 2 == 0 ? "white_wool" : "red_wool");
                }
                // Two rows of stalls with the way between them open.
                for (int x = x0 + 1; x <= x1 - 1; x++) {
                    if (x % 3 != 0) {
                        s.block(x, 0, zb + 1, "spruce_slab[type=top]");
                    }
                }
                s.block(x0 + 1, 0, zf - 1, "barrel[facing=up]").block(x1 - 1, 0, zf - 1, "barrel[facing=up]");
                s.block(x0 + 1, 1, zb + 1, "pumpkin").block(x0 + 2, 1, zb + 1, "hay_block");
                s.mark("clue:torn_flyer", x1 - 1, 1, zb + 1);
                s.mark("clue:mill_key", x0 + 2, 0, zf - 1);
            }
            case "피스크 가의 밭" -> {
                // A fenced wheat field with water channels, a path from the gate to the scarecrow in the middle.
                int x0 = 0, x1 = w - 1, zf = -1, zb = -d;
                for (int z = zb + 1; z <= zf - 1; z++) {
                    boolean water = (z - zb) % 4 == 0;
                    s.layer(x0 + 1, z, x1 - 1, z, -1, water ? "water" : "farmland[moisture=7]");
                    if (!water) {
                        s.layer(x0 + 1, z, x1 - 1, z, 0, "wheat[age=7]");
                    }
                }
                // The path from the gate to the scarecrow.
                // (The crop goes before the ground under it: a crop left without its farmland drops.)
                for (int z = cz; z <= zf - 1; z++) {
                    s.block(cx, 0, z, "air").block(cx, -1, z, "dirt_path");
                }
                s.layer(x0, zb, x1, zb, 0, "oak_fence").layer(x0, zf, x1, zf, 0, "oak_fence")
                    .layer(x0, zb, x0, zf, 0, "oak_fence").layer(x1, zb, x1, zf, 0, "oak_fence");
                s.block(cx, 0, zf, "oak_fence_gate[facing=south]");
                // The scarecrow: a post, a straw body with arms, a pumpkin head.
                s.block(cx, 0, cz - 1, "oak_fence").block(cx, -1, cz - 1, "dirt").block(cx, 1, cz - 1, "hay_block")
                    .block(cx - 1, 1, cz - 1, "oak_fence").block(cx + 1, 1, cz - 1, "oak_fence")
                    .block(cx, 2, cz - 1, "carved_pumpkin[facing=south]");
                s.mark("clue:scarecrow", cx, 0, cz);
            }
            default -> {
                return null;
            }
        }
        return s.toString();
    }
}

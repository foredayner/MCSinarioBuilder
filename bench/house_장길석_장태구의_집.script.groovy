// ===== 장길석·장태구의 집 (메이골, 1950s 강원 산골 너와집) =====
// Site x 0..14, z -15..-1. House footprint x 4..13, z -12..-4. Front (main door) faces +Z.
// Footing y=0, floor y=1, walls y=2..5, roof y=6..10.

// 1. Clear interior air of the house
ai.clearArea(5, 1, -11, 8, 5, 7);

// 2. Stone footing (cobblestone / mossy_cobblestone mix), including veranda supports
for (int x = 4; x <= 13; x++) {
    for (int z = -12; z <= -2; z++) {
        int h = (x * 73856093) ^ (0 * 19349663) ^ (z * 83492791);
        h = (h ^ (h >>> 13)) * 1274126177;
        int r = (h ^ (h >>> 16)) & 0x7fffffff;
        String fb = (r % 3 == 0) ? 'mossy_cobblestone' : 'cobblestone';
        ai.placeBlock(fb, x, 0, z);
    }
}

// 3. Floors: packed mud base, earth kitchen, warm yellow-paper rooms
ai.placeBox(4, 1, -12, 10, 1, 9, 'packed_mud');
ai.placeBox(5, 1, -11, 3, 1, 7, 'coarse_dirt');
ai.placeBox(9, 1, -7, 4, 1, 3, 'birch_planks');
ai.placeBox(9, 1, -11, 4, 1, 3, 'orange_terracotta');

// 4. Log-box perimeter walls with mud courses, about 1 cell in 6 weathered
for (int y = 2; y <= 5; y++) {
    for (int x = 4; x <= 13; x++) {
        for (int z = -12; z <= -4; z++) {
            boolean edge = (x == 4 || x == 13 || z == -12 || z == -4);
            if (!edge) {
                continue;
            }
            int hh = (x * 73856093) ^ (y * 19349663) ^ (z * 83492791);
            hh = (hh ^ (hh >>> 13)) * 1274126177;
            int rr = (hh ^ (hh >>> 16)) & 0x7fffffff;
            String b = (y % 2 == 0) ? 'stripped_spruce_log' : 'packed_mud';
            if (rr % 6 == 0) {
                b = (y % 2 == 0) ? 'dark_oak_log' : 'coarse_dirt';
            }
            ai.placeBlock(b, x, y, z);
        }
    }
}

// 5. Interior partitions
// P1: kitchen (x 5..7) | right side (x 9..12), door at (8, z=-6)
for (int y = 2; y <= 5; y++) {
    for (int z = -11; z <= -5; z++) {
        if (z == -6 && y <= 3) {
            continue;
        }
        String pb = (y % 2 == 0) ? 'stripped_spruce_log' : 'packed_mud';
        ai.placeBlock(pb, 8, y, z);
    }
}
// P2: upper room (back, z -11..-9) | living room (front, z -7..-5), door at (x=10, z=-8)
for (int y = 2; y <= 5; y++) {
    for (int x = 9; x <= 12; x++) {
        if (x == 10 && y <= 3) {
            continue;
        }
        String pb2 = (y % 2 == 0) ? 'stripped_spruce_log' : 'packed_mud';
        ai.placeBlock(pb2, x, y, -8);
    }
}

// 6. Real doors: front doors and interior doors
ai.clearArea(6, 2, -4, 1, 2, 1);
ai.placeBlock('spruce_door', 6, 2, -4);
ai.clearArea(11, 2, -4, 1, 2, 1);
ai.placeBlock('spruce_door', 11, 2, -4);
ai.clearArea(8, 2, -6, 1, 2, 1);
ai.placeBlock('spruce_door', 8, 2, -6);
ai.clearArea(10, 2, -8, 1, 2, 1);
ai.placeBlock('spruce_door', 10, 2, -8);

// 7. Paper-pane windows (small, few): front and side walls
ai.placeBlock('white_stained_glass_pane', 5, 3, -4);
ai.placeBlock('white_stained_glass_pane', 9, 3, -4);
ai.placeBlock('white_stained_glass_pane', 4, 3, -9);
ai.placeBlock('white_stained_glass_pane', 13, 3, -10);

// 8. Roof of split-shingle courses (spruce stairs edges, planks infill), stepped up to a ridge
for (int k = 0; k <= 4; k++) {
    int zb = -13 + k;
    int zf = -3 - k;
    int ry = 6 + k;
    for (int x = 3; x <= 14; x++) {
        ai.placeBlock('spruce_stairs', x, ry, zb);
        ai.placeBlock('spruce_stairs', x, ry, zf);
    }
    for (int z = zb + 1; z <= zf - 1; z++) {
        for (int x = 3; x <= 14; x++) {
            ai.placeBlock('spruce_planks', x, ry, z);
        }
    }
}
// Ridge pole
ai.placeBox('stripped_spruce_log', 3, 10, -8, 12, 1, 1);
// One patched spot in a different stair block
ai.placeBlock('dark_oak_stairs', 7, 6, -13);
// Hold-down stones resting on the shingles
ai.placeBlock('cobblestone', 6, 8, -12);
ai.placeBlock('mossy_cobblestone', 11, 8, -12);
ai.placeBlock('mossy_cobblestone', 6, 8, -4);
ai.placeBlock('cobblestone', 11, 8, -4);

// 9. Veranda (툇마루): spruce planks 2 deep along the front, low railing, one hanging lantern
ai.placeBox(5, 1, -3, 8, 1, 2, 'spruce_planks');
ai.clearArea(7, 1, -2, 1, 1, 1);
ai.placeBlock('spruce_fence', 5, 2, -2);
ai.placeBlock('spruce_fence', 12, 2, -2);
ai.placeBlock('lantern', 8, 4, -2);
// Stone step at the gate gap
ai.placeBlock('cobblestone_slab', 6, 0, -1);

// 10. Kitchen (부엌): hearth, cauldron, barrels, work table, jars, lantern, cobweb
ai.placeBlock('furnace', 5, 2, -11);
ai.placeBlock('cauldron', 6, 2, -11);
ai.placeBlock('barrel', 5, 2, -10);
ai.placeBlock('barrel', 5, 2, -9);
ai.placeBlock('crafting_table', 7, 2, -9);
ai.placeBlock('decorated_pot', 7, 2, -7);
ai.placeBlock('decorated_pot', 5, 2, -6);
ai.placeBlock('lantern', 6, 4, -9);
ai.placeBlock('cobweb', 5, 5, -11);

// 11. Living room (거실): fireplace, low table with stools, carpet, cabinet, chest, lantern
ai.placeBlock('campfire', 9, 2, -7);
ai.placeBlock('bookshelf', 12, 2, -7);
ai.placeBlock('spruce_planks', 10, 2, -6);
ai.placeBlock('spruce_trapdoor', 10, 3, -6);
ai.placeBlock('spruce_stairs', 9, 2, -6);
ai.placeBlock('spruce_stairs', 11, 2, -6);
ai.placeBlock('brown_carpet', 10, 2, -5);
ai.placeBlock('brown_carpet', 11, 2, -5);
ai.placeBlock('chest', 12, 2, -5);
ai.placeBlock('lantern', 11, 4, -6);
ai.placeBlock('cobweb', 12, 5, -5);

// 12. Upper room (웃방): folded bedding in corner, drawers, wardrobe, desk with books, lantern
ai.placeBlock('white_carpet', 9, 2, -11);
ai.placeBlock('white_carpet', 10, 2, -11);
ai.placeBlock('white_wool', 9, 2, -10);
ai.placeBlock('chest', 12, 2, -11);
ai.placeBlock('barrel', 12, 2, -10);
ai.placeBlock('spruce_slab', 11, 2, -9);
ai.placeBlock('bookshelf', 12, 2, -9);
ai.placeBlock('lantern', 11, 4, -10);
ai.placeBlock('cobweb', 12, 5, -11);

// 13. Outhouse (변소): 3x3 plank hut in the back-left corner, with a door
ai.placeBox(1, 1, -14, 3, 2, 3, 'spruce_planks');
ai.clearArea(2, 1, -13, 1, 2, 1);
ai.placeBox(1, 3, -14, 3, 1, 3, 'spruce_slab');
ai.clearArea(2, 1, -12, 1, 2, 1);
ai.placeBlock('spruce_door', 2, 1, -12);

// Two straw sacks (마대 자루) beside the outhouse
ai.placeBlock('hay_block', 1, 0, -11);
ai.placeBlock('hay_block', 2, 0, -11);

// 14. Firewood stack (나뭇단) under a slab roof, and clay jars (장독) beside the house
ai.placeBox('stripped_spruce_log', 1, 0, -9, 2, 2, 2);
ai.placeBox('spruce_slab', 1, 2, -9, 2, 1, 2);
ai.placeBlock('decorated_pot', 3, 0, -7);
ai.placeBlock('decorated_pot', 3, 0, -6);
ai.placeBlock('decorated_pot', 2, 0, -5);

// 15. Yard: spruce fence (싸리 울타리) around the lot with a front gap
for (int x = 0; x <= 14; x++) {
    if (!(x >= 5 && x <= 7)) {
        ai.placeBlock('spruce_fence', x, 0, -1);
    }
    ai.placeBlock('spruce_fence', x, 0, -15);
}
for (int z = -15; z <= -1; z++) {
    ai.placeBlock('spruce_fence', 0, 0, z);
    ai.placeBlock('spruce_fence', 14, 0, z);
}
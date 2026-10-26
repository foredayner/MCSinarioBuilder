// ===== 노은심의 집 (메이골, 강원도 너와집, 1950s) =====
// Site: x 0..14, z -15..-1, ground y=-1. House footprint x2..12, z-12..-4, front faces +Z.

// 1. Clear the site volume
ai.clearArea(0, 0, -15, 15, 13, 15)

// 2. Packed-earth yard (no paving)
ai.placeFloor(0, -1, -15, 15, 15, 'coarse_dirt')

// 3. Stone footing: cobblestone and mossy_cobblestone mixed by hash
for (int x = 2; x <= 12; x++) {
    for (int z = -12; z <= -4; z++) {
        int h = (x * 73856093) ^ (0 * 19349663) ^ (z * 83492791);
        h = (h ^ (h >>> 13)) * 1274126177;
        int r = ((h ^ (h >>> 16)) & 0x7fffffff) % 3;
        String stone = (r == 0) ? 'mossy_cobblestone' : 'cobblestone';
        ai.placeBlock(x, 0, z, stone);
    }
}

// 4. Interior floors (y=1)
ai.placeFloor(3, 1, -11, 3, 7, 'coarse_dirt');   // 정지 (kitchen, earth floor)
ai.placeFloor(7, 1, -11, 2, 7, 'packed_mud');    // 봉당 hall + 작은방
ai.placeFloor(10, 1, -11, 2, 7, 'birch_planks'); // 안방 / 웃방 warm floor

// 5. Perimeter log-box walls (y=1..4), mud courses between logs, hash weathering
for (int y = 1; y <= 4; y++) {
    for (int x = 2; x <= 12; x++) {
        for (int z = -12; z <= -4; z++) {
            boolean perimeter = (x == 2 || x == 12 || z == -12 || z == -4);
            if (!perimeter) {
                continue;
            }
            String wall = (y % 2 == 1) ? 'stripped_spruce_log' : 'packed_mud';
            int h = (x * 73856093) ^ (y * 19349663) ^ (z * 83492791);
            h = (h ^ (h >>> 13)) * 1274126177;
            int r = ((h ^ (h >>> 16)) & 0x7fffffff) % 6;
            if (r == 0) {
                wall = (y % 2 == 1) ? 'spruce_log' : 'light_gray_terracotta';
            }
            ai.placeBlock(x, y, z, wall);
        }
    }
}

// 6. Ceiling beams / planks (y=5)
ai.placeFloor(2, 5, -12, 11, 9, 'spruce_planks');

// 7. Clear interior air (y=2..4)
ai.clearArea(3, 2, -11, 9, 3, 7);

// 8. Interior partitions (y=1..4)
ai.placeBox('spruce_planks', 6, 1, -11, 1, 4, 7);   // kitchen | hall
ai.placeBox('spruce_planks', 9, 1, -11, 1, 4, 7);   // hall/작은방 | east rooms
ai.placeBox('spruce_planks', 7, 1, -8, 2, 4, 1);    // 작은방 | hall
ai.placeBox('spruce_planks', 10, 1, -8, 2, 4, 1);   // 안방 | 웃방

// 9. Doorways (clear then set doors)
ai.placeBlock('air', 6, 2, -6);
ai.placeBlock('air', 6, 3, -6);
ai.placeBlock('spruce_door', 6, 2, -6);             // kitchen -> hall

ai.placeBlock('air', 9, 2, -10);
ai.placeBlock('air', 9, 3, -10);
ai.placeBlock('spruce_door', 9, 2, -10);            // 작은방/hall side -> 안방

ai.placeBlock('air', 9, 2, -6);
ai.placeBlock('air', 9, 3, -6);
ai.placeBlock('spruce_door', 9, 2, -6);             // hall -> 웃방

ai.placeBlock('air', 7, 2, -8);
ai.placeBlock('air', 7, 3, -8);
ai.placeBlock('spruce_door', 7, 2, -8);             // hall -> 작은방

ai.placeBlock('air', 7, 2, -4);
ai.placeBlock('air', 7, 3, -4);
ai.placeBlock('spruce_door', 7, 2, -4);             // main front door
ai.placeBlock('spruce_slab', 7, 1, -4);             // threshold

// 10. Paper windows on the front wall (small, few)
ai.placeBlock('white_stained_glass_pane', 4, 3, -4);
ai.placeBlock('white_stained_glass_pane', 10, 3, -4);

// 11. Kitchen: 아궁이 hearth, cauldron, bowl shelf, water barrel, work table
ai.placeBox('stone_bricks', 3, 2, -10, 2, 2, 1);
ai.placeBlock('cauldron', 3, 4, -10);
ai.placeBlock('decorated_pot', 5, 2, -11);
ai.placeBlock('decorated_pot', 5, 2, -10);
ai.placeBlock('barrel', 3, 2, -6);
ai.placeBlock('crafting_table', 4, 2, -6);
ai.placeBlock('lantern', 4, 4, -8);

// 12. Hall (봉당): straw basket, hanging lantern, cobweb corner
ai.placeBlock('hay_block', 8, 2, -7);
ai.placeBlock('lantern', 7, 4, -6);
ai.placeBlock('cobweb', 3, 4, -5);

// 13. 웃방 (east front): folded bedding, chest, clothes pole, lantern
ai.placeBlock('white_wool', 11, 2, -5);
ai.placeBlock('white_carpet', 10, 2, -5);
ai.placeBlock('barrel', 11, 2, -7);
ai.placeBox('spruce_fence', 10, 3, -7, 1, 1, 1);
ai.placeBlock('lantern', 10, 4, -6);

// 14. 안방 (east back): bedding, low table, chest, lantern
ai.placeBlock('white_wool', 10, 2, -11);
ai.placeBlock('white_carpet', 11, 2, -11);
ai.placeBlock('spruce_trapdoor', 11, 2, -10);
ai.placeBlock('barrel', 11, 2, -9);
ai.placeBlock('lantern', 10, 4, -10);
ai.placeBlock('cobweb', 11, 4, -11);

// 15. 작은방 (middle back): child's bedding, rag doll, lantern
ai.placeBlock('white_carpet', 7, 2, -10);
ai.placeBlock('white_carpet', 8, 2, -10);
ai.placeBlock('pink_wool', 8, 2, -11);
ai.placeBlock('lantern', 7, 4, -11);

// 16. Veranda (툇마루): spruce planks at y=0, railing, step, hanging lantern
ai.placeFloor(2, 0, -3, 11, 2, 'spruce_planks');
ai.placeBlock('cobblestone_stairs', 7, 1, -3);
ai.placeBox('spruce_fence', 2, 1, -2, 4, 1, 1);
ai.placeBox('spruce_fence', 9, 1, -2, 4, 1, 1);
ai.placeBlock('lantern', 7, 4, -3);

// 17. Shingle roof (너와): stepped spruce slab courses with overhang, ridge pole
ai.placeBox('spruce_slab', 1, 6, -13, 13, 1, 11);
ai.placeBox('spruce_slab', 2, 7, -12, 11, 1, 9);
ai.placeBox('spruce_slab', 3, 8, -11, 9, 1, 7);
ai.placeBox('spruce_slab', 4, 9, -10, 7, 1, 5);
ai.placeBox('stripped_spruce_log', 7, 10, -10, 1, 1, 5);

// Stones holding shingles, one patched dark_oak spot, soot at eaves
ai.placeBlock('mossy_cobblestone', 3, 6, -13);
ai.placeBlock('cobblestone_slab', 9, 6, -13);
ai.placeBlock('dark_oak_slab', 9, 7, -11);
ai.placeBlock('cobweb', 1, 5, -5);
ai.placeBlock('cobweb', 13, 5, -11);

// 18. Yard fence (싸리 울타리) with opening at the front
ai.placeBox('spruce_fence', 0, 0, -15, 1, 1, 15);
ai.placeBox('spruce_fence', 14, 0, -15, 1, 1, 15);
ai.placeBox('spruce_fence', 0, 0, -15, 15, 1, 1);
ai.placeBox('spruce_fence', 0, 0, -1, 6, 1, 1);
ai.placeBox('spruce_fence', 9, 0, -1, 6, 1, 1);

// 19. Firewood stack (나뭇단) under slab roof, east side
ai.placeBox('stripped_spruce_log', 13, 0, -12, 1, 2, 4);
ai.placeBox('spruce_slab', 13, 2, -12, 1, 1, 4);

// 20. 장독대: raised cobblestone platform with clay jars, east side
ai.placeBox('cobblestone', 13, 0, -7, 1, 1, 3);
ai.placeBlock('decorated_pot', 13, 1, -7);
ai.placeBlock('flower_pot', 13, 1, -6);
ai.placeBlock('decorated_pot', 13, 1, -5);

// 21. Small outhouse (변소) in back corner
ai.placeBox('spruce_planks', 1, 0, -14, 2, 3, 2);
ai.placeBox('spruce_slab', 1, 3, -14, 2, 1, 2);
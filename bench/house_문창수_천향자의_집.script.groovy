// 문창수·천향자의 집 — 메이골 너와집 (1950s, Gangwon-do)
// Site: x 0..14, y 0..13, z -15..-1. Front (door) faces +Z.

// 1. Clear the site volume (y 0..13 only; never touches y = -1)
ai.clearArea(0, 0, -15, 15, 14, 15)

// 2. Stone footing: cobblestone with mossy_cobblestone scattered in
ai.placeBox('cobblestone', 2, 0, -13, 11, 1, 10)
for (int x = 2; x <= 12; x++) {
    for (int z = -13; z <= -4; z++) {
        int h = (x * 73856093) ^ (0 * 19349663) ^ (z * 83492791);
        h = (h ^ (h >>> 13)) * 1274126177;
        boolean w = ((((h ^ (h >>> 16)) & 0x7fffffff) % 6) == 0);
        if (w || (x + z) % 5 == 0) {
            ai.placeBlock(x, 0, z, 'mossy_cobblestone');
        }
    }
}

// 3. Main floor (packed mud) and room floors
ai.placeBox('packed_mud', 2, 1, -13, 11, 1, 10)
ai.placeBox('coarse_dirt', 3, 1, -7, 4, 1, 3)          // 정지 (kitchen), lower earth floor
ai.placeBox('orange_terracotta', 3, 1, -12, 4, 1, 4)   // 안방 (warm room)
ai.placeBox('birch_planks', 8, 1, -12, 4, 1, 4)        // 웃방 (small room)
ai.placeBox('coarse_dirt', 8, 1, -7, 4, 1, 3)          // 봉당 (earth-floored hall)

// 4. Log-box walls y 2..5 around the ring, with weathered patches
for (int y = 2; y <= 5; y++) {
    for (int x = 2; x <= 12; x++) {
        for (int z = -13; z <= -4; z++) {
            boolean border = (x == 2 || x == 12 || z == -13 || z == -4);
            if (!border) continue;
            String blk = (y % 2 == 0) ? 'stripped_spruce_log' : 'packed_mud';
            int h = (x * 73856093) ^ (y * 19349663) ^ (z * 83492791);
            h = (h ^ (h >>> 13)) * 1274126177;
            boolean w = ((((h ^ (h >>> 16)) & 0x7fffffff) % 6) == 0);
            if (w) {
                blk = (y % 2 == 0) ? 'spruce_log' : 'mossy_cobblestone';
            }
            ai.placeBlock(x, y, z, blk);
        }
    }
}

// 5. Front openings: main door (x 9) and paper windows
ai.clearArea(9, 2, -4, 1, 2, 1)
ai.placeBlock('spruce_door', 9, 2, -4)
ai.clearArea(5, 3, -4, 1, 1, 1)
ai.placeBlock('white_terracotta', 5, 3, -4)
ai.clearArea(11, 3, -4, 1, 1, 1)
ai.placeBlock('white_terracotta', 11, 3, -4)
// Side and back paper windows (few)
ai.clearArea(2, 3, -11, 1, 1, 1)
ai.placeBlock('white_terracotta', 2, 3, -11)
ai.clearArea(10, 3, -13, 1, 1, 1)
ai.placeBlock('white_terracotta', 10, 3, -13)

// 6. Interior: clear air, then partitions and doors
ai.clearArea(3, 2, -12, 9, 4, 8)
ai.placeBox('packed_mud', 7, 2, -7, 1, 4, 3)           // partition kitchen / hall
ai.placeBox('packed_mud', 7, 2, -12, 1, 4, 4)          // partition 안방 / 웃방
ai.placeBox('packed_mud', 3, 2, -8, 9, 4, 1)           // partition back / front rooms

ai.clearArea(7, 2, -6, 1, 2, 1)
ai.placeBlock('spruce_door', 7, 2, -6)                 // 정지 <-> 봉당
ai.clearArea(7, 2, -10, 1, 2, 1)
ai.placeBlock('spruce_door', 7, 2, -10)                // 안방 <-> 웃방
ai.clearArea(4, 2, -8, 1, 2, 1)
ai.placeBlock('spruce_door', 4, 2, -8)                 // 정지 <-> 안방
ai.clearArea(9, 2, -8, 1, 2, 1)
ai.placeBlock('spruce_door', 9, 2, -8)                 // 봉당 <-> 웃방

// 7. Kitchen (정지): two hearths, cauldron, water barrels, work table, lantern
ai.placeBlock('furnace', 3, 2, -7)
ai.placeBlock('cauldron', 4, 2, -7)
ai.placeBlock('furnace', 5, 2, -7)
ai.placeBlock('barrel', 3, 2, -5)
ai.placeBlock('spruce_slab', 5, 2, -5)
ai.placeBlock('barrel', 6, 2, -5)
ai.placeBlock('lantern', 5, 4, -6)

// 8. 안방: folded bedding, low chest, candle lamp
ai.placeBox('white_carpet', 3, 2, -12, 2, 1, 2)
ai.placeBlock('chest', 6, 2, -12)
ai.placeBlock('spruce_trapdoor', 5, 2, -10)
ai.placeBlock('lantern', 4, 4, -11)

// 9. 웃방: low chest, clothes pole, candle
ai.placeBlock('chest', 11, 2, -12)
ai.placeBlock('spruce_fence', 11, 4, -10)
ai.placeBlock('candle', 10, 2, -10)
ai.placeBlock('lantern', 10, 4, -10)

// 10. 봉당 / 마루: straw sandals (hay), basket barrel, stool
ai.placeBlock('hay_block', 8, 2, -5)
ai.placeBlock('barrel', 11, 2, -5)
ai.placeBlock('spruce_stairs', 10, 2, -6)
ai.placeBlock('lantern', 9, 4, -6)

// 11. Cobwebs in upper inside corners
ai.placeBlock('cobweb', 3, 5, -12)
ai.placeBlock('cobweb', 11, 5, -12)
ai.placeBlock('cobweb', 3, 5, -5)

// 12. Chimney outside the back wall (mud bricks, blackstone cap)
ai.placeBox('mud_bricks', 4, 0, -15, 2, 6, 2)
ai.placeBlock('blackstone', 4, 6, -15)

// 13. Roof of spruce shingle stairs, ridge pole, holding stones
ai.placeRoof(1, 6, -13, 13, 11, 'spruce_stairs')
ai.placeBox('stripped_spruce_log', 2, 10, -8, 11, 1, 1)
ai.placeBlock('cobblestone_slab', 4, 7, -11)
ai.placeBlock('mossy_cobblestone', 9, 7, -10)
ai.placeBlock('cobblestone_slab', 6, 7, -6)
ai.placeBlock('mossy_cobblestone', 11, 7, -4)

// 14. Porch (툇마루) on stone base, with fence posts and hanging lantern
ai.placeBox('stone_bricks', 4, 0, -3, 7, 1, 2)
ai.placeBox('spruce_planks', 4, 1, -3, 7, 1, 2)
ai.placeBlock('spruce_planks', 6, 1, -3)              // broken plank, worn look
ai.placeBlock('spruce_fence', 4, 2, -2)
ai.placeBlock('spruce_fence', 10, 2, -2)
ai.placeBlock('lantern', 10, 3, -2)

// 15. Yard: 싸리 fence along the lot edge with front opening (x 6..8)
ai.placeBox('spruce_fence', 0, 0, -15, 1, 1, 15)
ai.placeBox('spruce_fence', 14, 0, -15, 1, 1, 15)
ai.placeBox('spruce_fence', 1, 0, -15, 13, 1, 1)
ai.placeBox('spruce_fence', 1, 0, -1, 5, 1, 1)
ai.placeBox('spruce_fence', 9, 0, -1, 5, 1, 1)
ai.placeBlock('dead_bush', 6, 0, -14)
ai.placeBlock('dead_bush', 12, 0, -11)

// 16. 장독대 (jar platform) beside the house
ai.placeBox('cobblestone', 13, 0, -12, 1, 1, 3)
ai.placeBlock('decorated_pot', 13, 1, -12)
ai.placeBlock('decorated_pot', 13, 1, -11)
ai.placeBlock('flower_pot', 13, 1, -10)

// 17. 나뭇단 firewood stack under a slab roof
ai.placeBox('spruce_log', 1, 0, -12, 1, 2, 3)
ai.placeBox('spruce_slab', 1, 2, -12, 1, 1, 3)

// 18. 변소 (outhouse) in back corner
ai.placeBox('spruce_planks', 12, 0, -15, 2, 3, 2)
ai.placeBox('spruce_slab', 12, 3, -15, 2, 1, 2)
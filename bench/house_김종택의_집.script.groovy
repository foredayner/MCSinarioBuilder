// 김종택의 집 - 메이골 너와집 (1950s, Gangwon-do mountain village)
// Front faces +Z. Site: x 0..14, z -15..-1, y 0..13. Ground surface y = -1.

// ---------- 0. Clear the lot ----------
ai.clearArea(0, 0, -15, 15, 13, 15)

// ---------- 1. Packed-earth yard ----------
ai.placeBox('coarse_dirt', 0, -1, -15, 15, 1, 15)
ai.placeBox('dirt_path', 7, -1, -2, 2, 1, 2)
ai.placeBox('dirt_path', 7, -1, -1, 2, 1, 1)

// ---------- 2.싸리 울타리 along the lot edge (opening at front) ----------
ai.placeBox('spruce_fence', 0, 0, -15, 1, 1, 15)
ai.placeBox('spruce_fence', 14, 0, -15, 1, 1, 15)
ai.placeBox('spruce_fence', 0, 0, -15, 15, 1, 1)
ai.placeBox('spruce_fence', 0, 0, -1, 7, 1, 1)
ai.placeBox('spruce_fence', 9, 0, -1, 6, 1, 1)
ai.placeBlock('dead_bush', 1, 0, -2)
ai.placeBlock('dead_bush', 13, 0, -2)
ai.placeBlock('dead_bush', 3, 0, -1)
ai.placeBlock('spruce_leaves', 4, 0, -2)
ai.placeBlock('spruce_leaves', 10, 0, -1)
ai.placeBlock('spruce_leaves', 12, 0, -14)

// ---------- 3. Stone footing (dry-stacked, mixed cobble / mossy) ----------
for (int x = 2; x <= 12; x++) {
    for (int z = -12; z <= -5; z++) {
        int h = (x * 73856093) ^ (0 * 19349663) ^ (z * 83492791);
        h = (h ^ (h >>> 13)) * 1274126177;
        int hv = (h ^ (h >>> 16)) & 0x7fffffff;
        String b = (hv % 3 == 0) ? 'mossy_cobblestone' : 'cobblestone';
        ai.placeBlock(x, 0, z, b);
    }
}
// veranda footing
ai.placeBox('cobblestone', 2, 0, -4, 11, 1, 2)
// stone step in front of the door
ai.placeBlock('cobblestone_stairs', 8, 0, -2)

// ---------- 4. Floors ----------
ai.placeBox('packed_mud', 3, 1, -11, 9, 1, 6)
ai.placeBox('birch_planks', 3, 1, -11, 4, 1, 6)     // 안방 warm yellow floor
ai.placeBox('coarse_dirt', 8, 1, -11, 4, 1, 6)      // 정지 earth floor

// ---------- 5. Log-box walls with mud courses (y 1..4) ----------
for (int x = 2; x <= 12; x++) {
    for (int y = 1; y <= 4; y++) {
        for (int z = -12; z <= -5; z++) {
            if (!(x == 2 || x == 12 || z == -12 || z == -5)) {
                continue;
            }
            int h = (x * 73856093) ^ (y * 19349663) ^ (z * 83492791);
            h = (h ^ (h >>> 13)) * 1274126177;
            int hv = (h ^ (h >>> 16)) & 0x7fffffff;
            String b;
            if (y % 2 == 1) {
                b = (hv % 6 == 0) ? 'spruce_log' : 'stripped_spruce_log';
            } else {
                b = (hv % 6 == 0) ? 'mud_bricks' : 'packed_mud';
            }
            ai.placeBlock(x, y, z, b);
        }
    }
}

// interior air (3 blocks high)
ai.clearArea(3, 2, -11, 9, 3, 6)

// ---------- 6. Partition wall with real door (x = 7) ----------
for (int y = 1; y <= 4; y++) {
    for (int z = -11; z <= -6; z++) {
        String pb = (y % 2 == 1) ? 'stripped_spruce_log' : 'packed_mud';
        ai.placeBlock(7, y, z, pb);
    }
}
ai.placeBlock('spruce_door', 7, 2, -9)
ai.placeBlock('air', 7, 3, -9)

// ---------- 7. Front door (offset right) and paper windows ----------
ai.placeBlock('spruce_door', 8, 2, -5)
ai.placeBlock('air', 8, 3, -5)
ai.placeBlock('birch_trapdoor', 4, 3, -5)
ai.placeBlock('birch_trapdoor', 10, 3, -5)

// ---------- 8. Roof: 너와 shingle courses, hip-and-gable ----------
for (int k = 0; k < 5; k++) {
    int y = 5 + k;
    int x0 = 1 + k;
    int x1 = 13 - k;
    int z0 = -13 + k;
    int z1 = -4 - k;
    ai.placeBox('spruce_planks', x0, y, z0, x1 - x0 + 1, 1, z1 - z0 + 1);
    ai.placeBox('spruce_stairs', x0, y, z0, x1 - x0 + 1, 1, 1);
    ai.placeBox('spruce_stairs', x0, y, z1, x1 - x0 + 1, 1, 1);
    ai.placeBox('spruce_stairs', x0, y, z0, 1, 1, z1 - z0 + 1);
    ai.placeBox('spruce_stairs', x1, y, z0, 1, 1, z1 - z0 + 1);
}
// ridge pole
ai.placeBox('stripped_spruce_log', 5, 9, -8, 5, 1, 1)

// weight stones holding the shingles
ai.placeBlock('cobblestone_slab', 2, 5, -7)
ai.placeBlock('mossy_cobblestone', 4, 6, -10)
ai.placeBlock('cobblestone_slab', 6, 7, -8)
ai.placeBlock('mossy_cobblestone', 9, 6, -6)
ai.placeBlock('cobblestone_slab', 10, 7, -10)
// one patched shingle in a different stair block
ai.placeBlock('dark_oak_stairs', 6, 5, -4)

// ---------- 9. Chimney (kitchen side, outside the roof) ----------
ai.placePillar(11, 0, -14, 9, 'cobblestone')
ai.placeBlock('blackstone', 11, 9, -14)

// ---------- 10. 툇마루 veranda ----------
ai.placeBox('spruce_planks', 3, 1, -4, 9, 1, 2)
ai.placeBlock('air', 5, 1, -3)                      // a broken plank
ai.placePillar(3, 2, -4, 3, 'stripped_spruce_log')
ai.placePillar(11, 2, -4, 3, 'stripped_spruce_log')
ai.placeBox('spruce_fence', 3, 2, -3, 4, 1, 1)      // railing with gap at the steps
ai.placeBox('spruce_fence', 10, 2, -3, 2, 1, 1)
ai.placeBlock('lantern', 8, 4, -4)                  // single hanging lantern

// ---------- 11. Cobwebs in upper corners ----------
ai.placeBlock('cobweb', 3, 4, -11)
ai.placeBlock('cobweb', 11, 4, -11)
ai.placeBlock('cobweb', 3, 4, -6)

// ---------- 12. 정지 (kitchen) furnishing ----------
ai.placeBlock('furnace', 8, 2, -11)                 // 아궁이
ai.placeBlock('cauldron', 9, 2, -11)                // 가마솥
ai.placeBlock('furnace', 10, 2, -11)                // 아궁이
ai.placeBlock('barrel', 11, 2, -11)
ai.placeBlock('barrel', 11, 2, -10)
ai.placeBlock('decorated_pot', 11, 2, -8)           // 물독
ai.placeBlock('crafting_table', 9, 2, -7)           // work table
ai.placeBlock('lantern', 9, 4, -9)

// ---------- 13. 안방 (bedroom) furnishing ----------
ai.placeBox('brown_carpet', 3, 2, -8, 2, 1, 2)      // 멍석
ai.placeBox('white_wool', 3, 2, -11, 2, 1, 1)       // folded bedding
ai.placeBlock('chest', 6, 2, -11)                   // 낮은 궤짝
ai.placeBlock('spruce_trapdoor', 5, 2, -7)          // low table
ai.placeBlock('lantern', 5, 4, -8)                  // 등잔

// ---------- 14. 나뭇단 firewood stack (west side, under slab roof) ----------
ai.placeBox('stripped_spruce_log', 1, 0, -11, 1, 1, 4)
ai.placeBox('stripped_spruce_log', 1, 1, -11, 1, 1, 3)
ai.placeBox('spruce_slab', 1, 2, -11, 1, 1, 4)

// ---------- 15. 장독대 jar platform (east side) ----------
ai.placeBox('cobblestone', 13, 0, -11, 1, 1, 5)
ai.placeBlock('decorated_pot', 13, 1, -10)
ai.placeBlock('decorated_pot', 13, 1, -8)
ai.placeBlock('decorated_pot', 13, 1, -6)

// ---------- 16. 변소 outhouse (back corner) ----------
ai.placeBox('spruce_planks', 1, 0, -14, 2, 3, 2)
ai.placeBox('spruce_slab', 1, 3, -14, 2, 1, 2)
ai.placeBlock('spruce_door', 2, 1, -13)
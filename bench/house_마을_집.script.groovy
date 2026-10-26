// 마을 집 (메이골 너와집) - 1950s Gangwon-do mountain village house
// Site: x 0..14, z -15..-1, y 0..13. Front faces +Z (z=-1).
// Footprint x 3..11, z -12..-4. Walls y 1..4, roof y 5..9.

// 1. Clear the house volume and surrounding lot air
ai.clearArea(1, 0, -14, 13, 10, 12)

// 2. Packed-earth yard, dirt path, brushwood fence with gate opening at front
ai.placeBox('coarse_dirt', 1, -1, -14, 13, 1, 12)
ai.placeBox('coarse_dirt', 6, -1, -1, 4, 1, 1)
ai.placeBox('spruce_fence', 0, 0, -15, 15, 1, 1)
ai.placeBox('spruce_fence', 0, 0, -15, 1, 1, 15)
ai.placeBox('spruce_fence', 14, 0, -15, 1, 1, 15)
ai.placeBox('spruce_fence', 0, 0, -1, 6, 1, 1)
ai.placeBox('spruce_fence', 10, 0, -1, 5, 1, 1)

// 3. Dry-stacked stone foundation (x 3..11, z -12..-2), mixed with mossy stone
ai.placeBox('cobblestone', 3, 0, -12, 9, 1, 11)
for (int x = 3; x <= 11; x++) {
    for (int z = -12; z <= -2; z++) {
        int h = (x * 73856093) ^ (0 * 19349663) ^ (z * 83492791);
        h = (h ^ (h >>> 13)) * 1274126177;
        boolean sw = (((h ^ (h >>> 16)) & 0x7fffffff) % 6 == 0);
        if (sw) {
            ai.placeBlock(x, 0, z, 'mossy_cobblestone');
        }
    }
}

// 4. Clear interior air
ai.clearArea(4, 1, -11, 7, 4, 7)

// 5. Log-box walls y 1..4 with mud infill swaps (ring around footprint)
for (int y = 1; y <= 4; y++) {
    for (int x = 3; x <= 11; x++) {
        for (int z = -12; z <= -4; z++) {
            if (x == 3 || x == 11 || z == -12 || z == -4) {
                int h = (x * 73856093) ^ (y * 19349663) ^ (z * 83492791);
                h = (h ^ (h >>> 13)) * 1274126177;
                boolean sw = (((h ^ (h >>> 16)) & 0x7fffffff) % 6 == 0);
                ai.placeBlock(x, y, z, sw ? 'packed_mud' : 'stripped_spruce_log');
            }
        }
    }
}

// 6. Interior floors (y = 1)
ai.placeBox('packed_mud', 4, 1, -11, 3, 1, 7)       // 정지 (kitchen), earth floor
ai.placeBox('birch_planks', 8, 1, -11, 3, 1, 4)     // 안방
ai.placeBox('spruce_planks', 8, 1, -6, 3, 1, 2)     // 마루 (hall)

// 7. Interior partitions (mud walls)
ai.placeBox('packed_mud', 7, 1, -11, 1, 4, 7)       // x = 7 partition
ai.placeBox('packed_mud', 8, 1, -7, 3, 4, 1)        // z = -7 partition

// 8. Doorways with real doors
ai.clearArea(7, 1, -9, 1, 2, 1)
ai.placeBlock('spruce_door', 7, 1, -9)              // 정지 <-> 마루/안방 hall
ai.clearArea(9, 1, -7, 1, 2, 1)
ai.placeBlock('spruce_door', 9, 1, -7)              // 안방 <-> 마루
ai.clearArea(9, 1, -4, 1, 2, 1)
ai.placeBlock('spruce_door', 9, 1, -4)              // main front door

// 9. Small paper-pane windows on the front only
ai.placeBlock('white_stained_glass_pane', 5, 2, -4)
ai.placeBlock('white_stained_glass_pane', 7, 2, -4)

// 10. Front stone step
ai.placeBox('cobblestone_slab', 8, 0, -1, 2, 1, 1)

// 11. Shingle roof courses (너와), hip-and-gable, y 5..9, weathered patches
for (int i = 0; i < 5; i++) {
    int y = 5 + i;
    for (int x = 2 + i; x <= 12 - i; x++) {
        for (int z = -13 + i; z <= -3 - i; z++) {
            int h = (x * 73856093) ^ (y * 19349663) ^ (z * 83492791);
            h = (h ^ (h >>> 13)) * 1274126177;
            boolean sw = (((h ^ (h >>> 16)) & 0x7fffffff) % 6 == 0);
            ai.placeBlock(x, y, z, sw ? 'dark_oak_slab' : 'spruce_slab');
        }
    }
}

// 12. Ridge pole
ai.placeBox('stripped_spruce_log', 6, 10, -8, 3, 1, 1)

// 13. Holding stones on the roof
ai.placeBlock('cobblestone_slab', 2, 6, -7)
ai.placeBlock('mossy_cobblestone_slab', 12, 6, -7)
ai.placeBlock('mossy_cobblestone_slab', 7, 6, -13)
ai.placeBlock('cobblestone_slab', 7, 6, -3)
ai.placeBlock('mossy_cobblestone_slab', 7, 11, -8)

// 14. Cobblestone chimney (굴뚝) with blackstone soot cap
ai.placePillar(5, 5, -13, 5, 'cobblestone')
ai.placeBlock('blackstone', 5, 10, -13)

// 15. Front veranda (툇마루): planks, posts, railing, hanging lantern
ai.placeBox('spruce_planks', 3, 1, -3, 9, 1, 2)
ai.clearArea(6, 1, -2, 1, 1, 1)                     // one missing plank
ai.placeBox('stripped_spruce_log', 3, 2, -3, 1, 3, 1)
ai.placeBox('stripped_spruce_log', 11, 2, -3, 1, 3, 1)
ai.placeBox('spruce_fence', 4, 2, -2, 7, 1, 1)
ai.placeBlock('lantern', 7, 4, -3)

// 16. Kitchen (정지): two fire holes, cauldron, water barrels, work table
ai.placeBlock('furnace', 4, 2, -11)
ai.placeBlock('cauldron', 5, 2, -11)
ai.placeBlock('furnace', 6, 2, -11)
ai.placeBlock('barrel', 4, 2, -9)
ai.placeBlock('barrel', 4, 2, -7)
ai.placeBlock('crafting_table', 6, 2, -7)
ai.placeBlock('lantern', 5, 4, -8)

// 17. 안방: bedding folded in corner, low chest, low table, lantern
ai.placeBox('white_carpet', 8, 2, -11, 2, 1, 2)
ai.placeBlock('chest', 10, 2, -9)
ai.placeBlock('spruce_slab', 8, 2, -8)
ai.placeBlock('lantern', 9, 4, -10)

// 18. 마루 (hall): stool, straw bundle, straw sandals basket, lantern
ai.placeBlock('spruce_stairs', 8, 2, -5)
ai.placeBlock('hay_block', 8, 2, -6)
ai.placeBlock('barrel', 10, 2, -5)
ai.placeBlock('lantern', 9, 4, -5)

// 19. Cobwebs in upper inside corners
ai.placeBlock('cobweb', 4, 4, -5)
ai.placeBlock('cobweb', 10, 4, -11)
ai.placeBlock('cobweb', 10, 4, -5)

// 20. Outhouse (변소) in back corner
ai.placeBox('spruce_planks', 1, 0, -14, 2, 2, 2)
ai.placeBox('spruce_slab', 1, 2, -14, 2, 1, 2)

// 21. Firewood stack (나뭇단) under slab cover
ai.placeBox('stripped_spruce_log', 2, 0, -9, 1, 2, 3)
ai.placeBox('spruce_slab', 2, 2, -9, 1, 1, 3)

// 22. Jar platform (장독대) beside the house
ai.placeBox('cobblestone', 12, 0, -11, 2, 1, 4)
ai.placeBlock('decorated_pot', 12, 1, -11)
ai.placeBlock('flower_pot', 13, 1, -10)
ai.placeBlock('decorated_pot', 12, 1, -9)
ai.placeBlock('flower_pot', 13, 1, -8)

// 23. Yard litter
ai.placeBlock('spruce_leaves', 6, 0, -12)
ai.placeBlock('spruce_leaves', 10, 0, -14)
ai.placeBlock('dead_bush', 2, 0, -12)
ai.placeBlock('dead_bush', 13, 0, -3)
// 배길옥의 집 - 메이골 너와집 (1950s, Gangwon-do)
// Footprint x 3..17, z -17..-7; yard in front (+Z). Roof ridge at y=12.

// ---------- Foundation: dry-stacked stone base, mossy back row ----------
ai.placeBox('cobblestone', 3, 0, -17, 15, 1, 13);
ai.placeBox('mossy_cobblestone', 3, 0, -17, 15, 1, 1);
ai.placeBox('gravel', 2, 0, -18, 17, 1, 1);

// Clear envelope air above the base so terrain stays out of the house
ai.clearArea(2, 1, -18, 17, 12, 15);

// ---------- Log-box walls: alternating stripped spruce logs and packed mud courses ----------
for (int y = 1; y <= 5; y++) {
    String course = (y % 2 == 1) ? 'stripped_spruce_log' : 'packed_mud';
    ai.placeBox(course, 3, y, -17, 15, 1, 11);
}

// Clear interior volume (3 blocks of headroom under ceiling beams at y=5)
ai.clearArea(4, 2, -16, 13, 3, 9);

// ---------- Floors ----------
ai.placeFloor(4, 1, -16, 13, 9, 'packed_mud');        // base: stamped mud
ai.placeFloor(7, 1, -11, 5, 4, 'spruce_planks');      // 대청마루 (main hall)
ai.placeFloor(4, 1, -16, 5, 4, 'orange_terracotta');  // 안방 oiled-paper floor
ai.placeFloor(10, 1, -16, 7, 4, 'birch_planks');      // 아이 방
ai.placeFloor(4, 1, -11, 2, 4, 'coarse_dirt');        // 광 (storeroom, earth)

// ---------- Interior partitions (mud walls, y=2..4) ----------
ai.placeBox('packed_mud', 6, 2, -11, 1, 3, 4);        // 광 | 대청
ai.placeBox('packed_mud', 12, 2, -11, 1, 3, 4);       // 대청 | 정지
ai.placeBox('packed_mud', 4, 2, -12, 13, 3, 1);       // front row | back rows
ai.placeBox('packed_mud', 9, 2, -16, 1, 3, 4);        // 안방 | 아이 방

// ---------- Doorways (2 high) and doors ----------
ai.clearArea(9, 2, -7, 1, 2, 1);
ai.placeBlock(9, 2, -7, 'spruce_door');               // front door, faces the street
ai.clearArea(6, 2, -9, 1, 2, 1);
ai.placeBlock(6, 2, -9, 'spruce_door');               // 광 door
ai.clearArea(12, 2, -9, 1, 2, 1);
ai.placeBlock(12, 2, -9, 'spruce_door');              // 정지 door
ai.clearArea(8, 2, -12, 1, 2, 1);
ai.placeBlock(8, 2, -12, 'spruce_door');              // 안방 door
ai.clearArea(11, 2, -12, 1, 2, 1);
ai.placeBlock(11, 2, -12, 'spruce_door');             // 아이 방 door

// ---------- Front windows: paper-pane panes in lattice ----------
ai.placeBlock(7, 3, -7, 'white_stained_glass_pane');
ai.placeBlock(11, 3, -7, 'white_stained_glass_pane');
ai.placeBlock(14, 3, -7, 'white_stained_glass_pane');
ai.placeBlock(7, 2, -7, 'spruce_fence');              // sill lattice
ai.placeBlock(11, 2, -7, 'spruce_fence');
ai.placeBlock(14, 2, -7, 'spruce_fence');

// ---------- 정지 (kitchen): hearth, cauldron, water jar, barrels ----------
ai.placeBox('stone_bricks', 14, 2, -9, 2, 1, 1);      // 아궁이 stone block
ai.placeBlock(14, 2, -8, 'furnace');
ai.placeBlock(14, 3, -9, 'cauldron');
ai.placeBlock(16, 2, -11, 'barrel');
ai.placeBlock(13, 2, -11, 'decorated_pot');           // 물독
ai.placeBlock(15, 2, -11, 'spruce_trapdoor');         // work table top
ai.placeBlock(16, 2, -9, 'barrel');
ai.placeBlock(14, 4, -10, 'lantern');

// ---------- 광 (storeroom): grain sacks, jars, tools ----------
ai.placeBlock(4, 2, -11, 'barrel');
ai.placeBlock(4, 2, -9, 'decorated_pot');
ai.placeBlock(5, 2, -8, 'hay_block');
ai.placeBlock(5, 2, -11, 'hay_block');
ai.placeBlock(4, 4, -9, 'lantern');

// ---------- 안방 (master room): bedding, chest, low table, lantern ----------
ai.placeBlock(5, 2, -15, 'white_carpet');
ai.placeBlock(6, 2, -15, 'white_carpet');
ai.placeBlock(4, 2, -16, 'chest');                    // 반닫이
ai.placeBlock(7, 2, -14, 'spruce_trapdoor');          // low table
ai.placeBlock(6, 4, -14, 'lantern');

// ---------- 아이 방 (child room): small bedding, toy chest, lantern ----------
ai.placeBlock(11, 2, -15, 'light_gray_carpet');
ai.placeBlock(12, 2, -15, 'light_gray_carpet');
ai.placeBlock(15, 2, -15, 'barrel');
ai.placeBlock(13, 4, -14, 'lantern');

// ---------- 대청마루 (hall): straw mats and central lantern ----------
ai.placeBlock(9, 4, -10, 'lantern');
ai.placeBlock(8, 2, -9, 'hay_block');
ai.placeBlock(10, 2, -9, 'hay_block');

// ---------- Roof: stepped 45-degree split-shingle layers (너와) ----------
for (int k = 0; k <= 5; k++) {
    String shingle = (k % 2 == 0) ? 'spruce_slab' : 'dark_oak_slab';
    ai.placeBox(shingle, 2 + k, 6 + k, -18 + k, 17 - 2 * k, 1, 15 - 2 * k);
}
ai.placeBox('stripped_spruce_log', 8, 12, -11, 5, 1, 1);   // ridge pole

// ---------- 툇마루 (front veranda) under the eaves ----------
ai.placeBox('cobblestone', 3, 0, -6, 15, 1, 2);            // base under veranda
ai.placeFloor(3, 1, -6, 15, 2, 'spruce_planks');           // veranda boards
ai.placeBlock(10, 1, -5, 'air');                           // worn-through plank
ai.placeBlock(4, 1, -6, 'air');                            // missing plank
ai.placePillar(3, 2, -5, 4, 'stripped_spruce_log');
ai.placePillar(17, 2, -5, 4, 'stripped_spruce_log');
ai.placeBox('stripped_spruce_log', 3, 5, -5, 15, 1, 1);    // porch beam
ai.placeBox('spruce_fence', 3, 2, -4, 15, 1, 1);           // low railing
ai.clearArea(9, 2, -4, 1, 1, 1);                           // railing gap at the step
ai.placeBlock(9, 0, -4, 'cobblestone_slab');               // stone step
ai.placeBlock(9, 4, -5, 'lantern');                        // hanging porch lantern

// ---------- Chimney (굴뚝) outside the eaves ----------
ai.placePillar(19, 0, -12, 9, 'cobblestone');
ai.placeBlock(19, 9, -12, 'blackstone');

// ---------- Yard: packed earth path, brushwood fence, tufts ----------
ai.placeBox('dirt_path', 8, -1, -4, 3, 1, 4);
ai.placeBox('spruce_fence', 0, 0, -2, 8, 1, 1);
ai.placeBox('spruce_fence', 11, 0, -2, 10, 1, 1);
ai.placeBlock(1, 0, -3, 'dead_bush');
ai.placeBlock(18, 0, -3, 'dead_bush');
ai.placeBlock(2, 0, -8, 'azalea');

// ---------- 장독대 (jar platform) beside the house ----------
ai.placeBox('cobblestone', 19, 0, -17, 2, 1, 2);
ai.placeBlock(19, 1, -17, 'decorated_pot');
ai.placeBlock(20, 1, -17, 'decorated_pot');
ai.placeBlock(19, 1, -16, 'decorated_pot');

// ---------- 나뭇단 (firewood stack) under a slab roof ----------
ai.placeBox('spruce_log', 0, 0, -13, 1, 2, 4);
ai.placeBox('spruce_slab', 0, 2, -13, 1, 1, 4);

// ---------- 변소 (outhouse) in the back corner ----------
ai.placeBox('spruce_planks', 18, 0, -21, 3, 3, 3);
ai.clearArea(19, 1, -20, 1, 2, 1);
ai.placeBox('spruce_slab', 18, 3, -21, 3, 1, 3);

// ---------- Wear: soot, cobwebs, spruce needles, sap ----------
ai.placeBlock(2, 5, -10, 'cobweb');
ai.placeBlock(18, 5, -8, 'cobweb');
ai.placeBlock(3, 0, -3, 'spruce_leaves');
ai.placeBlock(16, 0, -3, 'spruce_leaves');
ai.placeBlock(17, 1, -6, 'dark_oak_stairs');
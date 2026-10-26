// 신상헌의 집 - 메이골 너와집 (1950s, Gangwon-do)
// Footprint x3..11, z-12..-5. Front (door) faces +Z.

// ---- Yard: packed coarse earth, no paving ----
ai.placeBox('coarse_dirt', 1, -1, -13, 13, 1, 12)
ai.placeBox('dirt_path', 5, -1, -2, 3, 1, 1)

// ---- Stone base (돌 기단) ----
ai.placeBox('cobblestone', 3, 0, -12, 9, 1, 8)
ai.placeBox('mossy_cobblestone', 3, 0, -12, 9, 1, 1)
ai.placeBox('gravel', 3, -1, -12, 9, 1, 1)

// ---- Veranda base and planks (툇마루), 3 deep ----
ai.placeBox('cobblestone', 3, 0, -4, 9, 1, 3)
ai.placeBox('spruce_planks', 3, 1, -4, 9, 1, 3)
ai.placeBox('spruce_planks', 9, 1, -2, 1, 1, 1)
ai.placeBlock('air', 9, 1, -2)
ai.placeBlock('cobblestone_slab', 6, 0, -1)

// ---- Log-box walls (귀틀벽), 4 high ----
ai.placeBox('stripped_spruce_log', 3, 1, -12, 9, 4, 1)
ai.placeBox('stripped_spruce_log', 3, 1, -5, 9, 4, 1)
ai.placeBox('stripped_spruce_log', 3, 1, -11, 1, 4, 6)
ai.placeBox('stripped_spruce_log', 11, 1, -11, 1, 4, 6)
ai.placeBox('packed_mud', 11, 3, -11, 1, 1, 6)

// ---- Clear interior and lay floors ----
ai.clearArea(4, 1, -11, 7, 4, 6)
ai.placeBox('packed_mud', 4, 1, -11, 7, 1, 6)
ai.placeBox('coarse_dirt', 4, 1, -11, 3, 1, 6)
ai.placeBox('birch_planks', 8, 1, -11, 3, 1, 3)
ai.placeBox('birch_planks', 8, 1, -7, 3, 1, 2)

// ---- Partitions ----
// West/east partition at x=7, door at z=-7
ai.placeBox('packed_mud', 7, 2, -11, 1, 3, 4)
ai.placeBox('packed_mud', 7, 2, -6, 1, 3, 1)
ai.placeBlock('spruce_door', 7, 2, -7)
ai.placeBlock('spruce_door', 7, 3, -7)
ai.placeBlock('spruce_planks', 7, 4, -7)
// Back partition at z=-8 (east side), door at x=9
ai.placeBox('packed_mud', 8, 2, -8, 3, 3, 1)
ai.placeBlock('spruce_door', 9, 2, -8)
ai.placeBlock('spruce_door', 9, 3, -8)
ai.placeBlock('stripped_spruce_log', 9, 4, -8)

// ---- Front door (offset to x=6), windows (paper-lattice shutters) ----
ai.placeBlock('spruce_door', 6, 1, -5)
ai.placeBlock('spruce_door', 6, 2, -5)
ai.placeBlock('birch_trapdoor', 4, 2, -5)
ai.placeBlock('birch_trapdoor', 4, 3, -5)
ai.placeBlock('birch_trapdoor', 9, 2, -5)
ai.placeBlock('birch_trapdoor', 9, 3, -5)
ai.placeBlock('flower_pot', 4, 2, -4)
ai.placeBlock('flower_pot', 9, 2, -4)

// ---- Kitchen hearth (아궁이) and kitchen furniture ----
ai.placeBox('blackstone', 4, 1, -11, 3, 1, 1)
ai.placeBlock('furnace', 5, 2, -11)
ai.placeBlock('cauldron', 4, 2, -11)
ai.placeBlock('cauldron', 6, 2, -11)
ai.placeBlock('barrel', 4, 2, -9)
ai.placeBlock('barrel', 4, 2, -8)
ai.placeBlock('spruce_slab', 6, 2, -9)
ai.placeBlock('decorated_pot', 6, 2, -8)

// ---- 안방 (back east room): bedding, low chest, low table ----
ai.placeBox('white_carpet', 8, 2, -11, 2, 1, 2)
ai.placeBlock('chest', 10, 2, -11)
ai.placeBlock('spruce_slab', 10, 2, -9)
ai.placeBlock('lantern', 9, 4, -10)

// ---- 웃방 (front east room): clothes chest, hanging cloth ----
ai.placeBlock('barrel', 10, 2, -6)
ai.placeBox('white_wool', 10, 3, -7, 1, 1, 1)
ai.placeBlock('spruce_stairs', 8, 2, -6)
ai.placeBlock('lantern', 9, 4, -6)

// ---- 봉당 entry (west front) ----
ai.placeBlock('hay_block', 4, 2, -6)
ai.placeBlock('lantern', 5, 4, -6)

// ---- Kitchen lantern ----
ai.placeBlock('lantern', 5, 4, -10)

// ---- Veranda railing and hanging lantern ----
ai.placeBox('spruce_fence', 3, 2, -2, 9, 1, 1)
ai.placeBlock('air', 6, 2, -2)
ai.placeBox('spruce_fence', 3, 2, -4, 1, 1, 2)
ai.placeBox('spruce_fence', 11, 2, -4, 1, 1, 2)
ai.placeBlock('spruce_fence', 6, 4, -3)
ai.placeBlock('lantern', 6, 3, -3)

// ---- Roof: split-shingle courses (너와), stepped hip roof ----
for (int k = 0; k < 5; k++) {
    String mat = (k % 2 == 0) ? 'spruce_slab' : 'dark_oak_slab';
    ai.placeBox(mat, 2 + k, 5 + k, -13 + k, 11 - 2 * k, 1, 10 - 2 * k);
}

// Ridge pole
ai.placeBox('stripped_spruce_log', 6, 10, -9, 3, 1, 2)

// Hold-down stones along roof rims
ai.placeBlock('mossy_cobblestone', 2, 6, -10)
ai.placeBlock('mossy_cobblestone', 12, 6, -10)
ai.placeBlock('mossy_cobblestone', 2, 6, -7)
ai.placeBlock('mossy_cobblestone', 12, 6, -7)
ai.placeBlock('mossy_cobblestone', 7, 6, -13)
ai.placeBlock('mossy_cobblestone', 7, 6, -4)
ai.placeBlock('cobblestone_slab', 3, 7, -9)
ai.placeBlock('cobblestone_slab', 11, 7, -9)
ai.placeBlock('cobblestone_slab', 5, 8, -8)
ai.placeBlock('cobblestone_slab', 9, 8, -8)

// Cobweb and soot at eaves
ai.placeBlock('cobweb', 3, 4, -13)
ai.placeBlock('cobweb', 11, 4, -13)

// ---- Chimney (굴뚝) on west side, blackened cap ----
ai.placeBox('mud_bricks', 0, 0, -10, 2, 8, 2)
ai.placeBox('blackstone', 0, 8, -10, 2, 1, 2)

// ---- Firewood stack (나뭇단) and 장독대 jar platform ----
ai.placeBox('stripped_spruce_log', 12, 0, -12, 1, 2, 4)
ai.placeBox('spruce_slab', 12, 2, -12, 1, 1, 4)
ai.placeBox('cobblestone', 12, 0, -8, 2, 1, 3)
ai.placeBlock('decorated_pot', 12, 1, -8)
ai.placeBlock('decorated_pot', 13, 1, -7)
ai.placeBlock('decorated_pot', 12, 1, -6)

// ---- Hay bundles under eaves (나뭇단) on west side ----
ai.placeBox('hay_block', 2, 0, -8, 1, 2, 3)

// ---- Yard fence (싸리 울타리) with gaps for chimney ----
ai.placeBox('spruce_fence', 0, 0, -14, 1, 1, 4)
ai.placeBox('spruce_fence', 0, 0, -8, 1, 1, 7)
ai.placeBox('spruce_fence', 14, 0, -14, 1, 1, 13)
ai.placeBox('spruce_fence', 1, 0, -14, 13, 1, 1)

// Dead bush tufts and azalea bits along base of fence
ai.placeBlock('dead_bush', 1, 0, -13)
ai.placeBlock('dead_bush', 13, 0, -3)
ai.placeBlock('azalea', 13, 0, -13)

// Pine needles litter the yard
ai.placeBlock('spruce_leaves', 5, 0, -2)
ai.placeBlock('spruce_leaves', 10, 0, -3)
ai.placeBlock('spruce_leaves', 2, 0, -12)
package com.foredayner.scenario.gametest;

import com.example.ai.AiController;
import com.example.api.AiBuildApi;
import com.foredayner.scenario.town.BuildService;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * Stands in for the AI on the "302호의 침묵" apartment: each room's furnishing is a script written by
 * hand, as the AI would answer it (the room's design frame: x along its door side, z from -1 at the
 * door side to -depth at the back, y from the floor). Rooms it has no script for are refused.
 */
public final class HandDesigner implements BuildService.Designer {

    /** Every script, by room name, as it was sent (for the results). */
    public final java.util.Map<String, String> scripts = new java.util.LinkedHashMap<>();

    private static final class Script {
        final StringBuilder s = new StringBuilder();

        Script block(int x, int y, int z, String block) {
            s.append(String.format(Locale.ROOT, "ai.placeBlock(%d, %d, %d, '%s')\n", x, y, z, block));
            return this;
        }

        /** A flat layer w along x, l along z (toward the front) from (x, y, z). */
        Script layer(int x, int y, int z, int w, int l, String block) {
            s.append(String.format(Locale.ROOT, "ai.placeFloor(%d, %d, %d, %d, %d, '%s')\n", x, y, z, w, l, block));
            return this;
        }

        /** A solid box, layer by layer. */
        Script solid(int x, int y, int z, int w, int h, int l, String block) {
            for (int i = 0; i < h; i++) {
                layer(x, y + i, z, w, l, block);
            }
            return this;
        }

        Script mark(String name, int x, int y, int z) {
            s.append(String.format(Locale.ROOT, "ai.mark('%s', %d, %d, %d)\n", name, x, y, z));
            return this;
        }
    }

    static String roomName(String description) {
        int q = description.indexOf('"');
        int q2 = q < 0 ? -1 : description.indexOf('"', q + 1);
        return q2 > q ? description.substring(q + 1, q2) : "";
    }

    @Override
    public CompletableFuture<AiBuildApi.Design> building(ServerWorld world, BlockPos near, AiBuildApi.Site site, AiBuildApi.BuildingRequest req) {
        String name = roomName(req.description());
        int w = site.width(), d = site.depth();
        Script s = new Script();
        switch (name) {
            case "1층 복도" -> {
                // A long hall (3 across, the way in at the front): grey tiles, the mailboxes on the left wall, tube lights.
                s.layer(0, 0, -d, w, d, "light_gray_carpet");
                s.solid(0, 1, -9, 1, 2, 4, "barrel[facing=east]");
                s.mark("clue:bills", 1, 0, -7);
                for (int z = -3; z > -d; z -= 7) {
                    s.block(1, 3, z, "sea_lantern");
                }
                s.block(w - 1, 0, -2, "potted_fern");
            }
            case "관리실" -> {
                // The caretaker's room: a desk with a lamp and a small TV, the key rack on the back wall, a cabinet.
                s.layer(0, 0, -d, w, d, "light_gray_carpet");
                s.layer(0, 0, -d + 1, 3, 1, "spruce_slab[type=top]");
                s.block(1, 0, -d + 2, "spruce_stairs[facing=north]");
                s.block(0, 1, -d + 1, "lantern");
                s.block(2, 1, -d + 1, "black_concrete");
                for (int x = w - 3; x < w; x++) {
                    if (x != w - 2) {
                        s.block(x, 2, -d, "tripwire_hook[facing=south]");
                    }
                }
                s.mark("clue:key_rack", w - 2, 1, -d);
                s.solid(w - 1, 0, -3, 1, 2, 1, "barrel[facing=west]");
                s.mark("npc:최씨", 0, 0, -3);
                s.block(w / 2, 3, -3, "lantern[hanging=true]");
            }
            case "3층 복도" -> {
                s.layer(0, 0, -d, w, d, "light_gray_carpet");
                for (int x = 2; x < w; x += 7) {
                    s.block(x, 3, -2, "sea_lantern");
                }
                s.block(w - 1, 0, -3, "potted_fern");
            }
            case "301호" -> {
                // The old lady's flat: yellow linoleum, a mother-of-pearl wardrobe, a low table with cushions, a small TV.
                s.layer(0, 0, -d, w, d, "yellow_carpet");
                s.solid(0, 0, -d, 3, 2, 1, "dark_oak_planks");
                s.block(1, 2, -d, "lantern");
                s.layer(2, 0, -3, 2, 1, "spruce_slab[type=bottom]");
                s.block(2, 0, -2, "red_carpet");
                s.block(3, 0, -4, "red_carpet");
                s.block(w - 1, 0, -d, "dark_oak_slab[type=bottom]");
                s.block(w - 1, 1, -d, "black_concrete");
                s.layer(w - 2, 0, -2, 2, 1, "white_carpet");
                s.mark("npc:오순례", 1, 0, -3);
                s.block(w / 2, 3, -3, "lantern[hanging=true]");
            }
            case "302호 거실" -> {
                // The living room: a dining table with a bowl (the key beside it), a sofa on the back wall, the TV.
                s.layer(0, 0, -d, w, d, "yellow_carpet");
                s.layer(1, 0, -5, 2, 1, "spruce_slab[type=top]");
                s.block(1, 0, -4, "spruce_stairs[facing=south]");
                s.block(2, 0, -6, "spruce_stairs[facing=north]");
                s.block(2, 1, -5, "flower_pot");
                s.mark("clue:rusty_key", 1, 1, -5);
                s.layer(w - 3, 0, -d, 3, 1, "dark_oak_stairs[facing=north]");
                s.block(w - 1, 0, -1, "dark_oak_slab[type=bottom]");
                s.block(w - 1, 1, -1, "black_concrete");
                s.block(w / 2, 3, -3, "lantern[hanging=true]");
            }
            case "302호 서재" -> {
                // The study: a whole wall of books, a desk with the lamp and the pile of manuscript.
                s.layer(0, 0, -d, w, d, "brown_carpet");
                s.solid(0, 0, -d, w, 3, 1, "bookshelf");
                s.mark("clue:bookshelf", w / 2, 0, -d + 1);
                s.layer(0, 0, -3, 1, 2, "spruce_slab[type=top]");
                s.block(1, 0, -3, "spruce_stairs[facing=east]");
                s.block(0, 1, -3, "lantern");
                s.mark("clue:manuscript", 0, 1, -2);
                s.block(w - 1, 0, -2, "chest[facing=west]");
                s.block(w / 2, 3, -3, "lantern[hanging=true]");
            }
            case "302호 침실" -> {
                // The bedroom: old boards (two of them loose, the diary under them), a bed, a wardrobe.
                s.block(w - 2, 0, -d + 1, "white_bed[facing=north,part=foot]");
                s.block(w - 2, 0, -d, "white_bed[facing=north,part=head]");
                s.solid(0, 0, -d, 2, 3, 1, "dark_oak_planks");
                s.block(w - 1, 0, -d, "spruce_slab[type=bottom]");
                s.block(w - 1, 1, -d, "lantern");
                s.block(0, 0, -2, "oak_trapdoor[half=bottom]");
                s.block(1, 0, -2, "oak_trapdoor[half=bottom]");
                s.mark("clue:floorboard", 0, 1, -2);
                s.mark("clue:diary", 1, 1, -2);
                s.block(w / 2, 3, -3, "lantern[hanging=true]");
            }
            case "지하 보일러실" -> {
                // The boiler room: the boiler at the back, pipes along the ceiling, a drain, one dim lantern.
                // The false wall is the room's front door (its middle); the stairs come down beside it.
                s.solid(2, 0, -d + 1, 4, 3, 3, "iron_block");
                s.layer(3, 0, -d + 4, 2, 1, "furnace[facing=south]");
                s.block(1, 0, -d + 2, "chain[axis=y]").block(1, 1, -d + 2, "chain[axis=y]").block(1, 2, -d + 2, "chain[axis=y]");
                s.layer(0, 3, -9, w, 1, "iron_bars");
                s.block(w - 1, 0, -d, "barrel[facing=up]");
                s.block(w - 1, 0, -d + 1, "barrel[facing=up]");
                s.block(0, 3, -d, "cobweb");
                s.block(w - 2, 0, -4, "iron_trapdoor[half=bottom]");
                s.mark("clue:silver_ring", w - 2, 1, -4);
                s.mark("clue:new_wall", w / 2, 0, -1);
                s.block(w / 2, 3, -8, "lantern[hanging=true]");
            }
            case "벽 뒤 공간" -> {
                // Behind the wall: bare earth, an old altar with candles and dead paper flowers. No other light.
                s.layer(0, -1, -d, w, d, "coarse_dirt");
                s.layer(1, 0, -d, 3, 1, "stripped_spruce_log[axis=x]");
                s.block(1, 1, -d, "candle[candles=3,lit=true]");
                s.block(3, 1, -d, "candle[candles=2,lit=true]");
                s.mark("clue:altar", 2, 1, -d);
                s.block(0, 0, -d, "dead_bush");
                s.block(w - 1, 0, -d, "dead_bush");
                s.block(0, 3, -d, "cobweb");
            }
            default -> {
                return CompletableFuture.completedFuture(new AiBuildApi.Design(false, "no hand script for " + name, null, "", 0, 0, 0));
            }
        }
        String script = s.s.toString();
        scripts.put(name, String.format(Locale.ROOT, "// site %d x %d, height %d\n%s", w, d, site.maxHeight(), script));
        var eval = AiController.evaluate(world, near, 180f, null, script, false);
        if (!eval.ok()) {
            return CompletableFuture.completedFuture(new AiBuildApi.Design(false, eval.failure(), null, script, 0, 0, 0));
        }
        return CompletableFuture.completedFuture(new AiBuildApi.Design(true, null, eval.plan(), script, 1, 0, 0));
    }

    @Override
    public CompletableFuture<AiBuildApi.Design> object(ServerWorld world, BlockPos near, String description, double maxSize, String model) {
        return CompletableFuture.completedFuture(new AiBuildApi.Design(false, "no props", null, "", 0, 0, 0));
    }
}

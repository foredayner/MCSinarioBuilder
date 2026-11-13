package com.foredayner.scenario.gametest;

import com.foredayner.scenario.layout.IndoorPlan;
import com.foredayner.scenario.layout.LayoutService;
import com.foredayner.scenario.layout.TownLayout;
import com.foredayner.scenario.sheet.ScenarioSheet;
import com.foredayner.scenario.sheet.SheetService;
import com.foredayner.scenario.store.ScenarioStore;
import com.foredayner.scenario.town.BuildPrompts;
import com.foredayner.scenario.town.BuildService;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.client.gui.screen.world.WorldCreator;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The whole way from a scenario to a built stage with no AI: the sheet, the floor plan and every
 * room's furnishing are written by hand as the AI would answer them ({@code <name>.sheet.json},
 * {@code <name>.plan.json}, {@link HandDesigner}); the builder does the rest as in a real run. Then
 * checks what came out (stairs, doors, the false wall, clue spots, nothing in front of doors) and
 * photographs it. Results in hand/.
 * <p>
 * Runs only when SCN_HAND names the scenario (the "hand" workflow).
 */
public class HandBench implements FabricClientGameTest {

    private static final Path OUT = Path.of("hand");

    private static void write(String file, String text) {
        try {
            Files.createDirectories(OUT);
            Files.writeString(OUT.resolve(file), text, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void runTest(ClientGameTestContext context) {
        String name = System.getenv().getOrDefault("SCN_HAND", "").trim();
        if (name.isEmpty()) {
            return;
        }
        HandDesigner hand = new HandDesigner();
        // Real terrain (a flat world lies just over the bottom of the world: no room for the cellar).
        try (TestSingleplayerContext game = context.worldBuilder()
                .setUseConsistentSettings(false)
                .adjustSettings(creator -> {
                    creator.setWorldType(creator.getNormalWorldTypes().get(0));
                    creator.setSeed("302");
                    creator.setGameMode(WorldCreator.Mode.CREATIVE);
                })
                .create()) {
            game.getClientWorld().waitForChunksRender();
            for (String c : new String[] { "time set noon", "weather clear", "gamerule doDaylightCycle false", "gamerule doWeatherCycle false",
                "gamerule doMobSpawning false", "gamerule sendCommandFeedback false" }) {
                game.getServer().runCommand(c);
            }
            String id = game.getServer().computeOnServer(server -> {
                var player = server.getPlayerManager().getPlayerList().get(0);
                server.getPlayerManager().getOpList().add(new net.minecraft.server.OperatorEntry(player.getGameProfile(), 4, false));
                try {
                    ScenarioStore.Info info = ScenarioStore.save(name, ScenarioGameTests.resource("/scenarios/" + name + ".txt"), false);
                    ScenarioSheet sheet = ScenarioSheet.fromJson(ScenarioGameTests.resource("/scenarios/" + name + ".sheet.json"));
                    List<String> sheetProblems = sheet.normalize();
                    write("sheet_problems.txt", String.join("\n", sheetProblems) + "\n");
                    SheetService.save(info.id, sheet);
                    TownLayout layout = IndoorPlan.fromAnswer(ScenarioGameTests.resource("/scenarios/" + name + ".plan.json"), sheet);
                    List<String> repaired = IndoorPlan.repair(layout, sheet);
                    List<String> problems = layout.check(sheet);
                    write("plan_check.txt", "repaired: " + repaired + "\nproblems: " + problems + "\n");
                    LayoutService.save(info.id, layout);
                    write("layout.json", layout.toJson());
                    com.example.config.AiBuilderConfig.get().ticksPerBlock = 1;
                    BuildService.designer = hand;
                    BuildService.columnsPerTick = 1500;
                    BuildService.here(player, info.id);
                    BuildService.start(player, info.id);
                    return info.id;
                } catch (java.io.IOException e) {
                    throw new RuntimeException(e);
                }
            });
            for (int i = 0; i < 20 * 60; i++) {
                context.waitTicks(20);
                if (game.getServer().computeOnServer(server -> BuildService.idle(id))) {
                    break;
                }
            }
            context.waitTicks(40);
            List<String> checks = new ArrayList<>();
            int[] origin = game.getServer().computeOnServer(server -> {
                try {
                    var state = BuildService.load(id);
                    write("build.json", state.toJson());
                    var f = BuildService.cluesFile(id);
                    write("clues.json", Files.exists(f) ? Files.readString(f, StandardCharsets.UTF_8) : "no clues file");
                    StringBuilder sc = new StringBuilder();
                    hand.scripts.forEach((room, script) -> sc.append("== ").append(room).append('\n').append(script).append('\n'));
                    write("scripts.txt", sc.toString());
                    checks.addAll(check(server.getOverworld(), id, state));
                    write("checks.txt", String.join("\n", checks) + "\n");
                    return new int[] { state.originX, state.originY, state.originZ };
                } catch (java.io.IOException e) {
                    throw new RuntimeException(e);
                }
            });
            TownLayout layout = game.getServer().computeOnServer(server -> {
                try {
                    return LayoutService.load(id);
                } catch (java.io.IOException e) {
                    throw new RuntimeException(e);
                }
            });
            photos(context, game, layout, origin);
            clueShots(context, game);
            game.getServer().runCommand("gamemode creative @a");
            long failed = checks.stream().filter(c -> c.startsWith("FAIL")).count();
            if (failed > 0) {
                throw new AssertionError(failed + " check(s) failed:\n" + String.join("\n", checks));
            }
        }
    }

    // ------------------------------------------------------------------ checks

    private static int floorY(int originY, int level) {
        return originY - 1 + level * IndoorPlan.FLOOR_H;
    }

    private static int[] step(String dir) {
        return switch (dir == null ? "" : dir) {
            case "east" -> new int[] { 1, 0 };
            case "south" -> new int[] { 0, 1 };
            case "west" -> new int[] { -1, 0 };
            default -> new int[] { 0, -1 };
        };
    }

    private static List<String> check(ServerWorld world, String id, com.foredayner.scenario.town.BuildState state) throws java.io.IOException {
        List<String> out = new ArrayList<>();
        TownLayout l = LayoutService.load(id);
        ScenarioSheet sheet = SheetService.load(id);
        int ox = state.originX, oy = state.originY, oz = state.originZ;
        java.util.function.BiConsumer<Boolean, String> ok = (pass, what) -> out.add((pass ? "ok   " : "FAIL ") + what);
        ok.accept("done".equals(state.status), "the build finished (" + state.status + ")");
        for (ScenarioSheet.Place p : sheet.places) {
            var bp = state.places.get(p.id);
            ok.accept(bp != null && "done".equals(bp.state), "room " + p.id + " built" + (bp == null ? "" : " (" + bp.state + " " + bp.error + ")"));
        }
        // Every flight: four steps, and the hole over the upper three.
        for (TownLayout.Stair st : l.stairs) {
            int fy = floorY(oy, st.level);
            int[] s = step(st.dir);
            boolean steps = true, hole = true;
            for (int i = 0; i < IndoorPlan.STAIR_RUN; i++) {
                BlockPos at = new BlockPos(ox + st.x + s[0] * i, fy + 1 + i, oz + st.z + s[1] * i);
                steps &= world.getBlockState(at).getBlock() instanceof StairsBlock;
                if (i >= 1) {
                    for (int y = fy + IndoorPlan.FLOOR_H; y <= fy + IndoorPlan.FLOOR_H + 1; y++) {
                        BlockPos over = new BlockPos(at.getX(), y, at.getZ());
                        hole &= !world.getBlockState(over).isSolidBlock(world, over);
                    }
                }
            }
            ok.accept(steps, String.format(Locale.ROOT, "stairs level %d at (%d,%d) %s: all four steps", st.level, st.x, st.z, st.dir));
            ok.accept(hole, String.format(Locale.ROOT, "stairs level %d: the way up is open", st.level));
            // The landing: two blocks of air to step off into.
            BlockPos land = new BlockPos(ox + st.x + s[0] * IndoorPlan.STAIR_RUN, fy + IndoorPlan.FLOOR_H + 1, oz + st.z + s[1] * IndoorPlan.STAIR_RUN);
            ok.accept(!world.getBlockState(land).isSolidBlock(world, land) && world.getBlockState(land.up()).isAir(),
                String.format(Locale.ROOT, "stairs level %d: the landing is clear (%s)", st.level, world.getBlockState(land).getBlock()));
        }
        // Doors: real doors, the secret one a false wall; nothing in front of either side.
        for (TownLayout.Door d : l.doors) {
            int fy = floorY(oy, d.level);
            BlockPos at = new BlockPos(ox + d.x, fy + 1, oz + d.z);
            var block = world.getBlockState(at).getBlock();
            if (d.secret) {
                ok.accept(!(block instanceof DoorBlock) && world.getBlockState(at).isSolidBlock(world, at)
                        && world.getBlockState(at.up()).isSolidBlock(world, at.up()),
                    String.format(Locale.ROOT, "secret door level %d at (%d,%d): a false wall of %s", d.level, d.x, d.z, block));
            } else {
                ok.accept(block instanceof DoorBlock, String.format(Locale.ROOT, "door level %d at (%d,%d)%s: %s", d.level, d.x, d.z,
                    d.entrance ? " (entrance)" : "", block));
            }
            for (int[] o : new int[][] { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } }) {
                int x = d.x + o[0], z = d.z + o[1];
                boolean inside = false;
                for (TownLayout.Site s : l.sites) {
                    if (s.level == d.level && x > s.x && x < s.x + s.w - 1 && z > s.z && z < s.z + s.d - 1) {
                        inside = true;
                    }
                }
                if (!inside) {
                    continue;
                }
                BlockPos front = new BlockPos(ox + x, fy + 1, oz + z);
                boolean clear = !world.getBlockState(front).isSolidBlock(world, front) && !world.getBlockState(front.up()).isSolidBlock(world, front.up());
                ok.accept(clear, String.format(Locale.ROOT, "  in front of it at (%d,%d): clear (%s / %s)", x, z,
                    world.getBlockState(front).getBlock(), world.getBlockState(front.up()).getBlock()));
            }
        }
        // Every clue has its spot, in the air of its room.
        for (ScenarioSheet.Clue c : sheet.clues) {
            var bp = state.places.get(c.place);
            var spots = bp == null ? null : bp.marks.get(BuildPrompts.clueMark(c));
            boolean has = spots != null && !spots.isEmpty();
            String where = has ? String.format(Locale.ROOT, " at %d,%d,%d (%s)", spots.get(0)[0], spots.get(0)[1], spots.get(0)[2],
                world.getBlockState(new BlockPos(spots.get(0)[0], spots.get(0)[1], spots.get(0)[2])).getBlock()) : "";
            ok.accept(has, "clue " + c.id + " (" + c.kind() + ") has its spot" + where);
            if (has) {
                BlockPos p = new BlockPos(spots.get(0)[0], spots.get(0)[1], spots.get(0)[2]);
                ok.accept(!world.getBlockState(p).isSolidBlock(world, p), "  clue " + c.id + " is not inside a solid block");
                TownLayout.Site s = l.site(c.place);
                int fy = floorY(oy, s.level);
                ok.accept(p.getX() > ox + s.x && p.getX() < ox + s.x + s.w - 1 && p.getZ() > oz + s.z && p.getZ() < oz + s.z + s.d - 1
                    && p.getY() > fy && p.getY() < fy + IndoorPlan.FLOOR_H, "  clue " + c.id + " lies inside its room");
            }
        }
        // The clues file Foundry reads: every clue, with its kind and checks.
        var f = BuildService.cluesFile(id);
        if (Files.exists(f)) {
            var json = com.google.gson.JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
            int n = json.getAsJsonArray("clues").size();
            ok.accept(n == sheet.clues.size(), "clues file has " + n + " of " + sheet.clues.size() + " clues");
        } else {
            ok.accept(false, "clues file written");
        }
        return out;
    }

    // ------------------------------------------------------------------ pictures

    private static void photos(ClientGameTestContext context, TestSingleplayerContext game, TownLayout l, int[] o) {
        game.getServer().runCommand("gamemode spectator @a");
        context.runOnClient(client -> client.options.hudHidden = true);
        int[] b = IndoorPlan.bounds(l);
        double cx = o[0] + (b[0] + b[2]) / 2.0, cz = o[2] + (b[1] + b[3]) / 2.0;
        int top = 4 * IndoorPlan.FLOOR_H;
        // Outside: from the south-west (the entrance side) and the south-east.
        look(context, game, o[0] + b[0] - 14, o[1] + 8, o[2] + b[3] + 18, cx, o[1] + top * 0.4, cz);
        context.takeScreenshot("hand_outside_sw");
        look(context, game, o[0] + b[2] + 16, o[1] + 14, o[2] + b[3] + 16, cx, o[1] + top * 0.4, cz);
        context.takeScreenshot("hand_outside_se");
        look(context, game, cx, o[1] + 45, cz + 30, cx, o[1], cz);
        context.takeScreenshot("hand_above");
        // Each furnished room, from up under its ceiling, looking down across it (both ways).
        for (TownLayout.Site s : l.sites) {
            if (s.id.startsWith("_")) {
                continue;
            }
            int fy = floorY(o[1], s.level);
            double x1 = o[0] + s.x + 1.5, z1 = o[2] + s.z + 1.5, x2 = o[0] + s.x + s.w - 1.5, z2 = o[2] + s.z + s.d - 1.5;
            double eye = fy + 3.2; // feet; the eyes are just under the ceiling
            look(context, game, x1, eye, z1, x2, fy + 1, z2);
            context.takeScreenshot("hand_room_" + s.id + "_a");
            look(context, game, x2, eye, z2, x1, fy + 1, z1);
            context.takeScreenshot("hand_room_" + s.id + "_b");
        }
        // Each flight from its foot, looking up it.
        for (TownLayout.Stair st : l.stairs) {
            int fy = floorY(o[1], st.level);
            int[] s = step(st.dir);
            double fx = o[0] + st.x + 0.5 - s[0] * 2, fz = o[2] + st.z + 0.5 - s[1] * 2;
            look(context, game, fx, fy + 1, fz, fx + s[0] * 6, fy + 1 + IndoorPlan.FLOOR_H, fz + s[1] * 6);
            context.takeScreenshot("hand_stairs_" + (st.level < 0 ? "b" + (-st.level) : String.valueOf(st.level)));
        }
        context.runOnClient(client -> client.options.hudHidden = false);
    }

    private static void clueShots(ClientGameTestContext context, TestSingleplayerContext game) {
        List<int[]> spots = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        try {
            String json = Files.readString(OUT.resolve("clues.json"), StandardCharsets.UTF_8);
            for (var el : com.google.gson.JsonParser.parseString(json).getAsJsonObject().getAsJsonArray("clues")) {
                var c = el.getAsJsonObject();
                spots.add(new int[] { c.get("x").getAsInt(), c.get("y").getAsInt(), c.get("z").getAsInt() });
                ids.add(c.get("id").getAsString());
            }
        } catch (Exception e) {
            return;
        }
        game.getServer().runCommand("gamemode spectator @a");
        context.runOnClient(client -> client.options.hudHidden = true);
        for (int k = 0; k < spots.size(); k++) {
            int[] c = spots.get(k);
            // From the room's middle side: a step back and up, looking at the spot.
            look(context, game, c[0] + 0.5 + 1.6, c[1] + 0.6, c[2] + 0.5 + 1.6, c[0] + 0.5, c[1] + 0.3, c[2] + 0.5);
            context.takeScreenshot("hand_clue_" + ids.get(k));
        }
        context.runOnClient(client -> client.options.hudHidden = false);
    }

    /** Stand at one point (feet) and look at another. */
    private static void look(ClientGameTestContext context, TestSingleplayerContext game, double x, double y, double z,
                             double tx, double ty, double tz) {
        double dx = tx - x, dy = ty - (y + 1.62), dz = tz - z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
        game.getServer().runCommand(String.format(Locale.ROOT, "tp @a %.2f %.2f %.2f", x, y, z));
        context.waitTicks(10);
        context.runOnClient(client -> {
            client.player.setYaw(yaw);
            client.player.setPitch(pitch);
        });
        context.waitTicks(25);
        try {
            game.getClientWorld().waitForChunksRender();
        } catch (AssertionError slow) {
            context.waitTicks(60);
        }
    }
}

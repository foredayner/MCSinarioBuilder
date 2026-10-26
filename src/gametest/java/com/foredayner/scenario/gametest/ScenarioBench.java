package com.foredayner.scenario.gametest;

import com.example.api.AiBuildApi;
import com.example.config.AiBuilderConfig;
import com.example.config.AiProvider;
import com.foredayner.scenario.layout.LayoutService;
import com.foredayner.scenario.layout.TownLayout;
import com.foredayner.scenario.sheet.ScenarioSheet;
import com.foredayner.scenario.sheet.SheetService;
import com.foredayner.scenario.store.ScenarioStore;
import com.foredayner.scenario.town.BuildService;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screen.world.WorldCreator;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * Real AI on the test scenario: the AI writes the town's architecture guide, then designs the
 * chosen places (SCN_BENCH_PLACES, default church, mansion and general store), which are built on
 * the town's ground. What each building was asked, the script it came back as and the result are
 * written to bench/, and each building is photographed from the street and inside.
 * <p>
 * Runs only when ANTHROPIC_API_KEY is set (the "bench" workflow); normal CI skips it.
 */
public class ScenarioBench implements FabricClientGameTest {

    private static final Path OUT = Path.of("bench");

    /** Records what each design was asked and what came back, then hands it on to the AI. */
    private static final class Recorder implements BuildService.Designer {
        @Override
        public CompletableFuture<AiBuildApi.Design> building(ServerWorld world, BlockPos near, AiBuildApi.Site site, AiBuildApi.BuildingRequest req) {
            String name = slugOf(req.description());
            write(name + ".request.txt", "DESCRIPTION:\n" + req.description() + "\n\nSITE: " + site + "\n\nSTYLE:\n" + req.style()
                + "\n\nROOMS:\n- " + String.join("\n- ", req.rooms()) + "\n\nMARKS:\n- " + String.join("\n- ", req.marks())
                + "\n\nEXTRA:\n" + req.extra() + "\n");
            long start = System.currentTimeMillis();
            return BuildService.AI.building(world, near, site, req).thenApply(d -> {
                write(name + ".result.txt", String.format(Locale.ROOT, "ok=%s calls=%d tokens in=%d out=%d seconds=%d%nerror=%s%n",
                    d.ok(), d.calls(), d.inputTokens(), d.outputTokens(), (System.currentTimeMillis() - start) / 1000, d.error()));
                write(name + ".script.groovy", d.script() == null ? "" : d.script());
                return d;
            });
        }

        @Override
        public CompletableFuture<AiBuildApi.Design> object(ServerWorld world, BlockPos near, String description, double maxSize, String model) {
            return BuildService.AI.object(world, near, description, maxSize, model);
        }
    }

    private static String slugOf(String description) {
        int q = description.indexOf('"');
        int q2 = q < 0 ? -1 : description.indexOf('"', q + 1);
        String kind = description.split(" ")[0];
        String name = q2 > q ? description.substring(q + 1, q2) : "";
        return (kind + "_" + name).replaceAll("[^0-9A-Za-z가-힣_]+", "_");
    }

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
        String key = System.getenv("ANTHROPIC_API_KEY");
        if (key == null || key.isBlank()) {
            return; // not a bench run
        }
        String model = System.getenv().getOrDefault("SCN_BENCH_MODEL", "claude-haiku-5-5").trim();
        String sheetName = System.getenv().getOrDefault("SCN_BENCH_SHEET", "greymouth").trim();
        List<String> wanted = new ArrayList<>();
        for (String p : System.getenv().getOrDefault("SCN_BENCH_PLACES", "").split(",")) {
            if (!p.isBlank()) {
                wanted.add(p.trim());
            }
        }
        AiBuilderConfig config = AiBuilderConfig.get();
        config.provider = AiProvider.CLAUDE;
        config.apiUrl = "https://api.anthropic.com";
        config.apiKey = key.trim();
        config.modelName = model;
        com.foredayner.scenario.store.ScenarioConfig.get().buildingModel = model;
        config.openAiWebSearchEnabled = false;
        config.autoRetries = 2;
        config.ticksPerBlock = 1;
        // End to end (SCN_BENCH_E2E = a scenario text in the test resources): the AI reads the scenario
        // into a sheet, lays out the town, writes the guide and builds it; nothing is hand-made.
        String e2e = System.getenv().getOrDefault("SCN_BENCH_E2E", "").trim();
        boolean endToEnd = !e2e.isEmpty();

        // Otherwise: the test town with only the chosen places, and no guide yet: the AI writes it.
        ScenarioSheet sheet = endToEnd ? null : ScenarioSheet.fromJson(ScenarioGameTests.resource("/scenarios/" + sheetName + ".sheet.json"));
        TownLayout layout = null;
        if (!endToEnd) {
            sheet.normalize();
            sheet.styleGuide = "";
            if (wanted.isEmpty()) {
                sheet.places.forEach(p -> wanted.add(p.id)); // every place of the sheet
            }
            sheet.places.removeIf(p -> !wanted.contains(p.id));
            sheet.npcs.removeIf(n -> !wanted.contains(n.place));
            sheet.clues.removeIf(c -> !wanted.contains(c.place));
            sheet.links.removeIf(l -> !wanted.contains(l.from) || !wanted.contains(l.to));
            layout = TownLayout.auto(sheet);
            write("layout.json", layout.toJson());
        }
        TownLayout madeLayout = layout;

        try (TestSingleplayerContext game = context.worldBuilder()
                .adjustSettings(creator -> creator.setGameMode(WorldCreator.Mode.CREATIVE))
                .create()) {
            game.getClientWorld().waitForChunksRender();
            game.getServer().runCommand("time set noon");
            game.getServer().runCommand("weather clear");
            game.getServer().runCommand("gamerule doDaylightCycle false");
            game.getServer().runCommand("gamerule doWeatherCycle false");
            game.getServer().runCommand("gamerule doMobSpawning false");
            game.getServer().runCommand("gamerule sendCommandFeedback false");
            String id = game.getServer().computeOnServer(server -> {
                var player = server.getPlayerManager().getPlayerList().get(0);
                server.getPlayerManager().getOpList().add(new net.minecraft.server.OperatorEntry(player.getGameProfile(), 4, false));
                try {
                    BuildService.designer = new Recorder();
                    BuildService.columnsPerTick = 1500;
                    if (endToEnd) {
                        ScenarioStore.Info info = ScenarioStore.save("e2e", ScenarioGameTests.resource("/scenarios/" + e2e + ".txt"), false);
                        BuildService.here(player, info.id);
                        com.foredayner.scenario.sheet.SheetService.analyze(player, info.id);
                        return info.id;
                    }
                    ScenarioStore.Info info = ScenarioStore.save("bench", ScenarioGameTests.testScenario(), false);
                    SheetService.save(info.id, sheet);
                    LayoutService.save(info.id, madeLayout);
                    BuildService.here(player, info.id);
                    // A real photo for one place (SCN_BENCH_REF = "place=path"): the AI reads it before building.
                    String ref = System.getenv().getOrDefault("SCN_BENCH_REF", "");
                    int eq = ref.indexOf('=');
                    Path photo = eq > 0 ? Path.of(ref.substring(eq + 1)) : null;
                    if (photo != null && Files.isRegularFile(photo) && sheet.place(ref.substring(0, eq)) != null) {
                        String placeId = ref.substring(0, eq);
                        String saved = com.foredayner.scenario.town.RefService.saveFile(info.id, placeId, Files.readAllBytes(photo));
                        com.foredayner.scenario.town.RefService.set(server, player, info.id, placeId, saved, ok -> {
                            try {
                                var read = SheetService.load(info.id);
                                write("ref_" + placeId + ".txt", ok ? read.place(placeId).refDescription : "the photo could not be read");
                            } catch (java.io.IOException ignored) {
                            }
                            BuildService.start(player, info.id);
                        });
                    } else {
                        if (!ref.isBlank()) {
                            write("ref_missing.txt", "no photo at " + ref);
                        }
                        BuildService.start(player, info.id);
                    }
                    return info.id;
                } catch (java.io.IOException e) {
                    throw new RuntimeException(e);
                }
            });
            if (endToEnd) {
                StringBuilder log = new StringBuilder();
                long t0 = System.currentTimeMillis();
                boolean sheetOk = waitUntil(context, game, 15 * 60, () -> SheetService.load(id) != null);
                stage(log, "sheet", t0, sheetOk, context);
                if (sheetOk) {
                    game.getServer().runOnServer(server -> {
                        try {
                            write("e2e_sheet.json", SheetService.load(id).toJson());
                        } catch (java.io.IOException e) {
                            write("e2e_sheet.json", e.toString());
                        }
                        LayoutService.make(server.getPlayerManager().getPlayerList().get(0), id);
                    });
                    long t1 = System.currentTimeMillis();
                    boolean layoutOk = waitUntil(context, game, 15 * 60, () -> LayoutService.load(id) != null);
                    stage(log, "layout", t1, layoutOk, context);
                    if (layoutOk) {
                        game.getServer().runOnServer(server -> {
                            try {
                                write("layout.json", LayoutService.load(id).toJson());
                            } catch (java.io.IOException e) {
                                write("layout.json", e.toString());
                            }
                            BuildService.start(server.getPlayerManager().getPlayerList().get(0), id);
                        });
                    }
                }
                write("e2e_stages.txt", log.toString());
            }
            // Up to 40 minutes (100 end to end) for the guide and the buildings.
            for (int i = 0; i < (endToEnd ? 100 : 40) * 60; i++) {
                context.waitTicks(20);
                if (i == 5 * 60 && game.getServer().computeOnServer(server -> {
                    try {
                        var guide = SheetService.load(id);
                        return guide == null || guide.styleGuide.isBlank();
                    } catch (java.io.IOException e) {
                        return true;
                    }
                })) {
                    write("FAILED.txt", "no architecture guide after 5 minutes (see the client log)");
                    break;
                }
                boolean done = game.getServer().computeOnServer(server -> {
                    try {
                        var s = BuildService.current(id);
                        var guide = SheetService.load(id);
                        return BuildService.idle(id) && guide != null && !guide.styleGuide.isBlank() && !"waiting".equals(s.ground);
                    } catch (java.io.IOException e) {
                        return false;
                    }
                });
                if (done) {
                    break;
                }
            }
            int[] origin = game.getServer().computeOnServer(server -> {
                try {
                    var s = BuildService.load(id);
                    write("build.json", s.toJson());
                    var guide = SheetService.load(id);
                    write("style_guide.txt", guide == null ? "" : guide.styleGuide);
                    return new int[] { s.originX, s.originY, s.originZ };
                } catch (java.io.IOException e) {
                    throw new RuntimeException(e);
                }
            });

            TownLayout finalLayout = game.getServer().computeOnServer(server -> {
                try {
                    TownLayout l = LayoutService.load(id);
                    return l == null ? new TownLayout() : l;
                } catch (java.io.IOException e) {
                    return new TownLayout();
                }
            });
            if (endToEnd) {
                write("e2e_report.txt", game.getServer().computeOnServer(server -> report(id)));
            }
            game.getServer().runCommand("gamemode spectator @a");
            context.runOnClient(client -> client.options.hudHidden = true);
            // Built places and how tall each came out (blocks above the ground).
            java.util.Map<String, Integer> built = game.getServer().computeOnServer(server -> {
                java.util.Map<String, Integer> ok = new java.util.HashMap<>();
                try {
                    var state = BuildService.load(id);
                    state.places.forEach((k, v) -> {
                        if ("done".equals(v.state)) {
                            ok.put(k, v.max == null ? 8 : Math.max(4, v.max[1] - state.originY));
                        }
                    });
                } catch (java.io.IOException ignored) {
                }
                return ok;
            });
            for (TownLayout.Site s : finalLayout.sites) {
                if (s.outdoor || !built.containsKey(s.id)) {
                    continue;
                }
                // Tall buildings (towers, lighthouses) are framed by their height, not their footprint.
                int h = Math.min(built.get(s.id), 40);
                double cx = origin[0] + s.x + s.w / 2.0, cz = origin[2] + s.z + s.d / 2.0;
                int[] f = TownLayout.frontPoint(s);
                double fx = origin[0] + f[0] + 0.5, fz = origin[2] + f[1] + 0.5;
                double ox = fx - cx, oz = fz - cz;
                double len = Math.max(1e-6, Math.hypot(ox, oz));
                ox /= len;
                oz /= len;
                // From the street (on this side of it, so the row across does not block the view).
                double dist = Math.max(7, h * 0.6);
                look(context, game, fx + ox * dist, origin[1] + 2.6 + s.w * 0.12, fz + oz * dist, cx, origin[1] + Math.min(s.w * 0.25, h * 0.4), cz);
                context.takeScreenshot("bench_" + s.id + "_outside");
                // From an angle.
                double far = Math.max(dist, h * 0.9);
                look(context, game, fx + ox * far * 0.8 + oz * far * 0.6, origin[1] + 1 + Math.max(s.w * 0.5, h * 0.55),
                    fz + oz * far * 0.8 - ox * far * 0.6, cx, origin[1] + h * 0.4, cz);
                context.takeScreenshot("bench_" + s.id + "_corner");
                // Inside: the open spot (floor with 2 blocks of air) nearest the middle, looking toward the
                // door, then toward the back.
                int sx0 = origin[0] + s.x, sz0 = origin[2] + s.z, y0 = origin[1];
                double[] spot = game.getServer().computeOnServer(server -> {
                    var world = server.getOverworld();
                    double best = Double.MAX_VALUE;
                    double[] at = { cx, y0 + 1, cz };
                    for (int x = sx0 + 1; x < sx0 + s.w - 1; x++) {
                        for (int z = sz0 + 1; z < sz0 + s.d - 1; z++) {
                            for (int y = y0; y <= y0 + 1; y++) {
                                BlockPos p = new BlockPos(x, y, z);
                                if (!world.getBlockState(p.down()).isAir() && world.getBlockState(p).isAir() && world.getBlockState(p.up()).isAir()
                                    && !world.isSkyVisible(p)) {
                                    double d = Math.hypot(x + 0.5 - cx, z + 0.5 - cz);
                                    if (d < best) {
                                        best = d;
                                        at = new double[] { x + 0.5, y, z + 0.5 };
                                    }
                                }
                            }
                        }
                    }
                    return at;
                });
                look(context, game, spot[0], spot[1] + 0.1, spot[2], fx, spot[1] + 1.4, fz);
                context.takeScreenshot("bench_" + s.id + "_inside");
                look(context, game, spot[0], spot[1] + 0.1, spot[2], spot[0] - ox * 10, spot[1] + 1.4, spot[2] - oz * 10);
                context.takeScreenshot("bench_" + s.id + "_inside_back");
            }
            // Along the main street, at eye height.
            if (!finalLayout.roads.isEmpty() && finalLayout.roads.get(0).points.size() >= 2) {
                var main = finalLayout.roads.get(0).points;
                int[] a = main.get(0), b = main.get(main.size() - 1);
                double sx = origin[0] + a[0] + (b[0] - a[0]) * 0.15, sz = origin[2] + a[1] + (b[1] - a[1]) * 0.15;
                look(context, game, sx, origin[1] + 1.7, sz + 2, origin[0] + b[0], origin[1] + 1.2, origin[2] + b[1] + 2);
                context.takeScreenshot("bench_street");
            }
            double mx = origin[0] + 80, mz = origin[2] + 80;
            look(context, game, mx, origin[1] + 70, mz + 90, mx, origin[1], mz);
            context.takeScreenshot("bench_town");
            context.runOnClient(client -> client.options.hudHidden = false);
            game.getServer().runCommand("gamemode creative @a");
            if (endToEnd) {
                // The world itself, to open in single player (blocks only; no mod needed to look at it).
                game.getServer().runCommand("tp @a " + (origin[0] + 80) + " " + (origin[1] + 2) + " " + (origin[2] + 80));
                game.getServer().runCommand("save-all flush");
                context.waitTicks(40);
                game.getServer().runOnServer(server -> copyWorld(server.getSavePath(net.minecraft.util.WorldSavePath.ROOT), OUT.resolve("world")));
            }
        }
    }

    private interface Check {
        boolean ok() throws java.io.IOException;
    }

    /** Poll on the server until the check holds or the time (seconds) is up. */
    private static boolean waitUntil(ClientGameTestContext context, TestSingleplayerContext game, int seconds, Check check) {
        for (int i = 0; i < seconds; i++) {
            context.waitTicks(20);
            if (game.getServer().computeOnServer(server -> {
                try {
                    return check.ok();
                } catch (java.io.IOException e) {
                    return false;
                }
            })) {
                return true;
            }
        }
        return false;
    }

    private static void stage(StringBuilder log, String name, long since, boolean ok, ClientGameTestContext context) {
        String message = context.computeOnClient(client -> com.foredayner.scenario.client.ScenarioClientState.message());
        log.append(String.format(Locale.ROOT, "%s: %s in %d s - %s%n", name, ok ? "done" : "FAILED", (System.currentTimeMillis() - since) / 1000, message));
    }

    /** What the end-to-end run made: every place's state, and whether each clue has its spot. */
    private static String report(String id) {
        StringBuilder sb = new StringBuilder();
        try {
            ScenarioSheet sheet = SheetService.load(id);
            var state = BuildService.load(id);
            int built = 0;
            for (ScenarioSheet.Place p : sheet.places) {
                var bp = state.places.get(p.id);
                String st = bp == null ? "missing" : bp.state;
                built += "done".equals(st) ? 1 : 0;
                sb.append(String.format(Locale.ROOT, "place %s (%s, %s%s): %s %s%n", p.id, p.kind, p.size, p.outdoor ? ", outdoor" : "", st,
                    bp == null ? "" : bp.error));
            }
            int marked = 0, clues = 0;
            for (ScenarioSheet.Clue c : sheet.clues) {
                ScenarioSheet.Place p = sheet.place(c.place);
                if (p == null || p.outdoor) {
                    continue;
                }
                clues++;
                var bp = state.places.get(c.place);
                boolean has = bp != null && bp.marks.containsKey(com.foredayner.scenario.town.BuildPrompts.clueMark(c));
                marked += has ? 1 : 0;
                sb.append(String.format(Locale.ROOT, "clue %s in %s: %s%n", c.id, c.place, has ? "spot marked" : "NO SPOT"));
            }
            sb.insert(0, String.format(Locale.ROOT, "places built %d/%d, clue spots %d/%d, tokens in %d out %d%n%n", built, sheet.places.size(),
                marked, clues, state.tokensIn, state.tokensOut));
        } catch (java.io.IOException | RuntimeException e) {
            sb.append("report failed: ").append(e);
        }
        return sb.toString();
    }

    /** Copy a world folder (without its lock file). */
    private static void copyWorld(Path world, Path to) {
        Path from = world.toAbsolutePath().normalize();
        try (var walk = Files.walk(from)) {
            for (Path p : (Iterable<Path>) walk::iterator) {
                Path rel = from.relativize(p);
                if (rel.toString().equals("session.lock")) {
                    continue;
                }
                Path target = to.resolve(rel.toString());
                if (Files.isDirectory(p)) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(p, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (java.io.IOException e) {
            write("world_copy_failed.txt", e.toString());
        }
    }

    /** Stand at one point and look at another. */
    private static void look(ClientGameTestContext context, TestSingleplayerContext game, double x, double y, double z,
                             double tx, double ty, double tz) {
        double dx = tx - x, dy = ty - y, dz = tz - z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
        game.getServer().runCommand(String.format(Locale.ROOT, "tp @a %.2f %.2f %.2f", x, y, z));
        context.waitTicks(10);
        context.runOnClient(client -> {
            client.player.setYaw(yaw);
            client.player.setPitch(pitch);
        });
        context.waitTicks(30);
        try {
            game.getClientWorld().waitForChunksRender();
        } catch (AssertionError slow) {
            context.waitTicks(100); // take the picture anyway
        }
    }
}

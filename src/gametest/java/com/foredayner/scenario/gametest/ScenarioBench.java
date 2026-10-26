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
        String model = System.getenv().getOrDefault("SCN_BENCH_MODEL", "claude-sonnet-5-5").trim();
        List<String> wanted = new ArrayList<>();
        for (String p : System.getenv().getOrDefault("SCN_BENCH_PLACES", "church,mansion,general_store").split(",")) {
            if (!p.isBlank()) {
                wanted.add(p.trim());
            }
        }
        AiBuilderConfig config = AiBuilderConfig.get();
        config.provider = AiProvider.CLAUDE;
        config.apiUrl = "https://api.anthropic.com";
        config.apiKey = key.trim();
        config.modelName = model;
        config.openAiWebSearchEnabled = false;
        config.autoRetries = 2;
        config.ticksPerBlock = 1;

        // The test town with only the chosen places, and no guide yet: the AI writes it.
        ScenarioSheet sheet = ScenarioSheet.fromJson(ScenarioGameTests.testSheet());
        sheet.normalize();
        sheet.styleGuide = "";
        sheet.places.removeIf(p -> !wanted.contains(p.id));
        sheet.npcs.removeIf(n -> !wanted.contains(n.place));
        sheet.clues.removeIf(c -> !wanted.contains(c.place));
        sheet.links.removeIf(l -> !wanted.contains(l.from) || !wanted.contains(l.to));
        TownLayout layout = TownLayout.auto(sheet);
        write("layout.json", layout.toJson());

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
                    ScenarioStore.Info info = ScenarioStore.save("bench", ScenarioGameTests.testScenario(), false);
                    SheetService.save(info.id, sheet);
                    LayoutService.save(info.id, layout);
                    BuildService.designer = new Recorder();
                    BuildService.columnsPerTick = 1500;
                    BuildService.here(player, info.id);
                    BuildService.start(player, info.id);
                    return info.id;
                } catch (java.io.IOException e) {
                    throw new RuntimeException(e);
                }
            });
            // Up to 40 minutes for the guide and the buildings.
            for (int i = 0; i < 40 * 60; i++) {
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

            game.getServer().runCommand("gamemode spectator @a");
            context.runOnClient(client -> client.options.hudHidden = true);
            for (TownLayout.Site s : layout.sites) {
                if (s.outdoor) {
                    continue;
                }
                double cx = origin[0] + s.x + s.w / 2.0, cz = origin[2] + s.z + s.d / 2.0;
                int[] f = TownLayout.frontPoint(s);
                double fx = origin[0] + f[0] + 0.5, fz = origin[2] + f[1] + 0.5;
                double ox = fx - cx, oz = fz - cz;
                double len = Math.max(1e-6, Math.hypot(ox, oz));
                ox /= len;
                oz /= len;
                // From the street: back from the front, raised, looking at the middle of the building.
                double dist = s.w * 0.95 + 6;
                look(context, game, fx + ox * dist, origin[1] + 1 + s.w * 0.35, fz + oz * dist, cx, origin[1] + s.w * 0.2, cz);
                context.takeScreenshot("bench_" + s.id + "_outside");
                // From an angle.
                look(context, game, fx + ox * dist * 0.8 + oz * dist * 0.6, origin[1] + 1 + s.w * 0.5, fz + oz * dist * 0.8 - ox * dist * 0.6,
                    cx, origin[1] + s.w * 0.15, cz);
                context.takeScreenshot("bench_" + s.id + "_corner");
                // Inside, just past the door.
                look(context, game, fx - ox * 3, origin[1] + 1.2, fz - oz * 3, cx - ox * 2, origin[1] + 1.2, cz - oz * 2);
                context.takeScreenshot("bench_" + s.id + "_inside");
            }
            double mx = origin[0] + 80, mz = origin[2] + 80;
            look(context, game, mx, origin[1] + 70, mz + 90, mx, origin[1], mz);
            context.takeScreenshot("bench_town");
            context.runOnClient(client -> client.options.hudHidden = false);
            game.getServer().runCommand("gamemode creative @a");
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
        game.getClientWorld().waitForChunksRender();
    }
}

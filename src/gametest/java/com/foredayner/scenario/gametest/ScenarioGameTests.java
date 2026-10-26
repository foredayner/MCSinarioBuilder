package com.foredayner.scenario.gametest;

import com.example.api.AiBuildApi;
import com.foredayner.scenario.ScenarioBuilderMod;
import com.foredayner.scenario.content.ScenarioContent;
import com.foredayner.scenario.input.ScenarioText;
import com.foredayner.scenario.store.ScenarioStore;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.registry.Registries;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Real-server checks of the scenario builder. */
public class ScenarioGameTests implements FabricGameTest {

    static void check(boolean ok, String message) {
        if (!ok) {
            ScenarioBuilderMod.LOG.error("Game test check failed: {}", message);
            throw new GameTestException(message);
        }
    }

    /** The original test scenario shipped with the tests. */
    static String testScenario() {
        try (InputStream in = ScenarioGameTests.class.getResourceAsStream("/scenarios/greymouth.txt")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new GameTestException("test scenario missing: " + e);
        }
    }

    /** The hand-written sheet of the test scenario (what a good AI answer looks like). */
    static String testSheet() {
        try (InputStream in = ScenarioGameTests.class.getResourceAsStream("/scenarios/greymouth.sheet.json")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new GameTestException("test sheet missing: " + e);
        }
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void sheetsAreCheckedAndSaved(TestContext context) throws Exception {
        Path root = Files.createTempDirectory("scenario_sheet_test");
        ScenarioStore.useRoot(root);
        ScenarioStore.Info info = ScenarioStore.save("그레이머스의 등불", testScenario(), false);
        var sheet = com.foredayner.scenario.sheet.ScenarioSheet.fromAnswer("```json\n" + testSheet() + "\n```");
        var problems = sheet.normalize();
        check(problems.isEmpty(), "the test sheet is consistent: " + problems);
        check(sheet.places.size() == 6 && sheet.clues.size() == 5 && sheet.npcs.size() == 5, "everything was read");
        com.foredayner.scenario.sheet.SheetService.save(info.id, sheet);
        var back = com.foredayner.scenario.sheet.SheetService.load(info.id);
        check(back != null && back.place("mansion").basement, "the sheet is saved and read back");
        check("sheet".equals(ScenarioStore.info(info.id).stage), "the scenario moved on to the sheet step");
        ScenarioStore.useRoot(net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("scenario_builder"));
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void layoutsAreCheckedAndSaved(TestContext context) throws Exception {
        Path root = Files.createTempDirectory("scenario_layout_test");
        ScenarioStore.useRoot(root);
        try {
            ScenarioStore.Info info = ScenarioStore.save("그레이머스의 등불", testScenario(), false);
            var sheet = com.foredayner.scenario.sheet.ScenarioSheet.fromJson(testSheet());
            sheet.normalize();
            com.foredayner.scenario.sheet.SheetService.save(info.id, sheet);
            var layout = com.foredayner.scenario.layout.TownLayout.auto(sheet);
            var problems = layout.check(sheet);
            check(problems.isEmpty(), "the automatic layout of the test town passes: " + problems);
            com.foredayner.scenario.layout.LayoutService.save(info.id, layout);
            var back = com.foredayner.scenario.layout.LayoutService.load(info.id);
            check(back != null && back.sites.size() == sheet.places.size(), "the layout is saved and read back");
            check(back.check(sheet).isEmpty(), "and still passes");
            check("layout".equals(ScenarioStore.info(info.id).stage), "the scenario moved on to the layout step");
        } finally {
            ScenarioStore.useRoot(net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("scenario_builder"));
        }
        context.complete();
    }

    /** Run {@code next} once nothing is being built for the scenario. */
    private static void whenIdle(TestContext context, String id, int left, Runnable next) {
        context.waitAndRun(10, () -> {
            if (com.foredayner.scenario.town.BuildService.idle(id)) {
                next.run();
            } else if (left <= 0) {
                throw new GameTestException("the town build did not finish");
            } else {
                whenIdle(context, id, left - 1, next);
            }
        });
    }

    /**
     * The whole town of the test scenario, with a fixed house in place of the AI: the ground is
     * laid (streets, sea, woods), every place is built with its clue and NPC spots, one place is
     * cleared again, and finally the land is put back as it was.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "town", tickLimit = 4000)
    public void buildsTheTestTownClearsAPlaceAndUndoes(TestContext context) throws Exception {
        Path root = Files.createTempDirectory("scenario_build_test");
        ScenarioStore.useRoot(root);
        ScenarioStore.Info info = ScenarioStore.save("그레이머스의 등불", testScenario(), false);
        String id = info.id;
        var sheet = com.foredayner.scenario.sheet.ScenarioSheet.fromJson(testSheet());
        sheet.normalize();
        com.foredayner.scenario.sheet.SheetService.save(id, sheet);
        var layout = com.foredayner.scenario.layout.TownLayout.auto(sheet);
        com.foredayner.scenario.layout.LayoutService.save(id, layout);
        var world = context.getWorld();
        var state = new com.foredayner.scenario.town.BuildState();
        com.foredayner.scenario.town.BuildService.placeAt(state, world, context.getAbsolutePos(net.minecraft.util.math.BlockPos.ORIGIN).add(4000, 0, 0));
        com.foredayner.scenario.town.BuildService.save(id, state);
        var origin = new net.minecraft.util.math.BlockPos(state.originX, state.originY, state.originZ);
        net.minecraft.util.math.BlockPos[] samples = { origin.add(80, -1, 80), origin.add(3, 2, 3), origin.add(150, -1, 150), origin.add(40, 0, 100) };
        net.minecraft.block.BlockState[] before = new net.minecraft.block.BlockState[samples.length];
        for (int i = 0; i < samples.length; i++) {
            before[i] = world.getBlockState(samples[i]);
        }
        com.example.config.AiBuilderConfig.get().ticksPerBlock = 1;
        com.foredayner.scenario.town.BuildService.designer = new FixedDesigner();
        com.foredayner.scenario.town.BuildService.columnsPerTick = 1500;
        com.foredayner.scenario.town.BuildService.startForTests(world.getServer(), id);
        Runnable restoreRoot = () -> {
            com.foredayner.scenario.town.BuildService.designer = com.foredayner.scenario.town.BuildService.AI;
            com.foredayner.scenario.town.BuildService.columnsPerTick = 300;
            ScenarioStore.useRoot(net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("scenario_builder"));
        };
        whenIdle(context, id, 300, () -> {
            try {
                var built = com.foredayner.scenario.town.BuildService.load(id);
                check("done".equals(built.ground), "the ground is laid: " + built.ground);
                check("done".equals(built.status), "the town is done: " + built.status + " " + built.message);
                for (var p : sheet.places) {
                    var bp = built.places.get(p.id);
                    check(bp != null && "done".equals(bp.state), p.id + " is built: " + (bp == null ? "-" : bp.state + " " + bp.error));
                }
                for (var c : sheet.clues) {
                    if (sheet.place(c.place).outdoor) {
                        continue;
                    }
                    var spots = built.places.get(c.place).marks.get(com.foredayner.scenario.town.BuildPrompts.clueMark(c));
                    check(spots != null && !spots.isEmpty(), "clue " + c.id + " has its spot");
                    int[] at = spots.get(0);
                    check(world.getBlockState(new net.minecraft.util.math.BlockPos(at[0], at[1], at[2])).isOf(net.minecraft.block.Blocks.BOOKSHELF),
                        "the clue spot of " + c.id + " is where the house put it");
                }
                // The ground: a street, the sea, the woods.
                var plan = com.foredayner.scenario.town.GroundPlan.of(layout, sheet);
                int[] road = null, sea = null;
                for (int x = 0; x < 160 && (road == null || sea == null); x++) {
                    for (int z = 0; z < 160; z++) {
                        if (road == null && plan.cells[x][z] == com.foredayner.scenario.town.GroundPlan.Cell.ROAD) {
                            road = new int[] { x, z };
                        }
                        if (sea == null && plan.cells[x][z] == com.foredayner.scenario.town.GroundPlan.Cell.WATER) {
                            sea = new int[] { x, z };
                        }
                    }
                }
                var roadTop = world.getBlockState(origin.add(road[0], -1, road[1]));
                check(roadTop.isOf(net.minecraft.block.Blocks.GRAVEL) || roadTop.isOf(net.minecraft.block.Blocks.COBBLESTONE), "streets are gravel: " + roadTop);
                check(world.getBlockState(origin.add(sea[0], -1, sea[1])).isOf(net.minecraft.block.Blocks.WATER), "the sea is water");
                check(world.getBlockState(origin.add(road[0], 3, road[1])).isAir(), "above the street is clear");
                // Clear the inn again.
                var inn = layout.site("inn");
                var roof = world.getBlockState(origin.add(inn.x + inn.w / 2, 6, inn.z + inn.d / 2));
                check(roof.isOf(net.minecraft.block.Blocks.DARK_OAK_PLANKS), "the inn stands (its roof): " + roof);
                com.foredayner.scenario.town.BuildService.clearForTests(world.getServer(), id, "inn");
            } catch (java.io.IOException e) {
                restoreRoot.run();
                throw new GameTestException(e.toString());
            }
            whenIdle(context, id, 100, () -> {
                try {
                    var inn = layout.site("inn");
                    var after = com.foredayner.scenario.town.BuildService.load(id);
                    check("cleared".equals(after.places.get("inn").state), "the inn is cleared");
                    for (int y = 0; y < 8; y++) {
                        check(world.getBlockState(origin.add(inn.x + inn.w / 2, y, inn.z + inn.d / 2)).isAir(), "nothing is left of the inn at y+" + y);
                    }
                    check(world.getBlockState(origin.add(inn.x + inn.w / 2, -1, inn.z + inn.d / 2)).isOf(net.minecraft.block.Blocks.GRASS_BLOCK),
                        "its lot is grass again");
                    check(com.foredayner.scenario.town.BuildService.canUndo(id), "the land before the town was kept");
                } catch (java.io.IOException e) {
                    restoreRoot.run();
                    throw new GameTestException(e.toString());
                }
                com.foredayner.scenario.town.BuildService.undoForTests(world.getServer(), id);
                whenIdle(context, id, 100, () -> {
                    for (int i = 0; i < samples.length; i++) {
                        check(world.getBlockState(samples[i]) == before[i], "the land is back at " + samples[i].toShortString()
                            + ": " + world.getBlockState(samples[i]) + " (was " + before[i] + ")");
                    }
                    restoreRoot.run();
                    context.complete();
                });
            });
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void loadsWithTheAiBuildAssistant(TestContext context) {
        check(AiBuildApi.VERSION >= ScenarioBuilderMod.NEEDS_API, "the AI Build Assistant API is new enough");
        check(Registries.ITEM.containsId(ScenarioContent.GM_HANDBOOK_ID), "the GM handbook item exists");
        check(ScenarioContent.GM_HANDBOOK.getMaxCount() == 1, "one handbook per stack");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void scenariosAreStoredAndListed(TestContext context) throws Exception {
        Path root = Files.createTempDirectory("scenario_store_test");
        ScenarioStore.useRoot(root);
        ScenarioText.Loaded loaded = ScenarioText.fromText("그레이머스의 등불", testScenario());
        ScenarioStore.Info a = ScenarioStore.save(loaded.name(), loaded.text(), loaded.cut());
        ScenarioStore.Info b = ScenarioStore.save(loaded.name(), loaded.text(), loaded.cut());
        check(!a.id.equals(b.id), "the same name twice gets two folders: " + a.id + ", " + b.id);
        check(ScenarioStore.text(a.id).contains("위트모어 저택"), "the text is kept");
        check(ScenarioStore.list().size() == 2, "both are listed");
        ScenarioStore.delete(b.id);
        check(ScenarioStore.list().size() == 1, "one is removed");
        try {
            ScenarioStore.dir("../escape");
            throw new GameTestException("a path outside the store must be refused");
        } catch (java.io.IOException expected) {
            // refused
        }
        ScenarioStore.useRoot(net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("scenario_builder"));
        context.complete();
    }
}

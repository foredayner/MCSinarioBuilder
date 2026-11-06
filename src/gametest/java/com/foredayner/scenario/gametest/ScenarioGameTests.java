package com.foredayner.scenario.gametest;

import com.example.api.AiBuildApi;
import com.foredayner.scenario.ScenarioBuilderMod;
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
        return resource("/scenarios/greymouth.sheet.json");
    }

    static String resource(String path) {
        try (InputStream in = ScenarioGameTests.class.getResourceAsStream(path)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new GameTestException("test resource missing: " + path + " " + e);
        }
    }

    /** A cellar under a tower and a secret tunnel to a shed's landing: dug, lit, with a false wall and a way down. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void cellarsAndTunnelsAreDug(TestContext context) {
        var sheet = new com.foredayner.scenario.sheet.ScenarioSheet();
        var tower = sheet.newPlace("탑", "S", "named", 0, "");
        tower.cellars = 1;
        sheet.places.add(tower);
        sheet.places.add(sheet.newPlace("헛간", "S", "named", 0, ""));
        var link = new com.foredayner.scenario.sheet.ScenarioSheet.Link();
        link.from = "탑";
        link.to = "헛간";
        link.kind = "secret";
        sheet.links.add(link);
        sheet.normalize();
        var layout = new com.foredayner.scenario.layout.TownLayout();
        layout.size = 160;
        int x = 0;
        for (var p : sheet.places) {
            var s = new com.foredayner.scenario.layout.TownLayout.Site();
            s.id = p.id;
            s.w = s.d = com.foredayner.scenario.sheet.ScenarioSheet.footprint(p);
            s.x = x;
            s.z = 0;
            x += s.w + 8;
            layout.sites.add(s);
        }
        var world = context.getWorld();
        // High in the air, so it is deep enough whatever the test world's ground is.
        var origin = context.getAbsolutePos(new net.minecraft.util.math.BlockPos(0, 40, 0));
        var work = new com.foredayner.scenario.town.UndergroundWork(world, origin, layout, sheet);
        for (int i = 0; i < 10000 && !work.done(); i++) {
            work.step(5000);
        }
        check(work.done() && work.notes().isEmpty(), "dug without trouble: " + work.notes());
        work.hatches();
        int y0 = origin.getY();
        var cellars = com.foredayner.scenario.layout.Underground.cellars(layout, sheet);
        var c = cellars.get(0);
        java.util.function.BiFunction<int[], Integer, net.minecraft.block.BlockState> at = (xz, y) ->
            world.getBlockState(new net.minecraft.util.math.BlockPos(origin.getX() + xz[0], y, origin.getZ() + xz[1]));
        int[] mid = c.middle();
        check(at.apply(mid, y0 - 3).isAir(), "the cellar is hollow");
        check(!at.apply(new int[] { c.x1(), mid[1] }, y0 - 3).isAir(), "with walls");
        check(at.apply(mid, y0 - 2).isOf(net.minecraft.block.Blocks.LANTERN) || at.apply(mid, y0 - 2).isOf(net.minecraft.block.Blocks.SOUL_LANTERN),
            "and a light");
        var t = com.foredayner.scenario.layout.Underground.tunnels(layout, sheet).get(0);
        long door = t.doorways().iterator().next();
        var fake = at.apply(new int[] { com.foredayner.scenario.layout.Underground.keyX(door), com.foredayner.scenario.layout.Underground.keyZ(door) },
            y0 - 4);
        check(fake.isOf(net.minecraft.block.Blocks.CRACKED_STONE_BRICKS) || fake.isOf(net.minecraft.block.Blocks.MOSSY_COBBLESTONE)
            || fake.isOf(net.minecraft.block.Blocks.ANDESITE), "a secret tunnel ends in a false wall, got " + fake);
        long inside = t.inside().iterator().next();
        check(at.apply(new int[] { com.foredayner.scenario.layout.Underground.keyX(inside), com.foredayner.scenario.layout.Underground.keyZ(inside) },
            y0 - 4).isAir(), "the tunnel is open inside");
        boolean ladder = false, trapdoor = false;
        for (int lx = c.x1() + 1; lx < c.x2(); lx++) {
            for (int lz = c.z1() + 1; lz < c.z2(); lz++) {
                ladder |= at.apply(new int[] { lx, lz }, y0 - 3).isOf(net.minecraft.block.Blocks.LADDER);
                trapdoor |= at.apply(new int[] { lx, lz }, y0 - 1).isOf(net.minecraft.block.Blocks.SPRUCE_TRAPDOOR);
            }
        }
        check(ladder && trapdoor, "a trapdoor and a ladder lead down");
        context.complete();
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

    /**
     * A building stage: rooms on three floors (a cellar, the ground floor, the floor above) laid out
     * by the plain plan, built with its shell by code (floors, walls, doors, stairs, roof) and each
     * room furnished (a fixed design in place of the AI), then undone.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "building", tickLimit = 4000)
    public void buildsABuildingStageAndUndoes(TestContext context) throws Exception {
        Path root = Files.createTempDirectory("scenario_building_test");
        ScenarioStore.useRoot(root);
        ScenarioStore.Info info = ScenarioStore.save("저택", testScenario(), false);
        String id = info.id;
        var sheet = new com.foredayner.scenario.sheet.ScenarioSheet();
        sheet.title = "저택";
        sheet.stage = "building";
        sheet.period = com.foredayner.scenario.sheet.Period.WEST_MODERN;
        String[][] rooms = { { "hall", "L", "0" }, { "study", "M", "0" }, { "kitchen", "M", "0" }, { "bedroom", "M", "1" },
            { "nursery", "S", "1" }, { "cellar", "M", "-1" } };
        for (String[] r : rooms) {
            var p = new com.foredayner.scenario.sheet.ScenarioSheet.Place();
            p.id = r[0];
            p.name = r[0];
            p.size = r[1];
            p.level = Integer.parseInt(r[2]);
            sheet.places.add(p);
        }
        sheet.styleGuide = "plain";
        check(sheet.normalize().isEmpty(), "the rooms are a sheet");
        com.foredayner.scenario.sheet.SheetService.save(id, sheet);
        var layout = com.foredayner.scenario.layout.IndoorPlan.auto(sheet);
        check(layout.check(sheet).isEmpty(), "the plain plan passes: " + layout.check(sheet));
        com.foredayner.scenario.layout.LayoutService.save(id, layout);
        var world = context.getWorld();
        var state = new com.foredayner.scenario.town.BuildState();
        com.foredayner.scenario.town.BuildService.placeAt(state, world, context.getAbsolutePos(net.minecraft.util.math.BlockPos.ORIGIN).add(-4000, 0, 0));
        com.foredayner.scenario.town.BuildService.save(id, state);
        var origin = new net.minecraft.util.math.BlockPos(state.originX, state.originY, state.originZ);
        var hall = layout.site("_hall0");
        var entrance = layout.doors.stream().filter(d -> d.entrance).findFirst().orElseThrow();
        var stair = layout.stairs.stream().filter(st -> st.level == 0).findFirst().orElseThrow();
        net.minecraft.util.math.BlockPos inHall = origin.add(hall.x + 2, 1, hall.z + 2);
        var before = world.getBlockState(origin.add(hall.x + 2, 5, hall.z + 2));
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
                check("done".equals(built.ground), "the ground and the shell are done: " + built.ground);
                for (var p : sheet.places) {
                    var bp = built.places.get(p.id);
                    check(bp != null && "done".equals(bp.state), p.id + " is furnished: " + (bp == null ? "-" : bp.state + " " + bp.error));
                }
                int h = com.foredayner.scenario.layout.IndoorPlan.FLOOR_H;
                check(world.getBlockState(origin.add(entrance.x, 0, entrance.z)).getBlock() instanceof net.minecraft.block.DoorBlock,
                    "the entrance is a door: " + world.getBlockState(origin.add(entrance.x, 0, entrance.z)));
                check(world.getBlockState(origin.add(stair.x, 0, stair.z)).getBlock() instanceof net.minecraft.block.StairsBlock,
                    "the stairs start in the hall: " + world.getBlockState(origin.add(stair.x, 0, stair.z)));
                check(world.getBlockState(origin.add(hall.x + 2, h - 1, hall.z + 2)).isOf(net.minecraft.block.Blocks.DARK_OAK_PLANKS),
                    "the floor above stands over the hall: " + world.getBlockState(origin.add(hall.x + 2, h - 1, hall.z + 2)));
                check(world.getBlockState(origin.add(hall.x, 0, hall.z + 1)).isOf(net.minecraft.block.Blocks.BRICKS)
                    || world.getBlockState(origin.add(hall.x, 0, hall.z + 1)).isOf(net.minecraft.block.Blocks.STRIPPED_DARK_OAK_LOG),
                    "the outside wall: " + world.getBlockState(origin.add(hall.x, 0, hall.z + 1)));
                var cellar = layout.site("cellar");
                check(world.getBlockState(origin.add(cellar.x + 2, -h + 1, cellar.z + 2)).isAir()
                    || !world.getBlockState(origin.add(cellar.x + 2, -h + 1, cellar.z + 2)).isOf(net.minecraft.block.Blocks.STONE),
                    "the cellar is dug out");
                check(!world.getBlockState(inHall).isOf(net.minecraft.block.Blocks.BRICKS), "the hall's inside is not wall");
            } catch (java.io.IOException e) {
                restoreRoot.run();
                throw new GameTestException(e.toString());
            }
            com.foredayner.scenario.town.BuildService.undoForTests(world.getServer(), id);
            whenIdle(context, id, 150, () -> {
                check(world.getBlockState(origin.add(hall.x + 2, 5, hall.z + 2)) == before, "the land is back");
                restoreRoot.run();
                context.complete();
            });
        });
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
        net.minecraft.util.math.BlockPos[] samples = { origin.add(80, -1, 80), origin.add(3, 2, 3), origin.add(150, -1, 150), origin.add(40, 0, 100),
            origin.add(-10, 2, 80), origin.add(-20, -1, 40) }; // the last two in the ring around the town
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
                for (int x = 0; x < plan.size && (road == null || sea == null); x++) {
                    for (int z = 0; z < plan.size; z++) {
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
                // The ring of land around the town is laid too: ground near the town's level at its edge.
                int found = Integer.MIN_VALUE;
                for (int y = origin.getY() + 20; y >= origin.getY() - 20; y--) {
                    var s = world.getBlockState(origin.add(-2, y - origin.getY(), 80));
                    if (s.isOf(net.minecraft.block.Blocks.GRASS_BLOCK) || s.isOf(net.minecraft.block.Blocks.PODZOL)
                        || s.isOf(net.minecraft.block.Blocks.COARSE_DIRT) || s.isOf(net.minecraft.block.Blocks.WATER)
                        || s.isOf(net.minecraft.block.Blocks.DIRT_PATH) || s.isOf(net.minecraft.block.Blocks.GRAVEL)) {
                        found = y;
                        break;
                    }
                }
                check(Math.abs(found - (origin.getY() - 1)) <= 2, "the ring starts at the town's level: " + found + " vs " + (origin.getY() - 1));
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

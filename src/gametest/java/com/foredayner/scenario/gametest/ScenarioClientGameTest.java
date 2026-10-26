package com.foredayner.scenario.gametest;

import com.foredayner.scenario.client.HandbookScreen;
import com.foredayner.scenario.client.ScenarioScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screen.world.WorldCreator;

/** A real client: the workshop and the handbook, photographed for review (CI keeps the pictures). */
public class ScenarioClientGameTest implements FabricClientGameTest {

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext game = context.worldBuilder()
                .adjustSettings(creator -> creator.setGameMode(WorldCreator.Mode.CREATIVE))
                .create()) {
            game.getClientWorld().waitForChunksRender();
            game.getServer().runCommand("time set noon");
            game.getServer().runOnServer(server -> {
                var player = server.getPlayerManager().getPlayerList().get(0);
                server.getPlayerManager().getOpList().add(new net.minecraft.server.OperatorEntry(player.getGameProfile(), 4, false));
                server.getPlayerManager().sendCommandTree(player);
            });
            context.waitTicks(5);
            context.setScreen(ScenarioScreen::new);
            context.waitTicks(5);
            context.takeScreenshot("01_scenario_workshop");
            // Load the original test scenario from a file, as the file dialog would, then save it on the server.
            context.runOnClient(client -> {
                try {
                    java.nio.file.Path file = java.nio.file.Files.createTempDirectory("scenario").resolve("그레이머스의 등불.txt");
                    java.nio.file.Files.writeString(file, ScenarioGameTests.testScenario());
                    com.foredayner.scenario.client.ScenarioClientState.loadFile(file);
                } catch (java.io.IOException e) {
                    throw new RuntimeException(e);
                }
            });
            context.waitTicks(3);
            context.takeScreenshot("02_scenario_loaded");
            context.runOnClient(client -> com.foredayner.scenario.client.ScenarioClientState.upload());
            context.waitTicks(20);
            context.takeScreenshot("03_scenario_saved");
            // The sheet tab, with the hand-written sheet of the test scenario.
            context.runOnClient(client -> {
                var sheet = com.foredayner.scenario.sheet.ScenarioSheet.fromJson(ScenarioGameTests.testSheet());
                sheet.normalize();
                String id = com.foredayner.scenario.client.ScenarioClientState.selected();
                com.foredayner.scenario.client.ScenarioClientState.showSheetForTests(id.isEmpty() ? "test" : id, sheet);
                ScenarioScreen.showTab(ScenarioScreen.Tab.SHEET);
            });
            context.waitTicks(3);
            context.takeScreenshot("03b_sheet");
            // The layout tab with the automatic layout of the test town, then with a place picked.
            context.runOnClient(client -> {
                var sheet = com.foredayner.scenario.client.ScenarioClientState.sheet();
                com.foredayner.scenario.client.ScenarioClientState.showLayoutForTests(
                    com.foredayner.scenario.client.ScenarioClientState.selected(), com.foredayner.scenario.layout.TownLayout.auto(sheet));
                ScenarioScreen.showTab(ScenarioScreen.Tab.LAYOUT);
            });
            context.waitTicks(3);
            context.takeScreenshot("03c_layout");
            context.runOnClient(client -> ScenarioScreen.pickForTests("lighthouse"));
            context.waitTicks(3);
            context.takeScreenshot("03d_layout_picked");
            // A crowded town: 14 places, the list scrolls; one place has no site and is picked.
            context.runOnClient(client -> {
                var sheet = new com.foredayner.scenario.sheet.ScenarioSheet();
                String[] names = { "시청", "경찰서", "도서관", "신문사", "교회", "여관", "잡화점", "약국", "학교", "병원", "제재소", "창고", "묘지", "부두" };
                String[] sizes = { "L", "M", "L", "M", "L", "L", "M", "S", "L", "XL", "XL", "L", "L", "M" };
                for (int i = 0; i < names.length; i++) {
                    var p = new com.foredayner.scenario.sheet.ScenarioSheet.Place();
                    p.id = "p" + i;
                    p.name = names[i];
                    p.size = sizes[i];
                    p.kind = i == 12 ? "cemetery" : i == 13 ? "dock" : "house";
                    p.outdoor = i >= 12;
                    sheet.places.add(p);
                }
                sheet.terrain.coast = "south";
                sheet.terrain.woods = "west";
                sheet.normalize();
                String id = com.foredayner.scenario.client.ScenarioClientState.selected();
                com.foredayner.scenario.client.ScenarioClientState.showSheetForTests(id, sheet);
                var layout = com.foredayner.scenario.layout.TownLayout.auto(sheet);
                layout.sites.remove(layout.site("p11"));
                com.foredayner.scenario.client.ScenarioClientState.showLayoutForTests(id, layout);
                ScenarioScreen.pickForTests("p11");
            });
            context.waitTicks(3);
            context.takeScreenshot("03e_layout_crowded");
            // Build the test town around the player (a fixed house in place of the AI), then look at it.
            String id = context.computeOnClient(client -> com.foredayner.scenario.client.ScenarioClientState.selected());
            game.getServer().runOnServer(server -> {
                var sheet = com.foredayner.scenario.sheet.ScenarioSheet.fromJson(ScenarioGameTests.testSheet());
                sheet.normalize();
                try {
                    com.foredayner.scenario.sheet.SheetService.save(id, sheet);
                    com.foredayner.scenario.layout.LayoutService.save(id, com.foredayner.scenario.layout.TownLayout.auto(sheet));
                } catch (java.io.IOException e) {
                    throw new RuntimeException(e);
                }
                com.example.config.AiBuilderConfig.get().ticksPerBlock = 1;
                com.foredayner.scenario.town.BuildService.designer = new FixedDesigner();
                com.foredayner.scenario.town.BuildService.columnsPerTick = 1500;
                var player = server.getPlayerManager().getPlayerList().get(0);
                com.foredayner.scenario.town.BuildService.here(player, id);
                com.foredayner.scenario.town.BuildService.start(player, id);
            });
            for (int i = 0; i < 150; i++) {
                context.waitTicks(10);
                if (game.getServer().computeOnServer(server -> com.foredayner.scenario.town.BuildService.idle(id))) {
                    break;
                }
            }
            context.runOnClient(client -> com.foredayner.scenario.client.ScenarioClientState.select(id));
            context.waitTicks(10);
            context.runOnClient(client -> ScenarioScreen.showTab(ScenarioScreen.Tab.BUILD));
            context.waitTicks(3);
            context.takeScreenshot("03f_build_tab");
            context.setScreen(() -> null);
            int[] mid = game.getServer().computeOnServer(server -> {
                try {
                    var s = com.foredayner.scenario.town.BuildService.load(id);
                    return new int[] { s.originX + 80, s.originY, s.originZ + 80 };
                } catch (java.io.IOException e) {
                    throw new RuntimeException(e);
                }
            });
            game.getServer().runCommand("gamemode spectator @a");
            game.getServer().runCommand("tp @a " + mid[0] + " " + (mid[1] + 110) + " " + mid[2] + " 0 90");
            context.waitTicks(40);
            game.getClientWorld().waitForChunksRender();
            context.takeScreenshot("05_town_from_above");
            game.getServer().runCommand("tp @a " + mid[0] + " " + (mid[1] + 28) + " " + (mid[2] + 95) + " 180 22");
            context.waitTicks(40);
            game.getClientWorld().waitForChunksRender();
            context.takeScreenshot("05b_town_view");
            game.getServer().runCommand("gamemode creative @a");
            context.setScreen(HandbookScreen::new);
            context.waitTicks(5);
            context.takeScreenshot("04_gm_handbook");
            context.setScreen(() -> null);
        }
    }
}

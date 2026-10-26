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
            context.setScreen(HandbookScreen::new);
            context.waitTicks(5);
            context.takeScreenshot("04_gm_handbook");
            context.setScreen(() -> null);
        }
    }
}

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
            context.setScreen(ScenarioScreen::new);
            context.waitTicks(5);
            context.takeScreenshot("01_scenario_workshop");
            context.setScreen(HandbookScreen::new);
            context.waitTicks(5);
            context.takeScreenshot("02_gm_handbook");
            context.setScreen(() -> null);
        }
    }
}

package com.foredayner.scenario.gametest;

import com.example.api.AiBuildApi;
import com.foredayner.scenario.ScenarioBuilderMod;
import com.foredayner.scenario.content.ScenarioContent;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.registry.Registries;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;

/** Real-server checks of the scenario builder. */
public class ScenarioGameTests implements FabricGameTest {

    private static void check(boolean ok, String message) {
        if (!ok) {
            throw new GameTestException(message);
        }
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void loadsWithTheAiBuildAssistant(TestContext context) {
        check(AiBuildApi.VERSION >= ScenarioBuilderMod.NEEDS_API, "the AI Build Assistant API is new enough");
        check(Registries.ITEM.containsId(ScenarioContent.GM_HANDBOOK_ID), "the GM handbook item exists");
        check(ScenarioContent.GM_HANDBOOK.getMaxCount() == 1, "one handbook per stack");
        context.complete();
    }
}

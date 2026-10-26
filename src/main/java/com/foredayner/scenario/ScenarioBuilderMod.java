package com.foredayner.scenario;

import com.example.api.AiBuildApi;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AI Scenario Builder: turns a TRPG scenario (Call of Cthulhu) into a town map. It uses the AI
 * Build Assistant (a required mod) for the AI connection and for designing and placing buildings
 * and objects.
 */
public final class ScenarioBuilderMod implements ModInitializer {

    public static final String MOD_ID = "scenario-builder";
    public static final Logger LOG = LoggerFactory.getLogger(MOD_ID);
    /** The AI Build Assistant API version this mod was written for. */
    public static final int NEEDS_API = 2;

    @Override
    public void onInitialize() {
        if (AiBuildApi.VERSION < NEEDS_API) {
            throw new IllegalStateException("AI Scenario Builder needs a newer AI Build Assistant (API " + NEEDS_API
                + ", found " + AiBuildApi.VERSION + ")");
        }
        com.foredayner.scenario.net.ScenarioNet.register();
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(com.foredayner.scenario.town.BuildService::tick);
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPING.register(s -> com.foredayner.scenario.town.BuildService.reset());
        LOG.info("AI Scenario Builder ready (AI Build Assistant API {})", AiBuildApi.VERSION);
    }
}

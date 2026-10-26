package com.foredayner.scenario.store;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Which model each step uses (empty = the model set in the AI Build Assistant). Reading the
 * scenario and laying out the town need judgement, so a bigger model can be set for them.
 */
public final class ScenarioConfig {

    public String analysisModel = "";
    public String layoutModel = "";
    public String buildingModel = "";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static ScenarioConfig current;

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("scenario_builder.json");
    }

    public static ScenarioConfig get() {
        if (current == null) {
            try {
                current = Files.exists(file()) ? GSON.fromJson(Files.readString(file(), StandardCharsets.UTF_8), ScenarioConfig.class) : null;
            } catch (Exception ignored) {
                current = null;
            }
            if (current == null) {
                current = new ScenarioConfig();
            }
        }
        return current;
    }

    public static void set(ScenarioConfig config) {
        current = config == null ? new ScenarioConfig() : config;
        try {
            Files.writeString(file(), GSON.toJson(current), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }
    }

    public String toJson() {
        return GSON.toJson(this);
    }

    public static ScenarioConfig fromJson(String json) {
        ScenarioConfig c = GSON.fromJson(json, ScenarioConfig.class);
        return c == null ? new ScenarioConfig() : c;
    }

    /** null = use the AI Build Assistant's model. */
    public static String model(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** Buildings and props: Haiku by default on a Claude connection (cheap and quick enough for them). */
    public static final String DEFAULT_CLAUDE_BUILDING_MODEL = "claude-haiku-5-5";

    /** The model for buildings and props (null = the AI Build Assistant's own model). */
    public static String buildingModel() {
        String set = model(get().buildingModel);
        if (set != null) {
            return set;
        }
        return com.example.config.AiBuilderConfig.get().provider == com.example.config.AiProvider.CLAUDE ? DEFAULT_CLAUDE_BUILDING_MODEL : null;
    }
}

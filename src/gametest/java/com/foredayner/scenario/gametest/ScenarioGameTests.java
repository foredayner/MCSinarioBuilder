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

package com.foredayner.scenario.town;

import com.example.ai.AiPrompt;
import com.example.api.AiBuildApi;
import com.foredayner.scenario.net.ScenarioNet;
import com.foredayner.scenario.sheet.ScenarioSheet;
import com.foredayner.scenario.sheet.SheetService;
import com.foredayner.scenario.store.ScenarioConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Consumer;

/** The town's architecture guide: written by the AI from the sheet, kept in the sheet, edited by the keeper. */
public final class StyleService {

    private static final Set<String> BUSY = new HashSet<>();

    private StyleService() {
    }

    static final String SYSTEM = """
        You are an architectural historian helping build a Call of Cthulhu town in Minecraft. Write the town's
        ARCHITECTURE GUIDE that every building will follow, so the town looks like one real place of its era and region.
        Plain text only (no markdown tables), 25-45 short lines, in English, with these sections:

        PALETTE: for each part name the Minecraft blocks to use (2-4 per part): walls, trim and frames, roofs, foundations,
          floors, windows, doors, chimneys, fences and gates, street lights and lamps.
        SHAPES: typical roof shapes and pitch, storey height, window size and rhythm, porches and entrances, decoration.
        INTERIORS: wall and floor finishes, typical furniture and lighting of the period.
        WEAR AND MOOD: how age and the scenario's mood show (weathering, moss, soot, fog-darkened wood...).
        BY BUILDING: one or two lines for each kind of building in this town, saying how it differs from the houses.
        AVOID: things that would look wrong for this era and place.

        Base it on real architecture of that era and region (e.g. 1920s New England: clapboard and shingle houses,
        gambrel and gable roofs, brick commercial blocks, white wooden churches; 1920s Gyeongseong: hanok with tiled
        roofs beside Japanese-colonial brick and stone offices; modern American small town: vinyl siding, asphalt
        shingles, strip-mall shops; modern Korean city street: 3-5 storey red-brick and white-concrete villas with flat
        roofs and rooftop railings, ground-floor shops with glass fronts and bright sign bands, tiled facades, metal
        balcony railings, air-conditioner units, blue water tanks on roofs). Use only real Minecraft block names (spruce_planks, deepslate_tiles...).
        """;

    static AiPrompt prompt(ScenarioSheet sheet) {
        Set<String> kinds = new LinkedHashSet<>();
        for (ScenarioSheet.Place p : sheet.places) {
            kinds.add(p.kind);
        }
        String period = sheet.period();
        String user = "TOWN: " + sheet.title + "\nERA: " + sheet.era + "\nPERIOD AND CULTURE: " + period
            + (com.foredayner.scenario.sheet.Period.OTHER.equals(period)
                ? " (no real-world period: invent a consistent architecture from the setting; the buildings get no other rules)"
                : "") + "\nSETTING: " + sheet.setting + "\nMOOD: " + sheet.mood
            + "\nTIME AND WEATHER: " + sheet.timeOfDay + ", " + sheet.weather
            + "\nTERRAIN: coast " + sheet.terrain.coast + ", woods " + sheet.terrain.woods + ". " + sheet.terrain.notes
            + "\nSTYLE NOTES FROM THE SCENARIO:\n" + sheet.style
            + "\nKINDS OF BUILDINGS: " + String.join(", ", kinds)
            + "\n\nWrite the architecture guide now.";
        return new AiPrompt(SYSTEM, user);
    }

    /** Write the guide with the AI and save it in the sheet; {@code then} gets the guide (or null on failure). */
    public static void make(MinecraftServer server, ServerPlayerEntity player, String id, Consumer<String> then) {
        ScenarioSheet sheet;
        try {
            sheet = SheetService.load(id);
        } catch (IOException e) {
            sheet = null;
        }
        if (sheet == null) {
            say(player, "먼저 시트를 만들어 주세요.", id);
            then.accept(null);
            return;
        }
        if (!BUSY.add(id)) {
            say(player, "스타일 가이드를 이미 만드는 중이에요.", id);
            return;
        }
        sheet.normalize();
        ScenarioSheet target = sheet;
        say(player, "AI가 마을 스타일 가이드를 쓰는 중이에요...", id);
        AiBuildApi.ask(server, prompt(sheet), ScenarioConfig.model(ScenarioConfig.get().analysisModel)).thenAccept(answer -> {
            BUSY.remove(id);
            String text = answer.ok() ? answer.text().strip() : "";
            if (!answer.ok() || text.length() < 200) {
                say(player, "스타일 가이드를 만들지 못했어요: " + (answer.ok() ? "너무 짧은 답" : answer.error()), id);
                then.accept(null);
                return;
            }
            target.styleGuide = text.replace("```", "").strip();
            try {
                SheetService.save(id, target);
                if (player != null) {
                    ScenarioNet.sheet(player, id, target);
                }
                say(player, "스타일 가이드를 만들었어요 (토큰 입력 " + answer.inputTokens() + " / 출력 " + answer.outputTokens() + ").", id);
                then.accept(target.styleGuide);
            } catch (IOException e) {
                say(player, "스타일 가이드를 저장하지 못했어요: " + e.getMessage(), id);
                then.accept(null);
            }
        });
    }

    private static void say(ServerPlayerEntity player, String text, String id) {
        if (player != null) {
            ScenarioNet.status(player, text, id);
        }
    }
}

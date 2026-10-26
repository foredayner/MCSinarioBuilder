package com.foredayner.scenario.sheet;

import com.example.ai.AiPrompt;
import com.example.api.AiBuildApi;
import com.example.config.AiBuilderConfig;
import com.foredayner.scenario.ScenarioBuilderMod;
import com.foredayner.scenario.net.ScenarioNet;
import com.foredayner.scenario.store.ScenarioConfig;
import com.foredayner.scenario.store.ScenarioStore;
import net.minecraft.server.network.ServerPlayerEntity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Reading a scenario into a sheet with the AI, saving it, and changing it as the keeper asks. */
public final class SheetService {

    /** Scenarios being read right now (one request at a time each). */
    private static final Set<String> BUSY = new HashSet<>();

    private SheetService() {
    }

    public static ScenarioSheet load(String id) throws IOException {
        var f = ScenarioStore.dir(id).resolve("sheet.json");
        return Files.exists(f) ? ScenarioSheet.fromJson(Files.readString(f, StandardCharsets.UTF_8)) : null;
    }

    public static void save(String id, ScenarioSheet sheet) throws IOException {
        Files.writeString(ScenarioStore.dir(id).resolve("sheet.json"), sheet.toJson(), StandardCharsets.UTF_8);
        ScenarioStore.Info info = ScenarioStore.info(id);
        if ("text".equals(info.stage)) {
            info.stage = "sheet";
            ScenarioStore.writeInfo(info);
        }
    }

    /** Read the scenario with the AI (the analysis model) and save the sheet. */
    public static void analyze(ServerPlayerEntity player, String id) {
        String text;
        try {
            text = ScenarioStore.text(id);
        } catch (IOException e) {
            ScenarioNet.status(player, "시나리오를 찾지 못했어요.", "");
            return;
        }
        run(player, id, SheetPrompt.analyze(text), "시나리오를 읽는 중이에요...");
    }

    /** Change the sheet as the keeper asks (e.g. "add a general store", "the church is bigger"). */
    public static void revise(ServerPlayerEntity player, String id, String instruction) {
        try {
            ScenarioSheet sheet = load(id);
            if (sheet == null) {
                ScenarioNet.status(player, "먼저 시트를 만들어 주세요.", id);
                return;
            }
            run(player, id, SheetPrompt.revise(sheet.toJson(), instruction, ScenarioStore.text(id)), "시트를 고치는 중이에요...");
        } catch (IOException e) {
            ScenarioNet.status(player, "시트를 읽지 못했어요: " + e.getMessage(), id);
        }
    }

    private static void run(ServerPlayerEntity player, String id, AiPrompt prompt, String busyMessage) {
        if (!BUSY.add(id)) {
            ScenarioNet.status(player, "이미 처리 중이에요. 잠시 기다려 주세요.", id);
            return;
        }
        ScenarioNet.status(player, busyMessage, id);
        attempt(player, id, prompt, prompt, 0, new int[2]);
    }

    private static void attempt(ServerPlayerEntity player, String id, AiPrompt base, AiPrompt prompt, int tries, int[] tokens) {
        String model = ScenarioConfig.model(ScenarioConfig.get().analysisModel);
        AiBuildApi.ask(player.getServer(), prompt, model).thenAccept(answer -> {
            tokens[0] += answer.inputTokens();
            tokens[1] += answer.outputTokens();
            if (!answer.ok()) {
                BUSY.remove(id);
                ScenarioNet.status(player, "AI 오류: " + answer.error(), id);
                return;
            }
            String problems;
            ScenarioSheet sheet = null;
            try {
                sheet = ScenarioSheet.fromAnswer(answer.text());
                List<String> list = sheet.normalize();
                problems = list.isEmpty() ? null : "- " + String.join("\n- ", list);
            } catch (RuntimeException e) {
                problems = "- the answer is not valid JSON of the required shape: " + e.getMessage();
            }
            if (problems == null) {
                BUSY.remove(id);
                try {
                    save(id, sheet);
                    ScenarioNet.sheet(player, id, sheet);
                    ScenarioNet.status(player, "시트를 만들었어요: 장소 " + sheet.places.size() + "곳, NPC " + sheet.npcs.size()
                        + "명, 단서 " + sheet.clues.size() + "개 (토큰 입력 " + tokens[0] + " / 출력 " + tokens[1] + ").", id);
                } catch (IOException e) {
                    ScenarioNet.status(player, "시트를 저장하지 못했어요: " + e.getMessage(), id);
                }
                return;
            }
            int max = AiBuilderConfig.get().autoRetries;
            if (tries < max) {
                ScenarioBuilderMod.LOG.info("Sheet for {} had problems, asking again ({}/{}): {}", id, tries + 1, max, problems);
                ScenarioNet.status(player, "시트에 문제가 있어 다시 요청해요 (" + (tries + 1) + "/" + max + ")...", id);
                attempt(player, id, base, SheetPrompt.fix(base, answer.text(), problems), tries + 1, tokens);
            } else {
                BUSY.remove(id);
                ScenarioNet.status(player, "시트를 만들지 못했어요: " + problems.lines().findFirst().orElse(""), id);
            }
        });
    }
}

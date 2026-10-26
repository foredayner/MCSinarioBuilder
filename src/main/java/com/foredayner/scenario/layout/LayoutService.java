package com.foredayner.scenario.layout;

import com.example.ai.AiPrompt;
import com.example.api.AiBuildApi;
import com.example.config.AiBuilderConfig;
import com.foredayner.scenario.ScenarioBuilderMod;
import com.foredayner.scenario.net.ScenarioNet;
import com.foredayner.scenario.sheet.ScenarioSheet;
import com.foredayner.scenario.sheet.SheetService;
import com.foredayner.scenario.store.ScenarioConfig;
import com.foredayner.scenario.store.ScenarioStore;
import net.minecraft.server.network.ServerPlayerEntity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Making the town layout (by the AI from the code's draft), saving it, and changing it. */
public final class LayoutService {

    private static final Set<String> BUSY = new HashSet<>();

    private LayoutService() {
    }

    public static TownLayout load(String id) throws IOException {
        var f = ScenarioStore.dir(id).resolve("layout.json");
        return Files.exists(f) ? TownLayout.fromJson(Files.readString(f, StandardCharsets.UTF_8)) : null;
    }

    public static void save(String id, TownLayout layout) throws IOException {
        Files.writeString(ScenarioStore.dir(id).resolve("layout.json"), layout.toJson(), StandardCharsets.UTF_8);
        ScenarioStore.Info info = ScenarioStore.info(id);
        if ("text".equals(info.stage) || "sheet".equals(info.stage)) {
            info.stage = "layout";
            ScenarioStore.writeInfo(info);
        }
    }

    /** "- a\n- b" or null. */
    static String problems(TownLayout layout, ScenarioSheet sheet) {
        List<String> list = layout.check(sheet);
        return list.isEmpty() ? null : "- " + String.join("\n- ", list);
    }

    /** The code's layout, no AI. */
    public static void auto(ServerPlayerEntity player, String id) {
        try {
            ScenarioSheet sheet = SheetService.load(id);
            if (sheet == null) {
                ScenarioNet.status(player, "먼저 시트를 만들어 주세요.", id);
                return;
            }
            sheet.normalize();
            TownLayout layout = TownLayout.auto(sheet);
            List<String> problems = layout.check(sheet);
            save(id, layout);
            ScenarioNet.layout(player, id, layout);
            ScenarioNet.status(player, problems.isEmpty() ? "기본 배치를 만들었어요." : "기본 배치를 만들었지만 확인할 것이 있어요: " + problems.get(0), id);
        } catch (IOException e) {
            ScenarioNet.status(player, "배치를 만들지 못했어요: " + e.getMessage(), id);
        }
    }

    /** Lay out the town with the AI, starting from the code's draft. */
    public static void make(ServerPlayerEntity player, String id) {
        try {
            ScenarioSheet sheet = SheetService.load(id);
            if (sheet == null) {
                ScenarioNet.status(player, "먼저 시트를 만들어 주세요.", id);
                return;
            }
            sheet.normalize();
            TownLayout draft = TownLayout.auto(sheet);
            run(player, id, sheet, draft, LayoutPrompt.layout(sheet, draft), "AI가 마을을 배치하는 중이에요...");
        } catch (IOException e) {
            ScenarioNet.status(player, "시트를 읽지 못했어요: " + e.getMessage(), id);
        }
    }

    /** Change the layout as the keeper asks. */
    public static void revise(ServerPlayerEntity player, String id, String instruction) {
        try {
            ScenarioSheet sheet = SheetService.load(id);
            TownLayout current = load(id);
            if (sheet == null || current == null) {
                ScenarioNet.status(player, "먼저 배치를 만들어 주세요.", id);
                return;
            }
            sheet.normalize();
            run(player, id, sheet, current, LayoutPrompt.revise(sheet, current, instruction), "배치를 고치는 중이에요...");
        } catch (IOException e) {
            ScenarioNet.status(player, "배치를 읽지 못했어요: " + e.getMessage(), id);
        }
    }

    private static void run(ServerPlayerEntity player, String id, ScenarioSheet sheet, TownLayout fallback, AiPrompt prompt, String busy) {
        if (!BUSY.add(id)) {
            ScenarioNet.status(player, "이미 처리 중이에요. 잠시 기다려 주세요.", id);
            return;
        }
        ScenarioNet.status(player, busy, id);
        attempt(player, id, sheet, fallback, prompt, prompt, 0, new int[2]);
    }

    private static void attempt(ServerPlayerEntity player, String id, ScenarioSheet sheet, TownLayout fallback,
                                AiPrompt base, AiPrompt prompt, int tries, int[] tokens) {
        String model = ScenarioConfig.model(ScenarioConfig.get().layoutModel);
        AiBuildApi.ask(player.getServer(), prompt, model).thenAccept(answer -> {
            tokens[0] += answer.inputTokens();
            tokens[1] += answer.outputTokens();
            if (!answer.ok()) {
                BUSY.remove(id);
                ScenarioNet.status(player, "AI 오류: " + answer.error(), id);
                return;
            }
            TownLayout layout = null;
            String problems;
            try {
                layout = TownLayout.fromAnswer(answer.text(), sheet);
                problems = problems(layout, sheet);
            } catch (RuntimeException e) {
                problems = "- the answer is not valid JSON of the required shape: " + e.getMessage();
            }
            if (problems == null) {
                finish(player, id, layout, "AI가 마을을 배치했어요 (토큰 입력 " + tokens[0] + " / 출력 " + tokens[1] + ").");
                return;
            }
            int max = AiBuilderConfig.get().autoRetries;
            if (tries < max) {
                ScenarioBuilderMod.LOG.info("Layout for {} had problems, asking again ({}/{}): {}", id, tries + 1, max, problems);
                ScenarioNet.status(player, "배치에 문제가 있어 다시 요청해요 (" + (tries + 1) + "/" + max + ")...", id);
                attempt(player, id, sheet, fallback, base, LayoutPrompt.fix(base, answer.text(), problems), tries + 1, tokens);
            } else {
                finish(player, id, fallback, "AI 배치가 검사를 통과하지 못해 기본 배치를 썼어요: " + problems.lines().findFirst().orElse(""));
            }
        });
    }

    private static void finish(ServerPlayerEntity player, String id, TownLayout layout, String message) {
        BUSY.remove(id);
        try {
            save(id, layout);
            ScenarioNet.layout(player, id, layout);
            ScenarioNet.status(player, message, id);
        } catch (IOException e) {
            ScenarioNet.status(player, "배치를 저장하지 못했어요: " + e.getMessage(), id);
        }
    }
}

package com.foredayner.scenario.layout;

import com.example.ai.AiImage;
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
import java.util.ArrayList;
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

    /** Too many places for the ground: say so and lay out nothing. */
    private static boolean tooMany(ServerPlayerEntity player, String id, ScenarioSheet sheet) {
        if (sheet.counted() <= ScenarioSheet.MAX_PLACES) {
            return false;
        }
        ScenarioNet.status(player, "장소가 " + sheet.counted() + "곳이에요. 시트에서 " + ScenarioSheet.MAX_PLACES
            + "곳 이하로 줄여 주세요 (X로 빼거나 AI에게 합쳐 달라고 하기).", id);
        return true;
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
            if (tooMany(player, id, sheet)) {
                return;
            }
            TownLayout layout = sheet.building() ? IndoorPlan.auto(sheet) : TownLayout.auto(sheet);
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
            if (tooMany(player, id, sheet)) {
                return;
            }
            if (sheet.building()) {
                // One building: the floor plan, after the scenario's plans if the keeper gave them.
                List<AiImage> plans = MapService.images(id, sheet);
                TownLayout draft = IndoorPlan.auto(sheet);
                run(player, id, sheet, draft, IndoorPrompt.plan(sheet, draft, !plans.isEmpty()), plans,
                    plans.isEmpty() ? "AI가 건물 평면도를 그리는 중이에요..." : "AI가 평면도 그림을 보며 건물을 배치하는 중이에요...");
                return;
            }
            List<AiImage> map = "indoor".equals(sheet.mapKind) ? List.of() : mapOf(id, sheet);
            if (!map.isEmpty() && sheet.places.removeIf(p -> p.mapFiller)) {
                // The map's ordinary houses are made again from this layout.
                SheetService.save(id, sheet);
                ScenarioNet.sheet(player, id, sheet);
            }
            TownLayout draft = TownLayout.auto(sheet);
            run(player, id, sheet, draft, LayoutPrompt.layout(sheet, draft, !map.isEmpty()), map,
                map.isEmpty() ? "AI가 마을을 배치하는 중이에요..." : "AI가 참고 지도를 보며 마을을 배치하는 중이에요...");
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
            if (tooMany(player, id, sheet)) {
                return;
            }
            if (sheet.building()) {
                List<AiImage> plans = MapService.images(id, sheet);
                run(player, id, sheet, current, IndoorPrompt.revise(sheet, current, instruction, !plans.isEmpty()), plans, "평면도를 고치는 중이에요...");
                return;
            }
            List<AiImage> map = mapOf(id, sheet);
            run(player, id, sheet, current, LayoutPrompt.revise(sheet, current, instruction, !map.isEmpty()), map, "배치를 고치는 중이에요...");
        } catch (IOException e) {
            ScenarioNet.status(player, "배치를 읽지 못했어요: " + e.getMessage(), id);
        }
    }

    /** The keeper's map as a picture for the AI, or nothing. */
    private static List<AiImage> mapOf(String id, ScenarioSheet sheet) {
        try {
            AiImage map = MapService.image(id, sheet);
            return map == null ? List.of() : List.of(map);
        } catch (IOException | RuntimeException e) {
            ScenarioBuilderMod.LOG.warn("Map of {} could not be read: {}", id, e.toString());
            return List.of();
        }
    }

    private static void run(ServerPlayerEntity player, String id, ScenarioSheet sheet, TownLayout fallback, AiPrompt prompt,
                            List<AiImage> images, String busy) {
        if (!BUSY.add(id)) {
            ScenarioNet.status(player, "이미 처리 중이에요. 잠시 기다려 주세요.", id);
            return;
        }
        ScenarioNet.status(player, busy, id);
        attempt(player, id, sheet, fallback, prompt, prompt, images, 0, new int[2]);
    }

    private static void attempt(ServerPlayerEntity player, String id, ScenarioSheet sheet, TownLayout fallback,
                                AiPrompt base, AiPrompt prompt, List<AiImage> images, int tries, int[] tokens) {
        String model = ScenarioConfig.model(ScenarioConfig.get().layoutModel);
        AiBuildApi.ask(player.getServer(), prompt, model, images).thenAccept(answer -> {
            tokens[0] += answer.inputTokens();
            tokens[1] += answer.outputTokens();
            if (!answer.ok()) {
                BUSY.remove(id);
                String error = answer.error() == null ? "" : answer.error();
                boolean badModel = model != null && error.toLowerCase(java.util.Locale.ROOT).contains("not");
                ScenarioNet.status(player, "AI 오류: " + error + (badModel ? " (모델 설정의 배치도 칸에 적힌 모델 \"" + model
                    + "\"을 확인하세요. 비워 두면 AI 공방 모델을 써요.)" : ""), id);
                return;
            }
            TownLayout layout = null;
            String problems;
            List<String> fixed = List.of();
            String kind = images.isEmpty() || sheet.building() ? "" : LayoutPrompt.mapKind(answer.text());
            int fillers = 0;
            try {
                layout = sheet.building() ? IndoorPlan.fromAnswer(answer.text(), sheet) : TownLayout.fromAnswer(answer.text(), sheet);
                if (!images.isEmpty() && !sheet.building()) {
                    sheet.places.removeIf(p -> p.mapFiller);
                    if (sheet.mapFillers && !"indoor".equals(kind)) {
                        fillers = addFillers(sheet, layout, LayoutPrompt.extras(answer.text()));
                    }
                }
                // What code can fix (a building on a road, a front far from any street...) is fixed here,
                // keeping the AI's (and the map's) layout; only the rest goes back to the AI.
                fixed = layout.repair(sheet);
                problems = problems(layout, sheet);
                if (problems != null && fillers > 0) {
                    // A map house that does not fit is simply left out (it is not one of the scenario's places).
                    for (TownLayout.Site s : new ArrayList<>(layout.sites)) {
                        if (s.id.startsWith("map_house_") && (problems.contains(s.id + " ") || problems.contains(s.id + "'"))) {
                            layout.sites.remove(s);
                            sheet.places.removeIf(p -> p.id.equals(s.id));
                            fillers--;
                        }
                    }
                    problems = problems(layout, sheet);
                }
            } catch (RuntimeException e) {
                problems = "- the answer is not valid JSON of the required shape: " + e.getMessage();
            }
            int max = AiBuilderConfig.get().autoRetries;
            if (problems != null && tries >= max && layout != null) {
                // Out of tries: drop just the places that still have problems and set them again by code.
                fixed = new ArrayList<>(fixed);
                for (TownLayout.Site s : new ArrayList<>(layout.sites)) {
                    if (problems.contains(s.id + " ") || problems.contains(s.id + "'")) {
                        layout.sites.remove(s);
                    }
                }
                fixed.addAll(layout.repair(sheet));
                problems = problems(layout, sheet);
            }
            if (problems == null) {
                if (!images.isEmpty() && !sheet.building()) {
                    saveSheet(player, id, sheet);
                }
                String kindNote = switch (kind) {
                    case "indoor" -> " 지도가 건물 평면도로 보여요: 마을 배치에는 길과 위치만 참고했어요.";
                    case "outdoor" -> " 야외 지도로 읽었어요.";
                    default -> "";
                };
                String what = sheet.building() ? (images.isEmpty() ? "AI가 건물 평면도를 그렸어요" : "AI가 평면도 그림을 따라 건물을 배치했어요")
                    : images.isEmpty() ? "AI가 마을을 배치했어요" : "AI가 지도를 따라 배치했어요";
                finish(player, id, layout, what + " (토큰 입력 "
                    + tokens[0] + " / 출력 " + tokens[1] + ")." + (fixed.isEmpty() ? "" : " 바로잡은 것: " + String.join(", ", fixed) + ".")
                    + (fillers > 0 ? " 지도의 이름 없는 건물 " + fillers + "채를 민가로 넣었어요." : "") + kindNote);
                return;
            }
            if (tries < max) {
                ScenarioBuilderMod.LOG.info("Layout for {} had problems, asking again ({}/{}): {}", id, tries + 1, max, problems);
                ScenarioNet.status(player, "배치에 문제가 있어 다시 요청해요 (" + (tries + 1) + "/" + max + ")...", id);
                attempt(player, id, sheet, fallback, base, LayoutPrompt.fix(base, answer.text(), problems), images, tries + 1, tokens);
            } else {
                if (sheet.places.removeIf(p -> p.mapFiller)) {
                    saveSheet(player, id, sheet);
                }
                finish(player, id, fallback, "AI 배치가 검사를 통과하지 못해 기본 배치를 썼어요: " + problems.lines().findFirst().orElse(""));
            }
        });
    }

    /** Most ordinary houses made from the map's unnamed buildings. */
    static final int MAX_FILLERS = 12;

    /**
     * The map's buildings that no place took become ordinary houses of the period, where the map
     * draws them; houses of a size share two designs (one AI design each, placed again).
     */
    static int addFillers(ScenarioSheet sheet, TownLayout layout, List<int[]> extras) {
        layout.sites.removeIf(s -> s.id.startsWith("map_house_"));
        java.util.Map<Integer, List<String>> designs = new java.util.HashMap<>();
        int n = 0;
        for (int[] e : extras) {
            if (n >= MAX_FILLERS) {
                break;
            }
            n++;
            ScenarioSheet.Place p = new ScenarioSheet.Place();
            p.id = "map_house_" + n;
            p.name = "민가 " + n;
            p.kind = "house";
            p.size = e[2] <= ScenarioSheet.footprint("S") ? "S" : "M";
            p.filler = true;
            p.mapFiller = true;
            p.notes = "지도에 그려진 이름 없는 집: 이 시대와 지역의 평범한 민가";
            List<String> made = designs.computeIfAbsent(e[2], k -> new ArrayList<>());
            if (made.size() < 2) {
                made.add(p.id);
            } else {
                p.sameAs = made.get(n % 2);
            }
            sheet.places.add(p);
            TownLayout.Site s = new TownLayout.Site();
            s.id = p.id;
            s.x = e[0];
            s.z = e[1];
            s.w = s.d = ScenarioSheet.footprint(p.size);
            s.front = TownLayout.FRONTS.get(e[3]);
            s.fromMap = true;
            layout.sites.add(s);
        }
        return n;
    }

    private static void saveSheet(ServerPlayerEntity player, String id, ScenarioSheet sheet) {
        try {
            SheetService.save(id, sheet);
            ScenarioNet.sheet(player, id, sheet);
        } catch (IOException e) {
            ScenarioBuilderMod.LOG.warn("Sheet of {} could not be saved: {}", id, e.toString());
        }
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

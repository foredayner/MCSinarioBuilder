package com.foredayner.scenario.client;

import com.foredayner.scenario.input.ScenarioText;
import com.foredayner.scenario.net.ScenarioNet;
import com.foredayner.scenario.store.ScenarioStore;
import com.google.gson.Gson;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.zip.GZIPOutputStream;

/** What the workshop shows: the scenario being loaded here, and what the server has stored. */
public final class ScenarioClientState {

    private static ScenarioText.Loaded loaded;
    private static List<ScenarioStore.Info> stored = List.of();
    private static String message = "";
    private static String selected = "";
    private static int uploadId = 1;
    private static com.foredayner.scenario.sheet.ScenarioSheet sheet;
    private static String sheetId = "";
    private static com.foredayner.scenario.town.BuildState build;
    private static String buildId = "";
    private static boolean canUndo;
    private static com.foredayner.scenario.layout.TownLayout layout;
    private static String layoutId = "";
    private static com.foredayner.scenario.store.ScenarioConfig config = new com.foredayner.scenario.store.ScenarioConfig();

    private ScenarioClientState() {
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(ScenarioNet.SheetPayload.ID, (p, ctx) -> ctx.client().execute(() -> {
            try {
                sheet = com.foredayner.scenario.sheet.ScenarioSheet.fromJson(ScenarioNet.gunzip(p.data()));
                sheetId = p.id();
            } catch (IOException | RuntimeException e) {
                message = "시트를 읽지 못했어요: " + e.getMessage();
            }
            refresh();
        }));
        ClientPlayNetworking.registerGlobalReceiver(ScenarioNet.LayoutPayload.ID, (p, ctx) -> ctx.client().execute(() -> {
            try {
                layout = p.data().length == 0 ? null : com.foredayner.scenario.layout.TownLayout.fromJson(ScenarioNet.gunzip(p.data()));
                layoutId = p.id();
            } catch (IOException | RuntimeException e) {
                message = "배치를 읽지 못했어요: " + e.getMessage();
            }
            refresh();
        }));
        ClientPlayNetworking.registerGlobalReceiver(ScenarioNet.BuildPayload.ID, (p, ctx) -> ctx.client().execute(() -> {
            try {
                build = com.foredayner.scenario.town.BuildState.fromJson(ScenarioNet.gunzip(p.data()));
                buildId = p.id();
                canUndo = p.undo();
            } catch (IOException | RuntimeException e) {
                message = "짓기 상태를 읽지 못했어요: " + e.getMessage();
            }
            refresh();
        }));
        ClientPlayNetworking.registerGlobalReceiver(ScenarioNet.ConfigS2C.ID, (p, ctx) -> ctx.client().execute(() -> {
            try {
                config = com.foredayner.scenario.store.ScenarioConfig.fromJson(p.json());
            } catch (RuntimeException ignored) {
            }
            refresh();
        }));
        ClientPlayNetworking.registerGlobalReceiver(ScenarioNet.StatusS2C.ID, (p, ctx) -> ctx.client().execute(() -> {
            try {
                ScenarioStore.Info[] list = new Gson().fromJson(p.scenarios(), ScenarioStore.Info[].class);
                stored = list == null ? List.of() : Arrays.asList(list);
            } catch (RuntimeException e) {
                stored = List.of();
            }
            if (!p.message().isEmpty()) {
                message = p.message();
            }
            if (!p.selected().isEmpty()) {
                selected = p.selected();
                loaded = null; // saved: the list shows it now
            }
            refresh();
        }));
    }

    public static ScenarioText.Loaded loaded() {
        return loaded;
    }

    public static List<ScenarioStore.Info> stored() {
        return stored;
    }

    public static String message() {
        return message;
    }

    public static String selected() {
        return selected;
    }

    public static void select(String id) {
        selected = id;
        if (!id.equals(sheetId)) {
            sheet = null;
        }
        if (!id.equals(layoutId)) {
            layout = null;
        }
        if (!id.equals(buildId)) {
            build = null;
        }
        if (ClientPlayNetworking.canSend(ScenarioNet.RequestC2S.ID)) {
            ClientPlayNetworking.send(new ScenarioNet.RequestC2S("open", id));
        }
        refresh();
    }

    /** The sheet of the selected scenario, or null if it has none yet. */
    public static com.foredayner.scenario.sheet.ScenarioSheet sheet() {
        return selected.equals(sheetId) ? sheet : null;
    }

    /** The layout of the selected scenario, or null if it has none yet. */
    public static com.foredayner.scenario.layout.TownLayout layout() {
        return selected.equals(layoutId) ? layout : null;
    }

    /** How building the selected scenario's town goes, or null. */
    public static com.foredayner.scenario.town.BuildState build() {
        return selected.equals(buildId) ? build : null;
    }

    public static boolean canUndo() {
        return canUndo && selected.equals(buildId);
    }

    /** build_here, build_start, build_stop, build_undo (the scenario), or build_redo, build_clear, build_tp (a place). */
    public static void buildAction(String action, String place) {
        if (!selected.isEmpty()) {
            ClientPlayNetworking.send(new ScenarioNet.RequestC2S(action, place == null ? selected : selected + "\n" + place));
        }
    }

    /** Tests: show a build state as if the server had sent it. */
    public static void showBuildForTests(String id, com.foredayner.scenario.town.BuildState s, boolean undo) {
        buildId = id;
        build = s;
        canUndo = undo;
        refresh();
    }

    /** Lay out the town: with the AI, or the plain automatic layout. */
    public static void makeLayout(boolean ai) {
        if (!selected.isEmpty()) {
            ClientPlayNetworking.send(new ScenarioNet.RequestC2S(ai ? "layout" : "layout_auto", selected));
        }
    }

    public static void reviseLayout(String instruction) {
        if (!selected.isEmpty() && instruction != null && !instruction.isBlank()) {
            ClientPlayNetworking.send(new ScenarioNet.RequestC2S("layout_revise", selected + "\n" + instruction.trim()));
        }
    }

    /** Save the GM's own change to the layout. */
    public static void saveLayout() {
        com.foredayner.scenario.layout.TownLayout l = layout();
        if (l != null) {
            ClientPlayNetworking.send(new ScenarioNet.LayoutPayload(selected, ScenarioNet.gzip(l.toJson())));
        }
    }

    /** Tests: show a layout as if the server had sent it. */
    public static void showLayoutForTests(String id, com.foredayner.scenario.layout.TownLayout l) {
        layoutId = id;
        layout = l;
        refresh();
    }

    public static ScenarioStore.Info selectedInfo() {
        for (ScenarioStore.Info i : stored) {
            if (i.id.equals(selected)) {
                return i;
            }
        }
        return null;
    }

    public static com.foredayner.scenario.store.ScenarioConfig config() {
        return config;
    }

    public static void saveModels(String analysis, String layout, String building) {
        com.foredayner.scenario.store.ScenarioConfig c = new com.foredayner.scenario.store.ScenarioConfig();
        c.analysisModel = analysis == null ? "" : analysis.trim();
        c.layoutModel = layout == null ? "" : layout.trim();
        c.buildingModel = building == null ? "" : building.trim();
        ClientPlayNetworking.send(new ScenarioNet.RequestC2S("models", c.toJson()));
    }

    /** Have the AI read the selected scenario into a sheet. */
    public static void analyze() {
        if (!selected.isEmpty()) {
            ClientPlayNetworking.send(new ScenarioNet.RequestC2S("analyze", selected));
        }
    }

    /** Set a place's reference picture by web address ("" clears it). */
    public static void refUrl(String place, String url) {
        if (!selected.isEmpty()) {
            ClientPlayNetworking.send(new ScenarioNet.RequestC2S(url == null || url.isBlank() ? "ref_clear" : "ref_url",
                selected + "\n" + place + "\n" + (url == null ? "" : url.trim())));
        }
    }

    /** Have the AI look at a place's picture again. */
    public static void refAgain(String place) {
        if (!selected.isEmpty()) {
            ClientPlayNetworking.send(new ScenarioNet.RequestC2S("ref_again", selected + "\n" + place));
        }
    }

    /** The system's file dialog for a picture; null if cancelled. */
    public static Path pickImage() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer filters = stack.mallocPointer(5);
            filters.put(stack.UTF8("*.png"));
            filters.put(stack.UTF8("*.jpg"));
            filters.put(stack.UTF8("*.jpeg"));
            filters.put(stack.UTF8("*.gif"));
            filters.put(stack.UTF8("*.webp"));
            filters.flip();
            String path = TinyFileDialogs.tinyfd_openFileDialog("참고 사진 고르기", "", filters, "사진 (.png .jpg .gif .webp)", false);
            return path == null ? null : Path.of(path);
        }
    }

    /** Send a picture file as a place's reference (in parts). */
    public static void refFile(String place, Path file) {
        try {
            byte[] all = java.nio.file.Files.readAllBytes(file);
            if (all.length > 4_500_000) {
                message = "사진이 너무 커요 (" + all.length / 1000 + " KB, 4500 KB까지).";
                refresh();
                return;
            }
            int total = Math.max(1, (all.length + ScenarioNet.PART_SIZE - 1) / ScenarioNet.PART_SIZE);
            for (int i = 0; i < total; i++) {
                byte[] part = Arrays.copyOfRange(all, i * ScenarioNet.PART_SIZE, Math.min(all.length, (i + 1) * ScenarioNet.PART_SIZE));
                ClientPlayNetworking.send(new ScenarioNet.RefUploadC2S(selected, place, i, total, part));
            }
            message = "사진을 보내는 중...";
        } catch (IOException e) {
            message = "사진을 읽지 못했어요: " + e.getMessage();
        }
        refresh();
    }

    /** Have the AI write the town's architecture guide. */
    public static void makeStyle() {
        if (!selected.isEmpty()) {
            ClientPlayNetworking.send(new ScenarioNet.RequestC2S("style", selected));
        }
    }

    /** Have the AI change the sheet as asked. */
    public static void revise(String instruction) {
        if (!selected.isEmpty() && instruction != null && !instruction.isBlank()) {
            ClientPlayNetworking.send(new ScenarioNet.RequestC2S("revise", selected + "\n" + instruction.trim()));
        }
    }

    /** Save the GM's own change to the sheet. */
    public static void saveSheet() {
        com.foredayner.scenario.sheet.ScenarioSheet s = sheet();
        if (s != null) {
            ClientPlayNetworking.send(new ScenarioNet.SheetPayload(selected, ScenarioNet.gzip(s.toJson())));
        }
    }

    /** Tests: show a sheet as if the server had sent it. */
    public static void showSheetForTests(String id, com.foredayner.scenario.sheet.ScenarioSheet s) {
        selected = id;
        sheetId = id;
        sheet = s;
        refresh();
    }

    public static void setMessage(String text) {
        message = text;
        refresh();
    }

    /** Ask the server for the stored scenarios. */
    public static void requestList() {
        if (ClientPlayNetworking.canSend(ScenarioNet.RequestC2S.ID)) {
            ClientPlayNetworking.send(new ScenarioNet.RequestC2S("list", ""));
        }
    }

    public static void delete(String id) {
        ClientPlayNetworking.send(new ScenarioNet.RequestC2S("delete", id));
    }

    /** The system's file dialog (.txt .md .pdf); null if cancelled. */
    public static Path pickFile() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer filters = stack.mallocPointer(4);
            filters.put(stack.UTF8("*.txt"));
            filters.put(stack.UTF8("*.md"));
            filters.put(stack.UTF8("*.markdown"));
            filters.put(stack.UTF8("*.pdf"));
            filters.flip();
            String path = TinyFileDialogs.tinyfd_openFileDialog("시나리오 파일 고르기", "", filters, "시나리오 (.txt .md .pdf)", false);
            return path == null ? null : Path.of(path);
        }
    }

    public static void loadFile(Path file) {
        try {
            loaded = ScenarioText.load(file);
            message = loaded.name() + " 불러왔어요 (" + loaded.text().length() + "자)"
                + (loaded.cut() ? ", " + loaded.originalChars() + "자 중 앞부분만 써요" : "") + ". 저장을 누르세요.";
        } catch (IOException | RuntimeException e) {
            message = "읽지 못했어요: " + e.getMessage();
        }
        refresh();
    }

    public static void loadText(String name, String text) {
        if (text == null || text.isBlank()) {
            message = "클립보드가 비어 있어요.";
        } else {
            loaded = ScenarioText.fromText(name, text);
            message = "붙여 넣었어요 (" + loaded.text().length() + "자). 이름을 확인하고 저장을 누르세요.";
        }
        refresh();
    }

    public static void rename(String name) {
        if (loaded != null && name != null && !name.isBlank()) {
            loaded = new ScenarioText.Loaded(name.trim(), loaded.text(), loaded.cut(), loaded.originalChars());
        }
    }

    public static void clearLoaded() {
        loaded = null;
        refresh();
    }

    /** Send the loaded scenario to the server (gzipped, in parts). */
    public static void upload() {
        if (loaded == null) {
            return;
        }
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (GZIPOutputStream gz = new GZIPOutputStream(bytes)) {
                gz.write(loaded.text().getBytes(StandardCharsets.UTF_8));
            }
            byte[] all = bytes.toByteArray();
            int total = Math.max(1, (all.length + ScenarioNet.PART_SIZE - 1) / ScenarioNet.PART_SIZE);
            int id = uploadId++;
            for (int i = 0; i < total; i++) {
                byte[] part = Arrays.copyOfRange(all, i * ScenarioNet.PART_SIZE, Math.min(all.length, (i + 1) * ScenarioNet.PART_SIZE));
                ClientPlayNetworking.send(new ScenarioNet.UploadC2S(id, i, total, loaded.name(), loaded.cut(), part));
            }
            message = "서버로 보내는 중...";
        } catch (IOException e) {
            message = "보내지 못했어요: " + e.getMessage();
        }
        refresh();
    }

    private static void refresh() {
        if (MinecraftClient.getInstance().currentScreen instanceof ScenarioScreen screen) {
            screen.refresh();
        } else if (MinecraftClient.getInstance().currentScreen instanceof StyleScreen screen) {
            screen.refresh();
        } else if (MinecraftClient.getInstance().currentScreen instanceof RefImageScreen screen) {
            screen.refresh();
        }
    }
}

package com.foredayner.scenario.client;

import com.example.client.WoodUi;
import com.foredayner.scenario.input.ScenarioText;
import com.foredayner.scenario.store.ScenarioStore;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Text;

import java.nio.file.Path;
import java.util.List;

/**
 * The AI Scenario Workshop: 시나리오 (load the scenario file) -> 시트 (what the AI read from it) ->
 * 배치도 (the town layout, editable) -> 짓기 (building, place by place). Tabs open up as the
 * steps are done.
 */
public class ScenarioScreen extends Screen {

    public enum Tab {
        SCENARIO("시나리오"), SHEET("시트"), LAYOUT("배치도"), BUILD("짓기"), LOG("로그");

        final String label;

        Tab(String label) {
            this.label = label;
        }
    }

    private static Tab tab = Tab.SCENARIO;

    /** Tests: switch the open workshop to a tab. */
    public static void showTab(Tab t) {
        tab = t;
        if (net.minecraft.client.MinecraftClient.getInstance().currentScreen instanceof ScenarioScreen s) {
            s.clearAndInit();
        }
    }
    private TextFieldWidget nameField;

    /** Tests: move the town view (blocks). */
    public static void panForTests(int x, int z) {
        panX = x;
        panZ = z;
        if (net.minecraft.client.MinecraftClient.getInstance().currentScreen instanceof ScenarioScreen s) {
            s.clearAndInit();
        }
    }

    /** Tests: pick a place on the open layout. */
    public static void pickForTests(String id) {
        if (net.minecraft.client.MinecraftClient.getInstance().currentScreen instanceof ScenarioScreen s) {
            s.picked = id;
            s.showPicked();
            s.clearAndInit();
        }
    }

    public ScenarioScreen() {
        super(Text.literal("AI 시나리오 공방"));
    }

    private int left() {
        return Math.max(10, this.width / 2 - 270);
    }

    private int right() {
        return Math.min(this.width - 10, this.width / 2 + 270);
    }

    private int top() {
        return 26;
    }

    private int bottom() {
        return this.height - 12;
    }

    private int mid() {
        return (left() + right()) / 2 + 30;
    }

    private int bodyTop() {
        return top() + 32;
    }

    /** Rebuild the widgets after the state changed. */
    public void refresh() {
        String name = nameField == null ? null : nameField.getText();
        String ask = askField == null ? null : askField.getText();
        clearAndInit();
        if (ask != null && askField != null) {
            askField.setText(ask);
        }
        if (name != null && nameField != null && ScenarioClientState.loaded() != null) {
            nameField.setText(name);
        }
    }

    @Override
    protected void init() {
        int x = left() + 6;
        for (Tab t : Tab.values()) {
            boolean ready = switch (t) { // later steps open as the earlier ones are done
                case SCENARIO -> true;
                case SHEET -> !ScenarioClientState.selected().isEmpty();
                case LAYOUT -> ScenarioClientState.sheet() != null;
                case BUILD -> ScenarioClientState.layout() != null;
                case LOG -> true;
            };
            // The 로그 tab counts what came while it was closed (red when something went wrong).
            String label = t == Tab.LOG && ScenarioClientState.unread() > 0 && tab != Tab.LOG
                ? t.label + " " + (ScenarioClientState.unreadError() ? "§c" : "§6") + "(" + Math.min(99, ScenarioClientState.unread()) + ")"
                : t.label;
            WoodUi.WoodButton button = new WoodUi.WoodButton(x, top() + 6, 70, 18, Text.literal(label), b -> {
                tab = t;
                clearAndInit();
            }, false).selected(t == tab);
            button.active = ready;
            addDrawableChild(button);
            x += 74;
        }
        if (tab == Tab.SHEET && ScenarioClientState.selected().isEmpty()) {
            tab = Tab.SCENARIO;
        }
        if (tab == Tab.BUILD && ScenarioClientState.layout() == null) {
            tab = Tab.LAYOUT;
        }
        if (tab == Tab.LAYOUT && ScenarioClientState.sheet() == null) {
            tab = ScenarioClientState.selected().isEmpty() ? Tab.SCENARIO : Tab.SHEET;
        }
        ScenarioClientState.setLogOpen(tab == Tab.LOG);
        if (tab == Tab.LOG) {
            initLogTab();
        } else if (tab == Tab.SCENARIO) {
            initScenarioTab();
        } else if (tab == Tab.SHEET) {
            initSheetTab();
        } else if (tab == Tab.LAYOUT) {
            initLayoutTab();
        } else if (tab == Tab.BUILD) {
            initBuildTab();
        }
    }

    private void initScenarioTab() {
        int l = left() + 12, y = bodyTop() + 8;
        addDrawableChild(new WoodUi.WoodButton(l, y, 96, 18, Text.literal("파일 고르기"), b -> {
            Path file = ScenarioClientState.pickFile();
            if (file != null) {
                ScenarioClientState.loadFile(file);
            }
        }, false));
        addDrawableChild(new WoodUi.WoodButton(l + 100, y, 96, 18, Text.literal("붙여넣기"), b ->
            ScenarioClientState.loadText("붙여넣은 시나리오", this.client.keyboard.getClipboard()), false));
        ScenarioText.Loaded loaded = ScenarioClientState.loaded();
        if (loaded != null) {
            nameField = new TextFieldWidget(this.textRenderer, l + 34, y + 26, mid() - l - 52, 14, Text.literal("이름"));
            nameField.setMaxLength(40);
            nameField.setText(loaded.name());
            nameField.setChangedListener(ScenarioClientState::rename);
            addDrawableChild(nameField);
            addDrawableChild(new WoodUi.WoodButton(l, bottom() - 44, 80, 18, Text.literal("저장"), b -> ScenarioClientState.upload(), false));
            addDrawableChild(new WoodUi.WoodButton(l + 84, bottom() - 44, 60, 18, Text.literal("지우기"),
                b -> ScenarioClientState.clearLoaded(), true));
        } else {
            nameField = null;
        }
        // Stored scenarios: pick one to go on with, or remove it.
        int rx = mid() + 10, ry = bodyTop() + 24;
        for (ScenarioStore.Info info : ScenarioClientState.stored()) {
            if (ry > bottom() - 70) {
                break;
            }
            String label = info.name + "  (" + info.chars + "자)";
            boolean sel = info.id.equals(ScenarioClientState.selected());
            addDrawableChild(new WoodUi.WoodButton(rx, ry, right() - rx - 34, 18, Text.literal(label),
                b -> ScenarioClientState.select(info.id), false).selected(sel));
            addDrawableChild(new WoodUi.WoodButton(right() - 30, ry, 20, 18, Text.literal("X"),
                b -> ScenarioClientState.delete(info.id), true));
            ry += 21;
        }
        boolean hasSheet = ScenarioClientState.selectedInfo() != null && !"text".equals(ScenarioClientState.selectedInfo().stage);
        WoodUi.WoodButton next = new WoodUi.WoodButton(rx, bottom() - 44, right() - rx - 10, 18,
            Text.literal(hasSheet ? "시트 보기 →" : "AI로 시트 만들기 →"), b -> {
                if (!hasSheet) {
                    ScenarioClientState.analyze();
                }
                tab = Tab.SHEET;
                clearAndInit();
            }, false);
        next.active = !ScenarioClientState.selected().isEmpty();
        addDrawableChild(next);
    }

    // ------------------------------------------------------------------ 시트

    private static final int ROW = 20;
    private int[] scroll = new int[3];
    private TextFieldWidget askField;

    /** Two columns: places and clues (NPCs are kept in the sheet but not shown). */
    private int colWidth() {
        return (right() - left() - 24) / 2;
    }

    private int colX(int i) {
        return left() + 12 + i * (colWidth() + 2);
    }

    private int listTop() {
        return bodyTop() + 50;
    }

    /** A button label that fits its button. */
    private Text fit(String label, int width) {
        String s = label;
        if (this.textRenderer.getWidth(s) > width - 8) {
            s = this.textRenderer.trimToWidth(s, width - 14) + "…";
        }
        return Text.literal(s);
    }

    private int listBottom() {
        return bottom() - 52;
    }

    private void initSheetTab() {
        int l = left() + 12, y = bodyTop() + 6;
        addDrawableChild(new WoodUi.WoodButton(right() - 104, y, 92, 18, Text.literal("AI로 다시 읽기"),
            b -> ScenarioClientState.analyze(), false));
        addDrawableChild(new WoodUi.WoodButton(right() - 182, y, 74, 18, Text.literal("모델 설정"),
            b -> this.client.setScreen(new ModelSettingsScreen(this)), false));
        var sheet = ScenarioClientState.sheet();
        if (sheet != null) {
            // Add a place (or a room) by hand.
            addDrawableChild(new WoodUi.WoodButton(colX(0) + colWidth() - 54, listTop() - 17, 52, 14,
                Text.literal(sheet.building() ? "+ 방" : "+ 장소"), b -> this.client.setScreen(new AddPlaceScreen(this)), false));
            boolean hasGuide = sheet.styleGuide != null && !sheet.styleGuide.isBlank();
            WoodUi.WoodButton style = new WoodUi.WoodButton(right() - 266, y, 80, 18, fit(hasGuide ? "스타일 가이드" : "스타일 가이드 +", 80),
                b -> this.client.setScreen(new StyleScreen(this)), false);
            style.setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal(hasGuide
                ? "모든 건물이 따르는 시대·지역 건축 규칙. 눌러서 보고 고치기."
                : "아직 없어요. 짓기를 시작하면 AI가 먼저 만들어요. 미리 만들고 고칠 수도 있어요.")));
            addDrawableChild(style);
            // The period and culture: picks the building checklists, reference cards and the streets.
            String period = sheet.period();
            WoodUi.WoodButton periodButton = new WoodUi.WoodButton(right() - 352, y, 82, 18,
                fit(com.foredayner.scenario.sheet.Period.label(period), 82), b -> {
                    var all = com.foredayner.scenario.sheet.Period.ALL;
                    sheet.period = all.get((all.indexOf(period) + 1) % all.size());
                    ScenarioClientState.saveSheet();
                    clearAndInit();
                }, false);
            periodButton.setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal("시대·문화권: "
                + com.foredayner.scenario.sheet.Period.description(period) + "\n눌러서 바꾸기. 건물 체크리스트, 참고 설명, 길과 가로등이 따라 바뀌어요.")));
            addDrawableChild(periodButton);
            // Around the town: a ring of land (open), the same ending in hills with the roads blocked (closed), or nothing.
            String outer = sheet.outer == null ? "open" : sheet.outer;
            // On the places column's header row (the top row is full on small windows).
            WoodUi.WoodButton outerButton = new WoodUi.WoodButton(colX(0) + colWidth() - 68, listTop() - 16, 66, 14,
                fit("바깥: " + ("closed".equals(outer) ? "닫힘" : "none".equals(outer) ? "없음" : "열림"), 66), b -> {
                    var all = com.foredayner.scenario.sheet.ScenarioSheet.OUTERS;
                    sheet.outer = all.get((Math.max(0, all.indexOf(outer)) + 1) % all.size());
                    ScenarioClientState.saveSheet();
                    clearAndInit();
                }, false);
            outerButton.setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal(
                "마을 땅 바깥 (폭 " + com.foredayner.scenario.town.OuterBand.WIDTH + "칸):\n"
                    + "열림: 마을 가장자리를 이어 들판, 숲, 언덕, 길을 만들고 주변 땅에 부드럽게 이어요.\n"
                    + "닫힘: 바깥이 가파른 숲 언덕으로 막히고, 나가는 길은 낙석으로 막혀요 (고립되는 시나리오).\n"
                    + "없음: 마을 땅만 지어요.\n짓기 전에 정해 주세요.")));
            addDrawableChild(outerButton);
            // The stage: a town of buildings, or one building whose rooms are the places.
            WoodUi.WoodButton stageButton = new WoodUi.WoodButton(colX(0) + colWidth() - 136, listTop() - 16, 66, 14,
                fit(sheet.building() ? "무대: 건물" : "무대: 마을", 66), b -> {
                    sheet.stage = sheet.building() ? "town" : "building";
                    ScenarioClientState.saveSheet();
                    ScenarioClientState.setMessage(sheet.building()
                        ? "무대를 건물로 바꿨어요. \"AI로 다시 읽기\"를 누르면 장소를 이 건물의 방(층별)으로 다시 읽어요."
                        : "무대를 마을로 바꿨어요. \"AI로 다시 읽기\"로 장소를 건물들로 다시 읽어요.");
                    clearAndInit();
                }, false);
            stageButton.setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal(
                "마을: 장소가 마을의 건물들이에요.\n건물: 시나리오 전체가 한 건물(저택, 병원, 배...) 안이에요. 장소가 방이 되고, "
                    + "층별 평면도로 배치해서 벽·바닥·문·계단은 코드가, 방 꾸미기는 AI가 해요.")));
            addDrawableChild(stageButton);
        }
        if (sheet != null) {
            int maxRows = Math.max(1, (listBottom() - listTop()) / ROW);
            // Places: name (details on hover), size, remove.
            int cx = colX(0), w = colWidth();
            int i = 0;
            for (int k = scroll[0]; k < sheet.places.size() && i < maxRows; k++, i++) {
                var p = sheet.places.get(k);
                int ry = listTop() + i * ROW;
                WoodUi.WoodButton name = new WoodUi.WoodButton(cx, ry, w - 58, 18, fit(p.name + (p.filler ? " ·" : ""), w - 58), b -> { }, false);
                name.setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal(placeDetail(p))));
                addDrawableChild(name);
                addDrawableChild(new WoodUi.WoodButton(cx + w - 56, ry, 32, 18, Text.literal(p.size), b -> {
                    int s = com.foredayner.scenario.sheet.ScenarioSheet.SIZES.indexOf(p.size);
                    p.size = com.foredayner.scenario.sheet.ScenarioSheet.SIZES.get((s + 1) % 4);
                    ScenarioClientState.saveSheet();
                }, false));
                addDrawableChild(new WoodUi.WoodButton(cx + w - 22, ry, 20, 18, Text.literal("X"), b -> {
                    sheet.places.remove(p);
                    ScenarioClientState.saveSheet();
                }, true));
            }
            // Clues: name, hidden or not, remove.
            cx = colX(1);
            i = 0;
            for (int k = scroll[2]; k < sheet.clues.size() && i < maxRows; k++, i++) {
                var c = sheet.clues.get(k);
                int ry = listTop() + i * ROW;
                WoodUi.WoodButton name = new WoodUi.WoodButton(cx, ry, w - 62, 18, fit(c.name, w - 62), b -> { }, false);
                name.setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal(c.name + " [" + formKo(c.form) + "]\n장소: "
                    + placeName(sheet, c.place) + (c.room.isBlank() ? "" : " / " + c.room) + "\n" + c.text)));
                addDrawableChild(name);
                addDrawableChild(new WoodUi.WoodButton(cx + w - 60, ry, 36, 18, Text.literal(c.hidden ? "숨김" : "보임"), b -> {
                    c.hidden = !c.hidden;
                    ScenarioClientState.saveSheet();
                }, false));
                addDrawableChild(new WoodUi.WoodButton(cx + w - 22, ry, 20, 18, Text.literal("X"), b -> {
                    sheet.clues.remove(c);
                    ScenarioClientState.saveSheet();
                }, true));
            }
            // Ask the AI to change it.
            askField = new TextFieldWidget(this.textRenderer, l + 2, bottom() - 42, right() - l - 210, 14, Text.literal("요청"));
            askField.setMaxLength(300);
            askField.setPlaceholder(Text.literal("AI에게 고쳐 달라고 하기"));
            addDrawableChild(askField);
            addDrawableChild(new WoodUi.WoodButton(right() - 202, bottom() - 44, 50, 18, Text.literal("요청"), b -> {
                ScenarioClientState.revise(askField.getText());
                askField.setText("");
            }, false));
            boolean hasLayout = ScenarioClientState.layout() != null;
            boolean tooMany = sheet.counted() > com.foredayner.scenario.sheet.ScenarioSheet.MAX_PLACES;
            WoodUi.WoodButton next = new WoodUi.WoodButton(right() - 148, bottom() - 44, 136, 18,
                fit(tooMany ? "장소를 " + com.foredayner.scenario.sheet.ScenarioSheet.MAX_PLACES + "곳 이하로" : hasLayout ? "배치도 보기 →" : "AI로 배치도 만들기 →", 136), b -> {
                    if (!hasLayout) {
                        ScenarioClientState.makeLayout(true);
                    }
                    tab = Tab.LAYOUT;
                    clearAndInit();
                }, false);
            next.active = !tooMany || hasLayout;
            if (tooMany) {
                next.setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal("장소가 " + sheet.places.size() + "곳이에요. 160x160 땅에는 "
                    + com.foredayner.scenario.sheet.ScenarioSheet.MAX_PLACES + "곳까지 놓을 수 있어요. X로 빼거나 AI에게 합쳐 달라고 하세요.")));
            }
            addDrawableChild(next);
        } else {
            askField = null;
        }
    }

    // ------------------------------------------------------------------ 로그

    private static final int LOG_ALL = 0, LOG_ERRORS = 1, LOG_AI = 2, LOG_BUILD = 3;
    private static int logFilter = LOG_ALL;
    /** Lines up from the newest (0 = following the newest). */
    private static int logScroll;

    private void initLogTab() {
        int x = left() + 12, y = bodyTop() + 4;
        String[] names = { "전체", "오류", "AI", "짓기" };
        for (int i = 0; i < names.length; i++) {
            int f = i;
            addDrawableChild(new WoodUi.WoodButton(x + i * 54, y, 50, 18, Text.literal(names[i]), b -> {
                logFilter = f;
                logScroll = 0;
                clearAndInit();
            }, false).selected(logFilter == i));
        }
        addDrawableChild(new WoodUi.WoodButton(right() - 72, y, 60, 18, Text.literal("지우기"), b -> {
            ScenarioClientState.clearLog();
            logScroll = 0;
            clearAndInit();
        }, true));
    }

    private static boolean logShows(ScenarioClientState.LogLine line) {
        return switch (logFilter) {
            case LOG_ERRORS -> line.kind() == ScenarioClientState.ERROR || line.kind() == ScenarioClientState.WARN;
            case LOG_AI -> line.kind() == ScenarioClientState.AI || line.text().contains("AI");
            case LOG_BUILD -> line.kind() == ScenarioClientState.BUILD || line.text().matches("(?s).*(짓|지었|땅|치웠).*");
            default -> true;
        };
    }

    private static int logColor(int kind) {
        return switch (kind) {
            case ScenarioClientState.ERROR -> 0xFFB0281A;
            case ScenarioClientState.WARN -> 0xFFC06A10;
            case ScenarioClientState.DONE -> 0xFF2E6B2E;
            case ScenarioClientState.AI -> 0xFF2E5FA4;
            default -> WoodUi.INK;
        };
    }

    private static final java.text.SimpleDateFormat CLOCK = new java.text.SimpleDateFormat("HH:mm:ss");

    private void renderLog(DrawContext ctx) {
        int l = left() + 12, r = right() - 14, t = bodyTop() + 28, b = bottom() - 6;
        List<OrderedText> rows = new java.util.ArrayList<>();
        List<Integer> colors = new java.util.ArrayList<>();
        for (ScenarioClientState.LogLine line : ScenarioClientState.logLines()) {
            if (!logShows(line)) {
                continue;
            }
            String text = CLOCK.format(new java.util.Date(line.time())) + "  " + line.text();
            for (OrderedText part : this.textRenderer.wrapLines(StringVisitable.plain(text), r - l)) {
                rows.add(part);
                colors.add(logColor(line.kind()));
            }
        }
        if (rows.isEmpty()) {
            ctx.drawText(this.textRenderer, "아직 로그가 없어요.", l, t + 4, WoodUi.INK_SOFT, false);
            return;
        }
        int fit = Math.max(1, (b - t) / 10);
        logScroll = Math.max(0, Math.min(logScroll, Math.max(0, rows.size() - fit)));
        int end = rows.size() - logScroll;
        int start = Math.max(0, end - fit);
        int y = t;
        for (int i = start; i < end; i++) {
            ctx.drawText(this.textRenderer, rows.get(i), l, y, colors.get(i), false);
            y += 10;
        }
        if (rows.size() > fit) {
            int h = b - t, barH = Math.max(6, h * fit / rows.size());
            int barY = t + (h - barH) * (rows.size() - fit - logScroll) / Math.max(1, rows.size() - fit);
            ctx.fill(right() - 9, t, right() - 7, b, 0x40000000);
            ctx.fill(right() - 9, barY, right() - 7, barY + barH, 0xFF6B4A2B);
        }
    }

    /** Errors and things done show at the top for a moment, whatever tab is open. */
    private void renderToast(DrawContext ctx) {
        var toast = ScenarioClientState.toast();
        if (toast == null || tab == Tab.LOG) {
            return;
        }
        // Beside the title, not over it.
        int room = right() - ((left() + right()) / 2 + 70);
        String text = this.textRenderer.trimToWidth(toast.text(), Math.max(40, room - 16));
        int w = this.textRenderer.getWidth(text) + 16;
        int x = right() - w, y = 3;
        ctx.fill(x, y, x + w, y + 16, 0xF0F4E4C1);
        ctx.fill(x, y + 15, x + w, y + 16, logColor(toast.kind()));
        ctx.drawText(this.textRenderer, text, x + 8, y + 4, logColor(toast.kind()), false);
    }

    // ------------------------------------------------------------------ 배치도

    private static final int GROUND = 0xFF8DAA63;
    private String picked = "";
    private boolean dragging;
    private boolean dragMoved;
    private int dragDx;
    private int dragDz;
    private List<String> layoutProblems = List.of();

    private int mapX() {
        return left() + 12;
    }

    private int mapY() {
        return bodyTop() + 6;
    }

    private int mapSize() {
        return Math.max(60, Math.min(bottom() - 26 - mapY(), (right() - left()) / 2));
    }

    private float scale() {
        return mapSize() / (float) view()[2];
    }

    /** What the plan shows: {x, z, span}: the whole ground, or a building stage's building with a margin. */
    private static int[] view() {
        var layout = ScenarioClientState.layout();
        int size = layout == null ? com.foredayner.scenario.layout.TownLayout.SIZE : layout.size;
        if (layout != null && !layout.building()) {
            // A town: moved with the right mouse button, up to a quarter of the map past each edge.
            int r = layout.reach();
            panX = Math.max(-r, Math.min(r, panX));
            panZ = Math.max(-r, Math.min(r, panZ));
            return new int[] { panX, panZ, size };
        }
        if (layout == null || !layout.building() || layout.sites.isEmpty()) {
            return new int[] { 0, 0, size };
        }
        int[] b = com.foredayner.scenario.layout.IndoorPlan.bounds(layout);
        int span = Math.min(size, Math.max(b[2] - b[0], b[3] - b[1]) + 1 + 12);
        int cx = (b[0] + b[2]) / 2, cz = (b[1] + b[3]) / 2;
        return new int[] { Math.max(0, Math.min(size - span, cx - span / 2)), Math.max(0, Math.min(size - span, cz - span / 2)), span };
    }

    private int panelX() {
        return mapX() + mapSize() + 10;
    }

    private static final int ENTRY = 11;
    /** Room under the list: the picked place's buttons, its corner line and the check. */
    private static final int CONTROLS = 88;
    /** "통로 잇기": the place a new tunnel starts from, until the next place is clicked on the map ("" = not linking). */
    private static String linkFrom = "";
    /** Whether the keeper's map lies over the layout preview. */
    private static boolean showMap = true;
    /** The reset button was pressed once (press again to clear). */
    private boolean resetArmed;
    /** "Remove from the layout" was pressed for this built place: ask whether to clear it in the world too. */
    private String removeAsk = "";
    /** The map toggle sits on the preview, which is drawn after the widgets: drawn again on top. */
    private WoodUi.WoodButton overlayButton;
    /** The layout on/off toggle, also on the preview. */
    private WoodUi.WoodButton planButton;
    private static boolean showPlan = true;
    private int listScroll;
    /** Where the town view is moved to (blocks), and the right-button drag that moves it. */
    private static int panX;
    private static int panZ;
    private boolean panning;
    private double panFromX;
    private double panFromY;
    private int panStartX;
    private int panStartZ;

    /** The row under the map: the two switches, then what the colours mean. */
    private int belowMapY() {
        return mapY() + mapSize() + 5;
    }

    private int entriesTop() {
        return bodyTop() + 30;
    }

    private int entryWidth() {
        return (right() - 12 - panelX()) / 2;
    }

    /** The places in the list: every place in the sheet (numbered in sheet order), placed or not. */
    private static List<com.foredayner.scenario.sheet.ScenarioSheet.Place> listed() {
        var sheet = ScenarioClientState.sheet();
        return sheet == null ? List.of() : sheet.places;
    }

    private int listRows() {
        return (listed().size() + 1) / 2;
    }

    /** How many list rows fit above the controls and the request field. */
    private int visibleRows() {
        return Math.max(2, (bottom() - 46 - CONTROLS - 4 - entriesTop()) / ENTRY);
    }

    /** Where the place list ends and the picked place's buttons go. */
    private int controlsY() {
        return entriesTop() + Math.min(listRows(), visibleRows()) * ENTRY + 4;
    }

    private void clampListScroll() {
        listScroll = Math.max(0, Math.min(listScroll, listRows() - visibleRows()));
    }

    /** Scroll the list so the picked place shows. */
    private void showPicked() {
        var places = listed();
        for (int i = 0; i < places.size(); i++) {
            if (places.get(i).id.equals(picked)) {
                int row = i / 2;
                if (row < listScroll) {
                    listScroll = row;
                } else if (row >= listScroll + visibleRows()) {
                    listScroll = row - visibleRows() + 1;
                }
            }
        }
        clampListScroll();
    }

    /** The list entry under the mouse (index into the sheet's places), or -1. */
    private int entryAt(double mx, double my) {
        if (ScenarioClientState.layout() == null || mx < panelX() || my < entriesTop() || my >= controlsY() - 4) {
            return -1;
        }
        int col = (int) ((mx - panelX()) / entryWidth());
        int row = (int) ((my - entriesTop()) / ENTRY) + listScroll;
        int i = row * 2 + col;
        return col <= 1 && i < listed().size() ? i : -1;
    }

    private void initLayoutTab() {
        int px = panelX(), y = bodyTop() + 6, pw = right() - 12 - px;
        int bw = (pw - 12) / 4;
        var layout = ScenarioClientState.layout();
        var sheet = ScenarioClientState.sheet();
        boolean tooMany = sheet != null && sheet.counted() > com.foredayner.scenario.sheet.ScenarioSheet.MAX_PLACES;
        boolean hasMap = sheet != null && sheet.mapImage != null && !sheet.mapImage.isBlank();
        WoodUi.WoodButton ai = new WoodUi.WoodButton(px, y, bw, 18, fit("AI로 배치", bw), b -> ScenarioClientState.makeLayout(true), false);
        ai.setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal(hasMap
            ? "AI가 참고 지도를 보고 지도대로 배치해요." : "AI가 이야기에 맞게 배치해요. 참고 지도를 올리면 지도를 따라요.")));
        WoodUi.WoodButton plain = new WoodUi.WoodButton(px + bw + 4, y, bw, 18, fit("기본 배치", bw), b -> ScenarioClientState.makeLayout(false), false);
        ai.active = plain.active = !tooMany;
        if (tooMany) {
            var tip = net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal("장소가 " + sheet.counted() + "곳이에요. 시트에서 "
                + com.foredayner.scenario.sheet.ScenarioSheet.MAX_PLACES + "곳 이하로 줄여 주세요."));
            ai.setTooltip(tip);
            plain.setTooltip(tip);
        }
        addDrawableChild(ai);
        addDrawableChild(plain);
        WoodUi.WoodButton mapButton = new WoodUi.WoodButton(px + 2 * (bw + 4), y, bw, 18, fit(hasMap ? "참고 지도 ✓" : "참고 지도", bw),
            b -> this.client.setScreen(new MapScreen(this)), false);
        mapButton.active = sheet != null;
        mapButton.setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal("마을 지도 그림을 올리면 AI가 지도를 따라 배치해요.")));
        addDrawableChild(mapButton);
        WoodUi.WoodButton reset = new WoodUi.WoodButton(px + 3 * (bw + 4), y, bw, 18, fit(resetArmed ? "정말 지울까요?" : "배치 초기화", bw), b -> {
            if (resetArmed) {
                resetArmed = false;
                picked = "";
                ScenarioClientState.clearLayout();
            } else {
                resetArmed = true;
            }
            clearAndInit();
        }, true);
        reset.active = layout != null;
        reset.setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal(
            "장소 자리, 길, 구역을 모두 지우고 빈 배치도로 돌아가요 (두 번 누르기). 지어진 마을은 그대로예요.")));
        addDrawableChild(reset);
        // Under the map (not on it, where they would hide what is under them): the map and the layout on or off.
        overlayButton = null;
        planButton = null;
        int sx = mapX();
        if (sheet != null && MapOverlay.available()) {
            WoodUi.WoodButton overlay = new WoodUi.WoodButton(sx, belowMapY(), 70, 14, Text.literal(showMap ? "참고 지도 ✓" : "참고 지도"), b -> {
                showMap = !showMap;
                clearAndInit();
            }, false).selected(showMap);
            overlay.setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal("참고 지도를 배치도 위에 흐리게 겹쳐 보기")));
            addDrawableChild(overlay);
            sx += 74;
        }
        if (layout != null) {
            WoodUi.WoodButton plan = new WoodUi.WoodButton(sx, belowMapY(), 70, 14,
                Text.literal(showPlan ? "건물 배치 ✓" : "건물 배치"), b -> {
                    showPlan = !showPlan;
                    clearAndInit();
                }, false).selected(showPlan);
            plan.setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal("장소, 길, 구역을 보이거나 숨기기")));
            addDrawableChild(plan);
        }
        if (layout == null || sheet == null) {
            askField = null;
            layoutProblems = List.of();
            return;
        }
        layoutProblems = layout.check(sheet);
        clampListScroll();
        // A building's rooms are changed by asking the AI (or by the floor plans): no controls per room.
        var place = layout.building() ? null : sheet.place(picked);
        var site = layout.site(picked);
        int by = controlsY();
        if (layout.building()) {
            // A building: pick the floor to see; rooms are changed by asking the AI (or by the floor plans).
            int low = 0, high = 0;
            for (var s : layout.sites) {
                low = Math.min(low, s.level);
                high = Math.max(high, s.level);
            }
            floorShown = Math.max(low, Math.min(high, floorShown));
            int lo = low, hi = high;
            WoodUi.WoodButton down = new WoodUi.WoodButton(px, by, 20, 18, Text.literal("▼"), b -> {
                floorShown = Math.max(lo, floorShown - 1);
                picked = "";
                clearAndInit();
            }, false);
            down.active = floorShown > low;
            addDrawableChild(down);
            addDrawableChild(new WoodUi.WoodButton(px + 22, by, 70, 18, Text.literal(floorKo(floorShown)), b -> { }, false));
            WoodUi.WoodButton up = new WoodUi.WoodButton(px + 94, by, 20, 18, Text.literal("▲"), b -> {
                floorShown = Math.min(hi, floorShown + 1);
                picked = "";
                clearAndInit();
            }, false);
            up.active = floorShown < high;
            addDrawableChild(up);
        }
        if (place != null && site != null) {
            addDrawableChild(new WoodUi.WoodButton(px, by, 66, 18, Text.literal("입구 " + sideKo(site.front)), b -> {
                int i = com.foredayner.scenario.layout.TownLayout.FRONTS.indexOf(site.front);
                site.front = com.foredayner.scenario.layout.TownLayout.FRONTS.get((i + 1) % 4);
                ScenarioClientState.saveLayout();
                clearAndInit();
            }, false));
        } else if (place != null) {
            WoodUi.WoodButton put = new WoodUi.WoodButton(px, by, 66, 18, fit("빈 자리에 놓기", 66), b -> {
                if (layout.addFree(place)) {
                    ScenarioClientState.saveLayout();
                } else {
                    ScenarioClientState.setMessage(place.name + ": 빈 자리가 없어요. 크기를 줄이거나 다른 장소를 옮겨 보세요.");
                }
                clearAndInit();
            }, false);
            addDrawableChild(put);
        }
        if (place != null && !place.outdoor) {
            WoodUi.WoodButton cond = new WoodUi.WoodButton(px + 126, by, 46, 18, Text.literal(conditionKo(place.condition)), b -> {
                int i = com.foredayner.scenario.sheet.ScenarioSheet.CONDITIONS.indexOf(place.condition);
                place.condition = com.foredayner.scenario.sheet.ScenarioSheet.CONDITIONS.get((i + 1) % 4);
                ScenarioClientState.saveSheet();
                clearAndInit();
            }, false);
            cond.setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal("건물 상태: 깔끔 / 낡음 / 폐허 / 불길. 지을 때 낡은 정도와 조명에 반영돼요.")));
            addDrawableChild(cond);
        }
        if (place != null) {
            boolean hasRef = place.refImage != null && !place.refImage.isBlank();
            WoodUi.WoodButton photo = new WoodUi.WoodButton(px + 176, by, 46, 18, Text.literal(hasRef ? "사진 ✓" : "사진"),
                b -> this.client.setScreen(new RefImageScreen(this, place.id)), false);
            photo.setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal(hasRef
                ? "참고 사진: " + place.refImage + (place.refDescription.isBlank() ? " (아직 읽지 않음)" : " (읽음)")
                : "실제 건물 사진을 붙이면 AI가 보고 따라 지어요.")));
            addDrawableChild(photo);
            // Second row: building or outdoor, and taking it off the layout.
            int by2 = by + 22;
            WoodUi.WoodButton kind = new WoodUi.WoodButton(px, by2, 66, 18, Text.literal(place.outdoor ? "야외" : "건물"), b -> {
                place.outdoor = !place.outdoor;
                if (place.outdoor && !java.util.Set.of("woods", "cemetery", "dock", "beach", "square", "cave", "scene", "landmark",
                    "pyeongsang", "pine").contains(place.kind)) {
                    place.kind = "landmark";
                    place.basement = false;
                } else if (!place.outdoor && java.util.Set.of("woods", "cemetery", "dock", "beach", "square", "cave", "scene", "landmark",
                    "pyeongsang", "pine").contains(place.kind)) {
                    place.kind = "house";
                }
                ScenarioClientState.saveSheet();
                layout.check(sheet);
                ScenarioClientState.saveLayout();
                clearAndInit();
            }, false);
            kind.setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal(place.outdoor
                ? "야외: 건물 없이 땅과 작은 소품만 놓아요. 눌러서 건물로 바꾸기."
                : "건물: 블록으로 집을 지어요. 눌러서 야외(땅과 소품만)로 바꾸기.")));
            addDrawableChild(kind);
            if (site != null) {
                var bp = ScenarioClientState.build() == null ? null : ScenarioClientState.build().places.get(place.id);
                boolean built = bp != null && bp.min != null;
                if (removeAsk.equals(place.id)) {
                    addDrawableChild(new WoodUi.WoodButton(px + 70, by2, 74, 18, fit("월드도 지우기", 74), b -> {
                        ScenarioClientState.buildAction("build_clear", place.id);
                        layout.sites.remove(site);
                        removeAsk = "";
                        ScenarioClientState.saveLayout();
                        clearAndInit();
                    }, true));
                    addDrawableChild(new WoodUi.WoodButton(px + 148, by2, 74, 18, fit("배치만 빼기", 74), b -> {
                        layout.sites.remove(site);
                        removeAsk = "";
                        ScenarioClientState.saveLayout();
                        clearAndInit();
                    }, false));
                } else {
                    WoodUi.WoodButton remove = new WoodUi.WoodButton(px + 70, by2, 96, 18, fit("배치에서 빼기", 96), b -> {
                        if (built) {
                            removeAsk = place.id;
                        } else {
                            layout.sites.remove(site);
                            ScenarioClientState.saveLayout();
                        }
                        clearAndInit();
                    }, true);
                    remove.setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal(built
                        ? "이미 지어진 곳이에요. 누르면 월드의 건물도 지울지 물어봐요."
                        : "배치도에서 자리만 빼요. 시트에는 남아서 \"빈 자리에 놓기\"로 다시 넣을 수 있어요.")));
                    addDrawableChild(remove);
                }
            }
            if (!layout.building()) {
                // Third row: what is under it - cellar floors, and tunnels to other places.
                int by3 = by + 44;
                var sheetLinks = sheet.links;
                List<com.foredayner.scenario.sheet.ScenarioSheet.Link> mine = new java.util.ArrayList<>();
                for (var l : sheet.underground()) {
                    if (l.from.equals(place.id) || l.to.equals(place.id)) {
                        mine.add(l);
                    }
                }
                if (!place.outdoor) {
                    WoodUi.WoodButton cellar = new WoodUi.WoodButton(px, by3, 56, 18,
                        Text.literal(place.cellars == 0 ? "지하 없음" : "지하 " + place.cellars + "층"), b -> {
                            place.cellars = (place.cellars + 1) % (com.foredayner.scenario.sheet.ScenarioSheet.MAX_CELLARS + 1);
                            place.basement = place.cellars > 0;
                            ScenarioClientState.saveSheet();
                            clearAndInit();
                        }, false).selected(place.cellars > 0);
                    cellar.setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal(
                        "건물 아래에 지하실을 파요 (한 층 5칸). 1층 바닥의 뚜껑문과 사다리로 내려가요. 땅이 깊은 월드가 필요해요.")));
                    addDrawableChild(cellar);
                }
                boolean linking = linkFrom.equals(place.id);
                WoodUi.WoodButton link = new WoodUi.WoodButton(px + 60, by3, 56, 18, Text.literal(linking ? "이을 곳?" : "통로 잇기"), b -> {
                    linkFrom = linking ? "" : place.id;
                    clearAndInit();
                }, false).selected(linking);
                link.setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal(
                    "누른 다음 지도에서 다른 장소를 누르면 둘을 지하 통로로 이어요. 건물에 지하실이 없으면 작은 지하 방과 사다리를 만들어요.")));
                addDrawableChild(link);
                if (!mine.isEmpty()) {
                    boolean secret = mine.stream().allMatch(l -> "secret".equals(l.kind));
                    WoodUi.WoodButton hide = new WoodUi.WoodButton(px + 120, by3, 46, 18, Text.literal(secret ? "비밀 ✓" : "비밀"), b -> {
                        for (var l : mine) {
                            l.kind = secret ? "tunnel" : "secret";
                        }
                        ScenarioClientState.saveSheet();
                        clearAndInit();
                    }, false).selected(secret);
                    hide.setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal(
                        "비밀 통로: 어둡고, 지하실 쪽 입구가 부숴야 열리는 가짜 벽(금 간 돌)이에요.")));
                    addDrawableChild(hide);
                    addDrawableChild(new WoodUi.WoodButton(px + 170, by3, 52, 18, Text.literal("통로 " + mine.size() + " X"), b -> {
                        sheetLinks.removeAll(mine);
                        ScenarioClientState.saveSheet();
                        clearAndInit();
                    }, true));
                }
            }
            addDrawableChild(new WoodUi.WoodButton(px + 70, by, 52, 18, Text.literal("크기 " + place.size), b -> {
                int s = com.foredayner.scenario.sheet.ScenarioSheet.SIZES.indexOf(place.size);
                place.size = com.foredayner.scenario.sheet.ScenarioSheet.SIZES.get((s + 1) % 4);
                ScenarioClientState.saveSheet();
                layout.check(sheet);
                ScenarioClientState.saveLayout();
                clearAndInit();
            }, false));
        }
        askField = null;
        // Tidy: streets, footpaths, water and woods round the buildings, by code; the buildings stay where they are.
        WoodUi.WoodButton tidy = new WoodUi.WoodButton(px + 1, bottom() - 44, pw - 1, 18, Text.literal("정리"), b -> {
            java.util.List<String> notes = layout.tidy(sheet);
            ScenarioClientState.saveLayout();
            if (notes.isEmpty()) {
                ScenarioClientState.log("정리: 고칠 것이 없어요");
            }
            for (String note : notes) {
                ScenarioClientState.log("정리: " + note);
            }
            clearAndInit();
        }, false);
        tidy.setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal(
            "건물은 그대로 두고 나머지를 정리해요: 건물 밑을 지나는 길을 끊고, 길에서 먼 건물에 오솔길을 내고, 떨어진 길을 잇고, 건물 자리의 물·숲을 비워요. 겹친 건물은 로그로 알려 줘요.")));
        addDrawableChild(tidy);
    }

    private int toScreenX(int x) {
        return mapX() + Math.round((x - view()[0]) * scale());
    }

    private int toScreenZ(int z) {
        return mapY() + Math.round((z - view()[1]) * scale());
    }

    /** Building stage: the floor shown on the plan. */
    private static int floorShown = 0;

    private com.foredayner.scenario.layout.TownLayout.Site siteAt(double mx, double my) {
        var layout = ScenarioClientState.layout();
        if (layout == null) {
            return null;
        }
        for (int i = layout.sites.size() - 1; i >= 0; i--) {
            var s = layout.sites.get(i);
            if (layout.building() && s.level != floorShown) {
                continue;
            }
            if (mx >= toScreenX(s.x) && mx < toScreenX(s.x + s.w) && my >= toScreenZ(s.z) && my < toScreenZ(s.z + s.d)) {
                return s;
            }
        }
        return null;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (tab == Tab.LAYOUT && button == 0 && entryAt(mx, my) >= 0) {
            picked = listed().get(entryAt(mx, my)).id;
            clearAndInit();
            return true;
        }
        boolean onMap = mx >= mapX() && my >= mapY() && mx < mapX() + mapSize() && my < mapY() + mapSize();
        var town = ScenarioClientState.layout();
        if (tab == Tab.LAYOUT && button == 1 && onMap && town != null && !town.building()) {
            // The right button moves the view (and lets places be put past the map).
            panning = true;
            panFromX = mx;
            panFromY = my;
            panStartX = panX;
            panStartZ = panZ;
            return true;
        }
        if (tab == Tab.LAYOUT && button == 0 && onMap) {
            var s = siteAt(mx, my);
            var linkSheet = ScenarioClientState.sheet();
            if (!linkFrom.isEmpty() && s != null && !s.id.equals(linkFrom) && linkSheet != null) {
                // "통로 잇기": a tunnel from the place picked before to this one.
                boolean known = linkSheet.underground().stream().anyMatch(l -> l.from.equals(linkFrom) && l.to.equals(s.id)
                    || l.from.equals(s.id) && l.to.equals(linkFrom));
                if (!known) {
                    var l = new com.foredayner.scenario.sheet.ScenarioSheet.Link();
                    l.from = linkFrom;
                    l.to = s.id;
                    l.kind = "tunnel";
                    linkSheet.links.add(l);
                    ScenarioClientState.saveSheet();
                    ScenarioClientState.log("지하 통로를 이었어요: " + linkSheet.place(linkFrom).name + " ↔ " + linkSheet.place(s.id).name);
                }
                linkFrom = "";
                picked = s.id;
                showPicked();
                clearAndInit();
                return true;
            }
            linkFrom = "";
            picked = s == null ? "" : s.id;
            showPicked();
            var plan = ScenarioClientState.layout();
            if (s != null && plan != null && !plan.building()) { // a building's rooms stay where the plan has them
                dragging = true;
                dragMoved = false;
                dragDx = view()[0] + (int) Math.floor((mx - mapX()) / scale()) - s.x;
                dragDz = view()[1] + (int) Math.floor((my - mapY()) / scale()) - s.z;
            }
            clearAndInit();
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (tab == Tab.LAYOUT && panning) {
            panX = panStartX - (int) Math.round((mx - panFromX) / scale());
            panZ = panStartZ - (int) Math.round((my - panFromY) / scale());
            view(); // keeps it within reach
            return true;
        }
        if (tab == Tab.LAYOUT && dragging) {
            var layout = ScenarioClientState.layout();
            var s = layout == null ? null : layout.site(picked);
            if (s != null) {
                int size = layout.size, r = layout.reach();
                int[] v = view();
                // Anywhere on the map, or past it as far as the view moves.
                int nx = Math.max(-r, Math.min(size + r - s.w, v[0] + (int) Math.floor((mx - mapX()) / scale()) - dragDx));
                int nz = Math.max(-r, Math.min(size + r - s.d, v[1] + (int) Math.floor((my - mapY()) / scale()) - dragDz));
                if (nx != s.x || nz != s.z) {
                    s.x = nx;
                    s.z = nz;
                    dragMoved = true;
                }
            }
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (tab == Tab.LAYOUT && panning && button == 1) {
            panning = false;
            return true;
        }
        if (tab == Tab.LAYOUT && dragging) {
            dragging = false;
            if (dragMoved) {
                ScenarioClientState.saveLayout();
                clearAndInit();
            }
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    private static int zoneColor(String kind) {
        return switch (kind) {
            case "water" -> 0xFF3E6E9C;
            case "beach" -> 0xFFD9CB98;
            case "woods" -> 0xFF2F5B2E;
            case "cemetery" -> 0xFF6F7466;
            case "square" -> 0xFFA99D86;
            case "garden" -> 0xFF5F9449;
            default -> 0xFFB7A660; // field
        };
    }

    private static String floorKo(int level) {
        return level < 0 ? "지하 " + (-level) + "층" : (level + 1) + "층";
    }

    private void renderLayout(DrawContext ctx, int mouseX, int mouseY) {
        int px = panelX();
        var layout = ScenarioClientState.layout();
        var sheet = ScenarioClientState.sheet();
        int x0 = mapX(), y0 = mapY(), size = mapSize();
        if (layout == null || sheet == null) {
            ctx.fill(x0, y0, x0 + size, y0 + size, 0xFF6E7F57);
            if (showMap) {
                MapOverlay.draw(ctx, x0, y0, size, 255);
            }
            ctx.drawText(this.textRenderer, "아직 배치가 없어요.", px, bodyTop() + 32, WoodUi.INK, false);
            List<OrderedText> lines = this.textRenderer.wrapLines(StringVisitable.plain(
                "\"AI로 배치\"는 AI가 이야기에 맞게, \"기본 배치\"는 AI 없이 바로 만들어요."), right() - 12 - px);
            int ty = bodyTop() + 46;
            for (OrderedText line : lines) {
                ctx.drawText(this.textRenderer, line, px, ty, WoodUi.INK_SOFT, false);
                ty += 10;
            }
            return;
        }
        float k = scale();
        ctx.enableScissor(x0, y0, x0 + size, y0 + size);
        boolean town = !layout.building();
        if (town) {
            // Past the map (darker): only buildings the keeper puts there are made, nothing else.
            ctx.fill(x0, y0, x0 + size, y0 + size, 0xFF55633F);
            ctx.fill(toScreenX(0), toScreenZ(0), toScreenX(layout.size), toScreenZ(layout.size), GROUND);
        } else {
            ctx.fill(x0, y0, x0 + size, y0 + size, GROUND);
        }
        for (var z : showPlan ? layout.zones : List.<com.foredayner.scenario.layout.TownLayout.Zone>of()) {
            ctx.fill(toScreenX(z.x), toScreenZ(z.z), toScreenX(z.x + z.w), toScreenZ(z.z + z.d), zoneColor(z.kind));
        }
        for (var r : showPlan ? layout.roads : List.<com.foredayner.scenario.layout.TownLayout.Road>of()) {
            int color = "path".equals(r.kind) ? 0xFFA3845A : 0xFF7E776C;
            int half = Math.max(1, r.width) / 2;
            for (int i = 0; i + 1 < r.points.size(); i++) {
                int[] a = r.points.get(i), b = r.points.get(i + 1);
                if (a[0] == b[0] || a[1] == b[1]) {
                    int ax = Math.min(a[0], b[0]) - half, bx = Math.max(a[0], b[0]) + half + 1;
                    int az = Math.min(a[1], b[1]) - half, bz = Math.max(a[1], b[1]) + half + 1;
                    ctx.fill(toScreenX(ax), toScreenZ(az), toScreenX(bx), toScreenZ(bz), color);
                } else {
                    int steps = Math.max(Math.abs(b[0] - a[0]), Math.abs(b[1] - a[1]));
                    for (int s = 0; s <= steps; s++) {
                        int cx = a[0] + (b[0] - a[0]) * s / steps, cz = a[1] + (b[1] - a[1]) * s / steps;
                        ctx.fill(toScreenX(cx - half), toScreenZ(cz - half), toScreenX(cx + half + 1), toScreenZ(cz + half + 1), color);
                    }
                }
            }
        }
        if (showMap && town) {
            // The keeper's map, faint, between the ground and the places (it moves with the view).
            MapOverlay.draw(ctx, toScreenX(0), toScreenZ(0), toScreenX(layout.size) - toScreenX(0), 0x80);
        }
        if (town && (panX != 0 || panZ != 0)) {
            // The map's edge.
            int ax = toScreenX(0), az = toScreenZ(0), bx = toScreenX(layout.size), bz = toScreenZ(layout.size);
            ctx.fill(ax, az, bx, az + 1, 0xA0FFF4D6);
            ctx.fill(ax, bz - 1, bx, bz, 0xA0FFF4D6);
            ctx.fill(ax, az, ax + 1, bz, 0xA0FFF4D6);
            ctx.fill(bx - 1, az, bx, bz, 0xA0FFF4D6);
        }
        com.foredayner.scenario.layout.TownLayout.Site hover = siteAt(mouseX, mouseY);
        boolean mapUsed = sheet.mapImage != null && !sheet.mapImage.isBlank() && layout.sites.stream().anyMatch(t -> t.fromMap);
        boolean anyChosen = false;
        for (int n = 0; showPlan && n < layout.sites.size(); n++) {
            var s = layout.sites.get(n);
            if (layout.building() && s.level != floorShown) {
                continue;
            }
            int sx = toScreenX(s.x), sz = toScreenZ(s.z), ex = toScreenX(s.x + s.w), ez = toScreenZ(s.z + s.d);
            boolean sel = s.id.equals(picked);
            var sitePlace = sheet.place(s.id);
            boolean mapHouse = sitePlace != null && sitePlace.mapFiller;
            // With a map: places the AI had to find a spot for (not on the map) get a blue border.
            boolean chosen = mapUsed && !s.fromMap;
            anyChosen |= chosen;
            ctx.fill(sx, sz, ex, ez, sel ? 0xFF2B1D10 : chosen ? 0xFF2E6DA4 : 0xFF4A3420);
            ctx.fill(sx + 1, sz + 1, ex - 1, ez - 1, s.outdoor ? 0xFF9D8C69 : sel ? 0xFFE0B25A : mapHouse ? 0xFFB9A88C : 0xFFC28A4E);
            int edge = 0xFFFFF4D6;
            switch (layout.building() ? "" : s.front) {
                case "north" -> ctx.fill(sx + 1, sz, ex - 1, sz + 2, edge);
                case "east" -> ctx.fill(ex - 2, sz + 1, ex, ez - 1, edge);
                case "west" -> ctx.fill(sx, sz + 1, sx + 2, ez - 1, edge);
                case "" -> {
                }
                default -> ctx.fill(sx + 1, ez - 2, ex - 1, ez, edge);
            }
            // The place's number in the sheet (its name is in the list beside the map).
            String num = String.valueOf(sheet.places.indexOf(sheet.place(s.id)) + 1);
            int tw = this.textRenderer.getWidth(num);
            if (tw + 2 <= ex - sx) {
                ctx.drawText(this.textRenderer, num, (sx + ex - tw) / 2 + 1, (sz + ez) / 2 - 3, 0xFF1E140A, false);
            } else {
                var m = ctx.getMatrices();
                m.push();
                m.translate((sx + ex) / 2.0F - tw / 4.0F, (sz + ez) / 2.0F - 2, 0);
                m.scale(0.5F, 0.5F, 1);
                ctx.drawText(this.textRenderer, num, 0, 0, 0xFF1E140A, false);
                m.pop();
            }
        }
        boolean anyTunnel = false, anySecret = false;
        if (town && showPlan) {
            // What is under the ground: tunnels (brown; secret ones purple) and cellar floors (B1, B2) on their places.
            for (var t : com.foredayner.scenario.layout.Underground.tunnels(layout, sheet)) {
                int col = t.secret() ? 0xB07A3AA0 : 0xB05A3A1A;
                anyTunnel |= !t.secret();
                anySecret |= t.secret();
                for (java.util.Set<Long> cells : List.of(t.inside(), t.doorways())) {
                    for (long key : cells) {
                        int cx = com.foredayner.scenario.layout.Underground.keyX(key), cz = com.foredayner.scenario.layout.Underground.keyZ(key);
                        ctx.fill(toScreenX(cx), toScreenZ(cz), Math.max(toScreenX(cx) + 1, toScreenX(cx + 1)),
                            Math.max(toScreenZ(cz) + 1, toScreenZ(cz + 1)), col);
                    }
                }
            }
            for (var s : layout.sites) {
                var sp = sheet.place(s.id);
                if (sp != null && sp.cellars > 0) {
                    var m = ctx.getMatrices();
                    m.push();
                    m.translate(toScreenX(s.x) + 2, toScreenZ(s.z) + 2, 0);
                    m.scale(0.5F, 0.5F, 1);
                    ctx.drawText(this.textRenderer, "B" + sp.cellars, 0, 0, 0xFF3A2A18, false);
                    m.pop();
                }
            }
        }
        if (layout.building() && showPlan) {
            // Doors (light) and stairs (an arrow of dark steps) on the floor shown.
            for (var d : layout.doors) {
                if (d.level == floorShown) {
                    ctx.fill(toScreenX(d.x), toScreenZ(d.z), toScreenX(d.x + 1), toScreenZ(d.z + 1), d.entrance ? 0xFF3FA34D : 0xFFFFF4D6);
                }
            }
            for (var st : layout.stairs) {
                if (st.level == floorShown || st.level + 1 == floorShown) {
                    int[] dir = switch (st.dir) {
                        case "east" -> new int[] { 1, 0 };
                        case "south" -> new int[] { 0, 1 };
                        case "west" -> new int[] { -1, 0 };
                        default -> new int[] { 0, -1 };
                    };
                    for (int i = 0; i < com.foredayner.scenario.layout.IndoorPlan.STAIR_RUN; i++) {
                        int cx = st.x + dir[0] * i, cz = st.z + dir[1] * i;
                        int shade = st.level == floorShown ? 0xFF3A2A18 + i * 0x00141008 : 0x803A2A18;
                        ctx.fill(toScreenX(cx), toScreenZ(cz), toScreenX(cx + 1), toScreenZ(cz + 1), shade);
                    }
                }
            }
            anyChosen = false;
        }
        ctx.disableScissor();
        // Under the map, beside the switches: what the colours mean.
        int lx = x0 + (MapOverlay.available() ? 148 : 74), legendY = belowMapY() + 3, lend = right() - 12; // the panel is free at this height
        List<Object[]> legend = new java.util.ArrayList<>();
        if (anyChosen) {
            legend.add(new Object[] { "■ 파란 테두리: AI가 정한 곳", 0xFF2E6DA4 });
        }
        if (!linkFrom.isEmpty()) {
            legend.add(new Object[] { "통로로 이을 장소를 누르세요", 0xFFC06A10 });
        }
        if (anyTunnel) {
            legend.add(new Object[] { "■ 지하 통로", 0xFF5A3A1A });
        }
        if (anySecret) {
            legend.add(new Object[] { "■ 비밀 통로", 0xFF7A3AA0 });
        }
        if (town) {
            legend.add(new Object[] { "■ 지도 밖(건물만)", 0xFF55633F });
            legend.add(new Object[] { "우클릭: 이동", WoodUi.INK_SOFT });
        }
        for (Object[] part : legend) {
            String text = (String) part[0];
            int w = this.textRenderer.getWidth(text);
            if (lx + w > lend) {
                text = this.textRenderer.trimToWidth(text, Math.max(0, lend - lx));
                w = this.textRenderer.getWidth(text);
            }
            if (w <= 0) {
                break;
            }
            ctx.drawText(this.textRenderer, text, lx, legendY, (int) part[1], false);
            lx += w + 8;
        }
        // Side panel: the numbered place list (scrolls when long), the picked place and the check.
        int ew = entryWidth();
        int hoverEntry = entryAt(mouseX, mouseY);
        var places = sheet.places;
        int shown = Math.min(listRows(), visibleRows());
        for (int r = 0; r < shown; r++) {
            for (int c = 0; c < 2; c++) {
                int n = (r + listScroll) * 2 + c;
                if (n >= places.size()) {
                    break;
                }
                var place = places.get(n);
                boolean placed = layout.site(place.id) != null;
                int ex = px + c * ew, ey = entriesTop() + r * ENTRY;
                if (place.id.equals(picked)) {
                    ctx.fill(ex - 2, ey - 2, ex + ew - 4, ey + ENTRY - 2, 0x60C28A4E);
                } else if (n == hoverEntry) {
                    ctx.fill(ex - 2, ey - 2, ex + ew - 4, ey + ENTRY - 2, 0x30C28A4E);
                }
                String label = (n + 1) + " " + place.name + (placed ? "" : " (자리 없음)");
                ctx.drawText(this.textRenderer, this.textRenderer.trimToWidth(label, ew - 6), ex, ey, placed ? WoodUi.INK : 0xFF8E2A1E, false);
            }
        }
        if (listRows() > shown) {
            // A thin scroll bar at the list's right edge.
            int top = entriesTop() - 1, h = shown * ENTRY;
            int barH = Math.max(6, h * shown / listRows());
            int barY = top + (h - barH) * listScroll / Math.max(1, listRows() - shown);
            ctx.fill(right() - 13, top, right() - 11, top + h, 0x40000000);
            ctx.fill(right() - 13, barY, right() - 11, barY + barH, 0xFF6B4A2B);
        }
        var site = layout.site(picked);
        var pickedPlace = sheet.place(picked);
        int ty = controlsY();
        int statusX = px;
        if (pickedPlace != null) {
            // The picked place's south-west corner (bottom left on the map) and size.
            var build = ScenarioClientState.build();
            boolean world = build != null && build.placed;
            int cx = site == null ? 0 : site.x + (world ? build.originX : 0);
            int cz = site == null ? 0 : site.z + site.d - 1 + (world ? build.originZ : 0);
            String corner = site == null ? "자리 없음 · " + com.foredayner.scenario.sheet.ScenarioSheet.footprint(pickedPlace) + "x"
                + com.foredayner.scenario.sheet.ScenarioSheet.footprint(pickedPlace)
                : (world ? "좌하단 월드 (" : "좌하단 (") + cx + ", " + cz + ") · " + site.w + "x" + site.d;
            int below = layout.building() ? 44 : 66;
            ctx.drawText(this.textRenderer, this.textRenderer.trimToWidth(corner, right() - 12 - px), px, ty + below, WoodUi.INK_SOFT, false);
            ty += below + 11;
        } else if (layout.building()) {
            // Under the floor buttons.
            ctx.drawText(this.textRenderer, this.textRenderer.trimToWidth("흰 칸 = 문 · 초록 = 입구 · 짙은 칸 = 계단", right() - 12 - px),
                px, ty + 22, WoodUi.INK_SOFT, false);
            ty += 33;
        } else {
            ctx.drawText(this.textRenderer, this.textRenderer.trimToWidth("지도에서 끌어 옮기기 · 밝은 테두리 = 입구", right() - 12 - px),
                px, ty, WoodUi.INK_SOFT, false);
            ty += 11;
        }
        String status = layoutProblems.isEmpty() ? "검사: 문제 없음" : "검사: 확인할 것 " + layoutProblems.size() + "개";
        ctx.drawText(this.textRenderer, status, statusX, ty, layoutProblems.isEmpty() ? 0xFF2E6B2E : 0xFF8E2A1E, false);
        boolean overStatus = mouseX >= statusX && mouseX < statusX + this.textRenderer.getWidth(status) && mouseY >= ty - 1 && mouseY < ty + 9;
        if (!layoutProblems.isEmpty()) {
            if (overStatus) {
                List<OrderedText> tip = new java.util.ArrayList<>();
                for (String problem : layoutProblems) {
                    tip.addAll(this.textRenderer.wrapLines(StringVisitable.plain("· " + problem), 220));
                }
                ctx.drawOrderedTooltip(this.textRenderer, tip, mouseX, mouseY);
            }
            int ly = controlsY() + CONTROLS;
            for (String problem : layoutProblems) {
                for (OrderedText line : this.textRenderer.wrapLines(StringVisitable.plain("· " + problem), right() - 12 - px)) {
                    if (ly > bottom() - 58) {
                        break;
                    }
                    ctx.drawText(this.textRenderer, line, px, ly, WoodUi.INK_SOFT, false);
                    ly += 9;
                }
            }
        }
        if (hover != null && !dragging) {
            var place = sheet.place(hover.id);
            int n = sheet.places.indexOf(sheet.place(hover.id)) + 1;
            ctx.drawTooltip(this.textRenderer, Text.literal(n + " " + (place == null ? hover.id : place.name + " (" + place.kind + ", "
                + hover.w + "x" + hover.d + ", 입구 " + sideKo(hover.front) + ")")), mouseX, mouseY);
        }
    }

    // ------------------------------------------------------------------ 짓기

    private static final int BUILD_ROW = 16;
    private int buildScroll;

    private int buildListTop() {
        return bodyTop() + 54;
    }

    /** Rows: the ground, then every place of the sheet. */
    private int buildRows() {
        var sheet = ScenarioClientState.sheet();
        return 1 + (sheet == null ? 0 : sheet.places.size());
    }

    private int buildVisible() {
        return Math.max(2, (bottom() - 24 - buildListTop()) / BUILD_ROW);
    }

    private static String stateKo(String s) {
        return switch (s == null ? "" : s) {
            case "designing" -> "설계 중";
            case "placing" -> "짓는 중";
            case "done" -> "완료";
            case "failed" -> "실패";
            case "stopped" -> "멈춤";
            case "cleared" -> "치움";
            case "working" -> "진행 중";
            default -> "대기";
        };
    }

    private static int stateColor(String s) {
        return switch (s == null ? "" : s) {
            case "done" -> 0xFF2E6B2E;
            case "failed" -> 0xFF8E2A1E;
            case "designing", "placing", "working" -> 0xFF8A5A10;
            default -> WoodUi.INK_SOFT;
        };
    }

    private void initBuildTab() {
        int l = left() + 12, r = right() - 12, y = bodyTop() + 6;
        var build = ScenarioClientState.build();
        boolean busy = build != null && build.busy();
        boolean placed = build != null && build.placed;
        WoodUi.WoodButton here = new WoodUi.WoodButton(r - 118, y, 118, 18, fit("내 위치를 한가운데로", 118),
            b -> ScenarioClientState.buildAction("build_here", null), false);
        here.active = !busy;
        here.setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal(
            "지금 서 있는 곳이 160x160 마을의 한가운데가 돼요. 그 안의 언덕·나무는 깎이고 땅이 평평해져요.")));
        addDrawableChild(here);
        int by = y + 26;
        boolean started = build != null && ("done".equals(build.ground) || build.places.values().stream().anyMatch(p -> !"waiting".equals(p.state)));
        WoodUi.WoodButton start = new WoodUi.WoodButton(l, by, 92, 18, fit(started ? "이어서 짓기" : "짓기 시작", 92),
            b -> ScenarioClientState.buildAction("build_start", null), false);
        start.active = placed && !busy;
        addDrawableChild(start);
        WoodUi.WoodButton stop = new WoodUi.WoodButton(l + 96, by, 60, 18, Text.literal("멈추기"),
            b -> ScenarioClientState.buildAction("build_stop", null), true);
        stop.active = busy;
        addDrawableChild(stop);
        WoodUi.WoodButton undo = new WoodUi.WoodButton(l + 160, by, 92, 18, fit("전체 되돌리기", 92),
            b -> ScenarioClientState.buildAction("build_undo", null), true);
        undo.active = !busy && ScenarioClientState.canUndo();
        undo.setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal(ScenarioClientState.canUndo()
            ? "마을을 짓기 전의 땅으로 되돌려요." : "원래 땅의 기록이 없어요 (짓기 전이거나 서버를 다시 켰어요).")));
        addDrawableChild(undo);
        // Per place: go there, build again, clear.
        var sheet = ScenarioClientState.sheet();
        if (sheet == null || build == null) {
            return;
        }
        buildScroll = Math.max(0, Math.min(buildScroll, buildRows() - buildVisible()));
        for (int row = 0; row < buildVisible(); row++) {
            int n = row + buildScroll - 1; // -1: the ground row
            if (n < 0 || n >= sheet.places.size()) {
                continue;
            }
            var place = sheet.places.get(n);
            int ry = buildListTop() + row * BUILD_ROW;
            var state = build.places.get(place.id);
            boolean built = state != null && ("done".equals(state.state) || state.min != null);
            WoodUi.WoodButton go = new WoodUi.WoodButton(r - 110, ry - 2, 34, 14, Text.literal("이동"),
                b -> ScenarioClientState.buildAction("build_tp", place.id), false);
            go.active = placed;
            addDrawableChild(go);
            WoodUi.WoodButton redo = new WoodUi.WoodButton(r - 74, ry - 2, 34, 14, Text.literal("다시"),
                b -> ScenarioClientState.buildAction("build_redo", place.id), false);
            redo.active = placed && !busy && "done".equals(build.ground);
            addDrawableChild(redo);
            WoodUi.WoodButton clear = new WoodUi.WoodButton(r - 38, ry - 2, 38, 14, Text.literal("치우기"),
                b -> ScenarioClientState.buildAction("build_clear", place.id), true);
            clear.active = placed && !busy && built;
            addDrawableChild(clear);
        }
    }

    private void renderBuild(DrawContext ctx, int mouseX, int mouseY) {
        int l = left() + 12, r = right() - 12, y = bodyTop() + 6;
        var build = ScenarioClientState.build();
        var sheet = ScenarioClientState.sheet();
        if (build == null || !build.placed) {
            ctx.drawText(this.textRenderer, "마을을 지을 곳을 정하세요.", l, y + 1, WoodUi.INK, false);
            ctx.drawText(this.textRenderer, this.textRenderer.trimToWidth("한가운데에 서서 오른쪽 버튼을 누르면 돼요.", r - l - 124), l, y + 12,
                WoodUi.INK_SOFT, false);
            return;
        }
        int ground = ScenarioClientState.layout() == null ? com.foredayner.scenario.layout.TownLayout.SIZE : ScenarioClientState.layout().size;
        int half = ground / 2;
        ctx.drawText(this.textRenderer, this.textRenderer.trimToWidth("한가운데 (" + (build.originX + half) + ", " + (build.originY - 1) + ", "
            + (build.originZ + half) + ")", r - l - 124), l, y + 1, WoodUi.INK, false);
        ctx.drawText(this.textRenderer, this.textRenderer.trimToWidth("범위 (" + build.originX + ", " + build.originZ + ") ~ ("
            + (build.originX + ground - 1) + ", " + (build.originZ + ground - 1) + ")", r - l - 124), l, y + 12, WoodUi.INK_SOFT, false);
        int[] c = build.counts();
        String progress = "완료 " + c[0] + "/" + c[2] + (c[1] > 0 ? " · 실패 " + c[1] : "") + " · 토큰 " + build.tokensIn + "/" + build.tokensOut;
        ctx.drawText(this.textRenderer, this.textRenderer.trimToWidth(progress, r - l - 258), l + 258, y + 31, WoodUi.INK_SOFT, false);
        // How far the whole build is: the ground (as it goes) and every place, done or failed.
        double groundDone = "done".equals(build.ground) ? 1 : "working".equals(build.ground) ? build.groundPercent / 100.0 : 0;
        double part = (groundDone + c[0] + c[1]) / Math.max(1, c[2] + 1);
        int bx0 = l + 258, bx1 = r, by = y + 42;
        ctx.fill(bx0, by, bx1, by + 4, 0x40000000);
        ctx.fill(bx0, by, bx0 + (int) Math.round((bx1 - bx0) * Math.min(1, part)), by + 4, c[1] > 0 ? 0xFFC06A10 : 0xFF2E6B2E);
        if (sheet == null) {
            return;
        }
        String tip = null;
        for (int row = 0; row < buildVisible(); row++) {
            int n = row + buildScroll - 1;
            if (n >= sheet.places.size()) {
                break;
            }
            int ry = buildListTop() + row * BUILD_ROW;
            String name;
            String state;
            String error = "";
            if (n < 0) {
                name = "땅 준비";
                state = build.ground;
            } else {
                var place = sheet.places.get(n);
                var bp = build.places.get(place.id);
                name = (n + 1) + " " + place.name;
                state = bp == null ? "waiting" : bp.state;
                error = bp == null ? "" : bp.error;
            }
            String stateText = stateKo(state) + ("working".equals(state) ? " " + build.groundPercent + "%" : "")
                + (error.isBlank() ? "" : " ⚠");
            ctx.drawText(this.textRenderer, this.textRenderer.trimToWidth(name, r - l - 200), l, ry + 1, WoodUi.INK, false);
            ctx.drawText(this.textRenderer, stateText, r - 190, ry + 1, stateColor(state), false);
            if (!error.isBlank() && mouseY >= ry - 2 && mouseY < ry + BUILD_ROW - 2 && mouseX >= l && mouseX < r - 112) {
                tip = error;
            }
        }
        if (buildRows() > buildVisible()) {
            int top = buildListTop() - 2, h = buildVisible() * BUILD_ROW;
            int barH = Math.max(6, h * buildVisible() / buildRows());
            int barY = top + (h - barH) * buildScroll / Math.max(1, buildRows() - buildVisible());
            ctx.fill(right() - 9, top, right() - 7, top + h, 0x40000000);
            ctx.fill(right() - 9, barY, right() - 7, barY + barH, 0xFF6B4A2B);
        }
        if (tip != null) {
            ctx.drawOrderedTooltip(this.textRenderer, this.textRenderer.wrapLines(StringVisitable.plain(tip), 220), mouseX, mouseY);
        }
    }

    private static String conditionKo(String c) {
        return switch (c == null ? "" : c) {
            case "worn" -> "낡음";
            case "abandoned" -> "폐허";
            case "sinister" -> "불길";
            default -> "깔끔";
        };
    }

    private static String placeName(com.foredayner.scenario.sheet.ScenarioSheet sheet, String id) {
        var p = sheet.place(id);
        return p == null ? id : p.name;
    }

    private static String formKo(String form) {
        return switch (form) {
            case "book" -> "책";
            case "map" -> "지도";
            case "item" -> "물건";
            default -> "쪽지";
        };
    }

    private static String placeDetail(com.foredayner.scenario.sheet.ScenarioSheet.Place p) {
        StringBuilder s = new StringBuilder(p.name + " (" + p.kind + ", " + p.size + " " + com.foredayner.scenario.sheet.ScenarioSheet.footprint(p)
            + "칸, " + p.floors + "층" + (p.basement ? " + 지하" : "") + (p.outdoor ? ", 야외" : ", " + conditionKo(p.condition)) + ")");
        if (!p.rooms.isEmpty()) {
            s.append("\n방: ");
            for (int i = 0; i < p.rooms.size(); i++) {
                s.append(i == 0 ? "" : ", ").append(p.rooms.get(i).name);
            }
        }
        if (!p.scenes.isEmpty()) {
            s.append("\n장면: ").append(String.join(", ", p.scenes));
        }
        if (p.notes != null && !p.notes.isBlank()) {
            s.append("\n").append(p.notes);
        }
        return s.toString();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (tab == Tab.LOG) {
            logScroll = Math.max(0, logScroll + (int) Math.signum(vertical) * 3);
            return true;
        }
        if (tab == Tab.BUILD && mouseY >= buildListTop() && buildRows() > buildVisible()) {
            buildScroll = Math.max(0, Math.min(buildRows() - buildVisible(), buildScroll - (int) Math.signum(vertical)));
            clearAndInit();
            return true;
        }
        if (tab == Tab.LAYOUT && mouseX >= panelX() && mouseY >= entriesTop() && mouseY < controlsY() && listRows() > visibleRows()) {
            listScroll -= (int) Math.signum(vertical);
            clampListScroll();
            return true;
        }
        if (tab == Tab.SHEET && ScenarioClientState.sheet() != null && mouseY >= listTop() && mouseY <= listBottom()) {
            var sheet = ScenarioClientState.sheet();
            int[] sizes = { sheet.places.size(), sheet.clues.size() };
            int[] which = { 0, 2 }; // scroll[0] places, scroll[2] clues
            for (int c = 0; c < 2; c++) {
                if (mouseX >= colX(c) && mouseX < colX(c) + colWidth()) {
                    int maxRows = Math.max(1, (listBottom() - listTop()) / ROW);
                    int k = which[c];
                    scroll[k] = Math.max(0, Math.min(Math.max(0, sizes[c] - maxRows), scroll[k] - (int) Math.signum(vertical)));
                    clearAndInit();
                    return true;
                }
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    private void renderSheet(DrawContext ctx) {
        int l = left() + 12;
        var sheet = ScenarioClientState.sheet();
        var info = ScenarioClientState.selectedInfo();
        String name = info == null ? "" : info.name;
        if (sheet == null) {
            ctx.drawText(this.textRenderer, name, l, bodyTop() + 11, WoodUi.INK, false);
            ctx.drawText(this.textRenderer, "아직 시트가 없어요. \"AI로 다시 읽기\"를 누르면 AI가 시나리오를 읽고 만들어요.", l, bodyTop() + 40,
                WoodUi.INK_SOFT, false);
        } else {
            ctx.drawText(this.textRenderer, this.textRenderer.trimToWidth(sheet.title.isBlank() ? name : sheet.title, right() - l - 370),
                l, bodyTop() + 11, WoodUi.INK, false);
            String line = sheet.era + " · " + sheet.mood + " · " + timeKo(sheet.timeOfDay) + " · " + weatherKo(sheet.weather)
                + " · 바다 " + sideKo(sheet.terrain.coast) + " · 숲 " + sideKo(sheet.terrain.woods) + " · " + sheet.setting;
            ctx.drawText(this.textRenderer, this.textRenderer.trimToWidth(line, right() - l - 20), l, bodyTop() + 23, WoodUi.INK_SOFT, false);
            String[] heads = { "장소 " + sheet.places.size(), "단서 " + sheet.clues.size() };
            for (int c = 0; c < 2; c++) {
                ctx.drawText(this.textRenderer, heads[c], colX(c) + 2, listTop() - 12, WoodUi.INK, false);
            }
        }
    }

    private static String timeKo(String t) {
        return switch (t == null ? "" : t) {
            case "day" -> "낮";
            case "dusk" -> "해 질 녘";
            default -> "밤";
        };
    }

    private static String weatherKo(String w) {
        return switch (w == null ? "" : w) {
            case "clear" -> "맑음";
            case "thunder" -> "뇌우";
            default -> "비";
        };
    }

    private static String sideKo(String s) {
        return switch (s == null ? "" : s) {
            case "north" -> "북";
            case "east" -> "동";
            case "south" -> "남";
            case "west" -> "서";
            default -> "없음";
        };
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.renderBackground(ctx, mouseX, mouseY, delta);
        WoodUi.frame(ctx, left(), top(), right(), bottom());
        WoodUi.plaque(ctx, this.textRenderer, "AI 시나리오 공방", this.width / 2, top() - 14, WoodUi.PAPER);
        WoodUi.inset(ctx, left() + 6, bodyTop(), right() - 6, bottom() - 6, WoodUi.PARCHMENT);
        if (tab == Tab.SHEET && ScenarioClientState.sheet() != null) {
            WoodUi.inset(ctx, left() + 10, listTop() - 16, right() - 10, listBottom() + 2, WoodUi.PARCHMENT_DEEP);
        }
                if (tab == Tab.SCENARIO && ScenarioClientState.loaded() != null) {
            WoodUi.inset(ctx, left() + 12, bodyTop() + 48, mid() - 6, bottom() - 50, WoodUi.PAPER);
        }
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        renderBody(ctx, mouseX, mouseY, delta);
        renderToast(ctx);
    }

    private void renderBody(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        if (tab == Tab.SHEET) {
            renderSheet(ctx);
            return;
        }
        if (tab == Tab.LAYOUT) {
            renderLayout(ctx, mouseX, mouseY);
            return;
        }
        if (tab == Tab.BUILD) {
            renderBuild(ctx, mouseX, mouseY);
            return;
        }
        if (tab == Tab.LOG) {
            renderLog(ctx);
            return;
        }
        if (tab != Tab.SCENARIO) {
            return;
        }
        int l = left() + 12;
        ScenarioText.Loaded loaded = ScenarioClientState.loaded();
        if (loaded != null) {
            ctx.drawText(this.textRenderer, "이름", l, bodyTop() + 37, WoodUi.INK, false);
            // A preview of the start of the scenario.
            int y = bodyTop() + 54;
            int maxY = bottom() - 56;
            String start = loaded.text().length() > 3000 ? loaded.text().substring(0, 3000) : loaded.text();
            List<OrderedText> lines = this.textRenderer.wrapLines(StringVisitable.plain(start), mid() - l - 18);
            for (OrderedText line : lines) {
                if (y > maxY - 9) {
                    break;
                }
                ctx.drawText(this.textRenderer, line, l + 5, y, WoodUi.INK, false);
                y += 10;
            }
            ctx.drawText(this.textRenderer, loaded.text().length() + "자" + (loaded.cut() ? " (잘림)" : ""),
                l + 150, bottom() - 39, WoodUi.INK_SOFT, false);
        } else {
            ctx.drawText(this.textRenderer, "크툴루의 부름 시나리오 파일(.txt .md .pdf)을 고르거나", l, bodyTop() + 40, WoodUi.INK_SOFT, false);
            ctx.drawText(this.textRenderer, "복사한 글을 붙여 넣으세요.", l, bodyTop() + 52, WoodUi.INK_SOFT, false);
        }
        ctx.drawText(this.textRenderer, "저장된 시나리오", mid() + 10, bodyTop() + 10, WoodUi.INK, false);
        if (ScenarioClientState.stored().isEmpty()) {
            ctx.drawText(this.textRenderer, "아직 없어요.", mid() + 10, bodyTop() + 28, WoodUi.INK_SOFT, false);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}

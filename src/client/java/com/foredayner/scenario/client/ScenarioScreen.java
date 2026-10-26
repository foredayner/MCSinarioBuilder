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
        SCENARIO("시나리오"), SHEET("시트"), LAYOUT("배치도"), BUILD("짓기");

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
            };
            WoodUi.WoodButton button = new WoodUi.WoodButton(x, top() + 6, 70, 18, Text.literal(t.label), b -> {
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
        if (tab == Tab.SCENARIO) {
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

    private int colWidth() {
        return (right() - left() - 24) / 3;
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
            // NPCs.
            cx = colX(1);
            i = 0;
            for (int k = scroll[1]; k < sheet.npcs.size() && i < maxRows; k++, i++) {
                var n = sheet.npcs.get(k);
                int ry = listTop() + i * ROW;
                WoodUi.WoodButton name = new WoodUi.WoodButton(cx, ry, w - 24, 18, fit(n.name + " · " + n.role, w - 24), b -> { }, false);
                name.setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal(n.name + " (" + n.role + ")\n장소: "
                    + placeName(sheet, n.place) + (n.room.isBlank() ? "" : " / " + n.room) + (n.notes.isBlank() ? "" : "\n" + n.notes))));
                addDrawableChild(name);
                addDrawableChild(new WoodUi.WoodButton(cx + w - 22, ry, 20, 18, Text.literal("X"), b -> {
                    sheet.npcs.remove(n);
                    ScenarioClientState.saveSheet();
                }, true));
            }
            // Clues: name, hidden or not, remove.
            cx = colX(2);
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
            boolean tooMany = sheet.places.size() > com.foredayner.scenario.sheet.ScenarioSheet.MAX_PLACES;
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
        return mapSize() / (float) com.foredayner.scenario.layout.TownLayout.SIZE;
    }

    private int panelX() {
        return mapX() + mapSize() + 10;
    }

    private static final int ENTRY = 11;
    /** Room under the list: the picked place's buttons, its corner line and the check. */
    private static final int CONTROLS = 44;
    private int listScroll;

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
        int bw = (pw - 8) / 3;
        var layout = ScenarioClientState.layout();
        var sheet = ScenarioClientState.sheet();
        boolean tooMany = sheet != null && sheet.places.size() > com.foredayner.scenario.sheet.ScenarioSheet.MAX_PLACES;
        WoodUi.WoodButton ai = new WoodUi.WoodButton(px, y, bw, 18, fit("AI로 배치", bw), b -> ScenarioClientState.makeLayout(true), false);
        WoodUi.WoodButton plain = new WoodUi.WoodButton(px + bw + 4, y, bw, 18, fit("기본 배치", bw), b -> ScenarioClientState.makeLayout(false), false);
        ai.active = plain.active = !tooMany;
        if (tooMany) {
            var tip = net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal("장소가 " + sheet.places.size() + "곳이에요. 시트에서 "
                + com.foredayner.scenario.sheet.ScenarioSheet.MAX_PLACES + "곳 이하로 줄여 주세요."));
            ai.setTooltip(tip);
            plain.setTooltip(tip);
        }
        addDrawableChild(ai);
        addDrawableChild(plain);
        addDrawableChild(new WoodUi.WoodButton(px + 2 * (bw + 4), y, bw, 18, fit("모델 설정", bw),
            b -> this.client.setScreen(new ModelSettingsScreen(this)), false));
        if (layout == null || sheet == null) {
            askField = null;
            layoutProblems = List.of();
            return;
        }
        layoutProblems = layout.check(sheet);
        clampListScroll();
        var place = sheet.place(picked);
        var site = layout.site(picked);
        int by = controlsY();
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
            addDrawableChild(new WoodUi.WoodButton(px + 70, by, 52, 18, Text.literal("크기 " + place.size), b -> {
                int s = com.foredayner.scenario.sheet.ScenarioSheet.SIZES.indexOf(place.size);
                place.size = com.foredayner.scenario.sheet.ScenarioSheet.SIZES.get((s + 1) % 4);
                ScenarioClientState.saveSheet();
                layout.check(sheet);
                ScenarioClientState.saveLayout();
                clearAndInit();
            }, false));
        }
        askField = new TextFieldWidget(this.textRenderer, px + 1, bottom() - 42, pw - 52, 14, Text.literal("요청"));
        askField.setMaxLength(300);
        askField.setPlaceholder(Text.literal("AI에게 배치 고쳐 달라고 하기"));
        addDrawableChild(askField);
        WoodUi.WoodButton ask = new WoodUi.WoodButton(right() - 60, bottom() - 44, 48, 18, Text.literal("요청"), b -> {
            ScenarioClientState.reviseLayout(askField.getText());
            askField.setText("");
        }, false);
        ask.active = !tooMany;
        addDrawableChild(ask);
    }

    private int toScreenX(int x) {
        return mapX() + Math.round(x * scale());
    }

    private int toScreenZ(int z) {
        return mapY() + Math.round(z * scale());
    }

    private com.foredayner.scenario.layout.TownLayout.Site siteAt(double mx, double my) {
        var layout = ScenarioClientState.layout();
        if (layout == null) {
            return null;
        }
        for (int i = layout.sites.size() - 1; i >= 0; i--) {
            var s = layout.sites.get(i);
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
        if (tab == Tab.LAYOUT && button == 0 && mx >= mapX() && my >= mapY() && mx < mapX() + mapSize() && my < mapY() + mapSize()) {
            var s = siteAt(mx, my);
            picked = s == null ? "" : s.id;
            showPicked();
            if (s != null) {
                dragging = true;
                dragMoved = false;
                dragDx = (int) Math.floor((mx - mapX()) / scale()) - s.x;
                dragDz = (int) Math.floor((my - mapY()) / scale()) - s.z;
            }
            clearAndInit();
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (tab == Tab.LAYOUT && dragging) {
            var layout = ScenarioClientState.layout();
            var s = layout == null ? null : layout.site(picked);
            if (s != null) {
                int size = com.foredayner.scenario.layout.TownLayout.SIZE;
                int nx = Math.max(0, Math.min(size - s.w, (int) Math.floor((mx - mapX()) / scale()) - dragDx));
                int nz = Math.max(0, Math.min(size - s.d, (int) Math.floor((my - mapY()) / scale()) - dragDz));
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

    private void renderLayout(DrawContext ctx, int mouseX, int mouseY) {
        int px = panelX();
        var layout = ScenarioClientState.layout();
        var sheet = ScenarioClientState.sheet();
        int x0 = mapX(), y0 = mapY(), size = mapSize();
        String message = ScenarioClientState.message();
        if (!message.isEmpty()) {
            ctx.drawText(this.textRenderer, this.textRenderer.trimToWidth(message, right() - left() - 32), left() + 12, bottom() - 18, WoodUi.INK, false);
        }
        if (layout == null || sheet == null) {
            ctx.fill(x0, y0, x0 + size, y0 + size, 0xFF6E7F57);
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
        ctx.fill(x0, y0, x0 + size, y0 + size, GROUND);
        ctx.enableScissor(x0, y0, x0 + size, y0 + size);
        for (var z : layout.zones) {
            ctx.fill(toScreenX(z.x), toScreenZ(z.z), toScreenX(z.x + z.w), toScreenZ(z.z + z.d), zoneColor(z.kind));
        }
        for (var r : layout.roads) {
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
        com.foredayner.scenario.layout.TownLayout.Site hover = siteAt(mouseX, mouseY);
        for (int n = 0; n < layout.sites.size(); n++) {
            var s = layout.sites.get(n);
            int sx = toScreenX(s.x), sz = toScreenZ(s.z), ex = toScreenX(s.x + s.w), ez = toScreenZ(s.z + s.d);
            boolean sel = s.id.equals(picked);
            ctx.fill(sx, sz, ex, ez, sel ? 0xFF2B1D10 : 0xFF4A3420);
            ctx.fill(sx + 1, sz + 1, ex - 1, ez - 1, s.outdoor ? 0xFF9D8C69 : sel ? 0xFFE0B25A : 0xFFC28A4E);
            int edge = 0xFFFFF4D6;
            switch (s.front) {
                case "north" -> ctx.fill(sx + 1, sz, ex - 1, sz + 2, edge);
                case "east" -> ctx.fill(ex - 2, sz + 1, ex, ez - 1, edge);
                case "west" -> ctx.fill(sx, sz + 1, sx + 2, ez - 1, edge);
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
        ctx.disableScissor();
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
            String corner = site == null ? "자리 없음 · " + com.foredayner.scenario.sheet.ScenarioSheet.footprint(pickedPlace.size) + "x"
                + com.foredayner.scenario.sheet.ScenarioSheet.footprint(pickedPlace.size)
                : (world ? "좌하단 월드 (" : "좌하단 (") + cx + ", " + cz + ") · " + site.w + "x" + site.d;
            ctx.drawText(this.textRenderer, this.textRenderer.trimToWidth(corner, right() - 12 - px), px, ty + 22, WoodUi.INK_SOFT, false);
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
            int ly = controlsY() + 44;
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
        String message = ScenarioClientState.message();
        if (!message.isEmpty()) {
            ctx.drawText(this.textRenderer, this.textRenderer.trimToWidth(message, r - l), l, bottom() - 18, WoodUi.INK, false);
        }
        if (build == null || !build.placed) {
            ctx.drawText(this.textRenderer, "마을을 지을 곳을 정하세요.", l, y + 1, WoodUi.INK, false);
            ctx.drawText(this.textRenderer, this.textRenderer.trimToWidth("한가운데에 서서 오른쪽 버튼을 누르면 돼요.", r - l - 124), l, y + 12,
                WoodUi.INK_SOFT, false);
            return;
        }
        int half = com.foredayner.scenario.layout.TownLayout.SIZE / 2;
        ctx.drawText(this.textRenderer, this.textRenderer.trimToWidth("한가운데 (" + (build.originX + half) + ", " + (build.originY - 1) + ", "
            + (build.originZ + half) + ")", r - l - 124), l, y + 1, WoodUi.INK, false);
        ctx.drawText(this.textRenderer, this.textRenderer.trimToWidth("범위 (" + build.originX + ", " + build.originZ + ") ~ ("
            + (build.originX + com.foredayner.scenario.layout.TownLayout.SIZE - 1) + ", " + (build.originZ + com.foredayner.scenario.layout.TownLayout.SIZE - 1) + ")", r - l - 124), l, y + 12, WoodUi.INK_SOFT, false);
        int[] c = build.counts();
        String progress = "완료 " + c[0] + "/" + c[2] + (c[1] > 0 ? " · 실패 " + c[1] : "") + " · 토큰 " + build.tokensIn + "/" + build.tokensOut;
        ctx.drawText(this.textRenderer, this.textRenderer.trimToWidth(progress, r - l - 258), l + 258, y + 31, WoodUi.INK_SOFT, false);
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
        StringBuilder s = new StringBuilder(p.name + " (" + p.kind + ", " + p.size + " " + com.foredayner.scenario.sheet.ScenarioSheet.footprint(p.size)
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
            int[] sizes = { sheet.places.size(), sheet.npcs.size(), sheet.clues.size() };
            for (int c = 0; c < 3; c++) {
                if (mouseX >= colX(c) && mouseX < colX(c) + colWidth()) {
                    int maxRows = Math.max(1, (listBottom() - listTop()) / ROW);
                    scroll[c] = Math.max(0, Math.min(Math.max(0, sizes[c] - maxRows), scroll[c] - (int) Math.signum(vertical)));
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
            String[] heads = { "장소 " + sheet.places.size(), "NPC " + sheet.npcs.size(), "단서 " + sheet.clues.size() };
            for (int c = 0; c < 3; c++) {
                ctx.drawText(this.textRenderer, heads[c], colX(c) + 2, listTop() - 12, WoodUi.INK, false);
            }
        }
        String message = ScenarioClientState.message();
        if (!message.isEmpty()) {
            ctx.drawText(this.textRenderer, this.textRenderer.trimToWidth(message, right() - l - 20), l, bottom() - 18, WoodUi.INK, false);
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
        String message = ScenarioClientState.message();
        if (!message.isEmpty()) {
            ctx.drawText(this.textRenderer, message, l, bottom() - 18, WoodUi.INK, false);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}

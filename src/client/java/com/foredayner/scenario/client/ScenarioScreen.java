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
                default -> false;
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
        if (tab == Tab.SCENARIO) {
            initScenarioTab();
        } else if (tab == Tab.SHEET) {
            initSheetTab();
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
            WoodUi.WoodButton next = new WoodUi.WoodButton(right() - 148, bottom() - 44, 136, 18, Text.literal("배치도 만들기 (다음 단계)"),
                b -> { }, false);
            next.active = false; // step 5
            addDrawableChild(next);
        } else {
            askField = null;
        }
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
            + "칸, " + p.floors + "층" + (p.basement ? " + 지하" : "") + (p.outdoor ? ", 야외" : "") + ")");
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
            ctx.drawText(this.textRenderer, this.textRenderer.trimToWidth(sheet.title.isBlank() ? name : sheet.title, right() - l - 200),
                l, bodyTop() + 11, WoodUi.INK, false);
            String line = sheet.era + " · " + sheet.mood + " · " + timeKo(sheet.timeOfDay) + " · " + weatherKo(sheet.weather)
                + " · 바다 " + sideKo(sheet.terrain.coast) + " · 숲 " + sideKo(sheet.terrain.woods) + " · " + sheet.setting;
            ctx.drawText(this.textRenderer, this.textRenderer.trimToWidth(line, right() - l - 20), l, bodyTop() + 28, WoodUi.INK_SOFT, false);
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

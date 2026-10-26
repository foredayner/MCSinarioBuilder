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
        clearAndInit();
        if (name != null && nameField != null && ScenarioClientState.loaded() != null) {
            nameField.setText(name);
        }
    }

    @Override
    protected void init() {
        int x = left() + 6;
        for (Tab t : Tab.values()) {
            boolean ready = t == Tab.SCENARIO; // later steps open as the earlier ones are done
            WoodUi.WoodButton button = new WoodUi.WoodButton(x, top() + 6, 70, 18, Text.literal(t.label), b -> {
                tab = t;
                clearAndInit();
            }, false).selected(t == tab);
            button.active = ready;
            addDrawableChild(button);
            x += 74;
        }
        if (tab == Tab.SCENARIO) {
            initScenarioTab();
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
        WoodUi.WoodButton next = new WoodUi.WoodButton(rx, bottom() - 44, right() - rx - 10, 18, Text.literal("시트 만들기 (다음 단계)"),
            b -> { }, false);
        next.active = false; // step 4
        addDrawableChild(next);
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.renderBackground(ctx, mouseX, mouseY, delta);
        WoodUi.frame(ctx, left(), top(), right(), bottom());
        WoodUi.plaque(ctx, this.textRenderer, "AI 시나리오 공방", this.width / 2, top() - 14, WoodUi.PAPER);
        WoodUi.inset(ctx, left() + 6, bodyTop(), right() - 6, bottom() - 6, WoodUi.PARCHMENT);
        if (tab == Tab.SCENARIO && ScenarioClientState.loaded() != null) {
            WoodUi.inset(ctx, left() + 12, bodyTop() + 48, mid() - 6, bottom() - 50, WoodUi.PAPER);
        }
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
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

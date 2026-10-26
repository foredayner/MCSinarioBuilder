package com.foredayner.scenario.client;

import com.example.client.WoodUi;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

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

    public ScenarioScreen() {
        super(Text.literal("AI 시나리오 공방"));
    }

    private int left() {
        return Math.max(10, this.width / 2 - 200);
    }

    private int right() {
        return Math.min(this.width - 10, this.width / 2 + 200);
    }

    private int top() {
        return 30;
    }

    private int bottom() {
        return this.height - 20;
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
    }

    /** The wooden frame goes under the buttons. */
    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.renderBackground(ctx, mouseX, mouseY, delta);
        WoodUi.frame(ctx, left(), top(), right(), bottom());
        WoodUi.plaque(ctx, this.textRenderer, "AI 시나리오 공방", this.width / 2, top() - 14, WoodUi.PAPER);
        WoodUi.inset(ctx, left() + 6, top() + 34, right() - 6, bottom() - 6, WoodUi.PARCHMENT);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        int y = top() + 34;
        ctx.drawText(this.textRenderer, "크툴루의 부름 시나리오를 넣으면 마을을 지어 줘요.", left() + 14, y + 10, WoodUi.INK, false);
        ctx.drawText(this.textRenderer, "1) 시나리오 파일(.txt .md .pdf)을 고르거나 글을 붙여 넣어요.", left() + 14, y + 26, WoodUi.INK_SOFT, false);
        ctx.drawText(this.textRenderer, "2) AI가 장소·NPC·단서를 정리한 시트를 확인해요.", left() + 14, y + 40, WoodUi.INK_SOFT, false);
        ctx.drawText(this.textRenderer, "3) 160×160 배치도를 확인하고 고쳐요.", left() + 14, y + 54, WoodUi.INK_SOFT, false);
        ctx.drawText(this.textRenderer, "4) 장소별로 지어요.", left() + 14, y + 68, WoodUi.INK_SOFT, false);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}

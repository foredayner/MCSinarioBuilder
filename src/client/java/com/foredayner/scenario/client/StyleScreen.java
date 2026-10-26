package com.foredayner.scenario.client;

import com.example.client.WoodUi;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.EditBoxWidget;
import net.minecraft.text.Text;

/** The town's architecture guide: every building follows it. The AI writes it; the keeper can change it. */
public class StyleScreen extends Screen {

    private final Screen parent;
    private EditBoxWidget box;
    private boolean edited;

    public StyleScreen(Screen parent) {
        super(Text.literal("마을 스타일 가이드"));
        this.parent = parent;
    }

    private int l() {
        return Math.max(10, this.width / 2 - 220);
    }

    private int r() {
        return Math.min(this.width - 10, this.width / 2 + 220);
    }

    private int t() {
        return 26;
    }

    private int b() {
        return this.height - 12;
    }

    private String guide() {
        var sheet = ScenarioClientState.sheet();
        return sheet == null || sheet.styleGuide == null ? "" : sheet.styleGuide;
    }

    @Override
    protected void init() {
        String keep = box != null && edited ? box.getText() : guide();
        box = new EditBoxWidget(this.textRenderer, l() + 12, t() + 30, r() - l() - 24, b() - t() - 66,
            Text.literal("비어 있어요. \"AI로 만들기\"를 누르거나 직접 쓰세요."), Text.literal("스타일 가이드"));
        box.setMaxLength(8000);
        box.setText(keep);
        box.setChangeListener(s -> edited = true);
        addDrawableChild(box);
        int y = b() - 30;
        addDrawableChild(new WoodUi.WoodButton(l() + 12, y, 96, 18, Text.literal("AI로 만들기"), b -> {
            edited = false;
            ScenarioClientState.makeStyle();
        }, false));
        addDrawableChild(new WoodUi.WoodButton(r() - 172, y, 76, 18, Text.literal("저장"), b -> {
            var sheet = ScenarioClientState.sheet();
            if (sheet != null) {
                sheet.styleGuide = box.getText();
                ScenarioClientState.saveSheet();
            }
            edited = false;
            this.client.setScreen(parent);
        }, false));
        addDrawableChild(new WoodUi.WoodButton(r() - 92, y, 76, 18, Text.literal("닫기"), b -> this.client.setScreen(parent), false));
    }

    /** A new guide arrived from the server: show it unless the keeper is typing. */
    public void refresh() {
        if (!edited && box != null) {
            box.setText(guide());
            edited = false;
        }
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.renderBackground(ctx, mouseX, mouseY, delta);
        WoodUi.frame(ctx, l(), t(), r(), b());
        WoodUi.plaque(ctx, this.textRenderer, "마을 스타일 가이드", this.width / 2, t() - 14, WoodUi.PAPER);
        WoodUi.inset(ctx, l() + 6, t() + 6, r() - 6, b() - 6, WoodUi.PARCHMENT);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        ctx.drawText(this.textRenderer, this.textRenderer.trimToWidth("모든 건물이 따르는 시대·지역 건축 규칙이에요. 짓기 전에 고칠 수 있어요.",
            r() - l() - 24), l() + 12, t() + 14, WoodUi.INK_SOFT, false);
        String message = ScenarioClientState.message();
        if (!message.isEmpty()) {
            ctx.drawText(this.textRenderer, this.textRenderer.trimToWidth(message, r() - l() - 300), l() + 114, b() - 25, WoodUi.INK, false);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}

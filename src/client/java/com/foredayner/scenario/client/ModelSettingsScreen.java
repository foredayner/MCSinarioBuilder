package com.foredayner.scenario.client;

import com.example.client.WoodUi;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/** Which model each step uses (empty = the model set in the AI Build Assistant). */
public class ModelSettingsScreen extends Screen {

    private final Screen parent;
    private TextFieldWidget analysis;
    private TextFieldWidget layout;
    private TextFieldWidget building;

    public ModelSettingsScreen(Screen parent) {
        super(Text.literal("모델 설정"));
        this.parent = parent;
    }

    private TextFieldWidget field(int y, String value, String placeholder) {
        TextFieldWidget f = new TextFieldWidget(this.textRenderer, this.width / 2 - 20, y, 170, 14, Text.literal(""));
        f.setMaxLength(60);
        f.setText(value);
        f.setPlaceholder(Text.literal(placeholder));
        addDrawableChild(f);
        return f;
    }

    @Override
    protected void init() {
        var c = ScenarioClientState.config();
        int y = this.height / 2 - 40;
        analysis = field(y, c.analysisModel, "기본 (AI 공방 설정)");
        layout = field(y + 22, c.layoutModel, "기본 (AI 공방 설정)");
        building = field(y + 44, c.buildingModel, "기본: Claude면 Haiku 5.5");
        addDrawableChild(new WoodUi.WoodButton(this.width / 2 - 100, y + 72, 96, 18, Text.literal("저장"), b -> {
            ScenarioClientState.saveModels(analysis.getText(), layout.getText(), building.getText());
            this.client.setScreen(parent);
        }, false));
        addDrawableChild(new WoodUi.WoodButton(this.width / 2 + 4, y + 72, 96, 18, Text.literal("취소"),
            b -> this.client.setScreen(parent), false));
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.renderBackground(ctx, mouseX, mouseY, delta);
        int l = this.width / 2 - 160, r = this.width / 2 + 160, t = this.height / 2 - 70, b = this.height / 2 + 62;
        WoodUi.frame(ctx, l, t, r, b);
        WoodUi.plaque(ctx, this.textRenderer, "모델 설정", this.width / 2, t - 14, WoodUi.PAPER);
        WoodUi.inset(ctx, l + 6, t + 6, r - 6, b - 6, WoodUi.PARCHMENT);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        int x = this.width / 2 - 150, y = this.height / 2 - 40;
        ctx.drawText(this.textRenderer, "시나리오 읽기", x, y + 3, WoodUi.INK, false);
        ctx.drawText(this.textRenderer, "배치도", x, y + 25, WoodUi.INK, false);
        ctx.drawText(this.textRenderer, "건물·소품", x, y + 47, WoodUi.INK, false);
        ctx.drawText(this.textRenderer, "예: claude-sonnet-5-5 (같은 AI 연결의 다른 모델)", x, y - 18, WoodUi.INK_SOFT, false);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}

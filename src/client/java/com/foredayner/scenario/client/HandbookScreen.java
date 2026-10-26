package com.foredayner.scenario.client;

import com.example.client.WoodUi;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/** The GM handbook: places and scenes (with teleports), clues, time and weather. */
public class HandbookScreen extends Screen {

    public HandbookScreen() {
        super(Text.literal("GM 수첩"));
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        int l = this.width / 2 - 150, r = this.width / 2 + 150, t = 30, b = this.height - 30;
        WoodUi.frame(ctx, l, t, r, b);
        WoodUi.plaque(ctx, this.textRenderer, "GM 수첩", this.width / 2, t - 14, WoodUi.PAPER);
        WoodUi.inset(ctx, l + 6, t + 6, r - 6, b - 6, WoodUi.PARCHMENT);
        ctx.drawText(this.textRenderer, "아직 지은 시나리오가 없어요.", l + 14, t + 16, WoodUi.INK, false);
        ctx.drawText(this.textRenderer, "K키로 AI 시나리오 공방을 열어 마을을 지으면", l + 14, t + 32, WoodUi.INK_SOFT, false);
        ctx.drawText(this.textRenderer, "장소·장면 이동, 단서 목록, 시간·날씨가 여기에 나와요.", l + 14, t + 46, WoodUi.INK_SOFT, false);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}

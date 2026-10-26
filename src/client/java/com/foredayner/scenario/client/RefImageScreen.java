package com.foredayner.scenario.client;

import com.example.client.WoodUi;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Text;

import java.nio.file.Path;
import java.util.List;

/**
 * A place's reference picture: a photo of a real building (a web address or a file) that the AI
 * looks at once and turns into building directions; shown here so the keeper can check them.
 */
public class RefImageScreen extends Screen {

    private final Screen parent;
    private final String placeId;
    private TextFieldWidget url;
    private int scroll;

    public RefImageScreen(Screen parent, String placeId) {
        super(Text.literal("참고 사진"));
        this.parent = parent;
        this.placeId = placeId;
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

    private com.foredayner.scenario.sheet.ScenarioSheet.Place place() {
        var sheet = ScenarioClientState.sheet();
        return sheet == null ? null : sheet.place(placeId);
    }

    @Override
    protected void init() {
        var p = place();
        int x = l() + 12, w = r() - l() - 24, y = t() + 30;
        String keep = url == null ? (p != null && p.refImage.startsWith("http") ? p.refImage : "") : url.getText();
        url = new TextFieldWidget(this.textRenderer, x + 1, y + 2, w - 94, 14, Text.literal("주소"));
        url.setMaxLength(1000);
        url.setPlaceholder(Text.literal("사진 주소 (https://...jpg)"));
        url.setText(keep);
        addDrawableChild(url);
        addDrawableChild(new WoodUi.WoodButton(x + w - 88, y, 88, 18, Text.literal("주소로 정하기"),
            b -> ScenarioClientState.refUrl(placeId, url.getText()), false));
        int by = b() - 30;
        addDrawableChild(new WoodUi.WoodButton(x, by, 88, 18, Text.literal("파일 고르기"), b -> {
            Path file = ScenarioClientState.pickImage();
            if (file != null) {
                ScenarioClientState.refFile(placeId, file);
            }
        }, false));
        WoodUi.WoodButton again = new WoodUi.WoodButton(x + 92, by, 88, 18, Text.literal("다시 읽기"),
            b -> ScenarioClientState.refAgain(placeId), false);
        again.active = p != null && !p.refImage.isBlank();
        addDrawableChild(again);
        WoodUi.WoodButton clear = new WoodUi.WoodButton(x + 184, by, 60, 18, Text.literal("지우기"), b -> {
            url.setText("");
            ScenarioClientState.refUrl(placeId, "");
        }, true);
        clear.active = again.active;
        addDrawableChild(clear);
        addDrawableChild(new WoodUi.WoodButton(r() - 88, by, 76, 18, Text.literal("닫기"), b -> this.client.setScreen(parent), false));
    }

    public void refresh() {
        String typed = url == null ? null : url.getText();
        clearAndInit();
        if (typed != null) {
            url.setText(typed);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        scroll = Math.max(0, scroll - (int) Math.signum(vertical) * 3);
        return true;
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.renderBackground(ctx, mouseX, mouseY, delta);
        WoodUi.frame(ctx, l(), t(), r(), b());
        WoodUi.plaque(ctx, this.textRenderer, "참고 사진", this.width / 2, t() - 14, WoodUi.PAPER);
        WoodUi.inset(ctx, l() + 6, t() + 6, r() - 6, b() - 6, WoodUi.PARCHMENT);
        WoodUi.inset(ctx, l() + 10, t() + 54, r() - 10, b() - 36, WoodUi.PAPER);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        var p = place();
        int x = l() + 12, w = r() - l() - 24;
        ctx.drawText(this.textRenderer, this.textRenderer.trimToWidth((p == null ? placeId : p.name)
            + " · 실제 건물 사진을 붙이면 AI가 보고 따라 지어요 (상표·글자는 따라 하지 않아요)", w), x, t() + 14, WoodUi.INK, false);
        String body;
        if (p == null || p.refImage.isBlank()) {
            body = "아직 사진이 없어요. 사진 주소를 넣거나 파일을 고르세요 (png, jpg, gif, webp, 4.5 MB까지).";
        } else if (p.refDescription.isBlank()) {
            body = "사진: " + p.refImage + "\n\nAI가 아직 읽지 않았어요. 잠시 기다리거나 \"다시 읽기\"를 누르세요.";
        } else {
            body = "사진: " + p.refImage + "\n\nAI가 읽은 내용 (지을 때 따라 해요):\n" + p.refDescription;
        }
        List<OrderedText> lines = new java.util.ArrayList<>();
        for (String para : body.split("\n", -1)) {
            lines.addAll(para.isEmpty() ? List.of(OrderedText.EMPTY) : this.textRenderer.wrapLines(StringVisitable.plain(para), w - 8));
        }
        int top = t() + 58, bottom = b() - 40, per = (bottom - top) / 10;
        scroll = Math.min(scroll, Math.max(0, lines.size() - per));
        for (int i = 0; i < per && i + scroll < lines.size(); i++) {
            ctx.drawText(this.textRenderer, lines.get(i + scroll), x + 2, top + i * 10, WoodUi.INK, false);
        }
        String message = ScenarioClientState.message();
        if (!message.isEmpty()) {
            ctx.drawText(this.textRenderer, this.textRenderer.trimToWidth(message, r() - l() - 360), x + 252, b() - 25, WoodUi.INK, false);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}

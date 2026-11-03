package com.foredayner.scenario.client;

import com.example.client.WoodUi;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Text;

import java.nio.file.Path;

/** The town's reference map: a picture the AI follows when it lays the town out. */
public class MapScreen extends Screen {

    private final Screen parent;
    private TextFieldWidget url;

    public MapScreen(Screen parent) {
        super(Text.literal("참고 지도"));
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

    private boolean hasMap() {
        var sheet = ScenarioClientState.sheet();
        return sheet != null && sheet.mapImage != null && !sheet.mapImage.isBlank();
    }

    @Override
    protected void init() {
        int x = l() + 12, w = r() - l() - 24, y = t() + 30;
        String keep = url == null ? "" : url.getText();
        url = new TextFieldWidget(this.textRenderer, x + 1, y + 2, w - 94, 14, Text.literal("주소"));
        url.setMaxLength(1000);
        url.setPlaceholder(Text.literal("지도 그림 주소 (https://...png)"));
        url.setText(keep);
        addDrawableChild(url);
        addDrawableChild(new WoodUi.WoodButton(x + w - 88, y, 88, 18, Text.literal("주소로 정하기"),
            b -> ScenarioClientState.mapUrl(url.getText()), false));
        int by = b() - 30;
        addDrawableChild(new WoodUi.WoodButton(x, by, 88, 18, Text.literal("파일 고르기"), b -> {
            Path file = ScenarioClientState.pickImage();
            if (file != null) {
                ScenarioClientState.mapFile(file);
            }
        }, false));
        WoodUi.WoodButton clear = new WoodUi.WoodButton(x + 92, by, 60, 18, Text.literal("지우기"), b -> {
            url.setText("");
            ScenarioClientState.mapClear();
        }, true);
        clear.active = hasMap();
        addDrawableChild(clear);
        addDrawableChild(new WoodUi.WoodButton(r() - 88, by, 76, 18, Text.literal("닫기"), b -> this.client.setScreen(parent), false));
        var sheet = ScenarioClientState.sheet();
        if (sheet != null) {
            // What the map shows, and whether its unnamed buildings become houses.
            int ry = by - 22;
            WoodUi.WoodButton kind = new WoodUi.WoodButton(x, ry, 104, 18, Text.literal("지도 종류: " + kindKo(sheet.mapKind)), b -> {
                var all = com.foredayner.scenario.sheet.ScenarioSheet.MAP_KINDS;
                sheet.mapKind = all.get((Math.max(0, all.indexOf(sheet.mapKind)) + 1) % all.size());
                ScenarioClientState.saveSheet();
                refresh();
            }, false);
            kind.setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal(
                "자동: AI가 지도를 보고 판단해요.\n마을: 위에서 본 건물 배치.\n야외: 길, 숲, 물, 표시 지점 (건물이 거의 없음).\n"
                    + "실내: 한 건물의 방 배치도 (마을 배치에는 쓰지 않아요).")));
            addDrawableChild(kind);
            WoodUi.WoodButton houses = new WoodUi.WoodButton(x + 108, ry, 104, 18,
                Text.literal(sheet.mapFillers ? "지도 민가: 켬" : "지도 민가: 끔"), b -> {
                    sheet.mapFillers = !sheet.mapFillers;
                    ScenarioClientState.saveSheet();
                    refresh();
                }, false);
            houses.setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal(
                "켜면: 지도에 그려진 건물 중 시트 장소가 들어가지 않은 곳에 시대에 맞는 평범한 민가를 지어요 (최대 "
                    + 12 + "채, 디자인 두세 가지를 돌려 써요).\n건물이 그려지지 않은 지도에서는 아무것도 짓지 않아요.")));
            addDrawableChild(houses);
        }
    }

    private static String kindKo(String kind) {
        return switch (kind == null ? "" : kind) {
            case "town" -> "마을";
            case "outdoor" -> "야외";
            case "indoor" -> "실내";
            default -> "자동";
        };
    }

    public void refresh() {
        String typed = url == null ? null : url.getText();
        clearAndInit();
        if (typed != null) {
            url.setText(typed);
        }
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.renderBackground(ctx, mouseX, mouseY, delta);
        WoodUi.frame(ctx, l(), t(), r(), b());
        WoodUi.plaque(ctx, this.textRenderer, "참고 지도", this.width / 2, t() - 14, WoodUi.PAPER);
        WoodUi.inset(ctx, l() + 6, t() + 6, r() - 6, b() - 6, WoodUi.PARCHMENT);
        WoodUi.inset(ctx, l() + 10, t() + 54, r() - 10, b() - 58, WoodUi.PAPER);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        int x = l() + 12, w = r() - l() - 24;
        ctx.drawText(this.textRenderer, this.textRenderer.trimToWidth(
            "마을 지도 그림을 올리면 AI가 지도를 따라 배치해요 (지도 전체 = 160x160 땅, 위쪽 = 북쪽)", w), x, t() + 14, WoodUi.INK, false);
        int top = t() + 58, bottom = b() - 62;
        int size = Math.max(40, Math.min(bottom - top, w / 2));
        if (MapOverlay.available()) {
            MapOverlay.draw(ctx, x + 2, top, size, 255);
        } else {
            ctx.fill(x + 2, top, x + 2 + size, top + size, 0x30000000);
        }
        int tx = x + size + 12, tw = w - size - 14, ty = top;
        String body = hasMap()
            ? "지도: " + ScenarioClientState.sheet().mapImage.substring(5) + "\n\n배치도 탭의 \"AI로 배치\"를 누르면 지도에 적힌 이름과 장소 이름을 맞춰 같은 자리에 놓고, 길도 지도대로 깔아요."
                + "\n\n지도에 없는 장소는 어울리는 자리(마을 밖 성격이면 산·숲 쪽 가장자리)에 놓고, 배치도에 파란 테두리로 보여요. 길에서 먼 집에는 오솔길을 이어요."
                + (MapOverlay.available() ? "" : "\n\n(이 형식은 배치도 밑에 겹쳐 보이지 않아요. png나 jpg를 쓰면 겹쳐 볼 수 있어요.)")
            : "아직 지도가 없어요. 지도 그림 주소를 넣거나 파일을 고르세요 (png, jpg, gif, webp, 4.5 MB까지).";
        for (String para : body.split("\n", -1)) {
            for (OrderedText line : para.isEmpty() ? java.util.List.of(OrderedText.EMPTY)
                : this.textRenderer.wrapLines(StringVisitable.plain(para), tw)) {
                if (ty > bottom - 10) {
                    break;
                }
                ctx.drawText(this.textRenderer, line, tx, ty, WoodUi.INK, false);
                ty += 10;
            }
        }
        String message = ScenarioClientState.message();
        if (!message.isEmpty()) {
            ctx.drawText(this.textRenderer, this.textRenderer.trimToWidth(message, r() - l() - 270), x + 160, b() - 25, WoodUi.INK, false);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}

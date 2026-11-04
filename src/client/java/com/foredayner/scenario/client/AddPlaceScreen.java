package com.foredayner.scenario.client;

import com.example.client.WoodUi;
import com.foredayner.scenario.sheet.ScenarioSheet;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.List;

/**
 * The keeper adds a place to the sheet by hand: its name, size, kind and a note for the AI that
 * designs it (or, on a building stage, a room on a floor). It is not placed yet: on the 배치도 it
 * waits in the list until "빈 자리에 놓기" or dragging puts it somewhere (past the map too).
 */
public class AddPlaceScreen extends Screen {

    /** named: a building of the story; house: an ordinary home; outdoor: a place in the open. */
    static final List<String> KINDS = List.of("named", "house", "outdoor");
    private static final List<String> KINDS_KO = List.of("네임드 건물", "일반 민가", "야외 장소");

    private final Screen parent;
    private TextFieldWidget nameField;
    private TextFieldWidget notesField;
    private String size = "M";
    private String kind = "named";
    private int level = 0;
    private int cellars = 0;
    private String problem = "";

    public AddPlaceScreen(Screen parent) {
        super(Text.literal("장소 추가"));
        this.parent = parent;
    }

    private boolean rooms() {
        var sheet = ScenarioClientState.sheet();
        return sheet != null && sheet.building();
    }

    private int l() {
        return this.width / 2 - 150;
    }

    private int r() {
        return this.width / 2 + 150;
    }

    private int t() {
        return Math.max(30, this.height / 2 - 70);
    }

    private int b() {
        return t() + 150;
    }

    @Override
    protected void init() {
        String name = nameField == null ? "" : nameField.getText();
        String notes = notesField == null ? "" : notesField.getText();
        int x = l() + 12, w = r() - l() - 24, y = t() + 22;
        nameField = new TextFieldWidget(this.textRenderer, x + 44, y, w - 44, 16, Text.literal("이름"));
        nameField.setMaxLength(40);
        nameField.setText(name);
        nameField.setPlaceholder(Text.literal(rooms() ? "예: 서재" : "예: 낡은 등대"));
        addDrawableChild(nameField);
        y += 24;
        addDrawableChild(new WoodUi.WoodButton(x + 44, y, 60, 18, Text.literal("크기 " + size), b -> {
            size = ScenarioSheet.SIZES.get((ScenarioSheet.SIZES.indexOf(size) + 1) % ScenarioSheet.SIZES.size());
            clearAndInit();
        }, false));
        if (rooms()) {
            addDrawableChild(new WoodUi.WoodButton(x + 108, y, 70, 18, Text.literal(level < 0 ? "지하 " + (-level) + "층" : (level + 1) + "층"),
                b -> {
                    level = level >= 3 ? -1 : level + 1;
                    clearAndInit();
                }, false));
        } else {
            addDrawableChild(new WoodUi.WoodButton(x + 108, y, 90, 18, Text.literal(KINDS_KO.get(KINDS.indexOf(kind))), b -> {
                kind = KINDS.get((KINDS.indexOf(kind) + 1) % KINDS.size());
                clearAndInit();
            }, false));
            if (!"outdoor".equals(kind)) {
                addDrawableChild(new WoodUi.WoodButton(x + 202, y, 62, 18, Text.literal(cellars == 0 ? "지하 없음" : "지하 " + cellars + "층"),
                    b -> {
                        cellars = (cellars + 1) % (ScenarioSheet.MAX_CELLARS + 1);
                        clearAndInit();
                    }, false));
            }
        }
        y += 24;
        notesField = new TextFieldWidget(this.textRenderer, x + 44, y, w - 44, 16, Text.literal("메모"));
        notesField.setMaxLength(300);
        notesField.setText(notes);
        notesField.setPlaceholder(Text.literal("어떤 곳인지 (AI가 지을 때 참고해요)"));
        addDrawableChild(notesField);
        addDrawableChild(new WoodUi.WoodButton(r() - 172, b() - 28, 76, 18, Text.literal("추가"), b -> add(), false));
        addDrawableChild(new WoodUi.WoodButton(r() - 92, b() - 28, 76, 18, Text.literal("취소"), b -> this.client.setScreen(parent), false));
        setInitialFocus(nameField);
    }

    private void add() {
        var sheet = ScenarioClientState.sheet();
        if (sheet == null) {
            return;
        }
        ScenarioSheet.Place p = sheet.newPlace(nameField.getText(), size, kind, level, notesField.getText());
        if (p == null) {
            problem = "이름을 적어 주세요.";
            return;
        }
        if (!p.outdoor && !sheet.building()) {
            p.cellars = cellars;
            p.basement = cellars > 0;
        }
        sheet.places.add(p);
        ScenarioClientState.saveSheet();
        ScenarioClientState.log("장소를 추가했어요: " + p.name + (rooms() ? "" : ". 배치도에서 \"빈 자리에 놓기\"나 끌어서 자리를 정하세요."));
        this.client.setScreen(parent);
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.renderBackground(ctx, mouseX, mouseY, delta);
        WoodUi.frame(ctx, l(), t(), r(), b());
        WoodUi.plaque(ctx, this.textRenderer, rooms() ? "방 추가" : "장소 추가", this.width / 2, t() - 14, WoodUi.PAPER);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        int x = l() + 12, y = t() + 22;
        ctx.drawText(this.textRenderer, "이름", x, y + 4, WoodUi.INK, false);
        ctx.drawText(this.textRenderer, rooms() ? "크기·층" : "크기·종류", x, y + 29, WoodUi.INK, false);
        ctx.drawText(this.textRenderer, "메모", x, y + 52, WoodUi.INK, false);
        if (!problem.isEmpty()) {
            ctx.drawText(this.textRenderer, problem, x, b() - 24, 0xFFB0281A, false);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}

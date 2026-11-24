package com.textboxes.gui;

import com.textboxes.TextBox;
import com.textboxes.TextBoxRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Multi-line text editor for one box, with a live preview of the box in place. */
public class TextEditScreen extends Screen {
    private final EditorScreen parent;
    private final TextBox box;
    private final String original;
    private MultiLineEditBox editor;
    private int areaX, areaY, areaW, areaH;

    public TextEditScreen(EditorScreen parent, TextBox box) {
        super(Component.literal("Edit Text"));
        this.parent = parent;
        this.box = box;
        this.original = box.text;
    }

    @Override
    protected void init() {
        areaW = Math.min(340, width - 20);
        areaH = Math.max(40, Math.min(120, height / 2 - 50));
        areaX = (width - areaW) / 2;
        // Put the editor on the opposite half of the screen from the box so the live preview stays visible.
        boolean boxInLowerHalf = box.y + box.height / 2 > height / 2;
        areaY = boxInLowerHalf ? 30 : height - areaH - 56;

        String current = editor != null ? editor.getValue() : box.text;
        editor = MultiLineEditBox.builder()
                .setX(areaX)
                .setY(areaY)
                .setPlaceholder(Component.literal("Type your text here..."))
                .build(font, areaW, areaH, Component.literal("Text"));
        editor.setCharacterLimit(10000);
        editor.setValue(current);
        editor.setValueListener(s -> box.text = s);
        addRenderableWidget(editor);

        int bw = (areaW - 8) / 5;
        int by = areaY + areaH + 12;
        addRenderableWidget(Button.builder(Component.literal("+ Item"),
                        b -> openPicker(new ItemPickerScreen(this, this::insert)))
                .bounds(areaX, by, bw, 18).build());
        addRenderableWidget(Button.builder(Component.literal("+ Image"),
                        b -> openPicker(new ImagePickerScreen(this, "", false, name -> insert("{img:" + name + "}"))))
                .bounds(areaX + (bw + 2), by, bw, 18).build());
        addRenderableWidget(Button.builder(Component.literal("+ Font"),
                        b -> openPicker(TokenPickerScreen.fonts(this, this::insert)))
                .bounds(areaX + 2 * (bw + 2), by, bw, 18).build());
        addRenderableWidget(Button.builder(Component.literal("+ Color"),
                        b -> openPicker(TokenPickerScreen.colors(this, this::insert)))
                .bounds(areaX + 3 * (bw + 2), by, bw, 18).build());
        addRenderableWidget(Button.builder(Component.literal("+ Info"),
                        b -> openPicker(TokenPickerScreen.info(this, this::insert)))
                .bounds(areaX + 4 * (bw + 2), by, bw, 18).build());

        int hw = (areaW - 2) / 2;
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(areaX, by + 20, hw, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> {
            box.text = original;
            onClose();
        }).bounds(areaX + hw + 2, by + 20, hw, 18).build());

        setInitialFocus(editor);
    }

    private void openPicker(Screen picker) {
        if (minecraft != null) minecraft.gui.setScreen(picker);
    }

    /** Inserts a token at the cursor (replacing any selection). */
    void insert(String token) {
        editor.textField.insertText(token);
        box.text = editor.getValue();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
        // Keep the world visible.
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        TextBoxRenderer.render(ctx, font, box);
        ctx.outline(box.x - 1, box.y - 1, box.width + 2, box.height + 2, 0xFFFFD700);
        ctx.nextStratum();
        ctx.fill(areaX - 4, areaY - 26, areaX + areaW + 4, areaY + areaH + 54, 0xC0101018);
        ctx.text(font, Component.literal("Edit text  (Enter = new line, color codes work)"),
                areaX, areaY - 22, 0xFFFFFFFF, true);
        ctx.text(font, Component.literal(font.plainSubstrByWidth(
                        "{item:paper|5}  {img:a.png}  {font:galactic}..{font}  {#FF8800}..{color}  {fps}", areaW)),
                areaX, areaY - 11, 0xFFAAAAAA, false);
        super.extractRenderState(ctx, mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        parent.markDirty();
        if (minecraft != null) minecraft.gui.setScreen(parent);
    }
}

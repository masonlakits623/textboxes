package com.textboxes.gui;

import com.textboxes.TextBox;
import com.textboxes.TextBoxRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.EditBoxWidget;
import net.minecraft.text.Text;

/** Multi-line text editor for one box, with a live preview of the box in place. */
public class TextEditScreen extends Screen {
    private final EditorScreen parent;
    private final TextBox box;
    private final String original;
    private EditBoxWidget editor;
    private int areaX, areaY, areaW, areaH;

    public TextEditScreen(EditorScreen parent, TextBox box) {
        super(Text.literal("Edit Text"));
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
        areaY = boxInLowerHalf ? 30 : height - areaH - 36;

        String current = editor != null ? editor.getText() : box.text;
        editor = EditBoxWidget.builder()
                .x(areaX)
                .y(areaY)
                .placeholder(Text.literal("Type your text here..."))
                .build(textRenderer, areaW, areaH, Text.literal("Text"));
        editor.setMaxLength(10000);
        editor.setText(current);
        editor.setChangeListener(s -> box.text = s);
        addDrawableChild(editor);

        int bw = (areaW - 6) / 4;
        int by = areaY + areaH + 12;
        addDrawableChild(ButtonWidget.builder(Text.literal("+ Item"),
                        b -> openPicker(new ItemPickerScreen(this, this::insert)))
                .dimensions(areaX, by, bw, 18).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("+ Image"),
                        b -> openPicker(new ImagePickerScreen(this, "", false, name -> insert("{img:" + name + "}"))))
                .dimensions(areaX + (bw + 2), by, bw, 18).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close())
                .dimensions(areaX + 2 * (bw + 2), by, bw, 18).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), b -> {
            box.text = original;
            close();
        }).dimensions(areaX + 3 * (bw + 2), by, bw, 18).build());

        setInitialFocus(editor);
    }

    private void openPicker(Screen picker) {
        if (client != null) client.setScreen(picker);
    }

    /** Inserts a token at the cursor (replacing any selection). */
    void insert(String token) {
        editor.editBox.replaceSelection(token);
        box.text = editor.getText();
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        // Keep the world visible.
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        TextBoxRenderer.render(ctx, textRenderer, box);
        ctx.drawStrokedRectangle(box.x - 1, box.y - 1, box.width + 2, box.height + 2, 0xFFFFD700);
        ctx.createNewRootLayer();
        ctx.fill(areaX - 4, areaY - 26, areaX + areaW + 4, areaY + areaH + 34, 0xC0101018);
        ctx.drawText(textRenderer, Text.literal("Edit text  (Enter = new line, color codes work)"),
                areaX, areaY - 22, 0xFFFFFFFF, true);
        ctx.drawText(textRenderer, Text.literal("{item:paper|5} = item renamed \"5\"   {img:file.png} = image"),
                areaX, areaY - 11, 0xFFAAAAAA, false);
        super.render(ctx, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        parent.markDirty();
        if (client != null) client.setScreen(parent);
    }
}

package com.textboxes.gui;

import com.textboxes.ImageManager;
import com.textboxes.TextBox;
import com.textboxes.TextBoxRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.List;
import java.util.function.Consumer;

/** Lists images in config/textboxes/images/ with thumbnails. */
public class ImagePickerScreen extends Screen {
    private static final int ENTRY_H = 24;
    private final Screen parent;
    private final String current;
    private final boolean allowNone;
    private final Consumer<String> onPick;
    private List<String> files = List.of();
    private int scroll;
    private int listX, listY, listW, listBottom;

    /**
     * @param current   currently chosen image (highlighted), may be empty
     * @param allowNone show a "No image" button that picks ""
     * @param onPick    receives the chosen relative image path
     */
    public ImagePickerScreen(Screen parent, String current, boolean allowNone, Consumer<String> onPick) {
        super(Text.literal("Choose Image"));
        this.parent = parent;
        this.current = ImageManager.normalizeName(current);
        this.allowNone = allowNone;
        this.onPick = onPick;
    }

    @Override
    protected void init() {
        files = ImageManager.listImages();
        listW = Math.min(320, width - 20);
        listX = (width - listW) / 2;
        listY = 26;
        listBottom = height - 30;
        scroll = Math.max(0, Math.min(scroll, maxScroll()));

        int bw = (listW - 6) / 4;
        int by = height - 24;
        addDrawableChild(ButtonWidget.builder(Text.literal("Open folder"), b -> ImageManager.openFolder())
                .dimensions(listX, by, bw, 18).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Reload"), b -> {
            ImageManager.reload();
            clearAndInit();
        }).dimensions(listX + (bw + 2), by, bw, 18).build());
        ButtonWidget none = addDrawableChild(ButtonWidget.builder(Text.literal("No image"), b -> {
            onPick.accept("");
            close();
        }).dimensions(listX + 2 * (bw + 2), by, bw, 18).build());
        none.active = allowNone;
        addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), b -> close())
                .dimensions(listX + 3 * (bw + 2), by, bw, 18).build());
    }

    private int maxScroll() {
        return Math.max(0, files.size() * ENTRY_H - (listBottom - listY));
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float deltaTicks) {
        ctx.fill(0, 0, width, height, 0x90000000);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.drawCenteredTextWithShadow(textRenderer, title, width / 2, 9, 0xFFFFFFFF);
        ctx.fill(listX, listY, listX + listW, listBottom, 0xC0000000);
        ctx.drawStrokedRectangle(listX - 1, listY - 1, listW + 2, listBottom - listY + 2, 0xFF505060);

        if (files.isEmpty()) {
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("No images found."), width / 2, listY + 12, 0xFFFFFFFF);
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("Put PNG, JPG, GIF or BMP files in"),
                    width / 2, listY + 28, 0xFFAAAAAA);
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("config/textboxes/images/ then press Reload."),
                    width / 2, listY + 40, 0xFFAAAAAA);
        } else {
            ctx.enableScissor(listX, listY, listX + listW, listBottom);
            for (int i = 0; i < files.size(); i++) {
                int y = listY + i * ENTRY_H - scroll;
                if (y + ENTRY_H < listY || y > listBottom) continue;
                String name = files.get(i);
                boolean hovered = mouseX >= listX && mouseX < listX + listW && mouseY >= y && mouseY < y + ENTRY_H
                        && mouseY >= listY && mouseY < listBottom;
                if (name.equals(current)) {
                    ctx.fill(listX, y, listX + listW, y + ENTRY_H, 0x5000AA00);
                } else if (hovered) {
                    ctx.fill(listX, y, listX + listW, y + ENTRY_H, 0x40FFFFFF);
                }
                ImageManager.LoadedImage img = ImageManager.get(name);
                if (img != null) {
                    TextBoxRenderer.drawImage(ctx, img, listX + 2, y + 2, 20, 20, TextBox.ImageFit.FIT, 0xFFFFFFFF);
                    String dims = img.width() + "x" + img.height();
                    ctx.drawText(textRenderer, Text.literal(dims), listX + listW - 4 - textRenderer.getWidth(dims),
                            y + 8, 0xFF888888, false);
                } else {
                    ctx.drawText(textRenderer, Text.literal("?"), listX + 9, y + 8, 0xFFFF5555, false);
                }
                String shown = textRenderer.trimToWidth(name, listW - 90);
                ctx.drawText(textRenderer, Text.literal(shown), listX + 28, y + 8, img != null ? 0xFFFFFFFF : 0xFFFF5555, true);
            }
            ctx.disableScissor();
        }
        super.render(ctx, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (super.mouseClicked(click, doubled)) return true;
        double mx = click.x(), my = click.y();
        if (mx >= listX && mx < listX + listW && my >= listY && my < listBottom) {
            int idx = (int) ((my - listY + scroll) / ENTRY_H);
            if (idx >= 0 && idx < files.size()) {
                onPick.accept(files.get(idx));
                close();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) (verticalAmount * ENTRY_H)));
        return true;
    }

    @Override
    public void close() {
        if (parent instanceof EditorScreen editor) editor.markDirty();
        if (client != null) client.setScreen(parent);
    }
}

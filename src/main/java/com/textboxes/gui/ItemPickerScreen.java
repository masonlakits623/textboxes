package com.textboxes.gui;

import com.textboxes.InlineText;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Grid of every registered item with a search box and a "rename to" box. The rename is applied to the
 * preview too, so resource packs that change an item's look by its custom name show here.
 */
public class ItemPickerScreen extends Screen {
    private static final int CELL = 18;
    private static String lastSearch = "";
    private static String lastName = "";

    private final Screen parent;
    private final Consumer<String> onPick;
    private final List<Item> all = new ArrayList<>();
    private final List<Item> filtered = new ArrayList<>();
    private final List<ItemStack> stacks = new ArrayList<>();
    private TextFieldWidget search;
    private TextFieldWidget rename;
    private int gridX, gridY, gridW, gridBottom, cols, scroll;

    public ItemPickerScreen(Screen parent, Consumer<String> onPick) {
        super(Text.literal("Insert Item"));
        this.parent = parent;
        this.onPick = onPick;
    }

    @Override
    protected void init() {
        if (all.isEmpty()) {
            Registries.ITEM.stream().filter(i -> i != Items.AIR).forEach(all::add);
        }
        int w = Math.min(380, width - 20);
        cols = Math.max(1, (w - 8) / CELL);
        gridW = cols * CELL;
        gridX = (width - gridW) / 2;
        int half = (gridW - 4) / 2;

        search = new TextFieldWidget(textRenderer, gridX, 20, half, 16, Text.literal("Search"));
        search.setMaxLength(64);
        search.setText(lastSearch);
        search.setPlaceholder(Text.literal("Search items...").formatted(Formatting.DARK_GRAY));
        search.setChangedListener(s -> {
            lastSearch = s;
            scroll = 0;
            refilter();
        });
        addDrawableChild(search);

        rename = new TextFieldWidget(textRenderer, gridX + half + 4, 20, half, 16, Text.literal("Rename"));
        rename.setMaxLength(50);
        rename.setText(lastName);
        rename.setPlaceholder(Text.literal("Rename to (optional)").formatted(Formatting.DARK_GRAY));
        rename.setTextPredicate(s -> s.indexOf('{') < 0 && s.indexOf('}') < 0 && s.indexOf('|') < 0);
        rename.setChangedListener(s -> {
            lastName = s;
            rebuildStacks();
        });
        addDrawableChild(rename);

        gridY = 52;
        gridBottom = height - 30;
        addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), b -> close())
                .dimensions(width / 2 - 50, height - 24, 100, 18).build());

        refilter();
        setInitialFocus(search);
    }

    private void refilter() {
        String q = lastSearch.trim().toLowerCase(Locale.ROOT);
        filtered.clear();
        for (Item item : all) {
            if (q.isEmpty()
                    || Registries.ITEM.getId(item).toString().contains(q)
                    || item.getName().getString().toLowerCase(Locale.ROOT).contains(q)) {
                filtered.add(item);
            }
        }
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
        rebuildStacks();
    }

    private void rebuildStacks() {
        stacks.clear();
        for (Item item : filtered) {
            ItemStack s = new ItemStack(item);
            if (!lastName.isEmpty()) s.set(DataComponentTypes.CUSTOM_NAME, Text.literal(lastName));
            stacks.add(s);
        }
    }

    private int rows() {
        return (filtered.size() + cols - 1) / cols;
    }

    private int maxScroll() {
        return Math.max(0, rows() * CELL - (gridBottom - gridY));
    }

    private int indexAt(double mx, double my) {
        if (mx < gridX || mx >= gridX + gridW || my < gridY || my >= gridBottom) return -1;
        int col = (int) ((mx - gridX) / CELL);
        int row = (int) ((my - gridY + scroll) / CELL);
        int idx = row * cols + col;
        return idx >= 0 && idx < filtered.size() ? idx : -1;
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float deltaTicks) {
        ctx.fill(0, 0, width, height, 0xA0000000);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.drawCenteredTextWithShadow(textRenderer, title, width / 2, 7, 0xFFFFFFFF);
        String info = filtered.size() + " items" + (lastName.isEmpty() ? "" : "  |  renamed to \"" + lastName + "\"");
        ctx.drawText(textRenderer, Text.literal(info), gridX, 40, 0xFFAAAAAA, false);

        ctx.fill(gridX - 2, gridY - 2, gridX + gridW + 2, gridBottom + 2, 0xC0000000);
        ctx.drawStrokedRectangle(gridX - 3, gridY - 3, gridW + 6, gridBottom - gridY + 6, 0xFF505060);

        int hovered = indexAt(mouseX, mouseY);
        ctx.enableScissor(gridX, gridY, gridX + gridW, gridBottom);
        int firstRow = scroll / CELL;
        int lastRow = Math.min(rows() - 1, (scroll + gridBottom - gridY) / CELL);
        for (int row = firstRow; row <= lastRow; row++) {
            for (int col = 0; col < cols; col++) {
                int idx = row * cols + col;
                if (idx >= stacks.size()) break;
                int x = gridX + col * CELL;
                int y = gridY + row * CELL - scroll;
                if (idx == hovered) ctx.fill(x, y, x + CELL, y + CELL, 0x60FFFFFF);
                ctx.drawItem(stacks.get(idx), x + 1, y + 1);
            }
        }
        ctx.disableScissor();

        if (filtered.isEmpty()) {
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("No items match."), width / 2, gridY + 10, 0xFFAAAAAA);
        }
        if (maxScroll() > 0) {
            int trackH = gridBottom - gridY;
            int thumbH = Math.max(10, trackH * trackH / (rows() * CELL));
            int thumbY = gridY + (trackH - thumbH) * scroll / maxScroll();
            ctx.fill(gridX + gridW + 4, gridY, gridX + gridW + 6, gridBottom, 0x40FFFFFF);
            ctx.fill(gridX + gridW + 4, thumbY, gridX + gridW + 6, thumbY + thumbH, 0xC0FFFFFF);
        }

        super.render(ctx, mouseX, mouseY, delta);

        if (hovered >= 0) {
            Item item = filtered.get(hovered);
            Identifier id = Registries.ITEM.getId(item);
            List<Text> tip = new ArrayList<>();
            tip.add(lastName.isEmpty() ? item.getName() : Text.literal(lastName).formatted(Formatting.ITALIC));
            tip.add(Text.literal(InlineText.itemToken(id, lastName)).formatted(Formatting.GRAY));
            ctx.drawTooltip(textRenderer, tip, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (super.mouseClicked(click, doubled)) return true;
        int idx = indexAt(click.x(), click.y());
        if (idx >= 0) {
            Identifier id = Registries.ITEM.getId(filtered.get(idx));
            onPick.accept(InlineText.itemToken(id, lastName.trim()));
            close();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.signum(verticalAmount) * CELL));
        return true;
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }
}

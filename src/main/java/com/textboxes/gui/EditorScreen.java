package com.textboxes.gui;

import com.textboxes.ImageManager;
import com.textboxes.TextBox;
import com.textboxes.TextBoxRenderer;
import com.textboxes.TextboxConfig;
import com.textboxes.TextboxesClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/** Main editor: toolbar on top, property panel on the side, boxes are dragged/resized directly. */
public class EditorScreen extends Screen {
    private enum Tab {
        BOX("Box", 10), TEXT("Text", 10), COLOR("Color", 9), IMAGE("Image", 6);
        final String title;
        final int rows;
        Tab(String title, int rows) { this.title = title; this.rows = rows; }
    }

    private enum PanelMode { RIGHT, LEFT, HIDDEN }

    private enum ColorTarget {
        BACKGROUND("Background"), GRADIENT("Gradient end"), TEXT("Text"), BORDER("Border"),
        IMAGE("Image tint"), SHADOW("Box shadow");
        final String title;
        ColorTarget(String title) { this.title = title; }
    }

    private enum Drag { NONE, MOVE, RESIZE }

    private record Label(int row, int x, String text, int color) {}

    private static final int PANEL_W = 150;
    private static final int ROW = 18;
    private static final int WH = 16;
    private static final int HANDLE = 6;
    private static final int[] SWATCHES = {
            0xFFFFFF, 0xC0C0C0, 0x808080, 0x404040, 0x000000, 0xFF5555, 0xAA0000, 0xFFAA00,
            0xFFFF55, 0x55FF55, 0x00AA00, 0x55FFFF, 0x00AAAA, 0x5555FF, 0x0000AA, 0xFF55FF
    };

    // Remembered between openings
    private static Tab tab = Tab.BOX;
    private static PanelMode panelMode = PanelMode.RIGHT;
    private static ColorTarget colorTarget = ColorTarget.BACKGROUND;
    private static int scroll = 0;
    private static boolean snap = true;

    private static final int SNAP_DIST = 4;
    private static final int MAX_HISTORY = 100;
    /** Ticks a change must stay unchanged before it becomes one undo step (so typing/dragging is one step). */
    private static final int SETTLE_TICKS = 8;

    private final TextboxConfig config = TextboxesClient.CONFIG;
    private final List<String> history = new ArrayList<>();
    private int historyIndex;
    private String pendingSnapshot;
    private int pendingTicks;
    private ButtonWidget undoButton, redoButton;
    private int guideX = -1, guideY = -1;
    private int selected = -1;
    private Drag drag = Drag.NONE;
    private int dragOffX, dragOffY;
    private boolean dirty;

    private int tx, ty, toolbarBottom;
    private int panelX, panelY, panelBottom, contentTop, px, iw;
    private final List<Label> labels = new ArrayList<>();

    private ValueSlider rSlider, gSlider, bSlider, aSlider;
    private TextFieldWidget hexField;
    private boolean syncing;

    public EditorScreen() {
        super(Text.literal("Textboxes Editor"));
        history.add(config.snapshot());
        historyIndex = 0;
    }

    // ------------------------------------------------------------------ undo / redo

    @Override
    public void tick() {
        super.tick();
        if (drag != Drag.NONE) return;
        String now = config.snapshot();
        if (now.equals(history.get(historyIndex))) {
            pendingSnapshot = null;
        } else if (!now.equals(pendingSnapshot)) {
            pendingSnapshot = now;
            pendingTicks = 0;
        } else if (++pendingTicks >= SETTLE_TICKS) {
            commitHistory(now);
        }
        updateUndoButtons();
    }

    private void commitHistory(String snap) {
        while (history.size() > historyIndex + 1) history.remove(history.size() - 1);
        history.add(snap);
        if (history.size() > MAX_HISTORY) history.remove(0);
        historyIndex = history.size() - 1;
        pendingSnapshot = null;
        updateUndoButtons();
    }

    /** Records a finished change right away (used after drags and toolbar actions). */
    private void recordNow() {
        String now = config.snapshot();
        if (!now.equals(history.get(historyIndex))) commitHistory(now);
    }

    private void undo() {
        recordNow();
        if (historyIndex <= 0) return;
        historyIndex--;
        applyHistory();
    }

    private void redo() {
        if (historyIndex >= history.size() - 1) return;
        historyIndex++;
        applyHistory();
    }

    private void applyHistory() {
        config.restore(history.get(historyIndex));
        pendingSnapshot = null;
        if (selected >= config.boxes.size()) selected = config.boxes.size() - 1;
        config.save();
        clearAndInit();
    }

    private void updateUndoButtons() {
        if (undoButton != null) undoButton.active = historyIndex > 0 || pendingSnapshot != null;
        if (redoButton != null) redoButton.active = historyIndex < history.size() - 1;
    }

    /** Called by sub-screens so widgets get rebuilt when we come back. */
    public void markDirty() {
        dirty = true;
    }

    /** Selects a box and switches the panel tab (0=Box, 1=Text, 2=Color, 3=Image). */
    public void select(int index, int tabIndex) {
        selected = index;
        tab = Tab.values()[Math.floorMod(tabIndex, Tab.values().length)];
        scroll = 0;
        clearAndInit();
    }

    private TextBox sel() {
        return selected >= 0 && selected < config.boxes.size() ? config.boxes.get(selected) : null;
    }

    private boolean panelVisible() {
        return panelMode != PanelMode.HIDDEN;
    }

    // ------------------------------------------------------------------ init

    @Override
    protected void init() {
        labels.clear();
        rSlider = gSlider = bSlider = aSlider = null;
        hexField = null;
        if (selected >= config.boxes.size()) selected = -1;

        buildToolbar();

        panelY = toolbarBottom + 4;
        panelBottom = height - 16;
        panelX = panelMode == PanelMode.LEFT ? 4 : width - PANEL_W - 4;
        px = panelX + 4;
        iw = PANEL_W - 8;
        contentTop = panelY + 4 + WH + 4;
        scroll = Math.max(0, Math.min(scroll, maxScroll()));

        TextBox b = sel();
        if (panelVisible() && b != null) buildPanel(b);
    }

    private int maxScroll() {
        return Math.max(0, tab.rows * ROW - (panelBottom - contentTop));
    }

    private void buildToolbar() {
        tx = 4;
        ty = 4;
        TextBox b = sel();
        int n = config.boxes.size();
        tool("+ Add", 38, true, this::addBox);
        tool("Duplicate", 54, b != null, this::duplicate);
        tool("Delete", 40, b != null, this::deleteSelected);
        tool("Forward", 46, b != null && selected < n - 1, () -> moveLayer(1));
        tool("Back", 34, b != null && selected > 0, () -> moveLayer(-1));
        undoButton = tool("Undo", 32, true, this::undo);
        redoButton = tool("Redo", 32, true, this::redo);
        updateUndoButtons();
        tool("Snap: " + (snap ? "On" : "Off"), 50, true, () -> {
            snap = !snap;
            clearAndInit();
        });
        String pm = switch (panelMode) { case RIGHT -> "Right"; case LEFT -> "Left"; case HIDDEN -> "Off"; };
        tool("Panel: " + pm, 62, true, () -> {
            panelMode = PanelMode.values()[(panelMode.ordinal() + 1) % PanelMode.values().length];
            clearAndInit();
        });
        tool("HUD: " + (config.hudVisible ? "On" : "Off"), 50, true, () -> {
            config.hudVisible = !config.hudVisible;
            clearAndInit();
        });
        tool("Done", 36, true, this::close);
        toolbarBottom = ty + WH;
    }

    private ButtonWidget tool(String label, int w, boolean active, Runnable action) {
        if (tx + w > width - 4 && tx > 4) {
            tx = 4;
            ty += ROW;
        }
        ButtonWidget btn = ButtonWidget.builder(Text.literal(label), x -> action.run())
                .dimensions(tx, ty, w, WH).build();
        btn.active = active;
        addDrawableChild(btn);
        tx += w + 2;
        return btn;
    }

    // ------------------------------------------------------------------ panel helpers

    private int rowY(int row) {
        return contentTop + row * ROW - scroll;
    }

    private boolean rowVisible(int row) {
        int y = rowY(row);
        return y >= contentTop && y + WH <= panelBottom;
    }

    private <T extends ClickableWidget> T panel(T widget, int row) {
        widget.setY(rowY(row));
        widget.visible = rowVisible(row);
        return addDrawableChild(widget);
    }

    private void label(int row, int x, String text, int color) {
        labels.add(new Label(row, x, text, color));
    }

    private ButtonWidget button(int x, int w, String text, Runnable action) {
        return ButtonWidget.builder(Text.literal(text), btn -> action.run()).dimensions(x, 0, w, WH).build();
    }

    private static Text onOff(String name, boolean on) {
        return Text.literal(name + ": ").append(Text.literal(on ? "On" : "Off")
                .formatted(on ? Formatting.GREEN : Formatting.RED));
    }

    private ButtonWidget toggle(int x, int w, String name, BooleanSupplier get, Consumer<Boolean> set) {
        return ButtonWidget.builder(onOff(name, get.getAsBoolean()), btn -> {
            boolean v = !get.getAsBoolean();
            set.accept(v);
            btn.setMessage(onOff(name, v));
        }).dimensions(x, 0, w, WH).build();
    }

    private ButtonWidget styleToggle(int x, int w, String letter, Formatting fmt, BooleanSupplier get, Consumer<Boolean> set) {
        return ButtonWidget.builder(styleText(letter, fmt, get.getAsBoolean()), btn -> {
            boolean v = !get.getAsBoolean();
            set.accept(v);
            btn.setMessage(styleText(letter, fmt, v));
        }).dimensions(x, 0, w, WH).build();
    }

    private static Text styleText(String letter, Formatting fmt, boolean on) {
        return Text.literal(letter).formatted(fmt, on ? Formatting.GREEN : Formatting.DARK_GRAY);
    }

    private TextFieldWidget numField(int x, int w, int value, IntConsumer setter) {
        TextFieldWidget f = new TextFieldWidget(textRenderer, x, 0, w, WH, Text.empty());
        f.setMaxLength(6);
        f.setText(Integer.toString(value));
        f.setTextPredicate(s -> s.matches("-?\\d{0,5}"));
        f.setChangedListener(s -> {
            try {
                setter.accept(Integer.parseInt(s));
            } catch (NumberFormatException ignored) {
            }
        });
        return f;
    }

    private static String pretty(Enum<?> e) {
        String n = e.name().toLowerCase();
        return Character.toUpperCase(n.charAt(0)) + n.substring(1);
    }

    // ------------------------------------------------------------------ panel

    private void buildPanel(TextBox b) {
        int tw = (iw - 6) / 4;
        Tab[] tabs = Tab.values();
        for (int i = 0; i < tabs.length; i++) {
            Tab t = tabs[i];
            ButtonWidget btn = ButtonWidget.builder(Text.literal(t.title), x -> {
                tab = t;
                scroll = 0;
                clearAndInit();
            }).dimensions(px + i * (tw + 2), panelY + 4, tw, WH).build();
            btn.active = t != tab;
            addDrawableChild(btn);
        }
        switch (tab) {
            case BOX -> buildBoxTab(b);
            case TEXT -> buildTextTab(b);
            case COLOR -> buildColorTab(b);
            case IMAGE -> buildImageTab(b);
        }
    }

    private void buildBoxTab(TextBox b) {
        int half = (iw - 4) / 2;
        label(0, px, "X", 0xFFCCCCCC);
        panel(numField(px + 10, half - 10, b.x, v -> b.x = v), 0);
        label(0, px + half + 4, "Y", 0xFFCCCCCC);
        panel(numField(px + half + 14, half - 10, b.y, v -> b.y = v), 0);
        label(1, px, "W", 0xFFCCCCCC);
        panel(numField(px + 10, half - 10, b.width, v -> b.width = Math.max(4, v)), 1);
        label(1, px + half + 4, "H", 0xFFCCCCCC);
        panel(numField(px + half + 14, half - 10, b.height, v -> b.height = Math.max(4, v)), 1);

        panel(new ValueSlider(px, 0, iw, WH, "Border width", 0, 16, 1, b.borderWidth,
                ValueSlider::fmtInt, v -> b.borderWidth = (int) Math.round(v)), 2);
        panel(toggle(px, iw, "Visible in HUD", () -> b.visible, v -> b.visible = v), 3);
        panel(button(px, half, "Center H", () -> {
            b.x = (width - b.width) / 2;
            clearAndInit();
        }), 4);
        panel(button(px + half + 4, half, "Center V", () -> {
            b.y = (height - b.height) / 2;
            clearAndInit();
        }), 4);
        ButtonWidget fit = panel(button(px, iw, "Fit to image aspect", () -> fitToAspect(b)), 5);
        fit.active = !b.image.isEmpty();

        panel(button(px, iw, "Shape: " + b.shape.title, () -> {
            TextBox.Shape[] all = TextBox.Shape.values();
            b.shape = all[(b.shape.ordinal() + 1) % all.length];
            clearAndInit();
        }), 6);
        String sizeLabel = b.shape.sizeLabel != null ? b.shape.sizeLabel : "Shape size";
        ValueSlider size = panel(new ValueSlider(px, 0, iw, WH, sizeLabel, 0, 32, 1, b.shapeSize,
                ValueSlider::fmtInt, v -> b.shapeSize = (int) Math.round(v)), 7);
        size.active = b.shape.sizeLabel != null;
        ValueSlider tail = panel(new ValueSlider(px, 0, iw, WH, "Tail position", 0, 100, 1, b.tailPos * 100.0,
                ValueSlider::fmtPercent, v -> b.tailPos = (float) (v / 100.0)), 8);
        tail.active = b.shape == TextBox.Shape.SPEECH;
        panel(toggle(px, half, "Shadow", () -> b.boxShadow, v -> b.boxShadow = v), 9);
        panel(new ValueSlider(px + half + 4, 0, half, WH, "Offset", 1, 8, 1, Math.max(1, b.shadowOffset),
                ValueSlider::fmtInt, v -> b.shadowOffset = (int) Math.round(v)), 9);
    }

    private void buildTextTab(TextBox b) {
        int half = (iw - 4) / 2;
        panel(button(px, iw, "Edit text...", () -> openTextEditor(b)), 0);

        panel(button(px, iw, "Font: " + TextBox.FONT_NAMES[b.fontIndex()], () -> {
            int next = (b.fontIndex() + 1) % TextBox.FONT_IDS.length;
            b.font = TextBox.FONT_IDS[next];
            clearAndInit();
        }), 1);

        boolean custom = "custom".equals(b.font);
        TextFieldWidget fontField = new TextFieldWidget(textRenderer, px, 0, iw, WH, Text.literal("Custom font"));
        fontField.setMaxLength(128);
        fontField.setText(b.customFont);
        fontField.setPlaceholder(Text.literal("namespace:font_id").formatted(Formatting.DARK_GRAY));
        fontField.setTextPredicate(s -> s.matches("[a-z0-9_.:/-]*"));
        fontField.setChangedListener(s -> b.customFont = s.isBlank() ? "minecraft:default" : s.trim());
        fontField.setEditable(custom);
        fontField.active = custom;
        panel(fontField, 2);

        panel(new ValueSlider(px, 0, iw, WH, "Scale", 0.25, 4.0, 0.05, b.scale,
                v -> String.format("%.2fx", v), v -> b.scale = (float) v), 3);

        int q = (iw - 6) / 4;
        panel(styleToggle(px, q, "B", Formatting.BOLD, () -> b.bold, v -> b.bold = v), 4);
        panel(styleToggle(px + (q + 2), q, "I", Formatting.ITALIC, () -> b.italic, v -> b.italic = v), 4);
        panel(styleToggle(px + 2 * (q + 2), q, "U", Formatting.UNDERLINE, () -> b.underline, v -> b.underline = v), 4);
        panel(styleToggle(px + 3 * (q + 2), q, "S", Formatting.STRIKETHROUGH, () -> b.strikethrough, v -> b.strikethrough = v), 4);

        panel(toggle(px, half, "Shadow", () -> b.shadow, v -> b.shadow = v), 5);
        panel(button(px + half + 4, half, "Align: " + pretty(b.align), () -> {
            b.align = TextBox.Align.values()[(b.align.ordinal() + 1) % TextBox.Align.values().length];
            clearAndInit();
        }), 5);
        panel(button(px, iw, "Vertical: " + pretty(b.valign), () -> {
            b.valign = TextBox.VAlign.values()[(b.valign.ordinal() + 1) % TextBox.VAlign.values().length];
            clearAndInit();
        }), 6);
        panel(new ValueSlider(px, 0, iw, WH, "Padding", 0, 32, 1, b.padding,
                ValueSlider::fmtInt, v -> b.padding = (int) Math.round(v)), 7);
        panel(new ValueSlider(px, 0, iw, WH, "Line spacing", 0, 20, 1, b.lineSpacing,
                ValueSlider::fmtInt, v -> b.lineSpacing = (int) Math.round(v)), 8);
        panel(new ValueSlider(px, 0, iw, WH, "Item/icon size", 4, 64, 1, b.iconSize,
                ValueSlider::fmtInt, v -> b.iconSize = (int) Math.round(v)), 9);
    }

    private void buildColorTab(TextBox b) {
        panel(button(px, iw, "Editing: " + colorTarget.title, () -> {
            colorTarget = ColorTarget.values()[(colorTarget.ordinal() + 1) % ColorTarget.values().length];
            clearAndInit();
        }), 0);

        int rgb = getRgb(b);
        rSlider = panel(new ValueSlider(px, 0, iw, WH, "Red", 0, 255, 1, (rgb >> 16) & 0xFF,
                ValueSlider::fmtInt, v -> setChannel(b, 16, (int) Math.round(v))), 1);
        gSlider = panel(new ValueSlider(px, 0, iw, WH, "Green", 0, 255, 1, (rgb >> 8) & 0xFF,
                ValueSlider::fmtInt, v -> setChannel(b, 8, (int) Math.round(v))), 2);
        bSlider = panel(new ValueSlider(px, 0, iw, WH, "Blue", 0, 255, 1, rgb & 0xFF,
                ValueSlider::fmtInt, v -> setChannel(b, 0, (int) Math.round(v))), 3);
        aSlider = panel(new ValueSlider(px, 0, iw, WH, "Opacity", 0, 100, 1, getOpacity(b) * 100.0,
                ValueSlider::fmtPercent, v -> setOpacity(b, (float) (v / 100.0))), 4);

        label(5, px, "Hex #", 0xFFCCCCCC);
        hexField = new TextFieldWidget(textRenderer, px + 30, 0, 52, WH, Text.literal("Hex"));
        hexField.setMaxLength(6);
        hexField.setText(String.format("%06X", rgb));
        hexField.setTextPredicate(s -> s.matches("[0-9a-fA-F]{0,6}"));
        hexField.setChangedListener(s -> {
            if (syncing || s.length() != 6) return;
            int c = Integer.parseInt(s, 16);
            setRgb(b, c);
            syncSliders(c);
        });
        panel(hexField, 5);

        panel(toggle(px, iw, "Gradient background", () -> b.gradient, v -> {
            b.gradient = v;
            if (v && colorTarget == ColorTarget.BACKGROUND) colorTarget = ColorTarget.GRADIENT;
            clearAndInit();
        }), 8);
    }

    private void buildImageTab(TextBox b) {
        int half = (iw - 4) / 2;
        panel(button(px, iw, "Choose image...", () -> {
            if (client != null) client.setScreen(new ImagePickerScreen(this, b.image, true, name -> b.image = name));
        }), 0);

        String name;
        int color;
        if (b.image.isEmpty()) {
            name = "Image: (none)";
            color = 0xFFAAAAAA;
        } else if (ImageManager.isMissing(b.image)) {
            name = "Missing: " + b.image;
            color = 0xFFFF5555;
        } else {
            name = "Image: " + b.image;
            color = 0xFFFFFFFF;
        }
        label(1, px, textRenderer.trimToWidth(name, iw), color);

        panel(button(px, half, "Fit: " + pretty(b.imageFit), () -> {
            b.imageFit = TextBox.ImageFit.values()[(b.imageFit.ordinal() + 1) % TextBox.ImageFit.values().length];
            clearAndInit();
        }), 2);
        ButtonWidget clear = panel(button(px + half + 4, half, "Clear", () -> {
            b.image = "";
            clearAndInit();
        }), 2);
        clear.active = !b.image.isEmpty();

        panel(new ValueSlider(px, 0, iw, WH, "Image opacity", 0, 100, 1, b.imageOpacity * 100.0,
                ValueSlider::fmtPercent, v -> b.imageOpacity = (float) (v / 100.0)), 3);
        ButtonWidget fit = panel(button(px, iw, "Fit box to image aspect", () -> fitToAspect(b)), 4);
        fit.active = !b.image.isEmpty();
        panel(button(px, half, "Open folder", ImageManager::openFolder), 5);
        panel(button(px + half + 4, half, "Reload", () -> {
            ImageManager.reload();
            clearAndInit();
        }), 5);
    }

    // ------------------------------------------------------------------ color plumbing

    private int getRgb(TextBox b) {
        return switch (colorTarget) {
            case BACKGROUND -> b.bgColor;
            case GRADIENT -> b.bgColor2;
            case TEXT -> b.textColor;
            case BORDER -> b.borderColor;
            case IMAGE -> b.imageTint;
            case SHADOW -> b.shadowColor;
        } & 0xFFFFFF;
    }

    private void setRgb(TextBox b, int rgb) {
        rgb &= 0xFFFFFF;
        switch (colorTarget) {
            case BACKGROUND -> b.bgColor = rgb;
            case GRADIENT -> b.bgColor2 = rgb;
            case TEXT -> b.textColor = rgb;
            case BORDER -> b.borderColor = rgb;
            case IMAGE -> b.imageTint = rgb;
            case SHADOW -> b.shadowColor = rgb;
        }
    }

    private float getOpacity(TextBox b) {
        return switch (colorTarget) {
            case BACKGROUND -> b.bgOpacity;
            case GRADIENT -> b.bgOpacity2;
            case TEXT -> b.textOpacity;
            case BORDER -> b.borderOpacity;
            case IMAGE -> b.imageOpacity;
            case SHADOW -> b.shadowOpacity;
        };
    }

    private void setOpacity(TextBox b, float v) {
        v = Math.max(0f, Math.min(1f, v));
        switch (colorTarget) {
            case BACKGROUND -> b.bgOpacity = v;
            case GRADIENT -> b.bgOpacity2 = v;
            case TEXT -> b.textOpacity = v;
            case BORDER -> b.borderOpacity = v;
            case IMAGE -> b.imageOpacity = v;
            case SHADOW -> b.shadowOpacity = v;
        }
    }

    private void setChannel(TextBox b, int shift, int value) {
        int rgb = getRgb(b);
        rgb = (rgb & ~(0xFF << shift)) | ((value & 0xFF) << shift);
        setRgb(b, rgb);
        syncHex(rgb);
    }

    private void syncHex(int rgb) {
        if (hexField == null) return;
        syncing = true;
        hexField.setText(String.format("%06X", rgb & 0xFFFFFF));
        syncing = false;
    }

    private void syncSliders(int rgb) {
        if (rSlider != null) rSlider.setReal((rgb >> 16) & 0xFF);
        if (gSlider != null) gSlider.setReal((rgb >> 8) & 0xFF);
        if (bSlider != null) bSlider.setReal(rgb & 0xFF);
    }

    private int swatchCell() {
        return iw / 8;
    }

    /** Swatches occupy rows 6 and 7 of the color tab. */
    private int swatchAt(double mx, double my) {
        int cw = swatchCell();
        for (int i = 0; i < SWATCHES.length; i++) {
            int row = 6 + i / 8;
            if (!rowVisible(row)) continue;
            int x = px + (i % 8) * cw;
            int y = rowY(row) + 2;
            if (mx >= x && mx < x + cw && my >= y && my < y + 12) return i;
        }
        return -1;
    }

    // ------------------------------------------------------------------ actions

    private void addBox() {
        TextBox b = new TextBox();
        b.x = Math.max(0, (width - b.width) / 2);
        b.y = Math.max(0, (height - b.height) / 2);
        config.boxes.add(b);
        selected = config.boxes.size() - 1;
        config.save();
        clearAndInit();
    }

    private void duplicate() {
        TextBox b = sel();
        if (b == null) return;
        TextBox c = TextboxConfig.copy(b);
        c.x += 10;
        c.y += 10;
        config.boxes.add(selected + 1, c);
        selected++;
        config.save();
        clearAndInit();
    }

    private void deleteSelected() {
        if (sel() == null) return;
        config.boxes.remove(selected);
        selected = Math.min(selected, config.boxes.size() - 1);
        config.save();
        clearAndInit();
    }

    private void moveLayer(int dir) {
        int to = selected + dir;
        if (sel() == null || to < 0 || to >= config.boxes.size()) return;
        TextBox b = config.boxes.remove(selected);
        config.boxes.add(to, b);
        selected = to;
        clearAndInit();
    }

    private void fitToAspect(TextBox b) {
        ImageManager.LoadedImage img = ImageManager.get(b.image);
        if (img == null || img.width() <= 0) return;
        int bw = b.borderWidth;
        int innerW = Math.max(1, b.width - 2 * bw);
        b.height = Math.max(4, Math.round(innerW * (float) img.height() / img.width()) + 2 * bw);
        clearAndInit();
    }

    private void openTextEditor(TextBox b) {
        if (client != null) client.setScreen(new TextEditScreen(this, b));
    }

    // ------------------------------------------------------------------ input

    private boolean inPanel(double mx, double my) {
        return panelVisible() && mx >= panelX && mx < panelX + PANEL_W && my >= panelY && my < panelBottom;
    }

    private int boxAt(double mx, double my) {
        for (int i = config.boxes.size() - 1; i >= 0; i--) {
            TextBox b = config.boxes.get(i);
            if (mx >= b.x && mx < b.x + b.width && my >= b.y && my < b.y + b.height) return i;
        }
        return -1;
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (super.mouseClicked(click, doubled)) return true;
        double mx = click.x(), my = click.y();

        if (inPanel(mx, my)) {
            TextBox b = sel();
            if (b != null && tab == Tab.COLOR) {
                int idx = swatchAt(mx, my);
                if (idx >= 0) {
                    setRgb(b, SWATCHES[idx]);
                    syncSliders(SWATCHES[idx]);
                    syncHex(SWATCHES[idx]);
                }
            }
            return true;
        }

        setFocused(null);
        int hit = boxAt(mx, my);
        if (hit >= 0) {
            boolean changed = hit != selected;
            selected = hit;
            TextBox b = config.boxes.get(hit);
            if (doubled && click.button() == 0) {
                openTextEditor(b);
                return true;
            }
            if (click.button() == 0) {
                boolean onHandle = mx >= b.x + b.width - HANDLE - 1 && my >= b.y + b.height - HANDLE - 1;
                drag = onHandle ? Drag.RESIZE : Drag.MOVE;
                dragOffX = (int) mx - b.x;
                dragOffY = (int) my - b.y;
            }
            if (changed) clearAndInit();
            return true;
        }

        if (selected != -1) {
            selected = -1;
            clearAndInit();
        }
        return true;
    }

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        TextBox b = sel();
        if (drag != Drag.NONE && b != null) {
            int mx = (int) click.x(), my = (int) click.y();
            guideX = guideY = -1;
            if (drag == Drag.MOVE) {
                int nx = mx - dragOffX, ny = my - dragOffY;
                if (snap) {
                    nx = snapMove(nx, b.width, true, b);
                    ny = snapMove(ny, b.height, false, b);
                }
                b.x = Math.max(0, Math.min(width - b.width, nx));
                b.y = Math.max(0, Math.min(height - b.height, ny));
            } else {
                int right = mx, bottom = my;
                if (snap) {
                    right = snapEdge(right, true, b);
                    bottom = snapEdge(bottom, false, b);
                }
                b.width = Math.max(8, right - b.x);
                b.height = Math.max(8, bottom - b.y);
            }
            return true;
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    /** Lines a box edge can snap to: screen edges and center, and the edges and centers of other boxes. */
    private List<Integer> snapLines(boolean xAxis, TextBox self) {
        List<Integer> lines = new ArrayList<>();
        int size = xAxis ? width : height;
        lines.add(0);
        lines.add(size / 2);
        lines.add(size);
        for (TextBox o : config.boxes) {
            if (o == self) continue;
            int p = xAxis ? o.x : o.y, s = xAxis ? o.width : o.height;
            lines.add(p);
            lines.add(p + s / 2);
            lines.add(p + s);
        }
        return lines;
    }

    private void setGuide(boolean xAxis, int line) {
        if (xAxis) guideX = line; else guideY = line;
    }

    /** Snaps a moving box's start, center or end to the nearest line. */
    private int snapMove(int pos, int size, boolean xAxis, TextBox self) {
        int best = SNAP_DIST + 1, result = pos, line = -1;
        for (int l : snapLines(xAxis, self)) {
            int[] edges = {pos, pos + size / 2, pos + size};
            for (int e : edges) {
                int d = Math.abs(l - e);
                if (d < best) {
                    best = d;
                    result = pos + (l - e);
                    line = l;
                }
            }
        }
        if (line >= 0) setGuide(xAxis, line);
        return result;
    }

    /** Snaps the right or bottom edge while resizing. */
    private int snapEdge(int edge, boolean xAxis, TextBox self) {
        int best = SNAP_DIST + 1, result = edge;
        for (int l : snapLines(xAxis, self)) {
            int d = Math.abs(l - edge);
            if (d < best) {
                best = d;
                result = l;
            }
        }
        if (result != edge || best == 0) setGuide(xAxis, result);
        return result;
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (drag != Drag.NONE) {
            drag = Drag.NONE;
            guideX = guideY = -1;
            recordNow();
            config.save();
            clearAndInit();
            return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (inPanel(mouseX, mouseY) && maxScroll() > 0) {
            int ns = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.signum(verticalAmount) * ROW));
            if (ns != scroll) {
                scroll = ns;
                clearAndInit();
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        boolean typing = getFocused() instanceof TextFieldWidget tf && tf.isFocused();
        boolean ctrlDown = (input.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0;
        if (!typing && ctrlDown) {
            boolean shiftDown = (input.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0;
            if (input.key() == GLFW.GLFW_KEY_Z) {
                if (shiftDown) redo(); else undo();
                return true;
            }
            if (input.key() == GLFW.GLFW_KEY_Y) {
                redo();
                return true;
            }
        }
        TextBox b = sel();
        if (!typing && b != null) {
            int key = input.key();
            boolean shift = (input.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0;
            boolean ctrl = (input.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0;
            int step = shift ? 10 : 1;
            switch (key) {
                case GLFW.GLFW_KEY_LEFT -> { b.x -= step; clearAndInit(); return true; }
                case GLFW.GLFW_KEY_RIGHT -> { b.x += step; clearAndInit(); return true; }
                case GLFW.GLFW_KEY_UP -> { b.y -= step; clearAndInit(); return true; }
                case GLFW.GLFW_KEY_DOWN -> { b.y += step; clearAndInit(); return true; }
                case GLFW.GLFW_KEY_DELETE -> { deleteSelected(); return true; }
                case GLFW.GLFW_KEY_D -> {
                    if (ctrl) { duplicate(); return true; }
                }
                case GLFW.GLFW_KEY_ENTER -> { openTextEditor(b); return true; }
                default -> { }
            }
        }
        return super.keyPressed(input);
    }

    // ------------------------------------------------------------------ render

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        // Keep the world visible behind the editor (no blur / dim).
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        if (dirty) {
            dirty = false;
            clearAndInit();
        }

        List<TextBox> boxes = config.boxes;
        for (int i = 0; i < boxes.size(); i++) {
            if (i > 0) ctx.createNewRootLayer();
            TextBoxRenderer.render(ctx, textRenderer, boxes.get(i));
        }
        ctx.createNewRootLayer();

        // Overlays: hidden markers, hover, selection, resize handle
        int hover = drag == Drag.NONE && !inPanel(mouseX, mouseY) ? boxAt(mouseX, mouseY) : -1;
        for (int i = 0; i < boxes.size(); i++) {
            TextBox b = boxes.get(i);
            if (!b.visible) {
                ctx.drawStrokedRectangle(b.x, b.y, b.width, b.height, 0xC0FF5555);
                int ly = b.y >= 11 ? b.y - 10 : b.y + b.height + 2;
                ctx.drawText(textRenderer, Text.literal("hidden"), b.x, ly, 0xFFFF5555, true);
            }
            if (i == hover && i != selected) {
                ctx.drawStrokedRectangle(b.x - 1, b.y - 1, b.width + 2, b.height + 2, 0x90FFFFFF);
            }
        }
        if (drag != Drag.NONE) {
            if (guideX >= 0) {
                int gx = Math.min(guideX, width - 1);
                ctx.fill(gx, 0, gx + 1, height, 0xC055FFFF);
            }
            if (guideY >= 0) {
                int gy = Math.min(guideY, height - 1);
                ctx.fill(0, gy, width, gy + 1, 0xC055FFFF);
            }
        }
        TextBox s = sel();
        if (s != null) {
            ctx.drawStrokedRectangle(s.x - 1, s.y - 1, s.width + 2, s.height + 2, 0xFFFFD700);
            int hx = s.x + s.width, hy = s.y + s.height;
            ctx.fill(hx - HANDLE, hy - HANDLE, hx + 1, hy + 1, 0xFFFFD700);
            String dims = s.width + "x" + s.height;
            ctx.drawText(textRenderer, Text.literal(dims), s.x, s.y + s.height + 3, 0xFFFFD700, true);
        }

        if (panelVisible()) {
            ctx.fill(panelX, panelY, panelX + PANEL_W, panelBottom, 0xD0101018);
            ctx.drawStrokedRectangle(panelX, panelY, PANEL_W, panelBottom - panelY, 0xFF505060);
            if (s == null) {
                String msg = boxes.isEmpty()
                        ? "No textboxes yet. Press \"+ Add\" to create one."
                        : "Click a textbox to select it. Double-click to edit its text.";
                int y = panelY + 8;
                for (OrderedText line : textRenderer.wrapLines(Text.literal(msg), iw)) {
                    ctx.drawText(textRenderer, line, px, y, 0xFFCCCCCC, false);
                    y += 10;
                }
            }
        }

        super.render(ctx, mouseX, mouseY, delta);

        if (panelVisible() && s != null) {
            for (Label l : labels) {
                if (rowVisible(l.row())) {
                    ctx.drawText(textRenderer, Text.literal(l.text()), l.x(), rowY(l.row()) + 4, l.color(), false);
                }
            }
            if (tab == Tab.COLOR) renderColorExtras(ctx, s);
            if (maxScroll() > 0) {
                int trackTop = contentTop, trackH = panelBottom - contentTop - 2;
                int thumbH = Math.max(10, trackH * trackH / (tab.rows * ROW));
                int thumbY = trackTop + (trackH - thumbH) * scroll / maxScroll();
                ctx.fill(panelX + PANEL_W - 3, trackTop, panelX + PANEL_W - 1, trackTop + trackH, 0x40FFFFFF);
                ctx.fill(panelX + PANEL_W - 3, thumbY, panelX + PANEL_W - 1, thumbY + thumbH, 0xC0FFFFFF);
            }
        }

        String hint = "Drag: move | Corner: resize | Double-click: edit text | Arrows: nudge | Ctrl+Z: undo | Del: delete";
        String shown = textRenderer.trimToWidth(hint, width - 8);
        // backing strip so the hint stays readable over the hotbar
        ctx.fill(0, height - 14, textRenderer.getWidth(shown) + 8, height, 0xB0000000);
        ctx.drawText(textRenderer, Text.literal(shown), 4, height - 11, 0xFFAAAAAA, true);
    }

    private void renderColorExtras(DrawContext ctx, TextBox b) {
        int rgb = getRgb(b);
        if (rowVisible(5)) {
            int y = rowY(5);
            int x1 = px + 86, x2 = px + iw;
            ctx.fill(x1, y, x2, y + WH, 0xFF000000 | rgb);
            ctx.drawStrokedRectangle(x1, y, x2 - x1, WH, 0xFFFFFFFF);
        }
        int cw = swatchCell();
        for (int i = 0; i < SWATCHES.length; i++) {
            int row = 6 + i / 8;
            if (!rowVisible(row)) continue;
            int x = px + (i % 8) * cw;
            int y = rowY(row) + 2;
            ctx.fill(x + 1, y, x + cw - 1, y + 12, 0xFF000000 | SWATCHES[i]);
            int outline = SWATCHES[i] == rgb ? 0xFFFFD700 : 0xFF303030;
            ctx.drawStrokedRectangle(x + 1, y, cw - 2, 12, outline);
        }
    }

    // ------------------------------------------------------------------ lifecycle

    @Override
    public void removed() {
        config.save();
        super.removed();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}

package com.textboxes.gui;

import com.textboxes.InlineText;
import com.textboxes.Placeholders;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

/** A grid of buttons that each insert a text token, plus an optional field for a custom value. */
public class TokenPickerScreen extends Screen {
    public record Entry(Component label, String token) {}

    private final Screen parent;
    private final String hint;
    private final List<Entry> entries;
    private final Consumer<String> onPick;
    private final String customPlaceholder;
    private final Function<String, String> customToToken;
    private EditBox custom;
    private int top, left, gridW;

    public TokenPickerScreen(Screen parent, String title, String hint, List<Entry> entries, Consumer<String> onPick,
                             String customPlaceholder, Function<String, String> customToToken) {
        super(Component.literal(title));
        this.parent = parent;
        this.hint = hint;
        this.entries = entries;
        this.onPick = onPick;
        this.customPlaceholder = customPlaceholder;
        this.customToToken = customToToken;
    }

    // ------------------------------------------------------------------ presets

    public static TokenPickerScreen fonts(Screen parent, Consumer<String> onPick) {
        List<Entry> e = new ArrayList<>();
        e.add(font("Default", "default"));
        e.add(font("Uniform", "uniform"));
        e.add(new Entry(Component.literal("Galactic ").append(Component.literal("abc")
                .setStyle(Style.EMPTY.withFont(new FontDescription.Resource(InlineText.fontId("galactic"))))), "{font:galactic}"));
        e.add(new Entry(Component.literal("Illageralt ").append(Component.literal("abc")
                .setStyle(Style.EMPTY.withFont(new FontDescription.Resource(InlineText.fontId("illageralt"))))), "{font:illageralt}"));
        e.add(new Entry(Component.literal("Back to box font"), "{font}"));
        return new TokenPickerScreen(parent, "Insert font", "Text after the token uses that font, until {font}.",
                e, onPick, "namespace:font_id", s -> {
                    String v = s.trim();
                    return v.isEmpty() || InlineText.fontId(v) == null ? null : "{font:" + v + "}";
                });
    }

    private static Entry font(String name, String alias) {
        return new Entry(Component.literal(name).setStyle(Style.EMPTY.withFont(new FontDescription.Resource(InlineText.fontId(alias)))),
                "{font:" + alias + "}");
    }

    private static final int[] COLORS = {
            0xFFFFFF, 0xAAAAAA, 0x555555, 0x000000, 0xFF5555, 0xFF8800, 0xFFDD33, 0x55FF55,
            0x22AA55, 0x55FFFF, 0x3399FF, 0x5555FF, 0xAA55FF, 0xFF55FF, 0xFF88BB, 0x8B5A2B
    };

    public static TokenPickerScreen colors(Screen parent, Consumer<String> onPick) {
        List<Entry> e = new ArrayList<>();
        for (int c : COLORS) {
            String hex = String.format("%06X", c);
            e.add(new Entry(Component.literal("#" + hex).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(c == 0 ? 0x333333 : c))),
                    "{#" + hex + "}"));
        }
        e.add(new Entry(Component.literal("Back to box color"), "{color}"));
        return new TokenPickerScreen(parent, "Insert color", "Text after the token uses that color, until {color}.",
                e, onPick, "RRGGBB hex", s -> {
                    String v = s.trim().replace("#", "");
                    return v.matches("[0-9a-fA-F]{6}") ? "{#" + v.toUpperCase() + "}" : null;
                });
    }

    public static TokenPickerScreen info(Screen parent, Consumer<String> onPick) {
        List<Entry> e = new ArrayList<>();
        for (String[] p : Placeholders.LIST) {
            e.add(new Entry(Component.literal(p[1]), "{" + p[0] + "}"));
        }
        return new TokenPickerScreen(parent, "Insert live info", "These update by themselves while you play.",
                e, onPick, null, null);
    }

    // ------------------------------------------------------------------ screen

    @Override
    protected void init() {
        int cols = Math.max(1, Math.min(4, (width - 20) / 92));
        int bw = 88;
        gridW = cols * (bw + 4) - 4;
        int rows = (entries.size() + cols - 1) / cols;
        int contentH = 30 + rows * 22 + (customToToken != null ? 26 : 0) + 24;
        top = Math.max(4, (height - contentH) / 2);
        left = (width - gridW) / 2;

        int y = top + 30;
        for (int i = 0; i < entries.size(); i++) {
            Entry en = entries.get(i);
            int x = left + (i % cols) * (bw + 4);
            int ry = y + (i / cols) * 22;
            Button btn = Button.builder(en.label(), b -> pick(en.token()))
                    .bounds(x, ry, bw, 20).build();
            btn.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(en.token())));
            addRenderableWidget(btn);
        }
        y += rows * 22;

        if (customToToken != null) {
            String keep = custom != null ? custom.getValue() : "";
            custom = new EditBox(font, left, y + 2, gridW - 64, 18, Component.literal("Custom"));
            custom.setMaxLength(128);
            custom.setValue(keep);
            custom.setHint(Component.literal(customPlaceholder).withStyle(ChatFormatting.DARK_GRAY));
            addRenderableWidget(custom);
            addRenderableWidget(Button.builder(Component.literal("Insert"), b -> {
                String token = customToToken.apply(custom.getValue());
                if (token != null) pick(token);
            }).bounds(left + gridW - 60, y + 1, 60, 20).build());
            y += 26;
        }

        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose())
                .bounds(width / 2 - 40, y + 2, 80, 20).build());
    }

    private void pick(String token) {
        if (minecraft != null) minecraft.gui.setScreen(parent);
        onPick.accept(token);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        super.extractRenderState(ctx, mouseX, mouseY, delta);
        ctx.centeredText(font, title, width / 2, top + 4, 0xFFFFFFFF);
        ctx.centeredText(font, Component.literal(hint), width / 2, top + 16, 0xFFAAAAAA);
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.gui.setScreen(parent);
    }
}

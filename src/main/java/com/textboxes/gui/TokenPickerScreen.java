package com.textboxes.gui;

import com.textboxes.InlineText;
import com.textboxes.Placeholders;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Style;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/** A grid of buttons that each insert a text token, plus an optional field for a custom value. */
public class TokenPickerScreen extends Screen {
    public record Entry(Text label, String token) {}

    private final Screen parent;
    private final String hint;
    private final List<Entry> entries;
    private final Consumer<String> onPick;
    private final String customPlaceholder;
    private final Function<String, String> customToToken;
    private TextFieldWidget custom;
    private int top, left, gridW;

    public TokenPickerScreen(Screen parent, String title, String hint, List<Entry> entries, Consumer<String> onPick,
                             String customPlaceholder, Function<String, String> customToToken) {
        super(Text.literal(title));
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
        e.add(new Entry(Text.literal("Galactic ").append(Text.literal("abc")
                .setStyle(Style.EMPTY.withFont(new StyleSpriteSource.Font(InlineText.fontId("galactic"))))), "{font:galactic}"));
        e.add(new Entry(Text.literal("Illageralt ").append(Text.literal("abc")
                .setStyle(Style.EMPTY.withFont(new StyleSpriteSource.Font(InlineText.fontId("illageralt"))))), "{font:illageralt}"));
        e.add(new Entry(Text.literal("Back to box font"), "{font}"));
        return new TokenPickerScreen(parent, "Insert font", "Text after the token uses that font, until {font}.",
                e, onPick, "namespace:font_id", s -> {
                    String v = s.trim();
                    return v.isEmpty() || InlineText.fontId(v) == null ? null : "{font:" + v + "}";
                });
    }

    private static Entry font(String name, String alias) {
        return new Entry(Text.literal(name).setStyle(Style.EMPTY.withFont(new StyleSpriteSource.Font(InlineText.fontId(alias)))),
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
            e.add(new Entry(Text.literal("#" + hex).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(c == 0 ? 0x333333 : c))),
                    "{#" + hex + "}"));
        }
        e.add(new Entry(Text.literal("Back to box color"), "{color}"));
        return new TokenPickerScreen(parent, "Insert color", "Text after the token uses that color, until {color}.",
                e, onPick, "RRGGBB hex", s -> {
                    String v = s.trim().replace("#", "");
                    return v.matches("[0-9a-fA-F]{6}") ? "{#" + v.toUpperCase() + "}" : null;
                });
    }

    public static TokenPickerScreen info(Screen parent, Consumer<String> onPick) {
        List<Entry> e = new ArrayList<>();
        for (String[] p : Placeholders.LIST) {
            e.add(new Entry(Text.literal(p[1]), "{" + p[0] + "}"));
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
            ButtonWidget btn = ButtonWidget.builder(en.label(), b -> pick(en.token()))
                    .dimensions(x, ry, bw, 20).build();
            btn.setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal(en.token())));
            addDrawableChild(btn);
        }
        y += rows * 22;

        if (customToToken != null) {
            String keep = custom != null ? custom.getText() : "";
            custom = new TextFieldWidget(textRenderer, left, y + 2, gridW - 64, 18, Text.literal("Custom"));
            custom.setMaxLength(128);
            custom.setText(keep);
            custom.setPlaceholder(Text.literal(customPlaceholder).formatted(Formatting.DARK_GRAY));
            addDrawableChild(custom);
            addDrawableChild(ButtonWidget.builder(Text.literal("Insert"), b -> {
                String token = customToToken.apply(custom.getText());
                if (token != null) pick(token);
            }).dimensions(left + gridW - 60, y + 1, 60, 20).build());
            y += 26;
        }

        addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), b -> close())
                .dimensions(width / 2 - 40, y + 2, 80, 20).build());
    }

    private void pick(String token) {
        if (client != null) client.setScreen(parent);
        onPick.accept(token);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        ctx.drawCenteredTextWithShadow(textRenderer, title, width / 2, top + 4, 0xFFFFFFFF);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(hint), width / 2, top + 16, 0xFFAAAAAA);
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }
}

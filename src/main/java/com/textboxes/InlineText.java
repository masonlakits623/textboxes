package com.textboxes;

import org.joml.Matrix3x2fStack;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Text with inline icons. Tokens inside box text:
 * <ul>
 *   <li>{@code {item:diamond}} or {@code {item:mymod:thing}} - an item icon</li>
 *   <li>{@code {item:paper|5}} - the item renamed to "5" (resource packs that key models on the
 *       custom name, like anvil-renamed items, will show their texture)</li>
 *   <li>{@code {img:logo.png}} - an image from config/textboxes/images/</li>
 *   <li>{@code {font:galactic}} ... {@code {font}} - switch font for part of the text</li>
 *   <li>{@code {#FF8800}} ... {@code {color}} - any hex color for part of the text</li>
 *   <li>live values like {@code {fps}} or {@code {coords}}, see {@link Placeholders}</li>
 * </ul>
 * Legacy section-sign color codes keep working across tokens.
 */
public final class InlineText {
    /** Groups: 1 item/img, 2 its body, 3 "font", 4 font name, 5 hex color, 6 "color". */
    public static final Pattern TOKEN = Pattern.compile(
            "\\{(?:(item|img):([^{}\\n]+)|(font)(?::([^{}\\n]*))?|#([0-9a-fA-F]{6})|(color))}");
    private static final int PLACEHOLDER_BASE = 0xF0000; // supplementary private use area

    /** Short names usable in {font:...}; anything else is read as a font id. */
    public static final String[][] FONT_ALIASES = {
            {"default", "minecraft:default"},
            {"uniform", "minecraft:uniform"},
            {"galactic", "minecraft:alt"},
            {"alt", "minecraft:alt"},
            {"illageralt", "minecraft:illageralt"},
            {"illager", "minecraft:illageralt"},
    };

    /** Resolves a font alias or id, or returns null if it isn't a valid id. */
    public static Identifier fontId(String name) {
        String n = name.trim().toLowerCase(Locale.ROOT);
        for (String[] a : FONT_ALIASES) {
            if (a[0].equals(n)) return Identifier.tryParse(a[1]);
        }
        return n.isEmpty() ? null : Identifier.tryParse(n);
    }

    /** Changes the running style when a {font:..} / {#..} / {color} marker is reached. */
    private interface StyleOp {
        Style apply(Style current, Style base);
    }

    sealed interface Atom permits Glyph, Icon {
        int width();
    }

    record Glyph(String s, Style style, int width, boolean space) implements Atom {}

    record Icon(ItemStack stack, ImageManager.LoadedImage image, int width, int height) implements Atom {}

    record Placed(Atom atom, int x) {}

    static final class Line {
        final List<Placed> items = new ArrayList<>();
        int width;
        int height;
    }

    public record Layout(List<Line> lines, int totalHeight) {}

    private record Cached(String key, Layout layout) {}

    private static final Map<TextBox, Cached> CACHE = new WeakHashMap<>();

    private InlineText() {}

    /** Builds a token for an item, e.g. {item:paper|5}. */
    public static String itemToken(Identifier id, String name) {
        String idStr = "minecraft".equals(id.getNamespace()) ? id.getPath() : id.toString();
        return "{item:" + idStr + (name == null || name.isEmpty() ? "" : "|" + name) + "}";
    }

    /** Makes the stack an item token refers to, or null if the item id is unknown. */
    public static ItemStack makeStack(String idStr, String name) {
        Identifier id = Identifier.tryParse(idStr.trim().toLowerCase(Locale.ROOT));
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) return null;
        Item item = BuiltInRegistries.ITEM.getValue(id);
        if (item == Items.AIR) return null;
        ItemStack stack = new ItemStack(item);
        if (name != null && !name.isEmpty()) stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        return stack;
    }

    public static Layout layout(TextBox b, Font tr, Style base, int availW) {
        String text = Placeholders.apply(b.text);
        String key = text + '\u0000' + b.resolvedFont() + b.bold + b.italic + b.underline + b.strikethrough
                + '|' + availW + '|' + b.iconSize + '|' + b.lineSpacing + '|' + ImageManager.generation;
        Cached c = CACHE.get(b);
        if (c != null && c.key().equals(key)) return c.layout();
        Layout l = build(b, text, tr, base, availW);
        CACHE.put(b, new Cached(key, l));
        return l;
    }

    private static Layout build(TextBox b, String text, Font tr, Style base, int availW) {
        List<Line> out = new ArrayList<>();
        for (String raw : text.split("\n", -1)) {
            List<Atom> atoms = parseLine(raw, tr, base, b.iconSize);
            wrap(atoms, availW, tr.lineHeight, out);
        }
        int total = 0;
        for (Line l : out) total += l.height;
        total += Math.max(0, out.size() - 1) * b.lineSpacing;
        return new Layout(out, total);
    }

    private static List<Atom> parseLine(String raw, Font tr, Style base, int iconSize) {
        // Swap tokens for placeholder code points so one formatted-text pass keeps color codes continuous.
        // Each marker is an Atom (icon), a StyleOp, or a String (a broken token, shown in red).
        List<Object> marks = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        Matcher m = TOKEN.matcher(raw);
        int last = 0;
        while (m.find()) {
            sb.append(raw, last, m.start());
            Object mark = makeMark(m, iconSize);
            marks.add(mark == null ? m.group() : mark);
            sb.appendCodePoint(PLACEHOLDER_BASE + marks.size() - 1);
            last = m.end();
        }
        sb.append(raw, last, raw.length());

        // Same rules as vanilla formatted text (section-sign codes), plus our style markers.
        List<Atom> atoms = new ArrayList<>();
        String s = sb.toString();
        Style style = base;
        int i = 0;
        while (i < s.length()) {
            int cp = s.codePointAt(i);
            if (cp == '§') {
                if (i + 1 >= s.length()) break;
                ChatFormatting f = ChatFormatting.getByCode(s.charAt(i + 1));
                if (f != null) style = f == ChatFormatting.RESET ? base : style.applyLegacyFormat(f);
                i += 2;
                continue;
            }
            i += Character.charCount(cp);
            int idx = cp - PLACEHOLDER_BASE;
            if (idx >= 0 && idx < marks.size()) {
                Object mark = marks.get(idx);
                if (mark instanceof Atom a) {
                    atoms.add(a);
                } else if (mark instanceof StyleOp op) {
                    style = op.apply(style, base);
                } else {
                    Style err = style.withColor(ChatFormatting.RED);
                    ((String) mark).codePoints().forEach(c -> atoms.add(glyph(tr, c, err)));
                }
            } else {
                atoms.add(glyph(tr, cp, style));
            }
        }
        return atoms;
    }

    private static Object makeMark(Matcher m, int iconSize) {
        if (m.group(1) != null) return makeIcon(m.group(1), m.group(2), iconSize);
        if (m.group(3) != null) {
            String name = m.group(4);
            if (name == null || name.isBlank()) return (StyleOp) (cur, base) -> cur.withFont(base.getFont());
            Identifier id = fontId(name);
            if (id == null) return null;
            FontDescription.Resource font = new FontDescription.Resource(id);
            return (StyleOp) (cur, base) -> cur.withFont(font);
        }
        if (m.group(5) != null) {
            TextColor color = TextColor.fromRgb(Integer.parseInt(m.group(5), 16));
            return (StyleOp) (cur, base) -> cur.withColor(color);
        }
        return (StyleOp) (cur, base) -> cur.withColor(base.getColor());
    }

    private static Glyph glyph(Font tr, int cp, Style style) {
        String s = Character.toString(cp);
        return new Glyph(s, style, tr.width(Component.literal(s).setStyle(style)), cp == ' ');
    }

    private static Atom makeIcon(String kind, String body, int size) {
        if ("item".equals(kind)) {
            int bar = body.indexOf('|');
            String id = bar >= 0 ? body.substring(0, bar) : body;
            String name = bar >= 0 ? body.substring(bar + 1) : null;
            ItemStack stack = makeStack(id, name);
            return stack == null ? null : new Icon(stack, null, size, size);
        }
        ImageManager.LoadedImage img = ImageManager.get(body.trim());
        if (img == null || img.height() <= 0) return null;
        int w = Math.max(1, Math.round(size * img.width() / (float) img.height()));
        return new Icon(null, img, w, size);
    }

    /** Greedy word wrap. Icons are unbreakable; over-long words are broken per atom. */
    private static void wrap(List<Atom> atoms, int availW, int fontH, List<Line> out) {
        Line line = newLine(fontH);
        int x = 0;
        int i = 0;
        int n = atoms.size();
        while (i < n) {
            // word = non-space run followed by its trailing spaces
            int wordStart = i;
            int wordW = 0;
            while (i < n && !isSpace(atoms.get(i))) wordW += atoms.get(i++).width();
            int wordEnd = i;
            int spaceStart = i;
            while (i < n && isSpace(atoms.get(i))) i++;

            if (x > 0 && x + wordW > availW) {
                out.add(line);
                line = newLine(fontH);
                x = 0;
            }
            for (int k = wordStart; k < wordEnd; k++) {
                Atom a = atoms.get(k);
                if (wordW > availW && x > 0 && x + a.width() > availW) {
                    out.add(line);
                    line = newLine(fontH);
                    x = 0;
                }
                place(line, a, x);
                x += a.width();
                line.width = x;
            }
            for (int k = spaceStart; k < i; k++) {
                place(line, atoms.get(k), x);
                x += atoms.get(k).width();
            }
        }
        out.add(line);
    }

    private static boolean isSpace(Atom a) {
        return a instanceof Glyph g && g.space();
    }

    private static Line newLine(int fontH) {
        Line l = new Line();
        l.height = fontH;
        return l;
    }

    private static void place(Line line, Atom a, int x) {
        line.items.add(new Placed(a, x));
        if (a instanceof Icon icon) line.height = Math.max(line.height, icon.height());
    }

    /** Draws a laid-out text block in the current (already translated/scaled) matrix space. */
    public static void draw(GuiGraphicsExtractor ctx, Font tr, Layout layout, TextBox.Align align, float availW,
                            int color, boolean shadow, int lineSpacing) {
        int y = 0;
        for (Line line : layout.lines()) {
            int lx = switch (align) {
                case LEFT -> 0;
                case CENTER -> Math.round((availW - line.width) / 2f);
                case RIGHT -> Math.round(availW - line.width);
            };
            int textY = y + (line.height - tr.lineHeight + 1) / 2;
            List<Placed> items = line.items;
            int k = 0;
            while (k < items.size()) {
                Placed p = items.get(k);
                if (p.atom() instanceof Glyph g) {
                    // batch consecutive glyphs of the same style
                    StringBuilder run = new StringBuilder(g.s());
                    int startX = p.x();
                    k++;
                    while (k < items.size() && items.get(k).atom() instanceof Glyph g2 && g2.style().equals(g.style())) {
                        run.append(g2.s());
                        k++;
                    }
                    ctx.text(tr, Component.literal(run.toString()).setStyle(g.style()), lx + startX, textY, color, shadow);
                } else {
                    Icon icon = (Icon) p.atom();
                    int iy = y + (line.height - icon.height()) / 2;
                    if (icon.stack() != null) {
                        Matrix3x2fStack m = ctx.pose();
                        m.pushMatrix();
                        m.translate(lx + p.x(), iy);
                        m.scale(icon.width() / 16f, icon.height() / 16f);
                        ItemAlpha.current = ((color >>> 24) & 0xFF) / 255f;
                        try {
                            ctx.item(icon.stack(), 0, 0);
                        } finally {
                            ItemAlpha.current = 1f;
                        }
                        m.popMatrix();
                    } else if (icon.image() != null) {
                        ImageManager.LoadedImage img = icon.image();
                        ctx.blit(RenderPipelines.GUI_TEXTURED, img.id(), lx + p.x(), iy, 0f, 0f,
                                icon.width(), icon.height(), img.width(), img.height(), img.width(), img.height(),
                                (color & 0xFF000000) | 0xFFFFFF);
                    }
                    k++;
                }
            }
            y += line.height + lineSpacing;
        }
    }
}

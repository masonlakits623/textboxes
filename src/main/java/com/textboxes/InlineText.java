package com.textboxes;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextVisitFactory;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.joml.Matrix3x2fStack;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Text with inline icons. Tokens inside box text:
 * <ul>
 *   <li>{@code {item:diamond}} or {@code {item:mymod:thing}} - an item icon</li>
 *   <li>{@code {item:paper|5}} - the item renamed to "5" (resource packs that key models on the
 *       custom name, like anvil-renamed items, will show their texture)</li>
 *   <li>{@code {img:logo.png}} - an image from config/textboxes/images/</li>
 * </ul>
 * Legacy section-sign color codes keep working across tokens.
 */
public final class InlineText {
    public static final Pattern TOKEN = Pattern.compile("\\{(item|img):([^{}\\n]+)}");
    private static final int PLACEHOLDER_BASE = 0xF0000; // supplementary private use area

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
        if (id == null || !Registries.ITEM.containsId(id)) return null;
        Item item = Registries.ITEM.get(id);
        if (item == Items.AIR) return null;
        ItemStack stack = new ItemStack(item);
        if (name != null && !name.isEmpty()) stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(name));
        return stack;
    }

    public static Layout layout(TextBox b, TextRenderer tr, Style base, int availW) {
        String key = b.text + '\u0000' + b.resolvedFont() + b.bold + b.italic + b.underline + b.strikethrough
                + '|' + availW + '|' + b.iconSize + '|' + b.lineSpacing + '|' + ImageManager.generation;
        Cached c = CACHE.get(b);
        if (c != null && c.key().equals(key)) return c.layout();
        Layout l = build(b, tr, base, availW);
        CACHE.put(b, new Cached(key, l));
        return l;
    }

    private static Layout build(TextBox b, TextRenderer tr, Style base, int availW) {
        List<Line> out = new ArrayList<>();
        for (String raw : b.text.split("\n", -1)) {
            List<Atom> atoms = parseLine(raw, tr, base, b.iconSize);
            wrap(atoms, availW, tr.fontHeight, out);
        }
        int total = 0;
        for (Line l : out) total += l.height;
        total += Math.max(0, out.size() - 1) * b.lineSpacing;
        return new Layout(out, total);
    }

    private static List<Atom> parseLine(String raw, TextRenderer tr, Style base, int iconSize) {
        // Swap tokens for placeholder code points so one formatted-text pass keeps color codes continuous.
        List<Atom> icons = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        Matcher m = TOKEN.matcher(raw);
        int last = 0;
        while (m.find()) {
            sb.append(raw, last, m.start());
            Atom icon = makeIcon(m.group(1), m.group(2), iconSize);
            icons.add(icon);
            failed.add(icon == null ? m.group() : null);
            sb.appendCodePoint(PLACEHOLDER_BASE + icons.size() - 1);
            last = m.end();
        }
        sb.append(raw, last, raw.length());

        List<Atom> atoms = new ArrayList<>();
        TextVisitFactory.visitFormatted(sb.toString(), 0, base, base, (index, style, cp) -> {
            int idx = cp - PLACEHOLDER_BASE;
            if (idx >= 0 && idx < icons.size()) {
                Atom icon = icons.get(idx);
                if (icon != null) {
                    atoms.add(icon);
                } else {
                    // Unknown item / missing image: show the token in red so it can be fixed.
                    Style err = style.withColor(Formatting.RED);
                    failed.get(idx).codePoints().forEach(c -> atoms.add(glyph(tr, c, err)));
                }
            } else {
                atoms.add(glyph(tr, cp, style));
            }
            return true;
        });
        return atoms;
    }

    private static Glyph glyph(TextRenderer tr, int cp, Style style) {
        String s = Character.toString(cp);
        return new Glyph(s, style, tr.getWidth(Text.literal(s).setStyle(style)), cp == ' ');
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
    public static void draw(DrawContext ctx, TextRenderer tr, Layout layout, TextBox.Align align, float availW,
                            int color, boolean shadow, int lineSpacing) {
        int y = 0;
        for (Line line : layout.lines()) {
            int lx = switch (align) {
                case LEFT -> 0;
                case CENTER -> Math.round((availW - line.width) / 2f);
                case RIGHT -> Math.round(availW - line.width);
            };
            int textY = y + (line.height - tr.fontHeight + 1) / 2;
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
                    ctx.drawText(tr, Text.literal(run.toString()).setStyle(g.style()), lx + startX, textY, color, shadow);
                } else {
                    Icon icon = (Icon) p.atom();
                    int iy = y + (line.height - icon.height()) / 2;
                    if (icon.stack() != null) {
                        Matrix3x2fStack m = ctx.getMatrices();
                        m.pushMatrix();
                        m.translate(lx + p.x(), iy);
                        m.scale(icon.width() / 16f, icon.height() / 16f);
                        ItemAlpha.current = ((color >>> 24) & 0xFF) / 255f;
                        try {
                            ctx.drawItem(icon.stack(), 0, 0);
                        } finally {
                            ItemAlpha.current = 1f;
                        }
                        m.popMatrix();
                    } else if (icon.image() != null) {
                        ImageManager.LoadedImage img = icon.image();
                        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, img.id(), lx + p.x(), iy, 0f, 0f,
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

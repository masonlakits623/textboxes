package com.textboxes;

import com.textboxes.TextBox.Shape;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import org.joml.Matrix3x2fStack;

/**
 * Draws non-rectangular boxes. Shapes are filled one screen-pixel row at a time (the matrix is scaled
 * down by the GUI scale), so curves and slopes stay smooth at any GUI scale.
 */
public final class ShapeRenderer {
    private ShapeRenderer() {}

    /** Fixed-point factor so per-row texture regions can be fractional. */
    private static final int UV = 1024;

    // ------------------------------------------------------------------ geometry

    /**
     * Horizontal extent of a shape of size w x h at height py (all relative to the shape's top-left).
     * Writes [left, right) into out and returns false if the row is empty.
     */
    static boolean span(Shape s, float w, float h, float size, float tailPos, float py, float[] out) {
        if (w <= 0 || h <= 0 || py < 0 || py >= h) return false;
        float l = 0, r = w;
        switch (s) {
            case RECTANGLE -> { }
            case ROUNDED -> {
                float d = roundInset(Math.min(size, Math.min(w, h) / 2f), h, py);
                l = d;
                r = w - d;
            }
            case PILL -> {
                float d = roundInset(Math.min(w, h) / 2f, h, py);
                l = d;
                r = w - d;
            }
            case ELLIPSE -> {
                float b = h / 2f, t = (py - b) / b;
                float hw = w / 2f * (float) Math.sqrt(Math.max(0f, 1f - t * t));
                l = w / 2f - hw;
                r = w / 2f + hw;
            }
            case HEXAGON -> {
                float c = Math.min(size, w / 2f);
                float k = Math.abs(py - h / 2f) / (h / 2f);
                l = c * k;
                r = w - c * k;
            }
            case CHAMFER -> {
                float c = Math.min(size, Math.min(w, h) / 2f);
                float e = py < c ? c - py : (py > h - c ? py - (h - c) : 0f);
                l = e;
                r = w - e;
            }
            case RIBBON -> {
                float c = Math.min(size, w / 4f);
                float k = 1f - Math.abs(py - h / 2f) / (h / 2f);
                l = c * k;
                r = w - c * k;
            }
            case ARROW -> {
                float c = Math.min(size, w / 2f);
                float k = Math.abs(py - h / 2f) / (h / 2f);
                r = w - c * k;
            }
            case SPEECH -> {
                float t = tail(size, h);
                float bh = h - t;
                if (py < bh) {
                    float d = roundInset(Math.min(t / 2f, Math.min(w, bh) / 2f), bh, py);
                    l = d;
                    r = w - d;
                } else {
                    // Tail: a triangle hanging off the bottom edge, leaning left.
                    float tb = Math.min(t * 1.2f, w / 3f);
                    float left = tailPos * (w - tb);
                    float f = (py - bh) / t;
                    l = left;
                    r = left + tb * (1f - f);
                }
            }
        }
        if (r <= l) return false;
        out[0] = l;
        out[1] = r;
        return true;
    }

    static float tail(float size, float h) {
        return Math.max(2f, Math.min(size, h / 2f));
    }

    private static float roundInset(float rad, float h, float py) {
        if (rad <= 0) return 0;
        float dy;
        if (py < rad) dy = rad - py;
        else if (py > h - rad) dy = py - (h - rad);
        else return 0;
        return rad - (float) Math.sqrt(Math.max(0f, rad * rad - dy * dy));
    }

    /** The same shape shrunk by d on every side (the inside of the border). */
    static boolean innerSpan(Shape s, float w, float h, float size, float tailPos, float d, float py, float[] out) {
        if (d <= 0) return span(s, w, h, size, tailPos, py, out);
        float iw = w - 2 * d, ih = h - 2 * d;
        if (iw <= 0 || ih <= 0) return false;
        float is = switch (s) {
            case ROUNDED -> Math.max(0f, size - d);
            case CHAMFER -> Math.max(0f, size - d * 0.414f);
            case HEXAGON, ARROW, RIBBON -> size * ih / h;
            default -> size;
        };
        if (!span(s, iw, ih, is, tailPos, py - d, out)) return false;
        out[0] += d;
        out[1] += d;
        return true;
    }

    /** Area (GUI coords x1, y1, x2, y2) that text is laid out in, so it stays inside the shape. */
    static int[] textArea(TextBox b, int bw) {
        float x1 = b.x + bw, y1 = b.y + bw, x2 = b.x + b.width - bw, y2 = b.y + b.height - bw;
        float iw = x2 - x1, ih = y2 - y1;
        float s = b.shapeSize;
        switch (b.shape) {
            case RECTANGLE -> { }
            case ROUNDED -> {
                float in = Math.max(0f, Math.min(s, Math.min(b.width, b.height) / 2f) - bw) * 0.3f;
                x1 += in; x2 -= in; y1 += in; y2 -= in;
            }
            case PILL -> {
                float r = Math.min(iw, ih) / 2f;
                if (iw >= ih) { x1 += r * 0.5f; x2 -= r * 0.5f; } else { y1 += r * 0.5f; y2 -= r * 0.5f; }
            }
            case ELLIPSE -> {
                x1 += iw * 0.1464f; x2 -= iw * 0.1464f;
                y1 += ih * 0.1464f; y2 -= ih * 0.1464f;
            }
            case HEXAGON -> {
                float c = Math.min(s, b.width / 2f) * ih / b.height;
                x1 += c * 0.5f; x2 -= c * 0.5f;
            }
            case CHAMFER -> {
                float c = Math.max(0f, Math.min(s, Math.min(b.width, b.height) / 2f) - bw * 0.414f) * 0.5f;
                x1 += c; x2 -= c; y1 += c * 0.5f; y2 -= c * 0.5f;
            }
            case RIBBON -> {
                float c = Math.min(s, b.width / 4f);
                x1 += c; x2 -= c;
            }
            case ARROW -> x2 -= Math.min(s, b.width / 2f) * 0.75f;
            case SPEECH -> {
                float t = tail(s, b.height);
                y2 -= t;
                float in = t / 2f * 0.3f;
                x1 += in; x2 -= in; y1 += in; y2 -= in;
            }
        }
        int ix1 = Math.round(x1), iy1 = Math.round(y1);
        return new int[]{ix1, iy1, Math.max(ix1, Math.round(x2)), Math.max(iy1, Math.round(y2))};
    }

    // ------------------------------------------------------------------ drawing

    /** Merges identical consecutive rows into one fill. */
    private static final class Rows {
        private final GuiGraphicsExtractor ctx;
        private int x1, x2, color, yStart, yEnd;
        private boolean open;

        Rows(GuiGraphicsExtractor ctx) {
            this.ctx = ctx;
        }

        void add(int y, int a, int b, int c) {
            if (b <= a) return;
            if (open && a == x1 && b == x2 && c == color && y == yEnd) {
                yEnd++;
                return;
            }
            flush();
            x1 = a; x2 = b; color = c; yStart = y; yEnd = y + 1;
            open = true;
        }

        void flush() {
            if (open) ctx.fill(x1, yStart, x2, yEnd, color);
            open = false;
        }
    }

    /** Fills [a0,a1) minus [b0,b1) on row y. */
    private static void addMinus(Rows left, Rows right, int y, int a0, int a1, boolean hasB, int b0, int b1, int color) {
        if (!hasB || b1 <= a0 || b0 >= a1) {
            left.add(y, a0, a1, color);
            return;
        }
        left.add(y, a0, Math.min(a1, b0), color);
        right.add(y, Math.max(a0, b1), a1, color);
    }

    public static void render(GuiGraphicsExtractor ctx, TextBox b, int bw) {
        int g = Math.max(1, Minecraft.getInstance().getWindow().getGuiScale());
        Matrix3x2fStack m = ctx.pose();
        m.pushMatrix();
        m.scale(1f / g, 1f / g);
        try {
            draw(ctx, b, bw, g);
        } finally {
            m.popMatrix();
        }
    }

    private static void draw(GuiGraphicsExtractor ctx, TextBox b, int bw, int g) {
        Shape sh = b.shape;
        int X = b.x * g, Y = b.y * g, W = b.width * g, H = b.height * g, D = bw * g;
        float S = b.shapeSize * g, tp = b.tailPos;
        float[] o = new float[2], in = new float[2];
        Rows left = new Rows(ctx), right = new Rows(ctx);

        // Drop shadow: the shape moved down-right, minus the part the box itself covers.
        if (b.boxShadow && b.shadowOpacity > 0f && b.shadowOffset > 0) {
            int off = b.shadowOffset * g;
            int c = TextBox.argb(b.shadowColor, b.shadowOpacity);
            for (int py = 0; py < H + off; py++) {
                if (!span(sh, W, H, S, tp, py - off + 0.5f, o)) continue;
                boolean hb = span(sh, W, H, S, tp, py + 0.5f, in);
                addMinus(left, right, Y + py, X + off + Math.round(o[0]), X + off + Math.round(o[1]),
                        hb, X + Math.round(in[0]), X + Math.round(in[1]), c);
            }
            left.flush();
            right.flush();
        }

        // Background (optionally a vertical gradient).
        boolean grad = b.gradient;
        if (b.bgOpacity > 0f || (grad && b.bgOpacity2 > 0f)) {
            int c1 = TextBox.argb(b.bgColor, b.bgOpacity);
            int c2 = TextBox.argb(b.bgColor2, b.bgOpacity2);
            for (int py = 0; py < H; py++) {
                if (!span(sh, W, H, S, tp, py + 0.5f, o)) continue;
                int c = grad ? lerpArgb(c1, c2, H <= 1 ? 0f : py / (float) (H - 1)) : c1;
                left.add(Y + py, X + Math.round(o[0]), X + Math.round(o[1]), c);
            }
            left.flush();
        }

        // Image, clipped to the inside of the border.
        if (!b.image.isEmpty() && b.imageOpacity > 0f && W - 2 * D > 0 && H - 2 * D > 0) {
            ImageManager.LoadedImage img = ImageManager.get(b.image);
            if (img != null) drawImage(ctx, b, img, X, Y, W, H, D, S, tp);
        }

        // Border ring: outer shape minus inner shape.
        if (D > 0 && b.borderOpacity > 0f) {
            int c = TextBox.argb(b.borderColor, b.borderOpacity);
            for (int py = 0; py < H; py++) {
                if (!span(sh, W, H, S, tp, py + 0.5f, o)) continue;
                boolean hi = innerSpan(sh, W, H, S, tp, D, py + 0.5f, in);
                addMinus(left, right, Y + py, X + Math.round(o[0]), X + Math.round(o[1]),
                        hi, X + Math.round(in[0]), X + Math.round(in[1]), c);
            }
            left.flush();
            right.flush();
        }
    }

    private static void drawImage(GuiGraphicsExtractor ctx, TextBox b, ImageManager.LoadedImage img,
                                  int X, int Y, int W, int H, int D, float S, float tp) {
        int tw = img.width(), th = img.height();
        if (tw <= 0 || th <= 0) return;
        float ix = X + D, iy = Y + D, iwid = W - 2f * D, ihei = H - 2f * D;
        float dx = ix, dy = iy, dw = iwid, dh = ihei, su = 0, sv = 0, sw = tw, shh = th;
        switch (b.imageFit) {
            case STRETCH -> { }
            case FIT -> {
                float s = Math.min(iwid / tw, ihei / th);
                dw = tw * s;
                dh = th * s;
                dx = ix + (iwid - dw) / 2f;
                dy = iy + (ihei - dh) / 2f;
            }
            case FILL -> {
                float s = Math.max(iwid / tw, ihei / th);
                sw = Math.min(tw, iwid / s);
                shh = Math.min(th, ihei / s);
                su = (tw - sw) / 2f;
                sv = (th - shh) / 2f;
            }
        }
        int color = TextBox.argb(b.imageTint, b.imageOpacity);
        float[] in = new float[2];
        float vh = shh / dh;
        for (int py = 0; py < H; py++) {
            int y = Y + py;
            float cy = y + 0.5f;
            if (cy < dy || cy >= dy + dh) continue;
            if (!innerSpan(b.shape, W, H, S, tp, D, py + 0.5f, in)) continue;
            int l = Math.round(Math.max(X + in[0], dx));
            int r = Math.round(Math.min(X + in[1], dx + dw));
            if (r <= l) continue;
            float u0 = su + (l - dx) / dw * sw;
            float uw = (r - l) / dw * sw;
            float v0 = sv + (y - dy) / dh * shh;
            ctx.blit(RenderPipelines.GUI_TEXTURED, img.id(), l, y, u0 * UV, v0 * UV, r - l, 1,
                    Math.max(1, Math.round(uw * UV)), Math.max(1, Math.round(vh * UV)), tw * UV, th * UV, color);
        }
    }

    public static int lerpArgb(int a, int b, float t) {
        int r = 0;
        for (int shift = 0; shift < 32; shift += 8) {
            int ca = (a >>> shift) & 0xFF, cb = (b >>> shift) & 0xFF;
            r |= (Math.round(ca + (cb - ca) * t) & 0xFF) << shift;
        }
        return r;
    }
}

package com.textboxes;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2fStack;

/** Draws a {@link TextBox}: background, image, border, then text. Shared by the HUD and the editor. */
public final class TextBoxRenderer {
    private TextBoxRenderer() {}

    public static void render(GuiGraphicsExtractor ctx, Font tr, TextBox b) {
        int bw = Math.max(0, Math.min(b.borderWidth, Math.min(b.width, b.height) / 2));
        if (b.shape == null || b.shape == TextBox.Shape.RECTANGLE) {
            renderRect(ctx, b, bw);
        } else {
            ShapeRenderer.render(ctx, b, bw);
        }
        if (b.textOpacity > 0f && !b.text.isEmpty()) {
            int[] a = ShapeRenderer.textArea(b, bw);
            drawText(ctx, tr, b, a[0], a[1], a[2], a[3]);
        }
    }

    private static void renderRect(GuiGraphicsExtractor ctx, TextBox b, int bw) {
        int x1 = b.x, y1 = b.y, x2 = b.x + b.width, y2 = b.y + b.height;

        if (b.boxShadow && b.shadowOpacity > 0f && b.shadowOffset > 0) {
            int o = b.shadowOffset;
            int c = TextBox.argb(b.shadowColor, b.shadowOpacity);
            ctx.fill(x2, y1 + o, x2 + o, y2 + o, c);
            ctx.fill(x1 + o, y2, x2, y2 + o, c);
        }

        if (b.gradient) {
            if (b.bgOpacity > 0f || b.bgOpacity2 > 0f) {
                ctx.fillGradient(x1, y1, x2, y2, TextBox.argb(b.bgColor, b.bgOpacity), TextBox.argb(b.bgColor2, b.bgOpacity2));
            }
        } else if (b.bgOpacity > 0f) {
            ctx.fill(x1, y1, x2, y2, TextBox.argb(b.bgColor, b.bgOpacity));
        }

        int ix1 = x1 + bw, iy1 = y1 + bw, ix2 = x2 - bw, iy2 = y2 - bw;
        int iw = ix2 - ix1, ih = iy2 - iy1;

        if (!b.image.isEmpty() && b.imageOpacity > 0f && iw > 0 && ih > 0) {
            ImageManager.LoadedImage img = ImageManager.get(b.image);
            if (img != null) drawImage(ctx, img, ix1, iy1, iw, ih, b.imageFit, TextBox.argb(b.imageTint, b.imageOpacity));
        }

        if (bw > 0 && b.borderOpacity > 0f) {
            int c = TextBox.argb(b.borderColor, b.borderOpacity);
            ctx.fill(x1, y1, x2, y1 + bw, c);
            ctx.fill(x1, y2 - bw, x2, y2, c);
            ctx.fill(x1, y1 + bw, x1 + bw, y2 - bw, c);
            ctx.fill(x2 - bw, y1 + bw, x2, y2 - bw, c);
        }
    }

    public static void drawImage(GuiGraphicsExtractor ctx, ImageManager.LoadedImage img, int x, int y, int w, int h,
                                 TextBox.ImageFit fit, int color) {
        int iw = img.width(), ih = img.height();
        if (iw <= 0 || ih <= 0) return;
        switch (fit) {
            case STRETCH -> ctx.blit(RenderPipelines.GUI_TEXTURED, img.id(), x, y, 0f, 0f, w, h, iw, ih, iw, ih, color);
            case FIT -> {
                float s = Math.min(w / (float) iw, h / (float) ih);
                int dw = Math.max(1, Math.round(iw * s));
                int dh = Math.max(1, Math.round(ih * s));
                ctx.blit(RenderPipelines.GUI_TEXTURED, img.id(), x + (w - dw) / 2, y + (h - dh) / 2,
                        0f, 0f, dw, dh, iw, ih, iw, ih, color);
            }
            case FILL -> {
                float s = Math.max(w / (float) iw, h / (float) ih);
                int rw = Math.max(1, Math.min(iw, Math.round(w / s)));
                int rh = Math.max(1, Math.min(ih, Math.round(h / s)));
                float u = (iw - rw) / 2f, v = (ih - rh) / 2f;
                ctx.blit(RenderPipelines.GUI_TEXTURED, img.id(), x, y, u, v, w, h, rw, rh, iw, ih, color);
            }
        }
    }

    public static Style style(TextBox b) {
        Identifier fontId = Identifier.tryParse(b.resolvedFont());
        Style style = Style.EMPTY
                .withBold(b.bold)
                .withItalic(b.italic)
                .withUnderlined(b.underline)
                .withStrikethrough(b.strikethrough);
        if (fontId != null) style = style.withFont(new FontDescription.Resource(fontId));
        return style;
    }

    private static void drawText(GuiGraphicsExtractor ctx, Font tr, TextBox b, int ix1, int iy1, int ix2, int iy2) {
        float s = b.scale <= 0 ? 1f : b.scale;
        int pad = b.padding;
        float availWScaled = ix2 - ix1 - 2f * pad;
        float availHScaled = iy2 - iy1 - 2f * pad;
        if (availWScaled <= 0 || availHScaled <= 0) return;
        int availW = Math.max(1, (int) (availWScaled / s));

        InlineText.Layout layout = InlineText.layout(b, tr, style(b), availW);
        float contentH = layout.totalHeight() * s;
        float top = switch (b.valign) {
            case TOP -> iy1 + pad;
            case MIDDLE -> iy1 + pad + (availHScaled - contentH) / 2f;
            case BOTTOM -> iy2 - pad - contentH;
        };

        int color = TextBox.argb(b.textColor, b.textOpacity);
        ctx.enableScissor(ix1, iy1, ix2, iy2);
        Matrix3x2fStack m = ctx.pose();
        m.pushMatrix();
        m.translate(ix1 + pad, top);
        m.scale(s, s);
        InlineText.draw(ctx, tr, layout, b.align, availWScaled / s, color, b.shadow, b.lineSpacing);
        m.popMatrix();
        ctx.disableScissor();
    }
}

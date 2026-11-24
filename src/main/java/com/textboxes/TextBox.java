package com.textboxes;

/** One textbox. Plain fields so Gson can (de)serialize it directly. */
public class TextBox {
    public enum Align { LEFT, CENTER, RIGHT }
    public enum VAlign { TOP, MIDDLE, BOTTOM }
    public enum ImageFit { STRETCH, FIT, FILL }

    public enum Shape {
        RECTANGLE("Rectangle", null),
        ROUNDED("Rounded", "Corner radius"),
        PILL("Pill", null),
        ELLIPSE("Ellipse", null),
        HEXAGON("Hexagon", "Point depth"),
        CHAMFER("Cut corners", "Corner cut"),
        RIBBON("Ribbon", "Notch depth"),
        ARROW("Arrow", "Point depth"),
        SPEECH("Speech bubble", "Tail size");

        public final String title;
        /** Label for the size slider, or null if the shape has no size. */
        public final String sizeLabel;

        Shape(String title, String sizeLabel) {
            this.title = title;
            this.sizeLabel = sizeLabel;
        }
    }

    public static final String[] FONT_IDS = {
            "minecraft:default", "minecraft:uniform", "minecraft:alt", "minecraft:illageralt", "custom"
    };
    public static final String[] FONT_NAMES = {"Default", "Uniform", "Galactic", "Illageralt", "Custom"};

    public boolean visible = true;

    // Position / size in GUI-scaled pixels
    public int x = 20;
    public int y = 20;
    public int width = 120;
    public int height = 40;

    public String text = "New textbox";

    public int bgColor = 0x000000;
    public float bgOpacity = 0.5f;

    public int textColor = 0xFFFFFF;
    public float textOpacity = 1f;

    public int borderColor = 0xFFFFFF;
    public float borderOpacity = 1f;
    public int borderWidth = 0;

    public Shape shape = Shape.RECTANGLE;
    /** Corner radius / cut / point depth / tail size in GUI pixels, depending on the shape. */
    public int shapeSize = 8;
    /** Speech bubble tail position along the bottom edge, 0 = left, 1 = right. */
    public float tailPos = 0.2f;

    /** Vertical gradient from bgColor (top) to bgColor2 (bottom). */
    public boolean gradient = false;
    public int bgColor2 = 0x3050A0;
    public float bgOpacity2 = 0.5f;

    public boolean boxShadow = false;
    public int shadowColor = 0x000000;
    public float shadowOpacity = 0.5f;
    public int shadowOffset = 2;

    /** One of FONT_IDS; "custom" means use {@link #customFont}. */
    public String font = "minecraft:default";
    public String customFont = "minecraft:default";
    public float scale = 1f;
    public boolean bold = false;
    public boolean italic = false;
    public boolean underline = false;
    public boolean strikethrough = false;
    public boolean shadow = true;
    public Align align = Align.LEFT;
    public VAlign valign = VAlign.TOP;
    public int padding = 4;
    public int lineSpacing = 1;
    /** Height (unscaled px) of inline {item:...} / {img:...} icons in the text. */
    public int iconSize = 16;

    /** Path relative to config/textboxes/images/, or empty for no image. */
    public String image = "";
    public int imageTint = 0xFFFFFF;
    public float imageOpacity = 1f;
    public ImageFit imageFit = ImageFit.STRETCH;

    public String resolvedFont() {
        return "custom".equals(font) ? customFont : font;
    }

    public int fontIndex() {
        for (int i = 0; i < FONT_IDS.length; i++) {
            if (FONT_IDS[i].equals(font)) return i;
        }
        return FONT_IDS.length - 1;
    }

    /** Fix up anything missing or out of range after loading from JSON. */
    public void sanitize() {
        if (text == null) text = "";
        if (image == null) image = "";
        if (customFont == null || customFont.isBlank()) customFont = "minecraft:default";
        if (font == null || font.isBlank()) font = "minecraft:default";
        boolean known = false;
        for (String id : FONT_IDS) known |= id.equals(font);
        if (!known) {
            customFont = font;
            font = "custom";
        }
        if (align == null) align = Align.LEFT;
        if (valign == null) valign = VAlign.TOP;
        if (imageFit == null) imageFit = ImageFit.STRETCH;
        if (shape == null) shape = Shape.RECTANGLE;
        shapeSize = Math.max(0, Math.min(64, shapeSize));
        tailPos = clamp(tailPos, 0f, 1f);
        bgOpacity2 = clamp(bgOpacity2, 0f, 1f);
        shadowOpacity = clamp(shadowOpacity, 0f, 1f);
        shadowOffset = Math.max(0, Math.min(16, shadowOffset));
        width = Math.max(4, width);
        height = Math.max(4, height);
        scale = clamp(scale, 0.25f, 4f);
        bgOpacity = clamp(bgOpacity, 0f, 1f);
        textOpacity = clamp(textOpacity, 0f, 1f);
        borderOpacity = clamp(borderOpacity, 0f, 1f);
        imageOpacity = clamp(imageOpacity, 0f, 1f);
        borderWidth = Math.max(0, Math.min(16, borderWidth));
        padding = Math.max(0, Math.min(64, padding));
        lineSpacing = Math.max(0, Math.min(32, lineSpacing));
        if (iconSize <= 0) iconSize = 16;
        iconSize = Math.max(4, Math.min(64, iconSize));
    }

    private static float clamp(float v, float min, float max) {
        if (Float.isNaN(v)) return max;
        return Math.max(min, Math.min(max, v));
    }

    public static int argb(int rgb, float opacity) {
        int a = Math.round(Math.max(0f, Math.min(1f, opacity)) * 255f);
        return (a << 24) | (rgb & 0xFFFFFF);
    }
}

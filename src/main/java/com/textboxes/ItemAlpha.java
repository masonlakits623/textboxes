package com.textboxes;

/**
 * Carries an opacity from our drawItem calls to the point where Minecraft blits the item.
 * GUI items are drawn later (deferred), so the value is stamped onto each item's render state
 * when it is created. Render thread only.
 */
public final class ItemAlpha {
    /** Opacity applied to items drawn right now; 1 = normal. */
    public static float current = 1f;

    private ItemAlpha() {}

    /** Implemented on ItemGuiElementRenderState by a mixin. */
    public interface Holder {
        /** 0 = fully opaque (the default), 1 = invisible. */
        float textboxes$getFade();
    }
}

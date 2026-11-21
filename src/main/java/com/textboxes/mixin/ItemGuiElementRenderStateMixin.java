package com.textboxes.mixin;

import com.textboxes.ItemAlpha;
import net.minecraft.client.gui.render.state.ItemGuiElementRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Remembers the opacity that was active when this item was queued for drawing. */
@Mixin(ItemGuiElementRenderState.class)
public abstract class ItemGuiElementRenderStateMixin implements ItemAlpha.Holder {
    // Stored as "fade" so the untouched default (0) means fully opaque.
    @Unique
    private float textboxes$fade;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void textboxes$captureAlpha(CallbackInfo ci) {
        textboxes$fade = 1f - Math.max(0f, Math.min(1f, ItemAlpha.current));
    }

    @Override
    public float textboxes$getFade() {
        return textboxes$fade;
    }
}

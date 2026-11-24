package com.textboxes.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.textboxes.ItemAlpha;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.state.gui.GuiItemRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * GuiRenderer blits each GUI item from its item atlas with a fixed white color (-1) using a
 * premultiplied-alpha pipeline. For items we queued with an opacity, scale all four channels instead.
 */
@Mixin(GuiRenderer.class)
public abstract class GuiRendererMixin {
    @ModifyExpressionValue(method = "submitBlitFromItemAtlas", at = @At(value = "CONSTANT", args = "intValue=-1"))
    private int textboxes$fadeItem(int color, @Local(argsOnly = true) GuiItemRenderState state) {
        float fade = ((ItemAlpha.Holder) (Object) state).textboxes$getFade();
        if (fade <= 0f) return color;
        int a = Math.round((1f - fade) * 255f);
        return (a << 24) | (a << 16) | (a << 8) | a;
    }
}

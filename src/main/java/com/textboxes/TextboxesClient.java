package com.textboxes;

import com.mojang.blaze3d.platform.InputConstants;
import com.textboxes.gui.EditorScreen;
import com.textboxes.gui.ImagePickerScreen;
import com.textboxes.gui.ItemPickerScreen;
import com.textboxes.gui.TextEditScreen;
import com.textboxes.gui.TokenPickerScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class TextboxesClient implements ClientModInitializer {
    public static final String MOD_ID = "textboxes";
    public static final Logger LOGGER = LoggerFactory.getLogger("Textboxes");

    public static TextboxConfig CONFIG = new TextboxConfig();

    private static KeyMapping openEditorKey;
    private static KeyMapping toggleKey;

    @Override
    public void onInitializeClient() {
        CONFIG = TextboxConfig.load();
        ImageManager.ensureDir();

        KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
        openEditorKey = KeyMappingHelper.registerKeyMapping(
                new KeyMapping("key.textboxes.editor", InputConstants.Type.KEYBOARD, InputConstants.KEY_J, category));
        toggleKey = KeyMappingHelper.registerKeyMapping(
                new KeyMapping("key.textboxes.toggle", InputConstants.Type.KEYBOARD, InputConstants.KEY_K, category));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openEditorKey.consumeClick()) {
                if (client.gui.screen() == null) client.gui.setScreen(new EditorScreen());
            }
            while (toggleKey.consumeClick()) {
                CONFIG.hudVisible = !CONFIG.hudVisible;
                CONFIG.save();
                client.gui.hud.setOverlayMessage(Component.literal("Textboxes: ")
                        .append(Component.literal(CONFIG.hudVisible ? "shown" : "hidden")
                                .withStyle(CONFIG.hudVisible ? ChatFormatting.GREEN : ChatFormatting.RED)), false);
            }
        });

        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Identifier.fromNamespaceAndPath(MOD_ID, "boxes"),
                TextboxesClient::renderHud);    }

    private static void renderHud(GuiGraphicsExtractor ctx, DeltaTracker tickCounter) {
        Minecraft client = Minecraft.getInstance();
        if (!CONFIG.hudVisible || client.gui.hud.isHidden()) return;
        if (client.gui.screen() instanceof EditorScreen
                || client.gui.screen() instanceof TextEditScreen
                || client.gui.screen() instanceof ImagePickerScreen
                || client.gui.screen() instanceof ItemPickerScreen
                || client.gui.screen() instanceof TokenPickerScreen) return;

        List<TextBox> boxes = CONFIG.boxes;
        boolean first = true;
        for (TextBox box : boxes) {
            if (!box.visible) continue;
            // New layer per box so a later box's background covers an earlier box's text.
            if (!first) ctx.nextStratum();
            first = false;
            TextBoxRenderer.render(ctx, client.font, box);
        }
    }
}

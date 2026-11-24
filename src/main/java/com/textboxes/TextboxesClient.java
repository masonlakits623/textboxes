package com.textboxes;

import com.textboxes.gui.EditorScreen;
import com.textboxes.gui.ImagePickerScreen;
import com.textboxes.gui.ItemPickerScreen;
import com.textboxes.gui.TextEditScreen;
import com.textboxes.gui.TokenPickerScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class TextboxesClient implements ClientModInitializer {
    public static final String MOD_ID = "textboxes";
    public static final Logger LOGGER = LoggerFactory.getLogger("Textboxes");

    public static TextboxConfig CONFIG = new TextboxConfig();

    private static KeyBinding openEditorKey;
    private static KeyBinding toggleKey;

    @Override
    public void onInitializeClient() {
        CONFIG = TextboxConfig.load();
        ImageManager.ensureDir();

        KeyBinding.Category category = KeyBinding.Category.create(Identifier.of(MOD_ID, "main"));
        openEditorKey = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.textboxes.editor", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_J, category));
        toggleKey = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.textboxes.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_K, category));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openEditorKey.wasPressed()) {
                if (client.currentScreen == null) client.setScreen(new EditorScreen());
            }
            while (toggleKey.wasPressed()) {
                CONFIG.hudVisible = !CONFIG.hudVisible;
                CONFIG.save();
                client.inGameHud.setOverlayMessage(Text.literal("Textboxes: ")
                        .append(Text.literal(CONFIG.hudVisible ? "shown" : "hidden")
                                .formatted(CONFIG.hudVisible ? Formatting.GREEN : Formatting.RED)), false);
            }
        });

        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Identifier.of(MOD_ID, "boxes"),
                TextboxesClient::renderHud);    }

    private static void renderHud(DrawContext ctx, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!CONFIG.hudVisible || client.options.hudHidden) return;
        if (client.currentScreen instanceof EditorScreen
                || client.currentScreen instanceof TextEditScreen
                || client.currentScreen instanceof ImagePickerScreen
                || client.currentScreen instanceof ItemPickerScreen
                || client.currentScreen instanceof TokenPickerScreen) return;

        List<TextBox> boxes = CONFIG.boxes;
        boolean first = true;
        for (TextBox box : boxes) {
            if (!box.visible) continue;
            // New layer per box so a later box's background covers an earlier box's text.
            if (!first) ctx.createNewRootLayer();
            first = false;
            TextBoxRenderer.render(ctx, client.textRenderer, box);
        }
    }
}

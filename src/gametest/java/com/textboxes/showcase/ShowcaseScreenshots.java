package com.textboxes.showcase;

import com.mojang.datafixers.util.Pair;
import com.textboxes.ImageManager;
import com.textboxes.TextBox;
import com.textboxes.TextboxesClient;
import com.textboxes.gui.EditorScreen;
import com.textboxes.gui.ImagePickerScreen;
import com.textboxes.gui.ItemPickerScreen;
import com.textboxes.gui.TextEditScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.gui.screen.world.WorldCreator;
import net.minecraft.client.option.GraphicsMode;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Builds a showcase world and takes the store-page screenshots (CurseForge / Modrinth / GitHub).
 * Run with ./gradlew runClientGameTest; PNGs land in the project's screenshots/ folder.
 */
public class ShowcaseScreenshots implements FabricClientGameTest {
    private static final int W = 1920, H = 1080, GUI_SCALE = 3;
    private static final String SEED = System.getProperty("textboxes.showcase.seed", "textboxes");
    private static final float YAW = Float.parseFloat(System.getProperty("textboxes.showcase.yaw", "-35"));

    @Override
    public void runTest(ClientGameTestContext ctx) {
        Path out = projectDir().resolve("screenshots");
        try {
            Files.createDirectories(out);
            writeIcons();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        ctx.getInput().resizeWindow(W, H);
        ctx.runOnClient(c -> {
            c.options.getGuiScale().setValue(GUI_SCALE);
            c.options.getViewDistance().setValue(12);
            c.options.applyGraphicsMode(GraphicsMode.FANCY);
            c.onResolutionChanged();
        });

        try (TestSingleplayerContext sp = ctx.worldBuilder()
                .setUseConsistentSettings(false)
                .adjustSettings(cr -> {
                    cr.setSeed(SEED);
                    cr.setGameMode(WorldCreator.Mode.CREATIVE);
                    cr.setCheatsEnabled(true);
                })
                .create()) {

            BlockPos spot = sp.getServer().computeOnServer(server -> {
                ServerWorld world = server.getOverworld();
                BlockPos origin = world.getSpawnPoint().getPos();
                Pair<BlockPos, RegistryEntry<Biome>> found = world.locateBiome(
                        b -> b.matchesKey(BiomeKeys.CHERRY_GROVE), origin, 4000, 32, 64);
                BlockPos p = found != null ? found.getFirst() : origin;
                world.getChunk(p.getX() >> 4, p.getZ() >> 4);
                int top = world.getTopY(Heightmap.Type.MOTION_BLOCKING, p.getX(), p.getZ());
                return new BlockPos(p.getX(), top + 16, p.getZ());
            });
            sp.getServer().runCommand("tp @a " + spot.getX() + " " + spot.getY() + " " + spot.getZ() + " " + YAW + " 20");
            sp.getServer().runOnServer(server -> {
                for (ServerPlayerEntity pl : server.getPlayerManager().getPlayerList()) {
                    pl.getAbilities().flying = true;
                    pl.sendAbilitiesUpdate();
                }
            });
            sp.getServer().runCommand("time set 6000");
            sp.getServer().runCommand("weather clear 99999");
            String[] hotbar = {"diamond_sword", "diamond_pickaxe", "bow", "cooked_beef", "oak_log", "torch", "ender_pearl", "map"};
            for (int i = 0; i < hotbar.length; i++) {
                sp.getServer().runCommand("item replace entity @a hotbar." + i + " with minecraft:" + hotbar[i]);
            }
            ctx.getInput().pressKey(o -> o.hotbarKeys[0]);

            ctx.waitTicks(40);
            try {
                sp.getClientWorld().waitForChunksRender(false, 1200);
            } catch (AssertionError e) {
                // far chunks still meshing is fine for a screenshot
            }
            ctx.waitTicks(100);
            clean(ctx);

            // 1. A clean photo of the world, used as an image inside a textbox.
            ctx.runOnClient(c -> c.options.hudHidden = true);
            Path photo = ctx.takeScreenshot(TestScreenshotOptions.of("photo").disableCounterPrefix());
            ctx.runOnClient(c -> c.options.hudHidden = false);
            try {
                BufferedImage full = ImageIO.read(photo.toFile());
                BufferedImage small = new BufferedImage(480, 270, BufferedImage.TYPE_INT_RGB);
                small.createGraphics().drawImage(full.getScaledInstance(480, 270, java.awt.Image.SCALE_SMOOTH), 0, 0, null);
                ImageIO.write(small, "png", ImageManager.dir().resolve("photo.png").toFile());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            ctx.runOnClient(c -> {
                ImageManager.reload();
                TextboxesClient.CONFIG.hudVisible = true;
                TextboxesClient.CONFIG.boxes = showcaseBoxes();
                TextboxesClient.CONFIG.save();
            });
            ctx.waitTicks(5);
            clean(ctx);
            shot(ctx, out, "01-hud");

            // 2. Editor, TEXT tab on the to-do box.
            ctx.setScreen(EditorScreen::new);
            ctx.waitTicks(3);
            ctx.runOnClient(c -> ((EditorScreen) c.currentScreen).select(1, 1));
            ctx.getInput().setCursorPos(-10, -10);
            ctx.waitTicks(3);
            shot(ctx, out, "02-editor-text");

            // 3. Editor, COLOR tab on the PvP box.
            ctx.runOnClient(c -> ((EditorScreen) c.currentScreen).select(4, 2));
            ctx.waitTicks(3);
            shot(ctx, out, "03-editor-colors");

            // 4. Editor, IMAGE tab on the photo box.
            ctx.runOnClient(c -> ((EditorScreen) c.currentScreen).select(5, 3));
            ctx.waitTicks(3);
            shot(ctx, out, "04-editor-image");

            // 5. Multi-line text editor with item / image tokens.
            EditorScreen editor = ctx.computeOnClient(c -> (EditorScreen) c.currentScreen);
            ctx.setScreen(() -> new TextEditScreen(editor, TextboxesClient.CONFIG.boxes.get(1)));
            ctx.waitTicks(3);
            shot(ctx, out, "05-text-editor");

            // 6. Item picker: every item, plus renaming for resource-pack textures.
            setPickerState("diamond", "Excalibur");
            ctx.setScreen(() -> new ItemPickerScreen(editor, s -> {}));
            ctx.waitTicks(3);
            // hover the 5th result so its tooltip (name + token) shows
            ctx.getInput().setCursorPos((140 + 4 * 18 + 9) * GUI_SCALE, (52 + 9) * GUI_SCALE);
            ctx.waitTicks(3);
            shot(ctx, out, "06-item-picker-rename");

            setPickerState("", "");
            ctx.setScreen(() -> new ItemPickerScreen(editor, s -> {}));
            ctx.waitTicks(3);
            ctx.getInput().setCursorPos((140 + 7 * 18 + 9) * GUI_SCALE, (52 + 3 * 18 + 9) * GUI_SCALE);
            ctx.waitTicks(3);
            shot(ctx, out, "07-item-picker");

            // 8. Image picker.
            ctx.setScreen(() -> new ImagePickerScreen(editor, "photo.png", true, s -> {}));
            ctx.waitTicks(5);
            ctx.getInput().setCursorPos(-10, -10);
            shot(ctx, out, "08-image-picker");

            // 9. HUD again with every box hidden except the to-do list, closer to normal play.
            ctx.setScreen(() -> null);
            ctx.runOnClient(c -> {
                List<TextBox> boxes = TextboxesClient.CONFIG.boxes;
                for (int i = 0; i < boxes.size(); i++) boxes.get(i).visible = i == 1 || i == 4;
            });
            ctx.waitTicks(3);
            clean(ctx);
            shot(ctx, out, "09-hud-minimal");
        }
    }

    private static void shot(ClientGameTestContext ctx, Path out, String name) {
        ctx.takeScreenshot(TestScreenshotOptions.of(name).withDestinationDir(out).disableCounterPrefix());
    }

    /** Clears chat lines and toasts so they don't clutter the shots. */
    private static void clean(ClientGameTestContext ctx) {
        ctx.runOnClient(c -> {
            c.inGameHud.getChatHud().clear(false);
            c.getToastManager().clear();
        });
        ctx.waitTicks(2);
    }

    private static void setPickerState(String search, String name) {
        try {
            Field s = ItemPickerScreen.class.getDeclaredField("lastSearch");
            Field n = ItemPickerScreen.class.getDeclaredField("lastName");
            s.setAccessible(true);
            n.setAccessible(true);
            s.set(null, search);
            n.set(null, name);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static Path projectDir() {
        Path p = Path.of("").toAbsolutePath();
        while (p != null && !Files.exists(p.resolve("build.gradle"))) p = p.getParent();
        if (p == null) throw new IllegalStateException("project dir not found");
        return p;
    }

    // ---------------------------------------------------------------- showcase content

    private static List<TextBox> showcaseBoxes() {
        List<TextBox> list = new ArrayList<>();

        // 0: title banner
        TextBox title = box(222, 8, 196, 34);
        title.text = "{img:star.png} Textboxes {img:star.png}";
        title.textColor = 0xFFD866;
        title.bold = true;
        title.scale = 2f;
        title.iconSize = 9;
        title.align = TextBox.Align.CENTER;
        title.valign = TextBox.VAlign.MIDDLE;
        title.bgColor = 0x1A0F2E;
        title.bgOpacity = 0.85f;
        title.borderColor = 0xB98CFF;
        title.borderWidth = 1;
        list.add(title);

        // 1: to-do list with item icons
        TextBox todo = box(8, 8, 156, 80);
        todo.text = "§6§lToday's goals\n"
                + "{item:diamond_pickaxe} Mine 64 {item:diamond}\n"
                + "{item:oak_log} §7§mChop wood§r §a✔\n"
                + "{item:cooked_beef} Stock up on food\n"
                + "{item:ender_eye} Find the stronghold";
        todo.iconSize = 12;
        todo.lineSpacing = 2;
        todo.bgColor = 0x000000;
        todo.bgOpacity = 0.55f;
        list.add(todo);

        // 2: whole box faded, items and images fade with the text
        TextBox faded = box(476, 8, 156, 44);
        faded.text = "§aFaded to 50%\n{item:emerald} {item:golden_apple} {item:totem_of_undying} {img:heart.png}";
        faded.iconSize = 12;
        faded.textOpacity = 0.5f;
        faded.bgColor = 0x0B3D2E;
        faded.bgOpacity = 0.3f;
        faded.borderColor = 0x55FF99;
        faded.borderOpacity = 0.5f;
        faded.borderWidth = 1;
        list.add(faded);

        // 3: Galactic font
        TextBox galactic = box(476, 58, 156, 26);
        galactic.text = "meet at the portal";
        galactic.font = "minecraft:alt";
        galactic.textColor = 0x66E0FF;
        galactic.align = TextBox.Align.CENTER;
        galactic.valign = TextBox.VAlign.MIDDLE;
        galactic.bgColor = 0x001828;
        galactic.bgOpacity = 0.7f;
        galactic.borderColor = 0x66E0FF;
        galactic.borderWidth = 1;
        list.add(galactic);

        // 4: stats box with inline image + item
        TextBox pvp = box(8, 102, 156, 38);
        pvp.text = "§c§lPvP Arena {img:heart.png}\n§7Kills §f12  §7Deaths §f3  {item:netherite_sword}";
        pvp.iconSize = 10;
        pvp.lineSpacing = 3;
        pvp.bgColor = 0x3B0A0A;
        pvp.bgOpacity = 0.75f;
        pvp.borderColor = 0xFF5555;
        pvp.borderWidth = 1;
        list.add(pvp);

        // 5: image box (screenshot of this world) with a caption
        TextBox photo = box(476, 92, 156, 88);
        photo.image = "photo.png";
        photo.imageFit = TextBox.ImageFit.FILL;
        photo.text = "Spawn, day one";
        photo.valign = TextBox.VAlign.BOTTOM;
        photo.bgOpacity = 0f;
        photo.borderColor = 0xFFFFFF;
        photo.borderWidth = 1;
        list.add(photo);

        // 6: tip box, Uniform font + italic
        TextBox tip = box(476, 188, 156, 36);
        tip.text = "§eTip: §fpress §bJ§f to edit boxes,\n§bK§f to hide them all";
        tip.font = "minecraft:uniform";
        tip.italic = true;
        tip.valign = TextBox.VAlign.MIDDLE;
        tip.bgColor = 0x202020;
        tip.bgOpacity = 0.65f;
        tip.borderColor = 0xFFD866;
        tip.borderWidth = 1;
        list.add(tip);

        return list;
    }

    private static TextBox box(int x, int y, int w, int h) {
        TextBox b = new TextBox();
        b.x = x;
        b.y = y + 20; // clear of the editor toolbar
        b.width = w;
        b.height = h;
        return b;
    }

    private static void writeIcons() throws IOException {
        ImageManager.ensureDir();
        pixelArt(ImageManager.dir().resolve("heart.png"), new String[]{
                ".XX...XX.",
                "XWRX.XRRX",
                "XRRRXRRRX",
                "XRRRRRRRX",
                ".XRRRRRX.",
                "..XRRRX..",
                "...XRX...",
                "....X....",
        }, Map.of('X', 0xFF3A0000, 'R', 0xFFFF2A2A, 'W', 0xFFFFFFFF));
        pixelArt(ImageManager.dir().resolve("star.png"), new String[]{
                "....X....",
                "...XYX...",
                "XXXXYXXXX",
                "XYYYWYYYX",
                ".XYYYYYX.",
                "..XYYYX..",
                ".XYYXYYX.",
                "XYYX.XYYX",
                "XXX...XXX",
        }, Map.of('X', 0xFF6B4A00, 'Y', 0xFFFFD84A, 'W', 0xFFFFFFFF));

        // a couple of larger images so the picker isn't empty
        BufferedImage sunset = new BufferedImage(320, 180, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = sunset.createGraphics();
        g.setPaint(new java.awt.GradientPaint(0, 0, new java.awt.Color(0x2B1055), 0, 180, new java.awt.Color(0xFF8A3D)));
        g.fillRect(0, 0, 320, 180);
        g.setColor(new java.awt.Color(0xFFE27A));
        g.fillOval(110, 95, 100, 100);
        g.setColor(new java.awt.Color(0x1B1030));
        g.fillRect(0, 150, 320, 30);
        g.dispose();
        ImageIO.write(sunset, "png", ImageManager.dir().resolve("sunset.png").toFile());

        BufferedImage badge = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
        g = badge.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        g.setPaint(new java.awt.GradientPaint(0, 0, new java.awt.Color(0x55FFCC), 128, 128, new java.awt.Color(0x3366FF)));
        g.fillRoundRect(4, 4, 120, 120, 36, 36);
        g.setColor(java.awt.Color.WHITE);
        g.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.BOLD, 64));
        g.drawString("TB", 22, 86);
        g.dispose();
        ImageIO.write(badge, "png", ImageManager.dir().resolve("server-badge.png").toFile());
    }

    private static void pixelArt(Path file, String[] rows, Map<Character, Integer> palette) throws IOException {
        BufferedImage img = new BufferedImage(rows[0].length(), rows.length, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < rows.length; y++) {
            for (int x = 0; x < rows[y].length(); x++) {
                img.setRGB(x, y, palette.getOrDefault(rows[y].charAt(x), 0));
            }
        }
        ImageIO.write(img, "png", file.toFile());
    }
}

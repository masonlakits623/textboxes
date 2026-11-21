package com.textboxes;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

/** Loads images from config/textboxes/images/ into GPU textures and caches them. */
public final class ImageManager {
    public record LoadedImage(Identifier id, int width, int height) {}

    private static final Set<String> EXTENSIONS = Set.of("png", "jpg", "jpeg", "gif", "bmp");
    private static final Map<String, LoadedImage> CACHE = new HashMap<>();
    private static final Set<String> FAILED = new HashSet<>();
    private static int counter = 0;
    /** Bumped on every reload so cached text layouts drop stale texture ids. */
    public static int generation = 0;

    private ImageManager() {}

    public static Path dir() {
        return FabricLoader.getInstance().getConfigDir().resolve("textboxes").resolve("images");
    }

    public static void ensureDir() {
        try {
            Files.createDirectories(dir());
        } catch (IOException e) {
            TextboxesClient.LOGGER.warn("Could not create {}", dir(), e);
        }
    }

    public static String normalizeName(String name) {
        return name == null ? "" : name.replace('\\', '/').trim();
    }

    /** Resolves a name inside the images folder, or returns null if it escapes the folder or is invalid. */
    public static Path resolveSafe(String name) {
        name = normalizeName(name);
        if (name.isEmpty()) return null;
        try {
            Path base = dir().toAbsolutePath().normalize();
            Path p = base.resolve(name).normalize();
            if (!p.startsWith(base) || p.equals(base)) return null;
            if (Files.exists(p) && Files.exists(base)) {
                // Guard against symlinks pointing outside the folder
                if (!p.toRealPath().startsWith(base.toRealPath())) return null;
            }
            return p;
        } catch (InvalidPathException | IOException e) {
            return null;
        }
    }

    /** Returns the loaded image, loading it on first use. Null if missing/invalid. Render thread only. */
    public static LoadedImage get(String name) {
        name = normalizeName(name);
        if (name.isEmpty()) return null;
        LoadedImage cached = CACHE.get(name);
        if (cached != null) return cached;
        if (FAILED.contains(name)) return null;
        try {
            LoadedImage img = load(name);
            CACHE.put(name, img);
            return img;
        } catch (Exception e) {
            FAILED.add(name);
            TextboxesClient.LOGGER.warn("Could not load textbox image '{}': {}", name, e.toString());
            return null;
        }
    }

    public static boolean isMissing(String name) {
        name = normalizeName(name);
        return !name.isEmpty() && get(name) == null;
    }

    private static LoadedImage load(String name) throws IOException {
        Path path = resolveSafe(name);
        if (path == null) throw new IOException("Path is outside the images folder");
        if (!Files.isRegularFile(path)) throw new IOException("File not found: " + path);

        byte[] bytes = Files.readAllBytes(path);
        if (!isPng(bytes)) bytes = toPng(bytes);

        NativeImage image = NativeImage.read(bytes);
        int w = image.getWidth();
        int h = image.getHeight();
        Identifier id = Identifier.of(TextboxesClient.MOD_ID, "dynamic/image_" + (counter++));
        final String label = "textboxes image " + name;
        NativeImageBackedTexture texture = new NativeImageBackedTexture(() -> label, image);
        MinecraftClient.getInstance().getTextureManager().registerTexture(id, texture);
        return new LoadedImage(id, w, h);
    }

    private static boolean isPng(byte[] b) {
        return b.length > 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G';
    }

    /** Converts JPG/GIF/BMP (first frame for GIF) to PNG bytes using ImageIO. */
    private static byte[] toPng(byte[] bytes) throws IOException {
        BufferedImage src = ImageIO.read(new ByteArrayInputStream(bytes));
        if (src == null) throw new IOException("Unsupported image format");
        int w = src.getWidth(), h = src.getHeight();
        BufferedImage argb = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        int[] row = new int[w];
        for (int y = 0; y < h; y++) {
            src.getRGB(0, y, w, 1, row, 0, w);
            argb.setRGB(0, y, w, 1, row, 0, w);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (!ImageIO.write(argb, "png", out)) throw new IOException("No PNG writer available");
        return out.toByteArray();
    }

    /** Frees all textures so files are re-read on next use. */
    public static void reload() {
        MinecraftClient client = MinecraftClient.getInstance();
        for (LoadedImage img : CACHE.values()) {
            client.getTextureManager().destroyTexture(img.id());
        }
        CACHE.clear();
        FAILED.clear();
        generation++;
    }

    /** All supported image files in the folder (and subfolders), as relative paths with '/'. */
    public static List<String> listImages() {
        ensureDir();
        Path base = dir().toAbsolutePath().normalize();
        List<String> result = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(base, 4)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> {
                        String n = p.getFileName().toString();
                        int dot = n.lastIndexOf('.');
                        return dot > 0 && EXTENSIONS.contains(n.substring(dot + 1).toLowerCase(Locale.ROOT));
                    })
                    .forEach(p -> result.add(base.relativize(p).toString().replace('\\', '/')));
        } catch (IOException e) {
            TextboxesClient.LOGGER.warn("Could not list images", e);
        }
        result.sort(String.CASE_INSENSITIVE_ORDER);
        return result;
    }

    public static void openFolder() {
        ensureDir();
        Util.getOperatingSystem().open(dir());
    }
}

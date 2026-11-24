package com.textboxes;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

public class TextboxConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public boolean hudVisible = true;
    public List<TextBox> boxes = new ArrayList<>();

    public static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("textboxes.json");
    }

    public static TextboxConfig load() {
        Path file = file();
        TextboxConfig cfg = null;
        if (Files.isRegularFile(file)) {
            try {
                cfg = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), TextboxConfig.class);
            } catch (Exception e) {
                TextboxesClient.LOGGER.error("Could not read {}, starting fresh", file, e);
            }
        }
        if (cfg == null) cfg = new TextboxConfig();
        if (cfg.boxes == null) cfg.boxes = new ArrayList<>();
        cfg.boxes.removeIf(b -> b == null);
        cfg.boxes.forEach(TextBox::sanitize);
        return cfg;
    }

    public void save() {
        Path file = file();
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling("textboxes.json.tmp");
            Files.writeString(tmp, GSON.toJson(this), StandardCharsets.UTF_8);
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            TextboxesClient.LOGGER.error("Could not save {}", file, e);
        }
    }

    /** Snapshot of all boxes, used for undo/redo in the editor. */
    public String snapshot() {
        return GSON.toJson(boxes);
    }

    /** Replaces the boxes with a snapshot taken by {@link #snapshot()}. */
    public void restore(String snapshot) {
        TextBox[] arr = GSON.fromJson(snapshot, TextBox[].class);
        boxes.clear();
        if (arr == null) return;
        for (TextBox b : arr) {
            if (b == null) continue;
            b.sanitize();
            boxes.add(b);
        }
    }

    public static TextBox copy(TextBox box) {
        TextBox c = GSON.fromJson(GSON.toJson(box), TextBox.class);
        c.sanitize();
        return c;
    }
}

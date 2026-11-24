package com.textboxes;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.world.ClientWorld;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Live values like {fps} or {coords}, filled in every frame before the text is laid out. */
public final class Placeholders {
    private Placeholders() {}

    /** Token name and a short description, for the picker. */
    public static final String[][] LIST = {
            {"fps", "FPS"},
            {"coords", "X Y Z"},
            {"x", "X"},
            {"y", "Y"},
            {"z", "Z"},
            {"facing", "Facing"},
            {"biome", "Biome"},
            {"dimension", "Dimension"},
            {"light", "Light level"},
            {"time", "Game time"},
            {"day", "Day"},
            {"clock", "Real time"},
            {"date", "Date"},
            {"player", "Player name"},
            {"health", "Health"},
            {"food", "Hunger"},
            {"armor", "Armor"},
            {"level", "XP level"},
            {"ping", "Ping"},
            {"server", "Server"},
    };

    private static final Pattern PATTERN;

    static {
        StringBuilder sb = new StringBuilder("\\{(");
        for (int i = 0; i < LIST.length; i++) {
            if (i > 0) sb.append('|');
            sb.append(LIST[i][0]);
        }
        PATTERN = Pattern.compile(sb.append(")}").toString());
    }

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");

    public static String apply(String text) {
        if (text.indexOf('{') < 0) return text;
        Matcher m = PATTERN.matcher(text);
        if (!m.find()) return text;
        MinecraftClient mc = MinecraftClient.getInstance();
        StringBuilder sb = new StringBuilder();
        do {
            m.appendReplacement(sb, Matcher.quoteReplacement(value(m.group(1), mc)));
        } while (m.find());
        m.appendTail(sb);
        return sb.toString();
    }

    private static String value(String key, MinecraftClient mc) {
        switch (key) {
            case "fps": return Integer.toString(mc.getCurrentFps());
            case "clock": return LocalTime.now().format(CLOCK);
            case "date": return LocalDate.now().toString();
            case "server": {
                ServerInfo si = mc.getCurrentServerEntry();
                if (si != null) return si.address;
                return mc.isInSingleplayer() ? "Singleplayer" : "-";
            }
            default: break;
        }
        ClientPlayerEntity p = mc.player;
        ClientWorld w = mc.world;
        if (p == null || w == null) return "-";
        return switch (key) {
            case "x" -> Integer.toString(p.getBlockX());
            case "y" -> Integer.toString(p.getBlockY());
            case "z" -> Integer.toString(p.getBlockZ());
            case "coords" -> p.getBlockX() + " " + p.getBlockY() + " " + p.getBlockZ();
            case "facing" -> pretty(p.getHorizontalFacing().asString());
            case "biome" -> w.getBiome(p.getBlockPos()).getKey()
                    .map(k -> pretty(k.getValue().getPath())).orElse("?");
            case "dimension" -> pretty(w.getRegistryKey().getValue().getPath());
            case "light" -> Integer.toString(w.getLightLevel(p.getBlockPos()));
            case "time" -> {
                long t = Math.floorMod(w.getTimeOfDay() + 6000L, 24000L);
                yield String.format(Locale.ROOT, "%02d:%02d", t / 1000, (t % 1000) * 60 / 1000);
            }
            case "day" -> Long.toString(w.getTimeOfDay() / 24000L + 1);
            case "player" -> p.getName().getString();
            case "health" -> Integer.toString((int) Math.ceil(p.getHealth()));
            case "food" -> Integer.toString(p.getHungerManager().getFoodLevel());
            case "armor" -> Integer.toString(p.getArmor());
            case "level" -> Integer.toString(p.experienceLevel);
            case "ping" -> {
                ClientPlayNetworkHandler nh = mc.getNetworkHandler();
                PlayerListEntry e = nh == null ? null : nh.getPlayerListEntry(p.getUuid());
                yield e == null ? "0" : Integer.toString(e.getLatency());
            }
            default -> "?";
        };
    }

    /** cherry_grove -> Cherry Grove */
    static String pretty(String id) {
        StringBuilder sb = new StringBuilder(id.length());
        boolean up = true;
        for (char c : id.toCharArray()) {
            if (c == '_' || c == '/') {
                sb.append(' ');
                up = true;
            } else {
                sb.append(up ? Character.toUpperCase(c) : c);
                up = false;
            }
        }
        return sb.toString();
    }
}

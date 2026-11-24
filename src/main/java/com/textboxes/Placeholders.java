package com.textboxes;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.player.LocalPlayer;

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
        Minecraft mc = Minecraft.getInstance();
        StringBuilder sb = new StringBuilder();
        do {
            m.appendReplacement(sb, Matcher.quoteReplacement(value(m.group(1), mc)));
        } while (m.find());
        m.appendTail(sb);
        return sb.toString();
    }

    private static String value(String key, Minecraft mc) {
        switch (key) {
            case "fps": return Integer.toString(mc.getFps());
            case "clock": return LocalTime.now().format(CLOCK);
            case "date": return LocalDate.now().toString();
            case "server": {
                ServerData si = mc.getCurrentServer();
                if (si != null) return si.ip;
                return mc.isLocalServer() ? "Singleplayer" : "-";
            }
            default: break;
        }
        LocalPlayer p = mc.player;
        ClientLevel w = mc.level;
        if (p == null || w == null) return "-";
        return switch (key) {
            case "x" -> Integer.toString(p.getBlockX());
            case "y" -> Integer.toString(p.getBlockY());
            case "z" -> Integer.toString(p.getBlockZ());
            case "coords" -> p.getBlockX() + " " + p.getBlockY() + " " + p.getBlockZ();
            case "facing" -> pretty(p.getDirection().getSerializedName());
            case "biome" -> w.getBiome(p.blockPosition()).unwrapKey()
                    .map(k -> pretty(k.identifier().getPath())).orElse("?");
            case "dimension" -> pretty(w.dimension().identifier().getPath());
            case "light" -> Integer.toString(w.getMaxLocalRawBrightness(p.blockPosition()));
            case "time" -> {
                long t = Math.floorMod(w.getOverworldClockTime() + 6000L, 24000L);
                yield String.format(Locale.ROOT, "%02d:%02d", t / 1000, (t % 1000) * 60 / 1000);
            }
            case "day" -> Long.toString(w.getOverworldClockTime() / 24000L + 1);
            case "player" -> p.getName().getString();
            case "health" -> Integer.toString((int) Math.ceil(p.getHealth()));
            case "food" -> Integer.toString(p.getFoodData().getFoodLevel());
            case "armor" -> Integer.toString(p.getArmorValue());
            case "level" -> Integer.toString(p.experienceLevel);
            case "ping" -> {
                ClientPacketListener nh = mc.getConnection();
                PlayerInfo e = nh == null ? null : nh.getPlayerInfo(p.getUUID());
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

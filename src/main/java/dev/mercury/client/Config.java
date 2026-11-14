package dev.mercury.client;


import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;

/** Ajustes generales del cliente (todo lo de cada modulo lo guarda dev.mercury.client.module.ModuleManager en este mismo archivo). */
public final class Config {
    public static boolean firstRun;
    public static int menuKey = 344; // RIGHT SHIFT
    public static boolean openOnJoin = true, hud = true, anim = true;
    public static final Set<String> friends = new LinkedHashSet<>();
    /** Nombres a vigilar con el Detector de cazadores (minusculas). Si esta vacia vigila a todos. */
    public static final Set<String> hunters = new LinkedHashSet<>();

    /** Paleta de Mercury (RGB) y sus nombres. */
    public static final int[] PALETTE = {0xff3030, 0xffa500, 0xffff00, 0x40ff40, 0x40e0ff, 0x4060ff, 0xb040ff, 0xff60c0, 0xffffff, 0x8b5a2b};
    public static final String[] PALETTE_NAMES = {"Rojo", "Naranja", "Amarillo", "Verde", "Celeste", "Azul", "Morado", "Rosa", "Blanco", "Cafe"};
    /** Colores de la hitbox de cristal (ARGB). */
    public static final int[] HB_COLORS = {0xFF00E5FF, 0xFFFF3B3B, 0xFF39FF14, 0xFFB026FF, 0xFFFFE600, 0xFFFFFFFF, 0xFFFF5EDB, 0xFFFF9500};
    public static final String[] HB_NAMES = {"Cyan", "Rojo", "Verde", "Morado", "Amarillo", "Blanco", "Rosa", "Naranja"};

    private static Path file() { return FabricLoader.getInstance().getConfigDir().resolve("mercury-client.properties"); }

    public static boolean isFriend(String name) {
        return name != null && friends.contains(name.toLowerCase(Locale.ROOT));
    }

    public static void load() {
        try {
            Path f = file();
            if (!Files.exists(f)) { firstRun = true; return; }
            Properties p = new Properties();
            try (Reader r = Files.newBufferedReader(f)) { p.load(r); }
            menuKey = i(p, "menuKey", menuKey);
            openOnJoin = Boolean.parseBoolean(p.getProperty("openOnJoin", "" + openOnJoin));
            hud = Boolean.parseBoolean(p.getProperty("hud", "" + hud));
            anim = Boolean.parseBoolean(p.getProperty("anim", "" + anim));
            friends.clear();
            for (String s : p.getProperty("friends", "").split(","))
                if (!s.isBlank()) friends.add(s.trim().toLowerCase(Locale.ROOT));
            hunters.clear();
            for (String s : p.getProperty("hunters", "").split(","))
                if (!s.isBlank()) hunters.add(s.trim().toLowerCase(Locale.ROOT));
            dev.mercury.client.module.ModuleManager.load(p);
        } catch (Exception ignored) {}
    }

    public static void save() {
        try {
            Properties p = new Properties();
            p.setProperty("menuKey", "" + menuKey);
            p.setProperty("openOnJoin", "" + openOnJoin);
            p.setProperty("hud", "" + hud);
            p.setProperty("anim", "" + anim);
            p.setProperty("friends", String.join(",", friends));
            p.setProperty("hunters", String.join(",", hunters));
            dev.mercury.client.module.ModuleManager.save(p);
            try (Writer w = Files.newBufferedWriter(file())) { p.store(w, "Mercury Client"); }
            com.visionplus.client.VPConfig.save();
        } catch (Exception ignored) {}
    }

    private static int i(Properties p, String k, int d) {
        try { return Integer.parseInt(p.getProperty(k, "" + d)); } catch (Exception e) { return d; }
    }
}

package dev.mercury.client.fx;

import dev.mercury.client.module.Setting;

/** Color configurable de un efecto: paleta, animados (arcoiris, galaxia...) o RGB personalizado. */
public final class ColorSet {
    public static final String[] NAMES = {"Rojo", "Naranja", "Amarillo", "Lima", "Verde", "Turquesa", "Celeste", "Azul",
            "Morado", "Magenta", "Rosa", "Blanco", "Dorado", "Arcoiris", "Galaxia", "Fuego", "Hielo", "Personalizado"};
    public static final String[] NAMES_NO_CUSTOM = java.util.Arrays.copyOf(NAMES, NAMES.length - 1);
    private static final int[] SOLID = {0xFF3030, 0xFF8A00, 0xFFE600, 0xA6FF00, 0x30FF60, 0x20FFD0, 0x40D8FF, 0x3C6CFF,
            0xB040FF, 0xFF30E0, 0xFF70B8, 0xFFFFFF, 0xFFC83C};

    public int mode;
    public int r = 255, g = 255, b = 255;

    public ColorSet(int defaultMode) { mode = defaultMode; }

    /** phase (0..1 ciclico) solo importa en los modos animados. */
    public int rgb(float phase) {
        int m = Math.floorMod(mode, NAMES.length);
        if (m < SOLID.length) return SOLID[m];
        float k = 0.5f + 0.5f * (float) Math.sin(phase * 6.2831853f);
        switch (m - SOLID.length) {
            case 0: return Col.hsv(phase, 0.85f, 1f);
            case 1: return Col.hsv(0.62f + 0.30f * k, 0.8f, 1f);
            case 2: return Col.hsv(0.12f * k, 0.95f, 1f);
            case 3: return Col.hsv(0.50f + 0.12f * k, 0.55f, 1f);
            default: return (r << 16) | (g << 8) | b;
        }
    }

    public Setting[] settings() {
        return new Setting[]{
                Setting.choice("Color", NAMES, () -> mode, v -> mode = v, true),
                Setting.integer("Custom R", () -> r, v -> r = v, 0, 255, 15, "", true),
                Setting.integer("Custom G", () -> g, v -> g = v, 0, 255, 15, "", true),
                Setting.integer("Custom B", () -> b, v -> b = v, 0, 255, 15, "", true)
        };
    }
}

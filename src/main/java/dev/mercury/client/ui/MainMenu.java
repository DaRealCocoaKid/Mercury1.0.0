package dev.mercury.client.ui;

import com.visionplus.client.VPConfig;
import dev.mercury.client.Config;
import dev.mercury.client.module.Setting;
import dev.mercury.client.module.ModuleManager;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Menu principal de Mercury: fondo animado gris, barra lateral y paginas (Mundos, Multijugador, Amigos, Mods, Configuracion). */
public class MainMenu extends Screen {
    private record Hit(int x1, int y1, int x2, int y2, Runnable left, Runnable right) {}

    private static final Identifier LOGO = Identifier.fromNamespaceAndPath("visionplus", "textures/gui/logo.png");
    private static final String[] NAV = {"Inicio", "Mundos", "Multijugador", "Amigos", "Mods", "Modulos", "Configuracion"};
    private static final int HOME = 0, WORLDS = 1, MULTI = 2, FRIENDS = 3, MODS = 4, MODULES = 5, CONFIG = 6;

    private final List<Hit> hits = new ArrayList<>();
    private final Map<String, Float> hov = new HashMap<>();
    private final long t0 = System.nanoTime();
    private long pageAt = System.nanoTime();
    private int page = HOME;
    private int scroll = 0;
    private String input = "";
    private boolean inputFocus = false;
    private boolean huntersMode = false;

    public MainMenu() { super(Component.literal("Mercury Client")); }

    @Override
    public boolean shouldCloseOnEsc() { return false; }

    private void go(int p) { if (p != page) { page = p; pageAt = System.nanoTime(); scroll = 0; inputFocus = false; } }

    private float hv(String key, boolean hovered) {
        float v = hov.getOrDefault(key, 0f);
        v += ((hovered ? 1f : 0f) - v) * 0.25f;
        hov.put(key, v);
        return v;
    }

    private void open(Screen s) { Minecraft.getInstance().gui.setScreen(s); }

    // ---------- fondo animado ----------
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float pt) {
        g.fillGradient(0, 0, width, height, 0xFF15171C, 0xFF0B0C0F);
        double t = (System.nanoTime() - t0) / 1e9 * (Config.anim ? 1.0 : 0.0);
        int[][] orbs = {{0, 0, 130, 0x2A8EC5FF}, {1, 2, 110, 0x22B0B8C8}, {2, 1, 150, 0x1E6F7A92}};
        for (int i = 0; i < orbs.length; i++) {
            int cx = (int) (width * (0.5 + 0.38 * Math.sin(t * 0.21 + i * 2.1)));
            int cy = (int) (height * (0.5 + 0.34 * Math.cos(t * 0.17 + i * 1.7)));
            int R = orbs[i][2];
            // estela: copias desfasadas muy suaves = "motion blur"
            for (int k = 3; k >= 0; k--) {
                double tt = t - k * 0.06;
                int ox = (int) (width * (0.5 + 0.38 * Math.sin(tt * 0.21 + i * 2.1)));
                int oy = (int) (height * (0.5 + 0.34 * Math.cos(tt * 0.17 + i * 1.7)));
                for (int l = 0; l < 4; l++) Gfx.disc(g, ox, oy, R - l * (R / 5), Gfx.a(orbs[i][3], (k == 0 ? 0.55f : 0.14f)));
            }
        }
        g.fillGradient(0, 0, width, height, 0x60000000, 0x18000000);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float dt) {
        super.extractRenderState(g, mx, my, dt);
        hits.clear();
        float intro = Skin.openProgress(t0, 650f);

        // ---- barra lateral ----
        int sx = 16 + (int) ((1f - intro) * -40), sy = 16;
        int sw = Math.min(150, width / 3), sh = height - 32;
        for (int k = 2; k >= 1 && intro < 1f; k--)
            Gfx.rrect(g, sx - k * 6, sy, sx + sw - k * 6, sy + sh, 9, Gfx.a(0xFFFFFFFF, 0.04f * (1f - intro)));
        Gfx.rrectBorder(g, sx, sy, sx + sw, sy + sh, 9, Gfx.a(Gfx.PANEL, intro), Gfx.a(0x30FFFFFF, intro));
        // logo + nombre
        g.blit(RenderPipelines.GUI_TEXTURED, LOGO, sx + (sw - 32) / 2, sy + 10, 0f, 0f, 32, 32, 64, 64);
        String name = "MERCURY";
        g.text(font, Gfx.bold(name), sx + (sw - font.width(name)) / 2, sy + 46, Gfx.a(Gfx.TEXT, intro), true);
        String sub = "CLIENT";
        g.text(font, sub, sx + (sw - font.width(sub)) / 2, sy + 57, Gfx.a(Gfx.ACC, intro), false);

        int ny = sy + 74, nh = Math.min(24, (sh - 74 - 34) / NAV.length);
        for (int i = 0; i < NAV.length; i++) {
            int x1 = sx + 8, x2 = sx + sw - 8, y1 = ny + i * (nh + 2), y2 = y1 + nh;
            boolean h = Gfx.in(mx, my, x1, y1, x2, y2);
            float hvv = hv("nav" + i, h);
            boolean act = i == page;
            if (act) Gfx.rrect(g, x1, y1, x2, y2, 5, Gfx.a(0x34FFFFFF, intro));
            else if (hvv > 0.02f) Gfx.rrect(g, x1, y1, x2, y2, 5, Gfx.a(0x1CFFFFFF, intro * hvv));
            if (act) g.fill(x1, y1 + 4, x1 + 2, y2 - 4, Gfx.a(Gfx.ACC, intro));
            g.text(font, NAV[i], x1 + 10 + (int) (hvv * 3), y1 + (nh - 8) / 2, Gfx.a(act ? Gfx.TEXT : Gfx.lerp(Gfx.DIM, Gfx.TEXT, hvv), intro), true);
            final int idx = i;
            hits.add(new Hit(x1, y1, x2, y2, () -> { if (idx == MODULES) open(new MercuryMenu(this)); else go(idx); }, () -> {}));
        }
        // salir
        int qx1 = sx + 8, qx2 = sx + sw - 8, qy1 = sy + sh - 28, qy2 = qy1 + 20;
        float qh = hv("quit", Gfx.in(mx, my, qx1, qy1, qx2, qy2));
        Gfx.rrect(g, qx1, qy1, qx2, qy2, 5, Gfx.a(Gfx.lerp(0x28FF5A5A, 0x55FF5A5A, qh), intro));
        g.text(font, "Salir del juego", qx1 + (qx2 - qx1 - font.width("Salir del juego")) / 2, qy1 + 6, Gfx.a(0xFFFFB4B4, intro), true);
        hits.add(new Hit(qx1, qy1, qx2, qy2, () -> Minecraft.getInstance().stop(), () -> {}));

        // ---- contenido ----
        int cx = sx + sw + 14, cy = 16, cw = width - cx - 16, ch = height - 32;
        float pp = Skin.openProgress(pageAt, 340f);
        int ox = (int) ((1f - pp) * 30);
        float a = Math.min(intro, 0.2f + 0.8f * pp);
        for (int k = 3; k >= 1 && pp < 1f; k--)
            Gfx.rrect(g, cx + ox + k * 9, cy, cx + cw + ox + k * 9, cy + ch, 9, Gfx.a(0xFFFFFFFF, 0.035f * (1f - pp)));
        Gfx.rrectBorder(g, cx + ox, cy, cx + cw + ox, cy + ch, 9, Gfx.a(0xA0181A20, a), Gfx.a(0x28FFFFFF, a));

        int ix = cx + ox + 14, iw = cw - 28;
        switch (page) {
            case HOME -> home(g, mx, my, cx + ox, cy, cw, ch, a);
            case WORLDS -> hero(g, mx, my, ix, cy + 14, iw, "Mundos", "Un jugador: crea, abre y administra tus mundos.", "Abrir mundos", () -> open(new SelectWorldScreen(this)), a);
            case MULTI -> hero(g, mx, my, ix, cy + 14, iw, "Multijugador", "Tu lista de servidores, conexion directa y mas.", "Abrir lista de servidores", () -> open(new JoinMultiplayerScreen(this)), a);
            case FRIENDS -> friends(g, mx, my, ix, cy + 14, iw, ch - 28, a);
            case MODS -> mods(g, mx, my, ix, cy + 14, iw, ch - 28, a);
            case CONFIG -> config(g, mx, my, ix, cy + 14, iw, ch - 28, a);
            default -> {}
        }
        String ver = "Mercury Client 1.0.0  |  Minecraft 26.2";
        g.text(font, ver, cx + cw - font.width(ver) - 4, height - 12, Gfx.a(0xFF5E6472, intro), false);
    }

    // ---------- paginas ----------
    private void home(GuiGraphicsExtractor g, int mx, int my, int cx, int cy, int cw, int ch, float a) {
        int midX = cx + cw / 2;
        g.blit(RenderPipelines.GUI_TEXTURED, LOGO, midX - 32, cy + ch / 5, 0f, 0f, 64, 64, 64, 64);
        String t = "MERCURY CLIENT";
        g.text(font, Gfx.bold(t), midX - font.width(t) / 2, cy + ch / 5 + 74, Gfx.a(Gfx.TEXT, a), true);
        String s = "Limpio. Rapido. Tuyo.";
        g.text(font, s, midX - font.width(s) / 2, cy + ch / 5 + 87, Gfx.a(Gfx.DIM, a), false);
        int bw = Math.min(150, cw / 2 - 24), bh = 42, by = cy + ch / 5 + 108;
        card(g, mx, my, midX - bw - 6, by, bw, bh, "Un jugador", "Mundos", () -> open(new SelectWorldScreen(this)), a);
        card(g, mx, my, midX + 6, by, bw, bh, "Multijugador", "Servidores", () -> open(new JoinMultiplayerScreen(this)), a);
    }

    private void card(GuiGraphicsExtractor g, int mx, int my, int x, int y, int w, int h, String title, String sub, Runnable r, float a) {
        float v = hv("card" + title, Gfx.in(mx, my, x, y, x + w, y + h));
        Gfx.rrect(g, x - 1, y - 1, x + w + 1, y + h + 1, 8, Gfx.a(Gfx.ACC, 0.35f * v * a));
        Gfx.rrectBorder(g, x, y, x + w, y + h, 7, Gfx.a(Gfx.lerp(0x40FFFFFF, 0x60FFFFFF, v), a), Gfx.a(0x30FFFFFF, a));
        g.text(font, Gfx.bold(title), x + 12, y + 10, Gfx.a(Gfx.TEXT, a), true);
        g.text(font, sub, x + 12, y + 24, Gfx.a(Gfx.lerp(Gfx.DIM, Gfx.ACC, v), a), false);
        hits.add(new Hit(x, y, x + w, y + h, r, () -> {}));
    }

    private void hero(GuiGraphicsExtractor g, int mx, int my, int x, int y, int w, String title, String desc, String button, Runnable r, float a) {
        g.text(font, Gfx.bold(title), x, y, Gfx.a(Gfx.TEXT, a), true);
        g.text(font, desc, x, y + 14, Gfx.a(Gfx.DIM, a), false);
        int bw = Math.min(w, 220);
        card(g, mx, my, x, y + 36, bw, 42, button, "Click para abrir", r, a);
        String note = "El fondo y la barra de Mercury se aplican tambien a esa pantalla.";
        g.text(font, note, x, y + 90, Gfx.a(0xFF6F7583, a), false);
    }

    private void friends(GuiGraphicsExtractor g, int mx, int my, int x, int y, int w, int h, float a) {
        g.text(font, Gfx.bold(huntersMode ? "Cazadores" : "Amigos"), x, y, Gfx.a(Gfx.TEXT, a), true);
        g.text(font, huntersMode ? "Nombres a vigilar con el Detector de cazadores (espectador / invisible)." : "Los amigos no reciben golpes de Combat Zylow.", x, y + 14, Gfx.a(Gfx.DIM, a), false);
        for (int k = 0; k < 2; k++) {
            String lb = k == 0 ? "Amigos" : "Cazadores";
            int tx = x + w - 130 + k * 66, tw = 62;
            boolean act = (k == 1) == huntersMode;
            Gfx.rrect(g, tx, y - 2, tx + tw, y + 12, 4, Gfx.a(act ? 0x50FFFFFF : 0x20FFFFFF, a));
            g.text(font, lb, tx + (tw - font.width(lb)) / 2, y + 1, Gfx.a(act ? Gfx.TEXT : Gfx.DIM, a), true);
            final boolean toHunters = k == 1;
            hits.add(new Hit(tx, y - 2, tx + tw, y + 12, () -> { huntersMode = toHunters; scroll = 0; }, () -> {}));
        }
        // caja de texto
        int iy = y + 30, iw = Math.min(w - 70, 220);
        boolean f = inputFocus;
        Gfx.rrectBorder(g, x, iy, x + iw, iy + 16, 4, Gfx.a(0x30000000, a), Gfx.a(f ? Gfx.ACC : 0x40FFFFFF, a));
        boolean blink = (System.nanoTime() / 500_000_000L) % 2 == 0;
        String shown = input.isEmpty() && !f ? "Nombre del jugador..." : input + (f && blink ? "_" : "");
        g.text(font, shown, x + 6, iy + 4, Gfx.a(input.isEmpty() && !f ? 0xFF5E6472 : Gfx.TEXT, a), false);
        hits.add(new Hit(x, iy, x + iw, iy + 16, () -> inputFocus = true, () -> inputFocus = true));
        int bx = x + iw + 6;
        float bv = hv("addfriend", Gfx.in(mx, my, bx, iy, bx + 56, iy + 16));
        Gfx.rrect(g, bx, iy, bx + 56, iy + 16, 4, Gfx.a(Gfx.lerp(0x40FFFFFF, 0x70FFFFFF, bv), a));
        g.text(font, "Anadir", bx + 9, iy + 4, Gfx.a(Gfx.TEXT, a), true);
        hits.add(new Hit(bx, iy, bx + 56, iy + 16, this::addFriend, () -> {}));

        var online = new java.util.HashSet<String>();
        var mc = Minecraft.getInstance();
        if (mc.level != null) for (var pl : mc.level.players()) online.add(pl.getName().getString().toLowerCase(Locale.ROOT));

        int ly = iy + 26, rows = Math.max(1, (y + h - ly) / 18);
        List<String> list = new ArrayList<>(huntersMode ? Config.hunters : Config.friends);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, list.size() - rows)));
        if (list.isEmpty()) g.text(font, huntersMode ? "Sin cazadores: el detector vigila a todos." : "Aun no tienes amigos agregados.", x, ly + 4, Gfx.a(0xFF6F7583, a), false);
        for (int i = scroll; i < Math.min(list.size(), scroll + rows); i++) {
            String n = list.get(i);
            int ry = ly + (i - scroll) * 18;
            Gfx.rrect(g, x, ry, x + w, ry + 16, 4, Gfx.a(0x24FFFFFF, a));
            boolean on = online.contains(n);
            Gfx.rrect(g, x + 6, ry + 5, x + 12, ry + 11, 3, Gfx.a(on ? 0xFF4CE06A : 0xFF555A66, a));
            g.text(font, n, x + 18, ry + 4, Gfx.a(Gfx.TEXT, a), true);
            String st = on ? "cerca" : "";
            g.text(font, st, x + w - 30 - font.width(st), ry + 4, Gfx.a(0xFF4CE06A, a), false);
            boolean xh = Gfx.in(mx, my, x + w - 20, ry, x + w - 4, ry + 16);
            g.text(font, "x", x + w - 14, ry + 4, Gfx.a(xh ? 0xFFFF7A7A : Gfx.DIM, a), true);
            final String nn = n;
            hits.add(new Hit(x + w - 20, ry, x + w - 4, ry + 16, () -> { (huntersMode ? Config.hunters : Config.friends).remove(nn); Config.save(); }, () -> {}));
        }
    }

    private void addFriend() {
        String n = input.trim().toLowerCase(Locale.ROOT);
        if (!n.isEmpty()) { (huntersMode ? Config.hunters : Config.friends).add(n); Config.save(); }
        input = "";
    }

    private void mods(GuiGraphicsExtractor g, int mx, int my, int x, int y, int w, int h, float a) {
        List<ModContainer> list = new ArrayList<>();
        for (ModContainer m : FabricLoader.getInstance().getAllMods()) {
            String id = m.getMetadata().getId();
            if (id.startsWith("fabric-") || id.equals("java") || id.startsWith("mixinextras") || id.startsWith("fabricloader")) continue;
            list.add(m);
        }
        list.sort((p, q) -> p.getMetadata().getName().compareToIgnoreCase(q.getMetadata().getName()));
        g.text(font, Gfx.bold("Mods"), x, y, Gfx.a(Gfx.TEXT, a), true);
        g.text(font, list.size() + " instalados. Rueda del mouse para bajar.", x, y + 14, Gfx.a(Gfx.DIM, a), false);
        int ly = y + 30, rows = Math.max(1, (y + h - ly) / 20);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, list.size() - rows)));
        for (int i = scroll; i < Math.min(list.size(), scroll + rows); i++) {
            ModContainer m = list.get(i);
            int ry = ly + (i - scroll) * 20;
            Gfx.rrect(g, x, ry, x + w, ry + 18, 4, Gfx.a(0x24FFFFFF, a));
            g.fill(x + 4, ry + 4, x + 6, ry + 14, Gfx.a(Gfx.ACC, a));
            g.text(font, m.getMetadata().getName(), x + 12, ry + 5, Gfx.a(Gfx.TEXT, a), true);
            String v = m.getMetadata().getVersion().getFriendlyString();
            g.text(font, v, x + w - 8 - font.width(v), ry + 5, Gfx.a(Gfx.DIM, a), false);
        }
    }

    private void config(GuiGraphicsExtractor g, int mx, int my, int x, int y, int w, int h, float a) {
        g.text(font, Gfx.bold("Configuracion"), x, y, Gfx.a(Gfx.TEXT, a), true);
        g.text(font, "Los modulos (PvP y Visuals) se configuran en la pestania Modulos.", x, y + 14, Gfx.a(Gfx.DIM, a), false);
        int cw = Math.min(w, 320), ry = y + 32;
        for (Setting s : ModuleManager.general()) {
            boolean hovr = Gfx.in(mx, my, x, ry, x + cw, ry + 17);
            if (hovr) Gfx.rrect(g, x, ry, x + cw, ry + 17, 4, Gfx.a(0x18FFFFFF, a));
            g.text(font, s.label, x + 4, ry + 5, Gfx.a(0xFFB4BAC6, a), false);
            Gfx.toggle(g, x + cw - 28, ry + 3, s.boolGet.getAsBoolean() ? 1f : 0f, a);
            hits.add(new Hit(x, ry, x + cw, ry + 17, s::inc, s::inc));
            ry += 19;
        }
        int by = ry + 8;
        card(g, mx, my, x, by, Math.min(cw, 180), 36, "Ajustes de Minecraft", "Video, sonido, controles...", () -> open(new OptionsScreen(this, Minecraft.getInstance().options, false)), a);
    }

    // ---------- input ----------
    @Override
    public boolean mouseClicked(MouseButtonEvent ev, boolean dbl) {
        int mx = (int) ev.x(), my = (int) ev.y();
        inputFocus = false;
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (Gfx.in(mx, my, h.x1(), h.y1(), h.x2(), h.y2())) {
                if (ev.button() == 0) h.left().run(); else if (ev.button() == 1) h.right().run();
                return true;
            }
        }
        return super.mouseClicked(ev, dbl);
    }

    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        scroll = Math.max(0, scroll - (int) Math.signum(sy));
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent ev) {
        if (page == FRIENDS && inputFocus) {
            int k = ev.key();
            long w = Minecraft.getInstance().getWindow().handle();
            boolean shift = GLFW.glfwGetKey(w, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS || GLFW.glfwGetKey(w, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
            if (k == GLFW.GLFW_KEY_BACKSPACE) { if (!input.isEmpty()) input = input.substring(0, input.length() - 1); }
            else if (k == GLFW.GLFW_KEY_ENTER || k == GLFW.GLFW_KEY_KP_ENTER) addFriend();
            else if (k == GLFW.GLFW_KEY_ESCAPE) inputFocus = false;
            else if (input.length() < 16) {
                if (k >= GLFW.GLFW_KEY_A && k <= GLFW.GLFW_KEY_Z) input += (char) (shift ? k : k + 32);
                else if (k >= GLFW.GLFW_KEY_0 && k <= GLFW.GLFW_KEY_9) input += (char) k;
                else if (k == GLFW.GLFW_KEY_MINUS && shift) input += '_';
            }
            return true;
        }
        if (ev.key() == Config.menuKey) { open(new MercuryMenu(this)); return true; }
        return super.keyPressed(ev);
    }
}

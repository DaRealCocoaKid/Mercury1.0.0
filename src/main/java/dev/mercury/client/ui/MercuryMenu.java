package dev.mercury.client.ui;

import dev.mercury.client.Config;
import dev.mercury.client.module.Module;
import dev.mercury.client.module.ModuleManager;
import dev.mercury.client.module.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** Menu del cliente: panel gris traslucido con pestanias Cliente / Visuals / Configuracion. */
public class MercuryMenu extends Screen {
    private record Hit(int x1, int y1, int x2, int y2, Runnable left, Runnable right) {}

    private static final String[] TABS = {"Cliente", "PvP", "Visuals", "Configuracion"};
    private static int tab = 0;
    private static final Module[] selected = new Module[Module.Cat.values().length];

    private final Screen parent;
    private final List<Hit> hits = new ArrayList<>();
    private final long opened = System.nanoTime();
    private long tabAt = System.nanoTime();
    private float indX = -1;
    // quien esta esperando una tecla: un Module, o "menu"
    private Object listening = null;

    public MercuryMenu(Screen parent) {
        super(Component.literal("Mercury Client"));
        this.parent = parent;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private void setTab(int t) { if (t != tab) { tab = t; tabAt = System.nanoTime(); listening = null; } }

    // fondo: vanilla (desenfoque del mundo) + gris traslucido
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float pt) {
        if (minecraft != null && minecraft.level != null) super.extractBackground(g, mx, my, pt);
        float p = Skin.openProgress(opened, 380f);
        if (minecraft == null || minecraft.level == null) {
            g.fillGradient(0, 0, width, height, 0xFF14161B, 0xFF0C0D10);
        }
        g.fillGradient(0, 0, width, height, Gfx.a(0xB0111317, p), Gfx.a(0xC8181A20, p));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float dt) {
        super.extractRenderState(g, mx, my, dt);
        hits.clear();
        float open = Skin.openProgress(opened, 380f);
        int pw = Math.min(width - 16, 470), ph = Math.min(height - 16, 268);
        int x0 = (width - pw) / 2;
        int oy = (int) ((1f - open) * 20);
        int y0 = (height - ph) / 2 + oy;

        // estela (desenfoque de movimiento) mientras se abre
        if (open < 1f) {
            for (int k = 3; k >= 1; k--) {
                int off = (int) ((1f - open) * 7 * k);
                Gfx.rrect(g, x0, y0 + off, x0 + pw, y0 + ph + off, 9, Gfx.a(0xFFFFFFFF, 0.05f * (1f - open)));
            }
        }
        // sombra + panel
        Gfx.rrect(g, x0 - 2, y0 - 2, x0 + pw + 2, y0 + ph + 2, 11, Gfx.a(0x40000000, open));
        Gfx.rrectBorder(g, x0, y0, x0 + pw, y0 + ph, 9, Gfx.a(Gfx.PANEL, open), Gfx.a(0x30FFFFFF, open));

        // titulo
        String title = "MERCURY CLIENT";
        g.text(font, Gfx.bold(title), (width - font.width(title)) / 2, y0 + 8, Gfx.a(Gfx.TEXT, open), true);
        String sub = "26.2  |  PvP + Visuals";
        g.text(font, sub, (width - font.width(sub)) / 2, y0 + 19, Gfx.a(Gfx.DIM, open), false);

        // pestanias
        int tx = x0 + 12, ty = y0 + 32, th = 16;
        int cx = tx;
        for (int i = 0; i < TABS.length; i++) {
            int tw = font.width(TABS[i]) + 18;
            boolean act = i == tab;
            boolean hov = Gfx.in(mx, my, cx, ty, cx + tw, ty + th);
            if (act || hov) Gfx.rrect(g, cx, ty, cx + tw, ty + th, 5, act ? 0x30FFFFFF : 0x18FFFFFF);
            g.text(font, TABS[i], cx + 9, ty + 4, act ? Gfx.ACC : Gfx.DIM, true);
            if (act) {
                if (indX < 0) indX = cx;
                indX += (cx - indX) * 0.3f;
                Gfx.rrect(g, (int) indX + 6, ty + th, (int) indX + tw - 6, ty + th + 2, 1, Gfx.ACC);
            }
            final int idx = i;
            hits.add(new Hit(cx, ty, cx + tw, ty + th, () -> setTab(idx), () -> {}));
            cx += tw + 4;
        }

        int bx = x0 + 12, by = ty + th + 8, bw = pw - 24, bh = y0 + ph - 20 - by;
        // transicion de pestania: desliza + se aclara (con estela)
        float tp = Skin.openProgress(tabAt, 260f);
        int ox = (int) ((1f - tp) * 22);
        float a = Math.min(open, 0.25f + 0.75f * tp);
        if (tp < 1f) {
            for (int k = 1; k <= 2; k++)
                Gfx.rrect(g, bx + ox + k * 8, by, bx + bw + ox + k * 8, by + bh, 6, Gfx.a(0xFFFFFFFF, 0.04f * (1f - tp)));
        }

        if (tab == 3) drawConfig(g, mx, my, bx + ox, by, bw, bh, a);
        else drawModules(g, mx, my, bx + ox, by, bw, bh, a, tab == 0 ? Module.Cat.CLIENTE : tab == 1 ? Module.Cat.PVP : Module.Cat.VISUALS);

        String hint = "Click: activar / cambiar    Click derecho: -    Tecla del menu: " + Gfx.keyName(Config.menuKey);
        g.text(font, hint, (width - font.width(hint)) / 2, y0 + ph - 13, Gfx.a(0xFF6F7583, open), false);
    }

    // ---------------- Cliente / Visuals ----------------
    private void drawModules(GuiGraphicsExtractor g, int mx, int my, int x, int y, int w, int h, float a, Module.Cat cat) {
        List<Module> mods = ModuleManager.of(cat);
        int lw = Math.min(150, w / 2 - 10);
        Gfx.rrect(g, x, y, x + lw, y + h, 6, Gfx.a(0x20FFFFFF, a));

        int ci = cat.ordinal();
        if (selected[ci] == null && !mods.isEmpty()) selected[ci] = mods.get(0);
        Module sel = selected[ci];

        int ry = y + 4, rh = Math.min(22, (h - 8) / Math.max(1, mods.size()));
        for (Module m : mods) {
            boolean hov = Gfx.in(mx, my, x + 3, ry, x + lw - 3, ry + rh - 1);
            boolean isSel = m == sel;
            m.anim += ((m.isOn() ? 1f : 0f) - m.anim) * 0.3f;
            if (isSel) { Gfx.rrect(g, x + 3, ry, x + lw - 3, ry + rh - 1, 4, Gfx.a(0x34FFFFFF, a)); g.fill(x + 3, ry + 3, x + 5, ry + rh - 4, Gfx.a(Gfx.ACC, a)); }
            else if (hov) Gfx.rrect(g, x + 3, ry, x + lw - 3, ry + rh - 1, 4, Gfx.a(0x18FFFFFF, a));
            int ty = ry + (rh - 9) / 2;
            g.text(font, m.name, x + 10, ty, Gfx.a(Gfx.lerp(0xFFA0A5B1, 0xFFFFFFFF, m.anim), a), true);
            Gfx.toggle(g, x + lw - 29, ry + (rh - 11) / 2, m.anim, a);
            final Module mm = m;
            // click en el switch = on/off; click en el resto = seleccionar
            hits.add(new Hit(x + lw - 31, ry, x + lw - 3, ry + rh - 1, mm::toggle, mm::toggle));
            hits.add(new Hit(x + 3, ry, x + lw - 31, ry + rh - 1, () -> { selected[ci] = mm; listening = null; }, () -> mm.toggle()));
            ry += rh;
        }

        if (sel == null) return;
        int dx = x + lw + 10, dw = w - lw - 10;
        Gfx.rrect(g, dx, y, dx + dw, y + h, 6, Gfx.a(0x20FFFFFF, a));
        g.text(font, Gfx.bold(sel.name), dx + 10, y + 7, Gfx.a(Gfx.TEXT, a), true);
        String d = sel.desc;
        while (font.width(d) > dw - 20 && d.length() > 4) d = d.substring(0, d.length() - 4) + "...";
        g.text(font, d, dx + 10, y + 19, Gfx.a(Gfx.DIM, a), false);

        // fila de tecla
        int ky = y + 33;
        keyRow(g, mx, my, dx + 8, ky, dw - 16, a, "Tecla", sel.key, sel);

        // opciones
        int sy = ky + 20, avail = y + h - sy - 4;
        drawSettings(g, mx, my, dx + 8, sy, dw - 16, avail, a, sel.settings);
    }

    private void keyRow(GuiGraphicsExtractor g, int mx, int my, int x, int y, int w, float a, String label, int key, Object target) {
        boolean on = listening == target;
        g.text(font, label, x + 2, y + 4, Gfx.a(Gfx.DIM, a), false);
        String s = on ? "Presiona una tecla..." : "[" + Gfx.keyName(key) + "]";
        int cw = font.width(s) + 14;
        int cx = x + w - cw;
        boolean hov = Gfx.in(mx, my, cx, y, cx + cw, y + 15);
        Gfx.rrect(g, cx, y, cx + cw, y + 15, 4, Gfx.a(on ? 0x55FFFFFF : hov ? 0x38FFFFFF : 0x26FFFFFF, a));
        g.text(font, s, cx + 7, y + 4, Gfx.a(on ? Gfx.ACC : Gfx.TEXT, a), true);
        hits.add(new Hit(cx, y, cx + cw, y + 15, () -> listening = on ? null : target, () -> {
            if (target instanceof Module m) { m.key = -1; }
            listening = null;
        }));
    }

    private void drawSettings(GuiGraphicsExtractor g, int mx, int my, int x, int y, int w, int avail, float a, List<Setting> list) {
        if (list.isEmpty()) {
            g.text(font, "Sin opciones: solo on/off y tecla.", x + 2, y + 4, Gfx.a(0xFF6F7583, a), false);
            return;
        }
        int rh = 17;
        int perCol = Math.max(1, avail / rh);
        int cols = list.size() > perCol ? 2 : 1;
        int cw = (w - (cols - 1) * 8) / cols;
        for (int i = 0; i < list.size(); i++) {
            int col = i / perCol, row = i % perCol;
            if (col >= cols) break;
            settingRow(g, mx, my, x + col * (cw + 8), y + row * rh, cw, a, list.get(i));
        }
    }

    private void settingRow(GuiGraphicsExtractor g, int mx, int my, int x, int y, int w, float a, Setting s) {
        boolean hov = Gfx.in(mx, my, x, y, x + w, y + 15);
        if (hov) Gfx.rrect(g, x, y, x + w, y + 15, 4, Gfx.a(0x18FFFFFF, a));
        String label = s.label;
        int maxLabel = w - 66;
        while (font.width(label) > maxLabel && label.length() > 4) label = label.substring(0, label.length() - 2);
        g.text(font, label, x + 4, y + 4, Gfx.a(0xFFB4BAC6, a), false);
        if (s.isBool()) {
            boolean v = s.boolGet.getAsBoolean();
            Gfx.toggle(g, x + w - 27, y + 2, v ? 1f : 0f, a);
            hits.add(new Hit(x, y, x + w, y + 15, s::inc, s::dec));
        } else {
            String v = s.display();
            int vw = font.width(v);
            int cx = x + w - 8 - vw / 2 - 18;
            g.text(font, "<", x + w - vw - 24, y + 4, Gfx.a(Gfx.DIM, a), false);
            g.text(font, v, x + w - vw - 14, y + 4, Gfx.a(Gfx.ACC, a), false);
            g.text(font, ">", x + w - 9, y + 4, Gfx.a(Gfx.DIM, a), false);
            int mid = x + w - vw / 2 - 14;
            hits.add(new Hit(x, y, mid, y + 15, s::dec, s::inc));
            hits.add(new Hit(mid, y, x + w, y + 15, s::inc, s::dec));
        }
    }

    // ---------------- Configuracion ----------------
    private void drawConfig(GuiGraphicsExtractor g, int mx, int my, int x, int y, int w, int h, float a) {
        int cw = Math.min(w, 330);
        int cx = x + (w - cw) / 2;
        Gfx.rrect(g, cx, y, cx + cw, y + h, 6, Gfx.a(0x20FFFFFF, a));
        g.text(font, Gfx.bold("Interfaz y teclas"), cx + 10, y + 7, Gfx.a(Gfx.TEXT, a), true);
        keyRow(g, mx, my, cx + 8, y + 22, cw - 16, a, "Tecla del menu", Config.menuKey, "menu");
        int ry = y + 44;
        for (Setting s : ModuleManager.general()) {
            settingRow(g, mx, my, cx + 8, ry, cw - 16, a, s);
            ry += 17;
        }
        String note = "Cada modulo tiene su propia tecla en su ficha.";
        g.text(font, note, cx + 10, y + h - 14, Gfx.a(0xFF6F7583, a), false);
    }

    // ---------------- input ----------------
    @Override
    public boolean mouseClicked(MouseButtonEvent ev, boolean dbl) {
        int mx = (int) ev.x(), my = (int) ev.y();
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (Gfx.in(mx, my, h.x1(), h.y1(), h.x2(), h.y2())) {
                if (ev.button() == 0) h.left().run();
                else if (ev.button() == 1) h.right().run();
                return true;
            }
        }
        listening = null;
        return super.mouseClicked(ev, dbl);
    }

    @Override
    public boolean keyPressed(KeyEvent ev) {
        int k = ev.key();
        if (listening != null) {
            if (k != GLFW.GLFW_KEY_ESCAPE) {
                boolean clear = k == GLFW.GLFW_KEY_BACKSPACE || k == GLFW.GLFW_KEY_DELETE;
                if (listening instanceof Module m) {
                    if (clear) m.key = -1; else if (k != Config.menuKey) m.key = k;
                } else if (!clear && !usedByModule(k)) {
                    Config.menuKey = k;
                }
            }
            listening = null;
            return true;
        }
        if (k == Config.menuKey) { onClose(); return true; }
        return super.keyPressed(ev);
    }

    private static boolean usedByModule(int k) {
        for (Module m : ModuleManager.ALL) if (m.key == k) return true;
        return false;
    }

    @Override
    public void onClose() {
        Config.save();
        Minecraft.getInstance().gui.setScreen(parent);
    }
}

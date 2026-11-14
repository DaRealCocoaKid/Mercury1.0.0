package dev.mercury.client.module;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class Module {
    public enum Cat {
        CLIENTE("Cliente"), PVP("PvP"), VISUALS("Visuals");
        public final String title;
        Cat(String t) { title = t; }
    }

    public final Cat cat;
    public final String id, name, desc;
    public int key; // -1 = sin tecla
    public final List<Setting> settings = new ArrayList<>();
    public float anim;        // animacion del switch (0..1)
    boolean keyWas = true;
    public boolean hold; // modulo que maneja su propia tecla (mantener)

    private final BooleanSupplier get;
    private final Consumer<Boolean> set;
    private boolean state;

    /** Modulo con su propio estado (se guarda en mercury-client.properties). */
    public Module(Cat cat, String id, String name, String desc, int key) { this(cat, id, name, desc, key, null, null); }

    /** Modulo cuyo estado vive en otro lado (VPConfig, Freecam, Optimizer...). */
    public Module(Cat cat, String id, String name, String desc, int key, BooleanSupplier get, Consumer<Boolean> set) {
        this.cat = cat; this.id = id; this.name = name; this.desc = desc; this.key = key; this.get = get; this.set = set;
    }

    public Module hold() { hold = true; return this; }

    public Module with(Setting... s) { settings.addAll(List.of(s)); return this; }

    public boolean isOn() { return get != null ? get.getAsBoolean() : state; }

    public void setOn(boolean v) {
        if (isOn() == v) return;
        if (set != null) set.accept(v); else state = v;
    }

    public void toggle() { setOn(!isOn()); }

    /** Toggle desde tecla: avisa en pantalla. */
    void toggleWithMessage(Minecraft mc) {
        toggle();
        if (mc.gui != null) mc.gui.hud.setOverlayMessage(Component.literal("Mercury | " + name + ": " + (isOn() ? "ON" : "OFF")), false);
    }

    void save(Properties p) {
        p.setProperty("m." + id + ".key", "" + key);
        if (get == null) p.setProperty("m." + id + ".on", "" + state);
        for (int i = 0; i < settings.size(); i++) {
            Setting s = settings.get(i);
            if (s.raw != null) p.setProperty("m." + id + ".s" + i, "" + s.raw.getAsInt());
        }
    }

    void load(Properties p) {
        try { key = Integer.parseInt(p.getProperty("m." + id + ".key", "" + key)); } catch (Exception ignored) {}
        if (get == null) state = Boolean.parseBoolean(p.getProperty("m." + id + ".on", "" + state));
        for (int i = 0; i < settings.size(); i++) {
            Setting s = settings.get(i);
            String v = p.getProperty("m." + id + ".s" + i);
            if (s.raw != null && v != null) try { s.setRaw.accept(Integer.parseInt(v)); } catch (Exception ignored) {}
        }
    }
}

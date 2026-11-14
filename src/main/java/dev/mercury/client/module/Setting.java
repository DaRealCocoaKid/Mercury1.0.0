package dev.mercury.client.module;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import java.util.function.DoubleSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/** Una opcion de un modulo: se cambia con las flechas (o un switch si es booleana). */
public final class Setting {
    public final String label;
    private final Supplier<String> display;
    private final Runnable up, down;
    public final BooleanSupplier boolGet; // solo para switches
    final IntSupplier raw;                // null = no se guarda aqui (la guarda VPConfig)
    final IntConsumer setRaw;

    private Setting(String label, Supplier<String> d, Runnable up, Runnable down, BooleanSupplier b, IntSupplier raw, IntConsumer setRaw) {
        this.label = label; this.display = d; this.up = up; this.down = down; this.boolGet = b; this.raw = raw; this.setRaw = setRaw;
    }

    public String display() { return display.get(); }
    public void inc() { up.run(); }
    public void dec() { down.run(); }
    public boolean isBool() { return boolGet != null; }

    public static Setting integer(String label, IntSupplier g, IntConsumer s, int lo, int hi, int step, String suffix, boolean persist) {
        return new Setting(label, () -> g.getAsInt() + suffix,
                () -> s.accept(Math.min(hi, g.getAsInt() + step)),
                () -> s.accept(Math.max(lo, g.getAsInt() - step)),
                null, persist ? g : null, s);
    }

    public static Setting bool(String label, BooleanSupplier g, Consumer<Boolean> s, boolean persist) {
        Runnable t = () -> s.accept(!g.getAsBoolean());
        return new Setting(label, () -> g.getAsBoolean() ? "ON" : "OFF", t, t, g,
                persist ? () -> g.getAsBoolean() ? 1 : 0 : null, v -> s.accept(v != 0));
    }

    public static Setting choice(String label, String[] opts, IntSupplier g, IntConsumer s, boolean persist) {
        int n = opts.length;
        return new Setting(label, () -> opts[Math.floorMod(g.getAsInt(), n)],
                () -> s.accept((Math.floorMod(g.getAsInt(), n) + 1) % n),
                () -> s.accept((Math.floorMod(g.getAsInt(), n) + n - 1) % n),
                null, persist ? g : null, s);
    }

    public static Setting decimal(String label, DoubleSupplier g, DoubleConsumer s, double lo, double hi, double step, DoubleFunction<String> fmt) {
        return new Setting(label, () -> fmt.apply(g.getAsDouble()),
                () -> s.accept(Math.min(hi, Math.round((g.getAsDouble() + step) / step) * step)),
                () -> s.accept(Math.max(lo, Math.round((g.getAsDouble() - step) / step) * step)),
                null, null, null);
    }
}

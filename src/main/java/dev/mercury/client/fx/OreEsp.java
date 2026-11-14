package dev.mercury.client.fx;

import dev.mercury.client.module.Module;
import dev.mercury.client.module.ModuleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * X-ray / buscador de minerales: escanea los bloques cargados cerca de ti por rebanadas (poco costo por tick)
 * y dibuja cajas y lineas guia a traves de las paredes. Dos instancias: XRAY (varios minerales) y DEBRIS (Ancient Debris).
 */
public final class OreEsp {
    public static final String[] ORE_NAMES = {"Diamante", "Esmeralda", "Oro", "Hierro", "Cobre", "Redstone", "Lapis", "Carbon", "Cuarzo"};
    private static final int[] ORE_COLORS = {0x40E0FF, 0x30FF60, 0xFFD030, 0xE0B090, 0xFF8040, 0xFF3030, 0x4060FF, 0x909090, 0xFFFFFF};
    public static final int DEBRIS_COLOR = 0xFF70B8;

    public static final OreEsp XRAY = new OreEsp(false), DEBRIS = new OreEsp(true);

    public final boolean debris;
    public int range, width = 1, colorIdx = 0; // colorIdx XRAY: 0 = natural
    public boolean tracer = true, fill = true, label = true;
    public final boolean[] ores = new boolean[ORE_NAMES.length];
    public final ColorSet color = new ColorSet(10);

    private static final class Found { int x, y, z, ore; }
    private List<Found> found = new ArrayList<>(), building = new ArrayList<>();
    private final Map<Block, Integer> table = new HashMap<>();
    private int sliceX, cx, cy, cz;
    private boolean scanning;
    private final BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();

    private OreEsp(boolean debris) {
        this.debris = debris;
        range = debris ? 40 : 24;
        for (int i = 0; i < ores.length; i++) ores[i] = i <= 3; // diamante, esmeralda, oro, hierro
    }

    private Module module() { return debris ? ModuleManager.DEBRIS : ModuleManager.XRAY; }
    public boolean on() { Module m = module(); return m != null && m.isOn(); }

    private void buildTable() {
        table.clear();
        if (debris) { table.put(Blocks.ANCIENT_DEBRIS, 0); return; }
        Block[][] g = {
                {Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE}, {Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE},
                {Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE, Blocks.NETHER_GOLD_ORE}, {Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE},
                {Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE}, {Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE},
                {Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE}, {Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE}, {Blocks.NETHER_QUARTZ_ORE}};
        for (int i = 0; i < g.length; i++) if (ores[i]) for (Block b : g[i]) table.put(b, i);
    }

    public void tick(Minecraft mc) {
        if (!on() || mc.level == null || mc.player == null) { found.clear(); building.clear(); scanning = false; return; }
        if (!scanning) {
            scanning = true;
            sliceX = -range;
            cx = (int) Math.floor(mc.player.getX()); cy = (int) Math.floor(mc.player.getY()); cz = (int) Math.floor(mc.player.getZ());
            building = new ArrayList<>();
            buildTable();
        }
        int cycle = debris ? 40 : 30;
        int perTick = Math.max(1, (int) Math.ceil((2 * range + 1) / (double) cycle));
        for (int n = 0; n < perTick && sliceX <= range; n++, sliceX++) {
            int x = cx + sliceX;
            for (int dy = -range; dy <= range; dy++) {
                int y = cy + dy;
                if (y < -64 || y > 320) continue;
                for (int dz = -range; dz <= range; dz++) {
                    mp.set(x, y, cz + dz);
                    BlockState st = mc.level.getBlockState(mp);
                    if (st.isAir()) continue;
                    Integer ore = table.get(st.getBlock());
                    if (ore == null) continue;
                    Found f = new Found();
                    f.x = x; f.y = y; f.z = cz + dz; f.ore = ore;
                    building.add(f);
                }
            }
        }
        if (sliceX > range) { found = building; scanning = false; }
    }

    private int rgbOf(Found f, float t) {
        if (debris) return color.rgb(t * 0.2f);
        if (colorIdx <= 0) return ORE_COLORS[f.ore];
        color.mode = colorIdx - 1;
        return color.rgb(t * 0.2f);
    }

    public void draw(Minecraft mc, GuiGraphicsExtractor g, float pt) {
        float t = Fx.time();
        int drawn = 0;
        for (Found f : found) {
            double dx = f.x + 0.5 - Cam.ex, dy = f.y + 0.5 - Cam.ey, dz = f.z + 0.5 - Cam.ez;
            double d2 = dx * dx + dy * dy + dz * dz;
            if (d2 > (range + 4) * (range + 4)) continue;
            if (++drawn > 500) break;
            int rgb = rgbOf(f, t);
            boolean near = d2 < 18 * 18;
            if (near) Cam.box(g, f.x, f.y, f.z, f.x + 1, f.y + 1, f.z + 1, rgb, 1f, width, drawn < 80);
            if (fill) Cam.halo(g, f.x + 0.5, f.y + 0.5, f.z + 0.5, near ? 1.9 : 1.5, rgb, near ? 0.5f : 0.75f);
            if (!near && Cam.project(f.x + 0.5, f.y + 0.5, f.z + 0.5)) Spr.flare(g, Cam.sx, Cam.sy, Math.max(10, 0.9 * Cam.scale / Cam.sz * 1.6), rgb, 0.7f);
            if (tracer) Cam.tracer(g, f.x + 0.5, f.y + 0.5, f.z + 0.5, rgb, 0.8f, Math.max(1, width));
            if (debris && label && Cam.project(f.x + 0.5, f.y + 1.2, f.z + 0.5)) {
                String s = "Debris " + Math.round(Math.sqrt(d2)) + "m  (" + f.x + ", " + f.y + ", " + f.z + ")";
                g.text(mc.font, s, (int) Cam.sx - mc.font.width(s) / 2, (int) Cam.sy - 8, Col.argb(rgb, 1f), true);
            }
        }
    }
}

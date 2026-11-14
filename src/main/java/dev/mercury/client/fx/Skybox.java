package dev.mercury.client.fx;

import dev.mercury.client.module.ModuleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.Arrays;
import java.util.Random;

/**
 * Skybox suave: degradado + nubes de nebulosa difuminadas (ruido horneado), auroras en cortinas, estrellas con brillo.
 * Se pinta en columnas finas con degradados verticales (sin cuadros), con el borde del terreno recortado al pixel
 * y difuminado, solo donde hay cielo de verdad. Deja huecos suaves donde hay jugadores para que no se escondan.
 */
public final class Skybox {
    public static final String[] STYLES = {"Video (nebulosa)", "Galaxia", "Arcoiris RGB", "Aurora boreal", "Nebulosa", "Neon sunset"};
    public static final String[] QUALITY = {"Alta", "Media", "Baja", "Minima"};
    private static final int[] CELLW = {2, 3, 4, 6};
    private static final int[] SEGH = {8, 12, 16, 24};

    public static final ColorSet SKY = new ColorSet(9);   // magenta
    public static final ColorSet AUR = new ColorSet(4);   // verde
    public static int style = 0, speed = 4, opacity = 100, quality = 1, density = 600;
    public static boolean auroras = true, stars = true, meteors = true;

    // ---------- seno rapido ----------
    private static final float[] ST = new float[4096];
    static { for (int i = 0; i < 4096; i++) ST[i] = (float) Math.sin(i * Math.PI * 2 / 4096.0); }
    private static float sin(double x) { return ST[((int) Math.floor(x * 651.8986469)) & 4095]; }
    private static float cos(double x) { return sin(x + 1.5707963); }

    // ---------- ruido horneado (nubes) ----------
    private static final int MA = 512, ME = 128;
    private static float[] N1, N2;

    private static float hash(int x, int y, int z) {
        int h = x * 374761393 + y * 668265263 + z * 1274126177;
        h = (h ^ (h >>> 13)) * 1103515245;
        h ^= h >>> 16;
        return (h & 0xFFFFFF) / (float) 0x1000000;
    }
    private static float fade(float t) { return t * t * (3 - 2 * t); }
    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }
    private static float vnoise(double x, double y, double z) {
        int xi = (int) Math.floor(x), yi = (int) Math.floor(y), zi = (int) Math.floor(z);
        float fx = fade((float) (x - xi)), fy = fade((float) (y - yi)), fz = fade((float) (z - zi));
        float c00 = lerp(hash(xi, yi, zi), hash(xi + 1, yi, zi), fx), c10 = lerp(hash(xi, yi + 1, zi), hash(xi + 1, yi + 1, zi), fx);
        float c01 = lerp(hash(xi, yi, zi + 1), hash(xi + 1, yi, zi + 1), fx), c11 = lerp(hash(xi, yi + 1, zi + 1), hash(xi + 1, yi + 1, zi + 1), fx);
        return lerp(lerp(c00, c10, fy), lerp(c01, c11, fy), fz);
    }
    private static float fbm(double x, double y, double z, int oct) {
        float a = 0.5f, s = 0, n = 0, f = 1;
        for (int o = 0; o < oct; o++) { s += a * vnoise(x * f, y * f, z * f); n += a; f *= 2.03f; a *= 0.5f; }
        return s / n;
    }
    private static void bake() {
        if (N1 != null) return;
        float[] a = new float[MA * ME], b = new float[MA * ME];
        for (int ia = 0; ia < MA; ia++) {
            double az = ia / (double) MA * Math.PI * 2;
            for (int ie = 0; ie < ME; ie++) {
                double el = ie / (double) (ME - 1) * (Math.PI / 2), ce = Math.cos(el);
                double x = -Math.sin(az) * ce, y = Math.sin(el), z = Math.cos(az) * ce;
                a[ie * MA + ia] = fbm(x * 1.7 + 5.2, y * 1.7 + 1.3, z * 1.7 + 2.1, 5);
                float r = 1f - Math.abs(2f * fbm(x * 3.6 + 9.1, y * 3.6 + 4.7, z * 3.6 + 7.3, 4) - 1f);
                b[ie * MA + ia] = r * r;
            }
        }
        N2 = b; N1 = a;
    }
    private static float samp(float[] m, double az, double el) {
        double u = az / (Math.PI * 2);
        u = (u - Math.floor(u)) * MA;
        double v = Math.max(0, Math.min(ME - 1.001, el / (Math.PI / 2) * (ME - 1)));
        int u0 = (int) u, v0 = (int) v, u1 = (u0 + 1) % MA;
        float fu = (float) (u - u0), fv = (float) (v - v0);
        float p = lerp(m[v0 * MA + u0], m[v0 * MA + u1], fu), q = lerp(m[(v0 + 1) * MA + u0], m[(v0 + 1) * MA + u1], fu);
        return lerp(p, q, fv);
    }

    // ---------- cache de visibilidad del cielo (0.5 grados) ----------
    private static final int AZN = 720, ELN = 180;
    private static final byte[] VAL = new byte[AZN * ELN];
    private static final int[] STAMP = new int[AZN * ELN];
    private static int gen = 1, sinceGen, budget, warm;
    private static double ox, oy, oz;
    private static boolean hasO;
    private static Object lastLevel;
    private static final BlockPos.MutableBlockPos MP = new BlockPos.MutableBlockPos();

    // estrellas y meteoro
    private static final int MAX_STARS = 1500;
    private static final double[] SA = new double[MAX_STARS], SE = new double[MAX_STARS];
    private static final float[] SS = new float[MAX_STARS];
    private static double mAz, mEl, mDAz, mDEl;
    private static long mBorn = -10000;

    // huecos por jugadores
    private static final double[][] RECTS = new double[24][4];
    private static int nRects;

    private static final double NX = 0.55, NY = 0.30, NZ = 0.78;
    private static final double NL = Math.sqrt(NX * NX + NY * NY + NZ * NZ);

    // direccion del pixel actual
    private static double dx, dy, dz, az, el, inv;
    private static int W, H;

    static {
        Random r = new Random(1337);
        for (int i = 0; i < MAX_STARS; i++) {
            SA[i] = r.nextDouble() * Math.PI * 2;
            SE[i] = Math.asin(r.nextDouble() * 0.98 + 0.02);
            SS[i] = r.nextFloat();
        }
    }

    private Skybox() {}

    public static boolean on() { return ModuleManager.SKYBOX != null && ModuleManager.SKYBOX.isOn(); }

    public static void tick(Minecraft mc) {
        if (!on()) { warm = 0; return; }
        if (!meteors) return;
        long now = Fx.now();
        if (now - mBorn > 900 && Fx.rnd() < 0.012f) {
            mBorn = now;
            mAz = Fx.rnd() * Math.PI * 2;
            mEl = 0.5 + Fx.rnd() * 0.8;
            double a = Fx.rnd() * Math.PI * 2;
            mDAz = Math.cos(a) * 0.9;
            mDEl = -Math.abs(Math.sin(a)) * 0.5 - 0.1;
        }
    }

    // ---------------- visibilidad ----------------
    private static boolean computeSky(Minecraft mc, double azr, double elr) {
        double ce = Math.cos(elr);
        double rx = -Math.sin(azr) * ce, ry = Math.sin(elr), rz = Math.cos(azr) * ce;
        double d = 0.6;
        while (d < 192) {
            double y = oy + ry * d;
            if (y > 330) return true;
            MP.set((int) Math.floor(ox + rx * d), (int) Math.floor(y), (int) Math.floor(oz + rz * d));
            BlockState st = mc.level.getBlockState(MP);
            if (!st.isAir() && (!st.getFluidState().isEmpty() || !st.getCollisionShape(mc.level, MP).isEmpty())) return false;
            d += Math.max(0.9, d * 0.08);
        }
        return true;
    }

    private static boolean visible(Minecraft mc, double azr, double elr) {
        int ai = Math.floorMod((int) Math.floor(Math.toDegrees(azr) * 2), AZN);
        int ei = Math.max(0, Math.min(ELN - 1, (int) Math.floor(Math.toDegrees(elr) * 2)));
        int idx = ei * AZN + ai;
        if (STAMP[idx] != gen && budget > 0) {
            budget--;
            VAL[idx] = computeSky(mc, Math.toRadians((ai + 0.5) / 2), Math.toRadians((ei + 0.5) / 2)) ? (byte) 1 : (byte) 2;
            STAMP[idx] = gen;
        }
        return VAL[idx] == 1;
    }

    private static boolean inRect(double x, double y) {
        for (int i = 0; i < nRects; i++) {
            double[] r = RECTS[i];
            if (x >= r[0] && x <= r[2] && y >= r[1] && y <= r[3]) return true;
        }
        return false;
    }

    private static void collectRects(Minecraft mc) {
        nRects = 0;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof Player) || e == mc.player || nRects >= RECTS.length) continue;
            double ex = e.getX() - Cam.ex, ey = e.getY() - Cam.ey, ez = e.getZ() - Cam.ez;
            if (ex * ex + ey * ey + ez * ez > 110 * 110) continue;
            AABB bb = e.getBoundingBox();
            double minX = 1e9, minY = 1e9, maxX = -1e9, maxY = -1e9;
            boolean ok = true;
            for (int i = 0; i < 8; i++) {
                if (!Cam.project((i & 1) == 0 ? bb.minX : bb.maxX, (i & 2) == 0 ? bb.minY : bb.maxY, (i & 4) == 0 ? bb.minZ : bb.maxZ)) { ok = false; break; }
                minX = Math.min(minX, Cam.sx); maxX = Math.max(maxX, Cam.sx);
                minY = Math.min(minY, Cam.sy); maxY = Math.max(maxY, Cam.sy);
            }
            if (!ok) continue;
            double[] r = RECTS[nRects++];
            r[0] = minX - 3; r[1] = minY - 3; r[2] = maxX + 3; r[3] = maxY + 3;
        }
    }

    // ---------------- colores ----------------
    private static float sm(float x) { x = Math.max(0f, Math.min(1f, x)); return x * x * (3 - 2 * x); }

    private static int base(float t) {
        float e = (float) (el / (Math.PI / 2));
        float sp = speed * 0.25f;
        int c;
        switch (style) {
            case 0 -> {
                int sky = SKY.rgb((float) (az / (Math.PI * 2)) * 0.5f + t * 0.03f);
                int horizon = Col.lerp(sky, 0xFFFFFF, 0.30f), top = Col.scale(sky, 0.42f);
                c = e < 0.45f ? Col.lerp(horizon, sky, sm(e / 0.45f)) : Col.lerp(sky, top, sm((e - 0.45f) / 0.55f));
                float n1 = samp(N1, az + t * 0.004 * sp, el), n2 = samp(N2, az - t * 0.007 * sp + 1.7, el);
                // nubes oscuras suaves + filamentos claros difuminados
                c = Col.scale(c, 0.42f + 0.58f * sm((n1 - 0.30f) / 0.40f));
                c = Col.add(c, Col.scale(Col.lerp(sky, 0xFFFFFF, 0.55f), n2 * n2 * 0.30f * (0.4f + 0.6f * (1f - e))));
                // variacion sutil de matiz con la nube
                c = Col.lerp(c, Col.lerp(c, Col.scale(0xFF60D0, 0.5f), 0.4f), sm((n1 - 0.55f) / 0.3f) * 0.35f);
            }
            case 2 -> c = Col.hsv((float) (az / (Math.PI * 2)) + e * 0.35f + t * 0.06f * sp, 0.85f, 0.55f + 0.4f * (1f - e));
            case 3 -> c = Col.lerp(0x08301F, 0x02040E, (float) Math.pow(e, 0.6));
            case 4 -> c = Col.lerp(0x3A0F5E, 0x04041A, (float) Math.pow(e, 0.55));
            case 5 -> c = e < 0.35f ? Col.lerp(0xFF6A3A, 0xC2308A, e / 0.35f) : Col.lerp(0xC2308A, 0x140A4A, (e - 0.35f) / 0.65f);
            default -> c = Col.lerp(0x2A1650, 0x04051A, (float) Math.pow(e, 0.5));
        }
        if (style == 1 || style == 4) {
            float n1 = samp(N1, az + t * 0.003 * sp, el), n2 = samp(N2, az - t * 0.005 * sp, el);
            double d = (dx * NX + dy * NY + dz * NZ) / NL;
            float band = 1f / (1f + (float) (d * d) / 0.012f);
            band *= band;
            c = Col.add(c, Col.scale(Col.lerp(0x9E7BFF, 0xFFD0F0, n2), band * (0.25f + 0.75f * n1)));
            c = Col.add(c, Col.scale(style == 4 ? 0x40E0FF : 0xFF4FB8, sm((n1 - 0.5f) / 0.4f) * 0.25f));
        }
        return c;
    }

    private static int aurora(float t) {
        float sp = speed * 0.25f;
        float rr = 0, gg = 0, bb = 0;
        if (el < 0.03 || el > 1.35) return 0;
        for (int k = 0; k < 3; k++) {
            double h = 0.40 + 0.20 * sin(az * (2 + k) + t * 0.30 * sp + k * 2.1) + 0.07 * sin(az * 7 - t * 0.7 * sp + k);
            double u = el - h;
            if (u < -0.08 || u > 0.65) continue;
            double prof = u < 0 ? 1.0 / (1 + (u * u) / 0.0006) : 1.0 / (1 + u / 0.10 + (u * u) / 0.05);
            double shim = 0.82 + 0.18 * sin(az * 26 + k * 3 + t * 0.9 * sp + sin(az * 4 + t * 0.35) * 2.5);
            double win = 0.5 + 0.5 * sin(az + k * 2 + t * 0.10 * sp);
            float I = (float) (prof * shim * win * win * (0.85 - 0.15 * k));
            if (I < 0.01f) continue;
            float uu = (float) Math.max(0, u);
            int c = Col.lerp(AUR.rgb((float) (az / (Math.PI * 2)) + t * 0.04f * sp + uu), 0xFFFFFF, Math.min(0.45f, uu * 0.9f));
            rr += ((c >> 16) & 255) * I; gg += ((c >> 8) & 255) * I; bb += (c & 255) * I;
        }
        return (Math.min(255, (int) rr) << 16) | (Math.min(255, (int) gg) << 8) | Math.min(255, (int) bb);
    }

    private static void dir(double px, double py) {
        double xc = (px - W / 2.0) * inv, yc = (H / 2.0 - py) * inv;
        dx = Cam.fwd[0] + Cam.right[0] * xc + Cam.up[0] * yc;
        dy = Cam.fwd[1] + Cam.right[1] * xc + Cam.up[1] * yc;
        dz = Cam.fwd[2] + Cam.right[2] * xc + Cam.up[2] * yc;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        dx /= len; dy /= len; dz /= len;
        el = Math.asin(Math.max(-1, Math.min(1, dy)));
        az = Math.atan2(-dx, dz);
        if (az < 0) az += Math.PI * 2;
    }

    private static boolean skyAt(Minecraft mc, double px, int py) {
        if (py < 0 || py >= H) return false;
        dir(px, py + 0.5);
        if (dy < -0.01 || inRect(px, py)) return false;
        return visible(mc, az, el);
    }

    private static int colorAt(double px, double py, float t) {
        dir(px, py);
        int c = base(t);
        return auroras ? Col.add(c, aurora(t)) : c;
    }

    // ---------------- dibujo ----------------
    private static final int[] YS = new int[256];

    private static void emit(GuiGraphicsExtractor g, double px, int x0, int cw, int ya, int yb, float alpha, float t, int sh) {
        if (yb - ya < 2) return;
        final int fade = 7;
        boolean fTop = ya > 0, fBot = yb < H;
        int n = 0;
        YS[n++] = ya;
        if (fTop && ya + fade < yb - 2) YS[n++] = ya + fade;
        for (int y = ya + sh; y < yb - 1 && n < YS.length - 3; y += sh) {
            if (fTop && Math.abs(y - (ya + fade)) < 3) continue;
            if (fBot && Math.abs(y - (yb - fade)) < 3) continue;
            YS[n++] = y;
        }
        if (fBot && yb - fade > ya + 2) YS[n++] = yb - fade;
        YS[n++] = yb;
        Arrays.sort(YS, 0, n);
        int prevY = YS[0], prevC = colorAt(px, prevY, t);
        float pa = fTop ? 0f : 1f;
        for (int i = 1; i < n; i++) {
            int y = YS[i];
            if (y <= prevY) continue;
            int c = colorAt(px, Math.min(y, H - 1), t);
            float a = 1f;
            if (fTop) a = Math.min(a, Math.max(0f, (y - ya) / (float) fade));
            if (fBot) a = Math.min(a, Math.max(0f, (yb - y) / (float) fade));
            g.fillGradient(x0, prevY, x0 + cw, y, Col.argb(prevC, alpha * pa), Col.argb(c, alpha * a));
            prevY = y; prevC = c; pa = a;
        }
    }

    public static void draw(Minecraft mc, GuiGraphicsExtractor g, float pt) {
        if (mc.level == null) return;
        bake();
        if (lastLevel != mc.level) { lastLevel = mc.level; Arrays.fill(VAL, (byte) 0); Arrays.fill(STAMP, 0); hasO = false; warm = 0; }
        double ddx = Cam.ex - ox, ddy = Cam.ey - oy, ddz = Cam.ez - oz;
        if (!hasO || ddx * ddx + ddy * ddy + ddz * ddz > 9 || ++sinceGen > 240) {
            gen++; sinceGen = 0; ox = Cam.ex; oy = Cam.ey; oz = Cam.ez; hasO = true;
        }
        budget = warm++ < 40 ? 3500 : 1400;
        collectRects(mc);

        W = Cam.W; H = Cam.H;
        inv = 1.0 / Cam.scale;
        float t = Fx.time();
        float alpha = Math.max(0.1f, Math.min(1f, opacity / 100f));
        int cw = CELLW[Math.floorMod(quality, CELLW.length)], sh = SEGH[Math.floorMod(quality, SEGH.length)];
        int step = 5;

        for (int x0 = 0; x0 < W; x0 += cw) {
            double px = x0 + cw * 0.5;
            boolean prev = false;
            int start = 0;
            for (int y = 0; y <= H; y += step) {
                boolean s = skyAt(mc, px, y);
                if (s != prev) {
                    if (y == 0) { start = 0; }
                    else {
                        int lo = y - step, hi = y;           // estado(hi) == s, estado(lo) != s
                        while (hi - lo > 1) {
                            int mid = (lo + hi) >>> 1;
                            if (skyAt(mc, px, mid) == s) hi = mid; else lo = mid;
                        }
                        if (s) start = hi; else emit(g, px, x0, Math.min(cw, W - x0), start, hi, alpha, t, sh);
                    }
                    prev = s;
                }
            }
        }

        if (stars) {
            int n = Math.min(MAX_STARS, density);
            for (int i = 0; i < n; i++) {
                double sa = SA[i], se = SE[i], ce = Math.cos(se);
                if (!Cam.project(Cam.ex - Math.sin(sa) * ce * 100, Cam.ey + Math.sin(se) * 100, Cam.ez + Math.cos(sa) * ce * 100)) continue;
                double sx = Cam.sx, sy = Cam.sy;
                if (sx < 0 || sy < 0 || sx >= W || sy >= H || inRect(sx, sy) || !visible(mc, sa, se)) continue;
                float tw = 0.55f + 0.45f * sin(t * (1.5 + SS[i] * 3) + SS[i] * 40);
                int col = SS[i] < 0.3f ? 0xB8D4FF : SS[i] > 0.8f ? 0xFFE0B8 : 0xFFFFFF;
                float size = 3.5f + SS[i] * 4.5f;
                Spr.glow(g, sx, sy, size * 1.6, col, tw * 0.55f * alpha);
                if (SS[i] > 0.72f) Spr.flare(g, sx, sy, size * 3.2, col, tw * 0.7f * alpha);
                else Spr.glow(g, sx, sy, size * 0.7, 0xFFFFFF, tw * alpha);
            }
        }

        if (meteors) {
            float age = (Fx.now() - mBorn) / 800f;
            if (age >= 0 && age < 1f) {
                double hx = 0, hy = 0;
                boolean have = false;
                for (int k = 0; k < 10; k++) {
                    float a = age - k * 0.02f;
                    if (a < 0) break;
                    double maz = mAz + mDAz * a, mel = mEl + mDEl * a;
                    if (mel <= 0.02) break;
                    double ce = Math.cos(mel);
                    if (!Cam.project(Cam.ex - Math.sin(maz) * ce * 100, Cam.ey + Math.sin(mel) * 100, Cam.ez + Math.cos(maz) * ce * 100)) continue;
                    if (Cam.sx < 0 || Cam.sy < 0 || Cam.sx >= W || Cam.sy >= H || !visible(mc, maz, mel)) continue;
                    float f = (1f - age) * (1f - k / 10f);
                    if (have) Spr.beam(g, hx, hy, Cam.sx, Cam.sy, 2.2 * f + 0.6, 0xCFE6FF, f * alpha);
                    else Spr.glow(g, Cam.sx, Cam.sy, 14, 0xFFFFFF, f * alpha);
                    hx = Cam.sx; hy = Cam.sy; have = true;
                }
            }
        }
    }
}

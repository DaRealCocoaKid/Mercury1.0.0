package dev.mercury.client.module;

import com.visionplus.client.Freecam;
import com.visionplus.client.Optimizer;
import com.visionplus.client.VPConfig;
import dev.mercury.client.Config;
import dev.mercury.client.fx.AuraOrbs;
import dev.mercury.client.fx.BlockAura;
import dev.mercury.client.fx.Fx;
import dev.mercury.client.fx.ColorSet;
import dev.mercury.client.fx.OreEsp;
import dev.mercury.client.fx.PlayerEsp;
import dev.mercury.client.fx.Portals;
import dev.mercury.client.fx.Skybox;
import dev.mercury.client.fx.SpectatorWatch;
import dev.mercury.client.fx.Trails;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public final class ModuleManager {
    public static final List<Module> ALL = new ArrayList<>();

    // ---- CLIENTE (PvP) ----
    public static Module AUTO_ANCHOR, AUTO_CRYSTAL, FAST_TOTEM, COMBAT, FREECAM, OPTIMIZER, MACE_COMBO, SAFE_ANCHOR, SPRINT, FAST_PLACE, AIM_ASSIST, AUTO_TOTEM, AUTO_ARMOR, AUTO_GAPPLE, PEARL, CRYSTAL_HOLD, CRYSTAL_AUTO, AUTOWALK, WATER_DROP;
    // ---- VISUALS ----
    public static Module CRYSTAL_HITBOX, FREELOOK, CHEST_ESP, CHUNK_FINDER, PLAYER_ALERT, INVIS_BOXES;
    public static Module SPEC_WATCH, FULLBRIGHT, TARGET_HUD, XRAY, DEBRIS, BLOCK_AURA, TRAILS, PLAYER_ESP, FOOTSTEPS, AURA, SKYBOX;

    private static boolean espBoxesPref = true, espTracersPref = false;

    private static VPConfig c() { return VPConfig.I; }

    private static int palIdx(int rgb) {
        for (int i = 0; i < Config.PALETTE.length; i++) if ((Config.PALETTE[i] & 0xFFFFFF) == (rgb & 0xFFFFFF)) return i;
        return 0;
    }

    public static List<Module> of(Module.Cat cat) {
        List<Module> l = new ArrayList<>();
        for (Module m : ALL) if (m.cat == cat) l.add(m);
        return l;
    }

    private static Module add(Module m) { ALL.add(m); return m; }

    private static Setting delay(String label, java.util.function.IntSupplier g, java.util.function.IntConsumer s) {
        return Setting.integer(label, g, s, 1, 10, 1, "t", true);
    }

    public static void init() {
        VPConfig.load();
        c().enabled = true; // interruptor maestro de los modulos de Mercury: siempre activo, cada modulo tiene su propio on/off
        espBoxesPref = c().boxes || !c().tracers;
        espTracersPref = c().tracers;

        AUTO_ANCHOR = add(new Module(Module.Cat.CLIENTE, "autoanchor", "Auto Anchor",
                "Respawn anchor + glowstone con cambio de slot", -1).with(
                delay("Delay min", () -> Pvp.anchorMin, v -> Pvp.anchorMin = v),
                delay("Delay max", () -> Pvp.anchorMax, v -> Pvp.anchorMax = v)));
        SAFE_ANCHOR = add(new Module(Module.Cat.PVP, "safeanchor", "Safe Anchor",
                "Anchor: carga + escudo + totem + detona", -1).with(
                Setting.integer("Delay min", () -> SafeAnchor.min, v -> SafeAnchor.min = v, 0, 10, 1, "t", true),
                Setting.integer("Delay max", () -> SafeAnchor.max, v -> SafeAnchor.max = v, 0, 10, 1, "t", true),
                Setting.integer("Rango enemigo", () -> SafeAnchor.range, v -> SafeAnchor.range = v, 3, 16, 1, "", true),
                Setting.bool("Escudo", () -> SafeAnchor.shield, v -> SafeAnchor.shield = v, true),
                Setting.choice("Bloque escudo", SafeAnchor.SHIELDS, () -> SafeAnchor.shieldMode, v -> SafeAnchor.shieldMode = v, true),
                Setting.bool("Totem", () -> SafeAnchor.totem, v -> SafeAnchor.totem = v, true),
                Setting.bool("Detonar", () -> SafeAnchor.detonate, v -> SafeAnchor.detonate = v, true)));

        AIM_ASSIST = add(new Module(Module.Cat.PVP, "aimassist", "Aim Assist",
                "Apunta suave al jugador mas cercano a tu mira", -1).with(
                Setting.integer("Rango", () -> PvpTools.aimRange, v -> PvpTools.aimRange = v, 2, 8, 1, "", true),
                Setting.integer("FOV", () -> PvpTools.aimFov, v -> PvpTools.aimFov = v, 20, 180, 10, "", true),
                Setting.integer("Velocidad", () -> PvpTools.aimSpeed, v -> PvpTools.aimSpeed = v, 1, 10, 1, "", true),
                Setting.choice("Objetivo", PvpTools.AIM_TARGETS, () -> PvpTools.aimTarget, v -> PvpTools.aimTarget = v, true),
                Setting.bool("Vertical", () -> PvpTools.aimVertical, v -> PvpTools.aimVertical = v, true),
                Setting.bool("Solo con click", () -> PvpTools.aimClick, v -> PvpTools.aimClick = v, true),
                Setting.bool("Ignorar amigos", () -> PvpTools.aimFriends, v -> PvpTools.aimFriends = v, true)));
        AUTO_TOTEM = add(new Module(Module.Cat.PVP, "autototem", "Auto Totem",
                "Mantiene un totem en la mano secundaria", -1).with(
                Setting.integer("Delay min", () -> PvpTools.totemDelayMin, v -> PvpTools.totemDelayMin = v, 0, 10, 1, "t", true),
                Setting.integer("Delay max", () -> PvpTools.totemDelayMax, v -> PvpTools.totemDelayMax = v, 0, 10, 1, "t", true),
                Setting.integer("Vida <= (0=siempre)", () -> PvpTools.totemHealth, v -> PvpTools.totemHealth = v, 0, 20, 2, "", true)));
        AUTO_ARMOR = add(new Module(Module.Cat.PVP, "autoarmor", "Auto Armor",
                "Se pone la mejor armadura del inventario", -1).with(
                Setting.integer("Velocidad", () -> PvpTools.armorDelay, v -> PvpTools.armorDelay = v, 1, 10, 1, "", true)));
        AUTO_GAPPLE = add(new Module(Module.Cat.PVP, "autogapple", "Auto Gapple",
                "Come manzana dorada con poca vida", -1).with(
                Setting.integer("Vida <=", () -> PvpTools.gapHealth, v -> PvpTools.gapHealth = v, 2, 19, 1, "", true),
                Setting.bool("Preferir encantada", () -> PvpTools.gapEnchanted, v -> PvpTools.gapEnchanted = v, true),
                Setting.bool("Volver al slot", () -> PvpTools.gapRestore, v -> PvpTools.gapRestore = v, true)));
        PEARL = add(new Module(Module.Cat.PVP, "pearl", "Lanzar perla",
                "Asignale una tecla: lanza la perla y vuelve", -1).with(
                Setting.bool("Volver al slot", () -> PvpTools.pearlRestore, v -> PvpTools.pearlRestore = v, true)));
        SPRINT = add(new Module(Module.Cat.PVP, "sprint", "Sprint",
                "Corres siempre al avanzar", -1));
        FAST_PLACE = add(new Module(Module.Cat.PVP, "fastplace", "Fast Place",
                "Sin espera al colocar bloques/cristales", -1));
        AUTOWALK = add(new Module(Module.Cat.PVP, "autowalk", "Auto Walk",
                "Camina solo hacia adelante", -1));
        WATER_DROP = add(new Module(Module.Cat.PVP, "waterdrop", "Auto Water Drop",
                "Cayendo con cubeta de agua en la hotbar: water drop solo", -1).with(
                Setting.integer("Caida minima", () -> MoveTools.minFall, v -> MoveTools.minFall = v, 3, 20, 1, "", true),
                Setting.bool("Recoger agua", () -> MoveTools.pickUp, v -> MoveTools.pickUp = v, true),
                Setting.bool("Volver al slot y mira", () -> MoveTools.restore, v -> MoveTools.restore = v, true)));
        FULLBRIGHT = add(new Module(Module.Cat.VISUALS, "fullbright", "Fullbright",
                "Ves en la oscuridad (vision nocturna local)", -1));
        TARGET_HUD = add(new Module(Module.Cat.VISUALS, "targethud", "Target HUD",
                "Nombre, vida y distancia del jugador al que miras", -1));

        SPEC_WATCH = add(new Module(Module.Cat.CLIENTE, "specwatch", "Detector cazadores",
                "Avisa si un cazador te espectea o va invisible", -1).with(
                Setting.choice("Vigilar", SpectatorWatch.MODES, () -> SpectatorWatch.mode, v -> SpectatorWatch.mode = v, true),
                Setting.integer("Rango", () -> SpectatorWatch.range, v -> SpectatorWatch.range = v, 8, 96, 8, "", true),
                Setting.integer("Distancia pegado", () -> SpectatorWatch.close, v -> SpectatorWatch.close = v, 1, 6, 1, "", true),
                Setting.bool("Aviso en pantalla", () -> SpectatorWatch.alert, v -> SpectatorWatch.alert = v, true),
                Setting.bool("Sonido", () -> SpectatorWatch.sound, v -> SpectatorWatch.sound = v, true),
                Setting.bool("Panel", () -> SpectatorWatch.panel, v -> SpectatorWatch.panel = v, true),
                Setting.bool("ESP + tracer", () -> SpectatorWatch.esp, v -> SpectatorWatch.esp = v, true)));
        AUTO_CRYSTAL = add(new Module(Module.Cat.CLIENTE, "autocrystal", "Auto Crystal",
                "Obsidiana -> cambia al cristal", -1).with(
                delay("Delay min", () -> Pvp.crystalMin, v -> Pvp.crystalMin = v),
                delay("Delay max", () -> Pvp.crystalMax, v -> Pvp.crystalMax = v)));
        CRYSTAL_HOLD = add(new Module(Module.Cat.CLIENTE, "crystalhold", "Crystal Hold",
                "Manten click derecho con cristal: coloca y rompe rapido", -1).with(
                Setting.integer("Colocar min", () -> CrystalTools.holdPlaceMin, v -> CrystalTools.holdPlaceMin = v, 0, 6, 1, "t", true),
                Setting.integer("Colocar max", () -> CrystalTools.holdPlaceMax, v -> CrystalTools.holdPlaceMax = v, 0, 6, 1, "t", true),
                Setting.integer("Romper min", () -> CrystalTools.holdBreakMin, v -> CrystalTools.holdBreakMin = v, 0, 6, 1, "t", true),
                Setting.integer("Romper max", () -> CrystalTools.holdBreakMax, v -> CrystalTools.holdBreakMax = v, 0, 6, 1, "t", true)));
        CRYSTAL_AUTO = add(new Module(Module.Cat.CLIENTE, "crystalauto", "Crystal Auto",
                "Mirando obsidiana coloca y rompe cristales solo", -1).with(
                Setting.integer("Colocar min", () -> CrystalTools.autoPlaceMin, v -> CrystalTools.autoPlaceMin = v, 0, 6, 1, "t", true),
                Setting.integer("Colocar max", () -> CrystalTools.autoPlaceMax, v -> CrystalTools.autoPlaceMax = v, 0, 6, 1, "t", true),
                Setting.integer("Romper min", () -> CrystalTools.autoBreakMin, v -> CrystalTools.autoBreakMin = v, 0, 6, 1, "t", true),
                Setting.integer("Romper max", () -> CrystalTools.autoBreakMax, v -> CrystalTools.autoBreakMax = v, 0, 6, 1, "t", true),
                Setting.bool("Cambiar al cristal", () -> CrystalTools.autoSwitch, v -> CrystalTools.autoSwitch = v, true),
                Setting.integer("Solo con enemigo a (0=siempre)", () -> CrystalTools.autoEnemyRange, v -> CrystalTools.autoEnemyRange = v, 0, 32, 2, "", true),
                Setting.integer("No romper si vida <= (0=off)", () -> CrystalTools.autoMinHealth, v -> CrystalTools.autoMinHealth = v, 0, 20, 2, "", true)));
        FAST_TOTEM = add(new Module(Module.Cat.CLIENTE, "fasttotem", "Fast Totem",
                "Repone el totem al instante al popear", -1).with(
                Setting.choice("Modo", new String[]{"1 Click", "Full Auto"}, () -> Pvp.totemMode, v -> Pvp.totemMode = v, true),
                delay("Delay min", () -> Pvp.totemMin, v -> Pvp.totemMin = v),
                delay("Delay max", () -> Pvp.totemMax, v -> Pvp.totemMax = v),
                Setting.bool("Moverte con inventario", () -> Pvp.totemWalk, v -> Pvp.totemWalk = v, true)));
        COMBAT = add(new Module(Module.Cat.CLIENTE, "combat", "Combat Zylow",
                "Golpea solo con el crosshair sobre un jugador", GLFW.GLFW_KEY_G).with(
                Setting.choice("Alcance max", AutoHit.REACH, () -> AutoHit.reachIdx, v -> AutoHit.reachIdx = v, true)));
        FREECAM = add(new Module(Module.Cat.CLIENTE, "freecam", "Freecam",
                "Camara libre", GLFW.GLFW_KEY_V, () -> Freecam.active, v -> Freecam.toggle(Minecraft.getInstance())).with(
                Setting.decimal("Velocidad", () -> c().freecamSpeed, v -> c().freecamSpeed = (float) v, 0.5, 10, 0.5, v -> String.format("%.1f", v))));
        OPTIMIZER = add(new Module(Module.Cat.CLIENTE, "optimizer", "Ultra Optimizador",
                "Baja distancia, sombras y particulas para mas FPS", -1, Optimizer::isOn, v -> Optimizer.toggle(Minecraft.getInstance())));


        MACE_COMBO = add(new Module(Module.Cat.PVP, "macecombo", "Mace Combo",
                "Elytra -> pechera -> mazo -> golpe automatico", -1).with(
                Setting.integer("Rango cambio", () -> MaceCombo.range, v -> MaceCombo.range = v, 4, 16, 1, "", true),
                Setting.bool("Elytra al volar", () -> MaceCombo.reEquip, v -> MaceCombo.reEquip = v, true),
                Setting.bool("Planear solo", () -> MaceCombo.autoGlide, v -> MaceCombo.autoGlide = v, true),
                Setting.bool("Abrir inventario", () -> MaceCombo.useInventory, v -> MaceCombo.useInventory = v, true)));

        CRYSTAL_HITBOX = add(new Module(Module.Cat.VISUALS, "crystalhitbox", "Crystal Hitbox",
                "Dibuja la hitbox de los end crystals", -1).with(
                Setting.choice("Color", Config.HB_NAMES, () -> Pvp.hitboxColor, v -> Pvp.hitboxColor = v, true)));
        FREELOOK = add(new Module(Module.Cat.VISUALS, "freelook", "Freelook",
                "Mantén la tecla y mira tu personaje en 360 grados", GLFW.GLFW_KEY_LEFT_ALT).hold().with(
                Setting.choice("Modo", new String[]{"Mantener", "Alternar"}, () -> Freelook.mode, v -> Freelook.mode = v, true),
                Setting.bool("Atravesar bloques", () -> Freelook.noClip, v -> Freelook.noClip = v, true),
                Setting.integer("Distancia", () -> Freelook.distance, v -> Freelook.distance = v, 2, 12, 1, "", true)));
        FREELOOK.setOn(true);
        CHEST_ESP = add(new Module(Module.Cat.VISUALS, "chestesp", "ESP de cofres",
                "Cofres, barriles, shulkers y ender chests a traves de bloques", -1,
                () -> c().boxes || c().tracers,
                v -> {
                    if (v) { c().boxes = espBoxesPref; c().tracers = espTracersPref; if (!c().boxes && !c().tracers) c().boxes = espBoxesPref = true; }
                    else { espBoxesPref = c().boxes || espBoxesPref; espTracersPref = c().tracers; c().boxes = false; c().tracers = false; }
                }).with(
                Setting.bool("Cajas x-ray", () -> c().boxes, v -> { c().boxes = v; if (v) espBoxesPref = true; }, false),
                Setting.bool("Lineas guia", () -> c().tracers, v -> { c().tracers = v; espTracersPref = v; }, false),
                Setting.integer("Rango", () -> c().range, v -> c().range = v, 16, 256, 16, "", false),
                Setting.decimal("Grosor", () -> c().lineWidth, v -> c().lineWidth = (float) v, 0.5, 5, 0.5, v -> String.format("%.1f", v)),
                Setting.decimal("Transparencia", () -> c().alpha, v -> c().alpha = (float) v, 0.1, 1, 0.05, v -> Math.round(v * 100) + "%"),
                Setting.integer("Max dibujados", () -> c().maxTargets, v -> c().maxTargets = v, 10, 500, 10, "", false),
                colorSetting("Color cofre", () -> c().chest, v -> c().chest = v),
                colorSetting("Color trampa", () -> c().trapped, v -> c().trapped = v),
                colorSetting("Color ender", () -> c().ender, v -> c().ender = v),
                colorSetting("Color barril", () -> c().barrel, v -> c().barrel = v),
                colorSetting("Color shulker", () -> c().shulker, v -> c().shulker = v)));
        CHUNK_FINDER = add(new Module(Module.Cat.VISUALS, "chunkfinder", "Chunk Finder",
                "Marca chunks con muchos cofres (bases)", -1, () -> c().chunkFinder, v -> c().chunkFinder = v).with(
                Setting.integer("Min. cofres por chunk", () -> c().chunkMin, v -> c().chunkMin = v, 1, 100, 1, "", false),
                Setting.integer("Rango (chunks)", () -> c().chunkRange, v -> c().chunkRange = v, 1, 32, 1, "", false),
                Setting.integer("Aviso desde (cofres)", () -> c().chunkAlertMin, v -> c().chunkAlertMin = v, 1, 200, 1, "", false)));
        PLAYER_ALERT = add(new Module(Module.Cat.VISUALS, "playeralert", "Aviso de jugadores",
                "Notificacion cuando aparece un jugador cerca", -1, () -> c().playerAlert, v -> c().playerAlert = v).with(
                Setting.integer("Distancia (chunks)", () -> c().alertChunks, v -> c().alertChunks = v, 1, 64, 1, "", false)));
        INVIS_BOXES = add(new Module(Module.Cat.VISUALS, "invisboxes", "Hitbox invisibles",
                "Caja sobre jugadores invisibles", -1, () -> c().invisBoxes, v -> c().invisBoxes = v).with(
                colorSetting("Color", () -> c().invis, v -> c().invis = v)));

        BLOCK_AURA = add(new Module(Module.Cat.VISUALS, "blockaura", "Aura de bloque",
                "El bloque que miras brilla y deja estela", -1).with(
                Setting.integer("Estela (ms)", () -> BlockAura.fade, v -> BlockAura.fade = v, 200, 4000, 200, "", true),
                Setting.integer("Brillo", () -> BlockAura.glow, v -> BlockAura.glow = v, 0, 4, 1, "", true),
                Setting.bool("Chispas", () -> BlockAura.sparks, v -> BlockAura.sparks = v, true)).with(BlockAura.COLOR.settings()));
        TRAILS = add(new Module(Module.Cat.VISUALS, "trails", "Estelas",
                "Estelas en items, totems, proyectiles, cristales", -1).with(
                Setting.integer("Largo", () -> Trails.length, v -> Trails.length = v, 4, 40, 2, "", true),
                Setting.integer("Grosor", () -> Trails.width, v -> Trails.width = v, 1, 4, 1, "", true),
                Setting.bool("Items", () -> Trails.items, v -> Trails.items = v, true),
                Setting.bool("Totems", () -> Trails.totems, v -> Trails.totems = v, true),
                Setting.bool("Proyectiles", () -> Trails.projectiles, v -> Trails.projectiles = v, true),
                Setting.bool("Cristales", () -> Trails.crystals, v -> Trails.crystals = v, true),
                Setting.bool("Jugadores", () -> Trails.players, v -> Trails.players = v, true),
                Setting.bool("Chispas", () -> Trails.sparks, v -> Trails.sparks = v, true)).with(Trails.COLOR.settings()));
        PLAYER_ESP = add(new Module(Module.Cat.VISUALS, "playeresp", "ESP de jugadores",
                "Hitbox + tracer a traves de paredes", -1).with(
                Setting.choice("Modo", PlayerEsp.MODES, () -> PlayerEsp.mode, v -> PlayerEsp.mode = v, true),
                Setting.integer("Rango", () -> PlayerEsp.range, v -> PlayerEsp.range = v, 16, 256, 16, "", true),
                Setting.integer("Grosor", () -> PlayerEsp.width, v -> PlayerEsp.width = v, 1, 4, 1, "", true),
                Setting.bool("Nombre", () -> PlayerEsp.names, v -> PlayerEsp.names = v, true),
                Setting.bool("Vida", () -> PlayerEsp.health, v -> PlayerEsp.health = v, true),
                Setting.bool("Color por vida", () -> PlayerEsp.healthColor, v -> PlayerEsp.healthColor = v, true),
                Setting.bool("Amigos verdes", () -> PlayerEsp.friendsGreen, v -> PlayerEsp.friendsGreen = v, true),
                Setting.bool("Invisibles", () -> PlayerEsp.invisible, v -> PlayerEsp.invisible = v, true),
                Setting.bool("Brillo", () -> PlayerEsp.glowFill, v -> PlayerEsp.glowFill = v, true)).with(PlayerEsp.COLOR.settings()));
        FOOTSTEPS = add(new Module(Module.Cat.VISUALS, "footsteps", "Portales (pasos)",
                "Cada paso abre un portal de chispas", -1).with(
                Setting.integer("Duracion (s)", () -> Portals.life, v -> Portals.life = v, 1, 6, 1, "", true),
                Setting.integer("Tamano", () -> Portals.radius, v -> Portals.radius = v, 3, 15, 1, "", true),
                Setting.choice("Orientacion", new String[]{"Suelo", "Vertical"}, () -> Portals.orient, v -> Portals.orient = v, true),
                Setting.bool("Chispas", () -> Portals.sparks, v -> Portals.sparks = v, true)).with(Portals.COLOR.settings()));
        AURA = add(new Module(Module.Cat.VISUALS, "aura", "Aura",
                "Bolitas de luz y destellos a tu alrededor", -1).with(
                Setting.integer("Bolitas", () -> AuraOrbs.count, v -> AuraOrbs.count = v, 2, 24, 1, "", true),
                Setting.integer("Radio", () -> AuraOrbs.radius, v -> AuraOrbs.radius = v, 5, 30, 1, "", true),
                Setting.integer("Velocidad", () -> AuraOrbs.speed, v -> AuraOrbs.speed = v, 1, 12, 1, "", true),
                Setting.integer("Tamano", () -> AuraOrbs.size, v -> AuraOrbs.size = v, 2, 12, 1, "", true),
                Setting.bool("Estela", () -> AuraOrbs.trail, v -> AuraOrbs.trail = v, true),
                Setting.bool("Rayos", () -> AuraOrbs.lightning, v -> AuraOrbs.lightning = v, true)).with(AuraOrbs.COLOR.settings()));
        SKYBOX = add(new Module(Module.Cat.VISUALS, "skybox", "Skybox",
                "Cielo nebulosa/galaxia con auroras y colores", -1).with(
                Setting.choice("Estilo", Skybox.STYLES, () -> Skybox.style, v -> Skybox.style = v, true),
                Setting.choice("Color cielo", ColorSet.NAMES, () -> Skybox.SKY.mode, v -> Skybox.SKY.mode = v, true),
                Setting.integer("Cielo R", () -> Skybox.SKY.r, v -> Skybox.SKY.r = v, 0, 255, 15, "", true),
                Setting.integer("Cielo G", () -> Skybox.SKY.g, v -> Skybox.SKY.g = v, 0, 255, 15, "", true),
                Setting.integer("Cielo B", () -> Skybox.SKY.b, v -> Skybox.SKY.b = v, 0, 255, 15, "", true),
                Setting.bool("Auroras", () -> Skybox.auroras, v -> Skybox.auroras = v, true),
                Setting.choice("Color aurora", ColorSet.NAMES_NO_CUSTOM, () -> Skybox.AUR.mode, v -> Skybox.AUR.mode = v, true),
                Setting.bool("Estrellas", () -> Skybox.stars, v -> Skybox.stars = v, true),
                Setting.integer("Cantidad", () -> Skybox.density, v -> Skybox.density = v, 100, 1500, 100, "", true),
                Setting.bool("Fugaces", () -> Skybox.meteors, v -> Skybox.meteors = v, true),
                Setting.integer("Velocidad", () -> Skybox.speed, v -> Skybox.speed = v, 1, 12, 1, "", true),
                Setting.integer("Opacidad", () -> Skybox.opacity, v -> Skybox.opacity = v, 20, 100, 10, "%", true),
                Setting.choice("Calidad", Skybox.QUALITY, () -> Skybox.quality, v -> Skybox.quality = v, true)));

        OreEsp X = OreEsp.XRAY, D = OreEsp.DEBRIS;
        String[] xcolors = new String[ColorSet.NAMES.length];
        xcolors[0] = "Natural";
        System.arraycopy(ColorSet.NAMES_NO_CUSTOM, 0, xcolors, 1, ColorSet.NAMES_NO_CUSTOM.length);
        XRAY = add(new Module(Module.Cat.VISUALS, "xray", "Xray",
                "Minerales a traves de bloques (cajas + tracer)", -1).with(
                Setting.integer("Rango", () -> X.range, v -> X.range = v, 8, 48, 4, "", true),
                Setting.integer("Grosor", () -> X.width, v -> X.width = v, 1, 4, 1, "", true),
                Setting.bool("Tracer", () -> X.tracer, v -> X.tracer = v, true),
                Setting.bool("Relleno", () -> X.fill, v -> X.fill = v, true),
                Setting.choice("Color", xcolors, () -> X.colorIdx, v -> X.colorIdx = v, true),
                Setting.bool("Diamante", () -> X.ores[0], v -> X.ores[0] = v, true),
                Setting.bool("Esmeralda", () -> X.ores[1], v -> X.ores[1] = v, true),
                Setting.bool("Oro", () -> X.ores[2], v -> X.ores[2] = v, true),
                Setting.bool("Hierro", () -> X.ores[3], v -> X.ores[3] = v, true),
                Setting.bool("Cobre", () -> X.ores[4], v -> X.ores[4] = v, true),
                Setting.bool("Redstone", () -> X.ores[5], v -> X.ores[5] = v, true),
                Setting.bool("Lapis", () -> X.ores[6], v -> X.ores[6] = v, true),
                Setting.bool("Carbon", () -> X.ores[7], v -> X.ores[7] = v, true),
                Setting.bool("Cuarzo", () -> X.ores[8], v -> X.ores[8] = v, true)));
        DEBRIS = add(new Module(Module.Cat.VISUALS, "debrisfinder", "Ancient Debris",
                "Buscador de Ancient Debris tipo xray", -1).with(
                Setting.integer("Rango", () -> D.range, v -> D.range = v, 8, 48, 4, "", true),
                Setting.integer("Grosor", () -> D.width, v -> D.width = v, 1, 4, 1, "", true),
                Setting.bool("Tracer", () -> D.tracer, v -> D.tracer = v, true),
                Setting.bool("Relleno", () -> D.fill, v -> D.fill = v, true),
                Setting.bool("Texto/coords", () -> D.label, v -> D.label = v, true)).with(D.color.settings()));
    }

    private static Setting colorSetting(String label, java.util.function.IntSupplier g, java.util.function.IntConsumer s) {
        return Setting.choice(label, Config.PALETTE_NAMES, () -> palIdx(g.getAsInt()), i -> s.accept(Config.PALETTE[Math.floorMod(i, Config.PALETTE.length)]), false);
    }

    // ---- ajustes generales (pestania Configuracion) ----
    public static List<Setting> general() {
        return List.of(
                Setting.bool("Menu principal Mercury", () -> c().customTitle, v -> c().customTitle = v, false),
                Setting.bool("Abrir menu al entrar a un server", () -> Config.openOnJoin, v -> Config.openOnJoin = v, false),
                Setting.bool("HUD del cliente", () -> Config.hud, v -> Config.hud = v, false),
                Setting.bool("Animaciones y desenfoque", () -> Config.anim, v -> Config.anim = v, false));
    }

    // ---- teclas ----
    public static void tick(Minecraft mc) {
        Pvp.tick();
        AutoHit.tick();
        MaceCombo.tick();
        SafeAnchor.tick();
        PvpTools.tick();
        CrystalTools.tick();
        MoveTools.tick();
        Fx.tick(mc);
        boolean free = mc.player != null && mc.gui.screen() == null;
        Freelook.tick(mc, free && FREELOOK.key > 0
                && GLFW.glfwGetKey(mc.getWindow().handle(), FREELOOK.key) == GLFW.GLFW_PRESS);
        for (Module m : ALL) {
            if (m.hold) continue;
            if (!free || m.key <= 0) { m.keyWas = true; continue; }
            boolean down = GLFW.glfwGetKey(mc.getWindow().handle(), m.key) == GLFW.GLFW_PRESS;
            if (down && !m.keyWas) m.toggleWithMessage(mc);
            m.keyWas = down;
        }
    }

    public static void save(Properties p) { for (Module m : ALL) m.save(p); }
    public static void load(Properties p) { for (Module m : ALL) m.load(p); }
}

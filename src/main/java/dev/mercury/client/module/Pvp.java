package dev.mercury.client.module;

import dev.mercury.client.mixin.ContainerScreenAccessor;
import dev.mercury.client.mixin.MouseHandlerAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class Pvp {
    private static final Minecraft mc = Minecraft.getInstance();

    // delays en ticks (1 tick = 50ms)
    public static int anchorMin = 1, anchorMax = 3, crystalMin = 1, crystalMax = 3, totemMin = 1, totemMax = 3;
    public static int totemMode = 0; // 0 = 1 click, 1 = full auto
    public static boolean totemWalk = true; // poder moverte mientras el inventario del 1 Click esta abierto
    public static int hitboxColor = 0;

    private static int tick;
    // cambio de slot pendiente (Auto Anchor / Auto Crystal)
    private static int pendingSlot = -1, pendingAt;
    // Fast Totem
    private static boolean prevOffTotem, armed;
    private static int popOpenAt = -1, armedAt, hoverAt = -1, autoAt = -1, closeAt = -1;
    private static java.lang.reflect.Method setAllKeys;
    private static boolean setAllLooked;

    static int rand(int a, int b) {
        if (b < a) b = a;
        return a + ThreadLocalRandom.current().nextInt(b - a + 1);
    }

    // ---------------- Auto Anchor / Auto Crystal ----------------
    public static void onUse(Player p, Level l, InteractionHand h, BlockHitResult hit) {
        if (!l.isClientSide() || p != mc.player || h != InteractionHand.MAIN_HAND) return;
        Item held = p.getMainHandItem().getItem();

        if (held == Items.RESPAWN_ANCHOR && SafeAnchor.on()) { SafeAnchor.start(p, l, hit); return; }
        if (ModuleManager.AUTO_ANCHOR.isOn()) {
            if (held == Items.RESPAWN_ANCHOR) {
                int s = findHotbar(p, Items.GLOWSTONE);
                if (s >= 0) schedule(s, rand(anchorMin, anchorMax));
                return;
            }
            if (held == Items.GLOWSTONE && l.getBlockState(hit.getBlockPos()).is(Blocks.RESPAWN_ANCHOR)) {
                int s = randomOther(p);
                if (s >= 0) schedule(s, rand(anchorMin, anchorMax));
                return;
            }
        }
        if (ModuleManager.AUTO_CRYSTAL.isOn() && held == Items.OBSIDIAN) {
            int s = findHotbar(p, Items.END_CRYSTAL);
            if (s >= 0) schedule(s, rand(crystalMin, crystalMax));
        }
    }

    private static void schedule(int slot, int delay) {
        pendingSlot = slot;
        pendingAt = tick + Math.max(1, delay);
    }

    private static int findHotbar(Player p, Item item) {
        for (int i = 0; i < 9; i++) if (p.getInventory().getItem(i).is(item)) return i;
        return -1;
    }

    // slot aleatorio de la hotbar con item, distinto al actual y que NO sea perla
    private static int randomOther(Player p) {
        List<Integer> c = new ArrayList<>();
        int cur = p.getInventory().getSelectedSlot();
        for (int i = 0; i < 9; i++) {
            var st = p.getInventory().getItem(i);
            if (i != cur && !st.isEmpty() && !st.is(Items.ENDER_PEARL)) c.add(i);
        }
        return c.isEmpty() ? -1 : c.get(ThreadLocalRandom.current().nextInt(c.size()));
    }

    // ---------------- Tick ----------------
    public static void tick() {
        anchorMax = Math.max(anchorMax, anchorMin); crystalMax = Math.max(crystalMax, crystalMin); totemMax = Math.max(totemMax, totemMin);
        tick++;
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) { armed = false; return; }

        if (pendingSlot >= 0 && tick >= pendingAt) {
            p.getInventory().setSelectedSlot(pendingSlot);
            pendingSlot = -1;
        }
        totem(p);
    }

    // ---------------- Fast Totem ----------------
    private static int findTotem(LocalPlayer p) {
        for (int i = 9; i <= 44; i++)
            if (p.inventoryMenu.slots.get(i).getItem().is(Items.TOTEM_OF_UNDYING)) return i;
        return -1;
    }

    private static void totem(LocalPlayer p) {
        boolean off = p.getOffhandItem().is(Items.TOTEM_OF_UNDYING);

        // pop detectado: offhand tenia totem, ahora no, y tiene el regen del totem
        if (ModuleManager.FAST_TOTEM.isOn() && prevOffTotem && !off && !p.isDeadOrDying()
                && p.hasEffect(MobEffects.REGENERATION) && mc.gui.screen() == null && findTotem(p) >= 0) {
            popOpenAt = tick + rand(totemMin, totemMax);
        }
        prevOffTotem = off;

        if (popOpenAt >= 0 && tick >= popOpenAt) {
            popOpenAt = -1;
            int ts = findTotem(p);
            if (mc.gui.screen() == null && ts >= 0) {
                // en los dos modos se abre el inventario (como un jugador normal)
                mc.gui.setScreen(new InventoryScreen(p));
                armed = true;
                armedAt = tick;
                hoverAt = tick + 1;
                autoAt = totemMode == 1 ? tick + rand(totemMin, totemMax) : -1;
                closeAt = -1;
            }
        }

        if (!armed) return;
        if (!(mc.gui.screen() instanceof InventoryScreen) || tick - armedAt > 20) {
            boolean ours = mc.gui.screen() instanceof InventoryScreen;
            if (ours) mc.gui.setScreen(null);
            disarm();
            resyncKeys();
            return;
        }
        if (off || (closeAt >= 0 && tick >= closeAt)) { // totem ya en mano (o ya hiciste el click) -> cierra solo
            mc.gui.setScreen(null);
            disarm();
            resyncKeys();
            return;
        }

        if (totemWalk) resyncKeys(); // mientras esta abierto sigues moviendote con las teclas que tienes presionadas
        if (totemMode == 0 && hoverAt >= 0 && tick >= hoverAt) { hoverAt = -1; hover(p); }
        if (totemMode == 1 && autoAt >= 0 && tick >= autoAt) {
            autoAt = -1;
            swap(p, findTotem(p));
            closeAt = tick + 1; // cierra enseguida, sin esperar la confirmacion del servidor (ping)
        }
    }

    private static void disarm() { armed = false; hoverAt = -1; autoAt = -1; closeAt = -1; }

    /** Vuelve a leer del teclado las teclas que sigues presionando (al abrir/cerrar una pantalla el juego las suelta). */
    private static void resyncKeys() {
        try {
            if (!setAllLooked) {
                setAllLooked = true;
                for (java.lang.reflect.Method m : Class.forName("net.minecraft.client.KeyMapping").getMethods())
                    if (m.getName().equals("setAll") && m.getParameterCount() == 0) { setAllKeys = m; break; }
            }
            if (setAllKeys != null) setAllKeys.invoke(null);
        } catch (Throwable ignored) { setAllKeys = null; }
    }

    private static void swap(LocalPlayer p, int slotId) {
        if (slotId < 0 || mc.gameMode == null) return;
        mc.gameMode.handleContainerInput(p.inventoryMenu.containerId, slotId, 40, ContainerInput.SWAP, p);
    }

    // posiciona el mouse encima de un totem
    private static void hover(LocalPlayer p) {
        int s = findTotem(p);
        if (s < 0 || !(mc.gui.screen() instanceof AbstractContainerScreen<?> scr)) return;
        Slot slot = p.inventoryMenu.slots.get(s);
        ContainerScreenAccessor acc = (ContainerScreenAccessor) (Object) scr;
        double gs = mc.getWindow().getGuiScale();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double px = (acc.mc$left() + slot.x + 8) * gs + r.nextInt(-3, 4);
        double py = (acc.mc$top() + slot.y + 8) * gs + r.nextInt(-3, 4);
        GLFW.glfwSetCursorPos(mc.getWindow().handle(), px, py);
        MouseHandlerAccessor mh = (MouseHandlerAccessor) (Object) mc.mouseHandler;
        mh.mc$setX(px);
        mh.mc$setY(py);
    }

    // llamado desde el mixin: 1 click sobre totem = va a la offhand
    public static boolean interceptClick(Slot slot, int button) {
        LocalPlayer p = mc.player;
        if (!armed || totemMode != 0 || p == null || slot == null || button != 0) return false;
        if (!slot.getItem().is(Items.TOTEM_OF_UNDYING) || slot.index < 9 || slot.index > 44) return false;
        swap(p, slot.index);
        closeAt = tick + 1; // cierra el inventario enseguida, sin esperar la confirmacion del servidor
        return true;
    }
}

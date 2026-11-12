package com.xenowarriors.client.forms;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.client.ClientCharacterData;
import com.xenowarriors.client.ModKeyMappings;
import com.xenowarriors.forms.Form;
import com.xenowarriors.forms.WiresItem;
import com.xenowarriors.network.FormPickPacket;
import com.xenowarriors.network.ModNetwork;
import com.xenowarriors.network.WeaponFormsPacket;
import com.xenowarriors.network.WireActionPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Weapon forms, client side: the summon key (tap = weapon in / out, hold = the form wheel) for Mikiko once he has more
 * than one form and for Rhalix once Season 2 is unlocked; Rhalix's wire controls (attack = whip, hold use on the
 * lock-on target = bind); and the "Mash attack!" prompt for whoever is caught in a bind.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class FormsClient {
    public static final int HOLD = 8, BIND_HOLD = 5;
    private static WeaponFormsPacket state = new WeaponFormsPacket(-1, 0, 0, 0F, 0);
    private static long stateAt;
    private static int held = -1, useHeld = -1, lastWhip = -100, ticks;
    private static boolean binding;
    /** Test counters. */
    public static int whipsSent, mashesSent, bindsSent;

    private FormsClient() {}

    public static void state(WeaponFormsPacket m) {
        state = m;
        Minecraft mc = Minecraft.getInstance();
        stateAt = mc.level == null ? 0 : mc.level.getGameTime();
    }

    public static Form.Style style() { return state.style() < 0 ? null : Form.Style.values()[state.style()]; }
    public static int mask() { return state.mask(); }
    public static boolean unlocked(Form f) { return (state.mask() & (1 << f.ordinal())) != 0; }
    public static int weaponLevel() { return state.level(); }
    public static float weaponProgress() { return state.progress(); }

    /** Ticks left before a new gun can be summoned. */
    public static int gunCooldown() {
        Minecraft mc = Minecraft.getInstance();
        long gone = mc.level == null ? 0 : mc.level.getGameTime() - stateAt;
        return (int) Math.max(0, state.gunCooldown() - gone);
    }

    /** Whether this client runs the wheel for the summon key right now. */
    private static boolean wheelOn(Minecraft mc) {
        if (mc.player == null) return false;
        CharacterType t = ClientCharacterData.getLocal();
        if (t == CharacterType.MIKIKO) return style() == Form.Style.HEKU && Integer.bitCount(state.mask()) > 1;
        return t == CharacterType.RHALIX && style() == Form.Style.WIRE;
    }

    /** Called first by the summon-key handling (BladeChannelClient); true = handled here. */
    public static boolean keyTick(Minecraft mc) {
        if (!wheelOn(mc)) { held = -1; return false; }
        if (mc.screen instanceof FormWheelScreen) return true;
        boolean down = mc.screen == null && ModKeyMappings.SUMMON_BLADE.isDown();
        if (down) {
            held++;
            if (held == HOLD) openWheel(mc, false);
        } else if (held >= 0) {
            if (held < HOLD) tap(mc);
            held = -1;
        }
        return true;
    }

    private static void tap(Minecraft mc) {
        if (ClientCharacterData.getLocal() == CharacterType.MIKIKO) ModNetwork.sendToServer(new com.xenowarriors.network.SummonBladePacket());
        else ModNetwork.sendToServer(new FormPickPacket(FormPickPacket.TAP, -1));
    }

    public static void openWheel(Minecraft mc, boolean test) {
        Form.Style s = style();
        if (s == null) return;
        ModNetwork.sendToServer(new FormPickPacket(FormPickPacket.OPEN, -1));
        mc.setScreen(new FormWheelScreen(s, test));
    }

    // ------------------------------------------------------------------ Rhalix's wires and the mash

    private static boolean holdingWires(Minecraft mc) {
        return mc.player != null && mc.player.getMainHandItem().getItem() instanceof WiresItem;
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onClick(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || event.isCanceled()) return;
        if (event.isAttack() && FormsFx.boundBy(mc.player.getId()) >= 0) {       // caught in a bind: every click pulls at the wires
            event.setCanceled(true);
            event.setSwingHand(true);
            mash();
            return;
        }
        if (GunClient.onClick(event)) return;                                  // (a summoned gun: left click fires, right click aims)
        if (!holdingWires(mc) || event.getHand() != InteractionHand.MAIN_HAND) return;
        if (event.isAttack()) {
            if (mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult b && b.getType() != net.minecraft.world.phys.HitResult.Type.MISS) return;   // (digging)
            event.setCanceled(true);
            event.setSwingHand(false);
            whip();
        } else if (event.isUseItem()) {
            event.setCanceled(true);
            event.setSwingHand(false);
            if (useHeld < 0) useHeld = 0;
        }
    }

    public static void whip() {
        if (ticks - lastWhip < com.xenowarriors.rhalix.Wires.WHIP_COOLDOWN) return;
        lastWhip = ticks;
        whipsSent++;
        ModNetwork.sendToServer(new WireActionPacket(WireActionPacket.WHIP, -1));
    }

    public static void mash() {
        mashesSent++;
        ModNetwork.sendToServer(new WireActionPacket(WireActionPacket.MASH, -1));
    }

    /** Starts a bind on the lock-on target (false + a hint if there's none). */
    public static boolean bind(Minecraft mc) {
        LivingEntity t = com.xenowarriors.client.LockOn.target();
        if (t == null || !t.isAlive()) {
            mc.player.displayClientMessage(Component.literal("Lock on (" + ModKeyMappings.LOCK_ON.getTranslatedKeyMessage().getString()
                    + ") to someone to bind them with your wires").withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        binding = true;
        bindsSent++;
        ModNetwork.sendToServer(new WireActionPacket(WireActionPacket.BIND, t.getId()));
        return true;
    }

    public static void unbind() {
        if (!binding) return;
        binding = false;
        ModNetwork.sendToServer(new WireActionPacket(WireActionPacket.RELEASE, -1));
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || mc.player == null) return;
        ticks++;
        if (useHeld >= 0) {
            boolean down = mc.options.keyUse.isDown() && holdingWires(mc) && mc.screen == null;
            if (!down) { unbind(); useHeld = -1; }
            else if (++useHeld == BIND_HOLD) bind(mc);
        }
        if (binding && !FormsFx.binding(mc.player.getId()) && useHeld > BIND_HOLD + 10) binding = false;   // (the server ended it)
    }

    /** "Mash attack!" for a bound player; the bind timer for Rhalix. */
    @SubscribeEvent
    public static void onHud(RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay() != VanillaGuiOverlay.HOTBAR.type()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        GuiGraphics g = event.getGuiGraphics();
        int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
        if (FormsFx.boundBy(mc.player.getId()) >= 0) {
            int prog = FormsFx.mashProgress(mc.player.getId());
            boolean flash = (ticks / 4) % 2 == 0;
            String s = "MASH ATTACK! (" + mc.options.keyAttack.getTranslatedKeyMessage().getString() + ")";
            g.pose().pushPose();
            g.pose().translate(w / 2F, h / 2F + 28, 0);
            g.pose().scale(1.6F, 1.6F, 1F);
            g.drawCenteredString(mc.font, s, 0, 0, flash ? 0xFF4040 : 0xFFD0D0);
            g.pose().popPose();
            int bw = 120, bx = w / 2 - bw / 2, by = h / 2 + 48;
            g.fill(bx - 1, by - 1, bx + bw + 1, by + 7, 0xC0000000);
            g.fill(bx, by, bx + Math.round(bw * prog / 100F), by + 6, 0xFFE03030);
            boolean proto = mc.level.getEntity(FormsFx.boundBy(mc.player.getId())) instanceof com.xenowarriors.gov.UltimaPrototypeEntity;
            g.drawCenteredString(mc.font, proto ? "Grabbed by the Ultima Prototype - break free!" : "Bound by wires - break free!", w / 2, by + 10, 0xFFB0B0);
        } else if (FormsFx.binding(mc.player.getId())) {
            long gone = mc.level.getGameTime() - FormsFx.bindStart(mc.player.getId());
            float left = Math.max(0, com.xenowarriors.rhalix.Wires.MAX_BIND - gone) / 20F;
            g.drawCenteredString(mc.font, String.format("Binding - draining their Karma (%.1fs)", left), w / 2, h / 2 + 30, 0xFF6060);
        }
    }

    /** UI test: as if the summon key were held until the wheel opened. */
    public static void testOpenWheel() {
        Minecraft mc = Minecraft.getInstance();
        if (style() != null) openWheel(mc, true);
    }
}

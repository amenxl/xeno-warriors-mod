package com.xenowarriors.client.test;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * UI tests for Mikiko / Kaeru (focuses starting with "mikiko", run from UiTest): falling after flight, no sitting in
 * menus, Hekublade Rain, the Scatter ride by blade level, Kaeru's hood. Results go to the log as "XW-TEST mikiko ...".
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class MikikoTests {
    /** Adds one step to the UI test: a screenshot name (or null), what to do, and how many ticks to wait after. */
    public interface Steps { void add(String shot, Runnable action, int delay); }

    private MikikoTests() {}

    public static void log(String s) { XenoWarriors.LOGGER.info("XW-TEST mikiko " + s); }

    private static ServerPlayer sp() {
        Minecraft mc = Minecraft.getInstance();
        var s = mc.getSingleplayerServer();
        return s == null || mc.player == null ? null : s.getPlayerList().getPlayer(mc.player.getUUID());
    }

    private static void server(java.util.function.Consumer<ServerPlayer> f) {
        var s = Minecraft.getInstance().getSingleplayerServer();
        if (s != null) s.execute(() -> { ServerPlayer p = sp(); if (p != null) try { f.accept(p); } catch (Throwable t) { XenoWarriors.LOGGER.error("XW-TEST mikiko server step failed", t); } });
    }

    private static void character(CharacterType t) {
        server(p -> com.xenowarriors.character.CharacterManager.setCharacter(p, t));
    }

    private static void levels(int a, int b, int c) {
        server(p -> {
            p.getCapability(com.xenowarriors.character.ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> { d.statLevel[0] = a; d.statLevel[1] = b; d.statLevel[2] = c; });
            com.xenowarriors.stats.Stats.syncAll(p);
            log("character level now " + com.xenowarriors.stats.Stats.characterLevel(p));
        });
    }

    public static void run(Minecraft mc, String focus, Steps l) {
        l.add(null, () -> { mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE); mc.getToasts().clear(); mc.setScreen(null); }, 5);
        boolean all = focus.contains("all") || focus.contains("before") || focus.contains("after");
        if (all || focus.contains("fall")) fall(mc, l);
        if (all || focus.contains("menu")) menuSit(mc, l);
        l.add(null, () -> log("done"), 5);
    }

    // ------------------------------------------------------------------ 1. falling after flight

    /** Per-tick recorder of the local player's fall (client side) - {label, ticks left}. */
    private static String rec;
    private static int recLeft;
    private static float healthAtStart;

    private static void record(String label, int ticks) { rec = label; recLeft = ticks; }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || rec == null) return;
        var p = Minecraft.getInstance().player;
        if (p == null) return;
        Vec3 v = p.getDeltaMovement();
        log(String.format("fall %s t=%d y=%.2f vy=%.3f fallDist=%.2f onGround=%s flying=%s hp=%.1f", rec, 40 - recLeft, p.getY(), v.y, p.fallDistance,
                p.onGround(), p.getAbilities().flying, p.getHealth()));
        if (--recLeft <= 0 || p.onGround()) {
            log(String.format("fall %s landed after %d ticks, hp %.1f -> %.1f (fall damage %.1f)", rec, 40 - recLeft, healthAtStart, p.getHealth(), healthAtStart - p.getHealth()));
            rec = null;
        }
    }

    /** Takes off, climbs 14 blocks, stops flying and falls - logged tick by tick. */
    private static void flyAndDrop(Minecraft mc, Steps l, String label) {
        l.add(null, () -> server(p -> {
            p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            p.setHealth(p.getMaxHealth());
            p.removeAllEffects();
            p.getAbilities().mayfly = true; p.getAbilities().flying = true; p.onUpdateAbilities();
            p.teleportTo(p.getX(), p.getY() + 14, p.getZ());
        }), 30);
        l.add(null, () -> { var p = mc.player; healthAtStart = p.getHealth(); p.getAbilities().flying = false; p.onUpdateAbilities(); record(label, 40); }, 45);
    }

    private static void fall(Minecraft mc, Steps l) {
        double[] home = new double[3];
        character(CharacterType.MIKIKO);
        l.add(null, () -> { mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); levels(20, 20, 21);   // level 60: he can fly
            server(p -> { home[0] = p.getX(); home[1] = p.getY(); home[2] = p.getZ(); }); }, 40);
        // a: a normal flight, landing
        flyAndDrop(mc, l, "fresh");
        // b: an aerial combo swing, then a trip to another dimension and back (a new client player - like the Hekublade realm)
        l.add(null, () -> server(p -> { p.getAbilities().mayfly = true; p.getAbilities().flying = true; p.onUpdateAbilities(); p.teleportTo(p.getX(), p.getY() + 3, p.getZ()); }), 20);
        l.add(null, () -> { var p = mc.player; p.getAbilities().flying = false; p.onUpdateAbilities();
            log("aerial swing at tickCount " + p.tickCount);
            com.xenowarriors.client.mikiko.AerialCombo.swing(p); }, 30);
        l.add(null, () -> server(p -> p.teleportTo(p.server.getLevel(net.minecraft.world.level.Level.NETHER), home[0], 80, home[2], 0, 0)), 60);
        l.add(null, () -> server(p -> p.teleportTo(p.server.overworld(), home[0], home[1], home[2], 0, 0)), 60);
        l.add(null, () -> log("back in the overworld: new player tickCount " + mc.player.tickCount), 5);
        flyAndDrop(mc, l, "after_dimension");
        l.add(null, () -> server(p -> { p.setGameMode(net.minecraft.world.level.GameType.CREATIVE); p.setHealth(p.getMaxHealth()); }), 10);
    }

    // ------------------------------------------------------------------ 2. no sitting in menus

    private static void menuSit(Minecraft mc, Steps l) {
        character(CharacterType.MIKIKO);
        l.add(null, () -> { mc.setScreen(null); mc.options.hideGui = true; mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            server(p -> { p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, net.minecraft.world.item.ItemStack.EMPTY); p.setYRot(0); p.setXRot(10); });
            mc.player.setXRot(10F); mc.player.setYRot(0F); }, 40);
        // control: standing around with no menu open - he sits down after 30 seconds
        l.add(null, () -> {}, 640);
        l.add("mikiko_menu_world_nomenu", () -> log("idle 32s, no menu: sitting " + sitting(mc)), 5);
        // a menu open: in the world behind it (the inventory), and the character select's big preview
        l.add(null, () -> mc.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player)), 40);
        l.add("mikiko_menu_inventory", () -> log("inventory open: sitting " + sitting(mc)), 5);
        l.add(null, () -> mc.setScreen(new com.xenowarriors.client.screen.CharacterSelectScreen()), 40);
        l.add("mikiko_menu_select", () -> log("character select open: sitting " + sitting(mc)), 5);
        l.add(null, () -> { mc.setScreen(null); mc.options.hideGui = false; mc.options.setCameraType(CameraType.FIRST_PERSON); }, 5);
    }

    private static String sitting(Minecraft mc) {
        return String.format("%.2f", com.xenowarriors.client.mikiko.MikikoAnimations.sitWeight(mc.player));
    }
}

package com.xenowarriors.client.test;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * UI tests for Mikiko / Kaeru (focuses starting with "mikiko", run from UiTest): falling after flight, no sitting in
 * menus, Hekublade Rain, the Scatter ride by blade level, Kaeru's hood. Results go to the log as "XW-TEST mikiko ...".
 * Focus words pick the parts: fall, menu, rain, ride, hood ("all" = everything).
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

    private static void character(Steps l, CharacterType t) {
        l.add(null, () -> server(p -> com.xenowarriors.character.CharacterManager.setCharacter(p, t)), 40);
    }

    private static void levels(ServerPlayer p, int a, int b, int c) {
        p.getCapability(com.xenowarriors.character.ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> { d.statLevel[0] = a; d.statLevel[1] = b; d.statLevel[2] = c; });
        com.xenowarriors.stats.Stats.syncAll(p);
        com.xenowarriors.skill.SkillManager.syncLoadout(p);
        log("character level now " + com.xenowarriors.stats.Stats.characterLevel(p));
    }

    public static void run(Minecraft mc, String focus, Steps l) {
        l.add(null, () -> { mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE); mc.getToasts().clear(); mc.setScreen(null); }, 5);
        boolean all = focus.contains("all") || focus.contains("before") || focus.contains("after");
        if (all || focus.contains("fall")) fall(mc, l);
        if (all || focus.contains("menu")) menuSit(mc, l);
        if (focus.contains("all") || focus.contains("rain")) rain(mc, l);
        if (focus.contains("all") || focus.contains("ride")) ride(mc, l);
        if (focus.contains("all") || focus.contains("hood")) hood(mc, l);
        l.add(null, () -> { mc.setCameraEntity(mc.player); mc.setScreen(null); log("done"); }, 5);
    }

    /** An invisible armor stand to film from: {x, y, z offset from the player, yaw, pitch}. */
    private static void camera(Minecraft mc, Steps l, double dx, double dy, double dz, float yaw, float pitch) {
        int[] id = {-1};
        l.add(null, () -> server(p -> {
            p.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.decoration.ArmorStand.class, p.getBoundingBox().inflate(40), a -> a.getTags().contains("xw_mk_cam")).forEach(a -> a.discard());
            var a = net.minecraft.world.entity.EntityType.ARMOR_STAND.create(p.serverLevel());
            a.moveTo(p.getX() + dx, p.getY() + dy, p.getZ() + dz, yaw, pitch); a.setNoGravity(true); a.setInvisible(true); a.setYHeadRot(yaw);
            a.addTag("xw_mk_cam");
            p.serverLevel().addFreshEntity(a); id[0] = a.getId();
        }), 15);
        l.add(null, () -> {
            if (mc.level.getEntity(id[0]) instanceof net.minecraft.world.entity.decoration.ArmorStand a) {
                a.setYRot(yaw); a.yRotO = yaw; a.setXRot(pitch); a.xRotO = pitch; a.setYHeadRot(yaw); a.yHeadRotO = yaw; mc.setCameraEntity(a);
            }
            mc.options.setCameraType(CameraType.FIRST_PERSON); mc.options.hideGui = true;
        }, 5);
    }

    // ------------------------------------------------------------------ 1. falling after flight

    /** Per-tick recorder of the local player's fall (client side). */
    private static String rec;
    private static int recLeft, recTick;
    private static float healthAtStart;

    private static void record(String label, int ticks) { rec = label; recLeft = ticks; recTick = 0; }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || rec == null) return;
        var p = Minecraft.getInstance().player;
        if (p == null) return;
        Vec3 v = p.getDeltaMovement();
        log(String.format("fall %s t=%d y=%.2f vy=%.3f fallDist=%.2f onGround=%s flying=%s hp=%.1f", rec, recTick, p.getY(), v.y, p.fallDistance,
                p.onGround(), p.getAbilities().flying, p.getHealth()));
        recTick++;
        if (--recLeft <= 0 || p.onGround()) {
            log(String.format("fall %s landed=%s after %d ticks", rec, p.onGround(), recTick));
            rec = null;
        }
    }

    /** Takes off, climbs 14 blocks, stops flying and falls - logged tick by tick; the server's health a second after. */
    private static void flyAndDrop(Minecraft mc, Steps l, String label) {
        float[] hp = new float[1];
        l.add(null, () -> server(p -> {
            p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            p.setHealth(p.getMaxHealth());
            p.removeAllEffects();
            p.getAbilities().mayfly = true; p.getAbilities().flying = true; p.onUpdateAbilities();
            p.teleportTo(p.getX(), p.getY() + 14, p.getZ());
        }), 30);
        l.add(null, () -> { server(p -> hp[0] = p.getHealth()); var p = mc.player; p.getAbilities().flying = false; p.onUpdateAbilities(); record(label, 60); }, 80);
        l.add(null, () -> server(p -> log(String.format("fall %s server hp %.1f -> %.1f (fall damage %.1f)", label, hp[0], p.getHealth(), hp[0] - p.getHealth()))), 5);
    }

    private static void fall(Minecraft mc, Steps l) {
        double[] home = new double[3];
        character(l, CharacterType.MIKIKO);
        l.add(null, () -> { mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            server(p -> { levels(p, 20, 20, 21); home[0] = p.getX(); home[1] = p.getY(); home[2] = p.getZ(); }); }, 40);   // level 60: he can fly
        // a: a normal flight, landing
        flyAndDrop(mc, l, "fresh");
        // b: an aerial combo swing at someone in the air, then a trip to another dimension and back (a new client
        //    player - like going in and out of the Hekublade realm)
        int[] dummy = {-1};
        l.add(null, () -> server(p -> {
            p.getAbilities().mayfly = true; p.getAbilities().flying = true; p.onUpdateAbilities(); p.teleportTo(p.getX(), p.getY() + 4, p.getZ());
            p.setYRot(0); p.setXRot(0);
            var z = net.minecraft.world.entity.EntityType.HUSK.create(p.serverLevel());
            z.moveTo(p.getX(), p.getY() + 4, p.getZ() + 2.2, 180, 0); z.setNoAi(true); z.setNoGravity(true);
            z.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(500); z.setHealth(500);
            p.serverLevel().addFreshEntity(z); dummy[0] = z.getId();
        }), 20);
        l.add(null, () -> { var p = mc.player; p.setYRot(0); p.setXRot(0); p.getAbilities().flying = false; p.onUpdateAbilities();
            log("aerial swing at tickCount " + p.tickCount + ", target in reach " + (mc.level.getEntity(dummy[0]) != null));
            com.xenowarriors.client.mikiko.AerialCombo.swing(p); }, 30);
        l.add(null, () -> server(p -> { if (p.serverLevel().getEntity(dummy[0]) != null) p.serverLevel().getEntity(dummy[0]).discard();
            p.teleportTo(p.server.getLevel(net.minecraft.world.level.Level.NETHER), home[0], 80, home[2], 0, 0); }), 60);
        l.add(null, () -> server(p -> p.teleportTo(p.server.overworld(), home[0], home[1], home[2], 0, 0)), 60);
        l.add(null, () -> log("back in the overworld: new player tickCount " + mc.player.tickCount), 5);
        flyAndDrop(mc, l, "after_dimension");
        l.add(null, () -> server(p -> { p.setGameMode(net.minecraft.world.level.GameType.CREATIVE); p.setHealth(p.getMaxHealth()); }), 10);
    }

    // ------------------------------------------------------------------ 2. no sitting in menus

    private static void menuSit(Minecraft mc, Steps l) {
        character(l, CharacterType.MIKIKO);
        l.add(null, () -> { mc.setScreen(null);
            server(p -> { p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL); p.setHealth(p.getMaxHealth());
                p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE, 20 * 120, 4, false, false));
                p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, net.minecraft.world.item.ItemStack.EMPTY); p.setYRot(0); p.setXRot(10); });
            mc.player.setXRot(10F); mc.player.setYRot(0F); }, 20);
        // filmed from the front-left, so he's on the left of the picture and the inventory doesn't cover him
        camera(mc, l, 2.6, 0.9, 4.6, 150F, 8F);
        // control: standing around with no menu open - he sits down after 30 seconds
        l.add(null, () -> {}, 700);
        l.add("mikiko_menu_world_nomenu", () -> log("idle 35s, no menu: sitting " + sitting(mc)), 5);
        // a menu open: in the world behind it (the inventory), the character customization, and the character select's big preview
        l.add(null, () -> mc.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player)), 40);
        l.add("mikiko_menu_inventory", () -> { log("inventory open: sitting " + sitting(mc)); server(p -> log("server knows the menu is open: " + com.xenowarriors.mikiko.InMenu.open(p))); }, 5);
        l.add(null, () -> { mc.setScreen(null); mc.setCameraEntity(mc.player); }, 200);
        l.add(null, () -> log("menu closed for 10s: sitting " + sitting(mc)), 5);
        l.add(null, () -> com.xenowarriors.client.menu.CharacterCustomScreen.open(null), 40);
        l.add("mikiko_menu_custom", () -> log("character customization open: sitting " + sitting(mc)), 5);
        l.add(null, () -> mc.setScreen(new com.xenowarriors.client.screen.CharacterSelectScreen()), 40);
        l.add("mikiko_menu_select", () -> log("character select open: sitting " + sitting(mc)), 5);
        l.add(null, () -> { mc.setScreen(null); mc.setCameraEntity(mc.player); mc.options.hideGui = false; mc.options.setCameraType(CameraType.FIRST_PERSON);
            server(p -> p.setGameMode(net.minecraft.world.level.GameType.CREATIVE)); }, 5);
    }

    private static String sitting(Minecraft mc) {
        return String.format("%.2f", com.xenowarriors.client.mikiko.MikikoAnimations.sitWeight(mc.player));
    }

    // ------------------------------------------------------------------ 3. Hekublade Rain

    private static final List<Integer> DUMMIES = new ArrayList<>();
    private static int allyBot = -1, wolf = -1;

    private static String ult(ServerPlayer p) {
        var s = com.xenowarriors.skill.SkillManager.get(p, com.xenowarriors.skill.SkillManager.ULTIMATE_SLOT);
        return s == null ? "none" : s.getId();
    }

    /** Three tough husks around the centre (10 blocks ahead), a tamed wolf and one of his party bots standing among them. */
    private static void arena(ServerPlayer p, boolean allies) {
        var lvl = p.serverLevel();
        lvl.getEntitiesOfClass(net.minecraft.world.entity.monster.Husk.class, p.getBoundingBox().inflate(40)).forEach(e -> e.discard());
        DUMMIES.clear();
        Vec3 c = p.position().add(0, 0, 10);
        double[][] at = {{0, 0}, {2.5, 1.5}, {-3, -1}};
        for (double[] o : at) {
            var z = net.minecraft.world.entity.EntityType.HUSK.create(lvl);
            z.moveTo(c.x + o[0], c.y, c.z + o[1], 180, 0); z.setNoAi(true); z.setPersistenceRequired();
            z.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(1000); z.setHealth(1000);
            lvl.addFreshEntity(z); DUMMIES.add(z.getId());
        }
        if (!allies) return;
        var w = net.minecraft.world.entity.EntityType.WOLF.create(lvl);
        w.moveTo(c.x + 1, c.y, c.z - 1.5, 0, 0); w.tame(p); w.setOrderedToSit(true);
        lvl.addFreshEntity(w); wolf = w.getId();
        for (var b : com.xenowarriors.bot.PartyBots.bots(p)) b.discard();
        var b = com.xenowarriors.bot.PartyBots.spawn(p, CharacterType.RHALIX, null);
        if (b != null) { b.moveTo(c.x - 1, c.y, c.z + 1.2, 180, 0); b.setNoAi(true); allyBot = b.getId(); }
    }

    private static String hp(ServerPlayer p, int id) {
        return p.serverLevel().getEntity(id) instanceof LivingEntity e ? String.format("%.1f/%.1f", e.getHealth(), e.getMaxHealth()) : "gone";
    }

    private static void report(ServerPlayer p, String when) {
        StringBuilder sb = new StringBuilder("rain " + when + ": dummies");
        for (int id : DUMMIES) sb.append(' ').append(hp(p, id));
        sb.append("  wolf ").append(hp(p, wolf)).append("  ally bot ").append(hp(p, allyBot)).append("  player ").append(String.format("%.1f", p.getHealth()));
        log(sb.toString());
    }

    private static void rain(Minecraft mc, Steps l) {
        character(l, CharacterType.MIKIKO);
        l.add(null, () -> server(p -> {
            p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            p.setYRot(0); p.setXRot(20);
            levels(p, 100, 100, 103);                           // Character Level 299
        }), 30);
        l.add(null, () -> server(p -> {
            com.xenowarriors.skill.SkillManager.setSlot(p, com.xenowarriors.skill.SkillManager.ULTIMATE_SLOT, 1);
            log("level " + com.xenowarriors.stats.Stats.characterLevel(p) + ": picking Hekublade Rain -> ultimate slot holds " + ult(p));
        }), 20);
        l.add(null, () -> { com.xenowarriors.client.menu.CharacterCustomScreen.open(null);
            if (mc.screen instanceof com.xenowarriors.client.menu.CharacterCustomScreen c) c.tab(1); }, 20);
        l.add(null, () -> { if (mc.screen != null) for (int i = 0; i < 6; i++) mc.screen.mouseScrolled(mc.getWindow().getGuiScaledWidth() / 2.0, mc.getWindow().getGuiScaledHeight() / 2.0, -1); }, 20);
        l.add("mikiko_rain_locked_299", () -> {}, 5);
        l.add(null, () -> { mc.setScreen(null); server(p -> levels(p, 101, 101, 102)); }, 30);   // Character Level 300
        l.add(null, () -> server(p -> {
            com.xenowarriors.skill.SkillManager.setSlot(p, com.xenowarriors.skill.SkillManager.ULTIMATE_SLOT, 1);
            log("level " + com.xenowarriors.stats.Stats.characterLevel(p) + ": picking Hekublade Rain -> ultimate slot holds " + ult(p));
        }), 20);
        l.add(null, () -> { com.xenowarriors.client.menu.CharacterCustomScreen.open(null);
            if (mc.screen instanceof com.xenowarriors.client.menu.CharacterCustomScreen c) c.tab(1); }, 20);
        l.add(null, () -> { if (mc.screen != null) for (int i = 0; i < 6; i++) mc.screen.mouseScrolled(mc.getWindow().getGuiScaledWidth() / 2.0, mc.getWindow().getGuiScaledHeight() / 2.0, -1); }, 20);
        l.add("mikiko_rain_unlocked_300", () -> log("client: ultimate choice " + com.xenowarriors.client.ClientSkillState.ultimate() + " -> "
                + com.xenowarriors.skill.SkillPool.inSlot(mc.player, com.xenowarriors.client.ClientSkillState.fullLoadout(), 4).getId()), 5);
        l.add(null, () -> { mc.setScreen(null); mc.options.hideGui = false; mc.options.setCameraType(CameraType.FIRST_PERSON); }, 10);
        l.add("mikiko_rain_hud", () -> {}, 5);
        // the cast: dummies, a tamed wolf and a party bot in the area
        l.add(null, () -> server(p -> { arena(p, true); com.xenowarriors.entity.HekubladeRainEntity.LOG = true;
            p.getCapability(com.xenowarriors.character.ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> { d.setKarma(d.karmaMax); java.util.Arrays.fill(d.cooldownEnd, 0); }); }), 40);
        camera(mc, l, 9, 7, 1, 60F, 26F);
        l.add(null, () -> server(p -> { report(p, "before"); p.setYRot(0); p.setXRot(20);
            float k = p.getCapability(com.xenowarriors.character.ModCapabilities.PLAYER_CHARACTER).map(d -> d.getKarma()).orElse(0F);
            com.xenowarriors.skill.SkillManager.useSkill(p, com.xenowarriors.skill.SkillManager.ULTIMATE_SLOT);
            float k2 = p.getCapability(com.xenowarriors.character.ModCapabilities.PLAYER_CHARACTER).map(d -> d.getKarma()).orElse(0F);
            log(String.format("cast %s: karma %.0f -> %.0f, rain entities %d", ult(p), k, k2,
                    p.serverLevel().getEntitiesOfClass(com.xenowarriors.entity.HekubladeRainEntity.class, p.getBoundingBox().inflate(40)).size()));
        }), 8);
        l.add("mikiko_rain_ring", () -> {}, 14);
        l.add("mikiko_rain_wave1", () -> {}, 12);
        l.add("mikiko_rain_waves", () -> {}, 22);
        l.add("mikiko_rain_giant", () -> {}, 5);
        l.add("mikiko_rain_finale", () -> {}, 30);
        l.add(null, () -> server(p -> report(p, "after (player cast)")), 5);
        // a Mikiko bot with the same pick: its ultimate is the rain too, and it doesn't hurt its owner standing in it
        l.add(null, () -> server(p -> {
            com.xenowarriors.character.CharacterManager.setCharacter(p, CharacterType.RHALIX);
        }), 30);
        l.add(null, () -> server(p -> {
            arena(p, false);
            for (var b : com.xenowarriors.bot.PartyBots.bots(p)) b.discard();
            var b = com.xenowarriors.bot.PartyBots.spawn(p, CharacterType.MIKIKO, null);
            if (b == null) { log("bot: could not spawn a Mikiko bot"); return; }
            b.moveTo(p.getX() + 2, p.getY(), p.getZ(), 0, 0); b.setNoAi(true); allyBot = b.getId(); wolf = -1;
            int[] lo = p.getCapability(com.xenowarriors.character.ModCapabilities.PLAYER_CHARACTER).map(d -> d.loadoutWithUlt(CharacterType.MIKIKO)).orElse(null);
            var sk = com.xenowarriors.skill.SkillPool.inSlot(CharacterType.MIKIKO, lo, 4);
            log("bot: Mikiko bot's ultimate (PartyBots.trySkill -> SkillPool.inSlot) = " + (sk == null ? "none" : sk.getId()) + " loadout " + java.util.Arrays.toString(lo));
            p.teleportTo(p.getX(), p.getY(), p.getZ() + 10);          // the owner stands in the middle of it
            try {
                var m = com.xenowarriors.bot.PartyBots.class.getDeclaredMethod("fake", com.xenowarriors.bot.PartyBotEntity.class);
                m.setAccessible(true);
                var f = (ServerPlayer) m.invoke(null, b);
                f.moveTo(b.getX(), b.getY(), b.getZ(), 0, 4); f.setYHeadRot(0);
                log("bot cast: " + (sk != null && sk.use(f)));
            } catch (Throwable t) { log("bot cast failed: " + t); }
        }), 100);
        l.add(null, () -> server(p -> { report(p, "after (bot cast, owner in the middle)"); com.xenowarriors.entity.HekubladeRainEntity.LOG = false;
            for (var b : com.xenowarriors.bot.PartyBots.bots(p)) b.discard();
            p.setGameMode(net.minecraft.world.level.GameType.CREATIVE); }), 10);
        l.add(null, () -> { mc.setCameraEntity(mc.player); mc.options.hideGui = false; }, 5);
    }

    // ------------------------------------------------------------------ 4. Scatter ride by blade level

    private static void ride(Minecraft mc, Steps l) {
        character(l, CharacterType.MIKIKO);
        double[] home = new double[3];
        l.add(null, () -> server(p -> { home[0] = p.getX(); home[1] = p.getY(); home[2] = p.getZ(); }), 5);
        for (int lv : new int[]{1, 20, 50, 200}) {
            l.add(null, () -> server(p -> p.teleportTo(home[0], home[1], home[2])), 10);   // every ride from the same spot
            int[] start = {-1};
            l.add(null, () -> server(p -> {
                p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
                p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE, 20 * 60, 4, false, false));
                p.getCapability(com.xenowarriors.character.ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> d.bladeLevel = lv);
                p.teleportTo(p.getX(), Math.max(p.getY() + 30, 200), p.getZ());   // up high above any hills, so he has room to ride
                p.setYRot(-90); p.setXRot(0);                                      // (heading +x)
                var b = com.xenowarriors.entity.HekubladeShardEntity.create(p, 0);
                p.level().addFreshEntity(b);
                b.surf(p);
                start[0] = p.server.getTickCount();
                log("ride blade level " + lv + ": planned " + b.rideMax() + " ticks (" + com.xenowarriors.entity.HekubladeShardEntity.rideTicks(lv) / 20F + "s)");
                // count the ticks he's carried
                int[] ticks = {0};
                for (int t = 1; t <= 700; t++) { int tt = t; com.xenowarriors.skill.ServerScheduler.later(t, () -> {
                    if (ticks[0] < 0) return;
                    if (p.getVehicle() instanceof com.xenowarriors.entity.HekubladeShardEntity) ticks[0]++;
                    else { log("ride blade level " + lv + ": rode " + ticks[0] + " ticks (" + ticks[0] / 20F + "s), ended at y=" + Math.round(p.getY()) + " " + (p.horizontalCollision ? "(hit something)" : "")); ticks[0] = -1; }
                }); }
            }), 25);
            if (lv == 50) l.add("mikiko_ride_hud", () -> { mc.options.hideGui = false; mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); }, 5);
            l.add(null, () -> {}, Math.min(640, com.xenowarriors.entity.HekubladeShardEntity.rideTicks(lv) + 40));
            l.add(null, () -> server(p -> { p.stopRiding(); p.teleportTo(p.getX(), p.getY() + 1, p.getZ()); }), 10);
        }
        l.add(null, () -> server(p -> p.setGameMode(net.minecraft.world.level.GameType.CREATIVE)), 20);
    }

    // ------------------------------------------------------------------ 5. Kaeru's hood

    private static void hood(Minecraft mc, Steps l) {
        character(l, CharacterType.KAERU);
        l.add(null, () -> { mc.setScreen(null); mc.setCameraEntity(mc.player); mc.options.hideGui = true;
            server(p -> { com.xenowarriors.kaeru.KaeruHood.set(p, false); p.setYRot(0); p.setXRot(8); }); mc.player.setYRot(0); mc.player.setXRot(8); }, 30);
        String[] views = {"front", "side", "back"};
        for (boolean up : new boolean[]{false, true}) {
            if (up) {
                // put it up from the Outfits tab, like a player would
                l.add(null, () -> { com.xenowarriors.client.menu.CharacterCustomScreen.open(null); if (mc.screen instanceof com.xenowarriors.client.menu.CharacterCustomScreen c) c.tab(2); }, 25);
                l.add("mikiko_hood_menu_down", () -> {}, 5);
                l.add(null, () -> {
                    if (mc.screen instanceof com.xenowarriors.client.menu.CharacterCustomScreen c) c.testClickHood();   // (the card after her one outfit)
                }, 30);
                l.add("mikiko_hood_menu_up", () -> server(p -> log("server saved hood up: " + com.xenowarriors.kaeru.KaeruHood.up(p))), 5);
                l.add(null, () -> mc.setScreen(null), 25);
            }
            for (String v : views) {
                if (v.equals("side")) camera(mc, l, 3.4, 0.4, 0.4, 90F, 6F);       // filmed from her left (she faces +z)
                else l.add(null, () -> { mc.setCameraEntity(mc.player); mc.options.setCameraType(v.equals("back") ? CameraType.THIRD_PERSON_BACK : CameraType.THIRD_PERSON_FRONT);
                    mc.player.setYRot(0F); mc.player.yBodyRot = 0F; mc.player.yHeadRot = 0F; mc.player.setXRot(8F); }, 25);
                l.add("mikiko_hood_" + (up ? "up" : "down") + "_" + v, () -> log("hood " + (up ? "up" : "down") + " " + v + ": client up=" + com.xenowarriors.client.mikiko.HoodClient.up(mc.player)), 5);
            }
            l.add(null, () -> mc.setCameraEntity(mc.player), 5);
        }
        // the hood key puts it back down (and it stays saved through a relog: the server's copy)
        l.add(null, () -> com.xenowarriors.network.ModNetwork.sendToServer(new com.xenowarriors.network.HoodTogglePacket(false)), 20);
        l.add(null, () -> server(p -> log("hood key -> server saved hood up: " + com.xenowarriors.kaeru.KaeruHood.up(p))), 25);
        l.add(null, () -> log("hood key -> client up=" + com.xenowarriors.client.mikiko.HoodClient.up(mc.player)), 5);
        l.add(null, () -> { mc.options.hideGui = false; mc.options.setCameraType(CameraType.FIRST_PERSON); }, 5);
    }
}

package com.xenowarriors.client.test;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.client.combo.ComboClient;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * UI test "rhalixcombo": Rhalix's own moves, filmed from the side (like the reference videos) on a flat stone floor:
 * the ground string on a dummy (and a zombie, and done by a Rhalix bot), the Kishi Warp (and its silent cooldown), the
 * air string, the Ki Burst and Rising Leap holds, and a ground-and-air mix route. Log lines "XW-TEST rhalixcombo ...";
 * shots "rc_&lt;scene&gt;_NN" one per tick (when the game keeps up), each logged with what he was doing then.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class RhalixComboTests {
    private RhalixComboTests() {}

    static void log(String s) { XenoWarriors.LOGGER.info("XW-TEST rhalixcombo " + s); }

    private static ServerPlayer sp() {
        Minecraft mc = Minecraft.getInstance();
        var s = mc.getSingleplayerServer();
        return s == null || mc.player == null ? null : s.getPlayerList().getPlayer(mc.player.getUUID());
    }

    private static void server(java.util.function.Consumer<ServerPlayer> f) {
        var s = Minecraft.getInstance().getSingleplayerServer();
        if (s != null) s.execute(() -> { ServerPlayer p = sp(); if (p != null) try { f.accept(p); } catch (Throwable t) { XenoWarriors.LOGGER.error("XW-TEST rhalixcombo server step failed", t); } });
    }

    // chat / action bar messages seen (the Kishi Warp's cooldown must never say anything)
    private static int chats;
    private static boolean countChats;

    @SubscribeEvent
    public static void onChat(ClientChatReceivedEvent event) {
        if (countChats) { chats++; log("chat/hud message during kishi test: " + event.getMessage().getString()); }
    }

    // ------------------------------------------------------------------ slow motion (the CI renders ~2 fps at 1080p)

    /** Extra ms the server sleeps each tick, and the client's ms per tick, while filming (both slowed alike). */
    private static volatile int serverSleep;

    /** Slow the whole game down by this factor (1 = normal), server and client together, so a shot lands every tick. */
    static void slow(Minecraft mc, int factor) {
        serverSleep = factor <= 1 ? 0 : 50 * (factor - 1);
        try {
            for (var f : Minecraft.class.getDeclaredFields()) {
                if (f.getType() != net.minecraft.client.Timer.class) continue;
                f.setAccessible(true);
                Object timer = f.get(mc);
                for (var g : net.minecraft.client.Timer.class.getDeclaredFields()) {
                    if (g.getType() == float.class && java.lang.reflect.Modifier.isFinal(g.getModifiers()) && !java.lang.reflect.Modifier.isStatic(g.getModifiers())) {
                        g.setAccessible(true);
                        g.setFloat(timer, 50F * Math.max(1, factor));
                        log("slow motion x" + factor + " (client " + g.getName() + "=" + g.getFloat(timer) + " ms/tick, server +" + serverSleep + " ms)");
                    }
                }
            }
        } catch (Throwable t) { log("slow motion failed: " + t); }
    }

    @SubscribeEvent
    public static void onServerTick(net.minecraftforge.event.TickEvent.ServerTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
        // the targets never die (kept under 150 health so they're not counted as bosses)
        var l = event.getServer().overworld();
        if (target >= 0 && l.getEntity(target) instanceof LivingEntity e && e.getTags().contains("xw_rc")) e.setHealth(e.getMaxHealth());
        int ms = serverSleep;
        if (ms > 0) {                       // (sleeps what's left of the slowed tick, so server and client keep the same pace)
            long left = ms + 50 - (System.nanoTime() - tickStart) / 1_000_000L;
            if (left > 0) try { Thread.sleep(left); } catch (InterruptedException ignored) {}
        }
    }

    private static long tickStart;

    @SubscribeEvent
    public static void onServerTickStart(net.minecraftforge.event.TickEvent.ServerTickEvent event) {
        if (event.phase == net.minecraftforge.event.TickEvent.Phase.START) tickStart = System.nanoTime();
    }

    private static final double[] HOME = new double[3];
    private static int target = -1, cam = -1, bot = -1;

    /** Clears the stage and puts him back at home, facing +z (south). */
    private static void reset(ServerPlayer p) {
        var l = p.serverLevel();
        l.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(40), e -> e.getTags().contains("xw_rc")).forEach(e -> e.discard());
        p.teleportTo(l, HOME[0], HOME[1], HOME[2], 0F, 8F);
        p.setDeltaMovement(Vec3.ZERO);
        p.setHealth(p.getMaxHealth());
    }

    /** A target: a dummy (AI on so hits move it, but it can't walk or fight back) or a real zombie. */
    private static LivingEntity mob(ServerPlayer p, EntityType<? extends Mob> type, double dz, boolean dummy, double hp) {
        Mob m = type.create(p.serverLevel());
        m.moveTo(HOME[0], HOME[1], HOME[2] + dz, 180F, 0F);
        m.setYHeadRot(180F); m.yBodyRot = 180F;
        m.addTag("xw_rc");
        m.getAttribute(Attributes.MAX_HEALTH).setBaseValue(hp); m.setHealth((float) hp);
        m.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0);
        m.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 20 * 600, 0, false, false));
        m.setPersistenceRequired();
        if (dummy) {
            m.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
            m.goalSelector.removeAllGoals(g -> true);
            m.targetSelector.removeAllGoals(g -> true);
        }
        p.serverLevel().addFreshEntity(m);
        target = m.getId();
        return m;
    }

    /** A side camera (like the videos): off to his left (+x), looking back across at him and the target. */
    private static void camera(Minecraft mc, RhalixTests.Steps l, double side, double dz, double up) {
        l.add(null, () -> server(p -> {
            p.serverLevel().getEntitiesOfClass(ArmorStand.class, p.getBoundingBox().inflate(60), a -> a.getTags().contains("xw_rc_cam")).forEach(a -> a.discard());
            var a = EntityType.ARMOR_STAND.create(p.serverLevel());
            a.moveTo(HOME[0] + side, HOME[1] + up, HOME[2] + dz, 90F, 12F); a.setNoGravity(true); a.setInvisible(true); a.setYHeadRot(90F);
            a.addTag("xw_rc_cam");
            p.serverLevel().addFreshEntity(a); cam = a.getId();
        }), 8);
        l.add(null, () -> {
            if (mc.level.getEntity(cam) instanceof ArmorStand a) {
                a.setYRot(90F); a.yRotO = 90F; a.setXRot(12F); a.xRotO = 12F; a.setYHeadRot(90F); a.yHeadRotO = 90F; mc.setCameraEntity(a);
            }
            mc.options.setCameraType(CameraType.FIRST_PERSON); mc.options.hideGui = true;
            if (mc.screen != null) mc.setScreen(null);
            mc.gui.getChat().clearMessages(false);
        }, 4);
    }

    private static void face(Minecraft mc) {
        var p = mc.player;
        p.setYRot(0F); p.yRotO = 0F; p.setXRot(8F); p.xRotO = 8F; p.yBodyRot = 0F; p.yBodyRotO = 0F; p.yHeadRot = 0F; p.yHeadRotO = 0F;
    }

    /** Where everyone is and what he's doing (logged with every shot, for the sheets). */
    private static void state(Minecraft mc, String shot) {
        var p = mc.player;
        float[] pl = com.xenowarriors.client.RhalixComboAnim.playing(p, 0F);
        var sw = ComboClient.swing(p);
        String t = mc.level.getEntity(target) instanceof LivingEntity e
                ? String.format("target dz=%.2f y=%+.2f down=%.2f hp=%.1f", e.getZ() - p.getZ(), e.getY() - HOME[1], com.xenowarriors.client.KnockdownClient.down(e, 0F), e.getHealth()) : "target -";
        log(String.format("frame %s tick=%d move=%s prog=%.2f style=%s player dz=%.2f y=%+.2f charge=%d crouch=%.0f fx=%d %s", shot, p.tickCount,
                pl == null ? "-" : pl[0] < 0 ? "heavy" : String.valueOf((int) pl[0]), pl == null ? -1F : pl[1], sw == null ? "-" : sw.style(),
                p.getZ() - HOME[2], p.getY() - HOME[1], ComboClient.charge(), com.xenowarriors.client.KishiWarpClient.crouchTicks(p, 0F),
                com.xenowarriors.client.RhalixComboFx.live(), t));
    }

    /** Shots one per tick: each frame's action logs the state the previous shot caught, then does the input for this tick. */
    private static void film(Minecraft mc, RhalixTests.Steps l, String scene, int frames, java.util.function.IntConsumer input) {
        l.add(null, () -> slow(mc, SLOW), 2);
        for (int f = 0; f < frames; f++) {
            int ff = f;
            l.add(String.format("rc_%s_%02d", scene, f), () -> {
                if (ff > 0) state(mc, String.format("rc_%s_%02d", scene, ff - 1));
                input.accept(ff);
            }, 0);
        }
        l.add(null, () -> { state(mc, String.format("rc_%s_%02d", scene, frames - 1)); slow(mc, 1); }, 1);
    }

    private static final int SLOW = 16;

    private static void run(Minecraft mc, boolean on) {
        var o = mc.options;
        o.keyUp.setDown(on);
        o.keySprint.setDown(on);
        if (on) mc.player.setSprinting(true);
    }

    public static void run(Minecraft mc, String focus, RhalixTests.Steps l) {
        l.add(null, () -> { mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE); mc.getToasts().clear(); mc.setScreen(null); }, 5);
        l.add(null, () -> server(p -> com.xenowarriors.character.CharacterManager.setCharacter(p, CharacterType.RHALIX)), 40);
        l.add(null, () -> server(p -> {
            p.getCapability(com.xenowarriors.character.ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> d.outfit = 0);
            com.xenowarriors.character.CharacterManager.syncToTrackingAndSelf(p);
            p.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            p.setGameMode(GameType.SURVIVAL);
            p.getAbilities().invulnerable = true; p.onUpdateAbilities();
            var l2 = p.serverLevel();
            l2.setDayTime(6000);
            l2.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false, l2.getServer());
            l2.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING).set(false, l2.getServer());
            // a flat stone floor to fight on
            BlockPos c = p.blockPosition();
            for (int x = -12; x <= 12; x++) for (int z = -8; z <= 24; z++) {
                l2.setBlock(c.offset(x, -1, z), Blocks.SMOOTH_STONE.defaultBlockState(), 2);
                for (int y = 0; y < 8; y++) l2.setBlock(c.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
            }
            HOME[0] = c.getX() + 0.5; HOME[1] = c.getY(); HOME[2] = c.getZ() + 0.5;
            reset(p);
            log("setup: rhalix, survival (invulnerable), flat floor at y=" + c.getY() + " style for bare hands = "
                    + com.xenowarriors.combat.ComboStyle.of(p, ItemStack.EMPTY, CharacterType.RHALIX));
        }), 30);

        // ---- 1. the ground string on a dummy 4 blocks off (the Shadow Rush closes the gap), filmed every tick
        l.add(null, () -> server(p -> { reset(p); mob(p, EntityType.HUSK, 4.0, true, 120); }), 12);
        camera(mc, l, 5.2, 2.2, 0.35);
        l.add(null, () -> { face(mc); log("scene ground: string on a dummy 4 blocks away"); }, 3);
        film(mc, l, "ground", 84, f -> { face(mc); if (f % 2 == 0 && f < 70) ComboClient.press(false); });
        l.add(null, () -> server(p -> {
            if (p.serverLevel().getEntity(target) instanceof LivingEntity e)
                log(String.format("ground dummy after the string: hp %.1f/%.0f, flown %.1f blocks, staggered %s", e.getHealth(), e.getMaxHealth(), e.getZ() - HOME[2], com.xenowarriors.combat.Stagger.staggered(e)));
        }), 20);

        // ---- 1b. stills every other tick (10 fps) of one more full string, the same angle
        l.add(null, () -> server(p -> { reset(p); mob(p, EntityType.HUSK, 4.0, true, 120); }), 30);
        l.add(null, () -> { face(mc); slow(mc, SLOW); }, 3);
        for (int f = 0; f < 40; f++) {
            int ff = f;
            l.add(String.format("rc_still_%02d", f), () -> { face(mc); state(mc, "still-" + ff); if (ff < 34) ComboClient.press(false); }, 1);
        }
        l.add(null, () -> slow(mc, 1), 2);

        // ---- 2. a regular zombie (it fights back - he's invulnerable for the test)
        l.add(null, () -> server(p -> { reset(p); mob(p, EntityType.ZOMBIE, 3.5, false, 120); }), 12);
        l.add(null, () -> { face(mc); log("scene zombie"); }, 3);
        film(mc, l, "zombie", 40, f -> { face(mc); if (f % 2 == 0 && f < 64) ComboClient.press(false); });

        // ---- 3. a Rhalix bot doing his string on a dummy zombie
        l.add(null, () -> server(p -> {
            reset(p);
            var b = com.xenowarriors.bot.PartyBots.spawn(p, CharacterType.RHALIX, null);
            if (b == null) { log("bot: none spawned"); return; }
            b.moveTo(HOME[0] - 3, HOME[1], HOME[2], 0F, 0F); b.addTag("xw_rc"); bot = b.getId();
            b.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            var z = mob(p, EntityType.ZOMBIE, 3.0, true, 120);
            z.moveTo(HOME[0] - 3, HOME[1], HOME[2] + 3.5, 180F, 0F);
            b.setTarget(z);
            log("bot spawned " + b.character() + " style " + com.xenowarriors.combat.ComboStyle.of(b, b.getMainHandItem(), b.character()));
        }), 10);
        camera(mc, l, 2.2, 2.0, 0.35);
        film(mc, l, "bot", 70, f -> {
            if (f % 10 == 0) server(p -> { if (p.serverLevel().getEntity(bot) instanceof com.xenowarriors.bot.PartyBotEntity b && p.serverLevel().getEntity(target) instanceof LivingEntity z) b.setTarget(z); });
        });

        // ---- 4. the Kishi Warp: running, attack held, at a dummy 10 blocks off; then again inside 5 s (cooldown), then after
        for (int round = 0; round < 3; round++) {
            int rr = round;
            l.add(null, () -> server(p -> { reset(p); log("kishi round " + rr + " starts, client says ready " + com.xenowarriors.client.KishiWarpClient.ready()); if (rr == 0) mob(p, EntityType.HUSK, 12.0, true, 120);
                else if (p.serverLevel().getEntity(target) instanceof LivingEntity e) { e.teleportTo(HOME[0], HOME[1], HOME[2] + 12.0); e.setDeltaMovement(Vec3.ZERO); }
                else mob(p, EntityType.HUSK, 12.0, true, 120); }), rr == 2 ? 110 : 3);
            if (round != 1) camera(mc, l, 9.0, 6.0, 0.8);
            l.add(null, () -> { face(mc); countChats = true; chats = 0; log("scene kishi round " + rr + " (ready " + com.xenowarriors.client.KishiWarpClient.ready() + ")"); }, 2);
            film(mc, l, "kishi" + rr, rr == 0 ? 42 : rr == 1 ? 30 : 80, f -> {
                face(mc);
                if (f == 0) run(mc, true);
                if (f == 4) ComboClient.testHold(10);
                if (f == 14) run(mc, false);
            });
            l.add(null, () -> { run(mc, false); log("kishi round " + rr + ": chat/hud messages " + chats); countChats = false; }, 4);
        }

        // ---- 5. the air string: the dummy thrown up, he jumps and attacks
        l.add(null, () -> server(p -> { reset(p); mob(p, EntityType.HUSK, 2.2, true, 120); }), 12);
        camera(mc, l, 5.5, 1.5, 1.2);
        l.add(null, () -> { face(mc); server(p -> { if (p.serverLevel().getEntity(target) instanceof LivingEntity e) { e.setDeltaMovement(0, 0.85, 0); e.hurtMarked = true; } }); }, 3);
        film(mc, l, "air", 70, f -> {
            face(mc);
            if (f == 1) mc.player.jumpFromGround();
            if (f >= 3 && f % 3 == 0 && f < 50) ComboClient.press(false);
        });

        // ---- 6. Ki Burst (half hold) on a knocked-down dummy, then Rising Leap (full hold) at one in the air
        l.add(null, () -> server(p -> { reset(p); mob(p, EntityType.HUSK, 2.0, true, 120); }), 12);
        camera(mc, l, 5.2, 1.6, 0.35);
        l.add(null, () -> { face(mc); server(p -> { if (p.serverLevel().getEntity(target) instanceof LivingEntity e) com.xenowarriors.anim.Poses.play(e, com.xenowarriors.anim.Poses.KNOCKDOWN, 40); }); }, 6);
        film(mc, l, "half", 40, f -> { face(mc); if (f == 0) ComboClient.testHold(10); });
        l.add(null, () -> server(p -> { reset(p); mob(p, EntityType.HUSK, 2.6, true, 120); }), 20);
        l.add(null, () -> face(mc), 2);
        film(mc, l, "hold", 70, f -> {
            face(mc);
            if (f == 0) ComboClient.testHold(22);
            if (f == 6) server(p -> { if (p.serverLevel().getEntity(target) instanceof LivingEntity e) { e.setDeltaMovement(0, 1.05, 0); e.hurtMarked = true; } });
            if (f >= 30 && f % 3 == 0 && f < 52) ComboClient.press(false);
        });

        // ---- 7. a ground-and-air mix (after the "Mix ground & air" video): dash in, the string, dash, Ki Burst, the
        //         grab and launcher, jump into the air string and its spike, then Ki Burst again and back on the ground
        l.add(null, () -> server(p -> { reset(p); mob(p, EntityType.HUSK, 6.0, true, 140); }), 12);
        camera(mc, l, 7.0, 4.0, 0.9);
        l.add(null, () -> { face(mc); log("scene mix"); }, 2);
        int[] route = new int[400];
        // 0 nothing, 1 tap, 2 half hold, 3 full hold, 4 run on, 5 run off, 6 jump
        java.util.function.BiConsumer<Integer, Integer> at = (t, a) -> route[t] = a;
        at.accept(0, 4); at.accept(3, 5);
        for (int i = 0; i < 6; i++) at.accept(5 + i * 4, 1);                  // D x6
        at.accept(40, 4); at.accept(43, 5);                                    // dash
        at.accept(46, 2);                                                       // D half (pop-up)
        at.accept(62, 1); at.accept(66, 1);                                    // grab, launcher
        at.accept(70, 6); at.accept(72, 1); at.accept(76, 1); at.accept(80, 1); at.accept(84, 1); at.accept(88, 1);   // jump, air x5 (ends in the spike)
        at.accept(112, 2);                                                      // land: D half again
        at.accept(128, 1); at.accept(132, 1); at.accept(136, 1);              // and on with the string
        film(mc, l, "mix", 150, f -> {
            face(mc);
            switch (route[f]) {
                case 1 -> { ComboClient.press(false); log("mix input " + f + ": D"); }
                case 2 -> { ComboClient.testHold(10); log("mix input " + f + ": D half"); }
                case 3 -> { ComboClient.testHold(22); log("mix input " + f + ": D hold"); }
                case 4 -> { run(mc, true); log("mix input " + f + ": dash"); }
                case 5 -> run(mc, false);
                case 6 -> { mc.player.jumpFromGround(); log("mix input " + f + ": jump"); }
                default -> {}
            }
        });
        l.add(null, () -> server(p -> {
            if (p.serverLevel().getEntity(target) instanceof LivingEntity e)
                log(String.format("mix dummy after: hp %.1f/%.0f staggered %s immune %s", e.getHealth(), e.getMaxHealth(), com.xenowarriors.combat.Stagger.staggered(e), com.xenowarriors.combat.Stagger.immune(e)));
            // balance: the multipliers the string's hits get with bare hands (attack speed 4)
            var st = com.xenowarriors.combat.ComboStyle.RHALIX;
            StringBuilder s = new StringBuilder();
            for (int h = 0; h < st.length(); h++) s.append(String.format("%d:%.2f ", h, st.multiplier(h, false, 4.0)));
            s.append(String.format("burst:%.2f leap:%.2f", st.multiplier(com.xenowarriors.combat.ComboStyle.BURST, false, 4.0), st.multiplier(com.xenowarriors.combat.ComboStyle.LEAP, false, 4.0)));
            var air = com.xenowarriors.combat.ComboStyle.RHALIX_AIR;
            s.append(" | air ");
            for (int h = 0; h < air.length(); h++) s.append(String.format("%d:%.2f ", h, air.multiplier(h, false, 4.0)));
            log("multipliers " + s);
            float t = 0, w = 0;
            for (int h = 0; h < st.length(); h++) { t += com.xenowarriors.combat.ComboStyle.cancel(st.duration(h, false)); w += st.multiplier(h, false, 4.0); }
            log(String.format("ground string DPS vs vanilla fists (4/s): %.2fx over %.0f ticks", (w / (t / 20F)) / 4.0, t));
        }), 10);

        l.add(null, () -> { run(mc, false); mc.setCameraEntity(mc.player); mc.options.hideGui = false; log("done"); }, 5);
    }
}

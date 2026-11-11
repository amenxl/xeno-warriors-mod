package com.xenowarriors.client.test;

import com.xenowarriors.boss.DragonBombEntity;
import com.xenowarriors.boss.XenoDragonEntity;
import com.xenowarriors.boss.XenoDragons;
import com.xenowarriors.character.CharacterType;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;
import java.util.function.Consumer;

/**
 * UI tests for the Xeno Dragon. Focus "dmzxenodragon..." (with DragonMineZ): stills of every form and the Dragon
 * Bomb, then in the End: a /killed dragon and the real one beaten -> a Xeno Dragon each time, the exit portal; the
 * forms one by one (speed each time), God -> Ultra Instinct, the Dragon Bomb thrown and interrupted, dodges, the
 * final defeat and its loot (once). Focus "xenodragon..." (no DragonMineZ): the dragon dies, no Xeno Dragon.
 * Results: "XW-TEST xenodragon ...".
 */
public final class XenoDragonTests {
    private XenoDragonTests() {}

    public static boolean handles(String focus) { return focus != null && (focus.startsWith("dmzxenodragon") || focus.startsWith("xenodragon")); }

    private static DmzTests.Adder S;
    private static Consumer<Consumer<ServerPlayer>> SERVER;
    private static Consumer<String> LOG;
    private static int pass, fail, dumped;
    private static final List<String> FAILED = new ArrayList<>();
    private static final int[] ID = {-1}, CAM = {-1};
    private static Vec3 AT;
    private static boolean guarded;

    private static void log(String s) { LOG.accept("xenodragon " + s); }
    private static void server(Consumer<ServerPlayer> f) { SERVER.accept(p -> { try { f.accept(p); } catch (Throwable t) { check("server step", false, String.valueOf(t)); com.xenowarriors.XenoWarriors.LOGGER.error("XW-TEST xenodragon", t); } }); }
    private static void step(String shot, Runnable a, int delay) { S.add(shot, a, delay); }

    private static void check(String what, boolean ok, String detail) {
        if (ok) pass++; else { fail++; FAILED.add(what); }
        log("CHECK " + what + ": " + detail + " -> " + (ok ? "PASS" : "FAIL"));
    }

    private static void dump() {
        synchronized (XenoDragons.LOG) { for (int i = dumped; i < XenoDragons.LOG.size(); i++) log("xdlog " + XenoDragons.LOG.get(i)); dumped = XenoDragons.LOG.size(); }
    }

    private static String f(double d) { return String.format(Locale.ROOT, "%.3f", d); }

    public static void build(Minecraft mc, String focus, DmzTests.Adder steps, Consumer<Consumer<ServerPlayer>> server, Consumer<CharacterType> character, Consumer<String> log) {
        S = steps; SERVER = server; LOG = log;
        boolean dmz = focus.startsWith("dmz");
        step(null, () -> { mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE); mc.getToasts().clear(); mc.setScreen(null); character.accept(CharacterType.MIKIKO); }, 60);
        step(null, () -> { noDmzScreen(mc); server(p -> {
            if (dmz) DmzTests.make(p, "saiyan");
            AT = com.xenowarriors.debug.SkillTestRunner.arena(p);
            p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            protect(p);
            p.level().getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false, p.server);
            p.serverLevel().setDayTime(6000);
            log("setup dmz=" + XenoDragons.dmz() + " enabled=" + XenoDragons.enabled());
        }); }, 40);
        guard();
        if (dmz) stills(mc);
        end(mc, dmz);
        if (dmz) fight(mc);
        step(null, () -> server(p -> { dump(); log("summary pass=" + pass + " fail=" + fail + (FAILED.isEmpty() ? "" : " failed=" + FAILED)); log("done"); }), 20);
    }

    private static void noDmzScreen(Minecraft mc) {
        if (mc.screen != null && mc.screen.getClass().getName().startsWith("com.dragonminez")) mc.setScreen(null);
    }

    private static void protect(ServerPlayer p) {
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20 * 60 * 30, 3, false, false));
        p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20 * 60 * 30, 5, false, false));
        p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 20 * 60 * 30, 0, false, false));
        p.setHealth(p.getMaxHealth());
    }

    /** The test player can't die (the boss is meant to hit hard). */
    private static void guard() {
        if (guarded) return;
        guarded = true;
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.LOWEST, false, net.minecraftforge.event.entity.living.LivingDamageEvent.class, e -> {
            if (e.getEntity() instanceof ServerPlayer p && !(p instanceof net.minecraftforge.common.util.FakePlayer) && p.getHealth() - e.getAmount() < 4F) e.setAmount(Math.max(0, p.getHealth() - 4F));
        });
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.HIGHEST, false, net.minecraftforge.event.entity.living.LivingDeathEvent.class, e -> {
            if (e.getEntity() instanceof ServerPlayer p && !(p instanceof net.minecraftforge.common.util.FakePlayer)) { e.setCanceled(true); p.setHealth(p.getMaxHealth()); }
        });
    }

    private static XenoDragonEntity xeno(ServerPlayer p) {
        Entity e = p.serverLevel().getEntity(ID[0]);
        if (e instanceof XenoDragonEntity x && x.isAlive()) return x;
        for (Entity o : p.serverLevel().getAllEntities()) if (o instanceof XenoDragonEntity x && x.isAlive()) { ID[0] = x.getId(); return x; }
        return null;
    }

    private static int xenoCount(ServerLevel l) {
        int n = 0;
        for (Entity o : l.getAllEntities()) if (o instanceof XenoDragonEntity x && x.isAlive()) n++;
        return n;
    }

    /** An invisible camera at (x,y,z) looking at {@code look}. */
    private static void cameraAt(Minecraft mc, Consumer<ServerPlayer> where) {
        step(null, () -> server(where), 8);
        step(null, () -> { mc.options.hideGui = true; mc.options.setCameraType(CameraType.FIRST_PERSON);
            if (mc.level.getEntity(CAM[0]) instanceof ArmorStand a) { a.yRotO = a.getYRot(); a.xRotO = a.getXRot(); a.yHeadRotO = a.getYRot(); mc.setCameraEntity(a); } }, 5);
    }

    private static void placeCam(ServerPlayer p, Vec3 eye, Vec3 look) {
        if (CAM[0] >= 0 && p.serverLevel().getEntity(CAM[0]) != null) p.serverLevel().getEntity(CAM[0]).discard();
        ArmorStand a = EntityType.ARMOR_STAND.create(p.serverLevel());
        Vec3 d = look.subtract(eye);
        float yaw = (float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90F), pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
        a.moveTo(eye.x, eye.y - a.getEyeHeight(), eye.z, yaw, pitch);
        a.setYHeadRot(yaw);
        a.setNoGravity(true); a.setInvisible(true); a.setInvulnerable(true);
        p.serverLevel().addFreshEntity(a);
        CAM[0] = a.getId();
    }

    /** A camera on the Xeno Dragon from this angle (degrees round from its front), distance and height. */
    private static void camOnXeno(Minecraft mc, double angle, double dist, double height) {
        cameraAt(mc, p -> {
            XenoDragonEntity x = xeno(p);
            Vec3 c = x != null ? x.position().add(0, 2.0, 0) : p.position();
            float yr = x != null ? x.getYRot() : 0;
            double a = Math.toRadians(yr + angle);
            Vec3 eye = c.add(-Math.sin(a) * dist, height, Math.cos(a) * dist);
            placeCam(p, eye, c);
        });
    }

    private static void playerCam(Minecraft mc) { step(null, () -> { mc.setCameraEntity(mc.player); mc.options.hideGui = false; }, 3); }

    // ---------------------------------------------------------------- stills (posed, AI off)

    private static void stills(Minecraft mc) {
        step(null, () -> server(p -> {
            XenoDragonEntity x = XenoDragons.XENO_DRAGON.get().create(p.serverLevel());
            x.moveTo(AT.x, AT.y, AT.z + 8, 180F, 0F);
            x.setYHeadRot(180F); x.yBodyRot = 180F;
            x.setNoAi(true); x.setPersistenceRequired();
            p.serverLevel().addFreshEntity(x);
            ID[0] = x.getId();
            log("still: spawned hp " + x.getMaxHealth() + " size " + x.getBbWidth() + "x" + x.getBbHeight());
        }), 20);
        camOnXeno(mc, 0, 8.5, 0.4);
        String[] names = {"base", "ssj", "ssj2", "ssj3", "ssg", "ui"};
        double[] speeds = new double[6];
        for (int i = 0; i <= 5; i++) {
            final int ff = i;
            step(null, () -> server(p -> { var x = xeno(p); if (x != null) { x.setFormForTest(ff); x.setYRot(180F); x.setYHeadRot(180F); x.yBodyRot = 180F; speeds[ff] = x.speed(); } }), 30);
            step("xd_" + names[i], () -> {}, 3);
        }
        step(null, () -> server(p -> {
            StringBuilder sb = new StringBuilder();
            boolean up = true;
            for (int i = 0; i <= 5; i++) { sb.append(names[i]).append('=').append(f(speeds[i])).append(' '); if (i > 0 && !(speeds[i] > speeds[i - 1] * 1.08)) up = false; }
            check("every form is a lot faster", up, sb.toString().trim());
        }), 2);
        camOnXeno(mc, 140, 8, 0.8);
        step(null, () -> server(p -> { var x = xeno(p); if (x != null) x.setFormForTest(3); }), 25);
        step("xd_ssj3_back", () -> {}, 3);
        camOnXeno(mc, 50, 8, 1.0);
        step(null, () -> server(p -> { var x = xeno(p); if (x != null) x.setFormForTest(4); }), 25);
        step("xd_ssg_side", () -> {}, 3);
        // the Dragon Bomb over its head
        step(null, () -> server(p -> {
            var x = xeno(p);
            if (x == null) return;
            x.setFormForTest(5);
            x.poseForTest(XenoDragonEntity.ACT_BOMB, 40);
            DragonBombEntity b = XenoDragons.DRAGON_BOMB.get().create(p.serverLevel());
            b.testHold = true;
            b.setRadiusForTest(DragonBombEntity.MAX_R);
            b.moveTo(x.getX(), x.getY() + 3.6 + DragonBombEntity.MAX_R, x.getZ());
            p.serverLevel().addFreshEntity(b);
        }), 10);
        camOnXeno(mc, 20, 20, 3);
        step("xd_dragon_bomb", () -> {}, 10);
        step(null, () -> server(p -> {
            for (var b : p.serverLevel().getEntitiesOfClass(DragonBombEntity.class, p.getBoundingBox().inflate(80))) b.discard();
            var x = xeno(p); if (x != null) x.discard();
        }), 5);
    }

    // ---------------------------------------------------------------- the End

    private static final Object[] DRAGON = new Object[1];
    private static final int[] WAIT = {0};
    private static final int[] PORTAL = {0};

    private static ServerLevel endLevel(ServerPlayer p) { return p.server.getLevel(Level.END); }

    private static void end(Minecraft mc, boolean dmz) {
        playerCam(mc);
        step(null, () -> server(p -> {
            ServerLevel end = endLevel(p);
            int y = end.getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 34);
            p.teleportTo(end, 0.5, Math.max(y, 50) + 1, 34.5, 180F, 10F);
            protect(p);
            log("in the End at y " + y);
        }), 160);
        // 1) a dragon /killed (any dragon, like a respawned one): a Xeno Dragon - or none without DragonMineZ
        step(null, () -> server(p -> {
            ServerLevel end = endLevel(p);
            log("natural dragons: " + end.getDragons().size());
            EnderDragon d = EntityType.ENDER_DRAGON.create(end);
            d.moveTo(30, 90, 0, 0, 0);
            end.addFreshEntity(d);
            DRAGON[0] = d;
        }), 10);
        step(null, () -> server(p -> { if (DRAGON[0] instanceof EnderDragon d) d.kill(); }), 10);
        step(null, () -> server(p -> {
            ServerLevel end = endLevel(p);
            int n = xenoCount(end);
            check(dmz ? "/kill dragon -> xeno dragon" : "no xeno dragon without dragonminez", dmz ? n == 1 : n == 0, "xeno dragons " + n);
            for (Entity o : end.getAllEntities()) if (o instanceof XenoDragonEntity x) {
                log("kill-path xeno at " + x.blockPosition().toShortString() + " lands " + (x.home() == null ? "-" : x.home().toShortString()) + " act " + x.act());
                x.kill();                                                       // (/kill in its base form: no loot)
            }
            dump();
        }), 20);
        step(null, () -> server(p -> {
            boolean none = p.serverLevel().getEntitiesOfClass(ItemEntity.class, new AABB(-40, 0, -40, 40, 200, 40), i -> i.getItem().is(XenoDragons.SCALE.get())).isEmpty();
            check("/kill in base form drops no loot", none && XenoDragons.lootDrops == 0, "lootDrops " + XenoDragons.lootDrops);
        }), 5);
        // 2) the real dragon, beaten: its whole death, then the Xeno Dragon; the exit portal still opens
        step(null, () -> server(p -> {
            ServerLevel end = endLevel(p);
            List<? extends EnderDragon> ds = end.getDragons();
            EnderDragon d;
            if (ds.isEmpty()) { d = EntityType.ENDER_DRAGON.create(end); d.moveTo(0, 80, 20, 0, 0); end.addFreshEntity(d); log("no natural dragon yet: spawned one"); }
            else d = ds.get(0);
            DRAGON[0] = d;
            d.hurt(p.damageSources().playerAttack(p), 100000F);
            log("real dragon hit: hp " + d.getHealth() + " phase " + d.getPhaseManager().getCurrentPhase().getPhase() + " fight " + (end.getDragonFight() != null));
        }), 20);
        for (int i = 0; i < 16; i++) {
            final int k = i;
            step(k == 9 ? "xd_end_dragon_dying" : null, () -> server(p -> {
                ServerLevel end = endLevel(p);
                if (DRAGON[0] instanceof EnderDragon d && WAIT[0] == 0) {
                    if (d.isRemoved() && xenoCount(end) > 0) WAIT[0] = k;
                    if (k % 3 == 0) log("waiting: dragon removed=" + d.isRemoved() + " deathTime " + d.dragonDeathTime + " xeno " + xenoCount(end));
                    if (!d.isRemoved() && d.isAlive() && k == 8) d.hurt(p.damageSources().playerAttack(p), 100000F);
                }
            }), 40);
        }
        step(null, () -> server(p -> {
            ServerLevel end = endLevel(p);
            int n = xenoCount(end);
            if (dmz) check("beaten dragon -> xeno dragon", n == 1, "xeno dragons " + n + " (appeared by wait step " + WAIT[0] + ")");
            else check("beaten dragon, no dragonminez -> no xeno dragon", n == 0, "xeno dragons " + n);
            int portal = 0, egg = 0;
            for (BlockPos b : BlockPos.betweenClosed(-4, 40, -4, 4, 90, 4)) {
                if (end.getBlockState(b).is(Blocks.END_PORTAL)) portal++;
                if (end.getBlockState(b).is(Blocks.DRAGON_EGG)) egg++;
            }
            PORTAL[0] = portal;
            int xp = 0;
            for (ExperienceOrb o : end.getEntitiesOfClass(ExperienceOrb.class, new AABB(-60, 0, -60, 60, 250, 60))) xp += o.getValue();
            check("exit portal open", portal >= 9, "end portal blocks " + portal + ", dragon egg " + egg + ", xp orbs on the ground " + xp
                    + ", fight killed=" + (end.getDragonFight() != null && end.getDragonFight().hasPreviouslyKilledDragon()));
            dump();
        }), 5);
        if (dmz) {
            camOnXeno(mc, 0, 9, 1.0);
            step("xd_end_emerging", () -> {}, 5);
            step(null, () -> {}, 60);
            playerCam(mc);
            step(null, () -> server(p -> {
                var x = xeno(p);
                if (x == null) { check("xeno landed", false, "none"); return; }
                p.teleportTo(x.getX(), x.getY() + 0.5, x.getZ() + 12);
                check("xeno landed on the island", x.act() != XenoDragonEntity.ACT_EMERGE && x.onGround() && x.getY() > 30, "at " + x.blockPosition().toShortString()
                        + " home " + (x.home() == null ? "-" : x.home().toShortString()) + " act " + x.act() + " hp " + Math.round(x.getHealth()) + "/" + Math.round(x.getMaxHealth()));
            }), 40);
            camOnXeno(mc, 0, 10, 1.5);
            step("xd_end_landed", () -> {}, 5);
        }
    }

    // ---------------------------------------------------------------- the fight, form by form

    private static final double[] SPD = new double[6];

    private static void fight(Minecraft mc) {
        playerCam(mc);
        step(null, () -> server(p -> { var x = xeno(p); if (x != null) { SPD[0] = x.speed(); log("fight: base speed " + f(SPD[0]) + " target " + (x.getTarget() == null ? "-" : x.getTarget().getName().getString())); } }), 80);
        step("xd_fight_base", () -> {}, 2);
        String[] shots = {"xd_fight_ssj", "xd_fight_ssj2", "xd_fight_ssj3"};
        for (int i = 0; i < 3; i++) {
            final int k = i;
            step(null, () -> server(p -> {
                var x = xeno(p); if (x == null) return;
                x.invulnerableTime = 0;
                x.hurt(p.damageSources().magic(), x.getMaxHealth() * 0.27F);
                log("fight: hit " + k + " -> form " + x.form() + " act " + x.act() + " hp " + Math.round(x.getHealth()) + "/" + Math.round(x.getMaxHealth()));
            }), 45);
            camOnXeno(mc, 25, 10, 1.5);
            step(shots[i], () -> server(p -> {
                var x = xeno(p); if (x == null) return;
                SPD[k + 1] = x.speed();
                check("form " + (k + 1) + " by damage", x.form() == k + 1, "form " + XenoDragonEntity.formName(x.form()) + " speed " + f(SPD[k]) + " -> " + f(SPD[k + 1]));
            }), 3);
            playerCam(mc);
        }
        step(null, () -> server(p -> { var x = xeno(p); if (x == null) return;
            x.invulnerableTime = 0; x.hurt(p.damageSources().playerAttack(p), x.getMaxHealth() * 3F);
            log("fight: 'killing' blow as SSJ3 -> act " + x.act() + " alive " + x.isAlive()); }), 30);
        camOnXeno(mc, 0, 10, 1.0);
        step("xd_fight_god_ritual", () -> {}, 3);
        step(null, () -> {}, 40);
        step("xd_fight_ssg", () -> server(p -> { var x = xeno(p); if (x == null) return;
            SPD[4] = x.speed();
            check("beaten as SSJ3 -> Super Saiyan God, healed", x.form() == XenoDragonEntity.SSG && x.getHealth() >= x.getMaxHealth() * 0.95F,
                    "form " + XenoDragonEntity.formName(x.form()) + " hp " + Math.round(x.getHealth()) + "/" + Math.round(x.getMaxHealth()) + " speed " + f(SPD[3]) + " -> " + f(SPD[4]) + " crystals " + x.crystalCount()); }), 3);
        playerCam(mc);
        step(null, () -> {}, 80);
        step(null, () -> server(p -> { var x = xeno(p); if (x == null) return;
            log("fight: SSG crystals " + x.crystalCount() + " breaths " + x.breaths + " fireballs " + x.fireballs + " gusts " + x.gusts + " dives " + x.dives + " blinks " + x.blinks);
            x.invulnerableTime = 0; x.hurt(p.damageSources().playerAttack(p), x.getMaxHealth() * 3F);
            log("fight: 'killing' blow as SSG -> ko " + x.ko() + " alive " + x.isAlive()); }), 20);
        camOnXeno(mc, 30, 9, 2.0);
        step("xd_fight_ssg_down", () -> {}, 3);
        step(null, () -> {}, 75);
        step("xd_fight_ui", () -> server(p -> { var x = xeno(p); if (x == null) return;
            SPD[5] = x.speed();
            check("beaten as SSG -> Ultra Instinct (Mastered)", x.form() == XenoDragonEntity.UI && x.getHealth() >= x.getMaxHealth() * 0.9F,
                    "form " + XenoDragonEntity.formName(x.form()) + " hp " + Math.round(x.getHealth()) + "/" + Math.round(x.getMaxHealth()) + " speed " + f(SPD[4]) + " -> " + f(SPD[5]));
            StringBuilder sb = new StringBuilder(); boolean up = true;
            for (int i = 0; i <= 5; i++) { sb.append(f(SPD[i])).append(' '); if (i > 0 && !(SPD[i] > SPD[i - 1] * 1.08)) up = false; }
            check("speed up with every transformation (fight)", up, sb.toString().trim());
            dump(); }), 3);
        playerCam(mc);
        // the Dragon Bomb: it uses it by itself
        step(null, () -> server(p -> { var x = xeno(p); if (x == null) return; protect(p); x.setTarget(p); x.bombCdForTest(0); }), 40);
        step(null, () -> server(p -> { var x = xeno(p); if (x == null) return;
            log("bomb: act " + x.act() + " bomb id " + x.bombIdForTest());
            if (x.act() != XenoDragonEntity.ACT_BOMB) { log("bomb: not started by itself yet - starting it"); x.bombForTest(p); } }), 40);
        camOnXeno(mc, 30, 20, 4);
        step("xd_bomb_charging", () -> {}, 3);
        step(null, () -> {}, 60);
        step("xd_bomb_thrown", () -> server(p -> { var x = xeno(p); if (x != null) log("bomb: thrown " + x.bombsThrown + " act " + x.act()); }), 3);
        step(null, () -> {}, 70);
        step("xd_bomb_after", () -> server(p -> { var x = xeno(p);
            check("dragon bomb thrown and exploded", x != null && x.bombsThrown >= 1 && DragonBombEntity.explosions >= 1,
                    "thrown " + (x == null ? -1 : x.bombsThrown) + " explosions " + DragonBombEntity.explosions + " last hit " + DragonBombEntity.lastHits); dump(); }), 3);
        playerCam(mc);
        // interrupted: hurt it enough while it charges
        step(null, () -> server(p -> { var x = xeno(p); if (x == null) return; x.bombForTest(p); }), 25);
        step(null, () -> server(p -> { var x = xeno(p); if (x == null) return;
            x.invulnerableTime = 0; x.hurt(p.damageSources().magic(), x.getMaxHealth() * 0.1F);
            check("dragon bomb interrupted", x.bombsStopped == 1 && x.act() == XenoDragonEntity.ACT_STUN, "stopped " + x.bombsStopped + " act " + x.act()); }), 10);
        // Ultra Instinct dodges (one hit every 15 ticks, after the stun)
        final int[] D0 = {0};
        step(null, () -> server(p -> { var x = xeno(p); if (x != null) D0[0] = x.dodges; }), 45);
        for (int i = 0; i < 16; i++) step(null, () -> server(p -> { var x = xeno(p); if (x == null) return; x.invulnerableTime = 0; x.hurt(p.damageSources().playerAttack(p), 1F); }), 15);
        step(null, () -> server(p -> { var x = xeno(p); if (x == null) return;
            check("ultra instinct dodges some hits", x.dodges - D0[0] > 0 && x.dodges - D0[0] < 16, "dodged " + (x.dodges - D0[0]) + " of 16"); }), 2);
        // the final defeat: loot once
        step(null, () -> server(p -> { var x = xeno(p); if (x == null) return;
            for (int i = 0; i < 20 && x.isAlive(); i++) { x.invulnerableTime = 0; x.hurt(p.damageSources().playerAttack(p), x.getMaxHealth()); }
            if (x.isAlive()) for (int i = 0; i < 10 && x.isAlive(); i++) { x.invulnerableTime = 0; x.hurt(p.damageSources().magic(), x.getMaxHealth()); }
            log("final blows: alive " + x.isAlive() + " dying " + x.isDeadOrDying());
            x.hurt(p.damageSources().magic(), 1000F);                       // (a hit on the corpse: no second drop)
        }), 40);
        step("xd_loot", () -> server(p -> {
            dump();
            Map<String, Integer> got = new TreeMap<>();
            for (ItemEntity it : p.serverLevel().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(40)))
                got.merge(ForgeRegistries.ITEMS.getKey(it.getItem().getItem()).toString(), it.getItem().getCount(), Integer::sum);
            int xp = 0;
            for (ExperienceOrb o : p.serverLevel().getEntitiesOfClass(ExperienceOrb.class, p.getBoundingBox().inflate(40))) xp += o.getValue();
            int scales = got.getOrDefault("xenowarriors:xeno_dragon_scale", 0);
            check("final defeat in UI drops loot once", XenoDragons.lootDrops == 1 && scales >= 4 && scales <= 6, "lootDrops " + XenoDragons.lootDrops + " scales " + scales + " xp " + xp + " items " + got);
            check("no xeno dragon left", xenoCount(p.serverLevel()) == 0, "left " + xenoCount(p.serverLevel()));
            check("exit portal still there", PORTAL[0] >= 9, "portal blocks " + PORTAL[0]);
        }), 3);
    }
}

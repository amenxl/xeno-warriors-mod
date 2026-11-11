package com.xenowarriors.client.test;

import com.mojang.authlib.GameProfile;
import com.xenowarriors.bot.PartyBotEntity;
import com.xenowarriors.bot.PartyBots;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.character.ModCapabilities;
import com.xenowarriors.client.UiTest;
import com.xenowarriors.client.UiTest.Step;
import com.xenowarriors.compat.DmzKishiGravity;
import com.xenowarriors.entity.KishiDomeEntity;
import com.xenowarriors.skill.SkillLevels;
import com.xenowarriors.skill.rhalix.KishiSkill;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.fml.ModList;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Kishi with DragonMineZ gravity (focus "kishigrav" plain, "dmzkishigrav" with DragonMineZ; {@link #pack} inside the
 * real-pack test when its focus names "kishi"). Logs "XW-TEST kishigrav ... PASS/FAIL":
 * the G formula, a fresh vs a maxed Rhalix's live G, husks slowed / unable to jump in proportion to G, allies (a pet
 * wolf, a bot) untouched, an enemy dome on the player handed to DragonMineZ's own gravity, the Rhalix bot's dome using
 * the bot's own levels, and the plain (no DMZ) Kishi unchanged. Screenshots: kishigrav_domes, kishigrav_hud.
 */
public final class KishiGravTests {
    private KishiGravTests() {}

    public static boolean handles(String focus) { return focus != null && focus.replaceFirst("^dmz", "").startsWith("kishigrav"); }

    private static void log(String s) { UiTest.log("kishigrav " + s); }
    private static String f(double v) { return String.format(Locale.ROOT, "%.3f", v); }
    private static String pf(boolean ok) { return ok ? "PASS" : "FAIL"; }

    private static boolean dmz() { return ModList.get().isLoaded("dragonminez"); }

    private static Vec3 O;
    private static final Husk[] H = new Husk[3];
    private static final KishiDomeEntity[] D = new KishiDomeEntity[3];
    private static final double[] JUMP = new double[3], BASEY = new double[3];
    private static Wolf wolf;
    private static PartyBotEntity ally, rbot;
    private static KishiDomeEntity enemyDome;
    private static FakePlayer enemy;
    private static float stamina0 = -1;

    private static FakePlayer enemy(ServerLevel l) {
        if (enemy == null || enemy.level() != l) enemy = FakePlayerFactory.get(l, new GameProfile(UUID.nameUUIDFromBytes("xwkishienemy".getBytes()), "KishiFoe"));
        return enemy;
    }

    private static void stats(ServerPlayer p, int v) {
        p.getCapability(ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> { d.statLevel[0] = v; d.statLevel[1] = v; d.statLevel[2] = v; });
    }

    private static Husk husk(ServerPlayer p, Vec3 at) {
        Husk h = EntityType.HUSK.create(p.serverLevel());
        h.moveTo(at.x, at.y, at.z, 180, 0);
        h.setNoAi(true);
        h.setPersistenceRequired();
        h.getAttribute(Attributes.MAX_HEALTH).setBaseValue(2000);
        h.setHealth(2000);
        h.addTag("xw_kishigrav");
        p.serverLevel().addFreshEntity(h);
        return h;
    }

    private static void clear(ServerPlayer p) {
        for (var e : p.serverLevel().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(64), e -> e.getTags().contains("xw_kishigrav"))) e.discard();
        for (var e : p.serverLevel().getEntitiesOfClass(KishiDomeEntity.class, p.getBoundingBox().inflate(64))) e.discard();
        for (var b : new java.util.ArrayList<>(PartyBots.bots(p))) b.discard();
    }

    private static KishiDomeEntity dome(ServerPlayer p, Player owner, Vec3 at, float g, int level) {
        KishiDomeEntity d = KishiDomeEntity.create(p.serverLevel(), owner, at);
        for (int i = 1; i < level; i++) d.raiseLevel();
        if (g > 0 && dmz()) d.setGravityForTest(g);
        p.serverLevel().addFreshEntity(d);
        return d;
    }

    private static void jump(LivingEntity e) {
        try {
            var m = net.minecraftforge.fml.util.ObfuscationReflectionHelper.findMethod(LivingEntity.class, "m_6135_");
            m.invoke(e);
        } catch (Throwable t) { log("jump failed " + t); }
    }

    /** DragonMineZ's own GravityLogic.getGravityMultiplier for a player (-1 without it). */
    static double dmzGravity(Player p) {
        try { return ((Number) Class.forName("com.dragonminez.server.util.GravityLogic").getMethod("getGravityMultiplier", Player.class).invoke(null, p)).doubleValue(); }
        catch (Throwable t) { return -1; }
    }

    static boolean dmzSpeedPenalty(Player p) {
        try {
            UUID id = (UUID) Class.forName("com.dragonminez.server.util.GravityLogic").getField("GRAVITY_SPEED_UUID").get(null);
            var a = p.getAttribute(Attributes.MOVEMENT_SPEED);
            return a != null && a.getModifier(id) != null;
        } catch (Throwable t) { return false; }
    }

    static float stamina(Player p) {
        Object d = com.xenowarriors.compat.DmzPerCharacter.live(p);
        Object r = HuntDmzTests.call(d, "getResources");
        Object v = HuntDmzTests.call(r, "getCurrentStamina");
        return v instanceof Number n ? n.floatValue() : -1;
    }

    private static ServerPlayer SAMPLER;
    private static int SAMPLE;
    private static boolean listening;

    private static void onServerTick(net.minecraftforge.event.TickEvent.ServerTickEvent e) {
        if (e.phase != net.minecraftforge.event.TickEvent.Phase.END || SAMPLE <= 0 || SAMPLER == null) return;
        for (int i = 0; i < 3; i++) JUMP[i] = Math.max(JUMP[i], H[i].getY() - BASEY[i]);
        if (--SAMPLE > 0) return;
        for (int i = 0; i < 3; i++) H[i].setNoAi(true);
        try { check3(SAMPLER); } catch (Throwable t) { log("check failed " + t + " -> FAIL"); }
    }

    private static void check3(ServerPlayer p) {
        boolean dmz = dmz();
            double[] sp = new double[3];
            for (int i = 0; i < 3; i++) sp[i] = H[i].getAttributeValue(Attributes.MOVEMENT_SPEED);
            log("domes G: light=" + D[1].gravity() + " (lv" + D[1].getGravityLevel() + ") heavy=" + D[2].gravity() + " (lv" + D[2].getGravityLevel() + ")"
                    + " net light=" + D[1].lastNet.get(H[1].getId()) + " heavy=" + D[2].lastNet.get(H[2].getId()));
            log("husk speed none/light/heavy=" + f(sp[0]) + "/" + f(sp[1]) + "/" + f(sp[2]) + " modifiers light=" + f(DmzKishiGravity.speedModifier(H[1]))
                    + " heavy=" + f(DmzKishiGravity.speedModifier(H[2])) + " -> "
                    + pf(dmz ? (sp[2] < sp[1] && sp[1] < sp[0] && Math.abs(DmzKishiGravity.speedModifier(H[1]) + DmzKishiGravity.movePenalty(10)) < 0.01
                                && Math.abs(DmzKishiGravity.speedModifier(H[2]) + DmzKishiGravity.movePenalty(80)) < 0.01)
                             : (H[1].hasEffect(MobEffects.MOVEMENT_SLOWDOWN) && H[2].hasEffect(MobEffects.MOVEMENT_SLOWDOWN) && sp[2] <= sp[1] && sp[1] < sp[0]
                                && DmzKishiGravity.speedModifier(H[1]) == 0)));
            log("husk jump height none/light/heavy=" + f(JUMP[0]) + "/" + f(JUMP[1]) + "/" + f(JUMP[2]) + " -> "
                    + pf(dmz ? (JUMP[0] > 1.0 && JUMP[1] < JUMP[0] * 0.6 && JUMP[1] > JUMP[2] && JUMP[2] < 0.1)
                             : (JUMP[0] > 1.0 && JUMP[1] < 0.05 && JUMP[2] < 0.05)));
            double wSpeed = wolf.getAttributeValue(Attributes.MOVEMENT_SPEED), wBase = wolf.getAttribute(Attributes.MOVEMENT_SPEED).getBaseValue();
            boolean botOk = ally == null || (!ally.hasEffect(MobEffects.MOVEMENT_SLOWDOWN) && DmzKishiGravity.speedModifier(ally) == 0 && !D[2].lastNet.containsKey(ally.getId()));
            log("allies in the heavy dome: wolf inside=" + D[2].isInside(wolf) + " speed " + f(wSpeed) + "/" + f(wBase) + " slow=" + wolf.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)
                    + " weighed=" + D[2].lastNet.containsKey(wolf.getId()) + " bot inside=" + (ally != null && D[2].isInside(ally)) + " ok=" + botOk + " -> "
                    + pf(D[2].isInside(wolf) && Math.abs(wSpeed - wBase) < 1e-6 && !wolf.hasEffect(MobEffects.MOVEMENT_SLOWDOWN) && !D[2].lastNet.containsKey(wolf.getId()) && botOk));
    }

    public static void build(Minecraft mc, List<Step> l, String focus) {
        boolean dmz = dmz();
        log("start focus=" + focus + " dmz=" + dmz);
        if (!listening) { listening = true; net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(KishiGravTests::onServerTick); }
        // DragonMineZ's race picker (a pausing screen: the world would stop) is closed whenever it opens
        if (dmz) net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.TickEvent.ClientTickEvent e) -> {
            if (e.phase == net.minecraftforge.event.TickEvent.Phase.END) HuntDmzTests.noDmzScreen(mc); });
        // ---- setup: Rhalix, survival, a flat arena, protected
        l.add(new Step(null, () -> { UiTest.noToasts(mc); mc.setScreen(null); UiTest.character(CharacterType.RHALIX); }, 60));
        l.add(new Step(null, () -> { HuntDmzTests.noDmzScreen(mc); UiTest.server(p -> {
            if (dmz) { HuntDmzTests.make(p, "human", 5); com.xenowarriors.compat.DmzPerCharacter.markCreatedForTest(p); }
            O = com.xenowarriors.debug.SkillTestRunner.arena(p);
            clear(p);
            p.setGameMode(GameType.SURVIVAL);
            p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20 * 600, 10, false, false));
            p.serverLevel().setDayTime(6000);
            stats(p, 0);
        }); }, 30));

        // ---- 1. the formula
        l.add(new Step(null, () -> {
            double n1 = DmzKishiGravity.formula(1, 10000, 1, 1, 1), n3 = DmzKishiGravity.formula(1, 10000, 1, 1, 3);
            double mid = DmzKishiGravity.formula(100, 10000, 500, 15, 1), mid3 = DmzKishiGravity.formula(100, 10000, 500, 15, 3);
            double max1 = DmzKishiGravity.formula(10000, 10000, DmzKishiGravity.XW_MAX_LEVEL, 100, 1), max3 = DmzKishiGravity.formula(10000, 10000, DmzKishiGravity.XW_MAX_LEVEL, 100, 3);
            log("formula new=" + n1 + " new(lv3)=" + n3 + " mid(DMZ100,XW500,Kishi15)=" + mid + "/" + mid3 + " max=" + max1 + "/" + max3
                    + " -> " + pf(n1 == 10 && n3 == 30 && max3 == 900 && n1 < mid && mid < max1 && mid3 < max3));
            log("dmz penalties: move(10G)=" + f(DmzKishiGravity.movePenalty(10)) + " move(40G)=" + f(DmzKishiGravity.movePenalty(40)) + " move(80G)=" + f(DmzKishiGravity.movePenalty(80))
                    + " jump(10G)=" + f(DmzKishiGravity.jumpFactor(10)) + " jump(80G)=" + f(DmzKishiGravity.jumpFactor(80)) + " fall(10G)=" + f(DmzKishiGravity.fallExtra(10)) + " " + DmzKishiGravity.selfTest());
        }, 2));

        // ---- 2. a fresh Rhalix vs a maxed one (the real caster's levels)
        l.add(new Step(null, () -> UiTest.server(p -> {
            Vec3 far = O.add(0, 0, 25);
            stats(p, 0);
            SkillLevels.setForTest(p, "kishi", 1, 1);
            if (dmz) HuntDmzTests.make(p, "human", 5);
            int[] in0 = DmzKishiGravity.inputs(p);
            KishiDomeEntity a = KishiDomeEntity.create(p.serverLevel(), p, far);
            float g0 = a.gravity();
            a.raiseLevel(); a.raiseLevel();
            float g0b = a.gravity();
            stats(p, 30000);
            SkillLevels.setForTest(p, "kishi", 10, 10);
            if (dmz) HuntDmzTests.make(p, "human", 10000);
            int[] in1 = DmzKishiGravity.inputs(p);
            KishiDomeEntity b = KishiDomeEntity.create(p.serverLevel(), p, far);
            float g1 = b.gravity();
            b.raiseLevel(); b.raiseLevel();
            float g1b = b.gravity();
            log("fresh Rhalix inputs(dmzLv,dmzMax,xwLv,kishi)=" + java.util.Arrays.toString(in0) + " G=" + g0 + " (lv3 " + g0b + ")"
                    + " | maxed inputs=" + java.util.Arrays.toString(in1) + " G=" + g1 + " (lv3 " + g1b + ") -> "
                    + pf(dmz ? (g0 >= 10 && g0 <= 15 && g0b == 3 * g0 && g1 > g0 * 5 && g1b > g1 && g1b <= 900) : (g0 == 0 && g1 == 0 && g1b == 0)));
            stats(p, 0);
            SkillLevels.setForTest(p, "kishi", 1, 1);
            if (dmz) HuntDmzTests.make(p, "human", 5);
        }), 5));

        // ---- 3. three husks: no dome / light dome / heavy dome (DMZ: 10G and 80G; plain: gravity level 1 and 3)
        l.add(new Step(null, () -> UiTest.server(p -> {
            p.connection.teleport(O.x, O.y, O.z - 5, 0F, -10F);
            FakePlayer foe = enemy(p.serverLevel());
            foe.moveTo(O.x, O.y, O.z - 30);
            for (int i = 0; i < 3; i++) H[i] = husk(p, O.add(-8 + 8 * i, 0, 10));
            D[0] = null;
            D[1] = dome(p, foe, O.add(0, 0, 10), 10, 1);
            D[2] = dome(p, p, O.add(8, 0, 10), 80, 3);          // (cast by the player: the wolf and the bot are its allies)
            // allies inside the heavy dome: a sitting pet wolf and a Mikiko bot
            wolf = EntityType.WOLF.create(p.serverLevel());
            wolf.moveTo(O.x + 9.5, O.y, O.z + 11, 0, 0);
            wolf.tame(p); wolf.setOrderedToSit(true); wolf.addTag("xw_kishigrav");
            p.serverLevel().addFreshEntity(wolf);
            ally = PartyBots.spawn(p, CharacterType.MIKIKO, null);
            if (ally != null) { ally.moveTo(O.x + 6.5, O.y, O.z + 9, 0, 0); ally.setNoAi(true); }
            // these domes' owner is someone else: the player's side is the wolf's and bot's, not the foe's
            D[1].setGravityForTest(dmz ? 10 : 0);
        }), 20));
        l.add(new Step(null, () -> { mc.options.hideGui = false; mc.options.setCameraType(CameraType.FIRST_PERSON); mc.player.setYRot(0F); mc.player.setXRot(-10F); }, 4));
        l.add(new Step("kishigrav_domes", () -> {}, 2));
        // the jumps are sampled on the server's own ticks (the CI client runs far slower than the server)
        l.add(new Step(null, () -> UiTest.server(p -> {
            // (a NoAI mob never moves: AI on for the jump, with nothing to chase, so it only goes up and down)
            for (int i = 0; i < 3; i++) { H[i].setNoAi(false); H[i].getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(0); H[i].setTarget(null);
                BASEY[i] = H[i].getY(); JUMP[i] = 0; H[i].setOnGround(true); jump(H[i]); }
            log("jump start v.y none/light/heavy=" + f(H[0].getDeltaMovement().y) + "/" + f(H[1].getDeltaMovement().y) + "/" + f(H[2].getDeltaMovement().y));
            SAMPLER = p;
            SAMPLE = 15;
        }), 40));
        // the husks are let go when the domes end (no leftover penalty)
        l.add(new Step(null, () -> UiTest.server(p -> { for (KishiDomeEntity d : D) if (d != null) d.discard(); }), 3));
        l.add(new Step(null, () -> UiTest.server(p -> {
            log("domes gone: husk speed modifiers light=" + f(DmzKishiGravity.speedModifier(H[1])) + " heavy=" + f(DmzKishiGravity.speedModifier(H[2]))
                    + " -> " + pf(DmzKishiGravity.speedModifier(H[1]) == 0 && DmzKishiGravity.speedModifier(H[2]) == 0));
            clear(p);
        }), 3));

        // ---- 4. an enemy's Kishi on the player: DragonMineZ's own gravity (with DMZ), Slowness (without)
        l.add(new Step(null, () -> UiTest.server(p -> {
            p.connection.teleport(O.x, O.y, O.z, 0F, 0F);
            stamina0 = dmz ? stamina(p) : -1;
            enemyDome = dome(p, enemy(p.serverLevel()), O, 40, 1);
        }), 30));
        l.add(new Step(null, () -> {
            double clientG = dmz ? dmzGravity(mc.player) : -1;
            UiTest.server(p -> {
                double serverG = dmzGravity(p);
                float st = dmz ? stamina(p) : -1;
                if (dmz) log("enemy dome on the player: Kishi G " + enemyDome.gravity() + " mark=" + f(DmzKishiGravity.extraFor(p)) + " DMZ gravity server=" + f(serverG)
                        + " client=" + f(clientG) + " DMZ speed penalty=" + dmzSpeedPenalty(p) + " mixinLive=" + DmzKishiGravity.mixinLive
                        + " stamina " + stamina0 + " -> " + st + " -> " + pf(serverG >= 39.9 && clientG >= 39.9 && dmzSpeedPenalty(p)));
                else log("enemy dome on the player (plain): slowness=" + p.hasEffect(MobEffects.MOVEMENT_SLOWDOWN) + " G=" + enemyDome.gravity()
                        + " -> " + pf(p.hasEffect(MobEffects.MOVEMENT_SLOWDOWN) && enemyDome.gravity() == 0));
                enemyDome.discard();
            });
        }, 4));
        l.add(new Step(null, () -> UiTest.server(p -> {
            if (dmz) log("enemy dome gone: mark=" + f(DmzKishiGravity.extraFor(p)) + " DMZ gravity=" + f(dmzGravity(p)) + " DMZ speed penalty=" + dmzSpeedPenalty(p)
                    + " -> " + pf(DmzKishiGravity.extraFor(p) == 0 && dmzGravity(p) < 39 && !dmzSpeedPenalty(p)));
            p.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        }), 4));

        // ---- 5. the Rhalix bot's Kishi uses the bot's own levels
        l.add(new Step(null, () -> UiTest.server(p -> {
            H[0] = husk(p, O.add(0, 0, 8));
            rbot = PartyBots.spawn(p, CharacterType.RHALIX, null);
            if (rbot != null) { rbot.moveTo(O.x, O.y, O.z + 2, 0, 0); rbot.setYRot(0); rbot.setYHeadRot(0); rbot.setNoAi(true); }
        }), 30));
        l.add(new Step(null, () -> UiTest.server(p -> {
            if (rbot == null) { log("rhalix bot: none spawned -> FAIL"); return; }
            FakePlayer f = PartyBots.standIn(rbot);
            log("rhalix bot before: at " + rbot.position().subtract(O) + " yRot=" + f(rbot.getYRot()) + " husk at " + H[0].position().subtract(O) + " alive=" + H[0].isAlive());
            rbot.setYRot(0F); rbot.setYHeadRot(0F); rbot.yBodyRot = 0F;
            // aimed at the husk's middle from the stand-in's eyes, the way a bot aims a skill (a DMZ race can be taller)
            f.moveTo(rbot.getX(), rbot.getY(), rbot.getZ(), 0F, 0F);
            Vec3 dv = H[0].getBoundingBox().getCenter().subtract(f.getEyePosition());
            rbot.setXRot((float) -Math.toDegrees(Math.atan2(dv.y, Math.sqrt(dv.x * dv.x + dv.z * dv.z))));
            log("rhalix bot aim: standIn eye height=" + f(f.getEyeHeight()) + " pitch=" + f(rbot.getXRot()));
            boolean used = PartyBots.useForTest(rbot, new KishiSkill());
            KishiDomeEntity d = null;
            for (KishiDomeEntity k : p.serverLevel().getEntitiesOfClass(KishiDomeEntity.class, p.getBoundingBox().inflate(30))) if (f.getUUID().equals(k.getOwnerId())) d = k;
            D[0] = d;
            log("rhalix bot cast facing: standIn yRot=" + f(f.getYRot()) + " xRot=" + f(f.getXRot()) + " dome at " + (d == null ? "-" : d.position().subtract(O)));
            if (d != null && !d.isInside(H[0])) H[0].teleportTo(d.getX(), d.getY(), d.getZ());   // (the enemy stands in the bot's dome either way)
            double want = dmz ? DmzKishiGravity.gravityFor(f, 1) : 0;
            log("rhalix bot kishi: used=" + used + " dome=" + (d != null) + " bot level=" + rbot.botLevel() + " inputs=" + java.util.Arrays.toString(DmzKishiGravity.inputs(f))
                    + " G=" + (d == null ? -1 : d.gravity()) + " want=" + want + " -> " + pf(used && d != null && Math.abs(d.gravity() - want) < 0.01 && (!dmz || d.gravity() >= 10)));
        }), 15));
        l.add(new Step(null, () -> UiTest.server(p -> log("rhalix bot dome: " + (D[0] == null ? "none" : "removed=" + D[0].isRemoved() + " at " + D[0].position().subtract(O)
                + " husk at " + H[0].position().subtract(O) + " inside=" + D[0].isInside(H[0]) + " affects=" + D[0].affects(H[0]) + " age=" + D[0].tickCount)
                + " standIn alive=" + PartyBots.standIn(rbot).isAlive() + " removed=" + PartyBots.standIn(rbot).isRemoved() + " sameLevel=" + (PartyBots.standIn(rbot).level() == p.level())
                + " | husk speed modifier=" + f(DmzKishiGravity.speedModifier(H[0]))
                + " slow=" + H[0].hasEffect(MobEffects.MOVEMENT_SLOWDOWN) + " -> " + pf(dmz ? DmzKishiGravity.speedModifier(H[0]) < -0.1 : H[0].hasEffect(MobEffects.MOVEMENT_SLOWDOWN)))), 5));

        // ---- 6. the player's own Kishi: the HUD and the dome show its G
        l.add(new Step(null, () -> UiTest.server(p -> {
            clear(p);
            H[1] = husk(p, O.add(0, 0, 7));
            p.connection.teleport(O.x, O.y, O.z, 0F, 8F);
            com.xenowarriors.karma.Karma.add(p, 200);
        }), 20));
        l.add(new Step(null, () -> { mc.player.setYRot(0F); mc.player.setXRot(8F); UiTest.server(p -> {
            p.setYRot(0F); p.setXRot(8F); p.setYHeadRot(0F);
            boolean used = new KishiSkill().use(p);
            log("player kishi used=" + used);
        }); }, 25));
        l.add(new Step("kishigrav_hud", () -> {
            float g = com.xenowarriors.client.ClientSkillState.kishiGravity(mc.player.getUUID());
            int lv = com.xenowarriors.client.ClientSkillState.kishiLevel(mc.player.getUUID());
            log("hud: level=" + lv + " G=" + g + " -> " + pf(lv == 1 && (dmz ? g >= 10 : g == 0)));
        }, 2));
        l.add(new Step(null, () -> UiTest.server(p -> { clear(p); log("done"); }), 5));
    }

    // ------------------------------------------------------------------ inside the real-pack test

    private static String where(ServerPlayer p) {
        return p.level().dimension().location() + " alive=" + p.isAlive() + " mode=" + p.gameMode.getGameModeForPlayer().getName() + " at " + p.blockPosition();
    }

    /** A dome's effect on a husk and on the player wherever the player is now (log only: the Otherworld after a knock-out...). */
    private static KishiDomeEntity probeDome, probePlayerDome;
    private static Husk probeHusk;

    private static void respawn(Minecraft mc) {
        if (mc.player != null && (mc.player.isDeadOrDying() || mc.screen instanceof net.minecraft.client.gui.screens.DeathScreen)) {
            PackTests.log("kishi: respawning after a death");
            mc.player.respawn();
        }
        mc.setScreen(null);
    }

    public static void pack(Minecraft mc, List<Step> l) {
        // ---- whatever state the steps before left: a death (the Xeno Dragon's bomb sends you to DragonMineZ's Otherworld)...
        //      respawn first and wait: the respawned player is a new one, placed by DragonMineZ in the Otherworld
        l.add(new Step(null, () -> { respawn(mc); PackTests.server(p -> PackTests.log("kishi before: " + where(p))); }, 40));
        l.add(new Step(null, () -> { respawn(mc); PackTests.server(p -> {
            PackTests.log("kishi after respawn: " + where(p));
            probeDome = probePlayerDome = null; probeHusk = null;
            if (!p.isAlive()) { PackTests.log("kishi: still dead on the server - respawning there"); p.server.getPlayerList().respawn(p, false); return; }
            if (p.level() != p.server.overworld() && p.isAlive()) {
                Vec3 at = p.position();
                probeHusk = EntityType.HUSK.create(p.serverLevel());
                probeHusk.moveTo(at.x, at.y, at.z + 6, 180, 0);
                probeHusk.setNoAi(true);
                probeHusk.addTag("xw_pack");
                boolean added = p.serverLevel().addFreshEntity(probeHusk);
                probeDome = dome(p, enemy(p.serverLevel()), at.add(0, 0, 6), 80, 1);
                probePlayerDome = dome(p, enemy(p.serverLevel()), at, 40, 1);
                PackTests.log("kishi probe in " + p.level().dimension().location() + ": husk added=" + added);
            }
        }); }, 30));
        l.add(new Step(null, () -> { respawn(mc); PackTests.server(p -> {
            if (probeDome != null) {
                PackTests.log("kishi probe result: husk removed=" + probeHusk.isRemoved() + " husk mod=" + PackTests.f(DmzKishiGravity.speedModifier(probeHusk))
                        + " dome removed=" + probeDome.isRemoved() + " age=" + probeDome.tickCount + " playerDome removed=" + probePlayerDome.isRemoved()
                        + " affects player=" + probePlayerDome.affects(p) + " mark=" + PackTests.f(DmzKishiGravity.extraFor(p)) + " dmz G=" + PackTests.f(dmzGravity(p))
                        + " foe alive=" + enemy(p.serverLevel()).isAlive() + " foe removed=" + enemy(p.serverLevel()).isRemoved() + " " + where(p));
                probeDome.discard(); probePlayerDome.discard(); probeHusk.discard();
            }
            // ...so start clean: alive (for DragonMineZ too - while it counts you dead it keeps you in its Otherworld), in the Overworld,
            // on the arena, survival
            Object st = HuntDmzTests.call(com.xenowarriors.compat.DmzPerCharacter.live(p), "getStatus");
            if (st != null && Boolean.FALSE.equals(HuntDmzTests.call(st, "isAlive"))) {
                HuntDmzTests.call(st, "setAlive", true);
                com.xenowarriors.compat.DmzPerCharacter.syncAll(p);
                PackTests.log("kishi: DragonMineZ had the player dead - revived");
            }
            if (p.level() != p.server.overworld()) p.teleportTo(p.server.overworld(), PackTests.HOME[0], PackTests.HOME[1], PackTests.HOME[2], 0F, 0F);
            if (p.isAlive()) p.setHealth(p.getMaxHealth());
        }); }, 80));   // (time for the Overworld chunks around the arena to load and tick again)
        l.add(new Step(null, () -> PackTests.server(p -> {
            if (p.level() != p.server.overworld()) p.teleportTo(p.server.overworld(), PackTests.HOME[0], PackTests.HOME[1], PackTests.HOME[2], 0F, 0F);
            PackTests.clear(p);
            PackTests.arena(p);
            p.setGameMode(GameType.SURVIVAL);
            O = p.position();
            H[0] = (Husk) PackTests.husk(p, 0, 10);
            D[0] = dome(p, enemy(p.serverLevel()), O.add(0, 0, 10), 80, 1);
            enemyDome = dome(p, enemy(p.serverLevel()), O, 40, 1);
            PackTests.log("kishi setup: " + where(p) + " husk chunk ticking=" + p.serverLevel().isPositionEntityTicking(H[0].blockPosition()));
        }), 40));
        l.add(new Step("pack_kishigrav", () -> {
            boolean dmz = dmz();
            double clientG = dmz ? dmzGravity(mc.player) : -1;
            PackTests.server(p -> {
                double mod = DmzKishiGravity.speedModifier(H[0]);
                PackTests.check("kishi dmz gravity on a mob", !dmz || Math.abs(mod + DmzKishiGravity.movePenalty(80)) < 0.01,
                        "dmz=" + dmz + " G=" + D[0].gravity() + " husk speed modifier=" + PackTests.f(mod) + " husk removed=" + H[0].isRemoved() + " dome removed=" + D[0].isRemoved());
                double serverG = dmz ? dmzGravity(p) : -1;
                PackTests.check("kishi dmz gravity on a player", !dmz || (serverG >= 39.9 && clientG >= 39.9),
                        "server=" + PackTests.f(serverG) + " client=" + PackTests.f(clientG) + " dome removed=" + enemyDome.isRemoved() + " " + where(p)
                        + " mixinLive=" + DmzKishiGravity.mixinLive + " " + DmzKishiGravity.selfTest());
                D[0].discard(); enemyDome.discard();
                p.setGameMode(GameType.CREATIVE);
                PackTests.clear(p);
            });
        }, 4));
    }
}

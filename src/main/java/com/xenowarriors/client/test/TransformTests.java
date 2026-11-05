package com.xenowarriors.client.test;

import com.xenowarriors.bot.BotForms;
import com.xenowarriors.bot.PartyBotEntity;
import com.xenowarriors.bot.PartyBots;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.client.party.PartyKiClient;
import com.xenowarriors.compat.DmzBotKi;
import com.xenowarriors.compat.DmzBotPower;
import com.xenowarriors.compat.DmzBots;
import com.xenowarriors.compat.DmzPartyKi;
import com.xenowarriors.compat.DmzPerCharacter;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.function.Consumer;

/**
 * UI tests for bots following the player's transformations and the party HUD's ki bars (focus "xform", and "dmzxform"
 * with DragonMineZ). Results are logged as "XW-TEST xform ...".
 * <ul>
 *   <li>XW: a Rhalix bot in a fight for 6 s without you transforming (baseline), then you go Fox King: how long until it
 *       calls up Demon's Remnants. Then you as Rhalix (Demon's Remnants) and a Mikiko bot (Fox King).</li>
 *   <li>DragonMineZ: a Saiyan bot with Super Saiyan unlocked, a weak mob nearby; 6 s with you in base form (baseline),
 *       then you go Super Saiyan: how long until it does; its power release following yours.</li>
 *   <li>Party HUD: ki numbers per member with DragonMineZ (all three HUD styles), none without.</li>
 * </ul>
 */
public final class TransformTests {
    private TransformTests() {}

    @FunctionalInterface
    public interface Adder { void add(String shot, Runnable action, int delay); }

    private static Adder S;
    private static Consumer<Consumer<ServerPlayer>> SERVER;
    private static Consumer<String> LOG;
    private static Consumer<CharacterType> CHAR;
    private static boolean DMZ;

    public static boolean handles(String focus) { return focus != null && focus.replaceFirst("^dmz", "").startsWith("xform"); }

    public static void build(Minecraft mc, String focus, Adder steps, Consumer<Consumer<ServerPlayer>> server, Consumer<CharacterType> character, Consumer<String> log) {
        S = steps; SERVER = server; LOG = log; CHAR = character;
        DMZ = focus.startsWith("dmz");
        setup(mc);
        xw(mc, CharacterType.MIKIKO, CharacterType.RHALIX);
        xw(mc, CharacterType.RHALIX, CharacterType.MIKIKO);
        if (DMZ) dmz(mc);
        hud(mc);
        step(null, () -> server(p -> { dump(); log("done"); }), 20);
    }

    static void log(String s) { LOG.accept("xform " + s); }
    static void server(Consumer<ServerPlayer> f) { SERVER.accept(f); }
    static void step(String shot, Runnable a, int delay) { S.add(shot, a, delay); }

    private static int dumped0, dumped1;

    static void dump() {
        synchronized (BotForms.LOG) { for (int i = dumped0; i < BotForms.LOG.size(); i++) log("formlog " + BotForms.LOG.get(i)); dumped0 = BotForms.LOG.size(); }
        synchronized (DmzBotPower.LOG) { for (int i = dumped1; i < DmzBotPower.LOG.size(); i++) log("powerlog " + DmzBotPower.LOG.get(i)); dumped1 = DmzBotPower.LOG.size(); }
    }

    static void noDmzScreen(Minecraft mc) {
        if (mc.screen != null && mc.screen.getClass().getName().startsWith("com.dragonminez")) mc.setScreen(null);
    }

    static Object call(Object o, String m, Object... args) {
        if (o == null) return null;
        try {
            for (var mm : o.getClass().getMethods()) if (mm.getName().equals(m) && mm.getParameterCount() == args.length) return mm.invoke(o, args);
        } catch (Throwable t) { return "?" + t.getClass().getSimpleName(); }
        return "?nomethod";
    }

    private static Vec3 AT;
    static Vec3 at(ServerPlayer p) { if (AT == null) AT = com.xenowarriors.debug.SkillTestRunner.arena(p); return AT; }

    static void protect(LivingEntity p) {
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20 * 60 * 30, 10, false, false));
        p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20 * 60 * 30, 5, false, false));
        p.setHealth(p.getMaxHealth());
    }

    static void clearHostiles(ServerPlayer p) {
        for (var m : p.serverLevel().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(64), m -> m instanceof net.minecraft.world.entity.monster.Enemy)) m.discard();
    }

    static void clearBots(ServerPlayer p) {
        for (PartyBotEntity b : PartyBots.bots(p)) { BotForms.resetForTest(b); PartyBots.forgetFake(b.getUUID()); b.discard(); }
    }

    static PartyBotEntity bot(ServerPlayer p, CharacterType t) {
        for (PartyBotEntity b : PartyBots.bots(p)) if (b.character() == t) return b;
        return null;
    }

    /** A tough enemy that never moves or hurts anyone and can't be killed: something to be "in a fight" with. */
    static Mob dummyFoe(ServerPlayer p, Vec3 at, boolean strong) {
        var z = (strong ? EntityType.HUSK : EntityType.ZOMBIE).create(p.serverLevel());
        z.moveTo(at.x, at.y, at.z, 180, 0);
        z.setNoAi(true); z.setPersistenceRequired();             // (not invulnerable: bots ignore what can't be hurt)
        z.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20 * 60 * 30, 9, false, false));
        if (strong) {
            z.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(400);
            z.setHealth(400); z.getPersistentData().putBoolean("xw_miniboss", true);
        }
        p.serverLevel().addFreshEntity(z);
        return z;
    }

    private static void setup(Minecraft mc) {
        step(null, () -> { mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE); mc.getToasts().clear(); mc.setScreen(null);
            com.xenowarriors.client.ClientSettings.setHudStyle("kh"); CHAR.accept(CharacterType.MIKIKO); }, 60);
        step(null, () -> { noDmzScreen(mc); server(p -> {
            if (DMZ) { makeDmz(p, "saiyan", 40); DmzPerCharacter.markCreatedForTest(p); }
            Vec3 at = at(p);
            p.teleportTo(at.x, at.y, at.z);
            protect(p);
            p.level().getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false, p.server);
            p.serverLevel().setDayTime(6000);
            log("setup dmz=" + DMZ + " partyKiAny=" + PartyKiClient.any());
        }); }, 40);
    }

    /** The player's DMZ character: this race, made, some stats. */
    static void makeDmz(ServerPlayer p, String race, int stats) {
        Object d = DmzPerCharacter.live(p);
        if (d == null) { log("no DMZ data"); return; }
        Object ch = call(d, "getCharacter");
        call(ch, "setRace", race);
        call(call(d, "getStatus"), "setHasCreatedCharacter", true);
        Object st = call(d, "getStats");
        for (String m : new String[]{"setStrength", "setStrikePower", "setResistance", "setVitality", "setKiPower", "setEnergy"}) call(st, m, stats);
        DmzPerCharacter.syncStats(p);
    }

    /** Super Saiyan unlocked (skill levels) and picked, as Dmz6Features does for its bot. */
    static void unlockSuper(Object d) {
        call(d, "updateTransformationSkillLimits", "saiyan");
        Object sk = call(d, "getSkills");
        if (call(sk, "getAllSkills") instanceof Map<?, ?> m) for (Object k : m.keySet()) {
            Object max = call(sk, "getMaxSkillLevel", k);
            if (String.valueOf(k).toLowerCase(Locale.ROOT).contains("super") && max instanceof Integer mx && mx > 0) call(sk, "setSkillLevel", k, Math.min(3, mx));
        }
        call(call(d, "getCharacter"), "setSelectedFormGroup", "supersaiyan");
    }

    // ---------------------------------------------------------------- XW forms

    private static final long[] T0 = new long[1];
    private static final Object[] FOE = new Object[1];

    private static void xw(Minecraft mc, CharacterType you, CharacterType mate) {
        String tag = you.getId() + "_" + mate.getId();
        step(null, () -> { mc.setScreen(null); server(p -> clearBots(p)); }, 10);
        step(null, () -> { CHAR.accept(you); }, 40);
        step(null, () -> { noDmzScreen(mc); mc.setScreen(null); server(p -> {
            Vec3 at = at(p);
            p.teleportTo(at.x, at.y, at.z); p.setYRot(0); p.setXRot(10);
            p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            protect(p); clearHostiles(p);
            PartyBotEntity b = PartyBots.spawn(p, mate, null);
            if (b == null) { log(tag + " bot spawn FAILED"); return; }
            b.moveTo(at.x + 1.5, at.y, at.z + 3, 180, 0);
            protect(b);
            b.karma = 100F;
            BotForms.resetForTest(b);
            Mob z = dummyFoe(p, new Vec3(at.x + 1.5, at.y, at.z + 7), true);
            FOE[0] = z;
            b.setTarget(z);
            T0[0] = p.level().getGameTime();
            log(tag + " baseline start: bot " + mate.getId() + " fighting " + z.getType().getDescriptionId() + " (strong) - " + BotForms.state(b) + " you=" + BotForms.playerForms(p));
        }); }, 10);
        step(null, () -> server(p -> { PartyBotEntity b = bot(p, mate); if (b != null && FOE[0] instanceof Mob z) b.setTarget(z); }), 60);
        step(null, () -> server(p -> { PartyBotEntity b = bot(p, mate); if (b != null && FOE[0] instanceof Mob z) b.setTarget(z); }), 60);
        step(null, () -> server(p -> { PartyBotEntity b = bot(p, mate);
            if (b == null) return;
            log(tag + " baseline after " + (p.level().getGameTime() - T0[0]) / 20.0 + " s without you transforming: " + BotForms.state(b)
                    + " | odds on its own in 6 s: " + Math.round((1 - Math.pow(1 - BotForms.BASE_CHANCE, 12)) * 1000) / 10.0 + "%");
            BotForms.resetForTest(b);
            b.karma = 100F;
            if (you == CharacterType.MIKIKO) { com.xenowarriors.mikiko.FoxKing.resetCooldown(p); com.xenowarriors.mikiko.FoxKing.toggle(p); }
            else com.xenowarriors.rhalix.DemonsRemnants.activate(p);
            T0[0] = p.level().getGameTime();
            log(tag + " you transform: " + BotForms.playerForms(p));
            dump();
        }), 2);
        for (int i = 0; i < 12; i++) {                                         // (6 s, every half second: when did it follow?)
            final int n = i;
            step(null, () -> server(p -> { PartyBotEntity b = bot(p, mate); if (b == null) return;
                if (FOE[0] instanceof Mob z) b.setTarget(z);
                BotForms.Kind k = BotForms.active(b);
                if (k != null && (n == 11 || T0[0] > 0)) {
                    if (T0[0] > 0) log(tag + " FOLLOWED: bot " + mate.getId() + " -> " + k + " " + (p.level().getGameTime() - T0[0]) + " ticks after you transformed; " + BotForms.state(b));
                    T0[0] = 0;
                } else if (n == 11) log(tag + " NOT followed within 6 s: " + BotForms.state(b));
            }), 10);
        }
        step(null, () -> { mc.options.hideGui = false; mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT); }, 15);
        step("xform_" + tag, () -> { mc.setScreen(null); server(p -> { PartyBotEntity b = bot(p, mate); if (b != null) log(tag + " shot: " + BotForms.state(b)); dump(); }); }, 3);
        step(null, () -> { mc.options.setCameraType(CameraType.FIRST_PERSON); server(p -> { if (FOE[0] instanceof Mob z) z.discard(); }); }, 5);
    }

    // ---------------------------------------------------------------- DragonMineZ forms

    private static void dmz(Minecraft mc) {
        step(null, () -> { mc.setScreen(null); server(p -> clearBots(p)); }, 10);
        step(null, () -> CHAR.accept(CharacterType.MIKIKO), 40);
        step(null, () -> { noDmzScreen(mc); mc.setScreen(null); server(p -> {
            Vec3 at = at(p);
            p.teleportTo(at.x, at.y, at.z); p.setYRot(0); p.setXRot(10);
            protect(p); clearHostiles(p);
            makeDmz(p, "saiyan", 40);
            Object pd = DmzPerCharacter.live(p);
            call(call(pd, "getCharacter"), "clearActiveForm");
            call(call(pd, "getResources"), "setPowerRelease", 50);
            DmzPerCharacter.syncStats(p);
            PartyBotEntity b = PartyBots.spawn(p, CharacterType.RHALIX, null);
            if (b == null) { log("dmz bot spawn FAILED"); return; }
            b.moveTo(at.x + 1.5, at.y, at.z + 3, 180, 0);
            protect(b);
            log("dmz bot race " + DmzBots.ensureRace(b));
            String key = DmzPerCharacter.botKey(b);
            CompoundTag tag = DmzPerCharacter.saved(p, key);
            Object d = DmzPerCharacter.blank(PartyBots.standIn(b));
            if (tag == null || d == null || !DmzPerCharacter.loadData(d, tag.copy())) { log("dmz bot DMZ data FAILED"); return; }
            call(call(d, "getCharacter"), "setRace", "saiyan");
            unlockSuper(d);
            Object st = call(d, "getStats");
            for (String m : new String[]{"setStrength", "setStrikePower", "setResistance", "setVitality", "setKiPower", "setEnergy"}) call(st, m, 60);
            log("dmz bot stored " + DmzPerCharacter.store(p, key, DmzPerCharacter.saveData(d)));
            DmzBotKi.forget(b.getUUID());
            // a weak mob close by (no AI, not fighting anyone): "near a fight", but nothing strong to transform for
            FOE[0] = dummyFoe(p, new Vec3(at.x + 1.5, at.y, at.z + 12), false);
            T0[0] = p.level().getGameTime();
        }); }, 40);
        step(null, () -> server(p -> { PartyBotEntity b = bot(p, CharacterType.RHALIX); if (b == null) return;
            DmzBotPower.setKiForTest(b, 1F); DmzBotPower.calmForTest(b); b.setTarget(null);
            log("dmz baseline start: " + DmzBotPower.state(b) + " you form=" + DmzBotPower.playerForm(p) + " release=" + DmzBotPower.playerRelease(p) + " near a fight=" + BotForms.nearCombat(b, p));
        }), 120);
        step(null, () -> server(p -> { PartyBotEntity b = bot(p, CharacterType.RHALIX); if (b == null) return;
            log("dmz baseline after 6 s in base form: " + DmzBotPower.state(b));
            // you go Super Saiyan (DragonMineZ's own next-form rules), full power
            Object pd = DmzPerCharacter.live(p);
            unlockSuper(pd);
            try {
                Class<?> th = Class.forName("com.dragonminez.common.util.TransformationsHelper"), sd = Class.forName("com.dragonminez.common.stats.StatsData");
                try { th.getMethod("ensureSelectedFormDefault", sd).invoke(null, pd); } catch (Throwable ignored) {}
                Object fd = th.getMethod("getNextAvailableForm", sd).invoke(null, pd);
                String name = fd == null ? null : (String) fd.getClass().getMethod("getName").invoke(fd);
                String group = (String) th.getMethod("getTransformTargetGroup", sd).invoke(null, pd);
                call(call(pd, "getCharacter"), "setActiveForm", group, name);
                call(call(pd, "getResources"), "setPowerRelease", 100);
                log("dmz you transform: " + group + "/" + name + " -> form " + DmzPerCharacter.class.getSimpleName() + " " + DmzBotPower.playerForm(p) + " release " + DmzBotPower.playerRelease(p));
            } catch (Throwable t) { log("dmz you transform FAILED " + t); }
            DmzPerCharacter.syncStats(p);
            T0[0] = p.level().getGameTime();
            dump();
        }), 2);
        for (int i = 0; i < 12; i++) {
            final int n = i;
            step(null, () -> server(p -> { PartyBotEntity b = bot(p, CharacterType.RHALIX); if (b == null) return;
                String st = DmzBotPower.state(b);
                if (!st.contains("form=null") && T0[0] > 0) { log("dmz FOLLOWED: " + (p.level().getGameTime() - T0[0]) + " ticks after you transformed; " + st + " | xw " + BotForms.state(b)); T0[0] = 0; }
                else if (n == 11) log("dmz after 6 s: " + st + " | xw " + BotForms.state(b));
            }), 10);
        }
        step(null, () -> { mc.options.hideGui = false; mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT); }, 15);
        step("xform_dmz_follow", () -> { mc.setScreen(null); server(p -> dump()); }, 3);
        step(null, () -> mc.options.setCameraType(CameraType.FIRST_PERSON), 5);
    }

    // ---------------------------------------------------------------- the party HUD's ki bars

    private static void hud(Minecraft mc) {
        step(null, () -> { mc.setScreen(null); server(p -> {
            if (PartyBots.bots(p).isEmpty()) { PartyBotEntity b = PartyBots.spawn(p, CharacterType.RHALIX, null); if (b != null) protect(b); }
            if (PartyBots.bots(p).size() < 2) { PartyBotEntity b = PartyBots.spawn(p, CharacterType.KAERU, null); if (b != null) protect(b); }
            for (PartyBotEntity b : PartyBots.bots(p)) if (DMZ) DmzBotPower.setKiForTest(b, b.character() == CharacterType.KAERU ? 0.4F : 0.85F);
            p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            p.setYRot(0); p.setXRot(10);
        }); mc.options.hideGui = false; mc.options.setCameraType(CameraType.FIRST_PERSON); mc.options.guiScale().set(0); mc.resizeDisplay(); }, 40);
        for (String style : new String[]{"classic", "kh", "blazing"}) {
            step(null, () -> { mc.setScreen(null); com.xenowarriors.client.ClientSettings.setHudStyle(style); }, 15);
            step("xform_party_ki_" + style + (DMZ ? "_dmz" : "_plain"), () -> { mc.setScreen(null);
                StringBuilder sb = new StringBuilder();
                for (var m : com.xenowarriors.client.party.PartyClient.members()) {
                    float[] k = PartyKiClient.of(m.id());
                    sb.append(m.name()).append(k == null ? " ki=none" : " ki=" + Math.round(k[0]) + "/" + Math.round(k[1])).append("; ");
                }
                log("hud " + style + " dmz=" + DMZ + " kiShown=" + PartyKiClient.any() + " packets=" + PartyKiClient.received + " members: " + sb);
            }, 3);
        }
        step(null, () -> server(p -> log("server ki packets sent " + DmzPartyKi.sent + " entries now " + DmzPartyKi.entries(p).size())), 3);
        // (the numbers change as ki is spent: one more packet, not one every tick)
        step(null, () -> server(p -> { for (PartyBotEntity b : PartyBots.bots(p)) if (DMZ) DmzBotPower.setKiForTest(b, 0.2F); }), 20);
        step(null, () -> log("after a ki change: packets=" + PartyKiClient.received + " " + PartyKiClient.dump()), 3);
        step(null, () -> {}, 40);
        step(null, () -> log("2 s later: packets=" + PartyKiClient.received + " (sent only when someone's ki number changed; at most 4/s)"), 3);
        step(null, () -> com.xenowarriors.client.ClientSettings.setHudStyle("kh"), 3);
    }
}

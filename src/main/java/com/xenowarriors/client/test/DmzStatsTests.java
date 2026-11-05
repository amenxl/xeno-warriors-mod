package com.xenowarriors.client.test;

import com.xenowarriors.bot.PartyBotEntity;
import com.xenowarriors.bot.PartyBots;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.compat.*;
import com.xenowarriors.soul.SoulEntity;
import com.xenowarriors.soul.SoulSkills;
import com.xenowarriors.soul.SoulSplit;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.ModList;

import java.util.*;
import java.util.function.Consumer;

/**
 * Bots and Soul Splitter souls with (focus "dmzstats") and without (focus "stats") DragonMineZ: the bot's max health,
 * melee, damage taken and regen from its own DMZ character; a soul's share of its owner's XW and DMZ stats. Results
 * are logged as "XW-TEST dmzstats ...".
 */
public final class DmzStatsTests {
    private DmzStatsTests() {}

    @FunctionalInterface
    public interface Adder { void add(String shot, Runnable action, int delay); }

    private static Adder S;
    private static Consumer<Consumer<ServerPlayer>> SERVER;
    private static Consumer<String> LOG;
    private static Consumer<CharacterType> CHAR;
    private static boolean DMZ;
    private static final PartyBotEntity[] BOT = new PartyBotEntity[1];
    private static Vec3 AT;

    public static boolean handles(String focus) { return focus != null && focus.replaceFirst("^dmz", "").startsWith("stats"); }

    public static void build(Minecraft mc, String focus, Adder steps, Consumer<Consumer<ServerPlayer>> server, Consumer<CharacterType> character, Consumer<String> log) {
        S = steps; SERVER = server; LOG = log; CHAR = character;
        DMZ = ModList.get().isLoaded("dragonminez");
        step(null, () -> { mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE); mc.getToasts().clear(); mc.setScreen(null); CHAR.accept(CharacterType.MIKIKO); }, 60);
        step(null, () -> { noDmzScreen(mc); server(p -> {
            log("dragonminez loaded=" + DMZ);
            AT = com.xenowarriors.debug.SkillTestRunner.arena(p);
            p.teleportTo(AT.x, AT.y, AT.z);
            p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            p.level().getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false, p.server);
            p.serverLevel().setDayTime(6000);
            clearHostiles(p);
            if (DMZ) { make(p, 50); DmzPerCharacter.markCreatedForTest(p); }
            PartyBotEntity b = PartyBots.spawn(p, CharacterType.RHALIX, null);
            if (b == null) { log("bot spawn FAILED"); return; }
            b.moveTo(AT.x + 2, AT.y, AT.z + 2, 0, 0);
            BOT[0] = b;
            log("bot spawned: level " + b.botLevel() + " maxHp " + b.getMaxHealth() + " attack " + b.getAttributeValue(Attributes.ATTACK_DAMAGE) + " (before its DMZ character is set)");
            if (DMZ) log("bot race " + DmzBots.ensureRace(b));
        }); }, 40);
        for (int stats : new int[]{50, 250}) round(mc, stats);
        soul(mc, 50);
        if (DMZ) soul(mc, 250);
        step(null, () -> { noDmzScreen(mc); server(p -> { SoulSplit.end(p, null); log("done"); }); }, 20);
    }

    static void log(String s) { LOG.accept("dmzstats " + s); }
    static void server(Consumer<ServerPlayer> f) { SERVER.accept(f); }
    static void step(String shot, Runnable a, int delay) { S.add(shot, a, delay); }

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

    static void clearHostiles(ServerPlayer p) {
        for (var m : p.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, p.getBoundingBox().inflate(64), m -> m instanceof net.minecraft.world.entity.monster.Enemy)) m.discard();
    }

    /** The owner's DMZ character: a Saiyan with all six stats at this value. */
    static void make(ServerPlayer p, int stats) {
        Object d = DmzPerCharacter.live(p);
        if (d == null) { log("no DMZ data"); return; }
        call(call(d, "getCharacter"), "setRace", "saiyan");
        call(call(d, "getStatus"), "setHasCreatedCharacter", true);
        Object st = call(d, "getStats");
        for (String m : new String[]{"setStrength", "setStrikePower", "setResistance", "setVitality", "setKiPower", "setEnergy"}) call(st, m, stats);
        DmzPerCharacter.syncStats(p);
    }

    /** The bot's DMZ character becomes a copy of the owner's (so a player with that character is right there to compare). */
    static void copyToBot(ServerPlayer p, PartyBotEntity b) {
        CompoundTag tag = DmzPerCharacter.saveData(DmzPerCharacter.live(p));
        log("bot DMZ character = copy of the owner's: stored " + DmzPerCharacter.store(p, DmzPerCharacter.botKey(b), tag));
        DmzBotKi.forget(b.getUUID());
    }

    static Zombie dummy(ServerPlayer p, Vec3 at, double hp) {
        Zombie z = EntityType.ZOMBIE.create(p.serverLevel());
        z.moveTo(at.x, at.y, at.z, 0, 0);
        z.setNoAi(true);
        z.getAttribute(Attributes.MAX_HEALTH).setBaseValue(hp);
        z.setHealth(z.getMaxHealth());
        z.getAttribute(Attributes.ARMOR).setBaseValue(0);
        p.serverLevel().addFreshEntity(z);
        return z;
    }

    static double r(double v) { return Math.round(v * 100) / 100.0; }

    private static void round(Minecraft mc, int stats) {
        step(null, () -> { noDmzScreen(mc); server(p -> {
            PartyBotEntity b = BOT[0];
            if (b == null) return;
            log("---- stats " + stats + (DMZ ? "" : " (no DragonMineZ: only the XW level bonuses)"));
            log("bot BEFORE: maxHp " + b.getMaxHealth() + " attack " + r(b.getAttributeValue(Attributes.ATTACK_DAMAGE)));
            if (DMZ) { make(p, stats); copyToBot(p, b); }
        }); }, 10);
        step(null, () -> { noDmzScreen(mc); server(p -> {               // (DMZ has put the owner's health bonus on by now; the bot's refresh ran too)
            PartyBotEntity b = BOT[0];
            if (b == null) return;
            DmzBotStats.apply(b);
            b.setHealth(b.getMaxHealth());
            double[] own = DmzBotStats.playerStats(p);
            double ownMod = 0;
            for (var m : p.getAttribute(Attributes.MAX_HEALTH).getModifiers()) if (m.getName().contains("DMZ")) ownMod += m.getAmount();
            log("owner (player, same DMZ character): maxHp " + r(p.getMaxHealth()) + " DMZ health modifier " + r(ownMod) + " getHealthBonus " + r(own[0]) + " meleeDamage " + r(own[1])
                    + " => a player of it has 20 + " + r(own[0]) + " = " + r(20 + own[0]) + " + XW hearts");
            log("bot AFTER: maxHp " + r(b.getMaxHealth()) + " = level health " + r(b.getAttribute(Attributes.MAX_HEALTH).getBaseValue()) + " + DMZ " + r(b.getMaxHealth() - b.getAttribute(Attributes.MAX_HEALTH).getBaseValue())
                    + " | attack attr " + r(b.getAttributeValue(Attributes.ATTACK_DAMAGE)) + " (base " + r(b.getAttribute(Attributes.ATTACK_DAMAGE).getBaseValue()) + ") | " + DmzBotKi.kiDamageForTest(b)
                    + " | power " + DmzBotPower.state(b) + " | regen/s " + r(DmzBotStats.regenFor(b)));
            for (var m : PartyBots.syncMembers(p)) log("party card: " + m.name() + " hp " + r(m.health()) + "/" + r(m.maxHealth()) + " lv " + m.level());
            // melee: one punch at a dummy with no armour
            Zombie z = dummy(p, AT.add(4, 0, 4), 100000);
            b.moveTo(AT.x + 4, AT.y, AT.z + 3, 0, 0);
            float h0 = z.getHealth();
            b.doHurtTarget(z);
            log("bot melee hit on a zombie: " + r(h0 - z.getHealth()));
            z.discard();
            // a zombie's hit on the bot
            Zombie za = dummy(p, AT.add(6, 0, 2), 20);
            b.invulnerableTime = 0;
            float bh = b.getHealth();
            DmzBotStats.TEST_LOG = true;
            boolean hit = b.hurt(b.damageSources().mobAttack(za), 10F);
            DmzBotStats.TEST_LOG = false;
            log("zombie hits the bot for 10: hurt=" + hit + " took " + r(bh - b.getHealth()) + " (" + r(b.getHealth()) + "/" + r(b.getMaxHealth()) + ")"
                    + " | DMZ mitigation of 10 for the owner (same character): " + r(DmzBotStats.playerMitigated(p, 10F)));
            za.discard();
            List<String> l = DmzBotStats.LOG;
            synchronized (l) { for (int i = Math.max(0, l.size() - 4); i < l.size(); i++) log("stats log: " + l.get(i)); }
            b.setHealth(Math.max(1F, b.getMaxHealth() * 0.5F));
            b.lastHurtAt = -100000;
            log("bot set to half health " + r(b.getHealth()) + " for the regen check");
        }); }, 80);
        step("dmzstats_party_" + stats, () -> { noDmzScreen(mc); mc.options.hideGui = false; mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            if (mc.level != null && BOT[0] != null && mc.level.getEntity(BOT[0].getId()) instanceof LivingEntity cb) log("client sees the bot at " + r(cb.getHealth()) + "/" + r(cb.getMaxHealth()));
            server(p -> { PartyBotEntity b = BOT[0]; if (b != null) log("regen after ~5 s out of a fight: " + r(b.getHealth()) + "/" + r(b.getMaxHealth())); });
        }, 100);
    }

    private static void soul(Minecraft mc, int stats) {
        LivingEntity[] tgt = new LivingEntity[1];
        step(null, () -> { noDmzScreen(mc); server(p -> {
            log("---- soul, owner stats " + stats);
            SoulSplit.end(p, null);
            if (DMZ) make(p, stats);
            SoulSkills.ABILITY_LEVEL = (pl, id) -> 40;                       // (tier 3+: it casts the owner's skills)
            p.getCapability(com.xenowarriors.character.ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> { d.statLevel[0] = 50; d.statLevel[1] = 50; d.statLevel[2] = 50; });   // (XW Speed / Attack / Defense 50)
            com.xenowarriors.stats.Stats.reapply(p);
            p.setHealth(p.getMaxHealth());
        }); }, 60);
        step(null, () -> { noDmzScreen(mc); server(p -> {
            float fullHp = p.getMaxHealth();
            double[] own = DmzBotStats.playerStats(p);
            Zombie z = dummy(p, AT.add(-4, 0, 3), 100000);
            float h0 = z.getHealth();
            p.resetAttackStrengthTicker();
            p.attack(z);
            float ownerPunch = h0 - z.getHealth();
            log("owner maxHp " + r(fullHp) + " DMZ {healthBonus, melee} " + r(own[0]) + ", " + r(own[1]) + " | owner punch on a zombie " + r(ownerPunch)
                    + " | owner skill multiplier ki x" + r(DmzScaling.skillMultiplier(p, false)) + " strike x" + r(DmzScaling.skillMultiplier(p, true)));
            SoulSplit.start(p);
            log("split: " + SoulSplit.lastStats);
            List<SoulEntity> souls = p.serverLevel().getEntitiesOfClass(SoulEntity.class, p.getBoundingBox().inflate(8), s -> p.getUUID().equals(s.ownerId()));
            for (SoulEntity s : souls) {
                log("soul maxHp " + r(s.getMaxHealth()) + " (owner now " + r(p.getMaxHealth()) + ") attack " + r(s.getAttributeValue(Attributes.ATTACK_DAMAGE)) + " speed " + r(s.getAttributeValue(Attributes.MOVEMENT_SPEED)));
                s.moveTo(z.getX(), z.getY(), z.getZ() - 1.5, 0, 0);
                float a = z.getHealth();
                s.doHurtTarget(z);
                log("soul punch on a zombie " + r(a - z.getHealth()) + " (owner's was " + r(ownerPunch) + ")");
                Zombie za = dummy(p, AT.add(-6, 0, 1), 20);
                s.invulnerableTime = 0;
                float sh = s.getHealth();
                s.hurt(s.damageSources().mobAttack(za), 10F);
                log("zombie hits the soul for 10: took " + r(sh - s.getHealth()) + " (" + SoulSplit.lastSoulHurt + ")");
                za.discard();
                s.setHealth(s.getMaxHealth());
                s.setTarget(z);
            }
            tgt[0] = z;
            SoulSplit.lastSkillHit = "";
        }); }, 40);
        step("dmzstats_soul_" + stats, () -> { noDmzScreen(mc); mc.options.hideGui = false; server(p -> {
            log("soul skills: casts so far " + SoulSkills.casts + " last " + SoulSkills.lastSkill + " | last soul skill hit " + SoulSplit.lastSkillHit);
            if (tgt[0] != null) tgt[0].discard();
        }); }, 200);
    }
}

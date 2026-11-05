package com.xenowarriors.client.test;

import com.xenowarriors.bot.PartyBotEntity;
import com.xenowarriors.bot.PartyBots;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.character.ModCapabilities;
import com.xenowarriors.client.UiTest;
import com.xenowarriors.client.UiTest.Step;
import com.xenowarriors.compat.GunLevel;
import com.xenowarriors.party.Bonds;
import com.xenowarriors.party.Synergy;
import com.xenowarriors.skill.Skill;
import com.xenowarriors.skill.SkillLevels;
import com.xenowarriors.skill.SkillPool;
import com.xenowarriors.stats.Appetite;
import com.xenowarriors.stats.Stat;
import com.xenowarriors.stats.Stats;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.GameType;

import java.util.List;

/**
 * UI tests for player / bot stat parity (focus "parity", "dmzparity" with DragonMineZ): bond hearts on bots (from
 * either side of the bond, stacking with level and DragonMineZ health, kept through the 1 s DragonMineZ refresh and a
 * re-add from a saved record), bot synergies, Attack / Defense levels, ability levels, Appetite and gun level.
 * Results are logged as "XW-TEST parity ...".
 */
public final class ParityTests {
    private ParityTests() {}

    public static boolean handles(String focus) { return focus != null && focus.replaceFirst("^dmz", "").startsWith("parity"); }

    private static void log(String s) { UiTest.log("parity " + s); }

    private static final Object[] BOT = new Object[1], ZOMBIE = new Object[1];
    private static final float[] HP = new float[8];

    private static PartyBotEntity bot(ServerPlayer p) {
        if (BOT[0] instanceof PartyBotEntity b && b.isAlive() && !b.isRemoved()) return b;
        for (PartyBotEntity b : PartyBots.bots(p)) if (b.character() == CharacterType.RHALIX) { BOT[0] = b; return b; }
        return null;
    }

    private static Zombie dummy(ServerPlayer p) {
        if (ZOMBIE[0] instanceof Zombie z && z.isAlive()) { z.setHealth(z.getMaxHealth()); z.invulnerableTime = 0; z.hurtTime = 0; return z; }
        Zombie z = EntityType.ZOMBIE.create(p.serverLevel());
        z.moveTo(p.getX() + 4, p.getY(), p.getZ() + 4, 0, 0);
        z.setNoAi(true); z.setPersistenceRequired();
        z.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100000); z.setHealth(100000);
        z.getAttribute(Attributes.ARMOR).setBaseValue(0);
        p.serverLevel().addFreshEntity(z);
        ZOMBIE[0] = z;
        return z;
    }

    private static float botHit(ServerPlayer p, PartyBotEntity b, float amount) {
        Zombie z = dummy(p);
        float h = z.getHealth();
        z.hurt(z.damageSources().mobAttack(b), amount);
        return h - z.getHealth();
    }

    /** Sets the owner's saved stat levels for a character it isn't playing. */
    private static void setStats(ServerPlayer p, CharacterType t, int speed, int attack, int defense) {
        p.getCapability(ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> {
            CompoundTag c = d.charStats.getCompound(t.getId());
            c.putIntArray("lv", new int[]{speed, attack, defense});
            d.charStats.put(t.getId(), c);
        });
    }

    /** Max health split into its parts: level base + DragonMineZ + bond (+ anything else). */
    private static String parts(PartyBotEntity b) {
        var a = b.getAttribute(Attributes.MAX_HEALTH);
        StringBuilder sb = new StringBuilder("maxHp=" + b.getMaxHealth() + " = level " + a.getBaseValue());
        for (var m : a.getModifiers()) sb.append(" + ").append(m.getName()).append(" ").append(Math.round(m.getAmount()));
        return sb.toString();
    }

    /** The bond modifier is on it with its full amount, and max health is all the parts added up. */
    private static boolean stacked(PartyBotEntity b) {
        var a = b.getAttribute(Attributes.MAX_HEALTH);
        var bond = a.getModifier(Bonds.BOT_HEALTH_ID);
        double sum = a.getBaseValue();
        for (var m : a.getModifiers()) sum += m.getAmount();
        return bond != null && Math.abs(bond.getAmount() - Bonds.hearts(Bonds.best(b)) * 2.0) < 0.01 && Math.abs(sum - b.getMaxHealth()) < 0.01;
    }

    private static String f(float v) { return String.format(java.util.Locale.ROOT, "%.2f", v); }

    public static void build(Minecraft mc, List<Step> l, String focus) {
        l.add(new Step(null, () -> { UiTest.noToasts(mc); mc.setScreen(null); UiTest.character(CharacterType.MIKIKO);
            UiTest.server(p -> { p.setGameMode(GameType.SURVIVAL); p.serverLevel().setDayTime(6000); p.setHealth(p.getMaxHealth()); }); }, 40));
        l.add(new Step(null, () -> UiTest.server(p -> {
            for (var b : new java.util.ArrayList<>(PartyBots.bots(p))) b.discard();
            setStats(p, CharacterType.RHALIX, 0, 0, 0);
            Bonds.set(p, "rhalix", 0, 0);
            BOT[0] = PartyBots.spawn(p, CharacterType.RHALIX, null);
        }), 60));
        // ---- 1. bond hearts
        l.add(new Step(null, () -> UiTest.server(p -> {
            PartyBotEntity b = bot(p);
            if (b == null) { log("FAIL no bot"); return; }
            Bonds.setBot(b, "mikiko", 0, 0);
            HP[0] = b.getMaxHealth();
            log("bot level=" + b.botLevel() + " maxHp bond0=" + HP[0] + " (level hp " + b.getAttribute(Attributes.MAX_HEALTH).getBaseValue() + ") player maxHp=" + p.getMaxHealth()
                    + " per10=" + Bonds.heartsPer10());
            Bonds.set(p, "rhalix", 50, 0);                                        // the owner's side
            HP[1] = b.getMaxHealth();
            log("owner bond 50: bot best=" + Bonds.best(b) + " maxHp=" + HP[1] + " (expect +" + Bonds.hearts(50) * 2 + ") player best=" + Bonds.best(p) + " playerMaxHp=" + p.getMaxHealth()
                    + " -> " + (Math.abs(HP[1] - HP[0] - Bonds.hearts(50) * 2) < 0.01 ? "PASS" : "FAIL"));
            Bonds.set(p, "rhalix", 0, 0);
            Bonds.setBot(b, "mikiko", 100, 0);                                    // the bot's own side
            HP[2] = b.getMaxHealth();
            log("bot own bond 100: bot best=" + Bonds.best(b) + " maxHp=" + HP[2] + " (expect +" + Bonds.hearts(100) * 2 + ") -> "
                    + (Math.abs(HP[2] - HP[0] - Bonds.hearts(100) * 2) < 0.01 ? "PASS" : "FAIL") + "; rises with bond: " + (HP[0] < HP[1] && HP[1] < HP[2] ? "PASS" : "FAIL"));
            // the level and DragonMineZ refreshes must not drop it
            b.applyLevel(PartyBots.levelFor(p, CharacterType.RHALIX));
            com.xenowarriors.compat.DmzBotStats.apply(b);
            log("after applyLevel + DmzBotStats.apply: maxHp=" + b.getMaxHealth() + " -> " + (Math.abs(b.getMaxHealth() - HP[2]) < 0.01 ? "PASS" : "FAIL")
                    + " modifiers=" + b.getAttribute(Attributes.MAX_HEALTH).getModifiers().stream().map(m -> m.getName() + "=" + Math.round(m.getAmount())).toList());
            b.setHealth(b.getMaxHealth() - 3F);
            HP[3] = b.getHealth();
        }), 30));
        l.add(new Step(null, () -> UiTest.server(p -> {
            PartyBotEntity b = bot(p);
            if (b == null) return;
            log("after 1.5 s (DmzBotStats 1 s refresh, Bonds 5 s refresh): " + parts(b) + " hp=" + b.getHealth() + " -> " + (stacked(b) && b.getHealth() >= HP[3] - 0.5F ? "PASS" : "FAIL"));
            HP[4] = b.getMaxHealth(); HP[5] = b.getHealth();
            CompoundTag rec = b.record();                                         // re-add / relog: a saved record spawns it again
            b.discard();
            PartyBotEntity n = PartyBots.spawn(p, CharacterType.RHALIX, rec);
            BOT[0] = n;
            log("re-added from record: " + (n == null ? "none" : parts(n)) + " hp=" + (n == null ? -1 : n.getHealth()) + " (was " + HP[4] + " / " + HP[5] + ") -> "
                    + (n != null && stacked(n) && Math.abs(n.getMaxHealth() - HP[4]) < 0.01 && Math.abs(n.getHealth() - HP[5]) < 0.5 ? "PASS" : "FAIL"));
        }), 30));
        // ---- 2. bot synergy (Partners: Rhalix + Mikiko)
        l.add(new Step(null, () -> UiTest.server(p -> {
            PartyBotEntity b = bot(p);
            if (b == null) return;
            b.setNoAi(true);
            Synergy.TEST_LOG = true;
            Bonds.setBot(b, "mikiko", 0, 0);
            log("bot synergies=" + Synergy.of(b) + " partners=" + Synergy.partners(b).size() + " player synergies=" + Synergy.of(p));
            float s0 = botHit(p, b, 10F);
            Bonds.setBot(b, "mikiko", 100, 0);
            float s100 = botHit(p, b, 10F);
            log("bot Partners hit bond0=" + f(s0) + " bond100=" + f(s100) + " ratio=" + String.format(java.util.Locale.ROOT, "%.3f", s100 / Math.max(0.001F, s0))
                    + " (expect " + String.format(java.util.Locale.ROOT, "%.3f", 1.30F / 1.15F) + ") -> " + (Synergy.of(b).contains(Synergy.PARTNERS) && Math.abs(s100 / Math.max(0.001F, s0) - 1.30F / 1.15F) < 0.01 ? "PASS" : "FAIL"));
            Synergy.TEST_LOG = false;
        }), 10));
        // ---- 3. Attack / Defense levels and Character Level hearts
        l.add(new Step(null, () -> UiTest.server(p -> {
            PartyBotEntity b = bot(p);
            if (b == null) return;
            float a0 = botHit(p, b, 10F);
            setStats(p, CharacterType.RHALIX, 0, 40, 40);
            b.applyLevel(PartyBots.levelFor(p, CharacterType.RHALIX));
            float a40 = botHit(p, b, 10F);
            log("bot Attack lv " + PartyBots.statLevel(b, Stat.ATTACK) + ": hit " + f(a0) + " -> " + f(a40) + " ratio=" + String.format(java.util.Locale.ROOT, "%.3f", a40 / Math.max(0.001F, a0))
                    + " (expect " + String.format(java.util.Locale.ROOT, "%.3f", 1F + Stat.ATTACK.bonus(40)) + ")");
            Zombie z = dummy(p);
            b.invulnerableTime = 0;
            b.setHealth(b.getMaxHealth());
            float h = b.getHealth();
            b.hurt(b.damageSources().mobAttack(z), 10F);
            log("bot Defense lv " + PartyBots.statLevel(b, Stat.DEFENSE) + ": took " + f(h - b.getHealth()) + " of 10 (expect about " + f(10F * (1F - Stat.DEFENSE.bonus(40))) + " before other reductions)");
            log("bot Character Level " + b.botLevel() + ": level hp " + b.getAttribute(Attributes.MAX_HEALTH).getBaseValue() + " (a player of it: 20 + " + Stats.bonusHearts(b.botLevel()) * 2 + ")");
            b.setHealth(b.getMaxHealth());
        }), 10));
        // ---- 4. ability levels, Appetite, gun level
        l.add(new Step(null, () -> UiTest.server(p -> {
            PartyBotEntity b = bot(p);
            if (b == null) return;
            Skill sk = SkillPool.inSlot(CharacterType.RHALIX, SkillPool.defaultLoadout(), 0);
            if (sk != null) {
                int before = SkillLevels.abilityLevel(p, CharacterType.RHALIX, sk.getId());
                for (int i = 0; i < 12; i++) SkillLevels.used(b, sk);
                log("bot skill uses: " + sk.getId() + " ability level " + before + " -> " + SkillLevels.abilityLevel(p, CharacterType.RHALIX, sk.getId())
                        + " (owner's save for Rhalix) cost x" + f(SkillLevels.costFor(b, sk.getId(), 100) / 100F));
            }
            int ap = Appetite.level(b);
            Appetite.addXp(b, 200F);
            log("bot appetite " + ap + " -> " + Appetite.level(b) + " food bonus +" + Math.round(Appetite.bonus(b) * 100) + "% (owner's own appetite " + Appetite.level(p) + ")");
            int gl = GunLevel.level(b);
            float shot = GunLevel.botShot(b, dummy(p), 10F);
            for (int i = 0; i < 20; i++) GunLevel.addXp(b, 30F);
            log("bot gun level " + gl + " -> " + GunLevel.level(b) + " shot 10 -> " + f(shot) + " (x" + f(GunLevel.multiplier(b)) + " now) owner's own gun level " + GunLevel.level(p));
            log("fall multiplier bot rhalix=" + com.xenowarriors.character.FallDamage.multiplier(CharacterType.RHALIX) + " player mikiko=" + com.xenowarriors.character.FallDamage.multiplier(p));
        }), 10));
        // ---- 5. the party menu: bond and hearts shown
        l.add(new Step(null, () -> UiTest.server(p -> Bonds.set(p, "rhalix", 60, 0)), 10));
        l.add(new Step(null, () -> mc.setScreen(new com.xenowarriors.client.party.PartySetupScreen()), 60));
        l.add(new Step("parity_party", () -> {
            var c = com.xenowarriors.client.party.PartySetupScreen.lastCards();
            log("party cards=" + (c == null ? "" : c.cards().stream().map(x -> x.name() + ":bond" + x.bond()).toList()) + " playerHearts=" + (c == null ? 0 : c.bondHearts()));
        }, 5));
        l.add(new Step(null, () -> { mc.setScreen(null); UiTest.server(p -> {
            PartyBotEntity b = bot(p);
            log("final: bot maxHp=" + (b == null ? -1 : b.getMaxHealth()) + " best=" + (b == null ? -1 : Bonds.best(b)) + " player maxHp=" + p.getMaxHealth() + " best=" + Bonds.best(p));
            for (var x : new java.util.ArrayList<>(PartyBots.bots(p))) x.discard();
            log("DONE");
        }); }, 20));
    }
}

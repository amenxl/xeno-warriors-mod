package com.xenowarriors.party;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.bot.PartyBotEntity;
import com.xenowarriors.bot.PartyBots;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.character.ModCapabilities;
import com.xenowarriors.kaeru.KaeruSoul;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.*;

/**
 * Team synergy: certain characters fight better together when they're in the same party and within 24 blocks
 * (party bots count as partners too, so it can be tried solo).
 *  Partners (Rhalix + Mikiko)          - both +15% damage, and Karma comes back faster.
 *  Skyland Bond (Rhalix + Sky Patrol / Kyrie) - both +10% damage (+20% with the whole Sky Patrol team).
 *  Goofy Rivals (Mikiko + Ultima)      - every kill one of them gets fires the OTHER up (+5% damage per kill, up to +25%,
 *                                         for 20 s - they can't let the other win), and now and then they goof off
 *                                         together for a random little bonus.
 *  Soul Link (Mikiko + Kaeru)          - both +20% damage and take 15% less; Karma comes back faster. But if one of them
 *                                         fully dies, the other dies too.
 * Characters that aren't playable yet (Kyrie, the Sky Patrol) are already listed so the bond works once they're added.
 * <p>Bots get their synergies the same way, from their own teammates near them and their own bonds (Bonds.botLevel):
 * the damage bonuses on everything they hit (melee and skills), Soul Link's lighter hits, faster Karma, Old Friends'
 * cheaper skills, Married's heals and Goofy Rivals' kill streaks.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Synergy {
    public static final double RANGE = 24;
    public static final String PARTNERS = "Partners", SKYLAND = "Skyland Bond", GOOFY = "Goofy Rivals", SOUL_LINK = "Soul Link", BROTHERS = "Brothers",
            TENJO = "Tenjo Siblings", OLD_FRIENDS = "Old Friends", MARRIED = "Married";
    /** Sky Patrol members (by character id) - for when they become playable. */
    public static final Set<String> SKY_PATROL = Set.of("sam", "lexy", "max", "technica");
    private static final Map<UUID, Set<String>> ACTIVE = new HashMap<>();
    /** The same for bots (by bot UUID). */
    private static final Map<UUID, Set<String>> BOT_ACTIVE = new HashMap<>();
    private static final Map<UUID, int[]> RIVAL = new HashMap<>();      // {stacks, ticks left}
    private static final Set<UUID> DYING = new HashSet<>();

    private Synergy() {}

    public static Set<String> of(ServerPlayer p) { return ACTIVE.getOrDefault(p.getUUID(), Set.of()); }
    public static Set<String> of(PartyBotEntity b) { return BOT_ACTIVE.getOrDefault(b.getUUID(), Set.of()); }
    public static boolean has(PartyBotEntity b, String s) { return of(b).contains(s); }

    /** A bot's synergy strength: from its best bond with a teammate near it who completes the synergy. */
    public static int bond(PartyBotEntity b, String s) {
        Def d = def(s);
        if (d == null) return 0;
        Set<String> need = d.partnersFor(id(b));
        int best = 0;
        for (LivingEntity e : partners(b)) if (need.contains(id(e))) best = Math.max(best, Bonds.botLevel(b, e));
        return best;
    }

    public static float strength(PartyBotEntity b, String s) { return Bonds.strength(bond(b, s)); }

    /** A bot's teammates close enough to count (its owner, the party players and their bots), not the bot itself. */
    public static List<LivingEntity> partners(PartyBotEntity b) {
        List<LivingEntity> out = new ArrayList<>();
        ServerPlayer o = b.owner();
        if (o == null) return out;
        for (ServerPlayer m : new LinkedHashSet<>(Parties.members(o))) {
            if (m.level() == b.level() && m.distanceToSqr(b) < RANGE * RANGE && m.isAlive() && !m.isSpectator()) out.add(m);
            for (PartyBotEntity x : PartyBots.bots(m))
                if (x != b && x.level() == b.level() && x.distanceToSqr(b) < RANGE * RANGE && x.isAlive() && !x.downed()) out.add(x);
        }
        return out;
    }

    /**
     * Every synergy, for the party menu: its name, the characters it needs (any of {@code a} with any of {@code b}),
     * what it does at bond 0 (the numbers scale with the bond, see {@link #effect}), and its downside (null if none).
     */
    public record Def(String name, Set<String> a, Set<String> b, String members, String downside) {
        public boolean involves(String c) { return a.contains(c) || b.contains(c); }
        /** The characters that complete it for {@code c}. */
        public Set<String> partnersFor(String c) {
            Set<String> s = new LinkedHashSet<>();
            if (a.contains(c)) s.addAll(b);
            if (b.contains(c)) s.addAll(a);
            return s;
        }
    }

    private static final Set<String> SKY_ALL = Set.of("kyrie", "sam", "lexy", "max", "technica");
    public static final List<Def> DEFS = List.of(
            new Def(PARTNERS, Set.of("rhalix"), Set.of("mikiko"), "Rhalix + Mikiko", null),
            new Def(SKYLAND, Set.of("rhalix"), SKY_ALL, "Rhalix + Sky Patrol / Kyrie", null),
            new Def(GOOFY, Set.of("mikiko"), Set.of("ultima"), "Mikiko + Ultima", null),
            new Def(SOUL_LINK, Set.of("mikiko"), Set.of("kaeru"), "Mikiko + Kaeru", "If one of you fully dies, so does the other"),
            new Def(BROTHERS, Set.of("stevie"), Set.of("garry"), "Stevie + Garry", null),
            new Def(TENJO, Set.of("yuri"), Set.of("ultima"), "Yuri + Ultima", null),
            new Def(OLD_FRIENDS, Set.of("zane"), Set.of("mikiko"), "Zane + Mikiko", null),
            new Def(MARRIED, Set.of("max"), Set.of("technica"), "Max + Technica", null));

    public static Def def(String name) { for (Def d : DEFS) if (d.name.equals(name)) return d; return null; }

    /** What a synergy does at this bond level (the numbers grow with it). */
    public static String effect(String s, int bond) {
        float k = Bonds.strength(bond);
        return switch (s) {
            case PARTNERS -> "+" + pct(0.15F * k) + " damage, faster Karma";
            case SKYLAND -> "+" + pct(0.10F * k) + " damage (+" + pct(0.20F * k) + " with 3+ of the Sky Patrol)";
            case GOOFY -> "Rival kills fire you up (+" + pct(0.05F * k) + " damage each, up to 5), random goofing-off bonuses";
            case SOUL_LINK -> "+" + pct(0.20F * k) + " damage, -" + pct(Math.min(0.5F, 0.15F * k)) + " damage taken, faster Karma";
            case BROTHERS -> "+" + pct(0.15F * k) + " damage, Brother Blast hits much harder together";
            case TENJO -> "Ultima +" + pct(0.15F * k) + " damage, Yuri's hacks last longer";
            case OLD_FRIENDS -> "Skills cost " + pct(Math.min(0.5F, 0.25F * k)) + " less, faster Karma";
            case MARRIED -> "Your hits heal your partner (" + String.format(java.util.Locale.ROOT, "%.1f", 0.5F * k) + " HP each)";
            default -> "";
        };
    }

    private static String pct(float f) { return Math.round(f * 100) + "%"; }

    /** The bond behind an active synergy: your best bond with a teammate who completes it. */
    public static int bond(ServerPlayer p, String s) {
        Def d = def(s);
        if (d == null) return 0;
        Set<String> need = d.partnersFor(id(p));
        int best = 0;
        for (LivingEntity e : partners(p)) if (need.contains(id(e))) best = Math.max(best, Bonds.level(p, id(e)));
        return best;
    }

    /** How strongly a synergy works for this player right now (1 = base, 2 = bond 100). */
    public static float strength(ServerPlayer p, String s) { return Bonds.strength(bond(p, s)); }
    public static boolean has(ServerPlayer p, String s) { return of(p).contains(s); }

    static String id(LivingEntity e) {
        if (e instanceof ServerPlayer p) return ModCapabilities.getType(p).getId();
        if (e instanceof PartyBotEntity b) return b.charKey();
        return "none";
    }

    /** Party members (players and bots) close enough to count, not including p. */
    public static List<LivingEntity> partners(ServerPlayer p) {
        List<LivingEntity> out = new ArrayList<>();
        Set<ServerPlayer> people = new LinkedHashSet<>(Parties.members(p));
        for (ServerPlayer m : people) {
            if (m != p && m.level() == p.level() && m.distanceToSqr(p) < RANGE * RANGE && m.isAlive()) out.add(m);
            for (PartyBotEntity b : PartyBots.bots(m))
                if (b.level() == p.level() && b.distanceToSqr(p) < RANGE * RANGE && b.isAlive() && !b.downed()) out.add(b);
        }
        // Kaeru inside Mikiko's blade is always right there with him
        if (ModCapabilities.getType(p) == CharacterType.MIKIKO)
            for (ServerPlayer o : p.server.getPlayerList().getPlayers()) if (KaeruSoul.state(o) != KaeruSoul.OUT && linked(o, p) && !out.contains(o)) out.add(o);
        return out;
    }

    private static Set<String> compute(ServerPlayer p) {
        List<String> them = new ArrayList<>();
        for (LivingEntity e : partners(p)) them.add(id(e));
        return compute(id(p), them);
    }

    private static Set<String> compute(PartyBotEntity b) {
        List<String> them = new ArrayList<>();
        for (LivingEntity e : partners(b)) them.add(id(e));
        return compute(id(b), them);
    }

    private static Set<String> compute(String me, List<String> them) {
        Set<String> s = new LinkedHashSet<>();
        if (pair(me, them, "rhalix", "mikiko")) s.add(PARTNERS);
        if (me.equals("rhalix") && (them.contains("kyrie") || them.stream().anyMatch(SKY_PATROL::contains))) s.add(SKYLAND);
        if ((SKY_PATROL.contains(me) || me.equals("kyrie")) && them.contains("rhalix")) s.add(SKYLAND);
        if (pair(me, them, "mikiko", "ultima")) s.add(GOOFY);
        if (pair(me, them, "mikiko", "kaeru")) s.add(SOUL_LINK);
        if (pair(me, them, "stevie", "garry")) s.add(BROTHERS);
        if (pair(me, them, "yuri", "ultima")) s.add(TENJO);
        if (pair(me, them, "zane", "mikiko")) s.add(OLD_FRIENDS);
        if (pair(me, them, "max", "technica")) s.add(MARRIED);
        return s;
    }

    private static boolean pair(String me, List<String> them, String a, String b) {
        return (me.equals(a) && them.contains(b)) || (me.equals(b) && them.contains(a));
    }

    /** Mikiko and Kaeru count as soul-linked if they're partied together, or she's in / out of his blade. */
    private static boolean linked(ServerPlayer kaeru, ServerPlayer mikiko) {
        if (Parties.sameParty(kaeru, mikiko)) return true;
        var t = kaeru.getPersistentData().getCompound(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG);
        return KaeruSoul.state(kaeru) != KaeruSoul.OUT && t.hasUUID("xw_kaeru_host") && t.getUUID("xw_kaeru_host").equals(mikiko.getUUID());
    }

    private static int skyPatrolCount(ServerPlayer p) {
        int n = 0;
        for (LivingEntity e : partners(p)) if (SKY_PATROL.contains(id(e))) n++;
        return n;
    }

    private static int skyPatrolCount(PartyBotEntity b) {
        int n = 0;
        for (LivingEntity e : partners(b)) if (SKY_PATROL.contains(id(e))) n++;
        return n;
    }

    // ------------------------------------------------------------------ ticking
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        RIVAL.values().removeIf(r -> --r[1] <= 0);
        if (server.getTickCount() % 20 != 0) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            Set<String> now = p.isSpectator() && KaeruSoul.state(p) == KaeruSoul.OUT ? Set.of() : compute(p);
            Set<String> before = ACTIVE.getOrDefault(p.getUUID(), Set.of());
            for (String s : now) if (!before.contains(s)) announce(p, s);
            if (now.isEmpty()) ACTIVE.remove(p.getUUID()); else ACTIVE.put(p.getUUID(), now);
            if (now.contains(PARTNERS)) com.xenowarriors.karma.Karma.add(p, 0.75F * strength(p, PARTNERS));
            if (now.contains(SOUL_LINK)) com.xenowarriors.karma.Karma.add(p, 0.75F * strength(p, SOUL_LINK));
            if (now.contains(GOOFY) && p.getRandom().nextInt(45) == 0) goof(p);
            if (!now.isEmpty() && server.getTickCount() % 60 == 0)
                p.serverLevel().sendParticles(now.contains(SOUL_LINK) ? ParticleTypes.SOUL : ParticleTypes.HAPPY_VILLAGER, p.getX(), p.getY() + 2.1, p.getZ(), 2, 0.2, 0.1, 0.2, 0.01);
        }
        ACTIVE.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
        // bots: the same synergies from their side
        Set<UUID> seen = new HashSet<>();
        for (ServerPlayer o : server.getPlayerList().getPlayers()) {
            for (PartyBotEntity b : PartyBots.bots(o)) {
                seen.add(b.getUUID());
                Set<String> now = b.downed() ? Set.of() : compute(b);
                if (now.isEmpty()) { BOT_ACTIVE.remove(b.getUUID()); continue; }
                BOT_ACTIVE.put(b.getUUID(), now);
                if (now.contains(PARTNERS)) b.karma = Math.min(100F, b.karma + 0.75F * strength(b, PARTNERS));
                if (now.contains(SOUL_LINK)) b.karma = Math.min(100F, b.karma + 0.75F * strength(b, SOUL_LINK));
                if (now.contains(OLD_FRIENDS)) b.karma = Math.min(100F, b.karma + 0.5F);
            }
        }
        BOT_ACTIVE.keySet().retainAll(seen);
    }

    private static void announce(ServerPlayer p, String s) {
        int bond = bond(p, s);
        Def d = def(s);
        String what = effect(s, bond) + (d != null && d.downside() != null && Bonds.downside(bond) > 0 ? " - but: " + d.downside().toLowerCase(java.util.Locale.ROOT)
                + (bond > 0 ? " (" + Math.round(Bonds.downside(bond) * 100) + "% chance, gone at bond " + Bonds.NO_DOWNSIDE + ")" : "") : "") + "  [bond " + bond + "]";
        p.displayClientMessage(Component.literal("Synergy - " + s + ": ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                .append(Component.literal(what).withStyle(ChatFormatting.YELLOW)), true);
    }

    /** Mikiko and Ultima goofing off together. */
    private static void goof(ServerPlayer p) {
        boolean mikiko = ModCapabilities.getType(p) == CharacterType.MIKIKO;
        int roll = p.getRandom().nextInt(4);
        String line;
        switch (roll) {
            case 0 -> { line = mikiko ? "Mikiko steals one of Ultima's snacks. Worth it." : "Ultima scans Mikiko's snack: 300% sugar. Data gained."; 
                        if (mikiko) p.getFoodData().eat(4, 0.6F); else com.xenowarriors.karma.Karma.add(p, 12F); }
            case 1 -> { line = mikiko ? "\"Bet I can hit harder than you, robot!\"" : "\"Calculating... no, you can't.\"";
                        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 200, 0)); }
            case 2 -> { line = mikiko ? "Mikiko trips over nothing. Ultima is laughing." : "Ultima's laugh protocol activates. Speed up!";
                        p.addEffect(new MobEffectInstance(mikiko ? MobEffects.DAMAGE_RESISTANCE : MobEffects.MOVEMENT_SPEED, 200, 0)); }
            default -> { line = "A pointless argument about who's cooler. Both feel great.";
                         p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 0)); }
        }
        p.displayClientMessage(Component.literal(line).withStyle(ChatFormatting.GREEN), true);
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.PLAYERS, 0.6F, mikiko ? 1.3F : 0.8F);
    }

    // ------------------------------------------------------------------ the bonuses
    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onHurt(LivingHurtEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer a) {
            Set<String> s = of(a);
            float mult = 1F;                                         // (each bonus x the bond's strength: 1x at bond 0, 2x at 100)
            if (s.contains(PARTNERS)) mult += 0.15F * strength(a, PARTNERS);
            if (s.contains(SKYLAND)) mult += (skyPatrolCount(a) >= 3 ? 0.2F : 0.1F) * strength(a, SKYLAND);
            if (s.contains(SOUL_LINK)) mult += 0.2F * strength(a, SOUL_LINK);
            if (s.contains(BROTHERS)) mult += 0.15F * strength(a, BROTHERS);
            if (s.contains(TENJO) && ModCapabilities.getType(a) == CharacterType.ULTIMA) mult += 0.15F * strength(a, TENJO);
            if (s.contains(MARRIED) && !(event.getEntity() instanceof ServerPlayer)) {
                float heal = 0.5F * strength(a, MARRIED);
                for (LivingEntity e : partners(a)) {
                    String pid = id(e);
                    if ((pid.equals("max") || pid.equals("technica")) && !pid.equals(id(a))) e.heal(heal);
                }
            }
            int[] r = RIVAL.get(a.getUUID());
            if (r != null && s.contains(GOOFY)) mult += 0.05F * r[0] * strength(a, GOOFY);
            if (mult != 1F) event.setAmount(event.getAmount() * mult);
        }
        PartyBotEntity ab = PartyBots.botOf(event.getSource().getEntity());           // a bot's hit (melee, or a skill through its stand-in)
        if (ab != null && ab != event.getEntity()) {
            Set<String> s = of(ab);
            float mult = 1F;
            if (s.contains(PARTNERS)) mult += 0.15F * strength(ab, PARTNERS);
            if (s.contains(SKYLAND)) mult += (skyPatrolCount(ab) >= 3 ? 0.2F : 0.1F) * strength(ab, SKYLAND);
            if (s.contains(SOUL_LINK)) mult += 0.2F * strength(ab, SOUL_LINK);
            if (s.contains(BROTHERS)) mult += 0.15F * strength(ab, BROTHERS);
            if (s.contains(TENJO) && ab.character() == CharacterType.ULTIMA) mult += 0.15F * strength(ab, TENJO);
            if (s.contains(MARRIED) && !(event.getEntity() instanceof ServerPlayer)) {
                float heal = 0.5F * strength(ab, MARRIED);
                for (LivingEntity e : partners(ab)) {
                    String pid = id(e);
                    if ((pid.equals("max") || pid.equals("technica")) && !pid.equals(id(ab))) e.heal(heal);
                }
            }
            int[] r = RIVAL.get(ab.getUUID());
            if (r != null && s.contains(GOOFY)) mult += 0.05F * r[0] * strength(ab, GOOFY);
            if (mult != 1F) {
                event.setAmount(event.getAmount() * mult);
                if (TEST_LOG) XenoWarriors.LOGGER.info("XW-TEST parity bot synergy {} x{} {}", ab.charKey(), String.format(java.util.Locale.ROOT, "%.3f", mult), s);
            }
        }
        if (event.getEntity() instanceof ServerPlayer v && has(v, SOUL_LINK)) event.setAmount(event.getAmount() * (1F - Math.min(0.5F, 0.15F * strength(v, SOUL_LINK))));
        if (event.getEntity() instanceof PartyBotEntity vb && has(vb, SOUL_LINK)) event.setAmount(event.getAmount() * (1F - Math.min(0.5F, 0.15F * strength(vb, SOUL_LINK))));
    }

    /** (Tests) log every bot synergy bonus. */
    public static boolean TEST_LOG;

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onDeath(LivingDeathEvent event) {
        if (event.isCanceled()) return;                         // (after the Hekublade seal and the modes have had their say)
        // a rival's kill fires the other one up
        PartyBotEntity kb = PartyBots.botOf(event.getSource().getEntity());
        LivingEntity killer = kb != null ? kb : event.getSource().getEntity() instanceof ServerPlayer kp ? kp : null;
        boolean goofy = killer instanceof ServerPlayer kp2 ? has(kp2, GOOFY) : kb != null && has(kb, GOOFY);
        if (killer != null && goofy && !(event.getEntity() instanceof ServerPlayer) && !(event.getEntity() instanceof PartyBotEntity)) {
            List<LivingEntity> rivals = killer instanceof ServerPlayer kp3 ? partners(kp3) : partners(kb);
            String who = killer instanceof PartyBotEntity bk ? bk.label().getString() : killer.getName().getString();
            for (LivingEntity e : rivals) {
                boolean on = e instanceof ServerPlayer o ? has(o, GOOFY) : e instanceof PartyBotEntity ob && has(ob, GOOFY);
                if (!on || id(e).equals(id(killer))) continue;
                int[] r = RIVAL.computeIfAbsent(e.getUUID(), k -> new int[2]);
                r[0] = Math.min(5, r[0] + 1); r[1] = 400;
                if (e instanceof ServerPlayer o) o.displayClientMessage(Component.literal(who + " got one! Rivalry +" + (r[0] * 5) + "% damage").withStyle(ChatFormatting.GREEN), true);
            }
        }
        // Soul Link: if one fully dies, so does the other
        if (!(event.getEntity() instanceof ServerPlayer dead) || DYING.contains(dead.getUUID())) return;
        CharacterType t = ModCapabilities.getType(dead);
        if (t != CharacterType.MIKIKO && t != CharacterType.KAERU) return;
        for (ServerPlayer o : dead.server.getPlayerList().getPlayers()) {
            if (o == dead || !o.isAlive() || DYING.contains(o.getUUID())) continue;
            CharacterType ot = ModCapabilities.getType(o);
            boolean link = (t == CharacterType.MIKIKO && ot == CharacterType.KAERU && linked(o, dead))
                    || (t == CharacterType.KAERU && ot == CharacterType.MIKIKO && linked(dead, o));
            if (!link) continue;
            // the bond softens it: a chance to hold on, and none of it from bond 50 (Bonds.NO_DOWNSIDE)
            float risk = Bonds.downside(Bonds.level(o, t.getId()));
            if (risk <= 0F || o.getRandom().nextFloat() >= risk) {
                o.displayClientMessage(Component.literal("Your bond with " + dead.getName().getString() + " held - the soul link didn't drag you down.").withStyle(ChatFormatting.LIGHT_PURPLE), false);
                continue;
            }
            // a tick later, once every death handler is done: if the death was taken back (sealed, downed), nothing happens
            com.xenowarriors.skill.ServerScheduler.later(1, () -> { if (dead.isDeadOrDying() || !dead.isAlive()) sever(dead, o); });
        }
    }

    private static void sever(ServerPlayer dead, ServerPlayer o) {
        if (!o.isAlive() || DYING.contains(o.getUUID())) return;
        {
            DYING.add(o.getUUID());
            try {
                if (KaeruSoul.state(o) != KaeruSoul.OUT) KaeruSoul.forceOut(o);
                o.displayClientMessage(Component.literal("Your soul link with " + dead.getName().getString() + " was severed...").withStyle(ChatFormatting.DARK_RED), false);
                o.serverLevel().sendParticles(ParticleTypes.SOUL, o.getX(), o.getY() + 1, o.getZ(), 30, 0.3, 0.6, 0.3, 0.05);
                o.hurt(o.damageSources().genericKill(), Float.MAX_VALUE);
            } finally { DYING.remove(o.getUUID()); }
        }
    }
}

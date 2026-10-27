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
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Synergy {
    public static final double RANGE = 24;
    public static final String PARTNERS = "Partners", SKYLAND = "Skyland Bond", GOOFY = "Goofy Rivals", SOUL_LINK = "Soul Link";
    /** Sky Patrol members (by character id) - for when they become playable. */
    public static final Set<String> SKY_PATROL = Set.of("sam", "lexy", "max");
    private static final Map<UUID, Set<String>> ACTIVE = new HashMap<>();
    private static final Map<UUID, int[]> RIVAL = new HashMap<>();      // {stacks, ticks left}
    private static final Set<UUID> DYING = new HashSet<>();

    private Synergy() {}

    public static Set<String> of(ServerPlayer p) { return ACTIVE.getOrDefault(p.getUUID(), Set.of()); }
    public static boolean has(ServerPlayer p, String s) { return of(p).contains(s); }

    private static String id(LivingEntity e) {
        if (e instanceof ServerPlayer p) return ModCapabilities.getType(p).getId();
        if (e instanceof PartyBotEntity b) return b.character().getId();
        return "none";
    }

    /** Party members (players and bots) close enough to count, not including p. */
    private static List<LivingEntity> partners(ServerPlayer p) {
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
        Set<String> s = new LinkedHashSet<>();
        String me = id(p);
        List<String> them = new ArrayList<>();
        for (LivingEntity e : partners(p)) them.add(id(e));
        if (pair(me, them, "rhalix", "mikiko")) s.add(PARTNERS);
        if (me.equals("rhalix") && (them.contains("kyrie") || them.stream().anyMatch(SKY_PATROL::contains))) s.add(SKYLAND);
        if ((SKY_PATROL.contains(me) || me.equals("kyrie")) && them.contains("rhalix")) s.add(SKYLAND);
        if (pair(me, them, "mikiko", "ultima")) s.add(GOOFY);
        if (pair(me, them, "mikiko", "kaeru")) s.add(SOUL_LINK);
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
            if (now.contains(PARTNERS) || now.contains(SOUL_LINK)) com.xenowarriors.karma.Karma.add(p, 0.75F);
            if (now.contains(GOOFY) && p.getRandom().nextInt(45) == 0) goof(p);
            if (!now.isEmpty() && server.getTickCount() % 60 == 0)
                p.serverLevel().sendParticles(now.contains(SOUL_LINK) ? ParticleTypes.SOUL : ParticleTypes.HAPPY_VILLAGER, p.getX(), p.getY() + 2.1, p.getZ(), 2, 0.2, 0.1, 0.2, 0.01);
        }
        ACTIVE.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
    }

    private static void announce(ServerPlayer p, String s) {
        String what = switch (s) {
            case PARTNERS -> "+15% damage, faster Karma";
            case SKYLAND -> "+10% damage";
            case GOOFY -> "rival kills fire you up, expect some goofing off";
            case SOUL_LINK -> "+20% damage, -15% damage taken, faster Karma - but if one of you dies, so does the other";
            default -> "";
        };
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
            float mult = 1F;
            if (s.contains(PARTNERS)) mult += 0.15F;
            if (s.contains(SKYLAND)) mult += skyPatrolCount(a) >= 3 ? 0.2F : 0.1F;
            if (s.contains(SOUL_LINK)) mult += 0.2F;
            int[] r = RIVAL.get(a.getUUID());
            if (r != null && s.contains(GOOFY)) mult += 0.05F * r[0];
            if (mult != 1F) event.setAmount(event.getAmount() * mult);
        }
        if (event.getEntity() instanceof ServerPlayer v && has(v, SOUL_LINK)) event.setAmount(event.getAmount() * 0.85F);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onDeath(LivingDeathEvent event) {
        if (event.isCanceled()) return;
        // a rival's kill fires the other one up
        if (event.getSource().getEntity() instanceof ServerPlayer killer && has(killer, GOOFY) && !(event.getEntity() instanceof ServerPlayer)) {
            for (LivingEntity e : partners(killer)) {
                if (!(e instanceof ServerPlayer o) || !has(o, GOOFY)) continue;
                int[] r = RIVAL.computeIfAbsent(o.getUUID(), k -> new int[2]);
                r[0] = Math.min(5, r[0] + 1); r[1] = 400;
                o.displayClientMessage(Component.literal(killer.getName().getString() + " got one! Rivalry +" + (r[0] * 5) + "% damage").withStyle(ChatFormatting.GREEN), true);
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

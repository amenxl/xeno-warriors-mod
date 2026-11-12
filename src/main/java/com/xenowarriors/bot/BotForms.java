package com.xenowarriors.bot;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.network.ModNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.*;

/**
 * Bot teammates transforming like the player does, and following the player's lead.
 * <ul>
 *   <li>A Mikiko bot can go Fox King and a Rhalix bot can call up Demon's Remnants (their transform-key forms), paid
 *       from the bot's own Karma, lasting and recovering like the player's (Fox King uses your mastery). Same hair /
 *       red eyes as on a player.</li>
 *   <li>On its own a bot rarely does it: now and then in a hard fight (more often when it's badly hurt).</li>
 *   <li>When you transform - Fox King, Demon's Remnants, Netsu, the Lunar form, a fully charged weapon summon, or (with
 *       DragonMineZ) a DragonMineZ form - your party's bots near you follow your lead: over the next few seconds each
 *       one (a short, different delay each, so they don't all flip at once) goes into what it has - its XW form and,
 *       with DragonMineZ, its best DragonMineZ form (see DmzBotPower) - if it can afford it and there's a fight on or
 *       close by.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BotForms {
    private BotForms() {}

    /** After you transform, bots may follow for this long (once a fight is on or close by). */
    public static final int FOLLOW_WINDOW = 20 * 20;
    /** On their own, per check (every half second) in a fight against something strong: a small chance; badly hurt, bigger. */
    public static final float BASE_CHANCE = 0.06F, HURT_CHANCE = 0.15F, FOLLOW_CHANCE = 0.8F;

    public enum Kind { FOX_KING, DEMONS_REMNANTS }

    private static final UUID SPD = UUID.fromString("5a9e3c10-7b2d-4e8f-9a61-0c3d2e1f4b51"), ATK = UUID.fromString("5a9e3c10-7b2d-4e8f-9a61-0c3d2e1f4b52"),
            ARM = UUID.fromString("5a9e3c10-7b2d-4e8f-9a61-0c3d2e1f4b53"), KB = UUID.fromString("5a9e3c10-7b2d-4e8f-9a61-0c3d2e1f4b54");

    private static final class State {
        Kind on;
        long end, ready, followAt, followUntil = -1;
        String followWhy = "";
    }

    private static final Map<UUID, State> STATES = new HashMap<>();
    /** Each player's transformations last seen (to spot a new one). */
    private static final Map<UUID, Set<String>> SEEN = new HashMap<>();

    /** (For the tests) what happened, newest last. */
    public static final List<String> LOG = Collections.synchronizedList(new ArrayList<>());

    private static void note(String s) {
        if (LOG.size() > 60) LOG.remove(0);
        LOG.add(s);
        XenoWarriors.LOGGER.info("XW bot forms: {}", s);
    }

    /** The XW form this character's bot can go into, or null. */
    public static Kind formOf(CharacterType t) {
        return t == CharacterType.MIKIKO ? Kind.FOX_KING : t == CharacterType.RHALIX ? Kind.DEMONS_REMNANTS : null;
    }

    /** The XW form this bot is in now, or null. */
    public static Kind active(PartyBotEntity b) { State s = STATES.get(b.getUUID()); return s == null ? null : s.on; }

    // ---------------------------------------------------------------- what the player is in

    /** Every powered-up state the player is in now (XW and, with DragonMineZ, its form), as names. */
    public static Set<String> playerForms(ServerPlayer p) {
        Set<String> out = new TreeSet<>();
        if (com.xenowarriors.mikiko.FoxKing.active(p)) out.add("fox_king");
        if (com.xenowarriors.rhalix.DemonsRemnants.active(p)) out.add("demons_remnants");
        if (com.xenowarriors.mikiko.Netsu.active(p)) out.add("netsu");
        if (com.xenowarriors.lunar.LunarForm.active(p)) out.add("lunar");
        MobEffectInstance str = p.getEffect(MobEffects.DAMAGE_BOOST);           // (the fully charged weapon summon: Strength II for 30 s)
        if (str != null && str.getAmplifier() == 1 && str.getDuration() <= com.xenowarriors.mikiko.BladeChannel.BOOST
                && str.getDuration() > com.xenowarriors.mikiko.BladeChannel.BOOST - 40) out.add("powered_summon");
        else if (SEEN.getOrDefault(p.getUUID(), Set.of()).contains("powered_summon") && str != null && str.getAmplifier() == 1) out.add("powered_summon");
        String dmz = com.xenowarriors.compat.DmzBotPower.playerForm(p);
        if (dmz != null) out.add("dmz:" + dmz);
        return out;
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p instanceof FakePlayer) return;
        if (p.tickCount % 5 == 2) watch(p);
        if (p.tickCount % 10 == 7) for (PartyBotEntity b : PartyBots.bots(p)) tick(b, p);
    }

    /** A new transformation since last time: the bots follow. */
    private static void watch(ServerPlayer p) {
        Set<String> now = playerForms(p), before = SEEN.put(p.getUUID(), now);
        if (before == null) return;                                             // (first look: nothing "new")
        for (String f : now) if (!before.contains(f)) { playerTransformed(p, f); return; }
    }

    /**
     * The player just transformed (form = its name, "dmz:..." for a DragonMineZ form): their party's bots within 48
     * blocks get a few seconds' head start each - staggered - to follow (XW form and DragonMineZ form).
     */
    public static void playerTransformed(ServerPlayer p, String form) {
        long now = p.level().getGameTime();
        List<PartyBotEntity> bots = new ArrayList<>();
        for (ServerPlayer m : com.xenowarriors.party.Parties.members(p))
            for (PartyBotEntity b : PartyBots.bots(m)) if (b.level() == p.level() && b.distanceToSqr(p) < 48 * 48 && !b.downed()) bots.add(b);
        note(p.getGameProfile().getName() + " transformed (" + form + "): " + bots.size() + " bot(s) may follow");
        int i = 0;
        for (PartyBotEntity b : bots) {
            int delay = 10 + i * 14 + b.getRandom().nextInt(16);                 // (0.5 s, then a beat apart: never all at once)
            i++;
            State s = STATES.computeIfAbsent(b.getUUID(), u -> new State());
            s.followAt = now + delay; s.followUntil = now + delay + FOLLOW_WINDOW; s.followWhy = form;
            com.xenowarriors.compat.DmzBotPower.follow(b, now + delay, now + delay + FOLLOW_WINDOW);
        }
    }

    // ---------------------------------------------------------------- the bots

    /** A fight is on or close by: it's fighting or was just hit, you were just in a fight, or something hostile is near. */
    public static boolean nearCombat(PartyBotEntity b, ServerPlayer o) {
        LivingEntity t = b.getTarget();
        if (t != null && t.isAlive() && !b.sparring()) return true;
        if (b.getLastHurtByMob() != null && b.tickCount - b.getLastHurtByMobTimestamp() < 100) return true;
        if (o != null) {
            if (o.getLastHurtByMob() != null && o.tickCount - o.getLastHurtByMobTimestamp() < 100) return true;
            if (o.getLastHurtMob() != null && o.getLastHurtMob().isAlive() && o.tickCount - o.getLastHurtMobTimestamp() < 100) return true;
        }
        return !b.level().getEntitiesOfClass(Mob.class, b.getBoundingBox().inflate(20),
                m -> m.isAlive() && m instanceof Enemy && !(m instanceof PartyBotEntity) && !PartyBots.friendly(b, m) && !PartyBotEntity.untouchable(m)).isEmpty();
    }

    /** Every half second, per bot. */
    static void tick(PartyBotEntity b, ServerPlayer o) {
        State s = STATES.get(b.getUUID());
        long now = b.level().getGameTime();
        if (s != null && s.on != null) {
            if (now >= s.end || b.downed() || !b.isAlive()) end(b, o, s, b.downed() ? "down" : "wore off");
            return;
        }
        Kind k = formOf(b.character());
        if (k == null || b.downed() || b.journey() && com.xenowarriors.journey.Journey.noSkills(o)) return;
        if (s != null && now < s.ready) return;
        boolean follow = s != null && now >= s.followAt && now < s.followUntil;
        float chance;
        String why;
        if (follow) {
            if (!nearCombat(b, o)) return;
            chance = FOLLOW_CHANCE; why = "following " + o.getGameProfile().getName() + " (" + s.followWhy + ")";
        } else {
            LivingEntity t = b.getTarget();
            if (t == null || !t.isAlive() || b.sparring()) return;                    // (any fight: it likes to train its form's mastery)
            boolean hurt = b.getHealth() < b.getMaxHealth() * 0.4F;
            chance = hurt ? HURT_CHANCE : BASE_CHANCE; why = hurt ? "on its own (badly hurt)" : "on its own";
        }
        if (b.getRandom().nextFloat() >= chance) return;
        if (start(b, o, k, why) && s != null) s.followUntil = -1;
    }

    private static float cost(Kind k, ServerPlayer o) {
        return k == Kind.FOX_KING ? com.xenowarriors.mikiko.FoxKing.cost(o == null ? 0 : com.xenowarriors.mikiko.FoxKing.mastery(o)) : com.xenowarriors.rhalix.DemonsRemnants.COST;
    }

    /** Into its form, paid from its Karma. False if it can't (not enough Karma, already in it, no form). */
    public static boolean start(PartyBotEntity b, ServerPlayer o, Kind k, String why) {
        if (k == null || !(b.level() instanceof ServerLevel level)) return false;
        State s = STATES.computeIfAbsent(b.getUUID(), u -> new State());
        if (s.on != null) return false;
        float cost = cost(k, o);
        if (b.karma < cost) { note(b.character().getId() + " can't afford " + k + " (" + Math.round(b.karma) + "/" + Math.round(cost) + " Karma)"); return false; }
        b.karma -= cost;
        long now = level.getGameTime();
        float m = o == null ? 0 : com.xenowarriors.mikiko.FoxKing.mastery(o);
        s.on = k;
        if (k == Kind.FOX_KING) {
            s.end = now + com.xenowarriors.mikiko.FoxKing.duration(m);
            mod(b, Attributes.MOVEMENT_SPEED, SPD, com.xenowarriors.mikiko.FoxKing.SPEED, AttributeModifier.Operation.MULTIPLY_BASE);
            mod(b, Attributes.ATTACK_DAMAGE, ATK, com.xenowarriors.mikiko.FoxKing.attack(m), AttributeModifier.Operation.MULTIPLY_BASE);
            ModNetwork.sendToTrackingEntityAndSelf(new com.xenowarriors.network.FoxKingPacket(b.getId(), true), b);
            level.sendParticles(new DustParticleOptions(new Vector3f(0.95F, 0.12F, 0.1F), 1.6F), b.getX(), b.getY() + 1, b.getZ(), 50, 0.6, 1, 0.6, 0);
            level.sendParticles(new DustParticleOptions(new Vector3f(1F, 1F, 1F), 1.2F), b.getX(), b.getY() + 1.8, b.getZ(), 20, 0.4, 0.4, 0.4, 0);
            level.sendParticles(ParticleTypes.FLAME, b.getX(), b.getY() + 0.2, b.getZ(), 30, 0.8, 0.1, 0.8, 0.05);
            level.playSound(null, b.blockPosition(), SoundEvents.FOX_SCREECH, SoundSource.NEUTRAL, 1.2F, 0.7F);
            level.playSound(null, b.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.NEUTRAL, 1F, 0.6F);
        } else {
            s.end = now + com.xenowarriors.rhalix.DemonsRemnants.DURATION;
            mod(b, Attributes.MOVEMENT_SPEED, SPD, 0.25, AttributeModifier.Operation.MULTIPLY_BASE);
            mod(b, Attributes.ATTACK_DAMAGE, ATK, 0.45, AttributeModifier.Operation.MULTIPLY_BASE);
            mod(b, Attributes.ARMOR, ARM, 6, AttributeModifier.Operation.ADDITION);
            mod(b, Attributes.KNOCKBACK_RESISTANCE, KB, 0.5, AttributeModifier.Operation.ADDITION);
            ModNetwork.sendToTrackingEntityAndSelf(new com.xenowarriors.network.DemonStatePacket(b.getId(), true), b);
            var eye = b.getEyePosition();
            level.sendParticles(new DustParticleOptions(new Vector3f(0.9F, 0.05F, 0.08F), 0.7F), eye.x, eye.y, eye.z, 16, 0.25, 0.1, 0.25, 0);
            level.playSound(null, b.blockPosition(), SoundEvents.WITHER_AMBIENT, SoundSource.NEUTRAL, 0.7F, 0.6F);
            level.playSound(null, b.blockPosition(), SoundEvents.SOUL_ESCAPE, SoundSource.NEUTRAL, 1F, 0.5F);
        }
        com.xenowarriors.anim.Poses.play(b, com.xenowarriors.anim.Poses.KA_BREAK, 12);
        if (o != null) o.displayClientMessage(Component.literal("").append(b.label()).append(k == Kind.FOX_KING ? " goes Fox King!" : ": Demon's Remnants!")
                .withStyle(k == Kind.FOX_KING ? ChatFormatting.GOLD : ChatFormatting.DARK_RED), true);
        note(b.character().getId() + " -> " + k + " " + why + " (" + (s.end - now) / 20 + " s, " + Math.round(cost(k, o)) + " Karma, karma left " + Math.round(b.karma) + ")");
        return true;
    }

    private static void end(PartyBotEntity b, ServerPlayer o, State s, String why) {
        Kind k = s.on;
        s.on = null;
        long now = b.level().getGameTime();
        s.ready = now + (k == Kind.FOX_KING ? com.xenowarriors.mikiko.FoxKing.cooldown(o == null ? 0 : com.xenowarriors.mikiko.FoxKing.mastery(o)) : com.xenowarriors.rhalix.DemonsRemnants.COOLDOWN);
        for (Attribute a : new Attribute[]{Attributes.MOVEMENT_SPEED, Attributes.ATTACK_DAMAGE, Attributes.ARMOR, Attributes.KNOCKBACK_RESISTANCE}) {
            AttributeInstance i = b.getAttribute(a);
            if (i != null) { i.removeModifier(SPD); i.removeModifier(ATK); i.removeModifier(ARM); i.removeModifier(KB); }
        }
        if (k == Kind.FOX_KING) ModNetwork.sendToTrackingEntityAndSelf(new com.xenowarriors.network.FoxKingPacket(b.getId(), false), b);
        else ModNetwork.sendToTrackingEntityAndSelf(new com.xenowarriors.network.DemonStatePacket(b.getId(), false), b);
        note(b.character().getId() + " leaves " + k + " (" + why + ")");
    }

    private static void mod(PartyBotEntity b, Attribute a, UUID id, double amount, AttributeModifier.Operation op) {
        AttributeInstance i = b.getAttribute(a);
        if (i == null) return;
        i.removeModifier(id);
        i.addTransientModifier(new AttributeModifier(id, "Bot transformation", amount, op));
    }

    /** Someone starts seeing a transformed bot: show it transformed. */
    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking e) {
        if (!(e.getTarget() instanceof PartyBotEntity b) || !(e.getEntity() instanceof ServerPlayer w)) return;
        Kind k = active(b);
        if (k == Kind.FOX_KING) ModNetwork.sendToPlayer(new com.xenowarriors.network.FoxKingPacket(b.getId(), true), w);
        else if (k == Kind.DEMONS_REMNANTS) ModNetwork.sendToPlayer(new com.xenowarriors.network.DemonStatePacket(b.getId(), true), w);
    }

    /** A bot is gone for good: forget it. */
    public static void forget(UUID bot) { STATES.remove(bot); }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) { SEEN.remove(e.getEntity().getUUID()); }

    // ---------------------------------------------------------------- tests

    /** (Tests) "form=.. followIn=.. readyIn=..". */
    public static String state(PartyBotEntity b) {
        State s = STATES.get(b.getUUID());
        long now = b.level().getGameTime();
        if (s == null) return "form=null karma=" + Math.round(b.karma);
        return "form=" + s.on + (s.on != null ? " left=" + (s.end - now) / 20 + "s" : "") + " karma=" + Math.round(b.karma)
                + " follow=" + (s.followUntil > now ? (s.followAt > now ? "in " + (s.followAt - now) + "t" : "open") : "no") + " readyIn=" + Math.max(0, s.ready - now) / 20 + "s";
    }

    /** (Tests) out of its form, recovered, no follow pending. */
    public static void resetForTest(PartyBotEntity b) {
        State s = STATES.get(b.getUUID());
        if (s == null) return;
        if (s.on != null) end(b, b.owner(), s, "test reset");
        s.ready = 0; s.followUntil = -1;
    }
}

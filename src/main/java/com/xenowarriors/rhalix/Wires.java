package com.xenowarriors.rhalix;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.character.ModCapabilities;
import com.xenowarriors.forms.WiresItem;
import com.xenowarriors.network.ModNetwork;
import com.xenowarriors.network.WireFxPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Rhalix's wires as a weapon (Season 2):
 * <ul>
 * <li><b>Whip</b> (attack): he swings them round - everything in front of him up to {@link #REACH} blocks away is hit.</li>
 * <li><b>Bind</b> (hold use on the lock-on target): the wires wrap round them and turn red; they're held in place and
 *     their Karma drains (their Ki with DragonMineZ; a mob with neither loses a little health instead) for as long
 *     as he holds on, up to {@link #MAX_BIND} ticks. Then a cooldown of {@link #COOLDOWN} ticks. A bound player can
 *     mash attack to break free: {@link #MASH} presses.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Wires {
    public static final double REACH = 5.5, BIND_RANGE = 12.0, BREAK_RANGE = 16.0;
    public static final int WHIP_COOLDOWN = 10, MAX_BIND = 8 * 20, COOLDOWN = 20 * 20, MASH = 15;
    /** Drained each second: Karma (players and bots), 4% of max Ki (DragonMineZ), or health (anything else). */
    public static final float KARMA_DRAIN = 6F, KI_DRAIN = 0.04F, HEALTH_DRAIN = 1.0F;
    private static final String BIND_READY = "xw_wire_bind_ready";
    private static final Map<UUID, Bind> BINDS = new HashMap<>();
    private static final Map<UUID, Integer> LAST_WHIP = new HashMap<>();
    /** Test log: drained amounts per second. */
    public static final java.util.List<String> LOG = new java.util.ArrayList<>();

    private Wires() {}

    /** One bind: who, where they're held, since when, how much they've mashed. */
    public static final class Bind {
        public final ServerPlayer rhalix;
        public final LivingEntity target;
        public final Vec3 anchor;
        public final long start;
        public int mash;
        Bind(ServerPlayer r, LivingEntity t, long start) { rhalix = r; target = t; anchor = t.position(); this.start = start; }
    }

    public static boolean holdingWires(Player p) {
        return p.getMainHandItem().getItem() instanceof WiresItem && ModCapabilities.getType(p) == CharacterType.RHALIX;
    }

    private static float damage(ServerPlayer p) {
        return (4F + com.xenowarriors.stats.Stat.effective(WireLevel.level(p)) * 0.12F);
    }

    // ------------------------------------------------------------------ whip

    /** One swing: returns how many it hit. */
    public static int whip(ServerPlayer p) {
        if (!holdingWires(p) || com.xenowarriors.party.Duels.cantAct(p) || BINDS.containsKey(p.getUUID())) return 0;
        Integer last = LAST_WHIP.get(p.getUUID());
        if (last != null && p.tickCount - last < WHIP_COOLDOWN) return 0;
        LAST_WHIP.put(p.getUUID(), p.tickCount);
        Vec3 eye = p.getEyePosition(), look = Vec3.directionFromRotation(Math.max(-30, Math.min(30, p.getXRot())), p.getYRot());
        float dmg = damage(p);
        int hits = 0;
        for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(REACH + 1))) {
            if (e == p || !com.xenowarriors.skill.SkillTargeting.canTarget(p, e)) continue;
            net.minecraft.world.phys.AABB box = e.getBoundingBox().inflate(0.6);   // wires are thin but they lash wide
            Vec3 to = box.getCenter().subtract(eye);
            double dist = Math.sqrt(box.distanceToSqr(eye));
            if (dist > REACH) continue;
            // close in, the lash wraps all round him; further out it is a wide arc in front (flat - ignores height)
            Vec3 flat = new Vec3(to.x, 0, to.z), flatLook = new Vec3(look.x, 0, look.z);
            double dot = flat.lengthSqr() < 1.0E-4 ? 1 : flat.normalize().dot(flatLook.normalize());
            if (dist > 1.8 && dot < -0.1) continue;
            e.invulnerableTime = 0;
            if (e.hurt(p.damageSources().playerAttack(p), dmg)) {
                hits++;
                Vec3 kb = (to.lengthSqr() < 1.0E-4 ? look : to.normalize()).scale(0.35);
                e.push(kb.x, 0.12, kb.z);
                e.hurtMarked = true;
                WireLevel.addXp(p, Math.min(dmg, e.getHealth() + 1F) * 0.25F);
            }
        }
        ServerLevel level = p.serverLevel();
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.9F, 1.4F);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.FISHING_BOBBER_THROW, SoundSource.PLAYERS, 1F, 0.6F);
        ModNetwork.sendToTrackingAndSelf(new WireFxPacket(p.getId(), WireFxPacket.WHIP, -1, (p.tickCount / WHIP_COOLDOWN) % 2), p);
        return hits;
    }

    // ------------------------------------------------------------------ bind

    public static long cooldownLeft(ServerPlayer p) {
        return Math.max(0, p.getPersistentData().getLong(BIND_READY) - p.serverLevel().getGameTime());
    }

    public static Bind bindOf(ServerPlayer rhalix) { return BINDS.get(rhalix.getUUID()); }

    /** The bind holding this entity, if any. */
    public static Bind holding(LivingEntity target) {
        for (Bind b : BINDS.values()) if (b.target == target) return b;
        return null;
    }

    public static boolean startBind(ServerPlayer p, int targetId) {
        if (!holdingWires(p) || com.xenowarriors.party.Duels.cantAct(p) || BINDS.containsKey(p.getUUID())) return false;
        long cd = cooldownLeft(p);
        if (cd > 0) {
            p.displayClientMessage(Component.literal("Wire bind ready in " + (cd + 19) / 20 + "s").withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (!(p.level().getEntity(targetId) instanceof LivingEntity t) || !t.isAlive() || t.distanceTo(p) > BIND_RANGE
                || !com.xenowarriors.skill.SkillTargeting.canTarget(p, t) || holding(t) != null) return false;
        BINDS.put(p.getUUID(), new Bind(p, t, p.serverLevel().getGameTime()));
        p.level().playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 1.2F, 0.6F);
        p.level().playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.TRIPWIRE_CLICK_ON, SoundSource.PLAYERS, 1F, 0.5F);
        send(BINDS.get(p.getUUID()), true);
        if (t instanceof ServerPlayer sp) sp.displayClientMessage(Component.literal("Bound by Rhalix's wires! Mash attack to break free!").withStyle(ChatFormatting.RED), true);
        return true;
    }

    public static void release(ServerPlayer p, String why) {
        Bind b = BINDS.remove(p.getUUID());
        if (b == null) return;
        p.getPersistentData().putLong(BIND_READY, p.serverLevel().getGameTime() + COOLDOWN);
        send(b, false);
        p.level().playSound(null, b.target.getX(), b.target.getY(), b.target.getZ(), SoundEvents.CHAIN_BREAK, SoundSource.PLAYERS, 1F, 0.9F);
        LOG.add("released: " + why);
        if (why.equals("mashed free")) {
            p.displayClientMessage(Component.literal(b.target.getName().getString() + " broke free of your wires!").withStyle(ChatFormatting.RED), true);
            if (b.target instanceof ServerPlayer sp) sp.displayClientMessage(Component.literal("You broke free!").withStyle(ChatFormatting.GREEN), true);
        }
    }

    /** A bound player pressed attack. */
    public static void mash(ServerPlayer bound) {
        var proto = com.xenowarriors.gov.UltimaPrototypeEntity.holding(bound);     // (grabbed by the Ultima Prototype's cables: same mashing)
        if (proto != null) proto.mash(bound);
        for (Bind b : new java.util.ArrayList<>(BINDS.values())) {
            if (b.target != bound) continue;
            b.mash++;
            send(b, true);
            if (b.mash >= MASH) release(b.rhalix, "mashed free");
        }
    }

    private static void send(Bind b, boolean on) {
        WireFxPacket pk = new WireFxPacket(b.rhalix.getId(), on ? WireFxPacket.BIND : WireFxPacket.UNBIND, b.target.getId(), Math.round(b.mash * 100F / MASH));
        ModNetwork.sendToTrackingAndSelf(pk, b.rhalix);
        if (b.target instanceof ServerPlayer sp && !(sp instanceof net.minecraftforge.common.util.FakePlayer)) ModNetwork.sendToPlayer(pk, sp);
    }

    /** Drains one second's worth from the target; returns a description for the log. */
    private static String drain(Bind b) {
        LivingEntity t = b.target;
        float[] ki = com.xenowarriors.compat.DmzPetKi.ki(t);
        if (ki[1] > 0 && ki[0] > 0.5F) {                                     // (Ki first with DragonMineZ; once it's empty, their Karma)
            float take = ki[1] * KI_DRAIN;
            com.xenowarriors.compat.DmzPetKi.setKi(t, Math.max(0, ki[0] - take));
            return String.format("ki %.1f -> %.1f", ki[0], com.xenowarriors.compat.DmzPetKi.ki(t)[0]);
        }
        if (t instanceof ServerPlayer sp && !(sp instanceof net.minecraftforge.common.util.FakePlayer && ModCapabilities.getType(sp) == CharacterType.NONE)) {
            float before = com.xenowarriors.karma.Karma.get(sp);
            com.xenowarriors.karma.Karma.add(sp, -Math.min(before, KARMA_DRAIN));
            return String.format("karma %.1f -> %.1f", before, com.xenowarriors.karma.Karma.get(sp));
        }
        if (t instanceof com.xenowarriors.bot.PartyBotEntity bot) {
            float before = bot.karma;
            bot.karma = Math.max(0, bot.karma - KARMA_DRAIN);
            return String.format("bot karma %.1f -> %.1f", before, bot.karma);
        }
        float before = t.getHealth();
        t.invulnerableTime = 0;
        t.hurt(b.rhalix.damageSources().indirectMagic(b.rhalix, b.rhalix), HEALTH_DRAIN);
        t.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 0, false, true));
        return String.format("health %.1f -> %.1f", before, t.getHealth());
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || BINDS.isEmpty()) return;
        for (Bind b : new java.util.ArrayList<>(BINDS.values())) {
            ServerPlayer p = b.rhalix;
            long t = p.serverLevel().getGameTime() - b.start;
            if (p.isRemoved() || !p.isAlive() || !holdingWires(p)) { release(p, "let go"); continue; }
            if (!b.target.isAlive() || b.target.isRemoved() || b.target.level() != p.level()) { release(p, "target gone"); continue; }
            if (b.target.distanceTo(p) > BREAK_RANGE) { release(p, "too far"); continue; }
            if (t >= MAX_BIND) { release(p, "time up"); continue; }
            com.xenowarriors.skill.Rooting.hold(b.target, b.anchor);
            if (t % 4 == 0) {
                Vec3 c = b.target.getBoundingBox().getCenter();
                p.serverLevel().sendParticles(new DustParticleOptions(new Vector3f(1F, 0.1F, 0.1F), 0.8F), c.x, c.y, c.z, 3,
                        b.target.getBbWidth() * 0.4, b.target.getBbHeight() * 0.3, b.target.getBbWidth() * 0.4, 0);
            }
            if (t > 0 && t % 20 == 0) {
                String d = drain(b);
                LOG.add("second " + t / 20 + ": " + d);
                WireLevel.addXp(p, 2F);
                p.level().playSound(null, b.target.getX(), b.target.getY(), b.target.getZ(), SoundEvents.CHAIN_STEP, SoundSource.PLAYERS, 0.8F, 0.6F);
            }
            if (b.target instanceof com.xenowarriors.bot.PartyBotEntity || (b.target instanceof net.minecraft.world.entity.Mob)) {
                // AI enemies struggle too: a little each second
                if (t % 20 == 10 && b.target.getRandom().nextFloat() < 0.25F) b.mash += 2;
                if (b.mash >= MASH) release(p, "mashed free");
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            release(p, "logged out");
            LAST_WHIP.remove(p.getUUID());
            for (Bind b : new java.util.ArrayList<>(BINDS.values())) if (b.target == p) release(b.rhalix, "target logged out");
        }
    }
}

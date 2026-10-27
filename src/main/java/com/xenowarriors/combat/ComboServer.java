package com.xenowarriors.combat;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.character.ModCapabilities;
import com.xenowarriors.network.ComboSwingPacket;
import com.xenowarriors.network.ModNetwork;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The server side of combo combat: players' combo hits (sent by their client at the strike frame) and party bots'
 * (see {@link BotCombo}) land here. A hit is a fully-charged vanilla attack (so enchantments, crits and sweeps all
 * still work) scaled by the combo's damage multiplier; light hits barely push (so the next one connects) and
 * stagger, the finisher knocks them away, a heavy launches them and breaks shields.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ComboServer {
    /** The hit being dealt right now (attacker, target, ...), so the hurt/knockback events can scale it. */
    public static final class Ctx {
        public final LivingEntity attacker; public final ComboStyle style; public final int hit; public final boolean heavy, finisher;
        public final float mult; boolean landed;
        Ctx(LivingEntity a, ComboStyle s, int hit, boolean heavy, float mult) {
            attacker = a; style = s; this.hit = hit; this.heavy = heavy; finisher = !heavy && hit == s.length() - 1; this.mult = mult;
        }
    }
    private static final ThreadLocal<Ctx> CTX = new ThreadLocal<>();
    /** player -> {style ordinal, hit, heavy, start tick} of the swing they started. */
    private static final Map<UUID, int[]> STARTED = new HashMap<>();

    private ComboServer() {}

    public static Ctx current() { return CTX.get(); }

    /** The combo hit index being dealt by this player right now (-1 if none, or a heavy). */
    public static int currentHit(Player p) {
        Ctx c = CTX.get();
        return c != null && c.attacker == p && !c.heavy ? c.hit : -1;
    }

    public static boolean currentHeavy(Player p) {
        Ctx c = CTX.get();
        return c != null && c.attacker == p && c.heavy;
    }

    // ------------------------------------------------------------------ players

    /** A player started a swing (their client's input); others see it. */
    public static void start(ServerPlayer p, int hit, boolean heavy) {
        ComboStyle style = ComboStyle.of(p, p.getMainHandItem(), ModCapabilities.getType(p));
        if (style == null || !p.isAlive() || p.isSpectator()) return;
        int now = p.tickCount;
        int[] prev = STARTED.get(p.getUUID());
        if (prev != null && prev[0] == style.ordinal()) {
            // no faster than the combo allows (some slack for lag)
            int need = ComboStyle.cancel(style.duration(prev[1], prev[2] != 0));
            if (now - prev[3] < need * 0.6F) return;
        }
        STARTED.put(p.getUUID(), new int[]{style.ordinal(), hit, heavy ? 1 : 0, now});
        ModNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> p), new ComboSwingPacket(p.getId(), style.ordinal(), hit, heavy));
    }

    /** The player's swing reached its strike: hit this target. */
    public static void strike(ServerPlayer p, int targetId, int hit, boolean heavy) {
        int[] st = STARTED.get(p.getUUID());
        if (st == null || st[1] != hit || (st[2] != 0) != heavy) return;
        ComboStyle style = ComboStyle.values()[st[0]];
        if (ComboStyle.of(p, p.getMainHandItem(), ModCapabilities.getType(p)) != style) return;
        if (p.tickCount - st[3] < ComboStyle.windup(style.duration(hit, heavy)) - 3) return;
        st[1] = -99;                                                        // one strike per swing
        Entity e = p.level().getEntity(targetId);
        if (!(e instanceof LivingEntity t) || !t.isAlive() || t == p) return;
        double reach = p.getEntityReach() + style.reachBonus + 1.0;
        if (p.distanceToSqr(t) > (reach + t.getBbWidth()) * (reach + t.getBbWidth()) || !p.hasLineOfSight(t)) return;
        float mult = style.multiplier(hit, heavy, p.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED));
        Ctx c = new Ctx(p, style, hit, heavy, mult);
        CTX.set(c);
        try {
            ((com.xenowarriors.mixin.LivingEntityComboAccessor) p).xenowarriors$setAttackStrengthTicker(1000);
            t.invulnerableTime = 0;            // combo hits come faster than vanilla's half-second of hit immunity: every hit counts
            p.attack(t);
        } finally {
            CTX.remove();
        }
        after(c, t);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) { STARTED.remove(event.getEntity().getUUID()); }

    // ------------------------------------------------------------------ shared (players and bots)

    /** Runs a bot's hit through the same rules. */
    public static void botStrike(LivingEntity bot, ComboStyle style, int hit, boolean heavy, LivingEntity t) {
        Ctx c = new Ctx(bot, style, hit, heavy, style.multiplier(hit, heavy, 1.5));            // (bots used to hit once a second; 1.5 makes up for the rests between strings)
        CTX.set(c);
        try {
            t.invulnerableTime = 0;
            if (bot instanceof net.minecraft.world.entity.Mob m) m.doHurtTarget(t);
        } finally {
            CTX.remove();
        }
        after(c, t);
    }

    /** Stagger, and the finisher's / heavy's send-off. */
    private static void after(Ctx c, LivingEntity t) {
        if (!c.landed || !(t.level() instanceof ServerLevel l)) return;
        Vec3 dir = Vec3.directionFromRotation(0, c.attacker.getYRot());     // the way the hit is thrown
        double y = t.getY() + t.getBbHeight() * 0.6;
        if (c.heavy) {
            Stagger.hit(t, 22, true);
            if (!Stagger.boss(t)) { t.setDeltaMovement(dir.x * 0.35, 0.62, dir.z * 0.35); t.hurtMarked = true; }   // launched
            if (t instanceof Player tp && tp.isBlocking()) tp.disableShield(true);                                  // guard broken
            l.sendParticles(ParticleTypes.EXPLOSION, t.getX(), y, t.getZ(), 1, 0, 0, 0, 0);
            l.playSound(null, t.blockPosition(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.1F, 0.6F);
        } else if (c.finisher) {
            Stagger.clear(t);
            Stagger.hit(t, 14, true);
            l.sendParticles(ParticleTypes.SWEEP_ATTACK, t.getX(), y, t.getZ(), 1, 0, 0, 0, 0);
            l.sendParticles(ParticleTypes.CRIT, t.getX(), y, t.getZ(), 14, 0.3, 0.3, 0.3, 0.3);
            l.playSound(null, t.blockPosition(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.1F, 0.8F);
        } else {
            Stagger.hit(t, 11, false);
            l.playSound(null, t.blockPosition(), SoundEvents.PLAYER_ATTACK_WEAK, SoundSource.PLAYERS, 0.7F, 0.9F + c.hit * 0.08F);
        }
    }

    /** The combo multiplier (Mikiko's light hits use her own table). */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onHurt(LivingHurtEvent event) {
        Ctx c = CTX.get();
        if (c == null || event.getSource().getEntity() != c.attacker) return;
        c.landed = true;
        if (c.style.ownDamage && !c.heavy) return;
        event.setAmount(event.getAmount() * c.mult);
    }

    /** Light hits barely push (so the next hit connects), finishers knock them away, heavies launch (done after). */
    @SubscribeEvent
    public static void onKnockback(LivingKnockBackEvent event) {
        Ctx c = CTX.get();
        if (c == null) return;
        if (c.heavy) event.setCanceled(true);
        else if (c.finisher) event.setStrength(Math.max(event.getOriginalStrength(), 0.4F) * c.style.finisherKnock * 1.4F);
        else event.setStrength(event.getOriginalStrength() * 0.15F);
    }
}

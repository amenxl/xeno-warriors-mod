package com.xenowarriors.combat;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.character.ModCapabilities;
import com.xenowarriors.network.KishiFxPacket;
import com.xenowarriors.network.ModNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Rhalix's Kishi Warp (a combo move, not a skill): running, he holds attack - drops to one knee, presses a hand to the
 * ground, and his Kishi floods out along the floor toward the enemy ahead of him (or the one he's aiming at, up to 14
 * blocks). It follows the ground (up and down a block), stops at walls and drops; when it reaches an enemy (never an
 * ally) he warps to them in a burst and goes straight into the knockdown slash and the second half of his string
 * (the reaping grab, the raven launcher, the gravity burst) - his own swings, so it all plays by the combo rules.
 * Reaching nothing, it soaks away and he stands back up. Either way it then can't be used for 5 seconds - silently:
 * nothing tells you, holding attack while running just attacks normally until it's back.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RhalixKishiWarp {
    public static final int COOLDOWN = 100, CROUCH = 5, RANGE = 14;
    private static final double SPEED = 0.9;
    private static final boolean TEST = System.getenv("XW_UITEST_FOCUS") != null;

    private static final class W {
        final LivingEntity user; final ServerLevel level; LivingEntity hint;
        Vec3 head, dir; final Vec3 origin; int age; double went;
        W(LivingEntity u, ServerLevel l, LivingEntity hint, Vec3 dir) { user = u; level = l; this.hint = hint; this.dir = dir; origin = u.position(); head = origin; }
    }
    private static final List<W> ACTIVE = new ArrayList<>();
    private static final Map<UUID, Long> READY_AT = new HashMap<>();
    /** Bots that just warped: bot -> who they reached (their combo goal picks it up). */
    private static final Map<LivingEntity, LivingEntity> WARPED = new java.util.WeakHashMap<>();

    private RhalixKishiWarp() {}

    public static boolean ready(LivingEntity u) {
        Long t = READY_AT.get(u.getUUID());
        return t == null || u.level().getGameTime() >= t;
    }

    public static boolean busy(LivingEntity u) {
        for (W w : ACTIVE) if (w.user == u) return true;
        return false;
    }

    /** A bot that warped onto someone since last asked (null if not). */
    public static LivingEntity takeWarp(LivingEntity bot) { return WARPED.remove(bot); }

    private static boolean rhalix(LivingEntity u) {
        if (u instanceof Player p) return ModCapabilities.getType(p) == CharacterType.RHALIX;
        return u instanceof com.xenowarriors.bot.PartyBotEntity b && b.character() == CharacterType.RHALIX;
    }

    /** From his client: he's held attack while running. */
    public static void fromPlayer(ServerPlayer p, int hint) {
        if (com.xenowarriors.party.Duels.cantAct(p) || p.isSpectator()) return;
        if (!p.isSprinting() && p.getDeltaMovement().horizontalDistanceSqr() < 0.01) return;   // (he has to be running)
        LivingEntity h = p.level().getEntity(hint) instanceof LivingEntity l ? l : null;
        start(p, h);
    }

    /** Starts it (players and bots); false if it can't go now (on cooldown, not Rhalix, not on the ground...). */
    public static boolean start(LivingEntity u, LivingEntity hint) {
        if (!(u.level() instanceof ServerLevel l) || !u.isAlive() || !rhalix(u) || !u.getMainHandItem().isEmpty() || !u.onGround()
                || u.isPassenger() || u.isInWater() || busy(u)) return false;
        if (!ready(u)) {
            if (TEST) XenoWarriors.LOGGER.info("XW-TEST rhalixcombo kishi refused: cooldown, ready in " + (READY_AT.get(u.getUUID()) - l.getGameTime()) + " ticks");
            return false;
        }
        READY_AT.put(u.getUUID(), l.getGameTime() + COOLDOWN);
        if (hint != null && (!valid(u, hint) || hint.distanceTo(u) > RANGE + 0.5)) hint = null;
        Vec3 dir = hint != null ? flat(hint.position().subtract(u.position())) : flat(Vec3.directionFromRotation(0, u.getYRot()));
        W w = new W(u, l, hint, dir);
        ACTIVE.add(w);
        u.setDeltaMovement(0, u.getDeltaMovement().y, 0);
        u.setSprinting(false);
        send(w, KishiFxPacket.START, w.origin, dir, -1);
        l.playSound(null, u.blockPosition(), SoundEvents.SCULK_BLOCK_SPREAD, SoundSource.PLAYERS, 1.2F, 0.6F);
        if (TEST) XenoWarriors.LOGGER.info(String.format("XW-TEST rhalixcombo kishi start by=%s hint=%s dir=(%.2f,%.2f) tick=%d",
                u.getType().getDescriptionId(), hint == null ? "none" : hint.getType().getDescriptionId(), dir.x, dir.z, l.getGameTime()));
        return true;
    }

    private static Vec3 flat(Vec3 v) {
        Vec3 f = new Vec3(v.x, 0, v.z);
        return f.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : f.normalize();
    }

    private static boolean valid(LivingEntity u, LivingEntity e) {
        return e != u && e.isAlive() && !e.isSpectator() && !(e instanceof net.minecraft.world.entity.decoration.ArmorStand)
                && !(e instanceof Player p && p.isCreative()) && !e.isInvulnerable() && !Allies.isAlly(u, e);
    }

    private static void send(W w, int phase, Vec3 a, Vec3 b, int target) {
        ModNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> w.user),
                new KishiFxPacket(w.user.getId(), phase, (float) a.x, (float) a.y, (float) a.z, (float) b.x, (float) b.y, (float) b.z, target));
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ACTIVE.isEmpty()) return;
        ACTIVE.removeIf(RhalixKishiWarp::step);
    }

    /** One tick; true when it's over. */
    private static boolean step(W w) {
        LivingEntity u = w.user;
        if (!u.isAlive() || u.isRemoved() || u.level() != w.level || u instanceof Player p && com.xenowarriors.party.Duels.cantAct(p)) return true;
        w.age++;
        u.setDeltaMovement(0, u.getDeltaMovement().y, 0);                   // (knelt down, hand to the ground)
        if (w.age < CROUCH) return false;
        // homing a little on whoever he sent it at
        if (w.hint != null && w.hint.isAlive()) w.dir = flat(w.dir.scale(0.65).add(flat(w.hint.position().subtract(w.head)).scale(0.35)));
        Vec3 next = w.head.add(w.dir.scale(SPEED));
        Double y = surface(w.level, next, w.head.y);
        if (y == null || w.went > RANGE) return fizzle(w, y == null ? "blocked" : "range");
        w.head = new Vec3(next.x, y, next.z);
        w.went += SPEED;
        send(w, KishiFxPacket.HEAD, w.head, w.dir, -1);
        if (w.age % 2 == 0) w.level.sendParticles(ParticleTypes.SQUID_INK, w.head.x, w.head.y + 0.15, w.head.z, 2, 0.25, 0.05, 0.25, 0.01);
        LivingEntity hit = null;
        double best = Double.MAX_VALUE;
        AABB box = new AABB(w.head, w.head).inflate(0.75, 0.3, 0.75).expandTowards(0, 1.8, 0);
        for (LivingEntity e : w.level.getEntitiesOfClass(LivingEntity.class, box, e -> valid(u, e))) {
            double d = e.position().distanceToSqr(w.head);
            if (d < best) { best = d; hit = e; }
        }
        return hit != null ? warp(w, hit) : false;
    }

    /** Where the ground is at this spot (the top of a floor block with room to stand), within a block up or down. */
    private static Double surface(ServerLevel l, Vec3 at, double y) {
        int by = Mth.floor(y + 0.01);
        for (int dy : new int[]{0, 1, -1}) {
            BlockPos feet = BlockPos.containing(at.x, by + dy, at.z);
            BlockPos floor = feet.below();
            if (!l.getBlockState(floor).getCollisionShape(l, floor).isEmpty()
                    && l.getBlockState(feet).getCollisionShape(l, feet).isEmpty()
                    && l.getBlockState(feet.above()).getCollisionShape(l, feet.above()).isEmpty()) {
                double top = l.getBlockState(floor).getCollisionShape(l, floor).max(net.minecraft.core.Direction.Axis.Y);
                return floor.getY() + top;
            }
        }
        return null;
    }

    private static boolean fizzle(W w, String why) {
        send(w, KishiFxPacket.FIZZLE, w.head, w.dir, -1);
        if (TEST) XenoWarriors.LOGGER.info(String.format("XW-TEST rhalixcombo kishi fizzled (%s) after %.1f blocks, tick=%d", why, w.went, w.level.getGameTime()));
        return true;
    }

    /** It reached them: he warps to their side, facing them. */
    private static boolean warp(W w, LivingEntity t) {
        LivingEntity u = w.user;
        Vec3 from = u.position();
        Vec3 back = flat(u.position().subtract(t.position()));
        double gap = (t.getBbWidth() + u.getBbWidth()) / 2.0 + 0.55;
        Vec3 dest = null;
        for (int k = 0; k < 8 && dest == null; k++) {              // the near side first, then round them
            double a = (k % 2 == 0 ? 1 : -1) * ((k + 1) / 2) * Math.PI / 4;
            Vec3 off = new Vec3(back.x * Math.cos(a) - back.z * Math.sin(a), 0, back.x * Math.sin(a) + back.z * Math.cos(a)).scale(gap);
            Vec3 c = t.position().add(off);
            AABB bb = u.getBoundingBox().move(c.subtract(u.position()));
            BlockPos floor = BlockPos.containing(c.x, c.y - 0.05, c.z);
            if (w.level.noCollision(u, bb) && !w.level.getBlockState(floor).getCollisionShape(w.level, floor).isEmpty()) dest = c;
        }
        if (dest == null) return fizzle(w, "no room by the target");
        Vec3 look = t.position().subtract(dest);
        float yaw = (float) (Mth.atan2(-look.x, look.z) * Mth.RAD_TO_DEG);
        w.level.sendParticles(ParticleTypes.REVERSE_PORTAL, from.x, from.y + 1, from.z, 30, 0.3, 0.6, 0.3, 0.1);
        if (u instanceof ServerPlayer sp) sp.connection.teleport(dest.x, dest.y, dest.z, yaw, 5F);
        else { u.moveTo(dest.x, dest.y, dest.z, yaw, 0F); u.setYHeadRot(yaw); u.yBodyRot = yaw; }
        u.setDeltaMovement(Vec3.ZERO);
        u.fallDistance = 0;
        Stagger.hit(t, 10, false);                                   // (caught: they wait for the slash)
        w.level.sendParticles(ParticleTypes.REVERSE_PORTAL, dest.x, dest.y + 1, dest.z, 30, 0.3, 0.6, 0.3, 0.1);
        w.level.playSound(null, BlockPos.containing(dest), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1F, 0.55F);
        send(w, KishiFxPacket.WARP, from, dest, t.getId());
        if (!(u instanceof Player)) WARPED.put(u, t);
        if (TEST) XenoWarriors.LOGGER.info(String.format("XW-TEST rhalixcombo kishi reached %s after %.1f blocks (age %d): warped (%.1f,%.1f,%.1f) -> (%.1f,%.1f,%.1f) facing yaw %.0f, tick=%d",
                t.getType().getDescriptionId(), w.went, w.age, from.x, from.y, from.z, dest.x, dest.y, dest.z, yaw, w.level.getGameTime()));
        return true;
    }

    @SubscribeEvent
    public static void onLogout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        READY_AT.remove(event.getEntity().getUUID());
    }
}

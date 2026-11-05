package com.xenowarriors.party;

import com.xenowarriors.bot.PartyBotEntity;
import com.xenowarriors.bot.PartyBots;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.character.ModCapabilities;
import com.xenowarriors.karma.Karma;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Revive warp: lock on to a teammate who's down (a player or a bot) and hold right-click - you teleport to a safe spot
 * next to them for 30 Karma, right into their revive circle. As Rhalix you arrive casting a Karma shield dome around
 * you both ({@link KarmaShield}) that lasts while you revive them. The Rhalix bot does it on its own when a teammate
 * goes down more than 8 blocks away.
 */
public final class ReviveWarp {
    private ReviveWarp() {}

    public static final float COST = 30F;
    public static final double RANGE = 48, BOT_MIN_DIST = 8;

    /** Last result (tests): "ok", or why it was refused. */
    public static String last = "";

    private static void say(LivingEntity user, String text, ChatFormatting col) {
        if (user instanceof ServerPlayer p) p.displayClientMessage(Component.literal(text).withStyle(col), true);
    }

    private static boolean refuse(LivingEntity user, String why) {
        last = why;
        say(user, why, ChatFormatting.RED);
        com.xenowarriors.XenoWarriors.LOGGER.info("Revive warp refused for {}: {}", user.getName().getString(), why);
        return false;
    }

    /** {@code downed} is down and on {@code user}'s side. */
    public static boolean downedTeammate(LivingEntity user, LivingEntity downed) {
        if (downed == null || downed == user || !PartyBots.isDown(downed)) return false;
        if (user instanceof PartyBotEntity b) return PartyBots.friendly(b, downed);
        if (downed instanceof PartyBotEntity db) return PartyBots.friendly(db, user);
        return user instanceof ServerPlayer p && downed instanceof ServerPlayer d && Parties.sameParty(p, d);
    }

    public static boolean isRhalix(LivingEntity e) {
        return e instanceof PartyBotEntity b ? b.character() == CharacterType.RHALIX
                : e instanceof ServerPlayer p && ModCapabilities.getType(p) == CharacterType.RHALIX;
    }

    private static float karmaOf(LivingEntity e) {
        return e instanceof PartyBotEntity b ? b.karma : e instanceof ServerPlayer p ? Karma.get(p) : 0F;
    }

    /** Warps {@code user} to {@code downed}; false (with a message) when it can't. */
    public static boolean warp(LivingEntity user, LivingEntity downed) {
        if (user == null || !user.isAlive() || user.level().isClientSide) return false;
        if (!downedTeammate(user, downed)) return refuse(user, "They don't need reviving.");
        if (user instanceof ServerPlayer p) {
            if (Duels.cantAct(p)) return refuse(user, "You can't do that right now.");
            if (Duels.inDuel(p)) return refuse(user, "Not during a battle.");
            if (com.xenowarriors.vr.Vr.inVr(p) || com.xenowarriors.vr.Vr.inVr(downed)) return refuse(user, "Revive warp doesn't work in VR.");
        }
        if (user instanceof PartyBotEntity b && b.downed()) return false;
        if (downed.level() != user.level()) return refuse(user, "They're in another world.");
        double dist = user.distanceTo(downed);
        if (dist > RANGE) return refuse(user, "Too far to warp (" + Math.round(dist) + "/" + Math.round(RANGE) + " blocks).");
        boolean free = user instanceof ServerPlayer p && p.isCreative();
        float k = karmaOf(user);
        if (!free && k < COST) return refuse(user, "Not enough Karma to warp (" + Math.round(k) + "/" + Math.round(COST) + ").");
        Vec3 spot = safeSpot(user, downed);
        ServerLevel l = (ServerLevel) user.level();
        Vec3 from = user.position();
        // spend it
        if (!free) {
            if (user instanceof PartyBotEntity b) b.karma -= COST;
            else if (user instanceof ServerPlayer p) Karma.add(p, -COST);
        }
        Vec3 look = downed.position().subtract(spot);
        float yaw = (float) Math.toDegrees(Mth.atan2(-look.x, look.z));
        DustParticleOptions dust = new DustParticleOptions(isRhalix(user) ? new Vector3f(0.65F, 0.3F, 1F) : new Vector3f(0.45F, 1F, 0.55F), 1.4F);
        l.sendParticles(dust, from.x, from.y + 1, from.z, 30, 0.4, 0.8, 0.4, 0);
        l.sendParticles(ParticleTypes.REVERSE_PORTAL, from.x, from.y + 1, from.z, 40, 0.3, 0.8, 0.3, 0.1);
        l.playSound(null, from.x, from.y, from.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.9F, 1.3F);
        if (user instanceof ServerPlayer p) {
            p.stopRiding();
            p.teleportTo(l, spot.x, spot.y, spot.z, yaw, 10F);
            p.fallDistance = 0F;
        } else {
            user.stopRiding();
            user.teleportTo(spot.x, spot.y, spot.z);
            user.setYRot(yaw); user.setYHeadRot(yaw); user.yBodyRot = yaw;
            if (user instanceof PartyBotEntity b) b.getNavigation().stop();
            user.fallDistance = 0F;
        }
        l.sendParticles(dust, spot.x, spot.y + 1, spot.z, 30, 0.4, 0.8, 0.4, 0);
        l.sendParticles(ParticleTypes.PORTAL, spot.x, spot.y + 1, spot.z, 50, 0.4, 0.8, 0.4, 0.4);
        l.playSound(null, spot.x, spot.y, spot.z, SoundEvents.CHORUS_FRUIT_TELEPORT, SoundSource.PLAYERS, 1F, 1.1F);
        String who = ReviveCircle.name(downed);
        say(user, "Warped to " + who + " (-" + Math.round(COST) + " Karma) - stay in the circle to revive them", ChatFormatting.GREEN);
        if (isRhalix(user)) KarmaShield.cast(user, downed);
        last = "ok";
        com.xenowarriors.XenoWarriors.LOGGER.info("Revive warp: {} -> {} ({} blocks, karma {} -> {}, shield={})", user.getName().getString(), who,
                Math.round(dist), Math.round(k), Math.round(karmaOf(user)), isRhalix(user));
        return true;
    }

    /** A free spot right next to them (on the side you come from), inside their revive circle. */
    static Vec3 safeSpot(LivingEntity user, LivingEntity downed) {
        Vec3 c = downed.position();
        Vec3 dir = user.position().subtract(c);
        double base = dir.horizontalDistanceSqr() < 1.0E-4 ? 0 : Math.atan2(dir.z, dir.x);
        ServerLevel l = (ServerLevel) downed.level();
        for (double r : new double[]{1.4, 1.0, 1.8}) {
            for (int i = 0; i < 12; i++) {
                double a = base + (i % 2 == 0 ? 1 : -1) * ((i + 1) / 2) * (Math.PI / 6);
                for (int dy : new int[]{0, 1, -1}) {
                    Vec3 at = new Vec3(c.x + Math.cos(a) * r, Math.floor(c.y) + dy, c.z + Math.sin(a) * r);
                    BlockPos below = BlockPos.containing(at.x, at.y - 0.5, at.z);
                    if (l.noCollision(user, user.getType().getDimensions().makeBoundingBox(at))
                            && !l.getBlockState(below).getCollisionShape(l, below).isEmpty()
                            && l.getFluidState(BlockPos.containing(at)).isEmpty()) return at;
                }
            }
        }
        return c;
    }
}

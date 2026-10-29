package com.xenowarriors.party;

import com.xenowarriors.bot.PartyBotEntity;
import com.xenowarriors.bot.PartyBots;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The revive circle: a ring on the ground around anyone who's down (a player or a bot teammate). A living teammate -
 * player or bot - standing inside it for 2 seconds brings them back up. The ring fills with green as it goes.
 */
public final class ReviveCircle {
    private ReviveCircle() {}

    public static final double RADIUS = 2.2;
    public static final int TICKS = 40;
    private static final DustParticleOptions RING = new DustParticleOptions(new Vector3f(1F, 0.35F, 0.3F), 0.9F),
            FILL = new DustParticleOptions(new Vector3f(0.4F, 1F, 0.5F), 1F);
    private static final Map<UUID, Integer> BOT_PROGRESS = new HashMap<>();

    /** Someone alive who can revive this downed player, standing in their circle. */
    public static LivingEntity helperFor(ServerPlayer downed) {
        for (ServerPlayer other : downed.server.getPlayerList().getPlayers())
            if (other != downed && other.isAlive() && !other.isSpectator() && !Duels.isKnockedOut(other) && other.level() == downed.level()
                    && other.distanceTo(downed) < RADIUS && other.hasLineOfSight(downed) && Parties.sameParty(downed, other)) return other;
        for (PartyBotEntity b : downed.level().getEntitiesOfClass(PartyBotEntity.class, downed.getBoundingBox().inflate(RADIUS + 1),
                b -> !b.downed() && !b.journey() && b.distanceTo(downed) < RADIUS && b.hasLineOfSight(downed) && PartyBots.friendly(b, downed))) return b;
        return null;
    }

    /** Someone alive who can revive this downed bot. */
    public static LivingEntity helperFor(PartyBotEntity downed) {
        ServerPlayer o = downed.owner();
        if (o == null) return null;
        for (ServerPlayer other : o.server.getPlayerList().getPlayers())
            if (other.isAlive() && !other.isSpectator() && !Duels.isKnockedOut(other) && other.level() == downed.level() && other.distanceTo(downed) < RADIUS
                    && other.hasLineOfSight(downed) && PartyBots.friendly(downed, other)) return other;
        for (PartyBotEntity b : downed.level().getEntitiesOfClass(PartyBotEntity.class, downed.getBoundingBox().inflate(RADIUS + 1),
                b -> b != downed && !b.downed() && !b.journey() && b.distanceTo(downed) < RADIUS && b.hasLineOfSight(downed) && PartyBots.friendly(downed, b))) return b;
        return null;
    }

    /** Draws the ring (every few ticks): red dots, green where the revive has got to. */
    public static void draw(ServerLevel l, Vec3 at, int progress) {
        int n = 24, lit = Math.round(n * Math.min(1F, progress / (float) TICKS));
        for (int i = 0; i < n; i++) {
            double a = i / (double) n * Math.PI * 2 + (l.getGameTime() % 40) / 40.0 * 0.26;
            l.sendParticles(i < lit ? FILL : RING, at.x + Math.cos(a) * RADIUS, at.y + 0.1, at.z + Math.sin(a) * RADIUS, 1, 0, 0.02, 0, 0);
        }
        if (progress > 0 && l.getGameTime() % 4 == 0) l.sendParticles(ParticleTypes.HEART, at.x, at.y + 0.6, at.z, 1, 0.3, 0.2, 0.3, 0);
    }

    static String name(LivingEntity e) { return e instanceof PartyBotEntity b ? b.label().getString() : e.getName().getString(); }

    /** Progress shown to a player helping. */
    public static void show(LivingEntity helper, LivingEntity downed, int progress) {
        if (!(helper instanceof ServerPlayer hp) || progress % 5 != 0) return;
        int bars = Math.round(progress / (float) TICKS * 10);
        hp.displayClientMessage(Component.literal("Reviving " + name(downed) + "  ").withStyle(ChatFormatting.GREEN)
                .append(Component.literal("▮".repeat(bars)).withStyle(ChatFormatting.GREEN))
                .append(Component.literal("▯".repeat(10 - bars)).withStyle(ChatFormatting.DARK_GRAY)), true);
    }

    /** A downed bot's circle, every tick. */
    public static void tickBot(PartyBotEntity b) {
        ServerLevel l = (ServerLevel) b.level();
        LivingEntity helper = helperFor(b);
        int progress = helper == null ? 0 : BOT_PROGRESS.getOrDefault(b.getUUID(), 0) + 1;
        BOT_PROGRESS.put(b.getUUID(), progress);
        if (b.tickCount % 3 == 0) draw(l, b.position(), progress);
        if (helper != null) {
            show(helper, b, progress);
            if (progress >= TICKS) {
                BOT_PROGRESS.remove(b.getUUID());
                b.revive();
                l.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, b.getX(), b.getY() + 1, b.getZ(), 30, 0.4, 0.6, 0.4, 0.2);
                l.playSound(null, b.blockPosition(), net.minecraft.sounds.SoundEvents.TOTEM_USE, net.minecraft.sounds.SoundSource.PLAYERS, 0.6F, 1.4F);
                ServerPlayer o = b.owner();
                if (o != null) o.displayClientMessage(Component.literal("").append(b.label()).append(" is back up! (revived by " + name(helper) + ")").withStyle(ChatFormatting.GREEN), true);
            }
        }
    }

    public static void forget(UUID id) { BOT_PROGRESS.remove(id); }
}

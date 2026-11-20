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
                    && other.distanceTo(downed) < com.xenowarriors.apl.Apl.reviveReach(other) && other.hasLineOfSight(downed) && Parties.sameParty(downed, other)) return other;
        for (PartyBotEntity b : downed.level().getEntitiesOfClass(PartyBotEntity.class, downed.getBoundingBox().inflate(com.xenowarriors.apl.Apl.MAX_REACH + 1),
                b -> !b.downed() && !b.journey() && b.distanceTo(downed) < com.xenowarriors.apl.Apl.reviveReach(b) && b.hasLineOfSight(downed) && PartyBots.friendly(b, downed))) return b;
        return null;
    }

    /** Someone alive who can revive this downed bot. */
    public static LivingEntity helperFor(PartyBotEntity downed) {
        ServerPlayer o = downed.owner();
        if (o == null) return null;
        for (ServerPlayer other : o.server.getPlayerList().getPlayers())
            if (other.isAlive() && !other.isSpectator() && !Duels.isKnockedOut(other) && other.level() == downed.level() && other.distanceTo(downed) < com.xenowarriors.apl.Apl.reviveReach(other)
                    && other.hasLineOfSight(downed) && PartyBots.friendly(downed, other)) return other;
        for (PartyBotEntity b : downed.level().getEntitiesOfClass(PartyBotEntity.class, downed.getBoundingBox().inflate(com.xenowarriors.apl.Apl.MAX_REACH + 1),
                b -> b != downed && !b.downed() && !b.journey() && b.distanceTo(downed) < com.xenowarriors.apl.Apl.reviveReach(b) && b.hasLineOfSight(downed) && PartyBots.friendly(downed, b))) return b;
        return null;
    }

    /** Kaeru's Fox Heal brings a downed bot teammate straight back up (no circle wait), like it does a downed player. */
    public static void foxRevive(PartyBotEntity b, LivingEntity by) {
        if (!b.downed()) return;
        LivingEntity who = com.xenowarriors.combat.Allies.actor(by) instanceof LivingEntity a ? a : by;   // (a bot's stand-in: the bot)
        BOT_PROGRESS.remove(b.getUUID());
        b.revive();
        Bonds.revived(b, who);
        ServerLevel l = (ServerLevel) b.level();
        l.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, b.getX(), b.getY() + 1, b.getZ(), 30, 0.4, 0.6, 0.4, 0.2);
        l.playSound(null, b.blockPosition(), net.minecraft.sounds.SoundEvents.TOTEM_USE, net.minecraft.sounds.SoundSource.PLAYERS, 0.6F, 1.4F);
        ServerPlayer o = b.owner();
        if (o != null) o.displayClientMessage(Component.literal("").append(b.label()).append(" is back up! (revived by " + name(who) + ")").withStyle(ChatFormatting.GREEN), true);
        if (who instanceof ServerPlayer hp && hp != o && !(hp instanceof net.minecraftforge.common.util.FakePlayer))
            hp.displayClientMessage(Component.literal("You revived ").append(b.label()).append("!").withStyle(ChatFormatting.GREEN), true);
    }

    /** (Tests) revive flows drawn. */
    public static int flows;

    /**
     * The reviver holds out a hand (ReviveClient draws it) and their karma flows from it into the one who's down: a
     * stream of motes in their character's colour, arcing over and pouring in, with sparkles where it lands.
     */
    static void flow(LivingEntity helper, LivingEntity downed) {
        if (!(helper.level() instanceof ServerLevel l)) return;
        long t = l.getGameTime();
        if (t % 4 == 0) com.xenowarriors.network.ModNetwork.sendToTrackingEntityAndSelf(new com.xenowarriors.network.ReviveAnimPacket(helper.getId(), downed.getId()), helper);
        if (helper instanceof PartyBotEntity b) { b.getNavigation().stop(); b.getLookControl().setLookAt(downed, 30F, 30F); }   // (a bot turns to them)
        if (t % 2 != 0) return;
        flows++;
        int rgb = helper instanceof PartyBotEntity b ? b.character().getColor()
                : helper instanceof ServerPlayer p ? (com.xenowarriors.character.ModCapabilities.getType(p) == com.xenowarriors.character.CharacterType.YOU
                    ? com.xenowarriors.you.You.color(p) : com.xenowarriors.character.ModCapabilities.getType(p).getColor()) : 0x66FF88;
        Vector3f col = new Vector3f((rgb >> 16 & 255) / 255F, (rgb >> 8 & 255) / 255F, (rgb & 255) / 255F);
        DustParticleOptions dust = new DustParticleOptions(col, 0.9F);
        Vec3 to = new Vec3(downed.getX() - helper.getX(), 0, downed.getZ() - helper.getZ());
        Vec3 dir = to.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : to.normalize();
        Vec3 right = new Vec3(-dir.z, 0, dir.x);
        Vec3 hand = helper.position().add(0, helper.getBbHeight() * 0.72, 0).add(dir.scale(0.7)).add(right.scale(0.3));
        Vec3 end = downed.position().add(0, 0.35, 0);
        for (int i = 0; i < 8; i++) {
            double k = ((i / 8.0) + (t % 16) / 16.0) % 1.0;                      // (moving along the stream)
            Vec3 at = hand.lerp(end, k).add(0, Math.sin(k * Math.PI) * 0.35, 0);
            double wob = Math.sin(t * 0.4 + i) * 0.05;
            l.sendParticles(dust, at.x + right.x * wob, at.y, at.z + right.z * wob, 1, 0, 0, 0, 0);
        }
        if (t % 6 == 0) l.sendParticles(ParticleTypes.END_ROD, end.x, end.y + 0.2, end.z, 2, 0.25, 0.15, 0.25, 0.01);
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
        flow(helper, downed);
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
        int was = BOT_PROGRESS.getOrDefault(b.getUUID(), 0);
        int progress = helper == null ? 0 : was + com.xenowarriors.apl.Apl.reviveGain(helper, l.getGameTime());
        BOT_PROGRESS.put(b.getUUID(), progress);
        if (helper != null && was == 0) com.xenowarriors.apl.Apl.reviveStarted(b, helper);
        if (b.tickCount % 3 == 0) draw(l, b.position(), progress);
        if (helper != null) {
            show(helper, b, progress);
            if (progress >= TICKS) {
                BOT_PROGRESS.remove(b.getUUID());   
                b.revive();
                Bonds.revived(b, helper);
                l.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, b.getX(), b.getY() + 1, b.getZ(), 30, 0.4, 0.6, 0.4, 0.2);
                l.playSound(null, b.blockPosition(), net.minecraft.sounds.SoundEvents.TOTEM_USE, net.minecraft.sounds.SoundSource.PLAYERS, 0.6F, 1.4F);
                ServerPlayer o = b.owner();
                if (o != null) o.displayClientMessage(Component.literal("").append(b.label()).append(" is back up! (revived by " + name(helper) + ")").withStyle(ChatFormatting.GREEN), true);
            }
        }
    }

    public static void forget(UUID id) { BOT_PROGRESS.remove(id); }
}

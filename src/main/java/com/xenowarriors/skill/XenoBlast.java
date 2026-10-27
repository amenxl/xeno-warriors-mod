package com.xenowarriors.skill;

import com.xenowarriors.character.CharacterType;
import com.xenowarriors.character.ModCapabilities;
import com.xenowarriors.karma.Karma;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.HashSet;
import java.util.Set;

/**
 * Xeno Blast - Mikiko's and Rhalix's last resort (their KA slot, until their real KAs are revealed). A short charge,
 * then a huge blast that pours out ALL the Karma they have left: the more Karma, the harder and wider it hits. After
 * it, their Karma is empty and won't come back for 30 seconds.
 */
public final class XenoBlast {
    public static final int LOCK_TICKS = 30 * 20, MIN_KARMA = 10, RANGE = 40;

    private XenoBlast() {}

    /** Whether this character's KA slot is the Xeno Blast. */
    public static boolean has(CharacterType t) { return t == CharacterType.MIKIKO || t == CharacterType.RHALIX; }

    public static boolean use(ServerPlayer p) {
        CharacterType type = ModCapabilities.getType(p);
        if (Karma.regenLocked(p)) {
            p.displayClientMessage(Component.literal("Your Karma is still drained from the last Xeno Blast.").withStyle(ChatFormatting.DARK_RED), true);
            return false;
        }
        float karma = Karma.get(p);
        if (karma < MIN_KARMA && !p.isCreative()) {
            p.displayClientMessage(Component.literal("Not enough Karma left for a Xeno Blast (needs " + MIN_KARMA + ").").withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        float power = p.isCreative() ? Math.max(karma, 100F) : karma;
        float dmg = 12F + power * 0.5F;
        double width = 1.2 + Math.min(2.0, power / 80.0);
        boolean rhalix = type == CharacterType.RHALIX;
        Vector3f core = rhalix ? new Vector3f(0.72F, 0.3F, 1F) : new Vector3f(1F, 0.5F, 0.12F);
        Vector3f rim = rhalix ? new Vector3f(0.35F, 0.05F, 0.6F) : new Vector3f(1F, 0.85F, 0.3F);
        ParticleOptions spark = rhalix ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME;
        ServerLevel l = p.serverLevel();

        // it takes everything they have
        if (!p.isCreative()) Karma.add(p, -karma);
        Karma.lockRegen(p, LOCK_TICKS);
        p.displayClientMessage(Component.literal("XENO BLAST!").withStyle(rhalix ? ChatFormatting.DARK_PURPLE : ChatFormatting.GOLD, ChatFormatting.BOLD), true);
        Component line = Component.literal(p.getName().getString() + ": ").withStyle(ChatFormatting.WHITE)
                .append(Component.literal("XENO BLAST!").withStyle(rhalix ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.GOLD, ChatFormatting.BOLD));
        for (ServerPlayer o : l.players()) if (o != p && o.distanceToSqr(p) < 48 * 48) o.displayClientMessage(line, true);

        // charge: energy pulled in around the hands
        com.xenowarriors.anim.Poses.play(p, com.xenowarriors.anim.Poses.EL_CHARGE, 16);
        l.playSound(null, p.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.4F, 0.6F);
        for (int i = 0; i < 16; i++) {
            int ii = i;
            ServerScheduler.later(i, () -> {
                if (!p.isAlive()) return;
                Vec3 h = p.getEyePosition().add(p.getLookAngle().scale(0.9)).subtract(0, 0.25, 0);
                for (int k = 0; k < 6; k++) {
                    double a = (ii * 0.6 + k * Math.PI / 3), r = 1.6 - ii * 0.08;
                    l.sendParticles(new DustParticleOptions(k % 2 == 0 ? core : rim, 1.3F), h.x + Math.cos(a) * r, h.y + Math.sin(a * 1.3) * r * 0.6, h.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
                }
                l.sendParticles(spark, h.x, h.y, h.z, 2, 0.15, 0.15, 0.15, 0.01);
            });
        }
        // the blast
        ServerScheduler.later(16, () -> {
            if (!p.isAlive()) return;
            com.xenowarriors.anim.Poses.play(p, com.xenowarriors.anim.Poses.EL_THRUST, 28);
            l.playSound(null, p.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.6F, rhalix ? 0.6F : 0.85F);
            l.playSound(null, p.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.0F, 0.7F);
            Set<Integer> hit = new HashSet<>();
            for (int k = 0; k < 22; k++) ServerScheduler.later(k, () -> {
                if (!p.isAlive()) return;
                Vec3 s = p.getEyePosition().subtract(0, 0.25, 0), d = p.getLookAngle();
                for (int j = 1; j <= RANGE; j++) {
                    Vec3 q = s.add(d.scale(j));
                    l.sendParticles(new DustParticleOptions(core, 2.6F), q.x, q.y, q.z, 2, width * 0.22, width * 0.22, width * 0.22, 0);
                    if (j % 2 == 0) l.sendParticles(new DustParticleOptions(rim, 1.8F), q.x, q.y, q.z, 1, width * 0.45, width * 0.45, width * 0.45, 0);
                    if (j % 5 == 0) l.sendParticles(spark, q.x, q.y, q.z, 1, 0.3, 0.3, 0.3, 0.02);
                }
                for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, new AABB(s, s.add(d.scale(RANGE))).inflate(width + 1), e -> SkillTargeting.canTarget(p, e))) {
                    Vec3 rel = e.position().add(0, e.getBbHeight() / 2, 0).subtract(s);
                    double along = rel.dot(d);
                    if (along < 0 || along > RANGE || rel.subtract(d.scale(along)).length() > width + e.getBbWidth()) continue;
                    if (hit.add(e.getId())) {
                        e.hurt(p.damageSources().indirectMagic(p, p), dmg);
                        e.push(d.x * 1.8, 0.45, d.z * 1.8);
                        e.hurtMarked = true;
                        l.sendParticles(ParticleTypes.EXPLOSION, e.getX(), e.getY() + e.getBbHeight() / 2, e.getZ(), 1, 0, 0, 0, 0);
                    }
                }
            });
        });
        return true;
    }
}

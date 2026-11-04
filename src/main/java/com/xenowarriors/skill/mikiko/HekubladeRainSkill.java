package com.xenowarriors.skill.mikiko;

import com.xenowarriors.entity.HekubladeRainEntity;
import com.xenowarriors.network.ModNetwork;
import com.xenowarriors.network.SkillAnimPacket;
import com.xenowarriors.skill.Skill;
import com.xenowarriors.skill.SkillTargeting;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Mikiko's second ultimate (Character Level 300) - Hekublade Rain: a ring of glowing Hekublades appears high over his
 * target and they rain down in waves for about three seconds, then a giant one drops in the middle. Picked for the
 * ultimate slot instead of Blazing Wind in Character Customization.
 */
public class HekubladeRainSkill implements Skill {
    public static final int UNLOCK_LEVEL = 300;
    private static final double RANGE = 28;

    @Override
    public String getId() { return "hekublade_rain"; }

    @Override
    public int getCooldownTicks() { return 60 * 20; }

    @Override
    public int getKarmaCost() { return 60; }

    @Override
    public boolean use(ServerPlayer player) {
        HekubladeRainEntity rain = HekubladeRainEntity.create(player, center(player));
        player.level().addFreshEntity(rain);
        ModNetwork.sendToTrackingAndSelf(new SkillAnimPacket(player.getId(), SkillAnimPacket.RAISE, !player.onGround()), player);
        return true;
    }

    /** Over his target (or what he's aiming at), on the ground under it. */
    public static Vec3 center(ServerPlayer p) {
        LivingEntity t = target(p);
        Vec3 at;
        if (t != null) at = t.position();
        else {
            Vec3 eye = p.getEyePosition(), end = eye.add(p.getViewVector(1F).scale(RANGE));
            var hit = p.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
            at = hit.getType() == HitResult.Type.MISS ? eye.add(p.getViewVector(1F).multiply(1, 0, 1).normalize().scale(8)) : hit.getLocation();
        }
        return new Vec3(at.x, ground(p, at), at.z);
    }

    /** The enemy closest to where he's aiming (a wide cone, in sight) - never someone on his side; else the nearest monster around. */
    private static LivingEntity target(ServerPlayer p) {
        Vec3 eye = p.getEyePosition(), look = p.getViewVector(1F);
        LivingEntity best = null;
        double bestScore = Math.cos(Math.toRadians(35));
        for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(RANGE), e -> HekubladeRainEntity.foe(p, e))) {
            Vec3 c = e.getBoundingBox().getCenter(), to = c.subtract(eye);
            double d = to.length();
            if (d > RANGE || d < 0.5) continue;
            double score = to.scale(1 / d).dot(look) - d / (RANGE * 8);
            if (score > bestScore && SkillTargeting.hasLineOfSight(p, eye, c)) { bestScore = score; best = e; }
        }
        if (best != null) return best;
        return SkillTargeting.around(p, p.position(), 14, 8, 16).stream()
                .filter(e -> e instanceof net.minecraft.world.entity.monster.Enemy && HekubladeRainEntity.foe(p, e))
                .findFirst().orElse(null);
    }

    private static double ground(ServerPlayer p, Vec3 at) {
        BlockPos.MutableBlockPos pos = BlockPos.containing(at.x, at.y + 2, at.z).mutable();
        for (int i = 0; i < 24; i++) {
            if (!p.level().getBlockState(pos).getCollisionShape(p.level(), pos).isEmpty()) return pos.getY() + 1;
            pos.move(0, -1, 0);
        }
        return at.y;
    }
}

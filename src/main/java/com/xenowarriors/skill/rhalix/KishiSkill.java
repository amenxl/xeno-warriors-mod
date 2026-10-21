package com.xenowarriors.skill.rhalix;

import com.xenowarriors.entity.KishiDomeEntity;
import com.xenowarriors.karma.Karma;
import com.xenowarriors.network.SkillAnimPacket;
import com.xenowarriors.network.ModNetwork;
import com.xenowarriors.skill.Skill;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Rhalix skill 1 - Kishi.
 * Press: slam a gravity dome down on the mob/player you're aiming at (or the spot you're looking at).
 * Press again while it's active: make the gravity heavier (up to level 3).
 */
public class KishiSkill implements Skill {
    public static final double RANGE = 20.0;
    private static final int COOLDOWN = 15 * 20;
    /** Karma to make the dome; making the gravity heavier costs more on top. */
    public static final int KARMA_COST = 30;
    public static final int HEAVIER_KARMA_COST = 15;

    private static final Map<UUID, KishiDomeEntity> ACTIVE = new HashMap<>();

    @Override
    public String getId() { return "kishi"; }

    @Override
    public int getCooldownTicks() { return COOLDOWN; }

    @Override
    public int getKarmaCost() { return KARMA_COST; }

    @Override
    public boolean tryRecast(ServerPlayer player) {
        KishiDomeEntity dome = ACTIVE.get(player.getUUID());
        if (dome == null || dome.isRemoved()) {
            ACTIVE.remove(player.getUUID());
            return false;
        }
        if (dome.getGravityLevel() >= KishiDomeEntity.MAX_LEVEL) {
            player.displayClientMessage(Component.translatable("message.xenowarriors.kishi.max"), true);
            return true;
        }
        if (!Karma.trySpend(player, HEAVIER_KARMA_COST)) return true;
        if (dome.raiseLevel()) {
            playCastAnimation(player);
            int level = dome.getGravityLevel();
            player.level().playSound(null, dome.getX(), dome.getY(), dome.getZ(),
                    SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 1.2F, 0.5F + 0.15F * level);
            player.level().playSound(null, dome.getX(), dome.getY(), dome.getZ(),
                    SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.5F, 0.4F);
            player.displayClientMessage(Component.translatable("message.xenowarriors.kishi.level", level), true);
        } else {
            player.displayClientMessage(Component.translatable("message.xenowarriors.kishi.max"), true);
        }
        return true;
    }

    @Override
    public boolean use(ServerPlayer player) {
        Vec3 target = findTarget(player);
        if (target == null) {
            player.displayClientMessage(Component.translatable("message.xenowarriors.no_target"), true);
            return false;
        }
        KishiDomeEntity dome = KishiDomeEntity.create(player.level(), player, target);
        player.level().addFreshEntity(dome);
        ACTIVE.put(player.getUUID(), dome);
        playCastAnimation(player);

        player.level().playSound(null, target.x, target.y, target.z,
                SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.5F, 0.6F);
        player.level().playSound(null, target.x, target.y, target.z,
                SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.7F, 0.5F);
        player.displayClientMessage(Component.translatable("message.xenowarriors.kishi.cast"), true);
        return true;
    }

    /** Tells everyone nearby to play Rhalix's cast pose (the flying one if he's in the air). */
    private static void playCastAnimation(ServerPlayer player) {
        boolean airborne = player.getAbilities().flying || player.isFallFlying()
                || (!player.onGround() && !player.isInWater() && !player.onClimbable());
        ModNetwork.sendToTrackingAndSelf(new SkillAnimPacket(player.getId(), SkillAnimPacket.KISHI, airborne), player);
    }

    /** The mob/player being aimed at, or else the block being looked at. */
    private static Vec3 findTarget(ServerPlayer player) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F);
        Vec3 end = eye.add(look.scale(RANGE));

        BlockHitResult block = player.level().clip(
                new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 limit = block.getType() == HitResult.Type.MISS ? end : block.getLocation();

        AABB search = player.getBoundingBox().expandTowards(look.scale(RANGE)).inflate(1.0);
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(player, eye, limit, search,
                e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator() && e != player,
                eye.distanceToSqr(limit));

        if (entityHit != null) return entityHit.getEntity().position();
        if (block.getType() == HitResult.Type.BLOCK) return block.getLocation();
        return null;
    }
}

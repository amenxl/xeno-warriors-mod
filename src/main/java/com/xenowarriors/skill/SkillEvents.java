package com.xenowarriors.skill;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.entity.KishiDomeEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SkillEvents {
    private SkillEvents() {}

    /** Rhalix can't be hurt while his ultimate cutscene plays. */
    public static final String ULT_UNTIL = "xenowarriors_ult_until";

    @SubscribeEvent
    public static void onAttacked(net.minecraftforge.event.entity.living.LivingAttackEvent event) {
        LivingEntity e = event.getEntity();
        if (e.getPersistentData().getLong(ULT_UNTIL) < e.level().getGameTime()) return;
        // an enemy got a hit in during an ultimate / Xeno Blast cutscene: the hit lands and counts against the guard
        // (CutsceneBreak.onHit, once its damage is known); enough of them end the cutscene
        if (e instanceof net.minecraft.server.level.ServerPlayer sp && CutsceneBreak.byEnemy(sp, event.getSource()) && CutsceneBreak.breakable(sp)) return;
        event.setCanceled(true);
    }

    /** The damage an enemy's hit did during a cutscene, after every reduction (DragonMineZ's too): the cutscene guard. */
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void onDamaged(net.minecraftforge.event.entity.living.LivingDamageEvent event) {
        if (!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer sp)) return;
        if (sp.getPersistentData().getLong(ULT_UNTIL) < sp.level().getGameTime()) return;
        if (CutsceneBreak.byEnemy(sp, event.getSource()) && CutsceneBreak.breakable(sp)) CutsceneBreak.onHit(sp, event.getSource(), event.getAmount());
    }

    /** A hit the cutscene guard shrugged off doesn't knock you out of the cutscene's path. */
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGH)
    public static void onKnockback(net.minecraftforge.event.entity.living.LivingKnockBackEvent event) {
        if (CutsceneBreak.guarding(event.getEntity())) event.setCanceled(true);
    }

    /** Kishi / gravity skills: nobody can jump inside a gravity dome or while rooted. */
    @SubscribeEvent
    public static void onJump(LivingEvent.LivingJumpEvent event) {
        LivingEntity entity = event.getEntity();
        if (com.xenowarriors.skill.rhalix.KaReleaseSkill.free(entity)) return;
        if (Rooting.isRooted(entity)) {
            Vec3 v = entity.getDeltaMovement();
            entity.setDeltaMovement(0, Math.min(v.y, 0), 0);
            return;
        }
        for (KishiDomeEntity dome : entity.level().getEntitiesOfClass(KishiDomeEntity.class,
                entity.getBoundingBox().inflate(KishiDomeEntity.MAX_RADIUS + 1, KishiDomeEntity.MAX_HEIGHT, KishiDomeEntity.MAX_RADIUS + 1))) {
            if (dome.gravity() > 0) {
                // DragonMineZ gravity: the jump is cut by DMZ's jump penalty for this G (none left from 75 net G)
                if (entity.level().isClientSide ? dome.affectedOnClient(entity) : dome.affects(entity)) {
                    double f = com.xenowarriors.compat.DmzKishiGravity.jumpFactor(com.xenowarriors.compat.DmzKishiGravity.net(entity, dome.gravity()));
                    Vec3 v = entity.getDeltaMovement();
                    entity.setDeltaMovement(v.x, v.y > 0 ? v.y * f : v.y, v.z);
                    return;
                }
                continue;
            }
            if (dome.affects(entity)) {
                Vec3 v = entity.getDeltaMovement();
                entity.setDeltaMovement(v.x, Math.min(v.y, 0), v.z);
                return;
            }
        }
    }
}

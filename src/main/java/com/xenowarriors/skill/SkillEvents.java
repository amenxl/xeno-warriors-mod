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
        if (e.getPersistentData().getLong(ULT_UNTIL) >= e.level().getGameTime()) event.setCanceled(true);
    }

    /** Kishi / gravity skills: nobody can jump inside a gravity dome or while rooted. */
    @SubscribeEvent
    public static void onJump(LivingEvent.LivingJumpEvent event) {
        LivingEntity entity = event.getEntity();
        if (Rooting.isRooted(entity)) {
            Vec3 v = entity.getDeltaMovement();
            entity.setDeltaMovement(0, Math.min(v.y, 0), 0);
            return;
        }
        for (KishiDomeEntity dome : entity.level().getEntitiesOfClass(KishiDomeEntity.class,
                entity.getBoundingBox().inflate(KishiDomeEntity.MAX_RADIUS + 1, KishiDomeEntity.MAX_HEIGHT, KishiDomeEntity.MAX_RADIUS + 1))) {
            if (dome.affects(entity)) {
                Vec3 v = entity.getDeltaMovement();
                entity.setDeltaMovement(v.x, Math.min(v.y, 0), v.z);
                return;
            }
        }
    }
}

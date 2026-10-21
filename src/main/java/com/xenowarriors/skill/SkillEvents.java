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

    /** Kishi: nobody can jump inside the gravity dome. */
    @SubscribeEvent
    public static void onJump(LivingEvent.LivingJumpEvent event) {
        LivingEntity entity = event.getEntity();
        for (KishiDomeEntity dome : entity.level().getEntitiesOfClass(KishiDomeEntity.class,
                entity.getBoundingBox().inflate(KishiDomeEntity.RADIUS + 1, KishiDomeEntity.HEIGHT, KishiDomeEntity.RADIUS + 1))) {
            if (dome.affects(entity)) {
                Vec3 v = entity.getDeltaMovement();
                entity.setDeltaMovement(v.x, Math.min(v.y, 0), v.z);
                return;
            }
        }
    }
}

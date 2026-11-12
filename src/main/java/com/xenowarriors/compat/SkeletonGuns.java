package com.xenowarriors.compat;

import com.xenowarriors.XenoWarriors;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.WitherSkeleton;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.EnumSet;

/**
 * With TACZ: now and then a skeleton spawns with a gun (an old sidearm or a shotgun) instead of its bow, shoots it at
 * you from a few blocks off, and can drop it.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID)
public final class SkeletonGuns {
    private SkeletonGuns() {}

    public static final float CHANCE = 0.07F, DROP = 0.2F;
    private static final String TAG = "xw_gun_skeleton";
    /** (Tests) skeletons armed. */
    public static int armed;

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent e) {
        if (e.getLevel().isClientSide() || !(e.getEntity() instanceof AbstractSkeleton s) || s instanceof WitherSkeleton || !TaczGuns.available()) return;
        if (!e.loadedFromDisk() && !s.getTags().contains(TAG + "_rolled")) {
            s.addTag(TAG + "_rolled");
            if (s.getRandom().nextFloat() < CHANCE) arm(s);
        }
        if (s.getTags().contains(TAG) && TaczGuns.isGun(s.getMainHandItem())) s.goalSelector.addGoal(1, new GunGoal(s));
    }

    /** Gives this skeleton a gun (also for the tests). */
    public static boolean arm(AbstractSkeleton s) {
        String id = TaczGuns.pick(TaczGuns.SKELETON_GUNS, s.getRandom());
        if (id == null) return false;
        ItemStack g = TaczGuns.gun(id, 12);
        if (g.isEmpty()) return false;
        s.setItemSlot(EquipmentSlot.MAINHAND, g);
        s.setDropChance(EquipmentSlot.MAINHAND, DROP);
        s.addTag(TAG);
        armed++;
        return true;
    }

    /** Keeps 5-14 blocks off and fires a shot every second or so while it can see you. */
    static class GunGoal extends Goal {
        private final AbstractSkeleton s;
        private int t;
        private boolean drawn;
        GunGoal(AbstractSkeleton s) { this.s = s; setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); }
        @Override public boolean canUse() { return TaczGuns.isGun(s.getMainHandItem()) && s.getTarget() != null && s.getTarget().isAlive(); }
        @Override public void start() { t = 0; }
        @Override public void stop() { s.getNavigation().stop(); }
        @Override public boolean requiresUpdateEveryTick() { return true; }
        @Override public void tick() {
            LivingEntity target = s.getTarget();
            if (target == null) return;
            s.getLookControl().setLookAt(target, 30F, 30F);
            double d = s.distanceTo(target);
            boolean see = s.getSensing().hasLineOfSight(target);
            if (!see || d > 14) s.getNavigation().moveTo(target, 1.0);
            else if (d < 5) { Vec3 away = s.position().add(s.position().subtract(target.position()).normalize().scale(5)); s.getNavigation().moveTo(away.x, away.y, away.z, 1.1); }
            else s.getNavigation().stop();
            if (++t < 20 || !see || t % 22 != 0) return;
            if (!drawn) { TaczGuns.draw(s); drawn = true; return; }
            Vec3 dir = target.getBoundingBox().getCenter().subtract(s.getEyePosition());
            float yaw = (float) (Math.toDegrees(Math.atan2(dir.z, dir.x)) - 90F) + (s.getRandom().nextFloat() - .5F) * 14F;
            float pitch = (float) -Math.toDegrees(Math.atan2(dir.y, Math.sqrt(dir.x * dir.x + dir.z * dir.z))) + (s.getRandom().nextFloat() - .5F) * 8F;
            s.setYRot(yaw); s.yBodyRot = yaw; s.yHeadRot = yaw; s.setXRot(pitch);
            TaczGuns.shoot(s, pitch, yaw);
        }
    }
}

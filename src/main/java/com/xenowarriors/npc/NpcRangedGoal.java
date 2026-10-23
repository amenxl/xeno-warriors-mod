package com.xenowarriors.npc;

import com.xenowarriors.corrupted.SoulBoltEntity;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Ranged attacks: the gunner fires bullets, the phone and briefcase guys throw them, the permanently possessed hurl
 * dark blasts, and (with DragonMineZ) fighters throw Ki blasts now and then. Runs alongside their melee: only when
 * the target's a few blocks off.
 */
public class NpcRangedGoal extends Goal {
    public enum Kind { BULLET, PHONE, BRIEFCASE, DARK, KI }

    private final PathfinderMob mob;
    private final Kind kind;
    private final int every;
    private final double min, max;
    private int cooldown = 40;

    public NpcRangedGoal(PathfinderMob mob, Kind kind, int every, double min, double max) {
        this.mob = mob;
        this.kind = kind;
        this.every = every;
        this.min = min;
        this.max = max;
        setFlags(EnumSet.noneOf(Flag.class));
    }

    @Override
    public boolean canUse() {
        LivingEntity t = mob.getTarget();
        if (t == null || !t.isAlive()) return false;
        if (mob instanceof HumanNpcEntity h && !h.canShoot(kind)) return false;
        double d = mob.distanceTo(t);
        return d >= min && d <= max && mob.hasLineOfSight(t);
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (--cooldown > 0) return;
        LivingEntity t = mob.getTarget();
        if (t == null) return;
        cooldown = every + mob.getRandom().nextInt(every / 2 + 1);
        mob.getLookControl().setLookAt(t, 60F, 60F);
        Vec3 from = mob.getEyePosition().subtract(0, 0.25, 0);
        Vec3 to = t.position().add(0, t.getBbHeight() * 0.55, 0);
        Vec3 dir = to.subtract(from);
        switch (kind) {
            case DARK -> {
                SoulBoltEntity b = new SoulBoltEntity(mob.level(), mob, dir.x, dir.y, dir.z);
                b.setPos(from.x, from.y, from.z);
                mob.level().addFreshEntity(b);
                mob.playSound(SoundEvents.SOUL_ESCAPE, 1F, 0.6F);
            }
            case KI -> {
                KiBlastEntity b = new KiBlastEntity(mob.level(), mob, dir.x, dir.y, dir.z);
                b.setPos(from.x, from.y, from.z);
                mob.level().addFreshEntity(b);
                mob.playSound(SoundEvents.BLAZE_SHOOT, 0.7F, 1.6F);
            }
            default -> {
                boolean bullet = kind == Kind.BULLET;
                ItemStack look = new ItemStack(kind == Kind.BULLET ? net.minecraft.world.item.Items.IRON_NUGGET
                        : kind == Kind.PHONE ? com.xenowarriors.item.ModItems.PHONE.get() : com.xenowarriors.item.ModItems.BRIEFCASE.get());
                NpcThrownEntity p = new NpcThrownEntity(mob.level(), mob, look, bullet ? 4F : 3F, bullet);
                p.setPos(from.x, from.y, from.z);
                double horiz = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
                p.shoot(dir.x, dir.y + (bullet ? 0 : horiz * 0.15), dir.z, bullet ? 2.6F : 1.2F, bullet ? 1.5F : 4F);
                mob.level().addFreshEntity(p);
                mob.playSound(bullet ? SoundEvents.CROSSBOW_SHOOT : SoundEvents.SNOWBALL_THROW, 1F, bullet ? 1.8F : 0.8F);
            }
        }
        mob.swing(InteractionHand.MAIN_HAND);
        com.xenowarriors.compat.EfNpcHooks.throwAnimation(mob);
    }
}

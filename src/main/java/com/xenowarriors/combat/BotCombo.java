package com.xenowarriors.combat;

import com.xenowarriors.network.ComboSwingPacket;
import com.xenowarriors.network.ModNetwork;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Function;

/**
 * Party bots fight with the same combos as players: the same strings for what they hold, the same timing (wind-up,
 * strike, cancel point), the same finishers. They throw a heavy now and then, and catch their breath between strings.
 */
public class BotCombo extends MeleeAttackGoal {
    private final PathfinderMob mob;
    private final Function<PathfinderMob, com.xenowarriors.character.CharacterType> character;
    private ComboStyle style;
    private int hit = -1, start, dur, rest;
    private boolean heavy, struck;
    /** Rhalix's Shadow Rush: dashing in on the target through the opening hit's wind-up. */
    private boolean rushing;

    public BotCombo(PathfinderMob mob, double speed, Function<PathfinderMob, com.xenowarriors.character.CharacterType> character) {
        super(mob, speed, true);
        this.mob = mob;
        this.character = character;
    }

    @Override
    public void stop() {
        super.stop();
        hit = -1;
    }

    @Override
    protected void checkAndPerformAttack(LivingEntity target, double distSqr) {
        int now = mob.tickCount;
        // Rhalix's Kishi Warp: knelt while it floods out; reaching them, he's warped in - straight into the knockdown slash
        if (RhalixKishiWarp.busy(mob)) { mob.getNavigation().stop(); return; }
        LivingEntity warped = RhalixKishiWarp.takeWarp(mob);
        if (warped != null && warped == target) { style = ComboStyle.RHALIX; swing(4, false, now); return; }
        if (hit >= 0) {                                                       // mid-swing
            if (rushing && !struck) rush(target);
            if (!struck && now - start >= ComboStyle.windup(dur)) {
                struck = true;
                double reach = getAttackReachSqr(target) + style.reachBonus * 3 + air(hit);
                if (distSqr <= reach && mob.hasLineOfSight(target)) ComboServer.botStrike(mob, style, hit, heavy, target);
            }
            if (now - start >= ComboStyle.cancel(dur)) {
                boolean last = heavy || !style.special(hit) && hit >= style.length() - 1;
                // Rhalix now and then follows his knockdown with the Ki Burst (pops them up) and carries on from the grab
                int nextHit = style.special(hit) ? 5 : style == ComboStyle.RHALIX && hit == 4 && mob.getRandom().nextInt(3) == 0 ? ComboStyle.BURST : hit + 1;
                if (last) { hit = -1; rest = now + 14 + mob.getRandom().nextInt(10); }        // catching their breath
                else if (distSqr <= getAttackReachSqr(target) * 1.4 + air(nextHit)) swing(nextHit, false, now);
                else { hit = -1; rest = now + 6; }
            }
            return;
        }
        if (now < rest) return;
        ComboStyle next = ComboStyle.of(mob, mob.getMainHandItem(), character.apply(mob));
        // ...and sends his Kishi along the ground at someone 5-14 blocks off, now and then
        if (next == ComboStyle.RHALIX && distSqr >= 5.0 * 5.0 && distSqr <= 14.0 * 14.0 && target.onGround() && mob.onGround()
                && mob.getRandom().nextInt(30) == 0 && mob.hasLineOfSight(target) && RhalixKishiWarp.ready(mob)
                && RhalixKishiWarp.start(mob, target)) { mob.getNavigation().stop(); return; }
        // Rhalix opens his string with a dash in on them from a few blocks out (Shadow Rush)
        boolean rush = next == ComboStyle.RHALIX && mob.onGround() && distSqr > getAttackReachSqr(target)
                && distSqr >= 2.0 * 2.0 && distSqr <= 5.0 * 5.0 && mob.hasLineOfSight(target) && Math.abs(target.getY() - mob.getY()) < 1.5;
        if (!rush && distSqr > getAttackReachSqr(target)) return;
        style = next;
        if (style == ComboStyle.MIKIKO) style = ComboStyle.FIST;            // (his damage table is for players)
        if (style == null) style = ComboStyle.FIST;
        swing(0, !rush && mob.getRandom().nextInt(6) == 0, now);              // now and then they open with a heavy
        rushing = rush;
        if (rush) { mob.getNavigation().stop(); rush(target); }
    }

    /** Rhalix's last hits follow them up into the air (lifted, launched): that much more reach, squared. */
    private double air(int h) {
        return style != ComboStyle.RHALIX || heavy ? 0.0 : h >= 6 ? 5.0 : h == 5 ? 2.0 : 0.0;
    }

    /** One tick of the dash in: fast and low, stopping just short of them. */
    private void rush(LivingEntity target) {
        Vec3 to = target.position().subtract(mob.position()).multiply(1, 0, 1);
        double gap = to.length() - (mob.getBbWidth() + target.getBbWidth()) / 2.0;
        if (gap < 0.45 || to.lengthSqr() < 1.0E-4) { rushing = false; return; }
        double v = Math.min(1.0, (gap - 0.35) * 0.6);
        Vec3 d = to.normalize();
        mob.setDeltaMovement(d.x * v, mob.getDeltaMovement().y, d.z * v);
        mob.setYRot((float) (Math.atan2(-d.x, d.z) * 180 / Math.PI)); mob.yBodyRot = mob.getYRot();
        if (mob.level() instanceof net.minecraft.server.level.ServerLevel l) {          // dust kicked up off the skid
            var below = l.getBlockState(mob.blockPosition().below());
            if (!below.isAir()) l.sendParticles(new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK, below),
                    mob.getX() - d.x * 0.4, mob.getY() + 0.05, mob.getZ() - d.z * 0.4, 3, 0.15, 0.02, 0.15, 0.1);
        }
    }

    private void swing(int h, boolean hv, int now) {
        hit = h; heavy = hv; start = now; struck = false; rushing = false;
        dur = style.duration(h, hv);
        mob.swing(InteractionHand.MAIN_HAND);
        if (style == ComboStyle.RHALIX && !hv && (h == 1 || h == 5) && mob.onGround() && mob.getTarget() != null) {   // stepping in: the lunge, the follow-up
            Vec3 to = mob.getTarget().position().subtract(mob.position()).multiply(1, 0, 1);
            if (to.length() > 1.3) { to = to.normalize().scale(h == 5 ? 0.34 : 0.26); mob.setDeltaMovement(to.x, mob.getDeltaMovement().y, to.z); }
        }
        ModNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> mob), new ComboSwingPacket(mob.getId(), style.ordinal(), h, hv));
    }
}

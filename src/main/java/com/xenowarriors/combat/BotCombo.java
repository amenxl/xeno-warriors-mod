package com.xenowarriors.combat;

import com.xenowarriors.network.ComboSwingPacket;
import com.xenowarriors.network.ModNetwork;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
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
        if (hit >= 0) {                                                       // mid-swing
            if (!struck && now - start >= ComboStyle.windup(dur)) {
                struck = true;
                double reach = getAttackReachSqr(target) + style.reachBonus * 3;
                if (distSqr <= reach && mob.hasLineOfSight(target)) ComboServer.botStrike(mob, style, hit, heavy, target);
            }
            if (now - start >= ComboStyle.cancel(dur)) {
                boolean last = heavy || hit >= style.length() - 1;
                if (last) { hit = -1; rest = now + 14 + mob.getRandom().nextInt(10); }        // catching their breath
                else if (distSqr <= getAttackReachSqr(target) * 1.4) swing(hit + 1, false, now);
                else { hit = -1; rest = now + 6; }
            }
            return;
        }
        if (now < rest || distSqr > getAttackReachSqr(target)) return;
        style = ComboStyle.of(mob, mob.getMainHandItem(), character.apply(mob));
        if (style == ComboStyle.MIKIKO) style = ComboStyle.FIST;            // (his damage table is for players)
        if (style == null) style = ComboStyle.FIST;
        swing(0, mob.getRandom().nextInt(6) == 0, now);                       // now and then they open with a heavy
    }

    private void swing(int h, boolean hv, int now) {
        hit = h; heavy = hv; start = now; struck = false;
        dur = style.duration(h, hv);
        mob.swing(InteractionHand.MAIN_HAND);
        ModNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> mob), new ComboSwingPacket(mob.getId(), style.ordinal(), h, hv));
    }
}

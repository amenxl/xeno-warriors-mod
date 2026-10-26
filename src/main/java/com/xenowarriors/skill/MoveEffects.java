package com.xenowarriors.skill;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.skill.mikiko.ManifestGreatnessSkill;
import com.xenowarriors.skill.mikiko.TrueEmpathySkill;
import com.xenowarriors.skill.rhalix.KaReleaseSkill;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

/** Lasting parts of the newer skills: Manifest Greatness glow + Karma regen, True Empathy's read, KA Release immunity. */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MoveEffects {
    private MoveEffects() {}

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p)) return;
        long until = p.getPersistentData().getLong(ManifestGreatnessSkill.UNTIL);
        if (until >= p.level().getGameTime()) {
            if (p.tickCount % 3 == 0)
                p.serverLevel().sendParticles(new DustParticleOptions(new Vector3f(1F, .12F, .08F), 1.3F), p.getX(), p.getY() + 1, p.getZ(), 4, 0.35, 0.7, 0.35, 0);
            if (p.tickCount % 20 == 0) com.xenowarriors.karma.Karma.add(p, 2F);
        }
        if (KaReleaseSkill.free(p) && com.xenowarriors.skill.Rooting.isRooted(p)) p.getPersistentData().remove(com.xenowarriors.skill.Rooting.ROOTED_UNTIL);
    }

    /** KA Release: no harmful effects stick for its 4 seconds. */
    @SubscribeEvent
    public static void onEffect(MobEffectEvent.Applicable event) {
        if (KaReleaseSkill.free(event.getEntity()) && event.getEffectInstance().getEffect().getCategory() == MobEffectCategory.HARMFUL)
            event.setResult(Event.Result.DENY);
    }

    /** True Empathy's read: the next hit inside the window is dodged and sent back harder. */
    @SubscribeEvent
    public static void onAttacked(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        if (p.getPersistentData().getLong(TrueEmpathySkill.READ_UNTIL) < p.level().getGameTime()) return;
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker) || attacker == p) return;
        p.getPersistentData().remove(TrueEmpathySkill.READ_UNTIL);
        event.setCanceled(true);
        Vec3 behind = attacker.position().add(attacker.getLookAngle().multiply(-1.4, 0, -1.4));
        p.teleportTo(behind.x, attacker.getY(), behind.z);
        p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, attacker.getEyePosition());
        attacker.hurt(p.damageSources().playerAttack(p), Math.max(4F, event.getAmount() * 1.5F));
        attacker.push(attacker.getLookAngle().x * -0.0 + (attacker.getX() - p.getX()) * 0.4, 0.3, (attacker.getZ() - p.getZ()) * 0.4);
        attacker.hurtMarked = true;
        p.serverLevel().sendParticles(ParticleTypes.SWEEP_ATTACK, attacker.getX(), attacker.getY() + 1, attacker.getZ(), 1, 0, 0, 0, 0);
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1F, 1.3F);
        p.displayClientMessage(net.minecraft.network.chat.Component.literal("Read it!").withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE), true);
    }
}

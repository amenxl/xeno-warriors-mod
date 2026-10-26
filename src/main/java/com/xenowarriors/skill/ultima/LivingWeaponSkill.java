package com.xenowarriors.skill.ultima;

import com.xenowarriors.skill.Skill;
import com.xenowarriors.ultima.UltimaOutput;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/** Living Weapon: every part of his body becomes a weapon and a shield - his hits sweep everyone around and hit harder (UltimaCombat). */
public class LivingWeaponSkill implements Skill {
    public static final String UNTIL = "xw_ult_weapon_until";

    @Override public String getId() { return "living_weapon"; }
    @Override public int getCooldownTicks() { return 25 * 20; }
    @Override public int getKarmaCost() { return 30; }

    @Override
    public boolean use(ServerPlayer p) {
        int t = UltimaOutput.tier(p);
        p.getPersistentData().putLong(UNTIL, p.level().getGameTime() + 200);
        if (t >= 2) p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200, 0, false, false, true));
        p.displayClientMessage(Component.literal("Living Weapon").withStyle(ChatFormatting.GREEN), true);
        p.serverLevel().sendParticles(new DustParticleOptions(UltimaBlasts.GREEN, 1.2F), p.getX(), p.getY() + 1, p.getZ(), 40, 0.5, 0.8, 0.5, 0);
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.ARMOR_EQUIP_NETHERITE, SoundSource.PLAYERS, 1F, 1.3F);
        com.xenowarriors.anim.Poses.play(p, com.xenowarriors.anim.Poses.KA_BREAK, 14);
        return true;
    }
}

package com.xenowarriors.sky;

import com.xenowarriors.XenoWarriors;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Server side of the Corrupted Armor: the chestplate's protection from souls, and the boots' dark jump. */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CorruptedArmorEffects {
    public static final int JUMP_COOLDOWN = 120;
    private static final java.util.Map<java.util.UUID, Long> READY = new java.util.HashMap<>();

    private CorruptedArmorEffects() {}

    /** Chestplate: 10% less damage from corrupted souls and anything a soul has taken. */
    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Player p) || !CorruptedArmorItem.wearing(p, EquipmentSlot.CHEST)) return;
        if (event.getSource().getEntity() instanceof LivingEntity src && com.xenowarriors.karma.KarmaSensing.corrupted(src)
                && !(src instanceof Player)) {
            event.setAmount(event.getAmount() * 0.9F);
        }
        if (event.getSource().getDirectEntity() instanceof com.xenowarriors.corrupted.SoulBoltEntity) event.setAmount(event.getAmount() * 0.9F);
    }

    /** Boots: the extra jump bursts with darkness - it hurts and blinds enemies close by. */
    public static void darkJump(ServerPlayer p) {
        if (!CorruptedArmorItem.wearing(p, EquipmentSlot.FEET)) return;
        long now = p.level().getGameTime();
        if (now < READY.getOrDefault(p.getUUID(), 0L)) return;
        READY.put(p.getUUID(), now + JUMP_COOLDOWN);
        p.fallDistance = 0;
        ServerLevel level = p.serverLevel();
        level.sendParticles(com.xenowarriors.corrupted.CorruptedFx.BLACK, p.getX(), p.getY() + 0.2, p.getZ(), 40, 0.9, 0.2, 0.9, 0.06);
        level.sendParticles(ParticleTypes.WITCH, p.getX(), p.getY() + 0.3, p.getZ(), 20, 0.8, 0.2, 0.8, 0.02);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 0.5F, 1.6F);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(3.5),
                e -> e != p && e.isAlive() && (e instanceof Enemy || com.xenowarriors.karma.KarmaSensing.corrupted(e)))) {
            e.hurt(p.damageSources().playerAttack(p), 5F);
            e.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0));
        }
    }
}

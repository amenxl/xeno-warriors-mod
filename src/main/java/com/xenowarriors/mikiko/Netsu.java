package com.xenowarriors.mikiko;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.damage.ModDamageTypes;
import com.xenowarriors.network.ModNetwork;
import com.xenowarriors.network.NetsuPacket;
import com.xenowarriors.skill.SkillTargeting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Mikiko skill 3 - Netsu: he sets his whole body on fire. While it burns he's incredibly fast,
 * anyone who touches him (or gets hit by him) catches fire, and it slowly hurts him too.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Netsu {
    public static final int DURATION = 10 * 20;
    public static final float SPEED_BONUS = 0.9F;          // +90% movement speed
    public static final float SELF_DAMAGE = 0.5F;          // every second
    public static final float TOUCH_DAMAGE = 2F;
    private static final UUID SPEED_ID = UUID.fromString("2b9f6a3e-4c1d-4f7b-8e2a-9d3c5b1a7e44");
    private static final Map<UUID, Long> END = new HashMap<>();
    private static final Map<Integer, Long> TOUCH_COOLDOWN = new HashMap<>();

    private Netsu() {}

    public static boolean active(Player p) {
        Long end = END.get(p.getUUID());
        return end != null && p.level().getGameTime() < end;
    }

    public static void start(ServerPlayer p) {
        END.put(p.getUUID(), p.level().getGameTime() + DURATION);
        AttributeInstance speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(SPEED_ID);
            speed.addTransientModifier(new AttributeModifier(SPEED_ID, "Netsu", SPEED_BONUS, AttributeModifier.Operation.MULTIPLY_BASE));
        }
        ModNetwork.sendToTrackingAndSelf(new NetsuPacket(p.getId(), DURATION), p);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.2F, 0.7F);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1F, 0.6F);
    }

    public static void stop(ServerPlayer p) {
        END.remove(p.getUUID());
        AttributeInstance speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.removeModifier(SPEED_ID);
        p.clearFire();
        ModNetwork.sendToTrackingAndSelf(new NetsuPacket(p.getId(), 0), p);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.8F, 1F);
        p.displayClientMessage(Component.translatable("message.xenowarriors.netsu.end"), true);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p)) return;
        if (!END.containsKey(p.getUUID())) return;
        if (!active(p) || !p.isAlive()) {
            stop(p);
            return;
        }
        long now = p.level().getGameTime();
        // burning (visually) without normal fire damage - Netsu's own cost is the slow self-damage below
        p.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 10, 0, false, false, false));
        p.setRemainingFireTicks(Math.max(p.getRemainingFireTicks(), 10));
        if (now % 20 == 0) p.hurt(ModDamageTypes.source(p.level(), ModDamageTypes.NETSU, null, null), SELF_DAMAGE);
        // anyone touching him catches fire
        for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(0.35),
                e -> SkillTargeting.canTarget(p, e))) {
            long ready = TOUCH_COOLDOWN.getOrDefault(e.getId(), 0L);
            if (now < ready) continue;
            TOUCH_COOLDOWN.put(e.getId(), now + 15);
            e.setSecondsOnFire(5);
            e.hurt(ModDamageTypes.source(p.level(), ModDamageTypes.NETSU, p, p), TOUCH_DAMAGE);
        }
        if (now % 100 == 0) TOUCH_COOLDOWN.values().removeIf(t -> t < now);
    }

    /** His punches and slashes set things on fire while Netsu burns. */
    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer p && event.getSource().getDirectEntity() == p && active(p)) {
            event.getEntity().setSecondsOnFire(5);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && END.containsKey(p.getUUID())) stop(p);
    }
}

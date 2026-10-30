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
    /** Each Defense level keeps the fire going 0.3 seconds longer (25 seconds at Defense 50). */
    public static final int TICKS_PER_DEFENSE = 6;
    /** At this Defense level Netsu no longer drains health at all. */
    public static final int NO_DRAIN_DEFENSE = 100;

    public static int duration(ServerPlayer p) {
        return Math.min(60 * 20, DURATION + com.xenowarriors.stats.Stats.level(p, com.xenowarriors.stats.Stat.DEFENSE) * TICKS_PER_DEFENSE);   // up to a minute
    }
    public static final float SPEED_BONUS = 0.9F;          // +90% movement speed
    public static final float SELF_DAMAGE = 0.5F;          // health drained per second
    public static final float TOUCH_DAMAGE = 2F;
    private static final UUID SPEED_ID = UUID.fromString("2b9f6a3e-4c1d-4f7b-8e2a-9d3c5b1a7e44");
    /** Netsu also burns hotter in a fight: more damage, faster swings, tougher skin. */
    public static final float ATTACK_BONUS = 0.4F, ATTACK_SPEED_BONUS = 0.25F, ARMOR_BONUS = 6F, DAMAGE_TAKEN = 0.75F;
    private static final UUID[] BOOST_IDS = {
            UUID.fromString("2b9f6a3e-4c1d-4f7b-8e2a-9d3c5b1a7e45"),
            UUID.fromString("2b9f6a3e-4c1d-4f7b-8e2a-9d3c5b1a7e46"),
            UUID.fromString("2b9f6a3e-4c1d-4f7b-8e2a-9d3c5b1a7e47"),
            UUID.fromString("2b9f6a3e-4c1d-4f7b-8e2a-9d3c5b1a7e48")};

    private static void boost(ServerPlayer p, boolean on) {
        Object[][] mods = {
                {Attributes.ATTACK_DAMAGE, ATTACK_BONUS, AttributeModifier.Operation.MULTIPLY_BASE},
                {Attributes.ATTACK_SPEED, ATTACK_SPEED_BONUS, AttributeModifier.Operation.MULTIPLY_BASE},
                {Attributes.ARMOR, ARMOR_BONUS, AttributeModifier.Operation.ADDITION},
                {Attributes.KNOCKBACK_RESISTANCE, 0.4F, AttributeModifier.Operation.ADDITION}};
        for (int i = 0; i < mods.length; i++) {
            AttributeInstance inst = p.getAttribute((net.minecraft.world.entity.ai.attributes.Attribute) mods[i][0]);
            if (inst == null) continue;
            inst.removeModifier(BOOST_IDS[i]);
            if (on) inst.addTransientModifier(new AttributeModifier(BOOST_IDS[i], "Netsu", (float) mods[i][1],
                    (AttributeModifier.Operation) mods[i][2]));
        }
    }
    private static final Map<UUID, Long> END = new HashMap<>();
    private static final Map<Integer, Long> TOUCH_COOLDOWN = new HashMap<>();

    private Netsu() {}

    public static boolean active(Player p) {
        Long end = END.get(p.getUUID());
        return end != null && p.level().getGameTime() < end;
    }

    public static void start(ServerPlayer p) {
        int duration = duration(p);
        END.put(p.getUUID(), p.level().getGameTime() + duration);
        AttributeInstance speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(SPEED_ID);
            speed.addTransientModifier(new AttributeModifier(SPEED_ID, "Netsu", SPEED_BONUS, AttributeModifier.Operation.MULTIPLY_BASE));
        }
        boost(p, true);
        p.displayClientMessage(Component.literal("Netsu for " + duration / 20 + "s! +40% attack, +25% attack speed, +6 armor, 25% less damage taken")
                .withStyle(net.minecraft.ChatFormatting.GOLD), true);
        ModNetwork.sendToTrackingAndSelf(new NetsuPacket(p.getId(), duration), p);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.2F, 0.7F);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1F, 0.6F);
    }

    public static void stop(ServerPlayer p) {
        END.remove(p.getUUID());
        AttributeInstance speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.removeModifier(SPEED_ID);
        boost(p, false);
        p.clearFire();
        ModNetwork.sendToTrackingAndSelf(new NetsuPacket(p.getId(), 0), p);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.8F, 1F);
        p.displayClientMessage(Component.translatable("message.xenowarriors.netsu.end"), true);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p)) return;
        if (!END.containsKey(p.getUUID())) return;
        if (!active(p) || !p.isAlive() || com.xenowarriors.character.ModCapabilities.getType(p) != com.xenowarriors.character.CharacterType.MIKIKO) {
            stop(p);
            return;
        }
        long now = p.level().getGameTime();
        // burning (visually) without normal fire damage - Netsu's own cost is the slow self-damage below
        p.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 10, 0, false, false, false));
        p.setRemainingFireTicks(Math.max(p.getRemainingFireTicks(), 10));
        // the fire slowly eats at his health (no hits, no flinching) - it never takes his last half-heart
        // a tougher body handles the heat: the drain shrinks with Defense and stops entirely at Defense 100
        float drain = SELF_DAMAGE / 20F * Math.max(0F, 1F - com.xenowarriors.stats.Stats.level(p, com.xenowarriors.stats.Stat.DEFENSE) / (float) NO_DRAIN_DEFENSE);
        if (drain > 0F && !p.isCreative() && p.getHealth() > 1F) p.setHealth(Math.max(1F, p.getHealth() - drain));
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
        // and it hardens him: 25% less damage from anything but Netsu's own burn
        if (event.getEntity() instanceof ServerPlayer self && active(self) && !event.getSource().is(ModDamageTypes.NETSU)) {
            event.setAmount(event.getAmount() * DAMAGE_TAKEN);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && END.containsKey(p.getUUID())) stop(p);
    }
}

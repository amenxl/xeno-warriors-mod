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
    private static final java.util.Set<UUID> RAIN_WARNED = new java.util.HashSet<>();
    /** Extra ticks of Netsu the rain burns off each tick (3x as fast in total). */
    public static final int RAIN_EXTRA = 2;

    /** Rain falling on him (not under cover, not under the Heku-umbrella). */
    public static boolean rainedOn(Player p) {
        return p.level().isRainingAt(p.blockPosition().above()) && !p.isUnderWater() && !com.xenowarriors.forms.Umbrella.covers(p);
    }

    /** Ticks of Netsu left (0 when it's out). */
    public static long remaining(Player p) {
        Long end = END.get(p.getUUID());
        return end == null ? 0 : Math.max(0, end - p.level().getGameTime());
    }
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
        RAIN_WARNED.remove(p.getUUID());
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
        // rain puts it out three times as fast - unless he's under the Heku-umbrella
        if (rainedOn(p)) {
            END.put(p.getUUID(), END.get(p.getUUID()) - RAIN_EXTRA);
            if (now % 4 == 0) ((net.minecraft.server.level.ServerLevel) p.level()).sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD,
                    p.getX(), p.getY() + 1.6, p.getZ(), 2, 0.3, 0.3, 0.3, 0.01);
            if (now % 20 == 0) {
                p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.3F, 1.8F);
                ModNetwork.sendToTrackingAndSelf(new NetsuPacket(p.getId(), (int) Math.max(1, remaining(p))), p);
                if (!RAIN_WARNED.contains(p.getUUID())) {
                    RAIN_WARNED.add(p.getUUID());
                    p.displayClientMessage(Component.literal("The rain is putting out your Netsu! Get under cover"
                            + (com.xenowarriors.mikiko.BladeLevel.level(p) >= com.xenowarriors.forms.Form.UMBRELLA.weaponLevel ? " or open the Heku-umbrella" : ""))
                            .withStyle(net.minecraft.ChatFormatting.AQUA), true);
                }
            }
            if (!active(p)) { stop(p); return; }
        }
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

    /** Someone who comes into view of him while he's burning sees the flames too (it was only sent when it started). */
    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof ServerPlayer target && event.getEntity() instanceof ServerPlayer viewer && active(target))
            ModNetwork.sendToPlayer(new NetsuPacket(target.getId(), (int) Math.max(1, remaining(target))), viewer);
    }
}

package com.xenowarriors.mikiko;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.character.ModCapabilities;
import com.xenowarriors.karma.Karma;
import com.xenowarriors.network.FoxKingPacket;
import com.xenowarriors.network.ModNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Mikiko's Fox King mode (default N): his hair floods red and grows long, one dread turns white, and the Fox King
 * mask appears on the side of his head. While it lasts: +50% max Karma, faster, hits harder and jumps higher.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FoxKing {
    public static final int DURATION = 90 * 20, COOLDOWN = 120 * 20;
    public static final float COST = 30F, KARMA_MAX_BONUS = 0.5F, SPEED = 0.3F, ATTACK = 0.35F;
    private static final UUID SPEED_ID = UUID.fromString("7d1f0c2a-5b6e-4f3a-9c8d-2e1f0a9b8c71"),
            ATTACK_ID = UUID.fromString("7d1f0c2a-5b6e-4f3a-9c8d-2e1f0a9b8c72");
    private static final Map<UUID, Long> END = new HashMap<>(), READY = new HashMap<>();

    private FoxKing() {}

    public static boolean active(Player p) {
        Long end = END.get(p.getUUID());
        return end != null && p.level().getGameTime() < end;
    }

    public static void toggle(ServerPlayer p) {
        if (ModCapabilities.getType(p) != CharacterType.MIKIKO) return;
        if (END.containsKey(p.getUUID())) {
            stop(p, true);
            return;
        }
        long now = p.level().getGameTime();
        long ready = READY.getOrDefault(p.getUUID(), 0L);
        if (now < ready) {
            p.displayClientMessage(Component.literal("Fox King needs to recover (" + (ready - now + 19) / 20 + "s)").withStyle(ChatFormatting.RED), true);
            return;
        }
        if (!Karma.trySpend(p, COST)) return;
        END.put(p.getUUID(), now + DURATION);
        p.getCapability(ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> {
            d.karmaMax = Karma.MAX * (1 + KARMA_MAX_BONUS);
            d.setKarma(d.getKarma() + Karma.MAX * KARMA_MAX_BONUS);   // the extra half comes in full
            Karma.sync(p, d);
        });
        set(p.getAttribute(Attributes.MOVEMENT_SPEED), SPEED_ID, SPEED);
        set(p.getAttribute(Attributes.ATTACK_DAMAGE), ATTACK_ID, ATTACK);
        ModNetwork.sendToTrackingAndSelf(new FoxKingPacket(p.getId(), true), p);
        var level = p.serverLevel();
        level.sendParticles(new DustParticleOptions(new Vector3f(0.95F, 0.12F, 0.1F), 1.6F), p.getX(), p.getY() + 1, p.getZ(), 50, 0.6, 1, 0.6, 0);
        level.sendParticles(new DustParticleOptions(new Vector3f(1F, 1F, 1F), 1.2F), p.getX(), p.getY() + 1.8, p.getZ(), 20, 0.4, 0.4, 0.4, 0);
        level.sendParticles(ParticleTypes.FLAME, p.getX(), p.getY() + 0.2, p.getZ(), 30, 0.8, 0.1, 0.8, 0.05);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.FOX_SCREECH, SoundSource.PLAYERS, 1.2F, 0.7F);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1F, 0.6F);
        p.displayClientMessage(Component.literal("Fox King! +50% max Karma, +30% speed, +35% attack, higher jumps")
                .withStyle(ChatFormatting.GOLD), true);
    }

    private static void set(AttributeInstance inst, UUID id, float amount) {
        if (inst == null) return;
        inst.removeModifier(id);
        if (amount != 0) inst.addTransientModifier(new AttributeModifier(id, "Fox King", amount, AttributeModifier.Operation.MULTIPLY_BASE));
    }

    public static void stop(ServerPlayer p, boolean byChoice) {
        if (END.remove(p.getUUID()) == null) return;
        READY.put(p.getUUID(), p.level().getGameTime() + COOLDOWN);
        set(p.getAttribute(Attributes.MOVEMENT_SPEED), SPEED_ID, 0);
        set(p.getAttribute(Attributes.ATTACK_DAMAGE), ATTACK_ID, 0);
        p.removeEffect(MobEffects.JUMP);
        p.getCapability(ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> {
            d.karmaMax = Karma.MAX;
            d.setKarma(d.getKarma());          // trims anything above the normal cap
            Karma.sync(p, d);
        });
        ModNetwork.sendToTrackingAndSelf(new FoxKingPacket(p.getId(), false), p);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.8F, 1.2F);
        if (!byChoice) p.displayClientMessage(Component.literal("Fox King mode wears off.").withStyle(ChatFormatting.GRAY), true);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p) || !END.containsKey(p.getUUID())) return;
        if (!active(p) || !p.isAlive() || ModCapabilities.getType(p) != CharacterType.MIKIKO) {
            stop(p, false);
            return;
        }
        if (p.tickCount % 20 == 0) {
            p.addEffect(new MobEffectInstance(MobEffects.JUMP, 40, 2, false, false, false));   // jump about 2 blocks higher
            long left = (END.get(p.getUUID()) - p.level().getGameTime()) / 20;
            if (left <= 10) p.displayClientMessage(Component.literal("Fox King: " + left + "s").withStyle(ChatFormatting.GOLD), true);
        }
        if (p.tickCount % 4 == 0) {
            p.serverLevel().sendParticles(new DustParticleOptions(new Vector3f(0.95F, 0.15F, 0.1F), 0.9F),
                    p.getX(), p.getY() + 0.1, p.getZ(), 2, 0.3, 0.05, 0.3, 0);
        }
    }

    /** Falling is nothing to a fox king. */
    @SubscribeEvent
    public static void onFall(net.minecraftforge.event.entity.living.LivingFallEvent event) {
        if (event.getEntity() instanceof Player p && active(p)) event.setDamageMultiplier(0F);
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof Player target && active(target) && event.getEntity() instanceof ServerPlayer viewer) {
            ModNetwork.sendToPlayer(new FoxKingPacket(target.getId(), true), viewer);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) stop(p, true);
    }
}

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
 * <p>
 * Fox King mastery (0-100, like a DragonMineZ form's mastery): grows while he's in the form, with every hit he lands
 * in it and every kill. The form lasts longer the more he's mastered it (90 s at 0, 180 s at 50, 270 s at 100), and
 * milestones cut its Karma cost (25, 75) and recovery time (50, 75); at 100 it hits harder still.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FoxKing {
    public static final int DURATION = 90 * 20, COOLDOWN = 120 * 20;
    public static final float COST = 30F, KARMA_MAX_BONUS = 0.5F, SPEED = 0.3F, ATTACK = 0.35F;
    private static final UUID SPEED_ID = UUID.fromString("7d1f0c2a-5b6e-4f3a-9c8d-2e1f0a9b8c71"),
            ATTACK_ID = UUID.fromString("7d1f0c2a-5b6e-4f3a-9c8d-2e1f0a9b8c72");
    private static final Map<UUID, Long> END = new HashMap<>(), READY = new HashMap<>(), LAST_HIT = new HashMap<>();
    private static final Map<UUID, Integer> TOTAL = new HashMap<>();

    // ---- mastery
    public static final float MASTERY_MAX = 100F, GAIN_PER_SECOND = 0.05F, GAIN_PER_HIT = 0.1F, GAIN_PER_KILL = 0.5F, MASTERED_ATTACK = 0.45F;

    public static float mastery(Player p) {
        return p.getCapability(ModCapabilities.PLAYER_CHARACTER).map(d -> d.foxMastery).orElse(0F);
    }

    /** How long the form lasts at a mastery: 90 s at 0, up to three times that (270 s) at 100. */
    public static int duration(float m) { return Math.round(DURATION * (1F + 2F * Math.max(0F, Math.min(MASTERY_MAX, m)) / MASTERY_MAX)); }
    /** Karma to transform: 30, 20 from mastery 25, 10 from 75. */
    public static float cost(float m) { return m >= 75 ? 10F : m >= 25 ? 20F : COST; }
    /** Recovery after it ends: 120 s, 90 s from mastery 50, 60 s from 75. */
    public static int cooldown(float m) { return m >= 75 ? 60 * 20 : m >= 50 ? 90 * 20 : COOLDOWN; }
    /** Attack bonus: +35%, +45% once fully mastered. */
    public static float attack(float m) { return m >= MASTERY_MAX ? MASTERED_ATTACK : ATTACK; }

    public static void setMastery(ServerPlayer p, float m) {
        p.getCapability(ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> d.foxMastery = Math.max(0F, Math.min(MASTERY_MAX, m)));
        sync(p);
    }

    /** Adds mastery (in the form only), with a message at every 10 and at the milestones. */
    public static void gain(ServerPlayer p, float amount) {
        if (ModCapabilities.getType(p) != CharacterType.MIKIKO) return;
        p.getCapability(ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> {
            float before = d.foxMastery;
            if (before >= MASTERY_MAX) return;
            d.foxMastery = Math.min(MASTERY_MAX, before + amount);
            int b = (int) before, a = (int) d.foxMastery;
            if (a / 10 > b / 10 || (a >= 25 && b < 25) || (a >= 75 && b < 75)) {
                String perk = a >= 100 ? " - MASTERED: +45% attack" : a >= 75 && b < 75 ? " - costs 10 Karma, 60 s recovery"
                        : a >= 50 && b < 50 ? " - 90 s recovery" : a >= 25 && b < 25 ? " - costs 20 Karma" : "";
                p.displayClientMessage(Component.literal("Fox King mastery " + a + "/100 (lasts " + duration(a) / 20 + " s)" + perk).withStyle(ChatFormatting.GOLD), false);
                if (a >= 100) set(p.getAttribute(Attributes.ATTACK_DAMAGE), ATTACK_ID, MASTERED_ATTACK);
            }
        });
    }

    /** Tells the client the mastery and, while in the form, how long is left. */
    public static void sync(ServerPlayer p) {
        Long end = END.get(p.getUUID());
        int left = end == null ? 0 : (int) Math.max(0, end - p.level().getGameTime());
        ModNetwork.sendToPlayer(new com.xenowarriors.network.FoxMasteryPacket(mastery(p), left, TOTAL.getOrDefault(p.getUUID(), duration(mastery(p)))), p);
    }

    /** Ticks of Fox King left (0 when not in it). */
    public static int left(Player p) {
        Long end = END.get(p.getUUID());
        return end == null ? 0 : (int) Math.max(0, end - p.level().getGameTime());
    }

    private FoxKing() {}

    public static boolean active(Player p) {
        Long end = END.get(p.getUUID());
        return end != null && p.level().getGameTime() < end;
    }

    public static void toggle(ServerPlayer p) {
        if (ModCapabilities.getType(p) != CharacterType.MIKIKO) return;
        long now = p.level().getGameTime();
        if (END.containsKey(p.getUUID())) {                 // can't transform back early: it ends on its own
            p.displayClientMessage(Component.literal("Fox King mode can't be stopped - wait for it to end (" + (END.get(p.getUUID()) - now + 19) / 20 + "s)").withStyle(ChatFormatting.GOLD), true);
            return;
        }
        long ready = READY.getOrDefault(p.getUUID(), 0L);
        if (now < ready) {
            p.displayClientMessage(Component.literal("Fox King needs to recover (" + (ready - now + 19) / 20 + "s)").withStyle(ChatFormatting.RED), true);
            return;
        }
        float m = mastery(p);
        if (!Karma.trySpend(p, cost(m))) return;
        int dur = duration(m);
        END.put(p.getUUID(), now + dur);
        TOTAL.put(p.getUUID(), dur);
        p.getCapability(ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> {
            d.karmaMax = Karma.MAX * (1 + KARMA_MAX_BONUS);
            d.setKarma(d.getKarma() + Karma.MAX * KARMA_MAX_BONUS);   // the extra half comes in full
            Karma.sync(p, d);
        });
        set(p.getAttribute(Attributes.MOVEMENT_SPEED), SPEED_ID, SPEED);
        set(p.getAttribute(Attributes.ATTACK_DAMAGE), ATTACK_ID, attack(m));
        ModNetwork.sendToTrackingAndSelf(new FoxKingPacket(p.getId(), true), p);
        var level = p.serverLevel();
        level.sendParticles(new DustParticleOptions(new Vector3f(0.95F, 0.12F, 0.1F), 1.6F), p.getX(), p.getY() + 1, p.getZ(), 50, 0.6, 1, 0.6, 0);
        level.sendParticles(new DustParticleOptions(new Vector3f(1F, 1F, 1F), 1.2F), p.getX(), p.getY() + 1.8, p.getZ(), 20, 0.4, 0.4, 0.4, 0);
        level.sendParticles(ParticleTypes.FLAME, p.getX(), p.getY() + 0.2, p.getZ(), 30, 0.8, 0.1, 0.8, 0.05);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.FOX_SCREECH, SoundSource.PLAYERS, 1.2F, 0.7F);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1F, 0.6F);
        p.displayClientMessage(Component.literal("Fox King! " + dur / 20 + " s (mastery " + (int) m + ") +50% max Karma, +30% speed, +" + Math.round(attack(m) * 100) + "% attack, higher jumps")
                .withStyle(ChatFormatting.GOLD), true);
        com.xenowarriors.XenoWarriors.LOGGER.info("Fox King on for {}: mastery {} -> {} ticks, cost {}, cooldown after {}", p.getGameProfile().getName(), m, dur, cost(m), cooldown(m));
        sync(p);
    }

    private static void set(AttributeInstance inst, UUID id, float amount) {
        if (inst == null) return;
        inst.removeModifier(id);
        if (amount != 0) inst.addTransientModifier(new AttributeModifier(id, "Fox King", amount, AttributeModifier.Operation.MULTIPLY_BASE));
    }

    public static void stop(ServerPlayer p, boolean byChoice) {
        if (END.remove(p.getUUID()) == null) return;
        READY.put(p.getUUID(), p.level().getGameTime() + cooldown(mastery(p)));
        TOTAL.remove(p.getUUID());
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
        sync(p);
    }

    /** Ends the recovery time (tests). */
    public static void resetCooldown(ServerPlayer p) { READY.remove(p.getUUID()); }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p)) return;
        if (!END.containsKey(p.getUUID())) {
            if (p.tickCount % 100 == 7 && ModCapabilities.getType(p) == CharacterType.MIKIKO) sync(p);    // (the stats page shows it)
            return;
        }
        if (!active(p) || !p.isAlive() || ModCapabilities.getType(p) != CharacterType.MIKIKO) {
            stop(p, false);
            return;
        }
        if (p.tickCount % 20 == 0) {
            gain(p, GAIN_PER_SECOND);
            sync(p);
            p.addEffect(new MobEffectInstance(MobEffects.JUMP, 40, 2, false, false, false));   // jump about 2 blocks higher
            long left = (END.get(p.getUUID()) - p.level().getGameTime()) / 20;
            if (left <= 10) p.displayClientMessage(Component.literal("Fox King: " + left + "s").withStyle(ChatFormatting.GOLD), true);
        }
        if (p.tickCount % 4 == 0) {
            p.serverLevel().sendParticles(new DustParticleOptions(new Vector3f(0.95F, 0.15F, 0.1F), 0.9F),
                    p.getX(), p.getY() + 0.1, p.getZ(), 2, 0.3, 0.05, 0.3, 0);
        }
    }

    /** Every hit he lands in the form (at most one every half second) and every kill adds mastery. */
    @SubscribeEvent
    public static void onHurt(net.minecraftforge.event.entity.living.LivingHurtEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer p) || event.getEntity() == p || !active(p)) return;
        long now = p.level().getGameTime();
        if (now - LAST_HIT.getOrDefault(p.getUUID(), -100L) < 10) return;
        LAST_HIT.put(p.getUUID(), now);
        gain(p, GAIN_PER_HIT);
    }

    @SubscribeEvent
    public static void onKill(net.minecraftforge.event.entity.living.LivingDeathEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer p && event.getEntity() != p && active(p)) gain(p, GAIN_PER_KILL);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) sync(p);
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

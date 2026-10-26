package com.xenowarriors.rhalix;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.character.ModCapabilities;
import com.xenowarriors.karma.Karma;
import com.xenowarriors.network.ModNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Demon's Remnants (Rhalix, Season 1, the transform key): what's left of the demon that used to live in him wakes up.
 * His eyes glow red - that's the only change you see - and he's stronger, faster and tougher. Lasts as long as Fox
 * King mode (90 s) and, like it, can't be ended early: it wears off on its own.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DemonsRemnants {
    public static final int DURATION = com.xenowarriors.mikiko.FoxKing.DURATION, COOLDOWN = com.xenowarriors.mikiko.FoxKing.COOLDOWN;
    public static final float COST = 30F;
    private static final UUID SPD = UUID.fromString("8e2a1b3c-4d5e-4f60-8172-93a4b5c6d7e1"), ATK = UUID.fromString("8e2a1b3c-4d5e-4f60-8172-93a4b5c6d7e2"),
            ARM = UUID.fromString("8e2a1b3c-4d5e-4f60-8172-93a4b5c6d7e3"), KB = UUID.fromString("8e2a1b3c-4d5e-4f60-8172-93a4b5c6d7e4");
    private static final Map<UUID, Long> END = new HashMap<>(), READY = new HashMap<>();
    private static final DustParticleOptions RED = new DustParticleOptions(new Vector3f(0.9F, 0.05F, 0.08F), 0.7F);

    private DemonsRemnants() {}

    public static boolean active(Player p) { Long e = END.get(p.getUUID()); return e != null && p.level().getGameTime() < e; }

    public static void activate(ServerPlayer p) {
        if (ModCapabilities.getType(p) != CharacterType.RHALIX) return;
        long now = p.level().getGameTime();
        if (END.containsKey(p.getUUID())) {
            p.displayClientMessage(Component.literal("Demon's Remnants can't be stopped - wait for it to fade (" + (END.get(p.getUUID()) - now + 19) / 20 + "s)").withStyle(ChatFormatting.DARK_RED), true);
            return;
        }
        long ready = READY.getOrDefault(p.getUUID(), 0L);
        if (now < ready) {
            p.displayClientMessage(Component.literal("The demon's remnants are still settling (" + (ready - now + 19) / 20 + "s)").withStyle(ChatFormatting.RED), true);
            return;
        }
        if (!Karma.trySpend(p, COST)) return;
        END.put(p.getUUID(), now + DURATION);
        mod(p.getAttribute(Attributes.MOVEMENT_SPEED), SPD, 0.25, AttributeModifier.Operation.MULTIPLY_BASE);
        mod(p.getAttribute(Attributes.ATTACK_DAMAGE), ATK, 0.45, AttributeModifier.Operation.MULTIPLY_BASE);
        mod(p.getAttribute(Attributes.ARMOR), ARM, 6, AttributeModifier.Operation.ADDITION);
        mod(p.getAttribute(Attributes.KNOCKBACK_RESISTANCE), KB, 0.5, AttributeModifier.Operation.ADDITION);
        sync(p, true);
        var s = p.serverLevel();
        s.playSound(null, p.blockPosition(), SoundEvents.WITHER_AMBIENT, SoundSource.PLAYERS, 0.7F, 0.6F);
        s.playSound(null, p.blockPosition(), SoundEvents.SOUL_ESCAPE, SoundSource.PLAYERS, 1F, 0.5F);
        Vec3 eye = p.getEyePosition();
        s.sendParticles(RED, eye.x, eye.y, eye.z, 16, 0.25, 0.1, 0.25, 0);
        com.xenowarriors.anim.Poses.play(p, com.xenowarriors.anim.Poses.KA_BREAK, 12);
        p.displayClientMessage(Component.literal("Demon's Remnants - +45% attack, +25% speed, tougher").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), true);
    }

    private static void mod(AttributeInstance a, UUID id, double amount, AttributeModifier.Operation op) {
        if (a == null) return;
        a.removeModifier(id);
        if (amount != 0) a.addTransientModifier(new AttributeModifier(id, "Demon's Remnants", amount, op));
    }

    private static void stop(ServerPlayer p) {
        if (END.remove(p.getUUID()) == null) return;
        READY.put(p.getUUID(), p.level().getGameTime() + COOLDOWN);
        for (var e : new Object[][]{{Attributes.MOVEMENT_SPEED, SPD}, {Attributes.ATTACK_DAMAGE, ATK}, {Attributes.ARMOR, ARM}, {Attributes.KNOCKBACK_RESISTANCE, KB}}) {
            AttributeInstance a = p.getAttribute((net.minecraft.world.entity.ai.attributes.Attribute) e[0]);
            if (a != null) a.removeModifier((UUID) e[1]);
        }
        sync(p, false);
        p.displayClientMessage(Component.literal("The red fades from Rhalix's eyes.").withStyle(ChatFormatting.GRAY), true);
    }

    private static void sync(ServerPlayer p, boolean on) {
        ModNetwork.sendToTrackingAndSelf(new com.xenowarriors.network.DemonStatePacket(p.getId(), on), p);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p) || !END.containsKey(p.getUUID())) return;
        if (!active(p) || !p.isAlive() || ModCapabilities.getType(p) != CharacterType.RHALIX) { stop(p); return; }
        if (p.tickCount % 20 == 0) {
            long left = (END.get(p.getUUID()) - p.level().getGameTime()) / 20;
            if (left <= 10) p.displayClientMessage(Component.literal("Demon's Remnants: " + left + "s").withStyle(ChatFormatting.DARK_RED), true);
        }
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof Player t && active(t) && event.getEntity() instanceof ServerPlayer w)
            ModNetwork.sendToPlayer(new com.xenowarriors.network.DemonStatePacket(t.getId(), true), w);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) stop(p);
    }
}

package com.xenowarriors.mikiko;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.character.ModCapabilities;
import com.xenowarriors.network.BladeChannelStatePacket;
import com.xenowarriors.network.ModNetwork;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Holding the summon key: Mikiko raises the Hekublade, other hand held out, head bowed, drawing power into the blade.
 * Two seconds of that gives him 30 seconds of extra damage (Strength II).
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BladeChannel {
    public static final int CHARGE = 40, BOOST = 30 * 20;
    private static final Map<UUID, Integer> START = new HashMap<>();

    private BladeChannel() {}

    public static void set(ServerPlayer p, boolean on) {
        if (ModCapabilities.getType(p) != CharacterType.MIKIKO) return;
        if (on) {
            if (!Hekublade.holding(p) && !p.getInventory().contains(new net.minecraft.world.item.ItemStack(com.xenowarriors.item.ModItems.HEKUBLADE.get())))
                Hekublade.toggle(p);                                     // calls it to his hand first
            START.put(p.getUUID(), p.tickCount);
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.5F, 1.6F);
        } else if (START.remove(p.getUUID()) == null) return;
        ModNetwork.sendToTrackingAndSelf(new BladeChannelStatePacket(p.getId(), on), p);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p)) return;
        Integer start = START.get(p.getUUID());
        if (start == null) return;
        if (!p.isAlive() || !Hekublade.holding(p)) { set(p, false); return; }
        int t = p.tickCount - start;
        var level = p.serverLevel();
        if (t % 3 == 0) {
            double a = t * 0.5;
            level.sendParticles(new DustParticleOptions(new Vector3f(1F, 0.45F, 0.12F), 1.0F),
                    p.getX() + Math.cos(a) * 0.8, p.getY() + 0.2 + t / (double) CHARGE * 2.0, p.getZ() + Math.sin(a) * 0.8, 1, 0, 0, 0, 0);
        }
        if (t >= CHARGE) {
            p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, BOOST, 1, false, true, true));
            level.sendParticles(ParticleTypes.FLAME, p.getX(), p.getY() + 1.8, p.getZ(), 24, 0.4, 0.5, 0.4, 0.06);
            level.sendParticles(new DustParticleOptions(new Vector3f(1F, 0.6F, 0.2F), 1.6F), p.getX(), p.getY() + 1.2, p.getZ(), 30, 0.6, 0.8, 0.6, 0);
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.8F, 1.3F);
            p.displayClientMessage(Component.literal("Hekublade power: +damage for 30s").withStyle(net.minecraft.ChatFormatting.GOLD), true);
            set(p, false);
        }
    }
}

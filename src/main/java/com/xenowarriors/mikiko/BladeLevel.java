package com.xenowarriors.mikiko;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.character.ModCapabilities;
import com.xenowarriors.character.PlayerCharacter;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The Hekublade grows stronger as Mikiko fights: hitting enemies (with the blade, his combo or its shards) and above
 * all sealing them inside it. Each level lets him stay inside the blade longer and shortens the wait after
 * he's pushed out.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BladeLevel {
    public static final int MAX = com.xenowarriors.stats.Stat.MAX_LEVEL;

    private BladeLevel() {}

    public static float needed(int level) {
        return 30F + level * 20F;
    }

    /** Time he can spend inside: 1.5 minutes at level 1, +15 seconds a level (about 14 minutes at 50). */
    public static int maxTicks(int level) {
        return Math.round(90 + (com.xenowarriors.stats.Stat.effective(level) - 1) * 15) * 20;   // tapers past 50
    }

    /** Wait after being pushed out: 3 minutes, 2 seconds less per level (down to about 1.4 minutes). */
    public static int cooldownTicks(int level) {
        return Math.max(60, Math.round(180 - (com.xenowarriors.stats.Stat.effective(level) - 1) * 2)) * 20;
    }

    public static int level(ServerPlayer p) {
        return p.getCapability(ModCapabilities.PLAYER_CHARACTER).map(d -> d.bladeLevel).orElse(1);
    }

    public static float progress(ServerPlayer p) {
        return p.getCapability(ModCapabilities.PLAYER_CHARACTER)
                .map(d -> d.bladeLevel >= MAX ? 1F : Math.min(1F, d.bladeXp / needed(d.bladeLevel))).orElse(0F);
    }

    public static void addXp(ServerPlayer p, float amount) {
        PlayerCharacter d = p.getCapability(ModCapabilities.PLAYER_CHARACTER).resolve().orElse(null);
        if (d == null || d.bladeLevel >= MAX || amount <= 0) return;
        d.bladeXp += amount;
        boolean up = false;
        while (d.bladeLevel < MAX && d.bladeXp >= needed(d.bladeLevel)) {
            d.bladeXp -= needed(d.bladeLevel);
            d.bladeLevel++;
            up = true;
        }
        if (d.bladeLevel >= MAX) d.bladeXp = 0;
        if (up) {
            p.sendSystemMessage(Component.literal("The Hekublade reached level " + d.bladeLevel + "! You can stay inside for "
                    + fmt(maxTicks(d.bladeLevel)) + ".").withStyle(ChatFormatting.GOLD));
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.2F, 1.3F);
        }
    }

    public static String fmt(int ticks) {
        int s = Math.max(0, ticks / 20);
        return s / 60 + ":" + String.format("%02d", s % 60);
    }

    private static ServerPlayer mikiko(net.minecraft.world.damagesource.DamageSource source) {
        if (source.getEntity() instanceof ServerPlayer p && ModCapabilities.getType(p) == CharacterType.MIKIKO) return p;
        return null;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onHurt(LivingHurtEvent event) {
        if (event.isCanceled() || event.getEntity() instanceof Player) return;
        ServerPlayer p = mikiko(event.getSource());
        if (p == null || p == event.getEntity()) return;
        // the blade's own strikes count fully; anything else of his a little
        boolean blade = Hekublade.isBlade(p.getMainHandItem()) || event.getSource().getDirectEntity() != p;
        addXp(p, Math.min(event.getAmount(), event.getEntity().getHealth()) * (blade ? 0.25F : 0.1F));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event) {
        if (event.isCanceled()) return;
        LivingEntity victim = event.getEntity();
        ServerPlayer p = mikiko(event.getSource());
        if (p == null || p == victim || victim instanceof Player) return;
        // sealing them in the blade feeds it
        addXp(p, Math.min(25F, 2F + victim.getMaxHealth() * 0.4F) * (victim instanceof Player ? 2F : 1F));
    }
}

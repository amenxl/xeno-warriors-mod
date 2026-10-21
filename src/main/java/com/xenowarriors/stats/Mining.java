package com.xenowarriors.stats;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.ModCapabilities;
import com.xenowarriors.network.MiningLevelPacket;
import com.xenowarriors.network.ModNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Mining skill: the more blocks you break, the faster you break them - with any tool, and even more so with your
 * bare hands. Level 0-50: +4% mining speed per level (3x at 50), and bare hands get another +5% per level.
 * Separate from Speed/Attack/Defense, so it doesn't count toward Character Level.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Mining {
    public static final int MAX_LEVEL = 50;
    public static final float PER_LEVEL = 0.04F, HAND_PER_LEVEL = 0.05F;
    private static int clientLevel;
    private static float clientProgress;

    private Mining() {}

    public static float needed(int level) {
        return 40F + level * 18F;
    }

    public static int level(Player p) {
        if (p.level().isClientSide) return clientLevel;
        return p.getCapability(ModCapabilities.PLAYER_CHARACTER).map(d -> d.miningLevel).orElse(0);
    }

    public static float progress(ServerPlayer p) {
        return p.getCapability(ModCapabilities.PLAYER_CHARACTER)
                .map(d -> d.miningLevel >= MAX_LEVEL ? 1F : Math.min(1F, d.miningXp / needed(d.miningLevel))).orElse(0F);
    }

    public static float multiplier(int level, boolean bareHand) {
        return (1F + level * PER_LEVEL) * (bareHand ? 1F + level * HAND_PER_LEVEL : 1F);
    }

    public static void setClient(int level, float progress) {
        clientLevel = level;
        clientProgress = progress;
    }

    public static float clientProgress() {
        return clientProgress;
    }

    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        int level = level(event.getEntity());
        if (level <= 0) return;
        boolean hand = event.getEntity().getMainHandItem().isEmpty();
        event.setNewSpeed(event.getNewSpeed() * multiplier(level, hand));
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer p) || p.isCreative() || event.isCanceled()) return;
        BlockState state = event.getState();
        float hardness = state.getDestroySpeed(event.getLevel(), event.getPos());
        if (hardness <= 0F) return;                       // grass, flowers, torches: no XP
        float xp = Math.min(4F, 0.4F + hardness * 0.5F);
        if (state.is(Tags.Blocks.ORES)) xp *= 3F;
        addXp(p, xp);
    }

    public static void addXp(ServerPlayer p, float amount) {
        p.getCapability(ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> {
            if (d.miningLevel >= MAX_LEVEL) return;
            d.miningXp += amount;
            boolean up = false;
            while (d.miningLevel < MAX_LEVEL && d.miningXp >= needed(d.miningLevel)) {
                d.miningXp -= needed(d.miningLevel);
                d.miningLevel++;
                up = true;
            }
            if (d.miningLevel >= MAX_LEVEL) d.miningXp = 0;
            if (up) {
                p.sendSystemMessage(Component.literal("Mining level " + d.miningLevel + "! You mine "
                        + Math.round((multiplier(d.miningLevel, false) - 1) * 100) + "% faster ("
                        + Math.round((multiplier(d.miningLevel, true) - 1) * 100) + "% with bare hands).").withStyle(ChatFormatting.AQUA));
                p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1F, 1.4F);
            }
        });
        sync(p);
    }

    public static void sync(ServerPlayer p) {
        ModNetwork.sendToPlayer(new MiningLevelPacket(level(p), progress(p)), p);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) sync(p);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) sync(p);
    }
}

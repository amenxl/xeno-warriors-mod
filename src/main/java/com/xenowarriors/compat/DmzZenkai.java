package com.xenowarriors.compat;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * Zenkai boost: a Saiyan (DragonMineZ's race) who's knocked down and revived may come back stronger -
 * {@value #CHANCE_PCT}% of the time (at most once every 10 minutes): +{@value #STAT} to each DragonMineZ stat, some
 * TPs, a burst of golden power and a few seconds of Strength.
 */
public final class DmzZenkai {
    private DmzZenkai() {}

    public static final int CHANCE_PCT = 30, STAT = 3, TP = 300;
    static final long GAP = 20L * 60 * 10;
    /** (Tests) Zenkai boosts given; force the next roll. */
    public static int boosts;
    public static boolean forceForTest;

    /** Their DragonMineZ race is Saiyan (or half). */
    public static boolean saiyan(ServerPlayer p) {
        if (!net.minecraftforge.fml.ModList.get().isLoaded("dragonminez")) return false;
        try {
            Object sd = DmzPerCharacter.live(p);
            if (sd == null) return false;
            Object ch = sd.getClass().getMethod("getCharacter").invoke(sd);
            String race = String.valueOf(ch.getClass().getMethod("getRaceName").invoke(ch));
            return race.toLowerCase(java.util.Locale.ROOT).contains("saiyan");
        } catch (Throwable t) { return false; }
    }

    /** Just revived: maybe a Zenkai boost. */
    public static void revived(ServerPlayer p) {
        if (!saiyan(p)) return;
        long now = p.level().getGameTime(), next = p.getPersistentData().getLong("xw_zenkai_next");
        if (!forceForTest && (now < next || p.getRandom().nextInt(100) >= CHANCE_PCT)) return;
        forceForTest = false;
        p.getPersistentData().putLong("xw_zenkai_next", now + GAP);
        for (String s : DmzKiTraining.DMZ_STATS) DmzKiTraining.bonus(p, s, STAT, "Zenkai boost!");
        DmzTpRewards.give(p, TP, "Zenkai boost");
        p.setHealth(p.getMaxHealth());
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 20 * 20, 1, false, true));
        p.serverLevel().sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1, p.getZ(), 60, 0.6, 1.0, 0.6, 0.15);
        p.serverLevel().sendParticles(ParticleTypes.FLASH, p.getX(), p.getY() + 1, p.getZ(), 1, 0, 0, 0, 0);
        p.level().playSound(null, p.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.2F, 0.8F);
        p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(5, 40, 10));
        p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(Component.literal("ZENKAI BOOST!").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)));
        p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(Component.literal("Your Saiyan blood came back stronger").withStyle(ChatFormatting.YELLOW)));
        p.sendSystemMessage(Component.literal("Zenkai boost! +" + STAT + " to every stat and +" + TP + " TPs.").withStyle(ChatFormatting.GOLD));
        boosts++;
    }
}

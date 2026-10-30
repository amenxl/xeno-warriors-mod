package com.xenowarriors.economy;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.network.ModNetwork;
import com.xenowarriors.network.MoneyPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Money: everyone's balance (kept through death). Earned from defeating enemies and from bounties; carried around as
 * Money notes (100 each) that villagers trade for emeralds. Being rich enough (10,000) gets a Skylander a Sky ID.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Money {
    private static final String KEY = "xw_money";
    /** What counts as rich enough to be treated as royalty in the Royal Capital. */
    public static final long ROYAL = 10_000;
    public static final int NOTE = 100;

    private Money() {}

    private static CompoundTag root(Player p) {
        CompoundTag t = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        p.getPersistentData().put(Player.PERSISTED_NBT_TAG, t);
        return t;
    }

    public static long get(Player p) { return root(p).getLong(KEY); }

    public static void set(ServerPlayer p, long v) {
        root(p).putLong(KEY, Math.max(0, v));
        ModNetwork.sendToPlayer(new MoneyPacket(get(p)), p);
    }

    public static void add(ServerPlayer p, long v, boolean tell) {
        if (v == 0) return;
        set(p, get(p) + v);
        if (tell) p.displayClientMessage(Component.literal((v > 0 ? "+" : "") + fmt(v) + " Money").withStyle(v > 0 ? ChatFormatting.GREEN : ChatFormatting.RED), true);
    }

    public static boolean take(ServerPlayer p, long v) {
        if (get(p) < v) return false;
        set(p, get(p) - v);
        return true;
    }

    public static String fmt(long v) { return String.format(java.util.Locale.ROOT, "%,d", v); }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) ModNetwork.sendToPlayer(new MoneyPacket(get(p)), p);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) ModNetwork.sendToPlayer(new MoneyPacket(get(p)), p);
    }

    /** Defeating enemies pays: about 1.5 Money per point of their health (a zombie ~30, a boss hundreds). */
    @SubscribeEvent
    public static void onKill(LivingDeathEvent e) {
        LivingEntity dead = e.getEntity();
        if (dead.level().isClientSide || !(e.getSource().getEntity() instanceof ServerPlayer p) || dead instanceof Player
                || com.xenowarriors.vr.Vr.inVr(dead)) return;   // (VR enemies are training, not loot)
        boolean foe = dead instanceof Enemy
                || (dead instanceof com.xenowarriors.npc.HumanNpcEntity h && (h.raider() || h.isAggressive()))
                || dead instanceof com.xenowarriors.sr.SrMemberEntity;
        if (!foe) return;
        // from its natural health (not what levels/DragonMineZ piled on), with a little for its level
        var mh = dead.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
        double base = mh != null ? mh.getBaseValue() : dead.getMaxHealth();
        int lvl = Math.min(50, com.xenowarriors.scaling.EnemyScaling.level(dead));
        add(p, (int) Math.max(5, Math.min(400, Math.round(base * 1.5 * (1 + lvl * 0.02)))), true);
    }
}

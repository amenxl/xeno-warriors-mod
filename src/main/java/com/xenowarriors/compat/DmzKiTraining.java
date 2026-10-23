package com.xenowarriors.compat;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.character.ModCapabilities;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * With DragonMineZ: fighting as Mikiko or Rhalix slowly trains your DragonMineZ stats too.
 * <ul>
 *   <li>Every 50 hits you land: +1 Strength. Every 30 heavy hits (8+ damage): +1 Strike Power.</li>
 *   <li>Every 30 hits you take: +1 Resistance. Every 8 close calls (dropping under 30% health and living): +1 Vitality.</li>
 *   <li>Every 15 skills used: +1 Ki Power. Every 300 Karma spent, and every 3 Karma Sensing practices: +1 Energy.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DmzKiTraining {
    private static Boolean loaded;
    private static boolean broken;
    private static Capability<?> cap;
    private static Method getStats, sendSync;
    private static java.lang.reflect.Constructor<?> syncPacket;
    private static final Map<String, Method> ADDERS = new HashMap<>();

    private DmzKiTraining() {}

    private static boolean ready() {
        if (loaded == null) loaded = ModList.get() != null && ModList.get().isLoaded("dragonminez");
        if (!loaded || broken) return false;
        if (cap == null) {
            try {
                cap = (Capability<?>) Class.forName("com.dragonminez.common.stats.StatsCapability").getField("INSTANCE").get(null);
                Class<?> data = Class.forName("com.dragonminez.common.stats.StatsData");
                getStats = data.getMethod("getStats");
                for (String s : new String[]{"Strength", "StrikePower", "Resistance", "Vitality", "KiPower", "Energy"})
                    ADDERS.put(s, getStats.getReturnType().getMethod("add" + s, int.class));
                Class<?> pkt = Class.forName("com.dragonminez.common.network.S2C.StatsSyncS2C");
                syncPacket = pkt.getConstructor(ServerPlayer.class);
                sendSync = Class.forName("com.dragonminez.common.network.NetworkHandler")
                        .getMethod("sendToTrackingEntityAndSelf", Object.class, net.minecraft.world.entity.Entity.class);
            } catch (Throwable t) {
                broken = true;
                cap = null;
                return false;
            }
        }
        return true;
    }

    private static boolean ours(Player p) {
        CharacterType t = ModCapabilities.getType(p);
        return t == CharacterType.MIKIKO || t == CharacterType.RHALIX;
    }

    /** Counts one more of something; every {@code every} of them gives +1 to the DragonMineZ stat. */
    private static void count(ServerPlayer p, String key, int every, String stat, String label) {
        if (!ready() || !ours(p)) return;
        CompoundTag root = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        int n = root.getInt(key) + 1;
        boolean gain = n >= every;
        root.putInt(key, gain ? 0 : n);
        p.getPersistentData().put(Player.PERSISTED_NBT_TAG, root);
        if (gain) give(p, stat, label);
    }

    private static void give(ServerPlayer p, String stat, String label) {
        try {
            Object data = p.getCapability(cap).resolve().orElse(null);
            if (data == null) return;
            ADDERS.get(stat).invoke(getStats.invoke(data), 1);
            sendSync.invoke(null, syncPacket.newInstance(p), p);
            p.displayClientMessage(Component.literal(label).withStyle(ChatFormatting.GOLD), true);
        } catch (Throwable t) {
            broken = true;
        }
    }

    /** One more bit of Karma Sensing practice. */
    public static void practiced(ServerPlayer p) {
        count(p, "xw_ki_practice", 3, "Energy", "Karma training: +1 Energy (max Ki)");
    }

    /** A skill went off. */
    public static void skillUsed(ServerPlayer p) {
        count(p, "xw_dmz_ki", 15, "KiPower", "Skill training: +1 Ki Power");
    }

    /** Karma was used up (skills, focus, purifying...). */
    public static void karmaSpent(ServerPlayer p, float amount) {
        if (amount <= 0 || !ready() || !ours(p)) return;
        CompoundTag root = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        float total = root.getFloat("xw_dmz_karma") + amount;
        boolean gain = total >= 300F;
        root.putFloat("xw_dmz_karma", gain ? total - 300F : total);
        p.getPersistentData().put(Player.PERSISTED_NBT_TAG, root);
        if (gain) give(p, "Energy", "Karma use: +1 Energy (max Ki)");
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onHurt(LivingHurtEvent event) {
        if (event.isCanceled() || event.getAmount() <= 0) return;
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide) return;
        // hitting things
        if (event.getSource().getEntity() instanceof ServerPlayer p && p != victim && event.getSource().getDirectEntity() == p) {
            count(p, "xw_dmz_str", 50, "Strength", "Combat training: +1 Strength");
            if (event.getAmount() >= 8F) count(p, "xw_dmz_strike", 30, "StrikePower", "Heavy hits: +1 Strike Power");
        }
        // getting hit
        if (victim instanceof ServerPlayer p && event.getSource().getEntity() instanceof LivingEntity attacker && attacker != p) {
            count(p, "xw_dmz_res", 30, "Resistance", "Taking hits: +1 Resistance");
            float after = p.getHealth() - event.getAmount();
            if (after > 0 && after < p.getMaxHealth() * 0.3F) {
                CompoundTag root = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
                long now = p.level().getGameTime();
                if (now - root.getLong("xw_dmz_close_at") > 200) {
                    root.putLong("xw_dmz_close_at", now);
                    p.getPersistentData().put(Player.PERSISTED_NBT_TAG, root);
                    count(p, "xw_dmz_vit", 8, "Vitality", "Close call: +1 Vitality");
                }
            }
        }
    }
}

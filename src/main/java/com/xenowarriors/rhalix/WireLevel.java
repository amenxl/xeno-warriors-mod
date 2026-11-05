package com.xenowarriors.rhalix;

import com.xenowarriors.character.CharacterType;
import com.xenowarriors.character.ModCapabilities;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

/**
 * Rhalix's wires level (Season 2): like the Hekublade's level, it grows by using his wires - whip hits, binds, the
 * wire sword. Gates what he can make out of them (guns at 50 with gun level 50, tools like Mikiko's).
 */
public final class WireLevel {
    public static final int MAX = com.xenowarriors.stats.Stat.MAX_LEVEL;
    private static final String LV = "xw_wire_lv", XP = "xw_wire_xp";

    private WireLevel() {}

    public static float needed(int level) { return 30F + level * 20F; }

    private static CompoundTag keep(Player p) {
        var root = p.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG)) root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        return root.getCompound(Player.PERSISTED_NBT_TAG);
    }

    public static int level(Player p) {
        CompoundTag k = keep(p);
        return k.contains(LV) ? Math.max(1, k.getInt(LV)) : 1;
    }

    public static float progress(Player p) {
        int lv = level(p);
        return lv >= MAX ? 1F : Math.min(1F, keep(p).getFloat(XP) / needed(lv));
    }

    public static void set(ServerPlayer p, int level) {
        keep(p).putInt(LV, Math.max(1, Math.min(MAX, level)));
        keep(p).putFloat(XP, 0F);
    }

    public static void addXp(ServerPlayer p, float amount) {
        if (amount <= 0 || ModCapabilities.getType(p) != CharacterType.RHALIX) return;
        int lv = level(p);
        if (lv >= MAX) return;
        float xp = keep(p).getFloat(XP) + amount;
        int before = lv;
        while (lv < MAX && xp >= needed(lv)) { xp -= needed(lv); lv++; }
        keep(p).putInt(LV, lv);
        keep(p).putFloat(XP, lv >= MAX ? 0F : xp);
        if (lv != before) {
            p.sendSystemMessage(Component.literal("Rhalix's wires reached level " + lv + "!").withStyle(ChatFormatting.LIGHT_PURPLE));
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1F, 0.8F);
            com.xenowarriors.forms.WeaponForms.announceUnlocks(p, before, lv);
        }
    }
}

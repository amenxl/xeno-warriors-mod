package com.xenowarriors.compat;

import com.xenowarriors.character.CharacterType;
import com.xenowarriors.character.ModCapabilities;
import com.xenowarriors.character.PlayerCharacter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

/**
 * Off-duty bots (bot.OffDuty) with DragonMineZ: training and their own missions earn Training Points on the bot's saved
 * DragonMineZ character, straight into its save (no entity needed). DmzBotTraining spends them the usual way once the
 * bot is back on the team. Nothing without DragonMineZ or before the character was made.
 */
public final class DmzOffDuty {
    private DmzOffDuty() {}

    private static String key(ServerPlayer o, CharacterType t) {
        return o.getCapability(ModCapabilities.PLAYER_CHARACTER).map(d -> PlayerCharacter.key(t, d.youSlot)).orElse(t.getId());
    }

    /** Has this character a DragonMineZ character of its own, put away (not the live or open one)? */
    public static boolean available(ServerPlayer o, CharacterType t) {
        if (!DmzBotQuests.ready() || o == null) return false;
        String k = key(o, t);
        if (DmzPerCharacter.isLive(o, k) || k.equals(DmzPerCharacter.viewing(o))) return false;
        CompoundTag tag = DmzPerCharacter.saved(o, k);
        return tag != null && tag.getCompound("Status").getBoolean("HasCreatedChar");
    }

    /** Adds TP to the saved character. True when it went in. */
    public static boolean addTp(ServerPlayer o, CharacterType t, float tp) {
        if (tp <= 0 || !available(o, t)) return false;
        String k = key(o, t);
        CompoundTag tag = DmzPerCharacter.saved(o, k).copy();
        CompoundTag res = tag.getCompound("Resources");
        res.putFloat("TrainingPointsF", res.getFloat("TrainingPointsF") + tp);
        tag.put("Resources", res);
        return DmzPerCharacter.store(o, k, tag);
    }

    /** The saved character's TP (tests), or -1. */
    public static float tp(ServerPlayer o, CharacterType t) {
        if (!available(o, t)) return -1;
        return DmzPerCharacter.saved(o, key(o, t)).getCompound("Resources").getFloat("TrainingPointsF");
    }
}

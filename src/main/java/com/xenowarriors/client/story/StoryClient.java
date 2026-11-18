package com.xenowarriors.client.story;

import com.xenowarriors.character.CharacterType;
import com.xenowarriors.network.ModNetwork;
import com.xenowarriors.network.StoryActionPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** Story mode on the client: the state the server sends (StoryStatePacket), and the dialogue / menu it asks to show. */
public final class StoryClient {
    private StoryClient() {}

    public static CompoundTag state = new CompoundTag();

    public static void state(CompoundTag t) {
        state = t;
        if (Minecraft.getInstance().screen instanceof StoryScreen s) s.refresh();
    }

    public static boolean active() { return state.getBoolean("active") && !state.getBoolean("all"); }

    /** This character's entry (met, play, bond, need, side...), or an empty tag. */
    public static CompoundTag ch(CharacterType t) {
        for (Tag x : state.getList("chars", Tag.TAG_COMPOUND)) if (((CompoundTag) x).getString("id").equals(t.getId())) return (CompoundTag) x;
        return new CompoundTag();
    }

    /** Locked by story mode (not met yet, or not enough bond)? */
    public static boolean locked(CharacterType t) {
        if (!active() || t == CharacterType.YOU || t == CharacterType.NONE) return false;
        CompoundTag c = ch(t);
        return c.contains("id") && !c.getBoolean("play");
    }

    /** Why, for the select screen. */
    public static String lockText(CharacterType t) {
        CompoundTag c = ch(t);
        if (!c.getBoolean("met")) return "Story mode: not met yet";
        return "Story mode: bond " + c.getInt("bond") + "/" + c.getInt("need") + (com.xenowarriors.story.Story.BIG.contains(t) ? " (or finish their storyline)" : "");
    }

    public static void dialogue(CompoundTag t) {
        Minecraft mc = Minecraft.getInstance();
        if (t.getBoolean("menu")) { mc.setScreen(new StoryScreen(mc.screen instanceof StoryDialogueScreen ? null : mc.screen)); return; }
        if (t.getBoolean("close")) { if (mc.screen instanceof StoryDialogueScreen) mc.setScreen(null); return; }
        mc.setScreen(new StoryDialogueScreen(t));
    }

    public static void act(int action, String arg, int choice) { ModNetwork.sendToServer(new StoryActionPacket(action, arg == null ? "" : arg, choice)); }

    /** Opens the Story menu (asks the server for the latest state first). */
    public static void open() { act(StoryActionPacket.OPEN, "", 0); }
}

package com.xenowarriors.client.journey;

import com.xenowarriors.character.CharacterType;
import com.xenowarriors.network.JourneyActionPacket;
import com.xenowarriors.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;

/** The client's copy of the Journey state (sent whole by the server whenever it changes). */
public final class JourneyClient {
    private JourneyClient() {}

    public static CompoundTag state = new CompoundTag();

    public static boolean active() { return state.getBoolean("active"); }
    public static int phase() { return state.getInt("phase"); }

    public record Member(String name, boolean me, boolean ready, boolean out, CharacterType active, int score, List<CharacterType> picks, List<CharacterType> chosen) {}

    public static List<Member> members() {
        List<Member> out = new ArrayList<>();
        ListTag l = state.getList("members", Tag.TAG_COMPOUND);
        for (int i = 0; i < l.size(); i++) {
            CompoundTag t = l.getCompound(i);
            out.add(new Member(t.getString("name"), t.getBoolean("me"), t.getBoolean("ready"), t.getBoolean("out"), type(t.getInt("active")), t.getInt("score"),
                    types(t.getIntArray("picks")), types(t.getIntArray("chosen"))));
        }
        return out;
    }

    public static Member me() { for (Member m : members()) if (m.me()) return m; return null; }
    public static Member partner() { for (Member m : members()) if (!m.me()) return m; return null; }

    public static CharacterType type(int i) { CharacterType[] v = CharacterType.values(); return i >= 0 && i < v.length ? v[i] : CharacterType.NONE; }
    private static List<CharacterType> types(int[] a) { List<CharacterType> l = new ArrayList<>(); for (int i : a) l.add(type(i)); return l; }

    /** A character's health in the run (0 = down, -1 = not in the party). */
    public static float hp(CharacterType t) {
        CompoundTag h = state.getCompound("hp");
        String k = String.valueOf(t.ordinal());
        return h.contains(k) ? h.getFloat(k) : -1F;
    }

    public static void act(int action, int a, int b) { ModNetwork.sendToServer(new JourneyActionPacket(action, a, b)); }

    public static void receive(CompoundTag t, int open) {
        state = t;
        Minecraft mc = Minecraft.getInstance();
        if (!active()) { if (mc.screen instanceof JourneyScreen) mc.setScreen(null); return; }
        switch (open) {
            case 1 -> mc.setScreen(new JourneyLobbyScreen());
            case 2 -> mc.setScreen(new JourneyTeamScreen());
            case 3 -> mc.setScreen(new JourneyMenuScreen(false));
            case 4 -> mc.setScreen(new JourneyMenuScreen(true));
            default -> {
                // a screen that no longer fits what's happening closes
                if (mc.screen instanceof JourneyLobbyScreen && phase() != 0) mc.setScreen(null);
                if (mc.screen instanceof JourneyTeamScreen && phase() != 2) mc.setScreen(null);
            }
        }
    }
}

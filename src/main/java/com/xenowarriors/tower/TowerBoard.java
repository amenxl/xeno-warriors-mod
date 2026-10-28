package com.xenowarriors.tower;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;

/** The server's Tower leaderboard: everyone's best floor (and who they were playing), top 10 shown. */
public final class TowerBoard extends SavedData {
    public record Entry(UUID id, String name, String character, int floor) {}
    private final Map<UUID, Entry> best = new HashMap<>();

    private static TowerBoard get(MinecraftServer s) {
        return s.overworld().getDataStorage().computeIfAbsent(TowerBoard::load, TowerBoard::new, "xenowarriors_tower_board");
    }

    private static TowerBoard load(CompoundTag t) {
        TowerBoard b = new TowerBoard();
        for (var e : t.getList("best", 10)) {
            CompoundTag c = (CompoundTag) e;
            Entry en = new Entry(c.getUUID("id"), c.getString("name"), c.getString("char"), c.getInt("floor"));
            b.best.put(en.id(), en);
        }
        return b;
    }

    @Override
    public CompoundTag save(CompoundTag t) {
        ListTag l = new ListTag();
        for (Entry e : best.values()) {
            CompoundTag c = new CompoundTag();
            c.putUUID("id", e.id()); c.putString("name", e.name()); c.putString("char", e.character()); c.putInt("floor", e.floor());
            l.add(c);
        }
        t.put("best", l);
        return t;
    }

    public static List<Entry> top(MinecraftServer s, int n) {
        List<Entry> l = new ArrayList<>(get(s).best.values());
        l.sort(Comparator.comparingInt(Entry::floor).reversed());
        return l.subList(0, Math.min(n, l.size()));
    }

    /** Reached a floor: record it, and tell everyone about a new server record. */
    public static void reached(ServerPlayer p, int floor) {
        TowerBoard b = get(p.server);
        Entry old = b.best.get(p.getUUID());
        if (old != null && old.floor() >= floor) return;
        int record = b.best.values().stream().mapToInt(Entry::floor).max().orElse(0);
        b.best.put(p.getUUID(), new Entry(p.getUUID(), p.getGameProfile().getName(),
                com.xenowarriors.character.ModCapabilities.getType(p).getDisplayName().getString(), floor));
        b.setDirty();
        if (floor > record && floor >= 10 && floor % 5 == 0)
            p.server.getPlayerList().broadcastSystemMessage(Component.literal("Tower record! " + p.getGameProfile().getName() + " reached floor " + floor + ".")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
    }

    public static void show(ServerPlayer p) {
        List<Entry> t = top(p.server, 10);
        p.sendSystemMessage(Component.literal("Tower leaderboard").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
        if (t.isEmpty()) p.sendSystemMessage(Component.literal("  Nobody has climbed the Tower yet.").withStyle(ChatFormatting.GRAY));
        for (int i = 0; i < t.size(); i++) {
            Entry e = t.get(i);
            p.sendSystemMessage(Component.literal("  " + (i + 1) + ". " + e.name() + " - floor " + e.floor() + " (" + e.character() + ")")
                    .withStyle(e.id().equals(p.getUUID()) ? ChatFormatting.YELLOW : ChatFormatting.WHITE));
        }
    }
}

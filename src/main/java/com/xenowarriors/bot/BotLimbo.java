package com.xenowarriors.bot;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;

/**
 * Keeps the bot data that only lives in memory across a server restart: bot teammates waiting for an offline owner
 * (PartyBots.LEFT_BEHIND) and the things of bots that went home while their owner was away (PartyBots.PENDING_HOME).
 *
 * The in-memory lists stay the one source of truth while the server runs; this world data is a snapshot of them,
 * written whenever the overworld's data is saved (always dirty, so every save writes the lists as they are right then)
 * and read back once when the server starts. Saving and logging in both run on the server thread, so a login can't
 * land half way through a save: whatever was handed over at login is gone from the lists and from the next snapshot.
 */
public final class BotLimbo extends SavedData {
    static final String NAME = "xenowarriors_bot_limbo";

    public static BotLimbo get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(BotLimbo::load, BotLimbo::new, NAME);
    }

    private CompoundTag loaded = new CompoundTag();

    private static BotLimbo load(CompoundTag tag) { BotLimbo l = new BotLimbo(); l.loaded = tag.copy(); return l; }

    @Override public boolean isDirty() { return true; }                 // (a snapshot of the live lists every save)

    @Override public CompoundTag save(CompoundTag tag) { write(tag); return tag; }

    /** The lists as they are now. */
    static CompoundTag write(CompoundTag tag) {
        ListTag left = new ListTag();
        for (var e : PartyBots.LEFT_BEHIND) {
            CompoundTag t = new CompoundTag();
            t.putUUID("owner", e.getKey());
            t.put("rec", e.getValue().copy());
            left.add(t);
        }
        ListTag home = new ListTag();
        for (var e : PartyBots.PENDING_HOME.entrySet()) for (CompoundTag rec : e.getValue()) {
            CompoundTag t = new CompoundTag();
            t.putUUID("owner", e.getKey());
            t.put("rec", rec.copy());
            home.add(t);
        }
        tag.put("left_behind", left);
        tag.put("pending_home", home);
        return tag;
    }

    /** The lists from a snapshot (replacing whatever they held). */
    static void read(CompoundTag tag) {
        PartyBots.LEFT_BEHIND.clear();
        PartyBots.PENDING_HOME.clear();
        ListTag left = tag.getList("left_behind", 10);
        for (int i = 0; i < left.size(); i++) {
            CompoundTag t = left.getCompound(i);
            if (t.hasUUID("owner")) PartyBots.LEFT_BEHIND.add(Map.entry(t.getUUID("owner"), t.getCompound("rec").copy()));
        }
        ListTag home = tag.getList("pending_home", 10);
        for (int i = 0; i < home.size(); i++) {
            CompoundTag t = home.getCompound(i);
            if (t.hasUUID("owner")) PartyBots.PENDING_HOME.computeIfAbsent(t.getUUID("owner"), k -> new ArrayList<>()).add(t.getCompound("rec").copy());
        }
    }

    /** At server start: what was waiting when the world was last saved comes back. */
    static void restore(MinecraftServer server) {
        BotLimbo l = get(server);
        read(l.loaded);
        l.loaded = new CompoundTag();
        if (!PartyBots.LEFT_BEHIND.isEmpty() || !PartyBots.PENDING_HOME.isEmpty())
            com.xenowarriors.XenoWarriors.LOGGER.info("Restored {} bot teammates waiting for offline owners and {} bot inventories not yet home",
                    PartyBots.LEFT_BEHIND.size(), PartyBots.PENDING_HOME.values().stream().mapToInt(List::size).sum());
    }
    /**
     * Selftest: a waiting bot and a bot bag that went home while its owner was away are saved with the world, the
     * lists are wiped (a restart), read back from the file on disk, and the bag reaches the owner's save exactly once.
     * The real lists are put back afterwards. Returns a one-line result ("OK ..." or "FAIL ...").
     */
    public static String selfTest(MinecraftServer server) throws java.io.IOException {
        var keepLeft = new ArrayList<>(PartyBots.LEFT_BEHIND);
        var keepHome = new HashMap<>(PartyBots.PENDING_HOME);
        try {
            UUID owner = UUID.nameUUIDFromBytes("xw-limbo-selftest".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            String ch = com.xenowarriors.character.CharacterType.MIKIKO.getId();
            CompoundTag rec = new CompoundTag();
            rec.putString("char", ch);
            ListTag inv = new ListTag();
            inv.add(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND, 7).save(new CompoundTag()));
            rec.put("inv", inv);
            rec.put("armour", new ListTag());
            PartyBots.LEFT_BEHIND.clear(); PartyBots.PENDING_HOME.clear();
            PartyBots.PENDING_HOME.computeIfAbsent(owner, k -> new ArrayList<>()).add(rec.copy());
            CompoundTag waiting = new CompoundTag(); waiting.putString("char", com.xenowarriors.character.CharacterType.RHALIX.getId());
            PartyBots.LEFT_BEHIND.add(Map.entry(owner, waiting));
            get(server);                                                       // (registered with the world's data)
            server.overworld().getDataStorage().save();                        // the world is saved
            PartyBots.LEFT_BEHIND.clear(); PartyBots.PENDING_HOME.clear();     // ... the server goes down
            java.io.File f = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data").resolve(NAME + ".dat").toFile();
            CompoundTag disk = net.minecraft.nbt.NbtIo.readCompressed(f).getCompound("data");
            read(disk);                                                        // ... and comes back up
            int left = (int) PartyBots.LEFT_BEHIND.stream().filter(e -> e.getKey().equals(owner)).count();
            int pend = PartyBots.PENDING_HOME.getOrDefault(owner, List.of()).size();
            var fp = net.minecraftforge.common.util.FakePlayerFactory.get(server.overworld(), new com.mojang.authlib.GameProfile(owner, "xw_limbo_test"));
            int d1 = PartyBots.deliverPending(fp), d2 = PartyBots.deliverPending(fp);   // (a second login hands over nothing)
            int diamonds = 0;
            ListTag saved = com.xenowarriors.character.CharacterInventories.saved(fp, ch).getList("inv", 10);
            for (int i = 0; i < saved.size(); i++) { var st = net.minecraft.world.item.ItemStack.of(saved.getCompound(i)); if (st.is(net.minecraft.world.item.Items.DIAMOND)) diamonds += st.getCount(); }
            int afterSnap = write(new CompoundTag()).getList("pending_home", 10).size();
            boolean ok = left == 1 && pend == 1 && d1 == 1 && d2 == 0 && diamonds == 7 && afterSnap == 0;
            com.xenowarriors.character.CharacterInventories.forget(fp, ch);    // (tidy the test owner's save)
            return (ok ? "OK" : "FAIL") + " leftBehind=" + left + " pendingHome=" + pend + " delivered=" + d1 + "/" + d2 + " diamonds=" + diamonds + " pendingAfter=" + afterSnap;
        } finally {
            PartyBots.LEFT_BEHIND.clear(); PartyBots.LEFT_BEHIND.addAll(keepLeft);
            PartyBots.PENDING_HOME.clear(); PartyBots.PENDING_HOME.putAll(keepHome);
        }
    }
}

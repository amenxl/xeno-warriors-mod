package com.xenowarriors.client.test;

import com.xenowarriors.bot.PartyBotEntity;
import com.xenowarriors.bot.PartyBots;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.client.UiTest;
import com.xenowarriors.client.UiTest.Step;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.entity.player.PlayerEvent;

import java.io.File;
import java.util.*;

/**
 * Bots' things across a crash (focus "botsave", "dmzbotsave" with DragonMineZ). Each case logs
 * "XW-TEST botsave ... -> PASS/FAIL". A "crash" here: the world is saved the normal way (/save-all), the files are read
 * back from disk, everything only in memory is thrown away without a logout (the bots just vanish, as when the process
 * is killed) and the player is loaded from what was on disk - then the login runs as it would after a restart.
 * <ol>
 *   <li>crash: a bot's bag, armour, health and a downed bot's things come back (the downed one's go home);</li>
 *   <li>a second crash right after: nothing restored twice (no doubled bot, no doubled things);</li>
 *   <li>relog twice: the item counts don't change;</li>
 *   <li>a clean logout, a login, then a crash before the next save: one copy, from the logout's records;</li>
 *   <li>a Journey teammate's finds survive a crash (they come to you, as at a logout).</li>
 * </ol>
 */
public final class BotSaveTests {
    private BotSaveTests() {}

    public static boolean handles(String focus) { return focus != null && focus.replaceFirst("^dmz", "").startsWith("botsave"); }

    private static void log(String s) { UiTest.log("botsave " + s); }

    private static String f(float v) { return String.format(Locale.ROOT, "%.1f", v); }

    private static PartyBotEntity bot(ServerPlayer p, CharacterType t) {
        for (PartyBotEntity b : PartyBots.bots(p)) if (b.character() == t) return b;
        return null;
    }

    private static int count(ServerPlayer p, CharacterType t) {
        int n = 0;
        for (PartyBotEntity b : PartyBots.bots(p)) if (b.character() == t) n++;
        return n;
    }

    /** Keys that only hold copies of live bots (the snapshot / logout records), skipped when counting real items. */
    private static final Set<String> SNAPSHOT_KEYS = Set.of("xw_bots", "xw_bots_live");

    private static int walk(Tag t, String id) {
        int n = 0;
        if (t instanceof CompoundTag c) {
            if (id.equals(c.getString("id")) && c.contains("Count")) n += c.getByte("Count");
            for (String k : c.getAllKeys()) if (!SNAPSHOT_KEYS.contains(k)) n += walk(c.get(k), id);
        } else if (t instanceof ListTag l) for (Tag e : l) n += walk(e, id);
        return n;
    }

    /** Every copy of this item the player owns: on them, in their saves (character inventories...), on their live bots. */
    private static int owned(ServerPlayer p, String id) {
        int n = walk(p.getInventory().save(new ListTag()), id) + walk(p.getEnderChestInventory().createTag(), id) + walk(p.getPersistentData(), id);
        for (PartyBotEntity b : PartyBots.bots(p)) n += walk(b.record(), id);
        return n;
    }

    /** Copies in the crash snapshot (what a restart would bring back on bots). */
    private static int inSnapshot(CompoundTag disk, String id) {
        CompoundTag fd = disk.getCompound("ForgeData");
        int n = 0;
        for (String k : SNAPSHOT_KEYS) { if (fd.contains(k)) n += walkAll(fd.get(k), id); if (fd.getCompound("PlayerPersisted").contains(k)) n += walkAll(fd.getCompound("PlayerPersisted").get(k), id); }
        return n;
    }

    private static int walkAll(Tag t, String id) {
        int n = 0;
        if (t instanceof CompoundTag c) {
            if (id.equals(c.getString("id")) && c.contains("Count")) n += c.getByte("Count");
            for (String k : c.getAllKeys()) n += walkAll(c.get(k), id);
        } else if (t instanceof ListTag l) for (Tag e : l) n += walkAll(e, id);
        return n;
    }

    private static void clear(ServerPlayer p) {
        for (PartyBotEntity b : new ArrayList<>(PartyBots.bots(p))) { PartyBots.checkIn(b, p); PartyBots.forgetFake(b.getUUID()); b.discard(); }
        for (CharacterType t : CharacterType.PLAYABLE) p.getPersistentData().remove("xw_bot_rejoin_" + t.getId());
        p.getPersistentData().remove("xw_bots");
    }

    /** The world saved the normal way (as /save-all or an autosave), then what a restart would load for this player. */
    private static CompoundTag saveAndReadBack(ServerPlayer p, String tag) {
        p.server.saveEverything(true, false, true);              // (no chunk flush: slow in CI; players and level.dat are written right away)
        CompoundTag disk = null;
        String from = "?";
        try {
            File level = p.server.getWorldPath(LevelResource.LEVEL_DATA_FILE).toFile();
            CompoundTag host = level.exists() ? NbtIo.readCompressed(level).getCompound("Data").getCompound("Player") : new CompoundTag();
            if (!host.isEmpty() && p.server.isSingleplayerOwner(p.getGameProfile())) { disk = host; from = "level.dat"; }   // (the singleplayer host is loaded from here)
            File own = p.server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(p.getStringUUID() + ".dat").toFile();
            if (disk == null && own.exists()) { disk = NbtIo.readCompressed(own); from = "playerdata"; }
            if (own.exists()) {
                CompoundTag pd = NbtIo.readCompressed(own);
                log(tag + " on disk: playerdata snapshot diamonds=" + inSnapshot(pd, "minecraft:diamond") + (disk != pd && from.equals("level.dat") ? " level.dat snapshot diamonds=" + inSnapshot(disk, "minecraft:diamond") : ""));
            }
        } catch (Exception e) { log(tag + " FAIL reading the save: " + e); }
        log(tag + " read back from " + from);
        return disk;
    }

    /** The crash: bots vanish without a logout, the server's memory of this player is replaced by what was on disk, then the login. */
    private static void crashAndRestart(ServerPlayer p, CompoundTag disk, String tag) {
        if (disk == null) { log(tag + " FAIL nothing on disk"); return; }
        int gone = 0;
        for (PartyBotEntity b : new ArrayList<>(PartyBots.bots(p))) { PartyBots.forgetFake(b.getUUID()); b.discard(); gone++; }
        PartyBots.resetForCrashTest(p);
        p.load(disk.copy());
        log(tag + " crashed: " + gone + " bots vanished, player loaded from disk");
        PartyBots.onLogin(new PlayerEvent.PlayerLoggedInEvent(p));
    }

    private static final int[] N = new int[4];
    private static final float[] HP = new float[1];
    private static final Object[] DISK = new Object[1];

    private static String counts(ServerPlayer p) {
        return "diamonds=" + owned(p, "minecraft:diamond") + " helmets=" + owned(p, "minecraft:iron_helmet") + " emeralds=" + owned(p, "minecraft:emerald") + " lapis=" + owned(p, "minecraft:lapis_lazuli");
    }

    private static boolean same(ServerPlayer p) {
        return owned(p, "minecraft:diamond") == N[0] && owned(p, "minecraft:iron_helmet") == N[1] && owned(p, "minecraft:emerald") == N[2] && owned(p, "minecraft:lapis_lazuli") == N[3];
    }

    private static int bag(PartyBotEntity b, net.minecraft.world.item.Item it) {
        int n = 0;
        for (int i = 0; i < b.inventory.getContainerSize(); i++) if (b.inventory.getItem(i).is(it)) n += b.inventory.getItem(i).getCount();
        return n;
    }

    private static int saved(ServerPlayer p, String ch, String id) { return walk(com.xenowarriors.character.CharacterInventories.saved(p, ch), id); }

    public static void build(Minecraft mc, List<Step> l, String focus) {
        l.add(new Step(null, () -> { UiTest.noToasts(mc); mc.setScreen(null); UiTest.character(CharacterType.MIKIKO);
            UiTest.server(p -> { p.setGameMode(GameType.SURVIVAL); p.setHealth(p.getMaxHealth()); }); }, 40));

        // ---- 1. a crash with a bot carrying things and a downed bot
        l.add(new Step(null, () -> UiTest.server(p -> {
            clear(p);
            PartyBots.spawn(p, CharacterType.RHALIX, null);
            PartyBots.spawn(p, CharacterType.KYRIE, null);
        }), 100));
        l.add(new Step(null, () -> UiTest.server(p -> {
            PartyBotEntity r = bot(p, CharacterType.RHALIX), k = bot(p, CharacterType.KYRIE);
            if (r == null || k == null) { log("crash FAIL no bots"); return; }
            // all in this one tick, right before the save: nothing but the save itself can have written it down
            r.inventory.addItem(new ItemStack(Items.DIAMOND, 13));
            r.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
            r.setHealth(r.getMaxHealth() - 3F);
            HP[0] = r.getHealth();
            k.inventory.addItem(new ItemStack(Items.EMERALD, 7));
            k.die(k.damageSources().generic());                                // (down, waiting for a revive)
            N[0] = owned(p, "minecraft:diamond"); N[1] = owned(p, "minecraft:iron_helmet"); N[2] = owned(p, "minecraft:emerald"); N[3] = owned(p, "minecraft:lapis_lazuli");
            log("crash before: rhalix bag diamonds=" + bag(r, Items.DIAMOND) + " head=" + r.getItemBySlot(EquipmentSlot.HEAD).getItem() + " hp=" + f(HP[0]) + "/" + f(r.getMaxHealth())
                    + " kyrie downed=" + k.downed() + " emeralds=" + bag(k, Items.EMERALD) + " | owned " + counts(p));
            CompoundTag disk = saveAndReadBack(p, "crash");
            DISK[0] = disk;
            crashAndRestart(p, disk, "crash");
        }), 100));
        l.add(new Step(null, () -> UiTest.server(p -> {
            PartyBotEntity r = bot(p, CharacterType.RHALIX), k = bot(p, CharacterType.KYRIE);
            int homeEm = saved(p, "kyrie", "minecraft:emerald");
            boolean ok = r != null && bag(r, Items.DIAMOND) == 13 && r.getItemBySlot(EquipmentSlot.HEAD).is(Items.IRON_HELMET)
                    && Math.abs(r.getHealth() - HP[0]) <= Math.max(0.6F, r.getMaxHealth() * 0.01F) && k == null && homeEm == 7 && same(p);
            log("crash after restart: rhalix=" + (r == null ? "MISSING" : "bag diamonds=" + bag(r, Items.DIAMOND) + " head=" + r.getItemBySlot(EquipmentSlot.HEAD).getItem() + " hp=" + f(r.getHealth()) + " (was " + f(HP[0]) + ")")
                    + " kyrie live=" + (k != null) + " kyrie emeralds home=" + homeEm + " | owned " + counts(p) + " (was " + N[0] + "/" + N[1] + "/" + N[2] + "/" + N[3] + ") -> " + (ok ? "PASS" : "FAIL"));
        }), 10));

        // ---- 2. crash again straight away: exactly once
        l.add(new Step(null, () -> UiTest.server(p -> {
            CompoundTag disk = saveAndReadBack(p, "crash2");
            crashAndRestart(p, disk, "crash2");
        }), 100));
        l.add(new Step(null, () -> UiTest.server(p -> {
            PartyBotEntity r = bot(p, CharacterType.RHALIX);
            boolean ok = count(p, CharacterType.RHALIX) == 1 && count(p, CharacterType.KYRIE) == 0 && r != null && bag(r, Items.DIAMOND) == 13 && saved(p, "kyrie", "minecraft:emerald") == 7 && same(p);
            log("crash2 after restart: rhalix bots=" + count(p, CharacterType.RHALIX) + " kyrie bots=" + count(p, CharacterType.KYRIE) + " kyrie emeralds home=" + saved(p, "kyrie", "minecraft:emerald")
                    + " | owned " + counts(p) + " -> " + (ok ? "PASS" : "FAIL"));
        }), 10));

        // ---- 3. relog twice: nothing changes
        l.add(new Step(null, () -> UiTest.server(p -> {
            for (int i = 0; i < 2; i++) { PartyBots.onLogout(new PlayerEvent.PlayerLoggedOutEvent(p)); PartyBots.onLogin(new PlayerEvent.PlayerLoggedInEvent(p)); }
        }), 100));
        l.add(new Step(null, () -> UiTest.server(p -> {
            PartyBotEntity r = bot(p, CharacterType.RHALIX);
            boolean ok = count(p, CharacterType.RHALIX) == 1 && r != null && bag(r, Items.DIAMOND) == 13 && r.getItemBySlot(EquipmentSlot.HEAD).is(Items.IRON_HELMET) && same(p);
            log("relog x2: rhalix bots=" + count(p, CharacterType.RHALIX) + (r == null ? "" : " bag diamonds=" + bag(r, Items.DIAMOND)) + " | owned " + counts(p) + " -> " + (ok ? "PASS" : "FAIL"));
        }), 10));

        // ---- 4. a clean logout (saved as the game does), a login, then a crash before the next save
        l.add(new Step(null, () -> UiTest.server(p -> {
            PartyBots.onLogout(new PlayerEvent.PlayerLoggedOutEvent(p));
            CompoundTag disk = saveAndReadBack(p, "logoutcrash");                // (the save a logout makes)
            PartyBots.onLogin(new PlayerEvent.PlayerLoggedInEvent(p));
            PartyBotEntity r = bot(p, CharacterType.RHALIX);
            if (r != null) r.inventory.addItem(new ItemStack(Items.LAPIS_LAZULI, 4));   // (picked up after the login: never saved, so gone with the crash)
            crashAndRestart(p, disk, "logoutcrash");                            // (no save since the login)
        }), 100));
        l.add(new Step(null, () -> UiTest.server(p -> {
            PartyBotEntity r = bot(p, CharacterType.RHALIX);
            boolean ok = count(p, CharacterType.RHALIX) == 1 && r != null && bag(r, Items.DIAMOND) == 13 && same(p);
            log("logoutcrash after restart: rhalix bots=" + count(p, CharacterType.RHALIX) + (r == null ? "" : " bag diamonds=" + bag(r, Items.DIAMOND) + " lapis=" + bag(r, Items.LAPIS_LAZULI))
                    + " | owned " + counts(p) + " -> " + (ok ? "PASS" : "FAIL"));
        }), 10));

        // ---- 5. a Journey teammate's finds
        l.add(new Step(null, () -> UiTest.server(p -> {
            PartyBotEntity j = PartyBots.spawn(p, CharacterType.MAX, new CompoundTag());
            if (j == null) { log("journey FAIL no bot"); return; }
            j.getPersistentData().putBoolean("xw_journey_bot", true);
            j.inventory.addItem(new ItemStack(Items.LAPIS_LAZULI, 6));
            N[3] = owned(p, "minecraft:lapis_lazuli");
            CompoundTag disk = saveAndReadBack(p, "journey");
            crashAndRestart(p, disk, "journey");
        }), 100));
        l.add(new Step(null, () -> UiTest.server(p -> {
            int onYou = walk(p.getInventory().save(new ListTag()), "minecraft:lapis_lazuli");
            boolean ok = count(p, CharacterType.MAX) == 0 && onYou == 6 && same(p);
            log("journey after restart: journey bots=" + count(p, CharacterType.MAX) + " lapis on you=" + onYou + " | owned " + counts(p) + " -> " + (ok ? "PASS" : "FAIL"));
        }), 10));

        l.add(new Step(null, () -> UiTest.server(p -> {
            clear(p);
            for (int i = 0; i < p.getInventory().getContainerSize(); i++) if (p.getInventory().getItem(i).is(Items.LAPIS_LAZULI)) p.getInventory().setItem(i, ItemStack.EMPTY);
            log("DONE");
        }), 20));
    }
}

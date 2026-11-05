package com.xenowarriors.client.test;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.bot.BotCrafting;
import com.xenowarriors.bot.PartyBotEntity;
import com.xenowarriors.bot.PartyBots;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.client.UiTest.Step;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * UI tests for the bot teammates' round 5 (focus "bots5", or one part: bots5_keep, bots5_follow, bots5_furnace,
 * bots5_craft, bots5_share, bots5_shield, bots5_gather, bots5_ultima, bots5_yuri, bots5_vehicle). Every result is an
 * "XW-TEST bots5 ..." line (the bots' own lines come from PartyBots.testLog with the same prefix).
 */
public final class Bots5Tests {
    private Bots5Tests() {}

    private static int X, Y, Z;
    private static final String TAG = "xw_b5";

    static void log(String s) { XenoWarriors.LOGGER.info("XW-TEST bots5 " + s); }

    static ServerPlayer sp() {
        Minecraft mc = Minecraft.getInstance();
        var s = mc.getSingleplayerServer();
        return s == null || mc.player == null ? null : s.getPlayerList().getPlayer(mc.player.getUUID());
    }

    static void server(Consumer<ServerPlayer> f) {
        var s = Minecraft.getInstance().getSingleplayerServer();
        if (s != null) s.execute(() -> { ServerPlayer p = sp(); if (p != null) try { f.accept(p); } catch (Throwable t) { log("step error " + t); XenoWarriors.LOGGER.error("bots5", t); } });
    }

    private static String f1(double d) { return String.format(Locale.ROOT, "%.1f", d); }
    static String id(ItemStack s) { return s.isEmpty() ? "empty" : net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(s.getItem()).getPath(); }

    static PartyBotEntity bot(ServerPlayer p, CharacterType t) {
        if (p == null) return null;
        for (PartyBotEntity b : PartyBots.bots(p)) if (b.character() == t) return b;
        return null;
    }

    static void clearAll(ServerPlayer p) {
        for (PartyBotEntity b : p.serverLevel().getEntitiesOfClass(PartyBotEntity.class, p.getBoundingBox().inflate(80))) { PartyBots.forgetFake(b.getUUID()); b.discard(); }
        for (PartyBotEntity b : PartyBots.bots(p)) { PartyBots.forgetFake(b.getUUID()); b.discard(); }
        p.serverLevel().getEntitiesOfClass(Entity.class, p.getBoundingBox().inflate(80), e -> e.getTags().contains(TAG) || e instanceof ItemEntity || e instanceof net.minecraft.world.entity.projectile.AbstractArrow).forEach(Entity::discard);
        for (String c : new String[]{"kaeru", "rhalix", "ultima", "yuri", "mikiko"}) p.getPersistentData().remove("xw_bot_rejoin_" + c);
    }

    /** A fresh bot here with nothing on it, tough (200 HP), in Neutral. */
    static PartyBotEntity spawnBot(ServerPlayer p, CharacterType t, double x, double z) {
        PartyBotEntity b = bot(p, t);
        if (b == null) b = PartyBots.spawn(p, t, null);
        if (b == null) { log("spawn " + t.getId() + " FAILED"); return null; }
        b.inventory.clearContent();
        for (EquipmentSlot es : EquipmentSlot.values()) b.setItemSlot(es, ItemStack.EMPTY);
        b.moveTo(x, Y, z, 0, 0);
        b.setTarget(null);
        b.setMode(PartyBotEntity.NEUTRAL);
        var hp = b.getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) hp.setBaseValue(200);
        b.setHealth(b.getMaxHealth());
        b.lastHurtAt = -100000;
        BotCrafting.clearQuiet(b);
        return b;
    }

    static void fill(ServerLevel l, int x0, int y0, int z0, int x1, int y1, int z1, BlockState s) {
        for (BlockPos q : BlockPos.betweenClosed(Math.min(x0, x1), Math.min(y0, y1), Math.min(z0, z1), Math.max(x0, x1), Math.max(y0, y1), Math.max(z0, z1))) l.setBlock(q, s, 2);
    }

    static <T extends Mob> T mob(ServerPlayer p, EntityType<T> type, double x, double z, boolean ai, float hp) {
        T m = type.create(p.serverLevel());
        if (m == null) return null;
        m.moveTo(x, Y, z, 0, 0);
        m.setNoAi(!ai);
        m.setPersistenceRequired();
        m.addTag(TAG);
        m.getPersistentData().putInt("xw_lvl", 0);
        p.serverLevel().addFreshEntity(m);
        var a = m.getAttribute(Attributes.MAX_HEALTH);
        if (a != null) { a.setBaseValue(hp); m.setHealth(hp); }
        return m;
    }

    /** A sky platform 61x61 over the start, the owner on it, no mobs. */
    private static void arena(ServerPlayer p) {
        ServerLevel lv = p.serverLevel();
        X = p.getBlockX(); Z = p.getBlockZ(); Y = Math.min(lv.getMaxBuildHeight() - 30, p.getBlockY() + 40);
        fill(lv, X - 30, Y, Z - 30, X + 30, Y + 10, Z + 30, Blocks.AIR.defaultBlockState());
        fill(lv, X - 30, Y - 1, Z - 30, X + 30, Y - 1, Z + 30, Blocks.SMOOTH_STONE.defaultBlockState());
        p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        p.getAbilities().flying = false; p.onUpdateAbilities();
        p.teleportTo(lv, X + 0.5, Y, Z + 0.5, 0, 20);
        lv.setDayTime(6000);
        clearAll(p);
        log("arena at " + X + " " + Y + " " + Z);
    }

    private static void home(ServerPlayer p, float yaw, float pitch) {
        p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        p.getAbilities().flying = false; p.onUpdateAbilities();
        p.teleportTo(p.server.overworld(), X + 0.5, Y, Z + 0.5, yaw, pitch);
    }

    public static void build(Minecraft mc, List<Step> l, String focus) {
        boolean all = focus.equals("bots5");
        l.add(new Step(null, () -> { mc.getToasts().clear(); mc.setScreen(null); mc.options.hideGui = false; server(Bots5Tests::arena); }, 40));
        if (all || focus.equals("bots5_keep")) keep(mc, l);
        if (all || focus.equals("bots5_follow")) follow(mc, l);
        if (all || focus.equals("bots5_furnace")) furnace(mc, l);
        if (all || focus.equals("bots5_craft")) craft(mc, l);
        if (all || focus.equals("bots5_share")) share(mc, l);
        if (all || focus.equals("bots5_shield")) shield(mc, l);
        if (all || focus.equals("bots5_gather")) gather(mc, l);
        if (all || focus.equals("bots5_ultima")) ultima(mc, l);
        if (all || focus.equals("bots5_yuri")) yuri(mc, l);
        if (all || focus.equals("bots5_vehicle")) vehicle(mc, l);
        l.add(new Step(null, () -> server(p -> { clearAll(p); log("done"); }), 10));
    }

    // ---------------------------------------------------------------- 8. inventories: never lost, never doubled

    /** Everything it has, counted: bag, hands, armour, and its ender chest (kept with it). */
    static int total(PartyBotEntity b) {
        if (b == null) return -1;
        int n = 0;
        for (int i = 0; i < b.inventory.getContainerSize(); i++) n += b.inventory.getItem(i).getCount();
        for (EquipmentSlot es : EquipmentSlot.values()) n += b.getItemBySlot(es).getCount();
        ListTag ender = b.getPersistentData().getCompound("xw_inv_kept").getList("ender", 10);
        for (int i = 0; i < ender.size(); i++) n += ender.getCompound(i).getByte("Count");
        return n;
    }

    static String things(PartyBotEntity b) {
        if (b == null) return "NO BOT";
        return "total=" + total(b) + " hand=" + id(b.getMainHandItem()) + " off=" + id(b.getOffhandItem()) + " chest=" + id(b.getItemBySlot(EquipmentSlot.CHEST))
                + " iron=" + b.inventory.countItem(Items.IRON_INGOT) + " bread=" + b.inventory.countItem(Items.BREAD) + " gapple=" + b.inventory.countItem(Items.GOLDEN_APPLE)
                + " diamond_sword=" + (b.inventory.countItem(Items.DIAMOND_SWORD) + (b.getMainHandItem().is(Items.DIAMOND_SWORD) ? 1 : 0))
                + " ender=" + b.getPersistentData().getCompound("xw_inv_kept").getList("ender", 10).size() + " xp=" + b.getPersistentData().getCompound("xw_inv_kept").getInt("xp_total");
    }

    static int saved(ServerPlayer p, String key) {
        CompoundTag t = com.xenowarriors.character.CharacterInventories.saved(p, key);
        int n = 0;
        ListTag inv = t.getList("inv", 10); for (int i = 0; i < inv.size(); i++) n += inv.getCompound(i).getByte("Count");
        ListTag en = t.getList("ender", 10); for (int i = 0; i < en.size(); i++) n += en.getCompound(i).getByte("Count");
        return n;
    }

    private static void kit(PartyBotEntity b) {
        b.inventory.clearContent();
        for (EquipmentSlot es : EquipmentSlot.values()) b.setItemSlot(es, ItemStack.EMPTY);
        b.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_SWORD));
        b.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        b.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET)); b.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        b.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS)); b.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
        b.inventory.addItem(new ItemStack(Items.IRON_INGOT, 5)); b.inventory.addItem(new ItemStack(Items.BREAD, 3)); b.inventory.addItem(new ItemStack(Items.GOLDEN_APPLE, 1));
        CompoundTag kept = new CompoundTag();
        ListTag ender = new ListTag();
        CompoundTag em = new ItemStack(Items.EMERALD, 3).save(new CompoundTag()); em.putByte("Slot", (byte) 0); ender.add(em);
        kept.put("ender", ender); kept.putInt("xp_total", 50);
        b.getPersistentData().put("xw_inv_kept", kept);
        b.handPicked = b.tickCount;                                              // (keeps the sword in hand for the count)
    }

    private static void platform(ServerLevel l, int x, int y, int z) {
        fill(l, x - 4, y, z - 4, x + 4, y + 4, z + 4, Blocks.AIR.defaultBlockState());
        fill(l, x - 4, y - 1, z - 4, x + 4, y - 1, z + 4, Blocks.STONE.defaultBlockState());
    }

    private static void keep(Minecraft mc, List<Step> l) {
        final int[] before = new int[1];
        l.add(new Step(null, () -> server(p -> {
            home(p, 0, 10);
            PartyBotEntity b = spawnBot(p, CharacterType.KAERU, X + 3.5, Z + 0.5);
            if (b == null) return;
            kit(b);
            before[0] = total(b);
            log("keep before: " + things(b));
        }), 20));
        // far away in the same world (a /tp, fast travel, an ender pearl, a respawn at spawn): 3000 blocks
        l.add(new Step(null, () -> server(p -> { platform(p.serverLevel(), X + 3000, Y, Z); p.teleportTo(p.serverLevel(), X + 3000.5, Y, Z + 0.5, 0, 10); }), 200));
        l.add(new Step("bots5_keep_far", () -> server(p -> {
            PartyBotEntity b = bot(p, CharacterType.KAERU);
            log("keep far 3000: bot with you=" + (b != null) + (b == null ? "" : " dist=" + f1(b.distanceTo(p)) + " catchUps=" + b.catchUps) + " waiting=" + PartyBots.waitingFor(p.getUUID())
                    + " " + things(b) + " same=" + (total(b) == before[0]));
        }), 10));
        l.add(new Step(null, () -> server(p -> home(p, 0, 10)), 200));
        l.add(new Step(null, () -> server(p -> {
            PartyBotEntity b = bot(p, CharacterType.KAERU);
            log("keep back home: bot=" + (b != null) + (b == null ? "" : " dist=" + f1(b.distanceTo(p))) + " " + things(b) + " same=" + (total(b) == before[0]));
        }), 10));
        // just past where chunks stop ticking (the bot would stand frozen there): 260 blocks
        l.add(new Step(null, () -> server(p -> { platform(p.serverLevel(), X + 260, Y, Z); p.teleportTo(p.serverLevel(), X + 260.5, Y, Z + 0.5, 0, 10); }), 100));
        l.add(new Step(null, () -> server(p -> {
            PartyBotEntity b = bot(p, CharacterType.KAERU);
            log("keep frozen-band 260: bot=" + (b != null) + (b == null ? "" : " dist=" + f1(b.distanceTo(p)) + " catchUps=" + b.catchUps) + " " + things(b) + " same=" + (total(b) == before[0]));
            home(p, 0, 10);
        }), 120));
        // the owner flies off on elytra-like height: a walker is put on the ground under them
        l.add(new Step(null, () -> server(p -> {
            platform(p.serverLevel(), X + 120, Y, Z);
            p.getAbilities().flying = true; p.onUpdateAbilities();
            p.teleportTo(p.serverLevel(), X + 120.5, Y + 20, Z + 0.5, 0, 10);
        }), 60));
        l.add(new Step(null, () -> server(p -> {
            PartyBotEntity b = bot(p, CharacterType.KAERU);
            log("keep owner flying 120 off, 20 up: bot=" + (b != null) + (b == null ? "" : " dist=" + f1(b.distanceTo(p)) + " under you=" + (Math.abs(b.getX() - p.getX()) < 6 && Math.abs(b.getZ() - p.getZ()) < 6) + " y=" + f1(b.getY() - Y)) + " same=" + (total(b) == before[0]));
            home(p, 0, 10);
        }), 60));
        // another world: the Nether and back
        l.add(new Step(null, () -> server(p -> {
            ServerLevel n = p.server.getLevel(Level.NETHER);
            if (n == null) { log("keep nether: no nether"); return; }
            fill(n, -3, 100, -3, 3, 104, 3, Blocks.AIR.defaultBlockState());
            fill(n, -3, 99, -3, 3, 99, 3, Blocks.OBSIDIAN.defaultBlockState());
            p.teleportTo(n, 0.5, 100, 0.5, 0, 10);
        }), 120));
        l.add(new Step(null, () -> server(p -> {
            PartyBotEntity b = bot(p, CharacterType.KAERU);
            log("keep nether: bot=" + (b != null) + (b == null ? "" : " in " + b.level().dimension().location().getPath() + " dist=" + f1(b.distanceTo(p))) + " " + things(b) + " same=" + (total(b) == before[0]));
            home(p, 0, 10);
        }), 120));
        l.add(new Step(null, () -> server(p -> {
            PartyBotEntity b = bot(p, CharacterType.KAERU);
            log("keep back from nether: bot=" + (b != null) + " " + things(b) + " same=" + (total(b) == before[0]));
            // a Journey puts bots away and back
            PartyBots.stashAll(p);
            log("keep journey stashed: bots now=" + PartyBots.bots(p).size());
            PartyBots.unstash(p);
        }), 20));
        l.add(new Step(null, () -> server(p -> {
            PartyBotEntity b = bot(p, CharacterType.KAERU);
            log("keep journey back: " + things(b) + " same=" + (total(b) == before[0]));
            // the owner logs out and in
            PartyBots.onLogout(new net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent(p));
            log("keep relog out: bots now=" + PartyBots.bots(p).size());
            PartyBots.onLogin(new net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent(p));
        }), 20));
        l.add(new Step(null, () -> server(p -> {
            PartyBotEntity b = bot(p, CharacterType.KAERU);
            log("keep relog back: " + things(b) + " same=" + (total(b) == before[0]));
            if (b == null) return;
            b.moveTo(p.getX() + 2, p.getY(), p.getZ() + 1);
            // its bag open while it changes what it holds (a fight): the gear column is the bot's own, never a stale copy
            PartyBots.openBag(p, b);
            b.inventory.addItem(new ItemStack(Items.IRON_SWORD));
            int slot = -1; for (int i = 0; i < 36; i++) if (b.inventory.getItem(i).is(Items.IRON_SWORD)) slot = i;
            ItemStack hand = b.getMainHandItem().copy();
            b.setItemSlot(EquipmentSlot.MAINHAND, b.inventory.getItem(slot).copy());   // (what BotGear's swap does mid-fight)
            b.inventory.setItem(slot, hand);
            var menu = p.containerMenu;
            log("keep bag open: menu=" + menu.getClass().getSimpleName() + " gear hand shows=" + id(menu.getSlot(5).getItem()) + " bot hand=" + id(b.getMainHandItem()));
            menu.clicked(5, 0, net.minecraft.world.inventory.ClickType.PICKUP, p);    // take what's in its hand
            String carried = id(menu.getCarried());
            menu.clicked(5, 0, net.minecraft.world.inventory.ClickType.PICKUP, p);    // and put it back
            int swords = b.inventory.countItem(Items.DIAMOND_SWORD) + (b.getMainHandItem().is(Items.DIAMOND_SWORD) ? 1 : 0), irons = b.inventory.countItem(Items.IRON_SWORD) + (b.getMainHandItem().is(Items.IRON_SWORD) ? 1 : 0);
            log("keep bag open swap: picked up=" + carried + " (should be iron_sword) diamond_swords=" + swords + " iron_swords=" + irons + " carried after=" + id(menu.getCarried()) + " no dupe=" + (swords == 1 && irons == 1));
            before[0] = total(b);
            // and it bleeds out with the bag still open
            b.hurt(p.damageSources().generic(), 10000F);
        }), 10));
        l.add(new Step(null, () -> server(p -> {
            PartyBotEntity b = bot(p, CharacterType.KAERU);
            log("keep downed with bag open: downed=" + (b != null && b.downed()) + " menu still=" + p.containerMenu.getClass().getSimpleName());
            if (b != null) PartyBots.bleedOutForTest(b);
        }), 10));
        l.add(new Step(null, () -> server(p -> {
            log("keep bled out: bot=" + (bot(p, CharacterType.KAERU) != null) + " menu=" + p.containerMenu.getClass().getSimpleName() + " saved for kaeru=" + saved(p, "kaeru") + " (had " + before[0] + ")");
            p.getPersistentData().remove("xw_bot_rejoin_kaeru");
            PartyBots.add(p, new String[]{"kaeru"});                              // re-added from the pick screen
        }), 10));
        l.add(new Step(null, () -> server(p -> {
            PartyBotEntity b = bot(p, CharacterType.KAERU);
            log("keep re-added after bleed out: " + things(b) + " same=" + (total(b) == before[0]) + " saved left=" + saved(p, "kaeru"));
            if (b != null) b.hurt(p.damageSources().fellOutOfWorld(), 100000F);  // the void: really dies
        }), 10));
        l.add(new Step(null, () -> server(p -> {
            log("keep void death: bot=" + (bot(p, CharacterType.KAERU) != null) + " saved for kaeru=" + saved(p, "kaeru"));
            p.getPersistentData().remove("xw_bot_rejoin_kaeru");
            PartyBots.add(p, new String[]{"kaeru"});
        }), 10));
        l.add(new Step(null, () -> server(p -> {
            PartyBotEntity b = bot(p, CharacterType.KAERU);
            log("keep re-added after void: " + things(b) + " same=" + (total(b) == before[0]) + " saved left=" + saved(p, "kaeru"));
            if (b != null) PartyBots.action(p, b.getId(), PartyBots.ACT_DISMISS, 0);
            com.xenowarriors.character.CharacterInventories.checkOut(p, "kaeru");   // (tidy)
            p.closeContainer();
        }), 10));
    }

    // ---------------------------------------------------------------- 9. fliers range further; everyone back when you're attacked

    private static void follow(Minecraft mc, List<Step> l) {
        final int[] t0 = new int[1];
        l.add(new Step(null, () -> server(p -> {
            clearAll(p); home(p, 90, 10);
            PartyBotEntity r = spawnBot(p, CharacterType.RHALIX, X + 22.5, Z + 0.5);
            PartyBotEntity k = spawnBot(p, CharacterType.KAERU, X - 22.5, Z + 0.5);
            log("follow start: rhalix (flier) " + (r == null ? "-" : f1(r.distanceTo(p))) + " kaeru (walker) " + (k == null ? "-" : f1(k.distanceTo(p))) + " leash flier=" + (r == null ? "-" : f1(r.leash())) + " walker=" + (k == null ? "-" : f1(k.leash())));
        }), 160));
        l.add(new Step("bots5_flier_range", () -> server(p -> {
            PartyBotEntity r = bot(p, CharacterType.RHALIX), k = bot(p, CharacterType.KAERU);
            log("follow after 8s calm: rhalix dist=" + (r == null ? "-" : f1(r.distanceTo(p))) + " (not called back: " + (r != null && r.distanceTo(p) > 6 && !r.goalsForTest().contains("FollowOwner")) + ", goals " + (r == null ? "-" : r.goalsForTest()) + ") kaeru dist=" + (k == null ? "-" : f1(k.distanceTo(p))) + " (came back: " + (k != null && k.distanceTo(p) < 10) + ")");
            if (r != null) { r.moveTo(X + 24.5, Y, Z + 0.5); r.getNavigation().stop(); }
            // the owner is attacked: a (harmless, untouchable) zombie hits them
            p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            var zb = mob(p, EntityType.ZOMBIE, X - 12.5, Z - 12.5, false, 100F);
            if (zb != null) { zb.setInvulnerable(true); p.hurt(p.damageSources().mobAttack(zb), 2F); }
            t0[0] = r == null ? 0 : r.tickCount;
            log("follow owner attacked: inDanger=" + PartyBotEntity.ownerInDanger(p) + " rhalix leash now=" + (r == null ? "-" : f1(r.leash())));
        }), 20));
        for (int i = 1; i <= 4; i++) {
            final int n = i;
            l.add(new Step(null, () -> server(p -> {
                PartyBotEntity r = bot(p, CharacterType.RHALIX);
                log("follow attacked +" + n + "s: rhalix dist=" + (r == null ? "-" : f1(r.distanceTo(p))) + " flying=" + (r != null && r.selfFlyingForTest()));
            }), 20));
        }
        l.add(new Step(null, () -> server(p -> {
            p.setHealth(p.getMaxHealth());
            p.serverLevel().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(40), m -> m.getTags().contains(TAG)).forEach(Entity::discard);
            home(p, 0, 10);
        }), 10));
    }

    // ---------------------------------------------------------------- 1. furnaces

    private static void furnace(Minecraft mc, List<Step> l) {
        l.add(new Step(null, () -> server(p -> {
            clearAll(p); home(p, 180, 25);
            ServerLevel lv = p.serverLevel();
            lv.setBlock(new BlockPos(X + 4, Y, Z - 4), Blocks.BLAST_FURNACE.defaultBlockState(), 3);
            lv.setBlock(new BlockPos(X + 2, Y, Z - 4), Blocks.SMOKER.defaultBlockState(), 3);
            lv.setBlock(new BlockPos(X, Y, Z - 4), Blocks.FURNACE.defaultBlockState(), 3);
            lv.setBlock(new BlockPos(X - 2, Y, Z - 4), Blocks.FURNACE.defaultBlockState(), 3);     // someone's: cobblestone in it
            if (lv.getBlockEntity(new BlockPos(X - 2, Y, Z - 4)) instanceof AbstractFurnaceBlockEntity f) f.setItem(0, new ItemStack(Items.COBBLESTONE, 3));
            PartyBotEntity b = spawnBot(p, CharacterType.KAERU, X + 1.5, Z - 1.5);
            if (b == null) return;
            b.inventory.addItem(new ItemStack(Items.RAW_IRON, 5));
            b.inventory.addItem(new ItemStack(Items.BEEF, 4));
            b.inventory.addItem(new ItemStack(Items.COAL, 3));
            b.inventory.addItem(new ItemStack(Items.COBBLESTONE, 10));          // (not worth smelting: stays)
            log("furnace start: kaeru has raw_iron 5, beef 4, coal 3; blast furnace, smoker, furnace free; one furnace busy with 3 cobblestone");
        }), 200));
        l.add(new Step("bots5_furnace", () -> server(p -> log("furnace after 10s: " + furnaces(p))), 10));
        l.add(new Step(null, () -> server(p -> log("furnace after 15s: " + furnaces(p))), 500));
        l.add(new Step(null, () -> server(p -> {
            PartyBotEntity b = bot(p, CharacterType.KAERU);
            log("furnace after 40s: " + furnaces(p) + " | kaeru bag: iron_ingot=" + (b == null ? -1 : b.inventory.countItem(Items.IRON_INGOT)) + " cooked_beef=" + (b == null ? -1 : b.inventory.countItem(Items.COOKED_BEEF))
                    + " raw_iron=" + (b == null ? -1 : b.inventory.countItem(Items.RAW_IRON)) + " beef=" + (b == null ? -1 : b.inventory.countItem(Items.BEEF)) + " coal=" + (b == null ? -1 : b.inventory.countItem(Items.COAL))
                    + " cobblestone=" + (b == null ? -1 : b.inventory.countItem(Items.COBBLESTONE)));
        }), 400));
        l.add(new Step(null, () -> server(p -> {
            PartyBotEntity b = bot(p, CharacterType.KAERU);
            log("furnace after 60s: " + furnaces(p) + " | kaeru bag: iron_ingot=" + (b == null ? -1 : b.inventory.countItem(Items.IRON_INGOT)) + " cooked_beef=" + (b == null ? -1 : b.inventory.countItem(Items.COOKED_BEEF)));
            for (int dx = -2; dx <= 4; dx += 2) p.serverLevel().setBlock(new BlockPos(X + dx, Y, Z - 4), Blocks.AIR.defaultBlockState(), 3);
        }), 10));
    }

    private static String furnaces(ServerPlayer p) {
        StringBuilder sb = new StringBuilder();
        for (int dx = 4; dx >= -2; dx -= 2) {
            BlockPos at = new BlockPos(X + dx, Y, Z - 4);
            if (p.serverLevel().getBlockEntity(at) instanceof AbstractFurnaceBlockEntity f)
                sb.append(net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(f.getBlockState().getBlock()).getPath()).append(dx == -2 ? "(busy)" : "").append("[in ").append(f.getItem(0).getCount()).append(id(f.getItem(0)))
                        .append(" fuel ").append(f.getItem(1).getCount()).append(id(f.getItem(1))).append(" out ").append(f.getItem(2).getCount()).append(id(f.getItem(2))).append("] ");
        }
        return sb.toString().trim();
    }

    // ---------------------------------------------------------------- 2 and 5. crafting: only when safe, rarer repairs, its own table, not with a full bag

    private static void craft(Minecraft mc, List<Step> l) {
        l.add(new Step(null, () -> server(p -> {
            clearAll(p); home(p, 0, 10);
            PartyBotEntity b = spawnBot(p, CharacterType.KAERU, X + 2.5, Z + 2.5);
            if (b == null) return;
            b.inventory.addItem(new ItemStack(Items.IRON_INGOT, 8)); b.inventory.addItem(new ItemStack(Items.CRAFTING_TABLE));
            mob(p, EntityType.ZOMBIE, X + 8.5, Z + 4.5, false, 300F);
            log("craft danger: zombie 6 blocks off, danger=" + com.xenowarriors.bot.BotErrands.danger(b));
        }), 230));
        l.add(new Step(null, () -> server(p -> {
            PartyBotEntity b = bot(p, CharacterType.KAERU);
            if (b == null) return;
            log("craft with an enemy about (11 s): offer token=" + BotCrafting.offerToken(b) + " looks held back=" + b.offersHeld + " (no offer: " + (BotCrafting.offerToken(b) == 0) + ")");
            p.serverLevel().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(40), m -> m.getTags().contains(TAG)).forEach(Entity::discard);
            // the owner's fighting: they just hit something
            b.offersHeld = 0;
        }), 230));
        l.add(new Step(null, () -> server(p -> {
            PartyBotEntity b = bot(p, CharacterType.KAERU);
            if (b == null) return;
            log("craft once safe (11 s): crafted by itself=" + b.autoCrafted + " (crafted: " + (b.autoCrafted > 0) + ", no offer: " + (BotCrafting.offerToken(b) == 0) + ") danger=" + com.xenowarriors.bot.BotErrands.danger(b));
            // repairs: 60% worn is no longer worth a word; 80% is, then not again for 15 minutes
            BotCrafting.clearQuiet(b);
            b.inventory.clearContent();
            ItemStack sword = new ItemStack(Items.IRON_SWORD); sword.setDamageValue(150);
            b.inventory.addItem(sword); b.inventory.addItem(new ItemStack(Items.IRON_INGOT, 2)); b.inventory.addItem(new ItemStack(Items.ANVIL));
            long rep60 = BotCrafting.ideas(b).stream().filter(i -> !i.kind.equals("craft")).count();
            b.inventory.getItem(0).setDamageValue(200);
            long rep80 = BotCrafting.ideas(b).stream().filter(i -> !i.kind.equals("craft")).count();
            var first = BotCrafting.suggest(b, p, false);
            BotCrafting.forget(b.getUUID());
            var second = BotCrafting.suggest(b, p, false);
            log("craft repair: ideas at 60% worn=" + rep60 + " at 80% worn=" + rep80 + " first ask=" + (first == null ? "none" : first.kind) + " asked again right after=" + (second == null ? "none" : second.kind) + " cooldown(min)=" + BotCrafting.REPAIR_COOLDOWN / 1200);
            BotCrafting.forget(b.getUUID());
            // its own crafting table: 8 iron and 4 planks, no table anywhere
            BotCrafting.clearQuiet(b);
            b.inventory.clearContent();
            b.inventory.addItem(new ItemStack(Items.IRON_INGOT, 8)); b.inventory.addItem(new ItemStack(Items.OAK_PLANKS, 4));
            var ideas = BotCrafting.ideas(b);
            StringBuilder sb = new StringBuilder();
            for (var i : ideas) sb.append(id(i.result)).append(i.makesTable ? "(+table)" : "").append(i.missing > 0 ? "(-" + i.missing + ")" : "").append(' ');
            log("craft no table about, 4 planks: ideas " + sb.toString().trim());
            var pick = BotCrafting.suggest(b, p, true);
            log("craft suggested: " + (pick == null ? "nothing" : id(pick.result) + " makesTable=" + pick.makesTable));
            if (pick != null) p.server.getCommands().performPrefixedCommand(p.createCommandSourceStack(), "xenobot craft " + b.getId() + " " + BotCrafting.offerToken(b));
        }), 20));
        l.add(new Step(null, () -> mc.setScreen(new net.minecraft.client.gui.screens.ChatScreen("")), 10));
        l.add(new Step("bots5_craft_table", () -> {}, 3));
        l.add(new Step(null, () -> { mc.setScreen(null); server(p -> {
            PartyBotEntity b = bot(p, CharacterType.KAERU);
            if (b == null) return;
            log("craft after OK: tablesMade=" + b.tablesMade + " chest=" + id(b.getItemBySlot(EquipmentSlot.CHEST)) + " table in bag=" + b.inventory.countItem(Items.CRAFTING_TABLE) + " planks left=" + b.inventory.countItem(Items.OAK_PLANKS));
            // a full bag: nothing new gets made
            BotCrafting.clearQuiet(b);
            b.inventory.clearContent();
            b.inventory.addItem(new ItemStack(Items.IRON_INGOT, 8)); b.inventory.addItem(new ItemStack(Items.CRAFTING_TABLE));
            for (int i = 0; i < 36; i++) if (b.inventory.getItem(i).isEmpty()) b.inventory.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
            long crafts = BotCrafting.ideas(b).stream().filter(i -> i.kind.equals("craft")).count();
            b.inventory.setItem(35, ItemStack.EMPTY);
            long crafts1 = BotCrafting.ideas(b).stream().filter(i -> i.kind.equals("craft")).count();
            log("craft full bag: craft ideas=" + crafts + " (none: " + (crafts == 0) + "), with one slot free=" + crafts1);
        }); }, 10));
    }

    // ---------------------------------------------------------------- 3 and 7. handing things over

    private static void share(Minecraft mc, List<Step> l) {
        l.add(new Step(null, () -> server(p -> {
            clearAll(p); home(p, 180, 30);
            PartyBotEntity r = spawnBot(p, CharacterType.RHALIX, X + 3.5, Z - 3.5), k = spawnBot(p, CharacterType.KAERU, X - 3.5, Z - 3.5);
            if (r == null || k == null) return;
            r.inventory.addItem(new ItemStack(Items.DIAMOND_SWORD)); r.inventory.addItem(new ItemStack(Items.IRON_SWORD)); r.inventory.addItem(new ItemStack(Items.STONE_SWORD));
            r.inventory.addItem(new ItemStack(Items.SHIELD)); r.inventory.addItem(new ItemStack(Items.SHIELD));
            r.inventory.addItem(new ItemStack(Items.BREAD, 10)); r.inventory.addItem(new ItemStack(Items.IRON_INGOT, 1));
            k.inventory.addItem(new ItemStack(Items.WOODEN_SWORD)); k.inventory.addItem(new ItemStack(Items.IRON_INGOT, 3)); k.inventory.addItem(new ItemStack(Items.CRAFTING_TABLE));
            k.setHealth(k.getMaxHealth() * 0.65F);
            var fox = EntityType.FOX.create(p.serverLevel());
            if (fox != null) {
                fox.moveTo(X + 0.5, Y, Z - 6.5, 0, 0); fox.addTag(TAG); fox.setPersistenceRequired(); fox.setNoAi(true);
                p.serverLevel().addFreshEntity(fox);
                com.xenowarriors.fox.FoxPets.tameNow(p, fox);                    // (a real pet: one with a record)
            }
            log("share start: rhalix has diamond+iron+stone swords, 2 shields, 10 bread, 1 iron; kaeru has a wooden sword, 3 iron (boots need 4), 65% HP, no food; a fox with nothing in its mouth");
        }), 400));
        l.add(new Step("bots5_share", () -> server(p -> log("share after 20s: " + shareState(p))), 10));
        l.add(new Step(null, () -> server(p -> log("share after 40s: " + shareState(p))), 400));
        l.add(new Step(null, () -> server(p -> log("share after 60s: " + shareState(p))), 400));
        l.add(new Step(null, () -> server(p -> log("share after 80s: " + shareState(p))), 10));
    }

    private static String shareState(ServerPlayer p) {
        PartyBotEntity r = bot(p, CharacterType.RHALIX), k = bot(p, CharacterType.KAERU);
        var foxes = p.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.animal.Fox.class, p.getBoundingBox().inflate(40), f -> f.getTags().contains(TAG));
        if (r == null || k == null) return "bots missing";
        StringBuilder kb = new StringBuilder(), rb = new StringBuilder();
        for (int i = 0; i < 36; i++) { ItemStack s = k.inventory.getItem(i); if (!s.isEmpty()) kb.append(s.getCount()).append(id(s)).append(' '); }
        for (int i = 0; i < 36; i++) { ItemStack s = r.inventory.getItem(i); if (!s.isEmpty()) rb.append(s.getCount()).append(id(s)).append(' '); }
        return "kaeru[hand " + id(k.getMainHandItem()) + " off " + id(k.getOffhandItem()) + " feet " + id(k.getItemBySlot(EquipmentSlot.FEET)) + " hp " + Math.round(100 * k.getHealth() / k.getMaxHealth()) + "% bag " + kb.toString().trim()
                + "] rhalix[hand " + id(r.getMainHandItem()) + " off " + id(r.getOffhandItem()) + " bag " + rb.toString().trim() + "] fox mouth=" + (foxes.isEmpty() ? "-" : id(foxes.get(0).getMainHandItem()));
    }

    // ---------------------------------------------------------------- 4. shields

    private static void shield(Minecraft mc, List<Step> l) {
        final float[] hp0 = new float[1];
        l.add(new Step(null, () -> server(p -> {
            clearAll(p); home(p, 90, 20);
            p.teleportTo(p.serverLevel(), X - 6.5, Y, Z + 4.5, -90, 10);
            PartyBotEntity k = spawnBot(p, CharacterType.KAERU, X + 0.5, Z + 4.5);
            if (k == null) return;
            ItemStack worn = new ItemStack(Items.SHIELD); worn.setDamageValue(250);
            ItemStack good = new ItemStack(Items.SHIELD); good.enchant(net.minecraft.world.item.enchantment.Enchantments.UNBREAKING, 3);
            k.inventory.addItem(worn); k.inventory.addItem(good); k.inventory.addItem(new ItemStack(Items.IRON_SWORD));
        }), 60));
        l.add(new Step(null, () -> server(p -> {
            PartyBotEntity k = bot(p, CharacterType.KAERU);
            if (k == null) return;
            ItemStack off = k.getOffhandItem();
            log("shield picked: " + id(off) + " unbreaking=" + off.getEnchantmentLevel(net.minecraft.world.item.enchantment.Enchantments.UNBREAKING) + " damage=" + off.getDamageValue() + " swaps=" + k.shieldSwaps);
            var sk = mob(p, EntityType.SKELETON, X + 0.5, Z + 18.5, true, 400F);
            if (sk != null) { sk.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW)); sk.setTarget(k); }
            hp0[0] = k.getHealth();
        }), 100));
        l.add(new Step("bots5_shield", () -> server(p -> {
            PartyBotEntity k = bot(p, CharacterType.KAERU);
            if (k != null) log("shield 5s under fire: guards raised=" + k.guards + " blows blocked=" + k.shieldBlocks + " hp lost=" + f1(hp0[0] - k.getHealth()) + " blocking now=" + k.isBlocking());
        }), 100));
        l.add(new Step(null, () -> server(p -> {
            PartyBotEntity k = bot(p, CharacterType.KAERU);
            if (k != null) log("shield 10s: guards raised=" + k.guards + " blows blocked=" + k.shieldBlocks + " hp lost=" + f1(hp0[0] - k.getHealth()) + " shield damage=" + k.getOffhandItem().getDamageValue());
            p.serverLevel().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(60), m -> m.getTags().contains(TAG)).forEach(Entity::discard);
        }), 10));
    }

    // ---------------------------------------------------------------- 6. mining what it's short of

    private static void gather(Minecraft mc, List<Step> l) {
        l.add(new Step(null, () -> server(p -> {
            clearAll(p); home(p, 0, 30);
            ServerLevel lv = p.serverLevel();
            for (BlockPos q : new BlockPos[]{new BlockPos(X + 5, Y, Z + 6), new BlockPos(X + 5, Y + 1, Z + 6), new BlockPos(X + 6, Y, Z + 6)}) lv.setBlock(q, Blocks.IRON_ORE.defaultBlockState(), 3);
            PartyBotEntity k = spawnBot(p, CharacterType.KAERU, X + 1.5, Z + 4.5);
            if (k == null) return;
            k.inventory.addItem(new ItemStack(Items.IRON_PICKAXE)); k.inventory.addItem(new ItemStack(Items.IRON_INGOT, 6)); k.inventory.addItem(new ItemStack(Items.CRAFTING_TABLE));
            k.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET)); k.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
            mob(p, EntityType.ZOMBIE, X - 4.5, Z + 4.5, false, 300F);
            var need = BotCrafting.shortfall(k);
            log("gather start: 6 iron, iron pickaxe; short of: " + (need == null ? "nothing" : need.more() + " " + id(new ItemStack(need.item())) + " for " + id(need.result())) + "; 3 iron ore 4 blocks off; a zombie about");
        }), 200));
        l.add(new Step(null, () -> server(p -> {
            PartyBotEntity k = bot(p, CharacterType.KAERU);
            if (k == null) return;
            log("gather with the zombie about (10 s): mined=" + k.gathered + " (none: " + (k.gathered == 0) + ")");
            p.serverLevel().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(60), m -> m.getTags().contains(TAG)).forEach(Entity::discard);
        }), 400));
        l.add(new Step("bots5_gather", () -> server(p -> {
            PartyBotEntity k = bot(p, CharacterType.KAERU);
            if (k == null) return;
            int ore = 0; for (BlockPos q : new BlockPos[]{new BlockPos(X + 5, Y, Z + 6), new BlockPos(X + 5, Y + 1, Z + 6), new BlockPos(X + 6, Y, Z + 6)}) if (p.serverLevel().getBlockState(q).is(Blocks.IRON_ORE)) ore++;
            log("gather once safe (20 s): mined=" + k.gathered + " raw_iron=" + k.inventory.countItem(Items.RAW_IRON) + " ore left=" + ore + " dist from you=" + f1(k.distanceTo(p)));
        }), 10));
    }

    // ---------------------------------------------------------------- 10. Ultima's Data Shards

    private static void ultima(Minecraft mc, List<Step> l) {
        l.add(new Step(null, () -> server(p -> {
            clearAll(p); home(p, 0, 10);
            PartyBotEntity u = spawnBot(p, CharacterType.ULTIMA, X + 2.5, Z + 2.5);
            if (u == null) return;
            u.inventory.addItem(new ItemStack(com.xenowarriors.item.ModItems.DATA_SHARD.get(), 5));
            u.karma = 10F;
        }), 60));
        l.add(new Step(null, () -> server(p -> {
            PartyBotEntity u = bot(p, CharacterType.ULTIMA);
            if (u == null) return;
            log("ultima low data: data=" + f1(u.karma) + " shards used=" + u.shardsUsed + " left=" + u.inventory.countItem(com.xenowarriors.item.ModItems.DATA_SHARD.get()));
            u.karma = 90F; u.shardAt = -100000;
        }), 60));
        l.add(new Step(null, () -> server(p -> {
            PartyBotEntity u = bot(p, CharacterType.ULTIMA);
            if (u == null) return;
            log("ultima full data, no fight: shards used=" + u.shardsUsed + " (still 1: " + (u.shardsUsed == 1) + ") left=" + u.inventory.countItem(com.xenowarriors.item.ModItems.DATA_SHARD.get())
                    + " robots here=" + (net.minecraftforge.fml.ModList.get().isLoaded("dragonminez") ? "dmz loaded" : "no DragonMineZ (robot taming not testable here)"));
        }), 10));
    }

    // ---------------------------------------------------------------- 11. Yuri and hamburgers

    private static void yuri(Minecraft mc, List<Step> l) {
        l.add(new Step(null, () -> server(p -> {
            clearAll(p); home(p, 0, 10);
            PartyBotEntity y = spawnBot(p, CharacterType.YURI, X + 1.5, Z + 1.5);
            if (y == null) return;
            ItemEntity it = new ItemEntity(p.serverLevel(), X + 9.5, Y + 0.2, Z + 4.5, new ItemStack(Items.WHEAT, 3));
            it.setPickUpDelay(0); p.serverLevel().addFreshEntity(it);
            y.yuriNext = y.tickCount;
            log("yuri start: 3 wheat on the ground 9 blocks off");
        }), 240));
        l.add(new Step(null, () -> server(p -> {
            PartyBotEntity y = bot(p, CharacterType.YURI);
            if (y == null) return;
            log("yuri fetched: wheat in her bag=" + y.inventory.countItem(Items.WHEAT) + " looks=" + y.yuriLooks + " on the ground=" + p.serverLevel().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(30)).size());
            // a wheat field and a cow further off
            ServerLevel lv = p.serverLevel();
            for (int dx = 0; dx < 3; dx++) {
                BlockPos f = new BlockPos(X + 14 + dx, Y - 1, Z - 10);
                lv.setBlock(f, Blocks.FARMLAND.defaultBlockState(), 3);
                lv.setBlock(f.above(), ((CropBlock) Blocks.WHEAT).getStateForAge(7), 3);
            }
            mob(p, EntityType.COW, X - 15.5, Z + 12.5, false, 10F);
            y.yuriNext = y.tickCount;
        }), 40));
        l.add(new Step(null, () -> mc.setScreen(new net.minecraft.client.gui.screens.ChatScreen("")), 10));
        l.add(new Step("bots5_yuri", () -> {}, 3));
        l.add(new Step(null, () -> { mc.setScreen(null); server(p -> {
            PartyBotEntity y = bot(p, CharacterType.YURI);
            if (y == null) return;
            log("yuri looked again: looks=" + y.yuriLooks);
            mob(p, EntityType.ZOMBIE, X + 15.5, Z + 1.5, false, 300F);           // 14 blocks off: no looking about with an enemy that close
            y.yuriNext = y.tickCount;
        }); }, 40));
        l.add(new Step(null, () -> server(p -> {
            PartyBotEntity y = bot(p, CharacterType.YURI);
            if (y == null) return;
            var zs = p.serverLevel().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(60), m -> m.getTags().contains(TAG) && m instanceof net.minecraft.world.entity.monster.Zombie);
            log("yuri with a zombie " + (zs.isEmpty() ? "?" : f1(zs.get(0).distanceTo(y))) + " blocks off: looks=" + y.yuriLooks + " (no new look)");
            p.serverLevel().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(60), m -> m.getTags().contains(TAG)).forEach(Entity::discard);
            for (int dx = 0; dx < 3; dx++) { p.serverLevel().setBlock(new BlockPos(X + 14 + dx, Y, Z - 10), Blocks.AIR.defaultBlockState(), 3); p.serverLevel().setBlock(new BlockPos(X + 14 + dx, Y - 1, Z - 10), Blocks.SMOOTH_STONE.defaultBlockState(), 3); }
        }), 10));
    }

    // ---------------------------------------------------------------- 12. shooting from a vehicle

    private static void vehicle(Minecraft mc, List<Step> l) {
        final float[] hp0 = new float[1];
        l.add(new Step(null, () -> server(p -> {
            clearAll(p); home(p, 180, 10);
            var car = com.xenowarriors.vehicle.Vehicles.TYPES.get(com.xenowarriors.vehicle.VehicleKind.CAR).get().create(p.serverLevel());
            if (car == null) { log("vehicle: no car"); return; }
            car.moveTo(X + 0.5, Y, Z - 2.5, 180, 0); car.addTag(TAG);
            p.serverLevel().addFreshEntity(car);
            p.startRiding(car, true);
            PartyBotEntity r = spawnBot(p, CharacterType.RHALIX, X + 1.5, Z - 1.5);
            if (r == null) return;
            r.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
            r.handPicked = r.tickCount;
            boolean in = r.startRiding(car, true);
            var zb = mob(p, EntityType.ZOMBIE, X + 0.5, Z - 16.5, false, 300F);
            if (zb != null) { r.setTarget(zb); hp0[0] = zb.getHealth(); }
            log("vehicle start: rhalix in the car=" + in + " (seat " + car.getPassengers().indexOf(r) + "), you driving=" + (p.getVehicle() == car) + ", zombie 14 blocks off");
        }), 120));
        l.add(new Step("bots5_vehicle", () -> server(p -> {
            PartyBotEntity r = bot(p, CharacterType.RHALIX);
            var zbs = p.serverLevel().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(40), m -> m.getTags().contains(TAG) && m instanceof net.minecraft.world.entity.monster.Zombie);
            if (r != null) log("vehicle after 6s: still seated=" + r.isPassenger() + " shots from the seat=" + r.seatShots + " zombie hp lost=" + (zbs.isEmpty() ? "dead" : f1(hp0[0] - zbs.get(0).getHealth())));
        }), 10));
        l.add(new Step(null, () -> server(p -> {
            p.stopRiding();
            p.serverLevel().getEntitiesOfClass(Entity.class, p.getBoundingBox().inflate(60), e -> e.getTags().contains(TAG)).forEach(Entity::discard);
            home(p, 0, 10);
        }), 20));
    }
}

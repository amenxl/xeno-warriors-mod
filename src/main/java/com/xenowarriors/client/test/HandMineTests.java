package com.xenowarriors.client.test;

import com.xenowarriors.bot.PartyBotEntity;
import com.xenowarriors.bot.PartyBots;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.character.ModCapabilities;
import com.xenowarriors.client.PlayerStatsScreen;
import com.xenowarriors.client.UiTest;
import com.xenowarriors.client.UiTest.Step;
import com.xenowarriors.stats.HandMining;
import com.xenowarriors.stats.Mining;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Bare-handed mining by Mining level (stats/HandMining): focus "handmine" (dev), and "packhandmine" (the user's real
 * pack, with Vein Miner and Tree Harvester). Each case logs "XW-TEST handmine ... -> PASS/FAIL", then a summary.
 */
public final class HandMineTests {
    private HandMineTests() {}

    /** Dev focus "handmine"; the pack's "packhandmine" goes through PackTests (its world loading and DragonMineZ handling). */
    public static boolean handles(String focus) { return "handmine".equals(focus); }

    private static int pass, fail;
    private static final List<String> FAILED = new ArrayList<>();
    private static final BlockPos[] HOME = new BlockPos[1];

    private static void log(String s) { UiTest.log("handmine " + s); }

    private static void check(String what, boolean ok, String detail) {
        if (ok) pass++; else { fail++; FAILED.add(what); }
        log(what + ": " + detail + " -> " + (ok ? "PASS" : "FAIL"));
    }

    private static String f(double d) { return String.format(Locale.ROOT, "%.2f", d); }

    private static void server(java.util.function.Consumer<ServerPlayer> c) {
        UiTest.server(p -> { try { c.accept(p); } catch (Throwable t) { check("server step", false, String.valueOf(t)); com.xenowarriors.XenoWarriors.LOGGER.error("XW-TEST handmine", t); } });
    }

    private static void setMining(ServerPlayer p, int lv) {
        p.getCapability(ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> { d.miningLevel = lv; d.miningXp = 0; });
        Mining.sync(p);
    }

    /** A floating stone platform high up, the player standing on it in survival. */
    private static void arena(ServerPlayer p) {
        ServerLevel l = p.serverLevel();
        if (HOME[0] == null) HOME[0] = BlockPos.containing(p.getX(), 200, p.getZ());
        BlockPos c = HOME[0];
        for (int x = -12; x <= 12; x++) for (int z = -12; z <= 12; z++) {
            l.setBlock(c.offset(x, -1, z), Blocks.SMOOTH_STONE.defaultBlockState(), 2);
            for (int y = 0; y < 8; y++) l.setBlock(c.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
        }
        for (ItemEntity e : l.getEntitiesOfClass(ItemEntity.class, new AABB(c).inflate(16))) e.discard();
        p.setGameMode(GameType.SURVIVAL);
        p.teleportTo(l, c.getX() + .5, c.getY(), c.getZ() + .5, 0F, 30F);
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        p.setHealth(p.getMaxHealth());
    }

    private static int count(ServerPlayer p, BlockPos at, Item it) {
        int n = 0;
        for (ItemEntity e : p.serverLevel().getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(3))) if (e.getItem().is(it)) n += e.getItem().getCount();
        return n + p.getInventory().countItem(it);
    }

    private static void clearDrops(ServerPlayer p, BlockPos at, Item... its) {
        for (ItemEntity e : p.serverLevel().getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(3))) e.discard();
        for (Item it : its) p.getInventory().clearOrCountMatchingItems(s -> s.is(it), -1, p.inventoryMenu.getCraftSlots());
    }

    /** Puts the block 2 in front of the player, breaks it the real server way (events, drops), returns how many of {@code drop} came out. */
    private static int breakOne(ServerPlayer p, BlockState st, Item drop) {
        BlockPos at = HOME[0].offset(0, 0, 2);
        clearDrops(p, at, drop);
        p.serverLevel().setBlock(at, st, 3);
        p.gameMode.destroyBlock(at);
        boolean gone = p.serverLevel().getBlockState(at).isAir();
        int n = count(p, at, drop);
        clearDrops(p, at, drop);
        p.serverLevel().setBlock(at, Blocks.AIR.defaultBlockState(), 3);
        return gone ? n : -1;
    }

    private static String id(BlockState st) { return net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(st.getBlock()).getPath(); }

    /** Drops at the level just under the tier and at it. */
    private static void tier(ServerPlayer p, BlockState st, Item drop, int need) {
        setMining(p, need - 1);
        int below = breakOne(p, st, drop);
        boolean hBelow = p.hasCorrectToolForDrops(st);
        setMining(p, need);
        boolean hAt = p.hasCorrectToolForDrops(st);
        int at = breakOne(p, st, drop);
        check(id(st) + " needs Lv " + need, below == 0 && at > 0 && !hBelow && hAt,
                "Lv " + (need - 1) + ": " + below + " " + BuiltIn.name(drop) + " (harvest " + hBelow + "), Lv " + need + ": " + at + " (harvest " + hAt + ")");
    }

    private static final class BuiltIn { static String name(Item i) { return net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(i).getPath(); } }

    public static void build(Minecraft mc, List<Step> l, String focus) {
        boolean pack = focus.startsWith("pack");
        l.add(new Step(null, () -> { UiTest.noToasts(mc); mc.setScreen(null); UiTest.character(CharacterType.MIKIKO); }, 30));
        l.add(new Step(null, () -> server(p -> { arena(p); setMining(p, 0); }), 30));

        // ---- drops by tier (server: the real destroyBlock path, with its events)
        l.add(new Step(null, () -> server(p -> {
            log("thresholds " + java.util.Arrays.toString(HandMining.LEVELS) + " names " + java.util.Arrays.toString(HandMining.NAMES));
            setMining(p, 0);
            int s0 = breakOne(p, Blocks.STONE.defaultBlockState(), Items.COBBLESTONE);
            check("stone at Lv 0", s0 == 0, "broke, " + s0 + " cobblestone (expect 0)");
            tier(p, Blocks.STONE.defaultBlockState(), Items.COBBLESTONE, HandMining.LEVELS[0]);
            tier(p, Blocks.COAL_ORE.defaultBlockState(), Items.COAL, HandMining.LEVELS[0]);
            tier(p, Blocks.IRON_ORE.defaultBlockState(), Items.RAW_IRON, HandMining.LEVELS[1]);
            tier(p, Blocks.DEEPSLATE_COPPER_ORE.defaultBlockState(), Items.RAW_COPPER, HandMining.LEVELS[1]);
            tier(p, Blocks.DIAMOND_ORE.defaultBlockState(), Items.DIAMOND, HandMining.LEVELS[2]);
            tier(p, Blocks.GOLD_ORE.defaultBlockState(), Items.RAW_GOLD, HandMining.LEVELS[2]);
            tier(p, Blocks.OBSIDIAN.defaultBlockState(), Items.OBSIDIAN, HandMining.LEVELS[3]);
            tier(p, Blocks.ANCIENT_DEBRIS.defaultBlockState(), Items.ANCIENT_DEBRIS, HandMining.LEVELS[3]);
            // dirt and wood never needed a tool: still drop at Lv 0
            setMining(p, 0);
            check("dirt/log at Lv 0", breakOne(p, Blocks.DIRT.defaultBlockState(), Items.DIRT) == 1 && breakOne(p, Blocks.OAK_LOG.defaultBlockState(), Items.OAK_LOG) == 1, "drop as before");
            // no Silk Touch / Fortune on hands
            setMining(p, 30000);
            int dia = 0; for (int i = 0; i < 20; i++) dia += breakOne(p, Blocks.DIAMOND_ORE.defaultBlockState(), Items.DIAMOND);
            int glass = breakOne(p, Blocks.GLASS.defaultBlockState(), Items.GLASS);
            check("no fortune / silk touch", dia == 20 && glass == 0, "20 diamond ore at Lv 30000 -> " + dia + " diamonds, glass -> " + glass);
        }), 10));

        // ---- what counts as a bare hand
        l.add(new Step(null, () -> server(p -> {
            BlockState stone = Blocks.STONE.defaultBlockState(), dia = Blocks.DIAMOND_ORE.defaultBlockState();
            setMining(p, HandMining.LEVELS[1]);
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIRT, 10));
            boolean block = p.hasCorrectToolForDrops(stone) && breakOne(p, stone, Items.COBBLESTONE) == 1;
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
            boolean sword = p.hasCorrectToolForDrops(stone);
            check("holding a block = bare hand, a sword isn't", block && !sword, "dirt in hand: stone drops " + block + ", sword in hand: harvest " + sword);
            // a pickaxe in hand: exactly as before (its own tier, the plain Mining bonus)
            setMining(p, 30000);
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WOODEN_PICKAXE));
            boolean woodDia = p.hasCorrectToolForDrops(dia);
            int woodDrop = breakOne(p, dia, Items.DIAMOND);
            setMining(p, 0);
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
            boolean ironDia = p.hasCorrectToolForDrops(dia);
            float s0 = p.getDigSpeed(stone, HOME[0]);
            setMining(p, 100);
            float s100 = p.getDigSpeed(stone, HOME[0]);
            float want = 6F * Mining.multiplier(100, false);
            int ironDrop = breakOne(p, dia, Items.DIAMOND);
            float ratio = s100 / s0, wantRatio = Mining.multiplier(100, false);   // (a ratio: on-ground / other mods' factors cancel out)
            check("pickaxe in hand unaffected", !woodDia && woodDrop == 0 && ironDia && ironDrop == 1 && Math.abs(ratio - wantRatio) < 0.01,
                    "wooden pick on diamond ore at Lv 30000: harvest " + woodDia + " drops " + woodDrop + "; iron pick at Lv 0: harvest " + ironDia + " drops " + ironDrop
                            + "; iron pick speed Lv0 " + f(s0) + " (6.00 on the ground), Lv100 " + f(s100) + " (" + f(want) + "), x" + f(ratio) + " (plain Mining bonus x" + f(wantRatio) + ")");
            p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            // creative: no change
            p.setGameMode(GameType.CREATIVE);
            setMining(p, 0);
            boolean cr = p.hasCorrectToolForDrops(stone);
            p.setGameMode(GameType.SURVIVAL);
            check("creative unchanged", !cr, "creative Lv 0 bare hand harvest stone " + cr + " (vanilla false)");
        }), 10));

        // ---- speed by level (bare hand, stone; and dirt/wood at the matching shovel/axe tier)
        l.add(new Step(null, () -> server(p -> {
            BlockState stone = Blocks.STONE.defaultBlockState(), dirt = Blocks.DIRT.defaultBlockState(), log = Blocks.OAK_LOG.defaultBlockState();
            int[] lvs = {0, 1, 4, 5, 15, 40, 50, 100, 250, 400, 700, 1000, 5000, 30000};
            StringBuilder sb = new StringBuilder();
            float last = -1; boolean up = true;
            for (int lv : lvs) {
                setMining(p, lv);
                float sp = p.getDigSpeed(stone, HOME[0]);
                float prog = stone.getDestroyProgress(p, p.level(), HOME[0]);
                int ticks = prog >= 1 ? 0 : (int) Math.ceil(1F / prog);
                sb.append("Lv").append(lv).append(" ").append(f(sp)).append("/").append(ticks).append("t (dirt ").append(f(p.getDigSpeed(dirt, HOME[0])))
                        .append(", log ").append(f(p.getDigSpeed(log, HOME[0]))).append(", ").append(HandMining.describe(lv)).append(")  ");
                if (sp < last - 0.001F) up = false;
                last = sp;
            }
            log("hand speeds on stone (speed / ticks to break): " + sb);
            setMining(p, 0); float t0 = Blocks.STONE.defaultBlockState().getDestroyProgress(p, p.level(), HOME[0]);
            setMining(p, HandMining.LEVELS[1]); float t15 = Blocks.STONE.defaultBlockState().getDestroyProgress(p, p.level(), HOME[0]);
            check("speed rises with level", up && t15 > t0 * 10, "never slower at a higher level " + up + "; stone break per tick Lv0 " + f(t0 * 100) + "% vs Lv15 " + f(t15 * 100) + "%");
            setMining(p, 100);
            float hand = p.getDigSpeed(stone, HOME[0]);
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
            float pick = p.getDigSpeed(stone, HOME[0]);
            p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            check("Lv 100 hands = diamond pickaxe speed", Math.abs(hand - pick) < 0.05F, "hand " + f(hand) + " vs diamond pickaxe " + f(pick));
        }), 10));

        // ---- the real client: hold attack on stone (client and server agree on the hand's speed)
        int[] ticks = new int[2];
        for (int k = 0; k < 2; k++) {
            final int lvl = k == 0 ? 0 : HandMining.LEVELS[1], kk = k;
            l.add(new Step(null, () -> server(p -> { arena(p); setMining(p, lvl); p.serverLevel().setBlock(HOME[0].offset(0, 0, 2), Blocks.STONE.defaultBlockState(), 3);
                p.teleportTo(p.serverLevel(), HOME[0].getX() + .5, HOME[0].getY(), HOME[0].getZ() + .5, 0F, 20F); }), 25));
            l.add(new Step(null, () -> { ticks[kk] = -1; mc.gameMode.startDestroyBlock(HOME[0].offset(0, 0, 2), Direction.NORTH); }, 0));
            for (int t = 1; t <= 60; t++) {
                final int tt = t;
                l.add(new Step(null, () -> {
                    if (ticks[kk] >= 0) return;
                    if (mc.level.getBlockState(HOME[0].offset(0, 0, 2)).isAir()) { ticks[kk] = tt; return; }
                    mc.gameMode.continueDestroyBlock(HOME[0].offset(0, 0, 2), Direction.NORTH);
                }, 0));
            }
            l.add(new Step(k == 1 ? "handmine_client_break" : null, () -> { mc.gameMode.stopDestroyBlock(); server(p -> {
                boolean gone = p.serverLevel().getBlockState(HOME[0].offset(0, 0, 2)).isAir();
                int cob = count(p, HOME[0].offset(0, 0, 2), Items.COBBLESTONE);
                if (kk == 0) check("client Lv 0: stone not broken in 60 steps", !gone, "client level " + Mining.level(mc.player) + ", broken " + gone);
                else check("client Lv 15: stone breaks fast, drops", gone && cob == 1 && ticks[kk] > 0 && ticks[kk] < 30, "client level " + Mining.level(mc.player)
                        + ", broken after " + ticks[kk] + " steps, cobblestone " + cob);
            }); }, 10));
        }

        // ---- bots: bare hands use their owner's Mining level
        PartyBotEntity[] bot = new PartyBotEntity[1];
        l.add(new Step(null, () -> server(p -> {
            arena(p); setMining(p, 0);
            for (PartyBotEntity b : PartyBots.bots(p)) b.discard();
            bot[0] = PartyBots.spawn(p, CharacterType.RHALIX, null);
        }), 40));
        l.add(new Step(null, () -> server(p -> {
            PartyBotEntity b = bot[0];
            if (b == null) { check("bot", false, "no bot spawned"); return; }
            b.inventory.clearContent();
            b.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            BlockState iron = Blocks.IRON_ORE.defaultBlockState(), stone = Blocks.STONE.defaultBlockState();
            setMining(p, 0);
            boolean c0 = HandMining.botCanHarvest(b, iron); ItemStack t0 = HandMining.botTool(b, iron);
            int d0 = com.xenowarriors.bot.BotGear.digTicks(b, HOME[0], iron);
            setMining(p, HandMining.LEVELS[1]);
            boolean c15 = HandMining.botCanHarvest(b, iron); ItemStack t15 = HandMining.botTool(b, iron);
            int d15 = com.xenowarriors.bot.BotGear.digTicks(b, HOME[0], iron);
            setMining(p, HandMining.LEVELS[2]);
            boolean dia40 = HandMining.botCanHarvest(b, Blocks.DIAMOND_ORE.defaultBlockState());
            setMining(p, HandMining.LEVELS[1]);
            boolean dia15 = HandMining.botCanHarvest(b, Blocks.DIAMOND_ORE.defaultBlockState());
            check("bot bare hands by Mining level", !c0 && t0.isEmpty() && c15 && t15.is(Items.STONE_PICKAXE) && dia40 && !dia15 && d15 < d0,
                    "Lv0 iron: can " + c0 + " tool " + t0 + " dig " + d0 + "t; Lv15: can " + c15 + " tool " + t15 + " dig " + d15 + "t; diamond ore Lv15 " + dia15 + " Lv40 " + dia40);
            // the real Mining mode: iron ore next to the bot, owner Lv 0 -> left alone; Lv 15 -> dug into its bag
            setMining(p, 0);
            BlockPos ore = HOME[0].offset(3, 0, 3);
            p.serverLevel().setBlock(ore, iron, 3);
            b.teleportTo(HOME[0].getX() + 2.5, HOME[0].getY(), HOME[0].getZ() + 3.5);
            log("bot Lv0 would pick " + PartyBots.findOreForTest(b, p) + " (expect null)");
            check("bot Lv 0 skips ore it can't dig", PartyBots.findOreForTest(b, p) == null, "target " + PartyBots.findOreForTest(b, p));
            setMining(p, HandMining.LEVELS[1]);
            b.setMode(PartyBotEntity.MINING);
        }), 200));
        l.add(new Step("handmine_bot", () -> server(p -> {
            PartyBotEntity b = bot[0];
            if (b == null) return;
            int raw = b.inventory.countItem(Items.RAW_IRON);
            boolean gone = p.serverLevel().getBlockState(HOME[0].offset(3, 0, 3)).isAir();
            check("bot Lv 15 digs iron ore by hand", gone && raw == 1, "ore gone " + gone + ", raw iron in bag " + raw + ", hand " + b.getMainHandItem());
            b.setMode(PartyBotEntity.NEUTRAL);
        }), 5));

        // ---- level-up message and the stats tooltip
        l.add(new Step(null, () -> server(p -> {
            arena(p);
            setMining(p, HandMining.LEVELS[2] - 1);
            int before = Mining.level(p);
            Mining.addXp(p, Mining.needed(before) + 0.5F);
            check("tier-up on level-up", Mining.level(p) == HandMining.LEVELS[2] && HandMining.tierIndex(Mining.level(p)) == 2, "Lv " + before + " -> " + Mining.level(p) + " = " + HandMining.describe(Mining.level(p)));
        }), 10));
        l.add(new Step(null, () -> mc.setScreen(new net.minecraft.client.gui.screens.ChatScreen("")), 15));
        l.add(new Step("handmine_levelup_chat", () -> {}, 5));
        l.add(new Step(null, () -> { mc.setScreen(null); PlayerStatsScreen.testMiningTip = true; PlayerStatsScreen.presetTab(1); mc.setScreen(new PlayerStatsScreen()); }, 40));
        l.add(new Step("handmine_stats_tip", () -> {
            List<String> tip = PlayerStatsScreen.miningTip(Mining.level(mc.player)).stream().map(net.minecraft.network.chat.Component::getString).toList();
            log("tooltip: " + String.join(" | ", tip));
            check("stats tooltip", tip.stream().anyMatch(s -> s.equals("Bare hands mine like: Iron Pickaxe (next: Diamond at Lv 100)")), "client Lv " + Mining.level(mc.player));
        }, 5));
        l.add(new Step(null, () -> { PlayerStatsScreen.testMiningTip = false; mc.setScreen(null); }, 5));
        l.add(new Step(null, () -> { mc.setScreen(null); com.xenowarriors.client.menu.CharacterCustomScreen.open(null); }, 40));
        l.add(new Step(null, () -> PlayerStatsScreen.testMiningTip = true, 10));
        l.add(new Step("handmine_custom_tip", () -> {}, 5));
        l.add(new Step(null, () -> { PlayerStatsScreen.testMiningTip = false; mc.setScreen(null); }, 5));

        if (pack) packMods(mc, l);

        l.add(new Step(null, () -> server(p -> { for (PartyBotEntity b : PartyBots.bots(p)) b.discard(); setMining(p, 0); }), 10));
        l.add(new Step("handmine_end", () -> log("summary pass=" + pass + " fail=" + fail + (FAILED.isEmpty() ? "" : " failed=" + FAILED)), 2));
    }

    /** In the real pack: Vein Miner and Tree Harvester with bare hands vs tools. */
    private static void packMods(Minecraft mc, List<Step> l) {
        l.add(new Step(null, () -> {
            log("veinminer loaded=" + net.minecraftforge.fml.ModList.get().isLoaded("veinminer") + " treeharvester loaded=" + net.minecraftforge.fml.ModList.get().isLoaded("treeharvester"));
            net.minecraftforge.fml.ModList.get().forEachModContainer((id, c) -> { if (id.contains("vein") || id.contains("tree")) log("mod " + id + " " + c.getModInfo().getVersion()); });
        }, 2));
        String[] modes = {"pickaxe", "pickaxe+sneak", "hand", "hand+sneak"};
        for (String m : modes) {
            l.add(new Step(null, () -> server(p -> {
                arena(p);
                setMining(p, 250);
                for (int i = 0; i < 4; i++) p.serverLevel().setBlock(HOME[0].offset(i, 0, 2), Blocks.IRON_ORE.defaultBlockState(), 3);
                p.setItemInHand(InteractionHand.MAIN_HAND, m.startsWith("pickaxe") ? new ItemStack(Items.IRON_PICKAXE) : ItemStack.EMPTY);
                p.setShiftKeyDown(m.endsWith("sneak"));
            }), 10));
            l.add(new Step(null, () -> server(p -> {
                clearDrops(p, HOME[0].offset(0, 0, 2), Items.RAW_IRON);
                boolean ok = p.gameMode.destroyBlock(HOME[0].offset(0, 0, 2));
                p.getPersistentData().putBoolean("xw_hm_ok", ok);
            }), 30));
            l.add(new Step(null, () -> server(p -> {
                int left = 0; for (int i = 0; i < 4; i++) if (p.serverLevel().getBlockState(HOME[0].offset(i, 0, 2)).is(Blocks.IRON_ORE)) left++;
                int raw = 0; for (ItemEntity e : p.serverLevel().getEntitiesOfClass(ItemEntity.class, new AABB(HOME[0]).inflate(10))) if (e.getItem().is(Items.RAW_IRON)) raw += e.getItem().getCount();
                raw += p.getInventory().countItem(Items.RAW_IRON);
                check("veinminer " + m, p.getPersistentData().getBoolean("xw_hm_ok") && left < 4 && raw >= 4 - left,
                        "broke " + (4 - left) + " of 4 iron ore in a row, raw iron " + raw);
                p.setShiftKeyDown(false);
                clearDrops(p, HOME[0], Items.RAW_IRON);
            }), 5));
        }
        String[] tools = {"axe", "hand"};
        for (String m : tools) {
            l.add(new Step(null, () -> server(p -> {
                arena(p);
                setMining(p, 250);
                BlockPos t = HOME[0].offset(2, 0, 3);
                for (int y = 0; y < 5; y++) p.serverLevel().setBlock(t.above(y), Blocks.OAK_LOG.defaultBlockState(), 3);
                for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) for (int y = 3; y <= 6; y++)
                    if (!(x == 0 && z == 0 && y < 5)) p.serverLevel().setBlock(t.offset(x, y, z), Blocks.OAK_LEAVES.defaultBlockState(), 3);
                p.setItemInHand(InteractionHand.MAIN_HAND, m.equals("axe") ? new ItemStack(Items.IRON_AXE) : ItemStack.EMPTY);
            }), 10));
            l.add(new Step(null, () -> server(p -> p.getPersistentData().putBoolean("xw_hm_ok", p.gameMode.destroyBlock(HOME[0].offset(2, 0, 3)))), 40));
            l.add(new Step(m.equals("hand") ? "handmine_pack_tree" : null, () -> server(p -> {
                int left = 0; for (int y = 0; y < 5; y++) if (p.serverLevel().getBlockState(HOME[0].offset(2, y, 3)).is(Blocks.OAK_LOG)) left++;
                check("treeharvester " + m, p.getPersistentData().getBoolean("xw_hm_ok") && left < 5, "logs left " + left + " of 5 (bottom one broken " + !p.serverLevel().getBlockState(HOME[0].offset(2, 0, 3)).is(Blocks.OAK_LOG) + ")");
            }), 5));
        }
        l.add(new Step(null, () -> server(p -> { arena(p); p.setGameMode(GameType.CREATIVE); }), 5));
    }
}

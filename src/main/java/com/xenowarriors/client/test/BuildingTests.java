package com.xenowarriors.client.test;

import com.xenowarriors.bot.PartyBotEntity;
import com.xenowarriors.bot.PartyBots;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.client.UiTest;
import com.xenowarriors.client.UiTest.Step;
import com.xenowarriors.stats.Building;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;

import java.util.List;
import java.util.Locale;

/**
 * UI tests for the Building stat (focus "building", "dmzbuilding" with DragonMineZ): XP from placing blocks (and none
 * for re-placing one spot), block reach per 10 levels (attribute values and a real client placement out of / in
 * reach), the keep-a-block chance per 20 levels (forced rolls, a statistical run at 50%, cancelled placements don't
 * dupe, creative unaffected, the client's count stays right), bot parity, and the stats menu.
 * Results are logged as "XW-TEST building ...".
 */
public final class BuildingTests {
    private BuildingTests() {}

    public static boolean handles(String focus) { return focus != null && focus.replaceFirst("^dmz", "").startsWith("building"); }

    private static void log(String s) { UiTest.log("building " + s); }

    private static String pass(boolean ok) { return ok ? "PASS" : "FAIL"; }

    private static String f(double v) { return String.format(Locale.ROOT, "%.3f", v); }

    private static final BlockPos[] ORIGIN = new BlockPos[2];       // stage centre, the far reach spot
    private static int next;
    private static boolean listening, cancel;
    private static final float[] REACH = new float[4];

    /** A new spot every time (so the same-spot rule doesn't kick in), away from the player. */
    private static BlockPos spot() {
        int i = next++;
        return ORIGIN[0].offset(-10 + i % 20, (i / 400) * 3, 8 + (i / 20) % 20);
    }

    private static void prep(ServerLevel l, BlockPos target) {
        l.setBlock(target.below(), Blocks.STONE.defaultBlockState(), 3);
        l.setBlock(target, Blocks.AIR.defaultBlockState(), 3);
        l.setBlock(target.above(), Blocks.AIR.defaultBlockState(), 3);
    }

    private static BlockHitResult top(BlockPos target) {
        BlockPos below = target.below();
        return new BlockHitResult(new Vec3(below.getX() + 0.5, below.getY() + 1.0, below.getZ() + 0.5), Direction.UP, below, false);
    }

    /** Places what's in the main hand on top of the block under {@code target}, the way a player's click does on the server. */
    private static InteractionResult place(ServerPlayer p, BlockPos target) {
        prep(p.serverLevel(), target);
        return p.getMainHandItem().useOn(new UseOnContext(p, InteractionHand.MAIN_HAND, top(target)));
    }

    private static void hold(ServerPlayer p, Item it, int n) { p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(it, n)); }

    private static double reach(ServerPlayer p) { return p.getAttributeValue(ForgeMod.BLOCK_REACH.get()); }

    public static void build(Minecraft mc, List<Step> l, String focus) {
        l.add(new Step(null, () -> { UiTest.noToasts(mc); mc.setScreen(null); UiTest.character(CharacterType.MIKIKO);
            UiTest.server(p -> {
                p.setGameMode(GameType.SURVIVAL); p.serverLevel().setDayTime(6000); p.setHealth(p.getMaxHealth());
                ORIGIN[0] = p.blockPosition();
                p.teleportTo(ORIGIN[0].getX() + 0.5, ORIGIN[0].getY(), ORIGIN[0].getZ() + 0.5);
                if (!listening) {
                    listening = true;
                    MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, (BlockEvent.EntityPlaceEvent e) -> { if (cancel) e.setCanceled(true); });
                }
            }); }, 40));

        // ---- 1. XP from placing blocks
        l.add(new Step(null, () -> UiTest.server(p -> {
            Building.testRoll = null;
            Building.set(p, 0, 0);
            hold(p, Items.STONE, 64);
            for (int i = 0; i < 20; i++) place(p, spot());
            int lv = Building.level(p), left = p.getMainHandItem().getCount();
            float xp = Building.xp(p);
            log("placed 20 stone from Lv0: Lv" + lv + " xp=" + f(xp) + " (needed(0)=" + Building.needed(0) + ", expect Lv1 xp 0) stack 64 -> " + left
                    + " (expect 44) -> " + pass(lv == 1 && xp < 0.01F && left == 44));
            for (int i = 0; i < 6; i++) place(p, spot());
            float a = Building.xp(p);
            BlockPos same = spot();
            place(p, same);
            float b = Building.xp(p);
            for (int i = 0; i < 5; i++) place(p, same);                   // place / break / place on one spot
            float c = Building.xp(p);
            log("same spot: first +" + f(b - a) + " then 5 more re-places +" + f(c - b) + " (expect +1 then +0) -> " + pass(Math.abs(b - a - 1F) < 0.01F && c == b));
            hold(p, Items.TORCH, 16);
            float t0 = Building.xp(p);
            InteractionResult r = place(p, spot());
            log("torch (instant-break block): " + r + " +" + f(Building.xp(p) - t0) + " xp (expect +0.25) -> " + pass(Math.abs(Building.xp(p) - t0 - 0.25F) < 0.01F));
            hold(p, Items.OAK_DOOR, 4);
            float d0 = Building.xp(p);
            r = place(p, spot());
            log("door (two blocks, one placement): " + r + " +" + f(Building.xp(p) - d0) + " xp (expect +1) -> " + pass(Math.abs(Building.xp(p) - d0 - 1F) < 0.01F));
        }), 10));

        // ---- 2. reach: +1 per 10 levels, capped, only block reach
        l.add(new Step(null, () -> UiTest.server(p -> {
            Building.set(p, 0, 0);
            double base = reach(p), ent = p.getAttributeValue(ForgeMod.ENTITY_REACH.get());
            StringBuilder sb = new StringBuilder();
            boolean ok = true;
            for (int lv : new int[]{9, 10, 19, 20, 55, 79, 80, 81, 1000, 30000}) {
                Building.set(p, lv, 0);
                double d = reach(p) - base;
                int want = Math.min(8, lv / 10);
                ok &= Math.abs(d - want) < 1e-6;
                sb.append(" Lv").append(lv).append("=+").append(f(d));
            }
            ok &= Math.abs(p.getAttributeValue(ForgeMod.ENTITY_REACH.get()) - ent) < 1e-6;
            log("block reach base " + f(base) + ":" + sb + " | entity reach " + f(ent) + " -> " + f(p.getAttributeValue(ForgeMod.ENTITY_REACH.get())) + " (unchanged) -> " + pass(ok));
            Building.set(p, 10, 0);
            REACH[0] = (float) reach(p);
            REACH[1] = (float) ent;
        }), 40));
        l.add(new Step(null, () -> {
            var pl = mc.player;
            log("client sees Lv10: block reach " + f(pl.getBlockReach()) + " (server " + f(REACH[0]) + ") entity reach " + f(pl.getEntityReach()) + " (server " + f(REACH[1]) + ") -> "
                    + pass(Math.abs(pl.getBlockReach() - REACH[0]) < 1e-4 && Math.abs(pl.getEntityReach() - REACH[1]) < 1e-4));
        }, 5));
        // a real click from the client, 9 blocks out: refused at Lv0 (4.5 reach), placed at Lv50 (+5)
        l.add(new Step(null, () -> UiTest.server(p -> {
            Building.set(p, 0, 0);
            p.teleportTo(ORIGIN[0].getX() + 0.5, ORIGIN[0].getY(), ORIGIN[0].getZ() + 0.5);
            p.setYRot(-90); p.setXRot(10);
            ORIGIN[1] = ORIGIN[0].offset(9, 0, 0);
            prep(p.serverLevel(), ORIGIN[1]);
            hold(p, Items.STONE, 10);
        }), 30));
        l.add(new Step(null, () -> mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, top(ORIGIN[1])), 15));
        l.add(new Step(null, () -> UiTest.server(p -> {
            boolean placed = !p.serverLevel().getBlockState(ORIGIN[1]).isAir();
            log("client click 9 blocks away at Lv0 (reach " + f(reach(p)) + "): placed=" + placed + " (expect false) -> " + pass(!placed));
            Building.set(p, 50, 0);
            prep(p.serverLevel(), ORIGIN[1]);
        }), 30));
        l.add(new Step(null, () -> mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, top(ORIGIN[1])), 15));
        l.add(new Step(null, () -> UiTest.server(p -> {
            boolean placed = p.serverLevel().getBlockState(ORIGIN[1]).is(Blocks.STONE);
            log("client click 9 blocks away at Lv50 (reach " + f(reach(p)) + "): placed=" + placed + " (expect true) -> " + pass(placed));
        }), 5));

        // ---- 3. the keep-a-block chance
        l.add(new Step(null, () -> UiTest.server(p -> {
            StringBuilder sb = new StringBuilder();
            for (int lv : new int[]{0, 19, 20, 39, 40, 200, 2000, 10000, 30000}) sb.append(" Lv").append(lv).append("=").append(Building.pct(Building.saveChance(lv)));
            log("keep chance:" + sb + " -> " + pass(Building.saveChance(19) == 0F && Math.abs(Building.saveChance(20) - 0.001F) < 1e-7
                    && Math.abs(Building.saveChance(40) - 0.002F) < 1e-7 && Building.saveChance(10000) == 0.5F && Building.saveChance(30000) == 0.5F));
            // forced rolls at level 20 (0.1%)
            Building.set(p, 20, 0);
            hold(p, Items.STONE, 64);
            Building.testRoll = 0.0005F;                      // under 0.1%: kept
            place(p, spot());
            int kept = p.getMainHandItem().getCount();
            Building.testRoll = 0.0015F;                      // over 0.1%: used up
            place(p, spot());
            int used = p.getMainHandItem().getCount();
            Building.testRoll = null;
            log("Lv20 roll 0.0005 -> stack " + kept + " (expect 64, kept); roll 0.0015 -> " + used + " (expect 63) -> " + pass(kept == 64 && used == 63));
            // statistical at level 10,000 (50%)
            Building.set(p, 10000, 0);
            int n = 400, saved = 0, s0 = Building.testSaved;
            for (int i = 0; i < n; i++) {
                hold(p, Items.COBBLESTONE, 64);
                place(p, spot());
                if (p.getMainHandItem().getCount() == 64) saved++;
            }
            log("Lv10000 (" + Building.pct(Building.saveChance(10000)) + "): kept " + saved + " / " + n + " (counter " + (Building.testSaved - s0) + ", expect ~200, 150-250) -> "
                    + pass(saved >= 150 && saved <= 250 && saved == Building.testSaved - s0));
            // statistical at level 2,000 (10%)
            Building.set(p, 2000, 0);
            saved = 0;
            for (int i = 0; i < n; i++) {
                hold(p, Items.OAK_PLANKS, 64);
                place(p, spot());
                if (p.getMainHandItem().getCount() == 64) saved++;
            }
            log("Lv2000 (" + Building.pct(Building.saveChance(2000)) + "): kept " + saved + " / " + n + " (expect ~40, 15-70) -> " + pass(saved >= 15 && saved <= 70));
        }), 20));
        // cancelled placements never dupe; other stack sizes; a single last block
        l.add(new Step(null, () -> UiTest.server(p -> {
            Building.set(p, 10000, 0);
            Building.testRoll = 0F;                            // always kept
            hold(p, Items.STONE, 10);
            cancel = true;
            BlockPos at = spot();
            place(p, at);
            cancel = false;
            int c = p.getMainHandItem().getCount();
            boolean air = p.serverLevel().getBlockState(at).isAir();
            log("cancelled placement with a forced keep: stack 10 -> " + c + " block placed=" + !air + " (expect 10, not placed) -> " + pass(c == 10 && air));
            hold(p, Items.STONE, 1);
            place(p, spot());
            log("last block in the stack, kept: count " + p.getMainHandItem().getCount() + " item " + p.getMainHandItem().getItem() + " (expect 1 stone) -> "
                    + pass(p.getMainHandItem().is(Items.STONE) && p.getMainHandItem().getCount() == 1));
            hold(p, Items.OAK_DOOR, 3);
            place(p, spot());
            log("door kept: count " + p.getMainHandItem().getCount() + " (expect 3) -> " + pass(p.getMainHandItem().getCount() == 3));
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WHITE_WOOL, 1));
            Building.testRoll = 0.99F;                         // not kept: the stack runs out as normal
            place(p, spot());
            log("last block not kept: empty=" + p.getMainHandItem().isEmpty() + " -> " + pass(p.getMainHandItem().isEmpty()));
            Building.testRoll = null;
        }), 10));
        // creative: no XP, never touched
        l.add(new Step(null, () -> UiTest.server(p -> {
            p.setGameMode(GameType.CREATIVE);
            Building.set(p, 10000, 5F);
            Building.testRoll = 0F;
            hold(p, Items.STONE, 10);
            int s0 = Building.testSaved;
            for (int i = 0; i < 5; i++) place(p, spot());
            log("creative: stack " + p.getMainHandItem().getCount() + " (10) xp " + f(Building.xp(p)) + " (5) keep rolls " + (Building.testSaved - s0) + " (0) -> "
                    + pass(p.getMainHandItem().getCount() == 10 && Math.abs(Building.xp(p) - 5F) < 1e-4 && Building.testSaved == s0));
            Building.testRoll = null;
            p.setGameMode(GameType.SURVIVAL);
        }), 10));
        // the client's own count after a real click that was kept (it takes one off by itself first)
        l.add(new Step(null, () -> UiTest.server(p -> {
            Building.set(p, 10000, 0);
            Building.testRoll = 0F;
            ORIGIN[1] = ORIGIN[0].offset(2, 0, 1);
            prep(p.serverLevel(), ORIGIN[1]);
            hold(p, Items.STONE, 30);
        }), 20));
        l.add(new Step(null, () -> mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, top(ORIGIN[1])), 20));
        l.add(new Step(null, () -> {
            int client = mc.player.getMainHandItem().getCount();
            UiTest.server(p -> {
                boolean placed = p.serverLevel().getBlockState(ORIGIN[1]).is(Blocks.STONE);
                int server = p.getMainHandItem().getCount();
                log("client click kept: placed=" + placed + " server stack " + server + " client stack " + client + " (expect 30 / 30) -> " + pass(placed && server == 30 && client == 30));
                Building.testRoll = null;
            });
        }, 10));

        // ---- 4. bots: their character's Building, in the owner's save
        l.add(new Step(null, () -> UiTest.server(p -> {
            Building.set(p, 3, 0);
            PartyBotEntity b = PartyBots.spawn(p, CharacterType.RHALIX, null);
            if (b == null) { log("bot: could not spawn -> FAIL"); return; }
            b.setNoAi(true);
            int bl0 = Building.level(b), own0 = Building.level(p);
            ServerLevel lv = p.serverLevel();
            for (int i = 0; i < 25; i++) {                     // torches / blocks it places
                BlockPos at = spot();
                prep(lv, at);
                lv.setBlock(at, Blocks.TORCH.defaultBlockState(), 3);
                Building.botPlaced(b, at, lv.getBlockState(at));
            }
            int bl1 = Building.level(b);
            for (int i = 0; i < 25; i++) {                     // the Dragon Ball way: a snapshot, the block, then the place event
                BlockPos at = spot();
                prep(lv, at);
                BlockSnapshot snap = BlockSnapshot.create(lv.dimension(), lv, at);
                lv.setBlock(at, Blocks.GOLD_BLOCK.defaultBlockState(), 3);
                MinecraftForge.EVENT_BUS.post(new BlockEvent.EntityPlaceEvent(snap, lv.getBlockState(at.below()), b));
            }
            int bl2 = Building.level(b);
            log("bot (Rhalix) Building Lv" + bl0 + " -> " + bl1 + " after 25 torches (6.25 xp) -> " + bl2 + " after 25 place events (25 xp); owner's own (Mikiko) Lv" + own0 + " -> " + Building.level(p)
                    + " -> " + pass(bl2 > bl1 && bl1 >= bl0 && Building.level(p) == own0));
            REACH[2] = bl2;
            b.discard();
            com.xenowarriors.character.CharacterManager.setCharacter(p, CharacterType.RHALIX);
            log("owner now plays Rhalix: Building Lv" + Building.level(p) + " (the bot's " + bl2 + ") -> " + pass(Building.level(p) == bl2));
            com.xenowarriors.character.CharacterManager.setCharacter(p, CharacterType.MIKIKO);
            log("back to Mikiko: Building Lv" + Building.level(p) + " (expect 3) -> " + pass(Building.level(p) == 3));
        }), 30));

        // ---- 5. the stats menu
        l.add(new Step(null, () -> UiTest.server(p -> Building.set(p, 47, Building.needed(47) * 0.6F)), 20));
        l.add(new Step(null, () -> com.xenowarriors.client.PlayerStatsScreen.openTab(null, 1), 50));
        l.add(new Step("building_stats", () -> {}, 5));
        l.add(new Step(null, () -> com.xenowarriors.client.PlayerStatsScreen.testBuildingTip = true, 10));
        l.add(new Step("building_stats_tip", () -> {
            StringBuilder sb = new StringBuilder();
            for (var c : com.xenowarriors.client.PlayerStatsScreen.buildingTip(47)) sb.append(" | ").append(c.getString());
            log("tooltip at Lv47:" + sb);
        }, 5));
        l.add(new Step(null, () -> { com.xenowarriors.client.PlayerStatsScreen.testBuildingTip = false; mc.setScreen(null); }, 10));
        l.add(new Step(null, () -> com.xenowarriors.client.menu.CharacterCustomScreen.open(null), 60));
        l.add(new Step("building_custom", () -> {}, 5));
        l.add(new Step(null, () -> mc.setScreen(null), 10));
        l.add(new Step(null, () -> log("done"), 5));
    }
}

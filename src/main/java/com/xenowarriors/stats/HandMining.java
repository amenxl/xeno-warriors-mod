package com.xenowarriors.stats;

import com.xenowarriors.XenoWarriors;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.TierSortingRegistry;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * Bare-handed mining: the higher your Mining level, the better a pickaxe your bare hands count as - for what drops
 * (stone gives cobblestone, iron ore gives raw iron, ...) and for how fast you dig.
 * <ul>
 *   <li>Lv 5 wooden, 15 stone, 40 iron, 100 diamond, 250 netherite, 1,000 "master" (the best tool tier any installed mod
 *   has - for modded ores that need more than netherite).</li>
 *   <li>Drops: blocks in {@code minecraft:mineable/pickaxe} whose {@code needs_*_tool} tier (Forge's tier sorting, so
 *   modded tiers and tags work) your hand-tier reaches. Silk Touch / Fortune never apply: hands aren't enchanted.</li>
 *   <li>Speed: on pickaxe, axe and shovel blocks the hand digs at that tier's tool speed (as fast as the pickaxe, axe or
 *   shovel of that tier), times the normal Mining bonus; past netherite an Efficiency-like bonus (I at Lv 400, up to V at
 *   Lv 1,000). Never slower than the old bare-hand bonus.</li>
 *   <li>"Bare hand" = an empty main hand, or holding something that isn't a tool or weapon (a block, food, a torch...).</li>
 *   <li>Bots: their bare hands mine like their owner's Mining level (Mining is one level per player, shared by every
 *   character), see {@link #botTool} / {@link #botSpeed}.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class HandMining {
    private HandMining() {}

    /** The Mining level each hand-tier starts at: wooden, stone, iron, diamond, netherite, master. */
    public static final int[] LEVELS = {5, 15, 40, 100, 250, 1000};
    public static final String[] NAMES = {"Wooden", "Stone", "Iron", "Diamond", "Netherite", "Master"};
    private static final Tier[] VANILLA = {Tiers.WOOD, Tiers.STONE, Tiers.IRON, Tiers.DIAMOND, Tiers.NETHERITE};
    private static final net.minecraft.world.item.Item[] PICKS = {Items.WOODEN_PICKAXE, Items.STONE_PICKAXE, Items.IRON_PICKAXE,
            Items.DIAMOND_PICKAXE, Items.NETHERITE_PICKAXE, Items.NETHERITE_PICKAXE};
    /** Efficiency-like bonus past netherite: +1 Efficiency level every 150 Mining levels from 250, up to V. */
    public static final int EFF_START = 250, EFF_STEP = 150, EFF_MAX = 5;

    /** Which hand-tier a Mining level gives: -1 none, 0 wooden ... 5 master. */
    public static int tierIndex(int level) {
        int t = -1;
        for (int i = 0; i < LEVELS.length; i++) if (level >= LEVELS[i]) t = i;
        return t;
    }

    /** The tool tier the hand counts as (null below Lv 5). Master = the highest tier in Forge's sorted list. */
    public static Tier tier(int level) {
        int i = tierIndex(level);
        if (i < 0) return null;
        if (i < VANILLA.length) return VANILLA[i];
        List<Tier> sorted = TierSortingRegistry.getSortedTiers();
        return sorted.isEmpty() ? Tiers.NETHERITE : sorted.get(sorted.size() - 1);
    }

    public static String name(int tierIndex) {
        return tierIndex < 0 ? "Bare hands" : NAMES[tierIndex] + " Pickaxe";
    }

    /** The Efficiency level the hand has (0 until Lv 400). */
    public static int efficiency(int level) {
        return level < EFF_START + EFF_STEP ? 0 : Math.min(EFF_MAX, (level - EFF_START) / EFF_STEP);
    }

    /** The hand-tier's tool speed (2 wooden ... 9 netherite, plus Efficiency like a real tool's eff^2+1), 1 below Lv 5. */
    public static float toolSpeed(int level) {
        Tier t = tier(level);
        if (t == null) return 1F;
        int e = efficiency(level);
        return Math.max(1F, t.getSpeed()) + (e > 0 ? e * e + 1 : 0);
    }

    /** Whether a hand-tier digs this block at tool speed: pickaxe blocks (and, for speed only, axe and shovel blocks). */
    public static boolean speedApplies(BlockState st) {
        return st.is(BlockTags.MINEABLE_WITH_PICKAXE) || st.is(BlockTags.MINEABLE_WITH_AXE) || st.is(BlockTags.MINEABLE_WITH_SHOVEL);
    }

    /** Whether bare hands at this Mining level get this block's drops (a pickaxe block their tier is good enough for). */
    public static boolean canHarvest(int level, BlockState st) {
        if (!st.requiresCorrectToolForDrops()) return true;
        Tier t = tier(level);
        return t != null && st.is(BlockTags.MINEABLE_WITH_PICKAXE) && TierSortingRegistry.isCorrectTierForDrops(t, st);
    }

    /** How much faster than a plain bare hand (speed 1) this level digs the block: tool speed x Mining bonus, never less than the old hand bonus. */
    public static float speedFactor(int level, BlockState st) {
        float old = Mining.multiplier(level, true);
        if (tier(level) == null || !speedApplies(st)) return old;
        return Math.max(old, toolSpeed(level) * Mining.multiplier(level, false));
    }

    /**
     * Whether this counts as a bare hand: empty, or something that isn't a tool or weapon (no durability, no tool tier,
     * no digging actions, no special dig speed on this block) - not the Hekublade or a summoned form either.
     */
    public static boolean bareHand(ItemStack s, BlockState st) {
        if (s.isEmpty()) return true;
        if (s.isDamageableItem() || s.getItem() instanceof TieredItem || s.getItem() instanceof DiggerItem
                || s.getItem() instanceof com.xenowarriors.forms.FormItems.Summoned || com.xenowarriors.mikiko.Hekublade.isBlade(s)) return false;
        if (s.canPerformAction(ToolActions.PICKAXE_DIG) || s.canPerformAction(ToolActions.AXE_DIG) || s.canPerformAction(ToolActions.SHOVEL_DIG)
                || s.canPerformAction(ToolActions.HOE_DIG) || s.canPerformAction(ToolActions.SWORD_DIG) || s.canPerformAction(ToolActions.SHEARS_DIG)) return false;
        return st == null || s.getDestroySpeed(st) <= 1F;
    }

    // ---------------------------------------------------------------- players

    /** Bare hands with a high enough Mining level get the drops (also makes the dig count as the right tool: ~3x faster). */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onHarvestCheck(PlayerEvent.HarvestCheck event) {
        if (event.canHarvest()) return;
        Player p = event.getEntity();
        BlockState st = event.getTargetBlock();
        if (p.isCreative() || !bareHand(p.getMainHandItem(), st)) return;
        int lv = Mining.level(p);
        if (lv >= LEVELS[0] && canHarvest(lv, st)) event.setCanHarvest(true);
    }

    /** The speed half: see {@link #speedFactor}. (Replaces Mining's old bare-hand bonus.) */
    static float handSpeed(Player p, BlockState st, float speed) {
        int lv = Mining.level(p);
        return speed * speedFactor(lv, st);
    }

    /** Level-up message when a Mining level crosses a hand-tier. */
    public static void leveled(ServerPlayer p, int before, int after) {
        int a = tierIndex(before), b = tierIndex(after);
        if (b <= a) return;
        int next = b + 1 < LEVELS.length ? LEVELS[b + 1] : -1;
        p.sendSystemMessage(Component.literal("Your bare hands now mine like a" + (b == 2 ? "n " : " ") + name(b)
                + (b == 5 ? " (anything any pickaxe can mine)" : "") + "!"
                + (next > 0 ? " Next: " + NAMES[b + 1] + " at Mining Lv " + next + "." : "")).withStyle(ChatFormatting.GOLD));
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 0.6F, 1.6F);
    }

    /** "Iron Pickaxe (next: Diamond at Lv 100)" - for the stats menu. */
    public static String describe(int level) {
        int i = tierIndex(level);
        String now = i < 0 ? "nothing yet" : name(i);
        int e = efficiency(level);
        if (e > 0) now += " (Efficiency " + roman(e) + ")";
        if (i + 1 < LEVELS.length) return now + " (next: " + NAMES[i + 1] + " at Lv " + String.format(java.util.Locale.ROOT, "%,d", LEVELS[i + 1]) + ")";
        if (e < EFF_MAX) return now + " (next: Efficiency " + roman(e + 1) + " at Lv " + String.format(java.util.Locale.ROOT, "%,d", EFF_START + (e + 1) * EFF_STEP) + ")";
        return now + " (max tier)";
    }

    private static String roman(int n) {
        return switch (n) { case 1 -> "I"; case 2 -> "II"; case 3 -> "III"; case 4 -> "IV"; default -> "V"; };
    }

    // ---------------------------------------------------------------- bots

    /** A bot's Mining level: its owner's (Mining is one level per player, for every character). */
    public static int botLevel(com.xenowarriors.bot.PartyBotEntity b) {
        ServerPlayer o = b.owner();
        return o == null ? 0 : Mining.level(o);
    }

    /**
     * What a bot digs this block with, for its drops: what it holds if that's the right tool, else its bare hand at its
     * hand-tier (as that tier's plain pickaxe), else EMPTY - it gets nothing out (like a player's hand).
     */
    public static ItemStack botTool(com.xenowarriors.bot.PartyBotEntity b, BlockState st) {
        ItemStack held = b.getMainHandItem();
        if (held.isCorrectToolForDrops(st)) return held;
        int lv = botLevel(b);
        if (!st.requiresCorrectToolForDrops()) return held;
        if (!canHarvest(lv, st)) return ItemStack.EMPTY;
        return new ItemStack(PICKS[tierIndex(lv)]);
    }

    /** Whether a bot gets anything out of this block: the right tool in its hand or bag, or bare hands good enough. */
    public static boolean botCanHarvest(com.xenowarriors.bot.PartyBotEntity b, BlockState st) {
        if (!st.requiresCorrectToolForDrops() || b.getMainHandItem().isCorrectToolForDrops(st)) return true;
        for (int i = 0; i < b.inventory.getContainerSize(); i++) if (b.inventory.getItem(i).isCorrectToolForDrops(st)) return true;
        return canHarvest(botLevel(b), st);
    }

    /** A bot's dig speed on this block: its tool's, or its hand-tier's when what it holds isn't a better tool. */
    public static float botSpeed(com.xenowarriors.bot.PartyBotEntity b, BlockState st) {
        ItemStack held = b.getMainHandItem();
        float speed = Math.max(1F, held.getDestroySpeed(st));
        if (bareHand(held, st) || !held.isCorrectToolForDrops(st) && speed <= 1F) speed = Math.max(speed, speedFactor(botLevel(b), st));
        return speed;
    }
}

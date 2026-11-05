package com.xenowarriors.statboost;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.bot.PartyBotEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Stat Boosts: one item per stat per tier (+1, +2, +5, +10, +100 levels), and where they come from.
 * <ul>
 *   <li>Enemies (any hostile mob) and bosses killed by a player or a party bot: one roll per kill, see the tables
 *   below. The +100 only drops from very strong enemies - bosses, or something at least {@link #STRONG_RATIO} times as
 *   strong as its killer (Karma Sense strength) and at least {@link #STRONG_MIN} - and is always the rarest.
 *   A bot's kill drops it for its owner (only the owner can pick it up).</li>
 *   <li>Digging dirt-like blocks with a shovel (survival): {@link #DIRT_CHANCE}, mostly +1, mostly Mining.</li>
 *   <li>Every level-up of any stat: {@link #LEVEL_UP_CHANCE} to get a boost for that same stat (mostly +1 / +2),
 *   straight into the inventory (or at your feet). A bot's level-up gives one to its owner. Levels from a boost item
 *   never roll this (no chains).</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class StatBoosts {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, XenoWarriors.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(net.minecraft.core.registries.Registries.CREATIVE_MODE_TAB, XenoWarriors.MOD_ID);
    private static final Map<BoostStat, Map<BoostTier, RegistryObject<Item>>> BY = new EnumMap<>(BoostStat.class);

    static {
        for (BoostStat s : BoostStat.values()) {
            Map<BoostTier, RegistryObject<Item>> m = new EnumMap<>(BoostTier.class);
            for (BoostTier t : BoostTier.values())
                m.put(t, ITEMS.register(name(s, t), () -> new StatBoostItem(s, t, new Item.Properties().stacksTo(64))));
            BY.put(s, m);
        }
    }

    /** "Xeno Warriors: Stat Boosts": every boost, a row per tier. */
    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("stat_boosts",
            () -> CreativeModeTab.builder()
                    .title(Component.translatableWithFallback("itemGroup.xenowarriors.stat_boosts", "Xeno Warriors: Stat Boosts"))
                    .icon(() -> stack(BoostStat.ATTACK, BoostTier.HUNDRED))
                    .withTabsBefore(XenoWarriors.id("xenowarriors"))
                    .displayItems((params, out) -> {
                        for (ItemStack s : tabItems()) out.accept(s);
                    })
                    .build());

    // ---------------------------------------------------------------- rates

    /** Drop tables: chance of each tier for one roll, +1 .. +100 (one item at most; the rarest tier is checked first). */
    public static final float[] NORMAL = {0.02F, 0.01F, 0.003F, 0.0008F, 0F};
    public static final float[] STRONG = {0.10F, 0.06F, 0.03F, 0.015F, 0.01F};
    public static final float[] BOSS = {0.25F, 0.15F, 0.10F, 0.05F, 0.025F};
    /** Shovel on dirt: chance per block, then the tier split (no +100). */
    public static final float DIRT_CHANCE = 0.004F;
    public static final float[] DIRT_SPLIT = {0.80F, 0.15F, 0.04F, 0.01F, 0F};
    /** Share of dirt drops that are Mining boosts (the rest: any stat this character has). */
    public static final float DIRT_MINING = 0.6F;
    /** Level-up: chance per level-up, then the tier split. */
    public static final float LEVEL_UP_CHANCE = 0.025F;
    public static final float[] LEVEL_UP_SPLIT = {0.75F, 0.20F, 0.05F, 0F, 0F};
    /** "Very strong": at least this many times its killer's strength, and at least {@link #STRONG_MIN} (or a boss). */
    public static final float STRONG_RATIO = 3F, STRONG_MIN = 80F;

    /** Tests: fixed rolls (0..1) in place of the random ones (null = random). */
    public static Float testRoll, testStatRoll;
    /** Tests: counts of drops by source. */
    public static int testMobDrops, testDirtDrops, testLevelUpDrops, testDirtSkipped;
    /** While a boost is being applied: its levels never roll the level-up drop. */
    private static int applying;

    private StatBoosts() {}

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        TABS.register(modBus);
    }

    public static String name(BoostStat s, BoostTier t) { return "stat_boost_" + s.id + "_" + t.levels; }

    public static Item item(BoostStat s, BoostTier t) { return BY.get(s).get(t).get(); }

    public static ItemStack stack(BoostStat s, BoostTier t) { return new ItemStack(item(s, t)); }

    /** What the creative tab shows: tier by tier, every stat (Season 2 stats once Season 2 is unlocked in this world). */
    public static List<ItemStack> tabItems() {
        List<ItemStack> l = new ArrayList<>();
        for (BoostTier t : BoostTier.values())
            for (BoostStat s : BoostStat.values())
                if (s.season() <= 1 || com.xenowarriors.character.Seasons.unlockedForWorldgen(s.season())) l.add(stack(s, t));
        return l;
    }

    private static float roll(RandomSource r) { return testRoll != null ? testRoll : r.nextFloat(); }

    private static float statRoll(RandomSource r) { return testStatRoll != null ? testStatRoll : r.nextFloat(); }

    /** The tier one roll gives from a table (rarest first, so +100 takes the lowest slice), or null for nothing. */
    public static BoostTier pick(float[] table, float roll) {
        float acc = 0;
        for (int i = BoostTier.values().length - 1; i >= 0; i--) {
            acc += table[i];
            if (table[i] > 0 && roll < acc) return BoostTier.values()[i];
        }
        return null;
    }

    /** Stats this player's character has (Hekublade only for Mikiko, wires only for Rhalix in Season 2). */
    public static List<BoostStat> pool(ServerPlayer p) {
        List<BoostStat> l = new ArrayList<>();
        for (BoostStat s : BoostStat.values()) if (s.usable(p)) l.add(s);
        if (l.isEmpty()) for (BoostStat s : BoostStat.values()) if (s.season() == 1 && s != BoostStat.HEKUBLADE) l.add(s);
        return l;
    }

    private static BoostStat randomStat(ServerPlayer p, RandomSource r) {
        List<BoostStat> l = pool(p);
        return l.get(Math.min(l.size() - 1, (int) (statRoll(r) * l.size())));
    }

    /** Adds the levels (no level-up drop roll for them). */
    public static int apply(ServerPlayer p, BoostStat s, int levels) {
        applying++;
        try {
            return s.add(p, levels);
        } finally {
            applying--;
        }
    }

    // ---------------------------------------------------------------- enemies and bosses

    /** Very strong: a boss, or at least {@link #STRONG_RATIO}x its killer's strength (and {@link #STRONG_MIN}). */
    public static boolean veryStrong(LivingEntity victim, LivingEntity killer) {
        if (com.xenowarriors.karma.KarmaSensing.boss(victim)) return true;
        float v = com.xenowarriors.karma.KarmaSensing.strength(victim);
        float k = killer == null ? 1F : com.xenowarriors.karma.KarmaSensing.strength(killer);
        return v >= STRONG_MIN && v >= STRONG_RATIO * k;
    }

    public static float[] table(LivingEntity victim, LivingEntity killer) {
        if (com.xenowarriors.karma.KarmaSensing.boss(victim)) return BOSS;
        return veryStrong(victim, killer) ? STRONG : NORMAL;
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onDrops(LivingDropsEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide || event.isCanceled() || victim instanceof Player || victim instanceof PartyBotEntity) return;
        if (!(victim instanceof Enemy) && !com.xenowarriors.karma.KarmaSensing.boss(victim)) return;
        Entity src = event.getSource().getEntity();
        if (src instanceof FakePlayer fp) { PartyBotEntity b = com.xenowarriors.bot.PartyBots.botOf(fp); if (b != null) src = b; }   // (a bot's skill hits as its stand-in)
        ServerPlayer owner;
        LivingEntity killer;
        if (src instanceof ServerPlayer p && !(p instanceof FakePlayer) && !p.isSpectator()) { owner = p; killer = p; }
        else if (src instanceof PartyBotEntity b && b.owner() != null && !b.journey()) { owner = b.owner(); killer = b; }
        else return;
        BoostTier t = pick(table(victim, killer), roll(victim.getRandom()));
        if (t == null) return;
        BoostStat s = randomStat(owner, victim.getRandom());
        ItemEntity it = new ItemEntity(victim.level(), victim.getX(), victim.getY() + 0.5, victim.getZ(), stack(s, t));
        it.setDefaultPickUpDelay();
        if (killer != owner) { it.setTarget(owner.getUUID()); it.setThrower(owner.getUUID()); }   // a bot's kill: its owner's (bots don't take it)
        event.getDrops().add(it);
        testMobDrops++;
        if (t.ordinal() >= BoostTier.TEN.ordinal()) {
            owner.sendSystemMessage(Component.literal((killer != owner ? killer.getName().getString() + "'s kill" : victim.getName().getString())
                    + " dropped a " + s.label + " Boost +" + t.levels + "!").withStyle(t.color));
            victim.level().playSound(null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 1F, 0.8F);
        }
        XenoWarriors.LOGGER.debug("XW-BOOST {} killed {}: {} +{}", killer.getName().getString(), victim.getName().getString(), s.id, t.levels);
    }

    // ---------------------------------------------------------------- shovel on dirt

    /** Dirt-like blocks: dirt, grass, coarse / rooted dirt, podzol, mycelium, moss, mud, farmland, dirt paths. */
    public static boolean dirtLike(BlockState st) {
        return st.is(BlockTags.DIRT) || st.is(Blocks.FARMLAND) || st.is(Blocks.DIRT_PATH) || st.is(Blocks.MUDDY_MANGROVE_ROOTS)
                || st.is(Blocks.CLAY) || st.is(Blocks.SOUL_SOIL);
    }

    public static boolean shovel(ItemStack s) {
        return !s.isEmpty() && (s.getItem() instanceof net.minecraft.world.item.ShovelItem || s.canPerformAction(ToolActions.SHOVEL_DIG));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.isCanceled() || !(event.getPlayer() instanceof ServerPlayer p) || p instanceof FakePlayer) return;
        if (p.isCreative() || p.isSpectator() || !(event.getLevel() instanceof ServerLevel l)) return;
        if (!dirtLike(event.getState()) || !shovel(p.getMainHandItem())) return;
        if (com.xenowarriors.character.ModCapabilities.getType(p) == com.xenowarriors.character.CharacterType.NONE) return;
        // dirt they just put down themselves: no roll (place + dig on one spot was an endless boost farm)
        if (com.xenowarriors.stats.Building.placedRecently(p.getUUID(), l, event.getPos())) { testDirtSkipped++; return; }
        RandomSource r = p.getRandom();
        float roll = roll(r);
        if (roll >= DIRT_CHANCE) return;
        BoostTier t = pick(DIRT_SPLIT, roll / DIRT_CHANCE);
        if (t == null) return;
        BoostStat s = statRoll(r) < DIRT_MINING ? BoostStat.MINING : randomStat(p, r);
        dropAt(l, event.getPos(), stack(s, t));
        testDirtDrops++;
        p.displayClientMessage(Component.literal("You dug up a " + s.label + " Boost +" + t.levels + "!").withStyle(t.color), true);
        l.playSound(null, event.getPos(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1F, 1.6F);
    }

    private static void dropAt(ServerLevel l, BlockPos pos, ItemStack s) {
        Block.popResource(l, pos, s);
    }

    // ---------------------------------------------------------------- level-ups

    /** A stat of this player went up (by XP): maybe a boost for that stat. */
    public static void leveledUp(ServerPlayer p, BoostStat s) {
        if (p == null || p instanceof FakePlayer || applying > 0 || p.isCreative()) return;
        RandomSource r = p.getRandom();
        float roll = roll(r);
        if (roll >= LEVEL_UP_CHANCE) return;
        BoostTier t = pick(LEVEL_UP_SPLIT, roll / LEVEL_UP_CHANCE);
        if (t == null) return;
        give(p, stack(s, t));
        testLevelUpDrops++;
        p.sendSystemMessage(Component.literal("Levelling up " + s.label + " left a " + s.label + " Boost +" + t.levels + "!").withStyle(t.color));
    }

    /** A bot's stat went up: maybe a boost for that stat, for its owner. */
    public static void botLeveledUp(PartyBotEntity b, ServerPlayer owner, BoostStat s) {
        if (b == null || owner == null || applying > 0 || b.journey()) return;
        RandomSource r = b.getRandom();
        float roll = roll(r);
        if (roll >= LEVEL_UP_CHANCE) return;
        BoostTier t = pick(LEVEL_UP_SPLIT, roll / LEVEL_UP_CHANCE);
        if (t == null) return;
        give(owner, stack(s, t));
        testLevelUpDrops++;
        owner.sendSystemMessage(Component.literal("").append(b.label()).append(Component.literal(" levelled up " + s.label + " and found a "
                + s.label + " Boost +" + t.levels + " for you!")).withStyle(t.color));
    }

    private static void give(ServerPlayer p, ItemStack s) {
        if (!p.getInventory().add(s)) p.drop(s, false);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1F, 1.8F);
    }
}

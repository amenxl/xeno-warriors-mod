package com.xenowarriors.bot;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.xenowarriors.XenoWarriors;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

/**
 * Bot teammates craft themselves better gear and fix what they have - on their own.
 *
 * <p>Every few seconds out of a fight a bot looks through its bag against the game's real crafting recipes for armour,
 * swords, axes and pickaxes better than the best it has (worn, in hand or in the bag). Rules:
 * <ul>
 *   <li>Recipes that fit a 2x2 grid it can always make; 3x3 ones need a crafting table - one in its bag, or a placed
 *   one within {@value #STATION_RANGE} blocks of it (or it makes its own from planks/a log).</li>
 *   <li>Repairs: worn armour or its best weapon/tool under 25% durability. Two damaged copies of the same unenchanted
 *   item combine (the crafting-grid repair: both durabilities plus 5%). Otherwise, with an anvil (in its bag or within
 *   {@value #STATION_RANGE} blocks) and the item's repair material, each material mends a quarter, enchantments kept.</li>
 * </ul>
 * When it can do one it just does it (only when it's safe and its bag isn't full) and tells you what it made. When it's
 * exactly one item short (or only missing the table/anvil) it asks you for that item - but only when you actually have
 * one on you ([Give 1 &amp; craft] / [No thanks]); otherwise it says nothing and goes to get it itself (ore it can mine:
 * see {@link #gatherTarget}) or drops the idea. Each idea has a cooldown, and a "No thanks" is remembered for a while.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BotCrafting {
    private BotCrafting() {}

    public static final int STATION_RANGE = 6;
    /** How often a bot looks (ticks), how long the same idea stays quiet after it was mentioned, after a "No thanks", and how long an offer stands. */
    public static final int CHECK_EVERY = 100, REPEAT_COOLDOWN = 5 * 60 * 20, DECLINE_COOLDOWN = 20 * 60 * 20, OFFER_TICKS = 30 * 20, REPAIR_COOLDOWN = 15 * 60 * 20;
    /** A repair is only suggested once it's this worn (under 25% durability left). */
    public static final float REPAIR_AT = 0.75F;

    /** What a bot could do: craft (a recipe), or repair (an item in a slot). */
    public static final class Idea {
        public final String kind;              // "craft", "repair_combine", "repair_anvil"
        public final ResourceLocation recipe;  // craft
        public final ItemStack result;         // what it makes (craft), or the item to fix (repair)
        public final int missing;              // 0 = can do it now, 1 = one short
        public final Component missingWhat;    // what it's short of (when missing == 1)
        public final ItemStack missingItem;    // the item it's short of (EMPTY for a crafting table/anvil block)
        public final float gain;               // how much better (for picking the best idea)
        final int slot;                        // repair: the bag slot (or -1 - 6 for gear, see GEAR)
        final int material;                    // repair: how many materials it uses
        int token;
        public boolean makesTable;             // craft: it makes itself a crafting table first (none about)
        Idea(String kind, ResourceLocation recipe, ItemStack result, int missing, Component missingWhat, ItemStack missingItem, float gain, int slot, int material) {
            this.kind = kind; this.recipe = recipe; this.result = result; this.missing = missing; this.missingWhat = missingWhat; this.missingItem = missingItem;
            this.gain = gain; this.slot = slot; this.material = material;
        }
        String key() { return kind + ":" + net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(result.getItem()) + ":" + missing; }
    }

    private static final Map<UUID, Idea> OFFERS = new HashMap<>();
    private static final Map<UUID, Long> OFFER_UNTIL = new HashMap<>();
    /** owner + character -> idea key -> quiet until (game time). */
    private static final Map<String, Map<String, Long>> QUIET = new HashMap<>();
    private static int nextToken = 1;

    /** Waiting for its owner's answer to an offer (its bag stays as it is meanwhile - see BotBags). */
    static boolean offering(PartyBotEntity b) {
        Long until = OFFER_UNTIL.get(b.getUUID());
        return OFFERS.containsKey(b.getUUID()) && until != null && until > b.level().getGameTime();
    }

    private static Map<String, Long> quiet(PartyBotEntity b) { return QUIET.computeIfAbsent(b.ownerId() + "/" + b.character().getId(), k -> new HashMap<>()); }

    // ---------------------------------------------------------------- the periodic look

    /** From the bot's tick (every second): every CHECK_EVERY ticks, out of a fight, a look through its bag. */
    public static void tick(PartyBotEntity b) {
        if (b.tickCount % CHECK_EVERY != 0 || b.downed() || b.journey() || b.sparring() || b.getTarget() != null || b.isNoAi()) return;
        ServerPlayer o = b.owner();
        if (o == null || o.level() != b.level() || o.distanceToSqr(b) > 32 * 32) return;
        if (b.getLastHurtByMobTimestamp() > 0 && b.tickCount - b.getLastHurtByMobTimestamp() < 200) return;   // (just been in a fight)
        String danger = BotErrands.danger(b);                                   // (never crafts or asks in a fight or in danger: enemies about, hurt, in lava, the owner fighting...)
        if (danger != null) { if (b.tickCount % 400 == 0) PartyBots.testLog(b, "craft: no offers now - " + danger); b.offersHeld++; return; }
        long now = b.level().getGameTime();
        Long until = OFFER_UNTIL.get(b.getUUID());
        if (until != null && until > now) return;                              // (asked you for an item: waiting for an answer)
        log(b, "periodic look at tick " + b.tickCount);
        suggest(b, o, false);
    }

    /** You closed its bag: a look right away (cooldowns still apply). */
    public static void bagClosed(PartyBotEntity b, ServerPlayer o) {
        if (b.downed() || b.journey() || b.sparring() || b.getTarget() != null || BotErrands.danger(b) != null) return;
        Long until = OFFER_UNTIL.get(b.getUUID());
        if (until != null && until > b.level().getGameTime()) return;
        suggest(b, o, false);
    }

    /**
     * Look now: the best idea that isn't on cooldown. One it can do now it simply does; one it's an item short of it
     * asks the owner for - only if the owner has that item on them (ideas for items they don't have are skipped
     * silently). force: ignore cooldowns (tests). Returns the idea acted on, or null.
     */
    public static Idea suggest(PartyBotEntity b, ServerPlayer o, boolean force) {
        long now = b.level().getGameTime();
        List<Idea> ideas = ideas(b);
        Map<String, Long> q = quiet(b);
        q.values().removeIf(t -> t <= now);
        Idea pick = null;
        int skipped = 0;
        for (Idea i : ideas) {
            if (!force && q.getOrDefault(i.key(), 0L) > now) continue;
            if (i.missing > 0 && (i.missingItem.isEmpty() || o == null || !ownerHas(o, i.missingItem))) { skipped++; continue; }   // (you don't have it: no asking)
            if (pick == null || i.missing < pick.missing || i.missing == pick.missing && i.gain > pick.gain) pick = i;
        }
        if (skipped > 0) log(b, "not asking for " + skipped + " idea(s) - the owner doesn't have the item");
        if (pick == null) return null;
        q.put(pick.key(), now + REPEAT_COOLDOWN);
        if (!pick.kind.equals("craft")) {                                        // repairs: much rarer - once in 15 minutes per item
            String item = String.valueOf(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(pick.result.getItem()));
            for (String k : new String[]{"repair_combine", "repair_anvil"}) for (int m = 0; m <= 1; m++) q.put(k + ":" + item + ":" + m, now + REPAIR_COOLDOWN);
        }
        if (pick.missing == 0) {                                                 // it can: no asking, it just does it
            log(b, "auto " + pick.key());
            do_(b, o, pick, true);
        } else tell(b, o, pick);
        return pick;
    }

    /** Everything it could do now, or with one more item. */
    public static List<Idea> ideas(PartyBotEntity b) {
        List<Idea> out = new ArrayList<>();
        if (!(b.level() instanceof ServerLevel s)) return out;
        boolean table = hasStation(b, Items.CRAFTING_TABLE, Blocks.CRAFTING_TABLE);
        boolean full = freeSlots(b.inventory) == 0;                             // (a full bag: nothing new made - it would only end up on the ground)
        for (CraftingRecipe r : full ? List.<CraftingRecipe>of() : candidates(s)) {
            ItemStack res = r.getResultItem(s.registryAccess());
            float gain = gain(b, res);
            if (gain <= 0F) continue;
            List<Ingredient> ing = new ArrayList<>();
            for (Ingredient i : r.getIngredients()) if (!i.isEmpty()) ing.add(i);
            if (ing.isEmpty()) continue;
            List<Ingredient> missing = match(b.inventory, ing, null);
            boolean big = !r.canCraftInDimensions(2, 2), selfTable = false;
            if (big && !table && missing.isEmpty()) {                            // no table about: it can make its own from its planks or a log, if that still leaves it enough
                SimpleContainer copy = copyOf(b.inventory);
                if (makeTable(copy) && match(copy, ing, null).isEmpty()) selfTable = true;
            }
            int short_ = missing.size() + (big && !table && !selfTable ? 1 : 0);
            if (short_ > 1) continue;
            Component what = null; ItemStack whatItem = ItemStack.EMPTY;
            if (!missing.isEmpty()) { whatItem = firstItem(missing.get(0)); what = whatItem.getHoverName(); }
            else if (big && !table && !selfTable) { whatItem = new ItemStack(Items.CRAFTING_TABLE); what = Component.literal("a crafting table (in its bag or close by)"); }
            Idea idea = new Idea("craft", r.getId(), res.copy(), short_, what, whatItem, gain, -1, 0);
            idea.makesTable = selfTable;
            out.add(idea);
        }
        repairs(b, out);
        return out;
    }

    private static ItemStack firstItem(Ingredient i) {
        ItemStack[] items = i.getItems();
        return items.length == 0 ? ItemStack.EMPTY : items[0].copyWithCount(1);
    }

    /** Crafting recipes that make armour, swords, axes or pickaxes (cached per recipe reload). */
    private static List<CraftingRecipe> cache;
    private static Object cacheFor;

    private static List<CraftingRecipe> candidates(ServerLevel s) {
        var rm = s.getRecipeManager();
        if (cache != null && cacheFor == rm && !cache.isEmpty() && rm.byKey(cache.get(0).getId()).orElse(null) == cache.get(0)) return cache;
        List<CraftingRecipe> l = new ArrayList<>();
        for (CraftingRecipe r : rm.getAllRecipesFor(RecipeType.CRAFTING)) {
            if (r.isSpecial()) continue;
            ItemStack res;
            try { res = r.getResultItem(s.registryAccess()); } catch (Throwable t) { continue; }
            if (res.isEmpty()) continue;
            Item it = res.getItem();
            if (it instanceof ArmorItem || it instanceof SwordItem || it instanceof AxeItem || it instanceof PickaxeItem) l.add(r);
        }
        cache = l; cacheFor = rm;
        return l;
    }

    // ---------------------------------------------------------------- what's "better"

    private static float armourScore(ItemStack s) {
        return s.getItem() instanceof ArmorItem a ? a.getDefense() * 10F + a.getToughness() + a.getMaterial().getEnchantmentValue() * 0.01F : -1F;
    }

    private static final BlockState STONE = Blocks.STONE.defaultBlockState(), LOG = Blocks.OAK_LOG.defaultBlockState();

    /** Everything it has: worn, in hand, offhand and its bag. */
    private static List<ItemStack> owned(PartyBotEntity b) {
        List<ItemStack> l = new ArrayList<>();
        for (EquipmentSlot es : EquipmentSlot.values()) l.add(b.getItemBySlot(es));
        for (int i = 0; i < b.inventory.getContainerSize(); i++) l.add(b.inventory.getItem(i));
        return l;
    }

    /** How much better this would be than the best of its kind it already has (0 or less: not better). */
    static float gain(PartyBotEntity b, ItemStack res) {
        Item it = res.getItem();
        float best = -1F, mine;
        if (it instanceof ArmorItem a) {
            mine = armourScore(res);
            for (ItemStack s : owned(b)) if (s.getItem() instanceof ArmorItem o && o.getEquipmentSlot() == a.getEquipmentSlot()) best = Math.max(best, armourScore(s));
        } else if (it instanceof PickaxeItem) {
            mine = BotGear.toolScore(res, STONE);
            for (ItemStack s : owned(b)) if (s.getItem() instanceof PickaxeItem) best = Math.max(best, BotGear.toolScore(s, STONE));
        } else if (it instanceof AxeItem && !(it instanceof SwordItem)) {
            mine = BotGear.toolScore(res, LOG);
            for (ItemStack s : owned(b)) if (s.getItem() instanceof AxeItem) best = Math.max(best, BotGear.toolScore(s, LOG));
            float w = BotGear.weaponScore(b, res), bw = 0F;                    // (an axe can be its best weapon too)
            for (ItemStack s : owned(b)) bw = Math.max(bw, BotGear.weaponScore(b, s));
            if (best >= 0F && mine <= best && w <= bw) return 0F;
            return best < 0F ? Math.max(1F, mine) : Math.max(mine - best, w - bw);
        } else {
            mine = BotGear.weaponScore(b, res);
            for (ItemStack s : owned(b)) best = Math.max(best, BotGear.weaponScore(b, s));
            if (best < 0F) best = 0F;
        }
        if (best < 0F) return Math.max(1F, mine);                               // (nothing of that kind yet)
        return mine - best;
    }

    // ---------------------------------------------------------------- ingredients

    /** Matches ingredients to its bag (taking them if take is set). Returns the ones it doesn't have. */
    private static List<Ingredient> match(net.minecraft.world.Container inv, List<Ingredient> ing, List<ItemStack> take) {
        int n = inv.getContainerSize();
        int[] left = new int[n];
        for (int i = 0; i < n; i++) left[i] = inv.getItem(i).getCount();
        // ingredients that accept fewer kinds first (so a "any planks" doesn't eat the only oak for an oak-only slot)
        List<Ingredient> order = new ArrayList<>(ing);
        order.sort(Comparator.comparingInt(x -> x.getItems().length));
        List<Ingredient> missing = new ArrayList<>();
        int[] used = new int[n];
        for (Ingredient x : order) {
            int found = -1;
            for (int i = 0; i < n; i++) if (left[i] > 0 && x.test(inv.getItem(i))) { found = i; break; }
            if (found < 0) { missing.add(x); continue; }
            left[found]--; used[found]++;
        }
        if (take != null && missing.isEmpty()) {
            for (int i = 0; i < n; i++) if (used[i] > 0) {
                ItemStack s = inv.getItem(i);
                ItemStack rem = s.hasCraftingRemainingItem() ? s.getCraftingRemainingItem().copyWithCount(used[i]) : ItemStack.EMPTY;
                s.shrink(used[i]);
                if (s.isEmpty()) inv.setItem(i, ItemStack.EMPTY);
                if (!rem.isEmpty()) take.add(rem);                            // (buckets and the like come back)
            }
        }
        return missing;
    }

    static int freeSlots(net.minecraft.world.Container inv) { int n = 0; for (int i = 0; i < inv.getContainerSize(); i++) if (inv.getItem(i).isEmpty()) n++; return n; }

    private static SimpleContainer copyOf(net.minecraft.world.Container inv) {
        SimpleContainer c = new SimpleContainer(inv.getContainerSize());
        for (int i = 0; i < inv.getContainerSize(); i++) c.setItem(i, inv.getItem(i).copy());
        return c;
    }

    /** Makes a crafting table in this bag, as a player would: 4 planks (any kind), or a log (its 4 planks). False if it has neither. */
    static boolean makeTable(net.minecraft.world.Container inv) {
        int planks = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) if (inv.getItem(i).is(net.minecraft.tags.ItemTags.PLANKS)) planks += inv.getItem(i).getCount();
        if (planks >= 4) {
            int need = 4;
            for (int i = 0; i < inv.getContainerSize() && need > 0; i++) {
                ItemStack s = inv.getItem(i);
                if (!s.is(net.minecraft.tags.ItemTags.PLANKS)) continue;
                int t = Math.min(need, s.getCount()); s.shrink(t); need -= t;
                if (s.isEmpty()) inv.setItem(i, ItemStack.EMPTY);
            }
        } else {
            int log = -1;
            for (int i = 0; i < inv.getContainerSize() && log < 0; i++) if (inv.getItem(i).is(net.minecraft.tags.ItemTags.LOGS_THAT_BURN) || inv.getItem(i).is(net.minecraft.tags.ItemTags.LOGS)) log = i;
            if (log < 0) return false;
            inv.getItem(log).shrink(1);
            if (inv.getItem(log).isEmpty()) inv.setItem(log, ItemStack.EMPTY);
        }
        ItemStack table = new ItemStack(Items.CRAFTING_TABLE);
        for (int i = 0; i < inv.getContainerSize() && !table.isEmpty(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty()) { inv.setItem(i, table); table = ItemStack.EMPTY; }
            else if (s.is(Items.CRAFTING_TABLE) && s.getCount() < s.getMaxStackSize()) { s.grow(1); table = ItemStack.EMPTY; }
        }
        return table.isEmpty();
    }

    // ---------------------------------------------------------------- 6. getting what it's short of itself

    /** Raw ore it would have to smelt for this ingot (counts toward what it has). */
    private static Item rawFor(Item ingot) {
        return ingot == Items.IRON_INGOT ? Items.RAW_IRON : ingot == Items.GOLD_INGOT ? Items.RAW_GOLD : ingot == Items.COPPER_INGOT ? Items.RAW_COPPER : null;
    }

    /** The ore that gives this (iron ore for iron ingots, diamond ore for diamonds...), or null. */
    private static net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block> oreFor(Item it) {
        if (it == Items.IRON_INGOT || it == Items.RAW_IRON) return net.minecraftforge.common.Tags.Blocks.ORES_IRON;
        if (it == Items.GOLD_INGOT || it == Items.RAW_GOLD) return net.minecraftforge.common.Tags.Blocks.ORES_GOLD;
        if (it == Items.COPPER_INGOT || it == Items.RAW_COPPER) return net.minecraftforge.common.Tags.Blocks.ORES_COPPER;
        if (it == Items.DIAMOND) return net.minecraftforge.common.Tags.Blocks.ORES_DIAMOND;
        if (it == Items.EMERALD) return net.minecraftforge.common.Tags.Blocks.ORES_EMERALD;
        if (it == Items.COAL) return net.minecraftforge.common.Tags.Blocks.ORES_COAL;
        return null;
    }

    /** What it's going for: an upgrade 1-3 of one ore's worth short (counting raw ore it already has to smell), and how many. */
    public record Need(ResourceLocation recipe, ItemStack result, Item item, net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block> ore, int more) {}

    public static Need shortfall(PartyBotEntity b) {
        if (!(b.level() instanceof ServerLevel s) || freeSlots(b.inventory) == 0) return null;
        boolean table = hasStation(b, Items.CRAFTING_TABLE, Blocks.CRAFTING_TABLE) || makeTable(copyOf(b.inventory));
        Need best = null; float bestGain = 0F;
        for (CraftingRecipe r : candidates(s)) {
            if (!r.canCraftInDimensions(2, 2) && !table) continue;
            ItemStack res = r.getResultItem(s.registryAccess());
            float gain = gain(b, res);
            if (gain <= 0F) continue;
            List<Ingredient> ing = new ArrayList<>();
            for (Ingredient i : r.getIngredients()) if (!i.isEmpty()) ing.add(i);
            List<Ingredient> missing = match(b.inventory, ing, null);
            if (missing.isEmpty() || missing.size() > 3) continue;
            Item kind = null; boolean same = true;
            for (Ingredient m : missing) {
                Item k = null;
                for (ItemStack x : m.getItems()) if (oreFor(x.getItem()) != null) { k = x.getItem(); break; }
                if (k == null || kind != null && k != kind) { same = false; break; }
                kind = k;
            }
            if (!same || kind == null) continue;
            Item raw = rawFor(kind);
            int more = missing.size() - (raw == null ? 0 : b.inventory.countItem(raw));   // (raw ore in its bag only wants a furnace)
            if (more <= 0) continue;
            if (best == null || more < best.more() || more == best.more() && gain > bestGain) { best = new Need(r.getId(), res.copy(), kind, oreFor(kind), more); bestGain = gain; }
        }
        return best;
    }

    /**
     * Out of a fight, not in mining mode: the nearest exposed ore of what it's short of for an upgrade, within 10 blocks
     * of it and 16 of its owner, if it has a pickaxe that gets the ore out. Null: nothing to go for.
     */
    public static BlockPos gatherTarget(PartyBotEntity b, ServerPlayer o) {
        if (b.mode() == PartyBotEntity.MINING || b.journey() || b.isPassenger() || BotErrands.danger(b) != null || !com.xenowarriors.modes.ModeRules.canGrief(b.level())) return null;
        Need need = shortfall(b);
        if (need == null) return null;
        BlockPos c = b.blockPosition(), best = null; double bd = 1e9;
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-10, -5, -10), c.offset(10, 5, 10))) {
            BlockState st = b.level().getBlockState(p);
            if (!st.is(need.ore()) || p.distSqr(o.blockPosition()) > 16 * 16 || b.unreachable(p) || !PartyBots.exposed(b, p)) continue;
            double d = p.distSqr(c);
            if (d < bd && hasToolFor(b, st)) { bd = d; best = p.immutable(); }
        }
        if (best != null) PartyBots.testLog(b, "gather: short " + need.more() + " " + BotErrands.id(new ItemStack(need.item())) + " for " + BotErrands.id(need.result()) + " - mining the ore at " + best.toShortString());
        return best;
    }

    private static boolean hasToolFor(PartyBotEntity b, BlockState st) {
        if (b.getMainHandItem().isCorrectToolForDrops(st)) return true;
        for (int i = 0; i < b.inventory.getContainerSize(); i++) if (b.inventory.getItem(i).isCorrectToolForDrops(st)) return true;
        return false;
    }

    /** A crafting table / anvil in its bag, or placed near it. */
    static boolean hasStation(PartyBotEntity b, Item item, net.minecraft.world.level.block.Block... blocks) {
        for (int i = 0; i < b.inventory.getContainerSize(); i++) if (b.inventory.getItem(i).is(item)) return true;
        BlockPos c = b.blockPosition();
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-STATION_RANGE, -3, -STATION_RANGE), c.offset(STATION_RANGE, 3, STATION_RANGE))) {
            BlockState st = b.level().getBlockState(p);
            for (var bl : blocks) if (st.is(bl)) return true;
        }
        return false;
    }

    private static boolean anvil(PartyBotEntity b) {
        boolean bag = false;
        for (int i = 0; i < b.inventory.getContainerSize(); i++) if (b.inventory.getItem(i).is(net.minecraft.tags.ItemTags.ANVIL)) bag = true;
        return bag || hasStation(b, Items.ANVIL, Blocks.ANVIL, Blocks.CHIPPED_ANVIL, Blocks.DAMAGED_ANVIL);
    }

    // ---------------------------------------------------------------- repairs

    /** Gear slots it repairs: armour and its hand (the bag's best weapon/tool too, by bag slot). */
    private static final EquipmentSlot[] GEAR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND};

    private static ItemStack at(PartyBotEntity b, int slot) { return slot < 0 ? b.getItemBySlot(GEAR[-slot - 1]) : b.inventory.getItem(slot); }

    private static boolean worn(ItemStack s) { return s.isDamageableItem() && s.isDamaged() && s.getDamageValue() >= s.getMaxDamage() * REPAIR_AT; }

    private static void repairs(PartyBotEntity b, List<Idea> out) {
        List<Integer> slots = new ArrayList<>();
        for (int g = 0; g < GEAR.length; g++) slots.add(-g - 1);
        for (int i = 0; i < b.inventory.getContainerSize(); i++) {          // tools and weapons in its bag
            Item it = b.inventory.getItem(i).getItem();
            if (it instanceof TieredItem || it instanceof ArmorItem || it instanceof ShieldItem || it instanceof BowItem) slots.add(i);
        }
        boolean anvil = anvil(b);
        Set<Item> seen = new HashSet<>();
        for (int slot : slots) {
            ItemStack s = at(b, slot);
            if (!worn(s) || !seen.add(s.getItem())) continue;
            // two of the same: combine (only when neither is enchanted - the grid repair would lose them)
            if (!s.isEnchanted()) {
                for (int j = 0; j < b.inventory.getContainerSize(); j++) {
                    ItemStack o = b.inventory.getItem(j);
                    if (j == slot || !o.is(s.getItem()) || !o.isDamaged() || o.isEnchanted()) continue;
                    out.add(new Idea("repair_combine", null, s.copy(), 0, null, ItemStack.EMPTY, 50F + s.getDamageValue(), slot, j));
                    break;
                }
                if (out.stream().anyMatch(i -> i.slot == slot && i.kind.equals("repair_combine"))) continue;
            }
            // the anvil: its repair material, a quarter each
            ItemStack mat = ItemStack.EMPTY;
            int have = 0;
            Ingredient rep = repairIngredient(s);
            if (rep == null) continue;
            for (int j = 0; j < b.inventory.getContainerSize(); j++) if (j != slot && rep.test(b.inventory.getItem(j))) { have += b.inventory.getItem(j).getCount(); mat = b.inventory.getItem(j); }
            int need = Math.max(1, Math.min(4, (int) Math.ceil(s.getDamageValue() / (s.getMaxDamage() / 4F))));
            int use = Math.min(need, have);
            if (use > 0 && anvil) out.add(new Idea("repair_anvil", null, s.copy(), 0, null, ItemStack.EMPTY, 40F + s.getDamageValue(), slot, use));
            else if (use > 0) out.add(new Idea("repair_anvil", null, s.copy(), 1, Component.literal("an anvil (in its bag or close by)"), new ItemStack(Items.ANVIL), 40F, slot, use));
            else if (anvil) {
                ItemStack m = firstItem(rep);
                if (!m.isEmpty()) out.add(new Idea("repair_anvil", null, s.copy(), 1, m.getHoverName(), m, 30F, slot, 1));
            }
        }
    }

    /** The material that repairs this item at an anvil (iron for iron, diamond for diamond...), or null. */
    private static Ingredient repairIngredient(ItemStack s) {
        Item it = s.getItem();
        if (it instanceof TieredItem t) return t.getTier().getRepairIngredient();
        if (it instanceof ArmorItem a) return a.getMaterial().getRepairIngredient();
        if (it instanceof ShieldItem) return Ingredient.of(net.minecraft.tags.ItemTags.PLANKS);
        return null;
    }

    // ---------------------------------------------------------------- asking the owner

    private static String verb(Idea i) { return i.kind.equals("craft") ? "craft" : "repair"; }

    /** One short of something the owner has on them: asks for it ([Give 1 & ...] / [No thanks]). */
    private static void tell(PartyBotEntity b, ServerPlayer o, Idea i) {
        if (o == null || i.missingItem.isEmpty() || !ownerHas(o, i.missingItem)) return;
        Component what = i.result.getHoverName();
        MutableComponent msg = Component.literal("").append(b.label()).withStyle(ChatFormatting.AQUA);
        msg.append(Component.literal(" needs one more: ").withStyle(ChatFormatting.WHITE)).append(Component.empty().append(i.missingWhat).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" to " + verb(i) + " ").withStyle(ChatFormatting.WHITE)).append(Component.empty().append(what).withStyle(ChatFormatting.GOLD))
                .append(Component.literal(". ").withStyle(ChatFormatting.WHITE));
        i.token = nextToken++;
        OFFERS.put(b.getUUID(), i);
        OFFER_UNTIL.put(b.getUUID(), b.level().getGameTime() + OFFER_TICKS);
        requests++;
        msg.append(button("[Give 1 & " + verb(i) + "]", "/xenobot give " + b.getId() + " " + i.token, ChatFormatting.GREEN)).append(Component.literal(" "))
                .append(button("[No thanks]", "/xenobot nocraft " + b.getId() + " " + i.token, ChatFormatting.RED));
        com.xenowarriors.network.ModNetwork.sendToPlayer(new com.xenowarriors.network.RequestPopupPacket(b.character().getDisplayName().getString() + " is one short",
                "Give 1 " + i.missingWhat.getString() + " so they can " + verb(i) + " " + what.getString() + "?", "/xenobot give " + b.getId() + " " + i.token,
                "/xenobot nocraft " + b.getId() + " " + i.token, OFFER_TICKS, 0xFFFFD24A), o);
        o.sendSystemMessage(msg);
        o.level().playSound(null, o.blockPosition(), SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 0.6F, 1.5F);
        log(b, "request " + i.key() + " short=" + i.missingWhat.getString() + " token=" + i.token);
    }

    /** (Tests) how many item requests bots have sent their owners since the game started. */
    public static int requests;

    static boolean ownerHas(ServerPlayer o, ItemStack s) {
        for (int i = 0; i < o.getInventory().getContainerSize(); i++) if (ItemStack.isSameItem(o.getInventory().getItem(i), s)) return true;
        return false;
    }

    private static MutableComponent button(String text, String command, ChatFormatting color) {
        return Component.literal(text).withStyle(s -> s.withColor(color).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(command))));
    }

    private static void log(PartyBotEntity b, String s) {
        if (System.getenv("XW_UITEST") != null) XenoWarriors.LOGGER.info("XW-TEST botgear craft {} {}", b.character().getId(), s);
    }

    // ---------------------------------------------------------------- the answer

    private static PartyBotEntity offerBot(ServerPlayer p, int botId, int token, boolean quietIfStale) {
        if (!(p.serverLevel().getEntity(botId) instanceof PartyBotEntity b) || !p.getUUID().equals(b.ownerId())) return null;
        Idea i = OFFERS.get(b.getUUID());
        if (i == null || i.token != token) {
            if (!quietIfStale) p.displayClientMessage(Component.literal("That offer's gone.").withStyle(ChatFormatting.GRAY), true);
            return null;
        }
        return b;
    }

    /** [No thanks]: not again for a while. */
    public static void decline(ServerPlayer p, int botId, int token) {
        PartyBotEntity b = offerBot(p, botId, token, true);
        if (b == null) return;
        Idea i = OFFERS.remove(b.getUUID());
        OFFER_UNTIL.remove(b.getUUID());
        quiet(b).put(i.key(), b.level().getGameTime() + DECLINE_COOLDOWN);
        if (i.missing == 0) quiet(b).put(i.kind + ":" + net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(i.result.getItem()) + ":1", b.level().getGameTime() + DECLINE_COOLDOWN);
        else quiet(b).put(i.kind + ":" + net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(i.result.getItem()) + ":0", b.level().getGameTime() + DECLINE_COOLDOWN / 4);
        p.displayClientMessage(Component.literal("OK - ").append(b.label()).append(" won't ask about that for a while.").withStyle(ChatFormatting.GRAY), true);
        log(b, "declined " + i.key());
    }

    /** [Give 1 & craft]: one of the item from you, then the craft. */
    public static void give(ServerPlayer p, int botId, int token) {
        PartyBotEntity b = offerBot(p, botId, token, false);
        if (b == null) return;
        Idea i = OFFERS.get(b.getUUID());
        if (i.missingItem.isEmpty()) return;
        for (int s = 0; s < p.getInventory().getContainerSize(); s++) {
            ItemStack st = p.getInventory().getItem(s);
            if (!ItemStack.isSameItem(st, i.missingItem)) continue;
            ItemStack one = st.split(1);
            ItemStack left = b.inventory.addItem(one);
            if (!left.isEmpty()) { p.getInventory().add(left); p.displayClientMessage(Component.literal("").append(b.label()).append("'s bag is full.").withStyle(ChatFormatting.RED), true); return; }
            log(b, "given 1 " + net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(one.getItem()));
            Idea now = null;
            for (Idea x : ideas(b)) if (x.missing == 0 && x.kind.equals(i.kind) && ItemStack.isSameItem(x.result, i.result)) { now = x; break; }
            if (now == null) { OFFERS.remove(b.getUUID()); OFFER_UNTIL.remove(b.getUUID()); p.displayClientMessage(Component.literal("").append(b.label()).append(" still can't make it.").withStyle(ChatFormatting.GRAY), true); return; }
            do_(b, p, now, false);
            return;
        }
        p.displayClientMessage(Component.literal("You don't have one.").withStyle(ChatFormatting.GRAY), true);
    }

    /** [Craft] / [Repair]. */
    public static void confirm(ServerPlayer p, int botId, int token) {
        PartyBotEntity b = offerBot(p, botId, token, false);
        if (b == null) return;
        Idea i = OFFERS.get(b.getUUID());
        if (i.missing != 0) { give(p, botId, token); return; }   // (only item requests are offered now - crafting itself needs no OK)
        do_(b, p, i, false);
    }

    /** Makes/repairs it. auto: it decided by itself (no "doesn't have what it takes" grumble if the bag changed). p may be null. */
    private static void do_(PartyBotEntity b, ServerPlayer p, Idea i, boolean auto) {
        OFFERS.remove(b.getUUID());
        OFFER_UNTIL.remove(b.getUUID());
        if (b.downed()) { if (p != null && !auto) p.displayClientMessage(Component.literal("").append(b.label()).append(" is down right now.").withStyle(ChatFormatting.RED), true); return; }
        ItemStack made = switch (i.kind) {
            case "craft" -> craft(b, i);
            case "repair_combine" -> combine(b, i);
            default -> anvilRepair(b, i);
        };
        if (made == null || made.isEmpty()) {
            if (p != null && !auto) p.displayClientMessage(Component.literal("").append(b.label()).append(" doesn't have what it takes any more.").withStyle(ChatFormatting.GRAY), true);
            log(b, "failed " + i.key());
            return;
        }
        if (i.kind.equals("craft")) b.autoCrafted++;
        b.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        b.level().playSound(null, b.blockPosition(), i.kind.equals("craft") ? SoundEvents.VILLAGER_WORK_TOOLSMITH : SoundEvents.ANVIL_USE, SoundSource.NEUTRAL, 0.7F, 1.1F);
        if (made.getItem() instanceof ArmorItem) PartyBots.wearBestArmour(b);
        boolean wearing = false;
        for (EquipmentSlot es : EquipmentSlot.values()) if (b.getItemBySlot(es) == made || ItemStack.isSameItemSameTags(b.getItemBySlot(es), made) && es.getType() == EquipmentSlot.Type.ARMOR) wearing = true;
        if (p != null) p.sendSystemMessage(Component.literal("").append(b.label()).append(i.kind.equals("craft") ? " crafted " : " repaired ").append(made.getHoverName())
                .append(wearing ? " and put it on." : ".").withStyle(ChatFormatting.AQUA));
        log(b, "done " + i.key() + " auto=" + auto + " wearing=" + wearing + " chest=" + net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(b.getItemBySlot(EquipmentSlot.CHEST).getItem()));
    }

    private static ItemStack craft(PartyBotEntity b, Idea i) {
        if (!(b.level() instanceof ServerLevel s)) return null;
        var opt = s.getRecipeManager().byKey(i.recipe);
        if (opt.isEmpty() || !(opt.get() instanceof CraftingRecipe r)) return null;
        if (freeSlots(b.inventory) == 0 && !b.inventory.canAddItem(r.getResultItem(s.registryAccess()))) return null;   // (a full bag: nothing more made)
        List<Ingredient> ing = new ArrayList<>();
        for (Ingredient x : r.getIngredients()) if (!x.isEmpty()) ing.add(x);
        if (!r.canCraftInDimensions(2, 2) && !hasStation(b, Items.CRAFTING_TABLE, Blocks.CRAFTING_TABLE)) {
            SimpleContainer copy = copyOf(b.inventory);                          // its own table first (none about) - if that leaves it enough
            if (!makeTable(copy) || !match(copy, ing, null).isEmpty() || !makeTable(b.inventory)) return null;
            b.tablesMade++;
            ServerPlayer o = b.owner();
            if (o != null) o.sendSystemMessage(Component.literal("").append(b.label()).append(" made themselves a crafting table.").withStyle(ChatFormatting.GRAY));
            log(b, "made a crafting table");
            PartyBots.testLog(b, "made itself a crafting table for " + BotErrands.id(i.result));
        }
        List<ItemStack> back = new ArrayList<>();
        if (!match(b.inventory, ing, back).isEmpty()) return null;
        ItemStack res = r.getResultItem(s.registryAccess()).copy();
        res.onCraftedBy(b.level(), PartyBots.standIn(b), res.getCount());
        for (ItemStack x : back) put(b, x);
        put(b, res);
        return res;
    }

    private static void put(PartyBotEntity b, ItemStack s) {
        ItemStack left = b.inventory.addItem(s);
        if (!left.isEmpty()) { net.minecraft.world.level.block.Block.popResource(b.level(), b.blockPosition(), left); PartyBots.notifyFull(b); }
    }

    private static ItemStack combine(PartyBotEntity b, Idea i) {
        ItemStack a = at(b, i.slot), o = i.material >= 0 && i.material < b.inventory.getContainerSize() ? b.inventory.getItem(i.material) : ItemStack.EMPTY;
        // (the bag may have changed since the offer: never fold an enchanted copy into another - the grid repair loses them)
        if (!a.is(i.result.getItem()) || !o.is(a.getItem()) || i.material == i.slot || a.isEnchanted() || o.isEnchanted() || !o.isDamageableItem()) return null;
        int max = a.getMaxDamage();
        int left = (max - a.getDamageValue()) + (max - o.getDamageValue()) + max * 5 / 100;
        a.setDamageValue(Math.max(0, max - left));
        b.inventory.setItem(i.material, ItemStack.EMPTY);
        return a;
    }

    private static ItemStack anvilRepair(PartyBotEntity b, Idea i) {
        ItemStack a = at(b, i.slot);
        if (!a.is(i.result.getItem()) || !anvil(b)) return null;
        Ingredient rep = repairIngredient(a);
        if (rep == null) return null;
        int use = i.material;
        for (int j = 0; j < b.inventory.getContainerSize() && use > 0; j++) {
            ItemStack m = b.inventory.getItem(j);
            if (j == i.slot || !rep.test(m)) continue;
            int n = Math.min(use, m.getCount());
            m.shrink(n); use -= n;
            if (m.isEmpty()) b.inventory.setItem(j, ItemStack.EMPTY);
        }
        int used = i.material - use;
        if (used <= 0) return null;
        a.setDamageValue(Math.max(0, a.getDamageValue() - used * a.getMaxDamage() / 4));
        return a;
    }

    /** A bot left or went down: its open offer goes. */
    public static void forget(UUID bot) { OFFERS.remove(bot); OFFER_UNTIL.remove(bot); }

    /** Cooldowns are game times of this world: none carry into another save (singleplayer). */
    @SubscribeEvent
    public static void onServerStopped(net.minecraftforge.event.server.ServerStoppedEvent event) { OFFERS.clear(); OFFER_UNTIL.clear(); QUIET.clear(); }

    /** (Tests) the open offer's token, or 0. */
    public static int offerToken(PartyBotEntity b) { Idea i = OFFERS.get(b.getUUID()); return i == null ? 0 : i.token; }

    /** (Tests) forget all cooldowns for this bot. */
    public static void clearQuiet(PartyBotEntity b) { quiet(b).clear(); forget(b.getUUID()); }

    // ---------------------------------------------------------------- the commands the buttons run

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("xenobot")
                .then(Commands.literal("craft").then(Commands.argument("bot", IntegerArgumentType.integer()).then(Commands.argument("token", IntegerArgumentType.integer())
                        .executes(c -> { confirm(c.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(c, "bot"), IntegerArgumentType.getInteger(c, "token")); return 1; }))))
                .then(Commands.literal("give").then(Commands.argument("bot", IntegerArgumentType.integer()).then(Commands.argument("token", IntegerArgumentType.integer())
                        .executes(c -> { give(c.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(c, "bot"), IntegerArgumentType.getInteger(c, "token")); return 1; }))))
                .then(Commands.literal("nocraft").then(Commands.argument("bot", IntegerArgumentType.integer()).then(Commands.argument("token", IntegerArgumentType.integer())
                        .executes(c -> { decline(c.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(c, "bot"), IntegerArgumentType.getInteger(c, "token")); return 1; })))));
    }
}

package com.xenowarriors.bot;

import com.xenowarriors.character.CharacterType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Fox;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlastFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SmokerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.Tags;

import java.util.*;

/**
 * Little jobs a bot teammate goes off and does when things are quiet (no fight, nobody hurt, no enemies about, its
 * owner close): putting what it can smelt into a free furnace near it and collecting the result, handing a spare weapon,
 * shield, food or a crafting ingredient to another of its owner's bots (or a spare sword to one of the owner's foxes),
 * Ultima feeding Data Shards to robots, and Yuri looking about for hamburger makings. One errand at a time, each with a
 * time limit; anything that comes up (an enemy, the owner hurt or walking off) calls it off.
 */
public final class BotErrands extends Goal {
    /** One job: where (a block or an entity), with what (a bag slot), and until when. */
    public static final class Errand {
        public final String kind;
        final BlockPos pos; final Entity to; final int slot, count; final ItemStack what; final int until;
        Errand(String kind, BlockPos pos, Entity to, int slot, int count, ItemStack what, int until) {
            this.kind = kind; this.pos = pos; this.to = to; this.slot = slot; this.count = count; this.what = what; this.until = until;
        }
        @Override public String toString() { return kind + (what.isEmpty() ? "" : " " + id(what)) + (to != null ? " -> " + to.getName().getString() : pos != null ? " @ " + pos.toShortString() : ""); }
    }

    private final PartyBotEntity b;
    private int lastLook, nextGive, nextFurnace, nextShardJob, walked;
    private double closest;

    BotErrands(PartyBotEntity b) {
        this.b = b;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        nextGive = 40 + (int) (Math.random() * 60); nextFurnace = 60;
    }

    // ---------------------------------------------------------------- is it quiet?

    /**
     * Why it isn't safe to stop and craft or run an errand right now (null: it is): fighting, hurt in the last 10 s or
     * low on health, in lava, water or fire or falling, an enemy within 12 blocks, or its owner fighting or in trouble.
     */
    public static String danger(PartyBotEntity b) {
        if (b.downed()) return "down";
        if (b.sparring()) return "sparring";
        if (b.getTarget() != null) return "fighting";
        if (b.hurtTime > 0 || b.tickCount - b.lastHurtAt < 200) return "hurt recently";
        if (b.getHealth() < b.getMaxHealth() * 0.5F) return "low health";
        if (b.isInLava() || b.isInWater() || b.isOnFire() || b.fallDistance > 2F) return "in lava/water/falling";
        ServerPlayer o = b.owner();
        if (o != null) {
            if (PartyBotEntity.ownerInDanger(o)) return "owner in danger";
            if (o.getLastHurtMob() != null && o.tickCount - o.getLastHurtMobTimestamp() < 200) return "owner fighting";
            if (o.isInLava() || o.fallDistance > 3F) return "owner in danger";
        }
        if (enemyNear(b, 12)) return "enemies nearby";
        return null;
    }

    static boolean enemyNear(PartyBotEntity b, double r) {
        return !b.level().getEntitiesOfClass(Mob.class, b.getBoundingBox().inflate(r, 6, r),
                m -> m instanceof Enemy && m.isAlive() && !PartyBots.friendly(b, m) && !PartyBotEntity.untouchable(m)).isEmpty();
    }

    private boolean calm() {
        ServerPlayer o = b.owner();
        return o != null && o.level() == b.level() && o.distanceToSqr(b) < 20 * 20 && !b.journey() && !b.isPassenger() && b.mineTarget == null && b.reviving == null
                && !b.eating() && !b.drinking() && !b.isNoAi() && danger(b) == null;
    }

    // ---------------------------------------------------------------- the goal

    @Override public boolean requiresUpdateEveryTick() { return true; }

    @Override public boolean canUse() {
        if (b.errand != null) return calm();
        if (b.tickCount - lastLook < 20) return false;
        lastLook = b.tickCount;
        if (!calm()) return false;
        b.errand = pick(b.owner());
        if (b.errand != null) PartyBots.testLog(b, "errand " + b.errand);
        return b.errand != null;
    }

    @Override public boolean canContinueToUse() { return b.errand != null && b.tickCount < b.errand.until && calm(); }
    @Override public void start() { closest = 1e9; walked = 0; }
    @Override public void stop() {
        if (b.errand != null && b.tickCount >= b.errand.until) PartyBots.testLog(b, "errand gave up (time) " + b.errand);
        b.errand = null; b.getNavigation().stop();
    }

    @Override public void tick() {
        Errand e = b.errand;
        if (e == null) return;
        if (e.to != null && (!e.to.isAlive() || e.to.level() != b.level())) { b.errand = null; return; }
        Vec3 at = e.to != null ? e.to.position() : Vec3.atCenterOf(e.pos);
        b.getLookControl().setLookAt(at.x, at.y + (e.to != null ? e.to.getBbHeight() * 0.6 : 0), at.z);
        double d2 = b.position().add(0, 0.5, 0).distanceToSqr(at);
        if (d2 > 2.6 * 2.6) {
            if (d2 < closest - 0.25) { closest = d2; walked = 0; }
            if (++walked > 100) { PartyBots.testLog(b, "errand gave up (can't get there) " + e); b.errand = null; return; }   // (5 s without getting closer)
            if (b.getNavigation().isDone() || b.tickCount % 10 == 0) b.getNavigation().moveTo(at.x, at.y, at.z, 1.1);
            return;
        }
        b.getNavigation().stop();
        ServerPlayer o = b.owner();
        if (o != null) switch (e.kind) {
            case "furnace" -> putInFurnace(o, e);
            case "collect" -> collect(o, e);
            case "give", "feed", "ingredient", "shield" -> handToBot(o, e);
            case "fox" -> handToFox(o, e);
            case "fetch" -> fetch(o, e);
            case "robot_tame", "robot_fix" -> robot(o, e);
            default -> {}
        }
        b.errand = null;
    }

    /** What to do next, most useful first. */
    private Errand pick(ServerPlayer o) {
        Errand e = null;
        if (b.character() == CharacterType.ULTIMA) e = robotJob(o);
        if (e == null) e = collectJob();
        if (e == null && b.tickCount >= nextGive) { nextGive = b.tickCount + 100; e = giveJob(o); }
        if (e == null && b.character() == CharacterType.YURI && b.tickCount >= b.yuriNext) { b.yuriNext = b.tickCount + 600 + b.getRandom().nextInt(600); e = yuriLook(o); }
        if (e == null && b.tickCount >= nextFurnace) { nextFurnace = b.tickCount + 60; e = furnaceJob(o); }
        return e;
    }

    private Errand job(String kind, BlockPos pos, Entity to, int slot, int count, ItemStack what, int secs) {
        return new Errand(kind, pos, to, slot, count, what == null ? ItemStack.EMPTY : what.copy(), b.tickCount + secs * 20);
    }

    static String id(ItemStack s) { return s.isEmpty() ? "empty" : String.valueOf(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(s.getItem()).getPath()); }

    private void tell(ServerPlayer o, String s) {
        o.displayClientMessage(Component.literal("").append(b.label()).append(" " + s).withStyle(ChatFormatting.AQUA), true);
        PartyBots.testLog(b, s);
    }

    // ---------------------------------------------------------------- 1. furnaces

    /** Furnaces it's using: where, what comes out, whose (so no one else touches them). */
    record Claim(UUID bot, String dim, ItemStack result, long until) {}
    static final Map<Long, Claim> CLAIMS = new HashMap<>();

    private static RecipeType<? extends AbstractCookingRecipe> typeOf(AbstractFurnaceBlockEntity f) {
        if (f instanceof SmokerBlockEntity) return RecipeType.SMOKING;
        if (f instanceof BlastFurnaceBlockEntity) return RecipeType.BLASTING;
        return RecipeType.SMELTING;
    }

    /** What this cooks into in that kind of furnace (empty: it doesn't). */
    static <T extends AbstractCookingRecipe> ItemStack cooked(net.minecraft.world.level.Level l, RecipeType<T> type, ItemStack in) {
        if (in.isEmpty()) return ItemStack.EMPTY;
        Container c = new SimpleContainer(in.copyWithCount(1));
        return l.getRecipeManager().getRecipeFor(type, c, l).map(r -> r.getResultItem(l.registryAccess()).copy()).orElse(ItemStack.EMPTY);
    }

    /** Worth smelting: raw ore and metal, ores, and raw food (into something better to eat). Never its gear. */
    static boolean worthSmelting(PartyBotEntity b, ItemStack s) {
        if (s.isEmpty() || s.isDamageableItem() || s.getItem() instanceof ArmorItem) return false;
        if (s.is(Tags.Items.RAW_MATERIALS) || s.is(Tags.Items.ORES)) return !cooked(b.level(), RecipeType.SMELTING, s).isEmpty();
        if (s.isEdible()) {
            ItemStack out = cooked(b.level(), RecipeType.SMELTING, s);
            var a = s.getFoodProperties(b); var c = out.getFoodProperties(b);
            return !out.isEmpty() && c != null && (a == null || c.getNutrition() > a.getNutrition());
        }
        return false;
    }

    static boolean food(ItemStack s) { return s.isEdible(); }

    /** Fuel it's happy to burn (never its tools, a crafting table or a bucket of lava). Best first. */
    private static int fuelRank(ItemStack s) {
        if (s.is(Items.COAL) || s.is(Items.CHARCOAL)) return 4;
        if (s.is(Items.COAL_BLOCK) || s.is(Items.BLAZE_ROD) || s.is(Items.DRIED_KELP_BLOCK)) return 3;
        if (s.is(ItemTags.PLANKS)) return 2;
        if (s.is(ItemTags.LOGS) || s.is(Items.STICK)) return 1;
        return 0;
    }

    private static int burn(ItemStack s, RecipeType<?> type) { return net.minecraftforge.common.ForgeHooks.getBurnTime(s, type); }

    /** Furnaces (furnace, smoker, blast furnace) in the loaded chunks within r blocks. */
    static List<AbstractFurnaceBlockEntity> furnaces(PartyBotEntity b, int r) {
        List<AbstractFurnaceBlockEntity> out = new ArrayList<>();
        if (!(b.level() instanceof ServerLevel s)) return out;
        BlockPos c = b.blockPosition();
        for (int cx = (c.getX() - r) >> 4; cx <= (c.getX() + r) >> 4; cx++) for (int cz = (c.getZ() - r) >> 4; cz <= (c.getZ() + r) >> 4; cz++) {
            LevelChunk ch = s.getChunkSource().getChunkNow(cx, cz);
            if (ch == null) continue;
            for (BlockEntity be : ch.getBlockEntities().values())
                if (be instanceof AbstractFurnaceBlockEntity f && !f.isRemoved() && be.getBlockPos().distSqr(c) <= r * r && Math.abs(be.getBlockPos().getY() - c.getY()) <= 4) out.add(f);
        }
        return out;
    }

    private static boolean claimedByOther(PartyBotEntity b, AbstractFurnaceBlockEntity f) {
        Claim cl = CLAIMS.get(f.getBlockPos().asLong());
        return cl != null && !cl.bot().equals(b.getUUID()) && cl.until() > b.level().getGameTime();
    }

    /** Free: nothing in it to smelt and nothing waiting to be taken out (it never touches anyone's cooking). */
    static boolean free(AbstractFurnaceBlockEntity f) { return f.getItem(0).isEmpty() && f.getItem(2).isEmpty(); }

    private Errand furnaceJob(ServerPlayer o) {
        boolean any = false;
        for (int i = 0; i < b.inventory.getContainerSize() && !any; i++) any = worthSmelting(b, b.inventory.getItem(i));
        if (!any) return null;
        AbstractFurnaceBlockEntity best = null; int bestSlot = -1, bestPref = 0; double bd = 1e9;
        for (AbstractFurnaceBlockEntity f : furnaces(b, 10)) {
            if (!free(f) || claimedByOther(b, f) || f.getBlockPos().distSqr(o.blockPosition()) > 16 * 16) continue;
            RecipeType<? extends AbstractCookingRecipe> type = typeOf(f);
            ItemStack fuelIn = f.getItem(1);
            for (int i = 0; i < b.inventory.getContainerSize(); i++) {
                ItemStack s = b.inventory.getItem(i);
                if (!worthSmelting(b, s) || cooked(b.level(), type, s).isEmpty()) continue;
                if (fuelIn.isEmpty() ? !hasFuel(type, i) : burn(fuelIn, type) <= 0 && !hasFuel(type, i)) continue;
                boolean isFood = food(s);
                int pref = isFood ? (type == RecipeType.SMOKING ? 3 : 2) : (type == RecipeType.BLASTING ? 3 : 2);   // (a smoker for food, a blast furnace for ore)
                double d = f.getBlockPos().distSqr(b.blockPosition());
                if (pref > bestPref || pref == bestPref && d < bd) { best = f; bestSlot = i; bestPref = pref; bd = d; }
            }
        }
        if (best == null) return null;
        return job("furnace", best.getBlockPos(), null, bestSlot, b.inventory.getItem(bestSlot).getCount(), b.inventory.getItem(bestSlot), 20);
    }

    private boolean hasFuel(RecipeType<?> type, int notSlot) {
        for (int i = 0; i < b.inventory.getContainerSize(); i++) if (i != notSlot && fuelRank(b.inventory.getItem(i)) > 0 && burn(b.inventory.getItem(i), type) > 0) return true;
        return false;
    }

    private void putInFurnace(ServerPlayer o, Errand e) {
        if (!(b.level().getBlockEntity(e.pos) instanceof AbstractFurnaceBlockEntity f) || !free(f) || claimedByOther(b, f)) { PartyBots.testLog(b, "furnace busy now - left it alone"); return; }
        ItemStack in = b.inventory.getItem(e.slot);
        if (!ItemStack.isSameItemSameTags(in, e.what)) return;
        RecipeType<? extends AbstractCookingRecipe> type = typeOf(f);
        ItemStack result = cooked(b.level(), type, in);
        if (result.isEmpty()) return;
        // fuel: what's in the fuel slot first, then the best fuel in its bag (the same kind, so it stacks)
        ItemStack slot1 = f.getItem(1);
        int have = slot1.isEmpty() ? 0 : slot1.getCount() * burn(slot1, type);
        int fuelSlot = -1;
        for (int i = 0; i < b.inventory.getContainerSize(); i++) {
            ItemStack s = b.inventory.getItem(i);
            if (i == e.slot || fuelRank(s) == 0 || burn(s, type) <= 0 || !slot1.isEmpty() && !ItemStack.isSameItemSameTags(s, slot1)) continue;
            if (fuelSlot < 0 || fuelRank(s) > fuelRank(b.inventory.getItem(fuelSlot))) fuelSlot = i;
        }
        ItemStack fuel = fuelSlot < 0 ? ItemStack.EMPTY : b.inventory.getItem(fuelSlot);
        int per = fuel.isEmpty() ? 1 : burn(fuel, type);
        int room = fuel.isEmpty() ? 0 : Math.min(fuel.getCount(), (slot1.isEmpty() ? fuel.getMaxStackSize() : slot1.getMaxStackSize() - slot1.getCount()));
        int n = Math.min(in.getCount(), in.getMaxStackSize());
        n = Math.min(n, (have + room * per) / 200);                              // (200 ticks of fuel an item, in any of the three)
        if (n <= 0) { PartyBots.testLog(b, "furnace: no fuel for " + id(in)); return; }
        int fuelUse = Math.max(0, (n * 200 - have + per - 1) / per);
        ItemStack put = in.split(n);
        if (in.isEmpty()) b.inventory.setItem(e.slot, ItemStack.EMPTY);
        f.setItem(0, put);
        if (fuelUse > 0) {
            ItemStack f1 = fuel.split(fuelUse);
            if (fuel.isEmpty()) b.inventory.setItem(fuelSlot, ItemStack.EMPTY);
            if (slot1.isEmpty()) f.setItem(1, f1); else slot1.grow(f1.getCount());
        }
        f.setChanged();
        CLAIMS.put(e.pos.asLong(), new Claim(b.getUUID(), b.level().dimension().location().toString(), result.copyWithCount(1), b.level().getGameTime() + 20L * 60 * 10));
        b.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        b.level().playSound(null, e.pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.5F, 0.8F);
        String fname = f.getBlockState().getBlock().getName().getString();
        ItemStack fuelNow = f.getItem(1);
        tell(o, "put " + n + " " + put.getHoverName().getString() + (fuelUse > 0 ? " and " + fuelUse + " " + fuelNow.getHoverName().getString() : "") + " in the " + fname.toLowerCase(Locale.ROOT));
        PartyBots.testLog(b, "furnace put " + n + "x" + id(put) + " fuel +" + fuelUse + " (slot now " + fuelNow.getCount() + "x" + id(fuelNow) + ") into " + net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(f.getBlockState().getBlock()).getPath() + " at " + e.pos.toShortString());
    }

    private Errand collectJob() {
        long now = b.level().getGameTime();
        for (var it = CLAIMS.entrySet().iterator(); it.hasNext(); ) {
            var en = it.next(); Claim cl = en.getValue();
            if (!cl.bot().equals(b.getUUID()) || !cl.dim().equals(b.level().dimension().location().toString())) continue;
            BlockPos p = BlockPos.of(en.getKey());
            if (cl.until() < now || !b.level().isLoaded(p) || !(b.level().getBlockEntity(p) instanceof AbstractFurnaceBlockEntity f)) { it.remove(); continue; }
            ItemStack out = f.getItem(2);
            if (out.isEmpty()) { if (f.getItem(0).isEmpty()) it.remove(); continue; }          // (done, and someone took it: not ours to watch any more)
            if (!ItemStack.isSameItem(out, cl.result()) || p.distSqr(b.blockPosition()) > 16 * 16) continue;
            if (f.getItem(0).isEmpty() || out.getCount() >= 16) return job("collect", p, null, -1, out.getCount(), out, 20);
        }
        return null;
    }

    private void collect(ServerPlayer o, Errand e) {
        if (!(b.level().getBlockEntity(e.pos) instanceof AbstractFurnaceBlockEntity f)) return;
        Claim cl = CLAIMS.get(e.pos.asLong());
        ItemStack out = f.getItem(2);
        if (cl == null || !cl.bot().equals(b.getUUID()) || out.isEmpty() || !ItemStack.isSameItem(out, cl.result())) return;
        int n = out.getCount();
        ItemStack left = b.inventory.addItem(out.copy());
        f.setItem(2, left);
        f.setChanged();
        if (f.getItem(0).isEmpty() && f.getItem(2).isEmpty()) CLAIMS.remove(e.pos.asLong());
        if (!left.isEmpty()) PartyBots.notifyFull(b);
        b.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        tell(o, "took " + (n - left.getCount()) + " " + cl.result().getHoverName().getString() + " out of the " + f.getBlockState().getBlock().getName().getString().toLowerCase(Locale.ROOT));
    }

    // ---------------------------------------------------------------- 3 and 7. handing things over

    /** Its strongest melee weapon (hand or bag). */
    static float bestWeapon(PartyBotEntity b) {
        float best = BotGear.weaponScore(b, b.getMainHandItem());
        for (int i = 0; i < b.inventory.getContainerSize(); i++) best = Math.max(best, BotGear.weaponScore(b, b.inventory.getItem(i)));
        return best;
    }

    /** Bag slots holding weapons it doesn't need: everything but its one best weapon and the best tool of each kind. */
    static List<Integer> spareWeapons(PartyBotEntity b) {
        int bestSlot = -1; float best = BotGear.weaponScore(b, b.getMainHandItem());
        for (int i = 0; i < b.inventory.getContainerSize(); i++) { float sc = BotGear.weaponScore(b, b.inventory.getItem(i)); if (sc > best) { best = sc; bestSlot = i; } }
        Set<Integer> keep = new HashSet<>();
        if (bestSlot >= 0) keep.add(bestSlot);
        for (Class<?> k : new Class<?>[]{PickaxeItem.class, AxeItem.class, ShovelItem.class, HoeItem.class}) {
            if (k.isInstance(b.getMainHandItem().getItem())) continue;          // (it has one in hand)
            int ks = -1; float kb = -1F;
            for (int i = 0; i < b.inventory.getContainerSize(); i++) {
                ItemStack s = b.inventory.getItem(i);
                if (!k.isInstance(s.getItem())) continue;
                float sc = BotGear.toolScore(s, k == AxeItem.class ? Blocks.OAK_LOG.defaultBlockState() : k == ShovelItem.class ? Blocks.DIRT.defaultBlockState() : Blocks.STONE.defaultBlockState());
                if (sc > kb) { kb = sc; ks = i; }
            }
            if (ks >= 0) keep.add(ks);
        }
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < b.inventory.getContainerSize(); i++) {
            ItemStack s = b.inventory.getItem(i);
            if (!keep.contains(i) && BotGear.weaponScore(b, s) > 0F && BotRanged.kindOf(s) == BotRanged.NONE && !com.xenowarriors.mikiko.Hekublade.isBlade(s)) out.add(i);
        }
        out.sort((x, y) -> Float.compare(BotGear.weaponScore(b, b.inventory.getItem(y)), BotGear.weaponScore(b, b.inventory.getItem(x))));
        return out;
    }

    static boolean bagRoom(PartyBotEntity m, ItemStack s) {
        for (int i = 0; i < m.inventory.getContainerSize(); i++) { ItemStack x = m.inventory.getItem(i); if (x.isEmpty() || ItemStack.isSameItemSameTags(x, s) && x.getCount() < x.getMaxStackSize()) return true; }
        return false;
    }

    /** What a sword adds to a hit (a fox holds it in its mouth: its damage counts, as a mob's held item does). */
    static float swordDamage(ItemStack s) {
        if (!(s.getItem() instanceof SwordItem)) return 0F;
        float d = 0F;
        for (AttributeModifier m : s.getAttributeModifiers(EquipmentSlot.MAINHAND).get(Attributes.ATTACK_DAMAGE)) if (m.getOperation() == AttributeModifier.Operation.ADDITION) d += (float) m.getAmount();
        return d + net.minecraft.world.item.enchantment.EnchantmentHelper.getDamageBonus(s, net.minecraft.world.entity.MobType.UNDEFINED);
    }

    static boolean hasFood(PartyBotEntity m) {
        for (int i = 0; i < m.inventory.getContainerSize(); i++) { ItemStack s = m.inventory.getItem(i); if (s.isEdible() && !s.is(Items.ROTTEN_FLESH) && !s.is(Items.SPIDER_EYE) && !s.is(Items.PUFFERFISH)) return true; }
        return false;
    }

    static boolean isShield(ItemStack s) { return !s.isEmpty() && s.canPerformAction(net.minecraftforge.common.ToolActions.SHIELD_BLOCK); }

    private Errand giveJob(ServerPlayer o) {
        List<PartyBotEntity> mates = new ArrayList<>();
        for (PartyBotEntity m : PartyBots.bots(o)) if (m != b && !m.downed() && !m.journey() && m.level() == b.level() && m.distanceToSqr(b) < 24 * 24) mates.add(m);
        List<Fox> foxes = b.level().getEntitiesOfClass(Fox.class, b.getBoundingBox().inflate(24), f -> f.isAlive() && com.xenowarriors.fox.FoxPets.isOwnedBy(f, o));
        // a spare weapon: to the bot it helps most, or a sword to a fox
        for (int slot : spareWeapons(b)) {
            ItemStack w = b.inventory.getItem(slot);
            PartyBotEntity to = null; float gain = 0.5F;
            for (PartyBotEntity m : mates) { float g = BotGear.weaponScore(m, w) - bestWeapon(m); if (g > gain && bagRoom(m, w)) { gain = g; to = m; } }
            if (to != null) return job("give", null, to, slot, 1, w, 20);
            if (w.getItem() instanceof SwordItem) for (Fox f : foxes) {
                ItemStack mouth = f.getMainHandItem();
                if (!mouth.isEmpty() && !(mouth.getItem() instanceof SwordItem)) continue;   // (it's carrying something else: food it fetched)
                if (swordDamage(w) > swordDamage(mouth) + 0.25F) return job("fox", null, f, slot, 1, w, 20);
            }
        }
        // a spare shield: to a bot with none
        int shields = 0, shieldSlot = -1;
        if (isShield(b.getOffhandItem())) shields++;
        for (int i = 0; i < b.inventory.getContainerSize(); i++) if (isShield(b.inventory.getItem(i))) { shields++; shieldSlot = i; }
        if (shields >= 2 && shieldSlot >= 0) for (PartyBotEntity m : mates) {
            boolean has = isShield(m.getOffhandItem());
            for (int i = 0; i < m.inventory.getContainerSize() && !has; i++) has = isShield(m.inventory.getItem(i));
            if (!has && m.getOffhandItem().isEmpty() && bagRoom(m, b.inventory.getItem(shieldSlot))) return job("shield", null, m, shieldSlot, 1, b.inventory.getItem(shieldSlot), 20);
        }
        // food: to a hurt or hungry bot with none, if it has a few to spare
        int foodSlot = -1, foodCount = 0;
        for (int i = 0; i < b.inventory.getContainerSize(); i++) { ItemStack s = b.inventory.getItem(i); if (s.isEdible() && !s.is(Items.ROTTEN_FLESH) && !s.is(Items.SPIDER_EYE) && !s.is(Items.PUFFERFISH)) { foodCount += s.getCount(); if (foodSlot < 0 || s.getCount() > b.inventory.getItem(foodSlot).getCount()) foodSlot = i; } }
        if (foodCount >= 2) for (PartyBotEntity m : mates)
            if (!hasFood(m) && (m.getHealth() < m.getMaxHealth() * 0.8F || foodCount >= 6) && bagRoom(m, b.inventory.getItem(foodSlot)))
                return job("feed", null, m, foodSlot, Math.max(1, Math.min(8, Math.min(b.inventory.getItem(foodSlot).getCount(), foodCount / 2))), b.inventory.getItem(foodSlot), 20);
        // a crafting ingredient one of them is one short of
        if (b.tickCount % 200 < 100) for (PartyBotEntity m : mates) {
            for (BotCrafting.Idea idea : BotCrafting.ideas(m)) {
                if (idea.missing != 1 || idea.missingItem.isEmpty()) continue;
                for (int i = 0; i < b.inventory.getContainerSize(); i++) {
                    ItemStack s = b.inventory.getItem(i);
                    if (ItemStack.isSameItem(s, idea.missingItem) && bagRoom(m, s)) return job("ingredient", null, m, i, 1, s, 20);
                }
            }
        }
        return null;
    }

    private void handToBot(ServerPlayer o, Errand e) {
        if (!(e.to instanceof PartyBotEntity m) || m.downed()) return;
        ItemStack s = b.inventory.getItem(e.slot);
        if (!ItemStack.isSameItemSameTags(s, e.what)) return;
        ItemStack give = s.split(Math.min(e.count, s.getCount()));
        if (s.isEmpty()) b.inventory.setItem(e.slot, ItemStack.EMPTY);
        ItemStack left = m.inventory.addItem(give);
        if (!left.isEmpty()) { ItemStack back = b.inventory.addItem(left); if (!back.isEmpty()) b.spawnAtLocation(back); }
        int n = give.getCount() - left.getCount();
        if (n <= 0) return;
        b.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        b.level().playSound(null, m.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL, 0.6F, 1.2F);
        BotShield.pickBest(m);                                                   // (a shield goes straight on its arm)
        tell(o, "gave " + m.character().getDisplayName().getString() + " " + (n > 1 ? n + " " : "") + e.what.getHoverName().getString()
                + switch (e.kind) { case "give" -> " to fight with"; case "feed" -> " to eat"; case "ingredient" -> " (they were one short)"; default -> ""; });
        PartyBots.testLog(b, "handed " + e.kind + " " + n + "x" + id(e.what) + " to " + m.character().getId());
    }

    private void handToFox(ServerPlayer o, Errand e) {
        if (!(e.to instanceof Fox f)) return;
        ItemStack s = b.inventory.getItem(e.slot), mouth = f.getMainHandItem();
        if (!ItemStack.isSameItemSameTags(s, e.what) || (!mouth.isEmpty() && !(mouth.getItem() instanceof SwordItem)) || swordDamage(s) <= swordDamage(mouth)) return;
        ItemStack old = mouth.copy();
        f.setItemSlot(EquipmentSlot.MAINHAND, s.split(1));
        f.setDropChance(EquipmentSlot.MAINHAND, 0F);
        if (s.isEmpty()) b.inventory.setItem(e.slot, ItemStack.EMPTY);
        if (!old.isEmpty()) { ItemStack back = b.inventory.addItem(old); if (!back.isEmpty()) b.spawnAtLocation(back); }
        f.playSound(SoundEvents.ARMOR_EQUIP_IRON, 1F, 1.3F);
        ((ServerLevel) b.level()).sendParticles(ParticleTypes.CRIT, f.getX(), f.getY() + 0.5, f.getZ(), 10, 0.25, 0.2, 0.25, 0.1);
        tell(o, "gave " + f.getName().getString() + " a " + e.what.getHoverName().getString() + " to fight with" + (old.isEmpty() ? "" : " (took back the " + old.getHoverName().getString() + ")"));
    }

    // ---------------------------------------------------------------- 10. Ultima's Data Shards

    static boolean isShard(ItemStack s) { return s.is(com.xenowarriors.item.ModItems.DATA_SHARD.get()); }

    static int shardSlot(PartyBotEntity b) { for (int i = 0; i < b.inventory.getContainerSize(); i++) if (isShard(b.inventory.getItem(i))) return i; return -1; }

    static int shards(PartyBotEntity b) { int n = 0; for (int i = 0; i < b.inventory.getContainerSize(); i++) if (isShard(b.inventory.getItem(i))) n += b.inventory.getItem(i).getCount(); return n; }

    /**
     * Every second, fight or not: Ultima absorbs a shard (as the player does: +25 Data, a little healing) when his Data
     * runs low in a fight or he's badly hurt. Once every 10 s at most.
     */
    static void absorbTick(PartyBotEntity b) {
        if (b.character() != CharacterType.ULTIMA || b.downed() || b.tickCount - b.shardAt < 200) return;
        boolean need = (b.getTarget() != null && b.karma < 35F) || b.karma < 20F || (b.getHealth() < b.getMaxHealth() * 0.35F && !hasFood(b));
        if (!need) return;
        int slot = shardSlot(b);
        if (slot < 0) return;
        b.inventory.getItem(slot).shrink(1);
        if (b.inventory.getItem(slot).isEmpty()) b.inventory.setItem(slot, ItemStack.EMPTY);
        b.shardAt = b.tickCount;
        float k0 = b.karma;
        b.karma = Math.min(100F, b.karma + com.xenowarriors.ultima.DataShardItem.DATA);
        b.heal(com.xenowarriors.ultima.DataShardItem.HEAL);
        b.shardsUsed++;
        ServerLevel l = (ServerLevel) b.level();
        l.sendParticles(new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(.2F, 1F, .5F), 1.2F), b.getX(), b.getY() + 1.2, b.getZ(), 18, .4, .6, .4, 0);
        l.playSound(null, b.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.NEUTRAL, 0.6F, 1.8F);
        PartyBots.testLog(b, "absorbed a Data Shard: data " + (int) k0 + " -> " + (int) b.karma + " hp " + String.format(Locale.ROOT, "%.1f", b.getHealth()));
    }

    /** Ultima with shards: mends the owner's robots, and rewrites wild ones (a shard a try, keeping one back). */
    private Errand robotJob(ServerPlayer o) {
        if (b.tickCount < nextShardJob) return null;
        nextShardJob = b.tickCount + 100;
        int n = shards(b);
        if (n == 0) return null;
        for (Mob m : b.level().getEntitiesOfClass(Mob.class, b.getBoundingBox().inflate(16), com.xenowarriors.ultima.RobotPets::isRobot)) {
            UUID own = com.xenowarriors.ultima.RobotPets.ownerOf(m);
            if (own != null && own.equals(o.getUUID()) && m.getHealth() < m.getMaxHealth() * 0.7F) return job("robot_fix", null, m, -1, 1, ItemStack.EMPTY, 15);
            if (own == null && n >= 2 && com.xenowarriors.party.Parties.petRoom(o) && m.getTarget() == null) { nextShardJob = b.tickCount + 600; return job("robot_tame", null, m, -1, 1, ItemStack.EMPTY, 15); }
        }
        return null;
    }

    private void robot(ServerPlayer o, Errand e) {
        if (!(e.to instanceof Mob m)) return;
        int slot = shardSlot(b);
        if (slot < 0) return;
        b.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        if (e.kind.equals("robot_fix")) {
            if (!o.getUUID().equals(com.xenowarriors.ultima.RobotPets.ownerOf(m))) return;
            m.heal(m.getMaxHealth() * 0.25F);
            ((ServerLevel) b.level()).sendParticles(ParticleTypes.HAPPY_VILLAGER, m.getX(), m.getY() + 1, m.getZ(), 8, .4, .5, .4, 0);
            b.inventory.getItem(slot).shrink(1);
            tell(o, "repaired your robot with a Data Shard");
        } else {
            if (com.xenowarriors.ultima.RobotPets.ownerOf(m) != null) return;
            ItemStack one = b.inventory.getItem(slot).copyWithCount(1);
            b.inventory.getItem(slot).shrink(1);
            boolean ok = com.xenowarriors.ultima.RobotPets.tryTame(o, m, one);   // (the robot is yours: Ultima rewrote it for you)
            PartyBots.testLog(b, "fed a Data Shard to a wild robot: " + (ok ? "tamed" : "rejected"));
        }
        if (b.inventory.getItem(slot).isEmpty()) b.inventory.setItem(slot, ItemStack.EMPTY);
    }

    // ---------------------------------------------------------------- 11. Yuri's hamburgers

    /** A datapack (or the hamburger's own code) can name more makings here. */
    static final TagKey<Item> BURGER_TAG = ItemTags.create(new ResourceLocation("xenowarriors", "hamburger_ingredients"));
    private static Set<Item> burgerItems; private static Object burgerFor;

    /** What goes into a hamburger: beef (raw or cooked), bread and wheat, plus whatever the hamburger's recipes use (if the mod has one). */
    static boolean burgerMaking(net.minecraft.world.level.Level l, ItemStack s) {
        if (s.isEmpty()) return false;
        if (s.is(BURGER_TAG)) return true;
        var rm = l.getRecipeManager();
        if (burgerItems == null || burgerFor != rm) {
            Set<Item> set = new HashSet<>(List.of(Items.BEEF, Items.COOKED_BEEF, Items.BREAD, Items.WHEAT));
            Item burger = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new ResourceLocation("xenowarriors", "hamburger"));
            if (burger != null && burger != Items.AIR) {
                for (CraftingRecipe r : rm.getAllRecipesFor(RecipeType.CRAFTING)) {
                    ItemStack res; try { res = r.getResultItem(l.registryAccess()); } catch (Throwable t) { continue; }
                    if (!res.is(burger)) continue;
                    for (Ingredient i : r.getIngredients()) for (ItemStack x : i.getItems()) set.add(x.getItem());
                }
            }
            set.remove(Items.AIR);
            burgerItems = set; burgerFor = rm;
        }
        return burgerItems.contains(s.getItem());
    }

    /** Where Yuri last pointed things out (kind -> game time), so she doesn't keep on about it. */
    private final Map<String, Long> told = new HashMap<>();

    /** Every 30-60 s with no enemies about: hamburger makings lying near (she fetches them), or a field of wheat or cows (she says where). */
    Errand yuriLook(ServerPlayer o) {
        if (enemyNear(b, 16)) { PartyBots.testLog(b, "yuri: enemies about - no looking around"); return null; }
        b.yuriLooks++;
        PartyBots.testLog(b, "yuri looks about for hamburger makings");
        ItemEntity near = null; double nd = 1e9;
        for (ItemEntity it : b.level().getEntitiesOfClass(ItemEntity.class, b.getBoundingBox().inflate(16, 6, 16), ItemEntity::isAlive)) {
            if (!burgerMaking(b.level(), it.getItem()) || it.getOwner() instanceof net.minecraft.world.entity.player.Player || !bagRoom(b, it.getItem())) continue;
            double d = it.distanceToSqr(b);
            if (d < nd) { nd = d; near = it; }
        }
        if (near != null) return job("fetch", null, near, -1, near.getItem().getCount(), near.getItem(), 15);
        // nothing to pick up: what's growing or grazing about
        long now = b.level().getGameTime();
        BlockPos c = b.blockPosition(), crop = null; double cd = 1e9;
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-20, -4, -20), c.offset(20, 4, 20))) {
            BlockState st = b.level().getBlockState(p);
            if (st.getBlock() instanceof CropBlock cb && cb.isMaxAge(st) && burgerMaking(b.level(), new ItemStack(cropItem(st)))) {
                double d = p.distSqr(c);
                if (d < cd) { cd = d; crop = p.immutable(); }
            }
        }
        Cow cow = null; double wd = 1e9;
        if (burgerMaking(b.level(), new ItemStack(Items.BEEF)))
            for (Cow cw : b.level().getEntitiesOfClass(Cow.class, b.getBoundingBox().inflate(24, 8, 24), Cow::isAlive)) { double d = cw.distanceToSqr(b); if (d < wd) { wd = d; cow = cw; } }
        String what = null; Vec3 where = null;
        if (crop != null && now - told.getOrDefault("crop", -100000L) > 20 * 180) { what = cropItem(b.level().getBlockState(crop)).getDescription().getString().toLowerCase(Locale.ROOT); where = Vec3.atCenterOf(crop); told.put("crop", now); }
        else if (cow != null && now - told.getOrDefault("cow", -100000L) > 20 * 180) { what = "cows (beef)"; where = cow.position(); told.put("cow", now); }
        if (what == null) return null;
        Vec3 d = where.subtract(o.position());
        String msg = "I found " + what + " to the " + compass(d.x, d.z) + ", " + Math.round(Math.sqrt(d.x * d.x + d.z * d.z)) + " blocks - for hamburgers!";
        o.sendSystemMessage(Component.literal(b.character().getDisplayName().getString() + ": ").withStyle(ChatFormatting.GREEN).append(Component.literal(msg).withStyle(ChatFormatting.WHITE)));
        PartyBots.testLog(b, "yuri told: " + msg);
        return null;
    }

    /** What a crop gives (wheat for wheat, carrots for carrots...). */
    private static Item cropItem(BlockState st) {
        if (st.is(Blocks.WHEAT)) return Items.WHEAT;
        if (st.is(Blocks.CARROTS)) return Items.CARROT;
        if (st.is(Blocks.POTATOES)) return Items.POTATO;
        if (st.is(Blocks.BEETROOTS)) return Items.BEETROOT;
        return st.getBlock().asItem();
    }

    /** "north-east" etc. (north is -Z). */
    static String compass(double dx, double dz) {
        String[] n = {"south", "south-west", "west", "north-west", "north", "north-east", "east", "south-east"};
        double a = Math.toDegrees(Math.atan2(-dx, dz));                       // (Minecraft yaw: 0 = south, 90 = west)
        return n[(int) Math.floorMod(Math.round(a / 45.0), 8)];
    }

    private void fetch(ServerPlayer o, Errand e) {
        if (!(e.to instanceof ItemEntity it) || !it.isAlive()) return;
        ItemStack st = it.getItem().copy();
        ItemStack left = b.inventory.addItem(st.copy());
        int n = st.getCount() - left.getCount();
        if (left.isEmpty()) it.discard(); else it.setItem(left);
        if (n <= 0) return;
        b.level().playSound(null, b.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL, 0.4F, 1.4F);
        tell(o, "picked up " + n + " " + st.getHoverName().getString() + " for hamburgers");
    }

    /** Claims are this world's: none carry over (singleplayer). */
    static void clear() { CLAIMS.clear(); }
}

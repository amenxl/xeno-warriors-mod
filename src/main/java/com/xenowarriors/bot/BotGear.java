package com.xenowarriors.bot;

import com.xenowarriors.character.CharacterType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * What a bot teammate holds: the strongest weapon in its bag for a fight, the best tool for the block it's breaking
 * (an axe for trees), swapped between its hand and its bag. Tools wear out like a player's. Also its tree felling.
 */
public final class BotGear {
    private BotGear() {}

    /**
     * How much damage this does over time in the main hand: its hit (attack damage bonus, Sharpness and the like)
     * times how fast it swings - so a diamond sword beats an iron axe, as it does for players.
     */
    public static float weaponScore(PartyBotEntity b, ItemStack s) {
        if (s.isEmpty() || !allowed(b, s)) return 0F;
        float dmg = 0F, speed = 0F;
        for (AttributeModifier m : s.getAttributeModifiers(EquipmentSlot.MAINHAND).get(Attributes.ATTACK_DAMAGE))
            if (m.getOperation() == AttributeModifier.Operation.ADDITION) dmg += (float) m.getAmount();
        for (AttributeModifier m : s.getAttributeModifiers(EquipmentSlot.MAINHAND).get(Attributes.ATTACK_SPEED))
            if (m.getOperation() == AttributeModifier.Operation.ADDITION) speed += (float) m.getAmount();
        if (dmg <= 0F) return 0F;
        float swings = Math.max(0.5F, Math.min(4F, 4F + speed));             // (players swing 4 times a second bare-handed)
        return (1F + dmg + EnchantmentHelper.getDamageBonus(s, MobType.UNDEFINED)) * swings;
    }

    /** How well this breaks a block (fast, and the right tool so it drops). */
    public static float toolScore(ItemStack s, BlockState st) {
        float speed = s.isEmpty() ? 1F : s.getDestroySpeed(st);
        int eff = s.isEmpty() ? 0 : s.getEnchantmentLevel(net.minecraft.world.item.enchantment.Enchantments.BLOCK_EFFICIENCY);
        if (speed > 1F && eff > 0) speed += eff * eff + 1;                    // (Efficiency, as for players)
        return speed * (s.isCorrectToolForDrops(st) ? 2F : 1F);
    }

    /** The Hekublade is Mikiko's (and Kaeru's): nobody else picks it up to fight with. */
    private static boolean allowed(PartyBotEntity b, ItemStack s) {
        return !com.xenowarriors.mikiko.Hekublade.isBlade(s) || b.character() == CharacterType.MIKIKO || b.character() == CharacterType.KAERU;
    }

    /** You put something in its hand from its menu: it keeps that for a while before choosing for itself. */
    private static boolean handLocked(PartyBotEntity b) { return b.tickCount - b.handPicked < 20 * 20; }

    /**
     * In a fight: its weakest weapon that wears out goes first, so it uses up the old ones (they wear faster the higher
     * its level - see stats.OutgrownWear) and gets rid of them; once those are gone, the strongest it has.
     */
    public static void holdBestWeapon(PartyBotEntity b) {
        if (handLocked(b)) return;
        if (summonSignature(b)) return;
        ItemStack hand = b.getMainHandItem();
        float handSc = weaponScore(b, hand);
        boolean handWears = handSc > 0F && hand.isDamageableItem();
        float weakest = handWears ? handSc : Float.MAX_VALUE;
        int weak = -1;
        for (int i = 0; i < b.inventory.getContainerSize(); i++) {
            ItemStack s = b.inventory.getItem(i);
            if (!s.isDamageableItem()) continue;
            float sc = weaponScore(b, s);
            if (sc > 0F && sc < weakest) { weakest = sc; weak = i; }
        }
        if (weak >= 0) { swap(b, weak); return; }
        if (handWears) return;
        float best = handSc;
        int pick = -1;
        for (int i = 0; i < b.inventory.getContainerSize(); i++) {
            float sc = weaponScore(b, b.inventory.getItem(i));
            if (sc > best) { best = sc; pick = i; }
        }
        if (pick >= 0) swap(b, pick);
    }

    /** A bag weapon must beat the summoned show weapon by this much before the bot leaves it for the bag one. */
    public static final float SIGNATURE_MARGIN = 2.0F;

    /**
     * A bot of a character with a show weapon (Max's knife, Sam's bat, ...) summons it for a fight and keeps it in hand
     * unless a bag weapon is much stronger. True = it holds its own weapon (nothing else to choose).
     */
    private static boolean summonSignature(PartyBotEntity b) {
        net.minecraft.world.item.Item it = com.xenowarriors.forms.SignatureWeapons.itemOf(b.character());
        if (it == null || b.level().isClientSide) return false;
        ItemStack hand = b.getMainHandItem();
        boolean inHand = com.xenowarriors.forms.SignatureWeapons.isSummoned(hand);
        ItemStack sig = inHand ? hand : com.xenowarriors.forms.SignatureWeapons.make(b.character());
        float sigScore = weaponScore(b, sig);
        if (sigScore <= 0F) return false;                                    // (a microphone is no weapon)
        if (!inHand && b.getServer() != null && !com.xenowarriors.character.Seasons.unlocked(b.getServer(), b.character().season())) return false;
        float bag = inHand ? 0F : weaponScore(b, hand);
        for (int i = 0; i < b.inventory.getContainerSize(); i++) bag = Math.max(bag, weaponScore(b, b.inventory.getItem(i)));
        if (bag > sigScore * SIGNATURE_MARGIN) {                             // a much stronger weapon in the bag: that one
            if (inHand) { b.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY); com.xenowarriors.forms.SignatureWeapons.awayFx(b, b.character()); }
            return false;
        }
        if (inHand) return true;
        ItemStack left = hand.isEmpty() ? ItemStack.EMPTY : b.inventory.addItem(hand.copy());
        if (!left.isEmpty()) return false;                                   // (no room to put the old one away)
        b.setItemSlot(EquipmentSlot.MAINHAND, sig);
        b.setDropChance(EquipmentSlot.MAINHAND, 0F);
        com.xenowarriors.forms.SignatureWeapons.summonFx(b, b.character());
        return true;
    }

    /** Breaking a block: the best tool for it (an axe for logs, a pickaxe for ore). */
    public static void holdBestTool(PartyBotEntity b, BlockState st) {
        if (handLocked(b)) return;
        float best = toolScore(b.getMainHandItem(), st);
        int pick = -1;
        for (int i = 0; i < b.inventory.getContainerSize(); i++) {
            ItemStack s = b.inventory.getItem(i);
            if (s.isEmpty() || !allowed(b, s)) continue;
            float sc = toolScore(s, st);
            if (sc > best) { best = sc; pick = i; }
        }
        if (pick >= 0) swap(b, pick);
    }

    /** What's in its hand goes into the bag slot, and that slot's item into its hand. */
    private static void swap(PartyBotEntity b, int slot) {
        ItemStack hand = b.getMainHandItem().copy();
        if (com.xenowarriors.forms.SignatureWeapons.isSummoned(hand)) hand = ItemStack.EMPTY;      // (a summoned weapon never goes in the bag)
        b.setItemSlot(EquipmentSlot.MAINHAND, b.inventory.getItem(slot).copy());
        b.inventory.setItem(slot, hand);
    }

    /** Its hand item back into the bag (before its things go home). Returns what didn't fit. */
    public static ItemStack stowHand(PartyBotEntity b) {
        ItemStack hand = b.getMainHandItem();
        if (hand.isEmpty()) return ItemStack.EMPTY;
        if (com.xenowarriors.forms.SignatureWeapons.isSummoned(hand)) { b.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY); return ItemStack.EMPTY; }
        ItemStack left = b.inventory.addItem(hand.copy());
        b.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        return left;
    }

    private static final java.util.Map<PartyBotEntity, Integer> LAST_BUSY = new java.util.WeakHashMap<>();

    /**
     * Once a second, from the bot's tick: out of a fight for a while (and not mining), it puts its weapon or tool away
     * like a player would - so its character's idles (most need empty hands) play - and looks at what it could craft.
     */
    public static void tickIdle(PartyBotEntity b) {
        if (b.level().isClientSide) return;
        boolean busy = b.getTarget() != null || b.mode() == PartyBotEntity.MINING || b.downed() || b.sparring() || b.isPassenger()
                || b.hurtTime > 0 || handLocked(b);
        if (busy || !LAST_BUSY.containsKey(b)) LAST_BUSY.put(b, b.tickCount);
        ItemStack hand = b.getMainHandItem();
        com.xenowarriors.forms.SignatureWeapons.strip(b.inventory);              // (never in the bag)
        if (!busy && b.tickCount - LAST_BUSY.get(b) > 15 * 20 && com.xenowarriors.forms.SignatureWeapons.isSummoned(hand)) {
            b.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);                // out of the fight: it puts its summoned weapon away
            com.xenowarriors.forms.SignatureWeapons.awayFx(b, b.character());
            hand = ItemStack.EMPTY;
        }
        if (!busy && !hand.isEmpty() && b.tickCount - LAST_BUSY.get(b) > 15 * 20 && !b.journey()
                && (hand.isDamageableItem() || weaponScore(b, hand) > 0F)) {
            ItemStack left = b.inventory.addItem(hand.copy());
            if (left.isEmpty()) b.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            else if (left.getCount() != hand.getCount()) b.setItemSlot(EquipmentSlot.MAINHAND, left);
        }
        BotCrafting.tick(b);
        BotBoosts.tick(b);                                                      // (a Stat Boost you gave it: it uses it)
    }

    /** One use of the held item (a hit landed, a block broken): it wears, and breaks when it's done. */
    public static void wear(PartyBotEntity b) {
        ItemStack hand = b.getMainHandItem();
        if (hand.isDamageableItem()) hand.hurtAndBreak(1, b, e -> e.broadcastBreakEvent(EquipmentSlot.MAINHAND));
    }

    /** Ticks to break a block with what it's holding (bare hands still get there, just slowly). */
    public static int digTicks(PartyBotEntity b, BlockPos pos, BlockState st) {
        float hard = Math.max(0.5F, st.getDestroySpeed(b.level(), pos));
        float speed = com.xenowarriors.stats.HandMining.botSpeed(b, st);    // (its tool's speed, or its bare hands' at its Mining level)
        return Math.max(8, Math.min(40, Math.round(hard * 30F / speed)));
    }

    // ---------------------------------------------------------------- trees

    public static boolean isLog(BlockState s) { return s.is(BlockTags.LOGS); }

    /** A tree near the owner: the bottom log of a trunk with natural leaves on top (never someone's log wall). */
    public static BlockPos findTree(PartyBotEntity b, net.minecraft.server.level.ServerPlayer o) {
        BlockPos c = o.blockPosition(), best = null, taken = null;
        double bd = 1e9, td = 1e9;
        List<BlockPos> others = PartyBots.othersDigging(b);                     // (each its own tree - a shared one only when it's the only tree)
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-16, -4, -16), c.offset(16, 8, 16))) {
            BlockState s = b.level().getBlockState(p);
            if (!isLog(s) || isLog(b.level().getBlockState(p.below())) || b.unreachable(p)) continue;
            double dd = p.distSqr(b.blockPosition()), pen = PartyBots.crowd(p, others, 5);
            if (pen < 0) { if (dd < td && natural(b, p)) { td = dd; taken = p.immutable(); } continue; }
            if (dd + pen >= bd || !natural(b, p)) continue;
            bd = dd + pen; best = p.immutable();
        }
        return best != null ? best : taken;
    }

    /** Logs going up from here end in leaves that grew there (not placed by someone). */
    private static boolean natural(PartyBotEntity b, BlockPos base) {
        BlockPos top = base;
        for (int i = 0; i < 24 && isLog(b.level().getBlockState(top.above())); i++) top = top.above();
        for (BlockPos q : BlockPos.betweenClosed(top.offset(-2, -1, -2), top.offset(2, 2, 2))) {
            BlockState s = b.level().getBlockState(q);
            if (s.getBlock() instanceof LeavesBlock && !s.getValue(LeavesBlock.PERSISTENT)) return true;
        }
        return false;
    }

    /** The whole trunk comes down: every log joined to this one (above it), drops into its bag. */
    public static int fellTree(PartyBotEntity b, BlockPos base) {
        if (!(b.level() instanceof ServerLevel s) || !com.xenowarriors.modes.ModeRules.canGrief(s)) return 0;
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> q = new ArrayDeque<>();
        q.add(base);
        seen.add(base);
        int felled = 0;
        while (!q.isEmpty() && felled < 96) {
            BlockPos p = q.poll();
            BlockState st = s.getBlockState(p);
            if (!isLog(st) || com.xenowarriors.gov.ArenaProtect.protectedAt(s, p)) continue;
            for (ItemStack d : Block.getDrops(st, s, p, s.getBlockEntity(p), b, b.getMainHandItem())) {
                ItemStack left = b.inventory.addItem(d);
                if (!left.isEmpty()) { Block.popResource(s, p, left); PartyBots.notifyFull(b); }
            }
            com.xenowarriors.npc.rebuild.VillageRebuild.playerDug(s, p);       // (its owner's dig: a village won't put it back)
            s.destroyBlock(p, false, b);
            felled++;
            if (felled % 4 == 1) wear(b);
            for (int dx = -1; dx <= 1; dx++) for (int dy = 0; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
                BlockPos n = p.offset(dx, dy, dz);
                if (n.getY() >= base.getY() && seen.add(n) && isLog(s.getBlockState(n))) q.add(n);
            }
        }
        return felled;
    }
}

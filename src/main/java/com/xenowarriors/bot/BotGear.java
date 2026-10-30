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

    /** In a fight: the strongest weapon it has. */
    public static void holdBestWeapon(PartyBotEntity b) {
        if (handLocked(b)) return;
        ItemStack hand = b.getMainHandItem();
        float best = weaponScore(b, hand);
        int pick = -1;
        for (int i = 0; i < b.inventory.getContainerSize(); i++) {
            float sc = weaponScore(b, b.inventory.getItem(i));
            if (sc > best) { best = sc; pick = i; }
        }
        if (pick >= 0) swap(b, pick);
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
        b.setItemSlot(EquipmentSlot.MAINHAND, b.inventory.getItem(slot).copy());
        b.inventory.setItem(slot, hand);
    }

    /** Its hand item back into the bag (before its things go home). Returns what didn't fit. */
    public static ItemStack stowHand(PartyBotEntity b) {
        ItemStack hand = b.getMainHandItem();
        if (hand.isEmpty()) return ItemStack.EMPTY;
        ItemStack left = b.inventory.addItem(hand.copy());
        b.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        return left;
    }

    /** One use of the held item (a hit landed, a block broken): it wears, and breaks when it's done. */
    public static void wear(PartyBotEntity b) {
        ItemStack hand = b.getMainHandItem();
        if (hand.isDamageableItem()) hand.hurtAndBreak(1, b, e -> e.broadcastBreakEvent(EquipmentSlot.MAINHAND));
    }

    /** Ticks to break a block with what it's holding (bare hands still get there, just slowly). */
    public static int digTicks(PartyBotEntity b, BlockPos pos, BlockState st) {
        float hard = Math.max(0.5F, st.getDestroySpeed(b.level(), pos));
        float speed = Math.max(1F, b.getMainHandItem().getDestroySpeed(st));
        return Math.max(8, Math.min(40, Math.round(hard * 30F / speed)));
    }

    // ---------------------------------------------------------------- trees

    public static boolean isLog(BlockState s) { return s.is(BlockTags.LOGS); }

    /** A tree near the owner: the bottom log of a trunk with natural leaves on top (never someone's log wall). */
    public static BlockPos findTree(PartyBotEntity b, net.minecraft.server.level.ServerPlayer o) {
        BlockPos c = o.blockPosition(), best = null;
        double bd = 1e9;
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-16, -4, -16), c.offset(16, 8, 16))) {
            BlockState s = b.level().getBlockState(p);
            if (!isLog(s) || isLog(b.level().getBlockState(p.below()))) continue;
            double dd = p.distSqr(b.blockPosition());
            if (dd >= bd || !natural(b, p)) continue;
            bd = dd; best = p.immutable();
        }
        return best;
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
            if (!isLog(st)) continue;
            for (ItemStack d : Block.getDrops(st, s, p, s.getBlockEntity(p), b, b.getMainHandItem())) {
                ItemStack left = b.inventory.addItem(d);
                if (!left.isEmpty()) { Block.popResource(s, p, left); PartyBots.notifyFull(b); }
            }
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

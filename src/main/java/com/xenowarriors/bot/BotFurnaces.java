package com.xenowarriors.bot;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FurnaceBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;

import java.util.*;

/**
 * Bots' own furnaces. A bot with ore to smelt and fuel for it, and no free furnace about, now and then makes itself a
 * furnace (8 cobblestone - or one from its bag) and sets it down next to it, then smelts in it (BotErrands) - and the
 * smelting levels up your Smelting (it's shared). When you move on and leave a furnace it's using behind (you're 24+
 * blocks off), it grabs everything - what's smelted, what isn't yet, the fuel, and its own furnace too - so nothing's
 * lost; it can finish smelting later.
 */
public final class BotFurnaces {
    private BotFurnaces() {}

    /** Smelting XP for each item it takes out (the same as yours for a furnace's output). */
    public static final float XP_EACH = 1F;
    /** How far you get from a furnace it's using before it packs up there. */
    public static final double LEAVE = 24;
    /** The least time between two furnaces made by one bot (ticks). */
    static final int GAP = 20 * 60 * 2;
    /** Its own furnaces (where, which dimension). */
    private record Own(UUID bot, String dim) {}
    private static final Map<Long, Own> OWN = new HashMap<>();
    private static final Map<UUID, Long> NEXT = new HashMap<>();
    /** (Tests) furnaces made, items smelted and taken out, pack-ups when you left. */
    public static int made, smelted, packed;
    public static boolean forceForTest;

    /** No free furnace about: it may make one and set it down. Where it went, or null. */
    static BlockPos makeOne(PartyBotEntity b, ServerPlayer o) {
        if (!(b.level() instanceof ServerLevel l) || b.journey() || BotSupport.arena(l) || !com.xenowarriors.modes.ModeRules.canGrief(l)) return null;
        long now = l.getGameTime();
        if (!forceForTest && (now < NEXT.getOrDefault(b.getUUID(), 0L) || b.getRandom().nextInt(3) != 0)) return null;
        NEXT.put(b.getUUID(), now + GAP);
        boolean ore = false;
        for (int i = 0; i < b.inventory.getContainerSize() && !ore; i++) { ItemStack s = b.inventory.getItem(i); ore = BotErrands.worthSmelting(b, s) && !BotErrands.food(s); }
        if (!ore) return null;
        BlockPos at = spot(l, b);
        if (at == null) return null;
        int slot = slotOf(b);
        if (slot < 0) {
            if (BotCrafting.craftRecipe(b, new net.minecraft.resources.ResourceLocation("minecraft", "furnace")) == null) return null;
            slot = slotOf(b);
            if (slot < 0) return null;
            o.displayClientMessage(Component.empty().append(b.label()).append(" made a furnace to smelt some ore.").withStyle(ChatFormatting.AQUA), true);
        }
        b.inventory.getItem(slot).shrink(1);
        b.inventory.setChanged();
        Direction face = Direction.fromYRot(b.getYRot()).getOpposite();
        l.setBlock(at, Blocks.FURNACE.defaultBlockState().setValue(FurnaceBlock.FACING, face), 3);
        l.playSound(null, at, SoundEvents.STONE_PLACE, SoundSource.BLOCKS, 0.8F, 1F);
        b.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        OWN.put(at.asLong(), new Own(b.getUUID(), l.dimension().location().toString()));
        forceForTest = false;
        made++;
        PartyBots.testLog(b, "set down its own furnace at " + at.toShortString());
        return at;
    }

    private static int slotOf(PartyBotEntity b) {
        for (int i = 0; i < b.inventory.getContainerSize(); i++) if (b.inventory.getItem(i).is(Items.FURNACE)) return i;
        return -1;
    }

    /** A free spot by it on solid ground (not where it or you stand). */
    private static BlockPos spot(ServerLevel l, PartyBotEntity b) {
        BlockPos c = b.blockPosition();
        for (int r = 1; r <= 2; r++) for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
            if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
            BlockPos p = c.offset(dx, 0, dz);
            if (l.getBlockState(p).canBeReplaced() && l.getFluidState(p).isEmpty() && l.getBlockState(p.below()).isFaceSturdy(l, p.below(), Direction.UP)
                    && l.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, new net.minecraft.world.phys.AABB(p)).isEmpty()) return p.immutable();
        }
        return null;
    }

    /** It's taken the smelting out: its own furnace, empty now, goes back in its bag. */
    static void doneWith(PartyBotEntity b, BlockPos p) {
        Own own = OWN.get(p.asLong());
        if (own == null || !own.bot().equals(b.getUUID()) || !(b.level().getBlockEntity(p) instanceof AbstractFurnaceBlockEntity f)) return;
        if (!f.getItem(0).isEmpty() || !f.getItem(2).isEmpty()) return;
        take(b, (ServerLevel) b.level(), p, f);
    }

    /** Once a second, from the bot's own tick: you've moved on - it grabs everything from the furnaces it's using. */
    static void tick(PartyBotEntity b) {
        if (b.tickCount % 20 != 11 || !(b.level() instanceof ServerLevel l)) return;
        ServerPlayer o = b.owner();
        if (o == null) return;
        String dim = l.dimension().location().toString();
        List<Long> mine = new ArrayList<>();
        for (var en : BotErrands.CLAIMS.entrySet()) if (en.getValue().bot().equals(b.getUUID()) && en.getValue().dim().equals(dim)) mine.add(en.getKey());
        for (var en : OWN.entrySet()) if (en.getValue().bot().equals(b.getUUID()) && en.getValue().dim().equals(dim) && !mine.contains(en.getKey())) mine.add(en.getKey());
        for (long k : mine) {
            BlockPos p = BlockPos.of(k);
            boolean left = o.level() != l || o.blockPosition().distSqr(p) > LEAVE * LEAVE;
            if (!left) continue;
            if (!l.isLoaded(p) || !(l.getBlockEntity(p) instanceof AbstractFurnaceBlockEntity f)) { BotErrands.CLAIMS.remove(k); OWN.remove(k); continue; }
            packUp(b, l, p, f, o);
        }
    }

    /** Everything it put in (and what came out) back in its bag - and its own furnace too. */
    private static void packUp(PartyBotEntity b, ServerLevel l, BlockPos p, AbstractFurnaceBlockEntity f, ServerPlayer o) {
        Own own = OWN.get(p.asLong());
        boolean ours = own != null && own.bot().equals(b.getUUID());
        int out = f.getItem(2).getCount();
        if (out > 0) { com.xenowarriors.stats.Crafting.addSmeltXp(o, out * XP_EACH); smelted += out; }
        for (int slot : ours ? new int[]{0, 1, 2} : new int[]{0, 2}) {           // (someone else's furnace: its fuel stays)
            ItemStack s = f.getItem(slot);
            if (s.isEmpty()) continue;
            ItemStack left = b.inventory.addItem(s.copy());
            f.setItem(slot, left);
            if (!left.isEmpty()) { b.spawnAtLocation(left); f.setItem(slot, ItemStack.EMPTY); }
        }
        f.setChanged();
        BotErrands.CLAIMS.remove(p.asLong());
        if (ours) take(b, l, p, f);
        packed++;
        o.displayClientMessage(Component.empty().append(b.label()).append(" packed up their smelting to bring along.").withStyle(ChatFormatting.GRAY), true);
        PartyBots.testLog(b, "packed up its smelting at " + p.toShortString() + (ours ? " (and its furnace)" : ""));
    }

    private static void take(PartyBotEntity b, ServerLevel l, BlockPos p, AbstractFurnaceBlockEntity f) {
        for (int i = 0; i < 3; i++) { ItemStack s = f.getItem(i); if (!s.isEmpty()) { ItemStack left = b.inventory.addItem(s.copy()); if (!left.isEmpty()) b.spawnAtLocation(left); f.setItem(i, ItemStack.EMPTY); } }
        l.removeBlock(p, false);
        ItemStack left = b.inventory.addItem(new ItemStack(Items.FURNACE));
        if (!left.isEmpty()) b.spawnAtLocation(left);
        OWN.remove(p.asLong());
        BotErrands.CLAIMS.remove(p.asLong());
    }
}

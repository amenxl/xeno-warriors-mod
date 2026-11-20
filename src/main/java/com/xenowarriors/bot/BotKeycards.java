package com.xenowarriors.bot;

import com.xenowarriors.gov.GovItems;
import com.xenowarriors.gov.GovVaultBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A bot with a Gov Keycard in its bag opens Government vaults: one shut within 10 blocks of it (and of you), out of a
 * fight, it walks over, swipes the card (used up) and the loot pours out - the same as you opening it, alarm and all
 * (staff about blame you).
 */
public final class BotKeycards {
    private BotKeycards() {}

    private static final Map<UUID, BlockPos> GOING = new HashMap<>();
    /** (Tests) vaults opened by bots. */
    public static int opened;

    private static int cardSlot(PartyBotEntity b) {
        for (int i = 0; i < b.inventory.getContainerSize(); i++) if (b.inventory.getItem(i).is(GovItems.KEYCARD.get())) return i;
        return -1;
    }

    /** Every few ticks, from the bot's own tick. */
    static void tick(PartyBotEntity b) {
        if (b.tickCount % 10 != 4 || !(b.level() instanceof ServerLevel l) || b.downed() || b.journey() || b.isPassenger()) return;
        BlockPos going = GOING.get(b.getUUID());
        if (going != null) { walk(b, l, going); return; }
        if (b.tickCount % 40 != 4 || b.getTarget() != null || cardSlot(b) < 0) return;
        ServerPlayer o = b.owner();
        if (o == null || o.level() != l || o.distanceToSqr(b) > 16 * 16) return;
        BlockPos c = b.blockPosition(), best = null; double bd = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-10, -3, -10), c.offset(10, 3, 10))) {
            BlockState s = l.getBlockState(p);
            if (!(s.getBlock() instanceof GovVaultBlock) || s.getValue(GovVaultBlock.OPEN)) continue;
            double d = p.distSqr(c);
            if (d < bd) { bd = d; best = p.immutable(); }
        }
        if (best == null) return;
        GOING.put(b.getUUID(), best);
        o.displayClientMessage(Component.empty().append(b.label()).append(" has a Gov Keycard - they're opening the vault!").withStyle(ChatFormatting.GOLD), true);
        walk(b, l, best);
    }

    private static void walk(PartyBotEntity b, ServerLevel l, BlockPos p) {
        BlockState s = l.getBlockState(p);
        int slot = cardSlot(b);
        if (!(s.getBlock() instanceof GovVaultBlock) || s.getValue(GovVaultBlock.OPEN) || slot < 0 || b.getTarget() != null || b.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(p)) > 24 * 24) { GOING.remove(b.getUUID()); return; }
        if (b.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(p)) > 4.5 * 4.5) {                // (to the front of it - near enough to reach the slot)
            net.minecraft.core.Direction f = s.getValue(GovVaultBlock.FACING);
            BlockPos front = p.relative(f);
            b.getNavigation().moveTo(front.getX() + 0.5, front.getY(), front.getZ() + 0.5, 1.15);
            return;
        }
        GOING.remove(b.getUUID());
        b.getNavigation().stop();
        b.getLookControl().setLookAt(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5);
        ItemStack card = b.inventory.getItem(slot);
        card.shrink(1);
        b.inventory.setChanged();
        b.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        GovVaultBlock.open(l, p, s, b, b.owner());
        opened++;
        PartyBots.testLog(b, "opened a Gov vault at " + p.toShortString());
    }

    /** (Tests) */
    public static boolean goingForTest(PartyBotEntity b) { return GOING.containsKey(b.getUUID()); }
}

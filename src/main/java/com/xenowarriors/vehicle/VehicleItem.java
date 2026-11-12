package com.xenowarriors.vehicle;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.List;

/** A vehicle in your pocket (its keys, really): use it on the ground to park it there. Remembers the colour. */
public class VehicleItem extends Item {
    public final VehicleKind kind;

    public VehicleItem(VehicleKind kind, Properties p) { super(p); this.kind = kind; }

    public static ItemStack make(VehicleKind kind, int variant) {
        ItemStack s = new ItemStack(VehicleItems.ITEMS_BY_KIND.get(kind).get());
        s.getOrCreateTag().putInt("Variant", variant);
        return s;
    }

    public static int variant(ItemStack s) { return s.hasTag() ? s.getTag().getInt("Variant") : 0; }

    @Override
    public Component getName(ItemStack s) {
        if (kind.technica()) return Component.literal("Technica's Motorcycle Key");
        String v = kind.variant(variant(s));
        return Component.literal(kind.title + (kind.variants.length > 1 ? " (" + Character.toUpperCase(v.charAt(0)) + v.substring(1) + ")" : ""));
    }

    @Override
    public void appendHoverText(ItemStack s, Level level, List<Component> tip, TooltipFlag flag) {
        if (kind.technica()) {
            tip.add(Component.literal("Use it anywhere (as Technica) to call your pink motorcycle - it comes to you.").withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE));
            tip.add(Component.literal("Driving: attack fires its Twin Blasters, use fires a rocket (with the Rocket Pod). Use attachments on it to fit them.").withStyle(net.minecraft.ChatFormatting.GRAY));
            return;
        }
        tip.add(Component.literal("Use on the ground to park it. Sneak + right-click it to pick it back up.").withStyle(net.minecraft.ChatFormatting.GRAY));
        tip.add(Component.literal(kind.seats + " seats").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        if (kind.upgraded()) tip.add(Component.literal("Mk II: " + Math.round(kind.maxSpeed * 72) + " km/h, " + kind.health + " health"
                + (kind.hovers() ? " - flies (Jump to lift off, Sprint key to come down)" : "")).withStyle(net.minecraft.ChatFormatting.AQUA));
    }

    /** Technica's key: used anywhere, it calls her motorcycle (the key stays). */
    @Override
    public net.minecraft.world.InteractionResultHolder<ItemStack> use(Level level, net.minecraft.world.entity.player.Player p, net.minecraft.world.InteractionHand hand) {
        if (!kind.technica()) return super.use(level, p, hand);
        if (!level.isClientSide && p instanceof net.minecraft.server.level.ServerPlayer sp) TechnicaBike.useKey(sp);
        return net.minecraft.world.InteractionResultHolder.sidedSuccess(p.getItemInHand(hand), level.isClientSide);
    }

    @Override
    public InteractionResult useOn(UseOnContext c) {
        Level level = c.getLevel();
        if (kind.technica()) {
            if (!level.isClientSide && c.getPlayer() instanceof net.minecraft.server.level.ServerPlayer sp) TechnicaBike.useKey(sp);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (level.isClientSide) return InteractionResult.SUCCESS;
        // not in the modes' arenas (the Tower / Journey dimension, VR): they're cleared when a run ends, and the
        // vehicle with them - no building there anyway (operators in creative still can, like blocks)
        if (com.xenowarriors.modes.ModeRules.arena(level) && (c.getPlayer() == null || !(c.getPlayer().isCreative() && c.getPlayer().hasPermissions(2)))) {
            if (c.getPlayer() != null) c.getPlayer().displayClientMessage(Component.literal("Vehicles can't be parked here.").withStyle(net.minecraft.ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }
        BlockPos at = c.getClickedPos().relative(c.getClickedFace());
        VehicleEntity v = Vehicles.TYPES.get(kind).get().create(level);
        if (v == null) return InteractionResult.FAIL;
        float yaw = c.getPlayer() != null ? c.getPlayer().getYRot() : 0;
        v.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, yaw, 0);
        v.yBodyRot = yaw; v.yHeadRot = yaw;
        if (!level.noCollision(v)) {
            if (c.getPlayer() != null) c.getPlayer().displayClientMessage(Component.literal("Not enough room to park it here."), true);
            return InteractionResult.FAIL;
        }
        v.setVariant(variant(c.getItemInHand()));
        if (c.getPlayer() != null) v.setOwner(c.getPlayer().getUUID());
        ((ServerLevel) level).addFreshEntity(v);
        if (c.getPlayer() == null || !c.getPlayer().getAbilities().instabuild) c.getItemInHand().shrink(1);
        return InteractionResult.CONSUME;
    }
}

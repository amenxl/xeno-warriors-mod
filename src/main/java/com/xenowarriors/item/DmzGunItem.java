package com.xenowarriors.item;

import com.xenowarriors.compat.DmzGuns;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Ki guns in the style of DragonMineZ's Blaster Cannon (only there with DragonMineZ installed): they shoot
 * DragonMineZ's own ki blasts. The Blaster Rifle fires while you hold right-click; the Burst Cannon fires three
 * quick blasts; the Blaster Mortar charges a huge one. Skylanders in DragonMineZ's worlds carry them.
 */
public class DmzGunItem extends Item {
    private final DmzGuns.Kind kind;

    public DmzGunItem(DmzGuns.Kind kind, Properties props) {
        super(props.stacksTo(1).durability(kind.durability));
        this.kind = kind;
    }

    public DmzGuns.Kind kind() { return kind; }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        ItemStack stack = p.getItemInHand(hand);
        if (kind.held) {                                                         // the rifle: keeps firing while held
            p.startUsingItem(hand);
            return InteractionResultHolder.consume(stack);
        }
        if (!level.isClientSide) {
            DmzGuns.fire(p, kind, null);
            stack.hurtAndBreak(1, p, e -> e.broadcastBreakEvent(hand));
        }
        p.getCooldowns().addCooldown(this, kind.cooldown);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void onUseTick(Level level, LivingEntity e, ItemStack stack, int left) {
        if (!kind.held || level.isClientSide) return;
        int used = getUseDuration(stack) - left;
        if (used % kind.cooldown != 0) return;
        DmzGuns.fire(e, kind, null);
        stack.hurtAndBreak(1, e, x -> x.broadcastBreakEvent(e.getUsedItemHand()));
    }

    @Override public int getUseDuration(ItemStack stack) { return kind.held ? 72000 : 0; }
    @Override public UseAnim getUseAnimation(ItemStack stack) { return UseAnim.NONE; }
    @Override public boolean isEnchantable(ItemStack stack) { return false; }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.literal(kind.blurb).withStyle(ChatFormatting.GRAY));
        tip.add(Component.literal(kind.damageLine()).withStyle(ChatFormatting.DARK_AQUA));
    }
}

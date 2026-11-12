package com.xenowarriors.stats;

import com.xenowarriors.bot.PartyBotEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TieredItem;
import net.minecraftforge.common.util.FakePlayer;

/**
 * Outgrown gear wears out fast: a player (or bot teammate) using a weaker weapon or tool than the best of its kind they
 * carry loses durability faster the higher their Character Level - a wooden sword with a diamond one in the bag breaks
 * quickly. x(1 + min(19, level / 50) x how much weaker it is): at level 500 a wooden sword next to a diamond one wears
 * about 4-5 times as fast. Nothing changes when it's the best you have. (mixin.ItemStackWearMixin)
 */
public final class OutgrownWear {
    private OutgrownWear() {}

    /** (For the tests) how many extra points of wear were added. */
    public static int added;

    private static float damage(ItemStack s) {
        float dmg = 0F, speed = 0F;
        for (AttributeModifier m : s.getAttributeModifiers(EquipmentSlot.MAINHAND).get(Attributes.ATTACK_DAMAGE))
            if (m.getOperation() == AttributeModifier.Operation.ADDITION) dmg += (float) m.getAmount();
        for (AttributeModifier m : s.getAttributeModifiers(EquipmentSlot.MAINHAND).get(Attributes.ATTACK_SPEED))
            if (m.getOperation() == AttributeModifier.Operation.ADDITION) speed += (float) m.getAmount();
        return dmg <= 0F ? 0F : (1F + dmg) * Math.max(0.5F, Math.min(4F, 4F + speed));
    }

    /** How good it is, compared only with things of the same kind (0 = not comparable). */
    private static float power(ItemStack s, ItemStack like) {
        if (s.isEmpty() || !s.isDamageableItem()) return 0F;
        if (like.getItem() instanceof DiggerItem) {                          // tools: the same kind (pickaxe vs pickaxe), by tier
            if (s.getItem().getClass() != like.getItem().getClass() || !(s.getItem() instanceof TieredItem t)) return 0F;
            return t.getTier().getSpeed() + t.getTier().getLevel();
        }
        return damage(s);                                                    // weapons: by damage over time
    }

    private static float best(Container c, ItemStack like, float best) {
        for (int i = 0; i < c.getContainerSize(); i++) {
            ItemStack s = c.getItem(i);
            if (s != like) best = Math.max(best, power(s, like));
        }
        return best;
    }

    /** The wear to apply instead of {@code amount} for this use of {@code stack} by {@code holder}. */
    public static int wear(ItemStack stack, int amount, LivingEntity holder) {
        if (amount <= 0 || holder == null || holder.level().isClientSide) return amount;
        if (stack != holder.getMainHandItem() && stack != holder.getOffhandItem()) return amount;   // (armour etc. untouched)
        int level;
        float top;
        float own = power(stack, stack);
        if (own <= 0F) return amount;
        if (holder instanceof ServerPlayer p && !(p instanceof FakePlayer)) {
            level = Stats.characterLevel(p);
            top = best(p.getInventory(), stack, 0F);
        } else if (holder instanceof PartyBotEntity b) {
            level = b.botLevel();
            top = best(b.inventory, stack, 0F);
        } else return amount;
        if (top <= own * 1.05F) return amount;                                // it's the best (or as good) as you have
        float gap = 1F - own / top;
        float mult = 1F + Math.min(19F, level / 50F) * gap;
        int out = Math.max(amount, Math.round(amount * mult));
        added += out - amount;
        return out;
    }
}

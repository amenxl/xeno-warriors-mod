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
 * (Also: the last few hits of an outgrown weapon hit hard - see {@link #onHurt}.)
 *
 * Outgrown gear wears out fast: a player (or bot teammate) using a weaker weapon or tool than the best of its kind they
 * carry loses durability faster the higher their Character Level - a wooden sword with a diamond one in the bag breaks
 * quickly: an outgrown weapon breaks in about 3 hits (a third of its full durability per hit); an outgrown tool wears
 * x(1 + min(19, level / 50) x how much weaker it is). Nothing changes when it's the best you have. (mixin.ItemStackWearMixin)
 */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = com.xenowarriors.XenoWarriors.MOD_ID, bus = net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus.FORGE)
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

    /** Never for guns, the weapons and tools characters summon, or anything that doesn't wear. */
    public static boolean excluded(ItemStack s) {
        return s.isEmpty() || !s.isDamageableItem() || s.getMaxDamage() <= 0 || com.xenowarriors.item.Guns.isGun(s)
                || s.getItem() instanceof com.xenowarriors.forms.FormItems.Summoned || com.xenowarriors.forms.SignatureWeapons.isSummoned(s)
                || com.xenowarriors.mikiko.Hekublade.isBlade(s);
    }

    /** The wear to apply instead of {@code amount} for this use of {@code stack} by {@code holder}. */
    public static int wear(ItemStack stack, int amount, LivingEntity holder) {
        int out = scaled(stack, amount, holder);
        added += out - amount;
        return out;
    }

    private static int scaled(ItemStack stack, int amount, LivingEntity holder) {
        if (amount <= 0 || holder == null || holder.level().isClientSide || excluded(stack)) return amount;
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
        if (!(stack.getItem() instanceof DiggerItem))                          // an outgrown weapon: gone in about 3 hits, whatever your level
            return Math.max(amount, (int) Math.ceil(stack.getMaxDamage() / 3.0));
        float gap = 1F - own / top;
        float mult = 1F + Math.min(19F, level / 50F) * gap;
        return Math.max(amount, Math.round(amount * mult));
    }

    // ------------------------------------------------------------------ its last hits hit hard

    /** (For the tests) the last boosted hit: {damage before, after}. */
    public static float[] lastBoost = {0F, 0F};

    /** The best weapon's damage (attack damage bonus) in what this holder carries, or 0. */
    private static float bestDamage(LivingEntity holder, ItemStack held) {
        Container c = holder instanceof ServerPlayer p ? p.getInventory() : holder instanceof PartyBotEntity b ? b.inventory : null;
        if (c == null) return 0F;
        float best = 0F;
        for (int i = 0; i < c.getContainerSize(); i++) {
            ItemStack s = c.getItem(i);
            if (s == held || s.isEmpty()) continue;
            best = Math.max(best, bonus(s));
        }
        return best;
    }

    private static float bonus(ItemStack s) {
        float dmg = 0F;
        for (AttributeModifier m : s.getAttributeModifiers(EquipmentSlot.MAINHAND).get(Attributes.ATTACK_DAMAGE))
            if (m.getOperation() == AttributeModifier.Operation.ADDITION) dmg += (float) m.getAmount();
        return dmg;
    }

    /**
     * An outgrown weapon on its last three hits (it'll break within them at the rate it's wearing) hits harder than
     * your best weapon would - x1.3 of it, and x2 on the hit that breaks it - so there's a reason to use up old gear.
     */
    @net.minecraftforge.eventbus.api.SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGH)
    public static void onHurt(net.minecraftforge.event.entity.living.LivingHurtEvent event) {
        var src = event.getSource();
        if (!(src.getEntity() instanceof LivingEntity holder) || src.getDirectEntity() != holder || holder.level().isClientSide) return;
        if (!(holder instanceof ServerPlayer || holder instanceof PartyBotEntity)) return;
        ItemStack held = holder.getMainHandItem();
        if (excluded(held) || damage(held) <= 0F) return;
        int per = scaled(held, 1, holder);
        if (per <= 1) return;                                                // (not outgrown: wears normally)
        int left = held.getMaxDamage() - held.getDamageValue();
        if (left > per * 3) return;                                          // not on its last hits yet
        float own = bonus(held), best = bestDamage(holder, held);
        if (best <= own) return;
        boolean breaking = left <= per;
        float base = event.getAmount() - own;                               // (what the holder brings without the weapon)
        float out = Math.max(event.getAmount(), (base + best * 1.3F) * (breaking ? 2F : 1F));
        lastBoost = new float[]{event.getAmount(), out};
        event.setAmount(out);
    }
}

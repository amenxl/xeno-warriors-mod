package com.xenowarriors.sky;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.UUID;

/**
 * Corrupted Armor (from Corrupted Ore): iron-strong, dark gifts - helmet +50 max Karma, chestplate 10% less damage
 * from corrupted souls and the possessed, leggings 5% faster, boots an extra (dark, damaging) air jump. The full set
 * wraps you in a dark aura; Karma Sensing reads anyone wearing a piece as a possible corrupted soul.
 */
public class CorruptedArmorItem extends ArmorItem {
    private static final UUID SPEED = UUID.fromString("5d0e3c7a-21b4-4f9e-8a6c-1f2b7e9d4c53");
    public static final ArmorMaterial CORRUPTED = new ArmorMaterial() {
        @Override public int getDurabilityForType(Type type) { return new int[]{195, 225, 240, 165}[3 - type.ordinal()]; }
        @Override public int getDefenseForType(Type type) {
            return switch (type) { case HELMET -> 2; case CHESTPLATE -> 6; case LEGGINGS -> 5; default -> 2; };
        }
        @Override public int getEnchantmentValue() { return 9; }
        @Override public SoundEvent getEquipSound() { return SoundEvents.ARMOR_EQUIP_IRON; }
        @Override public Ingredient getRepairIngredient() { return Ingredient.of(com.xenowarriors.item.ModItems.CORRUPTED_SHARD.get()); }
        @Override public String getName() { return "xenowarriors:corrupted"; }
        @Override public float getToughness() { return 0F; }
        @Override public float getKnockbackResistance() { return 0F; }
    };

    public CorruptedArmorItem(Type type, Properties props) {
        super(CORRUPTED, type, props);
    }

    public static boolean wearing(Player p, EquipmentSlot slot) {
        return p.getItemBySlot(slot).getItem() instanceof CorruptedArmorItem;
    }

    public static boolean wearingAny(net.minecraft.world.entity.LivingEntity e) {
        for (EquipmentSlot s : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET})
            if (e.getItemBySlot(s).getItem() instanceof CorruptedArmorItem) return true;
        return false;
    }

    public static boolean fullSet(net.minecraft.world.entity.LivingEntity e) {
        for (EquipmentSlot s : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET})
            if (!(e.getItemBySlot(s).getItem() instanceof CorruptedArmorItem)) return false;
        return true;
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(EquipmentSlot slot, ItemStack stack) {
        Multimap<Attribute, AttributeModifier> base = super.getAttributeModifiers(slot, stack);
        if (getType() != Type.LEGGINGS || slot != EquipmentSlot.LEGS) return base;
        ImmutableMultimap.Builder<Attribute, AttributeModifier> b = ImmutableMultimap.builder();
        b.putAll(base);
        b.put(Attributes.MOVEMENT_SPEED, new AttributeModifier(SPEED, "Corrupted leggings", 0.05, AttributeModifier.Operation.MULTIPLY_BASE));
        return b.build();
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        String key = switch (getType()) {
            case HELMET -> "+50 max Karma";
            case CHESTPLATE -> "10% less damage from corrupted souls and the possessed";
            case LEGGINGS -> "5% faster";
            default -> "An extra dark air jump that hurts enemies (cooldown)";
        };
        tooltip.add(Component.literal(key).withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.literal("Full set: a dark aura").withStyle(ChatFormatting.DARK_PURPLE));
    }
}

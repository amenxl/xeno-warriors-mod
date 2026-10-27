package com.xenowarriors.item;

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
 * Fox PJs (made with Fox Shards): a cozy orange fox onesie - hood with ears, white belly, elastic cuffs, a white-tipped
 * tail and paw slippers. Every character can wear it over any of their outfits. Each piece: +5% running speed.
 */
public class FoxPjsItem extends ArmorItem {
    public static final ArmorMaterial FOX_PJS = new ArmorMaterial() {
        @Override public int getDurabilityForType(Type type) { return new int[]{65, 75, 80, 55}[3 - type.ordinal()]; }
        @Override public int getDefenseForType(Type type) { return switch (type) { case CHESTPLATE -> 3; case LEGGINGS -> 2; default -> 1; }; }
        @Override public int getEnchantmentValue() { return 15; }
        @Override public SoundEvent getEquipSound() { return SoundEvents.ARMOR_EQUIP_LEATHER; }
        @Override public Ingredient getRepairIngredient() { return Ingredient.of(ModItems.FOX_SHARD.get()); }
        @Override public String getName() { return "xenowarriors:fox_pjs"; }
        @Override public float getToughness() { return 0F; }
        @Override public float getKnockbackResistance() { return 0F; }
    };
    private static final UUID[] SPEED_IDS = {UUID.fromString("6f7a1c2e-3d40-4b1a-9f21-0a1b2c3d4e01"), UUID.fromString("6f7a1c2e-3d40-4b1a-9f21-0a1b2c3d4e02"),
            UUID.fromString("6f7a1c2e-3d40-4b1a-9f21-0a1b2c3d4e03"), UUID.fromString("6f7a1c2e-3d40-4b1a-9f21-0a1b2c3d4e04")};
    private final Multimap<Attribute, AttributeModifier> mods;

    public FoxPjsItem(Type type, Properties props) {
        super(FOX_PJS, type, props);
        ImmutableMultimap.Builder<Attribute, AttributeModifier> b = ImmutableMultimap.builder();
        b.putAll(super.getDefaultAttributeModifiers(type.getSlot()));
        b.put(Attributes.MOVEMENT_SPEED, new AttributeModifier(SPEED_IDS[type.ordinal()], "Fox PJs speed", 0.05, AttributeModifier.Operation.MULTIPLY_BASE));
        mods = b.build();
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot slot) {
        return slot == getType().getSlot() ? mods : super.getDefaultAttributeModifiers(slot);
    }

    public static boolean wearing(Player p, EquipmentSlot slot) {
        return p.getItemBySlot(slot).getItem() instanceof FoxPjsItem;
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Cozy: +5% running speed per piece").withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.literal("Fits everyone, over any outfit").withStyle(ChatFormatting.GRAY));
    }
}

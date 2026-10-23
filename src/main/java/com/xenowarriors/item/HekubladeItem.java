package com.xenowarriors.item;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * Mikiko's sword. It's summoned (default key G) rather than crafted, never breaks, and sets what it
 * hits on fire - Mikiko's element.
 */
public class HekubladeItem extends SwordItem {
    public static final Tier TIER = new Tier() {
        @Override public int getUses() { return 0; }
        @Override public float getSpeed() { return 9F; }
        @Override public float getAttackDamageBonus() { return 4F; }
        @Override public int getLevel() { return 4; }
        @Override public int getEnchantmentValue() { return 15; }
        @Override public Ingredient getRepairIngredient() { return Ingredient.EMPTY; }
    };

    public HekubladeItem(Properties properties) {
        // 1 (hand) + 4 (tier) + 3 = 8 damage, a bit faster than a normal sword
        super(TIER, 3, -2.6F, properties);     // attack speed 1.4: combos at the pace of the other big weapons
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (!com.xenowarriors.mikiko.Hekublade.rollFire(target, 3) && target.level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(),
                    6, 0.25, 0.3, 0.25, 0.2);
        }
        return true;
    }

    private com.google.common.collect.Multimap<net.minecraft.world.entity.ai.attributes.Attribute,
            net.minecraft.world.entity.ai.attributes.AttributeModifier> withReach;
    private static final java.util.UUID REACH_ID = java.util.UUID.fromString("3c8e1a52-7d4f-4b9e-a1c2-9f0b6e5d4a21");

    /** A long blade: Mikiko can hit things 1.5 blocks further away than with a normal sword. */
    @Override
    public com.google.common.collect.Multimap<net.minecraft.world.entity.ai.attributes.Attribute,
            net.minecraft.world.entity.ai.attributes.AttributeModifier> getDefaultAttributeModifiers(net.minecraft.world.entity.EquipmentSlot slot) {
        if (slot != net.minecraft.world.entity.EquipmentSlot.MAINHAND) return super.getDefaultAttributeModifiers(slot);
        if (withReach == null) {
            var b = com.google.common.collect.ImmutableMultimap.<net.minecraft.world.entity.ai.attributes.Attribute,
                    net.minecraft.world.entity.ai.attributes.AttributeModifier>builder();
            b.putAll(super.getDefaultAttributeModifiers(slot));
            b.put(net.minecraftforge.common.ForgeMod.ENTITY_REACH.get(), new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                    REACH_ID, "Hekublade reach", 1.5, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION));
            withReach = b.build();
        }
        return withReach;
    }

    @Override
    public boolean isFoil(ItemStack stack) { return false; }

    /** Drawn by HekubladeRenderer so its crystals can move. */
    @Override
    public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientItemExtensions> consumer) {
        consumer.accept(new net.minecraftforge.client.extensions.common.IClientItemExtensions() {
            @Override
            public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return com.xenowarriors.client.mikiko.HekubladeRenderer.get();
            }
        });
    }
}

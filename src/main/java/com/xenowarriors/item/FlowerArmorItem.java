package com.xenowarriors.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * Flower Armor (iron and flowers): painted pale steel with flowers on it, gold trim. Like a plant:
 *  - slowly heals you (faster in water or rain - it's being watered),
 *  - fire hurts you more,
 *  - your attacks hit harder while the sun is up.
 * Every piece adds to these; the whole set gives the full effect.
 */
@Mod.EventBusSubscriber(modid = com.xenowarriors.XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class FlowerArmorItem extends ArmorItem implements SkinLayoutArmor {
    public static final ArmorMaterial FLOWER = new ArmorMaterial() {
        @Override public int getDurabilityForType(Type type) { return new int[]{195, 225, 240, 165}[3 - type.ordinal()]; }
        @Override public int getDefenseForType(Type type) { return switch (type) { case HELMET -> 2; case CHESTPLATE -> 6; case LEGGINGS -> 5; default -> 2; }; }
        @Override public int getEnchantmentValue() { return 14; }
        @Override public SoundEvent getEquipSound() { return SoundEvents.ARMOR_EQUIP_IRON; }
        @Override public Ingredient getRepairIngredient() { return Ingredient.of(net.minecraft.world.item.Items.IRON_INGOT); }
        @Override public String getName() { return "xenowarriors:flower"; }
        @Override public float getToughness() { return 0.5F; }
        @Override public float getKnockbackResistance() { return 0F; }
    };

    public FlowerArmorItem(Type type, Properties props) { super(FLOWER, type, props); }

    public static int pieces(Player p) {
        int n = 0;
        for (EquipmentSlot s : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET})
            if (p.getItemBySlot(s).getItem() instanceof FlowerArmorItem) n++;
        return n;
    }

    private static boolean sunny(Player p) {
        Level l = p.level();
        return l.isDay() && !l.isRaining() && l.canSeeSky(p.blockPosition().above());
    }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || !p.isAlive()) return;
        int n = pieces(p);
        if (n == 0 || p.getHealth() >= p.getMaxHealth()) return;
        boolean watered = p.isInWaterOrRain();
        int every = (watered ? 50 : 160) * 4 / n;                  // full set: half a heart every 8 s, every 2.5 s when watered
        if (p.tickCount % every == 0) {
            p.heal(1F);
            if (watered) p.serverLevel().sendParticles(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER, p.getX(), p.getY() + 1.2, p.getZ(), 3, 0.3, 0.4, 0.3, 0);
        }
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent e) {
        if (e.getEntity() instanceof Player v && e.getSource().is(DamageTypeTags.IS_FIRE)) {
            int n = pieces(v);
            if (n > 0) e.setAmount(e.getAmount() * (1F + 0.125F * n));        // full set: fire hurts 50% more
        }
        if (e.getSource().getEntity() instanceof Player a && e.getSource().getDirectEntity() == a) {
            int n = pieces(a);
            if (n > 0 && sunny(a)) e.setAmount(e.getAmount() * (1F + 0.05F * n));   // full set: +20% in the sun
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Slowly heals you (faster in water or rain)").withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.literal("Hits harder while the sun is up").withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.literal("Fire hurts more").withStyle(ChatFormatting.RED));
        tooltip.add(Component.literal("(each piece adds to it)").withStyle(ChatFormatting.DARK_GRAY));
    }
}

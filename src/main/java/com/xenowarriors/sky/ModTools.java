package com.xenowarriors.sky;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.item.ModItems;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegistryObject;

/**
 * Sky Crystal and Corrupted tools. Sky tools: netherite-tough, hit flying things harder (+35%) and dig faster up high
 * (above y 150). Corrupted tools: diamond-level, their weapons drain life (heal 10% of the damage) and sometimes wither,
 * and they dig faster deep underground (below y 0).
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ModTools {
    public static final Tier SKY = tier(4, 1900, 10F, 3.5F, 18, ModItems.SKY_CRYSTAL);
    public static final Tier CORRUPTED = tier(3, 1150, 8.5F, 3F, 12, ModItems.CORRUPTED_SHARD);

    private static Tier tier(int level, int uses, float speed, float dmg, int ench, RegistryObject<Item> repair) {
        return new Tier() {
            @Override public int getUses() { return uses; }
            @Override public float getSpeed() { return speed; }
            @Override public float getAttackDamageBonus() { return dmg; }
            @Override public int getLevel() { return level; }
            @Override public int getEnchantmentValue() { return ench; }
            @Override public Ingredient getRepairIngredient() { return Ingredient.of(repair.get()); }
        };
    }

    public static final RegistryObject<Item> SKY_SWORD = ModItems.ITEMS.register("sky_sword", () -> new SwordItem(SKY, 3, -2.3F, new Item.Properties()));
    public static final RegistryObject<Item> SKY_PICKAXE = ModItems.ITEMS.register("sky_pickaxe", () -> new PickaxeItem(SKY, 1, -2.8F, new Item.Properties()));
    public static final RegistryObject<Item> SKY_AXE = ModItems.ITEMS.register("sky_axe", () -> new AxeItem(SKY, 5F, -3F, new Item.Properties()));
    public static final RegistryObject<Item> SKY_SHOVEL = ModItems.ITEMS.register("sky_shovel", () -> new ShovelItem(SKY, 1.5F, -3F, new Item.Properties()));
    public static final RegistryObject<Item> SKY_HOE = ModItems.ITEMS.register("sky_hoe", () -> new HoeItem(SKY, -4, 0F, new Item.Properties()));
    public static final RegistryObject<Item> CORRUPTED_SWORD = ModItems.ITEMS.register("corrupted_sword", () -> new SwordItem(CORRUPTED, 3, -2.4F, new Item.Properties()));
    public static final RegistryObject<Item> CORRUPTED_PICKAXE = ModItems.ITEMS.register("corrupted_pickaxe", () -> new PickaxeItem(CORRUPTED, 1, -2.8F, new Item.Properties()));
    public static final RegistryObject<Item> CORRUPTED_AXE = ModItems.ITEMS.register("corrupted_axe", () -> new AxeItem(CORRUPTED, 5.5F, -3.05F, new Item.Properties()));
    public static final RegistryObject<Item> CORRUPTED_SHOVEL = ModItems.ITEMS.register("corrupted_shovel", () -> new ShovelItem(CORRUPTED, 1.5F, -3F, new Item.Properties()));
    public static final RegistryObject<Item> CORRUPTED_HOE = ModItems.ITEMS.register("corrupted_hoe", () -> new HoeItem(CORRUPTED, -3, 0F, new Item.Properties()));

    private ModTools() {}

    public static void init() {}

    private static boolean is(ItemStack s, Tier t) {
        return s.getItem() instanceof TieredItem ti && ti.getTier() == t;
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (!(event.getSource().getEntity() instanceof Player p) || event.getSource().getDirectEntity() != p) return;
        ItemStack held = p.getMainHandItem();
        LivingEntity t = event.getEntity();
        if (is(held, SKY) && !t.onGround() && !t.isInWater()) {
            event.setAmount(event.getAmount() * 1.35F);
            if (t.level() instanceof ServerLevel s) s.sendParticles(ParticleTypes.END_ROD, t.getX(), t.getY() + t.getBbHeight() * 0.6, t.getZ(), 5, 0.3, 0.3, 0.3, 0.05);
        } else if (is(held, CORRUPTED)) {
            p.heal(event.getAmount() * 0.1F);
            if (p.getRandom().nextFloat() < 0.2F) t.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 0), p);
            if (t.level() instanceof ServerLevel s) s.sendParticles(ParticleTypes.WITCH, t.getX(), t.getY() + t.getBbHeight() * 0.6, t.getZ(), 5, 0.3, 0.3, 0.3, 0.05);
        }
    }

    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        ItemStack held = event.getEntity().getMainHandItem();
        double y = event.getEntity().getY();
        if ((is(held, SKY) && y > 150) || (is(held, CORRUPTED) && y < 0)) event.setNewSpeed(event.getNewSpeed() * 1.4F);
    }
}

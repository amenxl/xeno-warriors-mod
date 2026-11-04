package com.xenowarriors.bot;

import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;

import java.util.List;

/**
 * Bots drink potions from their own bag when it makes sense: healing or regeneration when they're hurt, Karma
 * potions when they're low on Karma for skills, fire resistance when they're burning, water breathing when they're
 * running out of air, strength against something tough. A potion takes a moment to drink and leaves a glass bottle.
 */
public final class BotPotions {
    private BotPotions() {}

    static final int DRINK_TICKS = 24;

    /** What it last drank (for the tests). */
    public static String last(PartyBotEntity b) { return b.lastDrink; }

    /** Every tick from the bot's aiStep: carry on drinking, or pick a potion to drink. */
    static void tick(PartyBotEntity b) {
        if (b.drinkTicks >= 0) { drinking(b); return; }
        if (b.eating() || b.tickCount < b.drinkReady || b.tickCount % 5 != 0) return;
        int slot = choose(b);
        if (slot < 0) return;
        b.drinkSlot = slot;
        b.drinkTicks = DRINK_TICKS;
        com.xenowarriors.anim.Poses.play(b, com.xenowarriors.anim.Poses.EL_RAISE, DRINK_TICKS);
    }

    private static void drinking(PartyBotEntity b) {
        ItemStack pot = b.drinkSlot >= 0 && b.drinkSlot < b.inventory.getContainerSize() ? b.inventory.getItem(b.drinkSlot) : ItemStack.EMPTY;
        if (!drinkable(pot)) { b.drinkTicks = -1; b.drinkSlot = -1; return; }
        if (b.drinkTicks % 4 == 0) {
            ((ServerLevel) b.level()).sendParticles(new ItemParticleOption(ParticleTypes.ITEM, pot), b.getX(), b.getY() + 1.4, b.getZ(), 3, 0.12, 0.08, 0.12, 0.03);
            b.level().playSound(null, b.blockPosition(), SoundEvents.GENERIC_DRINK, SoundSource.NEUTRAL, 0.6F, 0.9F + b.getRandom().nextFloat() * 0.2F);
        }
        if (--b.drinkTicks >= 0) return;
        drink(b, pot);
        pot.shrink(1);
        if (pot.isEmpty()) b.inventory.setItem(b.drinkSlot, new ItemStack(Items.GLASS_BOTTLE));
        else { ItemStack left = b.inventory.addItem(new ItemStack(Items.GLASS_BOTTLE)); if (!left.isEmpty()) b.spawnAtLocation(left); }
        b.drinkSlot = -1;
        b.drinkReady = b.tickCount + 40;
    }

    /** The potion's effects on the bot (or its Karma back). */
    private static void drink(PartyBotEntity b, ItemStack pot) {
        b.drinks++;
        if (pot.getItem() instanceof com.xenowarriors.item.KarmaPotionItem) {
            b.karma = Math.min(100F, b.karma + com.xenowarriors.item.KarmaPotionItem.RESTORE);
            b.level().playSound(null, b.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 1F, 1.3F);
            ((ServerLevel) b.level()).sendParticles(ParticleTypes.ENCHANT, b.getX(), b.getY() + 1, b.getZ(), 20, 0.4, 0.6, 0.4, 0.4);
            b.lastDrink = "karma_potion";
            return;
        }
        StringBuilder what = new StringBuilder();
        for (MobEffectInstance e : PotionUtils.getMobEffects(pot)) {
            if (e.getEffect().isInstantenous()) e.getEffect().applyInstantenousEffect(b, b, b, e.getAmplifier(), 1.0);
            else b.addEffect(new MobEffectInstance(e));
            what.append(net.minecraftforge.registries.ForgeRegistries.MOB_EFFECTS.getKey(e.getEffect()).getPath()).append(' ');
        }
        b.lastDrink = what.toString().trim();
    }

    /** A potion it can drink (not splash or lingering), or a Karma potion. */
    static boolean drinkable(ItemStack s) {
        return s.is(Items.POTION) || s.getItem() instanceof com.xenowarriors.item.KarmaPotionItem;
    }

    /** Only good effects (no poison, harming, weakness...). */
    private static boolean wholesome(ItemStack s) {
        List<MobEffectInstance> l = PotionUtils.getMobEffects(s);
        if (l.isEmpty()) return false;
        for (MobEffectInstance e : l) if (!e.getEffect().isBeneficial()) return false;
        return true;
    }

    private static boolean has(ItemStack s, MobEffect eff) {
        for (MobEffectInstance e : PotionUtils.getMobEffects(s)) if (e.getEffect() == eff) return true;
        return false;
    }

    private static int find(PartyBotEntity b, MobEffect eff) {
        for (int i = 0; i < b.inventory.getContainerSize(); i++) {
            ItemStack s = b.inventory.getItem(i);
            if (s.is(Items.POTION) && wholesome(s) && has(s, eff)) return i;
        }
        return -1;
    }

    private static int findKarma(PartyBotEntity b) {
        for (int i = 0; i < b.inventory.getContainerSize(); i++) if (b.inventory.getItem(i).getItem() instanceof com.xenowarriors.item.KarmaPotionItem) return i;
        return -1;
    }

    /** The bag slot of the potion it needs most right now, or -1. */
    static int choose(PartyBotEntity b) {
        if (b.downed()) return -1;
        int s;
        if ((b.isOnFire() || b.isInLava()) && !b.hasEffect(MobEffects.FIRE_RESISTANCE) && (s = find(b, MobEffects.FIRE_RESISTANCE)) >= 0) return s;
        if (b.isUnderWater() && b.getAirSupply() < b.getMaxAirSupply() / 3 && !b.hasEffect(MobEffects.WATER_BREATHING) && (s = find(b, MobEffects.WATER_BREATHING)) >= 0) return s;
        float hp = b.getHealth() / b.getMaxHealth();
        if (hp < 0.35F && (s = find(b, MobEffects.HEAL)) >= 0) return s;
        if (hp < 0.5F && !b.hasEffect(MobEffects.REGENERATION) && (s = find(b, MobEffects.REGENERATION)) >= 0) return s;
        if (hp < 0.5F && (s = find(b, MobEffects.HEAL)) >= 0) return s;
        boolean fighting = b.getTarget() != null && b.getTarget().isAlive();
        if ((b.karma < 15F || (fighting && b.karma < 30F)) && (s = findKarma(b)) >= 0) return s;
        if (fighting && !b.sparring() && PartyBots.strong(b, b.getTarget()) && !b.hasEffect(MobEffects.DAMAGE_BOOST) && (s = find(b, MobEffects.DAMAGE_BOOST)) >= 0) return s;
        return -1;
    }
}

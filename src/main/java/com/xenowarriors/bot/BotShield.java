package com.xenowarriors.bot;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.Vec3;

/**
 * Bots and shields: the best shield it has goes on its arm (durability left, how tough it is, Unbreaking and Mending -
 * modded shields count, anything that can block), and it raises it when something's coming: an arrow or other
 * projectile flying at it, a skeleton drawing on it, a creeper about to blow, or an enemy right on top of it swinging
 * (in short guards between its own hits). It wears like a player's shield.
 */
public final class BotShield {
    private BotShield() {}

    /** How good a shield is (0: not one, or nearly broken). */
    public static float score(ItemStack s) {
        if (!BotErrands.isShield(s)) return 0F;
        float left = s.isDamageableItem() ? s.getMaxDamage() - s.getDamageValue() : 2000F;
        if (s.isDamageableItem() && left < s.getMaxDamage() * 0.05F && left < 20) return 0.5F;   // (about to break: only if there's nothing else)
        return 10F + left * 0.05F + s.getEnchantmentLevel(Enchantments.UNBREAKING) * 15F + s.getEnchantmentLevel(Enchantments.MENDING) * 25F
                + Math.min(s.getMaxDamage(), 3000) * 0.01F;
    }

    /** The best shield in its bag onto its arm (the old one into the bag). Leaves the offhand alone if you put something else there. */
    public static void pickBest(PartyBotEntity b) {
        if (b.downed() || b.isUsingItem() || b.tickCount - b.handPicked < 20 * 20) return;
        ItemStack off = b.getOffhandItem();
        if (!off.isEmpty() && !BotErrands.isShield(off)) return;
        float best = score(off); int pick = -1;
        for (int i = 0; i < b.inventory.getContainerSize(); i++) { float sc = score(b.inventory.getItem(i)); if (sc > best + 0.01F) { best = sc; pick = i; } }
        if (pick < 0) return;
        ItemStack old = off.copy();
        b.setItemSlot(EquipmentSlot.OFFHAND, b.inventory.getItem(pick).copy());
        b.inventory.setItem(pick, old);
        b.shieldSwaps++;
        PartyBots.testLog(b, "shield on: " + BotErrands.id(b.getOffhandItem()) + " score " + String.format(java.util.Locale.ROOT, "%.0f", best) + (old.isEmpty() ? "" : " (was " + BotErrands.id(old) + ")"));
    }

    /** What's coming at it that a shield would stop (null: nothing). */
    static Vec3 threat(PartyBotEntity b) {
        // projectiles on their way to it
        for (Projectile p : b.level().getEntitiesOfClass(Projectile.class, b.getBoundingBox().inflate(12), p -> p.isAlive() && !(p instanceof AbstractArrow a && a.pickup == AbstractArrow.Pickup.CREATIVE_ONLY && p.getOwner() instanceof PartyBotEntity))) {
            if (p.getOwner() != null && PartyBots.friendly(b, p.getOwner())) continue;
            Vec3 v = p.getDeltaMovement();
            if (v.lengthSqr() < 0.04) continue;
            Vec3 to = b.getBoundingBox().getCenter().subtract(p.position());
            if (to.normalize().dot(v.normalize()) > 0.9) return p.position();
        }
        LivingEntity t = b.getTarget();
        for (Mob m : b.level().getEntitiesOfClass(Mob.class, b.getBoundingBox().inflate(16), m -> m instanceof Enemy && m.isAlive() && !PartyBots.friendly(b, m))) {
            double d2 = m.distanceToSqr(b);
            if (m instanceof Creeper c && c.getSwellDir() > 0 && d2 < 5 * 5) return m.position();                // (about to blow)
            if (m.getTarget() != b) continue;
            if (m.isUsingItem() && d2 < 24 * 24 && b.hasLineOfSight(m)) return m.getEyePosition();               // (a bow drawn on it)
            if (d2 < 3.2 * 3.2 && m.isAggressive() && (b.tickCount % 24 < 9 || m == t && b.tickCount - b.lastHurtAt < 15)) return m.getEyePosition();   // (right there swinging: a guard between its own hits)
        }
        return null;
    }

    /** Every tick: up when something's coming, down once it's passed. */
    static void tick(PartyBotEntity b) {
        if (b.tickCount % 40 == 13) pickBest(b);
        ItemStack off = b.getOffhandItem();
        boolean blocking = b.isUsingItem() && b.getUsedItemHand() == InteractionHand.OFF_HAND;
        boolean can = BotErrands.isShield(off) && !b.downed() && !b.rangedActive && !b.eating() && !b.drinking() && !b.sparring() && b.reviving == null;
        if (!can) { if (blocking) b.stopUsingItem(); b.guardTicks = 0; return; }
        Vec3 from = b.tickCount % 2 == 0 || blocking ? threat(b) : null;
        if (from != null) {
            if (!blocking) { b.startUsingItem(InteractionHand.OFF_HAND); b.guards++; }
            b.guardTicks = 12;
            Vec3 d = from.subtract(b.position());
            float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));            // (face it: a shield only stops what's in front)
            b.setYRot(yaw); b.yBodyRot = yaw; b.yHeadRot = yaw;
            b.getLookControl().setLookAt(from.x, from.y, from.z);
        } else if (blocking && --b.guardTicks <= 0) b.stopUsingItem();
    }
}

package com.xenowarriors.bot;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Bows, crossbows and (with TACZ) guns: a bot that carries one uses it against enemies that are far off or out of
 * reach (up a pillar, flying), or when it hits harder than its best melee weapon. Bots never run out: a bow needs no
 * arrows (it shoots the kind in its bag if it has some, without using them up) and a gun's magazine stays full.
 * Its arrows can't be picked up, so they're not a free arrow farm.
 */
public final class BotRanged extends Goal {
    /** Kinds of ranged weapon. */
    static final int NONE = 0, BOW = 1, CROSSBOW = 2, GUN = 3;
    private final PartyBotEntity b;
    private int charge, cooldown, burst, slot, kind, drawn;

    BotRanged(PartyBotEntity b) { this.b = b; setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); }

    static int kindOf(ItemStack s) {
        if (s.isEmpty()) return NONE;
        if (com.xenowarriors.compat.TaczGuns.isGun(s)) return GUN;
        if (s.getItem() instanceof CrossbowItem) return CROSSBOW;
        if (s.getItem() instanceof BowItem) return BOW;
        return NONE;
    }

    /** Roughly how much damage a second it does with this (to weigh against its melee). */
    static float score(ItemStack s) {
        return switch (kindOf(s)) {
            case BOW -> 6F + 1.5F * s.getEnchantmentLevel(Enchantments.POWER_ARROWS);
            case CROSSBOW -> 7F + 1.5F * s.getEnchantmentLevel(Enchantments.QUICK_CHARGE) + s.getEnchantmentLevel(Enchantments.PIERCING);
            case GUN -> 25F;
            default -> 0F;
        };
    }

    /** Its best melee in damage a second (best weapon it has, plus its own strength). */
    static float meleeScore(PartyBotEntity b) {
        float best = BotGear.weaponScore(b, b.getMainHandItem());
        for (int i = 0; i < b.inventory.getContainerSize(); i++) best = Math.max(best, BotGear.weaponScore(b, b.inventory.getItem(i)));
        var a = b.getAttribute(Attributes.ATTACK_DAMAGE);
        return best + (a == null ? 4F : (float) a.getBaseValue()) * 1.6F;
    }

    /** Its best ranged weapon: -1 = the one in its hand, a bag slot, or -2 for none. */
    static int bestRanged(PartyBotEntity b) {
        int pick = -2; float best = 0F;
        if (score(b.getMainHandItem()) > 0F) { pick = -1; best = score(b.getMainHandItem()); }
        for (int i = 0; i < b.inventory.getContainerSize(); i++) {
            float sc = score(b.inventory.getItem(i));
            if (sc > best) { best = sc; pick = i; }
        }
        return pick;
    }

    private ItemStack at(int s) { return s == -1 ? b.getMainHandItem() : s >= 0 ? b.inventory.getItem(s) : ItemStack.EMPTY; }

    /** Worth shooting at: far off, out of reach, or the ranged weapon beats its melee anyway. */
    private boolean wanted(double slack) {
        LivingEntity t = b.getTarget();
        if (t == null || !t.isAlive() || b.downed() || b.isPassenger() || b.eating() || b.drinking()) return false;
        slot = bestRanged(b);
        if (slot == -2) return false;
        double d = b.distanceTo(t);
        if (d > 40) return false;
        if (b.targetUnreachable() || d > 8 - slack) return true;
        return d > 3 && score(at(slot)) > meleeScore(b);
    }

    @Override public boolean canUse() { return wanted(0); }
    @Override public boolean canContinueToUse() { return wanted(2); }
    @Override public boolean requiresUpdateEveryTick() { return true; }

    @Override public void start() {
        charge = 0; cooldown = 0; burst = 0; drawn = 0;
        b.rangedActive = true;
        equip();
    }

    @Override public void stop() {
        b.rangedActive = false;
        if (b.isUsingItem()) b.stopUsingItem();
        b.getNavigation().stop();
    }

    /** Ranged weapon into its hand (the melee one back in the bag). */
    private void equip() {
        if (slot >= 0) {
            ItemStack hand = b.getMainHandItem().copy();
            b.setItemSlot(EquipmentSlot.MAINHAND, b.inventory.getItem(slot).copy());
            b.inventory.setItem(slot, hand);
            slot = -1;
        }
        kind = kindOf(b.getMainHandItem());
        if (kind == GUN) { com.xenowarriors.compat.TaczGuns.draw(b); drawn = 12; }
    }

    @Override public void tick() {
        LivingEntity t = b.getTarget();
        if (t == null) return;
        if (slot >= 0 || kindOf(b.getMainHandItem()) == NONE) equip();
        if (kind == NONE) return;
        b.getLookControl().setLookAt(t, 30F, 30F);
        double d = b.distanceTo(t);
        boolean see = b.getSensing().hasLineOfSight(t);
        // keep a good distance: close in when it can't see them or they're far, back off when they're right there
        if (!see || d > 18) { if (b.tickCount % 10 == 0) b.getNavigation().moveTo(t, 1.15); }
        else if (d < 4.5 && !b.targetUnreachable()) {
            Vec3 away = b.position().add(b.position().subtract(t.position()).normalize().scale(4));
            if (b.tickCount % 10 == 0 && BotMoves.dropAt(b.level(), away.x, (int) Math.floor(b.getY()), away.z) < BotMoves.CLIFF) b.getNavigation().moveTo(away.x, away.y, away.z, 1.15);
        } else b.getNavigation().stop();
        if (cooldown > 0) { cooldown--; return; }
        if (!see) { if (b.isUsingItem()) b.stopUsingItem(); charge = 0; return; }
        if (kind == GUN) { gun(t); return; }
        if (!b.isUsingItem()) b.startUsingItem(InteractionHand.MAIN_HAND);       // (draws the bow / loads the crossbow, as players do)
        int need = kind == CROSSBOW ? Math.max(10, 25 - 5 * b.getMainHandItem().getEnchantmentLevel(Enchantments.QUICK_CHARGE)) : 20;
        if (++charge < need) return;
        b.stopUsingItem();
        shootArrow(t);
        charge = 0;
        cooldown = kind == CROSSBOW ? 8 : 6;
    }

    private void aim(LivingEntity t) {
        Vec3 d = t.getBoundingBox().getCenter().subtract(b.getEyePosition());
        float yaw = (float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90F);
        float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
        b.setYRot(yaw); b.yBodyRot = yaw; b.yHeadRot = yaw; b.setXRot(pitch);
    }

    /** One arrow, as hard as a fully drawn player shot. Nothing is used up. */
    private void shootArrow(LivingEntity t) {
        ItemStack ammo = new ItemStack(Items.ARROW);
        for (int i = 0; i < b.inventory.getContainerSize(); i++)                 // (tipped or spectral arrows in its bag: that kind - none used)
            if (b.inventory.getItem(i).getItem() instanceof ArrowItem) { ammo = b.inventory.getItem(i).copyWithCount(1); break; }
        AbstractArrow arrow = ProjectileUtil.getMobArrow(b, ammo, 1.0F);
        ItemStack weapon = b.getMainHandItem();
        if (weapon.getItem() instanceof BowItem bow) arrow = bow.customArrow(arrow);
        if (kind == CROSSBOW) { arrow.setShotFromCrossbow(true); int pierce = weapon.getEnchantmentLevel(Enchantments.PIERCING); if (pierce > 0) arrow.setPierceLevel((byte) pierce); }
        arrow.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
        float v = kind == CROSSBOW ? 3.15F : 2.8F;
        Vec3 c = t.getBoundingBox().getCenter();
        double dx = c.x - b.getX(), dz = c.z - b.getZ(), dy = c.y - arrow.getY(), h = Math.sqrt(dx * dx + dz * dz);
        arrow.shoot(dx, dy + 0.027 * h * h / (v * v), dz, v, 1.0F);                     // (lifted for the drop over that distance)
        aim(t);
        b.level().playSound(null, b.getX(), b.getY(), b.getZ(), kind == CROSSBOW ? SoundEvents.CROSSBOW_SHOOT : SoundEvents.ARROW_SHOOT, SoundSource.NEUTRAL, 1F, 1F / (b.getRandom().nextFloat() * 0.4F + 0.8F));
        b.level().addFreshEntity(arrow);
        b.shots++;
        BotGear.wear(b);
    }

    /** Bursts of fire, the magazine kept full (TaczGuns tops it up), short pauses between bursts. */
    private void gun(LivingEntity t) {
        if (drawn > 0) { drawn--; return; }
        burst = (burst + 1) % 50;
        if (burst >= 24 || b.tickCount % 3 != 0) return;
        aim(t);
        Vec3 d = t.getBoundingBox().getCenter().subtract(b.getEyePosition());
        float yaw = (float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90F), pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
        if (com.xenowarriors.compat.TaczGuns.shoot(b, pitch, yaw)) b.shots++;
    }
}

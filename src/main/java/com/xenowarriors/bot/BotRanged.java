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
 * arrows (it shoots the kind in its bag if it has some, without using them up) and a gun is reloaded for free - but it
 * does have to reload when the magazine runs dry (as long as that gun takes), and it fires in capped bursts.
 * Its arrows can't be picked up, so they're not a free arrow farm.
 * With DragonMineZ, the Blaster Rifle, Burst Cannon and Blaster Mortar too: each at its own fire rate and range, one
 * use of durability a shot (a burst, for the Burst Cannon) exactly as for a player - so the durability is its ammo and
 * the gun breaks when it runs out - and never while one of its own side is in the line of fire (or, for the Mortar,
 * close to where it lands).
 */
public final class BotRanged extends Goal {
    /** Kinds of ranged weapon. */
    static final int NONE = 0, BOW = 1, CROSSBOW = 2, GUN = 3, BLASTER = 4;
    private final PartyBotEntity b;
    private int charge, cooldown, burst, slot, kind, drawn;

    BotRanged(PartyBotEntity b) { this.b = b; setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); }

    static int kindOf(ItemStack s) {
        if (s.isEmpty()) return NONE;
        if (com.xenowarriors.compat.TaczGuns.isGun(s)) return GUN;
        if (com.xenowarriors.compat.DmzGuns.kindOf(s) != null) return BLASTER;
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
            case BLASTER -> !com.xenowarriors.compat.DmzGuns.on() ? 0F : switch (com.xenowarriors.compat.DmzGuns.kindOf(s)) {
                case RIFLE -> 18F;                                                  // (4 a shot, a shot every 4 ticks, in volleys)
                case BURST -> 14F;                                                  // (3 x 8 every 1.8 s)
                case MORTAR -> 12F;                                                 // (28 and a blast, every 5 s)
            };
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

    /** In a seat: a mod vehicle, a boat, a minecart or any mount. */
    static boolean inVehicle(PartyBotEntity b) {
        var v = b.getVehicle();
        return v != null && !(v instanceof net.minecraft.world.entity.player.Player);
    }

    private ItemStack at(int s) { return s == -1 ? b.getMainHandItem() : s >= 0 ? b.inventory.getItem(s) : ItemStack.EMPTY; }

    /** Worth shooting at: far off, out of reach, or the ranged weapon beats its melee anyway. */
    private boolean wanted(double slack) {
        LivingEntity t = b.getTarget();
        if (t == null || !t.isAlive() || b.downed() || b.eating() || b.drinking()) return false;
        if (b.isPassenger() && !inVehicle(b)) return false;                      // (being carried by someone: hands full)
        slot = bestRanged(b);
        if (slot == -2) return false;
        double d = b.distanceTo(t);
        if (d > 40) return false;
        if (inVehicle(b)) return d > 1.5;                                        // riding along: it shoots from its seat (it can't get out to swing)
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
        if (kind == GUN) { com.xenowarriors.compat.TaczGuns.draw(b); drawn = 12; if (reloading <= 0) rounds = -1; }   // (re-read: it may be another gun)
    }

    @Override public void tick() {
        LivingEntity t = b.getTarget();
        if (t == null) return;
        if (slot >= 0 || kindOf(b.getMainHandItem()) == NONE) equip();
        if (kind == NONE) return;
        b.getLookControl().setLookAt(t, 30F, 30F);
        double d = b.distanceTo(t);
        boolean see = b.getSensing().hasLineOfSight(t);
        // keep a good distance: close in when it can't see them or they're far, back off when they're right there (not from a seat: the driver drives)
        if (b.isPassenger()) b.getNavigation().stop();
        else if (!see || d > 18) { if (b.tickCount % 10 == 0) b.getNavigation().moveTo(t, 1.15); }
        else if (d < 4.5 && !b.targetUnreachable()) {
            Vec3 away = b.position().add(b.position().subtract(t.position()).normalize().scale(4));
            if (b.tickCount % 10 == 0 && BotMoves.dropAt(b.level(), away.x, (int) Math.floor(b.getY()), away.z) < BotMoves.CLIFF) b.getNavigation().moveTo(away.x, away.y, away.z, 1.15);
        } else b.getNavigation().stop();
        if (cooldown > 0) { cooldown--; return; }
        if (!see) { if (b.isUsingItem()) b.stopUsingItem(); charge = 0; return; }
        if (kind == GUN) { gun(t); return; }
        if (kind == BLASTER) { blaster(t, d); return; }
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
        if (inVehicle(b)) { b.seatShots++; if (b.seatShots % 5 == 1) PartyBots.testLog(b, "shot from its seat in " + b.getVehicle().getType().getDescription().getString() + " (" + b.seatShots + " so far)"); }
        BotGear.wear(b);
    }

    /** The gun's magazine, reload time and fire rate (from its TACZ data), read when it's drawn. */
    private int magSize = 30, reloadTicks = 50, shotGap = 6, rounds = -1, reloading, sinceShot, burstLeft;
    /** (tests) reloads done and the gap it keeps between shots. */
    int reloads;

    /**
     * Short bursts at a capped fire rate, then a breather; when the magazine runs dry it stops and reloads (as long
     * as that gun takes). It never runs out of ammo - the reload fills it up again.
     */
    private void gun(LivingEntity t) {
        ItemStack g = b.getMainHandItem();
        if (drawn > 0) {
            if (--drawn == 0 || rounds < 0) {
                int[] st = com.xenowarriors.compat.TaczGuns.stats(g);
                magSize = Math.max(1, st[0]); reloadTicks = Math.max(20, st[1]);
                shotGap = Math.max(MIN_GAP, Math.round(1200F / Math.max(60, st[2])));   // (never faster than about 3 a second)
                if (rounds < 0) rounds = Math.max(0, Math.min(magSize, com.xenowarriors.compat.TaczGuns.ammo(g)));
                if (rounds == 0) startReload(g);
                PartyBots.testLog(b, "gun drawn: magazine " + magSize + ", reload " + reloadTicks + " ticks, a shot every " + shotGap + " ticks, " + rounds + " in it");
            }
            return;
        }
        sinceShot++;
        if (reloading > 0) {
            if (--reloading == 0) {
                rounds = magSize;
                com.xenowarriors.compat.TaczGuns.setAmmo(g, magSize);
                reloads++;
                b.level().playSound(null, b.getX(), b.getY(), b.getZ(), SoundEvents.CROSSBOW_LOADING_END, SoundSource.NEUTRAL, 0.8F, 1.3F);
                PartyBots.testLog(b, "reloaded (" + reloads + " so far)");
                burstLeft = 0; sinceShot = 0;
            }
            return;
        }
        if (sinceShot < shotGap) return;
        if (burstLeft <= 0) {                                                   // a breather between bursts
            if (sinceShot < shotGap + BURST_PAUSE) return;
            burstLeft = 3 + b.getRandom().nextInt(3);
        }
        aim(t);
        Vec3 d = t.getBoundingBox().getCenter().subtract(b.getEyePosition());
        float yaw = (float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90F), pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
        if (com.xenowarriors.compat.TaczGuns.ammo(g) <= 0 && rounds > 0) com.xenowarriors.compat.TaczGuns.setAmmo(g, rounds);   // (something else emptied it)
        if (com.xenowarriors.compat.TaczGuns.shoot(b, pitch, yaw, false)) {
            b.shots++; if (inVehicle(b)) b.seatShots++;
            if (b.shots % 10 == 1) PartyBots.testLog(b, "gun shot " + b.shots + " (" + sinceShot + " ticks after the last, " + (rounds - 1) + " left)");
            sinceShot = 0; burstLeft--;
            rounds--;
            if (rounds <= 0) startReload(g);
        }
    }

    // ---------------------------------------------------------------- DragonMineZ Blaster guns

    /** (tests) blaster shots fired by bots, and times one held its fire because someone on its side was in the way. */
    public static int blasterShots, blasterHeld;
    /** (tests) the last few blaster shots: "KIND tick distance durabilityLeft". */
    public static final java.util.List<String> BLASTER_LOG = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
    private int volley;

    /** How far each one is worth firing from (the Mortar's big slow blast: not up close either - it would catch the bot). */
    static double blasterRange(com.xenowarriors.compat.DmzGuns.Kind k) { return k == com.xenowarriors.compat.DmzGuns.Kind.MORTAR ? 24 : 28; }

    private void blaster(LivingEntity t, double d) {
        ItemStack g = b.getMainHandItem();
        com.xenowarriors.compat.DmzGuns.Kind k = com.xenowarriors.compat.DmzGuns.kindOf(g);
        if (k == null || !com.xenowarriors.compat.DmzGuns.on()) return;
        if (d > blasterRange(k) || (k == com.xenowarriors.compat.DmzGuns.Kind.MORTAR && d < 5)) return;
        aim(t);
        if (!clearShot(t, k)) { blasterHeld++; cooldown = 5; return; }
        com.xenowarriors.compat.DmzGuns.fire(b, k, t);                         // (faces it and fires, the way a player's shot goes)
        b.shots++;
        if (inVehicle(b)) b.seatShots++;
        g.hurtAndBreak(1, b, e -> e.broadcastBreakEvent(EquipmentSlot.MAINHAND));   // one use a shot (a burst), like a player's
        blasterShots++;
        if (BLASTER_LOG.size() > 40) BLASTER_LOG.remove(0);
        BLASTER_LOG.add(k.name() + " t=" + b.tickCount + " d=" + String.format("%.1f", d) + " left=" + (g.isEmpty() ? 0 : g.getMaxDamage() - g.getDamageValue()));
        if (blasterShots % 5 == 1) PartyBots.testLog(b, "blaster " + k + " shot (" + blasterShots + " so far, " + (g.isEmpty() ? "broke" : (g.getMaxDamage() - g.getDamageValue()) + " uses left") + ")");
        if (k.held) {                                                           // the rifle: a shot every few ticks, in volleys
            cooldown = k.cooldown - 1;
            if (++volley >= 6) { volley = 0; cooldown = 20; }
        } else cooldown = k.cooldown - 1;                                       // (the same reload a player waits)
    }

    /** Nobody on its side between it and the target (nor, for the Mortar, standing near where it lands). */
    boolean clearShot(LivingEntity t, com.xenowarriors.compat.DmzGuns.Kind k) {
        Vec3 from = b.getEyePosition(), to = t.getBoundingBox().getCenter();
        double pad = 0.3 + k.size * 0.5;
        var level = b.level();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(from, to).inflate(pad + 0.5),
                e -> e != b && e != t && e.isAlive() && !e.isSpectator() && com.xenowarriors.combat.Allies.isAlly(b, e)
                        && !(b.isPassenger() && e.getRootVehicle() == b.getRootVehicle())))    // (riding together: its shots fly out past them)
            if (e.getBoundingBox().inflate(pad).clip(from, to).isPresent() || e.getBoundingBox().inflate(pad).contains(from)) return false;
        if (k == com.xenowarriors.compat.DmzGuns.Kind.MORTAR)
            return level.getEntitiesOfClass(LivingEntity.class, t.getBoundingBox().inflate(4),
                    e -> e != b && e != t && e.isAlive() && !e.isSpectator() && com.xenowarriors.combat.Allies.isAlly(b, e)).isEmpty();
        return true;
    }

    /** Fastest a bot fires (ticks between shots), and the pause between bursts. */
    static final int MIN_GAP = 6, BURST_PAUSE = 14;

    private void startReload(ItemStack g) {
        reloading = reloadTicks;
        rounds = 0;
        com.xenowarriors.compat.TaczGuns.reload(b);                             // (TACZ's own reload, for its animation, if it'll do one)
        b.level().playSound(null, b.getX(), b.getY(), b.getZ(), SoundEvents.ARMOR_EQUIP_IRON, SoundSource.NEUTRAL, 0.9F, 0.8F);
        PartyBots.testLog(b, "magazine empty - reloading for " + reloadTicks + " ticks");
    }
}

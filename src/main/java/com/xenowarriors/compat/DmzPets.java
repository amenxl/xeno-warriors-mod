package com.xenowarriors.compat;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.npc.KiBlastEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Fox;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/**
 * With DragonMineZ, tamed pets (Mikiko's foxes, Rhalix's wing pets, wolves, cats...) fight like Z-fighters: their
 * health and damage grow with their owner's DragonMineZ strength, and in a fight they throw Ki blasts, dash at their
 * target in an instant, and fly up after anything in the air.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DmzPets {
    private static final UUID HP = UUID.fromString("3f1c9a42-7b2e-4d6a-9c11-5e8f0a2b7d31");
    private static final UUID DMG = UUID.fromString("8a2d4e61-1c3b-4f7a-b5d2-9e0c6f4a1b82");
    private static final String KI = "xw_dmz_ki", DASH = "xw_dmz_dash", FLY = "xw_dmz_fly";
    private static Boolean loaded;

    private DmzPets() {}

    private static boolean loaded() {
        if (loaded == null) loaded = ModList.get() != null && ModList.get().isLoaded("dragonminez");
        return loaded;
    }

    private static Player owner(LivingEntity e) {
        if (e instanceof TamableAnimal t && t.isTame() && t.getOwner() instanceof Player p) return p;
        if (e instanceof Fox f && com.xenowarriors.fox.FoxPets.owner(f) != null) return com.xenowarriors.fox.FoxPets.owner(f);
        if (com.xenowarriors.wing.WingPets.ownerOf(e) != null) return com.xenowarriors.wing.WingPets.owner(e);
        return null;
    }

    @SubscribeEvent
    public static void onTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || mob.level().isClientSide || !loaded()) return;
        if (mob.tickCount % 5 != 0) return;
        Player owner = owner(mob);
        if (owner == null) return;
        CompoundTag d = mob.getPersistentData();
        if (mob.tickCount % 40 == 0) scaleStats(mob, owner);
        LivingEntity t = mob.getTarget();
        if (t == null || !t.isAlive() || t == owner) {
            land(mob, d);
            return;
        }
        double dist = mob.distanceTo(t);
        long now = mob.level().getGameTime();
        float mult = (float) DmzScaling.multiplier(owner, false);
        // Ki blast from a distance
        if (dist >= 4 && dist <= 16 && now >= d.getLong(KI) && mob.hasLineOfSight(t)) {
            d.putLong(KI, now + 60 + mob.getRandom().nextInt(50));
            Vec3 from = mob.position().add(0, mob.getBbHeight() * 0.7, 0);
            Vec3 dir = t.position().add(0, t.getBbHeight() * 0.5, 0).subtract(from);
            KiBlastEntity b = new KiBlastEntity(mob.level(), mob, dir.x, dir.y, dir.z).setDamage(4F * Math.min(6F, mult));
            b.setPos(from.x, from.y, from.z);
            mob.level().addFreshEntity(b);
            mob.playSound(SoundEvents.BLAZE_SHOOT, 0.6F, 1.8F);
        }
        // instant step: there in a blink
        if (dist > 3 && dist <= 10 && now >= d.getLong(DASH) && mob.hasLineOfSight(t)) {
            d.putLong(DASH, now + 80 + mob.getRandom().nextInt(40));
            Vec3 to = t.position().subtract(mob.position());
            Vec3 spot = mob.position().add(to.normalize().scale(dist - 1.6));
            if (mob.level() instanceof ServerLevel s) s.sendParticles(ParticleTypes.CLOUD, mob.getX(), mob.getY() + 0.5, mob.getZ(), 6, 0.2, 0.2, 0.2, 0.02);
            mob.teleportTo(spot.x, Math.max(spot.y, t.getY()), spot.z);
            mob.playSound(SoundEvents.ENDERMAN_TELEPORT, 0.4F, 1.8F);
        }
        // up after anything in the air
        boolean up = t.getY() - mob.getY() > 2.0 && !t.onGround();
        if (up) {
            if (!d.getBoolean(FLY)) { d.putBoolean(FLY, true); mob.setNoGravity(true); }
            Vec3 to = t.position().add(0, 0.3, 0).subtract(mob.position());
            mob.setDeltaMovement(mob.getDeltaMovement().lerp(to.normalize().scale(Math.min(0.45, to.length() * 0.15)), 0.4));
            mob.fallDistance = 0;
            if (mob.level() instanceof ServerLevel s)
                s.sendParticles(new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(1F, 1F, 0.85F), 0.9F),
                        mob.getX(), mob.getY() + 0.1, mob.getZ(), 2, 0.2, 0.05, 0.2, 0);
        } else land(mob, d);
    }

    private static void land(Mob mob, CompoundTag d) {
        if (!d.getBoolean(FLY)) return;
        d.remove(FLY);
        mob.setNoGravity(false);
        mob.fallDistance = 0;
    }

    /** Health and damage scaled with the owner's DragonMineZ strength. */
    private static void scaleStats(Mob mob, Player owner) {
        double m = Math.min(8.0, DmzScaling.multiplier(owner, true));
        apply(mob.getAttribute(Attributes.MAX_HEALTH), HP, "DMZ pet health", m);
        apply(mob.getAttribute(Attributes.ATTACK_DAMAGE), DMG, "DMZ pet damage", m);
        if (mob.getHealth() > mob.getMaxHealth()) mob.setHealth(mob.getMaxHealth());
    }

    private static void apply(AttributeInstance a, UUID id, String name, double m) {
        if (a == null) return;
        double amount = Math.max(0, (m - 1.0) * 0.6);          // +60% of base per point of the owner's multiplier
        AttributeModifier cur = a.getModifier(id);
        if (cur != null && Math.abs(cur.getAmount() - amount) < 0.05) return;
        if (cur != null) a.removeModifier(id);
        if (amount > 0) a.addPermanentModifier(new AttributeModifier(id, name, amount, AttributeModifier.Operation.MULTIPLY_BASE));
    }
}

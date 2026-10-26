package com.xenowarriors.ka;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.corrupted.CSoulBossEntity;
import com.xenowarriors.corrupted.CorruptedSoulEntity;
import com.xenowarriors.corrupted.CorruptedVillagerEntity;
import com.xenowarriors.npc.HumanNpcEntity;
import com.xenowarriors.npc.SkylanderEntity;
import com.xenowarriors.skill.Rooting;
import com.xenowarriors.skill.ServerScheduler;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

/** Who has an element, their aura, and their element attacks in a fight (each costs Karma). */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Elements {
    private static final String EL = "xw_element", ROLLED = "xw_el_rolled", CD = "xw_el_cd", CHARGE = "xw_el_charge", CHARGE_T = "xw_el_charge_t";

    /** Blast elements charge up first (that's when you can clash with them). */
    public static boolean blast(Element el) {
        return switch (el) { case FIRE, WATER, ICE, LIGHTNING, WIND, SOUND, LIGHT, METAL, SHADOW, CORRUPTION, CRYSTAL -> true; default -> false; };
    }

    /** Is {@code e} charging an element blast at {@code target} right now? */
    public static boolean chargingAt(LivingEntity e, LivingEntity target) {
        return e.getPersistentData().getLong(CHARGE) > e.level().getGameTime() && e.getPersistentData().getInt(CHARGE_T) == target.getId();
    }

    public static void cancelCharge(LivingEntity e) { e.getPersistentData().remove(CHARGE); }

    private Elements() {}

    public static Element of(LivingEntity e) { return Element.byId(e.getPersistentData().getString(EL)); }

    public static void give(LivingEntity e, Element el) {
        e.getPersistentData().putString(EL, el == null ? "" : el.id());
        e.getPersistentData().putBoolean(ROLLED, true);
    }

    private static boolean csoul(LivingEntity e) {
        return e instanceof CorruptedSoulEntity || e instanceof CorruptedVillagerEntity || e instanceof CSoulBossEntity;
    }

    private static void roll(Mob m) {
        CompoundTag t = m.getPersistentData();
        t.putBoolean(ROLLED, true);
        var r = m.getRandom();
        Element pick = null;
        if (m instanceof HumanNpcEntity h && !h.carriesSoul()) { if (r.nextFloat() < 0.75F) pick = Element.HUMAN[r.nextInt(Element.HUMAN.length)]; }
        else if (m instanceof SkylanderEntity) { if (r.nextFloat() < 0.4F) pick = Element.SKYLANDER[r.nextInt(Element.SKYLANDER.length)]; }
        else if (csoul(m) && r.nextFloat() < 0.9F) pick = r.nextInt(10) == 0 ? Element.VOID : Element.CSOUL[r.nextInt(Element.CSOUL.length)];
        if (pick != null) t.putString(EL, pick.id());
    }

    private static Vector3f rgb(Element el) {
        return new Vector3f((el.color >> 16 & 255) / 255F, (el.color >> 8 & 255) / 255F, (el.color & 255) / 255F);
    }

    @SubscribeEvent
    public static void onTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof Mob m) || m.level().isClientSide) return;
        CompoundTag t = m.getPersistentData();
        if (!t.getBoolean(ROLLED)) {
            if (m.tickCount > 40 && (m instanceof HumanNpcEntity || m instanceof SkylanderEntity || csoul(m))) roll(m);
            return;
        }
        Element el = of(m);
        if (el == null) return;
        ServerLevel level = (ServerLevel) m.level();
        if (m.tickCount % 12 == 0)                                                         // aura
            level.sendParticles(new DustParticleOptions(rgb(el), 0.8F), m.getX(), m.getY() + m.getBbHeight() * 0.5, m.getZ(), 2,
                    m.getBbWidth() * 0.5, m.getBbHeight() * 0.35, m.getBbWidth() * 0.5, 0);
        long now = level.getGameTime();
        if (Clashes.inClash(m)) return;
        // a charged blast goes off
        if (t.contains(CHARGE)) {
            long at = t.getLong(CHARGE);
            if (now < at) {
                Vec3 hand = m.getEyePosition().add(m.getLookAngle().scale(0.7)).subtract(0, 0.3, 0);
                level.sendParticles(new DustParticleOptions(rgb(el), 1.4F), hand.x, hand.y, hand.z, 3, 0.15, 0.15, 0.15, 0);
                return;
            }
            t.remove(CHARGE);
            if (level.getEntity(t.getInt(CHARGE_T)) instanceof LivingEntity ct && ct.isAlive() && ct.distanceTo(m) < 20) cast(m, ct, el, level);
            return;
        }
        LivingEntity target = m.getTarget();
        if (target == null || !target.isAlive() || now < t.getLong(CD) || m.distanceTo(target) > 16 || !m.hasLineOfSight(target)) return;
        if (target instanceof Player p && (p.isCreative() || p.isSpectator())) return;
        if (!EnemyKarma.has(m, el.karma)) return;
        t.putLong(CD, now + 80 + m.getRandom().nextInt(80));
        EnemyKarma.spend(m, el.karma);
        m.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        if (blast(el)) {                                     // charge it up (you can see it coming - and clash it)
            t.putLong(CHARGE, now + 16);
            com.xenowarriors.anim.Poses.play(m, com.xenowarriors.anim.Poses.EL_CHARGE, 16);
            t.putInt(CHARGE_T, target.getId());
            sound(level, m, SoundEvents.BEACON_ACTIVATE, 1.8F);
        } else cast(m, target, el, level);
    }

    private static void line(ServerLevel l, LivingEntity a, LivingEntity b, ParticleOptions p, int n) {
        Vec3 s = a.getEyePosition(), e = b.position().add(0, b.getBbHeight() * 0.5, 0);
        for (int i = 0; i <= n; i++) {
            Vec3 q = s.lerp(e, i / (double) n);
            l.sendParticles(p, q.x, q.y, q.z, 1, 0.02, 0.02, 0.02, 0);
        }
    }

    private static void hit(Mob m, LivingEntity t, float dmg) {
        t.hurt(m.damageSources().mobAttack(m), dmg * com.xenowarriors.scaling.EnemyScaling.damageMult(m));
    }

    private static void sound(ServerLevel l, LivingEntity at, SoundEvent s, float pitch) {
        l.playSound(null, at.blockPosition(), s, SoundSource.HOSTILE, 1F, pitch);
    }

    private static void cast(Mob m, LivingEntity t, Element el, ServerLevel l) {
        com.xenowarriors.anim.Poses.play(m, switch (el) {
            case FIRE, CRYSTAL, METAL, POISON -> com.xenowarriors.anim.Poses.EL_THROW;
            case EARTH -> com.xenowarriors.anim.Poses.EL_STOMP;
            case CLOUD, PLANT, VOID, SMOKE -> com.xenowarriors.anim.Poses.EL_RAISE;
            default -> com.xenowarriors.anim.Poses.EL_THRUST;
        }, 12);
        Vec3 to = t.position().subtract(m.position()).normalize();
        switch (el) {
            case FIRE -> {
                Vec3 eye = m.getEyePosition();
                Vec3 d = t.getEyePosition().subtract(eye);
                SmallFireball fb = new SmallFireball(l, m, d.x, d.y, d.z);
                fb.setPos(eye.x + to.x, eye.y - 0.2, eye.z + to.z);
                l.addFreshEntity(fb);
                sound(l, m, SoundEvents.BLAZE_SHOOT, 1.1F);
            }
            case WATER -> {
                line(l, m, t, ParticleTypes.SPLASH, 24);
                line(l, m, t, ParticleTypes.BUBBLE_POP, 12);
                hit(m, t, 4F); t.clearFire();
                t.push(to.x * 1.2, 0.3, to.z * 1.2); t.hurtMarked = true;
                sound(l, t, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, 1F);
            }
            case EARTH -> {                                  // spikes erupt where they stood, after a rumble
                Vec3 at = t.position();
                l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DIRT.defaultBlockState()), at.x, at.y + 0.1, at.z, 25, 0.6, 0.05, 0.6, 0.05);
                sound(l, t, SoundEvents.GRAVEL_BREAK, 0.6F);
                ServerScheduler.later(12, () -> {
                    l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()), at.x, at.y + 0.8, at.z, 50, 0.4, 0.9, 0.4, 0.2);
                    sound(l, t, SoundEvents.STONE_BREAK, 0.5F);
                    if (t.isAlive() && t.position().distanceTo(at) < 1.8) { hit(m, t, 6F); t.push(0, 0.75, 0); t.hurtMarked = true; }
                });
            }
            case ICE -> {
                line(l, m, t, ParticleTypes.SNOWFLAKE, 20);
                hit(m, t, 4F);
                t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2));
                t.setTicksFrozen(Math.max(t.getTicksFrozen(), 200));
                sound(l, t, SoundEvents.GLASS_BREAK, 1.6F);
            }
            case LIGHTNING -> {                              // a bolt that jumps to up to two more
                line(l, m, t, ParticleTypes.ELECTRIC_SPARK, 22);
                hit(m, t, 5F);
                LivingEntity from = t;
                int jumps = 0;
                for (LivingEntity o : l.getEntitiesOfClass(LivingEntity.class, t.getBoundingBox().inflate(4), o -> o != m && o != t && o.isAlive() && o.getClass() != m.getClass())) {
                    if (jumps++ >= 2) break;
                    line(l, from, o, ParticleTypes.ELECTRIC_SPARK, 10);
                    hit(m, o, 3F);
                    from = o;
                }
                sound(l, t, SoundEvents.LIGHTNING_BOLT_IMPACT, 1.8F);
            }
            case WIND -> {
                line(l, m, t, ParticleTypes.CLOUD, 16);
                t.push(to.x * 1.6, 0.55, to.z * 1.6); t.hurtMarked = true;
                hit(m, t, 2F);
                sound(l, m, SoundEvents.PHANTOM_FLAP, 0.7F);
            }
            case SOUND -> {
                line(l, m, t, ParticleTypes.NOTE, 10);
                l.sendParticles(ParticleTypes.SONIC_BOOM, t.getX(), t.getY() + 1, t.getZ(), 1, 0, 0, 0, 0);
                hit(m, t, 4F);
                t.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 80, 0));
                sound(l, t, SoundEvents.WARDEN_SONIC_BOOM, 1.5F);
            }
            case LIGHT -> {
                line(l, m, t, ParticleTypes.END_ROD, 24);
                l.sendParticles(ParticleTypes.FLASH, t.getX(), t.getY() + 1, t.getZ(), 1, 0, 0, 0, 0);
                hit(m, t, 4F);
                t.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0));
                sound(l, t, SoundEvents.AMETHYST_BLOCK_CHIME, 1.8F);
            }
            case CRYSTAL -> {
                for (int i = 0; i < 3; i++) ServerScheduler.later(i * 4, () -> {
                    if (!t.isAlive()) return;
                    line(l, m, t, new DustParticleOptions(rgb(el), 1F), 14);
                    hit(m, t, 2F);
                    sound(l, t, SoundEvents.AMETHYST_CLUSTER_HIT, 1.4F);
                });
                m.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 200, 0));         // crystal shield
            }
            case CLOUD -> {
                Vec3 at = t.position();
                l.sendParticles(ParticleTypes.CLOUD, at.x, at.y + 1, at.z, 60, 1.5, 0.8, 1.5, 0.01);
                for (LivingEntity o : l.getEntitiesOfClass(LivingEntity.class, t.getBoundingBox().inflate(3), o -> o != m && o.getClass() != m.getClass())) {
                    o.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0));
                    o.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 0));
                }
                sound(l, t, SoundEvents.WEATHER_RAIN, 1F);
            }
            case PLANT -> {                                  // vines hold them, and nearby allies get patched up
                Vec3 anchor = t.position();
                l.sendParticles(ParticleTypes.HAPPY_VILLAGER, anchor.x, anchor.y + 0.5, anchor.z, 20, 0.5, 0.5, 0.5, 0);
                for (int i = 0; i < 40; i++) ServerScheduler.later(i, () -> { if (t.isAlive()) Rooting.hold(t, anchor); });
                hit(m, t, 2F);
                for (LivingEntity a : l.getEntitiesOfClass(LivingEntity.class, m.getBoundingBox().inflate(6), a -> a.getClass() == m.getClass())) a.heal(4F);
                sound(l, t, SoundEvents.CAVE_VINES_PLACE, 0.8F);
            }
            case METAL -> {
                line(l, m, t, new DustParticleOptions(rgb(el), 1.1F), 18);
                hit(m, t, 6F);
                m.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 100, 0));
                sound(l, t, SoundEvents.ANVIL_LAND, 1.6F);
            }
            case SMOKE -> {                                  // vanish in smoke, reappear behind them
                l.sendParticles(ParticleTypes.LARGE_SMOKE, m.getX(), m.getY() + 1, m.getZ(), 40, 0.6, 0.8, 0.6, 0.02);
                m.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 50, 0));
                Vec3 behind = t.position().subtract(t.getLookAngle().multiply(1.6, 0, 1.6));
                if (l.noCollision(m, m.getBoundingBox().move(behind.subtract(m.position())))) m.teleportTo(behind.x, t.getY(), behind.z);
                sound(l, m, SoundEvents.FIRE_EXTINGUISH, 0.7F);
            }
            case SHADOW -> {
                line(l, m, t, ParticleTypes.SQUID_INK, 18);
                hit(m, t, 5F);
                t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 50, 1));
                if (m.getRandom().nextInt(3) == 0) {          // sinks into the shadows and comes up behind
                    Vec3 behind = t.position().subtract(t.getLookAngle().multiply(1.4, 0, 1.4));
                    if (l.noCollision(m, m.getBoundingBox().move(behind.subtract(m.position())))) m.teleportTo(behind.x, t.getY(), behind.z);
                }
                sound(l, t, SoundEvents.SCULK_SHRIEKER_SHRIEK, 1.5F);
            }
            case CORRUPTION -> {
                line(l, m, t, new DustParticleOptions(rgb(el), 1.2F), 20);
                hit(m, t, 4F);
                t.addEffect(new MobEffectInstance(MobEffects.WITHER, 40, 0));
                if (t instanceof ServerPlayer sp) com.xenowarriors.karma.Karma.add(sp, -15F);      // drains Karma
                else if (EnemyKarma.usesKarma(t)) EnemyKarma.spend(t, 15F);
                sound(l, t, SoundEvents.WITHER_SHOOT, 1.4F);
            }
            case POISON -> {
                AreaEffectCloud c = new AreaEffectCloud(l, t.getX(), t.getY(), t.getZ());
                c.setOwner(m);
                c.setRadius(2.5F);
                c.setDuration(80);
                c.setRadiusPerTick(-0.01F);
                c.addEffect(new MobEffectInstance(MobEffects.POISON, 80, 0));
                l.addFreshEntity(c);
                sound(l, t, SoundEvents.SPLASH_POTION_BREAK, 0.8F);
            }
            case VOID -> {                                   // a little black hole that drags them in
                Vec3 hole = m.position().add(to.scale(Math.max(1.5, m.distanceTo(t) * 0.5))).add(0, 1, 0);
                for (int i = 0; i < 30; i++) {
                    final int k = i;
                    ServerScheduler.later(i, () -> {
                        l.sendParticles(ParticleTypes.REVERSE_PORTAL, hole.x, hole.y, hole.z, 6, 0.3, 0.3, 0.3, 0.3);
                        if (!t.isAlive() || t.position().distanceTo(hole) > 8) return;
                        Vec3 pull = hole.subtract(t.position()).normalize().scale(0.18);
                        t.push(pull.x, pull.y * 0.5, pull.z);
                        t.hurtMarked = true;
                        if (k % 10 == 0) hit(m, t, 3F);
                    });
                }
                sound(l, t, SoundEvents.END_PORTAL_SPAWN, 1.8F);
            }
        }
    }
}

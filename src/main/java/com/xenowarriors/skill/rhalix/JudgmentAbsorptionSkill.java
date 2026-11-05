package com.xenowarriors.skill.rhalix;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.combat.SkillContext;
import com.xenowarriors.damage.ModDamageTypes;
import com.xenowarriors.skill.Skill;
import com.xenowarriors.skill.SkillTargeting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Rhalix skill (Character Level 30) - Judgment Absorption. He opens a point of crushing gravity at a spot (the enemy
 * he's aiming at or locked onto, else where he's looking): for 4 seconds it sucks in every enemy within
 * {@link #RADIUS} blocks, along with loose items, XP and enemy arrows / blasts, holding them in a knot in the middle
 * and squeezing them, then it collapses. Allies (party, pets, bots, souls) are never pulled. It's also the first half
 * of the Blazing Judgment team ultimate with Mikiko.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class JudgmentAbsorptionSkill implements Skill {
    public static final String ID = "judgment_absorption";
    public static final int UNLOCK_LEVEL = 30, KARMA_COST = 40, COOLDOWN = 22 * 20, DURATION = 80;
    public static final double RANGE = 26, RADIUS = 12, CORE = 1.6;
    public static final float CRUSH_DAMAGE = 2.5F, COLLAPSE_DAMAGE = 7F;
    public static final DustParticleOptions DARK = new DustParticleOptions(new Vector3f(0.30F, 0.08F, 0.45F), 1.6F);
    public static final DustParticleOptions VIOLET = new DustParticleOptions(new Vector3f(0.65F, 0.35F, 1.0F), 1.0F);

    /** (tests) the last point opened, and how many things it has pulled. */
    public static Vec3 lastPoint;
    public static int lastPulled;

    @Override public String getId() { return ID; }
    @Override public int getCooldownTicks() { return COOLDOWN; }
    @Override public int getKarmaCost() { return KARMA_COST; }

    @Override
    public boolean use(ServerPlayer player) {
        Vec3 at = spot(player, RANGE);
        open((ServerLevel) player.level(), player, at, DURATION, true);
        player.displayClientMessage(Component.literal("Judgment Absorption!").withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE), true);
        return true;
    }

    /** Where the point opens: the enemy aimed at / locked onto (its middle), else the block looked at (a little above it), else straight ahead. */
    public static Vec3 spot(ServerPlayer p, double range) {
        LivingEntity t = SkillTargeting.aimed(p, range, 10);
        if (t != null) return t.position().add(0, Math.min(1.2, t.getBbHeight() * 0.5), 0);
        Vec3 eye = p.getEyePosition(), end = eye.add(p.getViewVector(1F).scale(range * 0.6));
        var hit = p.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        Vec3 v = hit.getType() == HitResult.Type.MISS ? end : hit.getLocation().subtract(p.getViewVector(1F).scale(0.5));
        return v.add(0, 1.2, 0);
    }

    // ---------------------------------------------------------------- the gravity point (ticked on the server)

    private static final class Point {
        final ServerLevel level; final Entity caster; final Vec3 c; final int duration; final boolean collapse;
        int age;
        Point(ServerLevel level, Entity caster, Vec3 c, int duration, boolean collapse) { this.level = level; this.caster = caster; this.c = c; this.duration = duration; this.collapse = collapse; }
    }

    private static final List<Point> POINTS = new ArrayList<>();

    /** Opens a point at {@code c} for {@code duration} ticks (collapse: a crushing finish of its own - the team ultimate does its own finish). */
    public static void open(ServerLevel level, Entity caster, Vec3 c, int duration, boolean collapse) {
        POINTS.add(new Point(level, caster, c, duration, collapse));
        lastPoint = c;
        lastPulled = 0;
        level.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.6F, 0.5F);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 1.4F, 0.6F);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x, c.y, c.z, 80, 0.4, 0.4, 0.4, 0.6);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || POINTS.isEmpty()) return;
        for (Iterator<Point> it = POINTS.iterator(); it.hasNext(); ) {
            Point p = it.next();
            p.age++;
            if (p.caster == null || p.age > p.duration) {
                if (p.collapse && p.caster != null) SkillContext.callSkill(p.caster, ID, () -> { collapse(p.level, p.caster, p.c, COLLAPSE_DAMAGE); return true; });
                it.remove();
                continue;
            }
            final Point pt = p;
            SkillContext.callSkill(p.caster, ID, () -> { tickPull(pt.level, pt.caster, pt.c, RADIUS, pt.age, CRUSH_DAMAGE, e -> true); return true; });
        }
    }

    /** (tests / server stop) nothing open. */
    public static void clear() { POINTS.clear(); }
    public static int open() { return POINTS.size(); }

    /**
     * One tick of the pull at {@code c}: enemies of {@code caster} within {@code radius} are dragged in and held in the
     * core (bosses feel it less), loose items / XP and enemies' projectiles drift in too, and the swirl is drawn.
     * {@code crush} damage every 10 ticks to whoever is held in the core (0 = none). Returns the enemies it holds.
     */
    public static List<LivingEntity> tickPull(ServerLevel level, Entity caster, Vec3 c, double radius, int age, float crush, java.util.function.Predicate<LivingEntity> also) {
        AABB box = new AABB(c, c).inflate(radius);
        List<LivingEntity> held = new ArrayList<>();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> SkillTargeting.canTarget(caster, e) && also.test(e))) {
            Vec3 mid = e.position().add(0, e.getBbHeight() * 0.5, 0);
            Vec3 d = c.subtract(mid);
            double dist = d.length();
            if (dist > radius) continue;
            boolean boss = e.getPersistentData().getBoolean("xw_boss") || e.getMaxHealth() > 150;
            double k = boss ? 0.35 : 1.0;
            if (dist < CORE) {
                e.setDeltaMovement(e.getDeltaMovement().scale(0.25).add(d.scale(0.25 * k)));   // held in the knot
                held.add(e);
            } else {
                double pull = (0.32 + 0.5 * (1 - dist / radius)) * k;
                Vec3 in = d.scale(1 / dist);
                Vec3 swirl = new Vec3(-in.z, 0, in.x).scale(0.12 * k);
                Vec3 v = e.getDeltaMovement().scale(0.5).add(in.scale(Math.min(pull, dist * 0.45))).add(swirl);
                e.setDeltaMovement(v);
            }
            e.hurtMarked = true;
            e.fallDistance = 0;
            if (e instanceof net.minecraft.world.entity.Mob m) m.getNavigation().stop();
            lastPulled++;
        }
        for (Entity e : level.getEntities((Entity) null, box, e -> e.isAlive() && (e instanceof ItemEntity || e instanceof ExperienceOrb
                || e instanceof Projectile pr && !com.xenowarriors.combat.Allies.isAlly(caster, pr.getOwner() == null ? pr : pr.getOwner())))) {
            Vec3 d = c.subtract(e.position());
            double dist = d.length();
            if (dist > radius || dist < 0.4) { if (dist < 0.4) e.setDeltaMovement(e.getDeltaMovement().scale(0.3)); continue; }
            e.setDeltaMovement(e.getDeltaMovement().scale(0.4).add(d.scale(1 / dist).scale(Math.min(0.7, 0.25 + (radius - dist) * 0.04))));
            e.hasImpulse = true;
            e.hurtMarked = true;
        }
        if (crush > 0 && age % 10 == 0) for (LivingEntity e : held) {
            e.invulnerableTime = 0;
            e.hurt(ModDamageTypes.source(level, ModDamageTypes.GRAVITY_COMPRESS, caster, caster), crush);
        }
        // the look: a dark knot, a violet shell, the world streaming into it
        level.sendParticles(DARK, c.x, c.y, c.z, 6, 0.35, 0.35, 0.35, 0);
        level.sendParticles(ParticleTypes.SQUID_INK, c.x, c.y, c.z, 2, 0.2, 0.2, 0.2, 0.01);
        for (int i = 0; i < 6; i++) {
            double a = age * 0.35 + i * Math.PI / 3, r = 1.2 + 0.3 * Math.sin(age * 0.2 + i);
            level.sendParticles(VIOLET, c.x + Math.cos(a) * r, c.y + Math.sin(a * 0.7) * 0.6, c.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
        }
        for (int i = 0; i < 5; i++) {
            double a = level.random.nextDouble() * Math.PI * 2, y = (level.random.nextDouble() - 0.5) * radius * 0.6, r = radius * (0.5 + level.random.nextDouble() * 0.5);
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x + Math.cos(a) * r, c.y + y, c.z + Math.sin(a) * r, 0, -Math.cos(a), -y / r, -Math.sin(a), 0.9);
        }
        if (age % 4 == 0) level.sendParticles(ParticleTypes.PORTAL, c.x, c.y, c.z, 12, 0.6, 0.6, 0.6, 1.2);
        if (age % 20 == 0) level.playSound(null, c.x, c.y, c.z, SoundEvents.PORTAL_AMBIENT, SoundSource.PLAYERS, 0.9F, 0.45F);
        if (age % 12 == 6) level.playSound(null, c.x, c.y, c.z, SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.2F, 0.6F);
        return held;
    }

    /** The point collapses: whoever is close takes {@code dmg} and drops. */
    public static void collapse(ServerLevel level, Entity caster, Vec3 c, float dmg) {
        level.playSound(null, c.x, c.y, c.z, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.PLAYERS, 1.5F, 0.6F);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.7F, 0.5F);
        level.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y, c.z, 2, 0.4, 0.4, 0.4, 0);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x, c.y, c.z, 120, 0.2, 0.2, 0.2, 1.4);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(3.0), e -> SkillTargeting.canTarget(caster, e))) {
            e.invulnerableTime = 0;
            e.hurt(ModDamageTypes.source(level, ModDamageTypes.GRAVITY_COMPRESS, caster, caster), dmg);
            e.setDeltaMovement(e.getDeltaMovement().multiply(0.2, 0, 0.2).add(0, -0.4, 0));
            e.hurtMarked = true;
        }
    }

    @SubscribeEvent
    public static void onStop(net.minecraftforge.event.server.ServerStoppingEvent e) { clear(); }

    @Override
    public int clashColor() { return -1; }
}

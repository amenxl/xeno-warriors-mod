package com.xenowarriors.entity;

import com.xenowarriors.bot.PartyBotEntity;
import com.xenowarriors.combat.SkillContext;
import com.xenowarriors.damage.ModDamageTypes;
import com.xenowarriors.network.ModNetwork;
import com.xenowarriors.network.SkillAnimPacket;
import com.xenowarriors.party.TeamUltimate;
import com.xenowarriors.skill.SkillEvents;
import com.xenowarriors.skill.rhalix.JudgmentAbsorptionSkill;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

import java.util.List;

/**
 * A Mikiko + Rhalix team ultimate on a timeline. Either of them can be a player or a party bot: a player's client
 * flies their body along the choreography and plays it as a cutscene (TeamUltCutscene); a bot is flown along it here
 * on the server. The server deals the damage, as each of them (so it scales with each one's Attack and ability tier).
 * <pre>
 * Gravity Fox Fire (KIND 0):
 *   0-14   Rhalix rises above the enemies, wings out             Mikiko crouches, gathering fox fire
 *   14-20  Rhalix raises both hands and slams the dome down (20)
 *   22-32                                                        Mikiko leaps onto the top of the dome
 *   32-78  Rhalix hovers at the side holding the gravity          Mikiko spins on top, fox fire raining through it
 *   78-86                                                        Mikiko dives straight down through the dome
 *   86     the finale blast
 *   86-104 Rhalix lands; they end standing back to back in the middle
 *
 * Blazing Judgment (KIND 1):
 *   0-12   Rhalix rises to the side, hand out                     Mikiko gathers wind
 *   12-98  Judgment Absorption: a gravity point drags every enemy into one knot and squeezes them
 *   28     Mikiko calls Blazing Wind down onto the point: a fire tornado grows around the knot
 *   36-48                                                         Mikiko leaps out to face it
 *   56-96  the tornado burns everything held in it
 *   98     the finale: Rhalix collapses the point as Mikiko bursts the tornado
 *   98-118 they land side by side
 * </pre>
 */
public class TeamUltEntity extends Entity {
    public static final int GRAVITY_FOX_FIRE = 0, BLAZING_JUDGMENT = 1;
    // Gravity Fox Fire
    public static final int RISE_END = 14, SLAM = 20, LEAP = 22, TOP = 32, DIVE = 78, FINALE = 86, END = 104;
    public static final float STORM_DAMAGE = 9F, FINALE_DAMAGE = 42F;
    public static final double DOME_TOP = KishiDomeEntity.MAX_HEIGHT + 0.4, HOVER = 9;
    // Blazing Judgment
    public static final int BJ_OPEN = 12, BJ_TORNADO = 28, BJ_LEAP = 36, BJ_SPOT = 48, BJ_BURN = 56, BJ_FINALE = 98, BJ_END = 118;
    public static final float BJ_CRUSH = 3F, BJ_BURN_DAMAGE = 6F, BJ_COLLAPSE = 20F, BJ_BURST = 30F;
    public static final double BJ_CORE = 2.2, BJ_PULL = 14, BJ_HIT = 4.5;

    private static final EntityDataAccessor<Integer> RHALIX = SynchedEntityData.defineId(TeamUltEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> MIKIKO = SynchedEntityData.defineId(TeamUltEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(TeamUltEntity.class, EntityDataSerializers.INT);
    /** "rx/ry/rz", "mx/my/mz": where each started; "dx/dz": the direction from them toward the enemies. */
    private static final EntityDataAccessor<CompoundTag> DATA = SynchedEntityData.defineId(TeamUltEntity.class, EntityDataSerializers.COMPOUND_TAG);

    private KishiDomeEntity dome;
    private BlazingWindEntity tornado;
    private boolean rhalixMayfly, mikikoMayfly;
    /** (tests) the last Blazing Judgment: damage dealt by each side, and how many it held at the finale. */
    public static float lastRhalixDamage, lastMikikoDamage;
    public static int lastHeld, lastKind = -1;

    public TeamUltEntity(EntityType<? extends TeamUltEntity> type, Level level) {
        super(type, level);
        this.noCulling = true;        // (a big effect drawn far past its small box: never culled - vanilla or EntityCulling)
        noPhysics = true;
    }

    public static TeamUltEntity create(int kind, LivingEntity rhalix, LivingEntity mikiko, Vec3 center) {
        TeamUltEntity e = new TeamUltEntity(ModEntities.TEAM_ULT.get(), rhalix.level());
        e.setPos(center);
        e.entityData.set(KIND, kind);
        e.entityData.set(RHALIX, rhalix.getId());
        e.entityData.set(MIKIKO, mikiko.getId());
        CompoundTag t = new CompoundTag();
        t.putDouble("rx", rhalix.getX()); t.putDouble("ry", rhalix.getY()); t.putDouble("rz", rhalix.getZ());
        t.putDouble("mx", mikiko.getX()); t.putDouble("my", mikiko.getY()); t.putDouble("mz", mikiko.getZ());
        Vec3 mid = rhalix.position().lerp(mikiko.position(), 0.5);
        Vec3 d = new Vec3(center.x - mid.x, 0, center.z - mid.z);
        if (d.lengthSqr() < 0.5) d = Vec3.directionFromRotation(0, rhalix.getYRot());
        d = new Vec3(d.x, 0, d.z).normalize();
        t.putDouble("dx", d.x); t.putDouble("dz", d.z);
        e.entityData.set(DATA, t);
        lastKind = kind;
        return e;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(RHALIX, -1);
        entityData.define(MIKIKO, -1);
        entityData.define(KIND, GRAVITY_FOX_FIRE);
        entityData.define(DATA, new CompoundTag());
    }

    public int kind() { return entityData.get(KIND); }
    public boolean judgment() { return kind() == BLAZING_JUDGMENT; }
    public int end() { return judgment() ? BJ_END : END; }
    public int finale() { return judgment() ? BJ_FINALE : FINALE; }
    public int rhalixId() { return entityData.get(RHALIX); }
    public int mikikoId() { return entityData.get(MIKIKO); }
    public Vec3 rhalixStart() { CompoundTag t = entityData.get(DATA); return new Vec3(t.getDouble("rx"), t.getDouble("ry"), t.getDouble("rz")); }
    public Vec3 mikikoStart() { CompoundTag t = entityData.get(DATA); return new Vec3(t.getDouble("mx"), t.getDouble("my"), t.getDouble("mz")); }
    public Vec3 dir() { CompoundTag t = entityData.get(DATA); return new Vec3(t.getDouble("dx"), 0, t.getDouble("dz")); }
    public Vec3 side() { Vec3 d = dir(); return new Vec3(-d.z, 0, d.x); }
    /** Blazing Judgment's gravity point (the knot everyone is held in). */
    public Vec3 core() { return position().add(0, BJ_CORE, 0); }

    public static float smooth(float x) {
        x = Math.max(0F, Math.min(1F, x));
        return x * x * (3F - 2F * x);
    }

    // ---------------------------------------------------------------- the choreography (shared by server and clients)

    /** Where Rhalix is at this point of the move. */
    public Vec3 rhalixAt(float t) {
        Vec3 c = position(), d = dir(), s = side(), up = new Vec3(0, 1, 0);
        Vec3 start = rhalixStart();
        if (judgment()) {
            Vec3 hold = c.subtract(d.scale(4)).subtract(s.scale(4.5)).add(up.scale(6));
            Vec3 land = c.subtract(d.scale(5)).subtract(s.scale(1.2));
            if (t < BJ_OPEN) return start.lerp(hold, smooth(t / BJ_OPEN));
            if (t < BJ_FINALE) return hold.add(0, Math.sin(t * 0.15) * 0.25, 0);
            if (t < BJ_FINALE + 14) return hold.lerp(land, smooth((t - BJ_FINALE) / 14F));
            return land;
        }
        Vec3 above = c.subtract(d.scale(2.5)).add(s.scale(-1.5)).add(up.scale(HOVER));
        Vec3 hold = c.add(s.scale(-7)).add(up.scale(5));
        Vec3 land = c.subtract(s.scale(0.75));
        if (t < RISE_END) return start.lerp(above, smooth(t / RISE_END));
        if (t < SLAM) return above.add(0, -0.8 * smooth((t - 17) / 3F), 0);         // dips with the slam
        if (t < 30) return above.add(0, -0.8, 0).lerp(hold, smooth((t - SLAM) / 10F));
        if (t < 82) return hold.add(0, Math.sin(t * 0.18) * 0.25, 0);
        if (t < 98) return hold.lerp(land, smooth((t - 82) / 16F));
        return land;
    }

    /** Where Mikiko is at this point of the move. */
    public Vec3 mikikoAt(float t) {
        Vec3 c = position(), d = dir(), s = side(), up = new Vec3(0, 1, 0);
        Vec3 start = mikikoStart();
        if (judgment()) {
            Vec3 spot = c.subtract(d.scale(6.5)).add(s.scale(3.5));
            Vec3 land = c.subtract(d.scale(5)).add(s.scale(1.2));
            if (t < BJ_LEAP) return start;
            if (t < BJ_SPOT) {
                float f = (t - BJ_LEAP) / (BJ_SPOT - BJ_LEAP);
                return start.lerp(spot, smooth(f)).add(0, Math.sin(f * Math.PI) * 3.0, 0);
            }
            if (t < BJ_FINALE) return spot;
            if (t < BJ_FINALE + 14) return spot.lerp(land, smooth((t - BJ_FINALE) / 14F));
            return land;
        }
        Vec3 top = c.add(up.scale(DOME_TOP));
        Vec3 land = c.add(s.scale(0.75));
        if (t < LEAP) return start;
        if (t < TOP) {
            float f = (t - LEAP) / (TOP - LEAP);
            Vec3 flat = start.lerp(top, smooth(f));
            return flat.add(0, Math.sin(f * Math.PI) * 4.0, 0);                   // a high arc
        }
        if (t < DIVE) return top;
        if (t < FINALE) {
            float f = (t - DIVE) / (FINALE - DIVE);
            return top.lerp(c, f * f);                                            // accelerating dive
        }
        if (t < 92) return c;
        return c.lerp(land, smooth((t - 92) / 6F));
    }

    // ---------------------------------------------------------------- server: damage and effects

    private LivingEntity member(int id) {
        return level().getEntity(id) instanceof LivingEntity l ? l : null;
    }

    /** Who a side's hits come from: the player, or a bot's skill stand-in (so its Attack level and tiers count, as for its other skills). */
    private static Entity caster(LivingEntity e) {
        return e instanceof PartyBotEntity b ? com.xenowarriors.bot.PartyBots.standIn(b) : e;
    }

    private List<LivingEntity> inside(LivingEntity r, LivingEntity m) {
        Vec3 c = position();
        AABB box = judgment() ? new AABB(core(), core()).inflate(BJ_HIT, BJ_HIT + 2, BJ_HIT)
                : new AABB(c, c).inflate(KishiDomeEntity.MAX_RADIUS, KishiDomeEntity.MAX_HEIGHT, KishiDomeEntity.MAX_RADIUS);
        return level().getEntitiesOfClass(LivingEntity.class, box, e -> TeamUltimate.foe(r, m, e));
    }

    private static void hurt(ServerLevel level, LivingEntity by, LivingEntity e, net.minecraft.resources.ResourceKey<net.minecraft.world.damagesource.DamageType> type, String skill, float dmg) {
        Entity c = caster(by);
        float h = e.getHealth();
        SkillContext.callSkill(c, skill, () -> {
            e.invulnerableTime = 0;
            return e.hurt(ModDamageTypes.source(level, type, c, c), dmg);
        });
        float done = Math.max(0F, h - e.getHealth());
        if (skill.equals(JudgmentAbsorptionSkill.ID)) lastRhalixDamage += done;
        else lastMikikoDamage += done;
    }

    private static void anim(LivingEntity e, int anim, boolean air) {
        if (e instanceof ServerPlayer p) ModNetwork.sendToTrackingAndSelf(new SkillAnimPacket(p.getId(), anim, air), p);
        else ModNetwork.sendToTrackingEntityAndSelf(new SkillAnimPacket(e.getId(), anim, air), e);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        ServerLevel level = (ServerLevel) level();
        LivingEntity r = member(rhalixId()), m = member(mikikoId());
        if (r == null || m == null || !r.isAlive() || !m.isAlive() || tickCount > end()
                || r instanceof PartyBotEntity rb && rb.downed() || m instanceof PartyBotEntity mb && mb.downed()) {
            finish(r, m);
            return;
        }
        int t = tickCount;
        for (LivingEntity p : new LivingEntity[]{r, m}) p.fallDistance = 0F;
        if (t == 1) {
            for (LivingEntity p : new LivingEntity[]{r, m}) p.getPersistentData().putLong(SkillEvents.ULT_UNTIL, level.getGameTime() + end() + 2);
            rhalixMayfly = r instanceof ServerPlayer rp && com.xenowarriors.skill.CutsceneFlight.lend(rp);
            mikikoMayfly = m instanceof ServerPlayer mp && com.xenowarriors.skill.CutsceneFlight.lend(mp);
            if (judgment()) {
                lastRhalixDamage = 0; lastMikikoDamage = 0; lastHeld = 0;
                anim(r, SkillAnimPacket.RAISE, true);
            } else {
                anim(r, SkillAnimPacket.TEAM_RHALIX, true);
                anim(m, SkillAnimPacket.TEAM_MIKIKO, false);
            }
            level.playSound(null, r.getX(), r.getY(), r.getZ(), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 1.5F, 0.7F);
            level.playSound(null, m.getX(), m.getY(), m.getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1F, 0.6F);
        }
        flyBots(r, m, t);
        if (judgment()) judgmentTick(level, r, m, t);
        else foxFireTick(level, r, m, t);
    }

    /** A bot in it is carried along the choreography (a player's own client does that for them). */
    private void flyBots(LivingEntity r, LivingEntity m, int t) {
        Vec3 c = judgment() ? core() : position().add(0, 1, 0);
        for (LivingEntity e : new LivingEntity[]{r, m}) {
            if (!(e instanceof PartyBotEntity b)) continue;
            Vec3 at = e == r ? rhalixAt(t) : mikikoAt(t);
            b.getNavigation().stop();
            b.setNoGravity(true);
            b.setDeltaMovement(Vec3.ZERO);
            b.setPos(at.x, at.y, at.z);
            Vec3 look = c.subtract(at);
            float yaw = (float) Math.toDegrees(Math.atan2(-look.x, look.z));
            if (!judgment() && e == m && t >= TOP && t < DIVE) yaw += (t - TOP) * 38F;
            b.setYRot(yaw); b.yBodyRot = yaw; b.yHeadRot = yaw;
            b.hurtMarked = true;
        }
    }

    // ---------------------------------------------------------------- Blazing Judgment

    private void judgmentTick(ServerLevel level, LivingEntity r, LivingEntity m, int t) {
        Vec3 c = position(), core = core();
        if (t < BJ_LEAP) {                                                      // Mikiko gathering wind and fire
            Vec3 mp = m.position();
            for (int i = 0; i < 3; i++) {
                double a = t * 0.5 + i * Math.PI * 2 / 3, rr = 2.2 - t / (float) BJ_LEAP;
                level.sendParticles(ParticleTypes.CLOUD, mp.x + Math.cos(a) * rr, mp.y + 0.3 + i * 0.5, mp.z + Math.sin(a) * rr, 1, 0, 0.02, 0, 0);
                level.sendParticles(ParticleTypes.FLAME, mp.x + Math.cos(a + 1) * rr, mp.y + 0.6 + i * 0.4, mp.z + Math.sin(a + 1) * rr, 1, 0, 0.02, 0, 0.01);
            }
        }
        if (t < BJ_OPEN) {                                                      // a dark mote growing in Rhalix's hand
            Vec3 hand = r.position().add(0, 1.4, 0);
            level.sendParticles(JudgmentAbsorptionSkill.DARK, hand.x, hand.y, hand.z, 3, 0.15, 0.15, 0.15, 0);
        }
        if (t == BJ_OPEN) {
            JudgmentAbsorptionSkill.lastPoint = core;
            level.playSound(null, core.x, core.y, core.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 2F, 0.45F);
            level.playSound(null, core.x, core.y, core.z, SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 2F, 0.5F);
            level.playSound(null, core.x, core.y, core.z, SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 1.2F, 0.6F);
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, core.x, core.y, core.z, 150, 0.5, 0.5, 0.5, 0.8);
            for (int i = 0; i < 24; i++) {                                     // a beam of gravity from his hand to the point
                Vec3 q = r.position().add(0, 1.4, 0).lerp(core, i / 24.0);
                level.sendParticles(JudgmentAbsorptionSkill.VIOLET, q.x, q.y, q.z, 1, 0.05, 0.05, 0.05, 0);
            }
        }
        if (t > BJ_OPEN && t < BJ_FINALE && t % 28 == 0) anim(r, SkillAnimPacket.RAISE, true);   // (his hand stays up, holding it)
        // Judgment Absorption: everything is dragged into the knot and held
        if (t >= BJ_OPEN && t < BJ_FINALE) {
            Entity rc = caster(r);
            List<LivingEntity> held = SkillContext.callSkill(rc, JudgmentAbsorptionSkill.ID,
                    () -> JudgmentAbsorptionSkill.tickPull(level, rc, core, BJ_PULL, t, 0F, e -> TeamUltimate.foe(r, m, e)));
            if (t % 12 == 0 && t >= BJ_OPEN + 12) for (LivingEntity e : inside(r, m)) hurt(level, r, e, ModDamageTypes.GRAVITY_COMPRESS, JudgmentAbsorptionSkill.ID, BJ_CRUSH);
            lastHeld = Math.max(lastHeld, held.size());
        }
        // Blazing Wind comes down on the point
        if (t == BJ_TORNADO) {
            Entity mc = caster(m);
            if (mc instanceof Player mp) {
                tornado = BlazingWindEntity.createStill(mp, m, c, BJ_FINALE - BJ_TORNADO);
                level.addFreshEntity(tornado);
            }
            anim(m, SkillAnimPacket.BLAZING, false);
            level.playSound(null, c.x, c.y, c.z, SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 2F, 0.5F);
            level.playSound(null, c.x, c.y, c.z, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 0.5F, 1.5F);
        }
        if (t == BJ_LEAP) level.playSound(null, m.getX(), m.getY(), m.getZ(), SoundEvents.PHANTOM_SWOOP, SoundSource.PLAYERS, 1.2F, 1.2F);
        if (t >= BJ_SPOT && t < BJ_FINALE) {                                   // fire streaming from her hands into the tornado
            Vec3 hand = mikikoAt(t).add(0, 1.3, 0);
            for (int i = 0; i < 4; i++) {
                Vec3 q = hand.lerp(core, level.random.nextDouble());
                level.sendParticles(i % 2 == 0 ? ParticleTypes.FLAME : ParticleTypes.SMALL_FLAME, q.x, q.y, q.z, 1, 0.1, 0.1, 0.1, 0.01);
            }
        }
        if (t >= BJ_BURN && t <= 96 && t % 8 == 0) {
            level.playSound(null, c.x, c.y, c.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1F, 0.6F + level.random.nextFloat() * 0.4F);
            for (LivingEntity e : inside(r, m)) {
                hurt(level, m, e, ModDamageTypes.BLAZING_WIND, "blazing_wind", BJ_BURN_DAMAGE);
                e.setSecondsOnFire(5);
                level.sendParticles(ParticleTypes.LAVA, e.getX(), e.getY() + 1, e.getZ(), 2, 0.2, 0.3, 0.2, 0);
            }
        }
        if (t == BJ_FINALE) finaleJudgment(level, r, m, 1F);
    }

    /** Rhalix collapses the point as Mikiko bursts the tornado ({@code extra}: storm hits that land with it when cut short). */
    private void finaleJudgment(ServerLevel level, LivingEntity r, LivingEntity m, float extraBurn) {
        Vec3 core = core();
        anim(r, SkillAnimPacket.COMPRESS, true);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, core.x, core.y, core.z, 2, 1, 0.5, 1, 0);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, core.x, core.y, core.z, 200, 0.3, 0.3, 0.3, 1.6);
        level.sendParticles(ParticleTypes.FLAME, core.x, core.y, core.z, 220, 4, 3, 4, 0.3);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, core.x, core.y, core.z, 80, 3, 2, 3, 0.2);
        level.playSound(null, core.x, core.y, core.z, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.PLAYERS, 2F, 0.5F);
        level.playSound(null, core.x, core.y, core.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 3F, 0.6F);
        level.playSound(null, core.x, core.y, core.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 1.5F, 1.1F);
        for (LivingEntity e : inside(r, m)) {
            hurt(level, r, e, ModDamageTypes.GRAVITY_COMPRESS, JudgmentAbsorptionSkill.ID, BJ_COLLAPSE);
            hurt(level, m, e, ModDamageTypes.BLAZING_WIND, "blazing_wind", BJ_BURST + extraBurn - 1F);
            e.setSecondsOnFire(6);
            Vec3 out = e.position().subtract(position());
            out = out.lengthSqr() < 0.01 ? new Vec3(0, 0, 1) : new Vec3(out.x, 0, out.z).normalize();
            e.setDeltaMovement(out.x * 1.2, 0.7, out.z * 1.2);
            e.hurtMarked = true;
        }
    }

    // ---------------------------------------------------------------- Gravity Fox Fire

    private void foxFireTick(ServerLevel level, LivingEntity r, LivingEntity m, int t) {
        Vec3 c = position();
        List<LivingEntity> foes = inside(r, m);
        // Mikiko gathering fox fire while Rhalix rises
        if (t < LEAP) {
            Vec3 mp = m.position();
            level.sendParticles(ParticleTypes.FLAME, mp.x, mp.y + 0.2, mp.z, 4, 0.6, 0.1, 0.6, 0.02);
            if (t % 3 == 0) level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, mp.x, mp.y + 1, mp.z, 3, 0.4, 0.6, 0.4, 0.04);
        }
        if (t % 8 == 0 && t < 82) level.playSound(null, r.getX(), r.getY(), r.getZ(), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 0.7F, 1F);
        // the dome slams down
        if (t == SLAM) {
            if (caster(r) instanceof Player rp) {
                dome = KishiDomeEntity.create(level, rp, c);
                dome.raiseLevel();
                dome.raiseLevel();
                level.addFreshEntity(dome);
            }
            level.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 2F, 0.5F);
            level.playSound(null, c.x, c.y, c.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 1.5F, 0.4F);
            level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1F, 0.5F);
            level.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y + 0.5, c.z, 6, 3, 0.3, 3, 0);
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x, c.y + 3, c.z, 120, 4, 3, 4, 0.1);
        }
        // pinned inside
        if (t >= SLAM) for (LivingEntity e : foes) {
            Vec3 pull = c.subtract(e.position()).multiply(0.08, 0, 0.08);
            e.setDeltaMovement(e.getDeltaMovement().multiply(0.2, 1, 0.2).add(pull.x, -0.25, pull.z));
            e.hurtMarked = true;
        }
        if (t == LEAP) level.playSound(null, m.getX(), m.getY(), m.getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.5F, 0.7F);
        // the storm: Mikiko spinning on top, fox fire raining through the dome
        if (t >= TOP && t < DIVE) {
            double rad = KishiDomeEntity.MAX_RADIUS;
            Vec3 top = c.add(0, DOME_TOP + 1, 0);
            for (int i = 0; i < 3; i++) {
                double a = (t * 0.45) + i * (Math.PI * 2 / 3);
                level.sendParticles(ParticleTypes.FLAME, top.x + Math.cos(a) * 1.4, top.y, top.z + Math.sin(a) * 1.4, 3, 0.1, 0.1, 0.1, 0.02);
            }
            for (int i = 0; i < 8; i++) {
                double a = level.random.nextDouble() * Math.PI * 2, d = level.random.nextDouble() * rad;
                double x = c.x + Math.cos(a) * d, z = c.z + Math.sin(a) * d;
                level.sendParticles(i % 2 == 0 ? ParticleTypes.FLAME : ParticleTypes.SOUL_FIRE_FLAME, x, c.y + 1 + level.random.nextDouble() * 5, z,
                        0, 0, -1.2, 0, 0.6);                                                       // streaking down
            }
            if (t % 6 == 0) {
                level.playSound(null, c.x, c.y, c.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1F, 0.7F + level.random.nextFloat() * 0.5F);
                for (LivingEntity e : foes) {
                    hurt(level, m, e, ModDamageTypes.FIRE_BLITZ, "blazing_wind", STORM_DAMAGE);
                    e.setSecondsOnFire(5);
                    level.sendParticles(ParticleTypes.LAVA, e.getX(), e.getY() + 1, e.getZ(), 3, 0.2, 0.3, 0.2, 0);
                }
            }
        }
        if (t == DIVE) level.playSound(null, c.x, c.y + DOME_TOP, c.z, SoundEvents.PHANTOM_SWOOP, SoundSource.PLAYERS, 1.5F, 0.8F);
        if (t > DIVE && t < FINALE) level.sendParticles(ParticleTypes.FLAME, m.getX(), m.getY() + 1, m.getZ(), 6, 0.2, 0.4, 0.2, 0.01);
        // the finale
        if (t == FINALE) {
            double rad = KishiDomeEntity.MAX_RADIUS;
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y + 1, c.z, 2, 1, 0.5, 1, 0);
            level.sendParticles(ParticleTypes.FLAME, c.x, c.y + 1, c.z, 220, rad * 0.5, 2, rad * 0.5, 0.25);
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, c.x, c.y + 1, c.z, 120, rad * 0.5, 2, rad * 0.5, 0.2);
            level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 3F, 0.6F);
            level.playSound(null, c.x, c.y, c.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 1.5F, 1.2F);
            for (LivingEntity e : foes) {
                hurt(level, m, e, ModDamageTypes.FIRE_BLITZ, "blazing_wind", FINALE_DAMAGE);
                Vec3 out = e.position().subtract(c);
                out = out.lengthSqr() < 0.01 ? new Vec3(0, 0, 1) : out.normalize();
                e.setDeltaMovement(out.x * 1.1, 0.8, out.z * 1.1);
                e.hurtMarked = true;
            }
            if (dome != null) dome.discard();
        }
    }

    /**
     * One of them was hit mid-cutscene: the rest of the attack lands at once on everyone caught in it, and the cutscene
     * ends for both so they can defend themselves (nothing is refunded).
     */
    public boolean interrupt() {
        if (isRemoved() || level().isClientSide || tickCount > finale()) return false;
        ServerLevel level = (ServerLevel) level();
        LivingEntity r = member(rhalixId()), m = member(mikikoId());
        if (r == null || m == null) { finish(r, m); return true; }
        Vec3 c = position();
        if (judgment()) {
            int crush = 0, burns = 0;
            for (int t = Math.max(BJ_OPEN + 12, tickCount + 1); t < BJ_FINALE; t++) if (t % 12 == 0) crush++;
            for (int t = Math.max(BJ_BURN, tickCount + 1); t <= 96; t++) if (t % 8 == 0) burns++;
            for (LivingEntity e : inside(r, m)) if (crush > 0) hurt(level, r, e, ModDamageTypes.GRAVITY_COMPRESS, JudgmentAbsorptionSkill.ID, crush * BJ_CRUSH);
            finaleJudgment(level, r, m, 1F + burns * BJ_BURN_DAMAGE);
            com.xenowarriors.XenoWarriors.LOGGER.info("Blazing Judgment cut short at tick {}: {} crush + {} burn hits + finale landed at once", tickCount, crush, burns);
        } else {
            int storms = 0;
            for (int t = Math.max(TOP, tickCount + 1); t < DIVE; t++) if (t % 6 == 0) storms++;
            float dmg = storms * STORM_DAMAGE + FINALE_DAMAGE;
            for (LivingEntity e : inside(r, m)) {
                hurt(level, m, e, ModDamageTypes.FIRE_BLITZ, "blazing_wind", dmg);
                e.setSecondsOnFire(5);
                Vec3 out = e.position().subtract(c);
                out = out.lengthSqr() < 0.01 ? new Vec3(0, 0, 1) : out.normalize();
                e.setDeltaMovement(out.x * 1.1, 0.8, out.z * 1.1);
                e.hurtMarked = true;
            }
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y + 1, c.z, 1, 0, 0, 0, 0);
            level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 2F, 0.7F);
            com.xenowarriors.XenoWarriors.LOGGER.info("Team ultimate cut short at tick {}: {} storm hits + finale landed at once ({} dmg)", tickCount, storms, dmg);
        }
        finish(r, m);
        for (LivingEntity p : new LivingEntity[]{r, m})
            p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOW_FALLING, 60, 0, false, false));
        return true;
    }

    private void finish(LivingEntity r, LivingEntity m) {
        if (r != null) restore(r, rhalixMayfly);
        if (m != null) restore(m, mikikoMayfly);
        if (dome != null && dome.isAlive()) dome.discard();
        if (tornado != null && tornado.isAlive() && tickCount < finale()) tornado.discard();
        discard();
    }

    private static void restore(LivingEntity e, boolean hadMayfly) {
        e.getPersistentData().remove(SkillEvents.ULT_UNTIL);
        e.fallDistance = 0F;
        if (e instanceof PartyBotEntity b) {
            b.setNoGravity(false);
            b.setDeltaMovement(Vec3.ZERO);
            return;
        }
        if (e instanceof ServerPlayer p) com.xenowarriors.skill.CutsceneFlight.release(p, hadMayfly);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}

    @Override
    public boolean shouldBeSaved() { return false; }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}

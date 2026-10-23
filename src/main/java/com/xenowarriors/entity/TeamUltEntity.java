package com.xenowarriors.entity;

import com.xenowarriors.damage.ModDamageTypes;
import com.xenowarriors.network.ModNetwork;
import com.xenowarriors.network.SkillAnimPacket;
import com.xenowarriors.party.Parties;
import com.xenowarriors.skill.SkillEvents;
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
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

import java.util.List;

/**
 * Gravity Fox Fire, the Mikiko + Rhalix team ultimate, on a timeline. Both players' clients fly their bodies along the
 * choreography below and play it as a cutscene (TeamUltCutscene); the server deals the damage.
 * <pre>
 *   0-14   Rhalix rises above the enemies, wings out             Mikiko crouches, gathering fox fire
 *   14-20  Rhalix raises both hands and slams the dome down (20)
 *   22-32                                                        Mikiko leaps onto the top of the dome
 *   32-78  Rhalix hovers at the side holding the gravity          Mikiko spins on top, fox fire raining through it
 *   78-86                                                        Mikiko dives straight down through the dome
 *   86     the finale blast
 *   86-104 Rhalix lands; they end standing back to back in the middle
 * </pre>
 */
public class TeamUltEntity extends Entity {
    public static final int RISE_END = 14, SLAM = 20, LEAP = 22, TOP = 32, DIVE = 78, FINALE = 86, END = 104;
    public static final float STORM_DAMAGE = 9F, FINALE_DAMAGE = 42F;
    public static final double DOME_TOP = KishiDomeEntity.MAX_HEIGHT + 0.4, HOVER = 9;

    private static final EntityDataAccessor<Integer> RHALIX = SynchedEntityData.defineId(TeamUltEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> MIKIKO = SynchedEntityData.defineId(TeamUltEntity.class, EntityDataSerializers.INT);
    /** "rx/ry/rz", "mx/my/mz": where each started; "dx/dz": the direction from them toward the enemies. */
    private static final EntityDataAccessor<CompoundTag> DATA = SynchedEntityData.defineId(TeamUltEntity.class, EntityDataSerializers.COMPOUND_TAG);

    private KishiDomeEntity dome;
    private boolean rhalixMayfly, mikikoMayfly;

    public TeamUltEntity(EntityType<? extends TeamUltEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public static TeamUltEntity create(ServerPlayer rhalix, ServerPlayer mikiko, Vec3 center) {
        TeamUltEntity e = new TeamUltEntity(ModEntities.TEAM_ULT.get(), rhalix.level());
        e.setPos(center);
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
        return e;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(RHALIX, -1);
        entityData.define(MIKIKO, -1);
        entityData.define(DATA, new CompoundTag());
    }

    public int rhalixId() { return entityData.get(RHALIX); }
    public int mikikoId() { return entityData.get(MIKIKO); }
    public Vec3 rhalixStart() { CompoundTag t = entityData.get(DATA); return new Vec3(t.getDouble("rx"), t.getDouble("ry"), t.getDouble("rz")); }
    public Vec3 mikikoStart() { CompoundTag t = entityData.get(DATA); return new Vec3(t.getDouble("mx"), t.getDouble("my"), t.getDouble("mz")); }
    public Vec3 dir() { CompoundTag t = entityData.get(DATA); return new Vec3(t.getDouble("dx"), 0, t.getDouble("dz")); }
    public Vec3 side() { Vec3 d = dir(); return new Vec3(-d.z, 0, d.x); }

    public static float smooth(float x) {
        x = Math.max(0F, Math.min(1F, x));
        return x * x * (3F - 2F * x);
    }

    // ---------------------------------------------------------------- the choreography (shared by server and clients)

    /** Where Rhalix is at this point of the move. */
    public Vec3 rhalixAt(float t) {
        Vec3 c = position(), d = dir(), s = side(), up = new Vec3(0, 1, 0);
        Vec3 start = rhalixStart();
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
        Vec3 c = position(), s = side(), up = new Vec3(0, 1, 0);
        Vec3 start = mikikoStart();
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

    private ServerPlayer player(int id) {
        return level().getEntity(id) instanceof ServerPlayer p ? p : null;
    }

    private List<LivingEntity> inside(ServerPlayer r, ServerPlayer m) {
        double rad = KishiDomeEntity.MAX_RADIUS;
        Vec3 c = position();
        return level().getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(rad, KishiDomeEntity.MAX_HEIGHT, rad),
                e -> e.isAlive() && e != r && e != m && !(e instanceof Player) && Parties.petOwner(e) == null
                        && (e instanceof Enemy || com.xenowarriors.karma.KarmaSensing.corrupted(e)
                        || (e instanceof Mob mob && mob.getTarget() instanceof Player t && (t == r || t == m || Parties.sameParty(t, r)))));
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        ServerLevel level = (ServerLevel) level();
        ServerPlayer r = player(rhalixId()), m = player(mikikoId());
        if (r == null || m == null || !r.isAlive() || !m.isAlive() || tickCount > END) {
            finish(r, m);
            return;
        }
        int t = tickCount;
        for (ServerPlayer p : new ServerPlayer[]{r, m}) p.fallDistance = 0F;
        Vec3 c = position();
        if (t == 1) {
            rhalixMayfly = r.getAbilities().mayfly;
            mikikoMayfly = m.getAbilities().mayfly;
            for (ServerPlayer p : new ServerPlayer[]{r, m}) {
                p.getPersistentData().putLong(SkillEvents.ULT_UNTIL, level.getGameTime() + END + 2);
                if (!p.getAbilities().mayfly) {
                    p.getAbilities().mayfly = true;
                    p.onUpdateAbilities();
                }
            }
            ModNetwork.sendToTrackingAndSelf(new SkillAnimPacket(r.getId(), SkillAnimPacket.TEAM_RHALIX, true), r);
            ModNetwork.sendToTrackingAndSelf(new SkillAnimPacket(m.getId(), SkillAnimPacket.TEAM_MIKIKO, false), m);
            level.playSound(null, r.getX(), r.getY(), r.getZ(), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 1.5F, 0.7F);
            level.playSound(null, m.getX(), m.getY(), m.getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1F, 0.6F);
        }
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
            dome = KishiDomeEntity.create(level, r, c);
            dome.raiseLevel();
            dome.raiseLevel();
            level.addFreshEntity(dome);
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
                    e.invulnerableTime = 0;
                    e.hurt(ModDamageTypes.source(level, ModDamageTypes.FIRE_BLITZ, m, m), STORM_DAMAGE);
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
                e.invulnerableTime = 0;
                e.hurt(ModDamageTypes.source(level, ModDamageTypes.FIRE_BLITZ, m, m), FINALE_DAMAGE);
                Vec3 out = e.position().subtract(c);
                out = out.lengthSqr() < 0.01 ? new Vec3(0, 0, 1) : out.normalize();
                e.setDeltaMovement(out.x * 1.1, 0.8, out.z * 1.1);
                e.hurtMarked = true;
            }
            if (dome != null) dome.discard();
        }
    }

    private void finish(ServerPlayer r, ServerPlayer m) {
        if (r != null) restore(r, rhalixMayfly);
        if (m != null) restore(m, mikikoMayfly);
        if (dome != null && dome.isAlive()) dome.discard();
        discard();
    }

    private static void restore(ServerPlayer p, boolean hadMayfly) {
        p.getPersistentData().remove(SkillEvents.ULT_UNTIL);
        p.fallDistance = 0F;
        if (!hadMayfly && !p.isCreative() && !p.isSpectator()) {
            p.getAbilities().mayfly = false;
            p.getAbilities().flying = false;
            p.onUpdateAbilities();
        }
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

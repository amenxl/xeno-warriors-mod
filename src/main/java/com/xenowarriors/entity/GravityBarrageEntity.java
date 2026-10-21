package com.xenowarriors.entity;

import com.xenowarriors.damage.ModDamageTypes;
import com.xenowarriors.skill.Rooting;
import com.xenowarriors.skill.SkillEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Rhalix's ultimate, Gravity Barrage. Runs the whole sequence on a timeline:
 *   RISE     - he flies up with his wings out
 *   DOMES    - a gravity sphere locks around every target (they can't move from here on)
 *   FORM     - chunks of earth tear up out of the ground and orbit each target
 *   BARRAGE  - every rock fires into its target at once, again and again; rocks that hit stick
 *              in the ground around them
 *   DESCEND  - he flies back down
 *   DETONATE - every rock in the ground explodes into pebbles for extra damage
 * The owner's client plays this as a cutscene (see UltimateCutscene).
 */
public class GravityBarrageEntity extends Entity {
    // ---- Timeline (ticks) ----
    public static final int DOMES = 20;
    public static final int FORM = 26;
    public static final int BARRAGE = 50;
    public static final int VOLLEYS = 5;
    public static final int VOLLEY_EVERY = 12;
    public static final int IMPACT_IN_VOLLEY = 6;
    public static final int DESCEND = 110;
    public static final int LANDED = 135;
    public static final int DETONATE = 140;
    public static final int END = 152;
    public static final float MAX_RISE = 7F;
    public static final int ROCKS_PER_TARGET = 6;
    // ---- Tuning ----
    public static final float VOLLEY_DAMAGE = 3.5F;
    public static final float PEBBLE_DAMAGE = 3F;
    public static final float PEBBLE_RADIUS = 3F;
    public static final float MAX_PEBBLE_DAMAGE = 9F;   // per target, so big groups of rocks don't one-shot
    public static final double TARGET_RADIUS = 16;
    public static final int MAX_TARGETS = 8;

    private static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(GravityBarrageEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Integer> OWNER_ID = SynchedEntityData.defineId(GravityBarrageEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> RISE = SynchedEntityData.defineId(GravityBarrageEntity.class, EntityDataSerializers.FLOAT);
    /** "targets": int ids; "landed": list of [x, y, z, seed] rocks stuck in the ground; "cx/cy/cz": target center. */
    private static final EntityDataAccessor<CompoundTag> DATA = SynchedEntityData.defineId(GravityBarrageEntity.class, EntityDataSerializers.COMPOUND_TAG);

    private final Map<Integer, Vec3> anchors = new HashMap<>();
    private boolean hadMayfly;

    public GravityBarrageEntity(EntityType<? extends GravityBarrageEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static GravityBarrageEntity create(ServerPlayer owner, List<LivingEntity> targets) {
        GravityBarrageEntity e = new GravityBarrageEntity(ModEntities.GRAVITY_BARRAGE.get(), owner.level());
        e.setPos(owner.position());
        e.entityData.set(OWNER, Optional.of(owner.getUUID()));
        e.entityData.set(OWNER_ID, owner.getId());
        e.entityData.set(RISE, headroom(owner));
        CompoundTag tag = new CompoundTag();
        int[] ids = new int[targets.size()];
        Vec3 c = Vec3.ZERO;
        for (int i = 0; i < ids.length; i++) {
            ids[i] = targets.get(i).getId();
            c = c.add(targets.get(i).position());
            e.anchors.put(ids[i], targets.get(i).position());
        }
        c = c.scale(1.0 / Math.max(1, ids.length));
        tag.putIntArray("targets", ids);
        tag.putDouble("cx", c.x);
        tag.putDouble("cy", c.y);
        tag.putDouble("cz", c.z);
        tag.put("landed", new ListTag());
        e.entityData.set(DATA, tag);
        return e;
    }

    /** How high he can fly before hitting a ceiling (max 7 blocks). */
    private static float headroom(Player owner) {
        BlockPos base = owner.blockPosition();
        for (int dy = 2; dy <= MAX_RISE + 2; dy++) {
            if (!owner.level().getBlockState(base.above(dy)).getCollisionShape(owner.level(), base.above(dy)).isEmpty()) {
                return Math.max(0F, dy - 2.2F);
            }
        }
        return MAX_RISE;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(OWNER, Optional.empty());
        entityData.define(OWNER_ID, -1);
        entityData.define(RISE, MAX_RISE);
        entityData.define(DATA, new CompoundTag());
    }

    public UUID ownerUuid() { return entityData.get(OWNER).orElse(null); }
    public int ownerId() { return entityData.get(OWNER_ID); }
    public float rise() { return entityData.get(RISE); }
    public int[] targetIds() { return entityData.get(DATA).getIntArray("targets"); }
    public Vec3 center() {
        CompoundTag t = entityData.get(DATA);
        return new Vec3(t.getDouble("cx"), t.getDouble("cy"), t.getDouble("cz"));
    }

    /** Rocks stuck in the ground: {x, y, z, seed}. */
    public List<double[]> landed() {
        List<double[]> out = new ArrayList<>();
        for (Tag t : entityData.get(DATA).getList("landed", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            out.add(new double[]{c.getDouble("x"), c.getDouble("y"), c.getDouble("z"), c.getInt("s")});
        }
        return out;
    }

    /** 0..1 how far up Rhalix is at a moment in time. */
    public static float riseFraction(float age) {
        if (age < DESCEND) return smooth(age / 24F);
        return 1F - smooth((age - DESCEND) / (LANDED - DESCEND));
    }

    /** Which volley we're in (0-based) and how far through it, or -1 before/after the barrage. */
    public static int volley(float age) {
        if (age < BARRAGE || age >= BARRAGE + VOLLEYS * VOLLEY_EVERY) return -1;
        return (int) ((age - BARRAGE) / VOLLEY_EVERY);
    }

    public static float smooth(float x) {
        x = Math.max(0F, Math.min(1F, x));
        return x * x * (3F - 2F * x);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            clientTick();
            return;
        }
        ServerLevel server = (ServerLevel) level();
        Player p = ownerUuid() == null ? null : server.getPlayerByUUID(ownerUuid());
        if (!(p instanceof ServerPlayer owner) || !owner.isAlive() || tickCount > END) {
            finish(p instanceof ServerPlayer sp ? sp : null);
            return;
        }

        // keep Rhalix safe and allowed to hover during the cutscene
        owner.fallDistance = 0F;
        if (tickCount == 1) {
            hadMayfly = owner.getAbilities().mayfly;
            owner.getPersistentData().putLong(SkillEvents.ULT_UNTIL, server.getGameTime() + END + 2);
            if (!hadMayfly) {
                owner.getAbilities().mayfly = true;
                owner.onUpdateAbilities();
            }
            server.playSound(null, getX(), getY(), getZ(), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 1.5F, 0.7F);
            server.playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.5F, 0.5F);
        }
        if (tickCount % 10 == 5 && tickCount < DESCEND) {
            server.playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 0.8F, 0.9F);
        }

        List<LivingEntity> targets = targets();
        if (tickCount >= DOMES && tickCount <= DETONATE) {
            for (LivingEntity t : targets) Rooting.hold(t, anchors.getOrDefault(t.getId(), t.position()));
        }
        if (tickCount == DOMES) {
            for (LivingEntity t : targets) {
                server.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.2F, 0.6F);
            }
        }
        if (tickCount == FORM) {
            for (LivingEntity t : targets) {
                server.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.ROOTED_DIRT_BREAK, SoundSource.PLAYERS, 1.5F, 0.5F);
                server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, server.getBlockState(t.blockPosition().below())),
                        t.getX(), t.getY() + 0.1, t.getZ(), 40, 2, 0.1, 2, 0.15);
            }
        }

        // volleys: every rock hits its target at the same moment
        if (tickCount >= BARRAGE && tickCount < BARRAGE + VOLLEYS * VOLLEY_EVERY
                && (tickCount - BARRAGE) % VOLLEY_EVERY == IMPACT_IN_VOLLEY) {
            int v = (tickCount - BARRAGE) / VOLLEY_EVERY;
            CompoundTag data = entityData.get(DATA).copy();
            ListTag landed = data.getList("landed", Tag.TAG_COMPOUND);
            for (int i = 0; i < targets.size(); i++) {
                LivingEntity t = targets.get(i);
                t.invulnerableTime = 0;
                t.hurt(ModDamageTypes.gravityBarrage(server, this, owner), VOLLEY_DAMAGE);
                Vec3 c = t.getBoundingBox().getCenter();
                server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.COBBLESTONE.defaultBlockState()),
                        c.x, c.y, c.z, 30, 0.4, 0.4, 0.4, 0.3);
                server.sendParticles(ParticleTypes.CRIT, c.x, c.y, c.z, 10, 0.3, 0.3, 0.3, 0.4);
                server.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.6F, 0.9F + 0.1F * v);
                server.playSound(null, c.x, c.y, c.z, SoundEvents.STONE_BREAK, SoundSource.PLAYERS, 1.3F, 0.6F);
                // two rocks from each volley bounce off and bury themselves in the ground nearby
                for (int k = 0; k < 2; k++) {
                    int seed = t.getId() * 131 + v * 17 + k * 7;
                    double ang = (seed * 2.399963) % (Math.PI * 2);
                    double dist = 1.6 + (Math.abs(seed * 37) % 100) / 100.0 * 1.8;
                    double lx = t.getX() + Math.cos(ang) * dist, lz = t.getZ() + Math.sin(ang) * dist;
                    double ly = groundAt(server, lx, t.getY(), lz);
                    CompoundTag rock = new CompoundTag();
                    rock.putDouble("x", lx);
                    rock.putDouble("y", ly);
                    rock.putDouble("z", lz);
                    rock.putInt("s", seed);
                    landed.add(rock);
                }
            }
            data.put("landed", landed);
            entityData.set(DATA, data);
        }

        if (tickCount == LANDED) {
            server.playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.6F, 0.5F);
        }

        // the buried rocks burst into pebbles
        if (tickCount == DETONATE) {
            Map<Integer, Float> taken = new HashMap<>();
            for (double[] r : landed()) {
                server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.COBBLESTONE.defaultBlockState()),
                        r[0], r[1] + 0.4, r[2], 35, 0.3, 0.3, 0.3, 0.45);
                server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.GRAVEL.defaultBlockState()),
                        r[0], r[1] + 0.4, r[2], 20, 0.3, 0.3, 0.3, 0.35);
                server.sendParticles(ParticleTypes.EXPLOSION, r[0], r[1] + 0.5, r[2], 1, 0, 0, 0, 0);
                AABB box = new AABB(r[0] - PEBBLE_RADIUS, r[1] - 1, r[2] - PEBBLE_RADIUS, r[0] + PEBBLE_RADIUS, r[1] + 3, r[2] + PEBBLE_RADIUS);
                for (LivingEntity hit : server.getEntitiesOfClass(LivingEntity.class, box,
                        e -> e != owner && e.isAlive() && com.xenowarriors.skill.SkillTargeting.canTarget(owner, e))) {
                    float already = taken.getOrDefault(hit.getId(), 0F);
                    if (already >= MAX_PEBBLE_DAMAGE) continue;
                    hit.invulnerableTime = 0;
                    hit.hurt(ModDamageTypes.gravityBarrage(server, this, owner), PEBBLE_DAMAGE);
                    taken.put(hit.getId(), already + PEBBLE_DAMAGE);
                }
            }
            server.playSound(null, center().x, center().y, center().z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 2.0F, 0.7F);
            server.playSound(null, center().x, center().y, center().z, SoundEvents.DRIPSTONE_BLOCK_BREAK, SoundSource.PLAYERS, 2.0F, 0.5F);
            CompoundTag data = entityData.get(DATA).copy();
            data.put("landed", new ListTag());
            entityData.set(DATA, data);
        }
    }

    private static double groundAt(Level level, double x, double y, double z) {
        BlockPos.MutableBlockPos pos = BlockPos.containing(x, y + 2, z).mutable();
        for (int i = 0; i < 6; i++) {
            if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) return pos.getY() + 1;
            pos.move(0, -1, 0);
        }
        return y;
    }

    private List<LivingEntity> targets() {
        List<LivingEntity> out = new ArrayList<>();
        for (int id : targetIds()) {
            if (level().getEntity(id) instanceof LivingEntity l && l.isAlive()) out.add(l);
        }
        return out;
    }

    private void finish(ServerPlayer owner) {
        if (owner != null) {
            owner.getPersistentData().remove(SkillEvents.ULT_UNTIL);
            if (!hadMayfly && !owner.isCreative() && !owner.isSpectator()) {
                owner.getAbilities().mayfly = false;
                owner.getAbilities().flying = false;
                owner.onUpdateAbilities();
            }
            owner.fallDistance = 0F;
        }
        discard();
    }

    private void clientTick() {
        // purple threads from Rhalix to each target as the domes lock on
        if (tickCount >= DOMES - 2 && tickCount <= DOMES + 8) {
            Entity owner = level().getEntity(ownerId());
            if (owner == null) return;
            Vec3 from = owner.getEyePosition();
            for (int id : targetIds()) {
                Entity t = level().getEntity(id);
                if (t == null) continue;
                Vec3 to = t.getBoundingBox().getCenter();
                for (int i = 0; i < 6; i++) {
                    Vec3 p = from.lerp(to, random.nextDouble());
                    level().addParticle(ParticleTypes.END_ROD, p.x, p.y, p.z, 0, 0, 0);
                }
            }
        }
    }

    @Override
    public boolean shouldBeSaved() { return false; }

    @Override
    public boolean shouldRenderAtSqrDistance(double dist) { return dist < 128 * 128; }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}

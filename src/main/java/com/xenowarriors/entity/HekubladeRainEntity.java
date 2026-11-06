package com.xenowarriors.entity;

import com.xenowarriors.damage.ModDamageTypes;
import com.xenowarriors.skill.SkillTargeting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import org.joml.Vector3f;

/**
 * Mikiko's second ultimate, Hekublade Rain: a ring of glowing Hekublades appears high over the target area, and they
 * come down on it in waves - each one a hit and a small burst where it lands - then one giant blade drops in the middle
 * for the finale. Only his enemies are hurt (never his party, their pets or bots).
 * <p>
 * Everything about the blades (where each one hangs in the ring, where it lands, when) comes from the entity's id, so
 * the client draws the same rain the server deals out - no extra entities or packets.
 */
public class HekubladeRainEntity extends Entity {
    public static final float RADIUS = 5.5F, RING_HEIGHT = 11F, GIANT_HEIGHT = 15F;
    public static final int WAVES = 4, PER_WAVE = 5, BLADES = WAVES * PER_WAVE;
    /** Timeline (ticks): the ring forms, the waves fall (each takes FALL ticks to land), the giant blade, the end. */
    public static final int RING_UP = 14, FIRST_WAVE = 16, WAVE_GAP = 10, FALL = 7, GIANT_START = 54, GIANT_FALL = 8, END = 82;
    public static final int GIANT_LAND = GIANT_START + GIANT_FALL;
    public static final float DAMAGE = 6F, SPLASH = 3F, GIANT_DAMAGE = 16F;
    public static final float HIT_RADIUS = 1.4F, SPLASH_RADIUS = 2.8F, GIANT_RADIUS = 6.5F;

    /** Who cast it (server side only; a bot casts through its stand-in player). */
    private ServerPlayer caster;

    public HekubladeRainEntity(EntityType<? extends HekubladeRainEntity> type, Level level) {
        super(type, level);
        this.noCulling = true;        // (a big effect drawn far past its small box: never culled - vanilla or EntityCulling)
        this.noPhysics = true;
    }

    public static HekubladeRainEntity create(ServerPlayer caster, Vec3 at) {
        HekubladeRainEntity e = new HekubladeRainEntity(ModEntities.HEKUBLADE_RAIN.get(), caster.level());
        e.caster = caster;
        e.setPos(at);
        return e;
    }

    @Override protected void defineSynchedData() {}
    @Override protected void readAdditionalSaveData(CompoundTag tag) {}
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}
    @Override public boolean shouldBeSaved() { return false; }
    @Override public boolean isPickable() { return false; }
    @Override public boolean shouldRenderAtSqrDistance(double dist) { return dist < 128 * 128; }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    // ------------------------------------------------------------------ the blades (same on both sides)

    /** Which wave blade i falls in. */
    public static int wave(int i) { return i % WAVES; }

    /** When blade i leaves the ring, and when it lands. */
    public static int dropTick(int i) { return FIRST_WAVE + wave(i) * WAVE_GAP + (i / WAVES) % 2; }
    public static int landTick(int i) { return dropTick(i) + FALL; }

    /** Blade i's spot in the ring (relative to the centre), turning slowly. */
    public Vec3 ringPos(int i, float age) {
        double a = i * (Math.PI * 2 / BLADES) + age * 0.035;
        return new Vec3(Math.cos(a) * RADIUS, RING_HEIGHT + Mth.sin(age * 0.2F + i) * 0.15F, Math.sin(a) * RADIUS);
    }

    /** Where blade i comes down (relative to the centre): spread over the area, the later waves closer in. */
    public Vec3 landPos(int i) {
        long seed = getId() * 341873128712L + i * 132897987541L;
        java.util.Random r = new java.util.Random(seed);
        double a = r.nextDouble() * Math.PI * 2;
        double d = Math.sqrt(r.nextDouble()) * RADIUS * (1.0 - wave(i) * 0.12);
        return new Vec3(Math.cos(a) * d, 0, Math.sin(a) * d);
    }

    /** Blade i right now (relative to the centre): in the ring, falling (speeding up), or gone (null). */
    public Vec3 bladePos(int i, float age) {
        if (age < dropTick(i)) return ringPos(i, age);
        if (age >= landTick(i)) return null;
        float t = (age - dropTick(i)) / FALL;
        Vec3 from = ringPos(i, dropTick(i)), to = landPos(i);
        return from.lerp(to, t * t);
    }

    /** The giant finale blade's height above the centre, or NaN before it appears / after it lands. */
    public float giantHeight(float age) {
        if (age < GIANT_START - 10 || age >= GIANT_LAND) return Float.NaN;
        if (age < GIANT_START) return GIANT_HEIGHT;
        float t = (age - GIANT_START) / GIANT_FALL;
        return GIANT_HEIGHT * (1F - t * t);
    }

    // ------------------------------------------------------------------ ticking

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            clientTick();
            return;
        }
        ServerLevel server = (ServerLevel) level();
        if (tickCount > END || caster == null || caster.isRemoved() && !(caster instanceof net.minecraftforge.common.util.FakePlayer)) {
            discard();
            return;
        }
        if (tickCount == 1) {
            server.playSound(null, getX(), getY() + RING_HEIGHT, getZ(), com.xenowarriors.ModSounds.HEKUBLADE_SUMMON.get(), SoundSource.PLAYERS, 1.6F, 0.7F);
            server.playSound(null, getX(), getY() + RING_HEIGHT, getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.2F, 0.8F);
        }
        for (int i = 0; i < BLADES; i++) {
            if (tickCount == dropTick(i) && wave(i) == 0 && i < WAVES * 2)
                server.playSound(null, getX(), getY() + RING_HEIGHT, getZ(), SoundEvents.TRIDENT_THROW, SoundSource.PLAYERS, 1F, 0.8F);
            if (tickCount == landTick(i)) impact(server, position().add(landPos(i)));
        }
        if (tickCount == GIANT_START) server.playSound(null, getX(), getY() + GIANT_HEIGHT, getZ(), SoundEvents.TRIDENT_RIPTIDE_3, SoundSource.PLAYERS, 1.5F, 0.6F);
        if (tickCount == GIANT_LAND) finale(server);
    }

    /** Hits are dealt once per blade; this logs them for the tests. */
    private void hurt(LivingEntity e, float amount, String what) {
        e.invulnerableTime = 0;
        float before = e.getHealth();
        e.hurt(ModDamageTypes.source(level(), ModDamageTypes.HEKUBLADE_RAIN, this, caster), amount);
        if (LOG) com.xenowarriors.XenoWarriors.LOGGER.info("XW-TEST mikiko rain {} t={} {} {} hp {} -> {}", what, tickCount, e.getType().toShortString(),
                e.getName().getString(), String.format("%.1f", before), String.format("%.1f", e.getHealth()));
    }

    /** Tests switch this on to log every hit. */
    public static boolean LOG;

    private void impact(ServerLevel server, Vec3 at) {
        server.sendParticles(ParticleTypes.CRIT, at.x, at.y + 0.2, at.z, 10, 0.3, 0.1, 0.3, 0.3);
        server.sendParticles(ParticleTypes.FLAME, at.x, at.y + 0.2, at.z, 8, 0.35, 0.1, 0.35, 0.05);
        server.sendParticles(new DustParticleOptions(new Vector3f(1F, 0.97F, 0.92F), 1.2F), at.x, at.y + 0.3, at.z, 10, 0.4, 0.2, 0.4, 0);
        server.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 1F, 1.1F + random.nextFloat() * 0.3F);
        AABB box = new AABB(at.x - SPLASH_RADIUS, at.y - 2, at.z - SPLASH_RADIUS, at.x + SPLASH_RADIUS, at.y + 3.5, at.z + SPLASH_RADIUS);
        for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, box, e -> foe(caster, e))) {
            double d = Math.hypot(e.getX() - at.x, e.getZ() - at.z);
            if (d > SPLASH_RADIUS) continue;
            boolean direct = d <= HIT_RADIUS + e.getBbWidth() / 2;
            hurt(e, direct ? DAMAGE : SPLASH, direct ? "hit" : "splash");
            if (direct) com.xenowarriors.mikiko.Hekublade.rollFire(e, 2);
            Vec3 out = new Vec3(e.getX() - at.x, 0, e.getZ() - at.z);
            out = out.lengthSqr() < 1.0E-4 ? Vec3.ZERO : out.normalize().scale(0.25);
            e.push(out.x, direct ? 0.15 : 0.05, out.z);
            e.hurtMarked = true;
        }
    }

    private void finale(ServerLevel server) {
        Vec3 at = position();
        server.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y + 0.5, at.z, 1, 0, 0, 0, 0);
        server.sendParticles(ParticleTypes.FLAME, at.x, at.y + 0.5, at.z, 90, 2.5, 0.4, 2.5, 0.15);
        server.sendParticles(ParticleTypes.CRIT, at.x, at.y + 0.5, at.z, 60, 2.5, 0.5, 2.5, 0.5);
        server.sendParticles(new DustParticleOptions(new Vector3f(1F, 0.97F, 0.92F), 2F), at.x, at.y + 1, at.z, 50, 3, 1, 3, 0);
        server.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.4F, 0.7F);
        server.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 1F, 0.5F);
        AABB box = new AABB(at.x - GIANT_RADIUS, at.y - 2, at.z - GIANT_RADIUS, at.x + GIANT_RADIUS, at.y + 5, at.z + GIANT_RADIUS);
        for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, box, e -> foe(caster, e))) {
            double d = Math.hypot(e.getX() - at.x, e.getZ() - at.z);
            if (d > GIANT_RADIUS) continue;
            float k = (float) (1.0 - 0.5 * d / GIANT_RADIUS);                 // full in the middle, half at the edge
            hurt(e, GIANT_DAMAGE * k, "finale");
            com.xenowarriors.mikiko.Hekublade.rollFire(e, 3);
            Vec3 out = new Vec3(e.getX() - at.x, 0, e.getZ() - at.z);
            out = out.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : out.normalize();
            e.setDeltaMovement(e.getDeltaMovement().add(out.x * 0.9, 0.55, out.z * 0.9));
            e.hurtMarked = true;
        }
    }

    /**
     * Who the rain may hit: the caster's enemies only - never the caster, their party, anyone's pets on their side,
     * or party bots (and when a bot casts it, never its owner, the owner's party or the other bots).
     */
    public static boolean foe(ServerPlayer caster, Entity e) {
        if (caster == null || !SkillTargeting.canTarget(caster, e)) return false;
        com.xenowarriors.bot.PartyBotEntity bot = com.xenowarriors.bot.PartyBots.botOf(caster);
        if (bot != null && (e == bot || com.xenowarriors.bot.PartyBots.friendly(bot, e))) return false;
        if (e instanceof com.xenowarriors.bot.PartyBotEntity b && com.xenowarriors.bot.PartyBots.friendly(b, caster)) return false;
        java.util.UUID pet = com.xenowarriors.party.Parties.petOwner(e);
        if (pet != null && (pet.equals(caster.getUUID()) || com.xenowarriors.party.Parties.sameParty(pet, caster.getUUID()))) return false;
        return true;
    }

    private void clientTick() {
        float age = tickCount;
        for (int i = 0; i < BLADES; i++) {
            Vec3 p = bladePos(i, age);
            if (p == null) continue;
            Vec3 w = position().add(p);
            if (age < dropTick(i)) {
                if (random.nextInt(4) == 0) level().addParticle(new DustParticleOptions(new Vector3f(1F, 0.97F, 0.92F), 0.7F), w.x, w.y, w.z, 0, 0, 0);
            } else {
                level().addParticle(ParticleTypes.FLAME, w.x, w.y, w.z, 0, 0.05, 0);
                level().addParticle(ParticleTypes.END_ROD, w.x, w.y + 0.5, w.z, 0, 0.02, 0);
            }
        }
        if (tickCount < RING_UP && tickCount % 2 == 0) {
            for (int k = 0; k < 6; k++) {
                double a = random.nextDouble() * Math.PI * 2;
                level().addParticle(new DustParticleOptions(new Vector3f(0.95F, 0.12F, 0.1F), 1F), getX() + Math.cos(a) * RADIUS, getY() + RING_HEIGHT, getZ() + Math.sin(a) * RADIUS, 0, -0.02, 0);
            }
        }
        float gh = giantHeight(age);
        if (!Float.isNaN(gh)) level().addParticle(ParticleTypes.FLAME, getX(), getY() + gh, getZ(), 0, 0.1, 0);
    }
}

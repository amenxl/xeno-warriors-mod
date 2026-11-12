package com.xenowarriors.party;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.bot.PartyBotEntity;
import com.xenowarriors.network.ModNetwork;
import com.xenowarriors.network.SkillAnimPacket;
import com.xenowarriors.skill.SkillEvents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Hekublade Scatter Barrage - Mikiko and Kaeru's team attack. Back to back, they call up a ring of Hekublades that
 * spin around them and rise into a halo; then the blades scatter, flying out in every direction one after another and
 * cutting through every enemy in their path, coming back and flying out again; at the end every blade fires at once
 * in a final ring that bursts around them.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID)
public final class HekubladeBarrage {
    public static final int BLADES = 12, RISE = 30, HALO = 46, BARRAGE_END = 110, FINALE = 112, END = 132;
    public static final double RANGE = 22, FLY_SPEED = 1.6;
    public static final float HIT = 9F, FINAL_HIT = 24F, FINAL_RADIUS = 14F;
    private static final List<HekubladeBarrage> ACTIVE = new ArrayList<>();
    /** (Tests) blades sent out and enemies cut, last barrage. */
    public static int lastShots, lastHits, started;

    private final ServerLevel level;
    private final LivingEntity mikiko, kaeru;
    private final Vec3 center;
    private final Entity[] blades = new Entity[BLADES];
    private final Vec3[] from = new Vec3[BLADES], to = new Vec3[BLADES];
    private final int[] flyT = new int[BLADES];
    private final List<java.util.Set<Integer>> hitBy = new ArrayList<>();
    private final float power;
    private int t, next;

    private HekubladeBarrage(LivingEntity mikiko, LivingEntity kaeru, float power) {
        this.level = (ServerLevel) mikiko.level();
        this.mikiko = mikiko; this.kaeru = kaeru; this.power = power;
        this.center = mikiko.position().lerp(kaeru.position(), 0.5);
        for (int i = 0; i < BLADES; i++) hitBy.add(new java.util.HashSet<>());
    }

    private static int level(LivingEntity e) {
        return e instanceof PartyBotEntity b ? b.botLevel() : e instanceof ServerPlayer p ? com.xenowarriors.stats.Stats.characterLevel(p) : 1;
    }

    public static void start(LivingEntity mikiko, LivingEntity kaeru) {
        float power = 1F + (level(mikiko) + level(kaeru)) * 0.01F;
        HekubladeBarrage b = new HekubladeBarrage(mikiko, kaeru, power);
        ACTIVE.add(b);
        started++; lastShots = 0; lastHits = 0;
        for (LivingEntity e : new LivingEntity[]{mikiko, kaeru}) {
            e.getPersistentData().putLong(SkillEvents.ULT_UNTIL, e.level().getGameTime() + END + 2);
            if (e instanceof ServerPlayer p) ModNetwork.sendToTrackingAndSelf(new SkillAnimPacket(p.getId(), SkillAnimPacket.RAISE, false), p);
            else ModNetwork.sendToTrackingEntityAndSelf(new SkillAnimPacket(e.getId(), SkillAnimPacket.RAISE, false), e);
        }
        b.level.playSound(null, b.center.x, b.center.y, b.center.z, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.5F, 0.6F);
    }

    private Entity blade(Vec3 at) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", "minecraft:item_display");
        CompoundTag item = new CompoundTag();
        item.putString("id", XenoWarriors.MOD_ID + ":hekublade"); item.putByte("Count", (byte) 1);
        tag.put("item", item);
        tag.putString("item_display", "none");
        tag.putInt("interpolation_duration", 1);
        tag.put("Tags", new net.minecraft.nbt.ListTag());
        ((net.minecraft.nbt.ListTag) tag.get("Tags")).add(StringTag.valueOf("xw_hekublade_barrage"));
        CompoundTag tf = new CompoundTag();
        tf.put("translation", floats(0, 0, 0)); tf.put("scale", floats(1.6F, 1.6F, 1.6F));
        tf.put("left_rotation", floats(0, 0, 0, 1)); tf.put("right_rotation", floats(0, 0, 0, 1));
        tag.put("transformation", tf);
        CompoundTag br = new CompoundTag(); br.putInt("block", 15); br.putInt("sky", 15); tag.put("brightness", br);
        Entity e = EntityType.loadEntityRecursive(tag, level, x -> { x.moveTo(at.x, at.y, at.z, 0, 0); return x; });
        if (e != null) level.addFreshEntity(e);
        return e;
    }

    private static net.minecraft.nbt.ListTag floats(float... v) {
        net.minecraft.nbt.ListTag l = new net.minecraft.nbt.ListTag();
        for (float f : v) l.add(net.minecraft.nbt.FloatTag.valueOf(f));
        return l;
    }

    private static void place(Entity e, Vec3 at, float yaw, float pitch) {
        if (e == null) return;
        e.moveTo(at.x, at.y, at.z, yaw, pitch);
    }

    private boolean alive() {
        return mikiko.isAlive() && kaeru.isAlive() && !(mikiko instanceof PartyBotEntity mb && mb.downed()) && !(kaeru instanceof PartyBotEntity kb && kb.downed());
    }

    private boolean foe(LivingEntity e) { return TeamUltimate.foe(mikiko, kaeru, e); }

    /** One tick; false when it's over. */
    private boolean tick() {
        t++;
        if (!alive() || t > END) return false;
        for (LivingEntity e : new LivingEntity[]{mikiko, kaeru}) { e.fallDistance = 0; if (e instanceof PartyBotEntity b) { b.getNavigation().stop(); b.setDeltaMovement(Vec3.ZERO); } }
        DustParticleOptions pink = new DustParticleOptions(new Vector3f(1F, 0.45F, 0.6F), 1.2F), ember = new DustParticleOptions(new Vector3f(1F, 0.55F, 0.12F), 1.2F);
        if (t <= HALO) {
            // the ring: blades appear one by one around them, spinning, rising into a halo
            int show = Math.min(BLADES, t / 2 + 1);
            for (int i = 0; i < show; i++) {
                if (blades[i] == null) {
                    blades[i] = blade(center.add(0, 1, 0));
                    level.sendParticles(ParticleTypes.FLASH, center.x, center.y + 1.5, center.z, 1, 0, 0, 0, 0);
                    level.playSound(null, center.x, center.y, center.z, SoundEvents.TRIDENT_RETURN, SoundSource.PLAYERS, 0.6F, 1.4F + i * 0.05F);
                }
                double a = i * Math.PI * 2 / BLADES + t * 0.22;
                double rise = Math.min(1.0, t / (double) RISE);
                double r = 2.4 + rise * 1.4, y = 0.8 + rise * 2.6;
                Vec3 at = center.add(Math.cos(a) * r, y, Math.sin(a) * r);
                place(blades[i], at, (float) Math.toDegrees(-a) + 90F, t > RISE ? -90F : 0F);
                if (t % 3 == 0) level.sendParticles(i % 2 == 0 ? pink : ember, at.x, at.y, at.z, 1, 0.1, 0.1, 0.1, 0);
            }
            return true;
        }
        if (t < BARRAGE_END) {
            // the scatter: every few ticks the next blade flies out (at the nearest foe it hasn't hit, else outward)
            if (t >= next) {
                int i = (t - HALO) / 4 % BLADES;
                if (flyT[i] == 0 && blades[i] != null) {
                    from[i] = blades[i].position();
                    LivingEntity aim = null; double best = RANGE * RANGE;
                    for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(RANGE), this::foe)) {
                        double d = e.distanceToSqr(from[i]);
                        if (d < best && (lastShots % 3 != 0 || d > 9)) { best = d; aim = e; }
                    }
                    Vec3 dir = aim != null ? aim.getBoundingBox().getCenter().subtract(from[i]).normalize()
                            : new Vec3(Math.cos(i * Math.PI * 2 / BLADES), -0.15, Math.sin(i * Math.PI * 2 / BLADES)).normalize();
                    to[i] = from[i].add(dir.scale(RANGE));
                    flyT[i] = 1; hitBy.get(i).clear();
                    lastShots++;
                    level.playSound(null, from[i].x, from[i].y, from[i].z, SoundEvents.TRIDENT_THROW, SoundSource.PLAYERS, 0.9F, 1.2F);
                }
                next = t + 4;
            }
        }
        for (int i = 0; i < BLADES; i++) {
            if (blades[i] == null) continue;
            if (flyT[i] > 0) {
                double len = from[i].distanceTo(to[i]);
                int out = (int) Math.ceil(len / FLY_SPEED), back = out;
                Vec3 at;
                if (flyT[i] <= out) at = from[i].lerp(to[i], flyT[i] / (double) out);
                else at = to[i].lerp(halo(i), (flyT[i] - out) / (double) back);
                Vec3 d = flyT[i] <= out ? to[i].subtract(from[i]) : halo(i).subtract(to[i]);
                place(blades[i], at, (float) Math.toDegrees(Math.atan2(-d.x, d.z)), (float) -Math.toDegrees(Math.asin(Mth.clamp(d.normalize().y, -1, 1))));
                level.sendParticles(i % 2 == 0 ? pink : ember, at.x, at.y, at.z, 2, 0.05, 0.05, 0.05, 0);
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(1.1), this::foe)) {
                    if (!hitBy.get(i).add(e.getId())) continue;
                    e.invulnerableTime = 0;
                    e.hurt(level.damageSources().mobAttack(i % 2 == 0 ? mikiko : kaeru), HIT * power);
                    e.knockback(0.4, -d.x, -d.z);
                    com.xenowarriors.mikiko.Hekublade.rollFire(e, 3);
                    level.sendParticles(ParticleTypes.SWEEP_ATTACK, e.getX(), e.getY() + e.getBbHeight() / 2, e.getZ(), 1, 0, 0, 0, 0);
                    lastHits++;
                }
                if (++flyT[i] > out + back) flyT[i] = 0;
            } else {
                double a = i * Math.PI * 2 / BLADES + t * 0.22;
                place(blades[i], halo(i), (float) Math.toDegrees(-a) + 90F, -90F);
            }
        }
        if (t == FINALE) {
            // the finale: every blade at once, outward in a ring, and a burst around them
            level.playSound(null, center.x, center.y, center.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.4F, 1.3F);
            for (int k = 0; k < 48; k++) {
                double a = k * Math.PI * 2 / 48;
                for (double r = 2; r <= FINAL_RADIUS; r += 3)
                    level.sendParticles(k % 2 == 0 ? pink : ember, center.x + Math.cos(a) * r, center.y + 1.2, center.z + Math.sin(a) * r, 1, 0.1, 0.1, 0.1, 0);
            }
            for (int i = 0; i < BLADES; i++) {
                if (blades[i] == null) continue;
                from[i] = blades[i].position();
                double a = i * Math.PI * 2 / BLADES;
                to[i] = center.add(Math.cos(a) * FINAL_RADIUS, 1.0, Math.sin(a) * FINAL_RADIUS);
                flyT[i] = 1; hitBy.get(i).clear();
            }
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(FINAL_RADIUS), this::foe)) {
                if (e.distanceToSqr(center) > FINAL_RADIUS * FINAL_RADIUS) continue;
                e.invulnerableTime = 0;
                e.hurt(level.damageSources().mobAttack(mikiko), FINAL_HIT * power);
                Vec3 push = e.position().subtract(center).normalize();
                e.knockback(1.2, -push.x, -push.z);
                lastHits++;
            }
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, center.x, center.y + 1, center.z, 1, 0, 0, 0, 0);
        }
        return true;
    }

    private Vec3 halo(int i) {
        double a = i * Math.PI * 2 / BLADES + t * 0.22;
        return center.add(Math.cos(a) * 3.8, 3.4, Math.sin(a) * 3.8);
    }

    private void end() {
        for (Entity b : blades) if (b != null) { level.sendParticles(ParticleTypes.POOF, b.getX(), b.getY(), b.getZ(), 3, 0.1, 0.1, 0.1, 0.01); b.discard(); }
        for (LivingEntity e : new LivingEntity[]{mikiko, kaeru}) e.getPersistentData().remove(SkillEvents.ULT_UNTIL);
        XenoWarriors.LOGGER.info("XW team ult: Hekublade Scatter Barrage done, {} blades sent, {} hits", lastShots, lastHits);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || ACTIVE.isEmpty()) return;
        ACTIVE.removeIf(b -> { if (b.tick()) return false; b.end(); return true; });
    }

    public static boolean active() { return !ACTIVE.isEmpty(); }

    /** Blades left over from a barrage cut short by the server stopping: gone when they load again. */
    @SubscribeEvent
    public static void onJoin(net.minecraftforge.event.entity.EntityJoinLevelEvent e) {
        if (e.loadedFromDisk() && e.getEntity().getTags().contains("xw_hekublade_barrage")) e.setCanceled(true);
    }
}

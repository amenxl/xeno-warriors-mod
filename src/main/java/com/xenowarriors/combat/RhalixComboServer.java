package com.xenowarriors.combat;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.anim.Poses;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * What Rhalix's own bare-hand string ({@link ComboStyle#RHALIX}) does to whoever it lands on, hit by hit (players and
 * his party bots alike):
 * <ol start="0">
 *     <li>Shadow Rush - the rising claw at the end of the dash-in (the dash is the attacker's own movement)</li>
 *     <li>Low Lunge, 2. Crescent Slash, 3. Crow Wing - light hits (hitstun, barely a push)</li>
 *     <li value="4">Gravity Whirl - knocked down flat on their back, dazed</li>
 *     <li>Reaping Grab - hauled back up off the floor in front of him</li>
 *     <li>Raven Launcher - thrown up into the air</li>
 *     <li>Gravity Burst (finisher) - a dark gravity sphere bursts round them and blasts them far away</li>
 * </ol>
 * Bosses only flinch from the finisher (and heavies): they never get knocked down, lifted or launched. Dust kicks up
 * where someone it threw comes back down.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RhalixComboServer {
    public static final int KNOCKDOWN_TICKS = 18;
    private static final boolean TEST = System.getenv("XW_UITEST_FOCUS") != null;
    /** Thrown targets: {ticks in the air so far, hit that threw them}, until they land. */
    private static final Map<LivingEntity, int[]> THROWN = new WeakHashMap<>();

    private RhalixComboServer() {}

    /** @return true if it dealt with the hit's after-effects itself (otherwise the usual stagger / finisher runs). */
    static boolean after(ComboServer.Ctx c, LivingEntity t, ServerLevel l) {
        if (c.heavy) return false;
        LivingEntity a = c.attacker;
        Vec3 dir = Vec3.directionFromRotation(0, a.getYRot());
        // bosses only flinch; and once someone's had too many hits in a row they shrug off the throws as well
        boolean boss = Stagger.boss(t) || Stagger.immune(t);
        double y = t.getY() + t.getBbHeight() * 0.6;
        String did;
        if (c.style == ComboStyle.RHALIX_AIR || c.hit == ComboStyle.LEAP) did = air(c, t, l, dir, boss, y);
        else switch (c.hit) {
            case ComboStyle.BURST -> {   // Ki Burst: the point-blank blast from his palm pops them up into the air (off the floor too)
                Stagger.hit(t, 16, false, true);
                l.sendParticles(ParticleTypes.FLASH, t.getX(), y, t.getZ(), 1, 0, 0, 0, 0);
                l.sendParticles(ParticleTypes.END_ROD, t.getX(), y, t.getZ(), 14, 0.3, 0.3, 0.3, 0.15);
                l.playSound(null, t.blockPosition(), SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 1F, 0.7F);
                if (boss) { did = "flinch(boss)"; break; }
                Poses.clearState(t);
                t.setDeltaMovement(dir.x * 0.12, 0.5, dir.z * 0.12);
                t.hurtMarked = true;
                THROWN.put(t, new int[]{0, 20});
                did = "pop-up";
            }
            case 4 -> {          // Gravity Whirl: swept off their feet onto their back
                if (boss) { Stagger.hit(t, 11, false, true); did = "flinch(boss)"; break; }
                Stagger.hit(t, KNOCKDOWN_TICKS + 4, false, true);
                t.setDeltaMovement(dir.x * 0.22, 0.2, dir.z * 0.22);
                t.hurtMarked = true;
                Poses.play(t, Poses.KNOCKDOWN, KNOCKDOWN_TICKS);
                THROWN.put(t, new int[]{0, 4});
                l.playSound(null, t.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1F, 0.6F);
                did = "knockdown";
            }
            case 5 -> {          // Reaping Grab: hauled up off the floor, held in front of him
                if (boss) { Stagger.hit(t, 11, false, true); did = "flinch(boss)"; break; }
                Stagger.hit(t, 16, false, true);
                Poses.clearState(t);
                Vec3 spot = a.position().add(dir.scale(1.25));
                Vec3 pull = spot.subtract(t.position()).multiply(1, 0, 1);
                if (pull.length() > 1.0) pull = pull.normalize();
                t.setDeltaMovement(pull.x * 0.35, 0.46, pull.z * 0.35);
                t.hurtMarked = true;
                THROWN.put(t, new int[]{0, 5});
                l.playSound(null, t.blockPosition(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 0.9F, 0.8F);
                did = "lift";
            }
            case 6 -> {          // Raven Launcher: thrown up into the air
                if (boss) { Stagger.hit(t, 11, false, true); did = "flinch(boss)"; break; }
                Stagger.hit(t, 18, false, true);
                Poses.clearState(t);
                t.setDeltaMovement(dir.x * 0.08, 0.62, dir.z * 0.08);
                t.hurtMarked = true;
                THROWN.put(t, new int[]{0, 6});
                l.sendParticles(ParticleTypes.FLASH, t.getX(), y, t.getZ(), 1, 0, 0, 0, 0);
                l.playSound(null, t.blockPosition(), SoundEvents.PHANTOM_FLAP, SoundSource.PLAYERS, 1.2F, 0.7F);
                l.playSound(null, t.blockPosition(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1F, 0.7F);
                did = "launch";
            }
            case 7 -> {          // Gravity Burst: the dark sphere bursts round them and blasts them away
                Stagger.clear(t);
                Stagger.hit(t, 14, true, true);
                Poses.clearState(t);
                if (!boss) {
                    t.setDeltaMovement(dir.x * 1.55, 0.42, dir.z * 1.55);
                    t.hurtMarked = true;
                    THROWN.put(t, new int[]{0, 7});
                }
                // (client KiFx kind 3 = BURST: a black core in a purple shell blowing outward; caster -1 so it ends no Xeno Blast orb)
                com.xenowarriors.network.ModNetwork.CHANNEL.send(net.minecraftforge.network.PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> t),
                        new com.xenowarriors.network.XenoFxPacket(-1, 3, t.getId(), true,
                                (float) t.getX(), (float) y, (float) t.getZ(), 0, 0, 0, 1.9F, 16));
                l.sendParticles(ParticleTypes.REVERSE_PORTAL, t.getX(), y, t.getZ(), 30, 0.5, 0.5, 0.5, 0.25);
                l.playSound(null, t.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.55F, 1.5F);
                l.playSound(null, t.blockPosition(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.1F, 0.7F);
                did = boss ? "flinch(boss)" : "blast";
            }
            default -> {         // the light hits: hitstun, barely a push
                Stagger.hit(t, 11, false, true);
                l.playSound(null, t.blockPosition(), c.hit == 3 ? SoundEvents.PHANTOM_FLAP : SoundEvents.PLAYER_ATTACK_SWEEP,
                        SoundSource.PLAYERS, 0.7F, 1.1F + c.hit * 0.06F);
                did = "stagger";
            }
        }
        if (TEST) XenoWarriors.LOGGER.info(String.format("XW-TEST rhalixcombo landed style=" + c.style + " hit=%d by=%s on=%s dmg=%.2f mult=%.2f effect=%s vel=(%.2f,%.2f,%.2f) staggered=%s tick=%d",
                c.hit, a.getType().getDescriptionId().replace("entity.", ""), t.getType().getDescriptionId().replace("entity.minecraft.", ""),
                c.dealt, c.mult, did, t.getDeltaMovement().x, t.getDeltaMovement().y, t.getDeltaMovement().z, Stagger.staggered(t), l.getGameTime()));
        return true;
    }

    /** The air string: held up in front of him through the slashes, then the slam spikes them down into the ground. */
    private static String air(ComboServer.Ctx c, LivingEntity t, ServerLevel l, Vec3 dir, boolean boss, double y) {
        LivingEntity a = c.attacker;
        if (c.style == ComboStyle.RHALIX_AIR && c.hit >= c.style.length() - 1) {   // the spike slam
            Stagger.clear(t);
            Stagger.hit(t, 20, true, true);
            Poses.clearState(t);
            if (boss) return "flinch(boss)";
            t.setDeltaMovement(dir.x * 0.25, -1.8, dir.z * 0.25);
            t.hurtMarked = true;
            THROWN.put(t, new int[]{0, 14});
            l.sendParticles(ParticleTypes.SQUID_INK, t.getX(), y, t.getZ(), 12, 0.3, 0.3, 0.3, 0.05);
            l.playSound(null, t.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.1F, 0.6F);
            return "spike";
        }
        Stagger.hit(t, 12, false, true);
        l.playSound(null, t.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.7F, 1.05F + c.hit * 0.08F);
        if (boss) return "stagger";
        // held up in the air just in front of him (the first one lifts them if they're still standing)
        Vec3 spot = a.position().add(dir.scale(1.3)).add(0, 0.25, 0);
        Vec3 pull = spot.subtract(t.position()).scale(0.3);
        boolean first = c.hit == 0 || c.hit == ComboStyle.LEAP;
        double up = first ? Math.max(0.45, pull.y + 0.3) : Mth.clamp(pull.y * 0.5 + 0.14, 0.05, 0.4);
        t.setDeltaMovement(pull.x, up, pull.z);
        t.hurtMarked = true;
        t.fallDistance = 0;
        return first ? "air-lift" : "air-hold";
    }

    /** Thrown targets landing: a cloud of dust. */
    @SubscribeEvent
    public static void onTick(LivingEvent.LivingTickEvent event) {
        LivingEntity e = event.getEntity();
        if (e.level().isClientSide || THROWN.isEmpty()) return;
        int[] s = THROWN.get(e);
        if (s == null) return;
        if (++s[0] > 120 || !e.isAlive()) { THROWN.remove(e); return; }
        if (s[0] < 3 || !e.onGround() || !(e.level() instanceof ServerLevel l)) return;
        THROWN.remove(e);
        BlockState below = l.getBlockState(e.blockPosition().below());
        int n = s[1] >= 7 ? 26 : 12;
        if (s[1] == 14) l.sendParticles(ParticleTypes.EXPLOSION, e.getX(), e.getY() + 0.2, e.getZ(), 2, 0.3, 0.1, 0.3, 0);   // (the slam: a crater of dust)
        if (!below.isAir()) l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, below), e.getX(), e.getY() + 0.1, e.getZ(), n, 0.5, 0.05, 0.5, 0.15);
        l.sendParticles(ParticleTypes.POOF, e.getX(), e.getY() + 0.15, e.getZ(), n / 2, 0.45, 0.05, 0.45, 0.04);
        l.playSound(null, e.blockPosition(), SoundEvents.GENERIC_SMALL_FALL, SoundSource.PLAYERS, 1F, 0.7F);
        if (TEST) XenoWarriors.LOGGER.info(String.format("XW-TEST rhalixcombo landed-dust after hit=%d on=%s at=(%.1f,%.1f,%.1f) airTicks=%d",
                s[1], e.getType().getDescriptionId().replace("entity.minecraft.", ""), e.getX(), e.getY(), e.getZ(), s[0]));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.getServer().getTickCount() % 400 == 0) THROWN.keySet().removeIf(e -> e.isRemoved());
    }
}

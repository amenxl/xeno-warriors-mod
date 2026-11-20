package com.xenowarriors.vehicle;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.corrupted.CorruptedSoulEntity;
import com.xenowarriors.corrupted.ModCorrupted;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Ghost ships of the Corrupted Souls. Now and then, out on the open ocean (the Overworld, well away from land), one
 * comes over the horizon for you: a black galleon with torn purple sails, crewed by corrupted souls, that circles
 * you and fires its cannons. Sink it (it has 220 health; the crew can be picked off too) and its treasure chest is
 * left floating on the waves, and everyone nearby gets some Money. It gives up and fades if you get far away.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CorruptShips {
    private CorruptShips() {}

    public static final String CREW = "xw_ghost_crew", BALL = "xw_cannonball";
    private static final String NEXT = "xw_ghost_ship_next", TARGET = "xw_ghost_target", ALONE = "xw_ghost_alone";
    /** How often each player at sea is checked (ticks) and the chance a ship comes then; the least time between two for one player. */
    static final int CHECK = 400, GAP = 20 * 60 * 6;
    static final float CHANCE = 0.12F;
    /** (Tests) ships come, cannonballs fired, ships sunk. */
    public static int spawned, fired, sunk;

    // ------------------------------------------------------------------ coming for you

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p.tickCount % CHECK != 77) return;
        if (!(p.level() instanceof ServerLevel l) || l.dimension() != Level.OVERWORLD || p.isCreative() || p.isSpectator()) return;
        if (l.getGameTime() < p.getPersistentData().getLong(NEXT) || p.getRandom().nextFloat() >= CHANCE) return;
        if (!atSea(l, p)) return;
        if (spawnFor(p) != null) p.getPersistentData().putLong(NEXT, l.getGameTime() + GAP);
    }

    /** Out on the open ocean: on (or in) the water, in an ocean, no land within 24 blocks around. */
    public static boolean atSea(ServerLevel l, ServerPlayer p) {
        Entity at = p.getRootVehicle();
        if (!at.isInWater() && !(at instanceof VehicleEntity v && v.afloat()) && !l.getFluidState(at.blockPosition().below()).is(FluidTags.WATER)) return false;
        if (!l.getBiome(p.blockPosition()).is(BiomeTags.IS_OCEAN)) return false;
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4;
            if (!open(l, (int) Math.floor(p.getX() + Math.cos(a) * 24), (int) Math.floor(p.getZ() + Math.sin(a) * 24), 2)) return false;
        }
        return true;
    }

    /** The top of this column is water at least `depth` deep (open water: nothing standing out of it). */
    private static boolean open(ServerLevel l, int x, int z, int depth) {
        int top = l.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
        for (int k = 0; k < depth; k++) if (!l.getFluidState(new BlockPos(x, top - k, z)).is(FluidTags.WATER)) return false;
        return true;
    }

    /** A ghost ship comes for this player (40-56 blocks off, on open water). The ship, or null if there was no room. */
    public static VehicleEntity spawnFor(ServerPlayer p) {
        if (!(p.level() instanceof ServerLevel l)) return null;
        if (!l.getEntitiesOfClass(VehicleEntity.class, p.getBoundingBox().inflate(200), v -> v.kind.corrupt()).isEmpty()) return null;   // (one at a time)
        for (int tries = 0; tries < 12; tries++) {
            double a = p.getRandom().nextDouble() * Math.PI * 2, r = 40 + p.getRandom().nextDouble() * 16;
            int x = (int) Math.floor(p.getX() + Math.cos(a) * r), z = (int) Math.floor(p.getZ() + Math.sin(a) * r);
            if (!open(l, x, z, 4) || !open(l, x + 6, z, 3) || !open(l, x - 6, z, 3) || !open(l, x, z + 6, 3) || !open(l, x, z - 6, 3)) continue;
            int top = l.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
            double surf = top + l.getFluidState(new BlockPos(x, top, z)).getHeight(l, new BlockPos(x, top, z));
            VehicleEntity v = place(l, x + 0.5, surf - VehicleEntity.DRAFT, z + 0.5, p);
            if (v != null) {
                p.displayClientMessage(Component.literal("A ghost ship rises from the mist... its cannons turn toward you!").withStyle(ChatFormatting.DARK_PURPLE), false);
                l.playSound(null, v.blockPosition(), SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.HOSTILE, 1.4F, 0.6F);
                return v;
            }
        }
        return null;
    }

    /** (Tests too) the ship and its crew of corrupted souls, here, hunting this player. */
    public static VehicleEntity place(ServerLevel l, double x, double y, double z, ServerPlayer p) {
        VehicleEntity v = Vehicles.TYPES.get(VehicleKind.CORRUPT_SHIP).get().create(l);
        if (v == null) return null;
        float yaw = (float) (Mth.atan2(p.getZ() - z, p.getX() - x) * Mth.RAD_TO_DEG) - 90F;
        v.moveTo(x, y, z, yaw, 0F);
        v.yBodyRot = yaw; v.yHeadRot = yaw;
        v.getPersistentData().putUUID(TARGET, p.getUUID());
        l.addFreshEntity(v);
        l.sendParticles(ParticleTypes.SOUL, x, y + 3, z, 40, 3, 2, 5, 0.02);
        int crew = 4 + l.random.nextInt(3);
        for (int i = 0; i < crew; i++) {
            CorruptedSoulEntity s = ModCorrupted.CORRUPTED_SOUL.get().create(l);
            if (s == null) continue;
            s.moveTo(x, y + 3, z, yaw, 0F);
            s.getPersistentData().putBoolean(CorruptedSoulEntity.MISSION, true);   // (out at sea by day too)
            s.addTag(CREW);
            if (i == 0) s.addTag(VehicleEntity.NPC_DRIVER_TAG);                      // (the captain steers)
            s.setPersistenceRequired();
            l.addFreshEntity(s);
            s.startRiding(v, true);
        }
        spawned++;
        return v;
    }

    // ------------------------------------------------------------------ at sea

    /** Every server tick of a ghost ship: it circles its quarry and fires; with nobody about for a minute it fades. */
    static void tick(VehicleEntity v) {
        if (!(v.level() instanceof ServerLevel l)) return;
        Player t = quarry(v, l);
        if (t == null) {
            int alone = v.getPersistentData().getInt(ALONE) + 1;
            v.getPersistentData().putInt(ALONE, alone);
            v.npcDriveTo(v.position(), 0);
            if (alone > 20 * 60) fade(v, l);
            return;
        }
        v.getPersistentData().putInt(ALONE, 0);
        // sail: keep ~22 blocks off, circling round so its side (and its guns) face you
        Vec3 from = new Vec3(v.getX() - t.getX(), 0, v.getZ() - t.getZ());
        if (from.lengthSqr() < 1) from = new Vec3(1, 0, 0);
        Vec3 out = from.normalize();
        Vec3 round = new Vec3(-out.z, 0, out.x);
        Vec3 dest = t.position().add(out.scale(22)).add(round.scale(14));
        v.npcDriveTo(dest, 0.85);
        // the guns
        int every = 50 + (v.getId() % 3) * 7;
        double d = Math.sqrt(v.distanceToSqr(t));
        if (v.tickCount % every == 0 && d < 48 && d > 5 && crew(v) > 0) {
            fire(v, l, t);
            if (l.random.nextBoolean()) fire(v, l, t);
        }
    }

    /** Who it's after: its player while they're within 96 blocks, else the nearest one in 64 (not in creative). */
    private static Player quarry(VehicleEntity v, ServerLevel l) {
        if (v.getPersistentData().hasUUID(TARGET)) {
            Player p = l.getPlayerByUUID(v.getPersistentData().getUUID(TARGET));
            if (p != null && p.isAlive() && !p.isSpectator() && !p.isCreative() && p.distanceToSqr(v) < 96 * 96) return p;
        }
        Player p = l.getNearestPlayer(v.getX(), v.getY(), v.getZ(), 64, x -> x.isAlive() && !x.isSpectator() && !((Player) x).isCreative());
        if (p != null) v.getPersistentData().putUUID(TARGET, p.getUUID());
        return p;
    }

    private static int crew(VehicleEntity v) {
        int n = 0;
        for (Entity e : v.getPassengers()) if (e instanceof CorruptedSoulEntity && e.isAlive()) n++;
        return n;
    }

    /** One cannonball from the side facing them, lobbed at them (a little off). */
    static void fire(VehicleEntity v, ServerLevel l, Entity t) {
        Entity gunner = null;
        for (Entity e : v.getPassengers()) if (e instanceof CorruptedSoulEntity) { gunner = e; break; }
        float yaw = v.getYRot() * Mth.DEG_TO_RAD;
        Vec3 right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw)), fwd = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
        Vec3 to = t.position().subtract(v.position());
        double side = Math.signum(to.dot(right)); if (side == 0) side = 1;
        double along = (l.random.nextInt(3) - 1) * 2.25;                          // (one of its three gun ports)
        Vec3 muzzle = v.position().add(right.scale(side * 2.9)).add(fwd.scale(along)).add(0, 1.8, 0);
        Snowball ball = new Snowball(l, muzzle.x, muzzle.y, muzzle.z);
        if (gunner != null) ball.setOwner(gunner);
        ball.setItem(new ItemStack(Items.FIRE_CHARGE));
        ball.addTag(BALL);
        double dx = t.getX() - muzzle.x, dz = t.getZ() - muzzle.z, dy = t.getY() + 0.5 - muzzle.y, flat = Math.sqrt(dx * dx + dz * dz);
        ball.shoot(dx, dy + flat * 0.13, dz, 1.55F, 5F);
        l.addFreshEntity(ball);
        l.sendParticles(ParticleTypes.LARGE_SMOKE, muzzle.x, muzzle.y, muzzle.z, 8, 0.2, 0.2, 0.2, 0.03);
        l.sendParticles(ParticleTypes.FLAME, muzzle.x, muzzle.y, muzzle.z, 5, 0.1, 0.1, 0.1, 0.05);
        l.playSound(null, muzzle.x, muzzle.y, muzzle.z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.6F, 0.55F + l.random.nextFloat() * 0.15F);
        fired++;
    }

    /** A cannonball lands: it bursts (no blocks broken). It flies past the ship and crew that fired it. */
    @SubscribeEvent
    public static void onImpact(ProjectileImpactEvent e) {
        Entity ball = e.getProjectile();
        if (!ball.getTags().contains(BALL) || !(ball.level() instanceof ServerLevel l)) return;
        if (e.getRayTraceResult() instanceof EntityHitResult eh && (eh.getEntity().getTags().contains(CREW) || eh.getEntity() instanceof VehicleEntity v && v.kind.corrupt())) {
            e.setImpactResult(ProjectileImpactEvent.ImpactResult.SKIP_ENTITY);
            return;
        }
        Vec3 at = e.getRayTraceResult().getLocation();
        Entity owner = ((Snowball) ball).getOwner();
        l.explode(owner, at.x, at.y, at.z, 2.2F, false, Level.ExplosionInteraction.NONE);
        e.setImpactResult(ProjectileImpactEvent.ImpactResult.STOP_AT_CURRENT_NO_DAMAGE);
        ball.discard();
    }

    /** Its own cannonball (the ship can't hurt itself). */
    static boolean ownShot(VehicleEntity v, DamageSource src) {
        Entity d = src.getDirectEntity();
        return d != null && d.getTags().contains(BALL);
    }

    // ------------------------------------------------------------------ the end of it

    /** Sunk: its crew is loosed (and burns by day again), its treasure chest floats where it went down, Money for all nearby. */
    static void sunk(VehicleEntity v, DamageSource src) {
        if (!(v.level() instanceof ServerLevel l)) return;
        sunk++;
        for (CorruptedSoulEntity s : l.getEntitiesOfClass(CorruptedSoulEntity.class, v.getBoundingBox().inflate(12), x -> x.getTags().contains(CREW))) {
            s.getPersistentData().remove(CorruptedSoulEntity.MISSION);
            s.removeTag(VehicleEntity.NPC_DRIVER_TAG);
        }
        l.sendParticles(ParticleTypes.SOUL, v.getX(), v.getY() + 2, v.getZ(), 60, 3, 2, 5, 0.05);
        l.sendParticles(ParticleTypes.SPLASH, v.getX(), v.getY() + 1.2, v.getZ(), 120, 3, 0.5, 6, 0.2);
        l.playSound(null, v.blockPosition(), SoundEvents.WITHER_DEATH, SoundSource.HOSTILE, 0.7F, 1.4F);
        // the chest, afloat at the surface (or on whatever's there)
        double top = v.waterTop();
        BlockPos at = Double.isNaN(top) ? v.blockPosition() : BlockPos.containing(v.getX(), top - 0.5, v.getZ());
        for (int k = 0; k < 3 && !(l.getBlockState(at).isAir() || l.getBlockState(at).is(Blocks.WATER)); k++) at = at.above();
        boolean wet = l.getFluidState(at).is(FluidTags.WATER);
        if (l.setBlock(at, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.WATERLOGGED, wet), 3) && l.getBlockEntity(at) instanceof ChestBlockEntity c)
            c.setLootTable(l.random.nextInt(3) == 0 ? BuiltInLootTables.BURIED_TREASURE : BuiltInLootTables.SHIPWRECK_TREASURE, l.random.nextLong());
        long pay = 300 + l.random.nextInt(301);
        for (ServerPlayer p : l.getEntitiesOfClass(ServerPlayer.class, v.getBoundingBox().inflate(64))) {
            com.xenowarriors.economy.Money.add(p, pay, true);
            p.displayClientMessage(Component.literal("The ghost ship sinks! Its treasure chest is left floating on the waves.").withStyle(ChatFormatting.GOLD), false);
        }
    }

    /** It gives up (nobody about): it and its crew sink away into the mist. */
    private static void fade(VehicleEntity v, ServerLevel l) {
        l.sendParticles(ParticleTypes.SOUL, v.getX(), v.getY() + 2, v.getZ(), 40, 3, 2, 5, 0.02);
        for (Entity e : new java.util.ArrayList<>(v.getPassengers())) e.discard();
        v.discard();
    }
}

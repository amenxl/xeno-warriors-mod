package com.xenowarriors.forms;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.List;

/**
 * A gun made from the Hekublade or Rhalix's wires. Works without TACZ but handles like a TACZ gun: left click fires,
 * hold right click to aim down the sights (see {@link GunSpec} and client/forms/GunClient). Hitscan shots, infinite
 * ammo, but it wears out - the durability grows with the gun level - and once it breaks there's a wait before another
 * gun can be summoned. Hits feed the gun level (and its damage bonus).
 * <ul>
 * <li>Pistol: semi-auto, mid range.</li>
 * <li>Rifle: full auto, long range, scoped, pierces one target.</li>
 * <li>Shotgun: pump action, 7 pellets in a spread, short range.</li>
 * </ul>
 * Hekublade guns sometimes set what they hit on fire; wire guns snag it (a moment of Slowness).
 */
public class FormGunItem extends Item implements FormItems.Summoned, com.xenowarriors.item.Guns.Marker {
    public static final String DUR = "xw_dur", DUR_MAX = "xw_dur_max";
    /** Players aiming down the sights right now (server side). */
    private static final java.util.Set<java.util.UUID> AIMING = new java.util.HashSet<>();
    /** Test numbers: the mean angle (degrees) the last shot's bullets left the line of sight by, and whether it was aimed. */
    public static float LAST_DEV;
    public static boolean LAST_AIMED;
    private final Form.Style style;
    private final Form form;

    public FormGunItem(Form.Style style, Form form, Properties p) {
        super(p);
        this.style = style;
        this.form = form;
    }

    @Override public Form.Style style() { return style; }
    @Override public Form form() { return form; }
    public GunSpec spec() { return GunSpec.of(form); }

    /** {damage, ticks between shots, range, pellets, pellet cone (hip, degrees)} */
    public static float[] stats(Form f) {
        GunSpec g = GunSpec.of(f);
        return new float[]{g.damage, g.interval, g.range, g.pellets, g.pelletHip};
    }

    /** Shots it holds before it breaks: 40 + 3 per gun level (190 at 50, 1,540 at 500), up to 5,000. */
    public static int durabilityFor(int gunLevel) {
        return Math.min(5000, 40 + 3 * Math.max(1, gunLevel));
    }

    public static int dur(ItemStack s) { return s.hasTag() ? s.getTag().getInt(DUR) : 0; }
    public static int durMax(ItemStack s) { return s.hasTag() ? Math.max(1, s.getTag().getInt(DUR_MAX)) : 1; }

    @Override public boolean isBarVisible(ItemStack stack) { return stack.hasTag() && stack.getTag().contains(DUR); }
    @Override public int getBarWidth(ItemStack stack) { return Math.round(13F * dur(stack) / durMax(stack)); }
    @Override public int getBarColor(ItemStack stack) { return style == Form.Style.HEKU ? 0xFF7A1A : 0xB04CFF; }
    @Override public boolean isFoil(ItemStack stack) { return false; }

    /** Left click shoots - it never digs. */
    @Override
    public boolean canAttackBlock(net.minecraft.world.level.block.state.BlockState state, Level level, net.minecraft.core.BlockPos pos, Player player) { return false; }

    /** No arm swing (the gun has its own recoil). */
    @Override
    public boolean onEntitySwing(ItemStack stack, LivingEntity entity) { return true; }

    /** The durability ticking down isn't a new item: no re-equip bob on every shot. */
    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || oldStack.getItem() != newStack.getItem();
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tip, TooltipFlag flag) {
        GunSpec g = spec();
        tip.add(Component.literal("Ammo: unlimited").withStyle(ChatFormatting.AQUA));
        tip.add(Component.literal(g.fireModeLabel() + ", " + g.rpm() + " RPM" + (g.pellets > 1 ? ", " + g.pellets + " pellets" : "")).withStyle(ChatFormatting.GRAY));
        tip.add(Component.literal("Left click: fire - hold right click: aim").withStyle(ChatFormatting.DARK_GRAY));
        if (isBarVisible(stack)) tip.add(Component.literal("Durability: " + dur(stack) + " / " + durMax(stack)).withStyle(ChatFormatting.GRAY));
        FormTools.tooltip(stack, tip, style);
    }

    /** Right click aims (handled on the client, like TACZ) - it doesn't fire. */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return InteractionResultHolder.pass(player.getItemInHand(hand));
    }

    // ------------------------------------------------------------------ aiming down the sights (server side: who's aimed)

    public static boolean aiming(Player p) { return AIMING.contains(p.getUUID()); }

    /** Aim in / out (from the client); everyone around sees the aim pose and hears it. */
    public static void setAiming(ServerPlayer p, boolean on) {
        if (on && !(p.getMainHandItem().getItem() instanceof FormGunItem)) on = false;
        boolean changed = on ? AIMING.add(p.getUUID()) : AIMING.remove(p.getUUID());
        if (!changed) return;
        com.xenowarriors.network.ModNetwork.sendToTrackingAndSelf(
                new com.xenowarriors.network.WireFxPacket(p.getId(), com.xenowarriors.network.WireFxPacket.AIM, -1, on ? 1 : 0), p);
        GunSounds.aim(p.level(), p, p.getX(), p.getY(), p.getZ(), on);
    }

    /** Every tick: aim ends when the gun leaves the hand. */
    public static void tickAim(ServerPlayer p) {
        if (AIMING.contains(p.getUUID()) && (!(p.getMainHandItem().getItem() instanceof FormGunItem) || !p.isAlive())) setAiming(p, false);
    }

    public static void forget(java.util.UUID id) { AIMING.remove(id); RATE.remove(id); }

    // ------------------------------------------------------------------ shooting

    /** Fire rate on the server: {shots in hand (up to 2), game time last topped up}. */
    private static final java.util.Map<java.util.UUID, double[]> RATE = new java.util.HashMap<>();

    /**
     * May this player fire now? One shot per {@code interval} ticks on average, with one shot of slack so packets that
     * arrive bunched up (network jitter) aren't dropped - the client already timed them.
     */
    private static boolean rate(ServerPlayer p, GunSpec g) {
        long now = p.serverLevel().getGameTime();
        double[] r = RATE.computeIfAbsent(p.getUUID(), k -> new double[]{2, now});
        r[0] = Math.min(2.0, r[0] + (now - r[1]) / (double) g.interval);
        r[1] = now;
        if (r[0] < 0.999) return false;
        r[0] -= 1.0;
        return true;
    }

    /** One shot where the player looks, aimed if they're aiming (tests; uses the item cooldown, which a test can clear). */
    public int fire(ServerPlayer p, ItemStack stack) {
        if (p.getCooldowns().isOnCooldown(this)) return 0;
        p.getCooldowns().addCooldown(this, Math.max(1, spec().interval - 1));
        boolean a = aiming(p);
        return shoot(p, stack, a, a ? spec().adsSpread : spec().hipSpread, p.getXRot(), p.getYRot());
    }

    /** A shot from the client (GunActionPacket), held to the gun's fire rate. */
    public int fire(ServerPlayer p, ItemStack stack, boolean aimed, float spread, float pitch, float yaw) {
        if (com.xenowarriors.party.Duels.cantAct(p) || p.isSpectator() || !rate(p, spec())) return 0;
        return shoot(p, stack, aimed, spread, pitch, yaw);
    }

    /**
     * One shot (or one shotgun blast) toward pitch / yaw (what the client saw; the server's own view if that's far off)
     * with the client's spread (clamped to what the gun can have). Returns how many targets were hit.
     */
    private int shoot(ServerPlayer p, ItemStack stack, boolean aimed, float spread, float pitch, float yaw) {
        if (com.xenowarriors.party.Duels.cantAct(p) || p.isSpectator()) return 0;
        GunSpec g = spec();
        ServerLevel level = p.serverLevel();
        if (Math.abs(Mth.wrapDegrees(yaw - p.getYRot())) > 30F || Math.abs(pitch - p.getXRot()) > 30F) { pitch = p.getXRot(); yaw = p.getYRot(); }
        Vec3 eye = p.getEyePosition(), look = Vec3.directionFromRotation(pitch, yaw);
        aimed = aimed && aiming(p);
        spread = Mth.clamp(spread, g.adsSpread * 0.5F, g.maxSpread());
        float pellet = aimed ? g.pelletAds : g.pelletHip;
        float dmg = g.damage * com.xenowarriors.compat.GunLevel.multiplier(p) * (1F + com.xenowarriors.stats.Stat.effective(WeaponForms.weaponLevel(p)) * 0.005F);
        int hits = 0;
        int[] kills = {0};
        double dev = 0;
        Vec3 aimDir = GunSpec.cone(look, spread, p.getRandom());
        for (int i = 0; i < g.pellets; i++) {
            Vec3 dir = GunSpec.cone(aimDir, pellet, p.getRandom());
            dev += Math.toDegrees(Math.acos(Mth.clamp(dir.dot(look), -1.0, 1.0)));
            hits += ray(p, level, eye, dir, g.range, dmg, g.pierce, aimed, kills);
        }
        LAST_DEV = (float) (dev / g.pellets);
        LAST_AIMED = aimed;
        // muzzle flash + sound for everyone else (the shooter's own client shows its flash and plays the sound at once)
        Vec3 muzzle = eye.add(look.scale(0.9)).add(0, aimed ? -0.05 : -0.15, 0);
        if (style == Form.Style.HEKU) others(level, p, ParticleTypes.FLAME, muzzle, 4, 0.04, 0.02);
        else others(level, p, ParticleTypes.REVERSE_PORTAL, muzzle, 6, 0.05, 0.03);
        others(level, p, ParticleTypes.SMOKE, muzzle, 2, 0.03, 0.01);
        GunSounds.shot(level, p, p.getX(), p.getEyeY(), p.getZ(), form, style);
        com.xenowarriors.network.ModNetwork.sendToTrackingAndSelf(
                new com.xenowarriors.network.WireFxPacket(p.getId(), com.xenowarriors.network.WireFxPacket.RECOIL, -1, form.ordinal()), p);
        if (hits > 0) com.xenowarriors.network.ModNetwork.sendToPlayer(
                new com.xenowarriors.network.WireFxPacket(p.getId(), com.xenowarriors.network.WireFxPacket.HIT, -1, kills[0] > 0 ? 1 : 0), p);
        // wear: one point a shot; broken = gone, and a wait before the next gun
        if (!p.isCreative() || WeaponForms.testWear) {
            var tag = stack.getOrCreateTag();
            int d = tag.getInt(DUR) - 1;
            tag.putInt(DUR, d);
            WeaponForms.SHOTS++;
            if (d <= 0) WeaponForms.gunBroke(p, stack);
        }
        return hits;
    }

    private static void others(ServerLevel level, ServerPlayer p, net.minecraft.core.particles.ParticleOptions o, Vec3 at, int n, double d, double speed) {
        for (ServerPlayer other : level.players()) if (other != p) level.sendParticles(other, o, false, at.x, at.y, at.z, n, d, d, d, speed);
    }

    private int ray(ServerPlayer p, ServerLevel level, Vec3 from, Vec3 dir, float range, float dmg, int pierce, boolean aimed, int[] kills) {
        Vec3 end = from.add(dir.scale(range));
        BlockHitResult bh = level.clip(new ClipContext(from, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        if (bh.getType() != HitResult.Type.MISS) end = bh.getLocation();
        final Vec3 rayEnd = end;
        List<Entity> found = level.getEntities(p, new AABB(from, end).inflate(1.0),
                e -> e instanceof LivingEntity && e.isAlive() && com.xenowarriors.skill.SkillTargeting.canTarget(p, e));
        found.sort(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(from)));
        int hits = 0;
        Vec3 stop = end;
        for (Entity e : found) {
            var hit = e.getBoundingBox().inflate(0.3).clip(from, rayEnd);
            if (hit.isEmpty()) continue;
            LivingEntity t = (LivingEntity) e;
            t.invulnerableTime = 0;
            if (t.hurt(p.damageSources().playerAttack(p), dmg)) {
                hits++;
                WeaponForms.HITS++;
                com.xenowarriors.compat.GunLevel.addXp(p, Math.min(dmg, t.getHealth() + 1F));
                if (style == Form.Style.HEKU) com.xenowarriors.mikiko.Hekublade.rollFire(t, 3);
                else t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 1, false, true));
                if (!t.isAlive()) { com.xenowarriors.compat.GunLevel.addXp(p, com.xenowarriors.compat.GunLevel.KILL_XP); kills[0]++; }
                Vec3 at = hit.get();
                level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 3, 0.05, 0.05, 0.05, 0.15);
            }
            stop = hit.get();
            if (--pierce <= 0) break;
        }
        if (pierce > 0) stop = end;
        tracer(level, from.add(dir.scale(0.8)).add(0, aimed ? -0.06 : -0.12, 0), stop);
        if (bh.getType() != HitResult.Type.MISS && pierce > 0) {                // bullet impact: sparks, block chips, a puff
            level.sendParticles(style == Form.Style.HEKU ? ParticleTypes.SMALL_FLAME : ParticleTypes.WITCH, end.x, end.y, end.z, 3, 0.05, 0.05, 0.05, 0.01);
            var state = level.getBlockState(bh.getBlockPos());
            if (!state.isAir()) level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, state), end.x, end.y, end.z, 6, 0.05, 0.05, 0.05, 0.12);
            level.sendParticles(ParticleTypes.SMOKE, end.x, end.y, end.z, 2, 0.03, 0.03, 0.03, 0.01);
        }
        return hits;
    }

    private void tracer(ServerLevel level, Vec3 a, Vec3 b) {
        DustParticleOptions dust = style == Form.Style.HEKU ? new DustParticleOptions(new Vector3f(1F, 0.45F, 0.1F), 0.6F)
                : new DustParticleOptions(new Vector3f(0.62F, 0.25F, 1F), 0.6F);
        double len = a.distanceTo(b);
        int n = (int) Math.min(60, len * 2);
        for (int i = 0; i <= n; i++) {
            Vec3 q = a.lerp(b, i / (double) Math.max(1, n));
            level.sendParticles(dust, q.x, q.y, q.z, 1, 0, 0, 0, 0);
        }
    }
}

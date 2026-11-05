package com.xenowarriors.forms;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
 * A gun made from the Hekublade or Rhalix's wires. Works without TACZ: hitscan shots (use / right click, hold to keep
 * firing), infinite ammo, but it wears out - the durability grows with the gun level - and once it breaks there's a
 * wait before another gun can be summoned. Hits feed the gun level (and its damage bonus).
 * <ul>
 * <li>Pistol: quick shots, mid range.</li>
 * <li>Rifle: slow, hard-hitting, long range, pierces one target.</li>
 * <li>Shotgun: 7 pellets in a spread, short range.</li>
 * </ul>
 * Hekublade guns sometimes set what they hit on fire; wire guns snag it (a moment of Slowness).
 */
public class FormGunItem extends Item implements FormItems.Summoned, com.xenowarriors.item.Guns.Marker {
    public static final String DUR = "xw_dur", DUR_MAX = "xw_dur_max";
    private final Form.Style style;
    private final Form form;

    public FormGunItem(Form.Style style, Form form, Properties p) {
        super(p);
        this.style = style;
        this.form = form;
    }

    @Override public Form.Style style() { return style; }
    @Override public Form form() { return form; }

    /** {damage, cooldown ticks, range, pellets, spread} */
    public static float[] stats(Form f) {
        return switch (f) {
            case RIFLE -> new float[]{13F, 18, 80, 1, 0F};
            case SHOTGUN -> new float[]{3.2F, 20, 18, 7, 0.11F};
            default -> new float[]{5F, 5, 40, 1, 0.012F};
        };
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

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.literal("Ammo: unlimited").withStyle(ChatFormatting.AQUA));
        if (isBarVisible(stack)) tip.add(Component.literal("Durability: " + dur(stack) + " / " + durMax(stack)).withStyle(ChatFormatting.GRAY));
        FormTools.tooltip(stack, tip, style);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND) return InteractionResultHolder.pass(stack);
        if (!level.isClientSide && player instanceof ServerPlayer sp) fire(sp, stack);
        return InteractionResultHolder.consume(stack);
    }

    /** One shot (or one shotgun blast). Returns how many targets were hit. */
    public int fire(ServerPlayer p, ItemStack stack) {
        if (com.xenowarriors.party.Duels.cantAct(p) || p.getCooldowns().isOnCooldown(this)) return 0;
        float[] st = stats(form);
        p.getCooldowns().addCooldown(this, (int) st[1]);
        ServerLevel level = p.serverLevel();
        Vec3 eye = p.getEyePosition(), look = p.getViewVector(1F);
        int gun = com.xenowarriors.compat.GunLevel.level(p);
        float dmg = st[0] * com.xenowarriors.compat.GunLevel.multiplier(p) * (1F + com.xenowarriors.stats.Stat.effective(WeaponForms.weaponLevel(p)) * 0.005F);
        int hits = 0;
        for (int i = 0; i < (int) st[3]; i++) {
            Vec3 dir = look.add(p.getRandom().nextGaussian() * st[4], p.getRandom().nextGaussian() * st[4], p.getRandom().nextGaussian() * st[4]).normalize();
            hits += ray(p, level, eye, dir, st[2], dmg, form == Form.RIFLE ? 2 : 1);
        }
        // muzzle flash + sound
        Vec3 muzzle = eye.add(look.scale(0.9)).add(0, -0.15, 0);
        if (style == Form.Style.HEKU) level.sendParticles(ParticleTypes.FLAME, muzzle.x, muzzle.y, muzzle.z, 4, 0.04, 0.04, 0.04, 0.02);
        else level.sendParticles(ParticleTypes.REVERSE_PORTAL, muzzle.x, muzzle.y, muzzle.z, 6, 0.05, 0.05, 0.05, 0.03);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), form == Form.SHOTGUN ? SoundEvents.GENERIC_EXPLODE : SoundEvents.FIREWORK_ROCKET_BLAST,
                SoundSource.PLAYERS, form == Form.SHOTGUN ? 0.5F : 0.8F, form == Form.RIFLE ? 0.6F : form == Form.SHOTGUN ? 1.6F : 1.4F);
        com.xenowarriors.network.ModNetwork.sendToTrackingAndSelf(new com.xenowarriors.network.WireFxPacket(p.getId(), com.xenowarriors.network.WireFxPacket.RECOIL, -1, 0), p);
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

    private int ray(ServerPlayer p, ServerLevel level, Vec3 from, Vec3 dir, float range, float dmg, int pierce) {
        Vec3 end = from.add(dir.scale(range));
        BlockHitResult bh = level.clip(new ClipContext(from, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        if (bh.getType() != HitResult.Type.MISS) end = bh.getLocation();
        List<Entity> found = level.getEntities(p, new AABB(from, end).inflate(1.0),
                e -> e instanceof LivingEntity && e.isAlive() && com.xenowarriors.skill.SkillTargeting.canTarget(p, e));
        found.sort(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(from)));
        int hits = 0;
        Vec3 stop = end;
        for (Entity e : found) {
            var hit = e.getBoundingBox().inflate(0.3).clip(from, end);
            if (hit.isEmpty()) continue;
            LivingEntity t = (LivingEntity) e;
            t.invulnerableTime = 0;
            if (t.hurt(p.damageSources().playerAttack(p), dmg)) {
                hits++;
                WeaponForms.HITS++;
                com.xenowarriors.compat.GunLevel.addXp(p, Math.min(dmg, t.getHealth() + 1F));
                if (style == Form.Style.HEKU) com.xenowarriors.mikiko.Hekublade.rollFire(t, 3);
                else t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 1, false, true));
                if (!t.isAlive()) com.xenowarriors.compat.GunLevel.addXp(p, com.xenowarriors.compat.GunLevel.KILL_XP);
            }
            stop = hit.get();
            if (--pierce <= 0) break;
        }
        if (pierce > 0) stop = end;
        tracer(level, from.add(dir.scale(0.8)).add(0, -0.12, 0), stop);
        if (bh.getType() != HitResult.Type.MISS && pierce > 0)
            level.sendParticles(style == Form.Style.HEKU ? ParticleTypes.SMALL_FLAME : ParticleTypes.WITCH, end.x, end.y, end.z, 3, 0.05, 0.05, 0.05, 0.01);
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

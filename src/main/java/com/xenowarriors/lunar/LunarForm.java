package com.xenowarriors.lunar;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.character.ModCapabilities;
import com.xenowarriors.character.Seasons;
import com.xenowarriors.network.LunarStatePacket;
import com.xenowarriors.network.ModNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Rhalix's Lunar form (Season 2). Once he's passed the Lunar trial, the night turns him: white hair, crescent moons on
 * his cheeks, a crescent and stars hanging behind him, silver Karma. With DragonMineZ, looking at a Fake Moon (the
 * full-moon technique) turns him too, day or night, for a minute.
 *
 * The trial: under a full moon, out in the open, crouch still for 30 seconds - meditating like he did with the moon
 * lady. In Lunar form: stronger, faster, tougher; Moon Reflect turns blows back; the super slot becomes Moon Fall.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LunarForm {
    public static final String UNLOCKED = "xw_lunar_unlocked", ACTIVE = "xw_lunar_on", TRIAL = "xw_lunar_trial",
            FAKE_MOON = "xw_lunar_fake", REFLECT = "xw_lunar_reflect";
    private static final UUID DMG = UUID.fromString("7c1e2a30-4b5d-4e6f-8a9b-1c2d3e4f5a61"), SPD = UUID.fromString("7c1e2a30-4b5d-4e6f-8a9b-1c2d3e4f5a62"),
            ARM = UUID.fromString("7c1e2a30-4b5d-4e6f-8a9b-1c2d3e4f5a63");
    public static final int TRIAL_TICKS = 30 * 20, FAKE_MOON_TICKS = 60 * 20;
    public static final Vector3f SILVER = new Vector3f(0.85F, 0.9F, 1F);

    private LunarForm() {}

    private static net.minecraft.nbt.CompoundTag keep(Player p) {       // (survives death: Forge keeps this sub-tag)
        var root = p.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG)) root.put(Player.PERSISTED_NBT_TAG, new net.minecraft.nbt.CompoundTag());
        return root.getCompound(Player.PERSISTED_NBT_TAG);
    }

    public static boolean unlocked(Player p) { return keep(p).getBoolean(UNLOCKED) || p.getPersistentData().getBoolean(UNLOCKED); }

    public static boolean active(LivingEntity e) { return e.getPersistentData().getBoolean(ACTIVE); }

    public static void setUnlocked(ServerPlayer p, boolean on) {
        keep(p).putBoolean(UNLOCKED, on);
        p.getPersistentData().putBoolean(UNLOCKED, on);
        if (!on && active(p)) setActive(p, false);
    }

    /** Night in a world with a sky. */
    private static boolean night(ServerLevel level) {
        if (!level.dimensionType().hasSkyLight() || level.dimensionType().hasFixedTime()) return false;
        long t = level.getDayTime() % 24000L;
        return t >= 13000L && t <= 23000L;
    }

    private static boolean shouldBeActive(ServerPlayer p) {
        if (ModCapabilities.getType(p) != CharacterType.RHALIX || !unlocked(p) || !Seasons.unlocked(p.server, 2)) return false;
        return night(p.serverLevel()) || p.getPersistentData().getLong(FAKE_MOON) > p.level().getGameTime();
    }

    private static void setActive(ServerPlayer p, boolean on) {
        p.getPersistentData().putBoolean(ACTIVE, on);
        ServerLevel s = p.serverLevel();
        if (on) {
            p.displayClientMessage(Component.literal("Lunar Form").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD), true);
            s.playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.2F, 0.6F);
            s.playSound(null, p.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8F, 1.4F);
            s.sendParticles(new DustParticleOptions(SILVER, 1.6F), p.getX(), p.getY() + 1, p.getZ(), 50, 0.6, 1.0, 0.6, 0);
            s.sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1.5, p.getZ(), 20, 0.8, 0.8, 0.8, 0.02);
            com.xenowarriors.anim.Poses.play(p, com.xenowarriors.anim.Poses.KA_BREAK, 14);
        } else {
            p.displayClientMessage(Component.literal("The moon sets - Lunar Form fades").withStyle(ChatFormatting.GRAY), true);
        }
        apply(p, on);
        sync(p);
    }

    private static void apply(ServerPlayer p, boolean on) {
        mod(p.getAttribute(Attributes.ATTACK_DAMAGE), DMG, on ? 0.25 : 0, AttributeModifier.Operation.MULTIPLY_BASE);
        mod(p.getAttribute(Attributes.MOVEMENT_SPEED), SPD, on ? 0.12 : 0, AttributeModifier.Operation.MULTIPLY_BASE);
        mod(p.getAttribute(Attributes.ARMOR), ARM, on ? 4 : 0, AttributeModifier.Operation.ADDITION);
    }

    private static void mod(AttributeInstance a, UUID id, double amount, AttributeModifier.Operation op) {
        if (a == null) return;
        a.removeModifier(id);
        if (amount != 0) a.addTransientModifier(new AttributeModifier(id, "Lunar form", amount, op));
    }

    public static void sync(ServerPlayer p) {
        ModNetwork.sendToTrackingAndSelf(new LunarStatePacket(p.getId(), active(p), p.getPersistentData().getLong(REFLECT) > p.level().getGameTime()), p);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p)) return;
        if (p.tickCount % 10 == 0) {
            if (DmzMoon.lookingAtFakeMoon(p)) {
                if (p.getPersistentData().getLong(FAKE_MOON) <= p.level().getGameTime() && ModCapabilities.getType(p) == CharacterType.RHALIX && unlocked(p))
                    p.displayClientMessage(Component.literal("The fake moon's light reaches you...").withStyle(ChatFormatting.AQUA), true);
                p.getPersistentData().putLong(FAKE_MOON, p.level().getGameTime() + FAKE_MOON_TICKS);
            }
            boolean want = shouldBeActive(p);
            if (want != active(p)) setActive(p, want);
        }
        if (active(p) && p.tickCount % 4 == 0)                                        // silver motes drifting off him
            p.serverLevel().sendParticles(new DustParticleOptions(SILVER, 0.8F), p.getX(), p.getY() + 1.2, p.getZ(), 1, 0.4, 0.6, 0.4, 0);
        if (active(p) && p.tickCount % 40 == 0)                                       // his moon makes allies nearby stronger
            for (ServerPlayer o : p.serverLevel().getEntitiesOfClass(ServerPlayer.class, p.getBoundingBox().inflate(8), o -> o != p && o.isAlive() && com.xenowarriors.party.Parties.sameParty(p, o)))
                o.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DAMAGE_BOOST, 60, 0, true, false, true));
        trial(p);
    }

    /** The Lunar trial: crouch still out in the open for 30 seconds under a full moon. */
    private static void trial(ServerPlayer p) {
        if (unlocked(p) || ModCapabilities.getType(p) != CharacterType.RHALIX || !Seasons.unlocked(p.server, 2)) return;
        ServerLevel s = p.serverLevel();
        boolean ok = night(s) && s.getMoonPhase() == 0 && p.isCrouching() && p.onGround()
                && p.getDeltaMovement().horizontalDistanceSqr() < 1.0E-3 && s.canSeeSky(p.blockPosition().above());
        int t = p.getPersistentData().getInt(TRIAL);
        if (!ok) {
            if (t > 0) { p.getPersistentData().remove(TRIAL); p.displayClientMessage(Component.literal("The moon's voice fades...").withStyle(ChatFormatting.GRAY), true); }
            return;
        }
        t++;
        p.getPersistentData().putInt(TRIAL, t);
        if (t % 20 == 0) {
            p.displayClientMessage(Component.literal("Lunar trial: " + (TRIAL_TICKS - t) / 20 + "s - stay still").withStyle(ChatFormatting.AQUA), true);
            double a = t * 0.15;
            for (int i = 0; i < 6; i++) {                                             // moonlight gathering around him
                double r = 2.5 - 2.0 * t / (double) TRIAL_TICKS, ang = a + i * Math.PI / 3;
                s.sendParticles(ParticleTypes.END_ROD, p.getX() + Math.cos(ang) * r, p.getY() + 0.3 + i * 0.25, p.getZ() + Math.sin(ang) * r, 1, 0, 0, 0, 0);
            }
        }
        if (t >= TRIAL_TICKS) {
            p.getPersistentData().remove(TRIAL);
            setUnlocked(p, true);
            p.sendSystemMessage(Component.literal("The full moon answers you. Lunar Form unlocked - it wakes every night.").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
            s.playSound(null, p.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1F, 1.2F);
            setActive(p, true);
        }
    }

    // ---------------- Moon Reflect ----------------

    public static void startReflect(ServerPlayer p, int ticks) {
        p.getPersistentData().putLong(REFLECT, p.level().getGameTime() + ticks);
        sync(p);
        com.xenowarriors.skill.ServerScheduler.later(ticks + 1, () -> { if (p.isAlive()) sync(p); });
    }

    private static boolean reflecting(LivingEntity e) { return e.getPersistentData().getLong(REFLECT) > e.level().getGameTime(); }

    /** Projectiles bounce off the mirror back where they came from. */
    @SubscribeEvent
    public static void onProjectile(ProjectileImpactEvent event) {
        if (!(event.getRayTraceResult() instanceof net.minecraft.world.phys.EntityHitResult hit) || !(hit.getEntity() instanceof ServerPlayer p) || !reflecting(p)) return;
        Projectile pr = event.getProjectile();
        if (pr.getOwner() == p) return;
        event.setImpactResult(ProjectileImpactEvent.ImpactResult.SKIP_ENTITY);
        Vec3 v = pr.getDeltaMovement();
        pr.setDeltaMovement(v.scale(-1.2));
        pr.setOwner(p);
        pr.hurtMarked = true;
        p.serverLevel().sendParticles(ParticleTypes.END_ROD, pr.getX(), pr.getY(), pr.getZ(), 8, 0.2, 0.2, 0.2, 0.05);
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, 1F, 1.5F);
    }

    private static boolean reflectingNow;

    /** Melee: half the blow is turned back on the attacker. */
    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (reflectingNow || !(event.getEntity() instanceof ServerPlayer p) || !reflecting(p)) return;
        if (!(event.getSource().getEntity() instanceof LivingEntity att) || att == p) return;
        float back = event.getAmount() * 0.5F;
        event.setAmount(event.getAmount() * 0.5F);
        reflectingNow = true;
        try { att.hurt(p.damageSources().indirectMagic(p, p), back); } finally { reflectingNow = false; }
        Vec3 d = att.position().subtract(p.position()).normalize();
        att.push(d.x * 0.6, 0.2, d.z * 0.6); att.hurtMarked = true;
        p.serverLevel().sendParticles(new DustParticleOptions(SILVER, 1.4F), att.getX(), att.getY() + 1, att.getZ(), 14, 0.3, 0.5, 0.3, 0);
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 1F, 1.6F);
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof ServerPlayer t && active(t) && event.getEntity() instanceof ServerPlayer w)
            ModNetwork.sendToPlayer(new LunarStatePacket(t.getId(), true, reflecting(t)), w);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            boolean on = active(p);
            apply(p, on);
            if (on) sync(p);
        }
    }

    /** Is anything in this box a Rhalix in Lunar form (for AI / visuals). */
    public static boolean anyNear(ServerLevel level, AABB box) {
        return !level.getEntitiesOfClass(ServerPlayer.class, box, LunarForm::active).isEmpty();
    }

    /** DragonMineZ's Fake Moon (its full-moon technique), by reflection so the mod is optional. */
    static final class DmzMoon {
        private static Boolean loaded;
        private static Method looking;
        private static boolean broken;

        static boolean lookingAtFakeMoon(ServerPlayer p) {
            if (loaded == null) loaded = ModList.get() != null && ModList.get().isLoaded("dragonminez");
            if (!loaded || broken) return false;
            try {
                if (looking == null) {
                    looking = Class.forName("com.dragonminez.common.util.TransformationsHelper").getDeclaredMethod("isLookingAtFakeMoon", Player.class);
                    looking.setAccessible(true);
                }
                return (Boolean) looking.invoke(null, p);
            } catch (Throwable t) {
                broken = true;
                return false;
            }
        }
    }
}

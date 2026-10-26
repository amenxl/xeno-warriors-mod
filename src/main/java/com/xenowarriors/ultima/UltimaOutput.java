package com.xenowarriors.ultima;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.character.ModCapabilities;
import com.xenowarriors.network.ModNetwork;
import com.xenowarriors.network.UltimaOutputPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.UUID;

/**
 * Ultima's power output, like in the show: he fights at 25%, ramps to 45%, 70% and 100% the longer the fight goes
 * (or faster with Output Surge), and drops back when the fighting stops. Each step is its own fighting style.
 * Overload (over 100%, the super slot) is everything at once - and it burns him up.
 *   25% Analysis   - Attack Prediction sometimes dodges hits on its own
 *   45% Blaster    - he can fly; blasts come in bursts
 *   70% Heavy hitter - heavy combos (every third blow a slam), shrugs off knockback, tougher
 *   100% Full Output - fastest and strongest, everything at max
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class UltimaOutput {
    public static final int[] PERCENT = {25, 45, 70, 100};
    private static final String TIER = "xw_ult_tier", COMBAT = "xw_ult_combat", LAST_FIGHT = "xw_ult_last", OVER = "xw_ult_over";
    private static final UUID DMG = UUID.fromString("5b0e7a51-6c1d-4f2e-9a3b-0c4d5e6f7a81"), SPD = UUID.fromString("5b0e7a51-6c1d-4f2e-9a3b-0c4d5e6f7a82"),
            ARM = UUID.fromString("5b0e7a51-6c1d-4f2e-9a3b-0c4d5e6f7a83"), KB = UUID.fromString("5b0e7a51-6c1d-4f2e-9a3b-0c4d5e6f7a84");
    public static final int RAMP_TICKS = 200, OVERLOAD_TICKS = 20 * 20;

    private UltimaOutput() {}
    private static boolean arcing;

    public static boolean is(Player p) { return ModCapabilities.getType(p) == CharacterType.ULTIMA; }

    public static int tier(LivingEntity e) { return e.getPersistentData().getInt(TIER); }

    public static int percent(LivingEntity e) { return overloaded(e) ? 120 : PERCENT[Math.min(3, tier(e))]; }

    public static boolean overloaded(LivingEntity e) { return e.getPersistentData().getLong(OVER) > e.level().getGameTime(); }

    public static void setTier(ServerPlayer p, int t) {
        int old = tier(p);
        t = Math.max(0, Math.min(3, t));
        p.getPersistentData().putInt(TIER, t);
        p.getPersistentData().putInt(COMBAT, 0);
        if (t != old) {
            if (t > old) {
                p.displayClientMessage(Component.literal("Output " + PERCENT[t] + "%").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), true);
                p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1F, 0.8F + t * 0.25F);
                p.serverLevel().sendParticles(new DustParticleOptions(new Vector3f(.2F, 1F, .5F), 1.5F), p.getX(), p.getY() + 1, p.getZ(), 30, 0.5, 0.9, 0.5, 0);
            }
            apply(p);
        }
        sync(p);
    }

    public static void sync(ServerPlayer p) {
        ModNetwork.sendToTrackingAndSelf(new UltimaOutputPacket(p.getId(), tier(p), overloaded(p)), p);
    }

    /** Overload: only from 100%. Everything faster and harder, static arcs off every hit - and it eats his health. */
    public static boolean overload(ServerPlayer p) {
        if (tier(p) < 3) {
            p.displayClientMessage(Component.literal("Overload needs 100% output (now " + percent(p) + "%)").withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        p.getPersistentData().putLong(OVER, p.level().getGameTime() + OVERLOAD_TICKS);
        p.displayClientMessage(Component.literal("OVERLOAD").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD, ChatFormatting.OBFUSCATED), true);
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.8F, 1.6F);
        com.xenowarriors.anim.Poses.play(p, com.xenowarriors.anim.Poses.KA_BREAK, 16);
        apply(p);
        sync(p);
        return true;
    }

    private static void apply(ServerPlayer p) {
        int t = tier(p);
        boolean o = overloaded(p);
        double dmg = o ? 1.0 : new double[]{0, 0.15, 0.35, 0.6}[t];
        double spd = o ? 0.35 : new double[]{0.05, 0.08, 0.1, 0.2}[t];
        mod(p.getAttribute(Attributes.ATTACK_DAMAGE), DMG, dmg, AttributeModifier.Operation.MULTIPLY_BASE);
        mod(p.getAttribute(Attributes.MOVEMENT_SPEED), SPD, spd, AttributeModifier.Operation.MULTIPLY_BASE);
        mod(p.getAttribute(Attributes.ARMOR), ARM, t >= 2 || o ? 4 : 0, AttributeModifier.Operation.ADDITION);
        mod(p.getAttribute(Attributes.KNOCKBACK_RESISTANCE), KB, t >= 2 || o ? 0.6 : 0, AttributeModifier.Operation.ADDITION);
        // he flies from 45%
        if (!p.isCreative() && !p.isSpectator()) {
            boolean fly = t >= 1 || o;
            if (p.getAbilities().mayfly != fly) {
                p.getAbilities().mayfly = fly;
                if (!fly) p.getAbilities().flying = false;
                p.onUpdateAbilities();
            }
        }
    }

    private static void mod(AttributeInstance a, UUID id, double amount, AttributeModifier.Operation op) {
        if (a == null) return;
        a.removeModifier(id);
        if (amount != 0) a.addTransientModifier(new AttributeModifier(id, "Ultima output", amount, op));
    }

    public static void clear(ServerPlayer p) {
        p.getPersistentData().remove(TIER);
        p.getPersistentData().remove(OVER);
        for (UUID id : new UUID[]{DMG, SPD, ARM, KB})
            for (var a : new AttributeInstance[]{p.getAttribute(Attributes.ATTACK_DAMAGE), p.getAttribute(Attributes.MOVEMENT_SPEED), p.getAttribute(Attributes.ARMOR), p.getAttribute(Attributes.KNOCKBACK_RESISTANCE)})
                if (a != null) a.removeModifier(id);
        if (!p.isCreative() && !p.isSpectator() && p.getAbilities().mayfly) { p.getAbilities().mayfly = false; p.getAbilities().flying = false; p.onUpdateAbilities(); }
    }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p)) return;
        if (!is(p)) {
            if (p.getPersistentData().contains(TIER)) clear(p);
            return;
        }
        var t = p.getPersistentData();
        long now = p.level().getGameTime();
        p.getFoodData().setExhaustion(0F);                       // endless stamina: he never tires
        boolean fighting = now - t.getLong(LAST_FIGHT) < 100;
        if (fighting) {
            int c = t.getInt(COMBAT) + 1;
            t.putInt(COMBAT, c);
            if (c >= RAMP_TICKS && tier(p) < 3) setTier(p, tier(p) + 1);          // he ramps up the longer he fights
        } else if (now - t.getLong(LAST_FIGHT) > 300 && tier(p) > 0 && p.tickCount % 200 == 0 && !overloaded(p)) {
            setTier(p, tier(p) - 1);                                              // and cools down after
        }
        boolean over = overloaded(p);
        if (over) {
            ServerLevel l = p.serverLevel();
            if (p.tickCount % 2 == 0) {
                l.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + 1, p.getZ(), 6, 0.5, 0.9, 0.5, 0.15);
                l.sendParticles(new DustParticleOptions(new Vector3f(.3F, 1F, .55F), 1.0F), p.getX(), p.getY() + 1, p.getZ(), 4, 0.45, 0.9, 0.45, 0);
            }
            if (p.tickCount % 20 == 0) p.hurt(p.damageSources().magic(), 1F);       // it burns him up
            if (p.tickCount % 30 == 0) l.playSound(null, p.blockPosition(), SoundEvents.BEEHIVE_WORK, SoundSource.PLAYERS, 0.6F, 2F);
            t.putBoolean("xw_ult_was_over", true);
        } else if (t.getBoolean("xw_ult_was_over")) {
            t.remove("xw_ult_was_over");
            p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 1));
            p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200, 1));
            p.displayClientMessage(Component.literal("Overload burnt out... output back to 25%").withStyle(ChatFormatting.DARK_GREEN), true);
            setTier(p, 0);
        }
        if (p.tickCount % 100 == 0) { apply(p); sync(p); }
    }

    /** Fighting (hitting or being hit) keeps the ramp going; at 25% Attack Prediction can read a hit; Overload arcs. */
    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && is(p) && event.getSource().getEntity() instanceof LivingEntity att) {
            p.getPersistentData().putLong(LAST_FIGHT, p.level().getGameTime());
            if (tier(p) == 0 && !overloaded(p) && p.getRandom().nextFloat() < 0.15F) {
                event.setCanceled(true);
                p.displayClientMessage(Component.literal("Predicted.").withStyle(ChatFormatting.AQUA), true);
                p.serverLevel().sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1, p.getZ(), 8, 0.4, 0.6, 0.4, 0.02);
            }
        }
        if (event.getSource().getEntity() instanceof ServerPlayer p && is(p)) {
            p.getPersistentData().putLong(LAST_FIGHT, p.level().getGameTime());
            if (tier(p) == 2 && !overloaded(p) && event.getSource().getDirectEntity() == p && !arcing) {
                int n = p.getPersistentData().getInt("xw_ult_heavy") + 1;
                p.getPersistentData().putInt("xw_ult_heavy", n);
                LivingEntity hit = event.getEntity();
                event.setAmount(event.getAmount() * 1.25F);
                hit.push((hit.getX() - p.getX()) * 0.35, 0.15, (hit.getZ() - p.getZ()) * 0.35); hit.hurtMarked = true;
                if (n % 3 == 0) {                                     // every third blow: the slam
                    arcing = true;
                    try {
                        for (LivingEntity o : p.serverLevel().getEntitiesOfClass(LivingEntity.class, hit.getBoundingBox().inflate(3), o -> o != p && com.xenowarriors.skill.SkillTargeting.canTarget(p, o))) {
                            if (o != hit) o.hurt(p.damageSources().playerAttack(p), event.getAmount() * 0.5F);
                            o.push(0, 0.65, 0); o.hurtMarked = true;
                        }
                    } finally { arcing = false; }
                    p.serverLevel().sendParticles(ParticleTypes.EXPLOSION, hit.getX(), hit.getY() + 0.3, hit.getZ(), 2, 0.5, 0.1, 0.5, 0);
                    p.serverLevel().playSound(null, hit.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.8F, 0.6F);
                }
            }
            if (overloaded(p) && event.getSource().getDirectEntity() == p && !arcing) {
                arcing = true;
                try {
                LivingEntity hit = event.getEntity();
                int n = 0;
                for (LivingEntity o : p.serverLevel().getEntitiesOfClass(LivingEntity.class, hit.getBoundingBox().inflate(5), o -> o != p && o != hit && com.xenowarriors.skill.SkillTargeting.canTarget(p, o))) {
                    if (n++ >= 2) break;
                    o.hurt(p.damageSources().indirectMagic(p, p), 4F);
                    var a = hit.position().add(0, 1, 0); var b = o.position().add(0, 1, 0);
                    for (int i = 0; i <= 8; i++) { var q = a.lerp(b, i / 8.0); p.serverLevel().sendParticles(ParticleTypes.ELECTRIC_SPARK, q.x, q.y, q.z, 1, 0.05, 0.05, 0.05, 0); }
                }
                } finally { arcing = false; }
            }
        }
    }
}

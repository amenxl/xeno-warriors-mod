package com.xenowarriors.skill.ffr;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.skill.ServerScheduler;
import com.xenowarriors.skill.Skill;
import com.xenowarriors.skill.SkillTargeting;
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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.*;

/** The Sky Rebellion playables: Logan (the old royal knight), Kill (the first demon Skylander) and Keita (the leader's lazy son). */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SrSkills {
    private SrSkills() {}
    static final DustParticleOptions GOLD = new DustParticleOptions(new Vector3f(1F, 0.85F, 0.3F), 1F),
            DEVIL = new DustParticleOptions(new Vector3f(0.45F, 0.1F, 0.55F), 1.1F), DREAM = new DustParticleOptions(new Vector3f(0.4F, 0.45F, 1F), 1F);
    static final Map<UUID, Long> REDIRECT = new HashMap<>(), FEIGN = new HashMap<>(), DEVIL_MODE = new HashMap<>();

    static long now(ServerPlayer p) { return p.level().getGameTime(); }
    static boolean on(Map<UUID, Long> m, ServerPlayer p) { Long t = m.get(p.getUUID()); return t != null && t > now(p); }

    static void dashSlash(ServerPlayer p, float dmg, DustParticleOptions trail) {
        Vec3 dir = p.getLookAngle().multiply(1, 0.2, 1).normalize();
        Set<Integer> hit = new HashSet<>();
        Vec3 start = p.position();
        double reach = com.xenowarriors.skill.sp.SpUtil.pathReach(p, start, dir, 1.6 * 6);
        for (int i = 0; i < 6; i++) { int ii = i; ServerScheduler.later(i, () -> {
            if (!p.isAlive()) return;
            p.setDeltaMovement(dir.scale(1.6)); p.hurtMarked = true; p.fallDistance = 0;
            p.serverLevel().sendParticles(trail, p.getX(), p.getY() + 1, p.getZ(), 4, 0.2, 0.3, 0.2, 0);
            for (LivingEntity e : com.xenowarriors.skill.sp.SpUtil.swept(p, start, dir, Math.min(reach, (ii + 1) * 1.6), 2.0))
                if (hit.add(e.getId())) { e.hurt(p.damageSources().playerAttack(p), dmg); p.serverLevel().sendParticles(ParticleTypes.SWEEP_ATTACK, e.getX(), e.getY() + 1, e.getZ(), 1, 0, 0, 0, 0); }
        }); }
        ServerScheduler.later(6, () -> { p.setDeltaMovement(p.getDeltaMovement().scale(0.2)); p.hurtMarked = true; });
        p.level().playSound(null, p.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1F, 1.2F);
    }

    static void bolt(ServerPlayer p, Vec3 at, float dmg) {
        ServerLevel l = p.serverLevel();
        LightningBolt b = EntityType.LIGHTNING_BOLT.create(l);
        if (b != null) { b.moveTo(at); b.setVisualOnly(true); l.addFreshEntity(b); }
        for (LivingEntity e : YuriSkills.around(p, at, 2.2)) { e.invulnerableTime = 0; e.hurt(p.damageSources().indirectMagic(p, p), dmg); e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1)); }
    }

    // ============================================================ LOGAN
    /** Thunder Flash: a lightning strike on his target - it knocked the hood right off Sam. */
    public static class ThunderFlash implements Skill {
        @Override public String getId() { return "thunder_flash"; }
        @Override public int getCooldownTicks() { return 10 * 20; }
        @Override public int getKarmaCost() { return 20; }
        @Override public boolean use(ServerPlayer p) {
            LivingEntity t = YuriSkills.target(p, 26);
            if (t == null) return false;
            bolt(p, t.position(), 8F);
            if (t instanceof ServerPlayer o && !o.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).isEmpty()) {     // knocks their helmet / hood off
                o.drop(o.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).copy(), true);
                o.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.item.ItemStack.EMPTY);
            }
            return true;
        }
    }

    /** Knight's Charge: the royal guard's rush - a dash cut through everything in the way. */
    public static class KnightCharge implements Skill {
        @Override public String getId() { return "knight_charge"; }
        @Override public int getCooldownTicks() { return 7 * 20; }
        @Override public int getKarmaCost() { return 15; }
        @Override public boolean use(ServerPlayer p) { dashSlash(p, 7F, GOLD); return true; }
    }

    /** Royal Guard: he stands his ground like he did for King Arling - hard to hurt or move for 6 seconds. */
    public static class RoyalGuard implements Skill {
        @Override public String getId() { return "royal_guard"; }
        @Override public int getCooldownTicks() { return 25 * 20; }
        @Override public int getKarmaCost() { return 20; }
        @Override public boolean use(ServerPlayer p) {
            p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 120, 2));
            p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
            ServerScheduler.later(120, () -> p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0.0));
            p.serverLevel().sendParticles(GOLD, p.getX(), p.getY() + 1, p.getZ(), 30, 0.5, 0.8, 0.5, 0);
            p.level().playSound(null, p.blockPosition(), SoundEvents.ARMOR_EQUIP_NETHERITE, SoundSource.PLAYERS, 1F, 0.8F);
            return true;
        }
    }

    /** Portal Escape: a portal behind him - gone, 12 blocks back. */
    public static class PortalEscape implements Skill {
        @Override public String getId() { return "portal_escape"; }
        @Override public int getCooldownTicks() { return 20 * 20; }
        @Override public int getKarmaCost() { return 15; }
        @Override public boolean use(ServerPlayer p) {
            Vec3 back = p.position().subtract(p.getLookAngle().multiply(1, 0, 1).normalize().scale(12));
            p.serverLevel().sendParticles(ParticleTypes.PORTAL, p.getX(), p.getY() + 1, p.getZ(), 60, 0.4, 0.9, 0.4, 0.3);
            p.teleportTo(back.x, Math.max(p.getY(), p.serverLevel().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, (int) back.x, (int) back.z)), back.z);
            p.serverLevel().sendParticles(ParticleTypes.PORTAL, p.getX(), p.getY() + 1, p.getZ(), 60, 0.4, 0.9, 0.4, 0.3);
            p.level().playSound(null, p.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1F, 0.8F);
            return true;
        }
    }

    /** Ultimate - Thunder Judgement: lightning falls on everything around him, again and again. */
    public static class ThunderJudgement implements Skill {
        @Override public String getId() { return "thunder_judgement"; }
        @Override public int getCooldownTicks() { return 90 * 20; }
        @Override public int getKarmaCost() { return 60; }
        @Override public boolean use(ServerPlayer p) {
            for (int i = 0; i < 6; i++) ServerScheduler.later(i * 12, () -> {
                for (LivingEntity e : YuriSkills.around(p, p.position(), 16)) if ((e.getId() + p.tickCount) % 2 == 0) bolt(p, e.position(), 6F);
            });
            p.displayClientMessage(Component.literal("Thunder Judgement!").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), true);
            return true;
        }
    }

    /** Logan's KA - Redirect: for 8 seconds, anything flying at him turns around. Afterwards he's a little dizzy. */
    public static boolean redirect(ServerPlayer p) {
        if (on(REDIRECT, p)) return false;
        if (!p.isCreative() && !com.xenowarriors.karma.Karma.trySpend(p, 35)) return false;
        REDIRECT.put(p.getUUID(), now(p) + 160);
        say(p, "KA - Redirect!", ChatFormatting.GOLD);
        ServerScheduler.later(160, () -> p.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 80, 0)));
        return true;
    }

    // ============================================================ KILL
    /** Impale: a stab straight through - and it keeps bleeding. */
    public static class Impale implements Skill {
        @Override public String getId() { return "impale"; }
        @Override public int getCooldownTicks() { return 9 * 20; }
        @Override public int getKarmaCost() { return 20; }
        @Override public boolean use(ServerPlayer p) {
            LivingEntity t = YuriSkills.target(p, 5);
            if (t == null) return false;
            p.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
            t.hurt(p.damageSources().playerAttack(p), 9F);
            t.addEffect(new MobEffectInstance(MobEffects.WITHER, 80, 1));
            t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2));
            p.serverLevel().sendParticles(ParticleTypes.DAMAGE_INDICATOR, t.getX(), t.getY() + 1, t.getZ(), 10, 0.2, 0.3, 0.2, 0.1);
            p.level().playSound(null, t.blockPosition(), SoundEvents.TRIDENT_HIT, SoundSource.PLAYERS, 1F, 0.7F);
            return true;
        }
    }

    /** Devil Dash: on his demon wings, a slash through the line. */
    public static class DevilDash implements Skill {
        @Override public String getId() { return "devil_dash"; }
        @Override public int getCooldownTicks() { return 7 * 20; }
        @Override public int getKarmaCost() { return 15; }
        @Override public boolean use(ServerPlayer p) { dashSlash(p, 6F, DEVIL); return true; }
    }

    /** Play Weak: he acts like SR's weakest - his next hit in 8 seconds lands three times as hard. */
    public static class PlayWeak implements Skill {
        @Override public String getId() { return "play_weak"; }
        @Override public int getCooldownTicks() { return 20 * 20; }
        @Override public int getKarmaCost() { return 15; }
        @Override public boolean use(ServerPlayer p) {
            FEIGN.put(p.getUUID(), now(p) + 160);
            p.displayClientMessage(Component.literal("\"Who, me? I'm the weakest one here...\"").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), true);
            return true;
        }
    }

    /** Devil Flame: one of the devil's skills - black-purple fire bursts out around him. */
    public static class DevilFlame implements Skill {
        @Override public String getId() { return "devil_flame"; }
        @Override public int getCooldownTicks() { return 12 * 20; }
        @Override public int getKarmaCost() { return 25; }
        @Override public boolean use(ServerPlayer p) {
            for (LivingEntity e : YuriSkills.around(p, p.position(), 6)) { e.hurt(p.damageSources().indirectMagic(p, p), 5F); e.setSecondsOnFire(5); }
            p.serverLevel().sendParticles(ParticleTypes.SOUL_FIRE_FLAME, p.getX(), p.getY() + 0.5, p.getZ(), 80, 3, 0.4, 3, 0.03);
            p.serverLevel().sendParticles(DEVIL, p.getX(), p.getY() + 1, p.getZ(), 40, 2, 0.8, 2, 0);
            p.level().playSound(null, p.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1F, 0.5F);
            return true;
        }
    }

    /** Ultimate - Devil Unleashed: the devil inside him takes over for 12 seconds - stronger, faster, every hit withers. */
    public static class DevilUnleashed implements Skill {
        @Override public String getId() { return "devil_unleashed"; }
        @Override public int getCooldownTicks() { return 90 * 20; }
        @Override public int getKarmaCost() { return 60; }
        @Override public boolean use(ServerPlayer p) {
            DEVIL_MODE.put(p.getUUID(), now(p) + 240);
            p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 240, 1));
            p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 240, 1));
            for (int i = 0; i < 24; i++) ServerScheduler.later(i * 10, () -> p.serverLevel().sendParticles(DEVIL, p.getX(), p.getY() + 1, p.getZ(), 12, 0.5, 0.9, 0.5, 0));
            say(p, "Devil Unleashed!", ChatFormatting.DARK_PURPLE);
            return true;
        }
    }

    // ============================================================ KEITA
    /** Lights Out: "You can't see anything." And they believe him. */
    public static class LightsOut implements Skill {
        @Override public String getId() { return "lights_out"; }
        @Override public int getCooldownTicks() { return 14 * 20; }
        @Override public int getKarmaCost() { return 20; }
        @Override public boolean use(ServerPlayer p) {
            LivingEntity t = YuriSkills.target(p, 20);
            if (t == null) return false;
            t.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 120, 0));
            t.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 120, 3));
            if (t instanceof Mob m) { m.setTarget(null); m.getNavigation().stop(); }
            say(p, "\"You can't see anything.\"", ChatFormatting.BLUE);
            return true;
        }
    }

    /** Kneel: "Get down." They drop and can barely move. */
    public static class Kneel implements Skill {
        @Override public String getId() { return "kneel"; }
        @Override public int getCooldownTicks() { return 12 * 20; }
        @Override public int getKarmaCost() { return 20; }
        @Override public boolean use(ServerPlayer p) {
            int n = 0;
            for (LivingEntity e : YuriSkills.around(p, p.position(), 8)) {
                e.setDeltaMovement(0, -1.2, 0); e.hurtMarked = true;
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 4));
                e.hurt(p.damageSources().indirectMagic(p, p), 3F); n++;
            }
            say(p, "\"Get down.\"", ChatFormatting.BLUE);
            return n > 0 || true;
        }
    }

    /** Keita's sword: a quick slash straight through. */
    public static class LazySlash implements Skill {
        @Override public String getId() { return "lazy_slash"; }
        @Override public int getCooldownTicks() { return 6 * 20; }
        @Override public int getKarmaCost() { return 15; }
        @Override public boolean use(ServerPlayer p) { dashSlash(p, 7F, DREAM); return true; }
    }

    /** Power Nap: he lies down for a few seconds (can't move) and wakes up healed and with Karma back. */
    public static class PowerNap implements Skill {
        @Override public String getId() { return "power_nap"; }
        @Override public int getCooldownTicks() { return 40 * 20; }
        @Override public int getKarmaCost() { return 0; }
        @Override public boolean use(ServerPlayer p) {
            p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 10, false, false));
            p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 80, 2));
            for (int i = 0; i < 4; i++) ServerScheduler.later(i * 20, () -> {
                p.serverLevel().sendParticles(DREAM, p.getX(), p.getY() + 2.2, p.getZ(), 3, 0.2, 0.1, 0.2, 0);
                com.xenowarriors.karma.Karma.add(p, 8F);
            });
            p.displayClientMessage(Component.literal("Zzz...").withStyle(ChatFormatting.BLUE), true);
            return true;
        }
    }

    /** Ultimate - Mass Cut: the attack that took down every guard at once - one huge cut through everything around him. */
    public static class MassCut implements Skill {
        @Override public String getId() { return "mass_cut"; }
        @Override public int getCooldownTicks() { return 90 * 20; }
        @Override public int getKarmaCost() { return 60; }
        @Override public boolean use(ServerPlayer p) {
            ServerLevel l = p.serverLevel();
            for (int k = 0; k < 24; k++) {
                double a = k * Math.PI / 12;
                l.sendParticles(ParticleTypes.SWEEP_ATTACK, p.getX() + Math.cos(a) * 4, p.getY() + 1, p.getZ() + Math.sin(a) * 4, 1, 0, 0, 0, 0);
            }
            for (LivingEntity e : YuriSkills.around(p, p.position(), 9)) { e.invulnerableTime = 0; e.hurt(p.damageSources().playerAttack(p), 16F); }
            l.playSound(null, p.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.5F, 0.5F);
            say(p, "...it's too much effort. Just fall.", ChatFormatting.BLUE);
            return true;
        }
    }

    /** Keita's KA - "Everything I say, you believe": every enemy near him freezes on his word for 6 seconds. He gets sleepy after. */
    public static boolean believe(ServerPlayer p) {
        if (!p.isCreative() && !com.xenowarriors.karma.Karma.trySpend(p, 40)) return false;
        int n = 0;
        for (LivingEntity e : YuriSkills.around(p, p.position(), 12)) {
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 120, 9));
            e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 120, 4));
            e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 120, 0));
            if (e instanceof Mob m) { m.setTarget(null); m.getNavigation().stop(); }
            n++;
        }
        say(p, "KA - \"Don't move.\"", ChatFormatting.BLUE);
        p.serverLevel().sendParticles(DREAM, p.getX(), p.getY() + 1, p.getZ(), 60, 6, 1, 6, 0);
        ServerScheduler.later(120, () -> { p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 1)); p.causeFoodExhaustion(6F); });
        return true;
    }

    static void say(ServerPlayer p, String line, ChatFormatting col) {
        Component c = Component.literal(p.getName().getString() + ": ").withStyle(ChatFormatting.WHITE).append(Component.literal(line).withStyle(col, ChatFormatting.BOLD));
        for (ServerPlayer o : p.serverLevel().players()) if (o.distanceToSqr(p) < 40 * 40) o.displayClientMessage(c, true);
    }

    // ------------------------------------------------------------ the timed states
    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!on(REDIRECT, p)) continue;
            for (Projectile pr : p.serverLevel().getEntitiesOfClass(Projectile.class, p.getBoundingBox().inflate(4), pr -> pr.getOwner() != p)) {
                Vec3 v = pr.getDeltaMovement();
                if (v.dot(p.position().subtract(pr.position())) > 0) {          // heading at him: turned right around
                    pr.setDeltaMovement(v.scale(-1.1)); pr.setOwner(p); pr.hurtMarked = true;
                    p.serverLevel().sendParticles(GOLD, pr.getX(), pr.getY(), pr.getZ(), 6, 0.1, 0.1, 0.1, 0);
                }
            }
            if (p.tickCount % 4 == 0) {
                double a = p.tickCount * 0.35;
                p.serverLevel().sendParticles(GOLD, p.getX() + Math.cos(a) * 1.3, p.getY() + 1, p.getZ() + Math.sin(a) * 1.3, 1, 0, 0, 0, 0);
            }
        }
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent e) {
        if (e.getSource().getEntity() instanceof ServerPlayer p && e.getSource().getDirectEntity() == p) {
            if (on(FEIGN, p)) { e.setAmount(e.getAmount() * 3F); FEIGN.remove(p.getUUID()); }
            if (on(DEVIL_MODE, p)) e.getEntity().addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 1));
        }
    }
}

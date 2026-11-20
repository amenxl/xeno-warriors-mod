package com.xenowarriors.skill.nc;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.bot.PartyBotEntity;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.character.ModCapabilities;
import com.xenowarriors.skill.ServerScheduler;
import com.xenowarriors.skill.Skill;
import com.xenowarriors.skill.SkillTargeting;
import com.xenowarriors.skill.sp.SpUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.*;

/**
 * The seven characters added in November 2026: Kyle (C2's modified human), Zack and Kaitlyn (Ai-oni's demons), Chase
 * (the Keeper with two swords), Senju (the Sky Rebellion's leader), and Ava and Persephone (the demon sisters of the
 * Sky Rebellion). Their skills, ultimates, KAs (Zack's Infinite Loop, Kaitlyn's Raised Hand), their transform-key forms
 * (Demon Form, Senju's Heavenly Demon), Kyle's Modification Surge and Chase's Hidden Will.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class NcSkills {
    private NcSkills() {}

    static DustParticleOptions dust(float r, float g, float b, float s) { return new DustParticleOptions(new Vector3f(r, g, b), s); }
    static final DustParticleOptions C2 = dust(0.6F, 0.3F, 0.95F, 1F), SHADOW = dust(0.12F, 0.05F, 0.18F, 1.3F), DARK_KARMA = dust(0.35F, 0.05F, 0.4F, 1.2F),
            RED = dust(0.95F, 0.1F, 0.12F, 1.1F), KEEPER = dust(0.85F, 0.12F, 0.1F, 0.9F), HEAVEN = dust(1F, 0.95F, 0.85F, 1.2F), WIND = dust(0.85F, 0.9F, 0.95F, 1F);

    /** States, by player (a bot's under its stand-in player's UUID): until when. */
    static final Map<UUID, Long> MARKED = new HashMap<>(), VANISH = new HashMap<>(), CAGE = new HashMap<>(), SURGE = new HashMap<>(), FORM = new HashMap<>(),
            WILL = new HashMap<>(), UNLEASHED = new HashMap<>();
    /** Which form (Demon Form / Heavenly Demon) a player is in. */
    static final Map<UUID, Form> FORM_OF = new HashMap<>();

    static long now(Entity e) { return e.level().getGameTime(); }
    static boolean on(Map<UUID, Long> m, Entity e) { Long t = m.get(e.getUUID()); return t != null && t > now(e); }

    public static void clear(UUID id) {
        VANISH.remove(id); CAGE.remove(id); SURGE.remove(id); FORM.remove(id); FORM_OF.remove(id); WILL.remove(id); UNLEASHED.remove(id);
    }

    // ------------------------------------------------------------------ helpers

    static CharacterType type(ServerPlayer p) { return ModCapabilities.getType(p); }

    static void sound(ServerPlayer p, SoundEvent s, float vol, float pitch) { p.level().playSound(null, p.blockPosition(), s, SoundSource.PLAYERS, vol, pitch); }

    static void puff(ServerPlayer p, Entity at, ParticleOptions o, int n, double spread) {
        p.serverLevel().sendParticles(o, at.getX(), at.getY() + at.getBbHeight() / 2, at.getZ(), n, spread, spread, spread, 0.02);
    }

    /** What they're aiming at (a line telling them when there's nothing). */
    static LivingEntity aim(ServerPlayer p, double range) {
        LivingEntity t = SkillTargeting.aimed(p, range, 12);
        if (t == null) p.displayClientMessage(Component.translatable("message.xenowarriors.no_target"), true);
        return t;
    }

    /** What they're aiming at, else the closest enemy in front of them (close-range moves). */
    static LivingEntity front(ServerPlayer p, double range) {
        LivingEntity t = SpUtil.front(p, range);
        if (t == null) p.displayClientMessage(Component.translatable("message.xenowarriors.no_target"), true);
        return t;
    }

    static List<LivingEntity> around(ServerPlayer p, Vec3 c, double r) { return SpUtil.around(p, c, r); }

    /** Enemies around them in front (a cone of about 120 degrees). */
    static List<LivingEntity> cone(ServerPlayer p, double r) {
        List<LivingEntity> out = new ArrayList<>();
        Vec3 look = p.getLookAngle().multiply(1, 0, 1).normalize();
        for (LivingEntity e : around(p, p.position(), r)) {
            Vec3 to = e.position().subtract(p.position()).multiply(1, 0, 1);
            if (to.lengthSqr() < 1.0 || to.normalize().dot(look) > 0.45) out.add(e);
        }
        return out;
    }

    static void hit(ServerPlayer p, LivingEntity t, float dmg, boolean magic) {
        t.invulnerableTime = 0;
        t.hurt(magic ? p.damageSources().indirectMagic(p, p) : p.damageSources().playerAttack(p), dmg);
    }

    static void effect(LivingEntity e, MobEffect m, int ticks, int amp) { e.addEffect(new MobEffectInstance(m, ticks, amp)); }

    static void push(LivingEntity e, Vec3 from, double strength, double up) {
        Vec3 d = e.position().subtract(from).multiply(1, 0, 1);
        d = d.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : d.normalize();
        e.setDeltaMovement(e.getDeltaMovement().add(d.x * strength, up, d.z * strength));
        e.hurtMarked = true;
    }

    /** A mob stops going after them (it lost track / was fooled). */
    static void lose(LivingEntity e, ServerPlayer p) {
        if (e instanceof Mob m && m.getTarget() == p) { m.setTarget(null); m.getNavigation().stop(); }
    }

    static void tp(LivingEntity e, double x, double y, double z) {
        if (e instanceof ServerPlayer sp) sp.connection.teleport(x, y, z, sp.getYRot(), sp.getXRot());
        else e.teleportTo(x, y, z);
    }

    /** Holds them in place for `ticks` (rooted: moved back every tick, still able to turn and swing). */
    static void root(LivingEntity t, int ticks, DustParticleOptions o, ServerPlayer by) {
        Vec3 at = t.position();
        effect(t, MobEffects.MOVEMENT_SLOWDOWN, ticks, 6);
        for (int i = 1; i <= ticks; i++) {
            int ii = i;
            ServerScheduler.later(i, () -> {
                if (!t.isAlive()) return;
                if (t.position().distanceToSqr(at) > 0.04) tp(t, at.x, at.y, at.z);
                t.setDeltaMovement(0, Math.min(0, t.getDeltaMovement().y), 0); t.hurtMarked = true;
                if (ii % 4 == 0 && t.level() instanceof ServerLevel l) l.sendParticles(o, t.getX(), t.getY() + 0.2, t.getZ(), 4, 0.35, 0.1, 0.35, 0);
            });
        }
    }

    /** Their side: party members and their bots, and the party's bots. */
    static List<LivingEntity> allies(ServerPlayer p, double r) {
        List<LivingEntity> out = new ArrayList<>();
        for (ServerPlayer m : com.xenowarriors.party.Parties.members(p)) {
            if (m != p && m.isAlive() && m.level() == p.level() && m.distanceTo(p) <= r) out.add(m);
            for (PartyBotEntity b : com.xenowarriors.bot.PartyBots.bots(m)) if (b.level() == p.level() && b.distanceTo(p) <= r && !out.contains(b)) out.add(b);
        }
        for (PartyBotEntity b : com.xenowarriors.bot.PartyBots.bots(p)) if (b.level() == p.level() && b.distanceTo(p) <= r && !out.contains(b)) out.add(b);
        return out;
    }

    /** The ally they're looking at, else the most hurt one near, else themselves. */
    static LivingEntity ally(ServerPlayer p, double r) {
        Vec3 eye = p.getEyePosition(), look = p.getLookAngle();
        LivingEntity best = null; double bestDot = 0.96;
        List<LivingEntity> all = allies(p, r);
        for (LivingEntity e : all) {
            double d = e.position().add(0, e.getBbHeight() / 2, 0).subtract(eye).normalize().dot(look);
            if (d > bestDot) { bestDot = d; best = e; }
        }
        if (best != null) return best;
        float low = p.getHealth() / p.getMaxHealth() - 0.25F;
        for (LivingEntity e : all) { float f = e.getHealth() / e.getMaxHealth(); if (f < low) { low = f; best = e; } }
        return best == null ? p : best;
    }

    static boolean is(LivingEntity e, CharacterType t) {
        if (e instanceof PartyBotEntity b) return b.character() == t;
        return e instanceof ServerPlayer sp && type(sp) == t;
    }

    static void say(ServerPlayer p, String line, ChatFormatting col) {
        Component c = Component.literal(p.getName().getString() + ": ").withStyle(ChatFormatting.WHITE).append(Component.literal(line).withStyle(col, ChatFormatting.BOLD));
        for (ServerPlayer o : p.serverLevel().players()) if (o.distanceToSqr(p) < 40 * 40) o.displayClientMessage(c, true);
    }

    /** A dash along the look direction that hits each one it passes. */
    static void dash(ServerPlayer p, double speed, int ticks, ParticleOptions trail, float dmg, java.util.function.Consumer<LivingEntity> also) {
        SpUtil.dash(p, speed, ticks, trail, e -> {
            hit(p, e, dmg, false);
            p.serverLevel().sendParticles(ParticleTypes.SWEEP_ATTACK, e.getX(), e.getY() + 1, e.getZ(), 1, 0, 0, 0, 0);
            if (also != null) also.accept(e);
        });
        sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 1F, 1.2F);
    }

    /** Where they're looking, up to `range` blocks (stopping short of walls), standing height. */
    static Vec3 lookSpot(ServerPlayer p, double range) {
        Vec3 dir = p.getLookAngle().multiply(1, 0, 1).normalize();
        double d = SpUtil.pathReach(p, p.position(), dir, range);
        return p.position().add(dir.scale(d));
    }

    static void blink(ServerPlayer p, Vec3 to, ParticleOptions o) {
        ServerLevel l = p.serverLevel();
        l.sendParticles(o, p.getX(), p.getY() + 1, p.getZ(), 25, 0.3, 0.6, 0.3, 0.02);
        p.connection.teleport(to.x, to.y, to.z, p.getYRot(), p.getXRot());
        p.fallDistance = 0;
        l.sendParticles(o, to.x, to.y + 1, to.z, 25, 0.3, 0.6, 0.3, 0.02);
        l.playSound(null, p.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8F, 1.3F);
    }

    /** Right behind them (facing them), if there's room; else null. */
    static Vec3 behind(ServerPlayer p, LivingEntity t) {
        Vec3 f = Vec3.directionFromRotation(0, t.getYRot()).multiply(1, 0, 1).normalize();
        Vec3 at = t.position().subtract(f.scale(1.3));
        var box = p.getBoundingBox().move(at.subtract(p.position()));
        return p.level().noCollision(p, box) ? at : null;
    }

    static void faceTo(ServerPlayer p, Entity t) {
        Vec3 d = t.position().subtract(p.position());
        float yaw = (float) (Math.atan2(d.z, d.x) * 180 / Math.PI) - 90F;
        p.connection.teleport(p.getX(), p.getY(), p.getZ(), yaw, p.getXRot());
    }

    // ------------------------------------------------------------------ a copy of a skill for the right+left click
    //  (a skill picked there leaves the skill slots, so the moves that are both get a click copy of their own)

    public static final class Click implements Skill {
        private final Skill of;
        public Click(Skill of) { this.of = of; }
        @Override public String getId() { return of.getId() + "_click"; }
        @Override public int getCooldownTicks() { return of.getCooldownTicks(); }
        @Override public int getKarmaCost() { return of.getKarmaCost(); }
        @Override public boolean use(ServerPlayer p) { return of.use(p); }
    }

    // ================================================================== KYLE (C2's modified human)

    /** Will Break: he speaks and the target's will gives - weaker and slower, a player's Karma drained. */
    public static class WillBreak implements Skill {
        @Override public String getId() { return "will_break"; }
        @Override public int getCooldownTicks() { return 9 * 20; }
        @Override public int getKarmaCost() { return 20; }
        @Override public boolean use(ServerPlayer p) {
            LivingEntity t = aim(p, 20);
            if (t == null) return false;
            hit(p, t, 5F, true);
            effect(t, MobEffects.WEAKNESS, 160, 1);
            effect(t, MobEffects.MOVEMENT_SLOWDOWN, 100, 0);
            if (t instanceof ServerPlayer o && !o.isCreative()) com.xenowarriors.karma.Karma.trySpend(o, 20F);
            lose(t, p);
            p.serverLevel().sendParticles(C2, t.getX(), t.getY() + t.getBbHeight() + 0.3, t.getZ(), 30, 0.4, 0.4, 0.4, 0);
            sound(p, SoundEvents.EVOKER_CAST_SPELL, 0.9F, 0.7F);
            return true;
        }
    }

    /** Kyle Phone: he calls it in - every enemy within 30 blocks glows for 12 seconds and takes 20% more damage. */
    public static class KylePhone implements Skill {
        @Override public String getId() { return "kyle_phone"; }
        @Override public int getCooldownTicks() { return 18 * 20; }
        @Override public int getKarmaCost() { return 15; }
        @Override public boolean use(ServerPlayer p) {
            int n = 0;
            for (LivingEntity e : around(p, p.position(), 30)) {
                effect(e, MobEffects.GLOWING, 240, 0);
                MARKED.put(e.getUUID(), now(p) + 240);
                n++;
            }
            p.displayClientMessage(Component.literal("Kyle Phone: " + n + " marked").withStyle(ChatFormatting.LIGHT_PURPLE), true);
            sound(p, SoundEvents.NOTE_BLOCK_BIT.value(), 1F, 1.6F);
            return true;
        }
    }

    /** Interrogation: up close, he pins them with questions - stunned for 3 seconds, and he reads how hurt they are. */
    public static class Interrogation implements Skill {
        @Override public String getId() { return "interrogation"; }
        @Override public int getCooldownTicks() { return 14 * 20; }
        @Override public int getKarmaCost() { return 20; }
        @Override public boolean use(ServerPlayer p) {
            LivingEntity t = front(p, 6);
            if (t == null) return false;
            root(t, 60, C2, p);
            effect(t, MobEffects.WEAKNESS, 60, 2);
            effect(t, MobEffects.GLOWING, 200, 0);
            lose(t, p);
            p.displayClientMessage(Component.literal(t.getName().getString() + ": " + Math.round(t.getHealth()) + " / " + Math.round(t.getMaxHealth()) + " health")
                    .withStyle(ChatFormatting.LIGHT_PURPLE), true);
            sound(p, SoundEvents.VILLAGER_NO, 1F, 0.8F);
            return true;
        }
    }

    /** C2 Serum: a shot of the serum - heals an ally (or himself) and clears what ails them. */
    public static class C2Serum implements Skill {
        @Override public String getId() { return "c2_serum"; }
        @Override public int getCooldownTicks() { return 16 * 20; }
        @Override public int getKarmaCost() { return 20; }
        @Override public boolean use(ServerPlayer p) {
            LivingEntity a = ally(p, 16);
            a.heal(8F);
            for (MobEffectInstance i : new ArrayList<>(a.getActiveEffects())) if (!i.getEffect().isBeneficial()) a.removeEffect(i.getEffect());
            a.clearFire();
            effect(a, MobEffects.REGENERATION, 100, 0);
            p.serverLevel().sendParticles(ParticleTypes.HAPPY_VILLAGER, a.getX(), a.getY() + 1, a.getZ(), 14, 0.4, 0.6, 0.4, 0);
            p.serverLevel().sendParticles(C2, a.getX(), a.getY() + 1, a.getZ(), 14, 0.4, 0.6, 0.4, 0);
            sound(p, SoundEvents.BREWING_STAND_BREW, 1F, 1.4F);
            if (a != p) p.displayClientMessage(Component.literal("C2 Serum -> " + a.getName().getString()).withStyle(ChatFormatting.LIGHT_PURPLE), true);
            return true;
        }
    }

    /** Override: he pushes his modifications past their limit - much faster and stronger for 8 seconds, then a slow crash. */
    public static class OverrideSkill implements Skill {
        @Override public String getId() { return "override"; }
        @Override public int getCooldownTicks() { return 25 * 20; }
        @Override public int getKarmaCost() { return 25; }
        @Override public boolean use(ServerPlayer p) {
            effect(p, MobEffects.MOVEMENT_SPEED, 160, 1);
            effect(p, MobEffects.DAMAGE_BOOST, 160, 1);
            effect(p, MobEffects.DIG_SPEED, 160, 1);
            ServerScheduler.later(160, () -> { if (p.isAlive()) effect(p, MobEffects.MOVEMENT_SLOWDOWN, 60, 0); });
            puff(p, p, C2, 30, 0.5);
            sound(p, SoundEvents.BEACON_POWER_SELECT, 1F, 1.5F);
            say(p, "Override!", ChatFormatting.LIGHT_PURPLE);
            return true;
        }
    }

    /** Ultimate - The Weapon of Will: every enemy within 10 blocks is hit and loses the will to fight for 8 seconds. */
    public static class WeaponOfWill implements Skill {
        @Override public String getId() { return "weapon_of_will"; }
        @Override public int getCooldownTicks() { return 80 * 20; }
        @Override public int getKarmaCost() { return 60; }
        @Override public boolean use(ServerPlayer p) {
            List<LivingEntity> l = around(p, p.position(), 10);
            for (LivingEntity e : l) {
                hit(p, e, 10F, true);
                effect(e, MobEffects.WEAKNESS, 160, 2);
                effect(e, MobEffects.MOVEMENT_SLOWDOWN, 160, 2);
                WILL.put(e.getUUID(), now(p) + 160);
                lose(e, p);
            }
            p.serverLevel().sendParticles(C2, p.getX(), p.getY() + 1, p.getZ(), 150, 5, 1, 5, 0);
            p.serverLevel().sendParticles(ParticleTypes.SONIC_BOOM, p.getX(), p.getY() + 1, p.getZ(), 1, 0, 0, 0, 0);
            sound(p, SoundEvents.WARDEN_SONIC_BOOM, 1F, 0.9F);
            say(p, "Kneel. That's an order.", ChatFormatting.LIGHT_PURPLE);
            return true;
        }
    }

    /** Snap Shot (right+left click): a quick shot from his pistol. */
    public static class SnapShot implements Skill {
        @Override public String getId() { return "snap_shot"; }
        @Override public int getCooldownTicks() { return 3 * 20; }
        @Override public int getKarmaCost() { return 5; }
        @Override public boolean use(ServerPlayer p) {
            LivingEntity t = aim(p, 24);
            if (t == null) return false;
            hit(p, t, 4F, false);
            Vec3 a = p.getEyePosition(), b = t.position().add(0, t.getBbHeight() * 0.6, 0);
            for (int i = 0; i <= 12; i++) { Vec3 q = a.lerp(b, i / 12.0); p.serverLevel().sendParticles(ParticleTypes.CRIT, q.x, q.y, q.z, 1, 0, 0, 0, 0); }
            sound(p, SoundEvents.CROSSBOW_SHOOT, 1F, 1.6F);
            return true;
        }
    }

    /** Modification Surge (a buff, lit and put out on the wheel): faster, higher jumps and tougher while it's lit; it eats Karma. */
    public static class ModificationSurge implements Skill {
        @Override public String getId() { return "modification_surge"; }
        @Override public int getCooldownTicks() { return 3 * 20; }
        @Override public int getKarmaCost() { return 10; }
        @Override public boolean use(ServerPlayer p) {
            if (on(SURGE, p)) { SURGE.remove(p.getUUID()); p.displayClientMessage(Component.literal("Modification Surge - off").withStyle(ChatFormatting.GRAY), true); return true; }
            SURGE.put(p.getUUID(), Long.MAX_VALUE);
            puff(p, p, C2, 20, 0.5);
            sound(p, SoundEvents.BEACON_ACTIVATE, 0.8F, 1.6F);
            p.displayClientMessage(Component.literal("Modification Surge - on").withStyle(ChatFormatting.LIGHT_PURPLE), true);
            return true;
        }
    }

    public static boolean surge(ServerPlayer p) { return on(SURGE, p); }

    // ================================================================== ZACK (knife, portals)

    /** Shadow Slash: a dash through them with his knife, leaving a trail of shadow. */
    public static class ShadowSlash implements Skill {
        @Override public String getId() { return "shadow_slash"; }
        @Override public int getCooldownTicks() { return 7 * 20; }
        @Override public int getKarmaCost() { return 15; }
        @Override public boolean use(ServerPlayer p) { dash(p, 1.5, 6, SHADOW, 7F, e -> effect(e, MobEffects.BLINDNESS, 30, 0)); return true; }
    }

    /** Vanish: gone - invisible for 6 seconds, and his Karma can't be sensed or read while he is. */
    public static class Vanish implements Skill {
        @Override public String getId() { return "vanish"; }
        @Override public int getCooldownTicks() { return 20 * 20; }
        @Override public int getKarmaCost() { return 20; }
        @Override public boolean use(ServerPlayer p) {
            effect(p, MobEffects.INVISIBILITY, 120, 0);
            VANISH.put(p.getUUID(), now(p) + 120);
            for (LivingEntity e : around(p, p.position(), 24)) lose(e, p);
            puff(p, p, SHADOW, 40, 0.5);
            sound(p, SoundEvents.ENDERMAN_TELEPORT, 0.6F, 0.6F);
            return true;
        }
    }

    /** Portal Step: through a portal - behind the one he's aiming at, or up to 12 blocks ahead. */
    public static class PortalStep implements Skill {
        @Override public String getId() { return "portal_step"; }
        @Override public int getCooldownTicks() { return 6 * 20; }
        @Override public int getKarmaCost() { return 10; }
        @Override public boolean use(ServerPlayer p) {
            LivingEntity t = SkillTargeting.aimed(p, 16, 10);
            Vec3 to = t == null ? null : behind(p, t);
            if (to == null) to = lookSpot(p, 12);
            blink(p, to, ParticleTypes.PORTAL);
            if (t != null) faceTo(p, t);
            return true;
        }
    }

    /** Assassin's Takedown: a killing stab - double damage from behind, or on someone who isn't after him. */
    public static class AssassinsTakedown implements Skill {
        @Override public String getId() { return "assassins_takedown"; }
        @Override public int getCooldownTicks() { return 12 * 20; }
        @Override public int getKarmaCost() { return 25; }
        @Override public boolean use(ServerPlayer p) {
            LivingEntity t = front(p, 4.5);
            if (t == null) return false;
            Vec3 tf = Vec3.directionFromRotation(0, t.getYRot()), to = p.position().subtract(t.position()).multiply(1, 0, 1);
            boolean back = to.lengthSqr() > 0 && to.normalize().dot(tf) < -0.2;
            boolean unaware = !(t instanceof Mob m && m.getTarget() == p) && !(t instanceof ServerPlayer);
            float dmg = back || unaware ? 18F : 9F;
            p.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
            hit(p, t, dmg, false);
            effect(t, MobEffects.WITHER, 60, 0);
            p.serverLevel().sendParticles(ParticleTypes.DAMAGE_INDICATOR, t.getX(), t.getY() + 1, t.getZ(), back || unaware ? 14 : 6, 0.2, 0.3, 0.2, 0.1);
            sound(p, SoundEvents.PLAYER_ATTACK_CRIT, 1F, 0.8F);
            if (back || unaware) p.displayClientMessage(Component.literal("Takedown!").withStyle(ChatFormatting.DARK_RED), true);
            return true;
        }
    }

    /** Ultimate - Portal Ambush: portal to portal, he cuts up to six enemies within 16 blocks and comes back. */
    public static class PortalAmbush implements Skill {
        @Override public String getId() { return "portal_ambush"; }
        @Override public int getCooldownTicks() { return 70 * 20; }
        @Override public int getKarmaCost() { return 55; }
        @Override public boolean use(ServerPlayer p) {
            List<LivingEntity> l = around(p, p.position(), 16);
            if (l.isEmpty()) { p.displayClientMessage(Component.translatable("message.xenowarriors.no_target"), true); return false; }
            l.sort(Comparator.comparingDouble(e -> e.distanceToSqr(p)));
            Vec3 home = p.position();
            int n = Math.min(6, l.size());
            effect(p, MobEffects.DAMAGE_RESISTANCE, 8 * n + 10, 3);
            for (int i = 0; i < n; i++) {
                LivingEntity t = l.get(i);
                ServerScheduler.later(i * 8, () -> {
                    if (!p.isAlive() || !t.isAlive()) return;
                    Vec3 to = behind(p, t);
                    if (to == null) to = t.position().add(0.8, 0, 0.8);
                    blink(p, to, ParticleTypes.PORTAL);
                    faceTo(p, t);
                    p.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
                    hit(p, t, 9F, false);
                    p.serverLevel().sendParticles(ParticleTypes.SWEEP_ATTACK, t.getX(), t.getY() + 1, t.getZ(), 2, 0.2, 0.2, 0.2, 0);
                });
            }
            ServerScheduler.later(n * 8 + 4, () -> { if (p.isAlive()) blink(p, home, ParticleTypes.PORTAL); });
            say(p, "You didn't see me.", ChatFormatting.DARK_PURPLE);
            return true;
        }
    }

    /** Knife Throw (right+left click): a thrown knife. */
    public static class KnifeThrow implements Skill {
        @Override public String getId() { return "knife_throw"; }
        @Override public int getCooldownTicks() { return 3 * 20; }
        @Override public int getKarmaCost() { return 5; }
        @Override public boolean use(ServerPlayer p) {
            LivingEntity t = aim(p, 20);
            if (t == null) return false;
            Vec3 a = p.getEyePosition(), b = t.position().add(0, t.getBbHeight() * 0.6, 0);
            int steps = Math.max(2, (int) (a.distanceTo(b) / 2.5));
            for (int i = 0; i <= steps; i++) { int ii = i; ServerScheduler.later(i / 2, () -> { Vec3 q = a.lerp(b, ii / (double) steps); p.serverLevel().sendParticles(ParticleTypes.CRIT, q.x, q.y, q.z, 2, 0.02, 0.02, 0.02, 0); }); }
            ServerScheduler.later(steps / 2, () -> { if (t.isAlive()) { hit(p, t, 5F, false); effect(t, MobEffects.MOVEMENT_SLOWDOWN, 40, 0); } });
            sound(p, SoundEvents.TRIDENT_THROW, 1F, 1.4F);
            return true;
        }
    }

    /**
     * Zack's KA - Infinite Loop: while he stands still (up to 15 seconds), his target is caught in a loop - every 5
     * seconds it's pulled back to where it stood when it began and has to start over.
     */
    public static boolean infiniteLoop(ServerPlayer p) {
        LivingEntity t = aim(p, 20);
        if (t == null) return false;
        if (!p.isCreative() && !com.xenowarriors.karma.Karma.trySpend(p, 35F)) return false;
        Vec3 start = t.position(), me = p.position();
        float yaw = t.getYRot();
        say(p, "KA - Infinite Loop!", ChatFormatting.DARK_PURPLE);
        for (int i = 1; i <= 300; i++) {
            int ii = i;
            ServerScheduler.later(i, () -> {
                if (!p.isAlive() || !t.isAlive() || p.position().distanceToSqr(me) > 0.5 || LOOP_BROKEN.contains(p.getUUID())) { LOOP_BROKEN.add(p.getUUID()); return; }
                ServerLevel l = p.serverLevel();
                if (ii % 10 == 0) l.sendParticles(ParticleTypes.REVERSE_PORTAL, start.x, start.y + 0.1, start.z, 10, 0.5, 0.05, 0.5, 0.02);
                if (ii % 100 == 0) {
                    l.sendParticles(ParticleTypes.PORTAL, t.getX(), t.getY() + 1, t.getZ(), 30, 0.4, 0.8, 0.4, 0.3);
                    if (t instanceof ServerPlayer sp) sp.connection.teleport(start.x, start.y, start.z, yaw, sp.getXRot());
                    else { t.teleportTo(start.x, start.y, start.z); t.setYRot(yaw); }
                    t.setDeltaMovement(Vec3.ZERO); t.hurtMarked = true;
                    hit(p, t, 3F, true);
                    if (t instanceof Mob m) { m.getNavigation().stop(); m.setTarget(null); }
                    l.playSound(null, t.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8F, 0.6F);
                }
            });
        }
        ServerScheduler.later(301, () -> LOOP_BROKEN.remove(p.getUUID()));
        return true;
    }
    static final Set<UUID> LOOP_BROKEN = new HashSet<>();

    // ================================================================== KAITLYN (Kate)

    /** Fake Cry: tears on cue - enemies around her stop going after her, and lower their guard. */
    public static class FakeCry implements Skill {
        @Override public String getId() { return "fake_cry"; }
        @Override public int getCooldownTicks() { return 16 * 20; }
        @Override public int getKarmaCost() { return 15; }
        @Override public boolean use(ServerPlayer p) {
            for (LivingEntity e : around(p, p.position(), 10)) {
                lose(e, p);
                effect(e, MobEffects.WEAKNESS, 120, 1);
                if (e instanceof ServerPlayer) effect(e, MobEffects.MOVEMENT_SLOWDOWN, 60, 1);
            }
            p.serverLevel().sendParticles(ParticleTypes.FALLING_WATER, p.getX(), p.getEyeY(), p.getZ(), 20, 0.25, 0.1, 0.25, 0);
            say(p, "*sniff* ...why would you hurt me?", ChatFormatting.AQUA);
            sound(p, SoundEvents.VILLAGER_HURT, 0.8F, 1.6F);
            return true;
        }
    }

    /** Dark Karma Burst: dark Karma bursts where she aims (or around her) - damage and withering. */
    public static class DarkKarmaBurst implements Skill {
        @Override public String getId() { return "dark_karma_burst"; }
        @Override public int getCooldownTicks() { return 9 * 20; }
        @Override public int getKarmaCost() { return 20; }
        @Override public boolean use(ServerPlayer p) {
            LivingEntity t = SkillTargeting.aimed(p, 16, 12);
            Vec3 c = t != null ? t.position() : p.position();
            for (LivingEntity e : around(p, c, 4.5)) { hit(p, e, 7F, true); effect(e, MobEffects.WITHER, 60, 0); push(e, c, 0.5, 0.25); }
            ServerLevel l = p.serverLevel();
            l.sendParticles(DARK_KARMA, c.x, c.y + 1, c.z, 80, 2, 0.8, 2, 0);
            l.sendParticles(ParticleTypes.SQUID_INK, c.x, c.y + 1, c.z, 30, 1.5, 0.6, 1.5, 0.05);
            l.playSound(null, net.minecraft.core.BlockPos.containing(c), SoundEvents.WITHER_SHOOT, SoundSource.PLAYERS, 0.8F, 1.2F);
            return true;
        }
    }

    /** Demon Claw: her claws rake everything in front of her - it keeps bleeding. */
    public static class DemonClaw implements Skill {
        @Override public String getId() { return "demon_claw"; }
        @Override public int getCooldownTicks() { return 7 * 20; }
        @Override public int getKarmaCost() { return 15; }
        @Override public boolean use(ServerPlayer p) {
            List<LivingEntity> l = cone(p, 4.5);
            if (l.isEmpty()) { p.displayClientMessage(Component.translatable("message.xenowarriors.no_target"), true); return false; }
            p.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
            for (LivingEntity e : l) { hit(p, e, 8F, false); effect(e, MobEffects.WITHER, 80, 1); p.serverLevel().sendParticles(RED, e.getX(), e.getY() + 1, e.getZ(), 12, 0.3, 0.4, 0.3, 0); }
            sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 1F, 0.7F);
            return true;
        }
    }

    /**
     * Walkie-Talkie: she calls Zack. Your Zack bot, if you have one, comes through a portal at her target; if not, he
     * comes through just long enough to cut it three times.
     */
    public static class WalkieTalkie implements Skill {
        @Override public String getId() { return "walkie_talkie"; }
        @Override public int getCooldownTicks() { return 25 * 20; }
        @Override public int getKarmaCost() { return 25; }
        @Override public boolean use(ServerPlayer p) {
            LivingEntity t = aim(p, 24);
            if (t == null) return false;
            say(p, "Zack! Get over here!", ChatFormatting.RED);
            sound(p, SoundEvents.NOTE_BLOCK_BIT.value(), 1F, 0.9F);
            PartyBotEntity zack = null;
            ServerPlayer owner = p instanceof net.minecraftforge.common.util.FakePlayer ? null : p;
            if (owner != null) for (PartyBotEntity b : com.xenowarriors.bot.PartyBots.bots(owner)) if (b.character() == CharacterType.ZACK) zack = b;
            if (zack != null) {
                Vec3 at = t.position().add(t.getLookAngle().multiply(1, 0, 1).normalize().scale(-1.3));
                p.serverLevel().sendParticles(ParticleTypes.PORTAL, zack.getX(), zack.getY() + 1, zack.getZ(), 30, 0.3, 0.8, 0.3, 0.3);
                zack.teleportTo(at.x, t.getY(), at.z);
                zack.setTarget(t);
                p.serverLevel().sendParticles(ParticleTypes.PORTAL, at.x, t.getY() + 1, at.z, 40, 0.3, 0.8, 0.3, 0.3);
                hit(p, t, 6F, false);
                return true;
            }
            for (int i = 0; i < 3; i++) {
                ServerScheduler.later(6 + i * 6, () -> {
                    if (!t.isAlive()) return;
                    ServerLevel l = p.serverLevel();
                    l.sendParticles(ParticleTypes.PORTAL, t.getX(), t.getY() + 1, t.getZ(), 25, 0.6, 0.8, 0.6, 0.3);
                    l.sendParticles(ParticleTypes.SWEEP_ATTACK, t.getX(), t.getY() + 1, t.getZ(), 1, 0, 0, 0, 0);
                    hit(p, t, 4F, false);
                    l.playSound(null, t.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1F, 1.3F);
                });
            }
            return true;
        }
    }

    /** Ultimate - Dark Karma Overload: for 6 seconds her dark Karma pours out - a burst around her every second, and she's hard to hurt. */
    public static class DarkKarmaOverload implements Skill {
        @Override public String getId() { return "dark_karma_overload"; }
        @Override public int getCooldownTicks() { return 75 * 20; }
        @Override public int getKarmaCost() { return 55; }
        @Override public boolean use(ServerPlayer p) {
            effect(p, MobEffects.DAMAGE_RESISTANCE, 120, 1);
            say(p, "You want to see dark Karma? HERE!", ChatFormatting.DARK_PURPLE);
            for (int i = 0; i < 6; i++) {
                ServerScheduler.later(i * 20, () -> {
                    if (!p.isAlive()) return;
                    for (LivingEntity e : around(p, p.position(), 8)) { hit(p, e, 6F, true); effect(e, MobEffects.WITHER, 60, 1); push(e, p.position(), 0.6, 0.2); }
                    p.serverLevel().sendParticles(DARK_KARMA, p.getX(), p.getY() + 1, p.getZ(), 120, 4, 1, 4, 0);
                    p.serverLevel().sendParticles(ParticleTypes.SQUID_INK, p.getX(), p.getY() + 1, p.getZ(), 40, 3, 0.5, 3, 0.05);
                    sound(p, SoundEvents.WITHER_SHOOT, 0.7F, 0.8F);
                });
            }
            return true;
        }
    }

    /** Taunt (right+left click): everything around her comes for her - and she braces for it. */
    public static class Taunt implements Skill {
        @Override public String getId() { return "taunt"; }
        @Override public int getCooldownTicks() { return 8 * 20; }
        @Override public int getKarmaCost() { return 5; }
        @Override public boolean use(ServerPlayer p) {
            for (LivingEntity e : around(p, p.position(), 12)) if (e instanceof Mob m) m.setTarget(p);
            effect(p, MobEffects.DAMAGE_RESISTANCE, 80, 0);
            say(p, "Hey! Over here, loser!", ChatFormatting.RED);
            sound(p, SoundEvents.PIGLIN_ANGRY, 0.8F, 1.5F);
            return true;
        }
    }

    /** Kaitlyn's KA - Raised Hand: she raises her hand and her target can't move from where it stands for 6 seconds. */
    public static boolean raisedHand(ServerPlayer p) {
        LivingEntity t = aim(p, 20);
        if (t == null) return false;
        if (!p.isCreative() && !com.xenowarriors.karma.Karma.trySpend(p, 35F)) return false;
        say(p, "KA - Raised Hand!", ChatFormatting.DARK_PURPLE);
        root(t, 120, DARK_KARMA, p);
        lose(t, p);
        com.xenowarriors.anim.Poses.play(p, com.xenowarriors.anim.Poses.KA_BREAK, 12);
        return true;
    }

    // ================================================================== CHASE (the Keeper, two swords)

    /** Twin Blade: both swords, one after the other. */
    public static class TwinBlade implements Skill {
        @Override public String getId() { return "twin_blade"; }
        @Override public int getCooldownTicks() { return 6 * 20; }
        @Override public int getKarmaCost() { return 15; }
        @Override public boolean use(ServerPlayer p) {
            LivingEntity t = front(p, 4.5);
            if (t == null) return false;
            p.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
            hit(p, t, 6F, false);
            p.serverLevel().sendParticles(ParticleTypes.SWEEP_ATTACK, t.getX(), t.getY() + 1, t.getZ(), 1, 0, 0, 0, 0);
            sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 1F, 1.3F);
            ServerScheduler.later(5, () -> {
                if (!p.isAlive() || !t.isAlive()) return;
                p.swing(net.minecraft.world.InteractionHand.OFF_HAND, true);
                hit(p, t, 6F, false);
                p.serverLevel().sendParticles(KEEPER, t.getX(), t.getY() + 1, t.getZ(), 12, 0.3, 0.4, 0.3, 0);
                sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 1F, 1.0F);
            });
            return true;
        }
    }

    /** Soul Snatch: he tears at their soul - what it takes heals him. */
    public static class SoulSnatch implements Skill {
        @Override public String getId() { return "soul_snatch"; }
        @Override public int getCooldownTicks() { return 14 * 20; }
        @Override public int getKarmaCost() { return 25; }
        @Override public boolean use(ServerPlayer p) {
            LivingEntity t = aim(p, 8);
            if (t == null) return false;
            float before = t.getHealth();
            hit(p, t, 7F, true);
            p.heal(Math.max(2F, before - t.getHealth()));
            effect(t, MobEffects.WEAKNESS, 100, 0);
            Vec3 a = t.position().add(0, 1, 0), b = p.position().add(0, 1, 0);
            for (int i = 0; i <= 10; i++) { Vec3 q = a.lerp(b, i / 10.0); p.serverLevel().sendParticles(ParticleTypes.SOUL, q.x, q.y, q.z, 1, 0.05, 0.05, 0.05, 0.01); }
            sound(p, SoundEvents.SOUL_ESCAPE, 1.2F, 0.8F);
            return true;
        }
    }

    /** "Begone": one word, and everything in front of him is thrown back. */
    public static class Begone implements Skill {
        @Override public String getId() { return "begone"; }
        @Override public int getCooldownTicks() { return 10 * 20; }
        @Override public int getKarmaCost() { return 20; }
        @Override public boolean use(ServerPlayer p) {
            for (LivingEntity e : cone(p, 7)) { hit(p, e, 4F, true); push(e, p.position(), 2.4, 0.5); }
            Vec3 f = p.getLookAngle().multiply(1, 0, 1).normalize();
            for (int i = 1; i <= 6; i++) p.serverLevel().sendParticles(ParticleTypes.SWEEP_ATTACK, p.getX() + f.x * i, p.getY() + 1, p.getZ() + f.z * i, 1, 0.3, 0.2, 0.3, 0);
            say(p, "Begone.", ChatFormatting.DARK_RED);
            sound(p, SoundEvents.ENDER_DRAGON_FLAP, 1F, 0.7F);
            return true;
        }
    }

    /** Keeper's Step: a flash forward (8 blocks) - untouchable for a moment. */
    public static class KeepersStep implements Skill {
        @Override public String getId() { return "keepers_step"; }
        @Override public int getCooldownTicks() { return 6 * 20; }
        @Override public int getKarmaCost() { return 10; }
        @Override public boolean use(ServerPlayer p) {
            effect(p, MobEffects.DAMAGE_RESISTANCE, 12, 4);
            blink(p, lookSpot(p, 8), KEEPER);
            return true;
        }
    }

    /** Ultimate - Keeper's Judgement: everyone within 12 blocks is judged - a moment later his swords cross through each of them. */
    public static class KeepersJudgement implements Skill {
        @Override public String getId() { return "keepers_judgement"; }
        @Override public int getCooldownTicks() { return 80 * 20; }
        @Override public int getKarmaCost() { return 60; }
        @Override public boolean use(ServerPlayer p) {
            List<LivingEntity> l = around(p, p.position(), 12);
            if (l.isEmpty()) { p.displayClientMessage(Component.translatable("message.xenowarriors.no_target"), true); return false; }
            for (LivingEntity e : l) { effect(e, MobEffects.GLOWING, 40, 0); root(e, 30, KEEPER, p); }
            say(p, "Judgement.", ChatFormatting.DARK_RED);
            sound(p, SoundEvents.BELL_BLOCK, 1F, 0.5F);
            ServerScheduler.later(30, () -> {
                if (!p.isAlive()) return;
                for (LivingEntity e : l) {
                    if (!e.isAlive()) continue;
                    hit(p, e, 16F, false);
                    p.serverLevel().sendParticles(ParticleTypes.SWEEP_ATTACK, e.getX(), e.getY() + 1, e.getZ(), 3, 0.3, 0.3, 0.3, 0);
                    p.serverLevel().sendParticles(KEEPER, e.getX(), e.getY() + 1, e.getZ(), 30, 0.4, 0.8, 0.4, 0);
                }
                sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 1.2F, 0.6F);
            });
            return true;
        }
    }

    /** Sword Draw (right+left click): a quick-draw cut as he rushes past. */
    public static class SwordDraw implements Skill {
        @Override public String getId() { return "sword_draw"; }
        @Override public int getCooldownTicks() { return 4 * 20; }
        @Override public int getKarmaCost() { return 5; }
        @Override public boolean use(ServerPlayer p) { dash(p, 1.4, 3, KEEPER, 6F, null); return true; }
    }

    /** Hidden Will (on the wheel, starts on): nobody can lock on to Chase, and his Karma can't be sensed or read. */
    public static boolean hiddenWill(ServerPlayer p) {
        return type(p) == CharacterType.CHASE && com.xenowarriors.toggle.Toggles.enabled(p, com.xenowarriors.toggle.Toggles.Entry.HIDDEN_WILL);
    }

    /** Their Karma can't be sensed or read right now (Zack's Vanish, Chase's Hidden Will). */
    public static boolean karmaHidden(Entity e) {
        if (!(e instanceof ServerPlayer p)) return false;
        return on(VANISH, p) || hiddenWill(p);
    }

    // ================================================================== SENJU (the SR leader)

    /** Father's Blade: one heavy cut with the long sword - it throws them back. */
    public static class FathersBlade implements Skill {
        @Override public String getId() { return "fathers_blade"; }
        @Override public int getCooldownTicks() { return 8 * 20; }
        @Override public int getKarmaCost() { return 20; }
        @Override public boolean use(ServerPlayer p) {
            List<LivingEntity> l = cone(p, 5);
            if (l.isEmpty()) { p.displayClientMessage(Component.translatable("message.xenowarriors.no_target"), true); return false; }
            p.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
            for (LivingEntity e : l) { hit(p, e, 10F, false); push(e, p.position(), 1.2, 0.35); p.serverLevel().sendParticles(ParticleTypes.SWEEP_ATTACK, e.getX(), e.getY() + 1, e.getZ(), 2, 0.3, 0.2, 0.3, 0); }
            sound(p, SoundEvents.PLAYER_ATTACK_STRONG, 1F, 0.6F);
            return true;
        }
    }

    /** Wing Gust: a beat of his wings - a gust that throws everything in front of him away (and lifts him a little). */
    public static class WingGust implements Skill {
        @Override public String getId() { return "wing_gust"; }
        @Override public int getCooldownTicks() { return 10 * 20; }
        @Override public int getKarmaCost() { return 15; }
        @Override public boolean use(ServerPlayer p) {
            for (LivingEntity e : cone(p, 9)) { hit(p, e, 4F, false); push(e, p.position(), 2.0, 0.6); }
            p.setDeltaMovement(p.getDeltaMovement().add(0, 0.6, 0)); p.hurtMarked = true; p.fallDistance = 0;
            Vec3 f = p.getLookAngle().multiply(1, 0, 1).normalize();
            for (int i = 1; i <= 8; i++) p.serverLevel().sendParticles(WIND, p.getX() + f.x * i, p.getY() + 1, p.getZ() + f.z * i, 8, 0.6, 0.5, 0.6, 0);
            p.serverLevel().sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 1, p.getZ(), 30, 1, 0.4, 1, 0.1);
            sound(p, SoundEvents.ENDER_DRAGON_FLAP, 1F, 1.0F);
            return true;
        }
    }

    /** Demon Grant: he lends a demon's strength - an ally (or himself) is stronger and faster for 10 seconds. */
    public static class DemonGrant implements Skill {
        @Override public String getId() { return "demon_grant"; }
        @Override public int getCooldownTicks() { return 22 * 20; }
        @Override public int getKarmaCost() { return 25; }
        @Override public boolean use(ServerPlayer p) {
            LivingEntity a = ally(p, 16);
            effect(a, MobEffects.DAMAGE_BOOST, 200, 1);
            effect(a, MobEffects.MOVEMENT_SPEED, 200, 0);
            p.serverLevel().sendParticles(ParticleTypes.SOUL_FIRE_FLAME, a.getX(), a.getY() + 1, a.getZ(), 30, 0.4, 0.8, 0.4, 0.03);
            p.serverLevel().sendParticles(SHADOW, a.getX(), a.getY() + 1, a.getZ(), 20, 0.4, 0.8, 0.4, 0);
            sound(p, SoundEvents.EVOKER_CAST_SPELL, 1F, 0.5F);
            if (a != p) p.displayClientMessage(Component.literal("Demon Grant -> " + a.getName().getString()).withStyle(ChatFormatting.DARK_RED), true);
            return true;
        }
    }

    /** Ultimate - Rebellion's End: up into the sky, and down like a falling star - a huge burst where he lands. */
    public static class RebellionsEnd implements Skill {
        @Override public String getId() { return "rebellions_end"; }
        @Override public int getCooldownTicks() { return 85 * 20; }
        @Override public int getKarmaCost() { return 60; }
        @Override public boolean use(ServerPlayer p) {
            effect(p, MobEffects.DAMAGE_RESISTANCE, 50, 4);
            p.setDeltaMovement(0, 1.6, 0); p.hurtMarked = true;
            say(p, "This is where your rebellion ends.", ChatFormatting.GOLD);
            sound(p, SoundEvents.ENDER_DRAGON_FLAP, 1.2F, 0.6F);
            ServerScheduler.later(14, () -> { if (p.isAlive()) { p.setDeltaMovement(p.getLookAngle().multiply(0.6, 0, 0.6).add(0, -2.4, 0)); p.hurtMarked = true; } });
            for (int i = 15; i < 50; i++) {
                int ii = i;
                ServerScheduler.later(i, () -> {
                    if (!p.isAlive() || LANDED.contains(p.getUUID())) return;
                    p.serverLevel().sendParticles(HEAVEN, p.getX(), p.getY() + 1, p.getZ(), 6, 0.3, 0.5, 0.3, 0);
                    if (p.onGround() || ii == 49) {
                        LANDED.add(p.getUUID());
                        p.fallDistance = 0;
                        ServerLevel l = p.serverLevel();
                        for (LivingEntity e : around(p, p.position(), 7)) { hit(p, e, 18F, false); e.setSecondsOnFire(4); push(e, p.position(), 1.4, 0.6); }
                        l.sendParticles(ParticleTypes.EXPLOSION_EMITTER, p.getX(), p.getY(), p.getZ(), 1, 0, 0, 0, 0);
                        l.sendParticles(HEAVEN, p.getX(), p.getY() + 0.5, p.getZ(), 150, 4, 0.5, 4, 0);
                        l.sendParticles(ParticleTypes.FLAME, p.getX(), p.getY() + 0.3, p.getZ(), 80, 4, 0.2, 4, 0.05);
                        l.playSound(null, p.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.2F, 0.8F);
                    }
                });
            }
            ServerScheduler.later(51, () -> LANDED.remove(p.getUUID()));
            return true;
        }
    }
    static final Set<UUID> LANDED = new HashSet<>();

    /** Demon Glare (right+left click): one look - those in front of him freeze up. */
    public static class DemonGlare implements Skill {
        @Override public String getId() { return "demon_glare"; }
        @Override public int getCooldownTicks() { return 6 * 20; }
        @Override public int getKarmaCost() { return 5; }
        @Override public boolean use(ServerPlayer p) {
            for (LivingEntity e : cone(p, 10)) { effect(e, MobEffects.MOVEMENT_SLOWDOWN, 80, 2); effect(e, MobEffects.WEAKNESS, 80, 0); lose(e, p); }
            p.serverLevel().sendParticles(RED, p.getX(), p.getEyeY(), p.getZ(), 10, 0.2, 0.1, 0.2, 0);
            sound(p, SoundEvents.ELDER_GUARDIAN_CURSE, 0.5F, 1.3F);
            return true;
        }
    }

    // ================================================================== AVA (the cage fighter)

    /** Cage Fighter: 8 seconds of the ring - her bare-handed hits land much harder, and she can't be knocked around. */
    public static class CageFighter implements Skill {
        @Override public String getId() { return "cage_fighter"; }
        @Override public int getCooldownTicks() { return 20 * 20; }
        @Override public int getKarmaCost() { return 20; }
        @Override public boolean use(ServerPlayer p) {
            CAGE.put(p.getUUID(), now(p) + 160);
            puff(p, p, RED, 25, 0.5);
            say(p, "Let's go. One on one.", ChatFormatting.RED);
            sound(p, SoundEvents.ANVIL_LAND, 0.5F, 1.5F);
            return true;
        }
    }

    /** Red Slash: a dash with a red cut of demon Karma. */
    public static class RedSlash implements Skill {
        @Override public String getId() { return "red_slash"; }
        @Override public int getCooldownTicks() { return 7 * 20; }
        @Override public int getKarmaCost() { return 15; }
        @Override public boolean use(ServerPlayer p) { dash(p, 1.5, 6, RED, 7F, e -> e.setSecondsOnFire(2)); return true; }
    }

    /** Tough Body: she takes it - very hard to hurt for 5 seconds, and a little extra health. */
    public static class ToughBody implements Skill {
        @Override public String getId() { return "tough_body"; }
        @Override public int getCooldownTicks() { return 22 * 20; }
        @Override public int getKarmaCost() { return 20; }
        @Override public boolean use(ServerPlayer p) {
            effect(p, MobEffects.DAMAGE_RESISTANCE, 100, 2);
            effect(p, MobEffects.ABSORPTION, 200, 1);
            puff(p, p, ParticleTypes.CRIT, 20, 0.5);
            sound(p, SoundEvents.ARMOR_EQUIP_NETHERITE, 1F, 0.8F);
            return true;
        }
    }

    /** Ground Pound: up and down onto the ground with both fists - everything around her is hit and thrown up. */
    public static class GroundPound implements Skill {
        @Override public String getId() { return "ground_pound"; }
        @Override public int getCooldownTicks() { return 10 * 20; }
        @Override public int getKarmaCost() { return 20; }
        @Override public boolean use(ServerPlayer p) {
            p.setDeltaMovement(0, 0.7, 0); p.hurtMarked = true;
            ServerScheduler.later(7, () -> { if (p.isAlive()) { p.setDeltaMovement(0, -1.6, 0); p.hurtMarked = true; } });
            ServerScheduler.later(12, () -> {
                if (!p.isAlive()) return;
                p.fallDistance = 0;
                for (LivingEntity e : around(p, p.position(), 5)) { hit(p, e, 7F, false); e.setDeltaMovement(e.getDeltaMovement().add(0, 0.8, 0)); e.hurtMarked = true; }
                p.serverLevel().sendParticles(ParticleTypes.EXPLOSION, p.getX(), p.getY(), p.getZ(), 4, 1.5, 0.1, 1.5, 0);
                p.serverLevel().sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, p.level().getBlockState(p.blockPosition().below())), p.getX(), p.getY() + 0.1, p.getZ(), 60, 2.5, 0.1, 2.5, 0.2);
                sound(p, SoundEvents.GENERIC_EXPLODE, 0.8F, 1.2F);
            });
            return true;
        }
    }

    /** Ultimate - Demon Unleashed: 12 seconds of her demon side all the way out - a roar that throws them back, then much stronger and faster. */
    public static class AvaUnleashed implements Skill {
        @Override public String getId() { return "ava_unleashed"; }
        @Override public int getCooldownTicks() { return 80 * 20; }
        @Override public int getKarmaCost() { return 55; }
        @Override public boolean use(ServerPlayer p) {
            for (LivingEntity e : around(p, p.position(), 6)) { hit(p, e, 6F, true); push(e, p.position(), 1.6, 0.5); }
            effect(p, MobEffects.DAMAGE_BOOST, 240, 2);
            effect(p, MobEffects.MOVEMENT_SPEED, 240, 1);
            effect(p, MobEffects.DAMAGE_RESISTANCE, 240, 0);
            UNLEASHED.put(p.getUUID(), now(p) + 240);
            p.serverLevel().sendParticles(RED, p.getX(), p.getY() + 1, p.getZ(), 120, 1.5, 1, 1.5, 0);
            p.serverLevel().sendParticles(ParticleTypes.FLAME, p.getX(), p.getY() + 1, p.getZ(), 50, 1, 1, 1, 0.08);
            sound(p, SoundEvents.RAVAGER_ROAR, 1F, 1.2F);
            say(p, "RAAAAH!", ChatFormatting.DARK_RED);
            return true;
        }
    }

    /** Haymaker (right+left click): one big wind-up punch. */
    public static class Haymaker implements Skill {
        @Override public String getId() { return "haymaker"; }
        @Override public int getCooldownTicks() { return 4 * 20; }
        @Override public int getKarmaCost() { return 5; }
        @Override public boolean use(ServerPlayer p) {
            LivingEntity t = front(p, 5);
            if (t == null) return false;
            p.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
            hit(p, t, 8F, false);
            push(t, p.position(), 1.5, 0.4);
            p.serverLevel().sendParticles(ParticleTypes.CRIT, t.getX(), t.getY() + 1, t.getZ(), 15, 0.3, 0.3, 0.3, 0.2);
            sound(p, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1F, 0.7F);
            return true;
        }
    }

    // ================================================================== PERSEPHONE

    /** Shadow Voice: her voice from the shadows - those around her are dizzy and lost (blinded, weakened, they lose her). */
    public static class ShadowVoice implements Skill {
        @Override public String getId() { return "shadow_voice"; }
        @Override public int getCooldownTicks() { return 12 * 20; }
        @Override public int getKarmaCost() { return 20; }
        @Override public boolean use(ServerPlayer p) {
            for (LivingEntity e : around(p, p.position(), 10)) {
                effect(e, MobEffects.BLINDNESS, 80, 0);
                effect(e, MobEffects.CONFUSION, 120, 0);
                effect(e, MobEffects.WEAKNESS, 100, 0);
                hit(p, e, 3F, true);
                lose(e, p);
            }
            p.serverLevel().sendParticles(SHADOW, p.getX(), p.getY() + 1, p.getZ(), 80, 4, 1, 4, 0);
            sound(p, SoundEvents.WARDEN_NEARBY_CLOSER, 1F, 1.4F);
            return true;
        }
    }

    /** Protect Ava: a shield of shadow on an ally (or herself) - stronger on Ava. */
    public static class ProtectAva implements Skill {
        @Override public String getId() { return "protect_ava"; }
        @Override public int getCooldownTicks() { return 20 * 20; }
        @Override public int getKarmaCost() { return 20; }
        @Override public boolean use(ServerPlayer p) {
            LivingEntity a = null;
            for (LivingEntity e : allies(p, 20)) if (is(e, CharacterType.AVA)) a = e;
            if (a == null) a = ally(p, 16);
            boolean ava = is(a, CharacterType.AVA);
            effect(a, MobEffects.ABSORPTION, 200, ava ? 3 : 1);
            effect(a, MobEffects.DAMAGE_RESISTANCE, 100, ava ? 1 : 0);
            p.serverLevel().sendParticles(SHADOW, a.getX(), a.getY() + 1, a.getZ(), 40, 0.5, 0.9, 0.5, 0);
            sound(p, SoundEvents.AMETHYST_BLOCK_CHIME, 1.2F, 0.6F);
            if (ava) say(p, "Nobody touches my sister.", ChatFormatting.DARK_PURPLE);
            return true;
        }
    }

    /** Dark Bind: shadows hold her target where it stands for 4 seconds, and squeeze. */
    public static class DarkBind implements Skill {
        @Override public String getId() { return "dark_bind"; }
        @Override public int getCooldownTicks() { return 14 * 20; }
        @Override public int getKarmaCost() { return 20; }
        @Override public boolean use(ServerPlayer p) {
            LivingEntity t = aim(p, 16);
            if (t == null) return false;
            root(t, 80, SHADOW, p);
            for (int i = 1; i <= 4; i++) ServerScheduler.later(i * 20, () -> { if (t.isAlive()) hit(p, t, 2.5F, true); });
            sound(p, SoundEvents.SCULK_SHRIEKER_SHRIEK, 0.6F, 1.6F);
            return true;
        }
    }

    /** Shadow Step: into the shadows and out right behind her target - who's left weakened. */
    public static class ShadowStep implements Skill {
        @Override public String getId() { return "shadow_step"; }
        @Override public int getCooldownTicks() { return 9 * 20; }
        @Override public int getKarmaCost() { return 15; }
        @Override public boolean use(ServerPlayer p) {
            LivingEntity t = aim(p, 16);
            if (t == null) return false;
            Vec3 to = behind(p, t);
            if (to == null) { p.displayClientMessage(Component.literal("No room behind them").withStyle(ChatFormatting.GRAY), true); return false; }
            blink(p, to, SHADOW);
            faceTo(p, t);
            effect(t, MobEffects.WEAKNESS, 80, 1);
            lose(t, p);
            return true;
        }
    }

    /** Ultimate - Sister's Vengeance: a wave of shadow - every enemy within 14 blocks is hit hard and blinded, and she heals for each. */
    public static class SistersVengeance implements Skill {
        @Override public String getId() { return "sisters_vengeance"; }
        @Override public int getCooldownTicks() { return 80 * 20; }
        @Override public int getKarmaCost() { return 60; }
        @Override public boolean use(ServerPlayer p) {
            List<LivingEntity> l = around(p, p.position(), 14);
            say(p, "You'll pay for every scratch on her.", ChatFormatting.DARK_PURPLE);
            for (int ring = 1; ring <= 7; ring++) {
                int r = ring * 2;
                ServerScheduler.later(ring * 2, () -> {
                    for (int a = 0; a < 24; a++) {
                        double ang = a * Math.PI / 12;
                        p.serverLevel().sendParticles(SHADOW, p.getX() + Math.cos(ang) * r, p.getY() + 0.5, p.getZ() + Math.sin(ang) * r, 2, 0.1, 0.3, 0.1, 0);
                    }
                });
            }
            ServerScheduler.later(8, () -> {
                if (!p.isAlive()) return;
                for (LivingEntity e : l) { if (!e.isAlive()) continue; hit(p, e, 14F, true); effect(e, MobEffects.BLINDNESS, 100, 0); p.heal(1.5F); }
            });
            sound(p, SoundEvents.WITHER_SPAWN, 0.6F, 1.5F);
            return true;
        }
    }

    // ================================================================== the transform-key forms

    /**
     * A transform-key form (Demon Form for Zack, Kaitlyn, Ava and Persephone; Heavenly Demon for Senju): timed like
     * Devil Unleashed (mastery makes it last longer, cheaper and back sooner), can't be stopped early. Ava and
     * Persephone take on their demon looks while it's on.
     */
    public static class Form implements Skill {
        public final String id, title;
        final int seconds;
        final boolean heavenly;
        public Form(String id, String title, int seconds, boolean heavenly) { this.id = id; this.title = title; this.seconds = seconds; this.heavenly = heavenly; }
        @Override public String getId() { return id; }
        @Override public int getCooldownTicks() { return 90 * 20; }
        @Override public int getKarmaCost() { return 35; }
        @Override public boolean use(ServerPlayer p) {
            float m = com.xenowarriors.skill.FormMastery.get(p, id);
            int ticks = Math.round(seconds * 20 * com.xenowarriors.skill.FormMastery.durationMult(m));
            FORM.put(p.getUUID(), now(p) + ticks);
            FORM_OF.put(p.getUUID(), this);
            effect(p, MobEffects.DAMAGE_BOOST, ticks, heavenly ? 2 : 1);
            effect(p, MobEffects.MOVEMENT_SPEED, ticks, 0);
            effect(p, MobEffects.DAMAGE_RESISTANCE, ticks, heavenly ? 1 : 0);
            if (heavenly) effect(p, MobEffects.REGENERATION, ticks, 0);
            ServerLevel l = p.serverLevel();
            l.sendParticles(heavenly ? HEAVEN : RED, p.getX(), p.getY() + 1, p.getZ(), 80, 0.8, 1.2, 0.8, 0);
            l.sendParticles(heavenly ? ParticleTypes.END_ROD : ParticleTypes.SOUL_FIRE_FLAME, p.getX(), p.getY() + 1, p.getZ(), 40, 0.6, 1, 0.6, 0.05);
            sound(p, heavenly ? SoundEvents.BEACON_ACTIVATE : SoundEvents.WITHER_AMBIENT, 1F, heavenly ? 0.6F : 1.3F);
            say(p, title + "!", heavenly ? ChatFormatting.GOLD : ChatFormatting.DARK_RED);
            sync(p);
            return true;
        }
    }

    public static final Form DEMON_FORM = new Form("demon_form", "Demon Form", 20, false), HEAVENLY_DEMON = new Form("heavenly_demon", "Heavenly Demon", 25, true);

    /** This character's transform-key form, or null. */
    public static Form formOf(CharacterType t) {
        return switch (t) {
            case ZACK, KAITLYN, AVA, PERSEPHONE -> DEMON_FORM;
            case SENJU -> HEAVENLY_DEMON;
            default -> null;
        };
    }

    public static boolean formActive(ServerPlayer p) { return on(FORM, p); }

    /** The mastery key of the form they're in, or null. */
    public static String activeFormKey(ServerPlayer p) {
        if (!on(FORM, p)) return null;
        Form f = FORM_OF.get(p.getUUID());
        return f == null ? null : f.id;
    }

    // ------------------------------------------------------------------ events

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent e) {
        LivingEntity v = e.getEntity();
        if (v.level().isClientSide) return;
        if (on(MARKED, v)) e.setAmount(e.getAmount() * 1.2F);                                     // Kyle Phone: marked
        if (e.getSource().getEntity() instanceof ServerPlayer p && e.getSource().getDirectEntity() == p) {
            if (on(CAGE, p) && p.getMainHandItem().isEmpty()) e.setAmount(e.getAmount() + 5F);       // Cage Fighter: bare hands
            if (on(UNLEASHED, p) && p.getMainHandItem().isEmpty()) {                             // Demon Unleashed: her punches send a shockwave
                for (LivingEntity o : around(p, v.position(), 2.5)) if (o != v) { o.invulnerableTime = 0; o.hurt(p.damageSources().playerAttack(p), e.getAmount() * 0.4F); }
                p.serverLevel().sendParticles(ParticleTypes.EXPLOSION, v.getX(), v.getY() + 1, v.getZ(), 1, 0, 0, 0, 0);
            }
            if (com.xenowarriors.skill.FormMastery.mastered(com.xenowarriors.skill.FormMastery.get(p, activeFormKey(p) == null ? "-" : activeFormKey(p))))
                e.setAmount(e.getAmount() * 1.2F);                                                // a mastered form hits harder
        }
        if (e.getSource().getEntity() instanceof LivingEntity a && on(WILL, a)) e.setAmount(e.getAmount() * 0.5F);   // The Weapon of Will: no will to fight
        if (v instanceof ServerPlayer p && on(CAGE, p)) v.setDeltaMovement(v.getDeltaMovement().multiply(0.3, 1, 0.3));
    }

    private static final Map<UUID, Integer> SENT = new HashMap<>();

    /** Bits the clients need: 1 = in a form (Ava / Persephone's demon look), 2 = Hidden Will (no lock-on). */
    static int flags(ServerPlayer p) { return (on(FORM, p) ? 1 : 0) | (hiddenWill(p) || on(VANISH, p) ? 2 : 0); }

    static void sync(ServerPlayer p) {
        int f = flags(p);
        Integer was = SENT.put(p.getUUID(), f);
        if (was == null ? f != 0 : was != f)
            com.xenowarriors.network.ModNetwork.sendToTrackingAndSelf(new com.xenowarriors.network.NcStatePacket(p.getId(), f), p);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p instanceof net.minecraftforge.common.util.FakePlayer) return;
        if (p.tickCount % 5 == 0) sync(p);
        if (p.tickCount % 20 == 3) {
            if (on(SURGE, p)) {                                                                      // Modification Surge: lit
                if (type(p) != CharacterType.KYLE || (!p.isCreative() && !com.xenowarriors.karma.Karma.trySpend(p, 1F))) {
                    SURGE.remove(p.getUUID());
                    p.displayClientMessage(Component.literal("Modification Surge - out").withStyle(ChatFormatting.GRAY), true);
                } else {
                    effect(p, MobEffects.MOVEMENT_SPEED, 30, 1);
                    effect(p, MobEffects.JUMP, 30, 1);
                    effect(p, MobEffects.DAMAGE_RESISTANCE, 30, 0);
                    p.serverLevel().sendParticles(C2, p.getX(), p.getY() + 0.2, p.getZ(), 4, 0.3, 0.1, 0.3, 0);
                }
            }
            String f = activeFormKey(p);
            if (f != null) com.xenowarriors.skill.FormMastery.add(p, f, com.xenowarriors.skill.FormMastery.PER_SECOND);
        }
        if (on(FORM, p) && p.tickCount % 4 == 0) {
            Form f = FORM_OF.get(p.getUUID());
            p.serverLevel().sendParticles(f == HEAVENLY_DEMON ? HEAVEN : RED, p.getX(), p.getY() + 1, p.getZ(), 2, 0.4, 0.7, 0.4, 0);
        }
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking e) {
        if (e.getTarget() instanceof ServerPlayer p && e.getEntity() instanceof ServerPlayer w) {
            int f = flags(p);
            if (f != 0) com.xenowarriors.network.ModNetwork.sendToPlayer(new com.xenowarriors.network.NcStatePacket(p.getId(), f), w);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) { SENT.remove(e.getEntity().getUUID()); }

    // ================================================================== FOX GIRL (Kitsune: fox martial arts; Season 5)

    private static final ParticleOptions FOX = new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(0.95F, 0.3F, 0.6F), 1.1F);

    /** Kitsune Attack: a string of fox martial arts strikes - three hits, the last one sends them flying. */
    public static class KitsuneAttack implements Skill {
        @Override public String getId() { return "kitsune_attack"; }
        @Override public int getCooldownTicks() { return 5 * 20; }
        @Override public int getKarmaCost() { return 12; }
        @Override public boolean use(ServerPlayer p) {
            LivingEntity t = front(p, 4.5);
            if (t == null) return false;
            for (int k = 0; k < 3; k++) {
                final int n = k;
                ServerScheduler.later(n * 5, () -> {
                    if (!p.isAlive() || !t.isAlive()) return;
                    p.swing(n % 2 == 0 ? net.minecraft.world.InteractionHand.MAIN_HAND : net.minecraft.world.InteractionHand.OFF_HAND, true);
                    hit(p, t, n == 2 ? 9F : 5F, false);
                    if (n == 2) push(t, p.position(), 1.6, 0.4);
                    p.serverLevel().sendParticles(FOX, t.getX(), t.getY() + 1, t.getZ(), 10, 0.3, 0.4, 0.3, 0);
                    sound(p, SoundEvents.PLAYER_ATTACK_STRONG, 1F, 1.1F + n * 0.1F);
                });
            }
            return true;
        }
    }

    /** Kitsune Throw: she grabs them and hurls them far away (the way she threw Mikiko all the way to the disco). */
    public static class KitsuneThrow implements Skill {
        @Override public String getId() { return "kitsune_throw"; }
        @Override public int getCooldownTicks() { return 12 * 20; }
        @Override public int getKarmaCost() { return 20; }
        @Override public boolean use(ServerPlayer p) {
            LivingEntity t = front(p, 4);
            if (t == null) return false;
            p.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
            hit(p, t, 6F, false);
            Vec3 f = p.getLookAngle().multiply(1, 0, 1).normalize();
            t.setDeltaMovement(f.x * 3.2, 1.1, f.z * 3.2);
            t.hurtMarked = true;
            p.serverLevel().sendParticles(ParticleTypes.CLOUD, t.getX(), t.getY() + 0.5, t.getZ(), 12, 0.3, 0.2, 0.3, 0.05);
            say(p, "Out you go.", ChatFormatting.LIGHT_PURPLE);
            sound(p, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.2F, 0.7F);
            return true;
        }
    }

    /** Boss's Orders: the boss of Kitsune's Farm gives an order - she and everyone with her (players and bots close by) hit harder for a while. */
    public static class BossOrders implements Skill {
        @Override public String getId() { return "boss_orders"; }
        @Override public int getCooldownTicks() { return 30 * 20; }
        @Override public int getKarmaCost() { return 25; }
        @Override public boolean use(ServerPlayer p) {
            java.util.List<LivingEntity> team = new java.util.ArrayList<>();
            team.add(p);
            for (ServerPlayer o : p.serverLevel().players()) if (o != p && o.distanceToSqr(p) < 16 * 16) team.add(o);
            for (var b : com.xenowarriors.bot.PartyBots.bots(p)) if (b.distanceToSqr(p) < 16 * 16) team.add(b);
            for (LivingEntity e : team) {
                effect(e, MobEffects.DAMAGE_BOOST, 20 * 15, 1);
                effect(e, MobEffects.MOVEMENT_SPEED, 20 * 15, 0);
                p.serverLevel().sendParticles(FOX, e.getX(), e.getY() + 1.2, e.getZ(), 14, 0.3, 0.5, 0.3, 0);
            }
            say(p, "Get to work - all of you!", ChatFormatting.LIGHT_PURPLE);
            sound(p, SoundEvents.BELL_BLOCK, 1F, 1.4F);
            return true;
        }
    }

    /** Fox Step: a fast dash (8 blocks) that leaves an afterimage behind. */
    public static class FoxStep implements Skill {
        @Override public String getId() { return "fox_step"; }
        @Override public int getCooldownTicks() { return 5 * 20; }
        @Override public int getKarmaCost() { return 8; }
        @Override public boolean use(ServerPlayer p) {
            p.serverLevel().sendParticles(FOX, p.getX(), p.getY() + 1, p.getZ(), 30, 0.25, 0.6, 0.25, 0);   // (the afterimage where she stood)
            p.serverLevel().sendParticles(ParticleTypes.POOF, p.getX(), p.getY() + 1, p.getZ(), 6, 0.2, 0.4, 0.2, 0.01);
            blink(p, lookSpot(p, 8), FOX);
            return true;
        }
    }

    /** Fox Palm (middle click): a quick palm strike that knocks them back. */
    public static class FoxPalm implements Skill {
        @Override public String getId() { return "fox_palm"; }
        @Override public int getCooldownTicks() { return 3 * 20; }
        @Override public int getKarmaCost() { return 5; }
        @Override public boolean use(ServerPlayer p) {
            LivingEntity t = front(p, 4);
            if (t == null) return false;
            p.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
            hit(p, t, 6F, false);
            push(t, p.position(), 1.2, 0.3);
            p.serverLevel().sendParticles(FOX, t.getX(), t.getY() + 1, t.getZ(), 8, 0.25, 0.3, 0.25, 0);
            sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 1F, 1.5F);
            return true;
        }
    }

    /** Ultimate - Kitsune Attack Tier 3: the highest tier of fox martial arts - a flurry on everyone around her, then a finishing blow. */
    public static class KitsuneTier3 implements Skill {
        @Override public String getId() { return "kitsune_tier3"; }
        @Override public int getCooldownTicks() { return 75 * 20; }
        @Override public int getKarmaCost() { return 60; }
        @Override public boolean use(ServerPlayer p) {
            List<LivingEntity> l = around(p, p.position(), 8);
            if (l.isEmpty()) { p.displayClientMessage(Component.translatable("message.xenowarriors.no_target"), true); return false; }
            say(p, "Kitsune Attack - Tier 3.", ChatFormatting.LIGHT_PURPLE);
            effect(p, MobEffects.DAMAGE_RESISTANCE, 50, 2);
            for (int k = 0; k < 6; k++) {
                final int n = k;
                ServerScheduler.later(n * 6, () -> {
                    if (!p.isAlive()) return;
                    p.swing(n % 2 == 0 ? net.minecraft.world.InteractionHand.MAIN_HAND : net.minecraft.world.InteractionHand.OFF_HAND, true);
                    for (LivingEntity e : l) {
                        if (!e.isAlive()) continue;
                        hit(p, e, n == 5 ? 18F : 5F, false);
                        if (n == 5) push(e, p.position(), 2.4, 0.6);
                        p.serverLevel().sendParticles(FOX, e.getX(), e.getY() + 1, e.getZ(), n == 5 ? 30 : 8, 0.4, 0.6, 0.4, 0);
                    }
                    sound(p, n == 5 ? SoundEvents.GENERIC_EXPLODE : SoundEvents.PLAYER_ATTACK_STRONG, n == 5 ? 0.8F : 1F, 1.2F);
                });
            }
            return true;
        }
    }
}

package com.xenowarriors.foes;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.bot.PartyBotEntity;
import com.xenowarriors.corrupted.CSoulBossEntity;
import com.xenowarriors.corrupted.CorruptedSoulEntity;
import com.xenowarriors.corrupted.CorruptedVillagerEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The type triangle (the user's chart): Vessel beats Spell Caster beats C-soul beats Vessel.
 * - Spell Casters (Elementalists) take more from physical hits - a Vessel's claws, anyone's melee or weapon.
 * - C-souls take more from elements and magic - a Spell Caster's casts, anyone's element.
 * - Vessels shrug off elements and magic; and C-souls are hard for a Vessel to land a good hit on: a Vessel's hits (a
 *   demon-form player's, and a demon bot's of yours, too) often miss them, and the ones that land do less. C-souls hit
 *   Vessels harder.
 * The three fight each other when they meet: Vessels hunt Spell Casters, Spell Casters hunt C-souls, and C-souls hunt
 * Vessels - though a Cynic Dancer passes for a C-soul until it evolves; only the strong, smart ones see through it.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TypeTriangle {
    private TypeTriangle() {}

    public enum Kind { VESSEL, CASTER, CSOUL }

    public static final float STRONG = 1.5F, WEAK = 0.6F, CSOUL_ON_VESSEL = 1.3F, VESSEL_ON_CSOUL = 0.8F, MISS = 0.3F;
    /** The share of ordinary C-souls smart enough to see through a Cynic Dancer (the Ancient Swordstress and the boss always do). */
    public static final float SMART = 0.25F;
    private static final String SMART_KEY = "xw_smart_soul";

    /** (Tests) a fixed miss roll (null = random); misses, and boosted / weakened hits. */
    public static Boolean testMiss;
    public static int misses, strong, weak;

    /** What it is, in the triangle (or null). */
    public static Kind of(Entity e) {
        if (e instanceof VesselEntity) return Kind.VESSEL;
        if (e instanceof ElementalistEntity) return Kind.CASTER;
        if (cSoul(e)) return Kind.CSOUL;
        return null;
    }

    public static boolean cSoul(Entity e) {
        return e instanceof CorruptedSoulEntity || e instanceof CorruptedVillagerEntity || e instanceof CSoulBossEntity || e instanceof SwordstressEntity;
    }

    /** A demon in its demon form (a player of yours, or a bot of one of your own characters): it fights like a Vessel. */
    public static boolean demonForm(Entity e) {
        com.xenowarriors.you.YouSlot s = null;
        if (e instanceof net.minecraft.server.level.ServerPlayer p) s = com.xenowarriors.you.You.slot(p);
        else if (e instanceof PartyBotEntity b && b.ownCharacter()) s = b.you();
        return s != null && s.demon() && !s.humanForm;
    }

    private static Entity attacker(DamageSource src) {
        Entity a = src.getEntity();
        if (a instanceof Projectile pr && pr.getOwner() != null) a = pr.getOwner();
        return a;
    }

    /** An element's hit, or magic. */
    public static boolean elemental(DamageSource src) {
        return com.xenowarriors.ka.Elements.hitting > 0 || src.is(DamageTypeTags.WITCH_RESISTANT_TO);
    }

    /** A plain physical hit: up close, by hand or weapon (not an element, not magic, not a projectile). */
    public static boolean physical(DamageSource src) {
        return !elemental(src) && src.getEntity() != null && src.getDirectEntity() == src.getEntity() && !src.is(DamageTypeTags.IS_PROJECTILE)
                && !src.is(DamageTypeTags.IS_EXPLOSION) && !src.is(DamageTypeTags.IS_FIRE);
    }

    private static boolean vesselLike(Entity a) { return a instanceof VesselEntity || demonForm(a); }

    /** C-souls dodge a Vessel's hits. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onAttack(LivingAttackEvent e) {
        LivingEntity v = e.getEntity();
        if (v.level().isClientSide || !cSoul(v)) return;
        Entity a = attacker(e.getSource());
        if (a == null || !vesselLike(a) || elemental(e.getSource())) return;
        boolean miss = testMiss != null ? testMiss : v.getRandom().nextFloat() < MISS;
        if (!miss) return;
        misses++;
        e.setCanceled(true);
        if (v.level() instanceof ServerLevel l) l.sendParticles(ParticleTypes.LARGE_SMOKE, v.getX(), v.getY() + 1, v.getZ(), 6, 0.3, 0.4, 0.3, 0.02);
        if (a instanceof Player p) p.displayClientMessage(Component.literal("Missed - C-souls are hard for a demon to hit!").withStyle(ChatFormatting.DARK_GRAY), true);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onHurt(LivingHurtEvent e) {
        LivingEntity v = e.getEntity();
        if (v.level().isClientSide) return;
        Kind k = of(v);
        if (k == null) return;
        DamageSource src = e.getSource();
        Entity a = attacker(src);
        float m = 1F;
        switch (k) {
            case CASTER -> { if (physical(src)) m = STRONG; }
            case CSOUL -> {
                if (elemental(src)) m = STRONG;
                else if (a != null && vesselLike(a)) m = VESSEL_ON_CSOUL;
            }
            case VESSEL -> {
                if (elemental(src)) m = WEAK;
                else if (a != null && cSoul(a)) m = CSOUL_ON_VESSEL;
            }
        }
        if (m == 1F) return;
        if (m > 1F) strong++; else weak++;
        e.setAmount(e.getAmount() * m);
    }

    // ------------------------------------------------------------------ who hunts whom

    /** C-souls hunt Vessels (Cynic Dancers only once they've evolved - unless they're smart enough to see through them). */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent e) {
        if (e.getLevel().isClientSide() || !(e.getEntity() instanceof Mob m) || !cSoul(m) || m instanceof SwordstressEntity) return;
        var t = m.getPersistentData();
        if (!t.contains(SMART_KEY)) t.putBoolean(SMART_KEY, m instanceof CSoulBossEntity || m.getRandom().nextFloat() < SMART);
        boolean smart = t.getBoolean(SMART_KEY);
        m.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(m, VesselEntity.class, 10, true, false, v -> smart || !((VesselEntity) v).disguised()));
    }

    /** (Tests) */
    public static boolean smart(Mob m) { return m instanceof SwordstressEntity || m.getPersistentData().getBoolean(SMART_KEY); }
}

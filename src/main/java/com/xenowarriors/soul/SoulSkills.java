package com.xenowarriors.soul;

import com.mojang.authlib.GameProfile;
import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.ModCapabilities;
import com.xenowarriors.skill.Skill;
import com.xenowarriors.skill.SkillManager;
import com.xenowarriors.skill.SkillPool;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A strong Soul Splitter: once its level reaches {@link #SKILLS_AT}, the split-off soul fights with its owner's own
 * skills (slots 1-4 of their loadout - never the ultimate, never a KA, never Soul Splitter itself), cast through a
 * stand-in player that copies the owner (like the party bots do). The higher the level, the more often it casts.
 * <p>The level comes from {@link #abilityLevel} - a hook the per-ability level system can replace
 * ({@link #ABILITY_LEVEL}); until then it is the Soul Splitter's own level (see SoulSplit.level).
 */
public final class SoulSkills {
    public static final int SKILLS_AT = 20;
    public static final String SKILL_ID = "soul_splitter";

    /** The level of one of a player's abilities, by skill id. Replace this to wire in per-ability levels. */
    public static java.util.function.ToIntBiFunction<ServerPlayer, String> ABILITY_LEVEL = (p, id) -> SKILL_ID.equals(id) ? SoulSplit.level(p) : 0;

    public static int abilityLevel(ServerPlayer p, String skillId) {
        try { return ABILITY_LEVEL.applyAsInt(p, skillId); } catch (Throwable t) { return SoulSplit.level(p); }
    }

    private static final Map<UUID, FakePlayer> FAKES = new HashMap<>();             // soul -> stand-in
    private static final Map<UUID, UUID> FAKE_TO_OWNER = new HashMap<>();            // stand-in -> the soul's owner
    private static final Map<UUID, long[]> READY = new HashMap<>();                  // soul -> per-slot ready time
    /** Casts so far (tests). */
    public static int casts;
    public static String lastSkill;

    private SoulSkills() {}

    /** The owner behind a soul's stand-in (null if this isn't one). */
    public static UUID ownerOfStandIn(UUID id) { return id == null ? null : FAKE_TO_OWNER.get(id); }

    public static void forget(SoulEntity soul) {
        FakePlayer f = FAKES.remove(soul.getUUID());
        if (f != null) FAKE_TO_OWNER.remove(f.getUUID());
        READY.remove(soul.getUUID());
    }

    /** Ticks between casts at this level (6 s at level 20, down to 2.5 s). */
    public static int interval(int level) {
        return Math.max(50, 120 - (level - SKILLS_AT) * 2);
    }

    private static boolean allowed(Skill s) {
        if (s == null) return false;
        String id = s.getId();
        return !id.equals(SKILL_ID) && !id.startsWith("ka_") && !id.contains("ultimate");
    }

    private static FakePlayer standIn(SoulEntity soul, ServerPlayer o) {
        ServerLevel s = (ServerLevel) soul.level();
        FakePlayer f = FAKES.get(soul.getUUID());
        if (f == null || f.level() != s) {
            String name = "xwsoul_" + o.getName().getString();
            f = FakePlayerFactory.get(s, new GameProfile(UUID.nameUUIDFromBytes(("xwsoul" + o.getUUID()).getBytes()), name.substring(0, Math.min(16, name.length()))));
            FAKES.put(soul.getUUID(), f);
            FAKE_TO_OWNER.put(f.getUUID(), o.getUUID());
        }
        f.getCapability(ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> o.getCapability(ModCapabilities.PLAYER_CHARACTER).ifPresent(od -> {
            d.setType(soul.character());
            for (int i = 0; i < 3; i++) d.statLevel[i] = od.statLevel[i];
            d.loadouts = od.loadouts.copy();
            d.youSlot = od.youSlot;
            for (int i = 0; i < d.youSlots.length; i++) d.youSlots[i] = od.youSlots[i].copy();
            d.bladeLevel = od.bladeLevel;
            d.setKarma(100F);
        }));
        return f;
    }

    /** Called from the soul's tick while it has a target: maybe casts one of its owner's skills. */
    public static boolean tick(SoulEntity soul, ServerPlayer o, LivingEntity target) {
        if (o == null || target == null || !target.isAlive()) return false;
        int lvl = abilityLevel(o, SKILL_ID);
        if (lvl < SKILLS_AT || soul.distanceTo(target) > 18) return false;
        long now = soul.level().getGameTime();
        long[] ready = READY.computeIfAbsent(soul.getUUID(), k -> new long[]{now + 40, 0, 0, 0, 0});
        if (now < ready[0]) return false;
        int[] loadout = o.getCapability(ModCapabilities.PLAYER_CHARACTER).map(d -> d.loadout(soul.character())).orElse(SkillPool.defaultLoadout());
        int slot = soul.getRandom().nextInt(Math.min(SkillManager.ULTIMATE_SLOT, 4));
        if (now < ready[1 + slot]) { ready[0] = now + 10; return false; }
        FakePlayer f = standIn(soul, o);
        Skill skill = SkillPool.inSlot(f, loadout, slot);
        if (!allowed(skill)) { ready[1 + slot] = now + 200; ready[0] = now + 10; return false; }
        Vec3 eye = soul.getEyePosition(), to = target.getBoundingBox().getCenter(), d = to.subtract(eye);
        float yaw = (float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90F), pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
        f.moveTo(soul.getX(), soul.getY(), soul.getZ(), yaw, pitch);
        f.setYHeadRot(yaw);
        f.setOnGround(soul.onGround());
        f.removeAllEffects();
        f.setDeltaMovement(Vec3.ZERO);
        f.setHealth(f.getMaxHealth());
        boolean used;
        try { used = com.xenowarriors.combat.SkillContext.call(f, () -> skill.use(f)); } catch (Throwable t) { used = false; }
        f.removeAllEffects();                                                       // (self-buffs would never wear off on it)
        ready[0] = now + interval(lvl);
        ready[1 + slot] = now + Math.max(60, skill.getCooldownTicks());
        if (used) {
            casts++;
            lastSkill = skill.getId();
            soul.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            com.xenowarriors.anim.Poses.play(soul, com.xenowarriors.anim.Poses.KA_CAST, 12);
            ((ServerLevel) soul.level()).sendParticles(net.minecraft.core.particles.ParticleTypes.SOUL_FIRE_FLAME, soul.getX(), soul.getY() + 1.2, soul.getZ(), 10, 0.3, 0.4, 0.3, 0.03);
            XenoWarriors.LOGGER.info("XW-SOULSKILL soul of {} (Soul Splitter lv {}) cast {}", o.getName().getString(), lvl, skill.getId());
        }
        return used;
    }
}

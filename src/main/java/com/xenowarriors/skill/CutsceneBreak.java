package com.xenowarriors.skill;

import com.xenowarriors.entity.BlazingWindEntity;
import com.xenowarriors.entity.GravityBarrageEntity;
import com.xenowarriors.entity.TeamUltEntity;
import com.xenowarriors.stats.Stat;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;

/**
 * Hit during a cutscene (an ultimate's - Gravity Barrage, Blazing Wind, the team ultimate - or a Xeno Blast's): enough
 * hits end the cutscene so the player gets their camera and controls back and can defend themselves.
 * <ul>
 *   <li>Gravity Barrage and the team ultimate: the rest of the attack lands at once (all remaining hits in one go).</li>
 *   <li>Blazing Wind: only the cinematic part is cut - the tornado carries on by itself as normal.</li>
 *   <li>Xeno Blast: the caster is free; the blast still charges and fires at its normal moment. Beam clashes are never cut.</li>
 * </ul>
 * <b>Guard</b>: the enemy hits land (you take the damage, but no knockback while the cutscene holds) and only
 * {@link #HITS} hits that "count" break it. Each hit counts with a chance that falls as your Defense rises
 * ({@link #countChance}: 100% at Defense 0 down to a 20% floor at Defense 3,000+). A big hit (at least 20% of your max
 * health after all reductions) or 35% of your max health taken over the cutscene breaks it at once, whatever your Defense.
 * With DragonMineZ the damage and max health are its scaled ones. Party bots stay untouchable during a team ultimate.
 * Nothing is refunded. KAs have no cutscene. Only a hit from an enemy counts (not your own attacks, an ally's, or the world).
 */
public final class CutsceneBreak {
    private CutsceneBreak() {}

    /** Counted hits that break a cutscene. */
    public static final int HITS = 3;
    /** One hit this big (share of max health) breaks it at once. */
    public static final float BIG_HIT = 0.20F;
    /** This much damage taken in total over the cutscene (share of max health) breaks it at once. */
    public static final float HEAVY_TOTAL = 0.35F;
    /** Lowest chance a hit counts (from {@link #FLOOR_DEFENSE} Defense on): never impossible. */
    public static final float FLOOR = 0.20F;
    public static final int FLOOR_DEFENSE = 3000;
    private static final String GUARD = "xenowarriors_cut_guard";

    /** Last interrupt: game time and what was cut (tests). */
    public static long lastAt = -1;
    public static String lastWhat = "";
    /** Last guard decision (tests): e.g. "resisted 0/3 chance 0.57" / "counted 2/3" / "broken: big hit". */
    public static String lastGuard = "";
    /** Tests: a fixed roll in [0,1) (a hit counts if roll < chance); null = random. */
    public static Float forceRoll = null;

    /**
     * The chance a hit counts toward breaking the guard at a Defense level:
     * {@code 1 - 0.8 * min(1, ln(1 + D/10) / ln(301))} - 100% at 0, ~90% at 10, ~75% at 50, ~57% at 200, ~35% at 1,000,
     * 20% (the floor) from 3,000 to the 30,000 max.
     */
    public static float countChance(int defense) {
        double f = Math.min(1.0, Math.log(1 + Math.max(0, defense) / 10.0) / Math.log(1 + FLOOR_DEFENSE / 10.0));
        return (float) Math.max(FLOOR, 1.0 - (1.0 - FLOOR) * f);
    }

    /** The Defense level that guards this entity's cutscene (a player's own; a bot's own). */
    public static int defense(LivingEntity e) {
        if (e instanceof ServerPlayer sp) return com.xenowarriors.stats.Stats.level(sp, Stat.DEFENSE);
        if (e instanceof com.xenowarriors.bot.PartyBotEntity b) return com.xenowarriors.bot.PartyBots.statLevel(b, Stat.DEFENSE);
        return 0;
    }

    /** A hit from an enemy: someone (or their arrow) who isn't you or on your side. */
    public static boolean byEnemy(ServerPlayer p, DamageSource src) {
        Entity a = src.getEntity();
        if (!(a instanceof LivingEntity) || a == p) return false;
        if (src.getDirectEntity() instanceof Projectile pr && pr.getOwner() == p) return false;
        return !com.xenowarriors.combat.Allies.isAlly(p, a);
    }

    /** Whether {@code p} is in a cutscene a hit could cut (Kitsune's dash and other untouchable moments are not). */
    public static boolean breakable(ServerPlayer p) {
        var level = p.serverLevel();
        var box = p.getBoundingBox().inflate(96);
        if (!level.getEntitiesOfClass(GravityBarrageEntity.class, box, g -> p.getUUID().equals(g.ownerUuid()) && !g.isRemoved() && g.tickCount <= GravityBarrageEntity.DETONATE).isEmpty()) return true;
        if (!level.getEntitiesOfClass(BlazingWindEntity.class, box, w -> p.getUUID().equals(w.ownerUuid()) && !w.cutsceneCut() && w.tickCount < BlazingWindEntity.CUTSCENE_END).isEmpty()) return true;
        if (!level.getEntitiesOfClass(TeamUltEntity.class, box, t -> (t.rhalixId() == p.getId() || t.mikikoId() == p.getId()) && !t.isRemoved() && t.tickCount <= t.finale()).isEmpty()) return true;
        return XenoBlast.interruptible(p);
    }

    /** True while the guard is holding a cutscene for {@code p} (its hits have no knockback). */
    public static boolean guarding(LivingEntity e) {
        return e instanceof ServerPlayer && e.getPersistentData().getLong(SkillEvents.ULT_UNTIL) >= e.level().getGameTime() && e.getPersistentData().contains(GUARD);
    }

    /**
     * An enemy's hit has landed during a breakable cutscene, for {@code amount} after every reduction: counts it against
     * the guard and cuts the cutscene when the guard breaks. True if it was cut.
     */
    public static boolean onHit(ServerPlayer p, DamageSource src, float amount) {
        long until = p.getPersistentData().getLong(SkillEvents.ULT_UNTIL);
        CompoundTag g = p.getPersistentData().getCompound(GUARD);
        if (g.getLong("until") != until) { g = new CompoundTag(); g.putLong("until", until); }   // (a new cutscene: a fresh guard)
        int hits = g.getInt("hits");
        float total = g.getFloat("dmg") + Math.max(0F, amount);
        float max = Math.max(1F, p.getMaxHealth());
        int def = defense(p);
        float chance = countChance(def);
        String reason = null;
        if (amount >= BIG_HIT * max) reason = "big hit " + pct(amount / max);
        else if (total >= HEAVY_TOTAL * max) reason = "heavy damage " + pct(total / max);
        boolean counted = false;
        if (reason == null) {
            float roll = forceRoll != null ? forceRoll : p.getRandom().nextFloat();
            counted = roll < chance;
            if (counted && ++hits >= HITS) reason = "guard broken " + hits + "/" + HITS;
        }
        g.putInt("hits", hits);
        g.putFloat("dmg", total);
        p.getPersistentData().put(GUARD, g);
        String info = " (Defense " + def + ", count chance " + Math.round(chance * 100) + "%, hit " + pct(amount / max) + ", total " + pct(total / max) + " of max " + Math.round(max) + ")";
        if (reason != null) {
            lastGuard = "broken: " + reason + info;
            boolean cut = interrupt(p, src);
            if (cut) send(p, 2, hits, chance);
            return cut;
        }
        lastGuard = (counted ? "counted " : "resisted ") + hits + "/" + HITS + info;
        var level = p.serverLevel();
        var c = p.getBoundingBox().getCenter();
        if (counted) {
            level.sendParticles(ParticleTypes.CRIT, c.x, c.y, c.z, 10, 0.4, 0.6, 0.4, 0.2);
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.35F, 1.6F + 0.15F * hits);
        } else {
            level.sendParticles(ParticleTypes.ENCHANTED_HIT, c.x, c.y, c.z, 16, 0.5, 0.7, 0.5, 0.25);
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1F, 1.1F);
        }
        send(p, counted ? 1 : 0, hits, chance);
        com.xenowarriors.XenoWarriors.LOGGER.debug("Cutscene guard for {}: {}", p.getGameProfile().getName(), lastGuard);
        return false;
    }

    private static void send(ServerPlayer p, int state, int hits, float chance) {
        if (p instanceof net.minecraftforge.common.util.FakePlayer) return;          // (a bot's stand-in skill player has no client)
        com.xenowarriors.network.ModNetwork.sendToPlayer(new com.xenowarriors.network.CutsceneGuardPacket(state, hits, HITS, Math.round(chance * 100)), p);
    }

    private static String pct(float f) { return Math.round(f * 1000) / 10.0 + "%"; }

    /** Ends whatever cutscene {@code p} is in; true if there was one. */
    public static boolean interrupt(ServerPlayer p, DamageSource src) {
        String what = null;
        var level = p.serverLevel();
        var box = p.getBoundingBox().inflate(96);
        for (GravityBarrageEntity g : level.getEntitiesOfClass(GravityBarrageEntity.class, box, g -> p.getUUID().equals(g.ownerUuid())))
            if (g.interrupt(p)) what = "gravity_barrage";
        for (BlazingWindEntity w : level.getEntitiesOfClass(BlazingWindEntity.class, box, w -> p.getUUID().equals(w.ownerUuid())))
            if (w.cutCutscene(p)) what = "blazing_wind";
        for (TeamUltEntity t : level.getEntitiesOfClass(TeamUltEntity.class, box, t -> t.rhalixId() == p.getId() || t.mikikoId() == p.getId()))
            if (t.interrupt()) what = "team_ultimate";
        if (XenoBlast.interrupt(p)) what = "xeno_blast";
        p.getPersistentData().remove(GUARD);
        if (what == null) return false;
        p.getPersistentData().remove(SkillEvents.ULT_UNTIL);
        lastAt = level.getGameTime();
        lastWhat = what;
        Entity a = src.getEntity();
        com.xenowarriors.XenoWarriors.LOGGER.info("Cutscene {} interrupted for {} by a hit from {} - {}", what, p.getGameProfile().getName(), a == null ? "?" : a.getName().getString(), lastGuard);
        p.displayClientMessage(Component.literal("Interrupted!").withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
        return true;
    }
}

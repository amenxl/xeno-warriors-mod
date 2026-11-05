package com.xenowarriors.skill;

import com.xenowarriors.entity.BlazingWindEntity;
import com.xenowarriors.entity.GravityBarrageEntity;
import com.xenowarriors.entity.TeamUltEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;

/**
 * Hit during a cutscene (an ultimate's - Gravity Barrage, Blazing Wind, the team ultimate - or a Xeno Blast's): the
 * cutscene ends at once so the player gets their camera and controls back and can defend themselves.
 * <ul>
 *   <li>Gravity Barrage and the team ultimate: the rest of the attack lands at once (all remaining hits in one go).</li>
 *   <li>Blazing Wind: only the cinematic part is cut - the tornado carries on by itself as normal.</li>
 *   <li>Xeno Blast: the caster is free; the blast still charges and fires at its normal moment. Beam clashes are never cut.</li>
 * </ul>
 * Nothing is refunded. KAs have no cutscene. Only a hit from an enemy counts (not your own attacks, an ally's, or the world).
 */
public final class CutsceneBreak {
    private CutsceneBreak() {}

    /** Last interrupt: game time and what was cut (tests). */
    public static long lastAt = -1;
    public static String lastWhat = "";

    /** A hit from an enemy: someone (or their arrow) who isn't you or on your side. */
    public static boolean byEnemy(ServerPlayer p, DamageSource src) {
        Entity a = src.getEntity();
        if (!(a instanceof LivingEntity) || a == p) return false;
        if (src.getDirectEntity() instanceof Projectile pr && pr.getOwner() == p) return false;
        return !com.xenowarriors.combat.Allies.isAlly(p, a);
    }

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
        if (what == null) return false;
        p.getPersistentData().remove(SkillEvents.ULT_UNTIL);
        lastAt = level.getGameTime();
        lastWhat = what;
        Entity a = src.getEntity();
        com.xenowarriors.XenoWarriors.LOGGER.info("Cutscene {} interrupted for {} by a hit from {}", what, p.getGameProfile().getName(), a == null ? "?" : a.getName().getString());
        p.displayClientMessage(Component.literal("Interrupted!").withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
        return true;
    }
}

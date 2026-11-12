package com.xenowarriors.skill;

import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * Changing characters ends the timed skill states the old character had going (stances, fields, takeovers, a
 * projection, waiting blades, a wire bind), so none of them carry over onto whoever they switch to. Vanilla potion
 * effects a skill gave stay, like any potion.
 */
public final class SwitchCleanup {
    private SwitchCleanup() {}

    /** Test log: what the last switch ended. */
    public static String last = "";

    public static void onSwitch(ServerPlayer p) {
        UUID id = p.getUUID();
        StringBuilder ended = new StringBuilder();
        if (com.xenowarriors.skill.sp.SpState.boxing(p)) ended.append("boxing ");
        if (com.xenowarriors.skill.sp.SpState.persephone(p)) ended.append("persephone ");
        if (com.xenowarriors.skill.sp.SpState.knockout(p)) ended.append("knockout ");
        if (com.xenowarriors.skill.ffr.ZaneProjection.active(p)) ended.append("projection ");
        if (com.xenowarriors.skill.kaeru.HekubarrierSkill.active(p)) ended.append("hekubarrier ");
        if (com.xenowarriors.skill.UltCharge.cancel(p)) ended.append("ultimate-charge ");   // (an ultimate still charging never goes off as the new character)
        com.xenowarriors.skill.sp.SpState.clear(id);                         // Kyrie, Max, Sam, Lexy, Technica
        com.xenowarriors.skill.ffr.SrSkills.clear(id);                       // Logan, Kill
        com.xenowarriors.skill.ffr.ZaneProjection.clear(id);                 // Zane's KA
        com.xenowarriors.skill.kaeru.HekubarrierSkill.clear(id);             // Kaeru
        com.xenowarriors.skill.mikiko.HekubladeScatterSkill.clear(id);       // Mikiko / Kaeru: blades still in the air
        p.getPersistentData().remove(com.xenowarriors.skill.mikiko.ManifestGreatnessSkill.UNTIL);   // (its glow and Karma regen)
        p.getPersistentData().remove(com.xenowarriors.skill.mikiko.TrueEmpathySkill.READ_UNTIL);
        p.getPersistentData().remove(com.xenowarriors.skill.rhalix.KaReleaseSkill.FREE_UNTIL);
        p.getPersistentData().remove(com.xenowarriors.skill.ultima.AttackPredictionSkill.UNTIL);
        p.getPersistentData().remove(com.xenowarriors.skill.ultima.LivingWeaponSkill.UNTIL);
        if (com.xenowarriors.rhalix.Wires.bindOf(p) != null) { ended.append("wire-bind "); com.xenowarriors.rhalix.Wires.release(p, "switched character"); }
        last = ended.toString().trim();
    }
}

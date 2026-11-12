package com.xenowarriors.skill;

import com.xenowarriors.character.CharacterType;
import com.xenowarriors.character.ModCapabilities;
import com.xenowarriors.karma.Karma;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Transformations that used to be ultimates, now on the transform key (like Fox King / Demon's Remnants):
 * <ul>
 *   <li>Kill - Devil Unleashed (12 s): the devil inside takes over. His ultimate is now Hellclaw Rend.</li>
 *   <li>Lexy - Persephone (20 s): Persephone takes over, and Lexy is drained after. Her ultimate is now Maynaya Crash.</li>
 *   <li>You (Demon race) - Devil Unleashed with sneak + the transform key (the key alone still swaps human / demon
 *       form). The Demon ultimate is now Hellclaw Rend.</li>
 * </ul>
 * Same Karma cost and 90 s cooldown as when they were ultimates; once on, they can't be stopped early.
 */
public final class UltForms {
    private UltForms() {}

    private static final Skill DEVIL = new com.xenowarriors.skill.ffr.SrSkills.DevilUnleashed();
    private static final Skill PERSEPHONE = new com.xenowarriors.skill.sp.LexySkills.Persephone();
    /** player -> game time it can transform again */
    private static final Map<UUID, Long> READY = new HashMap<>();

    /** The transformation this player has on the transform key, or null. */
    public static Skill form(ServerPlayer p) {
        CharacterType t = ModCapabilities.getType(p);
        if (t == CharacterType.KILL) return DEVIL;
        if (t == CharacterType.LEXY) return PERSEPHONE;
        var s = com.xenowarriors.you.You.slot(p);
        if (s != null && s.race == com.xenowarriors.character.Race.DEMON && p.isShiftKeyDown()) return DEVIL;
        return null;
    }

    private static boolean active(ServerPlayer p, Skill s) {
        return s == DEVIL ? com.xenowarriors.skill.ffr.SrSkills.devilActive(p) : com.xenowarriors.skill.sp.SpState.persephone(p);
    }

    /** The transform key. True if this player has one of these transformations (handled, whether it went off or not). */
    /** Their transformation (Devil Unleashed / Persephone) is on right now. */
    public static boolean activeNow(ServerPlayer p) { Skill s = form(p); return s != null && active(p, s); }

    public static boolean press(ServerPlayer p) {
        Skill s = form(p);
        if (s == null) return false;
        String name = s == DEVIL ? "Devil Unleashed" : "Persephone";
        if (active(p, s)) {
            p.displayClientMessage(Component.literal(name + " can't be stopped - wait for it to fade").withStyle(ChatFormatting.DARK_RED), true);
            return true;
        }
        long now = p.level().getGameTime(), ready = READY.getOrDefault(p.getUUID(), 0L);
        if (now < ready && !p.isCreative()) {
            p.displayClientMessage(Component.literal(name + " is recharging (" + (ready - now + 19) / 20 + "s)").withStyle(ChatFormatting.RED), true);
            return true;
        }
        String f = s == DEVIL ? FormMastery.DEVIL : FormMastery.PERSEPHONE;
        float m = FormMastery.get(p, f);                                          // (its mastery: cheaper, back sooner, Devil Unleashed lasts longer)
        if (!p.isCreative() && !Karma.trySpend(p, s.getKarmaCost() * FormMastery.costMult(m))) return true;
        if (s.use(p)) {
            READY.put(p.getUUID(), now + Math.round(s.getCooldownTicks() * FormMastery.cooldownMult(m)));
            if (s == DEVIL) com.xenowarriors.skill.ffr.SrSkills.extendDevil(p, Math.round(240 * (FormMastery.durationMult(m) - 1F)));
            com.xenowarriors.anim.Poses.play(p, com.xenowarriors.anim.Poses.KA_BREAK, 12);
        }
        return true;
    }

    /** (Tests) clears the cooldown. */
    public static void resetForTest(ServerPlayer p) { READY.remove(p.getUUID()); }
}

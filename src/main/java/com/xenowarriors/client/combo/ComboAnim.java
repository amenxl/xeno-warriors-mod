package com.xenowarriors.client.combo;

import com.xenowarriors.character.CharacterType;
import com.xenowarriors.client.ClientCharacterData;
import com.xenowarriors.client.mikiko.MikikoAnimations;
import com.xenowarriors.client.mikiko.Move;
import com.xenowarriors.combat.ComboStyle;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * The combo swings for everyone without attack animations of their own (Mikiko and Rhalix have theirs for fists and
 * blades): each hit of each weapon's string is a different strike, and the heavy is a big launcher / overhead smash.
 */
public final class ComboAnim {
    private static final Move[] FIST = {Move.JAB_R, Move.JAB_L, Move.HOOK_R, Move.UPPERCUT_L, Move.KICK_FRONT};
    private static final Move[] QUICK = {Move.BLADE_H, Move.BLADE_THRUST, Move.BLADE_UP, Move.BLADE_H, Move.BLADE_DOWN};
    private static final Move[] SWORD = {Move.BLADE_H, Move.BLADE_UP, Move.BLADE_THRUST, Move.BLADE_DOWN};
    private static final Move[] HEAVY = {Move.BLADE_DOWN, Move.BLADE_H, Move.BLADE_DOWN};
    private static final Move[] SPEAR = {Move.BLADE_THRUST, Move.BLADE_THRUST, Move.BLADE_H, Move.BLADE_THRUST};
    private static final Move[] TOOL = {Move.BLADE_DOWN, Move.BLADE_H, Move.BLADE_DOWN};

    private ComboAnim() {}

    private static boolean ownAnimation(LivingEntity e, ComboStyle s) {
        if (!(e instanceof AbstractClientPlayer p)) return false;
        CharacterType t = ClientCharacterData.get(p);
        return (t == CharacterType.MIKIKO || t == CharacterType.RHALIX)
                && (s == ComboStyle.FIST || s == ComboStyle.MIKIKO || s == ComboStyle.SWORD || s == ComboStyle.QUICK_BLADE);
    }

    public static Move move(ComboStyle s, int hit, boolean heavy) {
        if (heavy) return switch (s) {
            case FIST, MIKIKO -> Move.UPPERCUT_L;
            case HEAVY, TOOL -> Move.BLADE_DOWN;
            default -> Move.BLADE_UP;
        };
        Move[] set = switch (s) {
            case FIST, MIKIKO -> FIST;
            case QUICK_BLADE -> QUICK;
            case SWORD -> SWORD;
            case HEAVY -> HEAVY;
            case SPEAR -> SPEAR;
            case TOOL -> TOOL;
        };
        return set[Math.floorMod(hit, set.length)];
    }

    public static void apply(HumanoidModel<?> m, LivingEntity e, float age) {
        if (com.xenowarriors.client.LimbBend.firstPerson) return;
        ComboClient.Swing s = ComboClient.swing(e);
        if (s == null || ownAnimation(e, s.style())) return;
        float p = s.progress(age);
        if (p < 0F || p > 1F) return;
        MikikoAnimations.poseMove(m, move(s.style(), s.hit(), s.heavy()), p);
        if (s.heavy()) {                                            // a heavy: thrown with the whole body
            float k = (float) Math.sin(Math.min(1F, p) * Math.PI);
            m.body.xRot += 0.25F * k;
            m.head.xRot -= 0.15F * k;
        }
        m.hat.copyFrom(m.head);
    }
}

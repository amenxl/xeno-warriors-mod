package com.xenowarriors.compat.epicfight;

import com.xenowarriors.XenoWarriors;
import net.minecraftforge.eventbus.api.IEventBus;
import yesman.epicfight.api.animation.AnimationManager;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.gameasset.Armatures;

/**
 * Our own Epic Fight animations (assets/xenowarriors/animmodels/animations, written by tools/xw/ef/ef_anims.py).
 */
public final class EfAnimations {
    public static AnimationManager.AnimationAccessor<StaticAnimation> CAL;
    /** Mikiko's Power Pole: a full spinning sweep, an overhead twirl into a slam, a spinning lunge thrust. */
    public static AnimationManager.AnimationAccessor<yesman.epicfight.api.animation.types.BasicAttackAnimation> POLE1, POLE2, POLE3;
    /** Rhalix's Z Sword: a rising gravity cleave and a crushing overhead slam. */
    public static AnimationManager.AnimationAccessor<yesman.epicfight.api.animation.types.BasicAttackAnimation> CLEAVE1, CLEAVE2;

    /**
     * Mikiko's own moves, recorded from his animations and rebuilt on Epic Fight's skeleton (tools/xw/ef/bake.py):
     * name -> length in ticks (his strike lands 26..42% of the way in).
     */
    public static final String[][] MIKIKO_MOVES = {
            {"jab_r", "7"}, {"jab_l", "7"}, {"hook_r", "8"}, {"uppercut_l", "8"}, {"chong_chui_r", "9"}, {"ding_zhou_r", "9"},
            {"tiao_zhang_l", "9"}, {"shuang_zhang", "10"}, {"tie_shan_kao", "14"}, {"flip_kick", "13"}, {"kick_front", "9"},
            {"kick_roundhouse", "10"}, {"kick_spin", "11"}, {"kick_side", "9"}, {"knee_flying", "9"}, {"lunge_punch_r", "9"},
            {"lunge_punch_l", "9"}};
    public static final java.util.Map<String, AnimationManager.AnimationAccessor<yesman.epicfight.api.animation.types.BasicAttackAnimation>> MIKIKO =
            new java.util.LinkedHashMap<>();

    /** Rhalix's ground combo (hits 0..7) and its heavy, baked the same way: name -> length in ticks. */
    public static final String[][] RHALIX_MOVES = {
            {"hit0", "12"}, {"hit1", "7"}, {"hit2", "7"}, {"hit3", "7"}, {"hit4", "10"}, {"hit5", "9"}, {"hit6", "9"}, {"hit7", "13"}, {"heavy", "15"}};
    public static final java.util.Map<String, AnimationManager.AnimationAccessor<yesman.epicfight.api.animation.types.BasicAttackAnimation>> RHALIX =
            new java.util.LinkedHashMap<>();

    private EfAnimations() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(EfAnimations::onRegister);
    }

    private static yesman.epicfight.api.animation.types.BasicAttackAnimation attack(AnimationManager.AnimationAccessor<yesman.epicfight.api.animation.types.BasicAttackAnimation> acc,
                                                                                float convert, float antic, float contact, float recovery) {
        return new yesman.epicfight.api.animation.types.BasicAttackAnimation(convert, antic, contact, recovery, null, Armatures.BIPED.get().toolR, acc, Armatures.BIPED);
    }

    private static void onRegister(AnimationManager.AnimationRegistryEvent event) {
        event.newBuilder(XenoWarriors.MOD_ID, b -> {
            CAL = b.nextAccessor("biped/xw/xw_cal", acc -> new StaticAnimation(0.1F, false, acc, Armatures.BIPED));
            POLE1 = b.nextAccessor("biped/combat/xw_pole1", acc -> attack(acc, 0.12F, 0.30F, 0.52F, 0.75F));
            POLE2 = b.nextAccessor("biped/combat/xw_pole2", acc -> attack(acc, 0.12F, 0.38F, 0.55F, 0.80F));
            POLE3 = b.nextAccessor("biped/combat/xw_pole3", acc -> attack(acc, 0.10F, 0.25F, 0.42F, 0.70F));
            CLEAVE1 = b.nextAccessor("biped/combat/xw_cleave1", acc -> attack(acc, 0.15F, 0.35F, 0.55F, 0.85F));
            CLEAVE2 = b.nextAccessor("biped/combat/xw_cleave2", acc -> attack(acc, 0.15F, 0.50F, 0.65F, 1.00F));
            for (String[] m : MIKIKO_MOVES) {
                float d = Integer.parseInt(m[1]) / 20F;
                MIKIKO.put(m[0], b.nextAccessor("biped/xw/mikiko_" + m[0], acc -> attack(acc, 0.05F, d * 0.24F, d * 0.44F, d * 0.8F)));
            }
            for (String[] m : RHALIX_MOVES) {
                if (EfAnimations.class.getResource("/assets/xenowarriors/animmodels/animations/biped/xw/rhalix_" + m[0] + ".json") == null) continue;   // (not baked yet)
                float d = Integer.parseInt(m[1]) / 20F;
                RHALIX.put(m[0], b.nextAccessor("biped/xw/rhalix_" + m[0], acc -> attack(acc, 0.05F, d * 0.24F, d * 0.44F, d * 0.8F)));
            }
        });
    }
}

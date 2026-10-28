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
        });
    }
}

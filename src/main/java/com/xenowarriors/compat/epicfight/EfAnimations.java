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

    private EfAnimations() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(EfAnimations::onRegister);
    }

    private static void onRegister(AnimationManager.AnimationRegistryEvent event) {
        event.newBuilder(XenoWarriors.MOD_ID, b -> {
            CAL = b.nextAccessor("biped/xw/xw_cal", acc -> new StaticAnimation(0.1F, false, acc, Armatures.BIPED));
        });
    }
}

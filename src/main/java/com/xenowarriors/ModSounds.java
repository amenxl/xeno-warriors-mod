package com.xenowarriors;

import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** The mod's own sounds (files in assets/xenowarriors/sounds/). */
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, XenoWarriors.MOD_ID);

    /** Whoosh, fire crackle and a metallic "shing" as the Hekublade forms. */
    public static final RegistryObject<SoundEvent> HEKUBLADE_SUMMON = SOUNDS.register("hekublade_summon",
            () -> SoundEvent.createVariableRangeEvent(XenoWarriors.id("hekublade_summon")));

    private ModSounds() {}
}

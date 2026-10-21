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

    /** Music disc: "Zack Eyecatcher". */
    public static final RegistryObject<SoundEvent> ZACK_EYECATCHER = SOUNDS.register("music_disc.zack_eyecatcher",
            () -> SoundEvent.createVariableRangeEvent(XenoWarriors.id("music_disc.zack_eyecatcher")));

    /** Music disc: "Thinking of You". */
    public static final RegistryObject<SoundEvent> THINKING_OF_YOU = SOUNDS.register("music_disc.thinking_of_you",
            () -> SoundEvent.createVariableRangeEvent(XenoWarriors.id("music_disc.thinking_of_you")));

    /** Music disc: "Step Into This New World, Recarnet!" (the theme). */
    public static final RegistryObject<SoundEvent> STEP_INTO_THIS_NEW_WORLD = SOUNDS.register("music_disc.step_into_this_new_world",
            () -> SoundEvent.createVariableRangeEvent(XenoWarriors.id("music_disc.step_into_this_new_world")));

    private ModSounds() {}
}

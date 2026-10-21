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

    /** Music disc: "The Sky Rebellion is Approaching!". */
    public static final RegistryObject<SoundEvent> THE_SKY_REBELLION = SOUNDS.register("music_disc.the_sky_rebellion_is_approaching",
            () -> SoundEvent.createVariableRangeEvent(XenoWarriors.id("music_disc.the_sky_rebellion_is_approaching")));

    /** Music disc: "It's Time to Get Groovy". */
    public static final RegistryObject<SoundEvent> GET_GROOVY = SOUNDS.register("music_disc.its_time_to_get_groovy",
            () -> SoundEvent.createVariableRangeEvent(XenoWarriors.id("music_disc.its_time_to_get_groovy")));

    /** Music disc: "Gravity Glitch Duel". */
    public static final RegistryObject<SoundEvent> GRAVITY_GLITCH_DUEL = SOUNDS.register("music_disc.gravity_glitch_duel",
            () -> SoundEvent.createVariableRangeEvent(XenoWarriors.id("music_disc.gravity_glitch_duel")));

    /** Music disc: "Lunar Ascendence". */
    public static final RegistryObject<SoundEvent> LUNAR_ASCENDENCE = SOUNDS.register("music_disc.lunar_ascendence",
            () -> SoundEvent.createVariableRangeEvent(XenoWarriors.id("music_disc.lunar_ascendence")));

    /** Music disc: "Smoke Break". */
    public static final RegistryObject<SoundEvent> SMOKE_BREAK = SOUNDS.register("music_disc.smoke_break",
            () -> SoundEvent.createVariableRangeEvent(XenoWarriors.id("music_disc.smoke_break")));

    /** Music disc: "Mimicry Eye Catcher". */
    public static final RegistryObject<SoundEvent> MIMICRY_EYE_CATCHER = SOUNDS.register("music_disc.mimicry_eye_catcher",
            () -> SoundEvent.createVariableRangeEvent(XenoWarriors.id("music_disc.mimicry_eye_catcher")));

    /** Music disc: "SkyBound Pursuit". */
    public static final RegistryObject<SoundEvent> SKYBOUND_PURSUIT = SOUNDS.register("music_disc.skybound_pursuit",
            () -> SoundEvent.createVariableRangeEvent(XenoWarriors.id("music_disc.skybound_pursuit")));

    /** Music disc: "Virtual Grind". */
    public static final RegistryObject<SoundEvent> VIRTUAL_GRIND = SOUNDS.register("music_disc.virtual_grind",
            () -> SoundEvent.createVariableRangeEvent(XenoWarriors.id("music_disc.virtual_grind")));

    /** Music disc: "Corrupted Pulse". */
    public static final RegistryObject<SoundEvent> CORRUPTED_PULSE = SOUNDS.register("music_disc.corrupted_pulse",
            () -> SoundEvent.createVariableRangeEvent(XenoWarriors.id("music_disc.corrupted_pulse")));

    /** Music disc: "Max's Chill Day". */
    public static final RegistryObject<SoundEvent> MAXS_CHILL_DAY = SOUNDS.register("music_disc.maxs_chill_day",
            () -> SoundEvent.createVariableRangeEvent(XenoWarriors.id("music_disc.maxs_chill_day")));

    private ModSounds() {}
}

package com.xenowarriors;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Status effects that show Mikiko's time inside the Hekublade, and his wait to go back in. */
public final class ModEffects {
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, XenoWarriors.MOD_ID);

    /** Time left inside the blade before it pushes him out. */
    public static final RegistryObject<MobEffect> BLADE_BOUND = EFFECTS.register("blade_bound",
            () -> new MobEffect(MobEffectCategory.NEUTRAL, 0xFF7A1A) {});
    /** Pushed out for staying too long: he can't go back in until this wears off. */
    public static final RegistryObject<MobEffect> BLADE_EXHAUSTED = EFFECTS.register("blade_exhausted",
            () -> new MobEffect(MobEffectCategory.HARMFUL, 0x5A3A8A) {});

    /** The Aurora virus (Season 2): amplifier = how bad it's got (0-2). */
    public static final RegistryObject<MobEffect> AURORA_VIRUS = EFFECTS.register("aurora_virus",
            () -> new MobEffect(MobEffectCategory.HARMFUL, 0x6FA8C8) {
                @Override public java.util.List<net.minecraft.world.item.ItemStack> getCurativeItems() { return java.util.List.of(); }   // (milk doesn't cure it - Aurora Medicine does)
            });
    /** Took Aurora Medicine: can't catch the virus for a while. */
    public static final RegistryObject<MobEffect> VIRUS_IMMUNITY = EFFECTS.register("virus_immunity",
            () -> new MobEffect(MobEffectCategory.BENEFICIAL, 0x7FE0D0) {});

    private ModEffects() {}
}

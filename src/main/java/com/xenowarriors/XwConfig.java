package com.xenowarriors;

import net.minecraftforge.common.ForgeConfigSpec;

/** config/xenowarriors-common.toml - settings for playing alongside other mods. */
public final class XwConfig {
    private XwConfig() {}

    public static final ForgeConfigSpec SPEC;
    /** DragonMineZ: how many stat points each DragonMineZ level is worth, compared to DragonMineZ on its own (1 = normal). */
    public static final ForgeConfigSpec.DoubleValue DMZ_LEVEL_UP_MULTIPLIER;
    /** DragonMineZ story/side quests: being in the right dimension isn't needed (to start them, or for "be in" objectives). */
    public static final ForgeConfigSpec.BooleanValue DMZ_QUESTS_ANY_DIMENSION;
    /** DragonMineZ installed: some Xeno Warriors energy blasts and beams use DragonMineZ's ki effects. */
    public static final ForgeConfigSpec.BooleanValue DMZ_KI_VISUALS;
    /** DragonMineZ's structures in the Recarnet dimension too (off: overworld only). */
    public static final ForgeConfigSpec.BooleanValue DMZ_STRUCTURES_IN_RECARNET;
    /** DragonMineZ movie missions: "be at Kami's Lookout" counts as met anywhere. */
    public static final ForgeConfigSpec.BooleanValue DMZ_MOVIES_ANYWHERE;
    /** Bot teammates walk up to DragonMineZ NPCs and talk to them for their own missions. */
    public static final ForgeConfigSpec.BooleanValue DMZ_BOTS_TALK;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("dragonminez");
        DMZ_LEVEL_UP_MULTIPLIER = b.comment("Each DragonMineZ level gives this many times the stat points DragonMineZ normally gives",
                        "(the extra ones arrive as free attribute points to spend in DragonMineZ's stats menu). 1 = off.")
                .defineInRange("levelUpStatMultiplier", 3.0, 1.0, 20.0);
        DMZ_QUESTS_ANY_DIMENSION = b.comment("DragonMineZ quests don't care which dimension you're in (start requirements and 'go to' objectives).")
                .define("questsAnyDimension", true);
        DMZ_KI_VISUALS = b.comment("Ultima's blasts and beam, Karma Blast and the Xeno Blast trail use DragonMineZ's ki particles.")
                .define("kiVisuals", true);
        DMZ_STRUCTURES_IN_RECARNET = b.comment("DragonMineZ's own structures (Kame House, Goku's house...) also generate in the Recarnet dimension.",
                        "Off: they stay in the overworld - Recarnet uses the overworld's biomes, so DragonMineZ would otherwise plan and force-place them there too.")
                .define("structuresInRecarnet", false);
        DMZ_MOVIES_ANYWHERE = b.comment("DragonMineZ's movie missions (the Movies saga and the movie side missions) don't need you at Kami's Lookout:",
                        "its 'be at Kami's Lookout' start requirement and objective count as met anywhere. Everything else (level, order) stays.")
                .define("moviesWithoutLookout", true);
        DMZ_BOTS_TALK = b.comment("Bot teammates walk up to DragonMineZ NPCs (Dende, the masters...) and talk to them themselves for their own missions:",
                        "'talk to' objectives, handing missions in, and taking a mission from its giver when you start yours.")
                .define("botsTalkToNpcs", true);
        b.pop();
        SPEC = b.build();
    }

    /** Reads a value, or its default before the config file has loaded. */
    public static double get(ForgeConfigSpec.DoubleValue v) {
        try { return v.get(); } catch (Throwable t) { return v.getDefault(); }
    }

    public static boolean get(ForgeConfigSpec.BooleanValue v) {
        try { return v.get(); } catch (Throwable t) { return v.getDefault(); }
    }
}

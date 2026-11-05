package com.xenowarriors.item;

import com.xenowarriors.XenoWarriors;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Recipes everyone knows from the start (no advancement to unlock them): they're put in the recipe book when you
 * join, so you can find them there. The Pet Whistle: an iron ingot, rabbit hide and string, or iron nuggets around
 * a feather with string.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RecipeUnlocks {
    private RecipeUnlocks() {}

    public static final ResourceLocation[] KNOWN = {XenoWarriors.id("fox_whistle"), XenoWarriors.id("fox_whistle_feather"), XenoWarriors.id("hamburger")};

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && !(p instanceof net.minecraftforge.common.util.FakePlayer)) p.awardRecipesByKey(KNOWN);
    }
}

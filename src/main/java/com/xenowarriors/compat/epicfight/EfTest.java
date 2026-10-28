package com.xenowarriors.compat.epicfight;

import net.minecraft.server.level.ServerPlayer;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

/** (UI tests) play one of our Epic Fight animations on a player. Only call when Epic Fight is loaded. */
public final class EfTest {
    private EfTest() {}

    public static void play(ServerPlayer p, String which) {
        if (!net.minecraftforge.fml.ModList.get().isLoaded("epicfight")) return;
        Impl.play(p, which);
    }

    private static final class Impl {
        static void play(ServerPlayer p, String which) {
            LivingEntityPatch<?> patch = EpicFightCapabilities.getEntityPatch(p, LivingEntityPatch.class);
            yesman.epicfight.api.asset.AssetAccessor<? extends yesman.epicfight.api.animation.types.StaticAnimation> anim = switch (which) {
                case "pole1" -> EfAnimations.POLE1; case "pole2" -> EfAnimations.POLE2; case "pole3" -> EfAnimations.POLE3;
                case "cleave1" -> EfAnimations.CLEAVE1; case "cleave2" -> EfAnimations.CLEAVE2;
                default -> EfAnimations.CAL; };
            if (patch != null && anim != null) patch.playAnimationSynchronized(anim, 0F);
            com.xenowarriors.XenoWarriors.LOGGER.info("XW-TEST ef play {} patch={} anim={}", which, patch, anim);
        }
    }
}

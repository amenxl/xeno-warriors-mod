package com.xenowarriors.compat.epicfight;

import net.minecraft.server.level.ServerPlayer;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

/** (UI tests) play one of our Epic Fight animations on a player. Only call when Epic Fight is loaded. */
public final class EfTest {
    private EfTest() {}

    public static void play(net.minecraft.world.entity.LivingEntity p, String which) {
        if (!net.minecraftforge.fml.ModList.get().isLoaded("epicfight")) return;
        Impl.play(p, which);
    }

    private static final class Impl {
        /** Epic Fight's bare-handed guard (whatever its field is called in this version). */
        @SuppressWarnings("unchecked")
        static yesman.epicfight.api.asset.AssetAccessor<? extends yesman.epicfight.api.animation.types.StaticAnimation> guard() {
            try {
                for (String n : new String[]{"BIPED_COMMON_GUARD", "FIST_GUARD", "BIPED_GUARD", "COMMON_GUARD", "SWORD_GUARD"}) {
                    try {
                        Object v = yesman.epicfight.gameasset.Animations.class.getField(n).get(null);
                        if (v != null) { com.xenowarriors.XenoWarriors.LOGGER.info("XW-TEST ef guard anim {}", n); return (yesman.epicfight.api.asset.AssetAccessor<? extends yesman.epicfight.api.animation.types.StaticAnimation>) v; }
                    } catch (NoSuchFieldException ignored) {}
                }
                java.util.List<String> names = new java.util.ArrayList<>();
                for (var fld : yesman.epicfight.gameasset.Animations.class.getFields()) if (fld.getName().contains("GUARD")) names.add(fld.getName());
                com.xenowarriors.XenoWarriors.LOGGER.info("XW-TEST ef guard fields {}", names);
                for (String n : names) if (!n.contains("HIT") && !n.contains("PARRY")) {
                    Object v = yesman.epicfight.gameasset.Animations.class.getField(n).get(null);
                    if (v instanceof yesman.epicfight.api.asset.AssetAccessor<?>) return (yesman.epicfight.api.asset.AssetAccessor<? extends yesman.epicfight.api.animation.types.StaticAnimation>) v;
                }
            } catch (Throwable t) { com.xenowarriors.XenoWarriors.LOGGER.info("XW-TEST ef guard lookup failed {}", t.toString()); }
            return null;
        }

        static void play(net.minecraft.world.entity.LivingEntity p, String which) {
            LivingEntityPatch<?> patch = EpicFightCapabilities.getEntityPatch(p, LivingEntityPatch.class);
            yesman.epicfight.api.asset.AssetAccessor<? extends yesman.epicfight.api.animation.types.StaticAnimation> anim = switch (which) {
                case "pole1" -> EfAnimations.POLE1; case "pole2" -> EfAnimations.POLE2; case "pole3" -> EfAnimations.POLE3;
                case "cleave1" -> EfAnimations.CLEAVE1; case "cleave2" -> EfAnimations.CLEAVE2; case "none", "mining" -> null;
                default -> EfAnimations.CAL; };
            if (patch instanceof yesman.epicfight.world.capabilities.entitypatch.player.PlayerPatch<?> pp)
                pp.toMode("mining".equals(which) ? yesman.epicfight.world.capabilities.entitypatch.player.PlayerPatch.PlayerMode.VANILLA
                        : yesman.epicfight.world.capabilities.entitypatch.player.PlayerPatch.PlayerMode.EPICFIGHT, true);     // its skeleton only shows in battle mode
            if ("guard".equals(which)) anim = guard();
            if (which.startsWith("ef:")) {                                   // any of Epic Fight's own (e.g. ef:FIST_AUTO1, ef:BIPED_ROLL_FORWARD)
                try { anim = (yesman.epicfight.api.asset.AssetAccessor<? extends yesman.epicfight.api.animation.types.StaticAnimation>) yesman.epicfight.gameasset.Animations.class.getField(which.substring(3)).get(null); }
                catch (Throwable t) { anim = null; com.xenowarriors.XenoWarriors.LOGGER.info("XW-TEST ef no animation {}", which); }
            }
            if (patch != null && anim != null) patch.playAnimationSynchronized(anim, 0F);
            com.xenowarriors.XenoWarriors.LOGGER.info("XW-TEST ef play {} patch={} anim={}", which, patch, anim);
        }
    }
}

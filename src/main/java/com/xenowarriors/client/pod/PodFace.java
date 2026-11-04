package com.xenowarriors.client.pod;

import com.xenowarriors.gov.HealingPodBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * Faces in a healing pod, read from the pod itself (no packets): standing in an occupied pod with the glass closed,
 * eyes are closed ("sleep"); once the doors swing open they open their eyes again.
 */
public final class PodFace {
    private PodFace() {}

    /** "sleep" while resting in a closed pod, otherwise null. */
    public static String face(Entity e) {
        if (e == null || e.level() == null) return null;
        BlockPos p = e.blockPosition();
        BlockState s = e.level().getBlockState(p);
        if (!(s.getBlock() instanceof HealingPodBlock) || s.getValue(HealingPodBlock.HALF) != DoubleBlockHalf.LOWER || !s.getValue(HealingPodBlock.OCCUPIED)) return null;
        if (Math.abs(e.getX() - (p.getX() + .5)) > 0.35 || Math.abs(e.getZ() - (p.getZ() + .5)) > 0.35) return null;
        return s.getValue(HealingPodBlock.OPEN) ? null : "sleep";
    }

    /** A face texture with the pod's expression (the sleeping version of a base face like ".../kyrie_face.png"). */
    public static net.minecraft.resources.ResourceLocation withFace(net.minecraft.resources.ResourceLocation base, Entity e) {
        String f = face(e);
        if (f == null || !base.getPath().endsWith("_face.png")) return base;
        return new net.minecraft.resources.ResourceLocation(base.getNamespace(), base.getPath().replace("_face.png", "_face_" + f + ".png"));
    }
}

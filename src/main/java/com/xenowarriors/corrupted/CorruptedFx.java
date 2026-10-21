package com.xenowarriors.corrupted;

import net.minecraft.core.particles.DustParticleOptions;
import org.joml.Vector3f;

/** Corrupted souls are dark purple and black - their particles too. */
public final class CorruptedFx {
    public static final DustParticleOptions DARK = new DustParticleOptions(new Vector3f(0.32F, 0.05F, 0.45F), 1.1F);
    public static final DustParticleOptions BLACK = new DustParticleOptions(new Vector3f(0.05F, 0.01F, 0.08F), 1.3F);

    private CorruptedFx() {}
}

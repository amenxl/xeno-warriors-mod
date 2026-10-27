package com.xenowarriors.vehicle;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;

/** Client-side vehicle effects: exhaust smoke, the helicopter's rotor wash kicking up dust (fancier with Lodestone). */
public final class VehicleFx {
    private VehicleFx() {}

    public static void tick(VehicleEntity v) {
        var level = v.level();
        float yaw = v.getYRot() * Mth.DEG_TO_RAD;
        double fx = -Mth.sin(yaw), fz = Mth.cos(yaw);
        boolean lodestone = net.minecraftforge.fml.ModList.get().isLoaded("lodestone");
        double moving = v.getDeltaMovement().horizontalDistance() + Math.abs(v.getX() - v.xo) + Math.abs(v.getZ() - v.zo);
        if (v.kind != VehicleKind.HELICOPTER && v.getControllingPassenger() != null && v.tickCount % 3 == 0) {
            double back = (v.kind == VehicleKind.GOV_TRUCK ? 2.6 : v.kind == VehicleKind.CAR ? 1.85 : 0.9) * v.kind.scale;
            double x = v.getX() - fx * back, z = v.getZ() - fz * back, y = v.getY() + 0.35;
            if (lodestone) com.xenowarriors.client.compat.lodestone.LodestoneFx.exhaust(level, x, y, z, moving);
            else level.addParticle(ParticleTypes.SMOKE, x, y, z, -fx * 0.02, 0.02, -fz * 0.02);
        }
        if (v.kind == VehicleKind.HELICOPTER && v.rotorSpin > 0.8F) {
            // rotor wash: dust ring on the ground under it when it's low
            int ground = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(v.getX()), (int) Math.floor(v.getZ()));
            double h = v.getY() - ground;
            if (h < 8 && v.tickCount % 2 == 0) {
                if (lodestone) com.xenowarriors.client.compat.lodestone.LodestoneFx.rotorWash(level, v.getX(), ground + 0.1, v.getZ(), 1 - h / 8);
                else for (int i = 0; i < 3; i++) {
                    double a = v.getRandom().nextDouble() * Math.PI * 2, r = 2 + v.getRandom().nextDouble() * 2;
                    level.addParticle(ParticleTypes.POOF, v.getX() + Math.cos(a) * r, ground + 0.15, v.getZ() + Math.sin(a) * r, Math.cos(a) * 0.12, 0.01, Math.sin(a) * 0.12);
                }
            }
        }
    }
}

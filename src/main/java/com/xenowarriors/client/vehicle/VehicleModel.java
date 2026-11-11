package com.xenowarriors.client.vehicle;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.vehicle.VehicleEntity;
import com.xenowarriors.vehicle.VehicleKind;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;

/** Vehicle models: wheels roll with the distance driven, rotors spin up when someone's flying. */
public class VehicleModel extends GeoModel<VehicleEntity> {
    @Override public ResourceLocation getModelResource(VehicleEntity e) { return XenoWarriors.id("geo/" + e.kind.model + ".geo.json"); }
    @Override public ResourceLocation getTextureResource(VehicleEntity e) { return XenoWarriors.id("textures/entity/vehicle/" + e.kind.id + "_" + e.kind.variant(e.variant()) + ".png"); }
    @Override public ResourceLocation getAnimationResource(VehicleEntity e) { return XenoWarriors.id("animations/" + e.kind.model + ".animation.json"); }

    /** The local player sits in one of this vehicle's seats (not on top / hanging on) and looks through their own eyes. */
    public static boolean insideFirstPerson(VehicleEntity e) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        return mc.player != null && mc.player.getVehicle() == e && mc.getCameraEntity() == mc.player && mc.options.getCameraType().isFirstPerson()
                && e.seatOf(mc.player).type() == VehicleKind.SeatType.SIT;
    }

    @Override
    public void setCustomAnimations(VehicleEntity e, long id, AnimationState<VehicleEntity> state) {
        float pt = state.getPartialTick();
        // seated inside it in first person: the roof, pillars, window frames and headrests go (they'd fill the view);
        // everyone else - and you in third person - sees the whole cabin
        CoreGeoBone fp = getAnimationProcessor().getBone("fp");
        if (fp != null) fp.setHidden(insideFirstPerson(e));
        float wheel = Mth.lerp(pt, e.wheelRotO, e.wheelRot);
        float steer = Mth.lerp(pt, e.steerO, e.steer) * Mth.DEG_TO_RAD;
        for (String b : new String[]{"wheel_fl", "wheel_fr", "wheel_rl", "wheel_rr", "wheel_f", "wheel_r"}) {
            CoreGeoBone bone = getAnimationProcessor().getBone(b);
            if (bone == null) continue;
            bone.setRotX(-wheel);                                       // (rolling forward: the tops go toward the nose, -z)
            bone.setRotY(b.equals("wheel_fl") || b.equals("wheel_fr") || b.equals("wheel_f") ? -steer : 0F);  // front wheels turn into the turn (+ steer = turning right)
        }
        if (e.kind.heli()) {
            float rotor = Mth.lerp(pt, e.rotorRotO, e.rotorRot);
            CoreGeoBone main = getAnimationProcessor().getBone("rotor");
            if (main != null) main.setRotY(rotor);
            CoreGeoBone tail = getAnimationProcessor().getBone("tail_rotor");
            if (tail != null) tail.setRotX(rotor * 1.6F);
        }
    }
}

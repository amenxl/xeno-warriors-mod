package com.xenowarriors.client.party;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;

/**
 * Reviving someone: the reviver holds a hand out toward them (the other hand at their chest), their karma flowing out of
 * it into the one who's down (the flow itself is particles from the server: ReviveCircle.flow). Drawn on our model
 * for players and bots alike - Epic Fight doesn't draw them meanwhile (its stance would cover the pose).
 */
public final class ReviveClient {
    private ReviveClient() {}

    private record At(int target, long until) {}
    private static final Map<Integer, At> ON = com.xenowarriors.client.ClientReset.track(new HashMap<>());

    public static void set(int helper, int target) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        ON.put(helper, new At(target, mc.level.getGameTime() + 8));
    }

    /** Who this one is reviving right now (null: nobody). */
    public static Entity target(Entity e) {
        At a = ON.get(e.getId());
        var mc = Minecraft.getInstance();
        if (a == null || mc.level == null) return null;
        if (mc.level.getGameTime() > a.until) { ON.remove(e.getId()); return null; }
        return mc.level.getEntity(a.target);
    }

    public static boolean reviving(Entity e) { return target(e) != null; }

    /** Right hand reaching out to them (turned toward them from where the body faces), left hand on the chest, head down at them. */
    public static void apply(HumanoidModel<?> m, Player p, float age) {
        Entity t = target(p);
        if (t == null) return;
        double dx = t.getX() - p.getX(), dz = t.getZ() - p.getZ(), dy = (t.getY() + 0.3) - (p.getY() + 1.35);
        float toward = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90F;
        float rel = Mth.wrapDegrees(toward - p.yBodyRot) * Mth.DEG_TO_RAD;
        float down = (float) Math.atan2(-dy, Math.max(0.3, Math.sqrt(dx * dx + dz * dz)));
        float pulse = Mth.sin(age * 0.3F) * 0.04F;
        m.rightArm.xRot = -Mth.HALF_PI + down + pulse;
        m.rightArm.yRot = Mth.clamp(rel, -1.2F, 1.2F);
        m.rightArm.zRot = 0F;
        m.leftArm.xRot = -0.9F;
        m.leftArm.yRot = 0.5F;
        m.leftArm.zRot = 0.1F;
        m.head.xRot = Mth.clamp(down * 0.8F, -0.2F, 0.9F);
        m.head.yRot = Mth.clamp(rel, -0.9F, 0.9F);
    }
}

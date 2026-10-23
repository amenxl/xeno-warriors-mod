package com.xenowarriors.client;

import com.xenowarriors.client.mikiko.GeoAnims;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;

/** Starving Rhalix: the hungry face, clutching his growling stomach, rubbing it while he stands about. */
public final class RhalixHungerClient {
    private static final Map<Integer, Boolean> HUNGRY = new HashMap<>();
    private static final Map<Integer, Integer> GROWL = new HashMap<>();
    private static final int GROWL_TIME = 48;

    private RhalixHungerClient() {}

    public static void set(int id, boolean hungry, boolean growl) {
        if (hungry) HUNGRY.put(id, true);
        else HUNGRY.remove(id);
        var mc = Minecraft.getInstance();
        if (growl && mc.level != null && mc.level.getEntity(id) != null) GROWL.put(id, mc.level.getEntity(id).tickCount);
    }

    public static boolean hungry(Player p) {
        return HUNGRY.containsKey(p.getId());
    }

    /** Ticks into the growl, or -1. */
    public static float growl(Player p, float age) {
        Integer start = GROWL.get(p.getId());
        if (start == null) return -1F;
        float t = age - start;
        if (t < 0F || t > GROWL_TIME) return -1F;
        return t;
    }

    public static String face(Player p) {
        if (!hungry(p) && growl(p, p.tickCount) < 0F) return null;
        if (growl(p, p.tickCount) >= 0F) return (p.tickCount / 5) % 2 == 0 ? "hungry2" : "hungry";
        return (p.tickCount + p.getId() * 13) % 90 < 30 ? "hungry2" : "hungry";
    }

    public static void apply(HumanoidModel<?> m, Player p, float age) {
        if (RhalixCombatAnim.attacking(p) || p.isSleeping()) return;
        float g = growl(p, age);
        if (g >= 0F) {
            float len = GeoAnims.length(GeoAnims.RHALIX, "rh_hungry_growl");
            GeoAnims.apply(m, GeoAnims.RHALIX, "rh_hungry_growl", g, false, GeoAnims.envelope(g, len, 3F));
            m.hat.copyFrom(m.head);
            return;
        }
        if (hungry(p) && p.onGround() && !p.isUsingItem() && !p.swinging && !Busy.busy(p)
                && Math.abs(p.getX() - p.xo) + Math.abs(p.getZ() - p.zo) < 0.02 && !RhalixCombatAnim.isSword(p)
                && !RhalixWingsClient.busy(p)) {
            GeoAnims.apply(m, GeoAnims.RHALIX, "rh_hungry_idle", age, true, 0.85F);
            m.hat.copyFrom(m.head);
        } else if (hungry(p)) {
            // walking along hungry: slouched, head low
            m.head.xRot = Mth.lerp(0.5F, m.head.xRot, 0.3F);
        }
    }
}

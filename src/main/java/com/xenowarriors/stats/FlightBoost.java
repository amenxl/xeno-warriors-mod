package com.xenowarriors.stats;

import net.minecraft.server.level.ServerPlayer;

/** How much faster someone flies: the Speed stat, Netsu's fire and Fox King mode all push flight speed up. */
public final class FlightBoost {
    private FlightBoost() {}

    public static float factor(ServerPlayer p) {
        float f = 1F + Stat.SPEED.bonus(Stats.level(p, Stat.SPEED)) * 0.6F;    // up to +90% from Speed
        if (com.xenowarriors.mikiko.Netsu.active(p)) f += 0.7F;                  // blazing through the air
        if (com.xenowarriors.mikiko.FoxKing.active(p)) f += 0.35F;
        if (com.xenowarriors.sky.SkyArmorItem.wearing(p, net.minecraft.world.entity.EquipmentSlot.CHEST)) f += 0.5F;   // Sky Chestplate
        return f;
    }
}

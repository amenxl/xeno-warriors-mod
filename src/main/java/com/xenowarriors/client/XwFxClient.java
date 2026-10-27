package com.xenowarriors.client;

import com.xenowarriors.network.FxPacket;
import net.minecraft.client.Minecraft;

/** Plays effects sent by the server - with Lodestone's particles and screen shake when it's installed. */
public final class XwFxClient {
    private XwFxClient() {}

    public static void play(FxPacket m) {
        if (!com.xenowarriors.client.compat.lodestone.LodestoneHooks.on()) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        double d = mc.player.distanceToSqr(m.x(), m.y(), m.z());
        var L = mc.level;
        switch (m.type()) {
            case FxPacket.SKILL -> com.xenowarriors.client.compat.lodestone.LodestoneFx.burst(L, m.x(), m.y() + 1, m.z(), m.color(), m.scale());
            case FxPacket.ULTIMATE -> {
                com.xenowarriors.client.compat.lodestone.LodestoneFx.shockwave(L, m.x(), m.y(), m.z(), m.color(), m.scale());
                if (d < 30 * 30) com.xenowarriors.client.compat.lodestone.LodestoneFx.shake((float) (0.6 * (1 - Math.sqrt(d) / 30)), 18);
            }
            case FxPacket.KA -> com.xenowarriors.client.compat.lodestone.LodestoneFx.kaRing(L, m.x(), m.y(), m.z(), m.color());
            case FxPacket.EYE -> com.xenowarriors.client.compat.lodestone.LodestoneFx.eye(L, m.x(), m.y(), m.z());
            case FxPacket.SHOCK -> {
                com.xenowarriors.client.compat.lodestone.LodestoneFx.shockwave(L, m.x(), m.y(), m.z(), m.color(), m.scale());
                if (d < 20 * 20) com.xenowarriors.client.compat.lodestone.LodestoneFx.shake(0.35F, 10);
            }
            default -> {}
        }
    }
}

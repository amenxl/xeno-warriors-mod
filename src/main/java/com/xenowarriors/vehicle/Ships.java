package com.xenowarriors.vehicle;

import com.xenowarriors.bot.PartyBotEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * The player's ship: whoever's first aboard is at the helm. Aboard, right-click the ship to hand the helm to one of
 * your bots (and again to take it back). A bot at the helm sails where you look while you hold W (S or letting go
 * slows it to a stop); if you're not aboard, it sails after you.
 */
public final class Ships {
    private Ships() {}

    /** (Tests) helms handed to bots. */
    public static int handed;

    /** Right-clicked from aboard: the helm goes to a bot of yours, or back to you. */
    public static void helm(VehicleEntity v, Player p) {
        List<Entity> ps = v.getPassengers();
        if (ps.isEmpty()) return;
        Entity first = v.driver();
        if (first == p) {
            PartyBotEntity bot = null;
            for (Entity e : ps) if (e instanceof PartyBotEntity b && p.getUUID().equals(b.ownerId()) && !b.downed()) { bot = b; break; }
            if (bot == null) { p.displayClientMessage(Component.literal("No crewmate aboard to take the helm - bring a bot along.").withStyle(ChatFormatting.GRAY), true); return; }
            giveHelm(v, bot);
            p.displayClientMessage(Component.empty().append(bot.label()).append(" takes the helm - hold W to sail where you look, S to stop. Right-click the ship to take it back.")
                    .withStyle(ChatFormatting.AQUA), true);
        } else if (first instanceof PartyBotEntity b && p.getUUID().equals(b.ownerId())) {
            b.removeTag(VehicleEntity.NPC_DRIVER_TAG);
            v.setHelm(p);
            v.npcDriveTo(null, 0);
            p.displayClientMessage(Component.literal("You take the helm.").withStyle(ChatFormatting.AQUA), true);
        } else p.displayClientMessage(Component.literal("Someone else is at the helm.").withStyle(ChatFormatting.GRAY), true);
    }

    /** This bot (aboard) takes the helm. */
    public static void giveHelm(VehicleEntity v, PartyBotEntity bot) {
        bot.addTag(VehicleEntity.NPC_DRIVER_TAG);
        v.setHelm(bot);
        handed++;
    }

    /** Every tick (server), the player's ship: a bot at the helm sails it. */
    static void tick(VehicleEntity v) {
        if (v.tickCount % 2 != 0) return;
        if (!(v.driver() instanceof PartyBotEntity b) || !b.getTags().contains(VehicleEntity.NPC_DRIVER_TAG)) return;
        ServerPlayer o = b.owner();
        if (o == null || o.level() != v.level()) { v.npcDriveTo(v.position(), 0); return; }
        if (o.getVehicle() == v) {
            if (o.zza > 0.1F) {                                                  // W: where you look
                float yaw = o.getYHeadRot() * net.minecraft.util.Mth.DEG_TO_RAD;
                v.npcDriveTo(v.position().add(-net.minecraft.util.Mth.sin(yaw) * 30, 0, net.minecraft.util.Mth.cos(yaw) * 30), 1.0);
            } else v.npcDriveTo(v.position(), 0);                                // (no orders: it slows to a stop)
            return;
        }
        Entity ov = o.getVehicle() != null ? o.getVehicle() : o;               // you went ahead (in a boat, swimming...): it follows
        Vec3 to = ov.position();
        v.npcDriveTo(v.distanceToSqr(ov) < 10 * 10 ? v.position() : to, 0.9);
    }
}

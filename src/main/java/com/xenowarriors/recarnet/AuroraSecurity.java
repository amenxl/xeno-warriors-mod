package com.xenowarriors.recarnet;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.bot.PartyBotEntity;
import com.xenowarriors.room.RoomTech;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The Aurora Security System: a battle that starts in Aurora City - you, a party member or a bot teammate hitting an
 * enemy, or an enemy hitting one of you - is moved into a Room (a copy of the area, see room.RoomTech) so the city
 * can't be wrecked; when the enemies are beaten everyone is sent back. In the Room the copied buildings can be
 * smashed; in the city itself you mine and build as normal. Rooms only open for a battle.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AuroraSecurity {
    private AuroraSecurity() {}

    /** Battles to move at the end of this tick: player -> the foe. (Not mid-hit: moving entities inside a damage event breaks things.) */
    private static final Map<UUID, LivingEntity> PENDING = new LinkedHashMap<>();
    /** (For the tests) Rooms the security system opened. */
    public static int opened;

    private static boolean hostile(LivingEntity e, LivingEntity other) {
        if (!(e instanceof Mob m) || e instanceof PartyBotEntity || !e.isAlive()) return false;
        return e instanceof Enemy || m.getTarget() == other;
    }

    /** The player whose battle this is (a bot's owner), if they're in Aurora City. */
    private static ServerPlayer side(LivingEntity e) {
        ServerPlayer p = e instanceof ServerPlayer sp && !(sp instanceof FakePlayer) ? sp : e instanceof PartyBotEntity b ? b.owner() : null;
        if (p == null || !Recarnet.inAurora(e.level(), e.blockPosition()) || p.isSpectator()) return null;
        return p;
    }

    @SubscribeEvent
    public static void onAttack(LivingAttackEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide || !(event.getSource().getEntity() instanceof LivingEntity attacker) || attacker == victim) return;
        ServerPlayer p = side(attacker);
        if (p != null && hostile(victim, attacker)) { PENDING.putIfAbsent(p.getUUID(), victim); return; }
        p = side(victim);
        if (p != null && hostile(attacker, victim)) PENDING.putIfAbsent(p.getUUID(), attacker);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || PENDING.isEmpty()) return;
        var server = event.getServer();
        for (var e : new java.util.ArrayList<>(PENDING.entrySet())) {
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (p != null && p.isAlive() && Recarnet.inAurora(p.level(), p.blockPosition()) && RoomTech.auroraBattle(p, e.getValue())) opened++;
        }
        PENDING.clear();
    }
}

package com.xenowarriors.rhalix;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.character.ModCapabilities;
import com.xenowarriors.stats.PlayerOptions;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

/**
 * Rhalix's passive Gravity Magnet (on by default; toggled in his character menu): dropped items and XP orbs within
 * 10 blocks drift to him with a faint purple gravity trail. Not something another player has just thrown (2 s), not in
 * VR. A Rhalix bot teammate does it too (they come to the bot, using its owner's setting).
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID)
public final class GravityMagnet {
    public static final double RADIUS = 10.0;
    private static final DustParticleOptions TRAIL = new DustParticleOptions(new Vector3f(0.62F, 0.32F, 0.95F), 0.55F);
    /** (tests) things pulled so far. */
    public static int pulled;

    private GravityMagnet() {}

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 2 != 0) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!p.isAlive() || p.isSpectator() || ModCapabilities.getType(p) != CharacterType.RHALIX) continue;
            if (!PlayerOptions.get(p, PlayerOptions.MAGNET)) continue;
            pull(p, p, server.getTickCount());
        }
        for (ServerLevel l : server.getAllLevels()) {
            if (l.dimension() == com.xenowarriors.vr.Vr.LEVEL) continue;
            for (ServerPlayer o : l.players()) for (com.xenowarriors.bot.PartyBotEntity b : com.xenowarriors.bot.PartyBots.bots(o)) {
                if (b.level() != l || !b.isAlive() || b.downed() || b.character() != CharacterType.RHALIX || !PlayerOptions.get(o, PlayerOptions.MAGNET)) continue;
                pull(b, o, server.getTickCount());
            }
        }
    }

    /** Draws loose items and orbs around {@code to} toward it. {@code who}: the player whose throws don't count as "someone else's". */
    private static void pull(LivingEntity to, Player who, int tick) {
        if (!(to.level() instanceof ServerLevel l) || l.dimension() == com.xenowarriors.vr.Vr.LEVEL) return;
        Vec3 goal = to.position().add(0, to.getBbHeight() * 0.45, 0);
        for (Entity e : l.getEntities((Entity) null, to.getBoundingBox().inflate(RADIUS), GravityMagnet::loose)) {
            if (e.distanceToSqr(goal) > RADIUS * RADIUS) continue;
            if (e instanceof ItemEntity it) {
                if (it.getAge() < 0) continue;                                                    // (display items that never despawn)
                Entity thrower = it.getOwner();
                if (thrower instanceof Player tp && tp != who && it.getAge() < 40) continue;      // someone else just threw it
            }
            Vec3 d = goal.subtract(e.position());
            double dist = d.length();
            if (dist < 0.6) continue;                                                              // (there: it gets picked up)
            double speed = Math.min(0.55, 0.12 + (RADIUS - dist) * 0.035);
            Vec3 v = e.getDeltaMovement().scale(0.4).add(d.normalize().scale(speed));
            e.setDeltaMovement(v);
            e.setNoGravity(false);
            e.hasImpulse = true;
            if (e instanceof ItemEntity) pulled++;
            if ((tick + e.getId()) % 6 == 0) l.sendParticles(TRAIL, e.getX(), e.getY() + 0.15, e.getZ(), 1, 0.05, 0.05, 0.05, 0);
        }
    }

    private static boolean loose(Entity e) {
        return e.isAlive() && (e instanceof ItemEntity || e instanceof ExperienceOrb);
    }
}

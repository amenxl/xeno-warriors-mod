package com.xenowarriors.character;

import com.xenowarriors.XenoWarriors;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Ai-oni and Rhalix: they can never be in the same party (checked in Parties), and Ai-oni can't come within 400 blocks of
 * a Rhalix player - she's warned as she gets close (450) and pushed back out to the edge if she crosses it.
 * Switching to either character while partied with the other leaves the party.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AiOniRules {
    public static final double KEEP_AWAY = 400, WARN = 450;

    private AiOniRules() {}

    public static boolean clash(ServerPlayer a, ServerPlayer b) {
        CharacterType x = ModCapabilities.getType(a), y = ModCapabilities.getType(b);
        return (x == CharacterType.AIONI && y == CharacterType.RHALIX) || (x == CharacterType.RHALIX && y == CharacterType.AIONI);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p) || p.tickCount % 10 != 0) return;
        CharacterType t = ModCapabilities.getType(p);
        if (t != CharacterType.AIONI && t != CharacterType.RHALIX) return;
        // never partied together
        for (ServerPlayer m : com.xenowarriors.party.Parties.members(p))
            if (m != p && clash(m, p)) {
                ServerPlayer out = t == CharacterType.AIONI ? p : m;
                com.xenowarriors.party.Parties.leave(out, true);
                out.sendSystemMessage(Component.literal("Ai-oni and Rhalix can never be in the same party.").withStyle(ChatFormatting.RED));
                break;
            }
        if (t != CharacterType.AIONI || p.isSpectator() || p.isCreative()) return;
        // keep 400 blocks from every Rhalix in her dimension
        for (ServerPlayer r : p.serverLevel().players()) {
            if (r == p || ModCapabilities.getType(r) != CharacterType.RHALIX) continue;
            Vec3 d = new Vec3(p.getX() - r.getX(), 0, p.getZ() - r.getZ());
            double dist = d.length();
            if (dist < KEEP_AWAY) {
                Vec3 dir = dist < 0.01 ? new Vec3(1, 0, 0) : d.normalize();
                double nx = r.getX() + dir.x * (KEEP_AWAY + 4), nz = r.getZ() + dir.z * (KEEP_AWAY + 4);
                int ny = p.serverLevel().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(nx), (int) Math.floor(nz));
                p.serverLevel().sendParticles(ParticleTypes.LARGE_SMOKE, p.getX(), p.getY() + 1, p.getZ(), 20, 0.4, 0.8, 0.4, 0.02);
                p.teleportTo(nx, Math.max(ny, p.getY()), nz);
                p.fallDistance = 0;
                p.level().playSound(null, p.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1F, 0.6F);
                p.displayClientMessage(Component.literal("Something keeps you away from Rhalix... (400 blocks)").withStyle(ChatFormatting.DARK_RED), true);
            } else if (dist < WARN && p.tickCount % 40 == 0) {
                p.displayClientMessage(Component.literal("Rhalix is near - you can't get closer than 400 blocks (" + (int) dist + ")").withStyle(ChatFormatting.RED), true);
            }
        }
    }
}

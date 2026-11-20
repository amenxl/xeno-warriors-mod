package com.xenowarriors.s3;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.character.ModCapabilities;
import com.xenowarriors.character.Seasons;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Where and when Distortions open (Season 3 only): now and then near players, more often and bigger near high Karma. */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Distortions {
    private Distortions() {}

    static int power(ServerPlayer p) {
        if (ModCapabilities.getType(p) == CharacterType.NONE) return 0;
        return p.getCapability(ModCapabilities.PLAYER_CHARACTER).map(d -> (d.statLevel[0] + d.statLevel[1] * 2 + d.statLevel[2]) / 4).orElse(0);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p) || (p.tickCount + p.getId() * 37) % 300 != 0) return;
        ServerLevel s = p.serverLevel();
        boolean field = com.xenowarriors.recarnet.Recarnet.inDistortionField(s, p.blockPosition());
        if (!field && (p.tickCount + p.getId() * 37) % 1200 != 0) return;
        if ((s.dimension() != Level.OVERWORLD && !com.xenowarriors.recarnet.Recarnet.is(s)) || !Seasons.unlocked(s.getServer(), 3) || p.isSpectator()) return;
        int pw = power(p);
        // the Distortion Field: one tears open now and then, and up to two can be about (halved again: outside it
        // about a sixth as often as at first, in it about a quarter)
        float chance = field ? 0.14F : 0.004F + 0.008F * Math.min(1F, pw / 50F);
        if (p.getRandom().nextFloat() > chance) return;
        var near = s.getEntitiesOfClass(DistortionEntity.class, p.getBoundingBox().inflate(field ? 64 : 160));
        if (near.size() >= (field ? 2 : 1)) return;
        int size = 1 + (pw > 20 || (field && p.getRandom().nextInt(3) == 0) ? 1 : 0) + ((pw > 45 || field) && p.getRandom().nextInt(field ? 4 : 2) == 0 ? 1 : 0);
        open(s, p, size);
    }

    /** Opens one 24-40 blocks from the player - next to villagers if there are any around (they want bodies). */
    public static DistortionEntity open(ServerLevel s, ServerPlayer p, int size) {
        var r = p.getRandom();
        BlockPos at = null;
        var villagers = s.getEntitiesOfClass(Villager.class, p.getBoundingBox().inflate(48));
        if (!villagers.isEmpty()) {
            Villager v = villagers.get(r.nextInt(villagers.size()));
            at = v.blockPosition().offset(r.nextInt(13) - 6, 0, r.nextInt(13) - 6);
        } else {
            double a = r.nextDouble() * Math.PI * 2, d = 24 + r.nextInt(17);
            at = p.blockPosition().offset((int) (Math.cos(a) * d), 0, (int) (Math.sin(a) * d));
        }
        if (!s.isLoaded(at)) at = p.blockPosition();                   // (a short view distance: don't load a chunk for it)
        at = s.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at);
        DistortionEntity d = S3Entities.DISTORTION.get().create(s);
        if (d == null) return null;
        d.moveTo(at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 0, 0);
        d.setSize(size);
        s.addFreshEntity(d);
        s.playSound(null, at, SoundEvents.END_PORTAL_SPAWN, SoundSource.HOSTILE, 1.2F, 0.6F);
        for (ServerPlayer o : s.getEntitiesOfClass(ServerPlayer.class, d.getBoundingBox().inflate(96)))
            o.displayClientMessage(Component.literal("The air tears open nearby - a Distortion!").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD), false);
        if (!villagers.isEmpty()) callDefenders(s, d, villagers.get(0).blockPosition());
        return d;
    }

    /** Defenders that come per Distortion by a village: 3, plus one per size (up to 6). */
    public static int defenders(int size) { return Math.min(6, 3 + size); }

    /**
     * A Distortion by a village: people come running out of it to fight - armed townsfolk (Village Defenders) going for its
     * C-souls and then the Distortion itself. They go home once it's closed (HumanNpcEntity.makeDefender).
     */
    public static void callDefenders(ServerLevel s, DistortionEntity d, BlockPos village) {
        var r = s.getRandom();
        int n = defenders(d.size());
        for (int i = 0; i < n; i++) {
            double a = r.nextDouble() * Math.PI * 2, dist = 6 + r.nextDouble() * 6;
            BlockPos at = village.offset((int) (Math.cos(a) * dist), 0, (int) (Math.sin(a) * dist));
            if (!s.isLoaded(at)) at = village;
            at = s.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at);
            var h = com.xenowarriors.npc.ModNpcs.HUMAN.get().create(s);
            if (h == null) continue;
            h.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, r.nextFloat() * 360F, 0F);
            try { h.finalizeSpawn(s, s.getCurrentDifficultyAt(at), net.minecraft.world.entity.MobSpawnType.EVENT, null, null); } catch (Throwable ignored) {}
            h.makeDefender();
            h.setTarget(d);
            s.addFreshEntity(h);
            s.sendParticles(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER, h.getX(), h.getY() + 1, h.getZ(), 6, 0.3, 0.5, 0.3, 0);
        }
        for (ServerPlayer o : s.getEntitiesOfClass(ServerPlayer.class, d.getBoundingBox().inflate(96)))
            o.displayClientMessage(Component.literal("The villagers rush out to fight the Distortion!").withStyle(ChatFormatting.GREEN), false);
    }
}

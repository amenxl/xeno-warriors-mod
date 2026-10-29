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
        // the Distortion Field: one tears open most of the time you look around, and up to three can be about
        float chance = field ? 0.55F : 0.025F + 0.05F * Math.min(1F, pw / 50F);
        if (p.getRandom().nextFloat() > chance) return;
        var near = s.getEntitiesOfClass(DistortionEntity.class, p.getBoundingBox().inflate(field ? 64 : 160));
        if (near.size() >= (field ? 3 : 1)) return;
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
        at = s.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at);
        DistortionEntity d = S3Entities.DISTORTION.get().create(s);
        if (d == null) return null;
        d.moveTo(at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 0, 0);
        d.setSize(size);
        s.addFreshEntity(d);
        s.playSound(null, at, SoundEvents.END_PORTAL_SPAWN, SoundSource.HOSTILE, 1.2F, 0.6F);
        for (ServerPlayer o : s.getEntitiesOfClass(ServerPlayer.class, d.getBoundingBox().inflate(96)))
            o.displayClientMessage(Component.literal("The air tears open nearby - a Distortion!").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD), false);
        return d;
    }
}

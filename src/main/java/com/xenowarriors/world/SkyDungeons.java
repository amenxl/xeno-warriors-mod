package com.xenowarriors.world;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.npc.SkylanderEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/** Sky temple guardians leave visitors alone - until someone breaks the temple or opens its chests. */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SkyDungeons {
    private SkyDungeons() {}

    private static List<SkylanderEntity> guards(Level level, BlockPos pos) {
        return level.getEntitiesOfClass(SkylanderEntity.class, new AABB(pos).inflate(32),
                g -> g.isAlive() && g.defendsAt() != null && g.defendsAt().distSqr(pos) < 16 * 16);
    }

    private static void alarm(ServerPlayer p, BlockPos pos, String why) {
        if (p.isCreative() || p.isSpectator()) return;
        List<SkylanderEntity> gs = guards(p.level(), pos);
        boolean fresh = false;
        for (SkylanderEntity g : gs) {
            if (g.getTarget() != p) fresh = true;
            g.defendAgainst(p);
        }
        if (fresh) p.displayClientMessage(Component.literal((gs.stream().anyMatch(SkylanderEntity::guardian) ? "Skylander Guardian" : "Skylander")
                + ": \"" + why + "\"").withStyle(ChatFormatting.AQUA), false);
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer p) alarm(p, event.getPos(), "Stop wrecking our home!");
    }

    @SubscribeEvent
    public static void onOpen(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        if (p.level().getBlockEntity(event.getPos()) instanceof Container) alarm(p, event.getPos(), "Hands off the sky treasure!");
    }
}

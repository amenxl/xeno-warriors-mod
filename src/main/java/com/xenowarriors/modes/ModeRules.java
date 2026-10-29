package com.xenowarriors.modes;

import com.xenowarriors.XenoWarriors;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** The game modes' arenas (Journey, the Tower, VR) are not for building: no breaking or placing blocks inside them (ops in creative excepted). */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ModeRules {
    private ModeRules() {}

    /** In one of the modes' arenas right now. */
    public static boolean inMode(Player p) {
        return com.xenowarriors.journey.Journey.inRun(p) || com.xenowarriors.tower.Tower.is(p.level()) || com.xenowarriors.vr.Vr.inVr(p);
    }

    private static boolean blocked(Player p) {
        return inMode(p) && !(p.isCreative() && p.hasPermissions(2));
    }

    private static void tell(Player p) {
        if (p instanceof ServerPlayer sp && sp.tickCount % 20 == 0) sp.displayClientMessage(Component.literal("No building here").withStyle(ChatFormatting.RED), true);
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent e) {
        if (blocked(e.getPlayer())) { e.setCanceled(true); tell(e.getPlayer()); }
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent e) {
        if (e.getEntity() instanceof Player p && blocked(p)) { e.setCanceled(true); tell(p); }
    }

    @SubscribeEvent
    public static void onLeftClick(PlayerInteractEvent.LeftClickBlock e) {
        if (blocked(e.getEntity())) { e.setUseBlock(net.minecraftforge.eventbus.api.Event.Result.DENY); e.setUseItem(net.minecraftforge.eventbus.api.Event.Result.DENY); e.setCanceled(true); }
    }
}

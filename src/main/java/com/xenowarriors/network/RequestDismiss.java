package com.xenowarriors.network;

import com.xenowarriors.XenoWarriors;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * A player answering a request card's question with a command (the chat message's [Accept] / [Decline] buttons, or
 * typing it): the card on their screen goes. Before, it stayed up until it timed out. The client only removes a card
 * whose accept or decline command is exactly this one.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RequestDismiss {
    private RequestDismiss() {}

    @SubscribeEvent
    public static void onCommand(CommandEvent event) {
        var src = event.getParseResults().getContext().getSource();
        if (!(src.getEntity() instanceof ServerPlayer p)) return;
        String in = event.getParseResults().getReader().getString().trim();
        if (in.startsWith("/")) in = in.substring(1);
        if (!in.startsWith("xeno ") && !in.startsWith("xenobot ") && !in.equals("xeno")) return;   // (every request card answers with one of these)
        RequestPopupPacket.dismiss(p, "/" + in.replaceAll("\\s+", " "));
    }
}

package com.xenowarriors.client.fox;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.client.ClientCharacterData;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.animal.Fox;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Client side of Mikiko's foxes: which foxes are mine, who carries a fox on their back, the naming screen. */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class FoxClient {
    private static final Map<UUID, String> MINE = new HashMap<>();
    private static final Map<UUID, Integer> BACK = new HashMap<>();

    private FoxClient() {}

    public static void sync(Map<UUID, String> foxes, UUID prompt) {
        MINE.clear();
        MINE.putAll(foxes);
        if (prompt != null) Minecraft.getInstance().setScreen(new FoxNameScreen(prompt, MINE.getOrDefault(prompt, "Fox"), true));
    }

    public static void setBack(UUID player, int variant) {
        if (variant < 0) BACK.remove(player);
        else BACK.put(player, variant);
    }

    /** The fox variant on this player's back, or -1. */
    public static int back(UUID player) {
        return BACK.getOrDefault(player, -1);
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        MINE.clear();
        BACK.clear();
    }

    /** Mirrors the server so the click "lands" (no eating the rabbit by accident) and opens the naming screen. */
    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (!event.getLevel().isClientSide || !(event.getTarget() instanceof Fox fox)) return;
        ItemStack stack = event.getItemStack();
        boolean rabbit = stack.is(Items.RABBIT) || stack.is(Items.COOKED_RABBIT);
        if (MINE.containsKey(fox.getUUID())) {
            // (a plain right-click shows the stat card - the server sends the numbers)
        } else if (!(rabbit && ClientCharacterData.get(event.getEntity()) == CharacterType.MIKIKO
                && fox.getCustomName() == null)) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }
}

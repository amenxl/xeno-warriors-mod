package com.xenowarriors.client.story;

import com.xenowarriors.XenoWarriors;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** With DragonMineZ: a "Xeno Warriors Story" button on its quest / saga screen, next to its own sagas. */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class StoryClientEvents {
    private StoryClientEvents() {}

    public static final String DMZ_QUESTS = "com.dragonminez.client.gui.character.QuestTreeScreen";
    /** (Tests) the button was put on DragonMineZ's quest screen. */
    public static boolean dmzButton;

    @SubscribeEvent
    public static void onInit(ScreenEvent.Init.Post e) {
        if (!e.getScreen().getClass().getName().equals(DMZ_QUESTS)) return;
        int w = 132, x = e.getScreen().width - w - 6, y = e.getScreen().height - 26;
        e.addListener(Button.builder(Component.literal("★ Xeno Warriors Story"), b -> StoryClient.open())
                .bounds(x, y, w, 20).tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("Season 1 Story Mode: Rhalix, Mikiko, the Sky Rebellion and the Government."))).build());
        dmzButton = true;
    }
}

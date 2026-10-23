package com.xenowarriors.client;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * When your own body is drawn in first person (DragonMineZ's first-person body), the camera sits inside your head -
 * so the head, its hair and face aren't drawn for you then (you'd be looking at the inside of them).
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class FirstPersonHead {
    private static boolean hidden;

    private FirstPersonHead() {}

    /** The head is hidden for this draw (the detail layer skips hair and face too). */
    public static boolean hidden() {
        return hidden;
    }

    public static boolean ownFirstPerson(AbstractClientPlayer p) {
        Minecraft mc = Minecraft.getInstance();
        return p == mc.player && mc.options.getCameraType().isFirstPerson() && mc.getCameraEntity() == p
                && !ClientCharacterData.isPreviewing() && ClientCharacterData.get(p) != CharacterType.NONE;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPre(RenderPlayerEvent.Pre event) {
        if (!(event.getEntity() instanceof AbstractClientPlayer p) || !ownFirstPerson(p)) return;
        var m = event.getRenderer().getModel();
        m.head.visible = false;
        m.hat.visible = false;
        hidden = true;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onPost(RenderPlayerEvent.Post event) {
        if (!hidden) return;
        var m = event.getRenderer().getModel();
        m.head.visible = true;
        m.hat.visible = true;
        hidden = false;
    }
}

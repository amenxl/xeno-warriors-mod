package com.xenowarriors.client.menu;

import com.xenowarriors.XenoWarriors;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Getting hit while in the menus (the M menu and everything it opens): after 4 hits you're knocked out of it, so you
 * can't hide in a menu while something beats on you. The count resets when the menu closes.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class MenuInterrupt {
    public static final int HITS = 4;
    private static int hits, lastHurt;

    private MenuInterrupt() {}

    /** The menu screens this applies to. */
    public static boolean menu(Screen s) {
        return s instanceof MainMenuScreen || s instanceof CharacterCustomScreen || s instanceof PetsScreen
                || s instanceof com.xenowarriors.client.PlayerStatsScreen || s instanceof com.xenowarriors.client.party.PartySetupScreen
                || s instanceof com.xenowarriors.client.SkillLoadoutScreen || s instanceof com.xenowarriors.client.KaGuideScreen;
    }

    /** Hits taken so far in this menu visit (for the warning on screen). */
    public static int hits() { return hits; }

    /** A red "under attack" warning at the top of the menu while you're being hit. */
    public static void warning(net.minecraft.client.gui.GuiGraphics g, int width, int height) {
        if (hits <= 0) return;
        var font = Minecraft.getInstance().font;
        String s = "UNDER ATTACK!  " + hits + " / " + HITS;
        float k = Math.max(1F, height / 240F);
        int tw = Math.round(font.width(s) * k);
        g.pose().pushPose();
        g.pose().translate(0, 0, 500);
        boolean flash = (System.currentTimeMillis() / 250) % 2 == 0;
        g.fill(width / 2 - tw / 2 - 6, Math.round(4 * k), width / 2 + tw / 2 + 6, Math.round(16 * k), flash ? 0xE0A01818 : 0xE0601010);
        g.pose().translate(width / 2F - tw / 2F, 6.5F * k, 0);
        g.pose().scale(k, k, 1F);
        g.drawString(font, s, 0, 0, 0xFFFFFF, true);
        g.pose().popPose();
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        var p = mc.player;
        if (p == null || !menu(mc.screen)) { hits = 0; lastHurt = p == null ? 0 : p.hurtTime; return; }
        // a new hit: the hurt timer jumps back up
        if (p.hurtTime > lastHurt && p.hurtTime >= p.hurtDuration - 1) {
            hits++;
            if (hits >= HITS) {
                hits = 0;
                mc.setScreen(null);
                p.displayClientMessage(Component.literal("You were knocked out of the menu!").withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
            }
        }
        lastHurt = p.hurtTime;
    }
}

package com.xenowarriors.client;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.network.ModNetwork;
import com.xenowarriors.network.ToggleActionPacket;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The transform key: tap it to transform (as before), hold it to open the toggle wheel of transformations, buffs and
 * passives (ToggleWheelScreen). And the right+left click skill: hold right click, tap left click - the left click
 * fires the skill instead of a swing, and the block (or whatever right click was doing) stops.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class TransformKeyClient {
    private TransformKeyClient() {}

    /** Ticks the key must be held to open the wheel instead of transforming. */
    public static final int HOLD = 6;
    private static int held = -1;
    /** (Tests) right+left clicks sent. */
    public static int rlSent;

    /** From the client tick (ClientEvents). */
    public static void keyTick(Minecraft mc) {
        boolean clicked = false;
        while (ModKeyMappings.FOX_KING.consumeClick()) clicked = true;
        boolean down = mc.player != null && mc.screen == null && ModKeyMappings.FOX_KING.isDown() && ClientCharacterData.getLocal() != CharacterType.NONE;
        if (!down && held == -1 && clicked && mc.screen == null) { tap(mc); return; }   // (pressed and let go between two ticks)
        if (down) {
            if (held < 0) held = 0;
            else if (held >= 0 && ++held == HOLD) { mc.setScreen(new ToggleWheelScreen(false)); held = -2; }   // held: the wheel
            return;
        }
        if (held >= 0 && held < HOLD) tap(mc);                                    // let go quickly: transform
        if (!(mc.screen instanceof ToggleWheelScreen)) held = -1;
    }

    private static void tap(Minecraft mc) {
        CharacterType tk = ClientCharacterData.getLocal();
        if (mc.player != null && (tk == CharacterType.MIKIKO || tk == CharacterType.RHALIX || tk == CharacterType.KILL || tk == CharacterType.LEXY || tk == CharacterType.YOU || tk == CharacterType.ULTIMA)
                && !UltimateCutscene.active())
            ModNetwork.sendToServer(new com.xenowarriors.network.FoxKingTogglePacket());
    }

    /** Has a right+left click skill picked (Mikiko, Rhalix). */
    private static boolean hasRl() {
        CharacterType t = ClientCharacterData.getLocal();
        if (com.xenowarriors.toggle.Toggles.rlChoices(t).isEmpty()) return false;
        var rl = com.xenowarriors.network.TogglesPacket.CLIENT;
        return rl.rl().isEmpty() || !rl.rlSelected().isEmpty();                  // (not heard yet: assume their default one)
    }

    /** Left click while right click is held: the right+left click skill instead of a swing. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onClick(InputEvent.InteractionKeyMappingTriggered e) {
        Minecraft mc = Minecraft.getInstance();
        if (!e.isAttack() || mc.player == null || mc.screen != null || !mc.options.keyUse.isDown() || !hasRl() || UltimateCutscene.active()) return;
        e.setCanceled(true);
        e.setSwingHand(false);
        if (mc.player.isUsingItem()) { mc.gameMode.releaseUsingItem(mc.player); }
        ModNetwork.sendToServer(new ToggleActionPacket(ToggleActionPacket.FIRE_RL, ""));
        rlSent++;
    }

    /**
     * Right+left click while something's in use (a shield up, food...): the game swallows the left click then, so it's
     * caught here first, before the game's own key handling.
     */
    @SubscribeEvent
    public static void onTickStart(net.minecraftforge.event.TickEvent.ClientTickEvent e) {
        if (e.phase != net.minecraftforge.event.TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null || mc.gameMode == null || !mc.options.keyUse.isDown() || !hasRl() || UltimateCutscene.active()) return;
        boolean fire = false;
        while (mc.options.keyAttack.consumeClick()) fire = true;
        if (!fire) return;
        if (mc.player.isUsingItem()) mc.gameMode.releaseUsingItem(mc.player);
        ModNetwork.sendToServer(new ToggleActionPacket(ToggleActionPacket.FIRE_RL, ""));
        rlSent++;
    }

    /** Right+left click: also ask for the picked skill once in a while so hasRl() is right. */
    public static void ensureKnown(Minecraft mc) {
        if (mc.player != null && mc.player.tickCount % 200 == 37 && !com.xenowarriors.toggle.Toggles.rlChoices(ClientCharacterData.getLocal()).isEmpty())
            ModNetwork.sendToServer(new ToggleActionPacket(ToggleActionPacket.REQUEST, ""));
    }
}

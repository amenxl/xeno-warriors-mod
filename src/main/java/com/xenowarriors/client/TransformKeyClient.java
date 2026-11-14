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
 * passives (ToggleWheelScreen). And the middle click skill: the middle mouse button (the pick-block button) fires it.
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

    /** A gun in either hand: right click aims and left click shoots - no right+left click skill then. */
    public static boolean holdingGun(Minecraft mc) {
        return mc.player != null && (com.xenowarriors.item.Guns.isGun(mc.player.getMainHandItem()) || com.xenowarriors.item.Guns.isGun(mc.player.getOffhandItem()));
    }

    /**
     * The middle click skill (it was hold right click + tap left click - the user moved it to the middle mouse button,
     * Minecraft's pick-block button). In creative, pointing at a block, the middle click still picks the block.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onClick(InputEvent.InteractionKeyMappingTriggered e) {
        Minecraft mc = Minecraft.getInstance();
        if (!e.isPickBlock() || mc.player == null || mc.screen != null || !hasRl() || UltimateCutscene.active() || holdingGun(mc)) return;
        if (mc.player.isCreative() && mc.hitResult != null && mc.hitResult.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) return;
        e.setCanceled(true);
        e.setSwingHand(false);
        if (mc.player.isUsingItem()) { mc.gameMode.releaseUsingItem(mc.player); }
        ModNetwork.sendToServer(new ToggleActionPacket(ToggleActionPacket.FIRE_RL, ""));
        rlSent++;
    }

    /** What the button is called on screens ("Middle Click" unless it's been rebound). */
    public static String buttonName() {
        try {
            return Minecraft.getInstance().options.keyPickItem.getTranslatedKeyMessage().getString();
        } catch (Throwable t) {
            return "Middle Click";
        }
    }

    /** Right+left click: also ask for the picked skill once in a while so hasRl() is right. */
    public static void ensureKnown(Minecraft mc) {
        if (mc.player != null && mc.player.tickCount % 200 == 37 && !com.xenowarriors.toggle.Toggles.rlChoices(ClientCharacterData.getLocal()).isEmpty())
            ModNetwork.sendToServer(new ToggleActionPacket(ToggleActionPacket.REQUEST, ""));
    }
}

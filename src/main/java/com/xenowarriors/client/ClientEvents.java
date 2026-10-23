package com.xenowarriors.client;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.network.ModNetwork;
import com.xenowarriors.network.UseSkillPacket;
import com.xenowarriors.client.screen.CharacterSelectScreen;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientEvents {
    private static boolean pendingSelectScreen = false;

    private ClientEvents() {}

    public static void requestSelectScreen() {
        pendingSelectScreen = true;
    }

    /** Wait until the world has finished loading before showing the screen. */
    /** Swinging the Hekublade at the air still cuts whatever's close in front (the server checks the arc). */
    @SubscribeEvent
    public static void onLeftClickEmpty(net.minecraftforge.event.entity.player.PlayerInteractEvent.LeftClickEmpty event) {
        if (com.xenowarriors.mikiko.Hekublade.isBlade(event.getEntity().getMainHandItem())) {
            ModNetwork.sendToServer(new com.xenowarriors.network.BladeSwingPacket());
        }
    }

    private static int slotBeforeKeys = -1;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.phase == TickEvent.Phase.START) {
            slotBeforeKeys = mc.player != null ? mc.player.getInventory().selected : -1;
            return;
        }
        WindEffects.tick(mc);
        limitRhalixFlight(mc);
        sleepCamera(mc);

        // skill keys (Ctrl + 1-5): holding Control, the number keys don't change the hotbar slot
        if (mc.player != null && slotBeforeKeys >= 0 && ClientCharacterData.getLocal() != CharacterType.NONE
                && ModKeyMappings.ctrlHeld() && mc.player.getInventory().selected != slotBeforeKeys) {
            mc.player.getInventory().selected = slotBeforeKeys;
        }
        for (int slot = 0; slot < ModKeyMappings.SKILLS.length; slot++) {
            while (ModKeyMappings.SKILLS[slot].consumeClick()) {
                if (mc.player != null && ClientCharacterData.getLocal() != CharacterType.NONE && !UltimateCutscene.active()) {
                    ModNetwork.sendToServer(new UseSkillPacket(slot));
                }
            }
        }

        while (ModKeyMappings.LOCK_ON.consumeClick()) LockOn.toggle();
        while (ModKeyMappings.FOX_KING.consumeClick()) {
            if (mc.player != null && ClientCharacterData.getLocal() == CharacterType.MIKIKO && !UltimateCutscene.active()) {
                ModNetwork.sendToServer(new com.xenowarriors.network.FoxKingTogglePacket());
            }
        }
        while (ModKeyMappings.STATS_MENU.consumeClick()) {
            if (mc.player != null && mc.screen == null) PlayerStatsScreen.open();
        }
        while (ModKeyMappings.KARMA_SENSE.consumeClick()) {
            if (mc.player != null && ClientCharacterData.getLocal() != CharacterType.NONE && !UltimateCutscene.active()) {
                ModNetwork.sendToServer(new com.xenowarriors.network.KarmaSensePacket(ClientSettings.senseFriendly()));
            }
        }
        while (ModKeyMappings.SUMMON_BLADE.consumeClick()) { }      // (tap vs hold: BladeChannelClient)
        if (!UltimateCutscene.active()) com.xenowarriors.client.mikiko.BladeChannelClient.keyTick(mc);

        while (ModKeyMappings.HEKUBLADE_REALM.consumeClick()) {
            if (mc.player != null) com.xenowarriors.network.ModNetwork.sendToServer(new com.xenowarriors.network.RealmKeyPacket());
        }
        while (ModKeyMappings.TOGGLE_HOOD.consumeClick()) {
            if (mc.player != null && com.xenowarriors.client.mikiko.HoodClient.hooded(mc.player))
                com.xenowarriors.network.ModNetwork.sendToServer(new com.xenowarriors.network.HoodTogglePacket(
                        !com.xenowarriors.client.mikiko.HoodClient.wantsUp(mc.player)));
        }
        while (ModKeyMappings.TOGGLE_WINGS.consumeClick()) {
            if (mc.player != null && ClientCharacterData.getLocal() == CharacterType.RHALIX && !UltimateCutscene.active()) {
                ModNetwork.sendToServer(new com.xenowarriors.network.ToggleWingsPacket());
            }
        }

        if (!pendingSelectScreen) return;
        if (mc.player != null && mc.level != null && mc.screen == null) {
            pendingSelectScreen = false;
            mc.setScreen(new CharacterSelectScreen());
        }
    }

    private static final java.util.Set<Integer> SQUISHED = new java.util.HashSet<>();

    /** Gravity Compress squashes its target flat and makes it shake. */
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void onRenderLivingPre(net.minecraftforge.client.event.RenderLivingEvent.Pre<?, ?> event) {
        net.minecraft.world.entity.LivingEntity e = event.getEntity();
        float s = ClientSkillState.squish(e.getId());
        if (s <= 0F) return;
        float t = e.tickCount + event.getPartialTick();
        var pose = event.getPoseStack();
        com.xenowarriors.client.PoseGuard.push(pose);
        pose.translate(Math.sin(t * 5.1) * 0.035 * s, 0, Math.cos(t * 4.3) * 0.035 * s);
        pose.scale(1F + 0.14F * s, 1F - 0.24F * s, 1F + 0.14F * s);
        SQUISHED.add(e.getId());
    }

    @SubscribeEvent
    public static void onRenderLivingPost(net.minecraftforge.client.event.RenderLivingEvent.Post<?, ?> event) {
        if (SQUISHED.remove(event.getEntity().getId())) com.xenowarriors.client.PoseGuard.pop(event.getPoseStack());
    }

    private static boolean warnedCeiling;

    /** Rhalix's wings only take him so high: no rising past the cap, and a gentle push back down above it. */
    private static net.minecraft.client.CameraType cameraBeforeSleep;

    /** Going to bed switches to third person (so you can see the sleeping animation), and back on waking up. */
    private static void sleepCamera(Minecraft mc) {
        var p = mc.player;
        boolean sleeping = p != null && p.isSleeping() && ClientCharacterData.getLocal() != CharacterType.NONE;
        if (sleeping && cameraBeforeSleep == null) {
            cameraBeforeSleep = mc.options.getCameraType();
            mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);
        } else if (!sleeping && cameraBeforeSleep != null) {
            mc.options.setCameraType(cameraBeforeSleep);
            cameraBeforeSleep = null;
        }
    }

    private static void limitRhalixFlight(Minecraft mc) {
        var p = mc.player;
        if (p == null || !p.getAbilities().flying || !com.xenowarriors.character.RhalixFlight.canFly(p)
                || UltimateCutscene.active()) {
            warnedCeiling = false;
            return;
        }
        double h = com.xenowarriors.character.RhalixFlight.heightAboveGround(p);
        double max = com.xenowarriors.character.RhalixFlight.maxHeight(p);
        if (h >= max) {
            var v = p.getDeltaMovement();
            p.setDeltaMovement(v.x, Math.min(v.y, h > max + 0.5 ? -0.12 : 0), v.z);
            if (!warnedCeiling) {
                p.displayClientMessage(net.minecraft.network.chat.Component.literal("You can only fly " + Math.round(max)
                        + " blocks high for now - level up to fly higher!").withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE), true);
                warnedCeiling = true;
            }
        } else if (h < max - 3) {
            warnedCeiling = false;
        }
    }

    /** Netsu: Mikiko is wreathed in his own flames - no burning overlay covering his screen. */
    @SubscribeEvent
    public static void onScreenEffect(net.minecraftforge.client.event.RenderBlockScreenEffectEvent event) {
        if (event.getOverlayType() == net.minecraftforge.client.event.RenderBlockScreenEffectEvent.OverlayType.FIRE
                && ClientSkillState.netsuActive(event.getPlayer().getId())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        pendingSelectScreen = false;
        ClientCharacterData.clear();
        ClientSkillState.clear();
        FlightTracker.clear();
    }
}

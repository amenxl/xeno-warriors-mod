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
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        WindEffects.tick(mc);
        limitRhalixFlight(mc);

        // skill keys
        for (int slot = 0; slot < ModKeyMappings.SKILLS.length; slot++) {
            while (ModKeyMappings.SKILLS[slot].consumeClick()) {
                if (mc.player != null && ClientCharacterData.getLocal() != CharacterType.NONE && !UltimateCutscene.active()) {
                    ModNetwork.sendToServer(new UseSkillPacket(slot));
                }
            }
        }

        while (ModKeyMappings.LOCK_ON.consumeClick()) LockOn.toggle();
        while (ModKeyMappings.KARMA_SENSE.consumeClick()) {
            if (mc.player != null && ClientCharacterData.getLocal() != CharacterType.NONE && !UltimateCutscene.active()) {
                ModNetwork.sendToServer(new com.xenowarriors.network.KarmaSensePacket());
            }
        }
        while (ModKeyMappings.SUMMON_BLADE.consumeClick()) {
            if (mc.player != null && ClientCharacterData.getLocal() == CharacterType.MIKIKO && !UltimateCutscene.active()) {
                ModNetwork.sendToServer(new com.xenowarriors.network.SummonBladePacket());
            }
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
    @SubscribeEvent
    public static void onRenderLivingPre(net.minecraftforge.client.event.RenderLivingEvent.Pre<?, ?> event) {
        net.minecraft.world.entity.LivingEntity e = event.getEntity();
        float s = ClientSkillState.squish(e.getId());
        if (s <= 0F) return;
        float t = e.tickCount + event.getPartialTick();
        var pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(Math.sin(t * 5.1) * 0.035 * s, 0, Math.cos(t * 4.3) * 0.035 * s);
        pose.scale(1F + 0.14F * s, 1F - 0.24F * s, 1F + 0.14F * s);
        SQUISHED.add(e.getId());
    }

    @SubscribeEvent
    public static void onRenderLivingPost(net.minecraftforge.client.event.RenderLivingEvent.Post<?, ?> event) {
        if (SQUISHED.remove(event.getEntity().getId())) event.getPoseStack().popPose();
    }

    private static boolean warnedCeiling;

    /** Rhalix's wings only take him so high: no rising past the cap, and a gentle push back down above it. */
    private static void limitRhalixFlight(Minecraft mc) {
        var p = mc.player;
        if (p == null || !p.getAbilities().flying || !com.xenowarriors.character.RhalixFlight.canFly(p)
                || UltimateCutscene.active()) {
            warnedCeiling = false;
            return;
        }
        double h = com.xenowarriors.character.RhalixFlight.heightAboveGround(p);
        double max = com.xenowarriors.character.RhalixFlight.MAX_HEIGHT;
        if (h >= max) {
            var v = p.getDeltaMovement();
            p.setDeltaMovement(v.x, Math.min(v.y, h > max + 0.5 ? -0.12 : 0), v.z);
            if (!warnedCeiling) {
                p.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.xenowarriors.flight_ceiling"), true);
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

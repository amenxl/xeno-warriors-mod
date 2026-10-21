package com.xenowarriors.client;

import com.xenowarriors.character.CharacterType;
import com.xenowarriors.client.render.CharacterDetailLayer;
import com.xenowarriors.client.render.KishiDomeRenderer;
import com.xenowarriors.client.screen.CharacterSelectScreen;
import com.xenowarriors.entity.KishiDomeEntity;
import com.xenowarriors.entity.ModEntities;
import com.xenowarriors.network.ModNetwork;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

public class XenoWarriorsClient implements ClientModInitializer {
    private static boolean pendingSelectScreen = false;

    @Override
    public void onInitializeClient() {
        // keys
        for (KeyMapping key : ModKeyMappings.SKILLS) KeyBindingHelper.registerKeyBinding(key);

        // rendering
        EntityRendererRegistry.register(ModEntities.KISHI_DOME, KishiDomeRenderer::new);
        LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, renderer, helper, context) -> {
            if (renderer instanceof PlayerRenderer playerRenderer) {
                helper.register(new CharacterDetailLayer(playerRenderer));
            }
        });
        HudRenderCallback.EVENT.register(new SkillHudOverlay());
        KishiDomeEntity.clientHook = ClientSkillState::markKishi;

        // packets from the server
        ClientPlayNetworking.registerGlobalReceiver(ModNetwork.SYNC_CHARACTER, (client, handler, buf, sender) -> {
            UUID id = buf.readUUID();
            CharacterType type = buf.readEnum(CharacterType.class);
            client.execute(() -> ClientCharacterData.set(id, type));
        });
        ClientPlayNetworking.registerGlobalReceiver(ModNetwork.OPEN_SELECT_SCREEN, (client, handler, buf, sender) ->
                client.execute(() -> pendingSelectScreen = true));
        ClientPlayNetworking.registerGlobalReceiver(ModNetwork.SKILL_COOLDOWN, (client, handler, buf, sender) -> {
            int slot = buf.readVarInt();
            int remaining = buf.readVarInt();
            int total = buf.readVarInt();
            client.execute(() -> ClientSkillState.setCooldown(slot, remaining, total));
        });

        ClientTickEvents.END_CLIENT_TICK.register(XenoWarriorsClient::onClientTick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            pendingSelectScreen = false;
            ClientCharacterData.clear();
            ClientSkillState.clear();
        });
    }

    private static void onClientTick(Minecraft mc) {
        // skill keys
        for (int slot = 0; slot < ModKeyMappings.SKILLS.length; slot++) {
            while (ModKeyMappings.SKILLS[slot].consumeClick()) {
                if (mc.player != null && ClientCharacterData.getLocal() != CharacterType.NONE) {
                    FriendlyByteBuf buf = PacketByteBufs.create();
                    buf.writeVarInt(slot);
                    ClientPlayNetworking.send(ModNetwork.USE_SKILL, buf);
                }
            }
        }
        // wait until the world has loaded before showing the selection screen
        if (pendingSelectScreen && mc.player != null && mc.level != null && mc.screen == null) {
            pendingSelectScreen = false;
            mc.setScreen(new CharacterSelectScreen());
        }
    }
}

package com.xenowarriors.network;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterHolder;
import com.xenowarriors.character.CharacterManager;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.skill.SkillManager;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * All packets. Client -> server: SELECT_CHARACTER, USE_SKILL.
 * Server -> client: SYNC_CHARACTER, OPEN_SELECT_SCREEN, SKILL_COOLDOWN.
 */
public final class ModNetwork {
    public static final ResourceLocation SELECT_CHARACTER = XenoWarriors.id("select_character");
    public static final ResourceLocation USE_SKILL = XenoWarriors.id("use_skill");
    public static final ResourceLocation SYNC_CHARACTER = XenoWarriors.id("sync_character");
    public static final ResourceLocation OPEN_SELECT_SCREEN = XenoWarriors.id("open_select_screen");
    public static final ResourceLocation SKILL_COOLDOWN = XenoWarriors.id("skill_cooldown");

    private ModNetwork() {}

    public static void registerServerReceivers() {
        ServerPlayNetworking.registerGlobalReceiver(SELECT_CHARACTER, (server, player, handler, buf, sender) -> {
            CharacterType type = buf.readEnum(CharacterType.class);
            server.execute(() -> {
                if (type != CharacterType.NONE) CharacterManager.setCharacter(player, type);
            });
        });
        ServerPlayNetworking.registerGlobalReceiver(USE_SKILL, (server, player, handler, buf, sender) -> {
            int slot = buf.readVarInt();
            server.execute(() -> SkillManager.useSkill(player, slot));
        });
    }

    private static FriendlyByteBuf characterBuf(ServerPlayer player) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeUUID(player.getUUID());
        buf.writeEnum(CharacterHolder.getType(player));
        return buf;
    }

    /** Tell one viewer which character {@code target} is. */
    public static void sendCharacter(ServerPlayer target, ServerPlayer viewer) {
        ServerPlayNetworking.send(viewer, SYNC_CHARACTER, characterBuf(target));
    }

    public static void sendCharacterToTrackingAndSelf(ServerPlayer player) {
        sendCharacter(player, player);
        for (ServerPlayer viewer : PlayerLookup.tracking(player)) {
            if (viewer != player) sendCharacter(player, viewer);
        }
    }

    public static void sendOpenSelectScreen(ServerPlayer player) {
        ServerPlayNetworking.send(player, OPEN_SELECT_SCREEN, PacketByteBufs.empty());
    }

    public static void sendCooldown(ServerPlayer player, int slot, int remaining, int total) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(slot);
        buf.writeVarInt(remaining);
        buf.writeVarInt(total);
        ServerPlayNetworking.send(player, SKILL_COOLDOWN, buf);
    }
}

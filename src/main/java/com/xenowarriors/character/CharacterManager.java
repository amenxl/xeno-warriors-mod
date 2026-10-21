package com.xenowarriors.character;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.xenowarriors.network.ModNetwork;
import com.xenowarriors.skill.SkillManager;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Server-side logic: choosing a character, syncing it to clients, and the /xeno command. */
public final class CharacterManager {
    private CharacterManager() {}

    public static void setCharacter(ServerPlayer player, CharacterType type) {
        CharacterHolder.get(player).setType(type);
        syncToTrackingAndSelf(player);
        SkillManager.resetCooldowns(player);
        if (type != CharacterType.NONE) {
            player.sendSystemMessage(Component.translatable("message.xenowarriors.chosen", type.getDisplayName())
                    .withStyle(s -> s.withColor(type.getColor() & 0xFFFFFF)));
        }
    }

    public static void syncToTrackingAndSelf(ServerPlayer player) {
        ModNetwork.sendCharacterToTrackingAndSelf(player);
    }

    public static void openSelectScreen(ServerPlayer player) {
        ModNetwork.sendOpenSelectScreen(player);
    }

    public static void registerEvents() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.player;
            syncToTrackingAndSelf(player);
            if (CharacterHolder.getType(player) == CharacterType.NONE) {
                openSelectScreen(player);
            }
        });
        // keep the chosen character after death and when returning from the End
        ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) ->
                CharacterHolder.get(newPlayer).copyFrom(CharacterHolder.get(oldPlayer)));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> syncToTrackingAndSelf(newPlayer));
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) ->
                syncToTrackingAndSelf(player));
        // when a player comes into view of another player, tell the viewer which character they are
        EntityTrackingEvents.START_TRACKING.register((tracked, viewer) -> {
            if (tracked instanceof ServerPlayer target) {
                ModNetwork.sendCharacter(target, viewer);
            }
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            LiteralArgumentBuilder<CommandSourceStack> set = Commands.literal("set").requires(s -> s.hasPermission(2));
            RequiredArgumentBuilder<CommandSourceStack, EntitySelector> targets =
                    Commands.argument("targets", EntityArgument.players());
            for (CharacterType type : CharacterType.PLAYABLE) {
                targets.then(Commands.literal(type.getId()).executes(ctx -> {
                    var players = EntityArgument.getPlayers(ctx, "targets");
                    for (ServerPlayer p : players) {
                        setCharacter(p, type);
                        ctx.getSource().sendSuccess(() -> Component.translatable(
                                "commands.xenowarriors.set.success", p.getDisplayName(), type.getDisplayName()), true);
                    }
                    return players.size();
                }));
            }
            set.then(targets);

            dispatcher.register(Commands.literal("xeno")
                    .then(Commands.literal("choose").executes(ctx -> {
                        openSelectScreen(ctx.getSource().getPlayerOrException());
                        return 1;
                    }))
                    .then(set)
                    .then(Commands.literal("reset").requires(s -> s.hasPermission(2))
                            .then(Commands.argument("targets", EntityArgument.players()).executes(ctx -> {
                                var players = EntityArgument.getPlayers(ctx, "targets");
                                for (ServerPlayer p : players) {
                                    setCharacter(p, CharacterType.NONE);
                                    openSelectScreen(p);
                                    ctx.getSource().sendSuccess(() -> Component.translatable(
                                            "commands.xenowarriors.reset.success", p.getDisplayName()), true);
                                }
                                return players.size();
                            }))));
        });
    }
}

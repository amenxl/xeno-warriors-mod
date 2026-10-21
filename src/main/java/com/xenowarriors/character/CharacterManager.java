package com.xenowarriors.character;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.network.ModNetwork;
import com.xenowarriors.network.OpenSelectScreenPacket;
import com.xenowarriors.network.SyncCharacterPacket;
import com.xenowarriors.skill.SkillManager;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Server-side logic: choosing a character, syncing it to clients, and the /xeno command. */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CharacterManager {
    private CharacterManager() {}

    public static void setCharacter(ServerPlayer player, CharacterType type) {
        player.getCapability(ModCapabilities.PLAYER_CHARACTER).ifPresent(data -> data.setType(type));
        syncToTrackingAndSelf(player);
        RhalixWings.sync(player, false);
        SkillManager.resetCooldowns(player);
        if (type != CharacterType.NONE) {
            player.sendSystemMessage(Component.translatable("message.xenowarriors.chosen", type.getDisplayName())
                    .withStyle(s -> s.withColor(type.getColor() & 0xFFFFFF)));
        }
    }

    public static void syncToTrackingAndSelf(ServerPlayer player) {
        ModNetwork.sendToTrackingAndSelf(new SyncCharacterPacket(player.getUUID(), ModCapabilities.getType(player)), player);
    }

    public static void openSelectScreen(ServerPlayer player) {
        ModNetwork.sendToPlayer(new OpenSelectScreenPacket(), player);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            syncToTrackingAndSelf(player);
            if (ModCapabilities.getType(player) == CharacterType.NONE) {
                openSelectScreen(player);
            }
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) syncToTrackingAndSelf(player);
    }

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) syncToTrackingAndSelf(player);
    }

    /** When a player comes into view of another player, tell the viewer which character they are. */
    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof ServerPlayer target && event.getEntity() instanceof ServerPlayer viewer) {
            ModNetwork.sendToPlayer(new SyncCharacterPacket(target.getUUID(), ModCapabilities.getType(target)), viewer);
        }
    }

    /**
     * /xeno choose                     - reopen the selection screen (anyone)
     * /xeno set <players> <character>  - set a character (ops only)
     * /xeno reset <players>            - clear a character so the screen shows again (ops only)
     */
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> set = Commands.literal("set").requires(s -> s.hasPermission(2));
        var targets = Commands.argument("targets", EntityArgument.players());
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

        event.getDispatcher().register(Commands.literal("xeno")
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
    }
}

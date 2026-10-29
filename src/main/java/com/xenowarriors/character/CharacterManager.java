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
        com.xenowarriors.kaeru.KaeruSoul.forceOut(player);               // (Kaeru inside a blade would be stuck a spectator)
        player.getCapability(ModCapabilities.PLAYER_CHARACTER).ifPresent(data -> {
            CharacterInventories.switchTo(player, data.key(), data.key(type));   // each character has its own inventory

            data.switchStats(data.getType(), type);           // every character keeps its own level
            data.setType(type);
        });
        com.xenowarriors.stats.Stats.reapply(player);
        com.xenowarriors.stats.Stats.syncAll(player);
        syncToTrackingAndSelf(player);
        RhalixWings.sync(player, false);
        SkillManager.resetCooldowns(player);
        if (type != CharacterType.NONE) {
            player.sendSystemMessage(Component.translatable("message.xenowarriors.chosen", Component.literal(com.xenowarriors.you.You.name(player)))
                    .withStyle(s -> s.withColor(com.xenowarriors.you.You.color(player) & 0xFFFFFF)));
        }
    }

    /** Play one of their You characters (slot 0-9; it must exist). */
    public static boolean setYou(ServerPlayer player, int slot) {
        var d = player.getCapability(ModCapabilities.PLAYER_CHARACTER).orElse(null);
        if (d == null || slot < 0 || slot >= com.xenowarriors.you.YouSlot.SLOTS || !d.youSlots[slot].exists()) return false;
        com.xenowarriors.kaeru.KaeruSoul.forceOut(player);
        String from = d.key();
        CharacterInventories.switchTo(player, from, PlayerCharacter.key(CharacterType.YOU, slot));
        d.youSlot = slot;
        d.switchStats(from, d.key(CharacterType.YOU));
        d.setType(CharacterType.YOU);
        com.xenowarriors.stats.Stats.reapply(player);
        com.xenowarriors.stats.Stats.syncAll(player);
        syncToTrackingAndSelf(player);
        RhalixWings.sync(player, false);
        SkillManager.resetCooldowns(player);
        com.xenowarriors.you.YouRaces.sync(player);
        player.sendSystemMessage(Component.translatable("message.xenowarriors.chosen", Component.literal(d.you().name))
                .withStyle(s -> s.withColor(d.you().color())));
        return true;
    }

    public static void syncToTrackingAndSelf(ServerPlayer player) {
        ModNetwork.sendToTrackingAndSelf(SyncCharacterPacket.of(player), player);
    }

    public static int outfit(net.minecraft.world.entity.player.Player p) {
        return p.getCapability(ModCapabilities.PLAYER_CHARACTER).map(d -> d.outfit).orElse(0);
    }

    public static void openSelectScreen(ServerPlayer player) {
        ModNetwork.sendToPlayer(new OpenSelectScreenPacket(), player);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // wearing an outfit from a season that's locked in this world: back to the default look
            player.getCapability(ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> {
                if (!Seasons.unlocked(player.server, ModCapabilities.getType(player).outfitSeason(d.outfit))) d.outfit = 0;
            });
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

    /** Out of spectator mode (a Journey knock-out, say): the client draws you again, so make sure it knows who you are. */
    @SubscribeEvent
    public static void onGameMode(net.minecraftforge.event.entity.player.PlayerEvent.PlayerChangeGameModeEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) player.server.execute(() -> syncToTrackingAndSelf(player));
    }

    /** When a player comes into view of another player, tell the viewer which character they are. */
    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof ServerPlayer target && event.getEntity() instanceof ServerPlayer viewer) {
            ModNetwork.sendToPlayer(SyncCharacterPacket.of(target), viewer);
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

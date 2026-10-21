package com.xenowarriors.character;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.xenowarriors.XenoWarriors;
import com.xenowarriors.fox.FoxLevels;
import com.xenowarriors.fox.FoxPets;
import com.xenowarriors.karma.Karma;
import com.xenowarriors.skill.SkillManager;
import com.xenowarriors.stats.Stat;
import com.xenowarriors.stats.Stats;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Collection;
import java.util.List;

/**
 * More /xeno commands:
 *   help, info [player], wings, blade, foxes, fox call|heal|release|setlevel, karma, stat, cooldowns
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class XenoCommands {
    private XenoCommands() {}

    private static void say(CommandContext<CommandSourceStack> ctx, Component msg) {
        ctx.getSource().sendSuccess(() -> msg, false);
    }

    private static MutableComponent gold(String s) {
        return Component.literal(s).withStyle(ChatFormatting.GOLD);
    }

    private static MutableComponent gray(String s) {
        return Component.literal(s).withStyle(ChatFormatting.GRAY);
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("xeno");

        root.then(Commands.literal("help").executes(ctx -> {
            String[][] lines = {
                    {"/xeno choose", "pick your warrior"},
                    {"/xeno info [player]", "character, karma and stat levels"},
                    {"/xeno wings", "Rhalix: take out / put away your wings"},
                    {"/xeno blade", "Mikiko: summon / put away the Hekublade"},
                    {"/xeno foxes", "Mikiko: list your foxes"},
                    {"/xeno fox call", "call your foxes (like the whistle)"},
                    {"/xeno fox release <name>", "let a fox go back to the wild"},
                    {"/xeno fox heal", "heal your foxes (op)"},
                    {"/xeno fox setlevel <level> [name]", "set fox levels (op)"},
                    {"/xeno party invite|accept|decline|leave|kick|list", "parties (or right-click a player)"},
                    {"/xeno duel challenge <player>|accept|decline|forfeit", "PvP battles"},
                    {"/xeno giveup", "when downed: stop waiting for a revive"},
                    {"/xeno karma set|add <amount> [players]", "change Karma (op)"},
                    {"/xeno stat <speed|attack|defense> <level> [players]", "set a stat level (op)"},
                    {"/xeno cooldowns [players]", "reset skill cooldowns (op)"},
                    {"/xeno set <players> <character>", "set a character (op)"},
                    {"/xeno reset <players>", "clear a character (op)"},
            };
            say(ctx, gold("Xeno Warriors commands:"));
            for (String[] l : lines) say(ctx, Component.literal(l[0]).withStyle(ChatFormatting.YELLOW).append(gray("  " + l[1])));
            return lines.length;
        }));

        root.then(Commands.literal("info")
                .executes(ctx -> info(ctx, ctx.getSource().getPlayerOrException()))
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> info(ctx, EntityArgument.getPlayer(ctx, "player")))));

        root.then(Commands.literal("wings").executes(ctx -> {
            ServerPlayer p = ctx.getSource().getPlayerOrException();
            if (ModCapabilities.getType(p) != CharacterType.RHALIX) {
                ctx.getSource().sendFailure(Component.literal("Only Rhalix has wings."));
                return 0;
            }
            RhalixWings.toggle(p);
            return 1;
        }));

        root.then(Commands.literal("blade").executes(ctx -> {
            ServerPlayer p = ctx.getSource().getPlayerOrException();
            if (ModCapabilities.getType(p) != CharacterType.MIKIKO) {
                ctx.getSource().sendFailure(Component.literal("Only Mikiko can summon the Hekublade."));
                return 0;
            }
            com.xenowarriors.mikiko.Hekublade.toggle(p);
            return 1;
        }));

        root.then(Commands.literal("foxes").executes(ctx -> {
            ServerPlayer p = ctx.getSource().getPlayerOrException();
            List<Component> lines = FoxPets.describe(p);
            if (lines.isEmpty()) {
                say(ctx, gray("You don't have any foxes yet. Tame one with rabbit meat!"));
                return 0;
            }
            say(ctx, gold("Your foxes (" + lines.size() + "/" + FoxPets.MAX_FOXES + "):"));
            for (Component l : lines) say(ctx, l);
            return lines.size();
        }));

        root.then(Commands.literal("fox")
                .then(Commands.literal("call").executes(ctx -> {
                    FoxPets.whistle(ctx.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("release").then(Commands.argument("name", StringArgumentType.greedyString()).executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    String name = StringArgumentType.getString(ctx, "name");
                    if (!FoxPets.release(p, name)) {
                        ctx.getSource().sendFailure(Component.literal("You don't have a fox called " + name + "."));
                        return 0;
                    }
                    say(ctx, gold(name + " runs off back into the wild. Goodbye!"));
                    return 1;
                })))
                .then(Commands.literal("heal").requires(s -> s.hasPermission(2)).executes(ctx -> {
                    int n = FoxPets.heal(ctx.getSource().getPlayerOrException());
                    say(ctx, gold("Healed " + n + " fox" + (n == 1 ? "" : "es") + "."));
                    return n;
                }))
                .then(Commands.literal("setlevel").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("level", IntegerArgumentType.integer(1, FoxLevels.MAX))
                                .executes(ctx -> foxLevel(ctx, null))
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .executes(ctx -> foxLevel(ctx, StringArgumentType.getString(ctx, "name")))))));

        root.then(Commands.literal("karma").requires(s -> s.hasPermission(2))
                .then(Commands.literal("set").then(Commands.argument("amount", FloatArgumentType.floatArg(0, Karma.MAX))
                        .executes(ctx -> karma(ctx, List.of(ctx.getSource().getPlayerOrException()), true))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(ctx -> karma(ctx, EntityArgument.getPlayers(ctx, "targets"), true)))))
                .then(Commands.literal("add").then(Commands.argument("amount", FloatArgumentType.floatArg(-Karma.MAX, Karma.MAX))
                        .executes(ctx -> karma(ctx, List.of(ctx.getSource().getPlayerOrException()), false))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(ctx -> karma(ctx, EntityArgument.getPlayers(ctx, "targets"), false))))));

        root.then(Commands.literal("party")
                .then(Commands.literal("invite").then(Commands.argument("player", EntityArgument.player()).executes(ctx -> {
                    com.xenowarriors.party.Parties.invite(ctx.getSource().getPlayerOrException(), EntityArgument.getPlayer(ctx, "player"));
                    return 1;
                })))
                .then(Commands.literal("accept").executes(ctx -> {
                    com.xenowarriors.party.Parties.accept(ctx.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("decline").executes(ctx -> {
                    com.xenowarriors.party.Parties.decline(ctx.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("leave").executes(ctx -> {
                    com.xenowarriors.party.Parties.leave(ctx.getSource().getPlayerOrException(), true);
                    return 1;
                }))
                .then(Commands.literal("kick").then(Commands.argument("player", EntityArgument.player()).executes(ctx -> {
                    com.xenowarriors.party.Parties.kick(ctx.getSource().getPlayerOrException(), EntityArgument.getPlayer(ctx, "player"));
                    return 1;
                })))
                .then(Commands.literal("list").executes(ctx -> {
                    var lines = com.xenowarriors.party.Parties.describe(ctx.getSource().getPlayerOrException());
                    if (lines.isEmpty()) say(ctx, gray("You're not in a party. Right-click a player to invite them."));
                    else say(ctx, gold("Your party:"));
                    for (Component l : lines) say(ctx, l);
                    return lines.size();
                })));

        root.then(Commands.literal("giveup").executes(ctx -> {
            ServerPlayer p = ctx.getSource().getPlayerOrException();
            if (!com.xenowarriors.party.Duels.isDowned(p)) {
                ctx.getSource().sendFailure(Component.literal("You're not downed."));
                return 0;
            }
            com.xenowarriors.party.Duels.bleedOut(p);
            return 1;
        }));

        root.then(Commands.literal("duel")
                .then(Commands.literal("challenge").then(Commands.argument("player", EntityArgument.player()).executes(ctx -> {
                    com.xenowarriors.party.Duels.request(ctx.getSource().getPlayerOrException(), EntityArgument.getPlayer(ctx, "player"));
                    return 1;
                })))
                .then(Commands.literal("accept").executes(ctx -> {
                    com.xenowarriors.party.Duels.accept(ctx.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("decline").executes(ctx -> {
                    com.xenowarriors.party.Duels.decline(ctx.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("forfeit").executes(ctx -> {
                    com.xenowarriors.party.Duels.forfeit(ctx.getSource().getPlayerOrException());
                    return 1;
                })));

        var stat = Commands.literal("stat").requires(s -> s.hasPermission(2));
        for (Stat st : Stat.values()) {
            stat.then(Commands.literal(st.id()).then(Commands.argument("level", IntegerArgumentType.integer(0, Stat.MAX_LEVEL))
                    .executes(ctx -> setStat(ctx, st, List.of(ctx.getSource().getPlayerOrException())))
                    .then(Commands.argument("targets", EntityArgument.players())
                            .executes(ctx -> setStat(ctx, st, EntityArgument.getPlayers(ctx, "targets"))))));
        }
        root.then(stat);

        root.then(Commands.literal("cooldowns").requires(s -> s.hasPermission(2))
                .executes(ctx -> cooldowns(ctx, List.of(ctx.getSource().getPlayerOrException())))
                .then(Commands.argument("targets", EntityArgument.players())
                        .executes(ctx -> cooldowns(ctx, EntityArgument.getPlayers(ctx, "targets")))));

        event.getDispatcher().register(root);
    }

    private static int info(CommandContext<CommandSourceStack> ctx, ServerPlayer p) {
        CharacterType type = ModCapabilities.getType(p);
        say(ctx, gold(p.getName().getString() + " - ").append(type.getDisplayName().copy()
                .withStyle(s -> s.withColor(type.getColor() & 0xFFFFFF))));
        if (type == CharacterType.NONE) return 1;
        say(ctx, gray("Character Level: " + Stats.characterLevel(p) + " / " + Stats.MAX_CHARACTER_LEVEL
                + "  (+" + Stats.bonusHearts(Stats.characterLevel(p)) + " hearts)"));
        say(ctx, gray("Karma: " + Math.round(Karma.get(p)) + " / " + Math.round(Karma.MAX)));
        for (Stat st : Stat.values()) {
            say(ctx, gray(Character.toUpperCase(st.id().charAt(0)) + st.id().substring(1) + ": Lv " + Stats.level(p, st)
                    + " / " + Stat.MAX_LEVEL));
        }
        if (type == CharacterType.RHALIX) say(ctx, gray("Wings: " + (RhalixWings.out(p) ? "out" : "put away")));
        if (type == CharacterType.RHALIX) say(ctx, gray("Winged companions: " + com.xenowarriors.wing.WingPets.count(p) + " / " + com.xenowarriors.wing.WingPets.MAX));
        if (type == CharacterType.MIKIKO) say(ctx, gray("Foxes: " + FoxPets.describe(p).size() + " / " + FoxPets.MAX_FOXES));
        return 1;
    }

    private static int foxLevel(CommandContext<CommandSourceStack> ctx, String name) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        int level = IntegerArgumentType.getInteger(ctx, "level");
        int n = FoxPets.setLevel(p, level, name);
        if (n == 0) {
            ctx.getSource().sendFailure(Component.literal(name == null ? "You don't have any foxes." : "You don't have a fox called " + name + "."));
            return 0;
        }
        say(ctx, gold("Set " + n + " fox" + (n == 1 ? "" : "es") + " to level " + level + "."));
        return n;
    }

    private static int karma(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> players, boolean set) {
        float amount = FloatArgumentType.getFloat(ctx, "amount");
        for (ServerPlayer p : players) {
            Karma.add(p, set ? amount - Karma.get(p) : amount);
            ctx.getSource().sendSuccess(() -> gold(p.getName().getString() + "'s Karma is now " + Math.round(Karma.get(p))), true);
        }
        return players.size();
    }

    private static int setStat(CommandContext<CommandSourceStack> ctx, Stat st, Collection<ServerPlayer> players) {
        int level = IntegerArgumentType.getInteger(ctx, "level");
        for (ServerPlayer p : players) {
            p.getCapability(ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> {
                d.statLevel[st.ordinal()] = level;
                d.statXp[st.ordinal()] = 0;
            });
            Stats.reapply(p);
            ctx.getSource().sendSuccess(() -> gold(p.getName().getString() + "'s " + st.id() + " is now level " + level), true);
        }
        return players.size();
    }

    private static int cooldowns(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> players) {
        for (ServerPlayer p : players) {
            SkillManager.resetCooldowns(p);
            ctx.getSource().sendSuccess(() -> gold("Reset " + p.getName().getString() + "'s skill cooldowns"), true);
        }
        return players.size();
    }
}

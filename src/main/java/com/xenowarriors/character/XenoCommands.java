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
 *   help, info [player], wings, blade [realm|plea|answer], foxking, sealed, foxes, fox call|heal|release|setlevel,
 *   party, duel, giveup, karma, stat, level, skill, bladelevel, karmasense, cooldowns
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
                    {"/xeno blade realm", "Mikiko: enter / leave the world inside the Hekublade"},
                    {"/xeno blade plea", "sealed in a Hekublade: ask to be freed"},
                    {"/xeno blade answer <player> yes|no", "Mikiko: free a sealed player, or not"},
                    {"/xeno sealed", "Mikiko: who's sealed in your Hekublade"},
                    {"/xeno foxking", "Mikiko: Fox King mode (same as N)"},
                    {"/xeno foxes", "Mikiko: list your foxes"},
                    {"/xeno fox call", "call your foxes (like the whistle)"},
                    {"/xeno fox release <name>", "let a fox go back to the wild"},
                    {"/xeno fox heal", "heal your foxes (op)"},
                    {"/xeno fox setlevel <level> [name]", "set fox levels (op)"},
                    {"/xeno party invite|accept|decline|leave|kick|list", "parties (or right-click a player)"},
                    {"/xeno duel challenge <player>|accept|decline|forfeit", "PvP battles"},
                    {"/xeno giveup", "when downed: stop waiting for a revive"},
                    {"/xeno karma set|add <amount> [players]", "change Karma (op)"},
                    {"/xeno stat <speed|attack|defense> <level> [players]", "set a stat level, up to " + Stat.MAX_LEVEL + " (op)"},
                    {"/xeno stat <mining|crafting|smelting> <level> [players]", "set a skill, up to " + Stat.MAX_LEVEL + " (op)"},
                    {"/xeno stat hekublade <level> | karmasense <uses> | level <level>", "the other stats (op)"},
                    {"/xeno level <level> [players]", "set Character Level, up to " + Stats.MAX_CHARACTER_LEVEL + " (op)"},
                    {"/xeno skill <mining|crafting|smelting> <level> [players]", "set a skill level (op)"},
                    {"/xeno bladelevel <level> [players]", "set the Hekublade's level (op)"},
                    {"/xeno karmasense <uses> [players]", "set Karma Sensing practice (op)"},
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

        root.then(Commands.literal("blade").then(Commands.literal("plea").executes(ctx -> {
            com.xenowarriors.mikiko.HekubladeRealm.plea(ctx.getSource().getPlayerOrException());
            return 1;
        })).then(Commands.literal("answer").then(Commands.argument("player", EntityArgument.player())
                .then(Commands.literal("yes").executes(ctx -> {
                    com.xenowarriors.mikiko.HekubladeRealm.answer(ctx.getSource().getPlayerOrException(), EntityArgument.getPlayer(ctx, "player"), true);
                    return 1;
                }))
                .then(Commands.literal("no").executes(ctx -> {
                    com.xenowarriors.mikiko.HekubladeRealm.answer(ctx.getSource().getPlayerOrException(), EntityArgument.getPlayer(ctx, "player"), false);
                    return 1;
                })))).then(Commands.literal("realm").executes(ctx -> {
            ServerPlayer p = ctx.getSource().getPlayerOrException();
            if (ModCapabilities.getType(p) != CharacterType.MIKIKO) {
                ctx.getSource().sendFailure(Component.literal("Only Mikiko can enter the Hekublade."));
                return 0;
            }
            com.xenowarriors.mikiko.HekubladeRealm.toggle(p);
            return 1;
        })).executes(ctx -> {
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
                .then(Commands.literal("set").then(Commands.argument("amount", FloatArgumentType.floatArg(0, Karma.MAX * 1.5F))
                        .executes(ctx -> karma(ctx, List.of(ctx.getSource().getPlayerOrException()), true))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(ctx -> karma(ctx, EntityArgument.getPlayers(ctx, "targets"), true)))))
                .then(Commands.literal("add").then(Commands.argument("amount", FloatArgumentType.floatArg(-Karma.MAX * 1.5F, Karma.MAX * 1.5F))
                        .executes(ctx -> karma(ctx, List.of(ctx.getSource().getPlayerOrException()), false))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(ctx -> karma(ctx, EntityArgument.getPlayers(ctx, "targets"), false))))));

        root.then(Commands.literal("phone")
                .then(Commands.literal("answer").executes(ctx -> { com.xenowarriors.item.PhoneCalls.answer(ctx.getSource().getPlayerOrException()); return 1; }))
                .then(Commands.literal("decline").executes(ctx -> { com.xenowarriors.item.PhoneCalls.decline(ctx.getSource().getPlayerOrException()); return 1; }))
                .then(Commands.literal("hangup").executes(ctx -> { com.xenowarriors.item.PhoneCalls.hangUp(ctx.getSource().getPlayerOrException()); return 1; })));
        // KAs: list them, or give one to the NPC you're looking at (for videos and testing)
        root.then(Commands.literal("ka")
                .then(Commands.literal("list").executes(ctx -> {
                    for (com.xenowarriors.ka.Ka k : com.xenowarriors.ka.Ka.values())
                        say(ctx, Component.literal((k.rare ? "[rare] " : "[simple] ") + k.title() + " - manipulates " + k.manipulates + "; price: " + k.cost).withStyle(k.chat()));
                    return 1;
                }))
                .then(Commands.literal("self").requires(src -> src.hasPermission(2))      // testing: give yourself a KA (Ctrl+6)
                        .then(Commands.argument("ka", com.mojang.brigadier.arguments.StringArgumentType.word())
                                .suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(java.util.stream.Stream.concat(
                                        java.util.Arrays.stream(com.xenowarriors.ka.Ka.values()).map(com.xenowarriors.ka.Ka::id), java.util.stream.Stream.of("clear")), b))
                                .executes(ctx -> {
                                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                                    String id = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "ka");
                                    var ka = com.xenowarriors.ka.Ka.byId(id);
                                    if (ka == null && !id.equals("clear")) { ctx.getSource().sendFailure(Component.literal("Unknown KA - see /xeno ka list")); return 0; }
                                    com.xenowarriors.ka.Kas.give(p, ka);
                                    p.getPersistentData().remove("xw_ka_strain");
                                    p.getPersistentData().remove("xw_ka_burn");
                                    say(ctx, ka == null ? gray("Test KA removed.") : Component.literal("Test KA: " + ka.title() + " - use it with Ctrl+6. Price: " + ka.cost + ".").withStyle(ka.chat()));
                                    return 1;
                                })))
                .then(Commands.literal("give").requires(src -> src.hasPermission(2))
                        .then(Commands.argument("ka", com.mojang.brigadier.arguments.StringArgumentType.word())
                                .suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(java.util.Arrays.stream(com.xenowarriors.ka.Ka.values()).map(com.xenowarriors.ka.Ka::id), b))
                                .executes(ctx -> {
                                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                                    var ka = com.xenowarriors.ka.Ka.byId(com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "ka"));
                                    var t = com.xenowarriors.skill.SkillTargeting.aimed(p, 20, 15);
                                    if (ka == null || t == null || t instanceof net.minecraft.world.entity.player.Player) {
                                        ctx.getSource().sendFailure(Component.literal("Look at an NPC or mob and give a KA from /xeno ka list (players can't hold KAs)."));
                                        return 0;
                                    }
                                    com.xenowarriors.ka.Kas.give(t, ka);
                                    say(ctx, Component.literal(t.getName().getString() + " now has KA " + ka.title()).withStyle(ka.chat()));
                                    return 1;
                                }))));

        root.then(Commands.literal("element")
                .then(Commands.literal("list").executes(ctx -> {
                    for (com.xenowarriors.ka.Element e : com.xenowarriors.ka.Element.values()) say(ctx, gray(e.title() + " - " + e.karma + " Karma"));
                    return 1;
                }))
                .then(Commands.literal("give").requires(src -> src.hasPermission(2))
                        .then(Commands.argument("element", com.mojang.brigadier.arguments.StringArgumentType.word())
                                .suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(java.util.Arrays.stream(com.xenowarriors.ka.Element.values()).map(com.xenowarriors.ka.Element::id), b))
                                .executes(ctx -> {
                                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                                    var el = com.xenowarriors.ka.Element.byId(com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "element"));
                                    var t = com.xenowarriors.skill.SkillTargeting.aimed(p, 20, 15);
                                    if (el == null || !(t instanceof net.minecraft.world.entity.Mob)) {
                                        ctx.getSource().sendFailure(Component.literal("Look at an NPC or mob and give an element from /xeno element list."));
                                        return 0;
                                    }
                                    com.xenowarriors.ka.Elements.give(t, el);
                                    say(ctx, gray(t.getName().getString() + " now has the " + el.title() + " element"));
                                    return 1;
                                }))));

        // story seasons: Season 2/3 content stays locked (no spoilers) until unlocked here
        root.then(Commands.literal("lunar").requires(src -> src.hasPermission(2))
                .then(Commands.literal("unlock").executes(ctx -> {
                    net.minecraft.server.level.ServerPlayer sp = ctx.getSource().getPlayerOrException();
                    com.xenowarriors.lunar.LunarForm.setUnlocked(sp, true);
                    say(ctx, gray("Lunar Form unlocked (Rhalix, Season 2, at night or under DragonMineZ's fake moon)."));
                    return 1;
                }))
                .then(Commands.literal("lock").executes(ctx -> {
                    com.xenowarriors.lunar.LunarForm.setUnlocked(ctx.getSource().getPlayerOrException(), false);
                    say(ctx, gray("Lunar Form locked again - pass the Lunar trial to get it back."));
                    return 1;
                })));
        root.then(Commands.literal("season")
                .executes(ctx -> { say(ctx, gray("Story content unlocked up to Season " + Seasons.highest(ctx.getSource().getServer()) + ".")); return 1; })
                .then(Commands.literal("unlock").requires(src -> src.hasPermission(2))
                        .then(Commands.argument("season", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 3)).executes(ctx -> {
                            int n = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "season");
                            Seasons.set(ctx.getSource().getServer(), n);
                            ctx.getSource().getServer().getPlayerList().broadcastSystemMessage(Component.literal("Season " + n + " content unlocked!").withStyle(ChatFormatting.GOLD), false);
                            return 1;
                        })))
                .then(Commands.literal("lock").requires(src -> src.hasPermission(2)).executes(ctx -> {
                    Seasons.set(ctx.getSource().getServer(), 1);
                    say(ctx, gray("Back to Season 1 content only."));
                    return 1;
                })));

        // build a Government facility where you stand (for testing and videos)
        root.then(Commands.literal("build").requires(src -> src.hasPermission(2))
                .then(Commands.literal("gov_facility").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    com.xenowarriors.gov.GovFacilityFeature.build(p.serverLevel(), p.getRandom(), p.blockPosition().offset(0, 0, -12));
                    say(ctx, gray("Government facility built just ahead of you."));
                    return 1;
                })));

        root.then(Commands.literal("giveup").executes(ctx -> {
            boolean ok = com.xenowarriors.party.Duels.giveUp(ctx.getSource().getPlayerOrException());
            if (!ok) ctx.getSource().sendFailure(Component.literal("You're not downed"));
            return ok ? 1 : 0;
        }));
        root.then(Commands.literal("siege").requires(src -> src.hasPermission(2)).executes(ctx -> {
            var p = ctx.getSource().getPlayerOrException();
            var sk = p.serverLevel().getEntitiesOfClass(com.xenowarriors.npc.SkylanderEntity.class, p.getBoundingBox().inflate(128, 256, 128),
                    s -> s.islandHome() != null && !s.guardian()).stream().findFirst().orElse(null);
            boolean ok = sk != null && com.xenowarriors.npc.SkyHaterSieges.start(p.serverLevel(), sk, p);
            ctx.getSource().sendSuccess(() -> Component.literal(ok ? "Sky Hater siege started" : "No sky island with Skylanders nearby (or nowhere to build)"), false);
            return ok ? 1 : 0;
        }));
        root.then(Commands.literal("raid").requires(src -> src.hasPermission(2)).executes(ctx -> {
            boolean ok = com.xenowarriors.npc.SkyHaterRaids.start(ctx.getSource().getPlayerOrException(), true);
            ctx.getSource().sendSuccess(() -> Component.literal(ok ? "Sky Hater raid started" : "A raid is already going on near here"), false);
            return ok ? 1 : 0;
        }));
        root.then(Commands.literal("teamult").then(Commands.literal("join").executes(ctx -> {
            com.xenowarriors.party.TeamUltimate.join(ctx.getSource().getPlayerOrException());
            return 1;
        })));
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
        // the other stats live under /xeno stat too
        for (String sk : new String[]{"mining", "crafting", "smelting"}) {
            int max = sk.equals("mining") ? com.xenowarriors.stats.Mining.MAX_LEVEL : com.xenowarriors.stats.Crafting.MAX_LEVEL;
            stat.then(Commands.literal(sk).then(Commands.argument("level", IntegerArgumentType.integer(0, max))
                    .executes(ctx -> setSkill(ctx, sk, List.of(ctx.getSource().getPlayerOrException())))
                    .then(Commands.argument("targets", EntityArgument.players())
                            .executes(ctx -> setSkill(ctx, sk, EntityArgument.getPlayers(ctx, "targets"))))));
        }
        stat.then(Commands.literal("hekublade").then(Commands.argument("level", IntegerArgumentType.integer(1, com.xenowarriors.mikiko.BladeLevel.MAX))
                .executes(ctx -> setBlade(ctx, List.of(ctx.getSource().getPlayerOrException())))
                .then(Commands.argument("targets", EntityArgument.players())
                        .executes(ctx -> setBlade(ctx, EntityArgument.getPlayers(ctx, "targets"))))));
        stat.then(Commands.literal("karmasense").then(Commands.argument("uses", IntegerArgumentType.integer(0, 100000))
                .executes(ctx -> setSense(ctx, List.of(ctx.getSource().getPlayerOrException())))
                .then(Commands.argument("targets", EntityArgument.players())
                        .executes(ctx -> setSense(ctx, EntityArgument.getPlayers(ctx, "targets"))))));
        stat.then(Commands.literal("level").then(Commands.argument("level", IntegerArgumentType.integer(0, Stats.MAX_CHARACTER_LEVEL))
                .executes(ctx -> setLevel(ctx, List.of(ctx.getSource().getPlayerOrException())))
                .then(Commands.argument("targets", EntityArgument.players())
                        .executes(ctx -> setLevel(ctx, EntityArgument.getPlayers(ctx, "targets"))))));
        root.then(stat);

        root.then(Commands.literal("level").requires(s -> s.hasPermission(2))
                .then(Commands.argument("level", IntegerArgumentType.integer(0, Stats.MAX_CHARACTER_LEVEL))
                        .executes(ctx -> setLevel(ctx, List.of(ctx.getSource().getPlayerOrException())))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(ctx -> setLevel(ctx, EntityArgument.getPlayers(ctx, "targets"))))));

        var skill = Commands.literal("skill").requires(s -> s.hasPermission(2));
        for (String sk : new String[]{"mining", "crafting", "smelting"}) {
            int max = sk.equals("mining") ? com.xenowarriors.stats.Mining.MAX_LEVEL : com.xenowarriors.stats.Crafting.MAX_LEVEL;
            skill.then(Commands.literal(sk).then(Commands.argument("level", IntegerArgumentType.integer(0, max))
                    .executes(ctx -> setSkill(ctx, sk, List.of(ctx.getSource().getPlayerOrException())))
                    .then(Commands.argument("targets", EntityArgument.players())
                            .executes(ctx -> setSkill(ctx, sk, EntityArgument.getPlayers(ctx, "targets"))))));
        }
        root.then(skill);

        root.then(Commands.literal("bladelevel").requires(s -> s.hasPermission(2))
                .then(Commands.argument("level", IntegerArgumentType.integer(1, com.xenowarriors.mikiko.BladeLevel.MAX))
                        .executes(ctx -> setBlade(ctx, List.of(ctx.getSource().getPlayerOrException())))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(ctx -> setBlade(ctx, EntityArgument.getPlayers(ctx, "targets"))))));

        root.then(Commands.literal("karmasense").requires(s -> s.hasPermission(2))
                .then(Commands.argument("uses", IntegerArgumentType.integer(0, 100000))
                        .executes(ctx -> setSense(ctx, List.of(ctx.getSource().getPlayerOrException())))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(ctx -> setSense(ctx, EntityArgument.getPlayers(ctx, "targets"))))));

        root.then(Commands.literal("foxking").executes(ctx -> {
            ServerPlayer p = ctx.getSource().getPlayerOrException();
            if (ModCapabilities.getType(p) != CharacterType.MIKIKO) {
                ctx.getSource().sendFailure(Component.literal("Only Mikiko has Fox King mode."));
                return 0;
            }
            com.xenowarriors.mikiko.FoxKing.toggle(p);
            return 1;
        }));

        root.then(Commands.literal("sealed").executes(ctx -> {
            ServerPlayer p = ctx.getSource().getPlayerOrException();
            var list = p.getCapability(ModCapabilities.PLAYER_CHARACTER).map(d -> d.sealed).orElse(new net.minecraft.nbt.ListTag());
            if (list.isEmpty()) {
                say(ctx, gray("Nothing is sealed in your Hekublade."));
                return 0;
            }
            java.util.Map<String, Integer> counts = new java.util.TreeMap<>();
            for (int i = 0; i < list.size(); i++) counts.merge(list.getCompound(i).getString("name"), 1, Integer::sum);
            say(ctx, gold("Sealed in your Hekublade (" + list.size() + "):"));
            counts.forEach((name, n) -> say(ctx, gray("  " + name + (n > 1 ? " x" + n : ""))));
            return list.size();
        }));

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
        say(ctx, gray("Karma Sensing: " + Math.round(com.xenowarriors.karma.KarmaSensing.radius(com.xenowarriors.karma.KarmaSensing.uses(p)))
                + " blocks, " + com.xenowarriors.karma.KarmaSensing.duration(com.xenowarriors.karma.KarmaSensing.uses(p)) / 20 + "s ("
                + Math.min(com.xenowarriors.karma.KarmaSensing.uses(p), com.xenowarriors.karma.KarmaSensing.MASTERY) + "/"
                + com.xenowarriors.karma.KarmaSensing.MASTERY + " practice)"));
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
        p.getCapability(ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> {
            say(ctx, gray("Mining: Lv " + d.miningLevel + "   Crafting: Lv " + d.craftLevel + "   Smelting: Lv " + d.smeltLevel));
            if (type == CharacterType.MIKIKO) {
                say(ctx, gray("Hekublade: Lv " + d.bladeLevel + " / " + com.xenowarriors.mikiko.BladeLevel.MAX + "   sealed: " + d.sealed.size()));
                say(ctx, gray("Fox King: " + (com.xenowarriors.mikiko.FoxKing.active(p) ? "active" : "ready (N)")));
            }
        });
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

    /** Spread a Character Level evenly over the three stats (the cap = all three maxed). */
    private static int setLevel(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> players) {
        int level = IntegerArgumentType.getInteger(ctx, "level");
        int each = level >= Stats.MAX_CHARACTER_LEVEL ? Stat.MAX_LEVEL : level / 3, extra = level >= Stats.MAX_CHARACTER_LEVEL ? 0 : level % 3;
        for (ServerPlayer p : players) {
            p.getCapability(ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> {
                for (int i = 0; i < 3; i++) {
                    d.statLevel[i] = Math.min(Stat.MAX_LEVEL, each + (i < extra ? 1 : 0));
                    d.statXp[i] = 0;
                }
            });
            Stats.reapply(p);
            ctx.getSource().sendSuccess(() -> gold(p.getName().getString() + " is now Character Level " + Stats.characterLevel(p)), true);
        }
        return players.size();
    }

    private static int setSkill(CommandContext<CommandSourceStack> ctx, String skill, Collection<ServerPlayer> players) {
        int level = IntegerArgumentType.getInteger(ctx, "level");
        for (ServerPlayer p : players) {
            p.getCapability(ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> {
                switch (skill) {
                    case "mining" -> { d.miningLevel = level; d.miningXp = 0; }
                    case "crafting" -> { d.craftLevel = level; d.craftXp = 0; }
                    default -> { d.smeltLevel = level; d.smeltXp = 0; }
                }
            });
            if (skill.equals("mining")) com.xenowarriors.stats.Mining.sync(p);
            ctx.getSource().sendSuccess(() -> gold(p.getName().getString() + "'s " + skill + " is now level " + level), true);
        }
        return players.size();
    }

    private static int setBlade(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> players) {
        int level = IntegerArgumentType.getInteger(ctx, "level");
        for (ServerPlayer p : players) {
            p.getCapability(ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> {
                d.bladeLevel = level;
                d.bladeXp = 0;
                d.realmTicks = com.xenowarriors.mikiko.BladeLevel.maxTicks(level);
            });
            ctx.getSource().sendSuccess(() -> gold(p.getName().getString() + "'s Hekublade is now level " + level), true);
        }
        return players.size();
    }

    private static int setSense(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> players) {
        int uses = IntegerArgumentType.getInteger(ctx, "uses");
        for (ServerPlayer p : players) {
            p.getCapability(ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> d.senseUses = uses);
            ctx.getSource().sendSuccess(() -> gold(p.getName().getString() + "'s Karma Sensing practice is now " + uses), true);
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

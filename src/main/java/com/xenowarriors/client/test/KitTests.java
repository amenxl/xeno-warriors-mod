package com.xenowarriors.client.test;

import com.xenowarriors.bot.PartyBotEntity;
import com.xenowarriors.bot.PartyBots;
import com.xenowarriors.character.CharacterManager;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.character.ModCapabilities;
import com.xenowarriors.client.UiTest;
import com.xenowarriors.network.ModNetwork;
import com.xenowarriors.skill.SkillManager;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;

import java.util.function.Consumer;

/**
 * Character-kit regression checks (focuses starting with "kits", run from UiTest), logged as "XW-TEST kits ...".
 * <ul>
 *   <li>a bot's self-buff skill (Max's Lunar Mind, through its stand-in player) buffs the bot, and the stand-in keeps nothing;</li>
 *   <li>picking the character you already are on the select screen doesn't reset your cooldowns;</li>
 *   <li>downed: Fox King, the Hekublade dimension and skills don't go (and Fox King does once revived);</li>
 *   <li>a You Seedling lying as a seed can't use skills or switch characters.</li>
 * </ul>
 */
public final class KitTests {
    private KitTests() {}

    @FunctionalInterface
    public interface Adder { void add(String shot, Runnable action, int delay); }

    public static boolean handles(String focus) { return focus != null && focus.startsWith("kits"); }

    private static void log(String s) { UiTest.log("kits " + s); }

    private static void server(Consumer<ServerPlayer> f) {
        var s = Minecraft.getInstance().getSingleplayerServer();
        if (s != null) s.execute(() -> {
            ServerPlayer p = s.getPlayerList().getPlayers().isEmpty() ? null : s.getPlayerList().getPlayers().get(0);
            if (p != null) f.accept(p);
        });
    }

    private static long cd(ServerPlayer p, int slot) {
        return p.getCapability(ModCapabilities.PLAYER_CHARACTER).resolve().map(d -> d.cooldownEnd[slot]).orElse(-1L);
    }

    private static PartyBotEntity bot(ServerPlayer p) {
        for (var b : PartyBots.bots(p)) if (b.character() == CharacterType.RHALIX) return b;
        return null;
    }

    public static void build(Minecraft mc, String focus, Adder l) {
        l.add(null, () -> { mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE); mc.getToasts().clear(); mc.setScreen(null); }, 20);
        l.add(null, () -> server(p -> {
            CharacterManager.setCharacter(p, CharacterType.MIKIKO);
            p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            com.xenowarriors.modes.SwitchRules.clearCombat(p);
            PartyBotEntity b = bot(p);
            if (b == null) b = PartyBots.spawn(p, CharacterType.RHALIX, null);
            if (b != null) { b.moveTo(p.getX() + 2, p.getY(), p.getZ(), 0, 0); b.setMode(PartyBotEntity.NEUTRAL); b.removeAllEffects(); }
            if (!com.xenowarriors.mikiko.Hekublade.holding(p)) com.xenowarriors.mikiko.Hekublade.toggle(p);
            com.xenowarriors.karma.Karma.add(p, 200);
            log("setup bot=" + (b != null) + " blade=" + com.xenowarriors.mikiko.Hekublade.holding(p));
        }), 40);

        // a bot's self-buff lands on the bot
        l.add(null, () -> server(p -> {
            PartyBotEntity b = bot(p);
            if (b == null) { log("bot self-buff: no bot FAIL"); return; }
            boolean used = PartyBots.useForTest(b, new com.xenowarriors.skill.sp.MaxSkills.LunarMind());
            int left = PartyBots.standIn(b).getActiveEffects().size();
            boolean ok = used && b.hasEffect(MobEffects.DAMAGE_BOOST) && b.hasEffect(MobEffects.MOVEMENT_SPEED) && left == 0;
            log("bot self-buff: used=" + used + " botStrength=" + b.hasEffect(MobEffects.DAMAGE_BOOST) + " botSpeed=" + b.hasEffect(MobEffects.MOVEMENT_SPEED)
                    + " standInEffects=" + left + (ok ? " OK" : " FAIL"));
        }), 20);

        // re-picking yourself keeps your cooldowns
        long[] until = {0};
        l.add(null, () -> server(p -> {
            p.getCapability(ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> d.cooldownEnd[0] = p.server.overworld().getGameTime() + 4000);
            until[0] = cd(p, 0);
            mc.execute(() -> ModNetwork.CHANNEL.sendToServer(new com.xenowarriors.network.SelectCharacterPacket(CharacterType.MIKIKO)));
        }), 10);
        l.add(null, () -> server(p -> log("repick self: cooldown before=" + until[0] + " after=" + cd(p, 0) + " type=" + ModCapabilities.getType(p).getId()
                + (cd(p, 0) == until[0] && until[0] > 0 ? " OK" : " FAIL"))), 25);

        // downed: no acting
        l.add(null, () -> server(p -> {
            SkillManager.resetCooldowns(p);
            p.hurt(p.damageSources().generic(), 1000F);
            log("downed=" + com.xenowarriors.party.Duels.isDowned(p));
        }), 10);
        l.add("kits_downed", () -> {
            ModNetwork.CHANNEL.sendToServer(new com.xenowarriors.network.FoxKingTogglePacket());
            ModNetwork.CHANNEL.sendToServer(new com.xenowarriors.network.RealmKeyPacket());
            server(p -> SkillManager.useSkill(p, 0));
        }, 10);
        l.add(null, () -> server(p -> {
            boolean fox = com.xenowarriors.mikiko.FoxKing.active(p), realm = com.xenowarriors.mikiko.HekubladeRealm.inRealm(p);
            long c = cd(p, 0);
            log("downed actions: stillDowned=" + com.xenowarriors.party.Duels.isDowned(p) + " foxKing=" + fox + " inRealm=" + realm + " skillCooldown=" + c
                    + (!fox && !realm && c == 0 ? " OK" : " FAIL"));
            com.xenowarriors.party.Duels.foxRevive(p, p);
            com.xenowarriors.karma.Karma.add(p, 200);
        }), 25);
        l.add(null, () -> ModNetwork.CHANNEL.sendToServer(new com.xenowarriors.network.FoxKingTogglePacket()), 10);
        l.add(null, () -> server(p -> {
            boolean fox = com.xenowarriors.mikiko.FoxKing.active(p);
            log("revived control: downed=" + com.xenowarriors.party.Duels.isDowned(p) + " foxKing=" + fox + (fox ? " OK" : " FAIL"));
            com.xenowarriors.mikiko.FoxKing.stop(p, true);
        }), 25);

        // a seed can't act
        l.add(null, () -> server(p -> {
            SkillManager.resetCooldowns(p);
            p.getPersistentData().putBoolean("xw_seed", true);
            SkillManager.useSkill(p, 0);
            String why = com.xenowarriors.modes.SwitchRules.why(p);
            long c = cd(p, 0);
            p.getPersistentData().remove("xw_seed");
            log("seed: skillCooldown=" + c + " switchBlocked=" + why + (c == 0 && why != null && why.contains("seed") ? " OK" : " FAIL"));
        }), 20);
        l.add(null, () -> log("done"), 10);
    }
}

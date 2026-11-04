package com.xenowarriors.client.test;

import com.xenowarriors.character.CharacterType;
import com.xenowarriors.character.ModCapabilities;
import com.xenowarriors.client.UiTest;
import com.xenowarriors.skill.SkillManager;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Consumer;

/**
 * Regression checks from the bug sweep (focuses starting with "sweep", run from UiTest). Results are logged as
 * "XW-TEST sweep ...".
 * <ul>
 *   <li>sweepsuper: every character presses the super-ultimate key with no test KA (the normal case) - nothing may
 *   throw, and the ones with their own KA (Ultima, Max, Logan, Keita, Zane, Kaeru) or a Xeno Blast have to go off
 *   (Kyrie has none: went=false is right for her).</li>
 * </ul>
 */
public final class SweepTests {
    private SweepTests() {}

    @FunctionalInterface
    public interface Adder { void add(String shot, Runnable action, int delay); }

    public static boolean handles(String focus) { return focus != null && focus.startsWith("sweep"); }

    private static void log(String s) { UiTest.log("sweep " + s); }

    private static void server(Consumer<ServerPlayer> f) {
        var s = Minecraft.getInstance().getSingleplayerServer();
        if (s != null) s.execute(() -> {
            ServerPlayer p = s.getPlayerList().getPlayers().isEmpty() ? null : s.getPlayerList().getPlayers().get(0);
            if (p != null) f.accept(p);
        });
    }

    public static void build(Minecraft mc, String focus, Adder l) {
        l.add(null, () -> { mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE); mc.getToasts().clear(); mc.setScreen(null); }, 20);
        superKey(mc, l);
        l.add(null, () -> log("done"), 10);
    }

    private static void superKey(Minecraft mc, Adder l) {
        CharacterType[] all = {CharacterType.ULTIMA, CharacterType.MAX, CharacterType.LOGAN, CharacterType.KEITA, CharacterType.ZANE,
                CharacterType.KAERU, CharacterType.MIKIKO, CharacterType.RHALIX, CharacterType.KYRIE};
        int[] bad = {0};
        for (CharacterType t : all) {
            l.add(null, () -> server(p -> {
                com.xenowarriors.character.CharacterManager.setCharacter(p, t);
                p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
                com.xenowarriors.ka.Kas.give(p, null);
                var at = com.xenowarriors.debug.SkillTestRunner.arena(p);
                var h = net.minecraft.world.entity.EntityType.HUSK.create(p.serverLevel());   // something to aim at
                h.moveTo(at.x, at.y, at.z + 4, 180F, 0F); h.setNoAi(true); h.addTag("xw_sweep");
                p.serverLevel().addFreshEntity(h);
            }), 30);
            l.add("sweep_super_" + t.getId(), () -> server(p -> {
                SkillManager.resetCooldowns(p);
                p.getPersistentData().remove("xw_karma_lock_until");                      // (the last Xeno Blast's drain carries over a character swap)
                if (t == CharacterType.ULTIMA) com.xenowarriors.ultima.UltimaOutput.setTier(p, 3);   // Overload needs 100% output
                com.xenowarriors.karma.Karma.add(p, 200);
                String err = "none";
                try { SkillManager.useSkill(p, SkillManager.SUPER_SLOT); }
                catch (Throwable e) { err = e.toString(); bad[0]++; com.xenowarriors.XenoWarriors.LOGGER.error("sweep super " + t.getId(), e); }
                boolean went = p.getCapability(ModCapabilities.PLAYER_CHARACTER).resolve().map(d -> d.cooldownEnd[SkillManager.SUPER_SLOT] > p.level().getGameTime()).orElse(false);
                log("super " + t.getId() + ": went=" + went + " exception=" + err + (err.equals("none") ? "" : " FAIL"));
            }), 40);
            l.add(null, () -> server(p -> {
                if (com.xenowarriors.kaeru.KaeruChange.mode(p) != com.xenowarriors.kaeru.KaeruChange.NONE) com.xenowarriors.kaeru.KaeruChange.revert(p, true);
                for (var e : p.serverLevel().getEntities((net.minecraft.world.entity.Entity) null, p.getBoundingBox().inflate(40), x -> x.getTags().contains("xw_sweep"))) e.discard();
            }), 20);
        }
        l.add(null, () -> log("super summary: exceptions=" + bad[0] + (bad[0] == 0 ? " OK" : " FAIL")), 5);
    }
}

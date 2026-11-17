package com.xenowarriors.client.test;

import com.xenowarriors.bot.PartyBotEntity;
import com.xenowarriors.bot.PartyBots;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.client.UiTest;
import com.xenowarriors.client.UiTest.Step;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;

import java.util.List;

/**
 * (focus "packvr") Yuri's VR Room: free training with a bot in the party - you, the bot and VR Ultima arrive, the
 * fight runs, taking the goggles off brings everyone back where they were (no drops, nothing left behind); then a
 * challenge that needs more people refuses politely.
 */
final class PackVrTests {
    private PackVrTests() {}

    static void build(Minecraft mc, List<Step> l) {
        double[] start = new double[3];
        l.add(new Step(null, () -> UiTest.character(CharacterType.MIKIKO), 30));
        l.add(new Step(null, () -> PackTests.server(p -> {
            PackTests.arena(p); PackTests.clear(p);
            for (PartyBotEntity b : PartyBots.bots(p)) b.discard();
            p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE, 20 * 120, 4, false, false));
            PartyBots.spawn(p, CharacterType.RHALIX, null);
            start[0] = p.getX(); start[1] = p.getY(); start[2] = p.getZ();
        }), 40));
        l.add(new Step(null, () -> PackTests.server(p -> com.xenowarriors.vr.Vr.action(p, 0, 0)), 60));
        l.add(new Step(null, () -> { mc.setScreen(null); mc.options.hideGui = false; mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); }, 40));
        l.add(new Step("vr_inside", () -> PackTests.server(p -> {
            boolean in = com.xenowarriors.vr.Vr.is(p.level());
            var bots = PartyBots.bots(p);
            boolean botIn = !bots.isEmpty() && bots.stream().allMatch(b -> b.level() == p.level());
            int ultimas = p.serverLevel().getEntitiesOfClass(com.xenowarriors.vr.VrUltimaEntity.class, p.getBoundingBox().inflate(48)).size();
            PackTests.check("vr: free training takes you in", in, "level " + p.level().dimension().location());
            PackTests.check("vr: your bot comes along", botIn, "bots " + bots.size() + " in " + bots.stream().map(b -> b.level().dimension().location().toString()).toList());
            PackTests.check("vr: VR Ultima is there to fight", ultimas > 0, "VR Ultimas " + ultimas);
        }), 200));
        l.add(new Step("vr_fight", () -> {}, 5));
        l.add(new Step(null, () -> PackTests.server(p -> com.xenowarriors.vr.Vr.action(p, 2, 0)), 60));
        l.add(new Step("vr_back", () -> PackTests.server(p -> {
            boolean out = !com.xenowarriors.vr.Vr.is(p.level());
            double d = Math.sqrt(p.distanceToSqr(start[0], start[1], start[2]));
            var bots = PartyBots.bots(p);
            boolean botOut = !bots.isEmpty() && bots.stream().allMatch(b -> b.level() == p.level());
            PackTests.check("vr: taking the goggles off brings you back where you were", out && d < 3, "level " + p.level().dimension().location() + " " + String.format("%.1f", d) + " blocks off");
            PackTests.check("vr: your bot comes back too", botOut, "bots " + bots.size() + " in " + bots.stream().map(b -> b.level().dimension().location().toString()).toList());
            PackTests.check("vr: no VR session left open", !com.xenowarriors.vr.Vr.inVr(p), "still tagged in VR");
        }), 5));
        // a challenge that needs a bigger party: refused, you stay put
        l.add(new Step(null, () -> PackTests.server(p -> {
            int full = -1;
            for (int i = 0; i < com.xenowarriors.vr.Vr.CHALLENGES.length; i++) if (com.xenowarriors.vr.Vr.CHALLENGES[i].partyMin() >= 4) { full = i; break; }
            if (full >= 0) com.xenowarriors.vr.Vr.action(p, 1, full);
        }), 40));
        l.add(new Step(null, () -> PackTests.server(p -> {
            PackTests.check("vr: a challenge you don't have the party for is refused", !com.xenowarriors.vr.Vr.is(p.level()), "level " + p.level().dimension().location());
            for (PartyBotEntity b : PartyBots.bots(p)) b.discard();
            mc.options.setCameraType(CameraType.FIRST_PERSON);
        }), 5));
    }
}

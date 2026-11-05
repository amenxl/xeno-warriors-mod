package com.xenowarriors.client.test;

import com.xenowarriors.character.CharacterType;
import com.xenowarriors.compat.DmzPerCharacter;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.function.Consumer;

/**
 * UI tests for the dmz6 DragonMineZ work (focus "dmz6..."): fusion with bots, bots summoning Shenron, the Blaster
 * guns, armed Skylanders in DragonMineZ's worlds, the HUD layout with DragonMineZ, bots charging / powering up /
 * transforming, and no grey screen while sensing. Results are logged as "XW-TEST dmz6 ...".
 * Sub-focuses: dmz6hud (HUD + sensing only), dmz6 (everything else, then the HUD).
 */
public final class Dmz6Tests {
    private Dmz6Tests() {}

    @FunctionalInterface
    public interface Adder { void add(String shot, Runnable action, int delay); }

    private static Adder S;
    private static Consumer<Consumer<ServerPlayer>> SERVER;
    private static Consumer<String> LOG;
    private static Consumer<CharacterType> CHAR;

    public static boolean handles(String focus) { return focus != null && focus.startsWith("dmz6"); }

    public static void build(Minecraft mc, String focus, Adder steps, Consumer<Consumer<ServerPlayer>> server, Consumer<CharacterType> character, Consumer<String> log) {
        S = steps; SERVER = server; LOG = log; CHAR = character;
        setup(mc);
        String tag = focus.endsWith("b") ? "before" : "after";
        if (!focus.startsWith("dmz6hud")) Dmz6Features.build(mc, steps, server, log);
        hud(mc, tag);
        step(null, () -> server(p -> log("done")), 20);
    }

    static void log(String s) { LOG.accept("dmz6 " + s); }
    static void server(Consumer<ServerPlayer> f) { SERVER.accept(f); }
    static void step(String shot, Runnable a, int delay) { S.add(shot, a, delay); }

    static void noDmzScreen(Minecraft mc) {
        if (mc.screen != null && mc.screen.getClass().getName().startsWith("com.dragonminez")) mc.setScreen(null);
    }

    static Object call(Object o, String m, Object... args) {
        if (o == null) return null;
        try {
            for (var mm : o.getClass().getMethods()) if (mm.getName().equals(m) && mm.getParameterCount() == args.length) return mm.invoke(o, args);
        } catch (Throwable t) { return "?" + t.getClass().getSimpleName(); }
        return "?nomethod";
    }

    /** The player's DMZ character: this race, made, some stats, a few techniques equipped (so its technique bar shows). */
    static void make(ServerPlayer p, String race, int stats) {
        Object d = DmzPerCharacter.live(p);
        if (d == null) { log("no DMZ data"); return; }
        Object ch = call(d, "getCharacter");
        call(ch, "setRace", race);
        call(call(d, "getStatus"), "setHasCreatedCharacter", true);
        Object st = call(d, "getStats");
        for (String m : new String[]{"setStrength", "setStrikePower", "setResistance", "setVitality", "setKiPower", "setEnergy"}) call(st, m, stats);
        try {
            Object tech = call(d, "getTechniques");
            var reg = (Map<?, ?>) Class.forName("com.dragonminez.common.stats.techniques.PredefinedTechniques").getField("REGISTRY").get(null);
            int i = 0;
            for (var e : reg.entrySet()) {
                if (i >= 4) break;
                call(tech, "unlockTechnique", e.getValue());
                call(tech, "equipTechnique", i++, e.getKey());
            }
            log("equipped " + Arrays.toString((String[]) call(tech, "getEquippedSlots")));
        } catch (Throwable t) { log("equip FAILED " + t); }
        DmzPerCharacter.syncStats(p);
    }

    static void protect(ServerPlayer p) {
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20 * 60 * 30, 10, false, false));
        p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20 * 60 * 30, 5, false, false));
        p.setHealth(p.getMaxHealth());
    }

    static void clearHostiles(ServerPlayer p) {
        for (var m : p.serverLevel().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(64), m -> m instanceof net.minecraft.world.entity.monster.Enemy)) m.discard();
    }

    private static Vec3 AT;
    static Vec3 at(ServerPlayer p) { if (AT == null) AT = com.xenowarriors.debug.SkillTestRunner.arena(p); return AT; }

    private static void setup(Minecraft mc) {
        step(null, () -> { mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE); mc.getToasts().clear(); mc.setScreen(null);
            CHAR.accept(CharacterType.MIKIKO); }, 60);
        step(null, () -> { noDmzScreen(mc); server(p -> {
            make(p, "saiyan", 40);
            DmzPerCharacter.markCreatedForTest(p);
            at(p);
            protect(p);
            p.level().getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false, p.server);
            p.serverLevel().setDayTime(6000);
        }); }, 40);
    }

    // ---------------------------------------------------------------- 5 + 7: the HUD with DragonMineZ, sensing without the grey

    private static void hud(Minecraft mc, String tag) {
        int[] zid = {-1};
        step(null, () -> { noDmzScreen(mc); mc.setScreen(null); CHAR.accept(CharacterType.MIKIKO); }, 40);
        step(null, () -> { noDmzScreen(mc); server(p -> {
            make(p, "saiyan", 40);
            Vec3 at = at(p);
            p.teleportTo(at.x, at.y, at.z);
            p.setYRot(0); p.setXRot(8);
            p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            clearHostiles(p);
            var z = net.minecraft.world.entity.EntityType.ZOMBIE.create(p.serverLevel());
            z.moveTo(at.x, at.y, at.z + 6, 180, 0); z.setNoAi(true); z.setPersistenceRequired(); p.serverLevel().addFreshEntity(z);
            zid[0] = z.getId();
            com.xenowarriors.bot.PartyBots.spawn(p, CharacterType.RHALIX, null);
        }); mc.options.hideGui = false; mc.options.setCameraType(CameraType.FIRST_PERSON); mc.options.guiScale().set(0); mc.resizeDisplay(); }, 60);
        step(null, () -> mc.setScreen(null), 20);
        step("dmz6hud_854_" + tag, () -> { mc.setScreen(null); log("hud " + tag + " window " + mc.getWindow().getWidth() + "x" + mc.getWindow().getHeight() + " gui " + mc.getWindow().getGuiScale()); }, 3);
        // sensing: Karma Sensing drives DragonMineZ's Ki Sense search (which used to grey the screen out)
        step(null, () -> {
            com.xenowarriors.client.KarmaSense.sensed(new int[]{zid[0]}, new int[]{5}, 400, true, new int[0], new int[]{30}, new int[]{2}, true, false);
        }, 30);
        step("dmz6_sense_854_" + tag, () -> { mc.setScreen(null); log("sense " + tag + " karma active=" + com.xenowarriors.client.KarmaSense.active()
                + " dmz linked=" + com.xenowarriors.client.compat.DmzKiSenseLink.karmaDriven() + " grey passes skipped=" + com.xenowarriors.client.compat.DmzKiSenseLink.greySkipped); }, 3);
        step(null, () -> { try { mc.getWindow().setWindowed(1920, 1080); } catch (Throwable t) { log("resize failed " + t); } mc.options.guiScale().set(0); mc.resizeDisplay(); }, 40);
        step("dmz6_sense_1080_" + tag, () -> mc.setScreen(null), 3);
        step(null, () -> {}, 400);                                              // (the sensing runs out)
        step("dmz6hud_1080_" + tag, () -> { mc.setScreen(null); log("hud " + tag + " window " + mc.getWindow().getWidth() + "x" + mc.getWindow().getHeight() + " gui " + mc.getWindow().getGuiScale()); }, 3);
        step(null, () -> { mc.options.guiScale().set(3); mc.resizeDisplay(); }, 20);
        step("dmz6hud_1080g3_" + tag, () -> mc.setScreen(null), 3);
        step(null, () -> { try { mc.getWindow().setWindowed(854, 480); } catch (Throwable t) { log("resize failed " + t); } mc.options.guiScale().set(0); mc.resizeDisplay(); }, 20);
    }
}

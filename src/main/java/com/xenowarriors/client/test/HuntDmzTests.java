package com.xenowarriors.client.test;

import com.xenowarriors.bot.PartyBotEntity;
import com.xenowarriors.bot.PartyBots;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.compat.*;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;
import java.util.function.Consumer;

/**
 * Bug hunt in the DragonMineZ features (focus "dmzhd..."): fusing with bots and what can break a fusion (changing game
 * mode, logging in after a crash, a bot's DragonMineZ menu, switching characters), bots summoning the dragon (a bot
 * downed mid-way, the owner gone), mission summons that are lost, the Zombie Saiyan's loot, Mikiko's Kaioken
 * bookkeeping, per-character DragonMineZ data while transformed / with a DMZ menu open, and DragonMineZ's HUD panels at
 * several GUI scales. Results are logged as "XW-TEST dmzhd ...". Sub-focus "dmzhdhud" runs the HUD shots only.
 */
public final class HuntDmzTests {
    private HuntDmzTests() {}

    private static Dmz6Tests.Adder S;
    private static Consumer<Consumer<ServerPlayer>> SERVER;
    private static Consumer<String> LOG;
    private static Consumer<CharacterType> CHAR;

    public static boolean handles(String focus) { return focus != null && focus.startsWith("dmzhd"); }

    static void log(String s) { LOG.accept("dmzhd " + s); }
    static void server(Consumer<ServerPlayer> f) { SERVER.accept(f); }
    static void step(String shot, Runnable a, int delay) { S.add(shot, a, delay); }

    public static void build(Minecraft mc, String focus, Dmz6Tests.Adder steps, Consumer<Consumer<ServerPlayer>> server, Consumer<CharacterType> character, Consumer<String> log) {
        S = steps; SERVER = server; LOG = log; CHAR = character;
        setup(mc);
        if (focus.startsWith("dmzhdballs")) {                                  // (the Dragon Balls and mission summons only)
            balls(mc);
            questSummons(mc);
            step(null, () -> server(p -> log("done")), 20);
            return;
        }
        if (!focus.startsWith("dmzhdhud")) {
            fusionGameMode(mc);
            fusionCrashLogin(mc);
            fusionBotMenu(mc);
            fusionSwitch(mc);
            switchTransformed(mc);
            switchMenuOpen(mc);
            balls(mc);
            questSummons(mc);
            zombieSaiyan(mc);
            kaioken(mc);
        }
        hud(mc);
        step(null, () -> server(p -> log("done")), 20);
    }

    // ---------------------------------------------------------------- helpers

    static Object call(Object o, String m, Object... args) {
        if (o == null) return null;
        try {
            for (var mm : o.getClass().getMethods()) if (mm.getName().equals(m) && mm.getParameterCount() == args.length) return mm.invoke(o, args);
        } catch (Throwable t) { return "?" + t.getClass().getSimpleName(); }
        return "?nomethod";
    }

    static void noDmzScreen(Minecraft mc) {
        if (mc.screen != null && mc.screen.getClass().getName().startsWith("com.dragonminez")) mc.setScreen(null);
    }

    static void protect(ServerPlayer p) {
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20 * 60 * 30, 10, false, false));
        p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20 * 60 * 30, 5, false, false));
        p.setHealth(p.getMaxHealth());
    }

    static void clearHostiles(ServerPlayer p) {
        for (var m : p.serverLevel().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(64), m -> m instanceof net.minecraft.world.entity.monster.Enemy)) m.discard();
    }

    /** The live DMZ character made: this race, created, these stats. */
    static void make(ServerPlayer p, String race, int stats) {
        Object d = DmzPerCharacter.live(p);
        if (d == null) { log("no DMZ data"); return; }
        call(call(d, "getCharacter"), "setRace", race);
        call(call(d, "getStatus"), "setHasCreatedCharacter", true);
        Object st = call(d, "getStats");
        for (String m : new String[]{"setStrength", "setStrikePower", "setResistance", "setVitality", "setKiPower", "setEnergy"}) call(st, m, stats);
        DmzPerCharacter.syncAll(p);
    }

    private static Vec3 AT;
    static Vec3 at(ServerPlayer p) { if (AT == null) AT = com.xenowarriors.debug.SkillTestRunner.arena(p); return AT; }

    private static int dF, dB, dQ, dZ;
    static void dump() {
        synchronized (DmzFusion.LOG) { for (int i = dF; i < DmzFusion.LOG.size(); i++) log("fusionlog " + DmzFusion.LOG.get(i)); dF = DmzFusion.LOG.size(); }
        synchronized (DmzDragonBalls.LOG) { for (int i = dB; i < DmzDragonBalls.LOG.size(); i++) log("ballslog " + DmzDragonBalls.LOG.get(i)); dB = DmzDragonBalls.LOG.size(); }
        synchronized (DmzQuestHelp.LOG) { for (int i = dQ; i < DmzQuestHelp.LOG.size(); i++) log("questlog " + DmzQuestHelp.LOG.get(i)); dQ = DmzQuestHelp.LOG.size(); }
        synchronized (com.xenowarriors.boss.ZombieSaiyans.LOG) { for (int i = dZ; i < com.xenowarriors.boss.ZombieSaiyans.LOG.size(); i++) log("saiyanlog " + com.xenowarriors.boss.ZombieSaiyans.LOG.get(i)); dZ = com.xenowarriors.boss.ZombieSaiyans.LOG.size(); }
    }

    static PartyBotEntity bot(ServerPlayer p, CharacterType t) {
        for (PartyBotEntity b : PartyBots.bots(p)) if (b.character() == t) return b;
        return null;
    }

    static int count(ServerPlayer p, CharacterType t) { int n = 0; for (PartyBotEntity b : PartyBots.bots(p)) if (b.character() == t) n++; return n; }

    /** A bot of this character beside you, with a DragonMineZ character (a Saiyan with some stats). */
    static PartyBotEntity ensureBot(ServerPlayer p, CharacterType t) {
        PartyBotEntity b = bot(p, t);
        if (b == null) b = PartyBots.spawn(p, t, null);
        if (b == null) { log("bot " + t.getId() + " spawn FAILED"); return null; }
        Vec3 at = at(p);
        b.moveTo(p.getX() + 2, p.getY(), p.getZ() + 1, 0, 0);
        b.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20 * 60 * 30, 3, false, false));
        DmzBots.ensureRace(b);
        String key = DmzPerCharacter.botKey(b);
        CompoundTag tag = DmzPerCharacter.saved(p, key);
        Object d = DmzPerCharacter.blank(PartyBots.standIn(b));
        if (tag != null && d != null && DmzPerCharacter.loadData(d, tag.copy())) {
            Object st = call(d, "getStats");
            for (String m : new String[]{"setStrength", "setStrikePower", "setResistance", "setVitality", "setKiPower", "setEnergy"}) call(st, m, 50);
            DmzPerCharacter.store(p, key, DmzPerCharacter.saveData(d));
        }
        return b;
    }

    static void clearFusionCd(ServerPlayer p) {
        for (var e : new ArrayList<>(p.getActiveEffects())) if (String.valueOf(ForgeRegistries.MOB_EFFECTS.getKey(e.getEffect())).contains("fusion")) p.removeEffect(e.getEffect());
    }

    static void giveEarring(PartyBotEntity b) {
        b.inventory.addItem(new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation("dragonminez", "pothala_right"))));
    }

    /** Fuse the player with the Rhalix bot (fresh earrings, no cooldown). */
    static boolean fuse(ServerPlayer p) {
        PartyBotEntity b = ensureBot(p, CharacterType.RHALIX);
        if (b == null) return false;
        clearFusionCd(p);
        giveEarring(b);
        b.moveTo(p.getX() + 2, p.getY(), p.getZ() + 1, 0, 0);
        PartyBots.action(p, b.getId(), PartyBots.ACT_FUSE, 0);
        return DmzFusion.fused(p);
    }

    static String own(ServerPlayer p) { DmzFusion.durationTicks(); return DmzFusion.statsLine(DmzPerCharacter.live(p)); }

    /** Back to the test spot in the Overworld (a knock-out sends you to DragonMineZ's Otherworld). */
    static void home(ServerPlayer p) { Vec3 at = at(p); p.teleportTo(p.server.overworld(), at.x, at.y, at.z, 0, 0); }

    static String gm(ServerPlayer p) { return p.gameMode.getGameModeForPlayer().getName(); }

    /** If the test player was knocked out, get back up (a death screen would stop the run). */
    static void respawnIfDead(Minecraft mc) {
        if (mc.player != null && (mc.player.isDeadOrDying() || mc.screen instanceof net.minecraft.client.gui.screens.DeathScreen)) {
            log("client: respawning after a death");
            mc.player.respawn();
            mc.setScreen(null);
        }
    }

    private static final int[] DEATHS = new int[1];
    private static boolean deathHook;

    static void hookDeaths() {
        if (deathHook) return;
        deathHook = true;
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.LOWEST, true,
                (net.minecraftforge.event.entity.living.LivingDeathEvent e) -> {
                    if (e.getEntity() instanceof ServerPlayer sp && !(sp instanceof net.minecraftforge.common.util.FakePlayer)) {
                        DEATHS[0]++;
                        log("DEATH of the test player: " + e.getSource().getMsgId() + " canceled=" + e.isCanceled());
                    }
                });
    }

    // ---------------------------------------------------------------- setup

    private static void setup(Minecraft mc) {
        step(null, () -> { mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE); mc.getToasts().clear(); mc.setScreen(null);
            CHAR.accept(CharacterType.MIKIKO); }, 60);
        step(null, () -> { noDmzScreen(mc); server(p -> {
            hookDeaths();
            make(p, "saiyan", 40);
            DmzPerCharacter.markCreatedForTest(p);
            at(p);
            protect(p);
            p.setGameMode(GameType.SURVIVAL);
            p.level().getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false, p.server);
            p.serverLevel().setDayTime(6000);
            log("setup " + own(p) + " gm=" + gm(p));
        }); }, 40);
    }

    // ---------------------------------------------------------------- fusion 1: changing game mode while fused

    private static void fusionGameMode(Minecraft mc) {
        step(null, () -> server(p -> {
            int d0 = DEATHS[0];
            boolean f = fuse(p);
            log("gamemode: fused=" + f + " " + own(p));
            p.setGameMode(GameType.CREATIVE);
            log("gamemode: after /gamemode creative: alive=" + p.isAlive() + " deadOrDying=" + p.isDeadOrDying() + " deaths+" + (DEATHS[0] - d0)
                    + " ourFused=" + DmzFusion.fused(p) + " rhalix bots=" + count(p, CharacterType.RHALIX) + " gm=" + gm(p) + " | " + own(p));
            dump();
        }), 30);
        step(null, () -> respawnIfDead(mc), 30);
        step(null, () -> { respawnIfDead(mc); server(p -> {
            log("gamemode: later alive=" + p.isAlive() + " fused=" + DmzFusion.fused(p) + " rhalix bots=" + count(p, CharacterType.RHALIX) + " | " + own(p));
            p.setGameMode(GameType.SURVIVAL);
            home(p); protect(p);
            dump();
        }); }, 30);
    }

    // ---------------------------------------------------------------- fusion 2: the game crashed while fused (login with the fused data saved)

    private static void fusionCrashLogin(Minecraft mc) {
        step(null, () -> server(p -> {
            int d0 = DEATHS[0];
            boolean f = fuse(p);
            log("crashlogin: fused=" + f + " " + own(p));
            // what a login after a crash runs, in order: ours at HIGHEST (if any), then DragonMineZ's (its endFusionIfNeeded), then ours (deferred)
            var ev = new net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent(p);
            boolean first = false;
            try { DmzFusion.class.getMethod("onLoginFirst", net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent.class).invoke(null, ev); first = true; } catch (NoSuchMethodException e) { /* not there (before the fix) */ } catch (Throwable t) { log("crashlogin: onLoginFirst failed " + t); }
            try { Class.forName("com.dragonminez.common.events.ForgeCommonEvents").getMethod("endFusionIfNeeded", ServerPlayer.class).invoke(null, p); }
            catch (Throwable t) { log("crashlogin: DMZ endFusionIfNeeded failed " + t); }
            DmzFusion.onLogin(ev);
            log("crashlogin: after the login handlers (ours first=" + first + "): alive=" + p.isAlive() + " deaths+" + (DEATHS[0] - d0) + " ourFused=" + DmzFusion.fused(p) + " | " + own(p));
            dump();
        }), 70);
        step(null, () -> respawnIfDead(mc), 30);
        step(null, () -> { respawnIfDead(mc); server(p -> {
            log("crashlogin: 3 s later alive=" + p.isAlive() + " fused=" + DmzFusion.fused(p) + " rhalix bots=" + count(p, CharacterType.RHALIX) + " | " + own(p));
            home(p); protect(p); p.setGameMode(GameType.SURVIVAL);
            dump();
        }); }, 30);
    }

    // ---------------------------------------------------------------- fusion 3: another bot's DragonMineZ menu while fused

    private static void fusionBotMenu(Minecraft mc) {
        step(null, () -> server(p -> {
            PartyBotEntity k = ensureBot(p, CharacterType.KAERU);
            boolean f = fuse(p);
            log("botmenu: fused=" + f + " " + own(p));
            if (k == null) return;
            PartyBots.action(p, k.getId(), PartyBots.ACT_DMZ, 0);
            log("botmenu: opened Kaeru's DMZ menu while fused: viewing=" + DmzPerCharacter.viewing(p));
        }), 40);
        step("dmzhd_botmenu_fused", () -> {}, 3);
        step(null, () -> { noDmzScreen(mc); server(p -> {
            DmzPerCharacter.endView(p, "menu closed");
            log("botmenu: menu closed: ourFused=" + DmzFusion.fused(p) + " rhalix bots=" + count(p, CharacterType.RHALIX) + " own " + own(p));
            dump();
        }); }, 20);
        step(null, () -> server(p -> {
            if (DmzFusion.fused(p)) DmzFusion.end(p, "test cleanup", false);
            Object me = DmzPerCharacter.live(p);
            log("botmenu: after cleanup " + own(p));
            if (me != null && Boolean.TRUE.equals(call(call(me, "getStatus"), "isFused"))) {       // (stuck fused: cleared so the next tests start clean)
                try { var m = Class.forName("com.dragonminez.server.util.FusionLogic").getDeclaredMethod("clearFusionState", me.getClass()); m.setAccessible(true); m.invoke(null, me); } catch (Throwable t) { log("clear failed " + t); }
                call(call(me, "getBonusStats"), "removeAllBonuses", "FusionBonus");
                DmzPerCharacter.syncAll(p);
                log("botmenu: STUCK fused DMZ state cleared by the test");
            }
            p.setGameMode(GameType.SURVIVAL);
            dump();
        }), 20);
    }

    // ---------------------------------------------------------------- fusion 4: switching characters while fused

    private static void fusionSwitch(Minecraft mc) {
        step(null, () -> server(p -> {
            boolean f = fuse(p);
            log("switch: fused=" + f + " mikiko " + own(p));
            // (the bot you're fused with can't also be picked as a new teammate)
            PartyBots.add(p, new String[]{"rhalix"});
            log("switch: picked Rhalix again while fused -> rhalix bots out=" + count(p, CharacterType.RHALIX));
        }), 30);
        step(null, () -> CHAR.accept(CharacterType.KAERU), 40);
        step(null, () -> { noDmzScreen(mc); server(p -> log("switch: now Kaeru: ourFused=" + DmzFusion.fused(p) + " rhalix bots=" + count(p, CharacterType.RHALIX) + " live " + own(p))); }, 20);
        step(null, () -> CHAR.accept(CharacterType.MIKIKO), 40);
        step(null, () -> { noDmzScreen(mc); server(p -> {
            log("switch: back to Mikiko: ourFused=" + DmzFusion.fused(p) + " rhalix bots=" + count(p, CharacterType.RHALIX) + " live " + own(p));
            if (DmzFusion.fused(p)) DmzFusion.end(p, "test cleanup", false);
            log("switch: after the fusion ended: rhalix bots=" + count(p, CharacterType.RHALIX) + " live " + own(p));
            Object me = DmzPerCharacter.live(p);
            if (me != null && Boolean.TRUE.equals(call(call(me, "getStatus"), "isFused"))) {
                try { var m = Class.forName("com.dragonminez.server.util.FusionLogic").getDeclaredMethod("clearFusionState", me.getClass()); m.setAccessible(true); m.invoke(null, me); } catch (Throwable t) { log("clear failed " + t); }
                call(call(me, "getBonusStats"), "removeAllBonuses", "FusionBonus");
                DmzPerCharacter.syncAll(p);
                log("switch: STUCK fused DMZ state on Mikiko cleared by the test");
            }
            while (count(p, CharacterType.RHALIX) > 1) { PartyBotEntity x = bot(p, CharacterType.RHALIX); PartyBots.forgetFake(x.getUUID()); x.discard(); }
            dump();
        }); }, 20);
    }

    // ---------------------------------------------------------------- per character: switching while transformed

    private static void switchTransformed(Minecraft mc) {
        step(null, () -> server(p -> {
            Object d = DmzPerCharacter.live(p);
            Object ch = call(d, "getCharacter");
            call(ch, "setActiveForm", "supersaiyan", "ssj1");
            DmzPerCharacter.syncAll(p);
            log("xform: Mikiko transformed: active=" + call(ch, "getActiveFormGroup") + "/" + call(ch, "getActiveForm") + " hasForm=" + call(ch, "hasActiveForm"));
        }), 20);
        step(null, () -> CHAR.accept(CharacterType.KAERU), 40);
        step(null, () -> { noDmzScreen(mc); server(p -> {
            Object ch = call(DmzPerCharacter.live(p), "getCharacter");
            log("xform: as Kaeru: active=" + call(ch, "getActiveFormGroup") + "/" + call(ch, "getActiveForm") + " hasForm=" + call(ch, "hasActiveForm") + " race=" + call(ch, "getRaceName"));
        }); }, 40);
        step(null, () -> CHAR.accept(CharacterType.MIKIKO), 40);
        step(null, () -> { noDmzScreen(mc); server(p -> {
            Object ch = call(DmzPerCharacter.live(p), "getCharacter");
            log("xform: back as Mikiko: active=" + call(ch, "getActiveFormGroup") + "/" + call(ch, "getActiveForm") + " hasForm=" + call(ch, "hasActiveForm") + " " + own(p));
            call(ch, "clearActiveForm");
            DmzPerCharacter.syncAll(p);
        }); }, 20);
    }

    // ---------------------------------------------------------------- per character: switching with a bot's DragonMineZ menu open

    private static void switchMenuOpen(Minecraft mc) {
        final CompoundTag[] before = new CompoundTag[1];
        step(null, () -> server(p -> {
            PartyBotEntity k = ensureBot(p, CharacterType.KAERU);
            before[0] = DmzPerCharacter.saveData(DmzPerCharacter.live(p));
            if (k != null) PartyBots.action(p, k.getId(), PartyBots.ACT_DMZ, 0);
            log("menuswitch: Kaeru bot's menu open viewing=" + DmzPerCharacter.viewing(p));
        }), 30);
        step(null, () -> log("menuswitch: client screen " + (mc.screen == null ? "none" : mc.screen.getClass().getName())), 3);
        step(null, () -> CHAR.accept(CharacterType.TECHNICA), 40);
        step(null, () -> { log("menuswitch: client screen after the switch " + (mc.screen == null ? "none" : mc.screen.getClass().getName())); noDmzScreen(mc); server(p ->
                log("menuswitch: as Technica viewing=" + DmzPerCharacter.viewing(p) + " created=" + DmzPerCharacter.created(p))); }, 30);
        step(null, () -> { noDmzScreen(mc); CHAR.accept(CharacterType.MIKIKO); }, 40);
        step(null, () -> { noDmzScreen(mc); server(p -> {
            CompoundTag now = DmzPerCharacter.saveData(DmzPerCharacter.live(p));
            boolean same = before[0] != null && now != null && before[0].getCompound("Stats").equals(now.getCompound("Stats"))
                    && before[0].getCompound("Character").getString("Race").equals(now.getCompound("Character").getString("Race"));
            log("menuswitch: back to Mikiko: own DMZ character unchanged=" + same + " viewing=" + DmzPerCharacter.viewing(p) + " " + own(p));
        }); }, 20);
    }

    // ---------------------------------------------------------------- Dragon Balls: the bot is downed half-way; the owner leaves

    private static void balls(Minecraft mc) {
        step(null, () -> server(p -> {
            Vec3 at = at(p);
            p.teleportTo(p.server.overworld(), at.x - 4, at.y, at.z - 4, 0, 0);
            PartyBotEntity old = bot(p, CharacterType.RHALIX);
            if (old != null && old.level() != p.level()) PartyBots.action(p, old.getId(), PartyBots.ACT_DISMISS, 0);
            PartyBotEntity b = ensureBot(p, CharacterType.RHALIX);
            if (b == null) return;
            clearHostiles(p);
            b.moveTo(at.x + 1, at.y, at.z + 1, 0, 0);
            log("balls1: set " + DmzDragonBalls.giveSetForTest(b) + " started=" + DmzDragonBalls.start(b, p, null, p.serverLevel(), true));
        }), 50);
        step(null, () -> server(p -> {                                       // (two or three are down: the bot is knocked down)
            PartyBotEntity b = bot(p, CharacterType.RHALIX);
            if (b == null) return;
            b.removeAllEffects();
            b.invulnerableTime = 0;
            b.hurt(b.damageSources().generic(), 1.0E6F);
            log("balls1: bot knocked down mid-way downed=" + b.downed() + " busy=" + busy(b));
            dump();
        }), 160);
        step(null, () -> server(p -> {
            PartyBotEntity b = bot(p, CharacterType.RHALIX);
            if (b == null) return;
            b.revive();
            b.setHealth(b.getMaxHealth());
            log("balls1: bot back up: busy=" + busy(b) + " (busy = it will never put balls down again)");
            pickUpBalls(p, b);
            log("balls1: balls back in its bag; set carried again: started=" + DmzDragonBalls.start(b, p, null, p.serverLevel(), true) + " busy=" + busy(b));
        }), 20);
        // the owner goes to the Nether while it's placing them
        step(null, () -> server(p -> {
            PartyBotEntity b = bot(p, CharacterType.RHALIX);
            var nether = p.server.getLevel(net.minecraft.world.level.Level.NETHER);
            if (b == null || nether == null) return;
            p.teleportTo(nether, 0.5, 100, 0.5, 0, 0);
            log("balls2: owner went to the Nether while the bot places the balls");
        }), 160);
        step(null, () -> server(p -> {
            dump();
            int dragons = 0;
            for (var l : p.server.getAllLevels()) for (var e : l.getAllEntities()) if (e != null && e.getClass().getName().contains("dragon.DragonWish")) { dragons++; log("balls2: a dragon in " + l.dimension().location() + " at " + e.blockPosition().toShortString()); }
            log("balls2: dragons anywhere=" + dragons);
            Vec3 at = at(p);
            p.teleportTo(p.server.overworld(), at.x, at.y, at.z, 0, 0);
        }), 40);
        step(null, () -> { noDmzScreen(mc); server(p -> {
            for (var l : p.server.getAllLevels()) for (var e : l.getAllEntities()) if (e != null && e.getClass().getName().contains("dragon.DragonWish")) e.discard();
            PartyBotEntity b = bot(p, CharacterType.RHALIX);
            if (b != null) pickUpBalls(p, b);
            dump();
        }); }, 20);
    }

    static Boolean busy(PartyBotEntity b) {
        try { return (Boolean) DmzDragonBalls.class.getMethod("busyForTest", UUID.class).invoke(null, b.getUUID()); } catch (Throwable t) { return null; }
    }

    /** Any Dragon Ball blocks around: broken and given back to the bot. */
    static void pickUpBalls(ServerPlayer p, PartyBotEntity b) {
        var l = (net.minecraft.server.level.ServerLevel) b.level();
        var c = b.blockPosition();
        int n = 0;
        for (var pos : net.minecraft.core.BlockPos.betweenClosed(c.offset(-8, -3, -8), c.offset(8, 3, 8))) {
            var id = ForgeRegistries.BLOCKS.getKey(l.getBlockState(pos).getBlock());
            if (id != null && id.getNamespace().equals("dragonminez") && id.getPath().startsWith("dball")) {
                var item = ForgeRegistries.ITEMS.getValue(id);
                l.removeBlock(pos, false);
                if (item != null) b.inventory.addItem(new ItemStack(item));
                n++;
            }
        }
        for (ItemEntity ie : l.getEntitiesOfClass(ItemEntity.class, b.getBoundingBox().inflate(10))) {
            var id = ForgeRegistries.ITEMS.getKey(ie.getItem().getItem());
            if (id != null && id.getPath().startsWith("dball")) { b.inventory.addItem(ie.getItem().copy()); ie.discard(); n++; }
        }
        log("balls: picked up " + n + " balls");
    }

    // ---------------------------------------------------------------- mission summons that are lost

    @SuppressWarnings("unchecked")
    private static String[] storyQuest(ServerPlayer p) {
        try {
            Map<String, Object> all = (Map<String, Object>) Class.forName("com.dragonminez.common.quest.QuestRegistry").getMethod("getAllQuests").invoke(null);
            for (var e : all.entrySet()) {
                List<?> objs = (List<?>) call(e.getValue(), "getObjectives");
                for (int i = 0; i < objs.size(); i++) {
                    Object o = objs.get(i);
                    if (!o.getClass().getSimpleName().equals("KillObjective") || Boolean.TRUE.equals(call(o, "isTag"))) continue;
                    if (!"NATURAL".equals(String.valueOf(call(o, "getSpawnMode")))) continue;
                    EntityType<?> t = (EntityType<?>) call(o, "resolveEntityType");
                    if (t == null) continue;
                    var c = t.create(p.serverLevel());
                    boolean saga = c != null && c.getClass().getName().contains(".sagas.");
                    if (c != null) c.discard();
                    if (saga) return new String[]{e.getKey(), "" + i, ForgeRegistries.ENTITY_TYPES.getKey(t).toString()};
                }
            }
        } catch (Throwable t) { log("quest lookup failed " + t); }
        return null;
    }

    static int summons(ServerPlayer p, String key) {
        int n = 0;
        for (var e : p.serverLevel().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(96), e -> e.getPersistentData().getBoolean("xw_quest_summon")))
            if (key.equals(e.getPersistentData().getString("dmz_quest_key"))) n++;
        return n;
    }

    private static void questSummons(Minecraft mc) {
        final String[][] q = new String[1][];
        step(null, () -> { noDmzScreen(mc); server(p -> {
            q[0] = storyQuest(p);
            log("summon: story quest " + Arrays.toString(q[0]));
            if (q[0] == null) return;
            try { DmzQuestHelp.class.getField("RESUMMON_TICKS").setInt(null, 300); log("summon: re-summon wait set to 300 ticks"); } catch (Throwable t) { log("summon: no re-summon setting (" + t.getClass().getSimpleName() + ")"); }
            DmzQuestHelp.resetSummonedForTest(p);
            home(p); protect(p);
            for (PartyBotEntity b : new ArrayList<>(PartyBots.bots(p))) PartyBots.action(p, b.getId(), PartyBots.ACT_DISMISS, 0);   // (no bot to kill them off)
            DmzQuests.accept(p, q[0][0]);
            log("summon: accepted " + q[0][0] + " " + DmzQuests.progress(p, q[0][0], Integer.parseInt(q[0][1])));
        }); }, 20 * 17);
        step(null, () -> server(p -> {
            if (q[0] == null) return;
            dump();
            int n = summons(p, q[0][0]);
            log("summon: first summons near the player=" + n + "; they're lost (despawned / killed by something else)");
            for (var e : p.serverLevel().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(96), e -> e.getPersistentData().getBoolean("xw_quest_summon"))) e.discard();
            log("summon: objective " + DmzQuests.progress(p, q[0][0], Integer.parseInt(q[0][1])));
        }), 20 * 26);
        step("dmzhd_resummoned", () -> server(p -> {
            if (q[0] == null) return;
            dump();
            log("summon: 26 s after losing them: summons near the player=" + summons(p, q[0][0]) + " (0 = the objective can never be finished)");
            for (var e : p.serverLevel().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(96), e -> e.getPersistentData().getBoolean("xw_quest_summon"))) e.discard();
            Object pqd = call(DmzPerCharacter.live(p), "getPlayerQuestData");
            call(pqd, "resetQuest", q[0][0]);
            DmzQuestHelp.resetSummonedForTest(p);
        }), 3);
    }

    // ---------------------------------------------------------------- Zombie Saiyan: loot only from its last form

    private static void zombieSaiyan(Minecraft mc) {
        step(null, () -> server(p -> {
            clearHostiles(p);
            Vec3 at = at(p);
            p.teleportTo(p.server.overworld(), at.x, at.y, at.z, 0, 0);
            for (int f : new int[]{0, 4}) {
                var z = com.xenowarriors.boss.ZombieSaiyans.ZOMBIE_SAIYAN.get().create(p.serverLevel());
                if (z == null) { log("saiyan: create FAILED"); return; }
                double x = at.x + (f == 0 ? -6 : 6);
                z.moveTo(x, at.y, at.z + 8, 0, 0);
                z.setPersistenceRequired(); z.setNoAi(true);
                p.serverLevel().addFreshEntity(z);
                if (f > 0) z.setFormForTest(f);
                z.kill();                                                        // (/kill - or falling out of the world: damage that bypasses its forms)
                int items = 0, armour = 0;
                for (ItemEntity ie : p.serverLevel().getEntitiesOfClass(ItemEntity.class, z.getBoundingBox().inflate(4))) {
                    items++;
                    if (String.valueOf(ForgeRegistries.ITEMS.getKey(ie.getItem().getItem())).contains("goku")) armour++;
                    ie.discard();
                }
                log("saiyan: killed outright as " + com.xenowarriors.boss.ZombieSaiyanEntity.formName(z.form()) + " -> item stacks dropped=" + items + " goku armour=" + armour);
            }
            dump();
        }), 20);
    }

    // ---------------------------------------------------------------- Mikiko's Kaioken bookkeeping across worlds

    private static void kaioken(Minecraft mc) {
        step(null, () -> server(p -> {
            DmzKaioken.resetForTest(p);
            Object fm = call(call(DmzPerCharacter.live(p), "getCharacter"), "getStackFormMasteries");
            call(fm, "setMastery", "kaioken", "x2", 10.0, 100.0);
            DmzKaioken.poll(p);
            log("kaioken: Mikiko x2 mastery " + DmzKaioken.mastery(p, "x2") + " seen");
            // she logs out; another world (or a restored backup) has her at a higher mastery
            boolean handler = false;
            try {
                DmzKaioken.class.getMethod("onLogout", net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent.class)
                        .invoke(null, new net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent(p));
                handler = true;
            } catch (NoSuchMethodException e) { /* none (before the fix) */ } catch (Throwable t) { log("kaioken: onLogout failed " + t); }
            call(fm, "setMastery", "kaioken", "x2", 50.0, 100.0);
            double given = DmzKaioken.poll(p);
            log("kaioken: logout handler=" + handler + "; after 'logging into another world' at mastery 50 the bonus given=" + given + " -> mastery " + DmzKaioken.mastery(p, "x2") + " (should be 0 bonus, 50)");
            call(fm, "setMastery", "kaioken", "x2", 0.0, 100.0);
            DmzKaioken.resetForTest(p);
        }), 20);
    }

    // ---------------------------------------------------------------- DragonMineZ's HUD panels at several GUI scales

    private static void hud(Minecraft mc) {
        step(null, () -> { noDmzScreen(mc); mc.setScreen(null); CHAR.accept(CharacterType.MIKIKO); }, 40);
        step(null, () -> { noDmzScreen(mc); server(p -> {
            make(p, "saiyan", 40);
            Vec3 at = at(p);
            p.teleportTo(p.server.overworld(), at.x, at.y, at.z, 0, 0);
            p.setYRot(0); p.setXRot(8);
            p.setGameMode(GameType.SURVIVAL);
            clearHostiles(p);
            String id = Dmz6Tests.killQuest();
            if (id != null) {
                DmzQuests.accept(p, id);
                Object qd = call(DmzPerCharacter.live(p), "getPlayerQuestData");
                call(qd, "setTrackedQuestId", id);
                DmzPerCharacter.syncAll(p);
            }
            log("hud: tracking " + id);
            try {
                Object tech = call(DmzPerCharacter.live(p), "getTechniques");
                var reg = (Map<?, ?>) Class.forName("com.dragonminez.common.stats.techniques.PredefinedTechniques").getField("REGISTRY").get(null);
                int i = 0;
                for (var e : reg.entrySet()) { if (i >= 4) break; call(tech, "unlockTechnique", e.getValue()); call(tech, "equipTechnique", i++, e.getKey()); }
                DmzPerCharacter.syncStats(p);
            } catch (Throwable t) { log("hud: equip FAILED " + t); }
        });
            try {
                Object cfg = Class.forName("com.dragonminez.common.config.ConfigManager").getMethod("getUserConfig").invoke(null);
                cfg.getClass().getMethod("setTechniqueHotbarRightSide", Boolean.class).invoke(cfg, Boolean.TRUE);
            } catch (Throwable t) { log("hud: right side FAILED " + t); }
            com.xenowarriors.client.compat.DmzHudLayout.forceTechniqueBarForTest = true;
            mc.options.hideGui = false; mc.options.setCameraType(CameraType.FIRST_PERSON);
        }, 60);
        int[][] sizes = {{854, 480, 1}, {854, 480, 2}, {1920, 1080, 1}, {1920, 1080, 2}, {1920, 1080, 3}, {1920, 1080, 4}};
        for (int[] s : sizes) {
            step(null, () -> { try { mc.getWindow().setWindowed(s[0], s[1]); } catch (Throwable t) { log("resize failed " + t); } mc.options.guiScale().set(s[2]); mc.resizeDisplay(); }, 30);
            step("dmzhd_hud_" + s[0] + "_g" + s[2], () -> { mc.setScreen(null);
                log("hud: " + mc.getWindow().getWidth() + "x" + mc.getWindow().getHeight() + " gui " + mc.getWindow().getGuiScale() + " scaled " + mc.getWindow().getGuiScaledWidth() + "x" + mc.getWindow().getGuiScaledHeight()
                        + " layout " + com.xenowarriors.client.compat.DmzHudLayout.lastShift + " ourTop=" + com.xenowarriors.client.KhHud.top); }, 3);
        }
        step(null, () -> { com.xenowarriors.client.compat.DmzHudLayout.forceTechniqueBarForTest = false; try { mc.getWindow().setWindowed(854, 480); } catch (Throwable t) { log("resize failed " + t); } mc.options.guiScale().set(0); mc.resizeDisplay(); }, 20);
    }
}

package com.xenowarriors.client.test;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.bot.PartyBotEntity;
import com.xenowarriors.bot.PartyBots;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.client.UiTest;
import com.xenowarriors.client.UiTest.Step;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Leftover bug-hunt checks (focus "huntleft", "efhuntleft" with Epic Fight). Every result is an
 * "XW-TEST huntleft ... -> PASS/FAIL" line.
 * <ol>
 *   <li>Fox Heal cast by a player brings a downed bot teammate back up (and a Kaeru bot's does too).</li>
 *   <li>(Epic Fight) Gravity Compress's squish on a player Epic Fight draws.</li>
 *   <li>(Epic Fight) Switching to battle mode while holding Rhalix's charge: no Ki Burst / Rising Leap / Kishi Warp.</li>
 *   <li>(Epic Fight) Our lock-on with Epic Fight's "always" third-person camera turns that camera too.</li>
 * </ol>
 */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus.FORGE, value = net.minecraftforge.api.distmarker.Dist.CLIENT)
public final class HuntLeftTests {
    private HuntLeftTests() {}

    public static boolean handles(String focus) { return focus != null && focus.replaceFirst("^ef", "").startsWith("huntleft"); }

    private static void log(String s) { UiTest.log("huntleft " + s); }

    private static String f(double v) { return String.format(java.util.Locale.ROOT, "%.1f", v); }

    private static boolean ef() { return net.minecraftforge.fml.ModList.get().isLoaded("epicfight"); }

    private static Vec3 origin = Vec3.ZERO;
    private static int dummy = -1;

    private static int intField(String cls, String name) {
        try { return Class.forName(cls).getField(name).getInt(null); } catch (Throwable t) { return -1; }
    }

    private static void arena(ServerPlayer p) {
        p.setGameMode(GameType.SURVIVAL);
        p.setInvulnerable(true);
        ServerLevel l = p.serverLevel();
        l.setDayTime(6000);
        for (var e : l.getEntitiesOfClass(Monster.class, p.getBoundingBox().inflate(64))) e.discard();
        BlockPos base = BlockPos.containing(p.getX(), 150, p.getZ());
        for (int x = -14; x <= 14; x++) for (int z = -10; z <= 20; z++) {
            l.setBlock(base.offset(x, -1, z), Blocks.SMOOTH_STONE.defaultBlockState(), 2);
            for (int y = 0; y < 8; y++) l.setBlock(base.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
        }
        origin = new Vec3(base.getX() + 0.5, base.getY(), base.getZ() + 0.5);
        p.stopRiding();
        p.connection.teleport(origin.x, origin.y, origin.z, 0F, 0F);
        p.setHealth(p.getMaxHealth());
        p.fallDistance = 0;
    }

    private static void clearBots(ServerPlayer p) {
        for (PartyBotEntity b : new ArrayList<>(PartyBots.bots(p))) { PartyBots.checkIn(b, p); PartyBots.forgetFake(b.getUUID()); b.discard(); }
        for (CharacterType t : CharacterType.PLAYABLE) p.getPersistentData().remove("xw_bot_rejoin_" + t.getId());
    }

    private static PartyBotEntity bot(ServerPlayer p, CharacterType t) {
        for (PartyBotEntity b : PartyBots.bots(p)) if (b.character() == t && !b.isRemoved()) return b;
        return PartyBots.spawn(p, t, null);
    }

    private static PartyBotEntity downBot(ServerPlayer p, CharacterType t, double dx, double dz) {
        PartyBotEntity b = bot(p, t);
        if (b == null) return null;
        b.moveTo(origin.x + dx, origin.y, origin.z + dz, 0, 0);
        b.getNavigation().stop();
        b.invulnerableTime = 0;
        b.hurt(p.damageSources().generic(), 100000F);
        return b;
    }

    private static void look(Minecraft mc, float yaw, float pitch) {
        mc.player.setYRot(yaw); mc.player.setXRot(pitch); mc.player.yRotO = yaw; mc.player.xRotO = pitch;
        mc.player.yBodyRot = yaw; mc.player.setYHeadRot(yaw);
    }

    private static Zombie zombie(ServerPlayer p, Vec3 at) {
        Zombie z = EntityType.ZOMBIE.create(p.serverLevel());
        z.moveTo(at.x, at.y, at.z, 180F, 0F);
        z.setNoAi(true);
        z.setPersistenceRequired();
        z.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        p.serverLevel().addFreshEntity(z);
        return z;
    }

    // ------------------------------------------------------------------ camera yaw where the world is really drawn from
    private static boolean camWatch;
    private static float camYaw;
    private static int camFrames;

    @SubscribeEvent
    public static void onStage(RenderLevelStageEvent event) {
        if (!camWatch || event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) return;
        camYaw = event.getCamera().getYRot();
        camFrames++;
    }

    public static void build(Minecraft mc, List<Step> l, String focus) {
        String tag = ef() ? "ef" : "noef";
        l.add(new Step(null, () -> { UiTest.noToasts(mc); mc.setScreen(null); UiTest.character(CharacterType.KAERU); UiTest.server(p -> { clearBots(p); arena(p); }); }, 50));
        l.add(new Step(null, () -> log("start: epic fight loaded=" + ef()), 2));

        // ---- 1. Fox Heal and a downed bot teammate
        // (a) cast by the player
        l.add(new Step(null, () -> UiTest.server(p -> {
            PartyBotEntity r = downBot(p, CharacterType.RHALIX, 3, 3);
            log("foxheal player: rhalix bot downed=" + (r != null && r.downed()) + " hp " + (r == null ? "-" : f(r.getHealth())));
        }), 20));
        l.add(new Step(null, () -> { mc.options.hideGui = false; mc.options.setCameraType(CameraType.FIRST_PERSON); look(mc, 45F, 20F);
            UiTest.server(p -> {
                PartyBotEntity r = bot(p, CharacterType.RHALIX);
                boolean was = r != null && r.downed();
                boolean used = new com.xenowarriors.skill.kaeru.FoxHealSkill().use(p);
                boolean now = r != null && r.downed();
                log("foxheal player: used=" + used + ", rhalix bot downed " + was + " -> " + now + ", hp " + (r == null ? "-" : f(r.getHealth()))
                        + " -> " + (was && used && !now ? "PASS" : "FAIL"));
            }); }, 15));
        l.add(new Step("huntleft_" + tag + "_foxheal_player", () -> {}, 2));
        // (b) cast by a Kaeru bot (once a second, before the revive circle could do it)
        int[] heals = new int[1];
        l.add(new Step(null, () -> UiTest.server(p -> { clearBots(p); arena(p);
            PartyBotEntity k = bot(p, CharacterType.KAERU);
            if (k != null) { k.moveTo(origin.x - 3, origin.y, origin.z + 1, 0, 0); k.karma = 100F; }
        }), 30));
        l.add(new Step(null, () -> UiTest.server(p -> {
            heals[0] = com.xenowarriors.bot.BotSupport.heals;
            PartyBotEntity r = downBot(p, CharacterType.RHALIX, 4, 2);
            log("foxheal bot: rhalix bot downed=" + (r != null && r.downed()) + " (" + f(7.1) + " blocks from the Kaeru bot)");
        }), 25));
        l.add(new Step(null, () -> UiTest.server(p -> {
            PartyBotEntity r = bot(p, CharacterType.RHALIX);
            int n = com.xenowarriors.bot.BotSupport.heals - heals[0];
            boolean down = r != null && r.downed();
            log("foxheal bot: kaeru bot heals " + n + ", rhalix bot downed after 1.2 s=" + down + " -> " + (n > 0 && !down ? "PASS" : "FAIL"));
            clearBots(p);
        }), 5));

        if (!ef()) { l.add(new Step(null, () -> log("done"), 5)); return; }

        // ---- 2. squished while Epic Fight draws the player (battle mode, an axe: Epic Fight's stance)
        l.add(new Step(null, () -> { UiTest.character(CharacterType.RHALIX); UiTest.server(p -> { arena(p); p.getInventory().clearContent(); p.getInventory().selected = 0;
            p.getInventory().setItem(0, new ItemStack(Items.IRON_AXE)); }); }, 40));
        l.add(new Step(null, () -> { Ef.battle(true); mc.options.hideGui = true; mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT); look(mc, 0F, 0F); }, 30));
        int[] c = new int[2];
        l.add(new Step("huntleft_ef_squish_off", () -> log("squish: " + Ef.draw(mc.player)), 2));
        l.add(new Step(null, () -> { c[0] = com.xenowarriors.client.ClientEvents.squishedDraws; com.xenowarriors.client.ClientSkillState.markSquish(mc.player.getId(), 1F); }, 1));
        for (int i = 0; i < 14; i++) l.add(new Step(i == 10 ? "huntleft_ef_squish_on" : null, () -> com.xenowarriors.client.ClientSkillState.markSquish(mc.player.getId(), 1F), 1));
        l.add(new Step(null, () -> {
            int n = com.xenowarriors.client.ClientEvents.squishedDraws - c[0];
            log("squish on an Epic Fight-drawn player: " + Ef.draw(mc.player) + ", drawn squished " + n + " times -> " + (n > 5 ? "PASS" : "FAIL"));
            mc.options.hideGui = false; mc.options.setCameraType(CameraType.FIRST_PERSON); }, 20));

        // ---- 3. Rhalix's held attack, then battle mode before it's let go
        l.add(new Step(null, () -> { Ef.battle(false); UiTest.server(p -> { arena(p); p.getInventory().clearContent(); zombie(p, origin.add(0, 0, 2.5)); }); }, 30));
        String[][] rounds = {{"leap", "22", "3", "0"}, {"burst", "12", "3", "0"}, {"kishi", "12", "2", "1"}};
        for (String[] r : rounds) {
            String name = r[0];
            int hold = Integer.parseInt(r[1]), switchAt = Integer.parseInt(r[2]);
            boolean run = r[3].equals("1");
            boolean[] seen = new boolean[3];       // burst, leap, kishi
            int[] cc = new int[1];
            l.add(new Step(null, () -> { Ef.battle(false); look(mc, 0F, 8F); log("charge " + name + ": battle=" + Ef.battleOn() + ", rhalix=" + (com.xenowarriors.client.ClientCharacterData.getLocal() == CharacterType.RHALIX)); }, 25));
            l.add(new Step(null, () -> {
                cc[0] = intField("com.xenowarriors.client.combo.ComboClient", "chargesCancelled");
                if (run) { mc.options.keyUp.setDown(true); mc.options.keySprint.setDown(true); mc.player.setSprinting(true); }
                com.xenowarriors.client.combo.ComboClient.testHold(hold);
            }, 1));
            for (int t = 1; t < 40; t++) {
                int tt = t;
                l.add(new Step(null, () -> {
                    if (tt == switchAt) Ef.battle(true);
                    if (run && tt == 14) { mc.options.keyUp.setDown(false); mc.options.keySprint.setDown(false); }
                    var s = com.xenowarriors.client.combo.ComboClient.swing(mc.player);
                    if (s != null && s.hit() == com.xenowarriors.combat.ComboStyle.BURST) seen[0] = true;
                    if (s != null && s.hit() == com.xenowarriors.combat.ComboStyle.LEAP) seen[1] = true;
                    if (com.xenowarriors.client.KishiWarpClient.warping()) seen[2] = true;
                }, 1));
            }
            l.add(new Step(null, () -> {
                boolean any = seen[0] || seen[1] || seen[2];
                int cancelled = intField("com.xenowarriors.client.combo.ComboClient", "chargesCancelled");
                log("charge " + name + " (held " + hold + " ticks, battle mode on at tick " + switchAt + "): battle=" + Ef.battleOn() + ", ki burst " + seen[0] + ", rising leap " + seen[1]
                        + ", kishi warp " + seen[2] + ", charges cancelled " + (cancelled < 0 ? "n/a" : String.valueOf(cancelled - cc[0])) + " -> " + (any ? "FAIL" : "PASS"));
                Ef.battle(false);
            }, 30));
        }
        // control: the same held attack without the switch still charges (vanilla mode)
        boolean[] ctl = new boolean[1];
        l.add(new Step(null, () -> { Ef.battle(false); look(mc, 0F, 8F); com.xenowarriors.client.combo.ComboClient.testHold(22); }, 1));
        for (int t = 1; t < 40; t++) l.add(new Step(null, () -> { var s = com.xenowarriors.client.combo.ComboClient.swing(mc.player); if (s != null && s.hit() == com.xenowarriors.combat.ComboStyle.LEAP) ctl[0] = true; }, 1));
        l.add(new Step(null, () -> log("charge control (no switch): rising leap " + ctl[0] + " -> " + (ctl[0] ? "PASS" : "FAIL")), 30));

        // ---- 4. our lock-on with Epic Fight's "always" third-person camera (vanilla mode)
        l.add(new Step(null, () -> { Ef.battle(false); UiTest.server(p -> { arena(p); dummy = zombie(p, origin.add(0, 0, 7)).getId(); }); }, 30));
        l.add(new Step(null, () -> { Ef.cameraAlways(true); mc.options.hideGui = false; mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); look(mc, 0F, 5F); }, 20));
        l.add(new Step(null, () -> {
            if (com.xenowarriors.client.LockOn.target() != null) com.xenowarriors.client.LockOn.toggle();
            com.xenowarriors.client.LockOn.toggle();
            var t = com.xenowarriors.client.LockOn.target();
            log("lock-on: ours " + (t == null ? "none" : t.getType().toShortString()) + ", epic fight tps=" + Ef.tps() + ", its lock-on " + Ef.lockOn());
        }, 10));
        int[] turns = new int[1];
        l.add(new Step(null, () -> { turns[0] = intField("com.xenowarriors.client.compat.epicfight.EpicFightCompat", "lockOnCameraTurns"); camFrames = 0; camWatch = true;
            UiTest.server(p -> { var z = p.serverLevel().getEntity(dummy); if (z != null) z.teleportTo(origin.x + 7, origin.y, origin.z + 1); }); }, 50));
        l.add(new Step("huntleft_ef_lockon_always", () -> {
            var t = com.xenowarriors.client.LockOn.target();
            Vec3 d = t == null ? Vec3.ZERO : t.position().subtract(mc.player.position());
            float want = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
            float off = Math.abs(net.minecraft.util.Mth.wrapDegrees(camYaw - want));
            int n = intField("com.xenowarriors.client.compat.epicfight.EpicFightCompat", "lockOnCameraTurns");
            log("lock-on with the \"always\" camera: target " + (t == null ? "none" : t.getType().toShortString()) + ", want yaw " + f(want) + ", player yaw " + f(net.minecraft.util.Mth.wrapDegrees(mc.player.getYRot()))
                    + ", epic fight camera yaw " + f(net.minecraft.util.Mth.wrapDegrees(Ef.camYaw())) + ", drawn camera yaw " + f(net.minecraft.util.Mth.wrapDegrees(camYaw)) + " (" + camFrames + " frames, off by " + f(off)
                    + "), tps=" + Ef.tps() + ", camera turns " + (n < 0 ? "n/a" : String.valueOf(n - turns[0])) + " -> " + (t != null && Ef.tps() && off < 15 ? "PASS" : "FAIL"));
            camWatch = false;
        }, 3));
        l.add(new Step(null, () -> { if (com.xenowarriors.client.LockOn.target() != null) com.xenowarriors.client.LockOn.toggle(); Ef.cameraAlways(false);
            mc.options.setCameraType(CameraType.FIRST_PERSON); log("done"); }, 5));
    }

    /** Epic Fight calls (only reached when it's installed). */
    private static final class Ef {
        static yesman.epicfight.client.world.capabilites.entitypatch.player.LocalPlayerPatch patch() {
            return yesman.epicfight.client.ClientEngine.getInstance().getPlayerPatch();
        }

        static void battle(boolean on) {
            try {
                var p = patch();
                if (p == null) return;
                if (on && !p.isEpicFightMode()) p.toEpicFightMode(true);
                if (!on && p.isEpicFightMode()) p.toVanillaMode(true);
            } catch (Throwable t) { log("battle: " + t); }
        }

        static boolean battleOn() { try { var p = patch(); return p != null && p.isEpicFightMode(); } catch (Throwable t) { return false; } }

        static String draw(net.minecraft.client.player.AbstractClientPlayer p) {
            try { return com.xenowarriors.client.compat.epicfight.EpicFightCompat.debugDraw(p); } catch (Throwable t) { return "error " + t; }
        }

        static boolean tps() {
            try { return yesman.epicfight.api.client.camera.EpicFightCameraAPI.getInstance().isTPSMode(); } catch (Throwable t) { return false; }
        }

        static float camYaw() {
            try { return yesman.epicfight.api.client.camera.EpicFightCameraAPI.getInstance().getCameraYRot(); } catch (Throwable t) { return Float.NaN; }
        }

        static String lockOn() {
            try { return yesman.epicfight.api.client.camera.EpicFightCameraAPI.getInstance().isLockingOnTarget() ? "on" : "off"; } catch (Throwable t) { return "error " + t; }
        }

        private static Object savedMode;

        static void cameraAlways(boolean on) {
            try {
                if (on) { savedMode = yesman.epicfight.config.ClientConfig.cameraMode; yesman.epicfight.config.ClientConfig.cameraMode = yesman.epicfight.config.ClientConfig.TPSType.ALWAYS; }
                else if (savedMode != null) { yesman.epicfight.config.ClientConfig.cameraMode = (yesman.epicfight.config.ClientConfig.TPSType) savedMode; savedMode = null; }
            } catch (Throwable t) { log("camera mode: " + t); }
        }
    }
}

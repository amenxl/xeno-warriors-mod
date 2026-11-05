package com.xenowarriors.client.test;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.client.UiTest;
import com.xenowarriors.client.UiTest.Step;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.List;

/**
 * Bug-hunt checks where Xeno Warriors meets Epic Fight (focus "efhuntef" with Epic Fight, "huntef" without it as the
 * control). Every result is an "XW-TEST huntef ..." line.
 * <ol>
 *   <li>Cutscene camera (Gravity Barrage) in third person: the camera must be where the cutscene puts it.</li>
 *   <li>Vehicle camera in third person: backed off behind the car.</li>
 *   <li>Knocked down / squished mobs that Epic Fight draws: tipped over / flattened.</li>
 *   <li>Battle mode left clicks: a clash push, a wire whip, nothing while knocked out (Epic Fight's swing eats them).</li>
 *   <li>Lock-on key in battle mode follows Epic Fight's lock-on (regression).</li>
 * </ol>
 */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus.FORGE, value = net.minecraftforge.api.distmarker.Dist.CLIENT)
public final class HuntEfTests {
    private HuntEfTests() {}

    public static boolean handles(String focus) { return focus != null && focus.replaceFirst("^ef", "").startsWith("huntef"); }

    private static void log(String s) { UiTest.log("huntef " + s); }

    private static String f(double v) { return String.format(java.util.Locale.ROOT, "%.2f", v); }

    private static boolean ef() { return net.minecraftforge.fml.ModList.get().isLoaded("epicfight"); }

    private static Vec3 origin;
    private static int dummy = -1;

    // ------------------------------------------------------------------ camera measuring (every frame, where the world is really drawn from)
    private static int camMode;                 // 0 off, 1 cutscene, 2 vehicle
    private static int frames;
    private static double maxOff, maxAng, sumDist;

    @SubscribeEvent
    public static void onStage(RenderLevelStageEvent event) {
        if (camMode == 0 || event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        var cam = event.getCamera();
        float pt = event.getPartialTick();
        if (camMode == 1) {
            double[] want = com.xenowarriors.client.UltimateCutscene.camera(pt);
            if (want == null) want = com.xenowarriors.client.TeamUltCutscene.camera(pt);
            if (want == null) return;
            frames++;
            maxOff = Math.max(maxOff, cam.getPosition().distanceTo(new Vec3(want[0], want[1], want[2])));
            maxAng = Math.max(maxAng, Math.abs(net.minecraft.util.Mth.wrapDegrees(cam.getYRot() - (float) want[3])));
        } else {
            frames++;
            sumDist += cam.getPosition().distanceTo(mc.player.getEyePosition(pt));
        }
    }

    private static void startCam(int mode) { camMode = mode; frames = 0; maxOff = 0; maxAng = 0; sumDist = 0; }

    // ------------------------------------------------------------------ input

    /** Holds (or lets go of) every key mapping on the same key as {@code base} (Epic Fight's attack / guard share the mouse buttons). */
    private static void press(Minecraft mc, net.minecraft.client.KeyMapping base, boolean down) {
        for (net.minecraft.client.KeyMapping k : mc.options.keyMappings) {
            if (!k.getKey().equals(base.getKey())) continue;
            k.setDown(down);
            if (down) try {                                                     // one click queued (clickCount: KeyMapping's only int field)
                for (var fld : net.minecraft.client.KeyMapping.class.getDeclaredFields())
                    if (fld.getType() == int.class && !java.lang.reflect.Modifier.isStatic(fld.getModifiers())) { fld.setAccessible(true); fld.setInt(k, fld.getInt(k) + 1); }
            } catch (Throwable t) { log("press: " + t); }
        }
    }

    private static void look(Minecraft mc, float yaw, float pitch) {
        mc.player.setYRot(yaw); mc.player.setXRot(pitch); mc.player.yRotO = yaw; mc.player.xRotO = pitch;
        mc.player.yBodyRot = yaw; mc.player.setYHeadRot(yaw);
    }

    private static void arena(ServerPlayer p) {
        p.setGameMode(GameType.SURVIVAL);
        p.setInvulnerable(true);
        p.serverLevel().setDayTime(6000);
        ServerLevel l = p.serverLevel();
        for (var e : l.getEntitiesOfClass(Monster.class, p.getBoundingBox().inflate(64))) e.discard();
        for (var e : l.getEntitiesOfClass(com.xenowarriors.vehicle.VehicleEntity.class, p.getBoundingBox().inflate(64))) e.discard();
        BlockPos base = BlockPos.containing(p.getX(), 150, p.getZ());
        for (int x = -14; x <= 14; x++) for (int z = -10; z <= 20; z++) {
            l.setBlock(base.offset(x, -1, z), Blocks.SMOOTH_STONE.defaultBlockState(), 2);
            for (int y = 0; y < 14; y++) l.setBlock(base.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
        }
        origin = new Vec3(base.getX() + 0.5, base.getY(), base.getZ() + 0.5);
        p.stopRiding();
        p.connection.teleport(origin.x, origin.y, origin.z, 0F, 0F);
        p.setHealth(p.getMaxHealth());
        p.fallDistance = 0;
    }

    private static Zombie zombie(ServerPlayer p, Vec3 at) {
        Zombie z = EntityType.ZOMBIE.create(p.serverLevel());
        z.moveTo(at.x, at.y, at.z, 180F, 0F);
        z.setNoAi(true);
        z.setPersistenceRequired();
        z.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(net.minecraft.world.item.Items.LEATHER_HELMET));   // (no burning in the sun)
        z.setYBodyRot(180F); z.setYHeadRot(180F);
        p.serverLevel().addFreshEntity(z);
        return z;
    }

    public static void build(Minecraft mc, List<Step> l, String focus) {
        String tag = ef() ? "ef" : "noef";
        l.add(new Step(null, () -> { UiTest.noToasts(mc); mc.setScreen(null); UiTest.character(CharacterType.RHALIX);
            UiTest.server(HuntEfTests::arena); }, 50));
        l.add(new Step(null, () -> log("start: epic fight loaded=" + ef() + (ef() ? ", " + Ef.info() : "")), 2));

        // ---- 1. Gravity Barrage's cutscene camera (it switches to third person back); with Epic Fight: as it is, while its
        //         lock-on holds a target (its lock-on camera), and with its camera mode on "always" (its shoulder camera)
        String[] variants = ef() ? new String[]{"plain", "eflockon", "efalways"} : new String[]{"plain"};
        for (String v : variants) {
            l.add(new Step(null, () -> { mc.options.hideGui = false; mc.options.setCameraType(CameraType.FIRST_PERSON); look(mc, 0F, 5F);
                UiTest.server(p -> {
                    arena(p);
                    p.connection.teleport(origin.x, origin.y, origin.z, 0F, 5F);
                    for (int i = 0; i < 3; i++) { Zombie z = zombie(p, origin.add(-2 + i * 2, 0, 6 + i)); if (i == 1) dummy = z.getId(); }
                    com.xenowarriors.skill.SkillManager.resetCooldowns(p);
                    com.xenowarriors.karma.Karma.add(p, 300);
                }); }, 20));
            if (v.equals("eflockon")) l.add(new Step(null, () -> { look(mc, 0F, 5F); Ef.lockOn(true); }, 6));
            if (v.equals("efalways")) l.add(new Step(null, () -> Ef.cameraAlways(true), 2));
            l.add(new Step(null, () -> { log("barrage " + v + ": " + (ef() ? Ef.info() + ", lock-on " + Ef.lockOn() : "")); startCam(1);
                UiTest.server(p -> log("gravity barrage used=" + new com.xenowarriors.skill.rhalix.GravityBarrageSkill().use(p))); }, 45));
            l.add(new Step("huntef_" + tag + "_barrage_" + v, () -> log("barrage " + v + ": cutscene active=" + com.xenowarriors.client.UltimateCutscene.active()
                    + " camera type " + mc.options.getCameraType() + (ef() ? " tps=" + Ef.tps() + " lock-on " + Ef.lockOn() + " camera hook calls " + Ef.cameraPosts() : "")), 40));
            l.add(new Step(null, () -> {
                boolean ok = frames > 10 && maxOff < 0.05;
                log("cutscene camera " + v + ": frames " + frames + ", max distance from the cutscene's camera " + f(maxOff) + " blocks, max yaw off " + f(maxAng)
                        + " -> " + (ok ? "PASS" : "FAIL"));
                camMode = 0; }, 110));
            l.add(new Step(null, () -> { if (ef()) { Ef.lockOn(false); Ef.cameraAlways(false); } log("after cutscene: active=" + com.xenowarriors.client.UltimateCutscene.active() + " camera " + mc.options.getCameraType()); }, 5));
        }

        // ---- 2. the vehicle camera in third person (with Epic Fight: also with its "always" shoulder camera)
        for (String v : variants) {
            if (v.equals("eflockon")) continue;
            l.add(new Step(null, () -> UiTest.server(p -> {
                arena(p);
                var car = com.xenowarriors.vehicle.Vehicles.TYPES.get(com.xenowarriors.vehicle.VehicleKind.CAR).get().create(p.serverLevel());
                car.moveTo(origin.x, origin.y, origin.z + 2, 0, 0);
                p.serverLevel().addFreshEntity(car);
                p.startRiding(car, true);
            }), 40));
            l.add(new Step(null, () -> { if (v.equals("efalways")) Ef.cameraAlways(true);
                mc.setScreen(null); mc.options.hideGui = false; mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); look(mc, 0F, 10F); startCam(2); }, 30));
            l.add(new Step("huntef_" + tag + "_car_" + v, () -> {
                double avg = frames == 0 ? 0 : sumDist / frames;
                log("vehicle camera " + v + ": riding " + (mc.player.getVehicle() == null ? "nothing" : mc.player.getVehicle().getType().toShortString())
                        + (ef() ? " tps=" + Ef.tps() : "") + ", frames " + frames + ", camera " + f(avg) + " blocks from the eyes (vanilla 4, ours backs off to ~7.5) -> " + (avg > 6.0 ? "PASS" : "FAIL"));
                camMode = 0; }, 3));
            l.add(new Step(null, () -> { if (ef()) Ef.cameraAlways(false); mc.options.setCameraType(CameraType.FIRST_PERSON); UiTest.server(p -> { p.stopRiding(); arena(p); }); }, 30));
        }

        // ---- 3. knocked down and squished, on a mob Epic Fight draws itself (a zombie)
        int[] c = new int[8];
        l.add(new Step(null, () -> UiTest.server(p -> {
            Zombie z = zombie(p, origin.add(0, 0, 4));
            dummy = z.getId();
            z.setYRot(90F); z.setYBodyRot(90F); z.setYHeadRot(90F);
        }), 20));
        l.add(new Step(null, () -> { mc.options.hideGui = true; look(mc, 0F, 20F);
            var z = mc.level.getEntity(dummy);
            log("dummy zombie: " + (z == null ? "missing" : z.getType().toShortString()) + (ef() && z instanceof LivingEntity le ? " epic fight draws it=" + Ef.drawsMob(le) : ""));
            c[0] = com.xenowarriors.client.KnockdownClient.tipped;
            UiTest.server(p -> { var e = p.serverLevel().getEntity(dummy); if (e instanceof LivingEntity le) com.xenowarriors.anim.Poses.play(le, com.xenowarriors.anim.Poses.KNOCKDOWN, 200); }); }, 30));
        l.add(new Step("huntef_" + tag + "_knockdown", () -> {
            int n = com.xenowarriors.client.KnockdownClient.tipped - c[0];
            log("knockdown: drawn tipped over " + n + " times in 1.5 s -> " + (n > 5 ? "PASS" : "FAIL")); }, 3));
        l.add(new Step(null, () -> UiTest.server(p -> { var e = p.serverLevel().getEntity(dummy); if (e instanceof LivingEntity le) com.xenowarriors.anim.Poses.clearState(le); }), 10));
        l.add(new Step(null, () -> { c[1] = com.xenowarriors.client.ClientEvents.squishedDraws; com.xenowarriors.client.ClientSkillState.markSquish(dummy, 1F); }, 1));
        for (int i = 0; i < 12; i++) l.add(new Step(i == 8 ? "huntef_" + tag + "_squish" : null, () -> com.xenowarriors.client.ClientSkillState.markSquish(dummy, 1F), 1));
        l.add(new Step(null, () -> {
            int n = com.xenowarriors.client.ClientEvents.squishedDraws - c[1];
            log("squish: drawn squished " + n + " times -> " + (n > 5 ? "PASS" : "FAIL"));
            mc.options.hideGui = false; }, 5));

        // ---- 4. left clicks in Epic Fight's battle mode
        if (ef()) {
            l.add(new Step(null, () -> { UiTest.server(p -> { arena(p); p.getInventory().clearContent(); }); }, 20));
            l.add(new Step(null, () -> { look(mc, 0F, 2F); Ef.battle(true); log("battle mode on: " + Ef.info()); }, 10));
            // control: an empty-hand click is an Epic Fight attack
            clickCheck(mc, l, "control (empty hand)", () -> {}, () -> {}, () -> "");
            // a clash: every click is a push
            clickCheck(mc, l, "clash", () -> { c[2] = com.xenowarriors.client.ClashClient.pushes; com.xenowarriors.client.ClashClient.set(true, 0.5F, 0, "Test"); },
                    () -> com.xenowarriors.client.ClashClient.set(false, 0.5F, 0, ""),
                    () -> "clash pushes " + (com.xenowarriors.client.ClashClient.pushes - c[2]));
            // Rhalix's wires: a click whips
            l.add(new Step(null, () -> UiTest.server(p -> { p.getInventory().selected = 0; p.getInventory().setItem(0, new ItemStack(com.xenowarriors.forms.FormItems.WIRES.get())); }), 10));
            clickCheck(mc, l, "wires", () -> c[3] = com.xenowarriors.client.forms.FormsClient.whipsSent, () -> {},
                    () -> "whips " + (com.xenowarriors.client.forms.FormsClient.whipsSent - c[3]));
            l.add(new Step(null, () -> UiTest.server(p -> p.getInventory().clearContent()), 10));
            // knocked out: no attacks at all
            clickCheck(mc, l, "knocked out", () -> com.xenowarriors.client.party.PartyClient.setKnockedOut(mc.player.getUUID(), 200),
                    () -> com.xenowarriors.client.party.PartyClient.setKnockedOut(mc.player.getUUID(), 0), () -> "knocked out " + com.xenowarriors.client.party.PartyClient.knockedOut(mc.player));
            clickCheck(mc, l, "clash again", () -> { c[2] = com.xenowarriors.client.ClashClient.pushes; com.xenowarriors.client.ClashClient.set(true, 0.5F, 0, "Test"); },
                    () -> com.xenowarriors.client.ClashClient.set(false, 0.5F, 0, ""),
                    () -> "clash pushes " + (com.xenowarriors.client.ClashClient.pushes - c[2]) + " active " + com.xenowarriors.client.ClashClient.active());

            // ---- 5. lock-on key in battle mode: Epic Fight's lock-on, ours follows it
            l.add(new Step(null, () -> UiTest.server(p -> { Zombie z = zombie(p, origin.add(0, 0, 6)); dummy = z.getId(); }), 20));
            l.add(new Step(null, () -> { look(mc, 0F, 5F); com.xenowarriors.client.LockOn.toggle(); }, 10));
            l.add(new Step(null, () -> {
                var t = com.xenowarriors.client.LockOn.target();
                log("lock-on in battle mode: ours " + (t == null ? "none" : t.getType().toShortString()) + ", epic fight's " + Ef.lockOn()
                        + " -> " + (t != null && t.getId() == dummy && Ef.lockOn().contains("zombie") ? "PASS" : "FAIL"));
                com.xenowarriors.client.LockOn.toggle(); }, 10));
            l.add(new Step(null, () -> log("lock-on released: ours " + com.xenowarriors.client.LockOn.target() + ", epic fight's " + Ef.lockOn()), 5));
        }
        l.add(new Step(null, () -> { mc.options.hideGui = false; mc.options.setCameraType(CameraType.FIRST_PERSON); log("done"); }, 5));
    }

    /** One left click in battle mode: did Epic Fight swing, and what did our side get? */
    private static void clickCheck(Minecraft mc, List<Step> l, String what, Runnable before, Runnable after, java.util.function.Supplier<String> result) {
        boolean[] swung = new boolean[1];
        int[] seen = new int[2];
        l.add(new Step(null, () -> { look(mc, 0F, 2F); before.run(); swung[0] = false; mc.player.swinging = false; seen[0] = attackEvents; seen[1] = com.xenowarriors.client.OurClicks.attacksKept;
            press(mc, mc.options.keyAttack, true); }, 1));
        l.add(new Step(null, () -> { swung[0] |= Ef.attacking(); press(mc, mc.options.keyAttack, false); }, 1));
        for (int i = 0; i < 10; i++) l.add(new Step(null, () -> swung[0] |= Ef.attacking(), 1));
        l.add(new Step(null, () -> { log("battle-mode click, " + what + ": epic fight attack " + swung[0] + ", " + result.get()
                + " (attack click events " + (attackEvents - seen[0]) + ", kept-for-us checks " + (com.xenowarriors.client.OurClicks.attacksKept - seen[1])
                + ", canceled " + attackCanceled + ")"); after.run(); }, 25));
    }

    private static int attackEvents;
    private static boolean attackCanceled;

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST, receiveCanceled = true)
    public static void onClick(net.minecraftforge.client.event.InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isAttack()) return;
        attackEvents++;
        attackCanceled = event.isCanceled();
    }

    /** Epic Fight calls (only reached when it's installed). */
    private static final class Ef {
        static yesman.epicfight.client.world.capabilites.entitypatch.player.LocalPlayerPatch patch() {
            return yesman.epicfight.client.ClientEngine.getInstance().getPlayerPatch();
        }

        static String info() {
            try {
                var p = patch();
                return "battle=" + (p != null && p.isEpicFightMode()) + " tpsType=" + yesman.epicfight.config.ClientConfig.getCameraMode();
            } catch (Throwable t) { return "error " + t; }
        }

        static int cameraPosts() {
            try { return (int) com.xenowarriors.client.compat.epicfight.EpicFightCompat.class.getField("cameraPosts").get(null); } catch (Throwable t) { return -1; }
        }

        static boolean tps() {
            try { return yesman.epicfight.api.client.camera.EpicFightCameraAPI.getInstance().isTPSMode(); } catch (Throwable t) { return false; }
        }

        static void battle(boolean on) {
            var p = patch();
            if (p == null) return;
            if (on && !p.isEpicFightMode()) p.toEpicFightMode(true);
            if (!on && p.isEpicFightMode()) p.toVanillaMode(true);
        }

        static boolean attacking() {
            var p = patch();
            if (p == null || p.getEntityState() == null) return false;
            var s = p.getEntityState();
            return Boolean.TRUE.equals(s.getState(yesman.epicfight.api.animation.types.EntityState.ATTACKING))
                    || Boolean.TRUE.equals(s.getState(yesman.epicfight.api.animation.types.EntityState.INACTION));
        }

        static boolean drawsMob(LivingEntity e) {
            try {
                var patch = yesman.epicfight.world.capabilities.EpicFightCapabilities.getEntityPatch(e, yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch.class);
                return yesman.epicfight.client.ClientEngine.getInstance().renderEngine.hasRendererFor(e) && patch != null && patch.overrideRender();
            } catch (Throwable t) { return false; }
        }

        static void lockOn(boolean on) {
            try { yesman.epicfight.api.client.camera.EpicFightCameraAPI.getInstance().setLockOn(on); } catch (Throwable t) { log("lock-on: " + t); }
        }

        private static Object savedMode;

        static void cameraAlways(boolean on) {
            try {
                if (on) { savedMode = yesman.epicfight.config.ClientConfig.cameraMode; yesman.epicfight.config.ClientConfig.cameraMode = yesman.epicfight.config.ClientConfig.TPSType.ALWAYS; }
                else if (savedMode != null) { yesman.epicfight.config.ClientConfig.cameraMode = (yesman.epicfight.config.ClientConfig.TPSType) savedMode; savedMode = null; }
            } catch (Throwable t) { log("camera mode: " + t); }
        }

        static String lockOn() {
            try {
                var api = yesman.epicfight.api.client.camera.EpicFightCameraAPI.getInstance();
                var t = api.getFocusingEntity();
                return api.isLockingOnTarget() ? (t == null ? "on, no target" : t.getType().toShortString()) : "off";
            } catch (Throwable t) { return "error " + t; }
        }
    }
}

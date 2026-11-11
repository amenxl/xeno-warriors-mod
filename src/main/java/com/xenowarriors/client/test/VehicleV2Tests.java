package com.xenowarriors.client.test;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.bot.PartyBotEntity;
import com.xenowarriors.bot.PartyBots;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.character.ModCapabilities;
import com.xenowarriors.client.UiTest;
import com.xenowarriors.client.UiTest.Step;
import com.xenowarriors.client.forms.GunClient;
import com.xenowarriors.forms.Form;
import com.xenowarriors.forms.FormGunItem;
import com.xenowarriors.forms.WeaponForms;
import com.xenowarriors.vehicle.VehicleEntity;
import com.xenowarriors.vehicle.VehicleKind;
import com.xenowarriors.vehicle.Vehicles;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Vehicles round 2 (focus "vehv2", "dmzvehv2", "efvehv2", "packvehv2"; a suffix picks one part: "_face", "_shoot",
 * "_models"). Every result is an "XW-TEST vehv2 ..." line.
 * <ol>
 *   <li>Facing: the vehicle turns to where its driver looks (car, Mk II car on the ground and flying, helicopter),
 *   smoothly (turn rate capped, no back-and-forth), the server agrees with the driver's game, a passenger's look
 *   never steers it; A/D steer (land) / strafe (helicopter), S reverses.</li>
 *   <li>Shooting from seats: a summoned gun from the driver's seat, from a back seat past the rider in front, from the
 *   helicopter; a bot passenger with a gun; no hits on the vehicle or its riders; a non-gun item fires nothing.</li>
 *   <li>Model stills of each vehicle: front 3/4, side, riders in the seats, the driver's view.</li>
 * </ol>
 */
public final class VehicleV2Tests {
    private VehicleV2Tests() {}

    public static boolean handles(String focus) { return focus != null && focus.replaceFirst("^(dmz|ef|pack)", "").startsWith("vehv2"); }

    private static void log(String s) { XenoWarriors.LOGGER.info("XW-TEST vehv2 " + s); }

    private static String f(double d) { return String.format(Locale.ROOT, "%.1f", d); }

    private static boolean ef() { return net.minecraftforge.fml.ModList.get().isLoaded("epicfight"); }

    private static List<Step> L;
    private static void step(String shot, Runnable a, int delay) { L.add(new Step(shot == null ? null : "vehv2_" + shot, () -> { try { a.run(); } catch (Throwable t) { log("step error " + t); XenoWarriors.LOGGER.error("XW-TEST vehv2", t); } }, delay)); }
    private static void server(java.util.function.Consumer<ServerPlayer> fn) {
        UiTest.server(p -> { try { fn.accept(p); } catch (Throwable t) { log("server step error " + t); XenoWarriors.LOGGER.error("XW-TEST vehv2", t); } });
    }

    private static BlockPos base;
    private static int pass, fail;
    private static final String TAG = "xw_vehv2";

    private static void check(String what, boolean ok, String detail) {
        if (ok) pass++; else fail++;
        log(what + ": " + detail + " -> " + (ok ? "PASS" : "FAIL"));
    }

    public static void build(Minecraft mc, List<Step> l, String focus) {
        L = l;
        String part = focus.replaceFirst("^(dmz|ef|pack)", "").substring("vehv2".length());
        boolean all = part.isEmpty();
        start(mc);
        if (all || part.contains("models")) models(mc);
        if (all || part.contains("face")) facing(mc);
        if (all || part.contains("shoot")) shooting(mc);
        step(null, () -> { mc.options.keyUp.setDown(false); mc.options.keyDown.setDown(false); mc.options.keyLeft.setDown(false); mc.options.keyRight.setDown(false);
            mc.options.keyJump.setDown(false); mc.options.keySprint.setDown(false); GunClient.testTrigger = null; GunClient.testAim = null;
            mc.setCameraEntity(mc.player); mc.options.hideGui = false; mc.options.setCameraType(CameraType.FIRST_PERSON);
            server(p -> { p.stopRiding(); clear(p); });
            log("summary: " + pass + " passed, " + fail + " failed"); }, 10);
    }

    // ================================================================== setup

    /** A big flat stone floor high up (80 x 80, clear 24 up), day, no mobs spawning. */
    private static void start(Minecraft mc) {
        step(null, () -> { UiTest.noToasts(mc); mc.setScreen(null); mc.options.hideGui = false;
            UiTest.character(CharacterType.MIKIKO);
            server(p -> {
                com.xenowarriors.character.Seasons.set(p.server, 2);
                p.setGameMode(GameType.CREATIVE);
                ServerLevel l = p.serverLevel();
                l.setDayTime(6000);
                l.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false, p.server);
                l.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING).set(false, p.server);
                l.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_WEATHER_CYCLE).set(false, p.server);
                l.setWeatherParameters(6000, 0, false, false);
                BlockPos b = BlockPos.containing(p.getX(), Math.max(p.getY() + 30, 120), p.getZ());
                for (int x = -40; x <= 40; x++) for (int z = -40; z <= 40; z++) {
                    l.setBlock(b.offset(x, -1, z), ((x + z) & 7) == 0 ? Blocks.STONE.defaultBlockState() : Blocks.SMOOTH_STONE.defaultBlockState(), 2);
                    for (int y = 0; y < 24; y++) if (!l.getBlockState(b.offset(x, y, z)).isAir()) l.setBlock(b.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                }
                base = b;
                home(p);
                p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20 * 1800, 4, false, false));
                p.addEffect(new MobEffectInstance(MobEffects.SATURATION, 20 * 1800, 0, false, false));
                p.getCapability(ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> { d.bladeLevel = 60; d.bladeXp = 0; });
                com.xenowarriors.compat.GunLevel.setLevel(p, 50);
                WeaponForms.sync(p, true);
                log("start: base " + b.toShortString() + " ef=" + ef() + " dmz=" + net.minecraftforge.fml.ModList.get().isLoaded("dragonminez")
                        + " tacz=" + com.xenowarriors.compat.TaczGuns.available());
            });
        }, 80);
    }

    private static void home(ServerPlayer p) {
        p.stopRiding();
        p.getAbilities().flying = false; p.onUpdateAbilities();
        p.connection.teleport(base.getX() + .5, base.getY(), base.getZ() + .5, 0F, 0F);
        clear(p);
    }

    private static void clear(ServerPlayer p) {
        if (base == null) return;
        AABB box = new AABB(base).inflate(60, 40, 60);
        for (Entity e : p.serverLevel().getEntitiesOfClass(Entity.class, box, e -> !(e instanceof net.minecraft.world.entity.player.Player))) {
            if (e instanceof VehicleEntity v) v.ejectPassengers();
            if (e instanceof PartyBotEntity b) PartyBots.forgetFake(b.getUUID());
            e.discard();
        }
        for (PartyBotEntity b : PartyBots.bots(p)) { PartyBots.forgetFake(b.getUUID()); b.discard(); }
    }

    private static int vehicle = -1;

    /** The player in the driver's seat of a fresh one at (dx, dz) from the base, facing +z (yaw 0). */
    private static void ride(VehicleKind k, double dx, double dz) {
        step(null, () -> server(p -> {
            home(p);
            p.setGameMode(GameType.CREATIVE);
            VehicleEntity v = spawn(p, k, dx, dz, 0F);
            vehicle = v.getId();
            p.setYRot(0);
            p.startRiding(v, true);
        }), 25);
    }

    private static VehicleEntity spawn(ServerPlayer p, VehicleKind k, double dx, double dz, float yaw) {
        ServerLevel l = p.serverLevel();
        VehicleEntity v = Vehicles.TYPES.get(k).get().create(l);
        v.moveTo(base.getX() + 0.5 + dx, base.getY(), base.getZ() + 0.5 + dz, yaw, 0);
        v.yBodyRot = yaw; v.yHeadRot = yaw; v.yRotO = yaw;
        v.addTag(TAG);
        l.addFreshEntity(v);
        return v;
    }

    private static VehicleEntity clientVehicle(Minecraft mc) { return mc.level != null && mc.level.getEntity(vehicle) instanceof VehicleEntity v ? v : null; }

    private static VehicleEntity serverVehicle(Minecraft mc) {
        var s = mc.getSingleplayerServer();
        VehicleEntity c = clientVehicle(mc);
        if (s == null || c == null) return null;
        var sl = s.getLevel(c.level().dimension());
        return sl != null && sl.getEntity(vehicle) instanceof VehicleEntity v ? v : null;
    }

    // ------------------------------------------------------------------ where the driver looks (Epic Fight's own camera when it's on)

    private static boolean tpsCam() { return ef() && Ef.tps(); }

    private static float view(Minecraft mc) { return tpsCam() ? Ef.camYaw() : mc.player.getYRot(); }

    private static void setView(Minecraft mc, float yaw) {
        if (tpsCam()) Ef.setCamYaw(yaw);
        else { mc.player.setYRot(yaw); mc.player.yRotO = yaw; mc.player.setYHeadRot(yaw); }
    }

    // ================================================================== 1. facing

    private static void facing(Minecraft mc) {
        turn(mc, VehicleKind.CAR, "car", false, CameraType.THIRD_PERSON_BACK);
        turn(mc, VehicleKind.CAR, "car first person", false, CameraType.FIRST_PERSON);
        turn(mc, VehicleKind.CAR_MK2, "car mk2", false, CameraType.THIRD_PERSON_BACK);
        turn(mc, VehicleKind.CAR_MK2, "car mk2 flying", true, CameraType.THIRD_PERSON_BACK);
        turn(mc, VehicleKind.HELICOPTER, "helicopter", true, CameraType.THIRD_PERSON_BACK);
        turn(mc, VehicleKind.MOTORBIKE, "motorbike", false, CameraType.THIRD_PERSON_BACK);
        if (ef()) {
            step(null, () -> { com.xenowarriors.compat.epicfight.EfGunCheck.battleMode(); Ef.cameraAlways(true); log("epic fight: battle mode on, " + Ef.info()); }, 10);
            turn(mc, VehicleKind.CAR, "car (epic fight battle mode)", false, CameraType.THIRD_PERSON_BACK);
            turn(mc, VehicleKind.HELICOPTER, "helicopter (epic fight battle mode)", true, CameraType.THIRD_PERSON_BACK);
            turn(mc, VehicleKind.CAR, "car first person (epic fight battle mode)", false, CameraType.FIRST_PERSON);
            step(null, () -> Ef.cameraAlways(false), 5);
        }
        controls(mc);
        passenger(mc);
        dismount(mc);
    }

    /**
     * Driving forward (or hovering), the driver flicks their view 90 degrees right, then sweeps it back left: the
     * vehicle should come round within a second, never step more than its turn rate a tick, never wobble back, and
     * the server's yaw should match the driver's game.
     */
    private static void turn(Minecraft mc, VehicleKind k, String label, boolean fly, CameraType cam) {
        ride(k, 0, -24);
        step(null, () -> { mc.options.hideGui = false; mc.options.setCameraType(cam); setView(mc, 0F); mc.player.setXRot(10F); if (fly) mc.options.keyJump.setDown(true); }, fly ? 45 : 5);
        step(null, () -> { mc.options.keyJump.setDown(false); mc.options.keyUp.setDown(true); }, 20);
        List<float[]> s = new ArrayList<>();                // client yaw, server yaw, view
        step(null, () -> { VehicleEntity v = clientVehicle(mc); s.clear();
            log(label + ": before the turn, view " + f(view(mc)) + " vehicle " + (v == null ? "?" : f(v.getYRot())) + " tps camera=" + tpsCam());
            setView(mc, view(mc) + 90F); }, 1);
        for (int i = 0; i < 40; i++) {
            int ii = i;
            step(i == 6 ? "turn_" + label.replaceAll("[^a-z0-9]+", "_") : null, () -> {
                VehicleEntity v = clientVehicle(mc), sv = serverVehicle(mc);
                s.add(new float[]{v == null ? Float.NaN : v.getYRot(), sv == null ? Float.NaN : sv.getYRot(), view(mc)});
                if (ii == 39) judgeFlick(label, s);
            }, 1);
        }
        // a steady mouse sweep back left (4 degrees a tick for 30 ticks), then let it settle
        List<float[]> w = new ArrayList<>();
        for (int i = 0; i < 45; i++) {
            int ii = i;
            step(null, () -> {
                if (ii < 30) setView(mc, view(mc) - 4F);
                VehicleEntity v = clientVehicle(mc), sv = serverVehicle(mc);
                w.add(new float[]{v == null ? Float.NaN : v.getYRot(), sv == null ? Float.NaN : sv.getYRot(), view(mc)});
                if (ii == 44) judgeSweep(label, w);
            }, 1);
        }
        step(null, () -> { mc.options.keyUp.setDown(false); server(VehicleV2Tests::home); }, 10);
    }

    private static void judgeFlick(String label, List<float[]> s) {
        int reach = -1, flips = 0;
        float maxStep = 0, lastD = 0, maxSrv = 0;
        for (int i = 0; i < s.size(); i++) {
            float[] r = s.get(i);
            float err = Math.abs(Mth.wrapDegrees(r[2] - r[0]));
            if (reach < 0 && err < 5F) reach = i + 1;
            if (i > 0) {
                float d = Mth.wrapDegrees(r[0] - s.get(i - 1)[0]);
                maxStep = Math.max(maxStep, Math.abs(d));
                if (Math.abs(d) > 0.3F && Math.abs(lastD) > 0.3F && Math.signum(d) != Math.signum(lastD)) flips++;
                if (Math.abs(d) > 0.3F) lastD = d;
            }
            if (i >= s.size() - 10 && !Float.isNaN(r[1])) maxSrv = Math.max(maxSrv, Math.abs(Mth.wrapDegrees(r[1] - r[0])));
        }
        float[] last = s.get(s.size() - 1);
        float fin = Math.abs(Mth.wrapDegrees(last[2] - last[0]));
        StringBuilder trace = new StringBuilder();
        for (int i = 0; i < Math.min(20, s.size()); i += 2) trace.append(f(s.get(i)[0])).append(' ');
        log(label + " flick 90: yaw trace " + trace);
        check(label + " follows a 90 degree flick", reach > 0 && reach <= 30 && fin < 3F,
                "within 5 deg after " + reach + " ticks, off by " + f(fin) + " deg after 2 s");
        check(label + " turns smoothly", maxStep <= 12F && flips == 0, "biggest step " + f(maxStep) + " deg/tick, direction flips " + flips);
        check(label + " server agrees", maxSrv < 2F, "server vs client yaw (settled) " + f(maxSrv) + " deg");
    }

    private static void judgeSweep(String label, List<float[]> w) {
        float maxErr = 0, flips = 0, lastD = 0, maxSrv = 0;
        for (int i = 0; i < w.size(); i++) {
            float[] r = w.get(i);
            if (i >= 8 && i < 30) maxErr = Math.max(maxErr, Math.abs(Mth.wrapDegrees(r[2] - r[0])));
            if (i > 0) {
                float d = Mth.wrapDegrees(r[0] - w.get(i - 1)[0]);
                if (Math.abs(d) > 0.3F && Math.abs(lastD) > 0.3F && Math.signum(d) != Math.signum(lastD)) flips++;
                if (Math.abs(d) > 0.3F) lastD = d;
            }
            if (!Float.isNaN(r[1]) && i > 2) maxSrv = Math.max(maxSrv, Math.abs(Mth.wrapDegrees(r[1] - r[0])));
        }
        float[] last = w.get(w.size() - 1);
        float fin = Math.abs(Mth.wrapDegrees(last[2] - last[0]));
        check(label + " tracks a mouse sweep", maxErr < 20F && fin < 3F && flips == 0,
                "lag during the sweep at most " + f(maxErr) + " deg, " + f(fin) + " deg off after settling, flips " + (int) flips);
        check(label + " server keeps up during the sweep", maxSrv < 10F, "server vs client yaw at most " + f(maxSrv) + " deg (one tick of turning)");
    }

    /** A/D steer a land vehicle (the view swings with it), strafe the helicopter; S reverses without turning round. */
    private static void controls(Minecraft mc) {
        float[] c = new float[6];
        ride(VehicleKind.CAR, 0, -24);
        step(null, () -> { mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); setView(mc, 0F); mc.options.keyUp.setDown(true); }, 25);
        step(null, () -> { VehicleEntity v = clientVehicle(mc); c[0] = view(mc); c[1] = v == null ? 0 : v.getYRot(); mc.options.keyRight.setDown(true); }, 20);
        step("car_steer_right", () -> { VehicleEntity v = clientVehicle(mc); mc.options.keyRight.setDown(false);
            float dv = Mth.wrapDegrees(view(mc) - c[0]), dy = v == null ? 0 : Mth.wrapDegrees(v.getYRot() - c[1]);
            check("car D steers right (view and car)", dv > 15F && dy > 15F && Math.abs(Mth.wrapDegrees(view(mc) - v.getYRot())) < 15F,
                    "view turned " + f(dv) + ", car turned " + f(dy) + " (positive = right)"); }, 2);
        step(null, () -> { mc.options.keyUp.setDown(false); mc.options.keyDown.setDown(true); }, 50);
        step(null, () -> { VehicleEntity v = clientVehicle(mc); mc.options.keyDown.setDown(false);
            double dx = v.getX() - v.xo, dz = v.getZ() - v.zo;
            double heading = Math.toDegrees(Math.atan2(dz, dx)) - 90;
            float diff = Math.abs(Mth.wrapDegrees((float) heading - v.getYRot()));
            check("car S reverses", v.speed() < -0.05 && diff > 150F, "speed " + String.format(Locale.ROOT, "%.2f", v.speed()) + ", moving " + f(diff) + " deg from its facing"); }, 2);
        // helicopter: D slides it right without turning it
        ride(VehicleKind.HELICOPTER, 0, -24);
        step(null, () -> { mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); setView(mc, 0F); mc.options.keyJump.setDown(true); }, 40);
        step(null, () -> { mc.options.keyJump.setDown(false); VehicleEntity v = clientVehicle(mc); c[2] = (float) v.getX(); c[3] = (float) v.getZ(); c[4] = v.getYRot(); mc.options.keyRight.setDown(true); }, 30);
        step("heli_strafe", () -> { VehicleEntity v = clientVehicle(mc); mc.options.keyRight.setDown(false);
            // facing +z (yaw 0): its right is -x
            double right = -(v.getX() - c[2]), fwd = v.getZ() - c[3];
            check("helicopter D strafes right", right > 2.0 && Math.abs(Mth.wrapDegrees(v.getYRot() - c[4])) < 3F,
                    "slid " + f(right) + " blocks right, " + f(fwd) + " forward, turned " + f(Mth.wrapDegrees(v.getYRot() - c[4])) + " deg"); }, 2);
        step(null, () -> server(VehicleV2Tests::home), 10);
    }

    /**
     * An NPC at the wheel (a husk tagged as a driver, sent somewhere with npcDriveTo) and a party bot riding along that
     * keeps looking 90 degrees off to the side: the car heads for the spot facing where it's going; the passenger's look
     * never turns it. (A player is always put in the driver's seat when they get in with mobs - vanilla - so a player
     * passenger only happens with two players; their game never moves the car: isControlledByLocalInstance.)
     */
    private static void passenger(Minecraft mc) {
        int[] ids = new int[3];
        double[] worst = new double[3];
        step(null, () -> server(p -> {
            home(p);
            VehicleEntity v = spawn(p, VehicleKind.CAR, -10, -24, 0F);
            vehicle = v.getId(); ids[0] = v.getId();
            var h = EntityType.HUSK.create(p.serverLevel());
            h.moveTo(v.position()); h.setNoAi(true); h.setPersistenceRequired(); h.setInvulnerable(true); h.addTag(TAG); h.addTag(VehicleEntity.NPC_DRIVER_TAG);
            p.serverLevel().addFreshEntity(h);
            h.startRiding(v, true);
            PartyBotEntity b = PartyBots.spawn(p, CharacterType.KAERU, null);
            if (b != null) { b.moveTo(v.getX() + 2, v.getY(), v.getZ(), 0, 0); b.startRiding(v, true); ids[1] = b.getId(); }
            v.npcDriveTo(new Vec3(base.getX() + 14.5, base.getY(), base.getZ() + 10.5), 0.6);
            log("npc driver: driving=" + v.npcDriving() + " controller " + v.getControllingPassenger() + ", bot riding " + (b != null && b.getVehicle() == v));
            p.connection.teleport(base.getX() + 0.5, base.getY() + 8, base.getZ() - 34, 0F, 30F);
        }), 10);
        step(null, () -> { mc.options.setCameraType(CameraType.FIRST_PERSON); }, 5);
        for (int i = 0; i < 40; i++) {
            int ii = i;
            step(i == 25 ? "npc_driver" : null, () -> server(p -> {
                if (!(p.serverLevel().getEntity(ids[0]) instanceof VehicleEntity v)) return;
                if (p.serverLevel().getEntity(ids[1]) instanceof PartyBotEntity b) { b.setYRot(v.getYRot() + 90F); b.setYHeadRot(v.getYRot() + 90F); }
                double dx = v.getX() - v.xo, dz = v.getZ() - v.zo;
                if (Math.hypot(dx, dz) > 0.05) {
                    double heading = Math.toDegrees(Math.atan2(dz, dx)) - 90;
                    worst[0] = Math.max(worst[0], Math.abs(Mth.wrapDegrees((float) heading - v.getYRot())));
                }
                if (ii > 0) worst[1] = Math.max(worst[1], Math.abs(Mth.wrapDegrees(v.getYRot() - v.yRotO)));
                if (ii == 39) {
                    Vec3 d = v.npcDestination();
                    double left = d == null ? -1 : Math.hypot(d.x - v.getX(), d.z - v.getZ());
                    check("an NPC driver faces where it's going", worst[0] < 25 && worst[1] <= 7.01, "motion vs facing at most " + f(worst[0]) + " deg, turn at most " + f(worst[1]) + " deg/tick");
                    check("an NPC driver gets there (a passenger looking away doesn't steer)", left >= 0 && left < 5, f(left) + " blocks from the spot after 4 s");
                }
            }), 2);
        }
        step(null, () -> server(VehicleV2Tests::home), 10);
    }

    /** Getting in faces you forward in the seat; getting out keeps where you were looking. */
    private static void dismount(Minecraft mc) {
        step(null, () -> server(p -> {
            home(p);
            VehicleEntity v = spawn(p, VehicleKind.CAR, 0, -24, 0F);
            vehicle = v.getId();
            p.connection.teleport(v.getX() + 3, v.getY(), v.getZ(), 90F, 0F);  // standing to its left, looking across it
        }), 10);
        step(null, () -> server(p -> { if (p.serverLevel().getEntity(vehicle) instanceof VehicleEntity v) p.startRiding(v, true); }), 6);
        step(null, () -> { VehicleEntity v = clientVehicle(mc);
            float d = v == null ? 999 : Math.abs(Mth.wrapDegrees(view(mc) - v.getYRot()));
            check("getting in faces you forward", d < 5F && v != null && Math.abs(Mth.wrapDegrees(v.getYRot())) < 5F,
                    "view vs car " + f(d) + " deg, car yaw " + (v == null ? "?" : f(v.getYRot())) + " (it was parked at 0; you looked at 90)"); }, 2);
        step(null, () -> { mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); setView(mc, 40F); }, 30);
        float[] y = new float[2];
        step(null, () -> { VehicleEntity v = clientVehicle(mc); y[0] = view(mc); y[1] = v == null ? 0 : v.getYRot(); server(Entity::stopRiding); }, 10);
        step("dismounted", () -> { VehicleEntity v = clientVehicle(mc);
            check("getting out keeps your look, car stays put", Math.abs(Mth.wrapDegrees(view(mc) - y[0])) < 3F && v != null && Math.abs(Mth.wrapDegrees(v.getYRot() - y[1])) < 2F,
                    "view " + f(y[0]) + " -> " + f(view(mc)) + ", car " + f(y[1]) + " -> " + (v == null ? "?" : f(v.getYRot())) + ", riding " + mc.player.isPassenger()); }, 2);
        step(null, () -> server(VehicleV2Tests::home), 10);
    }

    // ================================================================== 2. shooting from vehicles

    private static final AtomicInteger PROJ = new AtomicInteger();
    /** Damage dealt (after armor etc.) per entity id, server side - mobs heal and get rescaled, so health alone lies. */
    private static final java.util.Map<Integer, Float> DMG = new java.util.concurrent.ConcurrentHashMap<>();
    private static float dmg(int id) { return DMG.getOrDefault(id, 0F); }
    private static boolean listening;
    private static int shooterId = -1;

    private static LivingEntity dummy(ServerPlayer p, Vec3 at) {
        var h = EntityType.HUSK.create(p.serverLevel());
        h.moveTo(at.x, at.y, at.z, 180F, 0F);
        h.setNoAi(true); h.setPersistenceRequired();
        h.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000); h.setHealth(1000);
        h.addTag(TAG);
        p.serverLevel().addFreshEntity(h);
        return h;
    }

    /** The local player looking at a point (yaw/pitch), and the server told the same at once. */
    private static void aimAt(Minecraft mc, Vec3 at) {
        var p = mc.player;
        Vec3 d = at.subtract(p.getEyePosition());
        float yaw = (float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90F), pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
        setView(mc, yaw);
        p.setXRot(pitch); p.xRotO = pitch;
    }

    private static Vec3 center(Minecraft mc, int id) { Entity e = mc.level.getEntity(id); return e == null ? Vec3.ZERO : e.getBoundingBox().getCenter(); }

    private static float hp(ServerPlayer p, int id) { return p.serverLevel().getEntity(id) instanceof LivingEntity le ? le.getHealth() : -1; }

    private static void shooting(Minecraft mc) {
        if (!listening) {
            listening = true;
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.entity.EntityJoinLevelEvent e) -> {
                if (!e.getLevel().isClientSide() && e.getEntity() instanceof net.minecraft.world.entity.projectile.Projectile pr && pr.getOwner() != null && pr.getOwner().getId() == shooterId) PROJ.incrementAndGet();
            });
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.entity.living.LivingDamageEvent e) -> {
                if (!e.getEntity().level().isClientSide) DMG.merge(e.getEntity().getId(), e.getAmount(), Float::sum);
            });
        }
        step(null, () -> server(p -> { home(p); p.setGameMode(GameType.SURVIVAL); p.getInventory().clearContent(); p.getPersistentData().remove(WeaponForms.BROKEN); }), 10);
        gunFrom(mc, "car driver's seat", VehicleKind.CAR, 0, false);
        gunFrom(mc, "motorbike", VehicleKind.MOTORBIKE, 0, false);
        gunFrom(mc, "helicopter pilot's seat", VehicleKind.HELICOPTER, 0, true);
        gunFrom(mc, "car mk2 flying", VehicleKind.CAR_MK2, 0, true);
        if (net.minecraftforge.fml.ModList.get().isLoaded("dragonminez")) blasterFrom(mc);
        if (com.xenowarriors.compat.TaczGuns.available()) taczFrom(mc);
        notAGun(mc);
        botPassenger(mc);
        step(null, () -> server(p -> { home(p); p.getInventory().clearContent(); p.setGameMode(GameType.CREATIVE); }), 10);
    }

    /**
     * The player in seat `seat` (riders in the seats before it: villagers), a summoned pistol, a 1000 HP husk 12 blocks
     * ahead: ten shots. The husk loses health; the vehicle and the riders don't.
     */
    private static void gunFrom(Minecraft mc, String label, VehicleKind k, int seat, boolean fly) {
        int[] ids = new int[6];
        float[] hp0 = new float[6];
        step(null, () -> server(p -> {
            home(p);
            VehicleEntity v = spawn(p, k, 0, -24, 0F);
            vehicle = v.getId(); ids[0] = v.getId();
            for (int i = 0; i < seat; i++) {
                var vil = EntityType.VILLAGER.create(p.serverLevel());
                vil.moveTo(v.position()); vil.setNoAi(true); vil.setPersistenceRequired(); vil.addTag(TAG);
                p.serverLevel().addFreshEntity(vil);
                vil.startRiding(v, true);
                ids[1 + i] = vil.getId();
            }
            p.startRiding(v, true);
            shooterId = p.getId();
            WeaponForms.summon(p, Form.PISTOL, false);
            log(label + ": in seat " + v.getPassengers().indexOf(p) + " of " + k.id + ", holding " + p.getMainHandItem().getItem());
        }), 25);
        if (fly) {
            step(null, () -> { mc.options.setCameraType(CameraType.FIRST_PERSON); setView(mc, 0F); mc.options.keyJump.setDown(true); }, 40);
            step(null, () -> mc.options.keyJump.setDown(false), 20);
        }
        step(null, () -> server(p -> {
            VehicleEntity v = (VehicleEntity) p.serverLevel().getEntity(ids[0]);
            if (v == null) return;
            Vec3 eye = p.getEyePosition();
            var d = dummy(p, new Vec3(eye.x, base.getY(), eye.z + 12));
            ids[5] = d.getId();
            hp0[0] = v.getHealth(); hp0[5] = d.getHealth();
            for (int i = 1; i <= seat; i++) hp0[i] = hp(p, ids[i]);
            log(label + ": target 12 blocks ahead, " + f(eye.y - base.getY()) + " blocks up");
        }), 10);
        step(null, () -> { mc.options.setCameraType(CameraType.FIRST_PERSON); mc.options.hideGui = false; aimAt(mc, center(mc, ids[5])); }, 15);
        int[] sent = new int[2];
        step(null, () -> { aimAt(mc, center(mc, ids[5])); sent[0] = GunClient.shotsSent; server(p -> WeaponForms.SHOTS = 0); GunClient.testTrigger = true; }, 1);
        for (int i = 0; i < 10; i++) step(i == 3 ? "shoot_" + label.replaceAll("[^a-z0-9]+", "_") : null, () -> { aimAt(mc, center(mc, ids[5])); GunClient.testTrigger = !GunClient.testTrigger; }, 4);
        step(null, () -> { GunClient.testTrigger = false; sent[1] = GunClient.shotsSent - sent[0]; }, 10);
        step(null, () -> server(p -> {
            float lost = dmg(ids[5]), veh = hp0[0] - hp(p, ids[0]);
            float riders = 0;
            for (int i = 1; i <= seat; i++) riders += dmg(ids[i]);
            check("player fires a summoned gun from the " + label, sent[1] >= 3 && WeaponForms.SHOTS >= 3 && lost > 0 && veh == 0 && riders == 0,
                    "client sent " + sent[1] + " shots, server fired " + WeaponForms.SHOTS + ", target lost " + f(lost) + " HP, vehicle lost " + f(veh) + ", riders lost " + f(riders));
            p.getInventory().clearContent();
        }), 5);
        step(null, () -> server(VehicleV2Tests::home), 10);
    }

    /** DragonMineZ Blaster (Burst Cannon) from the driver's seat: the blasts fly out and hit the target, not the car. */
    private static void blasterFrom(Minecraft mc) {
        int[] ids = new int[2];
        float[] hp0 = new float[2];
        step(null, () -> server(p -> {
            home(p);
            VehicleEntity v = spawn(p, VehicleKind.CAR, 0, -24, 0F);
            vehicle = v.getId(); ids[0] = v.getId();
            p.startRiding(v, true);
            if (com.xenowarriors.item.ModItems.BURST_CANNON != null) p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(com.xenowarriors.item.ModItems.BURST_CANNON.get()));
            var d = dummy(p, new Vec3(p.getX(), base.getY(), p.getZ() + 12));
            ids[1] = d.getId();
            hp0[0] = v.getHealth(); hp0[1] = d.getHealth();
        }), 25);
        step(null, () -> { mc.options.setCameraType(CameraType.FIRST_PERSON); aimAt(mc, center(mc, ids[1])); }, 10);
        for (int i = 0; i < 3; i++) step(i == 1 ? "shoot_blaster_car" : null, () -> { aimAt(mc, center(mc, ids[1]));
            server(p -> com.xenowarriors.compat.DmzGuns.fire(p, com.xenowarriors.compat.DmzGuns.Kind.BURST, null)); }, 20);
        step(null, () -> server(p -> {
            float lost = dmg(ids[1]), veh = hp0[0] - hp(p, ids[0]);
            check("player fires a Blaster from the car", lost > 0 && veh == 0, "target lost " + f(lost) + " HP, car lost " + f(veh) + " (ki blasts on: " + com.xenowarriors.compat.DmzGuns.on() + ")");
            p.getInventory().clearContent();
        }), 5);
    }

    /** A TACZ gun fired by the player from the helicopter (TACZ's own bullet). */
    private static void taczFrom(Minecraft mc) {
        int[] ids = new int[2];
        float[] hp0 = new float[2];
        step(null, () -> server(p -> {
            home(p);
            VehicleEntity v = spawn(p, VehicleKind.CAR, 0, -24, 0F);
            vehicle = v.getId(); ids[0] = v.getId();
            p.startRiding(v, true);
            p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, com.xenowarriors.compat.TaczGuns.gun("tacz:m4a1", 30));
            var d = dummy(p, new Vec3(p.getX(), base.getY(), p.getZ() + 12));
            ids[1] = d.getId();
            hp0[0] = v.getHealth(); hp0[1] = d.getHealth();
            com.xenowarriors.compat.TaczGuns.draw(p);
        }), 40);
        step(null, () -> { mc.options.setCameraType(CameraType.FIRST_PERSON); aimAt(mc, center(mc, ids[1])); }, 10);
        int[] n = new int[1];
        for (int i = 0; i < 8; i++) step(i == 3 ? "shoot_tacz_car" : null, () -> { aimAt(mc, center(mc, ids[1]));
            server(p -> { Vec3 d = p.serverLevel().getEntity(ids[1]).getBoundingBox().getCenter().subtract(p.getEyePosition());
                float yaw = (float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90F), pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
                if (com.xenowarriors.compat.TaczGuns.shoot(p, pitch, yaw)) n[0]++; }); }, 8);
        step(null, () -> server(p -> {
            float lost = dmg(ids[1]), veh = hp0[0] - hp(p, ids[0]);
            check("player fires a TACZ gun from the car", n[0] > 0 && lost > 0 && veh == 0, n[0] + " shots, target lost " + f(lost) + " HP, car lost " + f(veh));
            p.getInventory().clearContent();
        }), 5);
    }

    /** A stick in hand in the driver's seat: clicking fires nothing and hurts nobody. */
    private static void notAGun(Minecraft mc) {
        int[] ids = new int[2];
        float[] hp0 = new float[2];
        step(null, () -> server(p -> {
            home(p);
            VehicleEntity v = spawn(p, VehicleKind.CAR, 0, -24, 0F);
            vehicle = v.getId(); ids[0] = v.getId();
            p.startRiding(v, true);
            shooterId = p.getId();
            p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
            var d = dummy(p, new Vec3(p.getX(), base.getY(), p.getZ() + 12));
            ids[1] = d.getId();
            hp0[0] = v.getHealth(); hp0[1] = d.getHealth();
        }), 25);
        int[] c = new int[2];
        step(null, () -> { mc.options.setCameraType(CameraType.FIRST_PERSON); aimAt(mc, center(mc, ids[1])); PROJ.set(0); c[0] = GunClient.shotsSent; }, 5);
        for (int i = 0; i < 6; i++) step(null, () -> { aimAt(mc, center(mc, ids[1]));
            net.minecraft.client.KeyMapping.click(mc.options.keyAttack.getKey()); net.minecraft.client.KeyMapping.click(mc.options.keyUse.getKey()); }, 5);
        step(null, () -> server(p -> {
            float lost = dmg(ids[1]), veh = hp0[0] - hp(p, ids[0]);
            check("a non-gun item fires nothing from a seat", PROJ.get() == 0 && GunClient.shotsSent == c[0] && lost == 0 && veh == 0,
                    "projectiles " + PROJ.get() + ", gun shots " + (GunClient.shotsSent - c[0]) + ", target lost " + f(lost) + ", car lost " + f(veh));
            p.getInventory().clearContent();
        }), 5);
    }

    /** A party bot (Rhalix) in the back seat with a gun (Blaster with DragonMineZ, a TACZ gun with TACZ, else a bow), the player driving. */
    private static void botPassenger(Minecraft mc) {
        int[] ids = new int[3];
        float[] hp0 = new float[3];
        String[] gun = {""};
        step(null, () -> server(p -> {
            home(p);
            p.setGameMode(GameType.SURVIVAL);
            VehicleEntity v = spawn(p, VehicleKind.CAR, 0, -24, 0F);
            vehicle = v.getId(); ids[0] = v.getId();
            p.startRiding(v, true);
            PartyBotEntity b = PartyBots.spawn(p, CharacterType.RHALIX, null);
            if (b == null) { log("bot passenger: no bot"); return; }
            b.inventory.clearContent();
            for (EquipmentSlot es : EquipmentSlot.values()) b.setItemSlot(es, ItemStack.EMPTY);
            b.moveTo(v.getX() + 2, v.getY(), v.getZ(), 0, 0);
            var hp = b.getAttribute(Attributes.MAX_HEALTH); if (hp != null) hp.setBaseValue(200); b.setHealth(b.getMaxHealth());
            ItemStack g = com.xenowarriors.compat.TaczGuns.available() ? com.xenowarriors.compat.TaczGuns.gun("tacz:m4a1", 30)
                    : com.xenowarriors.compat.DmzGuns.on() && com.xenowarriors.item.ModItems.BLASTER_RIFLE != null ? new ItemStack(com.xenowarriors.item.ModItems.BLASTER_RIFLE.get())
                    : new ItemStack(Items.BOW);
            gun[0] = String.valueOf(g.getItem());
            b.setItemSlot(EquipmentSlot.MAINHAND, g);
            b.handPicked = b.tickCount;
            var vil = EntityType.VILLAGER.create(p.serverLevel());                // (front passenger seat taken: the bot gets the seat behind you)
            vil.moveTo(v.position()); vil.setNoAi(true); vil.setPersistenceRequired(); vil.addTag(TAG);
            p.serverLevel().addFreshEntity(vil);
            vil.startRiding(v, true);
            b.startRiding(v, true);
            ids[1] = b.getId();
            Vec3 seatAt = b.position();
            var d = dummy(p, new Vec3(seatAt.x, base.getY(), v.getZ() + 16));     // (straight ahead of the bot: the line runs through you)
            ids[2] = d.getId();
            b.setTarget(d);
            hp0[0] = v.getHealth(); hp0[1] = p.getHealth(); hp0[2] = d.getHealth();
            log("bot passenger: rhalix seat " + v.getPassengers().indexOf(b) + " with " + gun[0] + ", you driving=" + (v.getControllingPassenger() == p));
        }), 30);
        step(null, () -> { mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); mc.options.hideGui = false; setView(mc, 0F); DMG.clear(); }, 60);
        step("bot_passenger_shooting", () -> server(p -> { if (p.serverLevel().getEntity(ids[1]) instanceof Mob b && p.serverLevel().getEntity(ids[2]) instanceof LivingEntity d && b.getTarget() == null) b.setTarget(d); }), 60);
        step(null, () -> server(p -> {
            PartyBotEntity b = p.serverLevel().getEntity(ids[1]) instanceof PartyBotEntity pb ? pb : null;
            float veh = hp0[0] - hp(p, ids[0]), me = dmg(p.getId()), lost = dmg(ids[2]);
            check("a bot passenger shoots from its seat (" + gun[0] + ")", b != null && b.isPassenger() && b.seatShots > 0 && lost > 0 && veh == 0 && me == 0,
                    "seated " + (b != null && b.isPassenger()) + ", shots from the seat " + (b == null ? 0 : b.seatShots) + ", target lost " + f(lost)
                            + " HP, car lost " + f(veh) + ", driver lost " + f(me));
        }), 5);
        step(null, () -> server(VehicleV2Tests::home), 10);
    }

    // ================================================================== 3. model stills

    private static void models(Minecraft mc) {
        VehicleKind[] ks = {VehicleKind.CAR, VehicleKind.CAR_MK2, VehicleKind.MOTORBIKE, VehicleKind.HELICOPTER, VehicleKind.HELICOPTER_MK2, VehicleKind.GOV_TRUCK, VehicleKind.MOTORBIKE_MK2, VehicleKind.GOV_TRUCK_MK2};
        int[] cam = {-1, 0, 0};
        for (VehicleKind k : ks) {
            int[] id = new int[1];
            step(null, () -> server(p -> {
                home(p);
                VehicleEntity v = spawn(p, k, 0, 0, 30F);
                id[0] = v.getId(); vehicle = v.getId();
                p.connection.teleport(base.getX() + 0.5, base.getY(), base.getZ() + 0.5 - 20, 0F, 0F);
            }), 30);
            double far = k.width * 1.9 + 3;
            // front three-quarter, side, back three-quarter (empty)
            camera(mc, cam, () -> view3(id[0], 25F, far, 0.32));
            step("model_" + k.id + "_front34", () -> {}, 3);
            camera(mc, cam, () -> view3(id[0], 90F + 0F, far * 0.95, 0.22));
            step("model_" + k.id + "_side", () -> {}, 3);
            camera(mc, cam, () -> view3(id[0], 155F, far, 0.3));
            step("model_" + k.id + "_back34", () -> {}, 3);
            cameraOff(mc);
            // riders in the seats
            step(null, () -> server(p -> {
                if (!(p.serverLevel().getEntity(id[0]) instanceof VehicleEntity v)) return;
                for (int i = 0; i < k.insideSeats(); i++) {
                    var h = EntityType.HUSK.create(p.serverLevel());
                    h.moveTo(v.position()); h.setNoAi(true); h.setPersistenceRequired(); h.setInvulnerable(true); h.addTag(TAG); h.setSilent(true);
                    p.serverLevel().addFreshEntity(h);
                    h.startRiding(v, true);
                }
            }), 10);
            camera(mc, cam, () -> view3(id[0], 60F, far, 0.45));
            step("model_" + k.id + "_riders", () -> {}, 3);
            cameraOff(mc);
            // the driver's view
            step(null, () -> server(p -> {
                if (!(p.serverLevel().getEntity(id[0]) instanceof VehicleEntity v)) return;
                v.ejectPassengers(); clearRiders(p);
                p.startRiding(v, true);
            }), 15);
            step(null, () -> { mc.options.hideGui = true; mc.options.setCameraType(CameraType.FIRST_PERSON); setView(mc, 30F); mc.player.setXRot(12F); }, 15);
            step("model_" + k.id + "_driver", () -> {}, 2);
            step(null, () -> { mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); setView(mc, 30F); }, 10);
            step("model_" + k.id + "_driver3p", () -> {}, 2);
            step(null, () -> { mc.options.hideGui = false; mc.options.setCameraType(CameraType.FIRST_PERSON); server(VehicleV2Tests::home); }, 10);
        }
    }

    private static void clearRiders(ServerPlayer p) {
        p.serverLevel().getEntitiesOfClass(LivingEntity.class, new AABB(base).inflate(30), e -> e.getTags().contains(TAG) && !(e instanceof VehicleEntity)).forEach(Entity::discard);
    }

    /** A camera spot `dist` away from vehicle `id`, `angle` degrees round from straight ahead of it (clockwise seen from above), looking at it. */
    private static double[] view3(int id, float angle, double dist, double up) {
        var s = Minecraft.getInstance().getSingleplayerServer();
        Entity v = null;
        for (var l : s.getAllLevels()) { v = l.getEntity(id); if (v != null) break; }
        if (v == null) return new double[]{base.getX(), base.getY() + 3, base.getZ(), 0, 20};
        float a = (v.getYRot() - angle) * Mth.DEG_TO_RAD;
        double fx = -Mth.sin(a), fz = Mth.cos(a);
        Vec3 c = v.position().add(0, v.getBbHeight() * 0.45, 0);
        Vec3 at = c.add(fx * dist, dist * up + 0.6, fz * dist);
        Vec3 d = c.subtract(at);
        float yaw = (float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90F), pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
        return new double[]{at.x, at.y - 1.62, at.z, yaw, pitch};      // (the armor stand's feet: its eyes are ~1.6 up... close enough)
    }

    private static void camera(Minecraft mc, int[] id, java.util.function.Supplier<double[]> where) {
        step(null, () -> server(p -> {
            if (id[0] >= 0 && p.serverLevel().getEntity(id[0]) != null) p.serverLevel().getEntity(id[0]).discard();
            double[] c = where.get();
            var a = EntityType.ARMOR_STAND.create(p.serverLevel());
            a.moveTo(c[0], c[1], c[2], (float) c[3], (float) c[4]); a.setNoGravity(true); a.setInvisible(true);
            p.serverLevel().addFreshEntity(a); id[0] = a.getId();
            id[1] = Float.floatToIntBits((float) c[3]); id[2] = Float.floatToIntBits((float) c[4]);
        }), 6);
        step(null, () -> { mc.options.hideGui = true; mc.options.setCameraType(CameraType.FIRST_PERSON);
            if (mc.level.getEntity(id[0]) instanceof ArmorStand a) {
                float yaw = Float.intBitsToFloat(id[1]), pitch = Float.intBitsToFloat(id[2]);
                a.setYRot(yaw); a.yRotO = yaw; a.setXRot(pitch); a.xRotO = pitch; a.setYHeadRot(yaw); a.yHeadRotO = yaw; mc.setCameraEntity(a); } }, 5);
    }

    private static void cameraOff(Minecraft mc) { step(null, () -> { mc.setCameraEntity(mc.player); mc.options.hideGui = false; }, 3); }

    // ================================================================== Epic Fight (only reached when it's installed)

    private static final class Ef {
        static String info() {
            try {
                var p = yesman.epicfight.client.ClientEngine.getInstance().getPlayerPatch();
                return "battle=" + (p != null && p.isEpicFightMode()) + " tpsType=" + yesman.epicfight.config.ClientConfig.getCameraMode() + " tps=" + tps();
            } catch (Throwable t) { return "error " + t; }
        }

        static boolean tps() {
            try { return yesman.epicfight.api.client.camera.EpicFightCameraAPI.getInstance().isTPSMode(); } catch (Throwable t) { return false; }
        }

        static float camYaw() {
            try { return yesman.epicfight.api.client.camera.EpicFightCameraAPI.getInstance().getCameraYRot(); } catch (Throwable t) { return Minecraft.getInstance().player.getYRot(); }
        }

        static void setCamYaw(float yaw) {
            try {
                var api = yesman.epicfight.api.client.camera.EpicFightCameraAPI.getInstance();
                api.setCameraRotations(api.getCameraXRot(), yaw, true);
            } catch (Throwable t) { Minecraft.getInstance().player.setYRot(yaw); }
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

package com.xenowarriors.client.test;

import com.xenowarriors.character.CharacterType;
import com.xenowarriors.vehicle.VehicleEntity;
import com.xenowarriors.vehicle.VehicleKind;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * UI tests for the DragonMineZ-world / vehicles / Epic Fight work (focuses "dmzworld", "world", "efworld"). Kept out
 * of UiTest (one dispatch line there); results are logged as "XW-TEST dmzworld|world|efworld ...".
 */
public final class WorldTests {
    private WorldTests() {}

    @FunctionalInterface
    public interface Adder { void add(String shot, Runnable action, int delay); }

    private static Adder S;
    private static Consumer<Consumer<ServerPlayer>> SERVER;
    private static Consumer<String> LOG;
    private static Consumer<CharacterType> CHAR;
    private static String area;

    public static boolean handles(String focus) {
        return "dmzworld".equals(focus) || "world".equals(focus) || "efworld".equals(focus) || "dmzefworld".equals(focus);
    }

    public static void build(Minecraft mc, String focus, Adder steps, Consumer<Consumer<ServerPlayer>> server, Consumer<CharacterType> character, Consumer<String> log) {
        S = steps; SERVER = server; LOG = log; CHAR = character; area = focus;
        switch (focus) {
            case "dmzworld" -> dmzWorld(mc);
            case "efworld", "dmzefworld" -> efWorld(mc);
            default -> world(mc);
        }
    }

    private static void log(String s) { LOG.accept(area + " " + s); }
    private static void server(Consumer<ServerPlayer> f) { SERVER.accept(f); }
    private static void step(String shot, Runnable a, int delay) { S.add(shot, a, delay); }
    private static void wait(int t) { S.add(null, () -> {}, t); }

    private static void start(Minecraft mc, CharacterType t) {
        step(null, () -> { mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE); mc.getToasts().clear();
            mc.setScreen(null); CHAR.accept(t); server(p -> { p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, net.minecraft.world.item.ItemStack.EMPTY);
                p.setGameMode(net.minecraft.world.level.GameType.CREATIVE); }); }, 60);
    }

    private static void noDmzScreen(Minecraft mc) {
        if (mc.screen != null && mc.screen.getClass().getName().startsWith("com.dragonminez")) mc.setScreen(null);
    }

    /** A camera (invisible armor stand) at an offset from the player, looking with this yaw/pitch. */
    private static void camera(Minecraft mc, int[] id, double dx, double dy, double dz, float yaw, float pitch) {
        step(null, () -> server(p -> {
            if (id[0] >= 0 && p.serverLevel().getEntity(id[0]) != null) p.serverLevel().getEntity(id[0]).discard();
            var a = net.minecraft.world.entity.EntityType.ARMOR_STAND.create(p.serverLevel());
            a.moveTo(p.getX() + dx, p.getY() + dy, p.getZ() + dz, yaw, pitch); a.setNoGravity(true); a.setInvisible(true);
            p.serverLevel().addFreshEntity(a); id[0] = a.getId();
        }), 10);
        step(null, () -> { mc.options.hideGui = true; mc.options.setCameraType(CameraType.FIRST_PERSON);
            if (mc.level.getEntity(id[0]) instanceof ArmorStand a) {
                a.setYRot(yaw); a.yRotO = yaw; a.setXRot(pitch); a.xRotO = pitch; a.setYHeadRot(yaw); a.yHeadRotO = yaw; mc.setCameraEntity(a); } }, 5);
    }

    private static void cameraOff(Minecraft mc) { step(null, () -> mc.setCameraEntity(mc.player), 5); }

    private static String v(Vec3 a) { return String.format("(%.2f %.2f %.2f)", a.x, a.y, a.z); }

    // ============================================================== DragonMineZ

    private static Object dmz(ServerPlayer p) throws Exception {
        var cap = (net.minecraftforge.common.capabilities.Capability<?>) Class.forName("com.dragonminez.common.stats.StatsCapability").getField("INSTANCE").get(null);
        return p.getCapability(cap).resolve().orElse(null);
    }

    private static void dmzMake(ServerPlayer p) {
        try {
            Object d = dmz(p);
            Object ch = d.getClass().getMethod("getCharacter").invoke(d);
            ch.getClass().getMethod("setRace", String.class).invoke(ch, "saiyan");
            com.xenowarriors.compat.DmzPerCharacter.markCreatedForTest(p);
        } catch (Throwable t) { log("dmz make failed " + t); }
    }

    /** Raises Strength one point at a time (as buying it with TP would) until the DragonMineZ level goes up; returns points raised. */
    private static int forceLevelUp(ServerPlayer p) {
        try {
            Object d = dmz(p);
            Object st = d.getClass().getMethod("getStats").invoke(d);
            var add = st.getClass().getMethod("addStrength", int.class);
            int[] r = com.xenowarriors.compat.DmzLevelUp.read(p);
            int lvl = r[0], n = 0;
            while (n < 5000 && com.xenowarriors.compat.DmzLevelUp.read(p)[0] == lvl) { add.invoke(st, 1); n++; }
            com.xenowarriors.compat.DmzPerCharacter.syncStats(p);
            return n;
        } catch (Throwable t) { log("dmz force level failed " + t); return -1; }
    }

    /** Spends free points the way DragonMineZ's menu does (one free point = one stat) - these mustn't earn more. */
    private static int spendFree(ServerPlayer p, int max) {
        try {
            Object d = dmz(p);
            Object st = d.getClass().getMethod("getStats").invoke(d);
            Object res = d.getClass().getMethod("getResources").invoke(d);
            int have = (Integer) res.getClass().getMethod("getPendingAttributePoints").invoke(res);
            int n = Math.min(have, max);
            st.getClass().getMethod("addVitality", int.class).invoke(st, n);
            res.getClass().getMethod("removePendingAttributePoints", int.class).invoke(res, n);
            return n;
        } catch (Throwable t) { log("dmz spend failed " + t); return -1; }
    }

    private static String dmzState(ServerPlayer p) {
        int[] r = com.xenowarriors.compat.DmzLevelUp.read(p);
        return r == null ? "none" : "level=" + r[0] + " stats=" + r[1] + " free=" + r[2] + " room=" + r[3];
    }

    private static void dmzWorld(Minecraft mc) {
        start(mc, CharacterType.MIKIKO);
        step(null, () -> { noDmzScreen(mc); server(p -> { dmzMake(p); com.xenowarriors.debug.SkillTestRunner.arena(p); }); }, 40);
        // ---- 1. DragonMineZ mobs: no Xeno Warriors level
        List<Integer> mobs = new ArrayList<>();
        step(null, () -> { noDmzScreen(mc); server(p -> {
            ServerLevel l = p.serverLevel();
            List<net.minecraft.world.entity.EntityType<?>> hostile = new ArrayList<>(), friendly = new ArrayList<>();
            for (var t : net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES) {
                var id = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(t);
                if (id == null || !id.getNamespace().equals("dragonminez") || id.getPath().contains("shenron") || id.getPath().contains("porunga")) continue;
                if (t.getCategory() == net.minecraft.world.entity.MobCategory.MONSTER && hostile.size() < 3) hostile.add(t);
                else if (t.getCategory() == net.minecraft.world.entity.MobCategory.CREATURE && friendly.size() < 2) friendly.add(t);
            }
            List<net.minecraft.world.entity.EntityType<?>> all = new ArrayList<>(hostile); all.addAll(friendly);
            all.add(net.minecraft.world.entity.EntityType.ZOMBIE);                     // (for comparison: an ordinary mob still gets one)
            int i = 0;
            for (var t : all) {
                Entity e = t.create(l);
                if (!(e instanceof Mob m)) continue;
                m.moveTo(p.getX() - 6 + i * 3, p.getY(), p.getZ() + 9, 180, 0); m.setNoAi(true);
                if (i == 0) m.getPersistentData().putInt("xw_lvl", 30);              // as if levelled by an older version
                l.addFreshEntity(m); mobs.add(m.getId()); i++;
            }
        }); }, 40);
        step(null, () -> { noDmzScreen(mc); server(p -> {
            for (int id : mobs) if (p.serverLevel().getEntity(id) instanceof Mob m) {
                int mods = 0;
                for (var a : List.of(Attributes.MAX_HEALTH, Attributes.ATTACK_DAMAGE, Attributes.ARMOR)) {
                    var in = m.getAttribute(a);
                    if (in != null) for (var mod : in.getModifiers()) if (mod.getName().equals("XW level")) mods++;
                }
                var hp = m.getAttribute(Attributes.MAX_HEALTH);
                log("mob " + net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(m.getType()) + " dmz=" + com.xenowarriors.scaling.EnemyScaling.dmz(m)
                        + " xwLevel=" + com.xenowarriors.scaling.EnemyScaling.level(m) + " xwModifiers=" + mods + " dbzDmg=" + m.getPersistentData().getFloat("xw_dbz_dmg")
                        + " maxHp=" + m.getMaxHealth() + " baseHp=" + (hp == null ? -1 : hp.getBaseValue()));
            }
        }); }, 5);
        step("dmzworld_mobs", () -> { for (int id : mobs) log("lock-on shows Lv " + com.xenowarriors.client.LockOn.LEVELS.get(id) + " for entity " + id); }, 5);
        step(null, () -> server(p -> { for (int id : mobs) { Entity e = p.serverLevel().getEntity(id); if (e != null) e.discard(); } }), 5);
        // ---- 2. DragonMineZ level-ups give 3x the stats (and per character)
        int[] got = new int[4];
        step(null, () -> { noDmzScreen(mc); server(p -> log("levelup before: " + dmzState(p) + " multiplier=" + com.xenowarriors.XwConfig.get(com.xenowarriors.XwConfig.DMZ_LEVEL_UP_MULTIPLIER))); }, 30);
        step(null, () -> server(p -> { got[0] = forceLevelUp(p); log("levelup forced: raised " + got[0] + " stat points (what DragonMineZ gives for this level) -> " + dmzState(p)); }), 45);
        step(null, () -> server(p -> log("levelup after: " + dmzState(p) + " (bonus expected ~" + got[0] * 2 + " free points)")), 5);
        step(null, () -> server(p -> { got[1] = spendFree(p, 100000); log("levelup spent " + got[1] + " free points -> " + dmzState(p)); }), 45);
        step(null, () -> server(p -> log("levelup after spending free points (no extra bonus expected): " + dmzState(p))), 5);
        // per character: Rhalix's own DragonMineZ character
        step(null, () -> { noDmzScreen(mc); CHAR.accept(CharacterType.RHALIX); }, 80);
        step(null, () -> { noDmzScreen(mc); server(p -> { log("percharacter right after switching to Rhalix (live key " + p.getPersistentData().getCompound("PlayerPersisted").getString("xw_dmz_live") + "): " + dmzState(p)
                + " screen=" + (mc.screen == null ? "none" : mc.screen.getClass().getSimpleName())); dmzMake(p); log("percharacter Rhalix start: " + dmzState(p)); }); }, 45);
        step(null, () -> { noDmzScreen(mc); server(p -> { got[2] = forceLevelUp(p); log("percharacter Rhalix forced: raised " + got[2] + " -> " + dmzState(p)); }); }, 45);
        step(null, () -> { noDmzScreen(mc); server(p -> log("percharacter Rhalix after: " + dmzState(p) + " (bonus expected ~" + got[2] * 2 + ")")); }, 5);
        step(null, () -> { noDmzScreen(mc); CHAR.accept(CharacterType.MIKIKO); }, 80);
        step(null, () -> { noDmzScreen(mc); server(p -> log("percharacter Mikiko back (unchanged): " + dmzState(p))); }, 5);
        // ---- 3. quests: the dimension doesn't matter
        String[] quest = new String[3];
        step(null, () -> { noDmzScreen(mc); server(p -> {
            var qs = com.xenowarriors.compat.DmzQuests.dimensionQuests();
            StringBuilder sb = new StringBuilder();
            for (String[] q : qs) sb.append(q[0]).append("#").append(q[1]).append("->").append(q[2]).append(' ');
            log("quests with a dimension objective: " + qs.size() + " " + sb);
            if (!qs.isEmpty()) { quest[0] = qs.get(0)[0]; quest[1] = qs.get(0)[1]; quest[2] = qs.get(0)[2]; }
            var rec = p.server.getLevel(com.xenowarriors.recarnet.Recarnet.LEVEL);
            if (rec != null) { p.teleportTo(rec, 0.5, 140, 0.5, 0, 0); p.getAbilities().flying = true; p.onUpdateAbilities(); }
        }); }, 60);
        step(null, () -> { noDmzScreen(mc); server(p -> {
            log("quest test in " + p.level().dimension().location() + ": isInDimension(dragonminez:namek)=" + com.xenowarriors.compat.DmzQuests.inDimension(p, "dragonminez:namek")
                    + " isInDimension(minecraft:overworld)=" + com.xenowarriors.compat.DmzQuests.inDimension(p, "minecraft:overworld"));
            if (quest[0] == null) return;
            log("quest " + quest[0] + " (objective " + quest[1] + ": be in " + quest[2] + ") startable here=" + com.xenowarriors.compat.DmzQuests.startable(p, quest[0]));
            log("quest story mode enabled=" + com.xenowarriors.compat.DmzQuests.storyMode(true) + " why not startable: " + com.xenowarriors.compat.DmzQuests.blocker(p, quest[0]));
            log("quest objective condition met here (QuestLocationHelper)=" + com.xenowarriors.compat.DmzQuests.locationMet(p, quest[0], Integer.parseInt(quest[1])));
            com.xenowarriors.compat.DmzQuests.accept(p, quest[0]);
            log("quest before: " + com.xenowarriors.compat.DmzQuests.progress(p, quest[0], Integer.parseInt(quest[1])));
        }); }, 80);
        step("dmzworld_quest", () -> server(p -> { if (quest[0] != null) log("quest after 4s in " + p.level().dimension().location() + ": "
                + com.xenowarriors.compat.DmzQuests.progress(p, quest[0], Integer.parseInt(quest[1])) + " waived checks=" + com.xenowarriors.compat.DmzQuests.waivedCount()); }), 5);
        step(null, () -> server(p -> { p.teleportTo(p.server.overworld(), p.server.overworld().getSharedSpawnPos().getX() + 0.5, 100, p.server.overworld().getSharedSpawnPos().getZ() + 0.5, 0, 0);
            com.xenowarriors.debug.SkillTestRunner.arena(p); }), 60);
        // ---- 4. DragonMineZ ki looks on our energy attacks
        kiShots(mc, "dmzworld");
    }

    /** Ultima's Energy Blast and Full Output Beam, Karma Blast, Xeno Blast - seen from the side. */
    private static void kiShots(Minecraft mc, String tag) {
        int[] cam = {-1};
        int[] target = {-1};
        step(null, () -> { noDmzScreen(mc); server(p -> {
            com.xenowarriors.debug.SkillTestRunner.arena(p);
            p.setYRot(0); p.setXRot(0); p.setYHeadRot(0); p.setYBodyRot(0);
            var h = net.minecraft.world.entity.EntityType.HUSK.create(p.serverLevel());
            h.moveTo(p.getX(), p.getY(), p.getZ() + 12, 180, 0); h.setNoAi(true); h.setInvulnerable(true);
            p.serverLevel().addFreshEntity(h); target[0] = h.getId();
            com.xenowarriors.skill.SkillManager.resetCooldowns(p);
        }); }, 20);
        camera(mc, cam, 9, 1.2, 6, 90F, 4F);
        step(null, () -> server(p -> { p.setYRot(0); p.setXRot(0); new com.xenowarriors.skill.ultima.EnergyBlastSkill().use(p); }), 4);
        step(tag + "_energyblast_0", () -> {}, 3);
        step(tag + "_energyblast_1", () -> {}, 4);
        step(null, () -> {}, 30);
        step(null, () -> server(p -> { p.setYRot(0); p.setXRot(0); new com.xenowarriors.skill.ultima.FullOutputBeamSkill().use(p); }), 12);
        step(tag + "_beam_charge", () -> {}, 14);
        step(tag + "_beam_0", () -> {}, 6);
        step(tag + "_beam_1", () -> log("ki particles drawn so far: " + com.xenowarriors.client.compat.DmzKiParticles.spawned), 30);
        step(null, () -> server(p -> { p.setYRot(0); p.setXRot(0); new com.xenowarriors.skill.sp.DojoSkills.KarmaBlast(0xFF4A3A).use(p); }), 2);
        step(tag + "_karmablast", () -> log("ki particles drawn so far: " + com.xenowarriors.client.compat.DmzKiParticles.spawned), 30);
        cameraOff(mc);
        // the Xeno Blast (its trail): through its own cutscene camera
        step(null, () -> { mc.options.hideGui = false; mc.options.setCameraType(CameraType.FIRST_PERSON); server(p -> {
            p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            p.getPersistentData().remove("xw_karma_lock_until");
            com.xenowarriors.debug.SkillTestRunner.arena(p);
            com.xenowarriors.skill.SkillManager.resetCooldowns(p);
            com.xenowarriors.karma.Karma.add(p, 200);
            com.xenowarriors.skill.SkillManager.useSkill(p, com.xenowarriors.skill.SkillManager.SUPER_SLOT);
        }); }, 205);
        step(tag + "_xenoblast_0", () -> {}, 20);
        step(tag + "_xenoblast_1", () -> log("ki particles drawn so far: " + com.xenowarriors.client.compat.DmzKiParticles.spawned), 140);
        step(null, () -> server(p -> p.setGameMode(net.minecraft.world.level.GameType.CREATIVE)), 10);
    }

    // ============================================================== vehicles and poses (no other mods)

    private static Mob rider(ServerLevel l, Vec3 at, int i) {
        var h = com.xenowarriors.npc.ModNpcs.HUMAN.get().create(l);
        if (h == null) return null;
        h.moveTo(at.x + i * 0.3, at.y, at.z, 0, 0); h.setNoAi(true); h.setInvulnerable(true);
        l.addFreshEntity(h);
        return h;
    }

    private static void logRiders(VehicleEntity v, String what) {
        StringBuilder sb = new StringBuilder();
        for (Entity e : v.getPassengers()) {
            Vec3 rel = e.position().subtract(v.position());
            sb.append(v.seatOf(e).type()).append(v(rel)).append(' ');
        }
        log(what + " " + v.kind.id + " riders=" + v.getPassengers().size() + "/" + v.kind.seats + " (inside " + v.kind.insideSeats() + ") onGround=" + v.onGround() + ": " + sb);
    }

    private static void vehicleScene(Minecraft mc, VehicleKind k, int riders, double up, String shot) {
        int[] veh = {-1};
        int[] cam = {-1};
        step(null, () -> server(p -> {
            ServerLevel l = p.serverLevel();
            l.getEntitiesOfClass(VehicleEntity.class, p.getBoundingBox().inflate(40)).forEach(e -> { e.ejectPassengers(); e.discard(); });
            l.getEntitiesOfClass(com.xenowarriors.npc.HumanNpcEntity.class, p.getBoundingBox().inflate(40)).forEach(Entity::discard);
            p.stopRiding();
            var e = com.xenowarriors.vehicle.Vehicles.TYPES.get(k).get().create(l);
            if (e == null) return;
            e.moveTo(p.getX(), p.getY() + up, p.getZ() + 4, 0, 0); e.yBodyRot = 0; e.yHeadRot = 0;
            l.addFreshEntity(e); veh[0] = e.getId();
            p.setYRot(0); p.startRiding(e, true);
            int ok = 0, refused = 0;
            for (int i = 1; i < riders; i++) {
                Mob r = rider(l, e.position(), i);
                if (r != null && r.startRiding(e)) ok++; else { refused++; if (r != null) r.discard(); }
            }
            Mob extra = rider(l, e.position(), 20);
            boolean overflow = extra != null && extra.startRiding(e);
            if (extra != null && !overflow) extra.discard();
            log(k.id + " seats=" + k.seats + " inside=" + k.insideSeats() + " boarded " + (ok + 1) + " of " + riders + " (refused " + refused + "), one more accepted=" + overflow);
        }), 30);
        step(null, () -> server(p -> { if (p.serverLevel().getEntity(veh[0]) instanceof VehicleEntity e) logRiders(e, up > 0 ? "in the air" : "on the ground"); }), 5);
        camera(mc, cam, 8.5, up > 0 ? -0.8 : 1.0, 4, 90F, up > 0 ? 10F : 8F);
        step(shot, () -> {}, 5);
        step(null, () -> {}, 3);
        cameraOff(mc);
        if (k == VehicleKind.HELICOPTER && up > 0) {
            // one of the hangers lets go in the air: drops from where they are, not into the cabin or a wall
            step(null, () -> server(p -> {
                if (!(p.serverLevel().getEntity(veh[0]) instanceof VehicleEntity e)) return;
                Entity hanger = null;
                for (Entity r : e.getPassengers()) if (e.seatOf(r).type() == VehicleKind.SeatType.HANG) hanger = r;
                if (hanger == null) { log("no hanger to drop"); return; }
                Vec3 before = hanger.position();
                hanger.stopRiding();
                if (hanger instanceof Mob m) m.setNoAi(false);              // (so it falls)
                boolean free = hanger.level().noCollision(hanger, hanger.getBoundingBox());
                log("hanger let go at " + v(before) + " -> dismounted at " + v(hanger.position()) + " room to stand=" + free + " (vehicle at " + v(e.position()) + ")");
            }), 5);
            // landed: the hangers stand beside it
            step(null, () -> mc.options.keySprint.setDown(true), 60);         // (the pilot takes it down)
            step(null, () -> mc.options.keySprint.setDown(false), 10);
            step(null, () -> server(p -> { if (p.serverLevel().getEntity(veh[0]) instanceof VehicleEntity e) logRiders(e, "landed"); }), 5);
            camera(mc, cam, 7.5, 1.0, 4, 90F, 8F);
            step(shot + "_landed", () -> {}, 5);
            cameraOff(mc);
        }
        // everyone off: safe spots
        step(null, () -> server(p -> {
            if (!(p.serverLevel().getEntity(veh[0]) instanceof VehicleEntity e)) return;
            StringBuilder sb = new StringBuilder();
            for (Entity r : new ArrayList<>(e.getPassengers())) {
                VehicleKind.SeatType t = e.seatOf(r).type();
                r.stopRiding();
                sb.append(t).append(v(r.position().subtract(e.position()))).append(r.level().noCollision(r, r.getBoundingBox()) ? " ok " : " STUCK ");
            }
            log(k.id + " all off: " + sb);
        }), 10);
    }

    private static void world(Minecraft mc) {
        start(mc, CharacterType.MIKIKO);
        step(null, () -> server(p -> com.xenowarriors.debug.SkillTestRunner.arena(p)), 30);
        for (VehicleKind k : VehicleKind.values()) {
            StringBuilder sb = new StringBuilder();
            for (var s : k.seatList()) sb.append(s.type()).append(String.format("(%.2f %.2f %.2f) ", s.x(), s.y(), s.z()));
            step(null, () -> log("seat layout " + k.id + ": " + k.seats + " seats, " + k.insideSeats() + " inside: " + sb), 1);
        }
        vehicleScene(mc, VehicleKind.CAR, 6, 0, "world_car_6");
        vehicleScene(mc, VehicleKind.HELICOPTER, 6, 0, "world_heli_6");
        vehicleScene(mc, VehicleKind.HELICOPTER, 10, 7, "world_heli_10_air");
        vehicleScene(mc, VehicleKind.MOTORBIKE, 4, 0, "world_bike_4");
        vehicleScene(mc, VehicleKind.GOV_TRUCK, 8, 0, "world_truck_8");
        // a bot gets in: car with you and 4 others - the bot takes a spot on the roof
        int[] ids = {-1, -1};
        step(null, () -> server(p -> {
            ServerLevel l = p.serverLevel();
            l.getEntitiesOfClass(VehicleEntity.class, p.getBoundingBox().inflate(40)).forEach(e -> { e.ejectPassengers(); e.discard(); });
            l.getEntitiesOfClass(com.xenowarriors.npc.HumanNpcEntity.class, p.getBoundingBox().inflate(40)).forEach(Entity::discard);
            var e = com.xenowarriors.vehicle.Vehicles.TYPES.get(VehicleKind.CAR).get().create(l);
            e.moveTo(p.getX(), p.getY(), p.getZ() + 3, 0, 0); l.addFreshEntity(e); ids[0] = e.getId();
            p.startRiding(e, true);
            for (int i = 1; i < 5; i++) { Mob r = rider(l, e.position(), i); if (r != null) r.startRiding(e); }
            var b = com.xenowarriors.bot.PartyBots.spawn(p, CharacterType.RHALIX, null);
            if (b != null) { b.moveTo(e.getX() + 2, e.getY(), e.getZ()); ids[1] = b.getId(); }
        }), 100);
        step(null, () -> server(p -> {
            if (p.serverLevel().getEntity(ids[0]) instanceof VehicleEntity e) {
                Entity b = p.serverLevel().getEntity(ids[1]);
                log("bot boarded=" + (b != null && b.getVehicle() == e) + (b != null && b.getVehicle() == e ? " spot=" + e.seatOf(b).type() : ""));
                logRiders(e, "with bot");
            }
        }), 5);
        int[] cam = {-1};
        camera(mc, cam, 7.5, 1.4, 3, 90F, 10F);
        step("world_car_bot", () -> {}, 5);
        cameraOff(mc);
        step(null, () -> server(p -> {
            p.stopRiding();
            p.serverLevel().getEntitiesOfClass(VehicleEntity.class, p.getBoundingBox().inflate(40)).forEach(e -> { e.ejectPassengers(); e.discard(); });
            p.serverLevel().getEntitiesOfClass(com.xenowarriors.npc.HumanNpcEntity.class, p.getBoundingBox().inflate(40)).forEach(Entity::discard);
            for (var b : com.xenowarriors.bot.PartyBots.bots(p)) b.discard();
        }), 20);
        poses(mc, "world");
        kiShots(mc, "world");          // (without DragonMineZ: our own looks)
    }

    /** The playable characters standing, walking and in a skill pose (front view). */
    private static void poses(Minecraft mc, String tag) {
        for (CharacterType t : new CharacterType[]{CharacterType.MIKIKO, CharacterType.RHALIX, CharacterType.ULTIMA, CharacterType.KAERU, CharacterType.KYRIE}) {
            step(null, () -> { mc.setScreen(null); CHAR.accept(t); server(p -> com.xenowarriors.debug.SkillTestRunner.arena(p)); }, 50);
            step(null, () -> { mc.options.hideGui = true; mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT); mc.player.setXRot(10F); mc.player.setYRot(0F); }, 10);
            step(tag + "_pose_" + t.getId() + "_idle", () -> {}, 3);
            step(null, () -> mc.options.keyUp.setDown(true), 12);
            step(tag + "_pose_" + t.getId() + "_walk", () -> {}, 1);
            step(null, () -> mc.options.keyUp.setDown(false), 10);
            step(null, () -> server(p -> com.xenowarriors.anim.Poses.play(p, com.xenowarriors.anim.Poses.EL_THRUST, 20)), 6);
            step(tag + "_pose_" + t.getId() + "_thrust", () -> {}, 20);
        }
        step(null, () -> mc.options.setCameraType(CameraType.FIRST_PERSON), 5);
    }

    // ============================================================== Epic Fight

    /** How many times each entity started being drawn this frame (two for one entity = it's drawn twice). */
    private static final java.util.Map<Integer, Integer> DRAWS = new java.util.HashMap<>(), LAST = new java.util.HashMap<>();
    private static boolean counting;

    private static void count() {
        if (counting) return;
        counting = true;
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.LOWEST, true,
                net.minecraftforge.client.event.RenderLivingEvent.Pre.class, e -> DRAWS.merge(e.getEntity().getId(), e.isCanceled() ? 100 : 1, Integer::sum));
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.NORMAL, false,
                net.minecraftforge.event.TickEvent.RenderTickEvent.class, e -> {
            if (e.phase == net.minecraftforge.event.TickEvent.Phase.START) { LAST.clear(); LAST.putAll(DRAWS); DRAWS.clear(); }
        });
    }

    /** "vanilla draws / Epic Fight draws" for an entity last frame (a Pre event Epic Fight cancelled = it drew its own model). */
    private static String draws(Entity e) {
        if (e == null) return "-";
        int n = LAST.getOrDefault(e.getId(), 0);
        return (n % 100) + " vanilla, " + (n / 100) + " epicfight";
    }

    private static void efWorld(Minecraft mc) {
        count();
        start(mc, CharacterType.MIKIKO);
        int[] ids = {-1, -1, -1};
        step(null, () -> server(p -> {
            com.xenowarriors.debug.SkillTestRunner.arena(p);
            p.setYRot(0); p.setXRot(0);
            com.xenowarriors.compat.epicfight.EfTest.play(p, "none");     // battle mode
            var b = com.xenowarriors.bot.PartyBots.spawn(p, CharacterType.RHALIX, null);
            if (b != null) { b.moveTo(p.getX() + 1.8, p.getY(), p.getZ() + 0.5, 0, 0); b.setNoAi(true); ids[0] = b.getId(); }
            var h = com.xenowarriors.npc.ModNpcs.HUMAN.get().create(p.serverLevel());
            if (net.minecraftforge.fml.ModList.get().isLoaded("dragonminez")) dmzMake(p);
            if (h != null) { h.moveTo(p.getX() - 1.8, p.getY(), p.getZ() + 0.5, 0, 0); h.setYBodyRot(0); h.setYHeadRot(0); h.setNoAi(true); p.serverLevel().addFreshEntity(h); ids[1] = h.getId(); }
            var d = com.xenowarriors.npc.ModNpcs.DARK_POSSESSED.get().create(p.serverLevel());
            if (d != null) { d.moveTo(p.getX() - 3.6, p.getY(), p.getZ() + 0.5, 0, 0); d.setYBodyRot(0); d.setYHeadRot(0); d.setNoAi(true); p.serverLevel().addFreshEntity(d); ids[2] = d.getId(); }
        }), 60);
        Runnable report = () -> log("draws last frame: player " + draws(mc.player) + " | bot " + draws(mc.level.getEntity(ids[0])) + " | townsfolk "
                + draws(mc.level.getEntity(ids[1])) + " | possessed " + draws(mc.level.getEntity(ids[2])) + " | battle=" + battle(mc));
        step(null, () -> { mc.options.hideGui = true; mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT); mc.player.setXRot(12F); mc.player.setYRot(0F); }, 20);
        step(area + "_idle", report, 3);
        step(null, () -> mc.options.keyUp.setDown(true), 12);
        step(area + "_walk", report, 1);
        step(null, () -> mc.options.keyUp.setDown(false), 10);
        step(null, () -> server(p -> com.xenowarriors.compat.epicfight.EfTest.play(p, "pole1")), 4);
        step(area + "_attack_0", report, 4);
        step(area + "_attack_1", report, 20);
        step(null, () -> server(p -> p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_SWORD))), 30);
        step(area + "_sword", report, 3);
        step(null, () -> server(p -> com.xenowarriors.anim.Poses.play(p, com.xenowarriors.anim.Poses.EL_THRUST, 20)), 5);
        step(area + "_pose_thrust", report, 20);
        step(null, () -> server(p -> p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, net.minecraft.world.item.ItemStack.EMPTY)), 10);
        step(null, () -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK), 10);
        step(area + "_back", report, 3);
        step(null, () -> { mc.options.setCameraType(CameraType.FIRST_PERSON); mc.options.hideGui = false; mc.player.setXRot(40F); }, 10);
        step(area + "_firstperson", report, 3);
        step(null, () -> server(p -> p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL)), 10);
        step(null, () -> mc.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player)), 10);
        step(area + "_inventory", report, 3);
        step(null, () -> com.xenowarriors.client.PlayerStatsScreen.open(), 15);
        step(area + "_menu", report, 3);
        step(null, () -> { mc.setScreen(null); server(p -> p.setGameMode(net.minecraft.world.level.GameType.CREATIVE)); }, 10);
        step(null, () -> { mc.setScreen(null); mc.options.hideGui = true; }, 5);
        // in a vehicle (our seat poses) and other characters
        step(null, () -> server(p -> {
            var e = com.xenowarriors.vehicle.Vehicles.TYPES.get(VehicleKind.CAR).get().create(p.serverLevel());
            e.moveTo(p.getX(), p.getY(), p.getZ() + 2, 0, 0); p.serverLevel().addFreshEntity(e); p.startRiding(e, true);
        }), 20);
        step(null, () -> { mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT); mc.player.setXRot(15F); }, 10);
        step(area + "_car", report, 3);
        step(null, () -> server(p -> { var v = p.getVehicle(); p.stopRiding(); if (v != null) v.discard(); }), 10);
        for (CharacterType t : new CharacterType[]{CharacterType.RHALIX, CharacterType.KAERU, CharacterType.KYRIE}) {
            step(null, () -> { mc.setScreen(null); CHAR.accept(t); server(p -> com.xenowarriors.compat.epicfight.EfTest.play(p, "none")); }, 60);
            step(null, () -> { mc.options.hideGui = true; mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT); mc.player.setXRot(12F); mc.player.setYRot(0F); }, 10);
            step(area + "_" + t.getId() + "_idle", report, 3);
            step(null, () -> server(p -> com.xenowarriors.compat.epicfight.EfTest.play(p, "pole1")), 4);
            step(area + "_" + t.getId() + "_attack", report, 20);
        }
        step(null, () -> mc.options.setCameraType(CameraType.FIRST_PERSON), 5);
    }

    private static String battle(Minecraft mc) {
        if (!net.minecraftforge.fml.ModList.get().isLoaded("epicfight")) return "no epicfight";
        try { return Ef.battle(mc); } catch (Throwable t) { return "?"; }
    }

    private static final class Ef {
        static String battle(Minecraft mc) {
            var patch = yesman.epicfight.world.capabilities.EpicFightCapabilities.getEntityPatch(mc.player,
                    yesman.epicfight.world.capabilities.entitypatch.player.PlayerPatch.class);
            return patch == null ? "no patch" : String.valueOf(patch.isEpicFightMode());
        }
    }

}

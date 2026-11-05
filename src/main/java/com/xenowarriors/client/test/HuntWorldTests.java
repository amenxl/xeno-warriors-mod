package com.xenowarriors.client.test;

import com.xenowarriors.character.CharacterType;
import com.xenowarriors.room.RoomTech;
import com.xenowarriors.vehicle.VehicleEntity;
import com.xenowarriors.vehicle.VehicleKind;
import com.xenowarriors.vehicle.Vehicles;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.function.Consumer;

/**
 * Bug-hunt tests for the world systems (focus "hw..."): flying vehicles on a server without allow-flight, loot of
 * bosses killed inside a Room, coming back from a Room after a logout / restart, parking vehicles in mode arenas.
 * Results are logged as "XW-TEST hw ...".
 */
public final class HuntWorldTests {
    private HuntWorldTests() {}

    private static WorldTests.Adder S;
    private static Consumer<Consumer<ServerPlayer>> SERVER;
    private static Consumer<String> LOG;
    private static Consumer<CharacterType> CHAR;

    public static boolean handles(String focus) { return focus != null && focus.startsWith("hw"); }

    public static void build(Minecraft mc, String focus, WorldTests.Adder steps, Consumer<Consumer<ServerPlayer>> server, Consumer<CharacterType> character, Consumer<String> log) {
        S = steps; SERVER = server; LOG = log; CHAR = character;
        start(mc);
        String part = focus.substring(2);
        if (part.isEmpty() || part.contains("fly")) fly(mc);
        if (part.isEmpty() || part.contains("loot")) roomLoot(mc);
        if (part.isEmpty() || part.contains("back")) roomBack(mc);
        if (part.isEmpty() || part.contains("park")) park(mc);
        step(null, () -> log("done"), 5);
    }

    private static void log(String s) { LOG.accept("hw " + s); }
    private static void server(Consumer<ServerPlayer> f) { SERVER.accept(f); }
    private static void step(String shot, Runnable a, int delay) { S.add(shot == null ? null : "hw_" + shot, a, delay); }
    private static String f1(double d) { return String.format("%.1f", d); }

    private static BlockPos base;

    private static void start(Minecraft mc) {
        step(null, () -> { mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE); mc.getToasts().clear(); mc.setScreen(null);
            CHAR.accept(CharacterType.RHALIX);
            server(p -> {
                p.setGameMode(GameType.CREATIVE);
                ServerLevel l = p.serverLevel();
                l.setDayTime(6000);
                l.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false, p.server);
                l.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING).set(false, p.server);
                l.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_WEATHER_CYCLE).set(false, p.server);
                floor(l, p.blockPosition().above(24));
                base = p.blockPosition().above(24);
                home(p);
            });
        }, 80);
    }

    private static void floor(ServerLevel l, BlockPos b) {
        for (int x = -24; x <= 24; x++) for (int z = -24; z <= 24; z++) {
            l.setBlock(b.offset(x, -1, z), Blocks.SMOOTH_STONE.defaultBlockState(), 2);
            for (int y = 0; y < 16; y++) if (!l.getBlockState(b.offset(x, y, z)).isAir()) l.setBlock(b.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
        }
    }

    private static void home(ServerPlayer p) {
        p.stopRiding();
        p.getAbilities().flying = false; p.onUpdateAbilities();
        ServerLevel ow = p.server.overworld();
        if (p.level() != ow) p.teleportTo(ow, base.getX() + .5, base.getY(), base.getZ() + .5, 0F, 0F);
        else p.connection.teleport(base.getX() + .5, base.getY(), base.getZ() + .5, 0F, 0F);
        for (Entity e : ow.getEntitiesOfClass(Entity.class, new AABB(base).inflate(30), e -> !(e instanceof net.minecraft.world.entity.player.Player))) {
            if (e instanceof VehicleEntity v) v.ejectPassengers();
            e.discard();
        }
    }

    private static int items(ServerLevel l, Vec3 at, double r) {
        int n = 0;
        for (ItemEntity it : l.getEntitiesOfClass(ItemEntity.class, new AABB(at, at).inflate(r))) n += it.getItem().getCount();
        return n;
    }

    // ================================================================== 1: a boss's own loot inside a Room

    private static void roomLoot(Minecraft mc) {
        Vec3[] at = new Vec3[1];
        step(null, () -> server(p -> {
            home(p);
            p.setGameMode(GameType.CREATIVE);
            boolean opened = RoomTech.use(p);
            log("room loot: room opened=" + opened + " in room=" + RoomTech.is(p.level()));
        }), 20);
        step(null, () -> server(p -> {
            if (!RoomTech.is(p.level())) { log("room loot: not in a room"); return; }
            ServerLevel l = p.serverLevel();
            at[0] = p.position().add(4, 0, 0);
            var z = com.xenowarriors.boss.ZombieSaiyans.ZOMBIE_SAIYAN.get().create(l);
            z.moveTo(at[0].x, at[0].y, at[0].z, 0, 0);
            l.addFreshEntity(z);
            z.setFormForTest(com.xenowarriors.boss.ZombieSaiyanEntity.SSJ4);
            z.setHealth(1F);
            z.hurt(p.damageSources().playerAttack(p), 1000F);
            log("room loot: zombie saiyan dead=" + !z.isAlive());
        }), 10);
        step(null, () -> server(p -> {
            if (at[0] == null) return;
            log("room loot: zombie saiyan items on the floor in the room = " + items(p.serverLevel(), at[0], 6));
            for (ItemEntity it : p.serverLevel().getEntitiesOfClass(ItemEntity.class, new AABB(at[0], at[0]).inflate(6))) it.discard();
            ServerLevel l = p.serverLevel();
            var m = net.minecraft.world.entity.EntityType.ZOMBIE.create(l);
            m.moveTo(at[0].x, at[0].y, at[0].z, 0, 0);
            l.addFreshEntity(m);
            com.xenowarriors.boss.MiniBosses.make(m, com.xenowarriors.boss.MiniBosses.Power.SWIFT);
            m.setHealth(1F);
            m.hurt(p.damageSources().playerAttack(p), 1000F);
            log("room loot: mini-boss dead=" + !m.isAlive());
        }), 10);
        step(null, () -> server(p -> {
            if (at[0] == null) return;
            int n = 0;
            StringBuilder sb = new StringBuilder();
            for (ItemEntity it : p.serverLevel().getEntitiesOfClass(ItemEntity.class, new AABB(at[0], at[0]).inflate(6))) {
                n += it.getItem().getCount();
                sb.append(it.getItem().getCount()).append('x').append(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(it.getItem().getItem()).getPath()).append(' ');
            }
            log("room loot: mini-boss items on the floor in the room = " + n + " [" + sb.toString().trim() + "]");
            RoomTech.use(p);                                    // leave: the kept drops come back with you
        }), 10);
        step(null, () -> server(p -> {
            log("room loot: back in overworld=" + (p.level().dimension() == Level.OVERWORLD) + ", items that came back near you = " + items(p.serverLevel(), p.position(), 12));
            home(p);
        }), 20);
    }

    // ================================================================== 2: logged out / server stopped inside a Room

    private static void roomBack(Minecraft mc) {
        Vec3[] origin = new Vec3[1];
        step(null, () -> server(p -> {
            home(p);
            // far from the world spawn, so "sent to spawn" and "back where you were" can be told apart
            BlockPos far = base.offset(400, 0, 400);
            floor(p.serverLevel(), far);
            p.connection.teleport(far.getX() + .5, far.getY(), far.getZ() + .5, 0F, 0F);
        }), 20);
        step(null, () -> server(p -> {
            origin[0] = p.position();
            boolean opened = RoomTech.use(p);
            log("room back: opened=" + opened + " at " + p.blockPosition().toShortString());
        }), 20);
        step(null, () -> server(p -> {
            log("room back: in room=" + RoomTech.is(p.level()));
            // what's left after a server stop / crash while inside: the Room is gone from memory, the player is still in its dimension
            try {
                for (String f : new String[]{"ROOMS", "BY_PLAYER"}) {
                    var fld = RoomTech.class.getDeclaredField(f);
                    fld.setAccessible(true);
                    ((java.util.Map<?, ?>) fld.get(null)).clear();
                }
            } catch (Throwable t) { log("room back: reflection failed " + t); }
            RoomTech.onLogin(new net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent(p));
        }), 10);
        step(null, () -> server(p -> {
            double d = origin[0] == null ? -1 : p.position().distanceTo(origin[0]);
            log("room back: after login dim=" + p.level().dimension().location() + " at " + p.blockPosition().toShortString() + ", " + f1(d) + " blocks from where the Room was opened"
                    + " -> " + (p.level().dimension() == Level.OVERWORLD && d >= 0 && d < 4 ? "BACK WHERE YOU WERE" : "elsewhere (spawn)"));
            home(p);
        }), 20);
    }

    // ================================================================== 3: parking a vehicle in a mode arena

    private static void park(Minecraft mc) {
        step(null, () -> server(p -> {
            ServerLevel tl = p.server.getLevel(com.xenowarriors.tower.Tower.LEVEL);
            if (tl == null) { log("park: no tower level"); return; }
            BlockPos spot = new BlockPos(40, com.xenowarriors.tower.Tower.Y, -3000);      // (well away from every run's arena)
            for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) tl.setBlock(spot.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 2);
            p.teleportTo(tl, spot.getX() + .5, spot.getY(), spot.getZ() + .5, 0F, 0F);
            p.setGameMode(GameType.SURVIVAL);
            p.setInvulnerable(true);
        }), 20);
        step(null, () -> server(p -> {
            ServerLevel tl = p.serverLevel();
            ItemStack car = com.xenowarriors.vehicle.VehicleItem.make(VehicleKind.CAR, 0);
            p.setItemInHand(InteractionHand.MAIN_HAND, car);
            BlockPos ground = p.blockPosition().below().offset(0, 0, 3);
            var res = car.useOn(new UseOnContext(p, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(ground), Direction.UP, ground, false)));
            int n = tl.getEntitiesOfClass(VehicleEntity.class, p.getBoundingBox().inflate(10)).size();
            log("park: in the Tower dimension (survival) -> " + res + ", vehicles there " + n + ", item left " + p.getMainHandItem().getCount());
            for (VehicleEntity v : tl.getEntitiesOfClass(VehicleEntity.class, p.getBoundingBox().inflate(10))) v.discard();
            p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            p.setInvulnerable(false);
            p.setGameMode(GameType.CREATIVE);
            home(p);
        }), 10);
    }

    // ================================================================== 4: flying vehicles on a server that doesn't allow flight

    private static int vehicle;
    private static final int[] maxFloat = new int[1];

    /** The vanilla "kicked for floating a vehicle too long" counter for this player (it kicks above 80). */
    private static String floating(ServerPlayer p) {
        try {
            var c = p.connection;
            var f1 = net.minecraft.server.network.ServerGamePacketListenerImpl.class.getDeclaredField("aboveGroundVehicleTickCount");
            var f2 = net.minecraft.server.network.ServerGamePacketListenerImpl.class.getDeclaredField("clientVehicleIsFloating");
            f1.setAccessible(true); f2.setAccessible(true);
            int n = f1.getInt(c);
            maxFloat[0] = Math.max(maxFloat[0], n);
            return "floatTicks=" + n + " floating=" + f2.getBoolean(c);
        } catch (Throwable t) { return "floatTicks=? (" + t.getClass().getSimpleName() + ")"; }
    }

    private static void sample(String what) {
        server(p -> {
            Entity e = p.serverLevel().getEntity(vehicle);
            if (!(e instanceof VehicleEntity v)) { log(what + ": vehicle gone"); return; }
            log(what + ": server noGravity=" + v.isNoGravity() + " up=" + f1(v.groundBelow()) + " y=" + f1(v.getY() - base.getY()) + " " + floating(p) + " connected=" + (p.server.getPlayerList().getPlayer(p.getUUID()) != null));
        });
    }

    private static void fly(Minecraft mc) {
        for (VehicleKind k : new VehicleKind[]{VehicleKind.HELICOPTER, VehicleKind.CAR_MK2}) {
            step(null, () -> server(p -> {
                home(p);
                p.setGameMode(GameType.SURVIVAL);
                p.setInvulnerable(true);
                p.server.setFlightAllowed(false);                 // (a dedicated server's default: allow-flight=false)
                maxFloat[0] = 0;
                ServerLevel l = p.serverLevel();
                VehicleEntity v = Vehicles.TYPES.get(k).get().create(l);
                v.moveTo(base.getX() + 0.5, base.getY(), base.getZ() + 6.5, 0, 0);
                l.addFreshEntity(v);
                vehicle = v.getId();
                p.startRiding(v, true);
                log("fly " + k.id + ": flight allowed on the server=" + p.server.isFlightAllowed());
            }), 60);
            step(null, () -> { mc.setScreen(null); mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); mc.options.keyJump.setDown(true);
                log("fly " + k.id + ": client riding " + (mc.player.getVehicle() == null ? "nothing" : mc.player.getVehicle().getType().toShortString())); }, 30);
            step(null, () -> mc.options.keyJump.setDown(false), 5);
            for (int i = 0; i < 6; i++) { int ii = i; step(ii == 2 ? k.id + "_hover" : null, () -> sample("fly " + k.id + " hovering t" + (ii * 15)), 15); }
            step(null, () -> server(p -> log("fly " + k.id + ": max float ticks " + maxFloat[0] + " (vanilla kicks above 80) -> " + (maxFloat[0] > 40 ? "WOULD BE KICKED" : "ok"))), 2);
            step(null, () -> { mc.options.keySprint.setDown(true); server(p -> p.server.setFlightAllowed(true)); }, 60);
            step(null, () -> { mc.options.keySprint.setDown(false); server(p -> { p.setInvulnerable(false); p.setGameMode(GameType.CREATIVE); home(p); }); }, 10);
        }
        step(null, () -> mc.options.setCameraType(CameraType.FIRST_PERSON), 5);
    }
}

package com.xenowarriors.client.test;

import com.xenowarriors.altar.GearUpgrades;
import com.xenowarriors.altar.KarmaAltarMenu;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.character.ModCapabilities;
import com.xenowarriors.corrupted.CSoulBossEntity;
import com.xenowarriors.corrupted.CorruptedSoulEntity;
import com.xenowarriors.corrupted.ModCorrupted;
import com.xenowarriors.corrupted.SoulAltitude;
import com.xenowarriors.item.ModItems;
import com.xenowarriors.room.RoomTech;
import com.xenowarriors.vehicle.VehicleEntity;
import com.xenowarriors.vehicle.VehicleKind;
import com.xenowarriors.vehicle.Vehicles;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * UI tests for the world5 round (focus "world5"): C-soul altitude, Corrupted Shard drops, the Karma Altar (Mikiko /
 * others, gear forging), Room Technology in survival, NPCs shooting from vehicles, vehicle facing/tilt and the Mk II
 * vehicles. Results are logged as "XW-TEST world5 ...".
 */
public final class World5Tests {
    private World5Tests() {}

    private static WorldTests.Adder S;
    private static Consumer<Consumer<ServerPlayer>> SERVER;
    private static Consumer<String> LOG;
    private static Consumer<CharacterType> CHAR;

    public static boolean handles(String focus) { return focus != null && focus.startsWith("world5"); }

    public static void build(Minecraft mc, String focus, WorldTests.Adder steps, Consumer<Consumer<ServerPlayer>> server, Consumer<CharacterType> character, Consumer<String> log) {
        S = steps; SERVER = server; LOG = log; CHAR = character;
        start(mc);
        String part = focus.substring("world5".length());
        if (part.isEmpty() || part.contains("souls")) souls(mc);
        if (part.isEmpty() || part.contains("altar")) altar(mc);
        if (part.isEmpty() || part.contains("room")) room(mc);
        if (part.isEmpty() || part.contains("shoot")) shooting(mc);
        if (part.isEmpty() || part.contains("veh")) vehicles(mc);
    }

    private static void log(String s) { LOG.accept("world5 " + s); }
    private static void server(Consumer<ServerPlayer> f) { SERVER.accept(f); }
    private static void step(String shot, Runnable a, int delay) { S.add(shot == null ? null : "w5_" + shot, a, delay); }
    private static void wait(int t) { S.add(null, () -> {}, t); }
    private static String f1(double d) { return String.format("%.1f", d); }

    private static BlockPos base;

    /** A big flat stone floor high above everything (64 x 64, clear 16 up), night, no other mobs. */
    private static void start(Minecraft mc) {
        step(null, () -> { mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE); mc.getToasts().clear(); mc.setScreen(null);
            CHAR.accept(CharacterType.RHALIX);
            server(p -> {
                p.setGameMode(GameType.CREATIVE);
                ServerLevel l = p.serverLevel();
                l.setDayTime(18000);
                l.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false, p.server);
                l.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING).set(false, p.server);
                l.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_WEATHER_CYCLE).set(false, p.server);
                BlockPos b = p.blockPosition().above(24);
                for (int x = -32; x <= 32; x++) for (int z = -32; z <= 32; z++) {
                    l.setBlock(b.offset(x, -1, z), Blocks.SMOOTH_STONE.defaultBlockState(), 2);
                    for (int y = 0; y < 16; y++) if (!l.getBlockState(b.offset(x, y, z)).isAir()) l.setBlock(b.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                }
                base = b;
                home(p);
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
        AABB box = new AABB(base).inflate(40, 40, 40);
        for (Entity e : p.serverLevel().getEntitiesOfClass(Entity.class, box, e -> !(e instanceof net.minecraft.world.entity.player.Player))) {
            if (e instanceof VehicleEntity v) v.ejectPassengers();
            e.discard();
        }
    }

    private static void camera(Minecraft mc, int[] id, Consumer<double[]> where) {
        step(null, () -> server(p -> {
            if (id[0] >= 0 && p.serverLevel().getEntity(id[0]) != null) p.serverLevel().getEntity(id[0]).discard();
            double[] c = new double[5];
            where.accept(c);
            var a = EntityType.ARMOR_STAND.create(p.serverLevel());
            a.moveTo(c[0], c[1], c[2], (float) c[3], (float) c[4]); a.setNoGravity(true); a.setInvisible(true);
            p.serverLevel().addFreshEntity(a); id[0] = a.getId();
            id[1] = Float.floatToIntBits((float) c[3]); id[2] = Float.floatToIntBits((float) c[4]);
        }), 6);
        step(null, () -> { mc.options.hideGui = true; mc.options.setCameraType(CameraType.FIRST_PERSON);
            if (mc.level.getEntity(id[0]) instanceof ArmorStand a) {
                float yaw = Float.intBitsToFloat(id[1]), pitch = Float.intBitsToFloat(id[2]);
                a.setYRot(yaw); a.yRotO = yaw; a.setXRot(pitch); a.xRotO = pitch; a.setYHeadRot(yaw); a.yHeadRotO = yaw; mc.setCameraEntity(a); } }, 4);
    }

    private static void cameraOff(Minecraft mc) { step(null, () -> { mc.setCameraEntity(mc.player); mc.options.hideGui = false; }, 3); }

    // ================================================================== 1-2: C-souls stay low, drop shards

    private static final List<Integer> SOULS = new ArrayList<>();

    private static void logHeights(ServerPlayer p, String when) {
        StringBuilder sb = new StringBuilder();
        double max = 0;
        for (int id : SOULS) if (p.serverLevel().getEntity(id) instanceof Mob m && m.isAlive()) {
            double h = SoulAltitude.above(m);
            max = Math.max(max, h);
            sb.append(m instanceof CSoulBossEntity ? "B" : "s").append(f1(h)).append(' ');
        }
        log("altitude " + when + ": max " + f1(max) + " above ground (limit " + (int) SoulAltitude.MAX + ") [" + sb.toString().trim() + "]");
    }

    private static void souls(Minecraft mc) {
        step(null, () -> server(p -> {
            home(p);
            SOULS.clear();
            ServerLevel l = p.serverLevel();
            for (int i = 0; i < 5; i++) {
                CorruptedSoulEntity s = ModCorrupted.CORRUPTED_SOUL.get().create(l);
                s.moveTo(base.getX() + 0.5 + (i - 2) * 4, base.getY() + 24, base.getZ() + 10, 0, 0);
                s.setPersistenceRequired();
                l.addFreshEntity(s); SOULS.add(s.getId());
            }
            CSoulBossEntity b = ModCorrupted.C_SOUL.get().create(l);
            b.moveTo(base.getX() + 0.5, base.getY() + 26, base.getZ() + 18, 0, 0);
            b.setPersistenceRequired();
            l.addFreshEntity(b); SOULS.add(b.getId());
            logHeights(p, "t=0 (spawned 24-26 up, no target)");
        }), 20);
        for (int t = 1; t <= 6; t++) { int tt = t; step(null, () -> server(p -> logHeights(p, "t=" + tt * 2 + "s no target")), 40); }
        int[] cam = {-1, 0, 0};
        camera(mc, cam, c -> { c[0] = base.getX() + 0.5 + 20; c[1] = base.getY() + 6; c[2] = base.getZ() + 12; c[3] = 90; c[4] = -5; });
        step("souls_low", () -> {}, 3);
        cameraOff(mc);
        // the player flies up 22 blocks (survival flight, invulnerable): they follow
        step(null, () -> server(p -> {
            p.setGameMode(GameType.SURVIVAL); p.setInvulnerable(true);
            p.getAbilities().mayfly = true; p.getAbilities().flying = true; p.onUpdateAbilities();
            p.connection.teleport(base.getX() + .5, base.getY() + 22, base.getZ() + 12.5, 0F, 0F);
            for (int id : SOULS) if (p.serverLevel().getEntity(id) instanceof Mob m) m.setTarget(p);
            log("player flying at " + f1(SoulAltitude.above(p)) + " above ground, flying()=" + SoulAltitude.flying(p));
        }), 10);
        for (int t = 1; t <= 4; t++) { int tt = t; step(null, () -> server(p -> {
            p.getAbilities().flying = true; p.setDeltaMovement(Vec3.ZERO);
            for (int id : SOULS) if (p.serverLevel().getEntity(id) instanceof Mob m) m.setTarget(p);
            logHeights(p, "t=" + tt * 2 + "s target FLYING");
        }), 40); }
        camera(mc, cam, c -> { c[0] = base.getX() + 0.5 + 22; c[1] = base.getY() + 14; c[2] = base.getZ() + 12; c[3] = 90; c[4] = -5; });
        step("souls_follow_flyer", () -> {}, 3);
        cameraOff(mc);
        // lands: back down
        step(null, () -> server(p -> {
            p.getAbilities().flying = false; p.getAbilities().mayfly = false; p.onUpdateAbilities();
            p.connection.teleport(base.getX() + .5, base.getY(), base.getZ() + .5, 0F, 0F);
            for (int id : SOULS) if (p.serverLevel().getEntity(id) instanceof Mob m) m.setTarget(p);
            log("player landed: flying()=" + SoulAltitude.flying(p));
        }), 10);
        for (int t = 1; t <= 4; t++) { int tt = t; step(null, () -> server(p -> logHeights(p, "t=" + tt * 2 + "s target on the ground")), 40); }
        // shards: 40 souls and 3 C-Souls killed by the player
        step(null, () -> server(p -> {
            p.setInvulnerable(false); p.setGameMode(GameType.CREATIVE);
            clear(p);
            ServerLevel l = p.serverLevel();
            for (int i = 0; i < 40; i++) {
                CorruptedSoulEntity s = ModCorrupted.CORRUPTED_SOUL.get().create(l);
                s.moveTo(base.getX() + 0.5 + (i % 8) - 4, base.getY() + 1, base.getZ() + 6 + i / 8, 0, 0);
                l.addFreshEntity(s);
                s.hurt(p.damageSources().playerAttack(p), 1000F);
            }
        }), 30);
        step(null, () -> server(p -> {
            int n = shards(p);
            log("shards: 40 corrupted souls killed -> " + n + " Corrupted Shards (" + Math.round(n * 100 / 40.0) + "%, chance " + Math.round(CorruptedSoulEntity.SHARD_CHANCE * 100) + "%)");
            clear(p);
            ServerLevel l = p.serverLevel();
            for (int i = 0; i < 3; i++) {
                CSoulBossEntity b = ModCorrupted.C_SOUL.get().create(l);
                b.moveTo(base.getX() + 0.5 + (i - 1) * 5, base.getY() + 1, base.getZ() + 10, 0, 0);
                l.addFreshEntity(b);
                b.hurt(p.damageSources().playerAttack(p), 100000F);
            }
        }), 30);
        step(null, () -> server(p -> { log("shards: 3 C-Souls killed -> " + shards(p) + " Corrupted Shards (4-7 each)"); clear(p); }), 30);
    }

    private static int shards(ServerPlayer p) {
        int n = 0;
        for (ItemEntity it : p.serverLevel().getEntitiesOfClass(ItemEntity.class, new AABB(base).inflate(40)))
            if (it.getItem().is(ModItems.CORRUPTED_SHARD.get())) n += it.getItem().getCount();
        return n;
    }

    // ================================================================== 3: the Karma Altar

    private static void openAltar(ServerPlayer p) {
        p.openMenu(new net.minecraft.world.SimpleMenuProvider((id, inv, pl) -> new KarmaAltarMenu(id, inv, net.minecraft.world.inventory.ContainerLevelAccess.NULL),
                net.minecraft.network.chat.Component.translatable("container.xenowarriors.karma_altar")));
    }

    private static KarmaAltarMenu menu(ServerPlayer p) { return p.containerMenu instanceof KarmaAltarMenu m ? m : null; }

    private static void put(ServerPlayer p, int slot, ItemStack s) {
        KarmaAltarMenu m = menu(p);
        if (m == null) return;
        m.getSlot(slot).set(s);
        m.broadcastChanges();
    }

    private static String gear(ItemStack s) {
        var mods = s.getAttributeModifiers(net.minecraft.world.entity.EquipmentSlot.MAINHAND).get(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        double dmg = 0;
        for (var m : mods) if (m.getOperation() == net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION) dmg += m.getAmount();
        return s.getHoverName().getString() + " lvl " + GearUpgrades.level(s) + " maxDurability " + s.getMaxDamage() + " damage " + s.getDamageValue() + " attackBonus " + f1(dmg);
    }

    /** Puts the mouse over the altar's item slot (so its tooltip shows). */
    private static void hoverItemSlot(Minecraft mc) {
        if (!(mc.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> s)) return;
        double scale = mc.getWindow().getGuiScale();
        int x = (s.width - 176) / 2 + 44 + 8, y = (s.height - 166) / 2 + 35 + 8;
        org.lwjgl.glfw.GLFW.glfwSetCursorPos(mc.getWindow().getWindow(), x * scale, y * scale);
    }

    private static void altar(Minecraft mc) {
        // ---- Mikiko: no blade slot needed, his edges read from him
        step(null, () -> { mc.setScreen(null); CHAR.accept(CharacterType.MIKIKO); }, 40);
        step(null, () -> server(p -> {
            home(p);
            p.setGameMode(GameType.SURVIVAL); p.setInvulnerable(true);
            com.xenowarriors.karma.Karma.add(p, 1000);
            p.getCapability(ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> { d.bladeSky = 0; d.bladeDark = 0; });
            p.getInventory().clearContent();
            p.getInventory().add(new ItemStack(ModItems.HEKUBLADE.get()));
            p.getInventory().add(new ItemStack(ModItems.CORRUPTED_SHARD.get(), 32));
            p.getInventory().add(new ItemStack(ModItems.SKY_CRYSTAL.get(), 16));
            openAltar(p);
        }), 20);
        step("altar_mikiko_empty", () -> log("Mikiko altar: screen=" + (mc.screen == null ? "none" : mc.screen.getClass().getSimpleName())
                + " mikiko=" + (mc.player.containerMenu instanceof KarmaAltarMenu m && m.mikiko()) + " target=" + (mc.player.containerMenu instanceof KarmaAltarMenu m2 ? m2.targetKind() : "?")), 4);
        step(null, () -> server(p -> put(p, KarmaAltarMenu.MATERIAL, new ItemStack(ModItems.CORRUPTED_SHARD.get(), 4))), 10);
        step("altar_mikiko_shards", () -> {
            if (mc.player.containerMenu instanceof KarmaAltarMenu m) log("Mikiko altar with 4 shards, blade not placed: target=" + m.targetKind() + " level=" + m.currentLevel() + " needs " + m.materialsNeeded(Math.max(0, m.currentLevel())));
        }, 4);
        step(null, () -> server(p -> {
            KarmaAltarMenu m = menu(p);
            float k0 = com.xenowarriors.karma.Karma.get(p);
            boolean ok = m != null && m.clickMenuButton(p, 0);
            var d = p.getCapability(ModCapabilities.PLAYER_CHARACTER).resolve().orElse(null);
            ItemStack blade = ItemStack.EMPTY;
            for (ItemStack s : p.getInventory().items) if (s.is(ModItems.HEKUBLADE.get())) blade = s;
            log("Mikiko upgrade (no blade dragged in): ok=" + ok + " bladeDark=" + (d == null ? -1 : d.bladeDark) + " blade in inventory stamped dark=" + com.xenowarriors.mikiko.BladeUpgrades.dark(blade)
                    + " shards left in altar=" + (m == null ? -1 : m.material().getCount()) + " karma " + Math.round(k0) + "->" + Math.round(com.xenowarriors.karma.Karma.get(p)));
        }), 10);
        step("altar_mikiko_after", () -> {}, 4);
        // Mikiko can still forge gear
        step(null, () -> server(p -> { put(p, KarmaAltarMenu.ITEM, new ItemStack(com.xenowarriors.sky.ModTools.SKY_SWORD.get())); put(p, KarmaAltarMenu.MATERIAL, new ItemStack(ModItems.SKY_CRYSTAL.get(), 3)); }), 10);
        step("altar_mikiko_gear", () -> { if (mc.player.containerMenu instanceof KarmaAltarMenu m) log("Mikiko with a Sky Sword in: target=" + m.targetKind() + " level=" + m.currentLevel()); }, 4);
        step(null, () -> { mc.player.closeContainer(); server(p -> { p.closeContainer(); p.getInventory().clearContent(); }); }, 10);

        // ---- Rhalix (not Mikiko): no blade, forging gear
        step(null, () -> CHAR.accept(CharacterType.RHALIX), 40);
        step(null, () -> server(p -> {
            p.setGameMode(GameType.SURVIVAL); p.setInvulnerable(true);
            com.xenowarriors.karma.Karma.add(p, 1000);
            p.getInventory().clearContent();
            p.getInventory().add(new ItemStack(ModItems.CORRUPTED_SHARD.get(), 40));
            p.getInventory().add(new ItemStack(ModItems.SKY_CRYSTAL.get(), 8));
            openAltar(p);
        }), 20);
        step("altar_other_empty", () -> {
            if (mc.player.containerMenu instanceof KarmaAltarMenu m)
                log("Rhalix altar: mikiko=" + m.mikiko() + " target=" + m.targetKind() + " blade fits the slot=" + m.getSlot(KarmaAltarMenu.ITEM).mayPlace(new ItemStack(ModItems.HEKUBLADE.get()))
                        + " corrupted sword fits=" + m.getSlot(KarmaAltarMenu.ITEM).mayPlace(new ItemStack(com.xenowarriors.sky.ModTools.CORRUPTED_SWORD.get()))
                        + " iron sword fits=" + m.getSlot(KarmaAltarMenu.ITEM).mayPlace(new ItemStack(Items.IRON_SWORD)));
        }, 4);
        step(null, () -> server(p -> {
            ItemStack sw = new ItemStack(com.xenowarriors.sky.ModTools.CORRUPTED_SWORD.get());
            sw.setDamageValue(300);
            log("before: " + gear(sw));
            put(p, KarmaAltarMenu.ITEM, sw);
            put(p, KarmaAltarMenu.MATERIAL, new ItemStack(ModItems.CORRUPTED_SHARD.get(), 21));
        }), 10);
        step("altar_other_gear", () -> {}, 4);
        for (int i = 1; i <= 4; i++) {
            int ii = i;
            step(null, () -> server(p -> {
                KarmaAltarMenu m = menu(p);
                if (m == null) return;
                float k0 = com.xenowarriors.karma.Karma.get(p);
                boolean ok = m.clickMenuButton(p, 0);
                log("forge #" + ii + ": ok=" + ok + " " + gear(m.item()) + " shards left=" + m.material().getCount() + " karma " + Math.round(k0) + "->" + Math.round(com.xenowarriors.karma.Karma.get(p)));
            }), 10);
        }
        step(null, () -> hoverItemSlot(mc), 6);
        step("altar_other_forged_tooltip", () -> {
            if (mc.player.containerMenu instanceof KarmaAltarMenu m) {
                List<String> lines = new ArrayList<>();
                for (var c : m.item().getTooltipLines(mc.player, TooltipFlag.NORMAL)) lines.add(c.getString());
                log("tooltip: " + String.join(" | ", lines));
            }
        }, 4);
        // wrong material, and a tool
        step(null, () -> server(p -> {
            KarmaAltarMenu m = menu(p);
            if (m == null) return;
            ItemStack back = m.item().copy();
            put(p, KarmaAltarMenu.ITEM, new ItemStack(com.xenowarriors.sky.ModTools.SKY_PICKAXE.get()));
            log("sky pickaxe + corrupted shards: level=" + m.currentLevel() + " upgrade ok=" + m.clickMenuButton(p, 0));
            put(p, KarmaAltarMenu.MATERIAL, new ItemStack(ModItems.SKY_CRYSTAL.get(), 3));
            boolean ok = m.clickMenuButton(p, 0);
            ItemStack pick = m.item();
            float dig = pick.getDestroySpeed(Blocks.STONE.defaultBlockState());
            log("sky pickaxe + sky crystals: ok=" + ok + " " + gear(pick) + " (dig speed bonus applied in BreakSpeed: x" + f1(1 + GearUpgrades.DIG * GearUpgrades.level(pick)) + " of " + f1(dig) + ")");
            p.getInventory().add(back);
        }), 10);
        step("altar_other_pickaxe", () -> {
            if (mc.player.containerMenu instanceof KarmaAltarMenu m) {
                List<String> lines = new ArrayList<>();
                for (var c : m.item().getTooltipLines(mc.player, TooltipFlag.NORMAL)) lines.add(c.getString());
                log("tooltip: " + String.join(" | ", lines));
            }
        }, 4);
        step(null, () -> { mc.player.closeContainer(); server(p -> { p.closeContainer(); p.setInvulnerable(false); p.setGameMode(GameType.CREATIVE); p.getInventory().clearContent(); }); }, 10);
    }

    // ================================================================== 4: Room Technology in survival

    private static void room(Minecraft mc) {
        int[] zs = new int[2];
        step(null, () -> server(p -> {
            home(p);
            p.setGameMode(GameType.SURVIVAL); p.setInvulnerable(true);
            p.getPersistentData().remove("xw_room_next");
            com.xenowarriors.karma.Karma.add(p, 1000);
            p.getInventory().clearContent();
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.ROOM_TECH.get()));
            float k0 = com.xenowarriors.karma.Karma.get(p);
            p.getMainHandItem().use(p.level(), p, InteractionHand.MAIN_HAND);
            log("room, no enemies around: opened=" + RoomTech.open(p) + " karma " + Math.round(k0) + "->" + Math.round(com.xenowarriors.karma.Karma.get(p)));
            ServerLevel l = p.serverLevel();
            l.setBlock(p.blockPosition().offset(2, 0, 2), Blocks.OAK_LOG.defaultBlockState(), 3);
            for (int i = 0; i < 2; i++) {
                var z = EntityType.ZOMBIE.create(l);
                z.moveTo(p.getX() + (i == 0 ? 5 : -5), p.getY(), p.getZ() + 6, 180, 0);
                z.setPersistenceRequired();
                l.addFreshEntity(z);
                zs[i] = z.getId();
            }
        }), 20);
        step(null, () -> server(p -> {
            p.getCooldowns().removeCooldown(ModItems.ROOM_TECH.get());
            float k0 = com.xenowarriors.karma.Karma.get(p);
            p.getMainHandItem().use(p.level(), p, InteractionHand.MAIN_HAND);
            log("room in survival with 2 zombies: opened=" + RoomTech.open(p) + " in Room dimension=" + RoomTech.is(p.level()) + " karma " + Math.round(k0) + "->" + Math.round(com.xenowarriors.karma.Karma.get(p))
                    + " cooldown left=" + (p.getPersistentData().getLong("xw_room_next") - p.serverLevel().getGameTime()) + " ticks");
        }), 40);
        step(null, () -> { mc.options.hideGui = false; mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); mc.player.setXRot(20); }, 10);
        step("room_inside_survival", () -> {}, 3);
        step(null, () -> server(p -> {
            BlockPos log = p.blockPosition().offset(2, 0, 2);
            String was = p.level().getBlockState(log).getBlock().getName().getString();
            boolean broke = p.gameMode.destroyBlock(log);
            log("in the Room: broke the copied " + was + " by hand: " + broke + " now=" + p.level().getBlockState(log).getBlock().getName().getString());
        }), 10);
        step(null, () -> server(p -> {
            int items = p.serverLevel().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(8)).size();
            log("in the Room: item drops from the copied block = " + items + " (should be 0 - it's a copy)");
            int killed = 0;
            for (Mob m : p.serverLevel().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(30), e -> e instanceof net.minecraft.world.entity.monster.Zombie)) { m.hurt(p.damageSources().playerAttack(p), 1000); killed++; }
            log("in the Room: killed " + killed + " zombies");
        }), 110);
        step(null, () -> server(p -> {
            List<String> drops = new ArrayList<>();
            for (ItemEntity it : p.serverLevel().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(12))) drops.add(it.getItem().getCount() + "x " + it.getItem().getHoverName().getString());
            int logs = 0;
            for (ItemStack s : p.getInventory().items) if (s.is(Items.OAK_LOG)) logs += s.getCount();
            log("after the Room closed: in Room=" + RoomTech.is(p.level()) + " open=" + RoomTech.open(p) + " real oak log still there=" + p.level().getBlockState(BlockPos.containing(p.position()).offset(2, 0, 2)).is(Blocks.OAK_LOG)
                    + " oak logs in inventory=" + logs + " items brought back=" + drops);
            var z = EntityType.ZOMBIE.create(p.serverLevel());
            z.moveTo(p.getX() + 4, p.getY(), p.getZ() + 4, 0, 0); p.serverLevel().addFreshEntity(z);
            p.getCooldowns().removeCooldown(ModItems.ROOM_TECH.get());
            p.getMainHandItem().use(p.level(), p, InteractionHand.MAIN_HAND);
            log("right away again (zombie near): opened=" + RoomTech.open(p) + " (recharging " + (p.getPersistentData().getLong("xw_room_next") - p.serverLevel().getGameTime()) / 20 + "s)");
        }), 130);
        step(null, () -> { mc.options.setCameraType(CameraType.FIRST_PERSON); server(p -> { if (RoomTech.open(p)) RoomTech.use(p); p.setInvulnerable(false); p.setGameMode(GameType.CREATIVE); home(p); p.getInventory().clearContent(); }); }, 40);
    }

    // ================================================================== 5a: NPCs shooting from vehicles

    private static final AtomicInteger SHOTS_NPC = new AtomicInteger(), SHOTS_GOV = new AtomicInteger();
    private static final int[] SHOOTERS = {-1, -1, -1};
    private static boolean listening;

    private static void shooting(Minecraft mc) {
        if (!listening) {
            listening = true;
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.entity.EntityJoinLevelEvent e) -> {
                if (e.getLevel().isClientSide() || !(e.getEntity() instanceof net.minecraft.world.entity.projectile.Projectile pr) || pr.getOwner() == null) return;
                int o = pr.getOwner().getId();
                if (o == SHOOTERS[0]) SHOTS_NPC.incrementAndGet();
                else if (o == SHOOTERS[1] || o == SHOOTERS[2]) SHOTS_GOV.incrementAndGet();
            });
        }
        int[] ids = new int[4];
        step(null, () -> server(p -> {
            home(p);
            ServerLevel l = p.serverLevel();
            // a gunner in the back seat of a parked car, a zombie 12 blocks off
            VehicleEntity car = Vehicles.TYPES.get(VehicleKind.CAR).get().create(l);
            car.moveTo(base.getX() + 0.5 - 6, base.getY(), base.getZ() + 6.5, 0, 0);
            l.addFreshEntity(car);
            var g = com.xenowarriors.npc.ModNpcs.HUMAN.get().create(l);
            g.moveTo(car.position());
            net.minecraft.nbt.CompoundTag t = new net.minecraft.nbt.CompoundTag();
            g.addAdditionalSaveData(t);
            t.putInt("Variant", java.util.Arrays.asList(com.xenowarriors.npc.NpcLooks.HUMANS).indexOf("gunner"));
            g.readAdditionalSaveData(t);
            g.setPersistenceRequired();
            l.addFreshEntity(g);
            g.startRiding(car, true);
            SHOOTERS[0] = g.getId();
            // a Gov patrol truck: guard at the wheel and one riding along
            VehicleEntity truck = Vehicles.TYPES.get(VehicleKind.GOV_TRUCK).get().create(l);
            truck.moveTo(base.getX() + 0.5 + 8, base.getY(), base.getZ() + 6.5, 0, 0);
            l.addFreshEntity(truck);
            for (int i = 0; i < 2; i++) {
                var a = com.xenowarriors.gov.GovEntities.GOV_AGENT.get().create(l);
                a.moveTo(truck.position());
                a.setup(true, i, truck.blockPosition());
                a.setPersistenceRequired();
                l.addFreshEntity(a);
                a.startRiding(truck, true);
                SHOOTERS[1 + i] = a.getId();
            }
            ids[0] = car.getId(); ids[1] = truck.getId();
            for (int i = 0; i < 2; i++) {
                var z = EntityType.HUSK.create(l);
                z.moveTo(base.getX() + 0.5 + (i == 0 ? -6 : 8), base.getY(), base.getZ() + 22, 180, 0);
                z.setNoAi(true); z.setPersistenceRequired(); z.setInvulnerable(true);
                l.addFreshEntity(z);
                ids[2 + i] = z.getId();
            }
            SHOTS_NPC.set(0); SHOTS_GOV.set(0);
        }), 30);
        step(null, () -> server(p -> {
            ServerLevel l = p.serverLevel();
            if (l.getEntity(SHOOTERS[0]) instanceof Mob g && l.getEntity(ids[2]) instanceof LivingEntity z) g.setTarget(z);
            for (int i = 1; i <= 2; i++) if (l.getEntity(SHOOTERS[i]) instanceof Mob a && l.getEntity(ids[3]) instanceof LivingEntity z) a.setTarget(z);
        }), 5);
        for (int t = 1; t <= 3; t++) {
            int tt = t;
            step(null, () -> server(p -> {
                ServerLevel l = p.serverLevel();
                for (int i = 1; i <= 2; i++) if (l.getEntity(SHOOTERS[i]) instanceof Mob a && l.getEntity(ids[3]) instanceof LivingEntity z && a.getTarget() == null) a.setTarget(z);
                if (l.getEntity(SHOOTERS[0]) instanceof Mob g && l.getEntity(ids[2]) instanceof LivingEntity z && g.getTarget() == null) g.setTarget(z);
                Entity g = l.getEntity(SHOOTERS[0]), a1 = l.getEntity(SHOOTERS[1]), a2 = l.getEntity(SHOOTERS[2]);
                log("shooting from vehicles t=" + tt * 2 + "s: car gunner riding=" + (g != null && g.getVehicle() != null) + " shots=" + SHOTS_NPC.get()
                        + " | gov truck guards riding=" + (a1 != null && a1.getVehicle() != null) + "/" + (a2 != null && a2.getVehicle() != null) + " shots=" + SHOTS_GOV.get());
            }), 40);
        }
        int[] cam = {-1, 0, 0};
        camera(mc, cam, c -> { c[0] = base.getX() + 0.5 + 1; c[1] = base.getY() + 4; c[2] = base.getZ() - 6; c[3] = 0; c[4] = 12; });
        step("npc_shoot_vehicle", () -> {}, 3);
        cameraOff(mc);
        step(null, () -> server(World5Tests::clear), 5);
    }

    // ================================================================== 5b-c: facing, tilt, Mk II

    private static int vehicle;

    private static void ride(VehicleKind k, double up) {
        step(null, () -> server(p -> {
            home(p);
            p.setGameMode(GameType.CREATIVE);
            ServerLevel l = p.serverLevel();
            VehicleEntity v = Vehicles.TYPES.get(k).get().create(l);
            v.moveTo(base.getX() + 0.5, base.getY() + up, base.getZ() - 20.5, 0, 0);
            v.yBodyRot = 0; v.yHeadRot = 0;
            l.addFreshEntity(v);
            vehicle = v.getId();
            p.setYRot(0);
            p.startRiding(v, true);
        }), 20);
    }

    private static VehicleEntity clientVehicle(Minecraft mc) { return mc.level.getEntity(vehicle) instanceof VehicleEntity v ? v : null; }

    private static double heading(VehicleEntity v) {
        double dx = v.getX() - v.xo, dz = v.getZ() - v.zo;
        return Math.hypot(dx, dz) < 0.02 ? Double.NaN : Mth.wrapDegrees(Math.toDegrees(Math.atan2(dz, dx)) - 90);
    }

    private static void sample(Minecraft mc, String what, double[] worst) {
        VehicleEntity v = clientVehicle(mc);
        if (v == null) return;
        double h = heading(v);
        double move = Double.isNaN(h) ? 0 : Math.abs(Mth.wrapDegrees(h - v.getYRot()));
        if (Math.abs(v.speed()) < 0.05) move = 0;          // (hardly moving: no heading to speak of)
        double body = Math.abs(Mth.wrapDegrees(v.yBodyRot - v.getYRot()));
        worst[0] = Math.max(worst[0], body); worst[1] = Math.max(worst[1], move);
        worst[2] = Math.max(worst[2], Math.abs(v.pitchTilt)); worst[3] = Math.max(worst[3], Math.abs(v.rollTilt));
        var s = Minecraft.getInstance().getSingleplayerServer();
        float[] sy = {Float.NaN};
        if (s != null) { var sl = s.getLevel(v.level().dimension()); if (sl != null && sl.getEntity(vehicle) instanceof VehicleEntity se) sy[0] = se.getYRot(); }
        double sv = Float.isNaN(sy[0]) ? 0 : Math.abs(Mth.wrapDegrees(sy[0] - v.getYRot()));
        worst[4] = Math.max(worst[4], sv);
        log(what + ": yaw " + f1(v.getYRot()) + " body " + f1(v.yBodyRot) + " moving " + (Double.isNaN(h) ? "-" : f1(h)) + " speed " + String.format("%.2f", v.speed())
                + " pitch " + f1(v.pitchTilt) + " roll " + f1(v.rollTilt) + " up " + f1(v.groundBelow()) + " server yaw " + f1(sy[0]));
    }

    private static void vehicles(Minecraft mc) {
        // ---- a car turning: body = yaw = where it's going
        double[] car = new double[5];
        ride(VehicleKind.CAR, 0);
        step(null, () -> { mc.options.hideGui = false; mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); mc.options.keyUp.setDown(true); }, 20);
        step(null, () -> mc.options.keyRight.setDown(true), 1);
        for (int i = 0; i < 8; i++) { int ii = i; step(i == 4 ? "car_turning" : null, () -> sample(mc, "car turning t" + ii, car), 4); }
        step(null, () -> { mc.options.keyRight.setDown(false); mc.options.keyLeft.setDown(true); }, 1);
        for (int i = 0; i < 6; i++) { int ii = i; step(null, () -> sample(mc, "car turning back t" + ii, car), 4); }
        step(null, () -> { mc.options.keyLeft.setDown(false); mc.options.keyUp.setDown(false); mc.options.keyDown.setDown(true); }, 20);
        for (int i = 0; i < 4; i++) { int ii = i; step(null, () -> sample(mc, "car braking/reversing t" + ii, car), 5); }
        step(null, () -> { mc.options.keyDown.setDown(false); log("car worst: body vs yaw " + f1(car[0]) + " deg, motion vs yaw " + f1(car[1]) + " deg, server vs client yaw " + f1(car[4]) + " deg"); }, 5);

        // ---- the helicopter: climbs, flies forward (nose down), turns (banks)
        double[] heli = new double[5];
        ride(VehicleKind.HELICOPTER, 0);
        step(null, () -> { mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); mc.options.keyJump.setDown(true); }, 40);
        step(null, () -> { mc.options.keyJump.setDown(false); mc.options.keyUp.setDown(true); }, 30);
        for (int i = 0; i < 4; i++) { int ii = i; step(null, () -> sample(mc, "heli forward t" + ii, heli), 4); }
        // side view while it flies forward along +z
        int[] cam = {-1, 0, 0};
        camera(mc, cam, c -> { VehicleEntity v = clientVehicle(mc); double x = v == null ? base.getX() : v.getX(), y = v == null ? base.getY() + 6 : v.getY(), z = v == null ? base.getZ() : v.getZ();
            c[0] = x + 16; c[1] = y + 1.5; c[2] = z + 12; c[3] = 90; c[4] = 0; });
        step("heli_forward_side", () -> sample(mc, "heli forward (side shot)", heli), 1);
        cameraOff(mc);
        step(null, () -> {}, 1);
        for (int i = 0; i < 8; i++) { int ii = i; step(null, () -> { mc.player.setYRot(mc.player.getYRot() + 9F); sample(mc, "heli turning right t" + ii, heli); }, 2); }
        step("heli_banking", () -> sample(mc, "heli banking", heli), 1);
        step(null, () -> { mc.options.keyUp.setDown(false); log("heli worst: body vs yaw " + f1(heli[0]) + ", motion vs yaw " + f1(heli[1]) + ", max pitch " + f1(heli[2]) + ", max roll " + f1(heli[3]) + ", server vs client yaw " + f1(heli[4])); }, 20);

        // ---- Mk II: the lineup, the dealer page, a flying car
        step(null, () -> server(p -> {
            home(p);
            ServerLevel l = p.serverLevel();
            VehicleKind[] row = {VehicleKind.CAR, VehicleKind.CAR_MK2, VehicleKind.MOTORBIKE, VehicleKind.MOTORBIKE_MK2, VehicleKind.HELICOPTER, VehicleKind.HELICOPTER_MK2, VehicleKind.GOV_TRUCK, VehicleKind.GOV_TRUCK_MK2};
            double x = -15;
            for (VehicleKind k : row) {
                VehicleEntity v = Vehicles.TYPES.get(k).get().create(l);
                v.moveTo(base.getX() + 0.5 + x, base.getY(), base.getZ() + 10.5, 180, 0);
                v.yBodyRot = 180; v.yHeadRot = 180;
                l.addFreshEntity(v);
                x += k.width + 0.8;
                log("vehicle " + k.id + ": " + k.title + " top speed " + k.maxSpeed + " (" + Math.round(k.maxSpeed * 72) + " km/h) health " + k.health + " price " + k.price + " flies=" + (k.heli() || k.hovers()));
            }
        }), 30);
        camera(mc, cam, c -> { c[0] = base.getX() + 0.5 + 1; c[1] = base.getY() + 4; c[2] = base.getZ() - 6; c[3] = 0; c[4] = 14; });
        step("mk2_lineup", () -> {}, 3);
        cameraOff(mc);
        step(null, () -> { com.xenowarriors.client.vehicle.DealerScreen.page(true); com.xenowarriors.client.vehicle.DealerScreen.open(500); }, 10);
        step("mk2_dealer", () -> {}, 3);
        step(null, () -> { com.xenowarriors.client.vehicle.DealerScreen.page(false); mc.setScreen(null); }, 5);
        double[] mk = new double[5];
        ride(VehicleKind.CAR_MK2, 0);
        step(null, () -> { mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); mc.options.keyJump.setDown(true); }, 30);
        step(null, () -> { mc.options.keyJump.setDown(false); sample(mc, "car mk2 after 30 ticks of Jump", mk); }, 1);
        step(null, () -> mc.options.keyUp.setDown(true), 22);
        step(null, () -> sample(mc, "car mk2 flying forward", mk), 1);
        camera(mc, cam, c -> { VehicleEntity v = clientVehicle(mc); double x = v == null ? base.getX() : v.getX(), y = v == null ? base.getY() + 6 : v.getY(), z = v == null ? base.getZ() : v.getZ();
            c[0] = x + 14; c[1] = y + 1; c[2] = z + 10; c[3] = 90; c[4] = 0; });
        step("mk2_car_flying", () -> sample(mc, "car mk2 flying (side shot)", mk), 1);
        cameraOff(mc);
        step(null, () -> { mc.options.keyUp.setDown(false); mc.options.keySprint.setDown(true); }, 80);
        step(null, () -> { mc.options.keySprint.setDown(false); VehicleEntity v = clientVehicle(mc); sample(mc, "car mk2 came down", mk);
            log("car mk2: onGround=" + (v != null && v.onGround()) + " airborne=" + (v != null && v.airborne())); }, 10);
        // top speed on the ground: base car vs Mk II
        for (VehicleKind k : new VehicleKind[]{VehicleKind.CAR, VehicleKind.CAR_MK2}) {
            ride(k, 0);
            step(null, () -> mc.options.keyUp.setDown(true), 45);
            step(null, () -> { VehicleEntity v = clientVehicle(mc); log("ground top speed " + k.id + ": " + (v == null ? "?" : String.format("%.2f blocks/tick (%d km/h)", v.speed(), Math.round(v.speed() * 72)))); mc.options.keyUp.setDown(false); }, 10);
        }
        step(null, () -> { mc.options.setCameraType(CameraType.FIRST_PERSON); server(p -> { home(p); }); }, 10);
    }
}

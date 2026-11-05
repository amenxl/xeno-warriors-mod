package com.xenowarriors.client.test;

import com.xenowarriors.bot.BotRanged;
import com.xenowarriors.bot.PartyBotEntity;
import com.xenowarriors.bot.PartyBots;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.compat.DmzGuns;
import com.xenowarriors.fox.FoxPets;
import com.xenowarriors.npc.rebuild.RebuildData;
import com.xenowarriors.npc.rebuild.VillageRebuild;
import com.xenowarriors.wing.WingPets;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Fox;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.function.Consumer;

/**
 * UI tests for the misc3 work. Focus "misc3": the Pet Whistle calls every pet for any character, and villages don't
 * rebuild what players dig (but do rebuild creeper damage). Focus "dmzmisc3" (DragonMineZ loaded): party bots shoot
 * the Blaster Rifle / Burst Cannon / Blaster Mortar. Results are "XW-TEST misc3 ... PASS/FAIL", ending with a summary.
 */
public final class Misc3Tests {
    private Misc3Tests() {}

    @FunctionalInterface
    public interface Adder { void add(String shot, Runnable action, int delay); }

    private static Adder S;
    private static Consumer<Consumer<ServerPlayer>> SERVER;
    private static Consumer<String> LOG;
    private static Consumer<CharacterType> CHAR;
    private static int pass, fail;
    private static final List<String> FAILED = new ArrayList<>();

    public static boolean handles(String focus) { return focus != null && (focus.startsWith("misc3") || focus.startsWith("dmzmisc3")); }

    public static void build(Minecraft mc, String focus, Adder steps, Consumer<Consumer<ServerPlayer>> server, Consumer<CharacterType> character, Consumer<String> log) {
        S = steps; SERVER = server; LOG = log; CHAR = character;
        step(null, () -> { mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE); mc.getToasts().clear(); mc.setScreen(null); }, 20);
        if (focus.startsWith("dmz")) blasters(mc);
        else {
            if (!focus.contains("rebuild")) whistle(mc);
            if (!focus.contains("whistle")) rebuild(mc);
        }
        step(null, () -> server(p -> log("summary pass=" + pass + " fail=" + fail + (FAILED.isEmpty() ? "" : " failed: " + FAILED))), 5);
    }

    private static void log(String s) { LOG.accept("misc3 " + s); }
    private static void server(Consumer<ServerPlayer> f) {
        SERVER.accept(p -> { try { f.accept(p); } catch (Throwable t) { check("server step", false, String.valueOf(t)); com.xenowarriors.XenoWarriors.LOGGER.error("XW-TEST misc3", t); } });
    }
    private static void step(String shot, Runnable a, int delay) { S.add(shot == null ? null : "misc3_" + shot, a, delay); }
    private static void check(String what, boolean ok, String detail) {
        if (ok) pass++; else { pass += 0; fail++; FAILED.add(what); }
        log(what + ": " + detail + " -> " + (ok ? "PASS" : "FAIL"));
    }
    private static String f(double d) { return String.format(Locale.ROOT, "%.1f", d); }

    // ================================================================== 1: the Pet Whistle

    private static UUID foxId, chickId;

    private static Mob find(ServerPlayer p, UUID id) {
        return id != null && p.serverLevel().getEntity(id) instanceof Mob m && m.isAlive() ? m : null;
    }

    private static BlockPos home;

    /** Along the stone strip (on the ground, not flying): at the start (0) or 40 blocks off either way. */
    private static void farAway(ServerPlayer p, int dx) {
        p.getAbilities().flying = false; p.onUpdateAbilities();
        p.connection.teleport(home.getX() + dx + .5, home.getY(), home.getZ() + .5, p.getYRot(), p.getXRot());
    }

    /** The pets' current bodies (a pet called back from far away may get a new one). */
    private static void refresh(ServerPlayer p) {
        if (!FoxPets.records(p).isEmpty() && FoxPets.records(p).getCompound(0).hasUUID("uuid")) foxId = FoxPets.records(p).getCompound(0).getUUID("uuid");
        if (!WingPets.records(p).isEmpty() && WingPets.records(p).getCompound(0).hasUUID("uuid")) chickId = WingPets.records(p).getCompound(0).getUUID("uuid");
    }

    private static String dist(ServerPlayer p) {
        refresh(p);
        Mob fox = find(p, foxId), ch = find(p, chickId);
        String wr = WingPets.records(p).isEmpty() ? "" : " [chicken rec dead=" + WingPets.records(p).getCompound(0).getBoolean("dead") + " gen=" + WingPets.records(p).getCompound(0).getInt("gen") + "]";
        return wr + " fox " + (fox == null ? "gone" : f(fox.distanceTo(p))) + ", chicken " + (ch == null ? "gone" : f(ch.distanceTo(p)));
    }

    private static boolean near(ServerPlayer p, boolean fox) { refresh(p); Mob m = find(p, fox ? foxId : chickId); return m != null && m.distanceTo(p) <= 6; }

    private static void whistle(Minecraft mc) {
        // a fox (Mikiko tames it) and a chicken (Rhalix tames it)
        step(null, () -> CHAR.accept(CharacterType.MIKIKO), 30);
        step(null, () -> server(p -> {
            p.setGameMode(GameType.CREATIVE);
            ServerLevel l = p.serverLevel();
            home = p.blockPosition();                                            // a flat stone strip 90 blocks long, open above
            for (int dx = -46; dx <= 46; dx++) for (int dz = -4; dz <= 4; dz++) {
                l.setBlock(home.offset(dx, -1, dz), Blocks.SMOOTH_STONE.defaultBlockState(), 2);
                for (int dy = 0; dy <= 4; dy++) l.setBlock(home.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 2);
            }
            farAway(p, 0);
            Fox fox = EntityType.FOX.create(l);
            fox.moveTo(p.getX() + 2, p.getY(), p.getZ() + 2, 0, 0);
            l.addFreshEntity(fox);
            FoxPets.tameNow(p, fox);
            foxId = fox.getUUID();
            log("fox tamed: records=" + FoxPets.records(p).size());
        }), 30);
        step(null, () -> CHAR.accept(CharacterType.RHALIX), 30);
        step(null, () -> server(p -> {
            ServerLevel l = p.serverLevel();
            Chicken ch = EntityType.CHICKEN.create(l);
            ch.moveTo(p.getX() - 2, p.getY(), p.getZ() + 2, 0, 0);
            l.addFreshEntity(ch);
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WHEAT_SEEDS, 64));
            for (int i = 0; i < 40 && WingPets.records(p).isEmpty(); i++)
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.entity.player.PlayerInteractEvent.EntityInteract(p, InteractionHand.MAIN_HAND, ch));
            chickId = WingPets.records(p).isEmpty() ? null : WingPets.records(p).getCompound(0).getUUID("uuid");
            log("chicken tamed: records=" + WingPets.records(p).size());
            check("whistle setup", FoxPets.records(p).size() == 1 && WingPets.records(p).size() == 1, "foxes " + FoxPets.records(p).size() + ", winged " + WingPets.records(p).size());
        }), 20);
        // Kaeru (neither Mikiko nor Rhalix) blows the Pet Whistle item, far from both
        step(null, () -> CHAR.accept(CharacterType.KAERU), 30);
        step(null, () -> server(p -> {
            p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            farAway(p, 40);
            Mob fox = find(p, foxId), ch = find(p, chickId);
            if (fox != null) fox.setNoAi(true);                                    // (so they don't just walk after them)
            if (ch != null) ch.setNoAi(true);
        }), 20);
        step(null, () -> server(p -> {
            log("before the whistle (Kaeru): " + dist(p));
            ItemStack w = new ItemStack(com.xenowarriors.item.ModItems.FOX_WHISTLE.get());
            p.setItemInHand(InteractionHand.MAIN_HAND, w);
            p.getCooldowns().removeCooldown(w.getItem());
            w.getItem().use(p.serverLevel(), p, InteractionHand.MAIN_HAND);
        }), 10);
        step("whistle_kaeru", () -> server(p -> check("pet whistle item as Kaeru calls fox + winged", near(p, true) && near(p, false),
                dist(p) + " (answered: " + FoxPets.lastWhistle + ") item name " + new ItemStack(com.xenowarriors.item.ModItems.FOX_WHISTLE.get()).getHoverName().getString())), 10);
        // Rhalix, through the Pets menu's whistle button: foxes answer him too now
        step(null, () -> CHAR.accept(CharacterType.RHALIX), 20);
        step(null, () -> server(p -> { farAway(p, -40); }), 20);
        step(null, () -> server(p -> {
            log("before the Pets-menu whistle (Rhalix): " + dist(p));
            com.xenowarriors.pet.PetCare.action(p, 0, 0, com.xenowarriors.pet.PetCare.WHISTLE, 0, null);
        }), 10);
        step(null, () -> server(p -> check("pets menu whistle as Rhalix calls the fox too", near(p, true) && near(p, false), dist(p) + " (answered: " + FoxPets.lastWhistle + ")")), 10);
        // Mikiko, /xeno fox call: the chicken answers her too
        step(null, () -> CHAR.accept(CharacterType.MIKIKO), 20);
        step(null, () -> server(p -> { farAway(p, 40); }), 20);
        step(null, () -> server(p -> p.server.getCommands().performPrefixedCommand(p.createCommandSourceStack().withPermission(4), "xeno fox call")), 10);
        step(null, () -> server(p -> {
            check("/xeno fox call as Mikiko calls the chicken too", near(p, true) && near(p, false), dist(p) + " (answered: " + FoxPets.lastWhistle + ")");
            Mob fox = find(p, foxId), ch = find(p, chickId);
            if (fox != null) fox.setNoAi(false);
            if (ch != null) ch.setNoAi(false);
            // a fox that ran away comes back to the whistle for anyone too (once recovered)
            if (fox != null) fox.kill();
        }), 10);
        step(null, () -> CHAR.accept(CharacterType.LEXY), 20);
        step(null, () -> server(p -> {
            CompoundTag rec = FoxPets.records(p).isEmpty() ? null : FoxPets.records(p).getCompound(0);
            if (rec != null) rec.putLong("awayUntil", 0L);                     // (skip the cool-down after running away)
            log("fox dead=" + (rec != null && rec.getBoolean("dead")) + " keys=" + (rec == null ? "-" : rec.getAllKeys()));
            ItemStack w = new ItemStack(com.xenowarriors.item.ModItems.FOX_WHISTLE.get());
            p.setItemInHand(InteractionHand.MAIN_HAND, w);
            p.getCooldowns().removeCooldown(w.getItem());
            w.getItem().use(p.serverLevel(), p, InteractionHand.MAIN_HAND);
        }), 10);
        step(null, () -> server(p -> {
            CompoundTag rec = FoxPets.records(p).isEmpty() ? null : FoxPets.records(p).getCompound(0);
            boolean back = rec != null && !rec.getBoolean("dead") && rec.hasUUID("uuid") && p.serverLevel().getEntity(rec.getUUID("uuid")) instanceof Fox f && f.isAlive() && f.distanceTo(p) < 8;
            log("fallen fox after Lexy's whistle: " + (back ? "back" : "not back (still recovering: " + (rec != null && com.xenowarriors.pet.PetCare.recovering(p, rec)) + ")"));
            if (rec != null && !com.xenowarriors.pet.PetCare.recovering(p, rec)) check("fallen fox answers Lexy's whistle", back, "dead=" + (rec.getBoolean("dead")));
            if (rec != null && rec.hasUUID("uuid")) foxId = rec.getUUID("uuid");
        }), 10);
        step("whistle_after", () -> {}, 5);
    }

    // ================================================================== 2: villages don't rebuild what players dig

    private static BlockPos center, bell, tntAt, creeperAt, shelf, wall, door;
    private static RebuildData.Village village;
    private static final List<Integer> CREEPER = new ArrayList<>(), TNT = new ArrayList<>();
    private static int dug0, bestLeft = Integer.MAX_VALUE;

    private static int idx(BlockPos q) { return village == null || q == null ? -1 : village.index(q); }
    private static boolean open(ServerLevel l, BlockPos q) { return q != null && l.getBlockState(q).isAir(); }

    private static void look(ServerPlayer p, BlockPos at, double back, double up) {
        p.getAbilities().flying = true; p.onUpdateAbilities();
        Vec3 eye = new Vec3(at.getX() + .5, at.getY() + up, at.getZ() + back + .5);
        Vec3 d = Vec3.atCenterOf(at).subtract(eye);
        float yaw = (float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90F), pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
        p.connection.teleport(eye.x, eye.y - 1.62, eye.z, yaw, pitch);
    }

    private static void rebuild(Minecraft mc) {
        step(null, () -> { CHAR.accept(CharacterType.RHALIX); server(p -> {
            p.setGameMode(GameType.CREATIVE);
            ServerLevel l = p.serverLevel();
            l.setDayTime(6000);
            l.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, p.server);
            l.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, p.server);
            l.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(true, p.server);
            VillageRebuild.speed = 4;
            BlockPos c = p.blockPosition();
            p.getAbilities().flying = true; p.onUpdateAbilities();
            p.connection.teleport(c.getX() + .5, 120, c.getZ() + .5, 0F, 60F);
        }); }, 60);
        step(null, () -> server(p -> {
            ServerLevel l = p.serverLevel();
            center = l.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG, p.blockPosition());
            p.connection.teleport(center.getX() + .5, center.getY() + 30, center.getZ() + .5, 0F, 60F);
        }), 60);
        step(null, () -> server(p -> {
            int r = p.server.getCommands().performPrefixedCommand(p.createCommandSourceStack().withPermission(4).withSuppressedOutput(),
                    "place structure minecraft:village_plains " + center.getX() + " " + center.getY() + " " + center.getZ());
            log("place structure result " + r + " at " + center.toShortString());
        }), 80);
        step(null, () -> server(p -> {
            ServerLevel l = p.serverLevel();
            bell = null;
            double bd = Double.MAX_VALUE;
            for (BlockPos q : BlockPos.betweenClosed(center.offset(-64, -6, -64), center.offset(64, 20, 64)))
                if (l.getBlockState(q).getBlock() instanceof BellBlock && q.distSqr(center) < bd) { bd = q.distSqr(center); bell = q.immutable(); }
            log("bell " + (bell == null ? "none" : bell.toShortString()));
            if (bell != null) p.connection.teleport(bell.getX() + .5, bell.getY() + 24, bell.getZ() + .5, 0F, 70F);
        }), 200);
        step(null, () -> server(p -> {
            ServerLevel l = p.serverLevel();
            if (bell == null) { check("village", false, "no bell"); return; }
            RebuildData d = VillageRebuild.data(l);
            village = d == null ? null : d.at(bell);
            if (village == null) village = VillageRebuild.snapshotNow(l, bell);
            if (village == null) { check("village", false, "snapshot failed"); return; }
            log("village #" + village.id + " blocks=" + village.size() + " pending=" + VillageRebuild.checkAll(l, village));
            for (Mob m : l.getEntitiesOfClass(Mob.class, new AABB(bell).inflate(80))) m.setInvulnerable(true);   // (nobody dies in the blasts)
            // two houses (beds at least 12 apart, away from the bell): one for the player's TNT, one for a creeper
            List<BlockPos> beds = new ArrayList<>();
            for (int i = 0; i < village.size(); i++) {
                BlockState s = village.state(i);
                if (s.getBlock() instanceof BedBlock && s.getValue(BedBlock.PART) == BedPart.HEAD && village.pos(i).distSqr(bell) > 144) beds.add(village.pos(i));
            }
            beds.sort(Comparator.comparingDouble(q -> q.distSqr(bell)));
            tntAt = beds.isEmpty() ? bell.offset(14, 0, 0) : beds.get(0);
            creeperAt = null;
            for (BlockPos q : beds) if (q.distSqr(tntAt) > 20 * 20) { creeperAt = q; break; }
            if (creeperAt == null) creeperAt = bell.offset(-16, 0, 0);
            // things the player digs by hand: the bell, a bookshelf (if any), a wall block, a door
            shelf = wall = door = null;
            for (int i = 0; i < village.size(); i++) {
                BlockPos q = village.pos(i);
                BlockState s = village.state(i);
                if (q.distSqr(tntAt) < 100 || q.distSqr(creeperAt) < 100) continue;
                if (shelf == null && s.is(Blocks.BOOKSHELF)) shelf = q;
                if (wall == null && (s.is(BlockTags.PLANKS) || s.is(Blocks.COBBLESTONE)) && q.getY() > bell.getY() && l.getBlockState(q).is(s.getBlock())) wall = q;
                if (door == null && s.getBlock() instanceof DoorBlock && s.getValue(DoorBlock.HALF) == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER) door = q;
            }
            log("targets: bell " + bell.toShortString() + " bookshelf " + (shelf == null ? "none" : shelf.toShortString()) + " wall " + (wall == null ? "none" : wall.toShortString())
                    + " door " + (door == null ? "none" : door.toShortString()) + " tnt " + tntAt.toShortString() + " creeper " + creeperAt.toShortString());
            look(p, bell, 8, 5);
        }), 30);
        step("dig_before", () -> {}, 2);
        // ---- the player digs (a real block break, through the game mode: the break event fires as for a click)
        step(null, () -> server(p -> {
            ServerLevel l = p.serverLevel();
            if (village == null) return;
            dug0 = VillageRebuild.dugTotal;
            p.setGameMode(GameType.SURVIVAL);
            for (BlockPos q : new BlockPos[]{bell, shelf, wall, door}) if (q != null) {
                boolean ok = p.gameMode.destroyBlock(q);
                log("player broke " + q.toShortString() + " ok=" + ok + " now " + l.getBlockState(q).getBlock().getName().getString() + " dug=" + village.isDug(idx(q)));
            }
            if (door != null) log("door upper half dug=" + village.isDug(idx(door.above())) + " now " + l.getBlockState(door.above()).getBlock().getName().getString());
            p.setGameMode(GameType.CREATIVE);
            p.getAbilities().flying = true; p.onUpdateAbilities();
            // TNT the player lit, at the first house
            PrimedTnt tnt = new PrimedTnt(l, tntAt.getX() + .5, tntAt.getY(), tntAt.getZ() + .5, p);
            tnt.setFuse(2);
            l.addFreshEntity(tnt);
            // a creeper at the other one
            Creeper cr = EntityType.CREEPER.create(l);
            cr.moveTo(creeperAt.getX() + .5, creeperAt.getY(), creeperAt.getZ() + .5, 0, 0);
            l.addFreshEntity(cr);
            cr.ignite();
        }), 60);
        step(null, () -> server(p -> {
            ServerLevel l = p.serverLevel();
            if (village == null) return;
            l.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new AABB(bell).inflate(80)).forEach(e -> e.discard());
            VillageRebuild.checkAll(l, village);
            CREEPER.clear(); TNT.clear();
            int tntDug = 0;
            for (int i = 0; i < village.size(); i++) {
                BlockPos q = village.pos(i);
                if (q.distSqr(creeperAt) < 8 * 8 && village.isPending(i)) CREEPER.add(i);
                if (q.distSqr(tntAt) < 8 * 8 && l.getBlockState(q).isAir() && !village.state(i).isAir()) { TNT.add(i); if (village.isDug(i)) tntDug++; }
            }
            int tntPending = 0; for (int i : TNT) if (village.isPending(i)) tntPending++;
            log("after blasts: creeper gaps pending=" + CREEPER.size() + "; TNT gaps=" + TNT.size() + " dug=" + tntDug + " pending=" + tntPending + "; dug marks " + (VillageRebuild.dugTotal - dug0));
            check("creeper damage is queued to rebuild", CREEPER.size() > 0, CREEPER.size() + " gaps");
            check("player's TNT crater is not queued", TNT.size() > 0 && tntPending == 0 && tntDug >= TNT.size() * 9 / 10, TNT.size() + " gaps, " + tntDug + " dug, " + tntPending + " pending");
            check("player-dug spots marked", (bell == null || village.isDug(idx(bell))) && (wall == null || village.isDug(idx(wall))) && (shelf == null || village.isDug(idx(shelf)))
                    && (door == null || village.isDug(idx(door)) && village.isDug(idx(door.above()))), "bell/wall/shelf/door");
            look(p, creeperAt, 14, 9);
        }), 20);
        step("after_blasts", () -> {}, 2);
        // ---- wait for the creeper's hole to be filled (or 4 minutes)
        for (int r = 1; r <= 48; r++) {
            int round = r;
            step(round == 6 ? "rebuilding" : null, () -> server(p -> {
                ServerLevel l = p.serverLevel();
                if (village == null || CREEPER.isEmpty()) return;
                int left = 0; for (int i : CREEPER) if (!l.getBlockState(village.pos(i)).is(village.state(i).getBlock())) left++;
                bestLeft = Math.min(bestLeft, left);
                if (round % 4 == 0 || left == 0) log("t=" + round * 5 + "s creeper gaps left " + left + "/" + CREEPER.size() + " workers " + VillageRebuild.workers(village) + " restored " + village.restored);
            }), 100);
        }
        step(null, () -> server(p -> {
            ServerLevel l = p.serverLevel();
            if (village == null) return;
            int left = 0; for (int i : CREEPER) if (!l.getBlockState(village.pos(i)).is(village.state(i).getBlock())) left++;
            int tntBack = 0; for (int i : TNT) if (!l.getBlockState(village.pos(i)).isAir()) tntBack++;
            bestLeft = Math.min(bestLeft, left);                                  // (afterwards villagers may trample a crop or a path again: that's new damage)
            check("creeper damage rebuilt", CREEPER.size() > 0 && bestLeft <= CREEPER.size() / 10, (CREEPER.size() - bestLeft) + "/" + CREEPER.size() + " back at best, " + (CREEPER.size() - left) + " now");
            check("player's TNT crater left open", tntBack == 0, tntBack + "/" + TNT.size() + " filled");
            check("player-mined bell not rebuilt", open(l, bell), "bell spot " + l.getBlockState(bell).getBlock().getName().getString());
            if (shelf != null) check("player-mined bookshelf not rebuilt", open(l, shelf), l.getBlockState(shelf).getBlock().getName().getString());
            if (wall != null) check("player-mined wall block not rebuilt", open(l, wall), l.getBlockState(wall).getBlock().getName().getString());
            if (door != null) check("player-mined door not rebuilt", open(l, door) && open(l, door.above()), l.getBlockState(door).getBlock().getName().getString());
            // the sweep and a full re-check don't bring them back either
            VillageRebuild.checkAll(l, village);
            check("full re-scan leaves dug spots alone", !village.isPending(idx(bell)) && (wall == null || !village.isPending(idx(wall))), "bell pending=" + village.isPending(idx(bell)));
            // saved and loaded: the dug marks survive
            RebuildData d = VillageRebuild.data(l);
            RebuildData back = RebuildData.load(d.save(new CompoundTag()));
            RebuildData.Village v2 = back.at(bell);
            check("dug marks saved", v2 != null && v2.dugCount() == village.dugCount() && v2.isDug(idx(bell)), village.dugCount() + " -> " + (v2 == null ? "-" : v2.dugCount()));
            look(p, bell, 8, 5);
        }), 5);
        step("dig_after", () -> {}, 2);
        // ---- the player puts the wall block back: it's looked after again (knocked out by something else -> rebuilt)
        step(null, () -> server(p -> {
            ServerLevel l = p.serverLevel();
            if (village == null || wall == null) return;
            l.setBlock(wall, village.state(idx(wall)), 3);
            VillageRebuild.checkAll(l, village);
            log("wall put back: dug=" + village.isDug(idx(wall)));
            l.removeBlock(wall, false);                                          // (not a player: a mob, a fire...)
            VillageRebuild.checkAll(l, village);
            check("put-back block is rebuilt again after other damage", !village.isDug(idx(wall)) && village.isPending(idx(wall)), "dug=" + village.isDug(idx(wall)) + " pending=" + village.isPending(idx(wall)));
            for (Mob m : l.getEntitiesOfClass(Mob.class, new AABB(bell).inflate(80))) m.setInvulnerable(false);
            VillageRebuild.speed = 1;
        }), 5);
    }

    // ================================================================== 3: bots and the Blaster guns (DragonMineZ)

    private static PartyBotEntity bot;
    private static Zombie target;
    private static int shots0, held0;
    private static Object killer;
    private static String killedBy = "-";

    private static ItemStack gun(DmzGuns.Kind k) {
        for (var it : DmzGuns.items()) if (DmzGuns.kindOf(new ItemStack(it)) == k) return new ItemStack(it);
        return ItemStack.EMPTY;
    }

    private static int used(ItemStack s) { return s.isEmpty() ? -1 : s.getDamageValue(); }

    private static ItemStack held() { return bot == null ? ItemStack.EMPTY : bot.getMainHandItem(); }

    private static void target(ServerPlayer p, double dz, float hp) {
        if (target != null) target.discard();
        Vec3 at = Dmz6Tests.at(p);
        target = EntityType.ZOMBIE.create(p.serverLevel());
        target.moveTo(at.x, at.y, at.z + dz, 180, 0);
        target.setNoAi(true); target.setPersistenceRequired();
        target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(Math.max(20, hp));
        target.setHealth(hp);
        p.serverLevel().addFreshEntity(target);
        if (bot != null) { bot.moveTo(at.x, at.y, at.z, 0, 0); bot.getNavigation().stop(); bot.setTarget(target); }
    }

    private static void stage(ServerPlayer p, DmzGuns.Kind k, boolean inBag) {
        if (bot == null) return;
        for (int i = 0; i < bot.inventory.getContainerSize(); i++) bot.inventory.setItem(i, ItemStack.EMPTY);
        bot.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        ItemStack g = gun(k);
        if (inBag) { bot.inventory.setItem(0, new ItemStack(Items.BOW)); bot.inventory.setItem(1, g); }
        else bot.setItemSlot(EquipmentSlot.MAINHAND, g);
        shots0 = BotRanged.blasterShots; held0 = BotRanged.blasterHeld;
    }

    private static void blasters(Minecraft mc) {
        step(null, () -> CHAR.accept(CharacterType.MIKIKO), 60);
        step(null, () -> { Dmz6Tests.noDmzScreen(mc); server(p -> {
            p.setGameMode(GameType.CREATIVE);
            Vec3 at = Dmz6Tests.at(p);
            Dmz6Tests.protect(p); Dmz6Tests.clearHostiles(p);
            com.xenowarriors.compat.DmzPerCharacter.markCreatedForTest(p);      // (no race picker over the shots)
            p.level().getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, p.server);
            p.serverLevel().setDayTime(6000);
            log("dmz on=" + DmzGuns.on() + " guns " + DmzGuns.items());
            bot = PartyBots.spawn(p, CharacterType.RHALIX, null);
            if (bot == null) { check("bot", false, "spawn failed"); return; }
            bot.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE, 20 * 60 * 30, 4, false, false));
            bot.moveTo(at.x, at.y, at.z, 0, 0);
            p.connection.teleport(at.x + 6, at.y, at.z - 3, 30F, 10F);
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.entity.living.LivingDeathEvent e) -> {
                if (e.getEntity() == target) { killer = e.getSource().getEntity(); killedBy = e.getSource().getDirectEntity() == null ? "-" : e.getSource().getDirectEntity().getClass().getSimpleName(); }
            });
        }); }, 60);
        for (DmzGuns.Kind k : DmzGuns.Kind.values()) {
            int[] dur0 = new int[1];
            float[] hp0 = new float[1];
            step(null, () -> { Dmz6Tests.noDmzScreen(mc); mc.options.setCameraType(CameraType.FIRST_PERSON); server(p -> {
                if (DmzGuns.items().isEmpty()) { check("guns registered", false, "none"); return; }
                stage(p, k, k == DmzGuns.Kind.RIFLE);                         // (the rifle starts in its bag next to a bow: it should pick the rifle)
                target(p, 14, 300F);
                hp0[0] = target.getHealth();
                PartyBots.testLog(bot, "misc3 " + k + " staged");
            }); }, k == DmzGuns.Kind.MORTAR ? 220 : 120);
            step(null, () -> Dmz6Tests.noDmzScreen(mc), 3);
            step("bot_" + k.name().toLowerCase(Locale.ROOT), () -> Dmz6Tests.noDmzScreen(mc), 2);
            step(null, () -> server(p -> {
                if (bot == null || target == null) return;
                ItemStack h = held();
                int shots = BotRanged.blasterShots - shots0;
                dur0[0] = used(h);
                synchronized (BotRanged.BLASTER_LOG) { log(k + " log " + BotRanged.BLASTER_LOG.subList(Math.max(0, BotRanged.BLASTER_LOG.size() - 8), BotRanged.BLASTER_LOG.size())); }
                check("bot fires " + k, DmzGuns.kindOf(h) == k && shots > 0, shots + " shots, holding " + h.getHoverName().getString() + ", target hp lost " + f(hp0[0] - target.getHealth()));
                check(k + " durability = shots (like a player)", dur0[0] == shots, "damage " + dur0[0] + " for " + shots + " shots");
                double secs = (k == DmzGuns.Kind.MORTAR ? 222 : 122) / 20.0;
                double rate = Math.max(0, shots - 1) / secs;
                double max = k == DmzGuns.Kind.RIFLE ? 20.0 / k.cooldown : 20.0 / k.cooldown + 0.05;   // (never faster than a player can)
                check(k + " fire rate within the player's", rate <= max + 0.01, f(rate) + "/s, player max " + f(max) + "/s");
                // now the owner steps into the line of fire
                Vec3 a = bot.position(), b = target.position();
                Vec3 mid = a.add(b).scale(0.5);
                p.getAbilities().flying = false; p.onUpdateAbilities();
                if (k == DmzGuns.Kind.MORTAR) p.connection.teleport(b.x + 2, b.y, b.z, 0F, 0F);   // (next to where it lands)
                else p.connection.teleport(mid.x, mid.y, mid.z, 0F, 0F);
                shots0 = BotRanged.blasterShots; held0 = BotRanged.blasterHeld;
            }), k == DmzGuns.Kind.MORTAR ? 120 : 60);
            step(null, () -> server(p -> {
                int shots = BotRanged.blasterShots - shots0, heldN = BotRanged.blasterHeld - held0;
                check(k + " holds fire with its owner in the way", shots == 0 && heldN > 0, shots + " shots, held " + heldN + " times, owner at " + f(p.distanceTo(bot)) + " from the bot");
                p.connection.teleport(bot.getX() + 6, bot.getY(), bot.getZ() - 3, 30F, 10F);
            }), 5);
        }
        // a nearly-spent rifle: it fires until it breaks, then it's gone (durability is its ammo)
        step(null, () -> server(p -> {
            if (bot == null) return;
            stage(p, DmzGuns.Kind.RIFLE, false);
            ItemStack h = held();
            h.setDamageValue(h.getMaxDamage() - 3);
            target(p, 12, 300F);
        }), 200);
        step(null, () -> server(p -> {
            boolean any = DmzGuns.kindOf(held()) != null;
            for (int i = 0; bot != null && i < bot.inventory.getContainerSize(); i++) if (DmzGuns.kindOf(bot.inventory.getItem(i)) != null) any = true;
            check("worn-out rifle breaks after its last uses", !any && BotRanged.blasterShots - shots0 == 3, (BotRanged.blasterShots - shots0) + " shots, still has a blaster=" + any);
        }), 5);
        // a kill with one counts for the bot (and so its owner)
        step(null, () -> server(p -> {
            if (bot == null) return;
            stage(p, DmzGuns.Kind.BURST, false);
            killer = null;
            target(p, 12, 6F);
        }), 120);
        step("bot_kill", () -> server(p -> {
            boolean byBot = killer instanceof net.minecraft.world.entity.Entity ke && PartyBots.botOf(ke) == bot;
            check("blaster kill credits the bot", target != null && !target.isAlive() && byBot && killedBy.contains("Ki"),
                    "target alive=" + (target != null && target.isAlive()) + " by " + killedBy + " killer " + (killer == null ? "none" : ((net.minecraft.world.entity.Entity) killer).getType().getDescriptionId())
                            + " (bot of owner: " + (killer instanceof PartyBotEntity pb && p.getUUID().equals(pb.ownerId())) + ")");
            synchronized (DmzGuns.LOG) { log("gun log tail " + DmzGuns.LOG.subList(Math.max(0, DmzGuns.LOG.size() - 4), DmzGuns.LOG.size())); }
        }), 5);
    }
}

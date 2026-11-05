package com.xenowarriors.client.test;

import com.xenowarriors.character.CharacterType;
import com.xenowarriors.npc.HumanNpcEntity;
import com.xenowarriors.npc.rebuild.RebuildData;
import com.xenowarriors.npc.rebuild.VillageRebuild;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * UI test for villages rebuilding themselves (focus "rebuild"): a plains village is placed on flattened ground, it's
 * recorded, a house is blown up, one of the gaps gets a different block (as a player would), and then the village's
 * people put it back - logged every few seconds until it's whole, compared block by block with the snapshot.
 * Results are logged as "XW-TEST rebuild ...".
 */
public final class RebuildTests {
    private RebuildTests() {}

    private static WorldTests.Adder S;
    private static Consumer<Consumer<ServerPlayer>> SERVER;
    private static Consumer<String> LOG;
    private static Consumer<CharacterType> CHAR;

    public static boolean handles(String focus) { return focus != null && focus.startsWith("rebuild"); }

    public static void build(Minecraft mc, String focus, WorldTests.Adder steps, Consumer<Consumer<ServerPlayer>> server, Consumer<CharacterType> character, Consumer<String> log) {
        S = steps; SERVER = server; LOG = log; CHAR = character;
        if (focus.contains("fast")) VillageRebuild.speed = 4;
        run(mc);
    }

    private static void log(String s) { LOG.accept("rebuild " + s); }
    private static void server(Consumer<ServerPlayer> f) { SERVER.accept(f); }
    private static void step(String shot, Runnable a, int delay) { S.add(shot == null ? null : "rb_" + shot, a, delay); }
    private static void wait(int t) { S.add(null, () -> {}, t); }
    private static String pct(int a, int b) { return b == 0 ? "-" : String.format("%.1f%%", 100.0 * a / b); }

    private static BlockPos center, bell, blast, gold;
    private static RebuildData.Village village;
    private static final List<Integer> BLAST = new ArrayList<>();
    private static int goldIdx = -1, firstFull = -1;
    private static long blastTime;

    private static void run(Minecraft mc) {
        // ---- flat ground, then a plains village on it
        step(null, () -> { mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE); mc.getToasts().clear(); mc.setScreen(null);
            CHAR.accept(CharacterType.RHALIX);
            server(p -> {
                p.setGameMode(GameType.CREATIVE);
                ServerLevel l = p.serverLevel();
                l.setDayTime(6000);
                l.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, p.server);
                l.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, p.server);
                l.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, p.server);
                l.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(true, p.server);
                log("gamerule xwVillageRebuild=" + l.getGameRules().getBoolean(VillageRebuild.RULE));
                BlockPos c = p.blockPosition();
                int gy = Math.max(c.getY(), l.getSeaLevel() + 2);
                center = new BlockPos(c.getX(), gy, c.getZ());
                for (int x = -56; x <= 56; x++) for (int z = -56; z <= 56; z++) {
                    for (int y = -4; y <= 32; y++) {
                        BlockState s = y < -1 ? Blocks.DIRT.defaultBlockState() : y == -1 ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.AIR.defaultBlockState();
                        BlockPos q = center.offset(x, y, z);
                        if (l.getBlockState(q) != s) l.setBlock(q, s, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                    }
                }
                l.getEntitiesOfClass(net.minecraft.world.entity.Entity.class, new AABB(center).inflate(70), e -> !(e instanceof net.minecraft.world.entity.player.Player)).forEach(e -> e.discard());
                p.getAbilities().flying = true; p.onUpdateAbilities();
                p.connection.teleport(center.getX() + .5, center.getY() + 30, center.getZ() + .5, 0F, 60F);
                log("ground at " + center.toShortString());
            });
        }, 60);
        step(null, () -> server(p -> {
            int r = p.server.getCommands().performPrefixedCommand(p.createCommandSourceStack().withPermission(4).withSuppressedOutput(),
                    "place structure minecraft:village_plains " + center.getX() + " " + center.getY() + " " + center.getZ());
            log("place structure result " + r);
        }), 60);
        // ---- find the bell; wait for the village to be recorded on its own
        step(null, () -> server(p -> {
            ServerLevel l = p.serverLevel();
            bell = null;
            double bd = Double.MAX_VALUE;
            for (BlockPos q : BlockPos.betweenClosed(center.offset(-64, -6, -64), center.offset(64, 20, 64)))
                if (l.getBlockState(q).getBlock() instanceof BellBlock && q.distSqr(center) < bd) { bd = q.distSqr(center); bell = q.immutable(); }
            log("bell " + (bell == null ? "none" : bell.toShortString()));
            if (bell != null) p.connection.teleport(bell.getX() + .5, bell.getY() + 24, bell.getZ() + .5, 0F, 70F);
        }), 260);
        step(null, () -> server(p -> {
            ServerLevel l = p.serverLevel();
            if (bell == null) return;
            RebuildData d = VillageRebuild.data(l);
            village = d == null ? null : d.at(bell);
            boolean auto = village != null;
            if (village == null) village = VillageRebuild.snapshotNow(l, bell);
            if (village == null) { log("snapshot FAILED"); return; }
            log("snapshot " + (auto ? "auto" : "forced") + " village #" + village.id + " kind=" + village.kind + " blocks=" + village.size() + " states=" + village.paletteSize()
                    + " box=" + village.box.getXSpan() + "x" + village.box.getYSpan() + "x" + village.box.getZSpan() + " pending=" + VillageRebuild.checkAll(l, village));
            int humans = l.getEntitiesOfClass(HumanNpcEntity.class, new AABB(bell).inflate(64)).size(), villagers = l.getEntitiesOfClass(Villager.class, new AABB(bell).inflate(64)).size();
            log("people humans=" + humans + " villagers=" + villagers);
            // a house to blow up: the bed nearest the bell, at least 10 out
            blast = null;
            double bd = Double.MAX_VALUE;
            for (int i = 0; i < village.size(); i++) {
                BlockState s = village.state(i);
                if (s.getBlock() instanceof BedBlock && s.getValue(BedBlock.PART) == BedPart.HEAD) {
                    BlockPos q = village.pos(i);
                    double dd = q.distSqr(bell);
                    if (dd > 100 && dd < bd) { bd = dd; blast = q; }
                }
            }
            if (blast == null) blast = bell.offset(8, 0, 0);
            log("house at " + blast.toShortString() + " (" + String.format("%.1f", Math.sqrt(bd)) + " from the bell)");
            look(p, mc, blast, 15, 9);
        }), 30);
        step("before", () -> {}, 2);
        // ---- boom
        step(null, () -> server(p -> {
            ServerLevel l = p.serverLevel();
            if (village == null) return;
            for (Mob m : l.getEntitiesOfClass(Mob.class, new AABB(blast).inflate(14))) m.setInvulnerable(true);
            l.explode(null, blast.getX() + .5, blast.getY() + .5, blast.getZ() + .5, 5F, Level.ExplosionInteraction.TNT);
            l.explode(null, blast.getX() + .5, blast.getY() + 3.5, blast.getZ() + .5, 4F, Level.ExplosionInteraction.TNT);
            blastTime = l.getGameTime();
        }), 3);
        step(null, () -> server(p -> {
            ServerLevel l = p.serverLevel();
            if (village == null) return;
            for (Mob m : l.getEntitiesOfClass(Mob.class, new AABB(blast).inflate(20))) m.setInvulnerable(false);
            l.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new AABB(blast).inflate(16)).forEach(e -> e.discard());
            int pending = VillageRebuild.checkAll(l, village);
            BLAST.clear();
            for (int i = 0; i < village.size(); i++) if (village.isPending(i)) BLAST.add(i);
            // a player puts a different block into one of the gaps: it must be left alone
            goldIdx = -1;
            for (int i : BLAST) {
                BlockState s = village.state(i);
                if (s.isSolidRender(l, village.pos(i)) && !(s.getBlock() instanceof FarmBlock)) { goldIdx = i; break; }
            }
            if (goldIdx >= 0) { gold = village.pos(goldIdx); l.setBlock(gold, Blocks.GOLD_BLOCK.defaultBlockState(), 3); }
            log("blast missing=" + BLAST.size() + " (village pending " + pending + " of " + village.size() + ")" + " player block at "
                    + (gold == null ? "none" : gold.toShortString() + " instead of " + village.state(goldIdx).getBlock().getName().getString()));
        }), 6);
        step("after_blast", () -> {}, 2);
        // ---- watch them put it back
        int every = 100, rounds = 54;
        for (int r = 1; r <= rounds; r++) {
            int round = r;
            step(round == 4 ? "mid1" : round == 10 ? "mid2" : null, () -> server(p -> progress(p.serverLevel(), round * every / 20)), every);
        }
        step(null, () -> server(p -> finalCheck(p.serverLevel())), 4);
        step("after", () -> {}, 2);
        // a closer look from the other side
        step(null, () -> server(p -> { if (blast != null) look(p, mc, blast, -12, 6); }), 30);
        step("after_close", () -> {}, 2);
    }

    /** Puts the player (the camera) d blocks south-east of the target, h up, looking at it. */
    private static void look(ServerPlayer p, Minecraft mc, BlockPos t, int d, int h) {
        double x = t.getX() + .5 + d * 0.8, y = t.getY() + h, z = t.getZ() + .5 + d * 0.6;
        double dx = t.getX() + .5 - x, dy = t.getY() + 1.5 - (y + 1.62), dz = t.getZ() + .5 - z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz)), pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        p.getAbilities().flying = true; p.onUpdateAbilities();
        p.connection.teleport(x, y, z, yaw, pitch);
        mc.execute(() -> { mc.options.hideGui = true; mc.options.setCameraType(CameraType.FIRST_PERSON); if (mc.player != null) { mc.player.setYRot(yaw); mc.player.setXRot(pitch); } });
    }

    private static boolean sameExact(BlockState want, BlockState cur) {
        if (want.getBlock() instanceof CropBlock) return cur.is(want.getBlock());           // (replanted: age starts over)
        if (want.hasProperty(BlockStateProperties.OPEN)) { want = want.setValue(BlockStateProperties.OPEN, false); if (cur.hasProperty(BlockStateProperties.OPEN)) cur = cur.setValue(BlockStateProperties.OPEN, false); }
        return want == cur;
    }

    private static void progress(ServerLevel l, int sec) {
        if (village == null || BLAST.isEmpty()) return;
        int same = 0, exact = 0, n = 0;
        for (int i : BLAST) {
            if (i == goldIdx) continue;
            n++;
            BlockState cur = l.getBlockState(village.pos(i)), want = village.state(i);
            if (cur.is(want.getBlock())) { same++; if (sameExact(want, cur)) exact++; }
        }
        int[] k = VillageRebuild.workerKinds(village);
        log("t=" + sec + "s restored " + same + "/" + n + " (" + pct(same, n) + ") exact " + exact + " workers humans=" + k[0] + " villagers=" + k[1] + " pending=" + village.pendingCount());
        if (same == n && firstFull < 0) { firstFull = sec; log("COMPLETE after " + sec + "s"); }
    }

    private static void finalCheck(ServerLevel l) {
        if (village == null) { log("final: no village"); return; }
        int same = 0, exact = 0, exactRaw = 0, n = 0;
        List<String> bad = new ArrayList<>();
        for (int i : BLAST) {
            if (i == goldIdx) continue;
            n++;
            BlockPos q = village.pos(i);
            BlockState cur = l.getBlockState(q), want = village.state(i);
            if (cur.is(want.getBlock())) { same++; if (sameExact(want, cur)) exact++; else if (bad.size() < 8) bad.add(q.toShortString() + " want " + want + " got " + cur); if (want == cur) exactRaw++; }
            else if (bad.size() < 8) bad.add(q.toShortString() + " want " + want + " got " + cur);
        }
        log("final restored " + same + "/" + n + " (" + pct(same, n) + "), exact state " + exact + "/" + n + " (" + pct(exact, n) + ", raw incl. door open/crop age " + exactRaw + "), first complete at "
                + (firstFull < 0 ? "never" : firstFull + "s") + ", placed by workers " + village.restored);
        for (String b : bad) log("  differs: " + b);
        if (gold != null) log("player block " + (l.getBlockState(gold).is(Blocks.GOLD_BLOCK) ? "LEFT ALONE (still gold)" : "REPLACED: " + l.getBlockState(gold)));
        log("whole village pending now " + VillageRebuild.checkAll(l, village) + " of " + village.size());
        // floating check: every restored solid block has something under it or beside it
        int floating = 0;
        for (int i : BLAST) {
            BlockPos q = village.pos(i);
            if (l.getBlockState(q).isAir()) continue;
            boolean held = false;
            for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) if (d != net.minecraft.core.Direction.UP && !l.getBlockState(q.relative(d)).isAir()) { held = true; break; }
            if (!held) floating++;
        }
        log("floating blocks " + floating);
    }
}

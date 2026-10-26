package com.xenowarriors.ultima;

import com.xenowarriors.character.CharacterType;
import com.xenowarriors.character.ModCapabilities;
import com.xenowarriors.karma.Karma;
import com.xenowarriors.karma.KarmaSensing;
import com.xenowarriors.network.ModNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

import java.util.*;

/**
 * Ultima's Data Sense (his Karma Sensing key): a scan, not a feeling. It reads living things and, unlike Karma
 * Sensing, ore in the rock - iron, coal, copper and Data Ore always; gold, redstone and lapis from scan level 25;
 * diamonds and emeralds from level 60. It can't read C-souls or Corrupted Ore: if either is in range the scan crashes
 * ("ERROR - OFFLINE") and has to reboot (a long cooldown).
 */
public final class DataSense {
    public static final float COST = 15F;
    public static final int COOLDOWN = 20 * 15, CRASH_COOLDOWN = 20 * 60, DURATION = 20 * 12;
    private static final Map<UUID, Long> READY = new HashMap<>();
    public static final int KIND_COMMON = 0, KIND_GOLD = 1, KIND_RED = 2, KIND_DIAMOND = 3, KIND_DATA = 4;

    private DataSense() {}

    public static boolean is(ServerPlayer p) { return ModCapabilities.getType(p) == CharacterType.ULTIMA; }

    /** Scan level 1-100 (grows with use, like Karma Sensing practice). */
    public static int level(ServerPlayer p) { return Math.min(100, 1 + KarmaSensing.uses(p)); }

    private static int kind(Block b, int lvl) {
        if (b == com.xenowarriors.sky.ModBlocks.DATA_ORE.get()) return KIND_DATA;
        if (b == Blocks.IRON_ORE || b == Blocks.DEEPSLATE_IRON_ORE || b == Blocks.COAL_ORE || b == Blocks.DEEPSLATE_COAL_ORE
                || b == Blocks.COPPER_ORE || b == Blocks.DEEPSLATE_COPPER_ORE) return KIND_COMMON;
        if (lvl >= 25 && (b == Blocks.GOLD_ORE || b == Blocks.DEEPSLATE_GOLD_ORE || b == Blocks.NETHER_GOLD_ORE)) return KIND_GOLD;
        if (lvl >= 25 && (b == Blocks.REDSTONE_ORE || b == Blocks.DEEPSLATE_REDSTONE_ORE || b == Blocks.LAPIS_ORE || b == Blocks.DEEPSLATE_LAPIS_ORE)) return KIND_RED;
        if (lvl >= 60 && (b == Blocks.DIAMOND_ORE || b == Blocks.DEEPSLATE_DIAMOND_ORE || b == Blocks.EMERALD_ORE || b == Blocks.DEEPSLATE_EMERALD_ORE)) return KIND_DIAMOND;
        return -1;
    }

    public static void use(ServerPlayer p) {
        long now = p.level().getGameTime();
        long ready = READY.getOrDefault(p.getUUID(), 0L);
        if (now < ready) {
            p.displayClientMessage(Component.literal("Data Sense rebooting... " + (ready - now + 19) / 20 + "s").withStyle(ChatFormatting.DARK_GREEN), true);
            return;
        }
        if (!Karma.trySpend(p, COST)) return;
        p.getCapability(ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> d.senseUses++);
        int lvl = level(p);
        double r = 32 + lvl * 0.6;
        ServerLevel s = p.serverLevel();
        // C-souls can't be read: crash
        List<LivingEntity> found = s.getEntitiesOfClass(LivingEntity.class, new AABB(p.blockPosition()).inflate(r),
                e -> e != p && e.isAlive() && !(e instanceof ArmorStand) && e.distanceToSqr(p) <= r * r);
        boolean soul = found.stream().anyMatch(KarmaSensing::corrupted);
        List<Long> ores = new ArrayList<>(); List<Integer> kinds = new ArrayList<>();
        boolean corruptOre = scanOres(p, Math.min(r, 48), lvl, ores, kinds);
        if (soul || corruptOre) {
            READY.put(p.getUUID(), now + CRASH_COOLDOWN);
            ModNetwork.sendToPlayer(new com.xenowarriors.network.DataSensedPacket(new int[0], new long[0], new int[0], 60, true), p);
            p.displayClientMessage(Component.literal("ERROR - DATA SENSE OFFLINE").withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
            p.sendSystemMessage(Component.literal("[Data Sense] Unreadable signal" + (soul ? " (corrupted soul)" : " (corrupted ore)") + " - rebooting in 60s").withStyle(ChatFormatting.DARK_RED));
            s.playSound(null, p.blockPosition(), SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 1F, 0.5F);
            s.playSound(null, p.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1F, 1.6F);
            return;
        }
        READY.put(p.getUUID(), now + COOLDOWN);
        int[] ids = found.stream().mapToInt(LivingEntity::getId).toArray();
        ModNetwork.sendToPlayer(new com.xenowarriors.network.DataSensedPacket(ids, ores.stream().mapToLong(Long::longValue).toArray(),
                kinds.stream().mapToInt(Integer::intValue).toArray(), DURATION, false), p);
        s.playSound(null, p.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.8F, 2F);
        p.displayClientMessage(Component.literal("Data Sense (lv " + lvl + "): " + ids.length + " life signs, " + ores.size() + " ore").withStyle(ChatFormatting.GREEN), true);
    }

    /** Collects readable ore; returns true if Corrupted Ore is in range (which crashes the scan). */
    private static boolean scanOres(ServerPlayer p, double radius, int lvl, List<Long> out, List<Integer> kinds) {
        ServerLevel level = p.serverLevel();
        BlockPos c = p.blockPosition();
        int r = (int) radius, cr = (r >> 4) + 1, ccx = c.getX() >> 4, ccz = c.getZ() >> 4;
        var dark = com.xenowarriors.sky.ModBlocks.CORRUPTED_ORE.get();
        for (int dx = -cr; dx <= cr; dx++) for (int dz = -cr; dz <= cr; dz++) {
            var chunk = level.getChunkSource().getChunkNow(ccx + dx, ccz + dz);
            if (chunk == null) continue;
            var sections = chunk.getSections();
            for (int i = 0; i < sections.length; i++) {
                var sec = sections[i];
                if (sec == null || sec.hasOnlyAir()) continue;
                int baseY = level.getSectionYFromSectionIndex(i) << 4;
                if (Math.abs(baseY + 8 - c.getY()) > r + 8) continue;
                if (!sec.maybeHas(st -> st.is(dark) || kind(st.getBlock(), lvl) >= 0)) continue;
                for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                    var st = sec.getBlockState(x, y, z);
                    BlockPos pos = new BlockPos(((ccx + dx) << 4) + x, baseY + y, ((ccz + dz) << 4) + z);
                    if (pos.distSqr(c) > (double) r * r) continue;
                    if (st.is(dark)) return true;
                    int k = kind(st.getBlock(), lvl);
                    if (k >= 0 && out.size() < 160) { out.add(pos.asLong()); kinds.add(k); }
                }
            }
        }
        return false;
    }
}

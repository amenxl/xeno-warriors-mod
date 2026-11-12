package com.xenowarriors.vehicle;

import com.xenowarriors.XenoWarriors;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

/**
 * Headlights: when it's dark (night, or no sky - caves, tunnels) and someone's driving, a vehicle's headlights come on.
 * The road ahead is really lit: invisible light blocks a few blocks in front, moved along with it and taken away
 * behind it (only ever in empty air, and only where building is allowed). The beams themselves are drawn by
 * client.vehicle.HeadlightBeams.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Headlights {
    private Headlights() {}

    /** vehicle -> the light blocks it has put down */
    private static final Map<UUID, List<BlockPos>> LIT = new HashMap<>();
    private static final Map<UUID, ServerLevel> WHERE = new HashMap<>();
    /** (Tests) light blocks placed. */
    public static int placed;

    /** Dark enough for headlights here: little sky light (night, or under cover). Our own light blocks don't count. */
    public static boolean dark(Level l, BlockPos p) {
        long t = l.getDayTime() % 24000;
        boolean night = l.dimensionType().hasSkyLight() && !l.dimensionType().hasFixedTime() && t > 12600 && t < 23400;
        return night || l.getBrightness(LightLayer.SKY, p) < 8;                  // (night, or under cover: caves, tunnels)
    }

    /** On: dark, and someone driving. */
    public static boolean on(VehicleEntity v) {
        return v.getControllingPassenger() != null && dark(v.level(), v.blockPosition().above());
    }

    /** Where the lights shine from: the front of the vehicle, a little up. */
    public static Vec3 front(VehicleEntity v, float pt) {
        float yaw = net.minecraft.util.Mth.rotLerp(pt, v.yRotO, v.getYRot()) * ((float) Math.PI / 180F);
        Vec3 fwd = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
        Vec3 pos = v.getPosition(pt);
        return pos.add(fwd.scale(v.getBbWidth() * 0.55 + 0.4)).add(0, Math.min(1.1, v.getBbHeight() * 0.35 + 0.2), 0);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.LevelTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.level instanceof ServerLevel l) || l.getGameTime() % 5 != 0) return;
        Set<UUID> seen = new HashSet<>();
        for (var ent : l.getAllEntities()) {
            if (!(ent instanceof VehicleEntity v)) continue;
            seen.add(v.getUUID());
            List<BlockPos> want = new ArrayList<>();
            if (on(v) && com.xenowarriors.modes.ModeRules.canGrief(l)) {
                float yaw = v.getYRot() * ((float) Math.PI / 180F);
                Vec3 fwd = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
                Vec3 from = front(v, 1F);
                for (double d : new double[]{2.5, 6.5, 11}) {
                    BlockPos p = BlockPos.containing(from.add(fwd.scale(d)));
                    for (int dy = 0; dy >= -2; dy--) {                            // (just above the road)
                        BlockPos q = p.offset(0, dy, 0);
                        if (l.getBlockState(q).isAir() || l.getBlockState(q).is(Blocks.LIGHT)) {
                            if (!l.getBlockState(q.below()).isAir() || dy == -2) { want.add(q); break; }
                        } else break;
                    }
                }
            }
            update(l, v.getUUID(), want);
        }
        for (UUID id : new ArrayList<>(LIT.keySet())) if (WHERE.get(id) == l && !seen.contains(id)) update(l, id, List.of());   // (gone)
    }

    private static void update(ServerLevel l, UUID id, List<BlockPos> want) {
        List<BlockPos> had = LIT.getOrDefault(id, List.of());
        for (BlockPos p : had) if (!want.contains(p) && l.getBlockState(p).is(Blocks.LIGHT)) l.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
        List<BlockPos> now = new ArrayList<>();
        for (BlockPos p : want) {
            if (l.getBlockState(p).isAir()) { l.setBlock(p, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 13), 3); placed++; }
            if (l.getBlockState(p).is(Blocks.LIGHT)) now.add(p);
        }
        if (now.isEmpty()) { LIT.remove(id); WHERE.remove(id); }
        else { LIT.put(id, now); WHERE.put(id, l); }
    }

    @SubscribeEvent
    public static void onLeave(EntityLeaveLevelEvent e) {
        if (e.getEntity() instanceof VehicleEntity v && e.getLevel() instanceof ServerLevel l) update(l, v.getUUID(), List.of());
    }

    @SubscribeEvent
    public static void onStop(ServerStoppingEvent e) {
        for (var en : new ArrayList<>(LIT.entrySet())) { ServerLevel l = WHERE.get(en.getKey()); if (l != null) update(l, en.getKey(), List.of()); }
    }

    /** (Tests) lights a vehicle has down right now. */
    public static int litForTest(VehicleEntity v) { return LIT.getOrDefault(v.getUUID(), List.of()).size(); }
}

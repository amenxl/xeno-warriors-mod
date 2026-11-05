package com.xenowarriors.recarnet;

import com.xenowarriors.XenoWarriors;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * CI only (XW_SELFTEST=1): unlock the seasons, generate Aurora City in Recarnet, count what spawned, save, stop.
 * Lets the build check the dimension actually loads and the city generates, and gives us the world to render.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SelfTest {
    private SelfTest() {}

    @SubscribeEvent
    public static void onStarted(ServerStartedEvent event) {
        if (!"1".equals(System.getenv("XW_SELFTEST"))) return;
        MinecraftServer server = event.getServer();
        try {
            com.xenowarriors.character.Seasons.set(server, 3);
            if (System.getenv("XW_PACKTEST") != null) {                  // (the pack test: which mods this server really loaded)
                java.util.List<String> mods = new java.util.ArrayList<>();
                net.minecraftforge.fml.ModList.get().forEachModContainer((id, c) -> mods.add(id + " " + c.getModInfo().getVersion()));
                mods.sort(String::compareTo);
                XenoWarriors.LOGGER.info("XW-SELFTEST mods loaded ({}): {}", mods.size(), String.join(", ", mods));
            }
            ServerLevel rec = server.getLevel(Recarnet.LEVEL);
            if (rec == null) { XenoWarriors.LOGGER.error("XW-SELFTEST FAIL: no Recarnet level"); server.halt(false); return; }
            int r = Integer.parseInt(System.getenv().getOrDefault("XW_SELFTEST_R", "15"));
            long t0 = System.currentTimeMillis();
            int n = 0;
            for (int cx = -r; cx <= r; cx++) for (int cz = -r; cz <= r; cz++) { rec.getChunk(cx, cz); n++; }
            rec.getChunk(0, (Recarnet.CITY_R + 36) >> 4);
            for (int cx = (AuroraLandmarks.YURI_X - 18) >> 4; cx <= (AuroraLandmarks.YURI_X + 18) >> 4; cx++)          // Yuri's HQ
                for (int cz = (AuroraLandmarks.HATCH_Z - 4) >> 4; cz <= (AuroraLandmarks.YURI_Z + 16) >> 4; cz++) { rec.getChunk(cx, cz); n++; }
            int vr = Integer.parseInt(System.getenv().getOrDefault("XW_SELFTEST_VR", "11"));
            int vcx = Recarnet.VILLAGE_X >> 4, vcz = Recarnet.VILLAGE_Z >> 4;
            for (int cx = vcx - vr; cx <= vcx + vr; cx++) for (int cz = vcz - vr; cz <= vcz + vr; cz++) { rec.getChunk(cx, cz); n++; }
            XenoWarriors.LOGGER.info("XW-SELFTEST generated {} chunks in {} ms", n, System.currentTimeMillis() - t0);
            XenoWarriors.LOGGER.info("XW-SELFTEST biome at 0,0: {}", rec.getBiome(new net.minecraft.core.BlockPos(0, 80, 0)).unwrapKey().map(Object::toString).orElse("?"));
            XenoWarriors.LOGGER.info("XW-SELFTEST block at plaza: {} / street {}", rec.getBlockState(new net.minecraft.core.BlockPos(27, Recarnet.BASE_Y, 27)),
                    rec.getBlockState(new net.minecraft.core.BlockPos(3, Recarnet.BASE_Y, 20)));
            int gov = 0, order = 0;
            for (var e : rec.getAllEntities()) { if (e instanceof com.xenowarriors.gov.GovAgentEntity) gov++; if (e instanceof com.xenowarriors.s3.OrderAssassinEntity) order++; }
            XenoWarriors.LOGGER.info("XW-SELFTEST gov agents {} order assassins {}", gov, order);
            try { modePodCheck(server); } catch (Throwable t) { XenoWarriors.LOGGER.warn("XW-SELFTEST pod check failed", t); }
            try { XenoWarriors.LOGGER.info("XW-SELFTEST bot limbo restart: {}", com.xenowarriors.bot.BotLimbo.selfTest(server)); } catch (Throwable t) { XenoWarriors.LOGGER.warn("XW-SELFTEST bot limbo check failed", t); }
            server.saveAllChunks(false, true, true);
            XenoWarriors.LOGGER.info("XW-SELFTEST OK");
        } catch (Throwable t) {
            XenoWarriors.LOGGER.error("XW-SELFTEST FAIL", t);
        }
        if (com.xenowarriors.debug.MpSelfTest.wanted()) { com.xenowarriors.debug.MpSelfTest.start(server); return; }   // (two players on this server first; it stops when done)
        server.halt(false);
    }

    /** A self-service healing pod in the Tower dimension (where Journeys run) refuses; Journey's own fixed rest still starts. */
    private static void modePodCheck(MinecraftServer server) {
        ServerLevel tl = server.getLevel(com.xenowarriors.tower.Tower.LEVEL);
        if (tl == null) { XenoWarriors.LOGGER.info("XW-SELFTEST pod check: no Tower level"); return; }
        net.minecraft.core.BlockPos at = new net.minecraft.core.BlockPos(-4000, 120, -4000);
        tl.getChunk(at);
        var pod = com.xenowarriors.sky.ModBlocks.HEALING_POD.get().defaultBlockState();
        tl.setBlock(at.below(), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 2);
        tl.setBlock(at, pod, 2);
        tl.setBlock(at.above(), pod.setValue(com.xenowarriors.gov.HealingPodBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER), 2);
        var z = net.minecraft.world.entity.EntityType.ZOMBIE.create(tl);
        if (z == null) return;
        z.moveTo(at.getX() + .5, at.getY(), at.getZ() + .5, 0, 0);
        tl.addFreshEntity(z);
        String self = com.xenowarriors.gov.HealingPods.start(tl, at, z, -1, null);
        String fixed = com.xenowarriors.gov.HealingPods.start(tl, at, z, 40, null);
        XenoWarriors.LOGGER.info("XW-SELFTEST pod in tower: self-service -> {} / journey rest -> {} (inPod={})", self, fixed == null ? "ok" : fixed, com.xenowarriors.gov.HealingPods.inPod(z));
        z.discard();
        for (var p : new net.minecraft.core.BlockPos[]{at.above(), at, at.below()}) tl.setBlock(p, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
    }
}

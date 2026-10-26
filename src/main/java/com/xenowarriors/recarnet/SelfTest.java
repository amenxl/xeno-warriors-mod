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
            ServerLevel rec = server.getLevel(Recarnet.LEVEL);
            if (rec == null) { XenoWarriors.LOGGER.error("XW-SELFTEST FAIL: no Recarnet level"); server.halt(false); return; }
            int r = Integer.parseInt(System.getenv().getOrDefault("XW_SELFTEST_R", "15"));
            long t0 = System.currentTimeMillis();
            int n = 0;
            for (int cx = -r; cx <= r; cx++) for (int cz = -r; cz <= r; cz++) { rec.getChunk(cx, cz); n++; }
            rec.getChunk(0, (Recarnet.CITY_R + 36) >> 4);
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
            server.saveAllChunks(false, true, true);
            XenoWarriors.LOGGER.info("XW-SELFTEST OK");
        } catch (Throwable t) {
            XenoWarriors.LOGGER.error("XW-SELFTEST FAIL", t);
        }
        server.halt(false);
    }
}

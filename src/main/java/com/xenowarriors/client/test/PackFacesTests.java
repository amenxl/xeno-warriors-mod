package com.xenowarriors.client.test;

import com.xenowarriors.client.UiTest.Step;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Mob;

import java.util.List;

/** (focus "packfaces") Every one of our people and foes, one at a time, standing still in front of you: a picture of each face. */
final class PackFacesTests {
    private PackFacesTests() {}

    static void build(Minecraft mc, List<Step> l) {
        List<String> ids = new java.util.ArrayList<>();
        for (var e : net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getEntries())
            if (e.getKey().location().getNamespace().equals(com.xenowarriors.XenoWarriors.MOD_ID)) ids.add(e.getKey().location().getPath());
        java.util.Collections.sort(ids);
        int[] cur = {-1};
        l.add(new Step(null, () -> { mc.setScreen(null); mc.options.hideGui = true; mc.options.setCameraType(CameraType.FIRST_PERSON); }, 5));
        for (String id : ids) {
            l.add(new Step(null, () -> PackTests.server(p -> {
                PackTests.arena(p); PackTests.clear(p);
                p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
                var type = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getValue(com.xenowarriors.XenoWarriors.id(id));
                if (type == null) return;
                var e = type.create(p.serverLevel());
                if (!(e instanceof Mob m) || e instanceof com.xenowarriors.bot.PartyBotEntity) { if (e != null) e.discard(); cur[0] = -1; return; }
                m.moveTo(p.getX(), p.getY(), p.getZ() + 2.6 + Math.max(0, m.getBbWidth() - 0.6), 180F, 0F);
                m.setYHeadRot(180F); m.yBodyRot = 180F;
                m.setNoAi(true); m.addTag("xw_pack"); m.setPersistenceRequired();
                p.serverLevel().addFreshEntity(m);
                cur[0] = m.getId();
                p.connection.teleport(p.getX(), p.getY(), p.getZ(), 0F, m.getBbHeight() > 2.5F ? -12F : 4F);
            }), 25));
            l.add(new Step("faces_" + id, () -> {}, 2));
        }
        l.add(new Step(null, () -> { mc.options.hideGui = false; PackTests.server(PackTests::clear); }, 3));
    }
}

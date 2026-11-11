package com.xenowarriors.client.test;

import com.xenowarriors.boss.DragonBombEntity;
import com.xenowarriors.boss.XenoDragonEntity;
import com.xenowarriors.boss.XenoDragons;
import com.xenowarriors.client.UiTest.Step;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.level.GameType;

import java.util.List;

/**
 * Pack test: the Xeno Dragon in the user's real pack (DragonMineZ, Epic Fight, GeckoLib, Oculus...): an Ender Dragon
 * /killed on the test floor turns into one, it draws, goes through its forms to Ultra Instinct, throws a Dragon Bomb,
 * and is cleaned up. "XW-TEST pack xenodragon ... PASS/FAIL".
 */
final class PackXenoDragonTests {
    private PackXenoDragonTests() {}

    private static XenoDragonEntity xeno(net.minecraft.server.level.ServerPlayer p) {
        for (var x : p.serverLevel().getEntitiesOfClass(XenoDragonEntity.class, p.getBoundingBox().inflate(120))) if (x.isAlive()) return x;
        return null;
    }

    static void build(Minecraft mc, List<Step> l) {
        l.add(new Step(null, () -> PackTests.server(p -> {
            PackTests.arena(p); PackTests.clear(p);
            for (var x : p.serverLevel().getEntitiesOfClass(XenoDragonEntity.class, p.getBoundingBox().inflate(200))) x.discard();
            if (!XenoDragons.enabled()) { PackTests.log("xenodragon: dragonminez not loaded - skipped"); return; }
            EnderDragon d = EntityType.ENDER_DRAGON.create(p.serverLevel());
            d.moveTo(PackTests.HOME[0], PackTests.HOME[1] + 12, PackTests.HOME[2] + 14, 0, 0);
            p.serverLevel().addFreshEntity(d);
            d.kill();
        }), 10));
        l.add(new Step(null, () -> PackTests.server(p -> {
            if (!XenoDragons.enabled()) return;
            var x = xeno(p);
            PackTests.check("xenodragon from a killed ender dragon", x != null, x == null ? "none" : "at " + x.blockPosition().toShortString() + " lands " + x.home());
            if (x != null) { x.skipEmergeForTest(); x.addTag("xw_pack"); }
            p.setGameMode(GameType.SURVIVAL);
        }), 40));
        l.add(new Step(null, () -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK), 5));
        l.add(new Step("pack_xenodragon_base", () -> {}, 30));
        l.add(new Step(null, () -> PackTests.server(p -> { var x = xeno(p); if (x != null) x.setFormForTest(XenoDragonEntity.SSG); }), 30));
        l.add(new Step("pack_xenodragon_ssg", () -> {}, 3));
        l.add(new Step(null, () -> PackTests.server(p -> { var x = xeno(p); if (x != null) { x.setFormForTest(XenoDragonEntity.UI); x.setTarget(p); x.bombForTest(p); } }), 60));
        l.add(new Step("pack_xenodragon_bomb", () -> PackTests.server(p -> { var x = xeno(p);
            PackTests.check("xenodragon ultra instinct + dragon bomb", x != null && x.form() == XenoDragonEntity.UI && x.act() == XenoDragonEntity.ACT_BOMB,
                    x == null ? "none" : "form " + x.form() + " act " + x.act() + " speed " + x.speed()); }), 3));
        l.add(new Step(null, () -> {}, 90));
        l.add(new Step(null, () -> PackTests.server(p -> {
            var x = xeno(p);
            if (XenoDragons.enabled()) PackTests.check("xenodragon dragon bomb exploded", DragonBombEntity.explosions >= 1 || (x != null && x.bombsThrown >= 1), "explosions " + DragonBombEntity.explosions);
            for (var e : p.serverLevel().getEntitiesOfClass(XenoDragonEntity.class, p.getBoundingBox().inflate(200))) e.discard();
            for (var e : p.serverLevel().getEntitiesOfClass(DragonBombEntity.class, p.getBoundingBox().inflate(200))) e.discard();
            PackTests.clear(p);
            p.setGameMode(GameType.CREATIVE);
            p.setHealth(p.getMaxHealth());
        }), 10));
        l.add(new Step(null, () -> mc.options.setCameraType(CameraType.FIRST_PERSON), 5));
    }
}

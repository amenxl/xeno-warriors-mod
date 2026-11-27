package com.xenowarriors.client.test;

import com.xenowarriors.bot.PartyBotEntity;
import com.xenowarriors.bot.PartyBots;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.client.UiTest.Step;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

/**
 * (focus "packefwalkgif") Frames of a bot walking on Epic Fight's body with a weapon out - Sam with his bat, then with a
 * greatsword - seen from the side, the camera following along (made into a GIF).
 */
final class PackEfWalkGif {
    private PackEfWalkGif() {}

    private static PartyBotEntity bot;

    /** The camera keeps pace on this side too (the server's moves only arrive every few ticks: it would jerk along). */
    private static void followHere(Minecraft mc, int camId) {
        if (mc.level == null || bot == null) return;
        var c = mc.level.getEntity(camId);
        var b = mc.level.getEntity(bot.getId());
        if (c == null || b == null) return;
        double x = b.getX() + 1.2, y = PackTests.HOME[1] + 0.1, z = b.getZ() + 4.6;
        c.xo = c.getX(); c.yo = c.getY(); c.zo = c.getZ();
        c.setPos(x, y, z);
        c.setYRot(165F); c.yRotO = 165F; c.setXRot(8F); c.xRotO = 8F;
        if (c instanceof net.minecraft.world.entity.LivingEntity le) { le.setYHeadRot(165F); le.yHeadRotO = 165F; }
    }

    static void build(Minecraft mc, List<Step> l) {
        int[] cam = {-1};
        l.add(new Step(null, () -> { mc.setScreen(null); com.xenowarriors.client.compat.epicfight.EfBotBodies.off = false;
            com.xenowarriors.client.UiTest.character(CharacterType.MIKIKO); }, 30));
        l.add(new Step(null, () -> PackTests.server(p -> {
            PackTests.arena(p); PackTests.clear(p);
            p.serverLevel().setDayTime(6000);
            var s = net.minecraft.world.entity.EntityType.ARMOR_STAND.create(p.serverLevel());
            if (s != null) {
                s.setInvisible(true); s.setNoGravity(true); s.setInvulnerable(true);
                s.moveTo(PackTests.HOME[0] - 12, PackTests.HOME[1], PackTests.HOME[2] + 10, 180F, 8F);
                p.serverLevel().addFreshEntity(s);
                cam[0] = s.getId();
            }
        }), 10));
        String[][] legs = {{"xenowarriors:sam_bat", "bat"}, {"epicfight:iron_greatsword", "greatsword"}};
        int frame = 0;
        for (String[] w : legs) {
            l.add(new Step(null, () -> PackTests.server(p -> {
                if (bot == null || bot.isRemoved()) {
                    for (PartyBotEntity b : PartyBots.bots(p)) b.discard();
                    bot = PartyBots.spawn(p, CharacterType.SAM, null);
                }
                if (bot == null) return;
                bot.setNoAi(false);
                bot.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation(w[0]))));
                com.xenowarriors.bot.BotGear.busyNow(bot);
                bot.moveTo(PackTests.HOME[0] - 13, PackTests.HOME[1], PackTests.HOME[2] + 6, -90F, 0F);
                bot.setYHeadRot(-90F); bot.yBodyRot = -90F;
                bot.getNavigation().stop();
                p.connection.teleport(PackTests.HOME[0], PackTests.HOME[1], PackTests.HOME[2] - 8, 0F, 0F);    // (out of shot, behind the camera's view)
            }), 25));
            l.add(new Step(null, () -> {
                mc.options.hideGui = true;
                mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
                var c = mc.level.getEntity(cam[0]);
                if (c != null) mc.setCameraEntity(c);
                PackTests.server(p -> { if (bot != null) bot.getNavigation().moveTo(PackTests.HOME[0] + 14, PackTests.HOME[1], PackTests.HOME[2] + 6, 0.9); });
            }, 6));
            for (int f = 0; f < 70; f++) {
                int k = frame++;
                l.add(new Step(String.format("efwalkgif_%03d", k), () -> { followHere(mc, cam[0]); PackTests.server(p -> {
                    if (bot == null) return;
                    com.xenowarriors.bot.BotGear.busyNow(bot);                  // (weapon stays out)
                    var c = p.serverLevel().getEntity(cam[0]);
                    if (c != null) c.moveTo(bot.getX() + 1.2, PackTests.HOME[1] + 0.1, bot.getZ() + 4.6, 165F, 8F);   // (beside it, a little ahead)
                    if (k % 10 == 0) PackTests.log("efwalkgif " + k + " " + w[1] + " at " + PackTests.f(bot.getX() - PackTests.HOME[0]) + " held " + bot.getMainHandItem());
                }); }, 2));
            }
        }
        l.add(new Step(null, () -> { mc.setCameraEntity(mc.player); mc.options.hideGui = false; PackTests.server(p -> { PackTests.clear(p); bot = null; }); }, 5));
    }
}

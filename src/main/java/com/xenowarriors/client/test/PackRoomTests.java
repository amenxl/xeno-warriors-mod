package com.xenowarriors.client.test;

import com.xenowarriors.client.UiTest.Step;
import com.xenowarriors.room.RoomTech;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * (focus "packroom") Room Technology I / II / III: each opens a Room of its own size (the glass shell where it should
 * be), and storage in a Room won't open (a chest, a barrel, an ender chest).
 */
final class PackRoomTests {
    private PackRoomTests() {}

    private static boolean tryOpen(ServerPlayer p, BlockPos pos) {
        p.closeContainer();
        var hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        p.gameMode.useItemOn(p, p.serverLevel(), ItemStack.EMPTY, InteractionHand.MAIN_HAND, hit);
        boolean opened = p.containerMenu != p.inventoryMenu;
        p.closeContainer();
        return opened;
    }

    static void build(Minecraft mc, List<Step> l) {
        for (int tier = 1; tier <= 3; tier++) {
            int t = tier;
            l.add(new Step(null, () -> PackTests.server(p -> {
                PackTests.arena(p); PackTests.clear(p);
                p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
                p.getInventory().clearContent();
                var z = EntityType.ZOMBIE.create(p.serverLevel());
                z.moveTo(p.getX() + 3, p.getY(), p.getZ() + 3, 0, 0);
                z.setNoAi(true); z.addTag("xw_pack");
                p.serverLevel().addFreshEntity(z);
                // a chest out here, to check it opens normally outside a Room
                BlockPos c = p.blockPosition().offset(2, 0, 0);
                p.serverLevel().setBlock(c, Blocks.CHEST.defaultBlockState(), 3);
                PackTests.check("room: a chest opens outside a Room (tier " + t + " run)", tryOpen(p, c), "");
                p.serverLevel().setBlock(c, Blocks.AIR.defaultBlockState(), 3);
                boolean opened = RoomTech.use(p, t);
                PackTests.check("room tier " + t + ": opens", opened && RoomTech.is(p.level()), "in " + p.level().dimension().location());
            }), 30));
            l.add(new Step("room_tier_" + t, () -> PackTests.server(p -> {
                if (!RoomTech.is(p.level())) return;
                int[] sz = RoomTech.size(t);
                var lv = p.serverLevel();
                BlockPos me = p.blockPosition();
                // the shell: glass just past the edge of this tier's size, the copied floor just inside it
                BlockPos edge = me.offset(sz[0] + 1, 2, 0), inside = me.offset(sz[0], 2, 0);
                boolean shell = lv.getBlockState(edge).is(Blocks.LIGHT_BLUE_STAINED_GLASS) && !lv.getBlockState(inside).is(Blocks.LIGHT_BLUE_STAINED_GLASS);
                PackTests.check("room tier " + t + ": the Room is " + (sz[0] * 2 + 1) + " wide", shell,
                        "at +" + (sz[0] + 1) + ": " + lv.getBlockState(edge).getBlock() + ", at +" + sz[0] + ": " + lv.getBlockState(inside).getBlock());
                int before = RoomTech.blocked;
                BlockPos a = me.offset(2, 0, 0), b = me.offset(-2, 0, 0), e = me.offset(0, 0, 2);
                lv.setBlock(a, Blocks.CHEST.defaultBlockState(), 3);
                lv.setBlock(b, Blocks.BARREL.defaultBlockState(), 3);
                lv.setBlock(e, Blocks.ENDER_CHEST.defaultBlockState(), 3);
                boolean ca = tryOpen(p, a), cb = tryOpen(p, b), ce = tryOpen(p, e);
                PackTests.check("room tier " + t + ": chest, barrel and ender chest won't open", !ca && !cb && !ce && RoomTech.blocked - before >= 3,
                        "chest " + ca + " barrel " + cb + " ender " + ce + " refused " + (RoomTech.blocked - before));
                RoomTech.use(p, t);                                                   // (leave)
            }), 10));
            l.add(new Step(null, () -> PackTests.server(p -> PackTests.check("room tier " + t + ": back out", !RoomTech.is(p.level()), "")), 10));
        }
    }
}

package com.xenowarriors.client.test;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.client.UiTest.Step;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * (focus "packitems") Every item the mod adds, one at a time, in survival: its tooltip and its inventory model (not
 * the missing purple-black cube), carried a tick, right-clicked in the air (and held/finished/let go if it's used over
 * time: food, potions, guns), used on the floor, swung at a mob, and worn if it's armor. Any exception is a failure;
 * screens an item opens are noted and closed. The player is put back on the arena after each one (a pass or a pod
 * that sends you somewhere).
 */
final class PackItemTests {
    private PackItemTests() {}

    private static final Map<String, List<String>> FAILS = new LinkedHashMap<>();
    private static final List<String> SCREENS = new ArrayList<>(), MISSING_MODEL = new ArrayList<>(), MOVED = new ArrayList<>();
    private static int done;

    private static List<Item> items() {
        List<Item> l = new ArrayList<>();
        ForgeRegistries.ITEMS.getEntries().stream()
                .filter(e -> e.getKey().location().getNamespace().equals(XenoWarriors.MOD_ID))
                .sorted((a, b) -> a.getKey().location().compareTo(b.getKey().location()))
                .forEach(e -> l.add(e.getValue()));
        return l;
    }

    private static String id(Item it) { ResourceLocation r = ForgeRegistries.ITEMS.getKey(it); return r == null ? "?" : r.getPath(); }

    private static void fail(Item it, String what, Throwable t) {
        FAILS.computeIfAbsent(id(it), k -> new ArrayList<>()).add(what + ": " + t);
        XenoWarriors.LOGGER.error("XW-TEST pack items " + id(it) + " " + what, t);
    }

    private interface Act { void run() throws Throwable; }
    private static void tr(Item it, String what, Act a) { try { a.run(); } catch (Throwable t) { fail(it, what, t); } }

    private static void reset(ServerPlayer p) {
        var over = p.server.overworld();
        if (p.level() != over || p.position().distanceTo(new Vec3(PackTests.HOME[0], PackTests.HOME[1], PackTests.HOME[2])) > 30) {
            MOVED.add(id(p.getMainHandItem().getItem()) + "@" + p.level().dimension().location());
            p.teleportTo(over, PackTests.HOME[0], PackTests.HOME[1], PackTests.HOME[2], 0F, 0F);
        }
        if (p.isDeadOrDying()) p.server.getPlayerList().respawn(p, false);
        p.stopUsingItem();
        p.getInventory().clearContent();
        p.setHealth(p.getMaxHealth());
        p.getFoodData().setFoodLevel(10);
        p.fallDistance = 0;
        p.setGameMode(GameType.SURVIVAL);
    }

    private static void server(ServerPlayer p, Item it) {
        Level lv = p.level();
        reset(p);
        ItemStack s = new ItemStack(it, Math.min(4, it.getMaxStackSize()));
        p.setItemInHand(InteractionHand.MAIN_HAND, s);
        tr(it, "carry", () -> s.inventoryTick(lv, p, p.getInventory().selected, true));
        // right-click in the air
        tr(it, "use", () -> {
            var r = s.use(lv, p, InteractionHand.MAIN_HAND);
            ItemStack held = r.getObject();
            if (held != p.getMainHandItem()) p.setItemInHand(InteractionHand.MAIN_HAND, held);
            if (p.isUsingItem()) {
                ItemStack u = p.getUseItem();
                int dur = u.getUseDuration();
                for (int i = 0; i < Math.min(4, Math.max(1, dur)); i++) u.onUseTick(lv, p, dur - i);
                if (dur > 0 && dur <= 72000 / 2) {
                    ItemStack after = u.finishUsingItem(lv, p);
                    p.setItemInHand(InteractionHand.MAIN_HAND, after);
                } else u.releaseUsing(lv, p, dur - 20);
                p.stopUsingItem();
            }
        });
        reset(p);
        ItemStack s2 = new ItemStack(it, Math.min(4, it.getMaxStackSize()));
        p.setItemInHand(InteractionHand.MAIN_HAND, s2);
        // use on the floor in front
        tr(it, "use on floor", () -> {
            BlockPos floor = BlockPos.containing(PackTests.HOME[0], PackTests.HOME[1] - 1, PackTests.HOME[2] + 3);
            var hit = new BlockHitResult(Vec3.atCenterOf(floor).add(0, 0.5, 0), Direction.UP, floor, false);
            s2.useOn(new UseOnContext(p, InteractionHand.MAIN_HAND, hit));
        });
        // swing at a mob
        reset(p);
        ItemStack s3 = new ItemStack(it);
        p.setItemInHand(InteractionHand.MAIN_HAND, s3);
        tr(it, "hit", () -> {
            Mob z = EntityType.ZOMBIE.create(lv);
            z.moveTo(PackTests.HOME[0], PackTests.HOME[1], PackTests.HOME[2] + 1.5, 180F, 0F);
            z.setNoAi(true); z.addTag("xw_pack");
            lv.addFreshEntity(z);
            s3.interactLivingEntity(p, z, InteractionHand.MAIN_HAND);
            p.attack(z);
            if (!p.getMainHandItem().isEmpty()) p.getMainHandItem().hurtEnemy(z, p);
            z.discard();
        });
        if (it instanceof ArmorItem a) {
            reset(p);
            tr(it, "wear", () -> {
                p.setItemSlot(a.getEquipmentSlot(), new ItemStack(it));
                p.getItemBySlot(a.getEquipmentSlot()).onArmorTick(lv, p);
            });
        }
        reset(p);
        PackTests.clear(p);
        // anything it spawned or placed: spawn-egg mobs, placed blocks
        lv.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(24), e -> !(e instanceof Player)
                && !(e instanceof com.xenowarriors.bot.PartyBotEntity)).forEach(e -> e.discard());
        BlockPos floor = BlockPos.containing(PackTests.HOME[0], PackTests.HOME[1], PackTests.HOME[2] + 3);
        for (int y = 0; y < 4; y++) lv.setBlock(floor.above(y), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
        lv.setBlock(floor.below(), net.minecraft.world.level.block.Blocks.SMOOTH_STONE.defaultBlockState(), 2);
        done++;
    }

    private static void client(Minecraft mc, Item it) {
        if (mc.screen != null) { SCREENS.add(id(it) + "=" + mc.screen.getClass().getSimpleName()); mc.setScreen(null); }
        ItemStack s = new ItemStack(it);
        tr(it, "tooltip", () -> s.getTooltipLines(mc.player, TooltipFlag.Default.ADVANCED));
        tr(it, "model", () -> {
            var m = mc.getItemRenderer().getModel(s, mc.level, mc.player, 0);
            if (m == mc.getModelManager().getMissingModel()) MISSING_MODEL.add(id(it));
        });
        tr(it, "name", () -> s.getHoverName().getString());
    }

    static void build(Minecraft mc, List<Step> l) {
        l.add(new Step(null, () -> { mc.options.hideGui = true; PackTests.server(p -> { PackTests.arena(p); PackTests.clear(p); }); }, 30));
        List<Item> all = items();
        PackTests.log("items: " + all.size());
        for (Item it : all) {
            l.add(new Step(null, () -> PackTests.server(p -> server(p, it)), 4));
            l.add(new Step(null, () -> client(mc, it), 2));
        }
        l.add(new Step("items_done", () -> {
            if (mc.screen != null) { SCREENS.add("end=" + mc.screen.getClass().getSimpleName()); mc.setScreen(null); }
            PackTests.server(p -> { reset(p); PackTests.arena(p); });
            PackTests.log("items screens opened: " + SCREENS);
            PackTests.log("items moved the player: " + MOVED);
            PackTests.check("items: every item went through the tests", done == all.size(), done + " of " + all.size());
            PackTests.check("items: no exceptions", FAILS.isEmpty(), FAILS.toString());
            PackTests.check("items: every item has a model", MISSING_MODEL.isEmpty(), MISSING_MODEL.toString());
        }, 20));
    }
}

package com.xenowarriors.bot;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Bots and TaCZ guns, out of a fight (every few seconds, like BotCrafting):
 * <ul>
 *   <li>Attachments: scopes, grips, muzzles, stocks, mags... in its bag go on its gun (the one in hand first, then any in
 *   its bag) wherever the gun takes one and that slot is empty.</li>
 *   <li>The Gun Smith Table: with one within {@value #RANGE} blocks it crafts what its gun can still take (an attachment
 *   for an empty slot) - or a gun, if it has none - from what's in its bag, by TaCZ's own table recipes.</li>
 *   <li>No table near but the bag holds what one takes (3 logs, 4 iron ingots, an iron block)? It crafts one and sets it
 *   down beside itself, then uses it.</li>
 * </ul>
 * All through TaCZ's API by name: without TaCZ nothing happens.
 */
public final class BotGunsmith {
    private BotGunsmith() {}

    public static final int RANGE = 6, EVERY = 120;
    /** (Tests) attachments fitted, things made at the table, tables made. */
    public static int fitted, made, tables;

    private static Boolean ok;
    private static Class<?> igun, iattach, recipeCls;
    private static Method allow, install, getAtt, attType, getInputs, getOutput, ingIngredient, ingCount;

    private static boolean ready() {
        if (ok != null) return ok;
        ok = false;
        if (!com.xenowarriors.compat.TaczGuns.available()) return false;
        try {
            igun = Class.forName("com.tacz.guns.api.item.IGun");
            iattach = Class.forName("com.tacz.guns.api.item.IAttachment");
            Class<?> type = Class.forName("com.tacz.guns.api.item.attachment.AttachmentType");
            allow = igun.getMethod("allowAttachment", ItemStack.class, ItemStack.class);
            install = igun.getMethod("installAttachment", ItemStack.class, ItemStack.class);
            getAtt = igun.getMethod("getAttachment", ItemStack.class, type);
            attType = iattach.getMethod("getType", ItemStack.class);
            recipeCls = Class.forName("com.tacz.guns.crafting.GunSmithTableRecipe");
            getInputs = recipeCls.getMethod("getInputs");
            getOutput = recipeCls.getMethod("getOutput");
            Class<?> ing = Class.forName("com.tacz.guns.crafting.GunSmithTableIngredient");
            ingIngredient = ing.getMethod("getIngredient");
            ingCount = ing.getMethod("getCount");
            ok = true;
        } catch (Throwable t) {
            com.xenowarriors.XenoWarriors.LOGGER.warn("TaCZ's attachment / gun smith API didn't match - bots won't fit attachments: {}", t.toString());
        }
        return ok;
    }

    static boolean isGun(ItemStack s) { return !s.isEmpty() && igun.isInstance(s.getItem()); }
    static boolean isAttachment(ItemStack s) { return !s.isEmpty() && iattach.isInstance(s.getItem()); }

    /** Out of a fight now and then: attachments on, then the gun smith table. */
    public static void tick(PartyBotEntity b) {
        if (b.tickCount % EVERY != 37 || b.downed() || b.getTarget() != null || b.isNoAi() || b.storyHold || !ready()) return;
        if (!(b.level() instanceof ServerLevel s)) return;
        try {
            fitAll(b);
            smith(b, s);
        } catch (Throwable t) {
            ok = false;
            com.xenowarriors.XenoWarriors.LOGGER.warn("Bot gun smithing failed - off for this session: {}", t.toString());
        }
    }

    /** (Tests) fit attachments and use / make the gun smith table now. */
    public static void testRun(PartyBotEntity b) throws Exception {
        if (!ready() || !(b.level() instanceof ServerLevel s)) return;
        fitAll(b);
        smith(b, s);
    }

    // ------------------------------------------------------------------ attachments

    /** Its guns (in hand, then in the bag) get the attachments from its bag that fit an empty slot. */
    static void fitAll(PartyBotEntity b) throws Exception {
        List<ItemStack> guns = new ArrayList<>();
        if (isGun(b.getMainHandItem())) guns.add(b.getMainHandItem());
        for (int i = 0; i < b.inventory.getContainerSize(); i++) if (isGun(b.inventory.getItem(i))) guns.add(b.inventory.getItem(i));
        for (ItemStack gun : guns) {
            for (int i = 0; i < b.inventory.getContainerSize(); i++) {
                ItemStack a = b.inventory.getItem(i);
                if (!isAttachment(a) || !fits(gun, a)) continue;
                ItemStack one = a.split(1);
                install.invoke(gun.getItem(), gun, one);
                b.inventory.setChanged();
                fitted++;
                say(b, Component.literal(" put ").append(one.getHoverName().copy().withStyle(ChatFormatting.YELLOW)).append(" on ").append(gun.getHoverName().copy().withStyle(ChatFormatting.GOLD)));
                b.level().playSound(null, b.blockPosition(), SoundEvents.ARMOR_EQUIP_IRON, SoundSource.NEUTRAL, 0.8F, 1.3F);
            }
        }
    }

    /** This gun takes this attachment and that slot is empty. */
    static boolean fits(ItemStack gun, ItemStack att) throws Exception {
        if (!(boolean) allow.invoke(gun.getItem(), gun, att)) return false;
        Object type = attType.invoke(att.getItem(), att);
        ItemStack on = (ItemStack) getAtt.invoke(gun.getItem(), gun, type);
        return on == null || on.isEmpty();
    }

    // ------------------------------------------------------------------ the gun smith table

    private static boolean table(net.minecraft.world.level.block.state.BlockState st) {
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(st.getBlock());
        return id != null && id.getNamespace().equals("tacz") && st.getBlock().getClass().getSimpleName().contains("GunSmithTable");
    }

    static BlockPos nearTable(PartyBotEntity b) {
        BlockPos c = b.blockPosition();
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-RANGE, -2, -RANGE), c.offset(RANGE, 2, RANGE)))
            if (table(b.level().getBlockState(p))) return p.immutable();
        return null;
    }

    /** What it'd like from the table: an attachment for an empty slot on its gun, or a gun if it has none. */
    private static boolean wanted(PartyBotEntity b, ItemStack out) throws Exception {
        ItemStack gun = isGun(b.getMainHandItem()) ? b.getMainHandItem() : ItemStack.EMPTY;
        if (gun.isEmpty()) for (int i = 0; i < b.inventory.getContainerSize(); i++) if (isGun(b.inventory.getItem(i))) { gun = b.inventory.getItem(i); break; }
        if (gun.isEmpty()) return isGun(out);
        if (!isAttachment(out) || !fits(gun, out)) return false;
        for (int i = 0; i < b.inventory.getContainerSize(); i++) {         // (not one it already carries for that slot)
            ItemStack a = b.inventory.getItem(i);
            if (isAttachment(a) && attType.invoke(a.getItem(), a) == attType.invoke(out.getItem(), out)) return false;
        }
        return true;
    }

    private static void smith(PartyBotEntity b, ServerLevel s) throws Exception {
        if (BotCrafting.freeSlots(b.inventory) == 0) return;
        List<Object[]> can = new ArrayList<>();                                // {recipe output, inputs}
        for (Recipe<?> r : s.getRecipeManager().getRecipes()) {
            if (!recipeCls.isInstance(r)) continue;
            ItemStack out = (ItemStack) getOutput.invoke(r);
            if (out == null || out.isEmpty() || !wanted(b, out)) continue;
            List<?> in = (List<?>) getInputs.invoke(r);
            if (take(b.inventory, in, false)) can.add(new Object[]{out, in});
        }
        if (can.isEmpty()) return;
        BlockPos t = nearTable(b);
        if (t == null) t = placeTable(b, s);                                    // (none near: makes and sets down its own, if it can)
        if (t.equals(BlockPos.ZERO)) return;
        Object[] pick = can.get(0);
        for (Object[] c : can) if (isAttachment((ItemStack) c[0]) && !isAttachment((ItemStack) pick[0])) pick = c;   // (attachments before another gun)
        if (!take(b.inventory, (List<?>) pick[1], true)) return;
        ItemStack res = ((ItemStack) pick[0]).copy();
        if (!b.inventory.canAddItem(res)) { b.spawnAtLocation(res); } else b.inventory.addItem(res);
        made++;
        BotMoves.face(b, (float) Math.toDegrees(Math.atan2(-(t.getX() + .5 - b.getX()), t.getZ() + .5 - b.getZ())), 30F);
        b.swing(InteractionHand.MAIN_HAND);
        s.playSound(null, t, SoundEvents.SMITHING_TABLE_USE, SoundSource.NEUTRAL, 0.9F, 1.1F);
        say(b, Component.literal(" made ").append(res.getHoverName().copy().withStyle(ChatFormatting.YELLOW)).append(" at the Gun Smith Table"));
        fitAll(b);                                                              // (and puts it straight on)
    }

    /** Takes (or, with {@code doIt} false, only checks for) a TaCZ recipe's inputs - each with its count - from the bag. */
    private static boolean take(net.minecraft.world.Container inv, List<?> inputs, boolean doIt) throws Exception {
        SimpleContainer c = new SimpleContainer(inv.getContainerSize());
        for (int i = 0; i < inv.getContainerSize(); i++) c.setItem(i, inv.getItem(i).copy());
        for (Object o : inputs) {
            Ingredient ing = (Ingredient) ingIngredient.invoke(o);
            int need = (int) ingCount.invoke(o);
            for (int i = 0; i < c.getContainerSize() && need > 0; i++) {
                ItemStack st = c.getItem(i);
                if (st.isEmpty() || !ing.test(st)) continue;
                int n = Math.min(need, st.getCount());
                st.shrink(n);
                need -= n;
            }
            if (need > 0) return false;
        }
        if (doIt) { for (int i = 0; i < inv.getContainerSize(); i++) inv.setItem(i, c.getItem(i)); inv.setChanged(); }
        return true;
    }

    /** No table near: makes one from its bag (3 logs, 4 iron ingots, an iron block) and sets it down beside itself. BlockPos.ZERO if it can't. */
    private static BlockPos placeTable(PartyBotEntity b, ServerLevel s) {
        var item = ForgeRegistries.ITEMS.getValue(new ResourceLocation("tacz", "gun_smith_table"));
        if (!(item instanceof BlockItem bi)) return BlockPos.ZERO;
        int slot = -1;
        for (int i = 0; i < b.inventory.getContainerSize(); i++) if (b.inventory.getItem(i).is(item)) { slot = i; break; }
        if (slot < 0) {
            SimpleContainer c = new SimpleContainer(b.inventory.getContainerSize());
            for (int i = 0; i < b.inventory.getContainerSize(); i++) c.setItem(i, b.inventory.getItem(i).copy());
            if (!takeTag(c, net.minecraft.tags.ItemTags.LOGS, 3) || !takeItem(c, net.minecraft.world.item.Items.IRON_INGOT, 4) || !takeItem(c, net.minecraft.world.item.Items.IRON_BLOCK, 1)) return BlockPos.ZERO;
            for (int i = 0; i < b.inventory.getContainerSize(); i++) b.inventory.setItem(i, c.getItem(i));
            say(b, Component.literal(" crafted a ").append(new ItemStack(item).getHoverName().copy().withStyle(ChatFormatting.YELLOW)));
        } else b.inventory.getItem(slot).shrink(1);
        // set it down: a free spot on solid ground next to it (the table is two blocks wide)
        ItemStack one = new ItemStack(item);
        var fake = PartyBots.fake(b);
        Direction face = b.getDirection();
        for (Direction d : new Direction[]{face, face.getClockWise(), face.getCounterClockWise(), face.getOpposite()}) {
            BlockPos at = b.blockPosition().relative(d, 2);
            if (!s.getBlockState(at.below()).isFaceSturdy(s, at.below(), Direction.UP) || !s.getBlockState(at).canBeReplaced()) continue;
            fake.moveTo(b.getX(), b.getY(), b.getZ(), d.toYRot(), 0F);
            fake.setItemInHand(InteractionHand.MAIN_HAND, one);
            var res = bi.place(new BlockPlaceContext(fake, InteractionHand.MAIN_HAND, one, new BlockHitResult(Vec3.atBottomCenterOf(at), Direction.UP, at, false)));
            fake.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            if (res.consumesAction() && table(s.getBlockState(at))) {
                tables++;
                b.swing(InteractionHand.MAIN_HAND);
                say(b, Component.literal(" set up a Gun Smith Table"));
                return at;
            }
        }
        b.inventory.addItem(new ItemStack(item));                              // (nowhere to put it: keeps it)
        return BlockPos.ZERO;
    }

    private static boolean takeTag(SimpleContainer c, net.minecraft.tags.TagKey<net.minecraft.world.item.Item> tag, int n) {
        for (int i = 0; i < c.getContainerSize() && n > 0; i++) { ItemStack st = c.getItem(i); if (st.is(tag)) { int k = Math.min(n, st.getCount()); st.shrink(k); n -= k; } }
        return n <= 0;
    }

    private static boolean takeItem(SimpleContainer c, net.minecraft.world.item.Item it, int n) {
        for (int i = 0; i < c.getContainerSize() && n > 0; i++) { ItemStack st = c.getItem(i); if (st.is(it)) { int k = Math.min(n, st.getCount()); st.shrink(k); n -= k; } }
        return n <= 0;
    }

    private static void say(PartyBotEntity b, Component what) {
        ServerPlayer o = b.owner();
        if (o != null && o.distanceToSqr(b) < 48 * 48) o.sendSystemMessage(Component.empty().append(b.label()).withStyle(ChatFormatting.AQUA).append(what.copy().withStyle(ChatFormatting.WHITE)));
    }
}

package com.xenowarriors.item;

import com.xenowarriors.XenoWarriors;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * The Xeno Warriors Guide: right-click to read how everything in the mod works (the same pages as the Guide tab in
 * the main menu - assets/xenowarriors/guide/guide.json). Everyone gets one the first time they join; crafted from a
 * book and paper.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class GuideBookItem extends Item {
    private static final String GIVEN = "xw_guide_given";

    public GuideBookItem(Properties p) { super(p); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide) DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.xenowarriors.client.menu.GuideScreen.open(null));
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.literal("How to play: combat, team attacks, party, characters, modes, progression").withStyle(ChatFormatting.GRAY));
        tip.add(Component.literal("Also in the main menu (M) - Guide").withStyle(ChatFormatting.DARK_GRAY));
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || p instanceof net.minecraftforge.common.util.FakePlayer) return;
        CompoundTag root = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (root.getBoolean(GIVEN)) return;
        root.putBoolean(GIVEN, true);
        p.getPersistentData().put(Player.PERSISTED_NBT_TAG, root);
        ItemStack book = new ItemStack(ModItems.GUIDE_BOOK.get());
        if (!p.addItem(book)) p.drop(book, false);
        p.sendSystemMessage(Component.literal("You got the Xeno Warriors Guide - right-click it (or open the Guide in the M menu) to learn how everything works.").withStyle(ChatFormatting.LIGHT_PURPLE));
    }
}

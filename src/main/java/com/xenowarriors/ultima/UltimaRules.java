package com.xenowarriors.ultima;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.character.ModCapabilities;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Ultima can't wear Skylander (Sky) armor or Corrupted armor - it rejects his body. It's taken back off. */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class UltimaRules {
    private UltimaRules() {}

    public static boolean forbidden(ItemStack s) {
        return s.getItem() instanceof com.xenowarriors.sky.SkyArmorItem || s.getItem() instanceof com.xenowarriors.sky.CorruptedArmorItem;
    }

    @SubscribeEvent
    public static void onEquip(LivingEquipmentChangeEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || event.getSlot().getType() != EquipmentSlot.Type.ARMOR) return;
        if (ModCapabilities.getType(p) != CharacterType.ULTIMA || !forbidden(event.getTo())) return;
        ItemStack st = event.getTo().copy();
        p.setItemSlot(event.getSlot(), ItemStack.EMPTY);
        if (!p.getInventory().add(st)) p.drop(st, false);
        p.displayClientMessage(Component.literal("Ultima's body rejects that armor").withStyle(ChatFormatting.RED), true);
    }
}

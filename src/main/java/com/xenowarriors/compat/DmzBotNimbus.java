package com.xenowarriors.compat;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.bot.PartyBotEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Locale;

/**
 * With DragonMineZ: a bot that can't fly yet (not a natural flier, hasn't learned DragonMineZ's Fly) walks over to
 * Master Korin (DragonMineZ's "karin") when one is near and asks for a Flying Nimbus - the same trip as asking a master
 * for training weights ({@link DmzBotMasters}). A Flying Nimbus (or a Black Nimbus) in its bag lets it fly: it rides
 * the cloud wherever the fliers fly (drawn under it), until it learns to fly by itself.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DmzBotNimbus {
    private DmzBotNimbus() {}

    public static final String MASTER = "karin";
    static final ResourceLocation NIMBUS = new ResourceLocation("dragonminez", "flying_nimbus"), BLACK = new ResourceLocation("dragonminez", "black_nimbus");
    /** (Tests) nimbuses handed out. */
    public static int given;

    static boolean ready() { return ModList.get() != null && ModList.get().isLoaded("dragonminez") && ForgeRegistries.ITEMS.containsKey(NIMBUS); }

    public static boolean isNimbus(ItemStack s) {
        if (s.isEmpty()) return false;
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(s.getItem());
        return NIMBUS.equals(id) || BLACK.equals(id);
    }

    public static boolean has(PartyBotEntity b) {
        for (int i = 0; i < b.inventory.getContainerSize(); i++) if (isNimbus(b.inventory.getItem(i))) return true;
        return false;
    }

    /** Can't fly on its own (yet). */
    static boolean grounded(PartyBotEntity b) { return !b.naturalFlier() && !b.dmzFly(); }

    static boolean wantsFrom(PartyBotEntity b, String master) {
        return ready() && master != null && MASTER.equals(master.toLowerCase(Locale.ROOT)) && grounded(b) && !has(b);
    }

    /** Korin hands it a Flying Nimbus. */
    static boolean receive(PartyBotEntity b, net.minecraft.server.level.ServerPlayer owner, String master) {
        if (!wantsFrom(b, master)) return false;
        Item it = ForgeRegistries.ITEMS.getValue(NIMBUS);
        if (it == null) return false;
        ItemStack s = new ItemStack(it);
        if (!b.inventory.canAddItem(s)) return false;
        b.inventory.addItem(s);
        given++;
        refresh(b);
        if (owner != null) DmzNews.bot(owner, b, "Flying Nimbus", Component.empty().append(b.character().getDisplayName()).append(" got a ")
                .append(s.getHoverName().copy().withStyle(ChatFormatting.GOLD)).append(" from Korin - it can fly now").withStyle(ChatFormatting.AQUA));
        return true;
    }

    /** Rides a nimbus while it can't fly any other way and has one in its bag. */
    public static void refresh(PartyBotEntity b) { b.setNimbus(grounded(b) && has(b)); }

    @SubscribeEvent
    public static void onTick(LivingEvent.LivingTickEvent e) {
        if (e.getEntity() instanceof PartyBotEntity b && !b.level().isClientSide && b.tickCount % 40 == 21) refresh(b);
    }

    /** (Tests) as if it had talked to Korin. */
    public static boolean receiveForTest(PartyBotEntity b) { return receive(b, b.owner(), MASTER); }
}

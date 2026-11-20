package com.xenowarriors.bot;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.xenowarriors.XenoWarriors;
import com.xenowarriors.network.RequestPopupPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.AnvilUpdateEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Souls-like Universe's Repairstones (slu:repairstone) fix anything, not only that mod's gear:
 * <ul>
 *   <li>at an anvil, each Repairstone puts back a tenth of an item's durability (1 level each);</li>
 *   <li>a bot with {@value #COST} in its bag mends its own badly worn gear (half gone or worse) completely;</li>
 *   <li>and now and then (every 10 minutes at most) it offers to mend yours - the most worn thing you're wearing or
 *       holding, 40% gone or worse - with a request card. Say yes and it's like new; the stones are the bot's.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BotRepairStones {
    private BotRepairStones() {}

    public static final ResourceLocation ID = new ResourceLocation("slu", "repairstone");
    public static final int COST = 10;
    static final int OFFER_GAP = 20 * 60 * 10;
    private static final Map<UUID, Long> NEXT_OFFER = new HashMap<>();
    /** (Tests) a bot's own gear mended, your gear mended, offers made, anvil repairs. */
    public static int ownFixed, yoursFixed, offers, anvil;

    public static Item stone() {
        Item it = ForgeRegistries.ITEMS.getValue(ID);
        return it == null || it == net.minecraft.world.item.Items.AIR ? null : it;
    }

    static int count(PartyBotEntity b) {
        Item s = stone();
        if (s == null) return 0;
        int n = 0;
        for (int i = 0; i < b.inventory.getContainerSize(); i++) if (b.inventory.getItem(i).is(s)) n += b.inventory.getItem(i).getCount();
        return n;
    }

    private static boolean take(PartyBotEntity b, int n) {
        Item s = stone();
        if (s == null || count(b) < n) return false;
        for (int i = 0; i < b.inventory.getContainerSize() && n > 0; i++) {
            ItemStack st = b.inventory.getItem(i);
            if (!st.is(s)) continue;
            int t = Math.min(n, st.getCount()); st.shrink(t); n -= t;
        }
        b.inventory.setChanged();
        return true;
    }

    private static float worn(ItemStack s) { return s.isDamageableItem() && s.getMaxDamage() > 0 ? s.getDamageValue() / (float) s.getMaxDamage() : 0F; }

    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    /** Every 20 seconds, from the bot's tick: its own gear, then an offer to you. */
    static void tick(PartyBotEntity b) {
        if (b.tickCount % 400 != 123 || b.level().isClientSide || b.downed() || b.journey() || stone() == null) return;
        if (count(b) < COST) return;
        ServerPlayer o = b.owner();
        if (o == null || !BotSpar.quiet(o)) return;
        for (EquipmentSlot sl : SLOTS) {                                          // its own gear first
            ItemStack s = b.getItemBySlot(sl);
            if (worn(s) >= 0.5F) { mend(b, s); ownFixed++; o.displayClientMessage(Component.empty().append(b.label()).append(" mended their ").append(s.getHoverName()).append(" with Repairstones.").withStyle(ChatFormatting.AQUA), true); return; }
        }
        long now = b.level().getGameTime();
        if (now < NEXT_OFFER.getOrDefault(b.getUUID(), 0L) || b.distanceToSqr(o) > 10 * 10) return;
        EquipmentSlot best = mostWorn(o);
        if (best == null) return;
        offer(b, o, best);
    }

    /** The most worn thing you're wearing or holding (40% gone or worse), or null. */
    static EquipmentSlot mostWorn(ServerPlayer o) {
        EquipmentSlot best = null; float bw = 0.4F;
        for (EquipmentSlot sl : SLOTS) { float w = worn(o.getItemBySlot(sl)); if (w >= bw) { bw = w; best = sl; } }
        return best;
    }

    /** (Tests too) asks you, with a card, whether it may mend this. */
    public static void offer(PartyBotEntity b, ServerPlayer o, EquipmentSlot sl) {
        NEXT_OFFER.put(b.getUUID(), b.level().getGameTime() + OFFER_GAP);
        ItemStack s = o.getItemBySlot(sl);
        offers++;
        String yes = "/xenorepair yes " + b.getId() + " " + sl.ordinal(), no = "/xenorepair no " + b.getId() + " " + sl.ordinal();
        BotRequests.send(o, new RequestPopupPacket(b.character().getDisplayName().getString() + " can fix your " + s.getHoverName().getString() + "!",
                "It's " + Math.round(worn(s) * 100) + "% worn - they'll use " + COST + " of their Repairstones to make it like new.", yes, no, 20 * 20, 0xFF9AD0FF));
    }

    private static void mend(PartyBotEntity b, ItemStack s) {
        take(b, COST);
        s.setDamageValue(0);
        b.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        b.level().playSound(null, b.blockPosition(), SoundEvents.ANVIL_USE, SoundSource.NEUTRAL, 0.6F, 1.3F);
    }

    /** (Tests) mends this slot of yours now, as if you'd said yes. */
    public static boolean accept(ServerPlayer o, PartyBotEntity b, EquipmentSlot sl) {
        ItemStack s = o.getItemBySlot(sl);
        if (worn(s) <= 0F) { o.displayClientMessage(Component.literal("Nothing to fix there any more.").withStyle(ChatFormatting.GRAY), true); return false; }
        if (count(b) < COST) { o.displayClientMessage(Component.empty().append(b.label()).append(" is out of Repairstones.").withStyle(ChatFormatting.GRAY), true); return false; }
        mend(b, s);
        yoursFixed++;
        o.displayClientMessage(Component.empty().append(b.label()).append(" fixed your ").append(s.getHoverName()).append(" - good as new!").withStyle(ChatFormatting.GREEN), false);
        return true;
    }

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("xenorepair")
                .then(Commands.literal("yes").then(Commands.argument("bot", IntegerArgumentType.integer()).then(Commands.argument("slot", IntegerArgumentType.integer(0, 5)).executes(c -> answer(c.getSource().getPlayerOrException(),
                        IntegerArgumentType.getInteger(c, "bot"), IntegerArgumentType.getInteger(c, "slot"), true)))))
                .then(Commands.literal("no").then(Commands.argument("bot", IntegerArgumentType.integer()).then(Commands.argument("slot", IntegerArgumentType.integer(0, 5)).executes(c -> answer(c.getSource().getPlayerOrException(),
                        IntegerArgumentType.getInteger(c, "bot"), IntegerArgumentType.getInteger(c, "slot"), false))))));
    }

    private static int answer(ServerPlayer p, int id, int slot, boolean yes) {
        RequestPopupPacket.dismiss(p, "/xenorepair yes " + id + " " + slot);
        if (!(p.level().getEntity(id) instanceof PartyBotEntity b) || !p.getUUID().equals(b.ownerId())) return 0;
        if (!yes) return 1;
        return accept(p, b, EquipmentSlot.values()[slot]) ? 1 : 0;
    }

    // ------------------------------------------------------------------ at an anvil

    /** Anything damaged + Repairstones at an anvil: a tenth of its durability back per stone (1 level each). Souls-like gear keeps its own way. */
    @SubscribeEvent
    public static void onAnvil(AnvilUpdateEvent e) {
        Item s = stone();
        ItemStack left = e.getLeft(), right = e.getRight();
        if (s == null || !right.is(s) || !left.isDamageableItem() || left.getDamageValue() <= 0) return;
        if (left.getItem().isValidRepairItem(left, right)) return;               // (its own repair - Souls-like's gear)
        int per = Math.max(1, left.getMaxDamage() / 10);
        int use = Math.min(right.getCount(), (left.getDamageValue() + per - 1) / per);
        if (use <= 0) return;
        ItemStack out = left.copy();
        out.setDamageValue(Math.max(0, left.getDamageValue() - per * use));
        if (e.getName() != null && !e.getName().isBlank() && !e.getName().equals(left.getHoverName().getString())) out.setHoverName(Component.literal(e.getName()));
        e.setOutput(out);
        e.setMaterialCost(use);
        e.setCost(use);
        anvil++;
    }
}

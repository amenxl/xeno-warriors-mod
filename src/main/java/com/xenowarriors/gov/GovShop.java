package com.xenowarriors.gov;

import com.xenowarriors.character.CharacterType;
import com.xenowarriors.character.ModCapabilities;
import com.xenowarriors.economy.Money;
import com.xenowarriors.network.ModNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;

import java.util.List;
import java.util.function.Supplier;

/**
 * The Government Supply Terminal (Aurora plaza, and in Government facilities): Government gear for Money.
 * The terminal face-scans you: Government people (Ultima, Technica, Yuri, Max) get 20% off; Mikiko is wanted by
 * Aurora's Government, so the terminal raises the alarm unless his face is hidden by a hood; anyone already wanted
 * for a facility break-in is refused.
 */
public final class GovShop {
    public record Offer(String name, String desc, long price, Supplier<ItemStack> make) {}

    public static final List<Offer> OFFERS = List.of(
            new Offer("Aurora Medicine", "Slows the Aurora virus", 150, () -> new ItemStack(com.xenowarriors.item.ModItems.AURORA_MEDICINE.get())),
            new Offer("Med Pack", "Strong healing", 100, () -> PotionUtils.setPotion(new ItemStack(Items.POTION), Potions.STRONG_HEALING)),
            new Offer("Data Shard", "Ultima's Data", 120, () -> new ItemStack(com.xenowarriors.item.ModItems.DATA_SHARD.get())),
            new Offer("Speed Mod", "Human mod: Speed II, 10 min", 400, () -> new ItemStack(GovItems.SPEED_MOD.get())),
            new Offer("Jump Boosters", "Human mod: big jumps, soft landings, 10 min", 400, () -> new ItemStack(GovItems.JUMP_MOD.get())),
            new Offer("Scanner Eye", "Human mod: see enemies through walls, 10 min", 450, () -> new ItemStack(GovItems.SCANNER_MOD.get())),
            new Offer("Armor Plating", "Human mod: Resistance + extra hearts, 10 min", 500, () -> new ItemStack(GovItems.PLATING_MOD.get())),
            new Offer("Karma Scanner", "Visor: Karma Levels of anyone you look at (overloads above 50,000)", 600, () -> new ItemStack(GovItems.KARMA_SCANNER.get())),
            new Offer("Mod Chip", "Government tech part", 250, () -> new ItemStack(com.xenowarriors.item.ModItems.MOD_CHIP.get())),
            new Offer("Facility Map", "Finds the nearest Gov facility", 300, () -> new ItemStack(com.xenowarriors.item.ModItems.FACILITY_MAP.get())),
            new Offer("Gov Keycard", "Opens a facility vault", 2500, () -> new ItemStack(GovItems.KEYCARD.get())),
            new Offer("Healing Pod", "The facility pod, for your base", 8000, () -> new ItemStack(com.xenowarriors.item.ModItems.HEALING_POD.get())));

    private GovShop() {}

    public static boolean govSide(CharacterType t) {
        return t == CharacterType.ULTIMA || t == CharacterType.TECHNICA || t == CharacterType.YURI || t == CharacterType.MAX;
    }

    /** A hood (Karma hoodie, SR hoodie, Fox PJs hood) or any helmet hides your face from the scanner. */
    static boolean faceHidden(ServerPlayer p) {
        return !p.getItemBySlot(EquipmentSlot.HEAD).isEmpty();
    }

    static boolean hoodie(ServerPlayer p) {
        ItemStack chest = p.getItemBySlot(EquipmentSlot.CHEST);
        return chest.is(com.xenowarriors.item.ModItems.KARMA_HOODIE.get()) || chest.is(com.xenowarriors.item.ModItems.SR_HOODIE.get());
    }

    /** Percent off from your Government standing (negative = a surcharge). */
    public static int discount(ServerPlayer p) {
        Integer c = com.xenowarriors.faction.Reputation.priceChange(p, com.xenowarriors.faction.Faction.GOV);
        return c == null ? 0 : -c;
    }

    public static long price(ServerPlayer p, Offer o) { return o.price * (100 - discount(p)) / 100; }

    /** null if they can shop; otherwise why not (and the alarm may go off). */
    private static String refuse(ServerPlayer p) {
        if (GovFacilities.wanted(p)) return "WANTED - service refused. Guards have been notified.";
        if (com.xenowarriors.faction.Reputation.priceChange(p, com.xenowarriors.faction.Faction.GOV) == null) return "Service refused - the Government considers you hostile.";
        if (ModCapabilities.getType(p) == CharacterType.MIKIKO && !faceHidden(p) && !hoodie(p) && !p.isCreative()) {
            GovFacilities.makeWanted(p, "were face-scanned at a Gov terminal");
            return "FACE SCAN: AmenXL Mikiko - WANTED BY THE GOVERNMENT OF AURORA. Guards have been notified.";
        }
        return null;
    }

    private static final String AT = "xw_gov_terminal";

    /** Opened from a terminal at `pos` (buying only works near it). */
    public static void open(ServerPlayer p, net.minecraft.core.BlockPos pos) {
        p.getPersistentData().putLong(AT, pos.asLong());
        open(p);
    }

    private static boolean atTerminal(ServerPlayer p) {
        if (!p.getPersistentData().contains(AT)) return false;
        net.minecraft.core.BlockPos pos = net.minecraft.core.BlockPos.of(p.getPersistentData().getLong(AT));
        return p.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pos)) < 7 * 7 && p.level().getBlockState(pos).is(GovItems.TERMINAL.get());
    }

    public static void open(ServerPlayer p) {
        String no = refuse(p);
        if (no != null) {
            p.displayClientMessage(Component.literal(no).withStyle(ChatFormatting.RED, ChatFormatting.BOLD), false);
            p.level().playSound(null, p.blockPosition(), SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.BLOCKS, 1F, 0.5F);
            return;
        }
        ModNetwork.sendToPlayer(new com.xenowarriors.network.GovShopPacket(Money.get(p), discount(p), -1), p);
    }

    public static void buy(ServerPlayer p, int idx) {
        if (idx < 0 || idx >= OFFERS.size() || !atTerminal(p)) return;
        if (refuse(p) != null) { open(p); return; }
        Offer o = OFFERS.get(idx);
        long cost = price(p, o);
        if (!p.isCreative() && !Money.take(p, cost)) {
            p.displayClientMessage(Component.literal("Card declined - you need " + Money.fmt(cost) + " Money.").withStyle(ChatFormatting.RED), true);
            return;
        }
        ItemStack it = o.make.get();
        if (!p.addItem(it)) p.drop(it, false);
        p.level().playSound(null, p.blockPosition(), SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 0.8F, 1.6F);
        p.displayClientMessage(Component.literal("Purchased: " + o.name).withStyle(ChatFormatting.AQUA), true);
        if (p.getRandom().nextInt(2) == 0) com.xenowarriors.faction.Reputation.add(p, com.xenowarriors.faction.Faction.GOV, 3);
        open(p);
    }
}

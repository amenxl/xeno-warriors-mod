package com.xenowarriors.statboost;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.item.ModItems;
import com.xenowarriors.network.BoostBagPacket;
import com.xenowarriors.network.ModNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;

/**
 * The Boost Bag: holds every Stat Boost (no limit per kind). While it's in your inventory, boosts you pick up - and the
 * ones a level-up hands you - go straight into it. Right-click it: every boost with how many you have; take some out,
 * use them from the bag, or put the ones in your inventory in.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BoostBag {
    private BoostBag() {}

    public static final RegistryObject<Item> ITEM = ModItems.ITEMS.register("boost_bag", () -> new BagItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));

    public static void init() {}

    /** Every boost kind, in the bag's order: stat by stat, tier by tier. */
    public static int index(BoostStat s, BoostTier t) { return s.ordinal() * BoostTier.values().length + t.ordinal(); }
    public static int kinds() { return BoostStat.values().length * BoostTier.values().length; }
    public static BoostStat statOf(int i) { return BoostStat.values()[i / BoostTier.values().length]; }
    public static BoostTier tierOf(int i) { return BoostTier.values()[i % BoostTier.values().length]; }

    private static CompoundTag tag(ItemStack bag) { return bag.getOrCreateTagElement("Boosts"); }

    public static int count(ItemStack bag, BoostStat s, BoostTier t) { return bag.getTagElement("Boosts") == null ? 0 : bag.getTagElement("Boosts").getInt(StatBoosts.name(s, t)); }

    public static void add(ItemStack bag, BoostStat s, BoostTier t, int n) {
        String k = StatBoosts.name(s, t);
        long v = (long) tag(bag).getInt(k) + n;
        if (v <= 0) tag(bag).remove(k); else tag(bag).putInt(k, (int) Math.min(Integer.MAX_VALUE, v));
    }

    public static int total(ItemStack bag) {
        CompoundTag t = bag.getTagElement("Boosts");
        if (t == null) return 0;
        long n = 0;
        for (String k : t.getAllKeys()) n += t.getInt(k);
        return (int) Math.min(Integer.MAX_VALUE, n);
    }

    public static int[] counts(ItemStack bag) {
        int[] c = new int[kinds()];
        for (BoostStat s : BoostStat.values()) for (BoostTier t : BoostTier.values()) c[index(s, t)] = count(bag, s, t);
        return c;
    }

    /** The first Boost Bag you're carrying (hands first), or empty. */
    public static ItemStack find(Player p) {
        if (p.getMainHandItem().is(ITEM.get())) return p.getMainHandItem();
        if (p.getOffhandItem().is(ITEM.get())) return p.getOffhandItem();
        for (ItemStack s : p.getInventory().items) if (s.is(ITEM.get())) return s;
        return ItemStack.EMPTY;
    }

    /** A boost you're getting: into your bag if you carry one. True if it went in. */
    public static boolean collect(Player p, ItemStack boost) {
        if (!(boost.getItem() instanceof StatBoostItem b) || boost.isEmpty()) return false;
        ItemStack bag = find(p);
        if (bag.isEmpty()) return false;
        add(bag, b.stat, b.tier, boost.getCount());
        collected += boost.getCount();
        return true;
    }

    /** (Tests) boosts that went into a bag. */
    public static int collected;

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onPickup(EntityItemPickupEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || e.getItem().hasPickUpDelay()) return;
        ItemStack s = e.getItem().getItem();
        if (!(s.getItem() instanceof StatBoostItem)) return;
        if (!collect(p, s)) return;
        p.take(e.getItem(), s.getCount());
        e.getItem().discard();
        e.setCanceled(true);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BUNDLE_INSERT, SoundSource.PLAYERS, 0.8F, 1.1F);
    }

    /** Puts every boost in your inventory into the bag. How many went in. */
    public static int depositAll(ServerPlayer p, ItemStack bag) {
        int n = 0;
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.getItem() instanceof StatBoostItem b) { add(bag, b.stat, b.tier, s.getCount()); n += s.getCount(); inv.setItem(i, ItemStack.EMPTY); }
        }
        return n;
    }

    /** Takes up to n of a kind out into your inventory (the rest at your feet). */
    public static int take(ServerPlayer p, ItemStack bag, BoostStat s, BoostTier t, int n) {
        int have = count(bag, s, t), out = Math.min(have, Math.max(1, n));
        if (out <= 0) return 0;
        add(bag, s, t, -out);
        int left = out;
        while (left > 0) {
            ItemStack st = StatBoosts.stack(s, t);
            st.setCount(Math.min(left, st.getMaxStackSize()));
            left -= st.getCount();
            if (!p.getInventory().add(st)) p.drop(st, false);
        }
        return out;
    }

    /** Uses boosts of a kind straight from the bag (one, or all of them). Levels gained. */
    public static int use(ServerPlayer p, ItemStack bag, BoostStat s, BoostTier t, boolean all) {
        if (!s.usable(p)) { p.displayClientMessage(Component.literal(s.whoCanUse()).withStyle(ChatFormatting.RED), true); return 0; }
        int uses = all ? count(bag, s, t) : Math.min(1, count(bag, s, t));
        int used = 0, gained = 0;
        for (int i = 0; i < uses; i++) {
            int g = StatBoosts.apply(p, s, t.levels);
            if (g <= 0) break;
            gained += g;
            used++;
        }
        if (used == 0) {
            p.displayClientMessage(Component.literal(uses == 0 ? "None of those in the bag" : s.label + " is already at its max (Lv " + String.format("%,d", s.max()) + ").").withStyle(ChatFormatting.RED), true);
            return 0;
        }
        add(bag, s, t, -used);
        p.sendSystemMessage(Component.literal("+" + gained + " " + s.label + "! ").withStyle(t.color)
                .append(Component.literal(s.label + " is now Lv " + String.format("%,d", s.level(p)) + (used > 1 ? "  (" + used + " boosts)" : "")).withStyle(ChatFormatting.WHITE)));
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8F, 1.1F + t.ordinal() * 0.1F);
        return gained;
    }

    public static void open(ServerPlayer p, ItemStack bag) {
        ModNetwork.sendToPlayer(new BoostBagPacket(BoostBagPacket.OPEN, -1, 0, counts(bag)), p);
    }

    /** The bag screen's buttons. */
    public static void act(ServerPlayer p, int action, int kind, int n) {
        ItemStack bag = find(p);
        if (bag.isEmpty()) return;
        if (kind >= kinds()) return;
        BoostStat s = kind >= 0 ? statOf(kind) : null;
        BoostTier t = kind >= 0 ? tierOf(kind) : null;
        switch (action) {
            case BoostBagPacket.TAKE -> { if (s != null) take(p, bag, s, t, n); }
            case BoostBagPacket.USE -> { if (s != null) use(p, bag, s, t, n > 1); }
            case BoostBagPacket.DEPOSIT -> {
                int d = depositAll(p, bag);
                p.displayClientMessage(Component.literal(d == 0 ? "No boosts in your inventory" : d + " boosts went into the bag").withStyle(ChatFormatting.GOLD), true);
            }
            default -> { return; }
        }
        open(p, bag);
    }

    public static class BagItem extends Item {
        public BagItem(Properties props) { super(props); }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack st = player.getItemInHand(hand);
            if (!level.isClientSide && player instanceof ServerPlayer p) {
                int d = depositAll(p, st);
                if (d > 0) p.displayClientMessage(Component.literal(d + " boosts went into the bag").withStyle(ChatFormatting.GOLD), true);
                open(p, st);
            }
            return InteractionResultHolder.sidedSuccess(st, level.isClientSide);
        }

        @Override
        public void appendHoverText(ItemStack stack, Level level, List<Component> tip, TooltipFlag flag) {
            tip.add(Component.literal(String.format("%,d", total(stack)) + " Stat Boosts inside").withStyle(ChatFormatting.GOLD));
            tip.add(Component.literal("Boosts you pick up go in by themselves.").withStyle(ChatFormatting.GRAY));
            tip.add(Component.literal("Right-click: open it (your loose boosts go in too).").withStyle(ChatFormatting.GRAY));
        }

        @Override public boolean isFoil(ItemStack stack) { return total(stack) >= 100; }
    }
}

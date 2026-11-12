package com.xenowarriors.compat;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.bot.PartyBotEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The party's DragonMineZ mission bag: items your missions (and your bot teammates' missions) still need go here
 * instead of filling anyone's inventory - drops from whatever you or your bots kill, and those things when you pick
 * them up. 216 slots (four pages of 54). You can take things out (main menu > Party > Mission Bag, or
 * /xeno missionbag) but not put anything in. What's in it counts for the missions like it was in your inventory
 * (DmzItemMissions). One bag per party (kept under its leader), the same whichever character you play; joining a
 * party pours your bag into the party's. Saved with the world. DragonMineZ only.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MissionBag {
    private MissionBag() {}

    public static final int PAGE = 54, PAGES = 4, SIZE = PAGE * PAGES;
    private static final String KEY = "xw_mission_bag";
    private static final Map<UUID, Bag> BAGS = new HashMap<>();
    private static final Map<UUID, Wanted> WANTED = new HashMap<>();
    private record Wanted(long at, Set<String> ids) {}
    private static Boolean loaded;

    /** (For the tests) how many stacks went into a bag instead of an inventory. */
    public static int stored;

    public static boolean on() {
        if (loaded == null) loaded = ModList.get() != null && ModList.get().isLoaded("dragonminez");
        return loaded;
    }

    /**
     * A bag: one per party, kept under the party leader (or the player alone) and saved with the world, so it's the
     * same whichever character you play. Joining a party pours your own bag into the party's.
     */
    public static final class Bag extends SimpleContainer {
        private final MinecraftServer server;
        final UUID owner;
        private boolean loading;

        Bag(MinecraftServer server, UUID owner) { super(SIZE); this.server = server; this.owner = owner; }

        @Override
        public void setChanged() {
            super.setChanged();
            if (!loading) save(server, this);
        }

        @Override public boolean canPlaceItem(int slot, ItemStack stack) { return false; }   // (taking out only)
    }

    /** Every bag, saved with the world (bag owner -> items). */
    public static final class Store extends net.minecraft.world.level.saveddata.SavedData {
        final CompoundTag bags = new CompoundTag();
        static Store load(CompoundTag t) { Store s = new Store(); s.bags.merge(t.getCompound("bags")); return s; }
        @Override public CompoundTag save(CompoundTag t) { t.put("bags", bags.copy()); return t; }
    }

    private static Store store(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(Store::load, Store::new, "xw_mission_bags");
    }

    private static CompoundTag persisted(Player p) { return p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG); }

    private static ListTag write(SimpleContainer bag) {
        ListTag list = new ListTag();
        for (int i = 0; i < bag.getContainerSize(); i++) {
            ItemStack s = bag.getItem(i);
            if (s.isEmpty()) continue;
            CompoundTag t = new CompoundTag();
            t.putShort("Slot", (short) i);
            s.save(t);
            list.add(t);
        }
        return list;
    }

    private static void save(MinecraftServer server, Bag bag) {
        Store st = store(server);
        ListTag list = write(bag);
        if (list.isEmpty()) st.bags.remove(bag.owner.toString()); else st.bags.put(bag.owner.toString(), list);
        st.setDirty();
    }

    /** The bag kept under this id (loaded the first time). */
    private static Bag bag(MinecraftServer server, UUID owner) {
        Bag bag = BAGS.get(owner);
        if (bag != null) return bag;
        bag = new Bag(server, owner);
        bag.loading = true;
        ListTag list = store(server).bags.getList(owner.toString(), Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            int slot = t.getShort("Slot");
            if (slot >= 0 && slot < SIZE) bag.setItem(slot, ItemStack.of(t));
        }
        bag.loading = false;
        BAGS.put(owner, bag);
        return bag;
    }

    /** The bag this player uses: their party's (kept under its leader), or their own. */
    public static Bag of(ServerPlayer p) {
        migrate(p);
        return bag(p.server, com.xenowarriors.party.Parties.leaderId(p));
    }

    /** An old per-player bag (kept in the player's own save) moves into the world's store, once. */
    private static void migrate(ServerPlayer p) {
        CompoundTag per = persisted(p);
        if (!per.contains(KEY)) return;
        ListTag list = per.getList(KEY, Tag.TAG_COMPOUND);
        per.remove(KEY);
        p.getPersistentData().put(Player.PERSISTED_NBT_TAG, per);
        Bag own = bag(p.server, p.getUUID());
        for (int i = 0; i < list.size(); i++) addTo(own, ItemStack.of(list.getCompound(i)));
        own.setChanged();
    }

    /** They joined a party: their own bag is poured into the party's (what doesn't fit stays in theirs). */
    public static void merge(ServerPlayer p) {
        if (!on()) return;
        migrate(p);
        UUID lead = com.xenowarriors.party.Parties.leaderId(p);
        if (lead.equals(p.getUUID())) return;
        Bag own = bag(p.server, p.getUUID()), party = bag(p.server, lead);
        int moved = 0;
        for (int i = 0; i < own.getContainerSize(); i++) {
            ItemStack s = own.getItem(i);
            if (s.isEmpty()) continue;
            ItemStack left = addTo(party, s);
            moved += s.getCount() - left.getCount();
            own.setItem(i, left);
        }
        own.setChanged(); party.setChanged();
        if (moved > 0) for (ServerPlayer m : com.xenowarriors.party.Parties.members(p))
            m.displayClientMessage(Component.literal(p.getName().getString() + "'s Mission Bag joined the party's (" + moved + " items)").withStyle(ChatFormatting.GOLD), false);
    }

    private static ItemStack addTo(SimpleContainer bag, ItemStack stack) {
        ItemStack left = stack.copy();
        for (int i = 0; i < SIZE && !left.isEmpty(); i++) {
            ItemStack s = bag.getItem(i);
            if (!s.isEmpty() && ItemStack.isSameItemSameTags(s, left) && s.getCount() < s.getMaxStackSize()) {
                int n = Math.min(left.getCount(), s.getMaxStackSize() - s.getCount());
                s.grow(n);
                left.shrink(n);
            }
        }
        for (int i = 0; i < SIZE && !left.isEmpty(); i++) if (bag.getItem(i).isEmpty()) { bag.setItem(i, left.copy()); left = ItemStack.EMPTY; }
        return left;
    }

    /** Puts a stack into the bag (stacking first); returns what didn't fit. */
    public static ItemStack add(ServerPlayer p, ItemStack stack) {
        Bag bag = of(p);
        ItemStack left = addTo(bag, stack);
        bag.setChanged();
        return left;
    }

    /** Is this item one your (or your bots') missions still need? (Checked once a second at most.) */
    public static boolean wanted(ServerPlayer p, ItemStack s) {
        if (!on() || s.isEmpty() || p instanceof FakePlayer) return false;
        long now = p.server.getTickCount();
        Wanted w = WANTED.get(p.getUUID());
        if (w == null || now - w.at > 20) {
            w = new Wanted(now, DmzBotQuests.wantedItems(p));
            WANTED.put(p.getUUID(), w);
        }
        return !w.ids.isEmpty() && w.ids.contains(BuiltInRegistries.ITEM.getKey(s.getItem()).toString());
    }

    /** Whose party gets a kill's drops: the player who killed it, or a bot teammate's owner. */
    private static ServerPlayer killer(Entity e) {
        if (e instanceof ServerPlayer sp && !(sp instanceof FakePlayer)) return sp;
        if (e instanceof PartyBotEntity b) return b.owner();
        return null;
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onDrops(LivingDropsEvent event) {
        if (!on() || event.getEntity().level().isClientSide) return;
        ServerPlayer p = killer(event.getSource().getEntity());
        if (p == null) return;
        int took = 0;
        var it = event.getDrops().iterator();
        while (it.hasNext()) {
            ItemEntity drop = it.next();
            ItemStack s = drop.getItem();
            if (!wanted(p, s)) continue;
            ItemStack left = add(p, s);
            took += s.getCount() - left.getCount();
            if (left.isEmpty()) it.remove(); else drop.setItem(left);
        }
        if (took > 0) told(p, took);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onPickup(EntityItemPickupEvent event) {
        if (!on() || event.isCanceled() || !(event.getEntity() instanceof ServerPlayer p) || p instanceof FakePlayer) return;
        ItemEntity e = event.getItem();
        ItemStack s = e.getItem();
        if (!wanted(p, s)) return;
        ItemStack left = add(p, s);
        int took = s.getCount() - left.getCount();
        if (took <= 0) return;
        if (left.isEmpty()) { e.discard(); event.setCanceled(true); }
        else e.setItem(left);
        p.take(e, took);
        told(p, took);
    }

    private static void told(ServerPlayer p, int n) {
        stored++;
        p.displayClientMessage(Component.literal("+" + n + " to the Mission Bag").withStyle(ChatFormatting.GOLD), true);
        p.level().playSound(null, p.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.3F, 1.4F);
    }

    /** Opens your mission bag. */
    public static void open(ServerPlayer p) {
        if (!on()) { p.displayClientMessage(Component.literal("The Mission Bag is for DragonMineZ missions").withStyle(ChatFormatting.GRAY), true); return; }
        Bag bag = of(p);
        net.minecraftforge.network.NetworkHooks.openScreen(p, new SimpleMenuProvider(
                (id, inv, pl) -> new MissionBagMenu(id, inv, bag), Component.literal("Mission Bag")));
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) WANTED.remove(p.getUUID());   // (bags are saved with the world on every change)
    }

    @SubscribeEvent
    public static void onServerStop(net.minecraftforge.event.server.ServerStoppedEvent event) { BAGS.clear(); WANTED.clear(); }
}

package com.xenowarriors.collection;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.network.ModNetwork;
import com.xenowarriors.stats.Stat;
import com.xenowarriors.stats.Stats;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

/**
 * The Collection (the book at the Mountain Village Element School): every hostile enemy and how many you've defeated,
 * up to 1000 each. Every 100 defeats: a random stat boost (a level in Speed, Attack or Defense - and with DragonMineZ,
 * +2 to a random DragonMineZ stat too). Your pets' and bots' kills count for you.
 * Opening the book once adds the Collection (and Tower mode) to your menu. Kills made in the Tower only count once a
 * Tower checkpoint (every 10 floors) saves them.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Collection {
    public static final String KEY = "xw_collection", TOTAL = "xw_collection_total", SEEN = "xw_collection_seen", BEST = "xw_tower_best";
    public static final int EVERY = 100, MAX = 1000;

    private Collection() {}

    private static CompoundTag root(Player p) { return p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG); }

    public static CompoundTag kills(Player p) { return root(p).getCompound(KEY); }

    public static int total(Player p) { return root(p).getInt(TOTAL); }

    public static boolean unlocked(Player p) { return root(p).getBoolean(SEEN); }

    public static int count(Player p, String id) { return kills(p).getInt(id); }

    /** Who gets the credit for this kill (bots and pets count for their owner). */
    public static ServerPlayer credit(Object src) {
        var bot = com.xenowarriors.bot.PartyBots.botOf(src instanceof net.minecraft.world.entity.Entity e ? e : null);
        if (bot != null) return bot.owner();
        if (src instanceof net.minecraftforge.common.util.FakePlayer) return null;
        if (src instanceof ServerPlayer sp) return sp;
        if (src instanceof OwnableEntity o && o.getOwner() instanceof ServerPlayer sp) return sp;
        if (src instanceof net.minecraft.world.entity.Entity e) {
            java.util.UUID owner = com.xenowarriors.party.Parties.petOwner(e);
            if (owner != null && e.level().getPlayerByUUID(owner) instanceof ServerPlayer sp) return sp;
        }
        return null;
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (dead.level().isClientSide || !Catalog.hostile(dead)) return;
        ServerPlayer p = credit(event.getSource().getEntity());
        if (p == null) return;
        var id = ForgeRegistries.ENTITY_TYPES.getKey(dead.getType());
        if (id == null) return;
        if (com.xenowarriors.tower.Tower.pending(p, id.toString())) return;     // in the Tower: held until a checkpoint
        add(p, id.toString(), 1);
    }

    /** Adds kills (capped at 1000 per enemy); returns how many counted. */
    public static int add(ServerPlayer p, String id, int n) {
        CompoundTag root = root(p);
        CompoundTag k = root.getCompound(KEY);
        int had = k.getInt(id), now = Math.min(MAX, had + n), added = now - had;
        if (added <= 0) return 0;
        k.putInt(id, now);
        root.put(KEY, k);
        int before = root.getInt(TOTAL), total = before + added;
        root.putInt(TOTAL, total);
        p.getPersistentData().put(Player.PERSISTED_NBT_TAG, root);
        for (int t = (before / EVERY + 1) * EVERY; t <= total; t += EVERY) reward(p, t);
        if (now == MAX) {
            String name = net.minecraft.world.entity.EntityType.byString(id).map(t -> t.getDescription().getString()).orElse(id);
            p.sendSystemMessage(Component.literal("Collection complete: " + name + " (1000/1000)!").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
        }
        return added;
    }

    private static void reward(ServerPlayer p, int total) {
        var r = p.getRandom();
        Stat stat = Stat.values()[r.nextInt(Stat.values().length)];
        int lv = Stats.level(p, stat);
        Stats.addXp(p, stat, stat.needed(lv) + 1);
        String extra = "";
        String dmz = com.xenowarriors.compat.DmzKiTraining.DMZ_STATS[r.nextInt(com.xenowarriors.compat.DmzKiTraining.DMZ_STATS.length)];
        if (com.xenowarriors.compat.DmzKiTraining.bonus(p, dmz, 2, "Collection bonus: +2 " + dmz)) extra = " and +2 " + dmz;
        p.sendSystemMessage(Component.literal(total + " enemies defeated! Collection bonus: +1 " + stat.name().charAt(0) + stat.name().substring(1).toLowerCase() + " level" + extra)
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.8F, 1.3F);
    }

    /** From the book on its stand: the first time unlocks the Collection + Tower in your menu. */
    public static void openFromBook(ServerPlayer p) {
        if (!unlocked(p)) {
            CompoundTag root = root(p);
            root.putBoolean(SEEN, true);
            p.getPersistentData().put(Player.PERSISTED_NBT_TAG, root);
            p.sendSystemMessage(Component.literal("Your Collection is now in your menu - with Tower mode!").withStyle(ChatFormatting.AQUA));
        }
        open(p, true);
    }

    /** Sends the Collection (show = open the screen; otherwise just lets the menu know it's unlocked). */
    public static void open(ServerPlayer p, boolean show) {
        if (show && !unlocked(p)) {
            p.displayClientMessage(Component.literal("Read the Collection Book at the Mountain Village Element School first").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        List<String> ids = show ? Catalog.all(p.serverLevel()) : List.of();
        CompoundTag k = kills(p);
        int[] counts = new int[ids.size()];
        for (int i = 0; i < ids.size(); i++) counts[i] = k.getInt(ids.get(i));
        ModNetwork.sendToPlayer(new com.xenowarriors.network.CollectionPacket(ids.toArray(new String[0]), counts, total(p), show, unlocked(p),
                com.xenowarriors.tower.Tower.floorOf(p), root(p).getInt(BEST)), p);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && unlocked(p)) open(p, false);
    }

    public static void setBest(ServerPlayer p, int floor) {
        CompoundTag root = root(p);
        if (floor > root.getInt(BEST)) { root.putInt(BEST, floor); p.getPersistentData().put(Player.PERSISTED_NBT_TAG, root); }
    }
}

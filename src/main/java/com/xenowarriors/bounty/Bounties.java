package com.xenowarriors.bounty;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.network.ModNetwork;
import com.xenowarriors.stats.Stat;
import com.xenowarriors.stats.Stats;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/**
 * Bounty boards (village plazas, or craft your own): three bounties a day - "defeat N of these" - with emeralds,
 * stat XP and sometimes something rare for turning them in. Season-locked enemies only show up once that season is.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Bounties {
    private static final String KEY = "xw_bounties", DAY = "xw_bounty_day";
    /** id, season, tier (1 easy - 3 hard), min, max count. */
    private record Target(String id, int season, int tier, int min, int max) {}
    private static final Target[] TARGETS = {
            new Target("minecraft:zombie", 1, 1, 8, 16), new Target("minecraft:skeleton", 1, 1, 6, 12), new Target("minecraft:spider", 1, 1, 6, 12),
            new Target("minecraft:creeper", 1, 2, 4, 8), new Target("minecraft:husk", 1, 1, 6, 10), new Target("minecraft:drowned", 1, 2, 5, 10),
            new Target("minecraft:witch", 1, 2, 2, 4), new Target("minecraft:pillager", 1, 2, 4, 8), new Target("minecraft:enderman", 1, 3, 3, 6),
            new Target("minecraft:blaze", 1, 3, 4, 8), new Target("xenowarriors:corrupted_soul", 1, 2, 5, 10), new Target("xenowarriors:dark_possessed", 1, 3, 2, 5),
            new Target("xenowarriors:corrupted_villager", 1, 2, 3, 6), new Target("xenowarriors:gov_agent", 2, 3, 3, 6), new Target("xenowarriors:order_assassin", 3, 3, 2, 4),
    };

    private Bounties() {}

    private static CompoundTag root(Player p) { return p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG); }

    private static ListTag list(ServerPlayer p) {
        CompoundTag root = root(p);
        long day = p.serverLevel().getDayTime() / 24000L;
        if (!root.contains(KEY) || root.getLong(DAY) != day) {           // a fresh set every Minecraft day
            ListTag l = new ListTag();
            List<Target> pool = new ArrayList<>();
            int season = com.xenowarriors.character.Seasons.highest(p.server);
            for (Target t : TARGETS) if (t.season <= season && ForgeRegistries.ENTITY_TYPES.containsKey(new ResourceLocation(t.id))) pool.add(t);
            var r = p.getRandom();
            for (int i = 0; i < 3 && !pool.isEmpty(); i++) {
                int want = i + 1;                                         // one easy, one medium, one hard if it can
                List<Target> tier = pool.stream().filter(t -> t.tier == want).toList();
                Target t = (tier.isEmpty() ? pool : tier).get(r.nextInt((tier.isEmpty() ? pool : tier).size()));
                pool.remove(t);
                CompoundTag b = new CompoundTag();
                b.putString("id", t.id); b.putInt("need", t.min + r.nextInt(t.max - t.min + 1)); b.putInt("have", 0); b.putInt("tier", t.tier); b.putBoolean("claimed", false);
                l.add(b);
            }
            root.put(KEY, l); root.putLong(DAY, day);
            p.getPersistentData().put(Player.PERSISTED_NBT_TAG, root);
        }
        return root.getList(KEY, 10);
    }

    private static void save(ServerPlayer p, ListTag l) {
        CompoundTag root = root(p); root.put(KEY, l); p.getPersistentData().put(Player.PERSISTED_NBT_TAG, root);
    }

    public static void open(ServerPlayer p) {
        ListTag l = list(p);
        int n = l.size();
        String[] ids = new String[n]; int[] need = new int[n], have = new int[n], tier = new int[n]; boolean[] claimed = new boolean[n];
        for (int i = 0; i < n; i++) {
            CompoundTag b = l.getCompound(i);
            ids[i] = b.getString("id"); need[i] = b.getInt("need"); have[i] = b.getInt("have"); tier[i] = b.getInt("tier"); claimed[i] = b.getBoolean("claimed");
        }
        long left = 24000L - p.serverLevel().getDayTime() % 24000L;
        ModNetwork.sendToPlayer(new com.xenowarriors.network.BountyPacket(ids, need, have, tier, claimed, (int) (left / 20)), p);
    }

    public static void claim(ServerPlayer p, int i) {
        ListTag l = list(p);
        if (i < 0 || i >= l.size()) return;
        CompoundTag b = l.getCompound(i);
        if (b.getBoolean("claimed") || b.getInt("have") < b.getInt("need")) return;
        b.putBoolean("claimed", true);
        save(p, l);
        int tier = b.getInt("tier");
        var r = p.getRandom();
        give(p, new ItemStack(Items.EMERALD, 3 + tier * 4 + r.nextInt(4)));
        Stat stat = Stat.values()[r.nextInt(Stat.values().length)];
        Stats.addXp(p, stat, stat.needed(Stats.level(p, stat)) * (0.25F * tier));
        String extra = "";
        if (tier >= 3 || r.nextInt(4) == 0) {
            var item = ForgeRegistries.ITEMS.getValue(XenoWarriors.id(r.nextBoolean() ? "karma_potion" : "data_shard"));
            if (item != null && item != Items.AIR) { give(p, new ItemStack(item)); extra = " + " + new ItemStack(item).getHoverName().getString(); }
        }
        p.sendSystemMessage(Component.literal("Bounty complete! Emeralds, " + stat.name().charAt(0) + stat.name().substring(1).toLowerCase() + " XP" + extra).withStyle(ChatFormatting.GOLD));
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.7F, 1.4F);
        open(p);
    }

    private static void give(ServerPlayer p, ItemStack s) { if (!p.getInventory().add(s)) p.drop(s, false); }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide) return;
        ServerPlayer p = com.xenowarriors.collection.Collection.credit(event.getSource().getEntity());
        if (p == null) return;
        CompoundTag root = root(p);
        if (!root.contains(KEY)) return;
        var id = ForgeRegistries.ENTITY_TYPES.getKey(event.getEntity().getType());
        if (id == null) return;
        ListTag l = list(p);
        boolean changed = false;
        for (int i = 0; i < l.size(); i++) {
            CompoundTag b = l.getCompound(i);
            if (!b.getString("id").equals(id.toString()) || b.getBoolean("claimed") || b.getInt("have") >= b.getInt("need")) continue;
            b.putInt("have", b.getInt("have") + 1);
            changed = true;
            if (b.getInt("have") == b.getInt("need"))
                p.displayClientMessage(Component.literal("Bounty done - turn it in at a Bounty Board!").withStyle(ChatFormatting.GOLD), true);
        }
        if (changed) save(p, l);
    }
}

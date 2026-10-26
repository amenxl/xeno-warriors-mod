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
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * The Collection (the book at the Mountain Village Element School): every kind of enemy you've defeated and how many.
 * Every 100 defeats: a random stat boost (a level in Speed, Attack or Defense - and with DragonMineZ, +2 to a random
 * DragonMineZ stat too). Your pets' kills count for you.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Collection {
    public static final String KEY = "xw_collection", TOTAL = "xw_collection_total";
    public static final int EVERY = 100;

    private Collection() {}

    private static CompoundTag root(Player p) { return p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG); }

    public static CompoundTag kills(Player p) { return root(p).getCompound(KEY); }

    public static int total(Player p) { return root(p).getInt(TOTAL); }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (dead.level().isClientSide || dead instanceof Player) return;
        ServerPlayer p = null;
        var src = event.getSource().getEntity();
        var bot = com.xenowarriors.bot.PartyBots.botOf(src);
        if (bot != null) p = bot.owner();                                   // bot teammates' kills count for their owner
        else if (src instanceof net.minecraftforge.common.util.FakePlayer) return;
        else if (src instanceof ServerPlayer sp) p = sp;
        else if (src instanceof OwnableEntity o && o.getOwner() instanceof ServerPlayer sp) p = sp;
        if (p == null) return;
        var id = ForgeRegistries.ENTITY_TYPES.getKey(dead.getType());
        if (id == null) return;
        CompoundTag root = root(p);
        CompoundTag k = root.getCompound(KEY);
        k.putInt(id.toString(), k.getInt(id.toString()) + 1);
        root.put(KEY, k);
        int total = root.getInt(TOTAL) + 1;
        root.putInt(TOTAL, total);
        p.getPersistentData().put(Player.PERSISTED_NBT_TAG, root);
        if (total % EVERY == 0) reward(p, total);
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

    /** Opens the book for this player. */
    public static void open(ServerPlayer p) {
        CompoundTag k = kills(p);
        String[] ids = k.getAllKeys().toArray(new String[0]);
        int[] counts = new int[ids.length];
        for (int i = 0; i < ids.length; i++) counts[i] = k.getInt(ids[i]);
        ModNetwork.sendToPlayer(new com.xenowarriors.network.CollectionPacket(ids, counts, total(p)), p);
    }
}

package com.xenowarriors.bot;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.home.HomeChestBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Taking loot home: when a bot's bag is nearly full (three free slots or fewer), it takes what it doesn't need -
 * ore, blocks, mob drops; never its food, gear, potions, torches, arrows or Xeno Warriors items - to the nearest Home
 * Chest you placed in that world. Within {@value #WALK} blocks it walks over and puts it in (an errand, when it's
 * calm); further off, it packs it up and sends it home there and then. Full chest: it tells you.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BotHome {
    private BotHome() {}

    public static final int WALK = 48, NEARLY_FULL = 3;
    private static final String CHESTS = "xw_home_chests", NEXT = "xw_home_next", TOLD = "xw_home_told";
    /** (Tests) loads walked over / sent home; stacks moved. */
    public static int walks, sends, moved;

    // ---------------------------------------------------------------- your Home Chests

    private static CompoundTag persisted(Player p) { return p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG); }

    /** A Home Chest you placed: remembered (by world) for your bots. */
    public static void placed(Player p, Level level, BlockPos pos) {
        CompoundTag pers = persisted(p);
        ListTag l = pers.getList(CHESTS, 10);
        String dim = level.dimension().location().toString();
        for (int i = 0; i < l.size(); i++) if (l.getCompound(i).getString("d").equals(dim) && l.getCompound(i).getLong("p") == pos.asLong()) return;
        CompoundTag c = new CompoundTag();
        c.putString("d", dim); c.putLong("p", pos.asLong());
        l.add(c);
        pers.put(CHESTS, l);
        p.getPersistentData().put(Player.PERSISTED_NBT_TAG, pers);
    }

    /** The nearest of your Home Chests in this bot's world (stale ones - broken, replaced - are forgotten), or null. */
    static HomeChestBlockEntity nearest(ServerPlayer o, PartyBotEntity b) { return nearest(o, b, Double.MAX_VALUE); }

    /** ...within this distance (farther ones aren't even looked at - their chunks stay as they are). */
    static HomeChestBlockEntity nearest(ServerPlayer o, PartyBotEntity b, double within) {
        if (!(b.level() instanceof ServerLevel level)) return null;
        CompoundTag pers = persisted(o);
        ListTag l = pers.getList(CHESTS, 10);
        String dim = level.dimension().location().toString();
        HomeChestBlockEntity best = null; double bd = Double.MAX_VALUE;
        for (int i = l.size() - 1; i >= 0; i--) {
            CompoundTag c = l.getCompound(i);
            if (!c.getString("d").equals(dim)) continue;
            BlockPos pos = BlockPos.of(c.getLong("p"));
            if (!level.isInWorldBounds(pos)) { l.remove(i); continue; }
            if (pos.distSqr(b.blockPosition()) > within * within) continue;
            if (level.getBlockEntity(pos) instanceof HomeChestBlockEntity be && o.getUUID().equals(be.owner())) {
                double d = pos.distSqr(b.blockPosition());
                if (d < bd) { bd = d; best = be; }
            } else l.remove(i);                                                     // (not there any more)
        }
        pers.put(CHESTS, l);
        o.getPersistentData().put(Player.PERSISTED_NBT_TAG, pers);
        return best;
    }

    // ---------------------------------------------------------------- what goes home

    /** Loot it can do without (it keeps food, gear, potions, torches, arrows, shields and anything of ours). */
    static boolean loot(ItemStack s) {
        if (s.isEmpty() || s.isEdible() || s.isDamageableItem() || s.getItem() instanceof ArmorItem || s.getItem() instanceof PotionItem) return false;
        if (s.is(Items.TORCH) || s.is(ItemTags.ARROWS) || s.is(Items.TOTEM_OF_UNDYING) || s.is(Items.CRAFTING_TABLE) || s.is(Items.ENDER_PEARL)) return false;
        if (com.xenowarriors.compat.DmzBotRadar.isRadar(s)) return false;
        var key = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(s.getItem());
        return key == null || !key.getNamespace().equals(XenoWarriors.MOD_ID);
    }

    static boolean nearlyFull(PartyBotEntity b) { return BotCrafting.freeSlots(b.inventory) <= NEARLY_FULL; }

    static boolean hasLoot(PartyBotEntity b) {
        for (int i = 0; i < b.inventory.getContainerSize(); i++) if (loot(b.inventory.getItem(i))) return true;
        return false;
    }

    /** Its loot into the chest: stacks moved (what doesn't fit stays in its bag). */
    static int deposit(PartyBotEntity b, ServerPlayer o, HomeChestBlockEntity chest) {
        int n = 0;
        for (int i = 0; i < b.inventory.getContainerSize(); i++) {
            ItemStack s = b.inventory.getItem(i);
            if (!loot(s)) continue;
            ItemStack rest = chest.insert(s);
            if (rest.getCount() < s.getCount()) n++;
            b.inventory.setItem(i, rest);
            if (!rest.isEmpty()) break;                                              // (the chest is full)
        }
        moved += n;
        return n;
    }

    // ---------------------------------------------------------------- walking it over (an errand)

    /** A Home Chest close enough to walk to, when its bag is nearly full of loot (BotErrands asks). */
    static BlockPos walkTo(PartyBotEntity b) {
        ServerPlayer o = b.owner();
        if (o == null || b.journey() || !nearlyFull(b) || !hasLoot(b)) return null;
        HomeChestBlockEntity c = nearest(o, b, WALK);
        if (c == null) return null;
        return c.getBlockPos();
    }

    /** At the chest (the errand's end). */
    static void arrived(PartyBotEntity b, ServerPlayer o, BlockPos pos) {
        if (!(b.level().getBlockEntity(pos) instanceof HomeChestBlockEntity c)) return;
        int n = deposit(b, o, c);
        b.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        b.level().playSound(null, pos, SoundEvents.CHEST_CLOSE, SoundSource.BLOCKS, 0.7F, 1F);
        walks++;
        tell(o, b, n > 0 ? "put " + n + " stack" + (n == 1 ? "" : "s") + " of loot in the Home Chest" : "found the Home Chest full");
    }

    // ---------------------------------------------------------------- further off: sent home

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer o) || o instanceof FakePlayer || o.tickCount % 100 != 37) return;
        long now = o.level().getGameTime();
        for (PartyBotEntity b : PartyBots.bots(o)) {
            if (b.journey() || b.level() != o.level() || b.errand != null || !nearlyFull(b) || !hasLoot(b)) continue;
            if (now < b.getPersistentData().getLong(NEXT) || BotErrands.danger(b) != null) continue;
            b.getPersistentData().putLong(NEXT, now + 20 * 60);                        // (once a minute at most)
            HomeChestBlockEntity c = nearest(o, b);
            if (c == null) {                                                            // no Home Chest: a word now and then
                if (now - o.getPersistentData().getLong(TOLD) > 20 * 60 * 15) {
                    o.getPersistentData().putLong(TOLD, now);
                    tell(o, b, "says: \"My bag's nearly full - craft a Home Chest (two chests and a compass) and I'll take my loot home to it.\"");
                }
                continue;
            }
            if (c.getBlockPos().distSqr(b.blockPosition()) <= WALK * WALK) continue;     // (close: it walks it over - see walkTo)
            int n = deposit(b, o, c);
            ((ServerLevel) b.level()).sendParticles(ParticleTypes.PORTAL, b.getX(), b.getY() + 1, b.getZ(), 20, 0.3, 0.5, 0.3, 0.2);
            b.level().playSound(null, b.blockPosition(), SoundEvents.BUNDLE_DROP_CONTENTS, SoundSource.NEUTRAL, 1F, 1F);
            sends++;
            int d = (int) Math.sqrt(c.getBlockPos().distSqr(b.blockPosition()));
            tell(o, b, n > 0 ? "sent " + n + " stack" + (n == 1 ? "" : "s") + " of loot home to the Home Chest (" + d + " blocks away)" : "couldn't send loot home: the Home Chest is full");
        }
    }

    private static void tell(ServerPlayer o, PartyBotEntity b, String s) {
        o.displayClientMessage(Component.empty().append(b.label()).append(" " + s).withStyle(ChatFormatting.GOLD), false);
        PartyBots.testLog(b, "home: " + s);
    }
}

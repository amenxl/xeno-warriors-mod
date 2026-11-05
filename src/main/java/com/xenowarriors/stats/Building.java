package com.xenowarriors.stats;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.bot.PartyBotEntity;
import com.xenowarriors.character.ModCapabilities;
import com.xenowarriors.character.PlayerCharacter;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Building: levels up from the blocks you place, for each character (like Appetite, kept in the player's save per
 * character). Separate from Speed/Attack/Defense, so it doesn't change the Character Level.
 *  - every {@link #LEVELS_PER_REACH} levels: +1 block of block reach (placing, breaking and using blocks - not
 *    attack reach), up to +{@link #MAX_REACH} blocks (level 80);
 *  - every {@link #LEVELS_PER_SAVE} levels: +0.1% chance a block you place isn't used up (survival only), up to
 *    {@link #MAX_SAVE} (50%, level 10,000).
 * XP: 1 for a normal block, 0.25 for one that breaks instantly (torches, flowers, seeds...). Placing at a spot you
 * (or that bot) already placed at in the last minute gives nothing, so place/break on one spot can't farm it.
 * <p>Bots have it too, per character in their owner's save (the same record as when the owner plays that character):
 * torches and Dragon Balls a bot places level its character's Building.
 * <p>The save is done in {@code BlockItemSaveMixin} around {@code BlockItem.place}: if exactly one item was used and
 * the roll succeeds it's put back before Forge's place event runs, so a cancelled placement (Forge restores the
 * stack it had before) can never dupe, and any mod's block item that goes through {@code BlockItem.place} gets it.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Building {
    public static final int MAX_LEVEL = Stat.MAX_LEVEL;
    public static final int LEVELS_PER_REACH = 10, MAX_REACH = 8;
    public static final int LEVELS_PER_SAVE = 20;
    public static final float SAVE_PER_STEP = 0.001F, MAX_SAVE = 0.5F;
    /** Ticks before the same spot gives XP again. */
    public static final int SAME_SPOT_COOLDOWN = 1200;
    private static final String KEY = "xw_building";
    private static final UUID REACH_ID = UUID.fromString("6f3c1f0e-8a3b-4c51-9a1e-5b7d2a9c0e21");
    private static final Map<UUID, LinkedHashMap<Long, Long>> RECENT = new HashMap<>();
    private static final ThreadLocal<ArrayDeque<Object[]>> PLACING = ThreadLocal.withInitial(ArrayDeque::new);

    /** Tests only: a fixed roll in place of the random one (null = random). */
    public static Float testRoll;
    /** Tests only: how many blocks were saved so far. */
    public static int testSaved;

    private Building() {}

    /** XP needed to go from this level to the next (level 10 is ~470 blocks, level 20 ~1,540, level 80 ~20,500). */
    public static float needed(int level) { return 20F + level * 6F; }

    /** Extra blocks of block reach at a level. */
    public static int reach(int level) { return Math.min(MAX_REACH, Math.max(0, level) / LEVELS_PER_REACH); }

    /** Chance (0..1) a placed block isn't used up at a level. */
    public static float saveChance(int level) { return Math.min(MAX_SAVE, (Math.max(0, level) / LEVELS_PER_SAVE) * SAVE_PER_STEP); }

    /** "0.1%", "2.5%", "50%". */
    public static String pct(float chance) {
        float v = chance * 100F;
        return (Math.abs(v - Math.round(v)) < 0.001F ? String.valueOf(Math.round(v)) : String.format(java.util.Locale.ROOT, "%.1f", v)) + "%";
    }

    // ---------------------------------------------------------------- storage (per character, in the player's save)

    private static CompoundTag root(Player p) {
        CompoundTag pers = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (!pers.contains(KEY)) { pers.put(KEY, new CompoundTag()); p.getPersistentData().put(Player.PERSISTED_NBT_TAG, pers); }
        return pers.getCompound(KEY);
    }

    private static String charKey(Player p) {
        return p.getCapability(ModCapabilities.PLAYER_CHARACTER).map(PlayerCharacter::key).orElse(ModCapabilities.getType(p).getId());
    }

    private static String botKey(PartyBotEntity b, ServerPlayer o) {
        return o.getCapability(ModCapabilities.PLAYER_CHARACTER).map(d -> d.key(b.character())).orElse(b.character().getId());
    }

    private static int clamp(int lv) { return Math.max(0, Math.min(MAX_LEVEL, lv)); }

    public static int level(Player p) { return clamp(root(p).getCompound(charKey(p)).getInt("lv")); }

    public static float progress(Player p) {
        int lv = level(p);
        return lv >= MAX_LEVEL ? 1F : Math.min(1F, root(p).getCompound(charKey(p)).getFloat("xp") / needed(lv));
    }

    /** Raw XP toward the next level (tests, commands). */
    public static float xp(Player p) { return root(p).getCompound(charKey(p)).getFloat("xp"); }

    public static void set(ServerPlayer p, int level, float xp) {
        CompoundTag r = root(p);
        CompoundTag c = r.getCompound(charKey(p));
        c.putInt("lv", clamp(level));
        c.putFloat("xp", Math.max(0F, xp));
        r.put(charKey(p), c);
        applyReach(p);
    }

    /** A bot's Building level (its character's, in its owner's save; 0 without its owner online). */
    public static int level(PartyBotEntity b) {
        ServerPlayer o = b.owner();
        return o == null ? 0 : clamp(root(o).getCompound(botKey(b, o)).getInt("lv"));
    }

    // ---------------------------------------------------------------- XP

    /** XP for placing this block: instant-break blocks (torches, flowers, seeds) give a quarter. */
    public static float xpFor(BlockState state, ServerLevel l, BlockPos pos) {
        if (state.isAir()) return 0F;
        return state.getDestroySpeed(l, pos) == 0F ? 0.25F : 1F;
    }

    /** True if this spot hasn't been placed at by {@code who} in the last minute (and remembers it now). */
    static boolean freshSpot(UUID who, ServerLevel l, BlockPos pos) {
        long now = l.getGameTime();
        long key = pos.asLong() * 31L + l.dimension().location().hashCode();
        if (RECENT.size() > 256 && !RECENT.containsKey(who)) RECENT.clear();     // (old bots' records: never grows forever)
        LinkedHashMap<Long, Long> m = RECENT.computeIfAbsent(who, k -> new LinkedHashMap<>(64, 0.75F, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<Long, Long> e) { return size() > 512; }
        });
        Long last = m.put(key, now);
        return last == null || now - last >= SAME_SPOT_COOLDOWN || now < last;
    }

    public static void addXp(ServerPlayer p, float amount) {
        if (amount <= 0 || p.isCreative() || p.isSpectator()) return;
        int lv = level(p);
        if (lv >= MAX_LEVEL) return;
        float xp = xp(p) + amount;
        int before = lv;
        while (lv < MAX_LEVEL && xp >= needed(lv)) { xp -= needed(lv); lv++; }
        if (lv >= MAX_LEVEL) xp = 0;
        set(p, lv, xp);
        if (lv > before) {
            boolean reachUp = reach(lv) > reach(before), saveUp = saveChance(lv) > saveChance(before);
            String extra = (reachUp ? " Block reach +" + reach(lv) + "!" : "") + (saveUp ? " " + pct(saveChance(lv)) + " chance to keep a placed block!" : "");
            p.displayClientMessage(Component.literal("Building level " + lv + "!" + extra).withStyle(ChatFormatting.GREEN), !(reachUp || saveUp));
            if (reachUp || saveUp) com.xenowarriors.network.PopupPacket.send(p, com.xenowarriors.network.PopupPacket.GOOD, p.getUUID(), ModCapabilities.getType(p).ordinal(), -1,
                    "Building level " + lv, reachUp ? "Block reach +" + reach(lv) : pct(saveChance(lv)) + " chance to keep blocks", "normal");
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1F, reachUp ? 0.9F : 1.3F);
        }
    }

    /** A bot placed a block: XP toward its character's Building (its owner hears when it goes up). */
    public static void botPlaced(PartyBotEntity b, BlockPos pos, BlockState state) {
        ServerPlayer o = b.owner();
        if (o == null || b.journey() || !(b.level() instanceof ServerLevel l)) return;
        if (!freshSpot(b.getUUID(), l, pos)) return;
        addXp(b, o, xpFor(state, l, pos));
    }

    public static void addXp(PartyBotEntity b, ServerPlayer o, float amount) {
        if (amount <= 0) return;
        String k = botKey(b, o);
        CompoundTag r = root(o);
        CompoundTag c = r.getCompound(k);
        int lv = clamp(c.getInt("lv"));
        if (lv >= MAX_LEVEL) return;
        int before = lv;
        float xp = c.getFloat("xp") + amount;
        while (lv < MAX_LEVEL && xp >= needed(lv)) { xp -= needed(lv); lv++; }
        if (lv >= MAX_LEVEL) xp = 0;
        c.putInt("lv", lv); c.putFloat("xp", xp);
        r.put(k, c);
        if (lv > before) o.displayClientMessage(Component.literal("").append(b.label()).append(Component.literal(": Building level " + lv
                + " (reach +" + reach(lv) + ", " + pct(saveChance(lv)) + " keep)")).withStyle(ChatFormatting.GREEN), true);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlace(BlockEvent.EntityPlaceEvent e) {
        if (e.isCanceled() || !(e.getLevel() instanceof ServerLevel l)) return;
        Entity who = e.getEntity();
        // (what's there now: for a non-player Forge's getPlacedBlock() is the block that was replaced)
        BlockState st = l.getBlockState(e.getPos());
        if (st.isAir()) st = e.getPlacedBlock();
        if (who instanceof ServerPlayer p) {
            if (p instanceof FakePlayer || p.isCreative() || p.isSpectator()) return;
            float xp = xpFor(st, l, e.getPos());
            if (xp > 0 && freshSpot(p.getUUID(), l, e.getPos())) addXp(p, xp);
        } else if (who instanceof PartyBotEntity b) {
            botPlaced(b, e.getPos(), st);      // (Dragon Balls post this event for their bot)
        }
    }

    // ---------------------------------------------------------------- reach

    /** Puts the block-reach bonus for this character's level on the player (only block reach, not attack reach). */
    public static void applyReach(ServerPlayer p) {
        AttributeInstance a = p.getAttribute(ForgeMod.BLOCK_REACH.get());
        if (a == null) return;
        int want = reach(level(p));
        AttributeModifier cur = a.getModifier(REACH_ID);
        if (cur != null && cur.getAmount() == want) return;
        if (cur != null) a.removeModifier(REACH_ID);
        if (want > 0) a.addTransientModifier(new AttributeModifier(REACH_ID, "Xeno Warriors building level", want, AttributeModifier.Operation.ADDITION));
    }

    /** Kept up to date once a second (character switches, respawns, dimension changes all just catch up). */
    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer p && !(p instanceof FakePlayer) && p.tickCount % 20 == 0) applyReach(p);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) applyReach(p);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        RECENT.remove(event.getEntity().getUUID());
    }

    /** Forgets a removed bot's recent spots. */
    public static void forget(UUID id) { RECENT.remove(id); }

    // ---------------------------------------------------------------- the save chance (BlockItemSaveMixin)

    public static void beforePlace(BlockPlaceContext ctx) {
        ItemStack s = ctx.getItemInHand();
        PLACING.get().push(new Object[]{s, s.getCount()});
    }

    public static void afterPlace(BlockPlaceContext ctx, InteractionResult result) {
        ArrayDeque<Object[]> q = PLACING.get();
        ItemStack s = ctx.getItemInHand();
        Object[] top = null;
        while (!q.isEmpty()) { top = q.pop(); if (top[0] == s) break; top = null; }   // (skips any left by a placement that threw)
        if (top == null || result == null || !result.consumesAction()) return;
        if (!(ctx.getPlayer() instanceof ServerPlayer p) || p instanceof FakePlayer || p.getAbilities().instabuild || p.isSpectator()) return;
        int before = (Integer) top[1];
        if (s.getCount() != before - 1) return;          // exactly one item used up (nothing else to give back)
        float chance = saveChance(level(p));
        if (chance <= 0F) return;
        float roll = testRoll != null ? testRoll : p.getRandom().nextFloat();
        if (roll >= chance) return;
        s.grow(1);
        testSaved++;
        // the client already took one off by itself: tell it the real count
        InteractionHand hand = ctx.getHand();
        if (p.connection != null && p.getItemInHand(hand) == s) {
            int slot = hand == InteractionHand.MAIN_HAND ? p.getInventory().selected : Inventory.SLOT_OFFHAND;
            p.connection.send(new ClientboundContainerSetSlotPacket(-2, 0, slot, s.copy()));
        }
    }
}

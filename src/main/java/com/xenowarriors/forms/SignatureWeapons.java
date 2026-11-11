package com.xenowarriors.forms;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.character.ModCapabilities;
import com.xenowarriors.item.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.function.Supplier;

/**
 * Every character whose show weapon exists as an item can summon it with the summon key (G): Max's knife, Sam's bat,
 * Logan's and Keita's blades, Zane's broom and Garry's microphone (Mikiko's Hekublade and Rhalix's wires have their own
 * systems). A tap summons it into the hand / puts it away. The summoned copy is tagged with its owner's character:
 * it can't be dropped, stored in a container, hung up, or kept after death, a character switch or a Season lock; it
 * never wears out. Bots of these characters summon theirs in fights (see {@code BotGear}).
 * <p>
 * The plain craftable / droppable items are untouched: only the tagged copy is "summoned".
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SignatureWeapons {
    public static final String TAG = "xw_sig";

    private SignatureWeapons() {}

    /** The item a character summons, or null (no show weapon in the mod). */
    public static Item itemOf(CharacterType t) {
        Supplier<Item> s = switch (t) {
            case MAX -> ModItems.MAX_KNIFE::get;
            case SAM -> ModItems.SAM_BAT::get;
            case LOGAN -> ModItems.LOGAN_BLADE::get;
            case KEITA -> ModItems.KEITA_BLADE::get;
            case ZANE -> ModItems.ZANE_BROOM::get;
            case GARRY -> ModItems.MICROPHONE::get;
            default -> null;
        };
        return s == null ? null : s.get();
    }

    public static boolean has(CharacterType t) { return t != null && t != CharacterType.NONE && switch (t) {
        case MAX, SAM, LOGAN, KEITA, ZANE, GARRY -> true;
        default -> false;
    }; }

    public static boolean isSummoned(ItemStack s) { return !s.isEmpty() && s.hasTag() && s.getTag().contains(TAG); }

    /** A fresh summoned copy for a character (unbreakable, tagged, with a line of lore). */
    public static ItemStack make(CharacterType t) {
        Item i = itemOf(t);
        if (i == null) return ItemStack.EMPTY;
        ItemStack st = new ItemStack(i);
        CompoundTag tag = st.getOrCreateTag();
        tag.putString(TAG, t.getId());
        tag.putBoolean("Unbreakable", true);
        tag.putInt("HideFlags", 4);
        ListTag lore = new ListTag();
        lore.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal("Summoned: can't be dropped or stored. Tap the summon key to put it away.")
                .withStyle(Style.EMPTY.withItalic(false).withColor(ChatFormatting.GRAY)))));
        CompoundTag display = new CompoundTag();
        display.put("Lore", lore);
        tag.put("display", display);
        return st;
    }

    private static boolean mine(ItemStack s, CharacterType t) {
        return isSummoned(s) && t != null && s.getTag().getString(TAG).equals(t.getId());
    }

    /** Is the summoned weapon out in this inventory? */
    public static boolean out(Container c, CharacterType t) {
        for (int i = 0; i < c.getContainerSize(); i++) if (mine(c.getItem(i), t)) return true;
        return false;
    }

    /** Takes every summoned weapon (any owner) out of an inventory. */
    public static int strip(Container c) {
        int n = 0;
        for (int i = 0; i < c.getContainerSize(); i++) if (isSummoned(c.getItem(i))) { c.setItem(i, ItemStack.EMPTY); n++; }
        return n;
    }

    // ------------------------------------------------------------------ the summon key

    /** Tap of the summon key. Returns true if this character has a signature weapon (handled, whatever happened). */
    public static boolean toggle(ServerPlayer p) {
        CharacterType t = ModCapabilities.getType(p);
        if (!has(t)) return false;
        if (p.isSpectator() || !p.isAlive()) return true;
        if (strip(p.getInventory()) > 0) {
            if (isSummoned(p.containerMenu.getCarried())) p.containerMenu.setCarried(ItemStack.EMPTY);
            awayFx(p, t);
            return true;
        }
        if (!com.xenowarriors.character.Seasons.unlocked(p.server, t.season())) return true;      // (Season 2/3 stay locked)
        ItemStack st = make(t);
        if (st.isEmpty() || !com.xenowarriors.mikiko.Hekublade.place(p, st)) return true;
        summonFx(p, t);
        return true;
    }

    public static void summonFx(Entity e, CharacterType t) {
        if (!(e.level() instanceof ServerLevel level)) return;
        int c = t.getColor();
        level.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.9F, 1.4F);
        level.sendParticles(new DustParticleOptions(new Vector3f(((c >> 16) & 255) / 255F, ((c >> 8) & 255) / 255F, (c & 255) / 255F), 1.1F),
                e.getX(), e.getY() + 1.1, e.getZ(), 18, 0.3, 0.4, 0.3, 0);
        level.sendParticles(ParticleTypes.END_ROD, e.getX(), e.getY() + 1.1, e.getZ(), 6, 0.25, 0.3, 0.25, 0.02);
    }

    public static void awayFx(Entity e, CharacterType t) {
        if (!(e.level() instanceof ServerLevel level)) return;
        level.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 0.7F, 1.2F);
        level.sendParticles(ParticleTypes.POOF, e.getX(), e.getY() + 1.1, e.getZ(), 8, 0.25, 0.3, 0.25, 0.02);
    }

    // ------------------------------------------------------------------ upkeep: it only exists for its owner

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p)) return;
        if (p.containerMenu != p.inventoryMenu) {                          // a chest and the like: nothing summoned stays in it
            for (Slot slot : p.containerMenu.slots) if (slot.container != p.getInventory() && isSummoned(slot.getItem())) slot.set(ItemStack.EMPTY);
        }
        if (p.tickCount % 10 != 0) return;
        CharacterType t = ModCapabilities.getType(p);
        boolean ok = has(t) && com.xenowarriors.character.Seasons.unlocked(p.server, t.season());
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (isSummoned(st) && !(ok && mine(st, t))) inv.setItem(i, ItemStack.EMPTY);          // not theirs any more (switched character / locked again)
        }
        ItemStack carried = p.containerMenu.getCarried();
        if (isSummoned(carried) && !(ok && mine(carried, t))) p.containerMenu.setCarried(ItemStack.EMPTY);
        for (Slot slot : p.inventoryMenu.slots) if (slot.container != inv && isSummoned(slot.getItem())) slot.set(ItemStack.EMPTY);   // (the 2x2 crafting grid)
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            strip(p.getInventory());
            p.containerMenu.setCarried(isSummoned(p.containerMenu.getCarried()) ? ItemStack.EMPTY : p.containerMenu.getCarried());
        }
    }

    @SubscribeEvent
    public static void onToss(ItemTossEvent event) {
        if (isSummoned(event.getEntity().getItem())) {
            event.setCanceled(true);
            if (event.getPlayer() instanceof ServerPlayer p) awayFx(p, ModCapabilities.getType(p));
        }
    }

    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        event.getDrops().removeIf(item -> isSummoned(item.getItem()));
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof ItemEntity item && isSummoned(item.getItem())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onContainerClose(PlayerContainerEvent.Close event) {
        for (Slot slot : event.getContainer().slots)
            if (!(slot.container instanceof Inventory) && isSummoned(slot.getItem())) slot.set(ItemStack.EMPTY);
    }

    @SubscribeEvent
    public static void onHang(PlayerInteractEvent.EntityInteractSpecific event) {
        if (hangs(event.getTarget()) && isSummoned(event.getItemStack())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onFrame(PlayerInteractEvent.EntityInteract event) {
        if (hangs(event.getTarget()) && isSummoned(event.getItemStack())) event.setCanceled(true);
    }

    private static boolean hangs(Entity e) {
        return e instanceof net.minecraft.world.entity.decoration.ArmorStand || e instanceof net.minecraft.world.entity.decoration.ItemFrame;
    }

    /** Whether a player is holding their own summoned weapon (for tests and the tutorial). */
    public static boolean holding(Player p) { return mine(p.getMainHandItem(), ModCapabilities.getType(p)); }
}

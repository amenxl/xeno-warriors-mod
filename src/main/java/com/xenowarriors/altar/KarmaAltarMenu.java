package com.xenowarriors.altar;

import com.xenowarriors.character.CharacterType;
import com.xenowarriors.character.ModCapabilities;
import com.xenowarriors.character.PlayerCharacter;
import com.xenowarriors.item.ModItems;
import com.xenowarriors.karma.Karma;
import com.xenowarriors.mikiko.BladeUpgrades;
import com.xenowarriors.mikiko.Hekublade;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Two slots on the altar - the Hekublade and the material - and the Upgrade button (button id 0). */
public class KarmaAltarMenu extends AbstractContainerMenu {
    public static final int BLADE = 0, MATERIAL = 1;
    private final Container slots = new SimpleContainer(2) {
        @Override
        public void setChanged() {
            super.setChanged();
            slotsChanged(this);
        }
    };
    private final ContainerLevelAccess access;
    private final Player player;

    public KarmaAltarMenu(int id, Inventory inv, ContainerLevelAccess access) {
        super(ModAltar.MENU.get(), id);
        this.access = access;
        this.player = inv.player;
        addSlot(new Slot(slots, BLADE, 44, 35) {
            @Override public boolean mayPlace(ItemStack s) { return Hekublade.isBlade(s); }
            @Override public int getMaxStackSize() { return 1; }
        });
        addSlot(new Slot(slots, MATERIAL, 80, 35) {
            @Override public boolean mayPlace(ItemStack s) { return path(s) != null; }
        });
        for (int r = 0; r < 3; r++) for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c + r * 9 + 9, 8 + c * 18, 84 + r * 18));
        for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c, 8 + c * 18, 142));
    }

    public static KarmaAltarMenu fromNetwork(int id, Inventory inv, FriendlyByteBuf buf) {
        return new KarmaAltarMenu(id, inv, ContainerLevelAccess.NULL);
    }

    /** "sky" / "dark" for an upgrade material, else null. */
    public static String path(ItemStack s) {
        if (s.is(ModItems.SKY_CRYSTAL.get())) return "sky";
        if (s.is(ModItems.CORRUPTED_SHARD.get())) return "dark";
        return null;
    }

    public ItemStack blade() { return slots.getItem(BLADE); }
    public ItemStack material() { return slots.getItem(MATERIAL); }

    /** Current level of the path the material is for (or -1 if nothing to upgrade). */
    public int currentLevel() {
        String path = path(material());
        if (path == null || !Hekublade.isBlade(blade())) return -1;
        return path.equals("sky") ? BladeUpgrades.sky(blade()) : BladeUpgrades.dark(blade());
    }

    @Override
    public boolean clickMenuButton(Player p, int id) {
        if (id != 0 || !(p instanceof ServerPlayer sp)) return false;
        if (ModCapabilities.getType(sp) != CharacterType.MIKIKO) return fail(sp, "Only Mikiko's Hekublade answers the altar");
        int lvl = currentLevel();
        if (lvl < 0) return fail(sp, "Put the Hekublade and Sky Crystals or Corrupted Shards on the altar");
        if (lvl >= BladeUpgrades.MAX) return fail(sp, "That edge is already at its strongest");
        int need = BladeUpgrades.MATERIALS[lvl], karma = BladeUpgrades.KARMA[lvl];
        if (material().getCount() < need) return fail(sp, "Needs " + need + " of them");
        if (!sp.isCreative() && !Karma.trySpend(sp, karma)) return false;
        String path = path(material());
        material().shrink(need);
        PlayerCharacter d = sp.getCapability(ModCapabilities.PLAYER_CHARACTER).resolve().orElse(null);
        if (d == null) return false;
        if (path.equals("sky")) d.bladeSky = lvl + 1; else d.bladeDark = lvl + 1;
        slots.setItem(BLADE, BladeUpgrades.apply(sp, blade().copy()));
        // and any other copy he's carrying
        for (int i = 0; i < sp.getInventory().getContainerSize(); i++) {
            ItemStack s = sp.getInventory().getItem(i);
            if (Hekublade.isBlade(s)) BladeUpgrades.apply(sp, s);
        }
        sp.level().playSound(null, sp.blockPosition(), path.equals("sky") ? SoundEvents.AMETHYST_BLOCK_RESONATE : SoundEvents.WITHER_AMBIENT,
                SoundSource.PLAYERS, 1F, path.equals("sky") ? 1.2F : 1.6F);
        sp.level().playSound(null, sp.blockPosition(), SoundEvents.SMITHING_TABLE_USE, SoundSource.PLAYERS, 1F, 1F);
        sp.displayClientMessage(Component.literal((path.equals("sky") ? "Sky Edge" : "Dark Edge") + " reached level " + (lvl + 1) + "!")
                .withStyle(path.equals("sky") ? ChatFormatting.AQUA : ChatFormatting.LIGHT_PURPLE), true);
        broadcastChanges();
        return true;
    }

    private static boolean fail(ServerPlayer p, String msg) {
        p.displayClientMessage(Component.literal(msg).withStyle(ChatFormatting.GRAY), true);
        return false;
    }

    @Override
    public void removed(Player p) {
        super.removed(p);
        if (!p.level().isClientSide) clearContainer(p, slots);
    }

    @Override
    public boolean stillValid(Player p) {
        return stillValid(access, p, ModAltar.ALTAR.get());
    }

    @Override
    public ItemStack quickMoveStack(Player p, int index) {
        if (index < 0 || index >= this.slots.size()) return ItemStack.EMPTY;
        Slot slot = getSlot(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem(), copy = stack.copy();
        if (index < 2) {
            if (!moveItemStackTo(stack, 2, 38, true)) return ItemStack.EMPTY;
        } else if (Hekublade.isBlade(stack)) {
            if (!moveItemStackTo(stack, BLADE, BLADE + 1, false)) return ItemStack.EMPTY;
        } else if (path(stack) != null) {
            if (!moveItemStackTo(stack, MATERIAL, MATERIAL + 1, false)) return ItemStack.EMPTY;
        } else return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        return copy;
    }
}

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
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The altar: an item slot, a material slot and the Upgrade button (button id 0).
 * <ul>
 *   <li>Mikiko doesn't need to put his Hekublade in: the altar reads his blade's edges from him, so with the item slot
 *   empty he just adds Sky Crystals or Corrupted Shards.</li>
 *   <li>Anyone can put a Sky Crystal or Corrupted weapon or tool in the item slot and upgrade it with its own material.</li>
 * </ul>
 * The server tells the screen whether the player is Mikiko and his edges' levels (data slots).
 */
public class KarmaAltarMenu extends AbstractContainerMenu {
    public static final int BLADE = 0, MATERIAL = 1;
    /** Slot 0 is the item slot (gear; Mikiko may also put the blade itself there - same effect as leaving it empty). */
    public static final int ITEM = BLADE;
    private static final int D_MIKIKO = 0, D_SKY = 1, D_DARK = 2;
    private final Container table = new SimpleContainer(2) {
        @Override
        public void setChanged() {
            super.setChanged();
            KarmaAltarMenu.this.slotsChanged(this);
        }
    };
    private final ContainerLevelAccess access;
    private final Player player;
    private final ContainerData data;

    public KarmaAltarMenu(int id, Inventory inv, ContainerLevelAccess access) {
        super(ModAltar.MENU.get(), id);
        this.access = access;
        this.player = inv.player;
        this.data = player instanceof ServerPlayer sp ? serverData(sp) : new SimpleContainerData(3);
        addDataSlots(data);
        addSlot(new Slot(table, ITEM, 44, 35) {
            @Override public boolean mayPlace(ItemStack s) { return GearUpgrades.path(s) != null || (Hekublade.isBlade(s) && mikiko()); }
            @Override public int getMaxStackSize() { return 1; }
        });
        addSlot(new Slot(table, MATERIAL, 80, 35) {
            @Override public boolean mayPlace(ItemStack s) { return path(s) != null; }
        });
        for (int r = 0; r < 3; r++) for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c + r * 9 + 9, 8 + c * 18, 84 + r * 18));
        for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c, 8 + c * 18, 142));
    }

    private static ContainerData serverData(ServerPlayer sp) {
        return new ContainerData() {
            @Override public int get(int i) {
                if (i == D_MIKIKO) return ModCapabilities.getType(sp) == CharacterType.MIKIKO ? 1 : 0;
                PlayerCharacter d = sp.getCapability(ModCapabilities.PLAYER_CHARACTER).resolve().orElse(null);
                return d == null ? 0 : i == D_SKY ? d.bladeSky : d.bladeDark;
            }
            @Override public void set(int i, int v) {}
            @Override public int getCount() { return 3; }
        };
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

    /** Whether the player at this altar is Mikiko (his blade is read from him). */
    public boolean mikiko() { return data.get(D_MIKIKO) == 1; }
    public int bladeSky() { return data.get(D_SKY); }
    public int bladeDark() { return data.get(D_DARK); }

    public ItemStack item() { return table.getItem(ITEM); }
    /** (old name) */
    public ItemStack blade() { return item(); }
    public ItemStack material() { return table.getItem(MATERIAL); }

    /** What the material goes into: "gear" (the item in the slot), "blade" (Mikiko's Hekublade), or null. */
    public String targetKind() {
        ItemStack it = item();
        if (GearUpgrades.path(it) != null) return "gear";
        if (mikiko() && (it.isEmpty() || Hekublade.isBlade(it))) return "blade";
        return null;
    }

    /** Current level of what the material would upgrade (-1 if nothing to upgrade, or the wrong material for the gear). */
    public int currentLevel() {
        String path = path(material()), target = targetKind();
        if (path == null || target == null) return -1;
        if (target.equals("gear")) return GearUpgrades.fits(item(), material()) ? GearUpgrades.level(item()) : -1;
        return path.equals("sky") ? bladeSky() : bladeDark();
    }

    public int maxLevel() { return "gear".equals(targetKind()) ? GearUpgrades.MAX : BladeUpgrades.MAX; }

    public int materialsNeeded(int lvl) { return "gear".equals(targetKind()) ? GearUpgrades.MATERIALS[lvl] : BladeUpgrades.MATERIALS[lvl]; }

    public int karmaNeeded(int lvl) { return "gear".equals(targetKind()) ? GearUpgrades.KARMA[lvl] : BladeUpgrades.KARMA[lvl]; }

    @Override
    public boolean clickMenuButton(Player p, int id) {
        if (id != 0 || !(p instanceof ServerPlayer sp)) return false;
        String target = targetKind();
        if (target == null) return fail(sp, mikiko() ? "Add Sky Crystals or Corrupted Shards (or put in Sky / Corrupted gear)"
                : "Put in a Sky Crystal or Corrupted weapon or tool");
        String path = path(material());
        if (path == null) return fail(sp, "Add Sky Crystals or Corrupted Shards");
        int lvl = currentLevel();
        if (lvl < 0) return fail(sp, GearUpgrades.path(item()).equals("sky") ? "Sky gear takes Sky Crystals" : "Corrupted gear takes Corrupted Shards");
        if (lvl >= maxLevel()) return fail(sp, target.equals("gear") ? "That's already at its strongest" : "That edge is already at its strongest");
        int need = materialsNeeded(lvl), karma = karmaNeeded(lvl);
        if (material().getCount() < need) return fail(sp, "Needs " + need + " of them");
        if (!sp.isCreative() && !Karma.trySpend(sp, karma)) return false;
        boolean sky = path.equals("sky");
        if (target.equals("gear")) {
            ItemStack g = item().copy();
            material().shrink(need);
            GearUpgrades.upgrade(g);
            table.setItem(ITEM, g);
            sp.displayClientMessage(Component.literal(g.getHoverName().getString() + " forged to level " + (lvl + 1) + "!")
                    .withStyle(sky ? ChatFormatting.AQUA : ChatFormatting.LIGHT_PURPLE), true);
        } else {
            PlayerCharacter d = sp.getCapability(ModCapabilities.PLAYER_CHARACTER).resolve().orElse(null);
            if (d == null) return false;
            material().shrink(need);
            if (sky) d.bladeSky = lvl + 1; else d.bladeDark = lvl + 1;
            if (Hekublade.isBlade(item())) table.setItem(ITEM, BladeUpgrades.apply(sp, item().copy()));
            // every copy he's carrying (and the one in his hand)
            for (int i = 0; i < sp.getInventory().getContainerSize(); i++) {
                ItemStack s = sp.getInventory().getItem(i);
                if (Hekublade.isBlade(s)) BladeUpgrades.apply(sp, s);
            }
            sp.displayClientMessage(Component.literal((sky ? "Sky Edge" : "Dark Edge") + " reached level " + (lvl + 1) + "!")
                    .withStyle(sky ? ChatFormatting.AQUA : ChatFormatting.LIGHT_PURPLE), true);
        }
        sp.level().playSound(null, sp.blockPosition(), sky ? SoundEvents.AMETHYST_BLOCK_RESONATE : SoundEvents.WITHER_AMBIENT,
                SoundSource.PLAYERS, 1F, sky ? 1.2F : 1.6F);
        sp.level().playSound(null, sp.blockPosition(), SoundEvents.SMITHING_TABLE_USE, SoundSource.PLAYERS, 1F, 1F);
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
        if (!p.level().isClientSide) clearContainer(p, table);
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
        } else if (getSlot(ITEM).mayPlace(stack)) {
            if (!moveItemStackTo(stack, ITEM, ITEM + 1, false)) return ItemStack.EMPTY;
        } else if (path(stack) != null) {
            if (!moveItemStackTo(stack, MATERIAL, MATERIAL + 1, false)) return ItemStack.EMPTY;
        } else return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        return copy;
    }
}

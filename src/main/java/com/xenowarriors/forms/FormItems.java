package com.xenowarriors.forms;

import com.xenowarriors.XenoWarriors;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.EnumMap;
import java.util.Map;

/**
 * The summoned forms (registered on their own list so they don't crowd the creative tab): Hekublade guns, tools and
 * the Heku-umbrella; Rhalix's wires, wire sword, wire guns and wire tools. They only exist while summoned.
 */
public final class FormItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, XenoWarriors.MOD_ID);

    /** Diamond-or-better: netherite harvest level; speed and damage grow with the weapon level (see FormTools). */
    public static final Tier TIER = new Tier() {
        @Override public int getUses() { return 0; }
        @Override public float getSpeed() { return 9F; }
        @Override public float getAttackDamageBonus() { return 4F; }
        @Override public int getLevel() { return 4; }
        @Override public int getEnchantmentValue() { return 15; }
        @Override public Ingredient getRepairIngredient() { return Ingredient.EMPTY; }
    };

    private static final Map<Form.Style, Map<Form, RegistryObject<Item>>> BY = new EnumMap<>(Form.Style.class);

    public static final RegistryObject<Item> WIRES = reg(Form.Style.WIRE, Form.WIRES, "rhalix_wires", () -> new WiresItem(props()));
    public static final RegistryObject<Item> WIRE_SWORD = reg(Form.Style.WIRE, Form.WIRE_SWORD, "wire_sword", () -> new FormTools.Sword(Form.Style.WIRE, props()));
    public static final RegistryObject<Item> UMBRELLA = reg(Form.Style.HEKU, Form.UMBRELLA, "heku_umbrella", () -> new UmbrellaItem(props()));

    static {
        for (Form.Style s : Form.Style.values()) {
            String p = s == Form.Style.HEKU ? "heku_" : "wire_";
            for (Form f : new Form[]{Form.PISTOL, Form.RIFLE, Form.SHOTGUN})
                reg(s, f, p + f.name().toLowerCase(), () -> new FormGunItem(s, f, props()));
            reg(s, Form.PICKAXE, p + "pickaxe", () -> new FormTools.Pickaxe(s, props()));
            reg(s, Form.AXE, p + "axe", () -> new FormTools.Axe(s, props()));
            reg(s, Form.SHOVEL, p + "shovel", () -> new FormTools.Shovel(s, props()));
            reg(s, Form.HOE, p + "hoe", () -> new FormTools.Hoe(s, props()));
        }
    }

    private FormItems() {}

    private static Item.Properties props() { return new Item.Properties().stacksTo(1).fireResistant(); }

    private static RegistryObject<Item> reg(Form.Style s, Form f, String name, java.util.function.Supplier<Item> sup) {
        RegistryObject<Item> r = ITEMS.register(name, sup);
        BY.computeIfAbsent(s, k -> new EnumMap<>(Form.class)).put(f, r);
        return r;
    }

    /** The item for a form in a style (null for the Hekublade itself, which is ModItems.HEKUBLADE). */
    public static Item item(Form.Style s, Form f) {
        if (f == Form.BLADE) return com.xenowarriors.item.ModItems.HEKUBLADE.get();
        RegistryObject<Item> r = BY.getOrDefault(s, Map.of()).get(f);
        return r == null ? null : r.get();
    }

    public static ItemStack stack(Form.Style s, Form f) {
        Item i = item(s, f);
        return i == null ? ItemStack.EMPTY : new ItemStack(i);
    }

    /** Is this one of the summoned forms (not the Hekublade itself)? */
    public static boolean isForm(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof Summoned;
    }

    /** Marker for summoned form items. */
    public interface Summoned {
        Form.Style style();
        Form form();
    }
}

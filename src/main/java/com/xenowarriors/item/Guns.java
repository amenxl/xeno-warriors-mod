package com.xenowarriors.item;

import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Guns of any kind: TACZ's, anything in the {@code xenowarriors:guns} item tag, and our own (items implementing
 * {@link Marker}, e.g. summoned guns). Holding one, clicks never start a melee combo string - you stand and shoot.
 */
public final class Guns {
    public static final TagKey<Item> TAG = ItemTags.create(com.xenowarriors.XenoWarriors.id("guns"));

    /** Put on a gun item of ours so the combo system leaves it alone. */
    public interface Marker {}

    private Guns() {}

    public static boolean isGun(ItemStack s) {
        if (s == null || s.isEmpty()) return false;
        return s.getItem() instanceof Marker || s.is(TAG) || com.xenowarriors.compat.TaczGuns.isGun(s);
    }
}

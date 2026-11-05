package com.xenowarriors.statboost;

import net.minecraft.ChatFormatting;
import net.minecraft.world.item.Rarity;

/** How many levels a Stat Boost gives; the higher, the rarer (and the +100 only from very strong enemies). */
public enum BoostTier {
    ONE(1, ChatFormatting.GREEN, Rarity.UNCOMMON),
    TWO(2, ChatFormatting.AQUA, Rarity.RARE),
    FIVE(5, ChatFormatting.LIGHT_PURPLE, Rarity.RARE),
    TEN(10, ChatFormatting.GOLD, Rarity.EPIC),
    HUNDRED(100, ChatFormatting.RED, Rarity.EPIC);

    public final int levels;
    public final ChatFormatting color;
    public final Rarity rarity;

    BoostTier(int levels, ChatFormatting color, Rarity rarity) {
        this.levels = levels;
        this.color = color;
        this.rarity = rarity;
    }

    public String label() {
        return switch (this) {
            case ONE -> "Common";
            case TWO -> "Uncommon";
            case FIVE -> "Rare";
            case TEN -> "Epic";
            case HUNDRED -> "Legendary";
        };
    }
}

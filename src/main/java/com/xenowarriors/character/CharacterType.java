package com.xenowarriors.character;

import com.xenowarriors.XenoWarriors;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * The playable characters. To add one later: add an entry here, add its skin
 * (and optional detail model) to the assets, and add its names to the lang file.
 */
public enum CharacterType {
    NONE("none", 0xFFFFFFFF),
    MIKIKO("mikiko", 0xFFE8801A),
    RHALIX("rhalix", 0xFFA02CD2);

    /** The characters shown on the selection screen, in order. */
    public static final CharacterType[] PLAYABLE = {MIKIKO, RHALIX};

    private final String id;
    private final int color;
    private final ResourceLocation skin;
    private final ResourceLocation detailTexture;
    private final ResourceLocation faceTexture;

    CharacterType(String id, int color) {
        this.id = id;
        this.color = color;
        this.skin = XenoWarriors.id("textures/entity/character/" + id + ".png");
        this.detailTexture = XenoWarriors.id("textures/entity/character/" + id + "_details.png");
        this.faceTexture = XenoWarriors.id("textures/entity/character/" + id + "_face.png");
    }

    public String getId() { return id; }
    public int getColor() { return color; }
    public ResourceLocation getSkin() { return skin; }
    public ResourceLocation getDetailTexture() { return detailTexture; }
    public ResourceLocation getFaceTexture() { return faceTexture; }

    public Component getDisplayName() { return Component.translatable("character.xenowarriors." + id); }
    public Component getFullName() { return Component.translatable("character.xenowarriors." + id + ".full_name"); }
    public Component getTitle() { return Component.translatable("character.xenowarriors." + id + ".title"); }
    public Component getElements() { return Component.translatable("character.xenowarriors." + id + ".elements"); }

    public static CharacterType byId(String id) {
        for (CharacterType type : values()) {
            if (type.id.equals(id)) return type;
        }
        return NONE;
    }
}

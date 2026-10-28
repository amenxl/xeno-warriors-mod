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
    RHALIX("rhalix", 0xFFA02CD2),
    /** Season 2 content (locked until /xeno season unlock 2). */
    ULTIMA("ultima", 0xFF1FE07A),
    /** Season 2 content: the Hekublade's guardian. */
    KAERU("kaeru", 0xFFE8701C),
    /** The Sky Patrol (season 1 looks). */
    KYRIE("kyrie", 0xFF7A4AD8),
    LEXY("lexy", 0xFF3A4AD0),
    MAX("max", 0xFF9AD2F2),
    SAM("sam", 0xFF2AA890),
    /** Season 3: a demon with wings. Never in a party with Rhalix, and can't come within 400 blocks of him. */
    AIONI("aioni", 0xFFC41E30),
    /** Technica ("Tech"), the Sky Patrol's inventor, on her artificial wings. */
    TECHNICA("technica", 0xFFEC5AA0),
    /** The Disco Dojo brothers. */
    STEVIE("stevie", 0xFF4A2A9A),
    GARRY("garry", 0xFF2AA82A),
    /** Yuri Tenjo: the hacker genius (season 2), Ultima's little sister. */
    YURI("yuri", 0xFF3AD04A),
    /** Zane the Magical Mage (season 3): magic, not karma. */
    ZANE("zane", 0xFF9A4AD8),
    /** The Sky Rebellion: Logan (the old royal knight), Kill (the first demon Skylander), Keita (the leader's sleepy son). */
    LOGAN("logan", 0xFFE8C040),
    KILL("kill", 0xFF6A2A8A),
    KEITA("keita", 0xFF2A3AA0);

    /** The characters shown on the selection screen, in order. */
    public static final CharacterType[] PLAYABLE = {MIKIKO, RHALIX, ULTIMA, KAERU, KYRIE, LEXY, MAX, SAM, TECHNICA, STEVIE, GARRY, AIONI, YURI, ZANE, LOGAN, KILL, KEITA};

    /** The story season a character comes from (2/3 stay locked until unlocked). */
    /** Skylanders: they have wings and can fly (H puts the wings away). */
    public boolean skylander() { return this == RHALIX || this == KYRIE || this == LEXY || this == MAX || this == SAM || this == AIONI || this == LOGAN || this == KILL || this == KEITA; }   // (Technica gets her artificial wings later in the show)   // (Ai-oni flies on her demon wings)

    /** The Sky Patrol members (for team synergy). */
    public boolean skyPatrol() { return this == LEXY || this == MAX || this == SAM || this == TECHNICA; }

    /** Their race (see Race). */
    public Race race() { return Race.of(this); }

    public int season() { return this == AIONI || this == ZANE ? 3 : this == ULTIMA || this == KAERU || this == YURI ? 2 : 1; }

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

    /** Outfits: display names and texture/model suffixes ("" = the normal look). */
    public String[] outfitNames() {
        return switch (this) {
            case MIKIKO -> new String[]{"Recarnet 1", "Origins 1", "Origins 2", "Recarnet Season 3", "Recarnet Season 3 (Variant 2)"};
            case RHALIX -> new String[]{"Recarnet 1", "Origins 1", "Origins 2", "Recarnet Season 3"};
            case YURI -> new String[]{"Default", "C1 PJs"};
            case MAX, SAM -> new String[]{"Default", "Royal Capital", "Season 3"};
            default -> new String[]{"Default"};
        };
    }

    /** The season an outfit is from (Season 2/3 outfits stay locked until /xeno season unlock 2 or 3). */
    public int outfitSeason(int outfit) {
        String[] keys = outfitKeys();
        if (outfit <= 0 || outfit >= keys.length) return 1;
        String k = keys[outfit];
        if (k.startsWith("s3")) return 3;                          // (the Royal Capital disguises are Season 1: Max and Sam's first trip there with Kyrie)
        if (k.equals("c1pjs")) return 2;
        return 1;
    }

    public String[] outfitKeys() {
        return switch (this) {
            case MIKIKO -> new String[]{"", "origins1", "origins2", "s3", "s3b"};
            case RHALIX -> new String[]{"", "origins1", "origins2", "s3"};
            case YURI -> new String[]{"", "c1pjs"};
            case MAX, SAM -> new String[]{"", "royal", "s3"};
            default -> new String[]{""};
        };
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

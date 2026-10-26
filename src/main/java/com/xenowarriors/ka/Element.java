package com.xenowarriors.ka;

/** Elements: the everyday powers. Very common in townsfolk, fairly common in Skylanders, nearly every corrupted soul. */
public enum Element {
    FIRE(0xFF6A1F, 15), WATER(0x3A8DFF, 12), EARTH(0x8B6B3E, 18), ICE(0xA8E8FF, 15), LIGHTNING(0xFFF15A, 18),
    WIND(0xCFFFE8, 12), SOUND(0xE07CFF, 15), LIGHT(0xFFFDE0, 15), CRYSTAL(0x8FD3FF, 18), CLOUD(0xE6E6F0, 14),
    PLANT(0x4CC34C, 15), METAL(0xB8BCC6, 16), SMOKE(0x5A5A5A, 14), SHADOW(0x2A1036, 16), CORRUPTION(0x8A3CC8, 18),
    POISON(0x7ACC2A, 14), VOID(0x120018, 25);

    public final int color, karma;

    Element(int color, int karma) { this.color = color; this.karma = karma; }

    public String id() { return name().toLowerCase(); }

    public String title() { return name().charAt(0) + name().substring(1).toLowerCase(); }

    public static Element byId(String id) {
        for (Element e : values()) if (e.id().equals(id)) return e;
        return null;
    }

    public static final Element[] HUMAN = {FIRE, WATER, EARTH, ICE, LIGHTNING, SOUND, PLANT, METAL, SMOKE};
    public static final Element[] SKYLANDER = {WIND, LIGHT, CRYSTAL, CLOUD, ICE, LIGHTNING};
    public static final Element[] CSOUL = {SHADOW, CORRUPTION, POISON, FIRE};
}

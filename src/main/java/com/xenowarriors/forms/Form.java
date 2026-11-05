package com.xenowarriors.forms;

/**
 * What Mikiko can turn his Hekublade into and what Rhalix (Season 2) can make out of his wires. Picked on the hold
 * wheel of the summon key. Requirements: the weapon level (Hekublade level / Wires level), the Mining level and the
 * gun level of the character.
 */
public enum Form {
    BLADE("Hekublade", Style.HEKU, 0, 0, 0),
    WIRES("Wires", Style.WIRE, 0, 0, 0),
    WIRE_SWORD("Wire Sword", Style.WIRE, 0, 0, 0),
    PISTOL("Pistol", null, 50, 0, 50),
    RIFLE("Rifle", null, 50, 0, 50),
    SHOTGUN("Shotgun", null, 50, 0, 50),
    PICKAXE("Pickaxe", null, 80, 50, 0),
    AXE("Axe", null, 60, 30, 0),
    SHOVEL("Shovel", null, 20, 10, 0),
    /** The user's "Hoe: needs 50" - read as weapon level 50, no Mining requirement. */
    HOE("Hoe", null, 50, 0, 0),
    UMBRELLA("Heku-umbrella", Style.HEKU, 300, 0, 0);

    /** Whose weapon it's made from: the Hekublade (blade steel, glowing runes) or Rhalix's wires (dark purple, gravity glow). */
    public enum Style { HEKU, WIRE }

    public final String label;
    /** Only this style has it (null = both). */
    public final Style only;
    public final int weaponLevel, miningLevel, gunLevel;

    Form(String label, Style only, int weaponLevel, int miningLevel, int gunLevel) {
        this.label = label;
        this.only = only;
        this.weaponLevel = weaponLevel;
        this.miningLevel = miningLevel;
        this.gunLevel = gunLevel;
    }

    public boolean gun() { return this == PISTOL || this == RIFLE || this == SHOTGUN; }

    public boolean tool() { return this == PICKAXE || this == AXE || this == SHOVEL || this == HOE; }

    public boolean has(Style s) { return only == null || only == s; }

    /** The forms on a style's wheel, in wheel order (clockwise from the top). */
    public static java.util.List<Form> wheel(Style s) {
        java.util.List<Form> l = new java.util.ArrayList<>();
        for (Form f : values()) if (f.has(s)) l.add(f);
        return l;
    }

    public boolean meets(int weapon, int mining, int gun) {
        return weapon >= weaponLevel && mining >= miningLevel && gun >= gunLevel;
    }

    /** "Hekublade Lv 50 + Gun Lv 50" etc. */
    public String requirement(Style s) {
        String w = s == Style.HEKU ? "Hekublade" : "Wires";
        StringBuilder b = new StringBuilder();
        if (weaponLevel > 0) b.append(w).append(" Lv ").append(weaponLevel);
        if (miningLevel > 0) b.append(b.length() > 0 ? " + " : "").append("Mining Lv ").append(miningLevel);
        if (gunLevel > 0) b.append(b.length() > 0 ? " + " : "").append("Gun Lv ").append(gunLevel);
        return b.length() == 0 ? "Always" : b.toString();
    }

    public static Form byId(int i) { return i >= 0 && i < values().length ? values()[i] : null; }
}

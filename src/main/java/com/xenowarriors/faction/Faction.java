package com.xenowarriors.faction;

/** The groups you have standing with. */
public enum Faction {
    GOV("Government", "logo_government", 0x4FB3E8),
    SR("Sky Rebellion", "logo_sky_rebellion", 0xD23C3C),
    SKY("Sky Patrol & Skylanders", "logo_sky_patrol", 0xB070FF),
    TOWN("Townsfolk", null, 0x7FCB5A);

    public final String title, logo;
    public final int color;

    Faction(String title, String logo, int color) { this.title = title; this.logo = logo; this.color = color; }
}

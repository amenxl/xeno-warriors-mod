package com.xenowarriors.you;

import com.xenowarriors.character.Race;
import com.xenowarriors.ka.Element;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

/**
 * One of a player's ten "You" characters: a warrior made in the creator that wears the player's own Minecraft skin.
 * Each slot has its own name, race and element (and, for Skylanders, a Lunar or Solar form); stats and skill loadouts
 * are kept per slot (see {@link com.xenowarriors.character.PlayerCharacter#key}).
 */
public final class YouSlot {
    public static final int SLOTS = 10;
    /** Skylander forms. */
    public static final int FORM_NONE = 0, FORM_LUNAR = 1, FORM_SOLAR = 2;

    public String name = "";
    public Race race = Race.HUMAN;
    public Element element = Element.FIRE;
    public int form = FORM_NONE;
    /** Demons: true while in their human form (no wings, no flight, no demon aura). */
    public boolean humanForm;

    public boolean exists() { return !name.isEmpty(); }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putString("name", name);
        t.putString("race", race.name());
        t.putString("element", element.id());
        t.putInt("form", form);
        t.putBoolean("human_form", humanForm);
        return t;
    }

    public static YouSlot load(CompoundTag t) {
        YouSlot s = new YouSlot();
        s.name = t.getString("name");
        try { s.race = Race.valueOf(t.getString("race")); } catch (IllegalArgumentException e) { s.race = Race.HUMAN; }
        Element el = Element.byId(t.getString("element"));
        s.element = el == null ? Element.FIRE : el;
        s.form = t.getInt("form");
        s.humanForm = t.getBoolean("human_form");
        return s;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(name, 32);
        buf.writeEnum(race);
        buf.writeEnum(element);
        buf.writeVarInt(form);
        buf.writeBoolean(humanForm);
    }

    public static YouSlot read(FriendlyByteBuf buf) {
        YouSlot s = new YouSlot();
        s.name = buf.readUtf(32);
        s.race = buf.readEnum(Race.class);
        s.element = buf.readEnum(Element.class);
        s.form = buf.readVarInt();
        s.humanForm = buf.readBoolean();
        return s;
    }

    public YouSlot copy() { return load(save()); }

    /** The character's colour on screens and the HUD: their element's. */
    public int color() { return element.color; }

    /** Names: letters, digits, spaces and a few marks, 2-16 characters. */
    public static boolean validName(String n) {
        return n != null && n.length() >= 2 && n.length() <= 16 && n.matches("[A-Za-z0-9 _'\\-]+") && !n.isBlank();
    }
}

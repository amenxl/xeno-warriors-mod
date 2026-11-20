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
    /** A Government path taken (Humans only, for good - see YouPaths): none, Modified Human, Artificial Demon, Artificial Skylander. */
    public int path = PATH_NONE;
    /** A Demon (born or made) that awakened as a Devil (see YouDevil). */
    public boolean devil;
    /** Devil's Hunger: kills in demon form from Level 50 on (see YouDevil). */
    public int hunger;
    /** A second element, from an Element Crystal (null: none yet). */
    public Element element2;
    /** A born Skylander that ascended as an Angel (see YouAngel). */
    public boolean ascended;
    /** A Skylander's Lunar / Solar path mastery (forms used at their own time of day, see YouAngel). */
    public int lunarM, solarM;
    /** Its own look: "" = your skin, "p:<name>" = that Minecraft player's skin, "f:<sha1>" = a skin file you added (see YouSkins). */
    public String skin = "";
    /** Its KA (a Ka id; "" = none), and whether it has had its one roll at Level 30 (1 in 3 get one - see YouKa). */
    public String ka = "";
    public boolean kaRolled;
    /** With DragonMineZ: drawn as its DragonMineZ character (race, hair, body and colours from DragonMineZ's own
     *  customization) instead of our look. Each character keeps its own DragonMineZ character (DmzPerCharacter). */
    public boolean dmzLook;
    /** Its own colour (menus, HUD, name), picked in the creator or on the slots screen; -1 = its element's colour. */
    public int colour = -1;
    /** A borrowed idle animation: the id of a character it has bond 100 with ("" = none; see YouIdles). */
    public String idle = "";
    /** With DragonMineZ and our look: its DragonMineZ hair drawn on its head too (styled in DragonMineZ's customization). */
    public boolean dmzHair;
    /**
     * Fox Kin: its tail (one of {@link #TAILS}) and colours (-1: the usual orange with a white tip). Skylanders: their
     * wings (one of {@link #WINGS}, -1: the usual ones for them) and each wing's colour (-1: the usual tint). Picked
     * once for free (see YouSlots, FEATURES) - after that, changing them takes a wish on the Dragon Balls ({@link #restyles}).
     */
    public int tail, tailColour = -1, tipColour = -1, wings = -1, wingL = -1, wingR = -1;
    /** The tail / wings have been picked once; wishes for changing them again, not used yet. */
    public boolean featuresSet;
    public int restyles;

    public static final String[] TAILS = {"Classic", "Fluffy", "Long", "Twin Tails", "Nine Tails"};
    /** The wing models a Skylander can wear (NpcRenderer "wing_" + id), and what they're called. */
    public static final String[] WINGS = {"angel", "angel_small", "falcon", "falcon_small", "hawk", "hawk_small", "butterfly", "crystal", "tech", "tattered"};
    public static final String[] WING_NAMES = {"Angel", "Small Angel", "Falcon", "Small Falcon", "Hawk", "Small Hawk", "Butterfly", "Crystal", "Tech", "Tattered"};

    /** Has a tail or wings to style: Fox Kin, or a Skylander (born or made, not an Angel or Devil). */
    public boolean foxTail() { return race == Race.FOX_KIN; }
    public boolean skyWings() { return skylander() && !angel() && !devil && !(path == PATH_DEMON); }
    public boolean styleable() { return foxTail() || skyWings(); }

    public static final int PATH_NONE = 0, PATH_MODIFIED = 1, PATH_DEMON = 2, PATH_SKYLANDER = 3;

    /** The race they are now: the one they were made as, changed by a Government path or a Devil's awakening. */
    public Race shown() {
        if (devil) return Race.DEVIL;
        if (ascended) return Race.ANGEL;
        return switch (path) {
            case PATH_MODIFIED -> Race.MODIFIED_HUMAN;
            case PATH_DEMON -> Race.ARTIFICIAL_DEMON;
            case PATH_SKYLANDER -> Race.ARTIFICIAL_SKYLANDER;
            default -> race;
        };
    }

    /** A demon of any kind: born one, made one, or a Devil now (the demon kit: wings, human form, Devil Unleashed). */
    public boolean demon() { return race == Race.DEMON || path == PATH_DEMON || devil; }

    /** A Skylander of any kind (born or made): Lunar / Solar form, Wing Rush. */
    public boolean skylander() { return race == Race.SKYLANDER || path == PATH_SKYLANDER; }

    /** Has wings (put away or not). */
    public boolean winged() { return race.wings() || path == PATH_DEMON || path == PATH_SKYLANDER || devil || ascended; }

    /** An Angel: made one in creative, or ascended (see YouAngel). */
    public boolean angel() { return race == Race.ANGEL || ascended; }

    /** Wings out right now: winged, and not a demon hiding in human form. */
    public boolean wingsNow() { return winged() && !(demon() && humanForm); }

    public boolean exists() { return !name.isEmpty(); }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putString("name", name);
        t.putString("race", race.name());
        t.putString("element", element.id());
        t.putInt("form", form);
        t.putBoolean("human_form", humanForm);
        t.putInt("path", path);
        t.putBoolean("devil", devil);
        t.putInt("hunger", hunger);
        t.putString("element2", element2 == null ? "" : element2.id());
        t.putBoolean("ascended", ascended);
        t.putInt("lunar_m", lunarM);
        t.putInt("solar_m", solarM);
        t.putString("skin", skin);
        t.putString("ka", ka);
        t.putBoolean("ka_rolled", kaRolled);
        t.putBoolean("dmz_look", dmzLook);
        t.putInt("colour", colour);
        t.putString("idle", idle);
        t.putBoolean("dmz_hair", dmzHair);
        t.putInt("tail", tail); t.putInt("tail_colour", tailColour); t.putInt("tip_colour", tipColour);
        t.putInt("wings", wings); t.putInt("wing_l", wingL); t.putInt("wing_r", wingR);
        t.putBoolean("features_set", featuresSet); t.putInt("restyles", restyles);
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
        s.path = t.getInt("path");
        s.devil = t.getBoolean("devil");
        s.hunger = t.getInt("hunger");
        s.element2 = Element.byId(t.getString("element2"));
        s.ascended = t.getBoolean("ascended");
        s.lunarM = t.getInt("lunar_m");
        s.solarM = t.getInt("solar_m");
        s.skin = com.xenowarriors.you.YouSkins.valid(t.getString("skin")) ? t.getString("skin") : "";
        s.ka = t.getString("ka");
        s.kaRolled = t.getBoolean("ka_rolled");
        s.dmzLook = t.getBoolean("dmz_look");
        s.colour = t.contains("colour") ? validColour(t.getInt("colour")) : -1;     // (characters made before: their element's)
        s.idle = YouIdles.owner(t.getString("idle")) ? t.getString("idle") : "";
        s.dmzHair = t.getBoolean("dmz_hair");
        s.tail = Math.floorMod(t.getInt("tail"), TAILS.length);
        s.tailColour = t.contains("tail_colour") ? validColour(t.getInt("tail_colour")) : -1;
        s.tipColour = t.contains("tip_colour") ? validColour(t.getInt("tip_colour")) : -1;
        s.wings = t.contains("wings") ? validWings(t.getInt("wings")) : -1;
        s.wingL = t.contains("wing_l") ? validColour(t.getInt("wing_l")) : -1;
        s.wingR = t.contains("wing_r") ? validColour(t.getInt("wing_r")) : -1;
        s.featuresSet = t.getBoolean("features_set");
        s.restyles = Math.max(0, t.getInt("restyles"));
        return s;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(name, 32);
        buf.writeEnum(race);
        buf.writeEnum(element);
        buf.writeVarInt(form);
        buf.writeBoolean(humanForm);
        buf.writeVarInt(path);
        buf.writeBoolean(devil);
        buf.writeVarInt(hunger);
        buf.writeUtf(element2 == null ? "" : element2.id(), 16);
        buf.writeBoolean(ascended);
        buf.writeVarInt(lunarM);
        buf.writeVarInt(solarM);
        buf.writeUtf(skin, 64);
        buf.writeUtf(ka, 16);
        buf.writeBoolean(kaRolled);
        buf.writeBoolean(dmzLook);
        buf.writeInt(colour);
        buf.writeUtf(idle, 32);
        buf.writeBoolean(dmzHair);
        buf.writeVarInt(tail); buf.writeInt(tailColour); buf.writeInt(tipColour);
        buf.writeInt(wings); buf.writeInt(wingL); buf.writeInt(wingR);
        buf.writeBoolean(featuresSet); buf.writeVarInt(restyles);
    }

    public static YouSlot read(FriendlyByteBuf buf) {
        YouSlot s = new YouSlot();
        s.name = buf.readUtf(32);
        s.race = buf.readEnum(Race.class);
        s.element = buf.readEnum(Element.class);
        s.form = buf.readVarInt();
        s.humanForm = buf.readBoolean();
        s.path = buf.readVarInt();
        s.devil = buf.readBoolean();
        s.hunger = buf.readVarInt();
        s.element2 = Element.byId(buf.readUtf(16));
        s.ascended = buf.readBoolean();
        s.lunarM = buf.readVarInt();
        s.solarM = buf.readVarInt();
        String sk = buf.readUtf(64);
        s.skin = com.xenowarriors.you.YouSkins.valid(sk) ? sk : "";
        s.ka = buf.readUtf(16);
        s.kaRolled = buf.readBoolean();
        s.dmzLook = buf.readBoolean();
        s.colour = validColour(buf.readInt());
        String id = buf.readUtf(32);
        s.idle = YouIdles.owner(id) ? id : "";
        s.dmzHair = buf.readBoolean();
        s.tail = Math.floorMod(buf.readVarInt(), TAILS.length); s.tailColour = validColour(buf.readInt()); s.tipColour = validColour(buf.readInt());
        s.wings = validWings(buf.readInt()); s.wingL = validColour(buf.readInt()); s.wingR = validColour(buf.readInt());
        s.featuresSet = buf.readBoolean(); s.restyles = Math.max(0, buf.readVarInt());
        return s;
    }

    public YouSlot copy() { return load(save()); }

    /** The character's colour on screens and the HUD: the one picked for it, otherwise their element's. */
    public int color() { return colour >= 0 ? colour : element.color; }

    /** -1 (the element's) or an RGB colour. */
    public static int validColour(int c) { return c < 0 ? -1 : c & 0xFFFFFF; }

    /** -1 (the usual) or one of {@link #WINGS}. */
    public static int validWings(int w) { return w < 0 || w >= WINGS.length ? -1 : w; }

    /** Names: letters, digits, spaces and a few marks, 2-16 characters. */
    public static boolean validName(String n) {
        return n != null && n.length() >= 2 && n.length() <= 16 && n.matches("[A-Za-z0-9 _'\\-]+") && !n.isBlank();
    }
}

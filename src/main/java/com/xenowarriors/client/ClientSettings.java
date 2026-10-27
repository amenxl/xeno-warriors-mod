package com.xenowarriors.client;

import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Your own settings from the M menu, saved in config/xenowarriors-client.properties. */
public final class ClientSettings {
    private static final Path FILE = FMLPaths.CONFIGDIR.get().resolve("xenowarriors-client.properties");
    private static Properties props;

    private ClientSettings() {}

    private static Properties props() {
        if (props == null) {
            props = new Properties();
            if (Files.exists(FILE)) {
                try (Reader r = Files.newBufferedReader(FILE)) {
                    props.load(r);
                } catch (IOException ignored) {}
            }
        }
        return props;
    }

    private static void save() {
        try (Writer w = Files.newBufferedWriter(FILE)) {
            props().store(w, "Xeno Warriors - your settings");
        } catch (IOException ignored) {}
    }

    /** Karma Sensing also picks up friendly life (animals, villagers...). Off = only players and threats. */
    public static boolean senseFriendly() {
        return !"false".equals(props().getProperty("sense_friendly", "true"));
    }

    public static void setSenseFriendly(boolean on) {
        props().setProperty("sense_friendly", String.valueOf(on));
        save();
    }

    /** With Epic Fight: its own animations in battle mode (off = Mikiko's and Rhalix's own moves). */
    public static boolean efWholeBattleMode() {
        return "true".equals(props().getProperty("ef_whole_battle_mode", "false"));
    }

    public static void setEfWholeBattleMode(boolean on) {
        props().setProperty("ef_whole_battle_mode", String.valueOf(on));
        save();
    }

    public static boolean efAlwaysBattle() {
        return !"false".equals(props().getProperty("ef_always_battle", "true"));
    }

    public static void setEfAlwaysBattle(boolean on) {
        props().setProperty("ef_always_battle", String.valueOf(on));
        save();
    }

    /** The Kingdom Hearts style HUD (portrait gauge, skill list, party cards). Off = the classic Xeno Warriors HUD. */
    public static boolean khHud() { return !"false".equals(props().getProperty("kh_hud", "true")); }

    public static void setKhHud(boolean on) { props().setProperty("kh_hud", String.valueOf(on)); save(); }

    /** Also show Minecraft's own hearts, armor and hunger next to the hotbar. */
    public static boolean vanillaBars() { return "true".equals(props().getProperty("vanilla_bars", "false")); }

    public static void setVanillaBars(boolean on) { props().setProperty("vanilla_bars", String.valueOf(on)); save(); }

    /** Skill list always visible (off = only while holding Control). */
    public static boolean skillsAlways() { return "true".equals(props().getProperty("skills_always", "false")); }

    public static void setSkillsAlways(boolean on) { props().setProperty("skills_always", String.valueOf(on)); save(); }

    /** Cel-shaded look: flat-shaded textures with ink lines and a black outline around characters. Off by default. */
    /** Ultima's hand-drawn (hi-res) faces instead of the old pixel ones. */
    public static boolean drawnFaces() { return "true".equals(props().getProperty("drawn_faces", "false")); }     // (off by default: pixel faces)

    public static void setDrawnFaces(boolean on) { props().setProperty("drawn_faces", String.valueOf(on)); save(); }

    public static boolean celShading() { return "true".equals(props().getProperty("cel_shading", "false")); }

    public static void setCelShading(boolean on) { props().setProperty("cel_shading", String.valueOf(on)); save(); }
}

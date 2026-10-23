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

    /** Mikiko's and Rhalix's heads taper to an anime-style chin (a trial; off = the plain square head). */
    public static boolean animeJaw() {
        return !"false".equals(props().getProperty("anime_jaw", "true"));
    }

    public static void setAnimeJaw(boolean on) {
        props().setProperty("anime_jaw", String.valueOf(on));
        save();
    }

    /** With Epic Fight: always stay in battle mode (its mode key can't switch it off; only this setting can). */
    public static boolean efAlwaysBattle() {
        return !"false".equals(props().getProperty("ef_always_battle", "true"));
    }

    public static void setEfAlwaysBattle(boolean on) {
        props().setProperty("ef_always_battle", String.valueOf(on));
        save();
    }
}

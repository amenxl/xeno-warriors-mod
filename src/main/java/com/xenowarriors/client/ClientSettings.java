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
}

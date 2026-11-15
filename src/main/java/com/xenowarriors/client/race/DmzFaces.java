package com.xenowarriors.client.race;

import com.xenowarriors.client.compat.DmzCompat;
import com.xenowarriors.client.compat.DmzForms;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;

import java.util.HashMap;
import java.util.Map;

/**
 * Faces for what a character does in DragonMineZ (tools/xw/faces/dmz_faces.py made the new ones): yelling as they
 * power up, focused and yelling while charging a Ki attack, concentrating through the training minigames (straining
 * in the Gravity one, humming to the Rhythm one, thinking in the Memory one), eyes shut in the God Ritual and while
 * recovering in their sleep, raging, dazed when stunned, a scream the moment they transform.
 */
public final class DmzFaces {
    private DmzFaces() {}

    private static final Map<Integer, String> LAST_FORM = new HashMap<>();
    private static final Map<Integer, Integer> TRANSFORMED = new HashMap<>();
    private static boolean loaded, have;

    public static String face(AbstractClientPlayer p) {
        if (!loaded) { loaded = true; have = net.minecraftforge.fml.ModList.get().isLoaded("dragonminez"); }
        if (!have) return null;
        // the moment they transform (their DragonMineZ form changes): a scream for two seconds
        String form = DmzForms.form(p);
        String was = LAST_FORM.put(p.getId(), form == null ? "" : form);
        if (was != null && !was.equals(form == null ? "" : form) && form != null) TRANSFORMED.put(p.getId(), p.tickCount);
        Integer at = TRANSFORMED.get(p.getId());
        if (at != null && p.tickCount - at >= 0 && p.tickCount - at < 40) return "transform";
        // the training minigames (you, while one's open)
        if (p == Minecraft.getInstance().player && Minecraft.getInstance().screen != null) {
            String n = Minecraft.getInstance().screen.getClass().getName();
            if (n.startsWith("com.dragonminez.client.gui.character.minigames.")) {
                if (n.contains("Rythm") || n.contains("Rhythm")) return (p.tickCount / 10) % 4 == 3 ? "blink" : "rhythm";
                if (n.contains("Gravity") || n.contains("Ultimate")) return "strain";
                if (n.contains("Memory")) return "thinking";
                return "concentrate";                                             // (Control, Precision)
            }
        }
        if (DmzCompat.status(p, "isStunned") || DmzCompat.status(p, "isStunEffect")) return "dizzy";
        if (DmzCompat.status(p, "isKnockedDown")) return "hurt_heavy";
        if (DmzCompat.status(p, "isInGodRitual")) return "meditate";
        if (DmzCompat.status(p, "isSleepRecovering")) return "meditate";
        if (DmzCompat.chargingAttack(p)) return "beam";
        if (DmzCompat.chargingKi(p)) return (p.tickCount / 30) % 3 == 2 ? "strain" : "powerup";
        if (DmzCompat.status(p, "isRageActive")) return "rage";
        if (DmzCompat.status(p, "isSharingKi")) return "determined";
        if (DmzCompat.status(p, "isSurgeActive")) return "fierce";
        return null;
    }
}

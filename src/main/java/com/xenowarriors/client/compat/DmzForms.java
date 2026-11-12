package com.xenowarriors.client.compat;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.client.ClientCharacterData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * DragonMineZ transformations on Mikiko and Rhalix.
 * - Forms that turn you into something else entirely (Oozaru and Golden Oozaru, Super Saiyan 4, Frost Demon /
 *   Majin / Bio-Android evolutions, giant forms...) are drawn by DragonMineZ itself.
 * - Every other form (Super Saiyan 1-3, Ikari, Kaioken, Ultimate, Shiyoken, Super Namekian...) keeps the Xeno Warriors
 *   look but takes on the form: hair recolored (Super Saiyan gold etc.), a bit bigger or smaller like the form, and an
 *   aura in the form's color.
 * Form details follow DragonMineZ's own form list (its default settings).
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class DmzForms {
    /** hair color (-1 keep), aura color (-1 none), size, drawn by DragonMineZ */
    private record Form(int hair, int aura, float scale, boolean dmz) {}

    private static final int GOLD = 0xFFE89E;
    private static final Map<String, Form> FORMS = new HashMap<>();

    static {
        // Saiyan
        put("supersaiyan", 0xFFEDB3, 0xFFD700, 1.0F, false);
        put("supersaiyangrade2", 0xFFEDB3, 0xFFD700, 1.07F, false);
        put("supersaiyangrade3", 0xFFEDB3, 0xFFD700, 1.28F, false);
        put("supersaiyanmastered", GOLD, 0xFFD700, 1.0F, false);
        put("supersaiyan2", GOLD, 0xFFD700, 1.0F, false);
        put("supersaiyan3", GOLD, 0xFFD700, 1.0F, false);
        put("ikari", GOLD, 0x40FF00, 1.17F, false);
        put("ssjhybrid", 0xFFE89E, 0x40FF00, 1.23F, false);
        put("ssjfullpower", 0x9EFE53, 0x40FF00, 1.45F, false);
        put("supersaiyan4", -1, 0xFFD633, 1.0F, true);
        put("oozaru", -1, -1, 4F, true);
        put("goldenoozaru", -1, 0xFFD700, 4F, true);
        // Human
        put("buffed", -1, -1, 1.2F, false);
        put("fullpower", GOLD, -1, 1.0F, false);
        put("overdrive", GOLD, 0xFFFD99, 1.17F, false);
        put("solaris", GOLD, -1, 1.07F, false);
        put("shiyoken", -1, 0xA10000, 1.17F, false);
        put("shin_shiyoken", -1, 0xA10000, 1.38F, false);
        put("chou_shiyoken", -1, 0xA10000, 1.6F, true);
        put("androidbase", -1, -1, 1.0F, false);
        put("superandroid", GOLD, -1, 1.1F, false);
        put("fusedandroid", 0xE65332, -1, 1.4F, false);
        // Namekian
        put("supernamekian", -1, 0x7FFF00, 1.1F, false);
        put("evilnamek", -1, 0x570B0B, 1.0F, false);
        put("buffednamek", -1, 0x570B0B, 1.38F, false);
        put("giant", -1, -1, 3.8F, true);
        put("evilgiantnamek", -1, 0x570B0B, 4F, true);
        // any race
        put("ultimate", -1, 0xFFFFFF, 1.0F, false);
        for (String x : new String[]{"x2", "x3", "x4", "x10", "x20"}) put(x, -1, 0xDB182C, 1.0F, false);
    }

    private static void put(String name, int hair, int aura, float scale, boolean dmz) {
        FORMS.put(name, new Form(hair, aura, scale, dmz));
    }

    private static Boolean loaded;
    private static boolean broken;
    private static Capability<?> cap;
    private static Method getCharacter, getActiveForm, getActiveStackForm, getCustomModel, getRace;

    private DmzForms() {}

    private static boolean ready() {
        if (loaded == null) loaded = ModList.get() != null && ModList.get().isLoaded("dragonminez");
        if (!loaded || broken) return false;
        if (cap == null) {
            try {
                cap = (Capability<?>) Class.forName("com.dragonminez.common.stats.StatsCapability").getField("INSTANCE").get(null);
                Class<?> data = Class.forName("com.dragonminez.common.stats.StatsData");
                getCharacter = data.getMethod("getCharacter");
                Class<?> ch = getCharacter.getReturnType();
                getActiveForm = ch.getMethod("getActiveForm");
                try { getActiveStackForm = ch.getMethod("getActiveStackForm"); } catch (NoSuchMethodException ignored) {}
                try { getRace = ch.getMethod("getRaceName"); } catch (NoSuchMethodException ignored) {}
            } catch (Throwable t) {
                broken = true;
                return false;
            }
        }
        return true;
    }

    private static String call(Player p, Method m) {
        if (m == null || !ready()) return null;
        try {
            Object data = p.getCapability(cap).resolve().orElse(null);
            if (data == null) return null;
            Object v = m.invoke(getCharacter.invoke(data));
            String s = v == null ? null : v.toString().toLowerCase();
            return s == null || s.isEmpty() || s.equals("base") || s.equals("none") ? null : s;
        } catch (Throwable t) {
            return null;
        }
    }

    /** In DragonMineZ's Kaioken (a "stack" form) right now? */
    public static boolean kaioken(Player p) { return call(p, getActiveStackForm) != null; }

    public static String form(Player p) {
        return call(p, getActiveForm);
    }

    /** DragonMineZ race ("human", "saiyan"...), or null. */
    public static String race(Player p) {
        return call(p, getRace);
    }

    /**
     * Mikiko's fox aura level from DragonMineZ's Human "Shiyoken" forms: 0 none, 1 Shiyoken, 2 Shin Shiyoken,
     * 3 Chou Shiyoken (drawn as the second level, bigger). Only for Mikiko with the Human race.
     */
    public static int shiyokenLevel(Player p) {
        if (!(p instanceof AbstractClientPlayer cp) || ClientCharacterData.get(cp) != CharacterType.MIKIKO) return 0;
        String f = form(p);
        if (f == null || !f.contains("shiyoken")) return 0;
        String r = race(p);
        if (r != null && !r.contains("human")) return 0;
        return f.startsWith("chou") ? 3 : f.startsWith("shin") ? 2 : 1;
    }

    /**
     * Mikiko, Rhalix and Kyrie's own Super Saiyan 4 (instead of DragonMineZ's body): they keep their look with a wild dark
     * SSJ4 mane, gold eyes ringed in red and a red-gold aura.
     */
    private static final Form SSJ4_OWN = new Form(0x2A1B1B, 0xFF5A33, 1.05F, false);

    /**
     * Mikiko, Rhalix and Kyrie's face set for their DragonMineZ form ("ssj" teal eyes, "ssj3" no eyebrows, "ssj4" gold eyes
     * ringed in red, "lssj" blank white eyes), or null for their normal face. Made by tools/xw/formfaces/faces.py.
     */
    public static String faceSet(Player p) {
        if (!(p instanceof AbstractClientPlayer cp)) return null;
        CharacterType t = ClientCharacterData.get(cp);
        if (t != CharacterType.MIKIKO && t != CharacterType.RHALIX && t != CharacterType.KYRIE && t != CharacterType.YURI) return null;
        if (ClientCharacterData.isPreviewing() && p == Minecraft.getInstance().player) return null;
        String f = form(p);
        if (f == null) return null;
        return switch (f) {
            case "supersaiyan", "supersaiyangrade2", "supersaiyangrade3", "supersaiyanmastered", "supersaiyan2",
                    "ikari", "ssjhybrid" -> "ssj";
            case "supersaiyan3" -> "ssj3";
            case "supersaiyan4" -> "ssj4";
            case "ssjfullpower" -> "lssj";
            default -> null;
        };
    }

    /** In their own Super Saiyan 4 with the top off: Mikiko and Rhalix (Kyrie keeps her clothes on). */
    public static boolean ssj4Shirtless(Player p) {
        CharacterType t = ClientCharacterData.get((AbstractClientPlayer) p);
        return ssj4Own(p) && t != CharacterType.KYRIE && t != CharacterType.YURI;
    }

    /** Mikiko, Rhalix or Kyrie in Super Saiyan 4: drawn by Xeno Warriors with the custom SSJ4 hair, eyes and aura. */
    public static boolean ssj4Own(Player p) {
        if (!(p instanceof AbstractClientPlayer cp)) return false;
        CharacterType t = ClientCharacterData.get(cp);
        if (t != CharacterType.MIKIKO && t != CharacterType.RHALIX && t != CharacterType.KYRIE && t != CharacterType.YURI) return false;
        if (ClientCharacterData.isPreviewing() && p == Minecraft.getInstance().player) return false;
        return "supersaiyan4".equals(form(p));
    }

    private static Form info(Player p) {
        if (ClientCharacterData.isPreviewing() && p == Minecraft.getInstance().player) return null;   // previews: the character as it is, not your form
        String f = form(p);
        if (f == null) return null;
        if ("supersaiyan4".equals(f) && ssj4Own(p)) return SSJ4_OWN;
        Form known = FORMS.get(f);
        return known != null ? known : new Form(-1, -1, 1F, true);   // a form we don't know: let DragonMineZ draw it
    }

    private static boolean ours(Player p) {
        return p instanceof AbstractClientPlayer cp && ClientCharacterData.get(cp) != CharacterType.NONE;
    }

    /** This Xeno Warriors player is in a form DragonMineZ should draw (Oozaru, giant forms...). */
    public static boolean dmzDraws(Player p) {
        if (!ours(p) || giantFox(p)) return false;
        if (ClientCharacterData.isPreviewing() && p == net.minecraft.client.Minecraft.getInstance().player) return false;   // previews: the character, not your form
        // Ai-oni (for now): DragonMineZ draws and animates her (its body, attacks and animations), with her
        // 3D details still put on by DragonMineZ's layer forwarder
        if (ClientCharacterData.get((AbstractClientPlayer) p) == CharacterType.AIONI && net.minecraftforge.fml.ModList.get().isLoaded("dragonminez")) return true;
        Form f = info(p);
        return f != null && f.dmz();
    }

    /** Mikiko as an Oozaru becomes a giant fox instead of a great ape (drawn by GiantFox). */
    public static boolean giantFox(Player p) {
        if (!(p instanceof AbstractClientPlayer cp) || ClientCharacterData.get(cp) != CharacterType.MIKIKO) return false;
        if (ClientCharacterData.isPreviewing() && p == net.minecraft.client.Minecraft.getInstance().player) return false;
        String f = form(p);
        return "oozaru".equals(f) || "goldenoozaru".equals(f);
    }

    public static boolean goldenFox(Player p) {
        return "goldenoozaru".equals(form(p));
    }

    /** Hair style for the current form: 0 their own, 1 Super Saiyan spikes, 2 Super Saiyan 2, 3 Super Saiyan 3, 4 SSJ4 mane. */
    public static int hairStyle(Player p) {
        String f = form(p);
        if (f == null) return 0;
        return switch (f) {
            case "supersaiyan", "supersaiyangrade2", "supersaiyangrade3", "supersaiyanmastered",
                    "ikari", "ssjhybrid", "ssjfullpower" -> 1;
            case "supersaiyan2" -> 2;
            case "supersaiyan3" -> 3;
            case "supersaiyan4" -> ssj4Own(p) ? 4 : 0;
            default -> 0;
        };
    }

    /** Hair color for the current form (RGB), or -1 to keep their own. */
    public static int hairColor(Player p) {
        Form f = info(p);
        return f == null || f.dmz() ? -1 : f.hair();
    }

    private static int auraColor(Player p) {
        Form f = info(p);
        int stack = -1;
        String s = call(p, getActiveStackForm);
        if (s != null && FORMS.containsKey(s)) stack = FORMS.get(s).aura();
        if (f == null || f.dmz()) return stack;
        return f.aura() >= 0 ? f.aura() : stack;
    }

    // ---------------------------------------------------------------- size and aura

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void onRenderPre(RenderPlayerEvent.Pre event) {
        Player p = event.getEntity();
        if (event.isCanceled() || !ours(p)) return;
        Form f = info(p);
        float s = f == null || f.dmz() ? 1F : f.scale();
        com.xenowarriors.client.PoseGuard.push(event.getPoseStack());
        if (s != 1F) event.getPoseStack().scale(s, s, s);
    }

    @SubscribeEvent
    public static void onRenderPost(RenderPlayerEvent.Post event) {
        if (ours(event.getEntity())) com.xenowarriors.client.PoseGuard.pop(event.getPoseStack());
    }

    /**
     * The aura of a form (or of powering up) on a Xeno Warriors player we draw: DragonMineZ's own aura when it would
     * show one; without it (or if it can't be queued), our Dragon Ball style flame shell in the form's colour. (It
     * used to be a cloud of dust motes - those are gone.)
     */
    static int auraFor(AbstractClientPlayer p) {
        if (!ours(p) || dmzDraws(p) || giantFox(p)) return -1;
        return auraColor(p);
    }

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOW)
    public static void onRenderAura(RenderPlayerEvent.Post event) {
        if (!(event.getEntity() instanceof AbstractClientPlayer p)) return;
        if (p == Minecraft.getInstance().player) DmzAuraBridge.lastWhy = "hook: ready " + ready() + " ours " + ours(p) + " dmzDraws " + dmzDraws(p);
        if (!ready() || !ours(p) || dmzDraws(p) || giantFox(p)) return;
        Minecraft mc = Minecraft.getInstance();
        if (p == mc.player && mc.options.getCameraType().isFirstPerson()) return;
        boolean on = DmzAuraBridge.auraOn(p);
        if (p == mc.player) DmzAuraBridge.lastWhy = "hook reached, aura " + on;
        if (on && DmzAuraBridge.queue(p, event.getPoseStack(), event.getPartialTick(), event.getPackedLight())) return;
        int c = auraColor(p);
        if (c >= 0 && (on || DmzCompat.charging(p))) com.xenowarriors.client.DbzAura.mark(p.getId(), c, 2);
    }
}

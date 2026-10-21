package com.xenowarriors.client.compat;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.client.ClientCharacterData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.particles.DustParticleOptions;
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
    private static Method getCharacter, getActiveForm, getActiveStackForm, getCustomModel;

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

    public static String form(Player p) {
        return call(p, getActiveForm);
    }

    private static Form info(Player p) {
        String f = form(p);
        if (f == null) return null;
        Form known = FORMS.get(f);
        return known != null ? known : new Form(-1, -1, 1F, true);   // a form we don't know: let DragonMineZ draw it
    }

    private static boolean ours(Player p) {
        return p instanceof AbstractClientPlayer cp && ClientCharacterData.get(cp) != CharacterType.NONE;
    }

    /** This Xeno Warriors player is in a form DragonMineZ should draw (Oozaru, giant forms...). */
    public static boolean dmzDraws(Player p) {
        if (!ours(p)) return false;
        Form f = info(p);
        return f != null && f.dmz();
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
        event.getPoseStack().pushPose();
        if (s != 1F) event.getPoseStack().scale(s, s, s);
    }

    @SubscribeEvent
    public static void onRenderPost(RenderPlayerEvent.Post event) {
        if (ours(event.getEntity())) event.getPoseStack().popPose();
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || mc.level == null || mc.isPaused() || !ready()) return;
        for (AbstractClientPlayer p : mc.level.players()) {
            if (!ours(p) || dmzDraws(p)) continue;
            int c = auraColor(p);
            if (c < 0 || (p == mc.player && mc.options.getCameraType().isFirstPerson())) continue;
            boolean strong = DmzCompat.aura(p) || DmzCompat.charging(p);
            var dust = new DustParticleOptions(new Vector3f(((c >> 16) & 255) / 255F, ((c >> 8) & 255) / 255F, (c & 255) / 255F),
                    strong ? 1.3F : 0.8F);
            var r = p.getRandom();
            int n = strong ? 4 : (p.tickCount % 3 == 0 ? 1 : 0);
            Form f = info(p);
            double size = f == null ? 1 : f.scale();
            for (int i = 0; i < n; i++) {
                double a = r.nextDouble() * Math.PI * 2, rad = (0.45 + r.nextDouble() * 0.35) * size;
                mc.level.addParticle(dust, p.getX() + Math.cos(a) * rad, p.getY() + r.nextDouble() * 1.8 * size,
                        p.getZ() + Math.sin(a) * rad, 0, strong ? 0.12 : 0.05, 0);
            }
        }
    }
}

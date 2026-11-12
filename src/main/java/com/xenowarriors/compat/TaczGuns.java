package com.xenowarriors.compat;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;
import java.util.function.Supplier;

/**
 * Optional bridge to Timeless and Classics Zero (TACZ) guns, all by reflection so the mod runs fine without it.
 * Lets mobs hold a TACZ gun and fire it like a player would.
 */
public final class TaczGuns {
    /** Guns Government soldiers carry (automatic/closed-bolt ones, which mobs can fire without bolting). */
    public static final String[] GOV_GUNS = {"tacz:m4a1", "tacz:hk416d", "tacz:hk_mp5a5", "tacz:ump45", "tacz:scar_l", "tacz:g36k", "tacz:p320", "tacz:m1911"};
    /** Heavy Gunners: machine guns, battle rifles and shotguns (whichever this TACZ has). */
    public static final String[] HEAVY_GUNS = {"tacz:m249", "tacz:rpk", "tacz:aa12", "tacz:m870", "tacz:spas_12", "tacz:scar_h", "tacz:fn_fal", "tacz:ak47"};
    /** Marksmen: sniper and marksman rifles. */
    public static final String[] SNIPER_GUNS = {"tacz:ai_awp", "tacz:m700", "tacz:m107", "tacz:m95", "tacz:mk14", "tacz:springfield1873"};
    /** Skeletons that found a gun: old sidearms and shotguns. */
    public static final String[] SKELETON_GUNS = {"tacz:m1911", "tacz:glock_17", "tacz:p320", "tacz:m870", "tacz:db_short", "tacz:hk_mp5a5"};

    private static final java.util.Map<String, Boolean> EXISTS = new java.util.HashMap<>();

    /** Does this TACZ (its gun packs) have a gun with this id? */
    public static boolean exists(String id) {
        if (!available()) return false;
        return EXISTS.computeIfAbsent(id, k -> {
            try {
                Object idx = Class.forName("com.tacz.guns.api.TimelessAPI").getMethod("getCommonGunIndex", ResourceLocation.class).invoke(null, new ResourceLocation(k));
                return idx instanceof java.util.Optional<?> o && o.isPresent();
            } catch (Throwable t) { return false; }
        });
    }

    /** A random gun id from the list that this TACZ has (or one of the standard Gov guns, or null). */
    public static String pick(String[] ids, net.minecraft.util.RandomSource r) {
        java.util.List<String> ok = new java.util.ArrayList<>();
        for (String id : ids) if (exists(id)) ok.add(id);
        if (ok.isEmpty()) for (String id : GOV_GUNS) if (exists(id)) ok.add(id);
        return ok.isEmpty() ? null : ok.get(r.nextInt(ok.size()));
    }
    private static Boolean loaded;
    private static Method create, setId, setAmmo, setBarrel, forceBuild, draw, shoot, initialData, getAmmo, setAmmoCount;
    private static Class<?> operator, igun;
    private static boolean broken;

    private TaczGuns() {}

    public static boolean available() {
        if (loaded == null) loaded = ModList.get().isLoaded("tacz") && init();
        return loaded && !broken;
    }

    private static boolean init() {
        try {
            Class<?> b = Class.forName("com.tacz.guns.api.item.builder.GunItemBuilder");
            create = b.getMethod("create");
            setId = b.getMethod("setId", ResourceLocation.class);
            setAmmo = b.getMethod("setAmmoCount", int.class);
            setBarrel = b.getMethod("setAmmoInBarrel", boolean.class);
            forceBuild = b.getMethod("forceBuild");
            operator = Class.forName("com.tacz.guns.api.entity.IGunOperator");
            draw = operator.getMethod("draw", Supplier.class);
            shoot = operator.getMethod("shoot", Supplier.class, Supplier.class);
            initialData = operator.getMethod("initialData");
            igun = Class.forName("com.tacz.guns.api.item.IGun");
            getAmmo = igun.getMethod("getCurrentAmmoCount", ItemStack.class);
            setAmmoCount = igun.getMethod("setCurrentAmmoCount", ItemStack.class, int.class);
            return true;
        } catch (Throwable t) {
            com.xenowarriors.XenoWarriors.LOGGER.warn("TACZ found but its API didn't match - Gov guns are off: {}", t.toString());
            return false;
        }
    }

    public static ItemStack gun(String id, int ammo) {
        if (!available()) return ItemStack.EMPTY;
        try {
            Object b = create.invoke(null);
            b = setId.invoke(b, new ResourceLocation(id));
            b = setAmmo.invoke(b, ammo);
            b = setBarrel.invoke(b, true);
            return (ItemStack) forceBuild.invoke(b);
        } catch (Throwable t) { broken = true; return ItemStack.EMPTY; }
    }

    public static boolean isGun(ItemStack s) { return available() && igun.isInstance(s.getItem()); }

    /** Takes the gun in hand out (TACZ needs this before the first shot, and again after loading). */
    public static void draw(LivingEntity e) {
        if (!available() || !operator.isInstance(e)) return;
        try { initialData.invoke(e); draw.invoke(e, (Supplier<ItemStack>) e::getMainHandItem); } catch (Throwable t) { broken = true; }
    }

    /** Fires toward pitch/yaw; true if a shot went off. Keeps the magazine topped up (soldiers have spare mags). */
    public static boolean shoot(LivingEntity e, float pitch, float yaw) { return shoot(e, pitch, yaw, true); }

    /** Fires toward pitch/yaw; true if a shot went off. {@code refill}: keep the magazine topped up (else the caller reloads it). */
    public static boolean shoot(LivingEntity e, float pitch, float yaw, boolean refill) {
        if (!available() || !operator.isInstance(e)) return false;
        try {
            ItemStack g = e.getMainHandItem();
            if (refill && igun.isInstance(g.getItem()) && (int) getAmmo.invoke(g.getItem(), g) < 3) setAmmoCount.invoke(g.getItem(), g, 30);
            Object r = shoot.invoke(e, (Supplier<Float>) () -> pitch, (Supplier<Float>) () -> yaw);
            return r != null && r.toString().equals("SUCCESS");
        } catch (Throwable t) { broken = true; return false; }
    }

    /** Rounds in the magazine (-1 if not a gun / unknown). */
    public static int ammo(ItemStack g) {
        if (!isGun(g)) return -1;
        try { return (int) getAmmo.invoke(g.getItem(), g); } catch (Throwable t) { return -1; }
    }

    public static void setAmmo(ItemStack g, int n) {
        if (!isGun(g)) return;
        try { setAmmoCount.invoke(g.getItem(), g, n); } catch (Throwable ignored) {}
    }

    /** A gun's own numbers from its TACZ data: {magazine size, reload ticks (empty mag), rounds per minute}; defaults when unknown. */
    public static int[] stats(ItemStack g) {
        int[] out = {30, 50, 600};
        if (!isGun(g)) return out;
        try {
            Object id = igun.getMethod("getGunId", ItemStack.class).invoke(g.getItem(), g);
            Object idx = Class.forName("com.tacz.guns.api.TimelessAPI").getMethod("getCommonGunIndex", ResourceLocation.class).invoke(null, id);
            if (!(idx instanceof java.util.Optional<?> o) || o.isEmpty()) return out;
            Object data = o.get().getClass().getMethod("getGunData").invoke(o.get());
            try { int n = (int) data.getClass().getMethod("getAmmoAmount").invoke(data); if (n > 0) out[0] = n; } catch (Throwable ignored) {}
            try {
                Object feed = call(call(data, "getReloadData"), "getFeed");
                float sec = ((Number) call(feed, "getEmptyTime")).floatValue();
                if (sec > 0.1F) out[1] = Math.round(sec * 20F);
            } catch (Throwable ignored) {}
            try { int rpm = (int) data.getClass().getMethod("getRoundsPerMinute").invoke(data); if (rpm > 0) out[2] = rpm; }
            catch (Throwable t) {
                try {                                                         // (newer TACZ: per fire mode)
                    for (Method m : data.getClass().getMethods()) if (m.getName().equals("getRoundsPerMinute") && m.getParameterCount() == 1) {
                        Object mode = m.getParameterTypes()[0].getEnumConstants()[0];
                        int rpm = (int) m.invoke(data, mode); if (rpm > 0) out[2] = rpm; break;
                    }
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        return out;
    }

    private static Object call(Object o, String name) throws Exception { return o.getClass().getMethod(name).invoke(o); }

    /** Starts TACZ's own reload on a mob holding a gun (for its animation; a mob with no ammo items may not get one). */
    public static void reload(LivingEntity e) {
        if (!available() || !operator.isInstance(e)) return;
        try { operator.getMethod("reload").invoke(e); } catch (Throwable ignored) {}
    }

    /** Is this one of TACZ's bullets? */
    public static boolean isBullet(Entity e) { return e != null && e.getClass().getName().startsWith("com.tacz.guns.entity"); }
}

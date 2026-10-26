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
    public static boolean shoot(LivingEntity e, float pitch, float yaw) {
        if (!available() || !operator.isInstance(e)) return false;
        try {
            ItemStack g = e.getMainHandItem();
            if (igun.isInstance(g.getItem()) && (int) getAmmo.invoke(g.getItem(), g) < 3) setAmmoCount.invoke(g.getItem(), g, 30);
            Object r = shoot.invoke(e, (Supplier<Float>) () -> pitch, (Supplier<Float>) () -> yaw);
            return r != null && r.toString().equals("SUCCESS");
        } catch (Throwable t) { broken = true; return false; }
    }

    /** Is this one of TACZ's bullets? */
    public static boolean isBullet(Entity e) { return e != null && e.getClass().getName().startsWith("com.tacz.guns.entity"); }
}

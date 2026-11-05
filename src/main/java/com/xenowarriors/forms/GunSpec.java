package com.xenowarriors.forms;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * How each summoned gun (Hekublade gun or wire gun) handles - the same idea as a TACZ gun's data: fire mode, fire
 * rate, hip-fire and aimed (ADS) spread, aim zoom and recoil. Shared by the server (the shot itself) and the client
 * (aiming, the crosshair, the view model).
 * <ul>
 * <li>Pistol: semi-auto (one shot a click), 300 rounds a minute at most, small zoom.</li>
 * <li>Rifle: full auto (hold to fire), 400 rounds a minute, pierces one target, scoped (most zoom).</li>
 * <li>Shotgun: pump action - 7 pellets a shot, a pump between shots, a little zoom.</li>
 * </ul>
 */
public enum GunSpec {
    //       mode            ticks dmg   range pellets pierce  hip   ads   pellet hip/ads  zoom   kick yaw   bloom  sightY sightZ dispY dispZ muzzleY muzzleZ aimDist aimTilt
    PISTOL(Mode.SEMI, 4, 5.0F, 40, 1, 1, 2.0F, 0.2F, 0F, 0F, 1.25F, 1.6F, 0.6F, 0.6F, 11.0F, 3.75F, 4.5F, 1F, 9.1F, 1.7F, 0.62F, 2.5F),
    RIFLE(Mode.AUTO, 3, 3.5F, 80, 1, 2, 2.6F, 0.12F, 0F, 0F, 2.5F, 0.85F, 0.45F, 0.35F, 11.95F, 6F, 4F, 0F, 9.0F, -7.5F, 0.5F, 0F),
    SHOTGUN(Mode.PUMP, 16, 3.2F, 18, 7, 1, 1.4F, 0.4F, 5.5F, 3.8F, 1.15F, 4.2F, 1.2F, 1.2F, 10.3F, -4.3F, 4F, 0F, 9.2F, -5.1F, 0.95F, 0F);

    /** SEMI: one shot a click. AUTO: fires while held. PUMP: one shot a click, with a pump between. */
    public enum Mode { SEMI, AUTO, PUMP }

    public final Mode mode;
    /** Ticks between shots (the fire rate: 1200 / ticks rounds a minute). */
    public final int interval;
    public final float damage, range;
    public final int pellets, pierce;
    /** Spread in degrees: standing hip fire, fully aimed. Pellet cone (shotguns) on top: hip / aimed. */
    public final float hipSpread, adsSpread, pelletHip, pelletAds;
    /** Aim magnification (TACZ's "iron zoom"): the field of view narrows by this much when fully aimed. */
    public final float zoom;
    /** Camera kick per shot (degrees up, max sideways) and the spread each shot adds (degrees, hip fire). */
    public final float kick, kickYaw, bloom;
    /**
     * The model (pixels): the point you aim over (front sight top / scope lens), the first-person display offset, the
     * muzzle; aimed, that point sits on the middle of the screen this far away (blocks), the gun tilted up this much
     * (degrees) so the rear sight drops just under the front one.
     */
    public final float sightY, sightZ, dispY, dispZ, muzzleY, muzzleZ, aimDist, aimTilt;

    GunSpec(Mode mode, int interval, float damage, float range, int pellets, int pierce, float hipSpread, float adsSpread,
            float pelletHip, float pelletAds, float zoom, float kick, float kickYaw, float bloom,
            float sightY, float sightZ, float dispY, float dispZ, float muzzleY, float muzzleZ, float aimDist, float aimTilt) {
        this.mode = mode; this.interval = interval; this.damage = damage; this.range = range; this.pellets = pellets; this.pierce = pierce;
        this.hipSpread = hipSpread; this.adsSpread = adsSpread; this.pelletHip = pelletHip; this.pelletAds = pelletAds;
        this.zoom = zoom; this.kick = kick; this.kickYaw = kickYaw; this.bloom = bloom;
        this.sightY = sightY; this.sightZ = sightZ; this.aimDist = aimDist; this.aimTilt = aimTilt; this.dispY = dispY; this.dispZ = dispZ; this.muzzleY = muzzleY; this.muzzleZ = muzzleZ;
    }

    /** Time to aim in / out (TACZ guns: 0.13-0.16 s). */
    public static final int AIM_TICKS = 3;
    /** Walking speed while aimed (TACZ rifles: -15%). */
    public static final float AIM_MOVE = 0.8F;

    public static GunSpec of(Form f) {
        return switch (f) {
            case RIFLE -> RIFLE;
            case SHOTGUN -> SHOTGUN;
            default -> PISTOL;
        };
    }

    public int rpm() { return 1200 / interval; }

    /** The fire mode's name (tooltip / guide). */
    public String fireModeLabel() {
        return switch (mode) { case SEMI -> "Semi-auto"; case AUTO -> "Full auto"; case PUMP -> "Pump action"; };
    }

    /**
     * Inaccuracy in degrees right now: hip spread x movement, blended to the aimed spread by the aim progress (0-1),
     * plus the bloom from recent shots (mostly gone when aimed).
     */
    public float spread(float aim, boolean moving, boolean airborne, boolean sneaking, float bloomNow) {
        float hip = hipSpread * (moving ? 1.5F : 1F) * (airborne ? 2.2F : 1F) * (sneaking ? 0.75F : 1F);
        float ads = adsSpread * (moving ? 1.3F : 1F) * (airborne ? 3F : 1F);
        return Mth.lerp(Mth.clamp(aim, 0F, 1F), hip, ads) + bloomNow * (1F - 0.85F * Mth.clamp(aim, 0F, 1F));
    }

    /** The most a client may claim (anything above is clamped). */
    public float maxSpread() { return hipSpread * 1.5F * 2.2F + bloom * 6F; }

    /** A direction within a cone of {@code deg} degrees around {@code dir} (uniform over the cone's disc). */
    public static Vec3 cone(Vec3 dir, float deg, RandomSource r) {
        if (deg <= 0F) return dir;
        double a = Math.toRadians(deg) * Math.sqrt(r.nextDouble()), phi = r.nextDouble() * Math.PI * 2;
        Vec3 up = Math.abs(dir.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 u = dir.cross(up).normalize(), v = u.cross(dir).normalize();
        return dir.scale(Math.cos(a)).add(u.scale(Math.sin(a) * Math.cos(phi))).add(v.scale(Math.sin(a) * Math.sin(phi))).normalize();
    }
}

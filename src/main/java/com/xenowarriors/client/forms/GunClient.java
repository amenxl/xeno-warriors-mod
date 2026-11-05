package com.xenowarriors.client.forms;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.xenowarriors.XenoWarriors;
import com.xenowarriors.forms.Form;
import com.xenowarriors.forms.FormGunItem;
import com.xenowarriors.forms.GunSounds;
import com.xenowarriors.forms.GunSpec;
import com.xenowarriors.network.GunActionPacket;
import com.xenowarriors.network.ModNetwork;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * Mikiko's and Rhalix's summoned guns handle like TACZ guns (its default controls, no new keys):
 * <ul>
 * <li>Left click fires: pistol semi-auto, rifle full auto (hold), shotgun pump action. TACZ's own shoot / aim keys are
 * used if TACZ is installed (so a rebind there carries over), and its Hold To Aim setting.</li>
 * <li>Hold right click to aim down the sights: the gun slides to the middle of the screen so you look along its sights
 * (the rifle's scope fills the screen), the view zooms in (pistol a little, rifle the most, shotgun barely), mouse
 * sensitivity drops with the zoom (TACZ's MDV 1.33 rule), walking slows down a bit and you can't sprint.</li>
 * <li>A dynamic crosshair that opens up with the gun's spread (moving, jumping, firing fast) and fades out as you
 * aim; a hit marker when a shot lands.</li>
 * <li>Camera recoil (kick up and a little sideways, partly recovering), the gun kicking back in your hands, a
 * muzzle flash on the model, spent shells, the shotgun's pump.</li>
 * </ul>
 * The shot itself is the server's ({@link FormGunItem#fire}); infinite ammo, durability and gun levels are unchanged.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class GunClient {
    private static boolean aimWanted, aimSent, trigWas, aimKeyWas, hitKill;
    private static float aim, aimO, bloom, recoilPitch, recoilYaw, recover;
    private static int ticks, cooldown, queued, lastShot = -100, hitAt = -100, flashSeed;
    private static GunSpec lastSpec = GunSpec.PISTOL;

    /** Tests: force aim / trigger (null = the real keys). */
    public static Boolean testAim, testTrigger;
    /** How long a muzzle flash shows (ticks); tests hold it longer for the stills. */
    public static float flashTicks = 1.6F;
    private static final net.minecraft.resources.ResourceLocation FLASH = new net.minecraft.resources.ResourceLocation("textures/particle/flash.png");
    /** Test numbers. */
    public static int shotsSent, aimPackets, sensCalls, viewModelsDrawn, crosshairsDrawn, scopesDrawn, hitMarkers, flashesDrawn;
    public static float lastFov = 70F, lastSpread, lastMoveScale = 1F, lastSens = 1F;
    public static final float[] lastView = new float[3];

    private GunClient() {}

    // ------------------------------------------------------------------ state

    public static FormGunItem held(Player p) {
        return p != null && !p.isSpectator() && p.getMainHandItem().getItem() instanceof FormGunItem g ? g : null;
    }

    /** 0-1, eased: how far into the sights (with the frame's partial tick). */
    public static float aimProgress(float pt) {
        float a = Mth.clamp(Mth.lerp(pt, aimO, aim), 0F, 1F);
        return a * a * (3F - 2F * a);
    }

    public static boolean aiming() { return aimWanted; }

    /** The rifle's scope covers the screen once it's nearly aimed in. */
    public static boolean scoped(float pt) {
        Minecraft mc = Minecraft.getInstance();
        FormGunItem g = held(mc.player);
        return g != null && g.form() == Form.RIFLE && mc.options.getCameraType().isFirstPerson() && aimProgress(pt) > 0.85F;
    }

    /** The spread (degrees) a shot fired now would have, pellets' cone included for the crosshair. */
    public static float spread(LocalPlayer p, GunSpec s, float pt) {
        Vec3 v = p.getDeltaMovement();
        boolean moving = v.x * v.x + v.z * v.z > 0.0025, air = !p.onGround() && p.getVehicle() == null && !p.getAbilities().flying;
        return s.spread(aimProgress(pt), moving, air, p.isCrouching(), bloom);
    }

    // ------------------------------------------------------------------ TACZ's keys and settings (if it's installed)

    private static final class Tacz {
        static boolean tried;
        static KeyMapping shoot, aimKey;
        static Object holdToAim, sensMult, screenCoef;

        static void load() {
            if (tried) return;
            tried = true;
            if (!net.minecraftforge.fml.ModList.get().isLoaded("tacz")) return;
            try { shoot = (KeyMapping) Class.forName("com.tacz.guns.client.input.ShootKey").getField("SHOOT_KEY").get(null); } catch (Throwable ignored) {}
            try { aimKey = (KeyMapping) Class.forName("com.tacz.guns.client.input.AimKey").getField("AIM_KEY").get(null); } catch (Throwable ignored) {}
            try { holdToAim = Class.forName("com.tacz.guns.config.client.KeyConfig").getField("HOLD_TO_AIM").get(null); } catch (Throwable ignored) {}
            try {
                Class<?> z = Class.forName("com.tacz.guns.config.client.ZoomConfig");
                sensMult = z.getField("ZOOM_SENSITIVITY_BASE_MULTIPLIER").get(null);
                screenCoef = z.getField("SCREEN_DISTANCE_COEFFICIENT").get(null);
            } catch (Throwable ignored) {}
            XenoWarriors.LOGGER.info("Summoned guns: TACZ keys/settings found - shoot {}, aim {}, hold-to-aim {}, zoom sensitivity {}",
                    shoot != null, aimKey != null, holdToAim != null, sensMult != null);
        }

        static Object get(Object cfg) {
            if (cfg instanceof net.minecraftforge.common.ForgeConfigSpec.ConfigValue<?> v) try { return v.get(); } catch (Throwable ignored) {}
            return null;
        }
    }

    /** TACZ's Hold To Aim (default on); without TACZ: hold. */
    public static boolean holdToAim() { Tacz.load(); return !(Tacz.get(Tacz.holdToAim) instanceof Boolean b) || b; }

    public static boolean taczKeys() { Tacz.load(); return Tacz.shoot != null && Tacz.aimKey != null; }

    private static double sensMultiplier() { Tacz.load(); return Tacz.get(Tacz.sensMult) instanceof Number n ? n.doubleValue() : 1.0; }

    private static double screenCoefficient() { Tacz.load(); return Tacz.get(Tacz.screenCoef) instanceof Number n ? n.doubleValue() : 1.33; }

    private static boolean aimKey(Minecraft mc) {
        if (testAim != null) return testAim;
        Tacz.load();
        return (Tacz.aimKey != null ? Tacz.aimKey : mc.options.keyUse).isDown();
    }

    private static boolean triggerKey(Minecraft mc) {
        if (testTrigger != null) return testTrigger;
        Tacz.load();
        return (Tacz.shoot != null ? Tacz.shoot : mc.options.keyAttack).isDown();
    }

    // ------------------------------------------------------------------ input

    /** From FormsClient's click handler: a gun in hand eats the attack / use clicks (no punching, digging or using). */
    public static boolean onClick(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        if (held(mc.player) == null || event.getHand() != InteractionHand.MAIN_HAND || !(event.isAttack() || event.isUseItem())) return false;
        if (event.isUseItem() && mc.hitResult instanceof net.minecraft.world.phys.EntityHitResult e
                && e.getEntity() instanceof net.minecraft.world.entity.decoration.ItemFrame) return false;   // (item frames still work, as with TACZ)
        event.setCanceled(true);
        event.setSwingHand(false);
        return true;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.isPaused()) return;
        ticks++;
        aimO = aim;
        FormGunItem gun = held(p);
        if (gun == null) {
            if (aimSent) { ModNetwork.sendToServer(GunActionPacket.aim(false)); aimSent = false; aimPackets++; }
            aimWanted = false; aim = aimO = 0F; queued = 0; trigWas = false; bloom = 0F; recoilPitch = recoilYaw = recover = 0F;
            return;
        }
        GunSpec s = gun.spec();
        if (s != lastSpec) { lastSpec = s; cooldown = Math.min(cooldown, 6); }
        boolean free = mc.screen == null && !com.xenowarriors.party.Duels.cantAct(p);
        boolean key = free && aimKey(mc);
        if (holdToAim()) aimWanted = key;
        else if (key && !aimKeyWas) aimWanted = !aimWanted;
        aimKeyWas = key;
        if (!free) aimWanted = false;
        if (aimWanted && p.isSprinting()) p.setSprinting(false);
        aim = aimWanted ? Math.min(1F, aim + 1F / GunSpec.AIM_TICKS) : Math.max(0F, aim - 1F / GunSpec.AIM_TICKS);
        if (aimWanted != aimSent) {
            ModNetwork.sendToServer(GunActionPacket.aim(aimWanted));
            aimSent = aimWanted;
            aimPackets++;
            GunSounds.aim(p.level(), p, p.getX(), p.getY(), p.getZ(), aimWanted);
        }
        // the trigger: semi / pump = one shot a click (a click a little early still counts), auto = while held
        if (cooldown > 0) cooldown--;
        boolean trig = free && triggerKey(mc);
        if (trig && !trigWas) queued = 4;
        else if (queued > 0) queued--;
        trigWas = trig;
        boolean want = s.mode == GunSpec.Mode.AUTO ? trig : queued > 0;
        bloom *= 0.8F;
        if (want && cooldown <= 0) shoot(mc, p, gun, s);
        int since = ticks - lastShot;
        if (s.mode == GunSpec.Mode.PUMP && (since == 5 || since == 9)) {
            GunSounds.pump(p.level(), p, p.getX(), p.getEyeY(), p.getZ(), since == 5);
            if (since == 5) shell(p, gun.style(), true);
        }
        lastSpread = spread(p, s, 1F);
    }

    private static void shoot(Minecraft mc, LocalPlayer p, FormGunItem gun, GunSpec s) {
        float sp = spread(p, s, 1F);
        boolean aimed = aim >= 0.5F;
        ModNetwork.sendToServer(new GunActionPacket(GunActionPacket.FIRE, p.getXRot(), p.getYRot(), sp, aimed));
        shotsSent++;
        lastShot = ticks;
        cooldown = s.interval;
        queued = 0;
        bloom = Math.min(bloom + s.bloom, s.bloom * 6F);
        float k = s.kick * (aimed ? 0.7F : 1F) * (p.isCrouching() ? 0.8F : 1F);
        recoilPitch += k * (0.9F + p.getRandom().nextFloat() * 0.2F);
        recoilYaw += (p.getRandom().nextFloat() * 2F - 1F) * s.kickYaw * (aimed ? 0.7F : 1F);
        flashSeed = p.getRandom().nextInt(90);
        GunSounds.shot(p.level(), p, p.getX(), p.getEyeY(), p.getZ(), gun.form(), gun.style());
        if (s.mode != GunSpec.Mode.PUMP) shell(p, gun.style(), false);
    }

    /** A spent casing flicked out to the right (a nugget of blade steel / a shard of wire crystal). */
    private static void shell(LocalPlayer p, Form.Style style, boolean shotgun) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !mc.options.getCameraType().isFirstPerson()) return;
        Vec3 look = p.getViewVector(1F), right = look.cross(new Vec3(0, 1, 0)).normalize(), up = right.cross(look).normalize();
        float a = aimProgress(1F);
        Vec3 at = p.getEyePosition().add(look.scale(0.6)).add(right.scale(Mth.lerp(a, 0.32F, 0.1F))).add(up.scale(Mth.lerp(a, -0.22F, -0.1F)));
        Vec3 v = right.scale(0.1 + p.getRandom().nextFloat() * 0.05).add(up.scale(0.12)).add(look.scale(-0.02)).add(p.getDeltaMovement());
        ItemStack casing = new ItemStack(shotgun ? Items.RED_DYE : style == Form.Style.HEKU ? Items.GOLD_NUGGET : Items.AMETHYST_SHARD);
        mc.level.addParticle(new ItemParticleOption(ParticleTypes.ITEM, casing), at.x, at.y, at.z, v.x, v.y, v.z);
    }

    /** Someone else fired a summoned gun: a casing out of their gun (their flash and sound come from the server). */
    public static void remoteShot(int entityId, int formOrdinal) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || entityId == mc.player.getId()) return;
        Entity e = mc.level.getEntity(entityId);
        Form f = Form.byId(formOrdinal);
        if (!(e instanceof LivingEntity le) || f == null || f == Form.SHOTGUN) return;
        Vec3 h = FormsFx.hand(le, 1F, true);
        boolean heku = le.getMainHandItem().getItem() instanceof FormGunItem g && g.style() == Form.Style.HEKU;
        float y = le.yBodyRot * Mth.DEG_TO_RAD;
        mc.level.addParticle(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(heku ? Items.GOLD_NUGGET : Items.AMETHYST_SHARD)),
                h.x, h.y + 0.1, h.z, -Mth.cos(y) * 0.12, 0.14, -Mth.sin(y) * 0.12);
    }

    public static void hitMarker(boolean kill) {
        hitAt = ticks;
        hitKill = kill;
        hitMarkers++;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.playSound(kill ? SoundEvents.PLAYER_ATTACK_CRIT : SoundEvents.PLAYER_ATTACK_WEAK, 0.35F, kill ? 1.2F : 1.8F);
    }

    // ------------------------------------------------------------------ camera: recoil, zoom, sensitivity, slower walking

    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.isPaused() || (recoilPitch == 0F && recoilYaw == 0F && recover == 0F)) return;
        float dt = Math.max(0.01F, mc.getDeltaFrameTime());
        float take = 1F - (float) Math.pow(0.5, dt / 0.4);                     // the kick lands in about a tick
        float dp = recoilPitch * take, dy = recoilYaw * take;
        recoilPitch -= dp; recoilYaw -= dy;
        recover += dp * 0.45F;                                                  // and about half of it settles back
        float back = recover * (1F - (float) Math.pow(0.5, dt / 2.5));
        recover -= back;
        float d = -dp + back;
        float nx = Mth.clamp(p.getXRot() + d, -90F, 90F);
        float applied = nx - p.getXRot();
        p.setXRot(nx); p.xRotO += applied;
        p.setYRot(p.getYRot() + dy); p.yRotO += dy;
        if (Math.abs(recoilPitch) < 0.001F && Math.abs(recoilYaw) < 0.001F && Math.abs(recover) < 0.001F) recoilPitch = recoilYaw = recover = 0F;
    }

    @SubscribeEvent
    public static void onFov(ViewportEvent.ComputeFov event) {
        if (!event.usedConfiguredFov()) return;
        Minecraft mc = Minecraft.getInstance();
        FormGunItem g = held(mc.player);
        if (g == null || !mc.options.getCameraType().isFirstPerson()) { lastFov = (float) event.getFOV(); return; }
        float a = aimProgress((float) event.getPartialTick());
        if (a > 0F) event.setFOV(magnificationToFov(1.0 + (g.spec().zoom - 1.0) * a, event.getFOV()));
        lastFov = (float) event.getFOV();
    }

    public static double magnificationToFov(double magnification, double fov) {
        return Math.toDegrees(Math.atan(Math.tan(Math.toRadians(fov / 2)) / magnification)) * 2;
    }

    /** Mouse sensitivity scale while aimed (MouseHandlerGunMixin): matched to the zoom, like TACZ. */
    public static double sensitivity() {
        sensCalls++;
        Minecraft mc = Minecraft.getInstance();
        FormGunItem g = held(mc.player);
        if (g == null) return lastSens = 1F;
        float a = aimProgress(mc.getFrameTime());
        if (a <= 0F) return lastSens = 1F;
        double base = mc.options.fov().get();
        double cur = magnificationToFov(1.0 + (g.spec().zoom - 1.0) * a, base), c = screenCoefficient();
        double ratio = Math.atan(Math.tan(Math.toRadians(cur / 2)) * c) / Math.atan(Math.tan(Math.toRadians(base / 2)) * c);
        double r = ratio * (1.0 + (sensMultiplier() - 1.0) * a);
        lastSens = (float) r;
        return r;
    }

    @SubscribeEvent
    public static void onMove(MovementInputUpdateEvent event) {
        if (event.getEntity() != Minecraft.getInstance().player || held(event.getEntity()) == null) { lastMoveScale = 1F; return; }
        float a = aimProgress(1F);
        lastMoveScale = Mth.lerp(a, 1F, GunSpec.AIM_MOVE);
        event.getInput().forwardImpulse *= lastMoveScale;
        event.getInput().leftImpulse *= lastMoveScale;
    }

    // ------------------------------------------------------------------ the crosshair, hit marker and scope

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onOverlay(RenderGuiOverlayEvent.Pre event) {
        if (!event.getOverlay().id().equals(VanillaGuiOverlay.CROSSHAIR.id())) return;
        Minecraft mc = Minecraft.getInstance();
        FormGunItem g = held(mc.player);
        if (g == null || !mc.options.getCameraType().isFirstPerson()) return;
        event.setCanceled(true);
        GuiGraphics gfx = event.getGuiGraphics();
        float pt = event.getPartialTick();
        int w = event.getWindow().getGuiScaledWidth(), h = event.getWindow().getGuiScaledHeight();
        int cx = w / 2, cy = h / 2;
        GunSpec s = g.spec();
        if (scoped(pt)) scope(gfx, w, h, g.style(), aimProgress(pt));
        float a = aimProgress(pt);
        if (a < 0.9F && !scoped(pt)) {
            int alpha = Math.round(230 * (1F - a / 0.9F));
            float deg = spread(mc.player, s, pt) + Mth.lerp(a, s.pelletHip, s.pelletAds);
            double px = Math.tan(Math.toRadians(deg)) / Math.tan(Math.toRadians(lastFov / 2)) * (h / 2.0);
            int gap = 2 + (int) Math.round(px), len = 4;
            int col = (alpha << 24) | 0xFFFFFF, sh = ((alpha * 2 / 3) << 24);
            line(gfx, cx - gap - len, cy, len, 1, col, sh);                     // left, right, top, bottom
            line(gfx, cx + gap + 1, cy, len, 1, col, sh);
            line(gfx, cx, cy - gap - len, 1, len, col, sh);
            line(gfx, cx, cy + gap + 1, 1, len, col, sh);
            if (s != GunSpec.SHOTGUN) line(gfx, cx, cy, 1, 1, col, sh);          // centre dot
            crosshairsDrawn++;
        }
        int since = ticks - hitAt;
        if (since >= 0 && since < 6) {                                          // hit marker: an X round the middle
            int c = hitKill ? 0xFFFF3030 : 0xFFFFFFFF;
            for (int i = 0; i < 4; i++) {
                int o = 3 + i;
                gfx.fill(cx - o, cy - o, cx - o + 1, cy - o + 1, c);
                gfx.fill(cx + o, cy - o, cx + o + 1, cy - o + 1, c);
                gfx.fill(cx - o, cy + o, cx - o + 1, cy + o + 1, c);
                gfx.fill(cx + o, cy + o, cx + o + 1, cy + o + 1, c);
            }
        }
    }

    private static void line(GuiGraphics g, int x, int y, int w, int h, int col, int shadow) {
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, shadow);
        g.fill(x, y, x + w, y + h, col);
    }

    /** The rifle's scope picture: the round sight (the spyglass view), a fine reticle in the gun's colour. */
    private static void scope(GuiGraphics g, int w, int h, Form.Style style, float a) {
        scopesDrawn++;
        int size = Math.round(Math.min(w, h) * Mth.lerp((a - 0.85F) / 0.15F, 1.25F, 1.0F));
        int cx = w / 2, cy = h / 2, rad = size / 2;
        // black all round a round window, a soft dark rim just inside it
        for (int row = 0; row < h; row++) {
            int dy = Math.abs(row - cy);
            if (dy >= rad) { g.fill(0, row, w, row + 1, 0xFF000000); continue; }
            int half = (int) Math.sqrt((double) rad * rad - (double) dy * dy);
            g.fill(0, row, cx - half, row + 1, 0xFF000000);
            g.fill(cx + half, row, w, row + 1, 0xFF000000);
            int rim = Math.max(2, rad / 14);
            int inner = dy >= rad - rim ? 0 : (int) Math.sqrt((double) (rad - rim) * (rad - rim) - (double) dy * dy);
            g.fill(cx - half, row, cx - inner, row + 1, 0xB0000000);
            g.fill(cx + inner, row, cx + half, row + 1, 0xB0000000);
        }
        int col = style == Form.Style.HEKU ? 0xE0FF7A1A : 0xE0B04CFF, r = rad - 6;
        g.fill(cx - r, cy, cx - 4, cy + 1, col);
        g.fill(cx + 5, cy, cx + r, cy + 1, col);
        g.fill(cx, cy + 5, cx + 1, cy + r, col);
        g.fill(cx, cy - r / 3, cx + 1, cy - 4, col);
        g.fill(cx, cy, cx + 1, cy + 1, 0xFFFF2020);
        for (int i = 1; i <= 3; i++) g.fill(cx - 2, cy + 5 + i * 6, cx + 3, cy + 6 + i * 6, col);   // drop marks
    }

    // ------------------------------------------------------------------ the gun in first person

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onHand(RenderHandEvent event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || !(event.getItemStack().getItem() instanceof FormGunItem g)) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null) return;
        event.setCanceled(true);
        float pt = event.getPartialTick();
        if (scoped(pt)) return;                                                 // (looking through the scope: the overlay)
        GunSpec s = g.spec();
        float a = aimProgress(pt), equip = event.getEquipProgress();
        PoseStack ps = event.getPoseStack();
        ps.pushPose();
        // hip: where the item model's own first-person display puts it (turning about the model's middle); aimed: the
        // front sight / scope lens (the pivot) right on the middle of the screen, the gun tilted so the rear sight drops under it
        float hx = 0.56F - 0.25F, hy = -0.52F + s.dispY / 16F, hz = -0.72F + s.dispZ / 16F;
        float x = Mth.lerp(a, hx, 0F), y = Mth.lerp(a, hy, 0F) - equip * 0.6F, z = Mth.lerp(a, hz, -s.aimDist);
        float pivY = a * (s.sightY / 16F - 0.5F), pivZ = a * (s.sightZ / 16F - 0.5F);
        // the shot: kick back and up, settling over a few ticks; the shotgun's pump after it
        float since = ticks - lastShot + pt;
        float kickT = s == GunSpec.SHOTGUN ? 5F : s == GunSpec.RIFLE ? 2.5F : 3.5F;
        float k = since < 0F ? 0F : Math.max(0F, 1F - since / kickT);
        k = k * k;
        float back = (s == GunSpec.SHOTGUN ? 0.11F : s == GunSpec.RIFLE ? 0.045F : 0.07F) * (1F - a * 0.35F);
        float rise = (s == GunSpec.SHOTGUN ? 11F : s == GunSpec.RIFLE ? 3F : 8F) * (1F - a * 0.6F);
        ps.translate(x, y + 0.015F * k, z + back * k);
        ps.mulPose(Axis.YP.rotationDegrees(Mth.lerp(a, 4F, 0F)));
        ps.mulPose(Axis.XP.rotationDegrees(rise * k + s.aimTilt * a));
        if (s.mode == GunSpec.Mode.PUMP) {
            float u = (since - 4F) / 9F;
            if (u > 0F && u < 1F) {
                float pm = Mth.sin(u * (float) Math.PI);
                ps.translate(0F, -0.035F * pm, 0.05F * pm * (1F - a * 0.5F));
                ps.mulPose(Axis.ZP.rotationDegrees(-9F * pm * (1F - a * 0.6F)));
            }
        }
        ps.scale(0.75F, 0.75F, 0.75F);
        ps.translate(0F, -pivY, -pivZ);
        lastView[0] = x; lastView[1] = y; lastView[2] = z;
        mc.getEntityRenderDispatcher().getItemInHandRenderer().renderItem(p, event.getItemStack(), ItemDisplayContext.NONE, false, ps,
                event.getMultiBufferSource(), event.getPackedLight());
        viewModelsDrawn++;
        if (since >= 0F && since < flashTicks) flash(ps, event.getMultiBufferSource(), s, g.style(), since);
        ps.popPose();
    }

    /** The muzzle flash: a bright star at the end of the barrel for a tick or two. */
    private static void flash(PoseStack ps, MultiBufferSource buffers, GunSpec s, Form.Style style, float since) {
        flashesDrawn++;
        float life = 1F - Math.min(since / flashTicks, 0.6F);
        float my = s.muzzleY / 16F - 0.5F, mz = s.muzzleZ / 16F - 0.5F - 0.03F;
        ps.pushPose();
        ps.translate(0F, my, mz);
        ps.mulPose(Axis.ZP.rotationDegrees(flashSeed));
        Matrix4f m = ps.last().pose();
        org.joml.Matrix3f n = ps.last().normal();
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(FLASH));
        float r = (s == GunSpec.SHOTGUN ? 0.32F : s == GunSpec.RIFLE ? 0.24F : 0.2F) * (0.6F + 0.4F * life);
        float[] outer = style == Form.Style.HEKU ? new float[]{1F, 0.6F, 0.2F} : new float[]{0.8F, 0.45F, 1F};
        sprite(vc, m, n, r, 0F, outer, life);
        sprite(vc, m, n, r * 0.75F, 45F, outer, life);
        sprite(vc, m, n, r * 0.4F, 20F, new float[]{1F, 0.97F, 0.85F}, life);
        ps.popPose();
    }

    /** One square of the flash texture, facing back down the barrel (both sides drawn), full bright. */
    private static void sprite(VertexConsumer vc, Matrix4f m, org.joml.Matrix3f n, float r, float rotDeg, float[] c, float a) {
        float cs = Mth.cos(rotDeg * Mth.DEG_TO_RAD) * r, sn = Mth.sin(rotDeg * Mth.DEG_TO_RAD) * r;
        float[][] q = {{-cs + sn, -sn - cs, 0, 1}, {cs + sn, sn - cs, 1, 1}, {cs - sn, sn + cs, 1, 0}, {-cs - sn, -sn + cs, 0, 0}};
        for (int side = 0; side < 2; side++)
            for (int i = 0; i < 4; i++) {
                float[] v = q[side == 0 ? i : 3 - i];
                vc.vertex(m, v[0], v[1], 0F).color(c[0], c[1], c[2], a).uv(v[2], v[3])
                        .overlayCoords(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).uv2(0xF000F0)
                        .normal(n, 0F, 0F, side == 0 ? 1F : -1F).endVertex();
            }
    }

    // ------------------------------------------------------------------ tests

    /** UI test: as if the trigger were pulled this tick (semi / pump: a click). */
    public static void testClick() { queued = 4; }

    public static int cooldownLeft() { return cooldown; }

    public static float bloom() { return bloom; }

    public static float pendingRecoil() { return recoilPitch + recover; }
}

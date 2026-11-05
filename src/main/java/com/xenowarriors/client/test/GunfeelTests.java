package com.xenowarriors.client.test;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.character.ModCapabilities;
import com.xenowarriors.client.UiTest;
import com.xenowarriors.client.UiTest.Step;
import com.xenowarriors.client.forms.FormsFx;
import com.xenowarriors.client.forms.GunClient;
import com.xenowarriors.forms.Form;
import com.xenowarriors.forms.FormGunItem;
import com.xenowarriors.forms.GunSpec;
import com.xenowarriors.forms.WeaponForms;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.List;
import java.util.Locale;

/**
 * UI tests for the summoned guns handling like TACZ guns (focus "gunfeel"; "gunfeeltacz" runs the same with TACZ
 * installed): aiming down the sights (zoom, view model to the middle, sensitivity, slower walking, no sprint), the
 * dynamic crosshair, hip vs aimed spread, each gun's fire mode and rate, recoil, durability still dropping, the aim
 * pose in third person. Every result is an "XW-TEST gunfeel ..." line; stills of hip fire and aimed views.
 */
public final class GunfeelTests {
    private GunfeelTests() {}

    private static final double[] HOME = new double[3];

    static void log(String s) { XenoWarriors.LOGGER.info("XW-TEST gunfeel " + s); }

    private static String f2(double d) { return String.format(Locale.ROOT, "%.2f", d); }

    public static boolean handles(String focus) { return focus != null && focus.startsWith("gunfeel"); }

    private static void server(java.util.function.Consumer<ServerPlayer> f) {
        UiTest.server(p -> { try { f.accept(p); } catch (Throwable t) { log("step error " + t); XenoWarriors.LOGGER.error("XW-TEST gunfeel", t); } });
    }

    public static void build(Minecraft mc, List<Step> l, String focus) {
        l.add(new Step(null, () -> { UiTest.noToasts(mc); mc.setScreen(null); mc.options.hideGui = false; mc.options.setCameraType(CameraType.FIRST_PERSON);
            GunClient.flashTicks = 8F;                                          // (so the stills catch the muzzle flash)
            UiTest.character(CharacterType.MIKIKO); }, 40));
        l.add(new Step(null, () -> server(p -> { com.xenowarriors.character.Seasons.set(p.server, 2); arena(p); levels(p, 60); p.getInventory().clearContent();
            p.getPersistentData().remove(WeaponForms.BROKEN); }), 20));
        l.add(new Step(null, () -> {
            log("tacz loaded=" + net.minecraftforge.fml.ModList.get().isLoaded("tacz") + " available=" + com.xenowarriors.compat.TaczGuns.available()
                    + " taczKeys=" + GunClient.taczKeys() + " holdToAim=" + GunClient.holdToAim());
            for (GunSpec s : GunSpec.values())
                log("spec " + s + ": mode " + s.mode + ", " + s.rpm() + " rpm (every " + s.interval + " ticks), hip " + s.hipSpread + " deg, aimed " + s.adsSpread
                        + " deg, pellets " + s.pellets + " (cone " + s.pelletHip + "/" + s.pelletAds + "), zoom x" + s.zoom);
        }, 2));
        for (Form f : new Form[]{Form.PISTOL, Form.RIFLE, Form.SHOTGUN}) views(mc, l, Form.Style.HEKU, f);
        fireModes(mc, l);
        spreadTest(mc, l);
        feel(mc, l);
        thirdPerson(mc, l);
        durability(mc, l);
        // Rhalix's wire guns (Season 2)
        l.add(new Step(null, () -> { UiTest.character(CharacterType.RHALIX); }, 40));
        l.add(new Step(null, () -> server(p -> { arena(p); levels(p, 60); p.getInventory().clearContent(); p.getPersistentData().remove(WeaponForms.BROKEN); }), 15));
        views(mc, l, Form.Style.WIRE, Form.PISTOL);
        views(mc, l, Form.Style.WIRE, Form.RIFLE);
        wiresStill(mc, l);
        tacz(mc, l);
        l.add(new Step(null, () -> { GunClient.testAim = null; GunClient.testTrigger = null; GunClient.flashTicks = 1.6F; server(p -> { clearDummies(p); p.getInventory().clearContent(); }); log("done"); }, 10));
    }

    // ------------------------------------------------------------------ setup

    private static void levels(ServerPlayer p, int lv) {
        p.getCapability(ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> { d.bladeLevel = lv; d.bladeXp = 0; });
        com.xenowarriors.rhalix.WireLevel.set(p, lv);
        com.xenowarriors.compat.GunLevel.setLevel(p, 50);
        WeaponForms.sync(p, true);
    }

    /** A flat range: grass floor, a pale wall 16 blocks ahead with a target on it, two husks to shoot at. */
    private static void arena(ServerPlayer p) {
        var level = p.serverLevel();
        if (HOME[1] == 0) {
            BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p.blockPosition());
            HOME[0] = top.getX() + 0.5; HOME[1] = top.getY() + 1; HOME[2] = top.getZ() + 0.5;
        }
        int y0 = (int) HOME[1];
        for (int x = -10; x <= 10; x++) for (int z = -6; z <= 18; z++) {
            BlockPos b = BlockPos.containing(HOME[0] + x, y0 - 1, HOME[2] + z);
            level.setBlock(b, Blocks.GRASS_BLOCK.defaultBlockState(), 3);
            for (int y = 0; y < 30; y++) level.setBlock(b.above(1 + y), Blocks.AIR.defaultBlockState(), 3);
        }
        for (int x = -10; x <= 10; x++) for (int y = 0; y < 7; y++) {
            int dx = Math.abs(x), dy = Math.abs(y - 2), r = Math.max(dx, dy);
            var st = r == 0 ? Blocks.RED_CONCRETE : r == 1 ? Blocks.WHITE_CONCRETE : r == 2 ? Blocks.RED_CONCRETE : Blocks.SMOOTH_QUARTZ;
            level.setBlock(BlockPos.containing(HOME[0] + x, y0 + y, HOME[2] + 16), st.defaultBlockState(), 3);
        }
        p.teleportTo(level, HOME[0], y0, HOME[2], 0F, 0F);
        p.setGameMode(GameType.SURVIVAL);
        p.setHealth(p.getMaxHealth());
        p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE, 20 * 900, 4, false, false));
        p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SATURATION, 20 * 900, 0, false, false));
        level.setDayTime(6000);
        clearDummies(p);
        dummy(p, -2.5, 10);
        dummy(p, 3, 12);
    }

    private static LivingEntity dummy(ServerPlayer p, double dx, double dz) {
        var h = EntityType.HUSK.create(p.serverLevel());
        h.moveTo(HOME[0] + dx, HOME[1], HOME[2] + dz, 180F, 0F);
        h.setNoAi(true);
        h.setPersistenceRequired();
        h.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
        h.setHealth(1000);
        h.addTag("xw_gf_dummy");
        h.setCustomName(Component.literal("target"));
        p.serverLevel().addFreshEntity(h);
        return h;
    }

    private static void clearDummies(ServerPlayer p) {
        p.serverLevel().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(80), e -> e.getTags().contains("xw_gf_dummy")).forEach(e -> e.discard());
    }

    private static void heal(ServerPlayer p) {
        p.serverLevel().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(80), e -> e.getTags().contains("xw_gf_dummy")).forEach(e -> e.setHealth(e.getMaxHealth()));
    }

    /** The local player level, facing down the range (+Z), standing still. */
    private static void face(Minecraft mc, float pitch) {
        var p = mc.player;
        if (p == null) return;
        p.setYRot(0F); p.yRotO = 0F; p.setYHeadRot(0F); p.yHeadRotO = 0F; p.yBodyRot = 0F; p.yBodyRotO = 0F;
        p.setXRot(pitch); p.xRotO = pitch;
        p.setDeltaMovement(0, p.getDeltaMovement().y, 0);
    }

    private static void summon(Minecraft mc, List<Step> l, Form f) {
        l.add(new Step(null, () -> { GunClient.testAim = false; GunClient.testTrigger = false;
            server(p -> { p.getPersistentData().remove(WeaponForms.BROKEN); WeaponForms.summon(p, f, false); heal(p); }); }, 30));
    }

    private static String held(Minecraft mc) {
        ItemStack s = mc.player.getMainHandItem();
        return s.isEmpty() ? "nothing" : String.valueOf(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(s.getItem()));
    }

    private static String state(Minecraft mc) {
        return "fov " + f2(GunClient.lastFov) + ", view model (" + f2(GunClient.lastView[0]) + ", " + f2(GunClient.lastView[1]) + ", " + f2(GunClient.lastView[2])
                + "), spread " + f2(GunClient.lastSpread) + " deg, sensitivity x" + f2(GunClient.sensitivity()) + ", walk x" + f2(GunClient.lastMoveScale)
                + ", aim " + f2(GunClient.aimProgress(1F)) + ", scoped " + GunClient.scoped(1F) + ", server aiming pose " + FormsFx.remoteAiming(mc.player.getId());
    }

    // ------------------------------------------------------------------ hip and aimed views

    private static void views(Minecraft mc, List<Step> l, Form.Style style, Form f) {
        String name = (style == Form.Style.HEKU ? "heku_" : "wire_") + f.name().toLowerCase(Locale.ROOT);
        summon(mc, l, f);
        l.add(new Step(null, () -> { face(mc, 2F); mc.options.setCameraType(CameraType.FIRST_PERSON); mc.options.hideGui = false; }, 12));
        l.add(new Step("gunfeel_" + name + "_hip", () -> { face(mc, 2F); log(name + " hip: held " + held(mc) + ", " + state(mc)); }, 3));
        if (f == Form.PISTOL && style == Form.Style.HEKU) {                     // a shot from the hip: flash, kick, tracer
            l.add(new Step(null, () -> { face(mc, 2F); GunClient.testClick(); }, 1));
            l.add(new Step("gunfeel_" + name + "_hip_fire", () -> log(name + " hip shot: flashes drawn " + GunClient.flashesDrawn), 1));
            l.add(new Step(null, () -> face(mc, 2F), 10));
        }
        l.add(new Step(null, () -> { face(mc, 2F); GunClient.testAim = true; }, 12));
        l.add(new Step("gunfeel_" + name + "_ads", () -> { face(mc, 2F); log(name + " aimed: " + state(mc) + ", scopes drawn " + GunClient.scopesDrawn); }, 3));
        if (f == Form.SHOTGUN || f == Form.RIFLE && style == Form.Style.WIRE) {   // a shot while aimed
            l.add(new Step(null, () -> { face(mc, 2F); GunClient.testClick(); }, 1));
            l.add(new Step("gunfeel_" + name + "_ads_fire", () -> {}, 1));
            l.add(new Step(null, () -> face(mc, 2F), 18));
        }
        l.add(new Step(null, () -> GunClient.testAim = false, 8));
        l.add(new Step(null, () -> log(name + " back to hip: " + state(mc)), 2));
    }

    // ------------------------------------------------------------------ fire modes and rates

    private static void fireModes(Minecraft mc, List<Step> l) {
        for (Form f : new Form[]{Form.PISTOL, Form.RIFLE, Form.SHOTGUN}) {
            int[] c = new int[4];
            summon(mc, l, f);
            l.add(new Step(null, () -> { face(mc, 3F); c[0] = GunClient.shotsSent; server(p -> { WeaponForms.SHOTS = 0; c[2] = FormGunItem.dur(p.getMainHandItem()); });
                GunClient.testTrigger = true; }, 30));
            l.add(new Step(null, () -> { GunClient.testTrigger = false; c[1] = GunClient.shotsSent - c[0];
                server(p -> log(f + " trigger held 30 ticks: " + c[1] + " shot(s) (" + GunSpec.of(f).mode + ", every " + GunSpec.of(f).interval
                        + " ticks); server fired " + WeaponForms.SHOTS + ", durability " + c[2] + " -> " + FormGunItem.dur(p.getMainHandItem()))); }, 20));
            // separate clicks, 5 ticks apart, for 40 ticks
            l.add(new Step(null, () -> { c[0] = GunClient.shotsSent; server(p -> WeaponForms.SHOTS = 0); }, 1));
            for (int i = 0; i < 8; i++) {
                l.add(new Step(null, () -> { face(mc, 3F); GunClient.testTrigger = true; }, 1));
                l.add(new Step(null, () -> GunClient.testTrigger = false, 4));
            }
            l.add(new Step(null, () -> { int n = GunClient.shotsSent - c[0];
                server(p -> log(f + " 8 clicks 5 ticks apart: " + n + " shot(s); server fired " + WeaponForms.SHOTS)); }, 20));
        }
    }

    // ------------------------------------------------------------------ hip vs aimed spread (measured on the server)

    private static void spreadTest(Minecraft mc, List<Step> l) {
        for (Form f : new Form[]{Form.PISTOL, Form.SHOTGUN}) {
            double[] sum = new double[4];
            summon(mc, l, f);
            for (int pass = 0; pass < 2; pass++) {
                final boolean ads = pass == 1;
                l.add(new Step(null, () -> { face(mc, 3F); GunClient.testAim = ads; }, 10));
                for (int i = 0; i < 8; i++) {
                    l.add(new Step(null, () -> { face(mc, 3F); GunClient.testClick(); }, GunSpec.of(f).interval + 3));
                    l.add(new Step(null, () -> server(p -> { sum[ads ? 2 : 0] += FormGunItem.LAST_DEV; sum[ads ? 3 : 1] += FormGunItem.LAST_AIMED == ads ? 1 : 0; heal(p); }), 1));
                }
                l.add(new Step(null, () -> server(p -> log(f + (ads ? " aimed" : " hip") + " spread: mean bullet deviation " + f2(sum[ads ? 2 : 0] / 8)
                        + " deg over 8 shots (" + (int) sum[ads ? 3 : 1] + "/8 counted as " + (ads ? "aimed" : "hip") + " by the server)")), 2));
            }
            l.add(new Step(null, () -> { GunClient.testAim = false;
                log(f + " spread hip " + f2(sum[0] / 8) + " vs aimed " + f2(sum[2] / 8) + " deg: aimed tighter = " + (sum[2] < sum[0])); }, 5));
        }
    }

    // ------------------------------------------------------------------ crosshair, recoil, walking, sprint, sensitivity

    private static void feel(Minecraft mc, List<Step> l) {
        summon(mc, l, Form.PISTOL);
        float[] v = new float[8];
        l.add(new Step(null, () -> { face(mc, 5F); v[0] = GunClient.lastSpread; mc.player.setDeltaMovement(0.25, 0, 0.0);
            v[1] = GunClient.spread(mc.player, GunSpec.PISTOL, 1F); mc.player.setDeltaMovement(0, 0, 0);
            log("crosshair spread standing " + f2(v[0]) + " deg, moving " + f2(v[1]) + " deg (crosshairs drawn " + GunClient.crosshairsDrawn + ")"); }, 2));
        l.add(new Step(null, () -> { face(mc, 5F); v[2] = mc.player.getXRot(); GunClient.testClick(); }, 2));
        l.add(new Step(null, () -> { v[3] = mc.player.getXRot(); v[4] = GunClient.bloom(); }, 12));
        l.add(new Step(null, () -> log("recoil: pitch " + f2(v[2]) + " -> " + f2(v[3]) + " two ticks after the shot (kick " + f2(v[2] - v[3]) + " deg up), settled at "
                + f2(mc.player.getXRot()) + "; bloom after a shot " + f2(v[4]) + " deg"), 2));
        l.add(new Step(null, () -> { face(mc, 5F); GunClient.testAim = true; }, 8));
        l.add(new Step(null, () -> { mc.player.setSprinting(true); }, 2));
        l.add(new Step(null, () -> log("aimed: sprinting " + mc.player.isSprinting() + " (aiming stops a sprint), walk speed x" + f2(GunClient.lastMoveScale)
                + ", sensitivity x" + f2(GunClient.sensitivity()) + " (mouse turns counted " + GunClient.sensCalls + "), crosshair hidden (drawn " + GunClient.crosshairsDrawn + ")"), 4));
        l.add(new Step(null, () -> { int before = GunClient.crosshairsDrawn; GunClient.testAim = false; v[5] = before; }, 8));
        l.add(new Step(null, () -> log("hip again: crosshair drawn again " + (GunClient.crosshairsDrawn > v[5]) + ", walk x" + f2(GunClient.lastMoveScale)
                + ", sensitivity x" + f2(GunClient.sensitivity()) + ", fov " + f2(GunClient.lastFov)), 2));
        // a hit: the marker
        l.add(new Step(null, () -> { int h = GunClient.hitMarkers; v[6] = h; face(mc, 2F);
            server(p -> { clearDummies(p); dummy(p, 0, 5); }); }, 6));
        l.add(new Step(null, () -> { face(mc, 6F); GunClient.testClick(); }, 1));
        l.add(new Step("gunfeel_hitmarker", () -> {}, 1));
        l.add(new Step(null, () -> { log("hit markers " + (int) v[6] + " -> " + GunClient.hitMarkers); server(p -> { clearDummies(p); dummy(p, -2.5, 10); dummy(p, 3, 12); }); }, 5));
    }

    // ------------------------------------------------------------------ third person: hip and aim poses

    private static void thirdPerson(Minecraft mc, List<Step> l) {
        summon(mc, l, Form.RIFLE);
        int[] cam = {-1};
        float yaw = -110F;                                                      // (from in front of the player, off to his left)
        l.add(new Step(null, () -> server(p -> {
            var a = EntityType.ARMOR_STAND.create(p.serverLevel());
            a.moveTo(HOME[0] - 3.0, HOME[1] + 0.1, HOME[2] + 1.1, yaw, 8F); a.setNoGravity(true); a.setInvisible(true); a.setYHeadRot(yaw);
            a.addTag("xw_gf_cam");
            p.serverLevel().addFreshEntity(a); cam[0] = a.getId();
        }), 12));
        l.add(new Step(null, () -> { face(mc, 0F); mc.options.hideGui = true;
            if (mc.level.getEntity(cam[0]) instanceof net.minecraft.world.entity.decoration.ArmorStand a) {
                a.setYRot(yaw); a.yRotO = yaw; a.setXRot(8F); a.xRotO = 8F; a.setYHeadRot(yaw); a.yHeadRotO = yaw; mc.setCameraEntity(a);
            } else mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT); }, 10));
        l.add(new Step("gunfeel_3p_hip_rifle", () -> face(mc, 0F), 3));
        l.add(new Step(null, () -> { face(mc, 0F); GunClient.testAim = true; }, 12));
        l.add(new Step("gunfeel_3p_aim_rifle", () -> { face(mc, 0F); log("third person aimed: pose aim " + f2(FormsFx.aimPose(mc.player, 1F))); }, 3));
        l.add(new Step(null, () -> { GunClient.testAim = false; mc.setCameraEntity(mc.player); mc.options.setCameraType(CameraType.FIRST_PERSON); mc.options.hideGui = false;
            server(p -> p.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.decoration.ArmorStand.class, p.getBoundingBox().inflate(20),
                    a -> a.getTags().contains("xw_gf_cam")).forEach(a -> a.discard())); }, 8));
    }

    // ------------------------------------------------------------------ durability still wears and breaks

    private static void durability(Minecraft mc, List<Step> l) {
        summon(mc, l, Form.PISTOL);
        l.add(new Step(null, () -> server(p -> { ItemStack g = p.getMainHandItem(); g.getOrCreateTag().putInt(FormGunItem.DUR, 2);
            log("durability: max " + FormGunItem.durMax(g) + " at gun level 50 (40 + 3 x 50), set to 2 to break it"); }), 3));
        for (int i = 0; i < 2; i++) {
            l.add(new Step(null, () -> { face(mc, 3F); GunClient.testClick(); }, 7));
        }
        l.add(new Step(null, () -> server(p -> log("after 2 shots: holding " + net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(p.getMainHandItem().getItem())
                + ", broken cooldown " + WeaponForms.gunCooldownLeft(p) + " ticks (30 s = 600)")), 5));
        l.add(new Step(null, () -> server(p -> p.getPersistentData().remove(WeaponForms.BROKEN)), 3));
    }

    // ------------------------------------------------------------------ Rhalix's wires still whip and bind (not guns)

    private static void wiresStill(Minecraft mc, List<Step> l) {
        summon(mc, l, Form.WIRES);
        l.add(new Step(null, () -> log("wires: held " + held(mc) + ", gun controls off (gun in hand " + (GunClient.held(mc.player) != null) + "), whips sent so far "
                + com.xenowarriors.client.forms.FormsClient.whipsSent), 3));
        l.add(new Step(null, () -> { int w = com.xenowarriors.client.forms.FormsClient.whipsSent; com.xenowarriors.client.forms.FormsClient.whip();
            log("wires: whip " + (com.xenowarriors.client.forms.FormsClient.whipsSent > w ? "sent" : "NOT sent")); }, 10));
    }

    // ------------------------------------------------------------------ with TACZ: its guns are untouched, ours keep working

    private static void tacz(Minecraft mc, List<Step> l) {
        l.add(new Step(null, () -> server(p -> {
            if (!com.xenowarriors.compat.TaczGuns.available()) { log("tacz: not installed - plain run (our guns need nothing else)"); return; }
            p.getInventory().clearContent();
            ItemStack g = com.xenowarriors.compat.TaczGuns.gun("tacz:glock_17", 17);
            p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, g);
            log("tacz: holding " + net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(g.getItem()) + " isGun=" + com.xenowarriors.item.Guns.isGun(g));
        }), 20));
        l.add(new Step(null, () -> log("tacz gun in hand: our gun controls " + (GunClient.held(mc.player) != null ? "ACTIVE (wrong)" : "off (TACZ handles it)")), 3));
        summon(mc, l, Form.PISTOL);
        l.add(new Step(null, () -> { face(mc, 2F); GunClient.testAim = true; }, 10));
        l.add(new Step("gunfeel_" + (net.minecraftforge.fml.ModList.get().isLoaded("tacz") ? "tacz_" : "") + "wire_pistol_ads_final", () -> log("final wire pistol aimed: " + state(mc)), 3));
        int[] s = new int[1];
        l.add(new Step(null, () -> { s[0] = GunClient.shotsSent; face(mc, 2F); GunClient.testClick(); }, 6));
        l.add(new Step(null, () -> { GunClient.testAim = false; int n = GunClient.shotsSent - s[0];
            server(p -> log("final wire pistol shot: " + n + " sent, durability " + FormGunItem.dur(p.getMainHandItem()) + "/" + FormGunItem.durMax(p.getMainHandItem()))); }, 3));
    }
}

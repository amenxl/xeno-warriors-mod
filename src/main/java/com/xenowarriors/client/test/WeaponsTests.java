package com.xenowarriors.client.test;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.character.ModCapabilities;
import com.xenowarriors.client.forms.FormWheelScreen;
import com.xenowarriors.client.forms.FormsClient;
import com.xenowarriors.client.forms.FormsFx;
import com.xenowarriors.forms.Form;
import com.xenowarriors.forms.FormGunItem;
import com.xenowarriors.forms.FormItems;
import com.xenowarriors.forms.WeaponForms;
import com.xenowarriors.rhalix.WireLevel;
import com.xenowarriors.rhalix.Wires;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.List;

/**
 * UI tests for the weapon forms (focus "weapons", "dmzweapons"; extra words pick parts: gate, gun, wheel, umbrella,
 * glide, wires, bind, mash, tools, gallery - none = all). Results: "XW-TEST weapons ..." lines.
 */
public final class WeaponsTests {
    private WeaponsTests() {}

    public static void log(String s) { XenoWarriors.LOGGER.info("XW-TEST weapons " + s); }

    private static ServerPlayer sp() {
        Minecraft mc = Minecraft.getInstance();
        var s = mc.getSingleplayerServer();
        return s == null || mc.player == null ? null : s.getPlayerList().getPlayer(mc.player.getUUID());
    }

    private static void server(java.util.function.Consumer<ServerPlayer> f) {
        var s = Minecraft.getInstance().getSingleplayerServer();
        if (s != null) s.execute(() -> { ServerPlayer p = sp(); if (p != null) try { f.accept(p); } catch (Throwable t) { log("step error " + t); XenoWarriors.LOGGER.error("XW-TEST weapons", t); } });
    }

    private static void character(MikikoTests.Steps l, CharacterType t) {
        l.add(null, () -> server(p -> com.xenowarriors.character.CharacterManager.setCharacter(p, t)), 40);
    }

    /** Sets the levels that gate the forms (weapon = Hekublade or wires level, whichever applies). */
    private static void levels(ServerPlayer p, int weapon, int mining, int gun) {
        p.getCapability(ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> { d.bladeLevel = weapon; d.bladeXp = 0; d.miningLevel = mining; d.miningXp = 0; });
        WireLevel.set(p, weapon);
        com.xenowarriors.compat.GunLevel.setLevel(p, gun);
        com.xenowarriors.stats.Mining.sync(p);
        WeaponForms.sync(p, true);
    }

    private static String names(int mask) {
        List<String> l = new ArrayList<>();
        for (Form f : Form.values()) if ((mask & (1 << f.ordinal())) != 0) l.add(f.name());
        return l.toString();
    }

    private static String held(ServerPlayer p) {
        ItemStack s = p.getMainHandItem();
        return s.isEmpty() ? "nothing" : String.valueOf(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(s.getItem()));
    }

    private static LivingEntity dummy(ServerPlayer p, double dx, double dz, String tag) {
        var h = EntityType.HUSK.create(p.serverLevel());
        h.moveTo(p.getX() + dx, p.getY(), p.getZ() + dz, 180F, 0F);
        h.setNoAi(true);
        h.setPersistenceRequired();
        h.getAttribute(Attributes.MAX_HEALTH).setBaseValue(400);
        h.setHealth(400);
        h.addTag("xw_wp_dummy");
        h.addTag(tag);
        h.setCustomName(Component.literal(tag));
        p.serverLevel().addFreshEntity(h);
        return h;
    }

    private static void clearDummies(ServerPlayer p) {
        p.serverLevel().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(60), e -> e.getTags().contains("xw_wp_dummy")).forEach(e -> e.discard());
    }

    /** A clear, flat, open-sky spot to work in (the same one each time). */
    private static final double[] HOME = new double[3];

    private static void arena(ServerPlayer p) {
        var level = p.serverLevel();
        if (HOME[1] == 0) {
            BlockPos at = HOME[0] != 0 || HOME[2] != 0 ? BlockPos.containing(HOME[0], 64, HOME[2]) : p.blockPosition();
            BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at);
            HOME[0] = top.getX() + 0.5; HOME[1] = top.getY() + 1; HOME[2] = top.getZ() + 0.5;
        }
        int y0 = (int) HOME[1];
        for (int x = -8; x <= 8; x++) for (int z = -8; z <= 10; z++) {
            BlockPos b = BlockPos.containing(HOME[0] + x, y0 - 1, HOME[2] + z);
            level.setBlock(b, Blocks.GRASS_BLOCK.defaultBlockState(), 3);
            for (int y = 0; y < 40; y++) level.setBlock(b.above(1 + y), Blocks.AIR.defaultBlockState(), 3);
        }
        p.teleportTo(level, HOME[0], y0, HOME[2], 0F, 10F);
        p.setYRot(0); p.setYHeadRot(0); p.setXRot(10);
        p.setGameMode(GameType.SURVIVAL);
        p.setHealth(p.getMaxHealth());
        p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE, 20 * 600, 4, false, false));
        p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SATURATION, 20 * 600, 0, false, false));
        level.setDayTime(6000);
    }

    /** An invisible armor stand to film from, relative to the arena's home spot. */
    private static void camera(Minecraft mc, MikikoTests.Steps l, double dx, double dy, double dz, float yaw, float pitch) {
        int[] id = {-1};
        l.add(null, () -> server(p -> {
            p.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.decoration.ArmorStand.class, p.getBoundingBox().inflate(80), a -> a.getTags().contains("xw_wp_cam")).forEach(a -> a.discard());
            var a = EntityType.ARMOR_STAND.create(p.serverLevel());
            a.moveTo(p.getX() + dx, p.getY() + dy, p.getZ() + dz, yaw, pitch); a.setNoGravity(true); a.setInvisible(true); a.setYHeadRot(yaw);
            a.addTag("xw_wp_cam");
            p.serverLevel().addFreshEntity(a); id[0] = a.getId();
        }), 12);
        l.add(null, () -> {
            if (mc.level.getEntity(id[0]) instanceof net.minecraft.world.entity.decoration.ArmorStand a) {
                a.setYRot(yaw); a.yRotO = yaw; a.setXRot(pitch); a.xRotO = pitch; a.setYHeadRot(yaw); a.yHeadRotO = yaw; mc.setCameraEntity(a);
            }
            mc.options.setCameraType(CameraType.FIRST_PERSON); mc.options.hideGui = true;
        }, 5);
    }

    /** The local player facing south (+Z), level - the way the cameras expect. */
    private static void face(Minecraft mc) {
        var p = mc.player;
        p.setYRot(0); p.yRotO = 0; p.setXRot(0); p.xRotO = 0; p.yBodyRot = 0; p.yBodyRotO = 0; p.setYHeadRot(0); p.yHeadRotO = 0;
    }

    private static void uncamera(Minecraft mc, MikikoTests.Steps l) {
        l.add(null, () -> { mc.setCameraEntity(mc.player); mc.options.hideGui = false; mc.options.setCameraType(CameraType.FIRST_PERSON); }, 5);
    }

    public static void run(Minecraft mc, String focus, MikikoTests.Steps l) {
        String f = focus.replaceFirst("^dmz", "").replaceFirst("^weapons", "");
        boolean all = f.isEmpty() || f.contains("all");
        l.add(null, () -> { mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE); mc.getToasts().clear(); mc.setScreen(null);
            server(p -> { com.xenowarriors.character.Seasons.set(p.server, 1); arena(p); }); }, 20);
        if (all || f.contains("gate")) gate(mc, l);
        if (all || f.contains("gallery")) gallery(mc, l);
        if (all || f.contains("wheel")) wheel(mc, l);
        if (all || f.contains("gun")) gun(mc, l);
        if (all || f.contains("hand")) inHand(mc, l);
        if (all || f.contains("tools")) tools(mc, l);
        if (all || f.contains("umbrella")) umbrella(mc, l);
        if (all || f.contains("glide")) glide(mc, l);
        if (all || f.contains("wires")) wires(mc, l);
        if (all || f.contains("wear")) wear(mc, l);
        if (all || f.contains("bind")) bind(mc, l, focus.startsWith("dmz"));
        if (all || f.contains("mash")) mash(mc, l, focus.startsWith("dmz"));
        l.add(null, () -> { mc.setCameraEntity(mc.player); mc.setScreen(null); mc.options.hideGui = false; log("done"); }, 5);
    }

    // ------------------------------------------------------------------ unlock gating

    private static final int[][] HEKU_CASES = {
            {1, 0, 1}, {19, 10, 1}, {20, 9, 1}, {20, 10, 1}, {49, 0, 1}, {50, 0, 1}, {50, 0, 49}, {49, 0, 50}, {50, 0, 50},
            {59, 30, 1}, {60, 29, 1}, {60, 30, 1}, {79, 50, 1}, {80, 49, 1}, {80, 50, 1}, {299, 0, 1}, {300, 0, 1}, {300, 50, 50}};

    private static void gate(Minecraft mc, MikikoTests.Steps l) {
        character(l, CharacterType.MIKIKO);
        l.add(null, () -> server(p -> {
            for (int[] c : HEKU_CASES) {
                levels(p, c[0], c[1], c[2]);
                log(String.format("gate heku blade=%d mining=%d gun=%d -> %s", c[0], c[1], c[2], names(WeaponForms.mask(p))));
            }
            levels(p, 49, 0, 50);
            log("gate heku summon PISTOL at blade 49 / gun 50: " + WeaponForms.summon(p, Form.PISTOL, false) + " (held " + held(p) + ")");
            levels(p, 50, 0, 50);
            log("gate heku summon PISTOL at blade 50 / gun 50: " + WeaponForms.summon(p, Form.PISTOL, false) + " (held " + held(p) + ")");
            levels(p, 299, 0, 1);
            log("gate heku summon UMBRELLA at blade 299: " + WeaponForms.summon(p, Form.UMBRELLA, false));
            levels(p, 300, 0, 1);
            log("gate heku summon UMBRELLA at blade 300: " + WeaponForms.summon(p, Form.UMBRELLA, false) + " (held " + held(p) + ")");
            WeaponForms.removeForms(p.getInventory());
        }), 10);
        character(l, CharacterType.RHALIX);
        l.add(null, () -> server(p -> {
            com.xenowarriors.character.Seasons.set(p.server, 1);
            levels(p, 300, 50, 50);
            log("gate wire season 1 (locked): style=" + WeaponForms.style(p) + " mask=" + names(WeaponForms.mask(p)) + " summon WIRES=" + WeaponForms.summon(p, Form.WIRES, false));
            com.xenowarriors.character.Seasons.set(p.server, 2);
            for (int[] c : HEKU_CASES) {
                levels(p, c[0], c[1], c[2]);
                log(String.format("gate wire wires=%d mining=%d gun=%d -> %s", c[0], c[1], c[2], names(WeaponForms.mask(p))));
            }
            levels(p, 1, 0, 1);
            WeaponForms.tap(p);
            log("gate wire tap at season 2: held " + held(p));
            WeaponForms.tap(p);
            log("gate wire second tap: held " + held(p));
        }), 10);
        l.add(null, () -> log("gate client: style=" + FormsClient.style() + " mask=" + names(FormsClient.mask()) + " level=" + FormsClient.weaponLevel()), 5);
    }

    // ------------------------------------------------------------------ the item gallery (all the models)

    /** Every form's model, big, in a grid with its name. */
    static final class Gallery extends Screen {
        private final List<ItemStack> items;
        private final int perRow;
        private final float scale;
        Gallery(List<ItemStack> items, int perRow, float scale) { super(Component.literal("gallery")); this.items = items; this.perRow = perRow; this.scale = scale; }
        @Override public boolean isPauseScreen() { return false; }
        @Override
        public void render(GuiGraphics g, int mx, int my, float pt) {
            g.fill(0, 0, width, height, 0xFF2A2A34);
            int cell = (int) (16 * scale) + 22;
            int rows = (items.size() + perRow - 1) / perRow;
            int x0 = (width - perRow * cell) / 2, y0 = (height - rows * (cell + 6)) / 2;
            for (int i = 0; i < items.size(); i++) {
                int cx = x0 + (i % perRow) * cell + cell / 2, cy = y0 + (i / perRow) * (cell + 6) + cell / 2;
                g.fill(cx - cell / 2 + 2, cy - cell / 2 + 2, cx + cell / 2 - 2, cy + cell / 2 - 2, 0xFF3C3C4A);
                g.pose().pushPose();
                g.pose().translate(cx, cy - 4, 0);
                g.pose().scale(scale, scale, 1F);
                g.renderItem(items.get(i), -8, -8);
                g.pose().popPose();
                String n = items.get(i).getHoverName().getString();
                g.pose().pushPose();
                g.pose().translate(cx, cy + cell / 2F - 12, 0);
                g.pose().scale(0.75F, 0.75F, 1F);
                g.drawCenteredString(font, n, 0, 0, 0xFFFFFF);
                g.pose().popPose();
            }
        }
    }

    private static List<ItemStack> stacks(Form.Style s, Form... forms) {
        List<ItemStack> l = new ArrayList<>();
        for (Form f : forms) l.add(FormItems.stack(s, f));
        return l;
    }

    private static void gallery(Minecraft mc, MikikoTests.Steps l) {
        l.add(null, () -> mc.setScreen(new Gallery(stacks(Form.Style.HEKU, Form.PISTOL, Form.RIFLE, Form.SHOTGUN), 3, 6F)), 10);
        l.add("weapons_gallery_heku_guns", () -> {}, 5);
        l.add(null, () -> mc.setScreen(new Gallery(stacks(Form.Style.WIRE, Form.PISTOL, Form.RIFLE, Form.SHOTGUN), 3, 6F)), 10);
        l.add("weapons_gallery_wire_guns", () -> {}, 5);
        l.add(null, () -> {
            List<ItemStack> t = stacks(Form.Style.HEKU, Form.PICKAXE, Form.AXE, Form.SHOVEL, Form.HOE, Form.UMBRELLA);
            t.addAll(stacks(Form.Style.WIRE, Form.PICKAXE, Form.AXE, Form.SHOVEL, Form.HOE, Form.WIRES, Form.WIRE_SWORD));
            mc.setScreen(new Gallery(t, 4, 3.4F));
        }, 10);
        l.add("weapons_gallery_tools", () -> {}, 5);
        l.add(null, () -> mc.setScreen(null), 5);
    }

    // ------------------------------------------------------------------ the hold wheel

    private static void wheel(Minecraft mc, MikikoTests.Steps l) {
        character(l, CharacterType.MIKIKO);
        l.add(null, () -> { mc.options.hideGui = false; mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            server(p -> { arena(p); levels(p, 300, 50, 50); p.getInventory().clearContent(); p.removeAllEffects(); }); }, 20);
        l.add(null, () -> { FormWheelScreen.testHover = Form.RIFLE; FormsClient.testOpenWheel(); }, 12);
        l.add("weapons_wheel_heku_charging", () -> {}, 40);
        l.add("weapons_wheel_heku_powered", () -> server(p -> log("wheel heku charged=" + WeaponForms.charged(p))), 3);
        l.add(null, () -> { if (mc.screen instanceof FormWheelScreen w) w.release(); }, 10);
        l.add(null, () -> server(p -> log("wheel heku pick RIFLE (powered): held " + held(p) + " strength=" + p.hasEffect(net.minecraft.world.effect.MobEffects.DAMAGE_BOOST))), 5);
        // a quick pick (not charged): no power-up
        l.add(null, () -> server(p -> p.removeAllEffects()), 3);
        l.add(null, () -> { FormWheelScreen.testHover = Form.PICKAXE; FormsClient.testOpenWheel(); }, 10);
        l.add(null, () -> { if (mc.screen instanceof FormWheelScreen w) w.release(); }, 10);
        l.add(null, () -> server(p -> log("wheel heku quick pick PICKAXE: held " + held(p) + " strength=" + p.hasEffect(net.minecraft.world.effect.MobEffects.DAMAGE_BOOST))), 5);
        // locked forms on the wheel: lower levels
        l.add(null, () -> server(p -> levels(p, 60, 30, 1)), 10);
        l.add(null, () -> { FormWheelScreen.testHover = Form.UMBRELLA; FormsClient.testOpenWheel(); }, 10);
        l.add("weapons_wheel_heku_locked", () -> {}, 5);
        l.add(null, () -> { if (mc.screen instanceof FormWheelScreen w) w.release(); }, 10);
        l.add(null, () -> server(p -> log("wheel heku pick locked UMBRELLA at 60: held " + held(p))), 5);
        // Rhalix's wheel (Season 2)
        character(l, CharacterType.RHALIX);
        l.add(null, () -> server(p -> { com.xenowarriors.character.Seasons.set(p.server, 2); arena(p); levels(p, 50, 10, 50); p.getInventory().clearContent(); }), 20);
        l.add(null, () -> { FormWheelScreen.testHover = Form.SHOTGUN; FormsClient.testOpenWheel(); }, 50);
        l.add("weapons_wheel_wire", () -> {}, 3);
        l.add(null, () -> { if (mc.screen instanceof FormWheelScreen w) w.release(); }, 10);
        l.add(null, () -> server(p -> log("wheel wire pick SHOTGUN (powered): held " + held(p) + " strength=" + p.hasEffect(net.minecraft.world.effect.MobEffects.DAMAGE_BOOST))), 5);
        l.add(null, () -> FormWheelScreen.testHover = null, 1);
    }

    // ------------------------------------------------------------------ guns: durability, the broken cooldown, infinite ammo, gun XP

    private static void gun(Minecraft mc, MikikoTests.Steps l) {
        character(l, CharacterType.MIKIKO);
        l.add(null, () -> server(p -> { arena(p); clearDummies(p); p.getInventory().clearContent(); levels(p, 50, 0, 50); WeaponForms.testWear = true;
            p.getPersistentData().remove(WeaponForms.BROKEN); }), 10);
        LivingEntity[] tgt = new LivingEntity[1];
        l.add(null, () -> server(p -> {
            tgt[0] = dummy(p, 0, 6, "target");
            for (int lv : new int[]{1, 50, 100, 500, 2000}) log("gun durability at gun level " + lv + ": " + FormGunItem.durabilityFor(lv));
            WeaponForms.summon(p, Form.PISTOL, false);
            ItemStack g = p.getMainHandItem();
            log("gun summoned " + held(p) + " durability " + FormGunItem.dur(g) + "/" + FormGunItem.durMax(g));
        }), 10);
        l.add(null, () -> server(p -> {
            p.setYRot(0); p.setXRot(8);
            ItemStack g = p.getMainHandItem();
            if (!(g.getItem() instanceof FormGunItem gun)) { log("gun: no gun in hand"); return; }
            int gunBefore = com.xenowarriors.compat.GunLevel.level(p);
            float xpBefore = com.xenowarriors.compat.GunLevel.xp(p);
            WeaponForms.SHOTS = 0; WeaponForms.HITS = 0;
            int shots = 0;
            while (p.getMainHandItem() == g && shots < 400) {
                p.getCooldowns().removeCooldown(gun);
                gun.fire(p, g);
                shots++;
                if (tgt[0] != null) tgt[0].setHealth(tgt[0].getMaxHealth());          // (a dummy that doesn't die)
                if (shots == 1 || shots == 50 || shots == 189) log("gun shot " + shots + ": durability " + FormGunItem.dur(g) + " (no reloads, ammo unlimited)");
            }
            log(String.format("gun fired %d shots (hits %d) until it broke; held now %s; gun level %d -> %d (xp %.1f -> %.1f)", shots, WeaponForms.HITS, held(p),
                    gunBefore, com.xenowarriors.compat.GunLevel.level(p), xpBefore, com.xenowarriors.compat.GunLevel.xp(p)));
            log("gun broken cooldown left " + WeaponForms.gunCooldownLeft(p) + " ticks; summon RIFLE now: " + WeaponForms.summon(p, Form.RIFLE, false) + " (held " + held(p) + ")");
            log("gun summon BLADE during the cooldown (not a gun): " + WeaponForms.summon(p, Form.BLADE, false) + " (held " + held(p) + ")");
        }), 30);
        l.add(null, () -> log("gun client wheel cooldown " + FormsClient.gunCooldown() + " ticks"), 3);
        l.add(null, () -> { FormWheelScreen.testHover = Form.PISTOL; FormsClient.testOpenWheel(); }, 8);
        l.add("weapons_wheel_gun_cooldown", () -> {}, 3);
        l.add(null, () -> { if (mc.screen instanceof FormWheelScreen w) w.release(); FormWheelScreen.testHover = null; }, 10);
        l.add(null, () -> server(p -> {
            p.getPersistentData().putLong(WeaponForms.BROKEN, p.serverLevel().getGameTime());       // (as if 30 seconds had passed)
            log("gun after the cooldown: summon SHOTGUN " + WeaponForms.summon(p, Form.SHOTGUN, false) + " (held " + held(p) + ")");
            ItemStack g = p.getMainHandItem();
            if (g.getItem() instanceof FormGunItem gun) {
                p.setXRot(8);
                WeaponForms.HITS = 0;
                p.getCooldowns().removeCooldown(gun);
                int h = gun.fire(p, g);
                log("gun shotgun blast: pellets hit " + h + ", durability " + FormGunItem.dur(g) + "/" + FormGunItem.durMax(g));
            }
            levels(p, 50, 0, 500);
            WeaponForms.summon(p, Form.RIFLE, false);
            log("gun at gun level 500: rifle durability " + FormGunItem.durMax(p.getMainHandItem()));
            WeaponForms.testWear = false;
            clearDummies(p);
        }), 10);
    }

    /** Each gun model in Mikiko's / Rhalix's hands, aimed (third person, from the side and from behind). */
    private static void inHand(Minecraft mc, MikikoTests.Steps l) {
        for (Form.Style s : Form.Style.values()) {
            character(l, s == Form.Style.HEKU ? CharacterType.MIKIKO : CharacterType.RHALIX);
            l.add(null, () -> server(p -> { com.xenowarriors.character.Seasons.set(p.server, 2); arena(p); levels(p, 300, 50, 50); p.getInventory().clearContent();
                p.getPersistentData().remove(WeaponForms.BROKEN); p.setXRot(0); }), 10);
            camera(mc, l, -1.8, -0.15, 1.6, -131.6F, 6F);
            for (Form f : new Form[]{Form.PISTOL, Form.RIFLE, Form.SHOTGUN}) {
                l.add(null, () -> server(p -> { p.setYRot(0); p.setYHeadRot(0); p.setXRot(0); WeaponForms.summon(p, f, false); }), 45);
                l.add(null, () -> face(mc), 3);
                l.add("weapons_hand_" + s.name().toLowerCase() + "_" + f.name().toLowerCase(), () -> {}, 3);
            }
            // first person, aiming the rifle
            l.add(null, () -> { mc.setCameraEntity(mc.player); mc.options.hideGui = false; mc.options.setCameraType(CameraType.FIRST_PERSON);
                server(p -> WeaponForms.summon(p, Form.RIFLE, false)); }, 45);
            l.add("weapons_fp_" + s.name().toLowerCase() + "_rifle", () -> {}, 3);
            l.add(null, () -> server(p -> WeaponForms.summon(p, Form.PISTOL, false)), 45);
            l.add("weapons_fp_" + s.name().toLowerCase() + "_pistol", () -> {}, 3);
            uncamera(mc, l);
        }
    }

    // ------------------------------------------------------------------ tools

    private static void tools(Minecraft mc, MikikoTests.Steps l) {
        for (Form.Style s : Form.Style.values()) {
            character(l, s == Form.Style.HEKU ? CharacterType.MIKIKO : CharacterType.RHALIX);
            l.add(null, () -> server(p -> {
                com.xenowarriors.character.Seasons.set(p.server, 2);
                arena(p); p.getInventory().clearContent();
                var stone = Blocks.STONE.defaultBlockState();
                var obsidian = Blocks.OBSIDIAN.defaultBlockState();
                float diamond = new ItemStack(Items.DIAMOND_PICKAXE).getDestroySpeed(stone), netherite = new ItemStack(Items.NETHERITE_PICKAXE).getDestroySpeed(stone);
                for (int lv : new int[]{80, 300}) {
                    levels(p, lv, 50, 1);
                    WeaponForms.summon(p, Form.PICKAXE, false);
                    ItemStack t = p.getMainHandItem();
                    log(String.format("tools %s pickaxe at weapon %d: stone speed %.1f (diamond %.1f, netherite %.1f), mines obsidian=%s, held %s", s, lv,
                            t.getDestroySpeed(stone), diamond, netherite, t.isCorrectToolForDrops(obsidian), held(p)));
                }
                for (Form f : new Form[]{Form.AXE, Form.SHOVEL, Form.HOE}) {
                    WeaponForms.summon(p, f, false);
                    log("tools " + s + " " + f + ": held " + held(p) + " (forms in inventory " + count(p) + ")");
                }
                // can't be dropped or stored
                ItemStack t = p.getMainHandItem();
                p.drop(t.copy(), false);
                log("tools " + s + " tossing it: item entities nearby " + p.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                        p.getBoundingBox().inflate(4), e -> FormItems.isForm(e.getItem())).size());
            }), 10);
            camera(mc, l, -1.8, -0.15, 1.6, -131.6F, 6F);
            for (Form f : new Form[]{Form.PICKAXE, Form.AXE, Form.SHOVEL, Form.HOE}) {
                l.add(null, () -> server(p -> { p.setYRot(0); p.setYHeadRot(0); WeaponForms.summon(p, f, false); }), 45);
                l.add(null, () -> face(mc), 3);
                l.add("weapons_tool_" + s.name().toLowerCase() + "_" + f.name().toLowerCase(), () -> {}, 3);
            }
            if (s == Form.Style.WIRE) {
                l.add(null, () -> server(p -> WeaponForms.summon(p, Form.WIRE_SWORD, false)), 45);
                l.add("weapons_tool_wire_sword", () -> {}, 3);
            }
            uncamera(mc, l);
        }
    }

    private static int count(ServerPlayer p) {
        int n = 0;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) if (FormItems.isForm(p.getInventory().getItem(i))) n++;
        return n;
    }

    // ------------------------------------------------------------------ umbrella in the rain (Netsu)

    private static void umbrella(Minecraft mc, MikikoTests.Steps l) {
        character(l, CharacterType.MIKIKO);
        long[] r = new long[4];
        l.add(null, () -> server(p -> {
            var found = p.serverLevel().findClosestBiome3d(h -> h.value().hasPrecipitation() && h.value().getBaseTemperature() > 0.3F && h.value().getBaseTemperature() < 1.0F
                    && !h.is(net.minecraft.tags.BiomeTags.IS_OCEAN) && !h.is(net.minecraft.tags.BiomeTags.IS_RIVER), p.blockPosition(), 4000, 32, 64);
            log("umbrella rainy biome: " + (found == null ? "none found" : found.getSecond().unwrapKey().map(k -> k.location().toString()).orElse("?") + " at " + found.getFirst()));
            if (found != null) { HOME[0] = found.getFirst().getX() + 0.5; HOME[1] = 0; HOME[2] = found.getFirst().getZ() + 0.5; }
        }), 20);
        l.add(null, () -> server(p -> { arena(p); p.getInventory().clearContent(); levels(p, 300, 0, 1);
            p.serverLevel().setWeatherParameters(0, 20 * 600, true, false);
            p.getCapability(ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> d.statLevel[2] = 100); }), 60);
        l.add(null, () -> server(p -> {
            log("umbrella rain at Mikiko: " + p.level().isRainingAt(p.blockPosition().above()) + " biome " + p.level().getBiome(p.blockPosition()).unwrapKey().map(k -> k.location().toString()).orElse("?"));
            com.xenowarriors.mikiko.Netsu.start(p);
            r[0] = com.xenowarriors.mikiko.Netsu.remaining(p);
        }), 60);
        l.add(null, () -> server(p -> {
            r[1] = com.xenowarriors.mikiko.Netsu.remaining(p);
            log(String.format("umbrella netsu in the rain, no umbrella: %d -> %d ticks over 60 ticks (lost %d), rainedOn=%s", r[0], r[1], r[0] - r[1], com.xenowarriors.mikiko.Netsu.rainedOn(p)));
            WeaponForms.summon(p, Form.UMBRELLA, false);
            r[2] = com.xenowarriors.mikiko.Netsu.remaining(p);
        }), 60);
        l.add(null, () -> server(p -> {
            r[3] = com.xenowarriors.mikiko.Netsu.remaining(p);
            log(String.format("umbrella netsu under the Heku-umbrella: %d -> %d ticks over 60 ticks (lost %d), rainedOn=%s, held %s", r[2], r[3], r[2] - r[3],
                    com.xenowarriors.mikiko.Netsu.rainedOn(p), held(p)));
        }), 5);
        int[] cols = new int[1];
        l.add(null, () -> { cols[0] = FormsFx.rainColumnsBlocked; FormsFx.canopiesDrawn = 0; }, 1);
        camera(mc, l, 3.2, 1.4, 3.6, 140F, 8F);
        l.add("weapons_umbrella_rain_side", () -> {}, 10);
        camera(mc, l, 0.5, 2.2, -4.5, 0F, 12F);
        l.add("weapons_umbrella_rain_back", () -> {}, 10);
        l.add(null, () -> log("umbrella client: canopies drawn " + FormsFx.canopiesDrawn + ", rain columns stopped at the canopy " + (FormsFx.rainColumnsBlocked - cols[0])), 3);
        uncamera(mc, l);
        l.add(null, () -> server(p -> com.xenowarriors.mikiko.Netsu.stop(p)), 30);
        l.add(null, () -> { mc.player.setXRot(-60); }, 10);
        l.add("weapons_umbrella_rain_firstperson_up", () -> {}, 5);
        // and without it, for comparison
        l.add(null, () -> server(p -> WeaponForms.removeForms(p.getInventory())), 5);
        camera(mc, l, 3.2, 1.4, 3.6, 140F, 8F);
        l.add("weapons_umbrella_rain_none", () -> {}, 10);
        uncamera(mc, l);
        l.add(null, () -> server(p -> { com.xenowarriors.mikiko.Netsu.stop(p); p.serverLevel().setWeatherParameters(20 * 600, 0, false, false); }), 10);
    }

    // ------------------------------------------------------------------ gliding with the umbrella

    private static final List<String> GLIDE = new ArrayList<>();

    private static void glide(Minecraft mc, MikikoTests.Steps l) {
        character(l, CharacterType.MIKIKO);
        float[] hp = new float[1];
        double[] start = new double[3];
        l.add(null, () -> server(p -> { arena(p); p.removeEffect(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE); p.getInventory().clearContent(); levels(p, 300, 0, 1);
            WeaponForms.summon(p, Form.UMBRELLA, false); }), 10);
        l.add(null, () -> server(p -> { hp[0] = p.getHealth(); p.teleportTo(HOME[0], HOME[1] + 16, HOME[2] - 6); p.setYRot(0); p.setDeltaMovement(0, 0, 0); }), 3);
        l.add(null, () -> { var p = mc.player; start[0] = p.getX(); start[1] = p.getY(); start[2] = p.getZ(); }, 20);
        l.add(null, () -> { var p = mc.player; log(String.format("glide after 1s: fell %.2f, drifted forward %.2f, vy=%.3f, gliding=%s",
                start[1] - p.getY(), p.getZ() - start[2], p.getDeltaMovement().y, com.xenowarriors.forms.Umbrella.gliding(p))); }, 1);
        camera(mc, l, 4.5, -0.5, 1.5, 90F, 10F);
        l.add("weapons_glide", () -> {}, 3);
        l.add(null, () -> { var p = mc.player; log(String.format("glide after ~2s: fell %.2f, drifted forward %.2f, vy=%.3f", start[1] - p.getY(), p.getZ() - start[2], p.getDeltaMovement().y)); }, 160);
        l.add(null, () -> server(p -> log(String.format("glide landed=%s health %.1f -> %.1f (fall damage %.1f)", p.onGround(), hp[0], p.getHealth(), hp[0] - p.getHealth()))), 3);
        uncamera(mc, l);
        // for comparison: the same drop without it, from lower
        l.add(null, () -> server(p -> { WeaponForms.removeForms(p.getInventory()); hp[0] = p.getHealth(); p.teleportTo(HOME[0], HOME[1] + 12, HOME[2]); p.setDeltaMovement(0, 0, 0); }), 60);
        l.add(null, () -> server(p -> { log(String.format("glide (no umbrella, 12 blocks) health %.1f -> %.1f (fall damage %.1f)", hp[0], p.getHealth(), hp[0] - p.getHealth()));
            p.setHealth(p.getMaxHealth()); arena(p); }), 3);
    }

    // ------------------------------------------------------------------ outgrown gear wears out faster

    private static void wear(Minecraft mc, MikikoTests.Steps l) {
        l.add(null, () -> server(p -> {
            int[] keep = new int[3];
            p.getCapability(ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> { keep[0] = d.statLevel[0]; keep[1] = d.statLevel[1]; keep[2] = d.statLevel[2];
                d.statLevel[0] = 200; d.statLevel[1] = 200; d.statLevel[2] = 200; });
            int lv = com.xenowarriors.stats.Stats.characterLevel(p);
            p.getInventory().clearContent();
            p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            ItemStack wood = new ItemStack(net.minecraft.world.item.Items.WOODEN_SWORD);
            p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, wood);
            wood.hurtAndBreak(1, p, e -> {});
            int alone = wood.getDamageValue();
            p.getInventory().add(new ItemStack(net.minecraft.world.item.Items.DIAMOND_SWORD));
            int before = wood.getDamageValue();
            wood.hurtAndBreak(1, p, e -> {});
            int withDiamond = wood.getDamageValue() - before;
            ItemStack dia = p.getInventory().items.stream().filter(s -> s.is(net.minecraft.world.item.Items.DIAMOND_SWORD)).findFirst().orElse(ItemStack.EMPTY);
            p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, dia.copy());
            p.getInventory().add(new ItemStack(net.minecraft.world.item.Items.WOODEN_SWORD));
            ItemStack held = p.getMainHandItem();
            held.hurtAndBreak(1, p, e -> {});
            int best = held.getDamageValue();
            log(String.format("wear: level %d, wooden sword alone %d, with a diamond sword in the bag %d, diamond (the best) %d -> %s",
                    lv, alone, withDiamond, best, alone == 1 && withDiamond > 1 && best == 1 ? "PASS" : "FAIL"));
            p.getCapability(ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> { d.statLevel[0] = keep[0]; d.statLevel[1] = keep[1]; d.statLevel[2] = keep[2]; });
            p.getInventory().clearContent();
            p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        }), 5);
    }

    // ------------------------------------------------------------------ Rhalix's wire whip

    private static void wires(Minecraft mc, MikikoTests.Steps l) {
        character(l, CharacterType.RHALIX);
        int[] ids = new int[3];
        l.add(null, () -> server(p -> { com.xenowarriors.character.Seasons.set(p.server, 2); arena(p); clearDummies(p); p.getInventory().clearContent(); levels(p, 1, 0, 1);
            WeaponForms.summon(p, Form.WIRES, false);
            ids[0] = dummy(p, 0.6, 2.0, "2 blocks").getId(); ids[1] = dummy(p, -1.2, 3.6, "3.6 blocks").getId(); ids[2] = dummy(p, 0, 5.6, "5.6 blocks").getId();
            p.setYRot(0); p.setYHeadRot(0); p.setXRot(5); }), 20);
        l.add(null, () -> server(p -> {
            float[] before = new float[3];
            for (int i = 0; i < 3; i++) before[i] = ((LivingEntity) p.level().getEntity(ids[i])).getHealth();
            int hits = Wires.whip(p);
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < 3; i++) { LivingEntity e = (LivingEntity) p.level().getEntity(ids[i]); b.append(String.format(" [%s: %.1f -> %.1f]", e.getCustomName().getString(), before[i], e.getHealth())); }
            log("wires whip (reach " + Wires.REACH + "): hit " + hits + b + ", wire level " + WireLevel.level(p) + " progress " + WireLevel.progress(p));
            log("wires whip again at once (cooldown): hit " + Wires.whip(p));
        }), 14);
        camera(mc, l, -3.5, 2.2, 1.0, -70F, 22F);
        l.add(null, () -> FormsClient.whip(), 2);
        l.add("weapons_whip_a", () -> {}, 1);
        l.add("weapons_whip_b", () -> {}, 2);
        l.add(null, () -> {}, 10);
        l.add(null, () -> FormsClient.whip(), 3);
        l.add("weapons_whip_c", () -> {}, 2);
        l.add("weapons_whip_d", () -> {}, 2);
        l.add("weapons_whip_e", () -> {}, 2);
        l.add("weapons_whip_f", () -> {}, 2);
        l.add(null, () -> { log("whip frames drawn " + FormsFx.whipsDrawn + " gametime " + mc.level.getGameTime());
            FormsFx.fx(new com.xenowarriors.network.WireFxPacket(mc.player.getId(), com.xenowarriors.network.WireFxPacket.WHIP, -1, 0)); }, 2);
        l.add("weapons_whip_g", () -> {}, 3);
        l.add("weapons_whip_h", () -> log("whip frames drawn " + FormsFx.whipsDrawn + " showing " + FormsFx.whipping(mc.player)), 2);
        l.add(null, () -> log("wires client whips sent " + FormsClient.whipsSent + ", showing " + FormsFx.whipping(mc.player)), 3);
        // Rhalix's fight animations pass 2: whole-body spins and shoulders that follow the torso
        l.add(null, () -> {
            var rl = com.xenowarriors.client.mikiko.GeoAnims.RHALIX;
            float spin = com.xenowarriors.client.mikiko.GeoAnims.rootYaw(rl, "rh_spin_back_kick", 0.39F * 20F) * Mth.RAD_TO_DEG;
            float whirl = com.xenowarriors.client.mikiko.GeoAnims.rootYaw(rl, "rh_sw_whirl", 0.42F * 20F) * Mth.RAD_TO_DEG;
            var m = new net.minecraft.client.model.PlayerModel<net.minecraft.client.player.AbstractClientPlayer>(
                    mc.getEntityModels().bakeLayer(net.minecraft.client.model.geom.ModelLayers.PLAYER), false);
            m.rightArm.x = -5F; m.rightArm.z = 0F; m.body.yRot = 0F;
            com.xenowarriors.client.mikiko.GeoAnims.apply(m, rl, "rh_jab_right", 0.22F * 20F, false, 1F);
            float want = -Mth.cos(m.body.yRot) * 5F, wantZ = Mth.sin(m.body.yRot) * 5F;
            boolean ok = Math.abs(spin) > 150F && Math.abs(whirl) > 250F && Math.abs(m.rightArm.x - want) < 0.3F && Math.abs(m.rightArm.z - wantZ) < 0.3F;
            log(String.format("rhfight spin=%.0f whirl=%.0f jab twist=%.1f arm=(%.2f,%.2f) want=(%.2f,%.2f) ok=%s",
                    spin, whirl, m.body.yRot * Mth.RAD_TO_DEG, m.rightArm.x, m.rightArm.z, want, wantZ, ok));
            try (var in = mc.getResourceManager().getResource(com.xenowarriors.XenoWarriors.id("models/item/3d/rhalix_wires.json")).get().open()) {
                var j = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in)).getAsJsonObject();
                log("rhfight wires model elements=" + j.getAsJsonArray("elements").size());
            } catch (Exception e) { log("rhfight wires model error " + e); }
        }, 2);
        uncamera(mc, l);
        // the wire sword
        l.add(null, () -> server(p -> { WeaponForms.summon(p, Form.WIRE_SWORD, false); log("wires sword: held " + held(p)); clearDummies(p); }), 5);
    }

    // ------------------------------------------------------------------ the bind: drain per second, cooldown

    private static void bind(Minecraft mc, MikikoTests.Steps l, boolean dmz) {
        character(l, CharacterType.RHALIX);
        int[] id = {-1};
        l.add(null, () -> server(p -> { com.xenowarriors.character.Seasons.set(p.server, 2); arena(p); clearDummies(p); p.getInventory().clearContent(); levels(p, 20, 0, 1);
            WeaponForms.summon(p, Form.WIRES, false); p.getPersistentData().remove("xw_wire_bind_ready");
            id[0] = dummy(p, 0, 4.5, "bound").getId(); p.setYRot(0); p.setYHeadRot(0); p.setXRot(10); Wires.LOG.clear(); }), 20);
        // lock on from the client, then hold use (as the client does it)
        l.add(null, () -> { mc.player.setYRot(0); mc.player.setXRot(10); com.xenowarriors.client.LockOn.toggle();
            log("bind lock-on target " + (com.xenowarriors.client.LockOn.target() == null ? "none" : com.xenowarriors.client.LockOn.target().getName().getString())); }, 5);
        l.add(null, () -> log("bind client sent: " + FormsClient.bind(mc)), 30);
        camera(mc, l, -3.2, 2.0, 2.0, -60F, 18F);
        l.add("weapons_bind_red_wires", () -> {}, 20);
        camera(mc, l, 2.5, 1.8, 6.5, 150F, 12F);
        l.add("weapons_bind_red_wires_close", () -> {}, 40);
        l.add(null, () -> server(p -> { for (String s : Wires.LOG) log("bind drain " + s); Wires.LOG.clear(); }), 3);
        l.add(null, () -> FormsClient.unbind(), 10);
        l.add(null, () -> server(p -> {
            for (String s : Wires.LOG) log("bind " + s);
            Wires.LOG.clear();
            log("bind cooldown left after letting go: " + Wires.cooldownLeft(p) + " ticks (" + Wires.COOLDOWN + ")");
            log("bind again at once: " + Wires.startBind(p, id[0]));
            p.getPersistentData().remove("xw_wire_bind_ready");
            log("bind after the cooldown: " + Wires.startBind(p, id[0]));
        }), Wires.MAX_BIND + 30);
        l.add(null, () -> server(p -> { for (String s : Wires.LOG) log("bind " + s); Wires.LOG.clear(); clearDummies(p); }), 3);
        uncamera(mc, l);
    }


    // ------------------------------------------------------------------ mash to escape (the player is the one bound)

    private static void mash(Minecraft mc, MikikoTests.Steps l, boolean dmz) {
        character(l, CharacterType.MIKIKO);
        ServerPlayer[] rh = new ServerPlayer[1];
        l.add(null, () -> server(p -> {
            com.xenowarriors.character.Seasons.set(p.server, 2);
            arena(p); p.getInventory().clearContent();
            ServerPlayer f = net.minecraftforge.common.util.FakePlayerFactory.get(p.serverLevel(), new com.mojang.authlib.GameProfile(java.util.UUID.nameUUIDFromBytes("WpRhalix".getBytes()), "WpRhalix"));
            f.getCapability(ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> d.setType(CharacterType.RHALIX));
            f.moveTo(p.getX(), p.getY(), p.getZ() + 5, 180F, 0F);
            f.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, FormItems.stack(Form.Style.WIRE, Form.WIRES));
            f.getPersistentData().remove("xw_wire_bind_ready");
            rh[0] = f;
            Wires.LOG.clear();
            com.xenowarriors.karma.Karma.add(p, 200);
            com.xenowarriors.compat.DmzPetKi.setKi(p, com.xenowarriors.compat.DmzPetKi.ki(p)[1]);
            float[] ki = com.xenowarriors.compat.DmzPetKi.ki(p);
            log("mash: a second player (Rhalix) binds Mikiko: " + Wires.startBind(f, p.getId()) + ", karma " + com.xenowarriors.karma.Karma.get(p)
                    + ", ki " + ki[0] + "/" + ki[1] + (dmz ? " (DragonMineZ)" : ""));
        }), 50);
        l.add(null, () -> server(p -> { for (String s : Wires.LOG) log("mash drain per second (bound player) " + s); Wires.LOG.clear(); }), 3);
        l.add(null, () -> { mc.options.hideGui = false; mc.setCameraEntity(mc.player); mc.options.setCameraType(CameraType.FIRST_PERSON);
            log("mash client: bound by " + FormsFx.boundBy(mc.player.getId()) + ", progress " + FormsFx.mashProgress(mc.player.getId())); }, 10);
        l.add("weapons_mash_prompt", () -> {}, 3);
        for (int i = 0; i < 8; i++) l.add(null, FormsClient::mash, 2);
        l.add(null, () -> log("mash client after 8 clicks: progress " + FormsFx.mashProgress(mc.player.getId()) + "%"), 2);
        l.add("weapons_mash_halfway", () -> {}, 3);
        l.add(null, () -> server(p -> {
            var b = Wires.holding(p);
            log("mash server: bound=" + (b != null) + " mashes " + (b == null ? -1 : b.mash) + "/" + Wires.MASH + ", rooted=" + com.xenowarriors.skill.Rooting.isRooted(p) + ", karma " + com.xenowarriors.karma.Karma.get(p));
        }), 3);
        for (int i = 0; i < 9; i++) l.add(null, FormsClient::mash, 2);
        l.add(null, () -> server(p -> {
            for (String s : Wires.LOG) log("mash " + s);
            log("mash after " + FormsClient.mashesSent + " clicks: still bound=" + (Wires.holding(p) != null) + ", Rhalix's bind cooldown " + (rh[0] == null ? -1 : Wires.cooldownLeft(rh[0])) + " ticks");
        }), 5);
        l.add(null, () -> log("mash client: bound by " + FormsFx.boundBy(mc.player.getId())), 5);
        l.add("weapons_mash_free", () -> {}, 3);
    }
}

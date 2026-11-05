package com.xenowarriors.client.test;

import com.mojang.blaze3d.vertex.PoseStack;
import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.client.UiTest;
import com.xenowarriors.client.UiTest.Step;
import com.xenowarriors.compat.epicfight.EfGunCheck;
import com.xenowarriors.forms.Form;
import com.xenowarriors.forms.FormItems;
import com.xenowarriors.forms.FormTools;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * UI tests for the 3D held models of the tools and weapons (focus "models3d"; "efmodels3d" with Epic Fight in battle
 * mode, "dmzmodels3d" with DragonMineZ for its three Skylander guns). Per item: the inventory icon (still the flat
 * sprite), first person, third person; then everything on the ground and in item frames. Each item logs the size of
 * the model drawn in the hand vs in the GUI ("XW-TEST models3d ..."): a flat sprite is 1/16 deep, a 3D model more.
 */
public final class Models3dTests {
    private Models3dTests() {}

    static void log(String s) { XenoWarriors.LOGGER.info("XW-TEST models3d " + s); }

    public static boolean handles(String focus) { return focus != null && focus.replaceFirst("^(ef|dmz)", "").startsWith("models3d"); }

    private static final double[] HOME = new double[3];

    /** item id, whose style (null = not a summoned form), which form, who holds it for the stills. */
    private record Entry(String id, Form.Style style, Form form, CharacterType who) {}

    static List<Entry> entries() {
        List<Entry> l = new ArrayList<>();
        for (Form f : new Form[]{Form.PICKAXE, Form.AXE, Form.SHOVEL, Form.HOE})
            l.add(new Entry("heku_" + f.name().toLowerCase(Locale.ROOT), Form.Style.HEKU, f, CharacterType.MIKIKO));
        l.add(new Entry("rhalix_wires", Form.Style.WIRE, Form.WIRES, CharacterType.RHALIX));
        l.add(new Entry("wire_sword", Form.Style.WIRE, Form.WIRE_SWORD, CharacterType.RHALIX));
        for (Form f : new Form[]{Form.PICKAXE, Form.AXE, Form.SHOVEL, Form.HOE})
            l.add(new Entry("wire_" + f.name().toLowerCase(Locale.ROOT), Form.Style.WIRE, f, CharacterType.RHALIX));
        for (String m : new String[]{"sky", "corrupted"})
            for (String k : new String[]{"sword", "pickaxe", "axe", "shovel", "hoe"}) l.add(new Entry(m + "_" + k, null, null, CharacterType.MIKIKO));
        l.add(new Entry("demonite_sword", null, null, CharacterType.MIKIKO));
        for (String g : new String[]{"blaster_rifle", "burst_cannon", "blaster_mortar"}) l.add(new Entry(g, null, null, CharacterType.MIKIKO));
        return l;
    }

    static Item item(String id) { return ForgeRegistries.ITEMS.getValue(XenoWarriors.id(id)); }

    static boolean present(String id) { Item i = item(id); return i != null && i != net.minecraft.world.item.Items.AIR; }

    static ItemStack stack(Entry e) {
        if (e.style != null) {
            ItemStack s = FormItems.stack(e.style, e.form);
            if (!s.isEmpty()) s.getOrCreateTag().putInt(FormTools.LV, 100);
            return s;
        }
        return new ItemStack(item(e.id));
    }

    private static void server(java.util.function.Consumer<ServerPlayer> f) {
        UiTest.server(p -> { try { f.accept(p); } catch (Throwable t) { log("step error " + t); XenoWarriors.LOGGER.error("XW-TEST models3d", t); } });
    }

    public static void build(Minecraft mc, List<Step> l, String focus) {
        boolean ef = focus.startsWith("ef");
        String tag = ef ? "efm3d_" : "m3d_";
        l.add(new Step(null, () -> { UiTest.noToasts(mc); mc.setScreen(null); mc.options.hideGui = false; mc.options.setCameraType(CameraType.FIRST_PERSON);
            UiTest.character(CharacterType.MIKIKO); }, 40));
        l.add(new Step(null, () -> server(p -> { com.xenowarriors.character.Seasons.set(p.server, 2); arena(p); p.getInventory().clearContent(); }), 20));
        l.add(new Step(null, () -> log("start: epic fight " + EfGunCheck.loaded() + ", dragonminez " + net.minecraftforge.fml.ModList.get().isLoaded("dragonminez")
                + ", embeddium " + net.minecraftforge.fml.ModList.get().isLoaded("embeddium") + ", oculus " + net.minecraftforge.fml.ModList.get().isLoaded("oculus")), 2));
        if (ef) l.add(new Step(null, () -> { EfGunCheck.battleMode(); log("ef: battle mode on"); }, 5));
        CharacterType[] who = {CharacterType.MIKIKO};
        for (Entry e : entries()) {
            if (!present(e.id)) { l.add(new Step(null, () -> log(e.id + ": not registered in this run (DragonMineZ-only) - skipped"), 1)); continue; }
            if (e.who != who[0]) {
                who[0] = e.who;
                l.add(new Step(null, () -> { UiTest.character(e.who); }, 40));
                l.add(new Step(null, () -> server(p -> { arena(p); p.getInventory().clearContent(); }), 15));
                if (ef) l.add(new Step(null, EfGunCheck::battleMode, 5));
            }
            one(mc, l, e, tag, ef);
        }
        gallery(mc, l, tag);
        l.add(new Step(null, () -> { mc.setCameraEntity(mc.player); mc.options.setCameraType(CameraType.FIRST_PERSON); mc.options.hideGui = false;
            server(p -> { p.getInventory().clearContent(); cleanup(p); }); log("done"); }, 10));
    }

    // ------------------------------------------------------------------ one item: icon, first person, third person

    private static void one(Minecraft mc, List<Step> l, Entry e, String tag, boolean ef) {
        l.add(new Step(null, () -> server(p -> { p.getInventory().clearContent();
            p.setItemInHand(InteractionHand.MAIN_HAND, stack(e)); }), 12));
        // the inventory: the icon must still be the flat sprite
        l.add(new Step(null, () -> { face(mc, 0F); mc.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player)); }, 8));
        l.add(new Step(tag + e.id + "_icon", () -> report(mc, e), 2));
        // first person
        l.add(new Step(null, () -> { mc.setScreen(null); mc.options.hideGui = false; mc.options.setCameraType(CameraType.FIRST_PERSON); face(mc, 12F);
            if (ef) EfGunCheck.battleMode(); }, 14));
        l.add(new Step(tag + e.id + "_fp", () -> { face(mc, 12F); if (ef) log(e.id + " ef: " + EfGunCheck.info()); }, 3));
        // third person, from in front of the player off to his right (the hand holding it)
        int[] cam = {-1};
        l.add(new Step(null, () -> server(p -> {
            cleanup(p);
            ArmorStand a = EntityType.ARMOR_STAND.create(p.serverLevel());
            a.moveTo(HOME[0] - 2.2, HOME[1] + 0.1, HOME[2] + 1.6, -126F, 10F); a.setNoGravity(true); a.setInvisible(true); a.setYHeadRot(-126F);
            a.addTag("xw_m3d_cam");
            p.serverLevel().addFreshEntity(a); cam[0] = a.getId();
        }), 10));
        l.add(new Step(null, () -> { face(mc, 0F); mc.options.hideGui = true;
            if (mc.level.getEntity(cam[0]) instanceof ArmorStand a) {
                a.setYRot(-126F); a.yRotO = -126F; a.setXRot(10F); a.xRotO = 10F; a.setYHeadRot(-126F); a.yHeadRotO = -126F; mc.setCameraEntity(a);
            } else mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT); }, 10));
        l.add(new Step(tag + e.id + "_tp", () -> face(mc, 0F), 3));
        l.add(new Step(null, () -> { mc.setCameraEntity(mc.player); mc.options.setCameraType(CameraType.FIRST_PERSON); mc.options.hideGui = false; }, 4));
    }

    /** The baked model per display context: its depth (a flat sprite is 1/16 = 0.06 deep) and height, and its quad count. */
    private static void report(Minecraft mc, Entry e) {
        ItemStack s = mc.player.getMainHandItem();
        BakedModel m = mc.getItemRenderer().getModel(s, mc.level, mc.player, 0);
        StringBuilder b = new StringBuilder(e.id + ": held " + ForgeRegistries.ITEMS.getKey(s.getItem()) + ", model " + m.getClass().getSimpleName());
        for (ItemDisplayContext c : new ItemDisplayContext[]{ItemDisplayContext.GUI, ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,
                ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, ItemDisplayContext.GROUND, ItemDisplayContext.FIXED}) {
            PoseStack ps = new PoseStack();
            BakedModel pm = m.applyTransform(c, ps, false);
            float[] mn = {9, 9, 9}, mx = {-9, -9, -9};
            int quads = 0;
            RandomSource r = RandomSource.create(42L);
            List<net.minecraft.client.renderer.block.model.BakedQuad> all = new ArrayList<>(pm.getQuads(null, null, r));
            for (Direction d : Direction.values()) all.addAll(pm.getQuads(null, d, r));
            for (var q : all) {
                quads++;
                int[] v = q.getVertices();
                int stride = v.length / 4;
                for (int i = 0; i < 4; i++) for (int k = 0; k < 3; k++) {
                    float f = Float.intBitsToFloat(v[i * stride + k]);
                    mn[k] = Math.min(mn[k], f); mx[k] = Math.max(mx[k], f);
                }
            }
            String kind = quads == 0 ? "empty" : (mx[2] - mn[2]) > 0.1F ? "3D" : "flat";
            b.append(String.format(Locale.ROOT, " | %s %s (%d quads, depth %.2f, height %.2f)", c.getSerializedName(), kind, quads, mx[2] - mn[2], mx[1] - mn[1]));
        }
        log(b.toString());
    }

    // ------------------------------------------------------------------ all of them on the ground and in item frames

    private static void gallery(Minecraft mc, List<Step> l, String tag) {
        l.add(new Step(null, () -> server(p -> {
            p.getInventory().clearContent();
            cleanup(p);
            var level = p.serverLevel();
            List<Entry> es = new ArrayList<>();
            for (Entry e : entries()) if (present(e.id)) es.add(e);
            int y0 = (int) HOME[1];
            // frames on the wall 6 blocks ahead: rows of 9
            int frames = 0, ground = 0;
            for (int i = 0; i < es.size(); i++) {
                int col = i % 9 - 4, row = i / 9;
                BlockPos wall = BlockPos.containing(HOME[0] + col, y0 + 3 - row, HOME[2] + 7);
                level.setBlock(wall, Blocks.SPRUCE_PLANKS.defaultBlockState(), 3);
                ItemFrame f = new ItemFrame(level, wall.north(), Direction.NORTH);
                f.setItem(stack(es.get(i)), false);
                f.addTag("xw_m3d");
                if (level.addFreshEntity(f)) frames++;
            }
            // on the ground in front, in a fan
            for (int i = 0; i < es.size(); i++) {
                int col = i % 9 - 4, row = i / 9;
                ItemEntity it = new ItemEntity(level, HOME[0] + col * 0.8, y0 + 0.1, HOME[2] + 2.2 + row * 0.9, stack(es.get(i)), 0, 0, 0);
                it.setNoPickUpDelay(); it.setPickUpDelay(32767); it.setUnlimitedLifetime(); it.setNoGravity(false);
                it.addTag("xw_m3d");
                if (level.addFreshEntity(it)) ground++;
            }
            log("gallery: " + frames + " in item frames, " + ground + " on the ground");
        }), 30));
        l.add(new Step(null, () -> { mc.options.hideGui = true; mc.options.setCameraType(CameraType.FIRST_PERSON); face(mc, 22F); }, 20));
        l.add(new Step(tag + "ground", () -> face(mc, 22F), 3));
        l.add(new Step(null, () -> server(p -> p.teleportTo(p.serverLevel(), HOME[0], HOME[1], HOME[2] + 1.5, 0F, 0F)), 10));
        l.add(new Step(null, () -> face(mc, -6F), 8));
        l.add(new Step(tag + "frames", () -> face(mc, -6F), 3));
        l.add(new Step(null, () -> server(p -> p.teleportTo(p.serverLevel(), HOME[0], HOME[1], HOME[2], 0F, 0F)), 5));
    }

    // ------------------------------------------------------------------ setup

    private static void face(Minecraft mc, float pitch) {
        var p = mc.player;
        if (p == null) return;
        p.setYRot(0F); p.yRotO = 0F; p.setYHeadRot(0F); p.yHeadRotO = 0F; p.yBodyRot = 0F; p.yBodyRotO = 0F;
        p.setXRot(pitch); p.xRotO = pitch;
        p.setDeltaMovement(0, p.getDeltaMovement().y, 0);
    }

    private static void cleanup(ServerPlayer p) {
        p.serverLevel().getEntities((net.minecraft.world.entity.Entity) null, p.getBoundingBox().inflate(40),
                x -> x.getTags().contains("xw_m3d_cam") || x.getTags().contains("xw_m3d")).forEach(x -> x.discard());
    }

    /** A flat lit floor and a pale back wall, noon, survival (so the inventory screen is the survival one). */
    private static void arena(ServerPlayer p) {
        var level = p.serverLevel();
        if (HOME[1] == 0) {
            BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p.blockPosition());
            HOME[0] = top.getX() + 0.5; HOME[1] = top.getY() + 1; HOME[2] = top.getZ() + 0.5;
        }
        int y0 = (int) HOME[1];
        for (int x = -10; x <= 10; x++) for (int z = -8; z <= 12; z++) {
            BlockPos b = BlockPos.containing(HOME[0] + x, y0 - 1, HOME[2] + z);
            level.setBlock(b, Blocks.GRASS_BLOCK.defaultBlockState(), 3);
            for (int y = 0; y < 20; y++) level.setBlock(b.above(1 + y), Blocks.AIR.defaultBlockState(), 3);
        }
        for (int x = -10; x <= 10; x++) for (int y = 0; y < 8; y++)
            level.setBlock(BlockPos.containing(HOME[0] + x, y0 + y, HOME[2] + 8), Blocks.SMOOTH_QUARTZ.defaultBlockState(), 3);
        p.teleportTo(level, HOME[0], y0, HOME[2], 0F, 0F);
        p.setGameMode(GameType.SURVIVAL);
        p.setHealth(p.getMaxHealth());
        p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE, 20 * 900, 4, false, false));
        p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SATURATION, 20 * 900, 0, false, false));
        level.setDayTime(6000);
        level.setWeatherParameters(6000, 0, false, false);
    }
}

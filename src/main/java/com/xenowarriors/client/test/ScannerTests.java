package com.xenowarriors.client.test;

import com.mojang.blaze3d.vertex.PoseStack;
import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.client.KarmaScanClient;
import com.xenowarriors.client.UiTest;
import com.xenowarriors.client.UiTest.Step;
import com.xenowarriors.client.render.ScannerVisorModel;
import com.xenowarriors.faction.Faction;
import com.xenowarriors.faction.Reputation;
import com.xenowarriors.gov.*;
import com.xenowarriors.karma.KarmaLevel;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.*;

/**
 * Karma Scanner tiers (focus "scanners"; "efscanners" with Epic Fight; "packscanners" in the user's real pack via
 * PackTests). Each tier reads under its capacity, overloads above it (the Omni reads 10^15 and never breaks), reads out
 * to its range and no further; recipes, shop standing, creative tab colours, the dye recipe, Scanner Eye's reach; the
 * worn 3D visor (Forge armor-model hook + our characters' layer), the 3D held model; Gov guards wearing scanners, and a
 * guard whose scanner overloads on a too-strong player panicking and running. Logs "XW-TEST scanners ... PASS/FAIL"
 * and a summary line.
 */
public final class ScannerTests {
    private ScannerTests() {}

    public static boolean handles(String focus) { return focus != null && focus.replaceFirst("^(ef|dmz)", "").startsWith("scanners"); }

    private static int pass, fail;
    private static final List<String> FAILED = new ArrayList<>();
    private static final double[] HOME = new double[3];
    private static final double[] SKY = new double[3];
    private static final int[] IDS = new int[16];
    private static final double[] D = new double[8];
    private static final long[] V = new long[4];

    static void log(String s) { XenoWarriors.LOGGER.info("XW-TEST scanners " + s); }

    static void check(String what, boolean ok, String detail) {
        if (ok) pass++; else { fail++; FAILED.add(what); }
        log(what + ": " + detail + " -> " + (ok ? "PASS" : "FAIL"));
    }

    private static void server(java.util.function.Consumer<ServerPlayer> f) {
        UiTest.server(p -> { try { f.accept(p); } catch (Throwable t) { check("server step", false, String.valueOf(t)); XenoWarriors.LOGGER.error("XW-TEST scanners", t); } });
    }

    private static void client(String what, Runnable r) {
        try { r.run(); } catch (Throwable t) { check(what, false, String.valueOf(t)); XenoWarriors.LOGGER.error("XW-TEST scanners " + what, t); }
    }

    static String f(double d) { return String.format(Locale.ROOT, "%.1f", d); }

    private static final KarmaScanner.Tier[] TIERS = KarmaScanner.Tier.values();

    public static void build(Minecraft mc, List<Step> l, String focus) {
        boolean ef = focus.startsWith("ef");
        l.add(new Step(null, () -> { UiTest.noToasts(mc); mc.setScreen(null); mc.options.hideGui = false; mc.options.setCameraType(CameraType.FIRST_PERSON);
            UiTest.character(CharacterType.RHALIX); }, 40));
        l.add(new Step(null, () -> server(p -> { ground(p); sky(p); p.getInventory().clearContent(); }), 30));
        l.add(new Step(null, () -> mc.setScreen(null), 5));
        if (ef) l.add(new Step(null, () -> { com.xenowarriors.compat.epicfight.EfGunCheck.battleMode(); log("ef battle mode"); }, 5));
        registry(mc, l);
        recipes(l);
        reading(mc, l);
        eye(l);
        shop(mc, l);
        worn(mc, l, ef);
        soldiers(mc, l);
        l.add(new Step(null, () -> { mc.setScreen(null); mc.setCameraEntity(mc.player); mc.options.setCameraType(CameraType.FIRST_PERSON); mc.options.hideGui = false;
            server(p -> { cleanup(p); p.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY); }); }, 10));
        l.add(new Step("scanners_end", () -> log("summary pass=" + pass + " fail=" + fail + (FAILED.isEmpty() ? "" : " failed=" + FAILED)), 2));
    }

    // ------------------------------------------------------------------ places

    /** A flat, cleared patch of ground (stills, guards running). */
    private static void ground(ServerPlayer p) {
        var level = p.serverLevel();
        if (HOME[1] == 0) {
            BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p.blockPosition());
            HOME[0] = top.getX() + 0.5; HOME[1] = top.getY() + 1; HOME[2] = top.getZ() + 0.5;
        }
        int y0 = (int) HOME[1];
        for (int x = -22; x <= 22; x++) for (int z = -22; z <= 22; z++) {
            BlockPos b = BlockPos.containing(HOME[0] + x, y0 - 1, HOME[2] + z);
            level.setBlock(b, Blocks.GRASS_BLOCK.defaultBlockState(), 2);
            level.setBlock(b.below(), Blocks.DIRT.defaultBlockState(), 2);
            for (int y = 0; y < 8; y++) level.setBlock(b.above(1 + y), Blocks.AIR.defaultBlockState(), 2);
        }
    }

    /** A glass block high in the sky: nothing in the way for the range checks. */
    private static void sky(ServerPlayer p) {
        SKY[0] = HOME[0]; SKY[1] = 230; SKY[2] = HOME[2];
        p.serverLevel().setBlock(BlockPos.containing(SKY[0], SKY[1] - 1, SKY[2]), Blocks.GLASS.defaultBlockState(), 3);
    }

    private static void atSky(ServerPlayer p) {
        p.teleportTo(p.serverLevel(), SKY[0], SKY[1], SKY[2], 0F, 0F);
        p.setYRot(0F); p.setXRot(0F); p.setYHeadRot(0F);
    }

    private static void atHome(ServerPlayer p, float yaw) {
        p.teleportTo(p.serverLevel(), HOME[0], HOME[1], HOME[2], yaw, 0F);
        p.setYRot(yaw); p.setXRot(0F); p.setYHeadRot(yaw);
    }

    private static void cleanup(ServerPlayer p) {
        var box = p.getBoundingBox().inflate(200);
        for (var e : p.serverLevel().getEntities((net.minecraft.world.entity.Entity) null, box, e -> e.getTags().contains("xw_sct"))) e.discard();
    }

    // ------------------------------------------------------------------ registry, creative tab, models

    private static void registry(Minecraft mc, List<Step> l) {
        l.add(new Step(null, () -> client("registry", () -> {
            for (KarmaScanner.Tier t : TIERS) {
                Item it = GovItems.scanner(t);
                boolean ok = it instanceof KarmaScanner.Item k && k.tier == t && net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(it).getPath().equals(t.id);
                check("item " + t.id, ok, "capacity " + t.capacityText() + ", range " + (int) t.range + ", durability " + new ItemStack(it).getMaxDamage()
                        + ", armor " + ((KarmaScanner.Item) it).getDefense() + ", toughness " + ((KarmaScanner.Item) it).getToughness());
            }
            // the creative tab: every tier in all 6 lens colours
            net.minecraft.world.item.CreativeModeTabs.tryRebuildTabContents(mc.player.connection.enabledFeatures(), true, mc.level.registryAccess());
            Set<String> seen = new TreeSet<>();
            for (ItemStack s : com.xenowarriors.item.ModItems.TAB.get().getDisplayItems())
                if (s.getItem() instanceof KarmaScanner.Item k) seen.add(k.tier.id + "/" + KarmaScanner.colorName(k.getColor(s)));
            check("creative tab", seen.size() == TIERS.length * KarmaScanner.COLORS.length, seen.size() + " scanner stacks: " + seen);
            // worn 3D model: Forge's armor-model hook gives our visor (vanilla bodies, armor stands, Gov guards)
            var orig = new net.minecraft.client.model.HumanoidModel<net.minecraft.world.entity.LivingEntity>(mc.getEntityModels().bakeLayer(net.minecraft.client.model.geom.ModelLayers.PLAYER_OUTER_ARMOR));
            for (KarmaScanner.Tier t : TIERS) {
                ItemStack s = new ItemStack(GovItems.scanner(t));
                var m = net.minecraftforge.client.ForgeHooksClient.getArmorModel(mc.player, s, EquipmentSlot.HEAD, orig);
                check("worn model " + t.id, m == ScannerVisorModel.get(t), "armor model " + m.getClass().getSimpleName() + ", " + ScannerVisorModel.cubes(t) + " cubes, textures "
                        + ScannerVisorModel.texture(t, false) + " + overlay");
                BakedModel bm = mc.getItemRenderer().getModel(s, mc.level, mc.player, 0);
                double gui = depth(bm, ItemDisplayContext.GUI), fixed = depth(bm, ItemDisplayContext.FIXED), hand = depth(bm, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND);
                check("item model " + t.id, gui < 0.1 && fixed > 0.1 && hand > 0.1, "depth gui " + f2(gui) + " (flat icon), frame " + f2(fixed) + ", hand " + f2(hand) + " (3D)");
                int tint = mc.getItemColors().getColor(KarmaScanner.colored(s.getItem(), KarmaScanner.GOLD), 0) & 0xFFFFFF;
                check("icon tint " + t.id, tint == KarmaScanner.GOLD, "layer0 colour #" + Integer.toHexString(tint));
            }
        }), 3));
    }

    static String f2(double d) { return String.format(Locale.ROOT, "%.2f", d); }

    /** How deep (z) the baked model is for a display context: a flat sprite ~0.06. */
    private static double depth(BakedModel m, ItemDisplayContext c) {
        BakedModel pm = m.applyTransform(c, new PoseStack(), false);
        float mn = 9, mx = -9;
        RandomSource r = RandomSource.create(42L);
        List<net.minecraft.client.renderer.block.model.BakedQuad> all = new ArrayList<>(pm.getQuads(null, null, r));
        for (Direction d : Direction.values()) all.addAll(pm.getQuads(null, d, r));
        for (var q : all) {
            int[] v = q.getVertices();
            int stride = v.length / 4;
            for (int i = 0; i < 4; i++) { float z = Float.intBitsToFloat(v[i * stride + 2]); mn = Math.min(mn, z); mx = Math.max(mx, z); }
        }
        return all.isEmpty() ? 0 : mx - mn;
    }

    // ------------------------------------------------------------------ recipes (real crafting grid), dyeing

    private static void recipes(List<Step> l) {
        l.add(new Step(null, () -> server(p -> {
            check("recipe advanced", craft(p, new Item[]{com.xenowarriors.item.ModItems.MOD_CHIP.get(), com.xenowarriors.item.ModItems.DATA_SHARD.get(), com.xenowarriors.item.ModItems.MOD_CHIP.get(),
                    com.xenowarriors.item.ModItems.DATA_SHARD.get(), GovItems.KARMA_SCANNER.get(), com.xenowarriors.item.ModItems.DATA_SHARD.get(),
                    com.xenowarriors.item.ModItems.MOD_CHIP.get(), Items.ENDER_EYE, com.xenowarriors.item.ModItems.MOD_CHIP.get()}, GovItems.ADVANCED_KARMA_SCANNER.get()),
                    "4 mod chips + 3 data shards + eye of ender + Karma Scanner");
            check("recipe elite", craft(p, new Item[]{com.xenowarriors.item.ModItems.MOD_CHIP.get(), Items.AMETHYST_SHARD, com.xenowarriors.item.ModItems.MOD_CHIP.get(),
                    Items.DIAMOND, GovItems.ADVANCED_KARMA_SCANNER.get(), Items.DIAMOND,
                    com.xenowarriors.item.ModItems.MOD_CHIP.get(), Items.AMETHYST_SHARD, com.xenowarriors.item.ModItems.MOD_CHIP.get()}, GovItems.ELITE_KARMA_SCANNER.get()),
                    "4 mod chips + 2 amethyst shards + 2 diamonds + Advanced");
            Item scale = com.xenowarriors.boss.XenoDragons.SCALE.get();
            check("recipe omni", craft(p, new Item[]{scale, Items.NETHER_STAR, scale,
                    com.xenowarriors.item.ModItems.DATA_SHARD.get(), GovItems.ELITE_KARMA_SCANNER.get(), com.xenowarriors.item.ModItems.DATA_SHARD.get(),
                    scale, com.xenowarriors.item.ModItems.MOD_CHIP.get(), scale}, GovItems.OMNI_KARMA_SCANNER.get()),
                    "4 Xeno Dragon Scales + nether star + 2 data shards + mod chip + Elite");
            check("recipe needs previous tier", !craft(p, new Item[]{com.xenowarriors.item.ModItems.MOD_CHIP.get(), Items.AMETHYST_SHARD, com.xenowarriors.item.ModItems.MOD_CHIP.get(),
                    Items.DIAMOND, GovItems.KARMA_SCANNER.get(), Items.DIAMOND,
                    com.xenowarriors.item.ModItems.MOD_CHIP.get(), Items.AMETHYST_SHARD, com.xenowarriors.item.ModItems.MOD_CHIP.get()}, GovItems.ELITE_KARMA_SCANNER.get()),
                    "a basic scanner in the Elite recipe gives nothing");
            // dyeing (vanilla's armor dye recipe, like leather)
            var c = new TransientCraftingContainer(p.inventoryMenu, 3, 3);
            c.setItem(0, new ItemStack(GovItems.ELITE_KARMA_SCANNER.get()));
            c.setItem(1, new ItemStack(Items.LIME_DYE));
            var r = p.server.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, c, p.level());
            ItemStack out = r.map(x -> x.assemble(c, p.level().registryAccess())).orElse(ItemStack.EMPTY);
            int col = out.getItem() instanceof KarmaScanner.Item k ? k.getColor(out) : -1;
            check("dye recipe", out.is(GovItems.ELITE_KARMA_SCANNER.get()) && col != KarmaScanner.Tier.ELITE.color,
                    "Elite + lime dye -> " + out + " lens #" + Integer.toHexString(col) + " (" + r.map(x -> x.getId().toString()).orElse("no recipe") + ")");
        }), 3));
    }

    private static boolean craft(ServerPlayer p, Item[] grid, Item want) {
        var c = new TransientCraftingContainer(p.inventoryMenu, 3, 3);
        for (int i = 0; i < 9; i++) c.setItem(i, new ItemStack(grid[i]));
        var r = p.server.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, c, p.level());
        ItemStack out = r.map(x -> x.assemble(c, p.level().registryAccess())).orElse(ItemStack.EMPTY);
        log("craft " + net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(want).getPath() + " -> " + out + " via " + r.map(x -> x.getId().toString()).orElse("none"));
        return out.is(want);
    }

    // ------------------------------------------------------------------ reading: under / over capacity, range

    /** An iron golem tuned to about `want` Karma Level (health and attack, then its enemy level). */
    private static LivingEntity target(ServerPlayer p, double dist, double want) {
        var g = EntityType.IRON_GOLEM.create(p.serverLevel());
        g.moveTo(p.getX(), p.getY(), p.getZ() + dist, 180F, 0F);
        g.setNoAi(true); g.setNoGravity(true); g.setYHeadRot(180F); g.addTag("xw_sct");
        p.serverLevel().addFreshEntity(g);
        tune(g, want);
        return g;
    }

    private static void tune(LivingEntity g, double want) {
        var hp = g.getAttribute(Attributes.MAX_HEALTH); var atk = g.getAttribute(Attributes.ATTACK_DAMAGE);
        hp.removeModifiers(); atk.removeModifiers();
        double base;
        if (want < 19000) { hp.setBaseValue(Math.max(1, want / (40 * 4.75))); atk.setBaseValue(15); base = 0; }
        else if (want < 1e11) { hp.setBaseValue(100); atk.setBaseValue(15); base = 19000; }
        else { hp.setBaseValue(1024); atk.setBaseValue(2048); base = 40.0 * 1024 * (1 + 2048 / 4.0); }
        int lvl = base == 0 ? 0 : (int) Math.min(Integer.MAX_VALUE, Math.max(0, Math.ceil(25 * (want / base - 1))));
        g.getPersistentData().putInt("xw_lvl", lvl);
        g.setHealth(g.getMaxHealth());
    }

    private static void reading(Minecraft mc, List<Step> l) {
        for (KarmaScanner.Tier t : TIERS) {
            double under = t.unlimited() ? 1e15 : t.capacity * 0.5, over = t.unlimited() ? 2e15 : t.capacity * 2.0;
            // under capacity: it reads, the visor stays on
            l.add(new Step(null, () -> server(p -> {
                cleanup(p); p.setGameMode(GameType.CREATIVE); atSky(p);
                p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(GovItems.scanner(t)));
                IDS[0] = target(p, 6, under).getId();
            }), 25));
            l.add(new Step("scanners_read_" + t.id, () -> server(p -> {
                atSky(p);
                var e = (LivingEntity) p.serverLevel().getEntity(IDS[0]);
                V[0] = e == null ? -1 : KarmaLevel.of(e);
            }), 4));
            l.add(new Step(null, () -> client("read " + t.id, () -> {
                long[] r = KarmaScanClient.lastReading();
                boolean on = mc.player.getItemBySlot(EquipmentSlot.HEAD).is(GovItems.scanner(t));
                check("reads under capacity " + t.id, on && r[0] == IDS[0] && r[1] == V[0] && !t.overloads(V[0]),
                        "target Karma Level " + KarmaLevel.fmt(V[0]) + " vs capacity " + t.capacityText() + ": HUD shows " + KarmaLevel.fmt(r[1]) + " (entity " + r[0] + "), visor on=" + on);
            }), 2));
            // over capacity: it blows apart (the Omni: still reads)
            l.add(new Step(null, () -> server(p -> {
                cleanup(p); atSky(p);
                p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(GovItems.scanner(t)));
                IDS[0] = target(p, 6, over).getId();
            }), 15));
            l.add(new Step(t == KarmaScanner.Tier.ELITE || t == KarmaScanner.Tier.OMNI ? "scanners_over_" + t.id : null, () -> server(p -> {
                var e = (LivingEntity) p.serverLevel().getEntity(IDS[0]);
                V[1] = e == null ? -1 : KarmaLevel.of(e);
                V[2] = p.getItemBySlot(EquipmentSlot.HEAD).is(GovItems.scanner(t)) ? 1 : 0;
            }), 4));
            l.add(new Step(null, () -> client("over " + t.id, () -> {
                long[] r = KarmaScanClient.lastReading();
                if (t.unlimited())
                    check("omni never overloads", V[2] == 1 && V[1] >= 1_000_000_000_000_000L && r[1] == V[1],
                            "Karma Level " + KarmaLevel.fmt(V[1]) + " read: HUD " + KarmaLevel.fmt(r[1]) + ", visor still on=" + (V[2] == 1));
                else
                    check("overloads above capacity " + t.id, V[2] == 0 && t.overloads(V[1]) && net.minecraft.Util.getMillis() - KarmaScanClient.brokeAt() < 5000,
                            "Karma Level " + KarmaLevel.fmt(V[1]) + " > " + t.capacityText() + ": visor gone=" + (V[2] == 0) + ", ERROR shown " + (net.minecraft.Util.getMillis() - KarmaScanClient.brokeAt()) + " ms ago");
            }), 2));
            // range: reads at range-6, not at range+6
            l.add(new Step(null, () -> server(p -> {
                cleanup(p); atSky(p);
                p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(GovItems.scanner(t)));
                IDS[1] = target(p, t.range - 6, 1000).getId();
            }), 10));
            l.add(new Step(null, () -> server(p -> {
                atSky(p);
                var hit = KarmaScanner.looked(p);
                boolean near = hit != null && hit.getId() == IDS[1];
                cleanup(p);
                IDS[1] = target(p, t.range + 6, 1000).getId();
                D[0] = near ? 1 : 0;
            }), 10));
            l.add(new Step(null, () -> server(p -> {
                atSky(p);
                var hit = KarmaScanner.looked(p);
                var far = p.serverLevel().getEntity(IDS[1]);
                check("range " + t.id, D[0] == 1 && hit == null && far != null, "reads at " + (int) (t.range - 6) + " blocks: " + (D[0] == 1)
                        + ", nothing at " + (int) (t.range + 6) + " (target there: " + (far != null) + ", looked=" + hit + ")");
                cleanup(p);
            }), 5));
        }
        l.add(new Step(null, () -> server(p -> p.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY)), 3));
    }

    // ------------------------------------------------------------------ Scanner Eye reach

    private static void eye(List<Step> l) {
        l.add(new Step(null, () -> server(p -> {
            StringBuilder b = new StringBuilder();
            double[] want = {24, 32, 48, 64};
            boolean ok = GovModItem.eyeRadius(p) == 24;
            for (KarmaScanner.Tier t : TIERS) {
                p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(GovItems.scanner(t)));
                double r = GovModItem.eyeRadius(p);
                ok &= r == want[t.ordinal()];
                b.append(t.id).append('=').append((int) r).append(' ');
            }
            p.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
            check("scanner eye reach", ok, "none=24 " + b);
        }), 3));
    }

    // ------------------------------------------------------------------ the Gov shop: standing gates

    private static int offer(String name) {
        for (int i = 0; i < GovShop.OFFERS.size(); i++) if (GovShop.OFFERS.get(i).name().equals(name)) return i;
        return -1;
    }

    private static int count(ServerPlayer p, Item it) {
        int n = 0;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) if (p.getInventory().getItem(i).is(it)) n++;
        return n;
    }

    private static void shop(Minecraft mc, List<Step> l) {
        l.add(new Step(null, () -> server(p -> {
            cleanup(p); atHome(p, 0F); p.setGameMode(GameType.SURVIVAL); p.getInventory().clearContent();
            p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(GovItems.KARMA_SCANNER.get()));
            BlockPos t = BlockPos.containing(HOME[0], HOME[1], HOME[2] + 2);
            p.serverLevel().setBlock(t, GovItems.TERMINAL.get().defaultBlockState(), 3);
            com.xenowarriors.economy.Money.set(p, 10_000_000);
            String[] names = {"Advanced Karma Scanner", "Elite Karma Scanner", "Omni Karma Scanner"};
            Item[] items = {GovItems.ADVANCED_KARMA_SCANNER.get(), GovItems.ELITE_KARMA_SCANNER.get(), GovItems.OMNI_KARMA_SCANNER.get()};
            int[] below = {0, 300, 600}, at = {100, 500, 800};
            for (int i = 0; i < 3; i++) {
                int idx = offer(names[i]);
                var o = idx < 0 ? null : GovShop.OFFERS.get(idx);
                Reputation.set(p, Faction.GOV, below[i]);
                GovShop.open(p, t);
                int n0 = count(p, items[i]);
                GovShop.buy(p, idx);
                int n1 = count(p, items[i]);
                Reputation.set(p, Faction.GOV, at[i]);
                GovShop.buy(p, idx);
                int n2 = count(p, items[i]);
                check("shop " + names[i], idx >= 0 && n1 == n0 && n2 == n0 + 1, "offer #" + idx + " price " + (o == null ? -1 : o.price()) + ", needs " + (o == null ? "?" : o.need().title)
                        + ": at " + Reputation.Tier.of(below[i]).title + " bought " + (n1 - n0) + ", at " + Reputation.Tier.of(at[i]).title + " bought " + (n2 - n1));
            }
            Reputation.set(p, Faction.GOV, 500);          // Honored: the Omni still shows as restricted
            GovShop.open(p, t);
        }), 15));
        l.add(new Step(null, () -> client("shop scroll", () -> { if (mc.screen instanceof com.xenowarriors.client.screen.GovShopScreen s) s.scrollToEnd(); }), 5));
        l.add(new Step("scanners_shop", () -> log("shop screen " + (mc.screen == null ? "none" : mc.screen.getClass().getSimpleName())), 3));
        l.add(new Step(null, () -> { mc.setScreen(null); server(p -> { Reputation.set(p, Faction.GOV, 0); p.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
            p.serverLevel().setBlock(BlockPos.containing(HOME[0], HOME[1], HOME[2] + 2), Blocks.AIR.defaultBlockState(), 3); }); }, 5));
    }

    // ------------------------------------------------------------------ worn: 3D visor stills

    private static void worn(Minecraft mc, List<Step> l, boolean ef) {
        // the icons: every tier and colour in the inventory
        l.add(new Step(null, () -> server(p -> {
            p.getInventory().clearContent(); p.setGameMode(GameType.SURVIVAL); atHome(p, 0F);
            int hot = 0, inv = 9;          // the hotbar: each tier as it comes; the inventory: the other colours, a row-ish per tier
            for (KarmaScanner.Tier t : TIERS) {
                p.getInventory().setItem(hot++, new ItemStack(GovItems.scanner(t)));
                for (int c : KarmaScanner.COLORS) if (c != t.color) p.getInventory().setItem(inv++, KarmaScanner.colored(GovItems.scanner(t), c));
            }
        }), 10));
        l.add(new Step(null, () -> mc.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player)), 8));
        l.add(new Step("scanners_icons", () -> {}, 3));
        l.add(new Step(null, () -> { mc.setScreen(null); server(p -> p.getInventory().clearContent()); }, 5));
        // worn on characters (our armor layer) and a plain body (the vanilla armor layer), third person from the front
        Object[][] who = {{CharacterType.MIKIKO, KarmaScanner.Tier.BASIC}, {CharacterType.MIKIKO, KarmaScanner.Tier.ADVANCED}, {CharacterType.MIKIKO, KarmaScanner.Tier.ELITE},
                {CharacterType.MIKIKO, KarmaScanner.Tier.OMNI}, {CharacterType.RHALIX, KarmaScanner.Tier.ELITE}, {CharacterType.KYRIE, KarmaScanner.Tier.ADVANCED}, {CharacterType.NONE, KarmaScanner.Tier.OMNI}};
        CharacterType[] cur = {null};
        for (Object[] w : who) {
            CharacterType c = (CharacterType) w[0];
            KarmaScanner.Tier t = (KarmaScanner.Tier) w[1];
            if (c != cur[0]) { cur[0] = c; l.add(new Step(null, () -> UiTest.character(c), 40)); l.add(new Step(null, () -> { mc.setScreen(null); if (ef) com.xenowarriors.compat.epicfight.EfGunCheck.battleMode(); }, 5)); }
            l.add(new Step(null, () -> { mc.setScreen(null); mc.options.hideGui = true; mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                server(p -> { atHome(p, 0F); p.setXRot(-8F); p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(GovItems.scanner(t))); }); ScannerVisorModel.uses = 0; }, 14));
            l.add(new Step("scanners_worn_" + c.getId() + "_" + t.id, () -> check("worn render " + c.getId() + " " + t.id, ScannerVisorModel.uses > 0,
                    ScannerVisorModel.uses + " visor model uses while on screen (" + (c == CharacterType.NONE ? "vanilla armor layer" : "character armor layer") + ")"), 3));
            l.add(new Step(null, () -> { mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); server(p -> p.setYRot(-60F)); }, 8));
            if (t == KarmaScanner.Tier.ELITE || t == KarmaScanner.Tier.OMNI) l.add(new Step("scanners_worn_" + c.getId() + "_" + t.id + "_side", () -> {}, 3));
        }
        l.add(new Step(null, () -> { mc.options.setCameraType(CameraType.FIRST_PERSON); mc.options.hideGui = false; UiTest.character(CharacterType.RHALIX); }, 40));
        // colour variants and tiers on armor stands; the 3D item in frames and on the ground
        l.add(new Step(null, () -> server(p -> {
            cleanup(p); p.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY); atHome(p, 0F); p.setXRot(10F);
            var lv = p.serverLevel();
            for (int i = 0; i < 6; i++) {
                ArmorStand a = EntityType.ARMOR_STAND.create(lv);
                a.moveTo(HOME[0] - 3.75 + i * 1.5, HOME[1], HOME[2] + 4.5, 180F, 0F); a.setYHeadRot(180F); a.setYBodyRot(180F); a.addTag("xw_sct");
                a.setItemSlot(EquipmentSlot.HEAD, KarmaScanner.colored(GovItems.ELITE_KARMA_SCANNER.get(), KarmaScanner.COLORS[i]));
                lv.addFreshEntity(a);
            }
            for (int i = 0; i < 4; i++) {
                ArmorStand a = EntityType.ARMOR_STAND.create(lv);
                a.moveTo(HOME[0] - 3 + i * 2, HOME[1], HOME[2] + 7.5, 180F, 0F); a.setYHeadRot(180F); a.setYBodyRot(180F); a.addTag("xw_sct");
                a.setItemSlot(EquipmentSlot.HEAD, new ItemStack(GovItems.scanner(TIERS[i])));
                lv.addFreshEntity(a);
            }
        }), 20));
        l.add(new Step(null, () -> { mc.options.hideGui = true; server(p -> { atHome(p, 0F); p.setXRot(8F); }); }, 8));
        l.add(new Step("scanners_colors", () -> {}, 3));
        l.add(new Step(null, () -> server(p -> {
            p.teleportTo(p.serverLevel(), HOME[0], HOME[1], HOME[2] + 5.6, 0F, 10F); p.setYRot(0F); p.setXRot(12F); p.setYHeadRot(0F); }), 8));
        l.add(new Step("scanners_tiers_close", () -> {}, 3));
        l.add(new Step(null, () -> server(p -> {
            cleanup(p); atHome(p, 0F); p.setXRot(20F);
            var lv = p.serverLevel();
            int y0 = (int) HOME[1];
            for (int i = 0; i < 4; i++) {
                BlockPos wall = BlockPos.containing(HOME[0] - 3 + i * 2, y0 + 1, HOME[2] + 5);
                lv.setBlock(wall, Blocks.SPRUCE_PLANKS.defaultBlockState(), 3);
                ItemFrame fr = new ItemFrame(lv, wall.north(), Direction.NORTH);
                fr.setItem(KarmaScanner.colored(GovItems.scanner(TIERS[i]), KarmaScanner.COLORS[i]), false); fr.addTag("xw_sct");
                lv.addFreshEntity(fr);
                ItemEntity it = new ItemEntity(lv, HOME[0] - 3 + i * 2, y0 + 0.1, HOME[2] + 2.5, new ItemStack(GovItems.scanner(TIERS[i])), 0, 0, 0);
                it.setPickUpDelay(32767); it.setUnlimitedLifetime(); it.addTag("xw_sct");
                lv.addFreshEntity(it);
            }
            p.getInventory().selected = 0;
            p.getInventory().setItem(0, new ItemStack(GovItems.OMNI_KARMA_SCANNER.get()));
        }), 20));
        l.add(new Step(null, () -> mc.options.hideGui = false, 5));
        l.add(new Step("scanners_frames_ground_held", () -> {}, 3));
        l.add(new Step(null, () -> server(p -> {
            cleanup(p); p.getInventory().clearContent();
            for (int i = 0; i < 4; i++) p.serverLevel().setBlock(BlockPos.containing(HOME[0] - 3 + i * 2, HOME[1] + 1, HOME[2] + 5), Blocks.AIR.defaultBlockState(), 3);
        }), 5));
    }

    // ------------------------------------------------------------------ Gov guards

    private static GovAgentEntity guard(ServerPlayer p, double dx, double dz, boolean isGuard) {
        var g = GovEntities.GOV_AGENT.get().create(p.serverLevel());
        g.moveTo(HOME[0] + dx, HOME[1], HOME[2] + dz, 180F, 0F);
        g.setup(isGuard, 0, BlockPos.containing(HOME[0] + dx, HOME[1], HOME[2] + dz));
        g.addTag("xw_sct");
        p.serverLevel().addFreshEntity(g);
        return g;
    }

    private static void soldiers(Minecraft mc, List<Step> l) {
        // guards get scanners by themselves (tier by toughness/area, mixed colours); a Commander an Elite
        l.add(new Step(null, () -> server(p -> {
            cleanup(p); atHome(p, 0F); p.setGameMode(GameType.CREATIVE); p.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
            for (int i = 0; i < 6; i++) IDS[i] = guard(p, -5 + i * 2, 6, true).getId();
            var c = guard(p, 0, 9, true); c.makeCommander(200, 12); IDS[6] = c.getId();
            for (int i = 0; i <= 6; i++) ((GovAgentEntity) p.serverLevel().getEntity(IDS[i])).setNoAi(true);
        }), 30));
        l.add(new Step(null, () -> server(p -> {
            int with = 0; StringBuilder b = new StringBuilder();
            for (int i = 0; i <= 6; i++) {
                var g = (GovAgentEntity) p.serverLevel().getEntity(IDS[i]);
                if (g == null) continue;
                ItemStack h = g.getItemBySlot(EquipmentSlot.HEAD);
                if (h.getItem() instanceof KarmaScanner.Item k) { with++; b.append(i == 6 ? "Commander:" : "").append(k.tier.id).append('/').append(KarmaScanner.colorName(k.getColor(h))).append(' '); }
            }
            var cmd = (GovAgentEntity) p.serverLevel().getEntity(IDS[6]);
            check("guards wear scanners", with == 7 && cmd != null && cmd.getItemBySlot(EquipmentSlot.HEAD).is(GovItems.ELITE_KARMA_SCANNER.get()), with + "/7: " + b);
        }), 5));
        l.add(new Step(null, () -> { mc.options.hideGui = true; mc.options.setCameraType(CameraType.FIRST_PERSON); server(p -> { atHome(p, 0F); p.setXRot(6F); }); }, 10));
        l.add(new Step("scanners_soldiers", () -> {}, 3));
        // a guard scans a player too strong for its basic scanner: it blows apart, the guard drops the fight and runs
        l.add(new Step(null, () -> { mc.options.hideGui = false; server(p -> {
            cleanup(p); atHome(p, 0F); p.setGameMode(GameType.SURVIVAL);
            p.getAbilities().invulnerable = true; p.onUpdateAbilities();
            p.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1024); p.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(20); p.setHealth(p.getMaxHealth());
            GovFacilities.makeWanted(p, "were caught in a test");
            var g = guard(p, 0, 7, true);
            GovScanning.give(g, KarmaScanner.Tier.OMNI, -1);                  // (first an Omni: it reads them fine and attacks)
            g.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
            IDS[0] = g.getId();
            V[3] = KarmaLevel.of(p);
        }); }, 70));
        l.add(new Step("scanners_soldier_scanning", () -> server(p -> {
            var g = (GovAgentEntity) p.serverLevel().getEntity(IDS[0]);
            long read = g == null ? -1 : g.getPersistentData().getLong(GovScanning.LAST_READ);
            check("guard reads with an Omni", g != null && g.getTarget() == p && read == V[3] && g.getItemBySlot(EquipmentSlot.HEAD).is(GovItems.OMNI_KARMA_SCANNER.get()),
                    "player Karma Level " + KarmaLevel.fmt(V[3]) + ", guard read " + KarmaLevel.fmt(read) + ", target=" + (g == null ? null : g.getTarget()) + ", distance " + (g == null ? "-" : f(g.distanceTo(p))));
            if (g != null) GovScanning.give(g, KarmaScanner.Tier.BASIC, KarmaScanner.RED);   // now a basic one: 50,000 is far too little
        }), 75));
        l.add(new Step("scanners_soldier_overload", () -> server(p -> {
            var g = (GovAgentEntity) p.serverLevel().getEntity(IDS[0]);
            D[0] = g == null ? -1 : g.distanceTo(p);
            check("guard scanner overloads", g != null && g.getItemBySlot(EquipmentSlot.HEAD).isEmpty() && g.panicking() && g.getTarget() == null,
                    "player " + KarmaLevel.fmt(V[3]) + " > 50,000: helmet=" + (g == null ? "-" : g.getItemBySlot(EquipmentSlot.HEAD)) + ", panicking=" + (g != null && g.panicking())
                            + ", target=" + (g == null ? null : g.getTarget()) + ", distance " + f(D[0]));
        }), 40));
        l.add(new Step(null, () -> server(p -> { var g = p.serverLevel().getEntity(IDS[0]); D[1] = g == null ? -1 : g.distanceTo(p); }), 40));
        l.add(new Step(null, () -> { mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); server(p -> {
            var g = p.serverLevel().getEntity(IDS[0]);
            if (g != null) { double dx = g.getX() - p.getX(), dz = g.getZ() - p.getZ(); float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90); p.setYRot(yaw); p.setYHeadRot(yaw); p.setXRot(5F); }
        }); }, 4));
        l.add(new Step("scanners_soldier_flee", () -> server(p -> {
            var g = (GovAgentEntity) p.serverLevel().getEntity(IDS[0]);
            D[2] = g == null ? -1 : g.distanceTo(p);
            check("guard flees", g != null && D[1] > D[0] + 2 && D[2] > D[1] && g.getTarget() == null && !g.isAggressive() && g.panicking(),
                    "distance " + f(D[0]) + " -> " + f(D[1]) + " -> " + f(D[2]) + " over 4 s, target=" + (g == null ? null : g.getTarget()) + ", aggressive=" + (g != null && g.isAggressive()));
        }), 3));
        // worn out by hits: the same panic
        l.add(new Step(null, () -> { mc.options.setCameraType(CameraType.FIRST_PERSON); server(p -> {
            atHome(p, 0F);
            var g = guard(p, 2, 4, true);
            GovScanning.give(g, KarmaScanner.Tier.ADVANCED, KarmaScanner.GOLD);
            ItemStack h = g.getItemBySlot(EquipmentSlot.HEAD); h.setDamageValue(h.getMaxDamage() - 1);
            IDS[1] = g.getId();
        }); }, 20));
        l.add(new Step(null, () -> server(p -> {
            var g = (GovAgentEntity) p.serverLevel().getEntity(IDS[1]);
            if (g != null) { g.invulnerableTime = 0; g.hurt(p.damageSources().playerAttack(p), 4F); }
        }), 10));
        l.add(new Step(null, () -> server(p -> {
            var g = (GovAgentEntity) p.serverLevel().getEntity(IDS[1]);
            check("worn-out scanner breaks, guard panics", g != null && g.getItemBySlot(EquipmentSlot.HEAD).isEmpty() && g.panicking(),
                    "helmet=" + (g == null ? "-" : g.getItemBySlot(EquipmentSlot.HEAD)) + ", panicking=" + (g != null && g.panicking()));
            // put the player back as they were
            p.getAttribute(Attributes.MAX_HEALTH).setBaseValue(20); p.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(1); p.setHealth(20);
            p.getAbilities().invulnerable = false; p.onUpdateAbilities();
            p.getPersistentData().remove("xw_gov_wanted_until");
            cleanup(p);
        }), 5));
        l.add(new Step(null, () -> mc.options.hideGui = false, 2));
    }
}

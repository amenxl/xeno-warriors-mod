package com.xenowarriors.client.test;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.bot.PartyBotEntity;
import com.xenowarriors.bot.PartyBots;
import com.xenowarriors.character.CharacterManager;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.forms.SignatureWeapons;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * UI test for the characters' summoned show weapons (focus "sigw"): each character's summon gives the right item, put
 * away removes it, no dupes (drop, death drops, death, character switch, Season lock), and bots of those characters
 * summon theirs in a fight. Results are "XW-TEST sigw ..." lines; a still of a few characters holding theirs.
 */
public final class SigWeaponTests {
    private SigWeaponTests() {}

    public static boolean handles(String focus) { return focus != null && focus.startsWith("sigw"); }

    private static void log(String s) { XenoWarriors.LOGGER.info("XW-TEST sigw " + s); }

    private static ServerPlayer sp() {
        Minecraft mc = Minecraft.getInstance();
        var s = mc.getSingleplayerServer();
        return s == null || mc.player == null ? null : s.getPlayerList().getPlayer(mc.player.getUUID());
    }

    private static void server(java.util.function.Consumer<ServerPlayer> f) {
        var s = Minecraft.getInstance().getSingleplayerServer();
        if (s != null) s.execute(() -> { ServerPlayer p = sp(); if (p != null) try { f.accept(p); } catch (Throwable t) { log("step error " + t); XenoWarriors.LOGGER.error("XW-TEST sigw", t); } });
    }

    private static final double[] HOME = new double[3];
    private static final CharacterType[] WHO = {CharacterType.MAX, CharacterType.SAM, CharacterType.LOGAN, CharacterType.KEITA, CharacterType.ZANE, CharacterType.GARRY};

    private static void arena(ServerPlayer p) {
        var level = p.serverLevel();
        if (HOME[1] == 0) {
            BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p.blockPosition());
            HOME[0] = top.getX() + 0.5; HOME[1] = top.getY() + 1; HOME[2] = top.getZ() + 0.5;
        }
        int y0 = (int) HOME[1];
        for (int x = -10; x <= 10; x++) for (int z = -10; z <= 12; z++) {
            BlockPos b = BlockPos.containing(HOME[0] + x, y0 - 1, HOME[2] + z);
            level.setBlock(b, Blocks.GRASS_BLOCK.defaultBlockState(), 3);
            for (int y = 0; y < 20; y++) level.setBlock(b.above(1 + y), Blocks.AIR.defaultBlockState(), 3);
        }
        p.teleportTo(level, HOME[0], y0, HOME[2], 0F, 10F);
        p.setGameMode(GameType.SURVIVAL);
        p.setHealth(p.getMaxHealth());
        level.setDayTime(6000);
    }

    private static int summoned(ServerPlayer p) {
        int n = 0;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) if (SignatureWeapons.isSummoned(p.getInventory().getItem(i))) n++;
        return n;
    }

    private static int itemEntities(ServerPlayer p) {
        return p.serverLevel().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(8), e -> SignatureWeapons.isSummoned(e.getItem())).size();
    }

    private static String id(ItemStack s) { return s.isEmpty() ? "empty" : net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(s.getItem()).getPath(); }

    private static PartyBotEntity bot(ServerPlayer p, CharacterType t) {
        for (var b : PartyBots.bots(p)) if (b.character() == t) return b;
        return null;
    }

    private static PartyBotEntity spawnBot(ServerPlayer p, CharacterType t, double dx, double dz) {
        PartyBotEntity b = bot(p, t);
        if (b == null) b = PartyBots.spawn(p, t, null);
        if (b == null) { log("bot spawn " + t.getId() + " FAILED"); return null; }
        b.inventory.clearContent();
        b.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        b.moveTo(HOME[0] + dx, HOME[1], HOME[2] + dz, 180, 0);
        b.setTarget(null);
        b.setMode(PartyBotEntity.NEUTRAL);
        var hp = b.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
        if (hp != null) hp.setBaseValue(300);
        b.setHealth(b.getMaxHealth());
        return b;
    }

    private static Zombie dummy(ServerPlayer p, double dx, double dz) {
        Zombie zb = EntityType.ZOMBIE.create(p.serverLevel());
        zb.moveTo(HOME[0] + dx, HOME[1], HOME[2] + dz, 180, 0);
        zb.setNoAi(true);
        zb.setPersistenceRequired();
        zb.addTag("xw_sigw");
        zb.getPersistentData().putInt("xw_lvl", 0);
        p.serverLevel().addFreshEntity(zb);
        zb.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(400); zb.setHealth(400);
        return zb;
    }

    private static void clearDummies(ServerPlayer p) {
        p.serverLevel().getEntitiesOfClass(Zombie.class, p.getBoundingBox().inflate(40), e -> e.getTags().contains("xw_sigw")).forEach(e -> e.discard());
    }

    private static final float[] HP = {0F};

    public static void run(Minecraft mc, String focus, MikikoTests.Steps l) {
        l.add(null, () -> { mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE); mc.getToasts().clear(); mc.setScreen(null);
            server(p -> { com.xenowarriors.character.Seasons.set(p.server, 3); arena(p); }); }, 20);

        // ---- each character: summon gives its item, tap again puts it away, a drop / death / drops leave nothing behind
        for (CharacterType t : WHO) {
            l.add(null, () -> server(p -> CharacterManager.setCharacter(p, t)), 40);
            l.add(null, () -> server(p -> {
                p.getInventory().clearContent();
                p.setGameMode(GameType.SURVIVAL);
                boolean handled = SignatureWeapons.toggle(p);
                ItemStack h = p.getMainHandItem();
                log(t.getId() + " summon: handled=" + handled + " held=" + id(h) + " expected=" + id(new ItemStack(SignatureWeapons.itemOf(t)))
                        + " summoned=" + SignatureWeapons.isSummoned(h) + " unbreakable=" + h.getTag().getBoolean("Unbreakable") + " inv=" + summoned(p));
                SignatureWeapons.toggle(p);
                log(t.getId() + " put away: inv=" + summoned(p) + " hand=" + id(p.getMainHandItem()));
                SignatureWeapons.toggle(p);
                // dupe attempts: the drop key / drop everything, a death's drops, death itself
                p.drop(p.getMainHandItem().copy(), false);
                log(t.getId() + " drop: item entities=" + itemEntities(p) + " inv=" + summoned(p));
                List<ItemEntity> drops = new ArrayList<>();
                drops.add(new ItemEntity(p.serverLevel(), p.getX(), p.getY(), p.getZ(), SignatureWeapons.make(t)));
                drops.add(new ItemEntity(p.serverLevel(), p.getX(), p.getY(), p.getZ(), new ItemStack(Items.APPLE)));
                var ev = new LivingDropsEvent(p, p.damageSources().generic(), drops, 0, false);
                MinecraftForge.EVENT_BUS.post(ev);
                log(t.getId() + " death drops after filter: " + ev.getDrops().size() + " (only the apple stays)");
                SignatureWeapons.toggle(p);
                if (summoned(p) == 0) SignatureWeapons.toggle(p);
                log(t.getId() + " before death: inv=" + summoned(p));
                MinecraftForge.EVENT_BUS.post(new LivingDeathEvent(p, p.damageSources().generic()));
                log(t.getId() + " after death: inv=" + summoned(p));
                // a real item stack that was summoned and sneaked into the world is refused
                ItemEntity loose = new ItemEntity(p.serverLevel(), p.getX(), p.getY(), p.getZ(), SignatureWeapons.make(t));
                boolean added = p.serverLevel().addFreshEntity(loose);
                log(t.getId() + " loose item entity added=" + added);
            }), 10);
        }

        // ---- no weapon for the others (reported), and Season locks
        for (CharacterType t : new CharacterType[]{CharacterType.KYRIE, CharacterType.LEXY, CharacterType.TECHNICA, CharacterType.STEVIE, CharacterType.YURI, CharacterType.KILL, CharacterType.AIONI}) {
            l.add(null, () -> log("no show weapon: " + t.getId() + " has=" + SignatureWeapons.has(t)), 1);
        }
        l.add(null, () -> server(p -> CharacterManager.setCharacter(p, CharacterType.ZANE)), 40);
        l.add(null, () -> server(p -> { com.xenowarriors.character.Seasons.set(p.server, 1); p.getInventory().clearContent(); }), 10);
        l.add(null, () -> server(p -> { SignatureWeapons.toggle(p); log("season 1 locked: zane summon -> inv=" + summoned(p)); }), 10);
        l.add(null, () -> server(p -> { com.xenowarriors.character.Seasons.set(p.server, 3); SignatureWeapons.toggle(p); log("season 3 unlocked: zane summon -> held=" + id(p.getMainHandItem()));
            com.xenowarriors.character.Seasons.set(p.server, 1); }), 25);
        l.add(null, () -> server(p -> { log("season locked again: inv after tick=" + summoned(p)); com.xenowarriors.character.Seasons.set(p.server, 3); }), 5);

        // ---- character switch removes it
        l.add(null, () -> server(p -> { CharacterManager.setCharacter(p, CharacterType.MAX); p.getInventory().clearContent(); }), 40);
        l.add(null, () -> server(p -> { SignatureWeapons.toggle(p); log("switch: max has " + id(p.getMainHandItem())); CharacterManager.setCharacter(p, CharacterType.SAM); }), 40);
        l.add(null, () -> server(p -> log("switch: as sam, summoned in inventory=" + summoned(p))), 5);

        // ---- bots
        l.add(null, () -> server(p -> {
            CharacterManager.setCharacter(p, CharacterType.MIKIKO);
            p.getInventory().clearContent();
            arena(p);
            clearDummies(p);
            var b = spawnBot(p, CharacterType.SAM, -2, 4);
            if (b != null) {
                b.inventory.addItem(new ItemStack(Items.DIAMOND_SWORD));
                var z = dummy(p, -2, 7);
                b.setTarget(z);
                log("bot sam: fight starts, bag has a diamond sword, hand=" + id(b.getMainHandItem()));
            }
        }), 60);
        l.add(null, () -> server(p -> {
            var b = bot(p, CharacterType.SAM);
            if (b == null) return;
            log("bot sam (bag: diamond sword): hand=" + id(b.getMainHandItem()) + " summoned=" + SignatureWeapons.isSummoned(b.getMainHandItem()) + " bagHasSummoned=" + SignatureWeapons.out(b.inventory, CharacterType.SAM));
            var z = p.serverLevel().getEntitiesOfClass(Zombie.class, p.getBoundingBox().inflate(40), e -> e.getTags().contains("xw_sigw"));
            log("bot sam hit the dummy: hp " + (z.isEmpty() ? -1 : z.get(0).getHealth()) + "/400");
            // now a much stronger weapon in the bag
            b.setTarget(null);
            b.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            b.inventory.clearContent();
            ItemStack big = new ItemStack(Items.NETHERITE_SWORD);
            big.enchant(Enchantments.SHARPNESS, 5);
            b.inventory.addItem(big);
            if (!z.isEmpty()) b.setTarget(z.get(0));
        }), 60);
        l.add(null, () -> server(p -> {
            var b = bot(p, CharacterType.SAM);
            if (b == null) return;
            log("bot sam (bag: sharpness V netherite sword, much stronger): hand=" + id(b.getMainHandItem()) + " summoned=" + SignatureWeapons.isSummoned(b.getMainHandItem()) + " bagHasSummoned=" + SignatureWeapons.out(b.inventory, CharacterType.SAM));
            b.setTarget(null);
            b.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            b.inventory.clearContent();
            clearDummies(p);
        }), 5);
        // each other bot character with a damaging weapon: empty bag, a fight, its own weapon in hand
        for (CharacterType t : new CharacterType[]{CharacterType.MAX, CharacterType.LOGAN, CharacterType.KEITA}) {
            l.add(null, () -> server(p -> {
                var b = spawnBot(p, t, 2, 4);
                if (b != null) { var z = dummy(p, 2, 7); b.setTarget(z); }
            }), 50);
            l.add(null, () -> server(p -> {
                var b = bot(p, t);
                if (b == null) return;
                log("bot " + t.getId() + " in a fight: hand=" + id(b.getMainHandItem()) + " summoned=" + SignatureWeapons.isSummoned(b.getMainHandItem()) + " bag=" + b.inventory.isEmpty());
                b.setTarget(null);
                clearDummies(p);
            }), 5);
        }
        // idle: the summoned weapon goes away again (never into the bag)
        l.add(null, () -> {}, 20 * 18);
        l.add(null, () -> server(p -> {
            var b = bot(p, CharacterType.MAX);
            if (b != null) log("bot max after 18s idle: hand=" + id(b.getMainHandItem()) + " bagHasSummoned=" + SignatureWeapons.out(b.inventory, CharacterType.MAX));
        }), 5);

        // ---- the still: a few characters holding theirs (the player as Zane, three bots)
        l.add(null, () -> server(p -> {
            CharacterManager.setCharacter(p, CharacterType.ZANE);
            arena(p);
            for (var b : new ArrayList<>(PartyBots.bots(p))) b.discard();
        }), 40);
        l.add(null, () -> server(p -> {
            p.getInventory().clearContent();
            SignatureWeapons.toggle(p);
            p.teleportTo(p.serverLevel(), HOME[0] + 1.5, HOME[1], HOME[2], 0F, 0F);
            double[][] at = {{-1, 0}, {-3, 0}, {-5, 0}};   // (bots stand left of the player, spread out)
            int i = 0;
            for (CharacterType t : new CharacterType[]{CharacterType.LOGAN, CharacterType.MAX, CharacterType.SAM}) {
                var b = spawnBot(p, t, at[i][0] + 2.5, 0.0);
                if (b != null) {
                    b.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, SignatureWeapons.make(t));
                    b.setNoAi(true);
                    b.setYRot(0); b.yBodyRot = 0; b.yHeadRot = 0;
                }
                i++;
            }
        }), 30);
        int[] cam = {-1};
        l.add(null, () -> server(p -> {
            var a = EntityType.ARMOR_STAND.create(p.serverLevel());
            a.moveTo(HOME[0] - 0.5, HOME[1] + 1.2, HOME[2] + 5.0, 180, 4);
            a.setNoGravity(true); a.setInvisible(true); a.setYHeadRot(180); a.addTag("xw_sigw_cam");
            p.serverLevel().addFreshEntity(a); cam[0] = a.getId();
        }), 12);
        l.add(null, () -> {
            if (mc.level.getEntity(cam[0]) instanceof net.minecraft.world.entity.decoration.ArmorStand a) {
                a.setYRot(180); a.yRotO = 180; a.setXRot(4); a.xRotO = 4; a.setYHeadRot(180); a.yHeadRotO = 180; mc.setCameraEntity(a);
            }
            mc.options.setCameraType(CameraType.FIRST_PERSON); mc.options.hideGui = true;
        }, 10);
        l.add("sigw_characters", () -> {}, 5);
        l.add(null, () -> { mc.setCameraEntity(mc.player); mc.options.hideGui = false;
            server(p -> { for (var b : new ArrayList<>(PartyBots.bots(p))) b.discard(); log("done"); }); }, 5);
    }
}

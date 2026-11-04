package com.xenowarriors.client.test;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.bot.PartyBotEntity;
import com.xenowarriors.bot.PartyBots;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.client.UiTest.Step;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * UI tests for the bot teammates' looks, items and crafting (focus "botgear", or one part: botgear_anim, botgear_keep,
 * botgear_craft, botgear_whistle). Results are logged as "XW-TEST botgear ...".
 */
public final class BotGearTests {
    private BotGearTests() {}

    static ServerPlayer sp() {
        Minecraft mc = Minecraft.getInstance();
        var s = mc.getSingleplayerServer();
        return s == null || mc.player == null ? null : s.getPlayerList().getPlayer(mc.player.getUUID());
    }

    static void server(java.util.function.Consumer<ServerPlayer> f) {
        var s = Minecraft.getInstance().getSingleplayerServer();
        if (s != null) s.execute(() -> { ServerPlayer p = sp(); if (p != null) f.accept(p); });
    }

    static void log(String s) { XenoWarriors.LOGGER.info("XW-TEST botgear " + s); }

    static void character(CharacterType t) { server(p -> com.xenowarriors.character.CharacterManager.setCharacter(p, t)); }

    static void clearBots(ServerPlayer p) {
        for (PartyBotEntity b : p.serverLevel().getEntitiesOfClass(PartyBotEntity.class, p.getBoundingBox().inflate(48))) { PartyBots.forgetFake(b.getUUID()); b.discard(); }
    }

    static PartyBotEntity bot(ServerPlayer p, CharacterType t) {
        for (PartyBotEntity b : PartyBots.bots(p)) if (b.character() == t) return b;
        return null;
    }

    static String id(ItemStack s) { return s.isEmpty() ? "empty" : net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(s.getItem()).getPath(); }

    static int count(net.minecraft.world.Container c) {
        int n = 0;
        for (int i = 0; i < c.getContainerSize(); i++) n += c.getItem(i).getCount();
        return n;
    }

    public static void build(Minecraft mc, List<Step> l, String focus) {
        boolean all = focus.equals("botgear");
        l.add(new Step(null, () -> { mc.getToasts().clear(); mc.setScreen(null); server(p -> {
            p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            p.getAbilities().mayfly = true; p.onUpdateAbilities();
            p.serverLevel().setDayTime(6000);
        }); }, 10));
        if (all || focus.equals("botgear_whistle")) whistle(mc, l);
        if (all || focus.equals("botgear_keep")) keep(mc, l);
        if (all || focus.equals("botgear_craft")) craft(mc, l);
        if (all || focus.equals("botgear_anim")) anim(mc, l);
        l.add(new Step(null, () -> server(BotGearTests::clearBots), 5));
    }

    // ---------------------------------------------------------------- 4. the Pet Whistle recipe

    private static void whistle(Minecraft mc, List<Step> l) {
        l.add(new Step(null, () -> server(p -> {
            for (var id : com.xenowarriors.item.RecipeUnlocks.KNOWN) {
                var r = p.server.getRecipeManager().byKey(id);
                log("whistle recipe " + id + " present=" + r.isPresent() + " result=" + r.map(x -> id(x.getResultItem(p.server.registryAccess()))).orElse("-")
                        + " inRecipeBook=" + p.getRecipeBook().contains(id));
            }
            // and the grid really makes it
            var grid = new net.minecraft.world.inventory.TransientCraftingContainer(new net.minecraft.world.inventory.AbstractContainerMenu(null, -1) {
                @Override public ItemStack quickMoveStack(net.minecraft.world.entity.player.Player pl, int i) { return ItemStack.EMPTY; }
                @Override public boolean stillValid(net.minecraft.world.entity.player.Player pl) { return true; }
            }, 3, 3);
            grid.setItem(1, new ItemStack(Items.IRON_NUGGET)); grid.setItem(3, new ItemStack(Items.IRON_NUGGET)); grid.setItem(5, new ItemStack(Items.IRON_NUGGET));
            grid.setItem(4, new ItemStack(Items.FEATHER)); grid.setItem(7, new ItemStack(Items.STRING));
            var m = p.server.getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING, grid, p.level());
            log("whistle grid (nuggets+feather+string) -> " + m.map(x -> id(x.assemble(grid, p.server.registryAccess()))).orElse("nothing"));
        }), 10));
        l.add(new Step(null, () -> { mc.options.hideGui = false; mc.setScreen(new net.minecraft.client.gui.screens.inventory.CraftingScreen(
                new net.minecraft.world.inventory.CraftingMenu(0, mc.player.getInventory()), mc.player.getInventory(), net.minecraft.network.chat.Component.literal("Crafting"))); }, 20));
        l.add(new Step(null, () -> { if (mc.screen instanceof net.minecraft.client.gui.screens.inventory.CraftingScreen cs) {
            var rb = cs.getRecipeBookComponent(); if (!rb.isVisible()) rb.toggleVisibility(); } }, 10));
        l.add(new Step("botgear_whistle_book", () -> {}, 5));
        l.add(new Step(null, () -> mc.setScreen(null), 5));
    }

    // ---------------------------------------------------------------- 2. keep inventory

    private static String things(PartyBotEntity b) {
        return "bag=" + count(b.inventory) + " (iron " + b.inventory.countItem(Items.IRON_INGOT) + ", bread " + b.inventory.countItem(Items.BREAD)
                + ", diamond_sword " + b.inventory.countItem(Items.DIAMOND_SWORD) + ") hand=" + id(b.getMainHandItem()) + " head=" + id(b.getItemBySlot(EquipmentSlot.HEAD))
                + " chest=" + id(b.getItemBySlot(EquipmentSlot.CHEST)) + " legs=" + id(b.getItemBySlot(EquipmentSlot.LEGS)) + " feet=" + id(b.getItemBySlot(EquipmentSlot.FEET))
                + " offhand=" + id(b.getItemBySlot(EquipmentSlot.OFFHAND));
    }

    private static int saved(ServerPlayer p, String key) {
        var t = com.xenowarriors.character.CharacterInventories.saved(p, key).getList("inv", 10);
        int n = 0;
        for (int i = 0; i < t.size(); i++) n += t.getCompound(i).getByte("Count");
        return n;
    }

    private static void keep(Minecraft mc, List<Step> l) {
        l.add(new Step(null, () -> { character(CharacterType.MIKIKO); server(BotGearTests::clearBots); }, 20));
        for (String how : new String[]{"bleedout", "void"}) {
            l.add(new Step(null, () -> server(p -> {
                p.getPersistentData().remove("xw_bot_rejoin_rhalix");
                PartyBotEntity b = PartyBots.spawn(p, CharacterType.RHALIX, null);
                if (b == null) { log("keep spawn FAILED"); return; }
                b.inventory.clearContent();
                b.inventory.addItem(new ItemStack(Items.IRON_INGOT, 5));
                b.inventory.addItem(new ItemStack(Items.BREAD, 3));
                b.inventory.addItem(new ItemStack(Items.TORCH, 20));
                b.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_SWORD));
                b.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
                b.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
                b.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
                b.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
                b.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
                log("keep " + how + " before: " + things(b));
            }), 10));
            if (how.equals("bleedout")) {
                l.add(new Step(null, () -> server(p -> {
                    PartyBotEntity b = bot(p, CharacterType.RHALIX);
                    if (b == null) return;
                    b.hurt(p.damageSources().generic(), 5000F);
                    log("keep bleedout downed=" + b.downed() + " " + things(b));
                }), 20));
                l.add(new Step(null, () -> server(p -> {
                    PartyBotEntity b = bot(p, CharacterType.RHALIX);
                    if (b != null) PartyBots.bleedOutForTest(b);
                }), 10));
            } else {
                l.add(new Step(null, () -> server(p -> {
                    PartyBotEntity b = bot(p, CharacterType.RHALIX);
                    if (b != null) b.hurt(p.damageSources().fellOutOfWorld(), 5000F);
                }), 10));
            }
            l.add(new Step(null, () -> server(p -> {
                StringBuilder dropped = new StringBuilder();
                for (var it : p.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, p.getBoundingBox().inflate(16))) dropped.append(id(it.getItem())).append('x').append(it.getItem().getCount()).append(' ');
                log("keep " + how + " gone: bot there=" + (bot(p, CharacterType.RHALIX) != null) + " items on the ground=[" + dropped.toString().trim() + "] saved for rhalix=" + saved(p, "rhalix")
                        + " in your bag=" + count(p.getInventory()));
                p.getPersistentData().remove("xw_bot_rejoin_rhalix");
                PartyBots.add(p, new String[]{"rhalix"});
            }), 10));
            l.add(new Step(null, () -> server(p -> {
                PartyBotEntity b = bot(p, CharacterType.RHALIX);
                log("keep " + how + " back: " + (b == null ? "no bot" : things(b)) + " saved left=" + saved(p, "rhalix"));
                clearBots(p);
                com.xenowarriors.character.CharacterInventories.checkOut(p, "rhalix");
            }), 10));
        }
    }

    // ---------------------------------------------------------------- 3. crafting and repairs

    private static void craft(Minecraft mc, List<Step> l) {
        l.add(new Step(null, () -> { character(CharacterType.MIKIKO); server(p -> { clearBots(p); p.getInventory().clearContent(); p.setYRot(0); p.setXRot(10); }); }, 20));
        l.add(new Step(null, () -> server(p -> {
            p.getPersistentData().remove("xw_bot_rejoin_rhalix");
            PartyBotEntity b = PartyBots.spawn(p, CharacterType.RHALIX, null);
            if (b == null) { log("craft spawn FAILED"); return; }
            b.moveTo(p.getX() + 1.5, p.getY(), p.getZ() + 3, 180, 0);
            b.inventory.clearContent();
            for (EquipmentSlot es : EquipmentSlot.values()) b.setItemSlot(es, ItemStack.EMPTY);
            b.inventory.addItem(new ItemStack(Items.IRON_INGOT, 8));
            b.inventory.addItem(new ItemStack(Items.CRAFTING_TABLE));
            com.xenowarriors.bot.BotCrafting.clearQuiet(b);
            var ideas = com.xenowarriors.bot.BotCrafting.ideas(b);
            StringBuilder sb = new StringBuilder();
            for (var i : ideas) sb.append(id(i.result)).append(i.missing == 0 ? "" : "(-1 " + i.missingWhat.getString() + ")").append(' ');
            log("craft ideas with 8 iron + table: " + sb.toString().trim());
            var pick = com.xenowarriors.bot.BotCrafting.suggest(b, p, true);
            log("craft suggested: " + (pick == null ? "nothing" : pick.kind + " " + id(pick.result) + " missing=" + pick.missing));
        }), 15));
        l.add(new Step(null, () -> { mc.setScreen(null); mc.options.hideGui = false; mc.options.setCameraType(CameraType.FIRST_PERSON); mc.player.setYRot(0); mc.player.setXRot(10); }, 5));
        l.add(new Step(null, () -> mc.setScreen(new net.minecraft.client.gui.screens.ChatScreen("")), 10));
        l.add(new Step("botgear_craft_offer", () -> {}, 3));
        l.add(new Step(null, () -> mc.setScreen(null), 3));
        l.add(new Step("botgear_craft_popup", () -> {}, 3));
        l.add(new Step(null, () -> server(p -> {                              // [Craft] (as the button runs it)
            PartyBotEntity b = bot(p, CharacterType.RHALIX);
            if (b == null) return;
            p.server.getCommands().performPrefixedCommand(p.createCommandSourceStack(), "xenobot craft " + b.getId() + " " + com.xenowarriors.bot.BotCrafting.offerToken(b));
        }), 10));
        l.add(new Step(null, () -> server(p -> { PartyBotEntity b = bot(p, CharacterType.RHALIX); if (b != null) log("craft after confirm: " + things(b)); }), 3));
        l.add(new Step("botgear_craft_worn", () -> mc.options.setCameraType(CameraType.FIRST_PERSON), 5));
        // one short: 3 iron (boots need 4) - and the owner has one to give
        l.add(new Step(null, () -> server(p -> {
            PartyBotEntity b = bot(p, CharacterType.RHALIX);
            if (b == null) return;
            b.inventory.clearContent();
            b.inventory.addItem(new ItemStack(Items.IRON_INGOT, 3));
            b.inventory.addItem(new ItemStack(Items.CRAFTING_TABLE));
            b.inventory.addItem(new ItemStack(Items.DIAMOND_SWORD));            // (good weapons and tools already: only armour is worth it)
            b.inventory.addItem(new ItemStack(Items.DIAMOND_PICKAXE));
            b.inventory.addItem(new ItemStack(Items.DIAMOND_AXE));
            b.inventory.addItem(new ItemStack(Items.DIAMOND_HELMET));
            p.getInventory().add(new ItemStack(Items.IRON_INGOT, 1));
            var pick = com.xenowarriors.bot.BotCrafting.suggest(b, p, true);
            log("craft one-short suggested: " + (pick == null ? "nothing" : pick.kind + " " + id(pick.result) + " missing=" + pick.missing + " short of " + (pick.missingWhat == null ? "-" : pick.missingWhat.getString())));
        }), 10));
        l.add(new Step(null, () -> mc.setScreen(new net.minecraft.client.gui.screens.ChatScreen("")), 10));
        l.add(new Step("botgear_craft_oneshort", () -> {}, 3));
        l.add(new Step(null, () -> mc.setScreen(null), 3));
        l.add(new Step("botgear_craft_oneshort_popup", () -> {}, 3));
        l.add(new Step(null, () -> server(p -> {                              // [Give 1 & craft]
            PartyBotEntity b = bot(p, CharacterType.RHALIX);
            if (b == null) return;
            p.server.getCommands().performPrefixedCommand(p.createCommandSourceStack(), "xenobot give " + b.getId() + " " + com.xenowarriors.bot.BotCrafting.offerToken(b));
        }), 10));
        l.add(new Step(null, () -> server(p -> { PartyBotEntity b = bot(p, CharacterType.RHALIX); if (b != null) log("craft after give: " + things(b) + " your iron=" + p.getInventory().countItem(Items.IRON_INGOT)); }), 3));
        // the bot's own periodic look (no forcing): 5 iron -> helmet; then "No thanks" keeps it quiet
        l.add(new Step(null, () -> server(p -> {
            PartyBotEntity b = bot(p, CharacterType.RHALIX);
            if (b == null) return;
            b.inventory.clearContent();
            b.inventory.addItem(new ItemStack(Items.IRON_INGOT, 5));
            b.inventory.addItem(new ItemStack(Items.CRAFTING_TABLE));
            b.inventory.addItem(new ItemStack(Items.DIAMOND_SWORD));
            b.inventory.addItem(new ItemStack(Items.DIAMOND_PICKAXE));
            b.inventory.addItem(new ItemStack(Items.DIAMOND_AXE));
            b.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
            com.xenowarriors.bot.BotCrafting.forget(b.getUUID());
            b.setLastHurtByMob(null);
            log("craft waiting for the periodic look (tick " + b.tickCount + ")");
        }), 260));
        l.add(new Step(null, () -> server(p -> {
            PartyBotEntity b = bot(p, CharacterType.RHALIX);
            if (b == null) return;
            int tok = com.xenowarriors.bot.BotCrafting.offerToken(b);
            log("craft periodic offer token=" + tok);
            if (tok != 0) p.server.getCommands().performPrefixedCommand(p.createCommandSourceStack(), "xenobot nocraft " + b.getId() + " " + tok);
            var again = com.xenowarriors.bot.BotCrafting.suggest(b, p, false);
            log("craft after no-thanks, asks again: " + (again == null ? "nothing (quiet)" : id(again.result)) + " helmet still " + id(b.getItemBySlot(EquipmentSlot.HEAD)));
        }), 10));
        // repairs: a worn iron sword + 2 iron at an anvil; and two worn stone pickaxes combined
        l.add(new Step(null, () -> server(p -> {
            PartyBotEntity b = bot(p, CharacterType.RHALIX);
            if (b == null) return;
            com.xenowarriors.bot.BotCrafting.clearQuiet(b);
            b.inventory.clearContent();
            ItemStack sword = new ItemStack(Items.IRON_SWORD); sword.setDamageValue(200);
            b.inventory.addItem(sword);
            b.inventory.addItem(new ItemStack(Items.IRON_INGOT, 2));
            p.serverLevel().setBlock(b.blockPosition().offset(2, 0, 0), net.minecraft.world.level.block.Blocks.ANVIL.defaultBlockState(), 3);
            var pick = com.xenowarriors.bot.BotCrafting.suggest(b, p, true);
            log("craft repair suggested: " + (pick == null ? "nothing" : pick.kind + " " + id(pick.result) + " missing=" + pick.missing) + " sword damage before=" + sword.getDamageValue() + "/" + sword.getMaxDamage());
            p.server.getCommands().performPrefixedCommand(p.createCommandSourceStack(), "xenobot craft " + b.getId() + " " + com.xenowarriors.bot.BotCrafting.offerToken(b));
            int dmg = -1;
            for (int i = 0; i < b.inventory.getContainerSize(); i++) if (b.inventory.getItem(i).is(Items.IRON_SWORD)) dmg = b.inventory.getItem(i).getDamageValue();
            log("craft repair done: sword damage after=" + dmg + " iron left=" + b.inventory.countItem(Items.IRON_INGOT));
            b.inventory.clearContent();
            ItemStack a = new ItemStack(Items.STONE_PICKAXE); a.setDamageValue(100);
            ItemStack c = new ItemStack(Items.STONE_PICKAXE); c.setDamageValue(90);
            b.inventory.addItem(a); b.inventory.addItem(c);
            var pick2 = com.xenowarriors.bot.BotCrafting.suggest(b, p, true);
            log("craft combine suggested: " + (pick2 == null ? "nothing" : pick2.kind + " " + id(pick2.result)));
            p.server.getCommands().performPrefixedCommand(p.createCommandSourceStack(), "xenobot craft " + b.getId() + " " + com.xenowarriors.bot.BotCrafting.offerToken(b));
            int n = 0, d = -1;
            for (int i = 0; i < b.inventory.getContainerSize(); i++) if (b.inventory.getItem(i).is(Items.STONE_PICKAXE)) { n++; d = b.inventory.getItem(i).getDamageValue(); }
            log("craft combine done: pickaxes=" + n + " damage=" + d + "/" + Items.STONE_PICKAXE.getMaxDamage(ItemStack.EMPTY));
        }), 10));
        l.add(new Step(null, () -> mc.setScreen(new net.minecraft.client.gui.screens.ChatScreen("")), 10));
        l.add(new Step("botgear_craft_chat", () -> {}, 3));
        l.add(new Step(null, () -> { mc.setScreen(null); server(BotGearTests::clearBots); }, 5));
    }

    // ---------------------------------------------------------------- 1. bots animate like players of that character

    private static final CharacterType[] ANIM = {CharacterType.GARRY, CharacterType.KEITA, CharacterType.MIKIKO, CharacterType.RHALIX,
            CharacterType.YURI, CharacterType.KAERU};

    private static void anim(Minecraft mc, List<Step> l) {
        final double[] at = new double[3];
        l.add(new Step(null, () -> server(p -> { at[0] = p.getX(); at[1] = p.getY(); at[2] = p.getZ(); }), 3));
        for (CharacterType t : ANIM) {
            String n = t.getId();
            l.add(new Step(null, () -> { character(t); server(p -> {
                clearBots(p);
                p.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                p.getAbilities().flying = false; p.onUpdateAbilities();
                p.teleportTo(at[0], at[1], at[2]); p.setYRot(0); p.setXRot(10); p.setYHeadRot(0); p.yBodyRot = 0;
                p.setHealth(p.getMaxHealth());
            }); }, 20));
            l.add(new Step(null, () -> server(p -> {
                PartyBotEntity b = PartyBots.spawn(p, t, null);
                if (b == null) { log("anim " + n + " spawn FAILED"); return; }
                b.setNoAi(true);
                b.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                b.moveTo(at[0] - 1.5, at[1], at[2], 0, 0);
                b.setYHeadRot(0); b.yBodyRot = 0; b.yBodyRotO = 0; b.yHeadRotO = 0;
                log("anim " + n + " bot " + b.getId() + " next to player");
            }), 10));
            l.add(new Step(null, () -> { mc.setScreen(null); mc.options.hideGui = true; mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                mc.player.setYRot(0); mc.player.setXRot(10); mc.player.yBodyRot = 0; mc.player.setYHeadRot(0); }, 200));
            l.add(new Step(null, () -> {                                     // (client side: what the bot's player body reports)
                for (var e : mc.level.entitiesForRendering()) if (e instanceof PartyBotEntity b && b.character() == t) {
                    var a = com.xenowarriors.client.bot.BotAvatars.existing(b);
                    if (a == null) { log("anim " + n + " client: no body"); continue; }
                    log("anim " + n + " client: body id=" + a.getId() + "/" + b.getId() + " char=" + com.xenowarriors.client.ClientCharacterData.get(a).getId()
                            + " onGround=" + a.onGround() + " (bot " + b.onGround() + ") flying=" + com.xenowarriors.client.FlightTracker.isFlying(a)
                            + " busy=" + com.xenowarriors.client.Busy.busy(a) + " tick=" + a.tickCount + " inPlayers=" + com.xenowarriors.client.bot.BotAvatars.players(mc.level).contains(a));
                }
            }, 1));
            for (int f = 0; f < 3; f++) l.add(new Step("botgear_anim_" + n + "_idle" + f, () -> {}, 12));
            // a skill: the same cast pose on both
            int cast = t == CharacterType.RHALIX ? com.xenowarriors.network.SkillAnimPacket.KISHI : t == CharacterType.MIKIKO ? com.xenowarriors.network.SkillAnimPacket.KITSUNE
                    : t == CharacterType.KAERU ? com.xenowarriors.network.SkillAnimPacket.KICK : com.xenowarriors.network.SkillAnimPacket.CAST;
            l.add(new Step(null, () -> server(p -> {
                com.xenowarriors.network.ModNetwork.sendToTrackingAndSelf(new com.xenowarriors.network.SkillAnimPacket(p.getId(), cast, false), p);
                PartyBotEntity b = bot(p, t);
                if (b != null) com.xenowarriors.network.ModNetwork.sendToTrackingEntityAndSelf(new com.xenowarriors.network.SkillAnimPacket(b.getId(), cast, false), b);
            }), 7));
            l.add(new Step("botgear_anim_" + n + "_cast", () -> {}, 20));
            // hurt: the hurt face
            l.add(new Step(null, () -> server(p -> {
                p.hurt(p.damageSources().generic(), 1F);
                PartyBotEntity b = bot(p, t);
                if (b != null) b.hurt(p.damageSources().generic(), 1F);
            }), 2));
            l.add(new Step("botgear_anim_" + n + "_hurt", () -> {}, 20));
            if (t == CharacterType.RHALIX) {                                   // up in the air: wings out
                l.add(new Step(null, () -> server(p -> {
                    p.getAbilities().flying = true; p.onUpdateAbilities();
                    p.teleportTo(at[0], at[1] + 2.5, at[2]);
                    PartyBotEntity b = bot(p, t);
                    if (b != null) { b.setNoGravity(true); b.moveTo(at[0] - 1.5, at[1] + 2.5, at[2], 0, 0); }
                }), 40));
                l.add(new Step("botgear_anim_rhalix_air", () -> {}, 10));
                l.add(new Step(null, () -> server(p -> { p.getAbilities().flying = false; p.onUpdateAbilities(); p.teleportTo(at[0], at[1], at[2]); }), 10));
            }
            // walking: you step away, the bot follows
            l.add(new Step(null, () -> server(p -> {
                PartyBotEntity b = bot(p, t);
                if (b == null) return;
                b.setNoGravity(false);
                b.moveTo(at[0] - 1.5, at[1], at[2], 0, 0);
                b.setNoAi(false);
                p.teleportTo(at[0] + 10, at[1], at[2]);
            }), 14));
            l.add(new Step("botgear_anim_" + n + "_walk", () -> {}, 8));
            l.add(new Step("botgear_anim_" + n + "_walk2", () -> {}, 5));
        }
        l.add(new Step(null, () -> mc.options.setCameraType(CameraType.FIRST_PERSON), 5));
    }
}

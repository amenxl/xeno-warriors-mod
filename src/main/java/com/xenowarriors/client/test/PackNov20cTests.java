package com.xenowarriors.client.test;

import com.xenowarriors.character.CharacterType;
import com.xenowarriors.client.UiTest.Step;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

/**
 * (focus "packnov20ctacz": TaCZ is loaded) Bots fit gun attachments from their bag, make a Gun Smith Table from their
 * bag and craft an attachment at it; townsfolk's pets can be bought; NPCs do parkour (vault, climb).
 */
final class PackNov20cTests {
    private PackNov20cTests() {}

    private static ItemStack attachment(String id) {
        var it = ForgeRegistries.ITEMS.getValue(new ResourceLocation("tacz", "attachment"));
        if (it == null || it == Items.AIR) return ItemStack.EMPTY;
        ItemStack a = new ItemStack(it);
        a.getOrCreateTag().putString("AttachmentId", id);
        return a;
    }

    static void build(Minecraft mc, List<Step> l) {
        l.add(new Step(null, () -> { mc.options.hideGui = true; mc.options.setCameraType(CameraType.FIRST_PERSON); PackTests.server(p -> { PackTests.arena(p); PackTests.clear(p); }); }, 30));
        // bots and gun attachments
        com.xenowarriors.bot.PartyBotEntity[] bot = new com.xenowarriors.bot.PartyBotEntity[1];
        l.add(new Step(null, () -> PackTests.server(p -> {
            p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            bot[0] = com.xenowarriors.bot.PartyBots.spawn(p, CharacterType.TECHNICA, null);
            if (bot[0] == null) return;
            bot[0].moveTo(p.getX() + 2, p.getY(), p.getZ() + 3, 180F, 0F);
            bot[0].setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, com.xenowarriors.compat.TaczGuns.gun("tacz:m4a1", 30));
            for (int i = 0; i < bot[0].inventory.getContainerSize(); i++) bot[0].inventory.setItem(i, ItemStack.EMPTY);
            bot[0].inventory.addItem(attachment("tacz:grip_magpul_afg"));
        }), 30));
        int[] before = new int[3];
        l.add(new Step(null, () -> PackTests.server(p -> {
            before[0] = com.xenowarriors.bot.BotGunsmith.fitted;
            try { com.xenowarriors.bot.BotGunsmith.testRun(bot[0]); } catch (Exception x) { PackTests.log("gunsmith: " + x); }
            PackTests.check("gunsmith: TaCZ is here", com.xenowarriors.compat.TaczGuns.available(), "");
            PackTests.check("gunsmith: the bot fits an attachment from its bag", com.xenowarriors.bot.BotGunsmith.fitted > before[0], "fitted " + (com.xenowarriors.bot.BotGunsmith.fitted - before[0]));
        }), 10));
        // the table: none near, so it makes one from its bag and crafts a sight at it
        l.add(new Step(null, () -> PackTests.server(p -> {
            before[1] = com.xenowarriors.bot.BotGunsmith.tables; before[2] = com.xenowarriors.bot.BotGunsmith.made;
            var inv = bot[0].inventory;
            inv.addItem(new ItemStack(Items.OAK_LOG, 3)); inv.addItem(new ItemStack(Items.IRON_INGOT, 12)); inv.addItem(new ItemStack(Items.IRON_BLOCK)); inv.addItem(new ItemStack(Items.REDSTONE, 4));
            try { com.xenowarriors.bot.BotGunsmith.testRun(bot[0]); } catch (Exception x) { PackTests.log("gunsmith: " + x); }
        }), 20));
        l.add(new Step("gunsmith_table", () -> PackTests.server(p -> {
            PackTests.check("gunsmith: the bot makes and sets down a Gun Smith Table", com.xenowarriors.bot.BotGunsmith.tables > before[1], "tables " + (com.xenowarriors.bot.BotGunsmith.tables - before[1]));
            PackTests.check("gunsmith: the bot crafts at the table", com.xenowarriors.bot.BotGunsmith.made > before[2], "made " + (com.xenowarriors.bot.BotGunsmith.made - before[2]));
        }), 5));
        // pets: a person with a wolf; buying it
        Object[] h = new Object[2];
        l.add(new Step(null, () -> PackTests.server(p -> {
            PackTests.clear(p);
            var hn = com.xenowarriors.npc.ModNpcs.HUMAN.get().create(p.serverLevel());
            hn.moveTo(p.getX() + 1, p.getY(), p.getZ() + 3, 180F, 0F);
            hn.addTag("xw_pack");
            p.serverLevel().addFreshEntity(hn);
            h[0] = hn;
            h[1] = com.xenowarriors.npc.HumanPets.give(hn, true);
            com.xenowarriors.economy.Money.set(p, 1000);
        }), 30));
        l.add(new Step("pet_owner", () -> PackTests.server(p -> {
            boolean ok = com.xenowarriors.npc.HumanPets.buy(p, (com.xenowarriors.npc.HumanNpcEntity) h[0]);
            var w = (net.minecraft.world.entity.TamableAnimal) h[1];
            PackTests.check("pets: buying a person's wolf tames it to you", ok && w != null && w.isTame() && p.getUUID().equals(w.getOwnerUUID())
                    && com.xenowarriors.economy.Money.get(p) == 1000 - com.xenowarriors.npc.HumanPets.WOLF_PRICE, "money " + com.xenowarriors.economy.Money.get(p));
            if (w != null) w.discard();
        }), 10));
        // NPC parkour: a fighter vaults a one-block wall and climbs a two-block one to get at its target
        int[] pk = new int[2];
        l.add(new Step(null, () -> PackTests.server(p -> {
            PackTests.clear(p);
            com.xenowarriors.bot.BotParkour.force = true;
            var lv = p.serverLevel();
            BlockPos base = BlockPos.containing(p.getX(), p.getY(), p.getZ());
            for (int x = -3; x <= 3; x++) lv.setBlock(base.offset(x, 0, 5), Blocks.STONE.defaultBlockState(), 3);              // (a one-block wall)
            for (int x = -3; x <= 3; x++) for (int y = 0; y < 2; y++) lv.setBlock(base.offset(x, y, 11), Blocks.STONE.defaultBlockState(), 3);   // (a two-block one)
            for (int x = -3; x <= 3; x++) for (int z = 12; z <= 14; z++) for (int y = 0; y < 2; y++) lv.setBlock(base.offset(x, y, z), Blocks.STONE.defaultBlockState(), 3);
            var target = PackTests.husk(p, 0, 13);
            target.teleportTo(target.getX(), p.getY() + 2, target.getZ());
            var hn = com.xenowarriors.npc.ModNpcs.HUMAN.get().create(lv);
            try { hn.finalizeSpawn(lv, lv.getCurrentDifficultyAt(base), net.minecraft.world.entity.MobSpawnType.EVENT, null, null); } catch (Throwable ignored) {}
            hn.moveTo(p.getX(), p.getY(), p.getZ() + 1.5, 0F, 0F);
            hn.addTag("xw_pack");
            lv.addFreshEntity(hn);
            hn.setTarget((net.minecraft.world.entity.LivingEntity) target);
            h[0] = hn;
            pk[0] = com.xenowarriors.bot.NpcParkour.STARTED[com.xenowarriors.bot.BotParkour.VAULT_L] + com.xenowarriors.bot.NpcParkour.STARTED[com.xenowarriors.bot.BotParkour.VAULT_R];
            pk[1] = com.xenowarriors.bot.NpcParkour.STARTED[com.xenowarriors.bot.BotParkour.CLING] + com.xenowarriors.bot.NpcParkour.STARTED[com.xenowarriors.bot.BotParkour.WALLJUMP_L] + com.xenowarriors.bot.NpcParkour.STARTED[com.xenowarriors.bot.BotParkour.WALLJUMP_R];
            mc.player.setYRot(0); mc.player.setXRot(15);
        }), 5));
        for (int i = 0; i < 12; i++) {
            int k = i;
            l.add(new Step(k == 2 ? "npc_vault" : k == 7 ? "npc_climb" : null, () -> PackTests.server(p -> {
                var hn = (com.xenowarriors.npc.HumanNpcEntity) h[0];
                var t = p.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.monster.Husk.class, p.getBoundingBox().inflate(30), e -> e.getTags().contains("xw_pack"));
                if (!t.isEmpty() && hn.isAlive()) { hn.setTarget(t.get(0)); hn.getNavigation().moveTo(t.get(0), 1.2); }
            }), 10));
        }
        l.add(new Step(null, () -> PackTests.server(p -> {
            int v = com.xenowarriors.bot.NpcParkour.STARTED[com.xenowarriors.bot.BotParkour.VAULT_L] + com.xenowarriors.bot.NpcParkour.STARTED[com.xenowarriors.bot.BotParkour.VAULT_R] - pk[0];
            int c = com.xenowarriors.bot.NpcParkour.STARTED[com.xenowarriors.bot.BotParkour.CLING] + com.xenowarriors.bot.NpcParkour.STARTED[com.xenowarriors.bot.BotParkour.WALLJUMP_L] + com.xenowarriors.bot.NpcParkour.STARTED[com.xenowarriors.bot.BotParkour.WALLJUMP_R] - pk[1];
            PackTests.check("npc parkour: a person vaults or climbs to get at their target", v + c > 0, "vaults " + v + " climbs " + c);
            com.xenowarriors.bot.BotParkour.force = false;
            PackTests.clear(p);
        }), 5));
        // bots with Building 10+ lay blocks across water and pick them back up
        int[] wb = new int[2];
        Object[] wbot = new Object[1];
        l.add(new Step(null, () -> PackTests.server(p -> {
            PackTests.arena(p); PackTests.clear(p);
            p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
            var lv = p.serverLevel();
            BlockPos base = BlockPos.containing(p.getX(), p.getY(), p.getZ());
            for (int x = -2; x <= 2; x++) for (int z = 3; z <= 11; z++) { lv.setBlock(base.offset(x, -1, z), Blocks.WATER.defaultBlockState(), 3); lv.setBlock(base.offset(x, -2, z), Blocks.STONE.defaultBlockState(), 3); }
            var b = com.xenowarriors.bot.PartyBots.spawn(p, CharacterType.SAM, null);
            if (b == null) return;
            for (int i = 0; i < b.inventory.getContainerSize(); i++) b.inventory.setItem(i, ItemStack.EMPTY);
            b.inventory.addItem(new ItemStack(Items.COBBLESTONE, 16));
            com.xenowarriors.stats.Building.addLevels(b, 12);
            b.moveTo(p.getX(), p.getY(), p.getZ() + 1.5, 0F, 0F);
            wbot[0] = b;
            wb[0] = com.xenowarriors.bot.BotWaterBridge.laid; wb[1] = com.xenowarriors.bot.BotWaterBridge.lifted;
            p.connection.teleport(p.getX(), p.getY(), p.getZ() + 14, 180F, 20F);
        }), 30));
        l.add(new Step("bot_water_bridge", () -> {}, 120));
        l.add(new Step(null, () -> PackTests.server(p -> {
            var b = (com.xenowarriors.bot.PartyBotEntity) wbot[0];
            int laid = com.xenowarriors.bot.BotWaterBridge.laid - wb[0], lifted = com.xenowarriors.bot.BotWaterBridge.lifted - wb[1];
            PackTests.check("water bridge: a Building 10+ bot lays blocks across water and picks them back up", laid > 2 && lifted > 0 && b != null && b.distanceToSqr(p) < 6 * 6,
                    "laid " + laid + " lifted " + lifted + " dist " + (b == null ? -1 : Math.round(b.distanceTo(p))) + " level " + (b == null ? -1 : com.xenowarriors.stats.Building.level(b)));
            PackTests.clear(p);
        }), 40));
        // DragonMineZ hair on one of your own characters with our look
        int[] hair = new int[1];
        l.add(new Step(null, () -> PackTests.server(p -> {
            PackTests.arena(p);
            var d = p.getCapability(com.xenowarriors.character.ModCapabilities.PLAYER_CHARACTER).orElse(null);
            d.youSlots[7] = new com.xenowarriors.you.YouSlot();
            var s = new com.xenowarriors.you.YouSlot(); s.name = "Corvin"; s.race = com.xenowarriors.character.Race.HUMAN; s.element = com.xenowarriors.ka.Element.FIRE;
            com.xenowarriors.you.YouSlots.act(p, com.xenowarriors.network.YouActionPacket.CREATE, 7, s);
            var on = new com.xenowarriors.you.YouSlot(); on.name = "Corvin"; on.dmzHair = true;
            com.xenowarriors.you.YouSlots.act(p, com.xenowarriors.network.YouActionPacket.DMZ_HAIR, 7, on);
        }), 40));
        l.add(new Step(null, () -> { mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT); mc.player.setYRot(0); mc.player.setXRot(0); hair[0] = com.xenowarriors.client.compat.DmzHairBridge.drawn; }, 20));
        l.add(new Step("you_dmz_hair", () -> {
            var you = com.xenowarriors.client.ClientCharacterData.you(mc.player);
            PackTests.check("dmzhair: your character's DragonMineZ hair is drawn on our look", you != null && you.dmzHair && com.xenowarriors.client.compat.DmzHairBridge.drawn > hair[0],
                    "dmz " + com.xenowarriors.client.compat.DmzHairBridge.works() + " drawn " + (com.xenowarriors.client.compat.DmzHairBridge.drawn - hair[0]) + " why " + com.xenowarriors.client.compat.DmzHairBridge.lastWhy);
        }, 5));
        l.add(new Step(null, () -> mc.setScreen(new com.xenowarriors.client.you.YouSlotsScreen()), 20));
        l.add(new Step("you_dmz_hair_button", () -> { mc.setScreen(null); mc.options.setCameraType(CameraType.FIRST_PERSON); mc.options.hideGui = false; }, 5));
    }
}

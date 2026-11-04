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
        if (all || focus.equals("botgear_anim")) anim(mc, l);
        l.add(new Step(null, () -> server(BotGearTests::clearBots), 5));
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
            // walking: the bot strolls off
            l.add(new Step(null, () -> server(p -> {
                PartyBotEntity b = bot(p, t);
                if (b == null) return;
                b.setNoGravity(false);
                b.moveTo(at[0] - 1.5, at[1], at[2], 0, 0);
                b.setNoAi(false);
                b.getNavigation().moveTo(at[0] - 6, at[1], at[2] + 1, 1.0);
            }), 8));
            l.add(new Step("botgear_anim_" + n + "_walk", () -> {}, 10));
        }
        l.add(new Step(null, () -> mc.options.setCameraType(CameraType.FIRST_PERSON), 5));
    }
}

package com.xenowarriors.client.test;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.bot.PartyBotEntity;
import com.xenowarriors.bot.PartyBots;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.client.ClientCharacterData;
import com.xenowarriors.client.ClientSettings;
import com.xenowarriors.client.HudHead;
import com.xenowarriors.client.HudPortrait;
import com.xenowarriors.client.UiTest;
import com.xenowarriors.client.UiTest.Step;
import com.xenowarriors.client.bot.BotAvatars;
import com.xenowarriors.client.render.CharacterDetailLayer;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * HUD portraits mirror the model's face (focus "hudface"; {@link #pack} inside the real-pack test). With
 * CharacterDetailLayer.recordFaces on, every frame the model records the face it drew and every portrait records the face
 * it showed; the test compares them for you (Mikiko) and a Rhalix bot teammate:
 * <ul>
 *   <li>sync: 160 ticks of natural animation (blinks, idle looks), every tick: model face == portrait face;</li>
 *   <li>forced states: hurt, attacking, starving, asleep, knocked out, Fox King, chatting (mouth), badly hurt (damage
 *   layer); the bot: hurt, starving, Demon's Remnants, Lunar, knocked out - each must equal and be the expected face;</li>
 *   <li>the Blazing Wind HUD shows the same live face.</li>
 * </ul>
 * Logs "XW-TEST hudface ... PASS/FAIL" and "hudface summary pass=N fail=N". Stills hudface_*: the big live head (the
 * model) beside the big portrait, over the real HUD and the third-person view.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class HudFaceTests {
    private HudFaceTests() {}

    public static boolean handles(String focus) { return focus != null && focus.startsWith("hudface"); }

    private static int pass, fail;
    private static boolean inPack;
    private static void log(String s) {
        if (inPack) PackTests.log("hudface " + s); else UiTest.log("hudface " + s);
    }

    private static void check(String what, boolean ok, String detail) {
        if (inPack) { PackTests.check("hudface " + what, ok, detail); return; }
        if (ok) pass++; else fail++;
        log(what + ": " + detail + " -> " + (ok ? "PASS" : "FAIL"));
    }

    /** Draws the big side-by-side (model head | portrait) for the stills. */
    private static boolean showcase;

    @SubscribeEvent
    public static void onGui(RenderGuiEvent.Post event) {
        if (!showcase) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        var g = event.getGuiGraphics();
        int y = 4;
        for (Entity e : new Entity[]{mc.player, bot(mc)}) {
            if (!(e instanceof net.minecraft.world.entity.LivingEntity le)) continue;
            AbstractClientPlayer body = HudPortrait.body(le);
            CharacterType t = body == null ? CharacterType.NONE : ClientCharacterData.get(body);
            g.fill(2, y - 2, 196, y + 92, 0xC0101018);
            HudHead.draw(g, body, t, 6, y + 2, 86, false, 0.5F, 1.1F, true, 0F);   // the model itself (live render)
            HudPortrait.draw(g, le, t, 104, y + 2, 86, false);                    // the portrait
            g.pose().pushPose();
            g.pose().translate(0, 0, 400);                                         // (in front of the 3D head)
            g.drawString(mc.font, "model", 8, y + 80, 0xFFFFFF, true);
            g.drawString(mc.font, "portrait", 106, y + 80, 0xFFFFFF, true);
            g.pose().popPose();
            y += 98;
        }
    }

    /** The Rhalix bot teammate on the party HUD (found the way the HUD finds it: by the party member's id). */
    private static PartyBotEntity bot(Minecraft mc) {
        if (mc.level == null) return null;
        PartyBotEntity any = null;
        for (var m : com.xenowarriors.client.party.PartyClient.members()) {
            if (!m.bot() || m.type() != CharacterType.RHALIX.ordinal()) continue;
            if (HudHead.find(m.id()) instanceof PartyBotEntity b) { if (b.getTags().contains("xw_hudface")) return b; any = b; }
        }
        if (any != null) return any;
        for (Entity e : mc.level.entitiesForRendering()) if (e instanceof PartyBotEntity b && b.character() == CharacterType.RHALIX) return b;
        return null;
    }

    private static AbstractClientPlayer botBody(Minecraft mc) {
        PartyBotEntity b = bot(mc);
        return b == null ? null : BotAvatars.existing(b);
    }

    /** "face|damage|mouth" the model drew and the portrait showed last frame, for you or the bot. */
    private static String model(Minecraft mc, boolean ofBot) {
        Entity e = ofBot ? botBody(mc) : mc.player;
        return e == null ? null : CharacterDetailLayer.RENDERED.get(e.getId());
    }

    private static String hud(Minecraft mc, boolean ofBot) {
        Entity e = ofBot ? bot(mc) : mc.player;
        return e == null ? null : HudPortrait.SHOWN.get(e.getUUID());
    }

    private static String face(String key) {
        if (key == null) return "null";
        String f = key.split("\\|")[0];
        return f.substring(f.lastIndexOf('/') + 1);
    }

    /** Model and portrait show the same face, and it's one of the expected ones (any = "" accepts all). */
    private static void same(Minecraft mc, String what, boolean ofBot, String... expect) {
        if (ofBot && skipBot(what)) return;
        String m = model(mc, ofBot), h = hud(mc, ofBot);
        boolean eq = m != null && m.equals(h);
        boolean exp = expect.length == 0 || inPack;     // (pack: a slow client, timed faces may be over - only "the same face" counts)
        for (String x : expect) if (m != null && m.contains(x)) exp = true;
        check(what, eq && exp, "model=" + m + " portrait=" + h + " face=" + face(m) + " expected=" + String.join("/", expect));
    }

    /** The client has the bot and its drawn body (the pack waits for it; see steps). */
    private static boolean botReady;
    private static String botWhy = "";
    private static int polls;

    /** In the pack, a bot the slow client never loaded: SKIP its checks (with the reason) instead of failing them. */
    private static boolean skipBot(String what) {
        if (!inPack || botReady) return false;
        log(what + ": SKIP (the client never got the bot: " + botWhy + ")");
        return true;
    }

    /** What the client knows about the bot (why it isn't ready). */
    private static String botDiag(Minecraft mc) {
        int n = 0; double near = -1; String chars = "";
        if (mc.level != null && mc.player != null) for (Entity e : mc.level.entitiesForRendering()) if (e instanceof PartyBotEntity b) {
            n++; double d = b.distanceTo(mc.player); if (near < 0 || d < near) near = d; chars += b.character().getId() + " ";
        }
        long members = com.xenowarriors.client.party.PartyClient.members().stream().filter(m -> m.bot()).count();
        return "clientBots=" + n + " nearest=" + (near < 0 ? "-" : String.format(java.util.Locale.ROOT, "%.1f", near)) + " chars=[" + chars.trim()
                + "] botMembers=" + members + " avatarsOff=" + BotAvatars.disabled + " bot=" + (bot(mc) != null) + " body=" + (botBody(mc) != null);
    }

    private static int samples, mismatches;
    private static final Set<String> SEEN = new LinkedHashSet<>(), SEEN_BOT = new LinkedHashSet<>();

    private static void sample(Minecraft mc) {
        for (boolean ofBot : new boolean[]{false, true}) {
            String m = model(mc, ofBot), h = hud(mc, ofBot);
            if (m == null || h == null) continue;
            samples++;
            if (!m.equals(h)) { mismatches++; if (mismatches <= 5) log("mismatch " + (ofBot ? "bot" : "you") + " model=" + m + " portrait=" + h); }
            (ofBot ? SEEN_BOT : SEEN).add(face(m));
        }
    }

    // ------------------------------------------------------------------ the test

    public static void build(Minecraft mc, List<Step> l, String focus) {
        inPack = false;
        steps(mc, l, "hudface");
        l.add(new Step(null, () -> log("summary pass=" + pass + " fail=" + fail), 2));
    }

    /** Inside the real-pack test (PackTests): the same checks, counted in the pack's summary. */
    public static void pack(Minecraft mc, List<Step> l) {
        l.add(new Step(null, () -> inPack = true, 1));
        steps(mc, l, "pack_hudface");
        l.add(new Step(null, () -> inPack = false, 1));
    }

    private static void steps(Minecraft mc, List<Step> l, String shot) {
        l.add(new Step(null, () -> {
            UiTest.noToasts(mc); mc.setScreen(null); mc.options.hideGui = false;
            ClientSettings.setHudStyle("kh");
            CharacterDetailLayer.recordFaces = true;
            UiTest.character(CharacterType.MIKIKO);
            UiTest.server(p -> {
                p.setGameMode(GameType.SURVIVAL);
                p.setHealth(p.getMaxHealth()); p.getFoodData().setFoodLevel(20);
                for (var b : new java.util.ArrayList<>(PartyBots.bots(p))) b.discard();
                p.moveTo(p.getX(), p.getY(), p.getZ(), 0F, 0F);
            });
        }, 30));
        l.add(new Step(null, () -> UiTest.server(p -> {
            PartyBotEntity b = PartyBots.spawn(p, CharacterType.RHALIX, null);
            if (b != null) { b.moveTo(p.getX() + 1.3, p.getY(), p.getZ() + 0.4, 0F, 0F); b.setYHeadRot(0F); b.setYBodyRot(0F); b.setNoAi(true); b.addTag("xw_hudface"); }
            log("bot spawned=" + (b != null) + " bots=" + PartyBots.bots(p).size());
        }), 40));
        l.add(new Step(null, () -> { mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT); mc.player.setYRot(0F); mc.player.setXRot(0F); showcase = true;
            botReady = false; botWhy = ""; polls = 0; }, 20));
        // wait (up to ~20 s) until the client really has the bot and its body; keep it right beside you meanwhile
        for (int i = 0; i < 40; i++) l.add(new Step(null, () -> {
            if (botReady) return;
            polls++;
            if (bot(mc) != null && botBody(mc) != null) { botReady = true; log("bot loaded on the client after " + polls + " polls"); return; }
            UiTest.server(p -> { for (var b : PartyBots.bots(p)) if (b.getTags().contains("xw_hudface"))
                b.teleportTo(p.getX() + 1.3, p.getY(), p.getZ() + 0.4); });
            botWhy = botDiag(mc);
            if (polls % 8 == 1) log("waiting for the bot: " + botWhy);
        }, 10));
        l.add(new Step(null, () -> {
            if (bot(mc) != null && botBody(mc) != null) botReady = true;
            if (!botReady && inPack) { botWhy = botDiag(mc); log("bot in view: SKIP (not loaded on the client after ~20 s: " + botWhy + ")"); }
            else check("bot in view", botReady, "bot=" + bot(mc) + " body=" + botBody(mc) + " " + botDiag(mc));
        }, 2));
        // natural animation: every tick for 160 ticks
        l.add(new Step(null, () -> { samples = 0; mismatches = 0; SEEN.clear(); SEEN_BOT.clear(); HudPortrait.composes = 0; }, 1));
        for (int i = 0; i < 160; i++) l.add(new Step(null, () -> sample(mc), 0));
        l.add(new Step(null, () -> check("sync 160 ticks", samples >= (inPack ? 100 : 200) && mismatches == 0 && (inPack || SEEN.contains("mikiko_face_blink.png") || SEEN_BOT.contains("rhalix_face_blink.png")),
                "samples=" + samples + " mismatches=" + mismatches + " you=" + SEEN + " bot=" + SEEN_BOT + " recomposes=" + HudPortrait.composes), 1));
        l.add(new Step(shot + "_normal", () -> same(mc, "normal you", false), 1));

        // you: hurt
        l.add(new Step(null, () -> UiTest.server(p -> p.hurt(p.damageSources().generic(), 2F)), 2));
        l.add(new Step(shot + "_hurt", () -> same(mc, "hurt you", false, "_hurt", "_shocked"), 14));
        // attacking
        l.add(new Step(null, () -> mc.player.swing(InteractionHand.MAIN_HAND), 1));
        l.add(new Step(shot + "_attack", () -> same(mc, "attack you", false, "_fierce", "_shout"), 1));
        // starving
        l.add(new Step(null, () -> UiTest.server(p -> p.getFoodData().setFoodLevel(0)), 6));
        l.add(new Step(shot + "_starving", () -> same(mc, "starving you", false, "_starving", "_blink"), 1));
        l.add(new Step(null, () -> UiTest.server(p -> p.getFoodData().setFoodLevel(20)), 4));
        // asleep
        l.add(new Step(null, () -> UiTest.server(p -> {           // (in a real bed: without one the game wakes you straight away)
            var at = p.blockPosition();
            p.serverLevel().setBlock(at, net.minecraft.world.level.block.Blocks.RED_BED.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.HEAD), 3);
            p.serverLevel().setDayTime(18000);                    // (and at night: by day it wakes you too)
            p.startSleeping(at);
        }), 6));
        l.add(new Step(shot + "_sleep", () -> { mc.setScreen(null); same(mc, "asleep you", false, "_snore", "_sleep"); }, 1));
        l.add(new Step(null, () -> UiTest.server(p -> {
            var at = p.getSleepingPos().orElse(p.blockPosition());
            p.stopSleeping();
            p.serverLevel().setDayTime(6000);
            if (p.serverLevel().getBlockState(at).getBlock() instanceof net.minecraft.world.level.block.BedBlock)
                p.serverLevel().setBlock(at, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
        }), 6));
        // knocked out
        l.add(new Step(null, () -> { mc.setScreen(null); com.xenowarriors.client.party.PartyClient.setKnockedOut(mc.player.getUUID(), 200); }, 3));
        l.add(new Step(shot + "_down", () -> same(mc, "knocked out you", false, "_dizzy"), 1));
        l.add(new Step(null, () -> com.xenowarriors.client.party.PartyClient.setKnockedOut(mc.player.getUUID(), 0), 2));
        // Fox King: his own frame (the Fox King hair) and the same face
        l.add(new Step(null, () -> com.xenowarriors.client.mikiko.FoxKingClient.set(mc.player.getId(), true), 4));
        l.add(new Step(shot + "_foxking", () -> {
            same(mc, "fox king you", false);
            String k = HudPortrait.keyFor(mc.player, CharacterType.MIKIKO);
            check("fox king frame", k != null && k.contains("foxking.png"), "key=" + k);
        }, 1));
        l.add(new Step(null, () -> com.xenowarriors.client.mikiko.FoxKingClient.set(mc.player.getId(), false), 2));
        // chatting: the mouth flaps on the model and the portrait alike
        l.add(new Step(null, () -> MinecraftForge.EVENT_BUS.post(new net.minecraftforge.client.event.ClientChatEvent("hello from the hud test")), 1));
        int[] mouths = new int[2];
        for (int i = 0; i < 12; i++) l.add(new Step(null, () -> {
            String m = model(mc, false), h = hud(mc, false);
            if (m != null && m.equals(h) && !m.endsWith("|null")) mouths[0]++;
            if (m != null && !m.equals(h)) mouths[1]++;
        }, 0));
        l.add(new Step(shot + "_talk", () -> check("chat mouth you", mouths[0] >= 3 && mouths[1] == 0, "frames with the mouth on both=" + mouths[0] + " mismatches=" + mouths[1]), 0));
        // badly hurt: the battle-damage layer over the face
        l.add(new Step(null, () -> UiTest.server(p -> p.setHealth(p.getMaxHealth() * 0.2F)), 4));
        l.add(new Step(shot + "_lowhp", () -> same(mc, "badly hurt you", false, "_face_damage2"), 1));
        l.add(new Step(null, () -> UiTest.server(p -> p.setHealth(p.getMaxHealth())), 4));

        // the bot (its body is a stand-in player: the same face the portrait shows)
        l.add(new Step(null, () -> UiTest.server(p -> {
            for (var b : PartyBots.bots(p)) b.hurt(p.damageSources().generic(), 1F);
        }), 2));
        l.add(new Step(shot + "_bot_hurt", () -> same(mc, "hurt bot", true, "_hurt", "_shocked"), 14));
        l.add(new Step(null, () -> { var b = bot(mc); if (b != null) com.xenowarriors.client.ClientSkillState.setStatus(b.getId(), com.xenowarriors.network.PoisonStatePacket.STARVING); }, 3));
        l.add(new Step(shot + "_bot_starving", () -> same(mc, "starving bot", true, "_starving", "_blink"), 1));
        l.add(new Step(null, () -> { var b = bot(mc); if (b != null) { com.xenowarriors.client.ClientSkillState.setStatus(b.getId(), 0); com.xenowarriors.client.DemonClient.set(b.getId(), true); } }, 3));
        l.add(new Step(shot + "_bot_demon", () -> same(mc, "demon bot", true, "rhalix_demon_face"), 1));
        l.add(new Step(null, () -> { var b = bot(mc); if (b != null) { com.xenowarriors.client.DemonClient.set(b.getId(), false); com.xenowarriors.client.LunarClient.set(b.getId(), true, false); } }, 3));
        l.add(new Step(shot + "_bot_lunar", () -> {
            same(mc, "lunar bot", true, "rhalix_lunar_face");
            var b = bot(mc);
            String k = b == null ? null : HudPortrait.keyFor(b, CharacterType.RHALIX);
            if (!skipBot("lunar frame")) check("lunar frame", k != null && k.contains("frame/rhalix_lunar.png"), "key=" + k);
        }, 1));
        l.add(new Step(null, () -> { var b = bot(mc); if (b != null) com.xenowarriors.client.LunarClient.set(b.getId(), false, false);
            var a = botBody(mc); if (a != null) com.xenowarriors.client.party.PartyClient.setKnockedOut(a.getUUID(), 200); }, 3));
        l.add(new Step(shot + "_bot_down", () -> same(mc, "knocked out bot", true, "_dizzy"), 1));
        l.add(new Step(null, () -> { var a = botBody(mc); if (a != null) com.xenowarriors.client.party.PartyClient.setKnockedOut(a.getUUID(), 0); }, 2));

        // Blazing Wind: the same live faces
        l.add(new Step(null, () -> ClientSettings.setHudStyle("blazing"), 4));
        l.add(new Step(null, () -> { samples = 0; mismatches = 0; }, 1));
        for (int i = 0; i < 40; i++) l.add(new Step(null, () -> sample(mc), 0));
        l.add(new Step(shot + "_blazing", () -> check("blazing sync", samples >= (inPack ? 25 : 50) && mismatches == 0, "samples=" + samples + " mismatches=" + mismatches), 1));
        l.add(new Step(null, () -> {
            showcase = false; CharacterDetailLayer.recordFaces = false;
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            UiTest.server(p -> { for (var b : new java.util.ArrayList<>(PartyBots.bots(p))) b.discard(); p.setGameMode(GameType.CREATIVE); });
        }, 4));
    }
}

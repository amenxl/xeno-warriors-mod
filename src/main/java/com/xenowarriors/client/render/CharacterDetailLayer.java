package com.xenowarriors.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.client.ClientCharacterData;
import com.xenowarriors.client.ClientSkillState;
import com.xenowarriors.client.FlightTracker;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;

import java.util.EnumMap;
import java.util.Map;

/**
 * Draws each character's 3D details on top of the player model. The detail bones copy the
 * player model's pose every frame, so hair follows the head, sleeves follow the arms, etc.
 * Rhalix's wings come out when he's in the air (flying, gliding, falling) or casting Kishi,
 * like in the show, and are always shown on the selection screen.
 */
public class CharacterDetailLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private final Map<CharacterType, DetailModel> models = new EnumMap<>(CharacterType.class);
    private static final net.minecraft.resources.ResourceLocation FOX_KING_TEX =
            com.xenowarriors.XenoWarriors.id("textures/entity/character/mikiko_foxking_details.png");
    private final DetailModel foxKing = DetailModel.loadPath("/assets/" + com.xenowarriors.XenoWarriors.MOD_ID + "/models/detail/mikiko_foxking.json");

    /** Mikiko's other outfits (index = outfit number; 0 = his normal look, the type's own model). */
    private final Map<CharacterType, DetailModel[]> outfits = new EnumMap<>(CharacterType.class);
    /** The same outfits in Fox King mode: the outfit with Fox King's hair. */
    private final Map<CharacterType, DetailModel[]> kingOutfits = new EnumMap<>(CharacterType.class);

    /** The most recently made layer (both player renderers load the same models) - for Epic Fight's renderer. */
    private static CharacterDetailLayer instance;

    public CharacterDetailLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
        instance = this;
        for (CharacterType type : CharacterType.PLAYABLE) {
            DetailModel model = DetailModel.load(type);
            if (model != null) models.put(type, model);
        }
        lunarModel = DetailModel.loadPath("/assets/" + com.xenowarriors.XenoWarriors.MOD_ID + "/models/detail/rhalix_lunar.json");
        for (CharacterType type : CharacterType.PLAYABLE) {
            String[] keys = type.outfitKeys();
            DetailModel[] arr = new DetailModel[keys.length];
            for (int i = 1; i < keys.length; i++) {
                arr[i] = DetailModel.loadPath("/assets/" + com.xenowarriors.XenoWarriors.MOD_ID + "/models/detail/" + type.getId()
                        + "_" + keys[i] + ".json");
            }
            outfits.put(type, arr);
            if (type == CharacterType.MIKIKO) {
                DetailModel[] karr = new DetailModel[keys.length];
                for (int i = 1; i < keys.length; i++) {
                    karr[i] = DetailModel.loadPath("/assets/" + com.xenowarriors.XenoWarriors.MOD_ID + "/models/detail/" + type.getId()
                            + "_" + keys[i] + "_foxking.json");
                }
                kingOutfits.put(type, karr);
            }
        }
    }

    private static DetailModel shiyoModel;
    private static boolean shiyoTried;
    public static final net.minecraft.resources.ResourceLocation SHIYO_TEX = com.xenowarriors.XenoWarriors.id("textures/entity/character/mikiko_shiyoken_details.png");
    public static final net.minecraft.resources.ResourceLocation SHIYO_SKIN = com.xenowarriors.XenoWarriors.id("textures/entity/character/mikiko_shiyoken.png");

    /**
     * Mikiko in DragonMineZ's Shiyoken forms wears his Shiyoken outfit (tools/xw/outfits/mikiko_shiyoken.py) - a black
     * hoodie with a fire swirl and flames - whatever outfit he had on (not in Fox King mode, not fused).
     */
    public static boolean shiyokenOutfit(AbstractClientPlayer p, CharacterType type) {
        return type == CharacterType.MIKIKO && !foxActive(p, type) && com.xenowarriors.client.FusionLook.of(p) == null
                && com.xenowarriors.client.mikiko.FoxAuraClient.level(p) > 0 && !(ClientCharacterData.isPreviewing() && com.xenowarriors.client.mikiko.FoxAuraClient.forceLevel < 0);
    }

    private static DetailModel shiyokenModel() {
        if (!shiyoTried) { shiyoTried = true; shiyoModel = DetailModel.loadPath("/assets/" + com.xenowarriors.XenoWarriors.MOD_ID + "/models/detail/mikiko_shiyoken.json"); }
        return shiyoModel;
    }

    /** The detail model this player shows right now (Fox King Mikiko has his own), or null. */
    public static DetailModel detailModel(AbstractClientPlayer p) {
        if (instance == null) return null;
        CharacterType type = ClientCharacterData.get(p);
        if (isFoxKing(p, type)) return instance.foxKing;
        if (shiyokenOutfit(p, type) && shiyokenModel() != null) return shiyokenModel();
        if (lunar(p, type) && instance.lunarModel != null) return instance.lunarModel;      // crescent-moon wings
        var fl = com.xenowarriors.client.FusionLook.of(p);
        if (fl != null && fl.model() != null) return fl.model();                                // a fusion: its own model
        DetailModel om = instance.outfitModel(p, type);
        if (om != null) return om;
        return instance.models.get(type);
    }

    /** Resource path of the detail model this player shows (for Epic Fight's own copy). */
    public static String detailModelPath(AbstractClientPlayer p) {
        CharacterType type = ClientCharacterData.get(p);
        var fl = com.xenowarriors.client.FusionLook.of(p);
        if (fl != null) return fl.modelPath();
        String name = isFoxKing(p, type) ? "mikiko_foxking" : shiyokenOutfit(p, type) ? "mikiko_shiyoken" : lunar(p, type) ? "rhalix_lunar" : outfitName(p, type);
        return "/assets/" + com.xenowarriors.XenoWarriors.MOD_ID + "/models/detail/" + name + ".json";
    }

    private DetailModel lunarModel;

    private static boolean lunar(AbstractClientPlayer p, CharacterType type) {
        return type == CharacterType.RHALIX && ClientCharacterData.outfit(p) == 0 && com.xenowarriors.client.LunarClient.active(p.getId());
    }

    private DetailModel outfitModel(AbstractClientPlayer p, CharacterType type) {
        int o = ClientCharacterData.outfit(p);
        DetailModel[] arr = (foxActive(p, type) ? kingOutfits : outfits).get(type);
        DetailModel m = o > 0 && arr != null && o < arr.length ? arr[o] : null;
        if (m == null && o > 0 && outfits.get(type) != null) m = outfits.get(type)[o];
        return m;
    }

    /** "mikiko_origins1"... for an outfit, else the type's id. */
    public static String outfitName(AbstractClientPlayer p, CharacterType type) {
        int o = ClientCharacterData.outfit(p);
        return o > 0 ? type.getId() + "_" + type.outfitKeys()[o] + (foxActive(p, type) ? "_foxking" : "") : type.getId();
    }

    /** Mikiko is in Fox King mode (whatever he's wearing). */
    private static boolean foxActive(AbstractClientPlayer p, CharacterType type) {
        return type == CharacterType.MIKIKO && com.xenowarriors.client.mikiko.FoxKingClient.active(p);
    }

    /** Fox King in his normal outfit (other outfits have their own Fox King versions, see outfitModel). */
    private static boolean isFoxKing(AbstractClientPlayer p, CharacterType type) {
        return foxActive(p, type) && instance != null && instance.foxKing != null && ClientCharacterData.outfit(p) == 0;
    }

    private static final net.minecraft.resources.ResourceLocation LUNAR_TEX = com.xenowarriors.XenoWarriors.id("textures/entity/character/rhalix_lunar_details.png");
    private static final net.minecraft.resources.ResourceLocation LUNAR_GLOW = com.xenowarriors.XenoWarriors.id("textures/entity/character/rhalix_lunar_glow.png");

    public static net.minecraft.resources.ResourceLocation detailTexture(AbstractClientPlayer p) {
        CharacterType type = ClientCharacterData.get(p);
        var fl = com.xenowarriors.client.FusionLook.of(p);
        if (fl != null) return fl.details();
        if (isFoxKing(p, type)) return FOX_KING_TEX;
        if (shiyokenOutfit(p, type)) return SHIYO_TEX;
        int o = ClientCharacterData.outfit(p);
        if (o == 0 && type == CharacterType.RHALIX && com.xenowarriors.client.LunarClient.active(p.getId())) return LUNAR_TEX;
        return o > 0 ? com.xenowarriors.XenoWarriors.id("textures/entity/character/" + outfitName(p, type) + "_details.png")
                : type.getDetailTexture();
    }

    /** Drawn faces are on (the setting): Ultima's and Mikiko's faces come from their <id>_drawn_* sets. */
    public static boolean drawn(CharacterType type) {
        return (type == CharacterType.ULTIMA || type == CharacterType.MIKIKO) && com.xenowarriors.client.ClientSettings.drawnFaces();
    }

    /** The plain face for a character (bots, VR, previews). */
    public static net.minecraft.resources.ResourceLocation baseFace(CharacterType type) {
        return drawn(type) ? face(type.getId() + "_drawn", null) : type.getFaceTexture();
    }

    /** set -> expression ("" = none) -> texture; filled as faces are first shown, emptied on resource reload. */
    private static final java.util.Map<String, java.util.Map<String, net.minecraft.resources.ResourceLocation>> FACES = new java.util.concurrent.ConcurrentHashMap<>();
    /** A close expression for sets that don't have one (the drawn faces, Rhalix's Demon/Lunar faces lack a few). */
    private static final java.util.Map<String, String> FACE_FALLBACK = java.util.Map.ofEntries(java.util.Map.entry("wince", "strained"), java.util.Map.entry("hurt_heavy", "panic"),
            java.util.Map.entry("shocked", "hurt"), java.util.Map.entry("determined", "focused"),
            java.util.Map.entry("smirk", "smug"), java.util.Map.entry("laugh", "grin"), java.util.Map.entry("pout", "annoyed"), java.util.Map.entry("blush", "happy"),
            java.util.Map.entry("love", "happy"), java.util.Map.entry("starving", "exhausted"), java.util.Map.entry("starving2", "starving"));

    /**
     * textures/entity/character/&lt;set&gt;_face_&lt;expr&gt;.png - or a close expression, or the plain &lt;set&gt;_face.png when
     * that set has no such face (never the missing-texture checkerboard). Looked up once, no allocation per frame.
     */
    public static net.minecraft.resources.ResourceLocation face(String set, String expr) {
        var bySet = FACES.computeIfAbsent(set, k -> new java.util.concurrent.ConcurrentHashMap<>());
        String key = expr == null ? "" : expr;
        net.minecraft.resources.ResourceLocation r = bySet.get(key);
        if (r == null) {
            r = resolveFace(set, expr);
            bySet.put(key, r);
        }
        return r;
    }

    private static net.minecraft.resources.ResourceLocation resolveFace(String set, String expr) {
        String base = "textures/entity/character/" + set + "_face";
        net.minecraft.resources.ResourceLocation plain = com.xenowarriors.XenoWarriors.id(base + ".png");
        if (expr == null) return plain;
        var rm = net.minecraft.client.Minecraft.getInstance().getResourceManager();
        int hops = 0;
        for (String e = expr; e != null && hops++ < 4; e = FACE_FALLBACK.get(e)) {
            net.minecraft.resources.ResourceLocation rl = com.xenowarriors.XenoWarriors.id(base + "_" + e + ".png");
            if (rm.getResource(rl).isPresent()) return rl;
        }
        return plain;
    }

    /** Resource packs changed: look the faces up again. */
    public static void clearFaceCache() { FACES.clear(); }

    public static net.minecraft.resources.ResourceLocation faceTexture(AbstractClientPlayer p) {
        return faceFrame(p, new FaceFrame()).face;
    }

    /**
     * Everything drawn on a player's face this frame: the face texture (expression, blink, Demon/Lunar eyes, drawn set,
     * overload), the battle-damage layer over it and the talking mouth. The model and the HUD portraits
     * (client/HudPortrait) both read it, so the portrait always shows exactly the face the model is wearing.
     */
    public static final class FaceFrame {
        public net.minecraft.resources.ResourceLocation face, damage, mouth;
        /** The expression ("blink", "hurt"...; null = the plain face). */
        public String expr;
        /** Kaeru's Change: a dark silhouette with glowing eyes (face is the silhouette's eyes). */
        public boolean silhouette;
    }

    /** Fills {@code f} with the face this player shows right now (see FaceFrame) and returns it. */
    public static FaceFrame faceFrame(AbstractClientPlayer p, FaceFrame f) {
        CharacterType type = ClientCharacterData.get(p);
        f.damage = null; f.mouth = null; f.expr = null;
        f.silhouette = com.xenowarriors.client.kaeru.ChangeClient.silhouette(p.getId());
        if (f.silhouette) { f.face = com.xenowarriors.client.kaeru.ChangeClient.SIL_FACE; return f; }
        String expr = expression(p, type);
        f.expr = expr;
        var fusion = com.xenowarriors.client.FusionLook.of(p);
        if (fusion != null) {                                                                  // a fusion: its own faces (and the shared talking mouths)
            f.face = face(fusion.id, expr);
            int mouth = talkingMouth(p, expr);
            if (mouth > 0) f.mouth = com.xenowarriors.XenoWarriors.id("textures/entity/character/" + fusion.id + "_mouth_" + mouth + ".png");
            return f;
        }
        boolean dr = drawn(type);
        String fp = dr ? type.getId() + "_drawn" : type.getId();
        if (type == CharacterType.ULTIMA && com.xenowarriors.client.UltimaClient.overload(p.getId())) f.face = face(fp, "overload");
        else if (!dr && type == CharacterType.RHALIX && com.xenowarriors.client.LunarClient.active(p.getId()))      // Lunar: crescents on his cheeks
            f.face = face("rhalix_lunar", expr);
        else if (!dr && type == CharacterType.RHALIX && com.xenowarriors.client.DemonClient.active(p.getId()))     // Demon's Remnants: glowing red eyes
            f.face = face("rhalix_demon", expr);
        else if (!dr && com.xenowarriors.client.compat.DmzForms.faceSet(p) != null)       // Super Saiyan forms: teal eyes, SSJ3 no brows, SSJ4 red-ringed gold eyes...
            f.face = face(type.getId() + "_" + com.xenowarriors.client.compat.DmzForms.faceSet(p), expr);
        else f.face = expr == null ? baseFace(type) : face(fp, expr);
        int damage = damageLevel(p);
        if (damage > 0 && !ClientCharacterData.isPreviewing())
            f.damage = com.xenowarriors.XenoWarriors.id("textures/entity/character/" + fp + "_face_damage" + damage + ".png");
        int mouth = talkingMouth(p, expr);          // talking in voice chat (or just sent a chat line): the mouth over the face
        if (mouth > 0) f.mouth = com.xenowarriors.XenoWarriors.id("textures/entity/character/" + fp + "_mouth_" + mouth + ".png");
        return f;
    }

    /**
     * Which HUD portrait frame (textures/gui/hud/frame/, made by tools/xw/hud/portrait_frames.py) matches this player's
     * head right now: their outfit, Fox King or Lunar look, then the hood up, Yuri's glasses or Technica's goggles down.
     * Returns {look, look + toggle} (the second is null when nothing is toggled).
     */
    public static String[] portraitLook(AbstractClientPlayer p, CharacterType type) {
        String look = ClientCharacterData.outfit(p) > 0 ? outfitName(p, type)
                : foxActive(p, type) ? "mikiko_foxking" : lunar(p, type) ? "rhalix_lunar" : type.getId();
        String toggle = null;
        if (com.xenowarriors.client.mikiko.HoodClient.up(p)) toggle = "_hood";
        else if (type == CharacterType.YURI && com.xenowarriors.client.YuriLaptopIdle.glasses(p)) toggle = "_glasses";
        else if (type == CharacterType.TECHNICA && com.xenowarriors.client.TechGoggles.down(p)) toggle = "_goggles";
        return new String[]{look, toggle == null ? null : look + toggle};
    }

    /**
     * Which face to show: sleeping/snoring (Mikiko), hurt when hit, shouting while using a skill, fierce while
     * attacking, sick when poisoned or weakened, a blink now and then - otherwise the normal face (null).
     */
    /** The color of the torn, hanging cloth and bandages on a hurt character: their clothes' tone. */
    private static int clothColor(CharacterType type) {
        if (type == CharacterType.MIKIKO) return 0x3A3440;
        if (type == CharacterType.RHALIX) return 0x7A3AA8;
        int c = type.getColor();
        return (((c >> 16) & 255) * 6 / 10 << 16) | (((c >> 8) & 255) * 6 / 10 << 8) | ((c & 255) * 6 / 10);
    }

    /** per entity: {health last seen, how hard the last hit was (0 light, 1 normal, 2 heavy)} */
    private static final java.util.Map<Integer, float[]> HURT = com.xenowarriors.client.ClientReset.track(new java.util.HashMap<>());

    /**
     * The hurt face for how hard they were just hit (players and bots): a light hit (under 8% of their health) a wince,
     * a normal one the hurt face, a big one (20% or more) - or any hit while under a quarter of their health - the
     * heavy one with tears; under half health it flickers with the shocked face. Null when they weren't just hit.
     */
    static String hurtFace(AbstractClientPlayer p) {
        float[] h = HURT.computeIfAbsent(p.getId(), k -> new float[]{p.getHealth(), 1});
        float hp = p.getHealth(), max = Math.max(1F, p.getMaxHealth());
        if (hp < h[0] - 0.01F) {                                          // a new hit: how hard?
            float d = (h[0] - hp) / max;
            h[1] = d >= 0.2F || hp < max * 0.25F ? 2 : d < 0.08F ? 0 : 1;
        }
        h[0] = hp;
        if (p.hurtTime <= 0) return null;
        if (h[1] == 2) return "hurt_heavy";
        if (h[1] == 0) return "wince";
        return hp < max * 0.5F && (p.getId() + p.tickCount / 10) % 2 == 0 ? "shocked" : "hurt";
    }

    private static DetailModel yuriSsj;
    private static boolean yuriSsjTried;
    private static final net.minecraft.resources.ResourceLocation YURI_SSJ_TEX = com.xenowarriors.XenoWarriors.id("textures/entity/character/yuri_ssjhair.png");

    /** Yuri's Super Saiyan hair (tools/xw/dmzhair/yuri_ssj_hair.py), loaded the first time it's needed. */
    private static DetailModel yuriSsjHair() {
        if (!yuriSsjTried) { yuriSsjTried = true; yuriSsj = DetailModel.loadPath("/assets/" + com.xenowarriors.XenoWarriors.MOD_ID + "/models/detail/yuri_ssjhair.json"); }
        return yuriSsj;
    }

    /** A face forced on a menu portrait (the off-duty cards: what they're busy doing), set only around that draw. */
    public static String previewExpr;

    private static String expression(AbstractClientPlayer p, CharacterType type) {
        if (previewExpr != null && ClientCharacterData.isPreviewing()) return previewExpr;
        String pod = com.xenowarriors.client.pod.PodFace.face(p);       // resting in a healing pod: eyes closed
        if (pod != null) return pod;
        if (p.isSleeping()) {
            if (type != CharacterType.MIKIKO) return "blink";
            return (p.tickCount % 40) < 22 ? "snore" : "sleep";   // snores in time with the z's
        }
        if (com.xenowarriors.client.party.PartyClient.knockedOut(p)) return "dizzy";     // knocked out in a battle
        if (ClientSkillState.starving(p) && !(p.isUsingItem() && p.getUseItem().getUseAnimation() == net.minecraft.world.item.UseAnim.EAT)) {
            // starving: sunken eyes, pale, mouth hanging open (breathing weakly), the odd slow blink
            int st = Math.floorMod(p.tickCount + p.getId() * 11, 100);
            if (st < 4) return "blink";
            return (st / 16) % 2 == 0 ? "starving" : "starving2";
        }
        String hurt = hurtFace(p);
        if (hurt != null) return hurt;
        String beat = com.xenowarriors.client.GarryBeatbox.face(p);
        if (beat != null) return beat;
        String doze = com.xenowarriors.client.KeitaDoze.face(p);
        if (doze != null) return doze;
        if (com.xenowarriors.client.KarmaFocusClient.weight(p) > 0.4F) return "sense";      // eyes closed, sensing Karma
        String arrow = com.xenowarriors.client.ArrowPullClient.face(p);
        if (arrow != null) return arrow;
        String rainIdle = com.xenowarriors.client.RainIdle.face(p);
        if (rainIdle != null) return rainIdle;
        if (ClientSkillState.currentAnim(p) != null) return "shout";
        String efFace = com.xenowarriors.client.AttackHooks.externalFace.apply(p);
        if (efFace != null) return efFace;
        if (com.xenowarriors.client.ChargePose.weight(p) > 0.5F) {
            return (p.tickCount / 20) % 3 == 0 ? "concentrate" : "shout";   // gritting, then yelling as it builds
        }
        if (p.swinging || com.xenowarriors.client.mikiko.MikikoAnimations.attacking(p)
                || com.xenowarriors.client.RhalixCombatAnim.attacking(p) || com.xenowarriors.client.SurfPose.attacking(p)) return "fierce";
        if (p.isUsingItem() && p.getUseItem().getUseAnimation() == net.minecraft.world.item.UseAnim.EAT
                || p.isUsingItem() && p.getUseItem().getUseAnimation() == net.minecraft.world.item.UseAnim.DRINK) {
            return (p.getTicksUsingItem() / 4) % 2 == 0 ? "eat" : "eat2";      // munching
        }
        if (type == CharacterType.RHALIX) {
            String hungry = com.xenowarriors.client.RhalixHungerClient.face(p);
            if (hungry != null) return hungry;
        }
        if (type == CharacterType.MIKIKO && com.xenowarriors.client.mikiko.MikikoAnimations.exhausted(p)) {
            // worn out: droopy eyes, sweat, panting
            return (p.tickCount / 7) % 2 == 0 ? "exhausted" : "exhausted2";
        }
        if (p.getHealth() <= p.getMaxHealth() * 0.3F && p.isAlive())
            return com.xenowarriors.client.Surroundings.of(p) == com.xenowarriors.client.Surroundings.ENEMIES ? "determined" : "strained";   // not giving up
        if (ClientSkillState.hungerEffect(p)) {
            // the Hunger effect: queasy and starving - drooling, the odd green-faced wince
            int ht = Math.floorMod(p.tickCount + p.getId() * 17, 120);
            if (ht < 3) return "blink";
            return ht >= 90 && ht < 108 ? "sick" : (ht / 12) % 2 == 0 ? "hungry" : "hungry2";
        }
        if (ClientSkillState.feelingSick(p)) return "sick";
        if (p == net.minecraft.client.Minecraft.getInstance().player && com.xenowarriors.client.StatToasts.justLeveledUp()) return "sparkle";
        String thunder = com.xenowarriors.client.ThunderFaces.face(p, type);
        if (thunder != null) return thunder;
        String stance = com.xenowarriors.client.AttackHooks.externalStanceFace.apply(p);
        if (stance != null) return stance;
        int around = com.xenowarriors.client.Surroundings.of(p);
        if (around == com.xenowarriors.client.Surroundings.ENEMIES) {
            // enemies close by: eyes narrowed, ready to fight
            int rt = Math.floorMod(p.tickCount + p.getId() * 29, 160);
            if (rt < 3) return "blink";
            if (type == CharacterType.RHALIX) return rt < 120 ? "focused" : "smug";        // cocky about it
            return rt < 110 ? "focused" : "concentrate";
        }
        var pos = p.blockPosition();
        boolean snowing = p.level().isRaining() && p.level().canSeeSky(pos) && p.level().getBiome(pos).value().coldEnoughToSnow(pos);
        if (p.getTicksFrozen() > 0 || snowing) return "freezing";
        if (p.level().isRainingAt(pos.above()) && !p.isUnderWater() && !com.xenowarriors.client.mikiko.HoodClient.wearingHoodie(p) && !com.xenowarriors.forms.Umbrella.covers(p)) {
            // caught in the rain: soaked, drops running down the face (two frames so they drip)
            int bt = p.tickCount + p.getId() * 37;
            if (bt % 97 < 3) return "blink";
            int f = (bt / 7) % 3;                       // the water running down the face
            return f == 0 ? "rain" : f == 1 ? "rain2" : "rain3";
        }
        String mood = type == CharacterType.MIKIKO ? com.xenowarriors.client.mikiko.MikikoAnimations.mood(p) : null;
        if (type == CharacterType.RHALIX) {
            if (com.xenowarriors.client.RhalixWingsClient.hyped(p, 0F) > 0.5F) {
                String[] h = {"headbang", "grin", "sing", "excited", "headbang", "wink"};
                return h[(p.tickCount / 20) % h.length];
            }
            if (com.xenowarriors.client.RhalixWingsClient.sadVibe(p, 0F) > 0.5F) {
                String[] l = {"sad", "melancholy", "sad", "melancholy", "crying"};
                return l[(p.tickCount / 60) % l.length];
            }
            if (com.xenowarriors.client.RhalixWingsClient.chill(p, 0F) > 0.5F) {
                String[] c = {"calm", "smug", "whistle", "hum", "calm"};
                return c[(p.tickCount / 50) % c.length];
            }
            if (com.xenowarriors.client.RhalixWingsClient.smoking(p, 0F) > 0.5F) {
                float st = com.xenowarriors.client.RhalixWingsClient.smokeTime(p, p.tickCount);
                return st >= 56 && st < 84 ? "hum" : "calm";      // eyes closed, breathing out
            }
            if (com.xenowarriors.client.RhalixWingsClient.gravityDance(p, 0F) > 0.5F) {
                // glitching out during the stutter-snaps, otherwise smug and loving it
                float gt = com.xenowarriors.client.RhalixWingsClient.gravityTime(p, p.tickCount) % 160F;
                if (gt >= 40F && gt < 66F) return "glitch";
                String[] g = {"smug", "grin", "cool", "wink", "excited"};
                return g[(p.tickCount / 40) % g.length];
            }
            if (FlightTracker.isFlying(p)) mood = "smug";          // right at home in the air
            else if (p.isSprinting()) mood = "focused";
        }
        if (mood == null && around == com.xenowarriors.client.Surroundings.PEOPLE && type == CharacterType.MIKIKO) {
            String[] h = {"happy", "grin", "happy", "excited", "happy", "wink"};        // glad to have company
            mood = h[Math.floorMod((p.tickCount + p.getId() * 41) / 60, h.length)];   // (floorMod: stand-in players have negative ids)
        } else if (mood == null && around != com.xenowarriors.client.Surroundings.NONE) {
            String[] c = {"calm", "hum", "calm", "cool", "whistle", "smirk", "calm"};  // nothing to worry about: chilling
            mood = c[Math.floorMod((p.tickCount + p.getId() * 41) / 70, c.length)];
        }
        // the people they care about: Yuri blushes around Mikiko (her crush), Max and Technica (married) light up together
        if (mood == null) {
            String heart = com.xenowarriors.client.GarryBeatbox.closeTo(p, type == CharacterType.YURI ? CharacterType.MIKIKO
                    : type == CharacterType.MAX ? CharacterType.TECHNICA : type == CharacterType.TECHNICA ? CharacterType.MAX : null, 6) ? (type == CharacterType.YURI ? "blush" : "love") : null;
            if (heart != null && Math.floorMod(p.tickCount + p.getId() * 13, 240) < 70) mood = heart;
        }
        int bt = p.tickCount + p.getId() * 37;
        boolean blink = bt % 97 < 3 || (bt % 233 >= 8 && bt % 233 < 11);
        if (mood != null) {
            // moods with open eyes still blink now and then
            boolean eyesOpen = !mood.equals("happy") && !mood.equals("yawn") && !mood.equals("robot");
            return blink && eyesOpen ? "blink" : mood;
        }
        int t = p.tickCount + p.getId() * 37;
        if (t % 97 < 3 || (t % 233 >= 8 && t % 233 < 11)) return "blink";   // blinks every few seconds, sometimes twice
        return idleFace(p);
    }

    /**
     * Neutral faces so they don't stare straight ahead all the time: glancing around, looking up, thinking,
     * calm, humming to themselves - about half the time it's just the normal face.
     */
    private static String idleFace(AbstractClientPlayer p) {
        int seg = (p.tickCount + p.getId() * 53) / 70;
        long h = (seg * 2654435761L) ^ (p.getId() * 40503L);
        int pick = (int) Math.floorMod(h >>> 5, 20);
        boolean still = Math.abs(p.getX() - p.xo) + Math.abs(p.getZ() - p.zo) < 0.05;
        return switch (pick) {
            case 10, 11 -> "glance_l";
            case 12, 13 -> "glance_r";
            case 14 -> "look_up";
            case 15 -> still ? "thinking" : null;
            case 16, 17 -> "calm";
            case 18 -> still ? "hum" : null;
            default -> null;
        };
    }

    private static final java.util.Map<Integer, float[]> HAIR = com.xenowarriors.client.ClientReset.track(new java.util.HashMap<>());

    /** which = 0: how far the hair is blown back, 1: how much it flutters (smoothed so it eases in and out). */
    private static float hairStream(AbstractClientPlayer p, int which) {
        float[] h = HAIR.computeIfAbsent(p.getId(), k -> new float[3]);
        if (which == 0) {
            double dx = p.getX() - p.xo, dz = p.getZ() - p.zo;
            float speed = (float) Math.min(1.0, Math.sqrt(dx * dx + dz * dz) / 0.4);
            boolean flying = FlightTracker.isFlying(p) || p.isFallFlying();
            boolean air = !p.onGround() && !p.isInWater();
            float stream = speed * (flying ? 0.65F : p.isSprinting() ? 0.38F : 0.14F) + (air && !flying ? 0.08F : 0F);
            float charge = com.xenowarriors.client.ChargePose.weight(p);
            if (charge > 0F) stream = Math.max(stream, 0.3F * charge);          // the aura lifts it
            float flutter = (flying ? 0.15F : p.isSprinting() ? 0.1F : 0.035F) * Math.min(1F, speed * 1.4F) + (air ? 0.03F : 0F);
            if (charge > 0F) flutter = Math.max(flutter, 0.14F * charge);
            h[0] += (stream - h[0]) * 0.12F;
            h[1] += (flutter - h[1]) * 0.12F;
            return h[0];
        }
        return h[1];
    }

    /** per player: {style weight 0..1, last age, style shown} - the hair rises when transforming and falls back after */
    private static final java.util.Map<Integer, float[]> SSJ = com.xenowarriors.client.ClientReset.track(new java.util.HashMap<>());

    private static float hairStyleWeight(AbstractClientPlayer p, int style, float age) {
        float[] s = SSJ.computeIfAbsent(p.getId(), k -> new float[]{0F, age, 0F});
        float dt = net.minecraft.util.Mth.clamp(age - s[1], 0F, 5F);
        s[1] = age;
        if (style > 0 && style != (int) s[2] && s[0] > 0.05F) {
            s[0] = Math.max(0F, s[0] - dt * 0.15F);          // changing style: settle first, then rise into the new one
            if (s[0] <= 0.05F) s[2] = style;
        } else {
            if (style > 0) s[2] = style;
            float target = style > 0 ? 1F : 0F;
            s[0] = net.minecraft.util.Mth.clamp(s[0] + Math.signum(target - s[0]) * dt * (style > 0 ? 0.12F : 0.08F), 0F, 1F);
        }
        if (s[0] <= 0F && style == 0) s[2] = 0F;
        return s[0];
    }

    private static int lastStyle(AbstractClientPlayer p) {
        float[] s = SSJ.get(p.getId());
        return s == null ? 0 : (int) s[2];
    }

    private static final java.util.Map<java.util.UUID, Integer> TORN = com.xenowarriors.client.ClientReset.track(new java.util.concurrent.ConcurrentHashMap<>());

    public static void setTorn(java.util.UUID player, int torn) {
        if (torn <= 0) TORN.remove(player);
        else TORN.put(player, torn);
    }

    /** 0 = fine, 1 = hurt (half health or less), 2 = badly hurt (a quarter or less). */
    private static int damageLevel(AbstractClientPlayer p) {
        if (!p.isAlive() || p.isCreative()) return 0;
        float hp = p.getHealth() / Math.max(1F, p.getMaxHealth());
        return hp <= 0.25F ? 2 : hp <= 0.5F ? 1 : 0;
    }

    private static final java.util.Set<String> OWN_MOUTH = java.util.Set.of("sleep", "snore", "eat", "eat2", "shout", "hurt", "yawn", "panic", "wince", "hurt_heavy");

    /** 0 = not talking, otherwise the mouth shape (1-5) following the voice. */
    private static int talkingMouth(AbstractClientPlayer p, String expr) {
        if (expr != null && OWN_MOUTH.contains(expr)) return 0;
        int mouth = com.xenowarriors.voice.VoiceLevels.mouth(p.getUUID());
        if (p == net.minecraft.client.Minecraft.getInstance().player) {
            mouth = Math.max(mouth, com.xenowarriors.voice.VoiceLevels.mouth(com.xenowarriors.voice.VoiceLevels.LOCAL));
        }
        // just sent a chat line: the mouth flaps for a moment (as the HUD portrait always did; now the model does too)
        if (mouth == 0 && com.xenowarriors.client.HudFaces.talking(p.getUUID())) mouth = (com.xenowarriors.client.HudFaces.clock() / 4) % 2 == 0 ? 3 : 0;
        return mouth;
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffers, int packedLight, AbstractClientPlayer player,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        if (player.isInvisible()) return;
        CharacterType type = ClientCharacterData.get(player);
        DetailModel model = models.get(type);
        boolean king = isFoxKing(player, type);
        if (king) model = foxKing;
        int outfit = king ? 0 : ClientCharacterData.outfit(player);
        DetailModel om = outfit > 0 ? outfitModel(player, type) : null;
        if (om != null) model = om;
        else outfit = 0;
        if (shiyokenOutfit(player, type) && shiyokenModel() != null) { model = shiyokenModel(); outfit = 1; }   // Shiyoken: his fire outfit (over any outfit)
        var fusionModel = com.xenowarriors.client.FusionLook.of(player);
        if (fusionModel != null && fusionModel.model() != null) { model = fusionModel.model(); outfit = 0; king = false; }   // a fusion: its own model
        if (model == null) return;

        PlayerModel<AbstractClientPlayer> parent = getParentModel();
        model.head.copyFrom(parent.head);
        model.body.copyFrom(parent.body);
        model.rightArm.copyFrom(parent.rightArm);
        model.leftArm.copyFrom(parent.leftArm);
        model.rightLeg.copyFrom(parent.rightLeg);
        model.leftLeg.copyFrom(parent.leftLeg);
        // elbows and knees bend with the player's
        com.xenowarriors.client.LimbBend.copy(parent.rightArm, model.rightArm);
        com.xenowarriors.client.LimbBend.copy(parent.leftArm, model.leftArm);
        com.xenowarriors.client.LimbBend.copy(parent.rightLeg, model.rightLeg);
        com.xenowarriors.client.LimbBend.copy(parent.leftLeg, model.leftLeg);

        if (model.hasWings()) {
            boolean preview = ClientCharacterData.isPreviewing();
            boolean flying = FlightTracker.isFlying(player);
            boolean falling = !player.onGround() && !player.isInWater() && player.fallDistance > 1.5F;
            boolean kishi = ClientSkillState.isCastingKishi(player.getUUID());
            float spread = Math.max(com.xenowarriors.client.SkillAnimations.wingSpread(player, ageInTicks),
                    com.xenowarriors.client.KarmaFocusClient.wingSpread(player));       // spread wide while floating, sensing
            com.xenowarriors.client.RhalixWingsClient.pose(model, player, preview, flying, falling, kishi, spread, ageInTicks);
        }

        // hood up (SR hoodie): the hair tucks under it, only the front bangs show
        boolean hood = model.hoodUp != null && com.xenowarriors.client.mikiko.HoodClient.up(player);
        boolean armorHood = com.xenowarriors.client.mikiko.HoodClient.karmaHoodie(player) && com.xenowarriors.client.mikiko.HoodClient.up(player);
        if (model.hoodUp != null) model.hoodUp.visible = hood;
        if (model.glasses != null) model.glasses.visible = com.xenowarriors.client.YuriLaptopIdle.glasses(player);
        if (model.goggles != null) { boolean dn = com.xenowarriors.client.TechGoggles.down(player); model.goggles.y = dn ? 2.9F : 0F; model.goggles.z = dn ? -0.15F : 0F; }
        if (model.hoodDown != null) model.hoodDown.visible = !hood;
        if (model.hair != null) {
            // the outfit's own hood: his hair still shows under it (the strands that would poke through it are tucked in)
            model.hair.visible = !(player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).getItem()
                    instanceof com.xenowarriors.item.SrHoodieItem)
                    && !(player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).getItem() instanceof com.xenowarriors.item.FoxPjsItem);   // (under the Fox PJs hood)
            for (net.minecraft.client.model.geom.ModelPart strand : model.hoodPokers) strand.visible = !hood && !armorHood;
        } else {
            for (net.minecraft.client.model.geom.ModelPart strand : model.hoodPokers) strand.visible = !hood && !armorHood;   // (Kaeru: her bob's outer locks)
        }

        // Rhalix's cigarette while he's on his smoke break
        if (type == CharacterType.RHALIX && com.xenowarriors.client.RhalixWingsClient.smoking(player, partialTick) > 0.3F) {
            float st = com.xenowarriors.client.RhalixWingsClient.smokeTime(player, ageInTicks);
            if (st >= 34F && st < 52F) Cigarette.renderInMouth(parent.head, poseStack, buffers, packedLight);   // taking a drag
            else Cigarette.render(parent.rightArm, poseStack, buffers, packedLight);
        }

        // Rhalix's Origins outfits: the book he summons and reads
        if (type == CharacterType.RHALIX) com.xenowarriors.client.RhalixBookIdle.render(player, poseStack, buffers, packedLight, partialTick);
        if (type == CharacterType.YURI) com.xenowarriors.client.YuriLaptopIdle.render(player, poseStack, buffers, packedLight, partialTick);
        if (type == CharacterType.GARRY || type == CharacterType.STEVIE) com.xenowarriors.client.GarryBeatbox.render(player, parent, poseStack, buffers, packedLight, partialTick);

        // Rhalix sharpening his sword: a whetstone in his other hand
        if (type == CharacterType.RHALIX && com.xenowarriors.client.RhalixCombatAnim.sharpening(player)) {
            poseStack.pushPose();
            parent.translateToHand(net.minecraft.world.entity.HumanoidArm.LEFT, poseStack);
            poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-90F));
            poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180F));
            poseStack.translate(-1F / 16F, 0.125F, -0.625F);
            poseStack.scale(0.6F, 0.6F, 0.6F);
            net.minecraft.client.Minecraft.getInstance().getItemRenderer().renderStatic(player,
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.FLINT),
                    net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_LEFT_HAND, true, poseStack, buffers,
                    player.level(), packedLight, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, player.getId());
            poseStack.popPose();
        }

        // hair streaming back and fluttering when running or flying
        // (far away you can't see it anyway: skip the work)
        boolean nearCam = net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().getPosition()
                .distanceToSqr(player.position()) < 32 * 32;
        float windS = nearCam ? hairStream(player, 0) : 0F, windF = nearCam ? hairStream(player, 1) : 0F;
        if (model.coatBack != null) {
            model.animateCoat(parent.rightLeg.xRot, parent.leftLeg.xRot, parent.rightLeg.zRot, -parent.leftLeg.zRot, windS * 0.9F, windF * 1.3F, ageInTicks,
                    player.isPassenger() || parent.riding);
            model.untwistCoat(parent.body.yRot);
        }
        int ssj = com.xenowarriors.client.compat.DmzForms.hairStyle(player);
        float ssjWeight = hairStyleWeight(player, ssj, ageInTicks);
        int shownStyle = lastStyle(player);
        model.animateHair(windS, windF, ageInTicks, shownStyle, ssjWeight);

        // your own head in first person (camera inside it): no hair, goggles or face
        boolean noHead = com.xenowarriors.client.FirstPersonHead.hidden() && player == net.minecraft.client.Minecraft.getInstance().player;
        model.head.visible = !noHead;
        int overlay = LivingEntityRenderer.getOverlayCoords(player, 0.0F);
        // torn clothes (explosions): holes ripped in the clothing, skin showing through, strips hanging off
        int torn = ClientCharacterData.isPreviewing() || outfit > 0 ? 0 : TORN.getOrDefault(player.getUUID(), 0);
        var detailTex = torn > 0 ? com.xenowarriors.XenoWarriors.id("textures/entity/character/" + type.getId() + "_details_torn" + torn + ".png")
                : type.getDetailTexture();
        if (king) detailTex = FOX_KING_TEX;
        if (outfit == 0 && torn == 0 && type == CharacterType.RHALIX && com.xenowarriors.client.LunarClient.active(player.getId())) detailTex = LUNAR_TEX;
        if (outfit > 0) detailTex = detailTexture(player);      // its own atlas (the torn/damaged detail sheets are for the normal look)
        var fusionLook = com.xenowarriors.client.FusionLook.of(player);
        if (fusionLook != null) detailTex = com.xenowarriors.client.cel.Cel.tex(fusionLook.details());
        detailTex = com.xenowarriors.client.cel.Cel.tex(detailTex);
        VertexConsumer buffer = buffers.getBuffer(RenderType.entityCutoutNoCull(detailTex));
        // Rhalix's mouth stick: in his hand or pocket during his fidget
        int stick = type == CharacterType.RHALIX && !model.mouthStick.isEmpty()
                ? com.xenowarriors.client.RhalixCombatAnim.stickState(player, partialTick) : 0;
        if (stick != 0) for (var sp : model.mouthStick) sp.visible = false;
        // wearing another mod's outfit (DragonMineZ gis): his own clothes under it are left off, so nothing clips through
        boolean giTop = CharacterArmorLayer.foreignWorn(player, net.minecraft.world.entity.EquipmentSlot.CHEST);
        boolean giLegs = CharacterArmorLayer.foreignWorn(player, net.minecraft.world.entity.EquipmentSlot.LEGS);
        boolean ssj4Top = com.xenowarriors.client.compat.DmzForms.ssj4Shirtless(player);   // Super Saiyan 4: shirt / coat off
        if (giTop || giLegs || ssj4Top) model.hideOutfit(giTop || ssj4Top, giTop || ssj4Top, giLegs);
        if (com.xenowarriors.client.kaeru.ChangeClient.silhouette(player.getId())) {      // Kaeru's Change: a dark silhouette, glowing white eyes
            model.root.render(poseStack, buffer, packedLight, overlay, 0.05F, 0.05F, 0.06F, 1F);
            if (model.faceRoot != null) {
                model.faceHead.copyFrom(parent.head);
                model.faceRoot.render(poseStack, buffers.getBuffer(RenderType.entityCutoutNoCull(com.xenowarriors.client.kaeru.ChangeClient.SIL_FACE)), 0xF000F0, overlay);   // eyes at full brightness
            }
            return;
        }
        model.root.render(poseStack, buffer, packedLight, overlay);
        if (com.xenowarriors.client.compat.DmzForms.ssj4Own(player)) ssj4Fur(parent, poseStack, buffers, packedLight, overlay);   // Super Saiyan 4: the red fur
        if (com.xenowarriors.client.cel.Cel.outline() && !noHead) {                 // cel-shading: the ink outline around him
            com.xenowarriors.client.cel.CelOutline.body(parent, poseStack, buffers, packedLight, overlay);
            com.xenowarriors.client.cel.CelOutline.details(model, poseStack, buffers, packedLight, overlay);
        }
        if (detailTex == LUNAR_TEX || detailTex.getPath().endsWith("rhalix_lunar_details_cel.png")) model.root.render(poseStack, buffers.getBuffer(RenderType.eyes(LUNAR_GLOW)), packedLight, overlay);   // the moons' purple glow
        if (giTop || giLegs || ssj4Top) model.showOutfit();
        if (stick != 0) for (var sp : model.mouthStick) sp.visible = true;
        if (stick == 1) {
            poseStack.pushPose();
            model.root.translateAndRotate(poseStack);
            parent.rightArm.translateAndRotate(poseStack);
            com.xenowarriors.client.LimbBend.toForearm(parent.rightArm, poseStack);
            poseStack.translate(-1F / 16F, 10.2F / 16F, -0.5F / 16F);           // between his fingers
            poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(90F));    // pointing ahead
            for (var sp : model.mouthStick) {
                float x = sp.x, y = sp.y, z = sp.z;
                sp.x = 0F; sp.y = 0F; sp.z = 0F;
                sp.render(poseStack, buffer, packedLight, overlay);
                sp.x = x; sp.y = y; sp.z = z;
            }
            poseStack.popPose();
        }
        // DragonMineZ forms (Super Saiyan gold...): the hair drawn again on top in the form's color
        int formHair = com.xenowarriors.client.compat.DmzForms.hairColor(player);
        if (fusionLook != null) formHair = -1;                                              // (a fusion keeps its own hair colours)
        if (formHair >= 0 && model.hair == null && (type == CharacterType.KYRIE || type == CharacterType.YURI)) {   // (Kyrie's hair is the head's own strands, Yuri's bob the head's own cubes: the whole head with the mask, only the hair shows)
            float hr = ((formHair >> 16) & 255) / 255F, hg = ((formHair >> 8) & 255) / 255F, hb = (formHair & 255) / 255F;
            String mask = type == CharacterType.KYRIE ? (outfit > 0 ? "kyrie_s2_details_hairmask" : "kyrie_details_hairmask") : outfit > 0 ? "yuri_c1pjs_details_hairmask" : "yuri_details_hairmask";
            poseStack.pushPose();
            model.root.translateAndRotate(poseStack);
            if (type == CharacterType.YURI) { poseStack.pushPose(); poseStack.scale(1.025F, 1.02F, 1.025F); }   // (a hair over her own bob, so the tint always shows - no flicker with the black under it)
            model.head.render(poseStack, buffers.getBuffer(RenderType.entityCutoutNoCull(com.xenowarriors.XenoWarriors.id("textures/entity/character/" + mask + ".png"))),
                    packedLight, overlay, hr, hg, hb, 1F);
            if (type == CharacterType.YURI) poseStack.popPose();
            if (type == CharacterType.YURI && !noHead) {                            // Yuri: her Super Saiyan hair over the bob, styled for the form
                DetailModel sh = yuriSsjHair();
                if (sh != null && sh.hair != null) {
                    sh.animateHair(windS, windF, ageInTicks, shownStyle, ssjWeight);
                    model.head.translateAndRotate(poseStack);
                    sh.hair.render(poseStack, buffers.getBuffer(RenderType.entityCutoutNoCull(YURI_SSJ_TEX)), packedLight, overlay, hr, hg, hb, 1F);
                }
            }
            poseStack.popPose();
        }
        if (formHair >= 0 && model.hair != null) {
            String atlas = king || (outfit > 0 && foxActive(player, type)) ? "mikiko_foxking" : type.getId();
            VertexConsumer hairBuf = buffers.getBuffer(RenderType.entityCutoutNoCull(com.xenowarriors.XenoWarriors.id(
                    "textures/entity/character/" + atlas + "_details_hairmask.png")));
            float hr = ((formHair >> 16) & 255) / 255F, hg = ((formHair >> 8) & 255) / 255F, hb = (formHair & 255) / 255F;
            poseStack.pushPose();
            model.root.translateAndRotate(poseStack);
            model.head.translateAndRotate(poseStack);
            model.hair.render(poseStack, hairBuf, packedLight, overlay, hr, hg, hb, 1F);
            if (model.bangs != null) model.bangs.render(poseStack, hairBuf, packedLight, overlay, hr, hg, hb, 1F);
            poseStack.popPose();
        }
        if (torn > 0) {
            parent.renderToBuffer(poseStack, buffers.getBuffer(RenderType.entityCutoutNoCull(com.xenowarriors.XenoWarriors.id(
                    "textures/entity/character/" + type.getId() + "_torn" + torn + ".png"))), packedLight, overlay, 1F, 1F, 1F, 1F);
            BattleDamageModel.get().renderCloth(parent, poseStack, buffers, packedLight, overlay, Math.min(2, torn),
                    clothColor(type));
        }

        // battle damage: scratches, grime and torn clothes as they get hurt
        int damage = damageLevel(player);
        if (damage > 0 && !ClientCharacterData.isPreviewing()) {
            String base = "textures/entity/character/" + type.getId();
            if (outfit == 0) parent.renderToBuffer(poseStack, buffers.getBuffer(RenderType.entityTranslucent(   // (painted for his normal clothes)
                    com.xenowarriors.XenoWarriors.id(base + "_damage" + damage + ".png"))), packedLight, overlay, 1F, 1F, 1F, 1F);
            if (!king && outfit == 0) model.root.render(poseStack, buffers.getBuffer(RenderType.entityTranslucent(
                    com.xenowarriors.XenoWarriors.id(base + "_details_damage" + damage + ".png"))), packedLight, overlay);
            // 3D: bandages and torn, hanging cloth
            BattleDamageModel.get().render(parent, poseStack, buffers, packedLight, overlay, damage,
                    clothColor(type));
        }

        if (model.faceRoot != null && !noHead) {
            model.faceHead.copyFrom(parent.head);
            FaceFrame ff = faceFrame(player, FRAME);       // (the same face the HUD portrait shows)
            if (recordFaces) RENDERED.put(player.getId(), ff.face + "|" + ff.damage + "|" + ff.mouth);
            VertexConsumer faceBuffer = buffers.getBuffer(RenderType.entityCutoutNoCull(ff.face));
            renderFace(model, parent, player, poseStack, faceBuffer, packedLight, overlay);

            if (ff.damage != null)
                renderFace(model, parent, player, poseStack, buffers.getBuffer(RenderType.entityTranslucent(ff.damage)), packedLight, overlay);

            // talking in voice chat: an animated mouth drawn over the face
            if (ff.mouth != null)
                renderFace(model, parent, player, poseStack, buffers.getBuffer(RenderType.entityCutoutNoCull(ff.mouth)), packedLight, overlay);
        }
    }

    /** UI test: the face each model last drew (entity id -> "face|damage|mouth"), recorded only while this is on. */
    public static boolean recordFaces;
    public static final java.util.Map<Integer, String> RENDERED = com.xenowarriors.client.ClientReset.track(new java.util.HashMap<>());

    /** Reused for the model's own face each frame (render thread only). */
    private static final FaceFrame FRAME = new FaceFrame();

    /** The face (or a layer over it) on the head. */
    /** DragonMineZ's Super Saiyan 4 fur pattern (body and arms of the skin layout), tinted SSJ4 red. */
    private static final net.minecraft.resources.ResourceLocation SSJ4_FUR = new net.minecraft.resources.ResourceLocation("dragonminez", "textures/entity/races/humansaiyan/ssj4gt_layer1.png");
    public static final float[] FUR = {0.66F, 0.13F, 0.16F};
    /** (Tests) times the fur was drawn. */
    public static int furDraws;

    /**
     * Super Saiyan 4 fur on Mikiko, Rhalix, Kyrie and Yuri: laid right on the skin of the body and arms (a decal on the
     * same surface), so it shows wherever skin does - all over Mikiko's and Rhalix's bare top, and on the girls' arms
     * and anywhere their clothes leave bare; their clothes stay over it.
     */
    private static void ssj4Fur(PlayerModel<AbstractClientPlayer> parent, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        VertexConsumer vc = buffers.getBuffer(RenderType.entityDecal(SSJ4_FUR));
        for (var part : new net.minecraft.client.model.geom.ModelPart[]{parent.body, parent.rightArm, parent.leftArm})
            if (part.visible) part.render(pose, vc, light, overlay, FUR[0], FUR[1], FUR[2], 1F);
        furDraws++;
    }

    private static void renderFace(DetailModel model, PlayerModel<AbstractClientPlayer> parent, AbstractClientPlayer player, PoseStack pose,
                                   VertexConsumer vc, int light, int overlay) {
        model.faceRoot.render(pose, vc, light, overlay);
    }
}

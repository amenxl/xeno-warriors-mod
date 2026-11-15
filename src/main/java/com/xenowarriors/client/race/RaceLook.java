package com.xenowarriors.client.race;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.client.ClientCharacterData;
import com.xenowarriors.client.compat.DmzForms;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * Our characters as another DragonMineZ race: picking Namekian, Majin, Frost Demon, Bio-Android or Glind in DragonMineZ's
 * character maker gives the character that race's look - their skin (body and face, every expression, outfit and form)
 * recoloured to the race's body colour (the one picked in the maker), and the race's parts on their own head:
 * Namekian antennae and pointed ears, the Majin head tentacle, Frost Demon horns and head gem, the Bio-Android crest
 * and spots, Glind horns. Saiyans get a tail round the waist. Humans look as they always did.
 */
public final class RaceLook {
    private RaceLook() {}

    public static final String NAMEK = "namekian", MAJIN = "majin", FROST = "frostdemon", BIO = "bioandroid", GLIND = "glind", SAIYAN = "saiyan";
    private static final ResourceLocation WHITE = XenoWarriors.id("textures/misc/xw_white.png");
    /** (Tests) a race to show on everyone instead of their DragonMineZ one (null = off). */
    public static String testRace;
    public static int tinted, partsDrawn;

    /** This player's race look ("namekian"...), or null for a human (or no DragonMineZ). */
    public static String race(AbstractClientPlayer p) {
        if (testRace != null) return testRace.isEmpty() ? null : testRace;
        CharacterType t = ClientCharacterData.get(p);
        if (t == CharacterType.NONE || t == CharacterType.YOU) return null;
        if (ClientCharacterData.isPreviewing() && p == Minecraft.getInstance().player) return null;
        String r = DmzForms.race(p);
        if (r == null) return null;
        if (r.contains(SAIYAN)) return com.xenowarriors.client.compat.DmzCompat.tail(p) ? SAIYAN : null;   // (the tail when DragonMineZ shows it)
        for (String k : new String[]{NAMEK, MAJIN, FROST, BIO, GLIND}) if (r.contains(k)) return k;
        if (r.contains("namek")) return NAMEK;
        if (r.contains("frost") || r.contains("arcos")) return FROST;
        if (r.contains("android") && r.contains("bio")) return BIO;
        return null;
    }

    /** The skin colour for a race: what they picked in DragonMineZ, else the race's usual one. */
    public static int skinColor(AbstractClientPlayer p, String race) {
        int c = testRace != null ? -1 : DmzForms.bodyColors(p)[0];
        if (c >= 0 && c != 0xFFD3C9) return c;                              // (their pick - not the human default left over)
        return switch (race) {
            case NAMEK -> 0x3FB43A;
            case MAJIN -> 0xFFA4E8;
            case FROST -> 0xF4F4FA;
            case BIO -> 0x4E8A22;
            case GLIND -> 0xD2A3DA;
            default -> -1;
        };
    }

    private static int accent(AbstractClientPlayer p, String race) {
        int c = testRace != null ? -1 : DmzForms.bodyColors(p)[2];
        if (c >= 0) return c;
        return switch (race) {
            case FROST -> 0xB040E0;
            case BIO -> 0xC7B022;
            case NAMEK -> 0xFF86A6;
            default -> 0x704050;
        };
    }

    // ------------------------------------------------------------------ the skin, recoloured

    private static final Map<String, ResourceLocation> TINTED = new HashMap<>();
    private static final Map<CharacterType, int[]> REF = new HashMap<>();

    /** The character's skin tone (the commonest colour on their plain face). */
    private static int[] ref(CharacterType t) {
        return REF.computeIfAbsent(t, k -> {
            try (NativeImage im = load(XenoWarriors.id("textures/entity/character/" + k.getId() + "_face.png"))) {
                if (im == null) return null;
                Map<Integer, Integer> count = new HashMap<>();
                for (int y = 2; y < 30; y++) for (int x = 2; x < 30; x++) {
                    int c = im.getPixelRGBA(x, y);
                    if ((c >>> 24) < 200) continue;
                    count.merge(c & 0xFFFFFF, 1, Integer::sum);
                }
                int best = -1, n = 0;
                for (var e : count.entrySet()) if (e.getValue() > n) { n = e.getValue(); best = e.getKey(); }
                if (best < 0) return null;
                return new int[]{best & 255, (best >> 8) & 255, (best >> 16) & 255};    // (NativeImage is ABGR: r, g, b)
            } catch (Exception e) {
                return null;
            }
        });
    }

    private static NativeImage load(ResourceLocation rl) {
        var res = Minecraft.getInstance().getResourceManager().getResource(rl);
        if (res.isEmpty()) return null;
        try (var in = res.get().open()) { return NativeImage.read(in); } catch (Exception e) { return null; }
    }

    /**
     * {@code tex} with the character's skin recoloured for their race (a cached copy made once), or {@code tex} itself
     * when they're human / it can't be done.
     */
    public static ResourceLocation tint(ResourceLocation tex, AbstractClientPlayer p, CharacterType type) {
        if (tex == null) return null;
        String race = race(p);
        if (race == null || SAIYAN.equals(race)) return tex;
        int col = skinColor(p, race);
        if (col < 0) return tex;
        String key = tex + "|" + race + "|" + Integer.toHexString(col) + "|" + type.getId();
        ResourceLocation done = TINTED.get(key);
        if (done != null) return done;
        int[] r = ref(type);
        ResourceLocation out = tex;
        if (r != null) {
            try (NativeImage src = load(tex)) {
                if (src != null) {
                    NativeImage im = new NativeImage(src.getWidth(), src.getHeight(), true);
                    im.copyFrom(src);
                    recolor(im, r, col, race);
                    out = XenoWarriors.id("race/" + Integer.toHexString(key.hashCode()));
                    Minecraft.getInstance().getTextureManager().register(out, new DynamicTexture(im));
                    tinted++;
                }
            } catch (Exception e) {
                XenoWarriors.LOGGER.warn("Couldn't recolour {} for the {} race", tex, race, e);
            }
        }
        TINTED.put(key, out);
        return out;
    }

    /** Resource packs reloaded: make the copies again. */
    public static void clear() { TINTED.clear(); REF.clear(); }

    private static void recolor(NativeImage im, int[] ref, int col, String race) {
        float rl = lum(ref[0], ref[1], ref[2]);
        float[] rn = {ref[0] / Math.max(1F, rl), ref[1] / Math.max(1F, rl), ref[2] / Math.max(1F, rl)};
        int cr = (col >> 16) & 255, cg = (col >> 8) & 255, cb = col & 255;
        for (int y = 0; y < im.getHeight(); y++) for (int x = 0; x < im.getWidth(); x++) {
            int c = im.getPixelRGBA(x, y);
            int a = c >>> 24;
            if (a == 0) continue;
            int r = c & 255, g = (c >> 8) & 255, b = (c >> 16) & 255;
            float l = lum(r, g, b);
            if (l < 30) continue;
            float k = l / Math.max(1F, rl);
            if (k < 0.55F || k > 1.3F) continue;
            float d = Math.abs(r / l - rn[0]) + Math.abs(g / l - rn[1]) + Math.abs(b / l - rn[2]);
            if (d > 0.22F) continue;                                          // (not skin: clothes, hair, eyes, lips)
            if (BIO.equals(race) && ((x * 7 + y * 13) % 11 == 0 || (x * 5 + y * 3) % 17 == 0)) k *= 0.62F;   // Bio-Android spots
            int nr = Math.min(255, Math.round(cr * k)), ng = Math.min(255, Math.round(cg * k)), nb = Math.min(255, Math.round(cb * k));
            im.setPixelRGBA(x, y, (a << 24) | (nb << 16) | (ng << 8) | nr);
        }
    }

    private static float lum(int r, int g, int b) { return 0.3F * r + 0.59F * g + 0.11F * b; }

    // ------------------------------------------------------------------ the race's parts

    private static ModelPart HEAD_PARTS, TAIL;
    private static final Map<String, ModelPart[]> BY_RACE = new HashMap<>();

    private static void build() {
        if (HEAD_PARTS != null) return;
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        // Namekian: two antennae from the forehead, swept back, with knobs; pointed ears
        PartDefinition namek = root.addOrReplaceChild(NAMEK, CubeListBuilder.create(), PartPose.ZERO);
        for (int s : new int[]{-1, 1}) {
            PartDefinition ant = namek.addOrReplaceChild("ant" + s, CubeListBuilder.create().texOffs(0, 0).addBox(-0.4F, -5.5F, -0.4F, 0.8F, 5.5F, 0.8F),
                    PartPose.offsetAndRotation(s * 1.6F, -8F, -3.0F, -0.45F, 0F, s * 0.22F));
            ant.addOrReplaceChild("knob", CubeListBuilder.create().addBox(-0.6F, -1.1F, -0.6F, 1.2F, 1.2F, 1.2F), PartPose.offset(0F, -5.5F, 0F));
            namek.addOrReplaceChild("ear" + s, CubeListBuilder.create().addBox(-0.5F, -3F, -0.8F, 1F, 3F, 1.6F),
                    PartPose.offsetAndRotation(s * 4.3F, -3.5F, -0.2F, 0F, 0F, s * 0.55F));
        }
        // Majin: the head tentacle, curling down the back
        PartDefinition majin = root.addOrReplaceChild(MAJIN, CubeListBuilder.create(), PartPose.ZERO);
        PartDefinition t1 = majin.addOrReplaceChild("t1", CubeListBuilder.create().addBox(-1.3F, -3.5F, -1.3F, 2.6F, 3.5F, 2.6F), PartPose.offsetAndRotation(0F, -7.6F, 0.8F, 0.7F, 0F, 0F));
        PartDefinition t2 = t1.addOrReplaceChild("t2", CubeListBuilder.create().addBox(-1F, -3.2F, -1F, 2F, 3.2F, 2F), PartPose.offsetAndRotation(0F, -3.3F, 0F, 0.9F, 0F, 0F));
        t2.addOrReplaceChild("t3", CubeListBuilder.create().addBox(-0.7F, -2.6F, -0.7F, 1.4F, 2.6F, 1.4F), PartPose.offsetAndRotation(0F, -3.1F, 0F, 0.9F, 0F, 0F));
        // Frost Demon: horns at the sides of the head, and the gem on top (accent colour)
        PartDefinition frost = root.addOrReplaceChild(FROST, CubeListBuilder.create(), PartPose.ZERO);
        for (int s : new int[]{-1, 1}) {
            PartDefinition h = frost.addOrReplaceChild("horn" + s, CubeListBuilder.create().addBox(-0.7F, -3.2F, -0.7F, 1.4F, 3.2F, 1.4F),
                    PartPose.offsetAndRotation(s * 3.8F, -6.6F, -0.5F, 0F, 0F, s * 0.75F));
            h.addOrReplaceChild("tip", CubeListBuilder.create().addBox(-0.45F, -2.2F, -0.45F, 0.9F, 2.2F, 0.9F), PartPose.offsetAndRotation(0F, -3.1F, 0F, 0F, 0F, -s * 0.5F));
        }
        root.addOrReplaceChild(FROST + "_gem", CubeListBuilder.create().addBox(-2F, -8.9F, -2.5F, 4F, 1F, 4F), PartPose.ZERO);
        // Bio-Android: the crest, two blades sweeping up and back
        PartDefinition bio = root.addOrReplaceChild(BIO, CubeListBuilder.create(), PartPose.ZERO);
        for (int s : new int[]{-1, 1})
            bio.addOrReplaceChild("crest" + s, CubeListBuilder.create().addBox(-0.5F, -6.5F, -1.4F, 1F, 6.5F, 2.8F),
                    PartPose.offsetAndRotation(s * 2.4F, -7.5F, -1.5F, -0.75F, 0F, s * 0.35F));
        // Glind: small swept horns
        PartDefinition glind = root.addOrReplaceChild(GLIND, CubeListBuilder.create(), PartPose.ZERO);
        for (int s : new int[]{-1, 1})
            glind.addOrReplaceChild("horn" + s, CubeListBuilder.create().addBox(-0.5F, -2.6F, -0.5F, 1F, 2.6F, 1F),
                    PartPose.offsetAndRotation(s * 2.6F, -8F, -2.2F, -0.5F, 0F, s * 0.4F));
        // Saiyan: the tail, wrapped round the waist (body space: y 0 neck .. 12 hips)
        PartDefinition tail = root.addOrReplaceChild(SAIYAN, CubeListBuilder.create()
                .addBox(-4.4F, 10.2F, -2.4F, 8.8F, 1.3F, 0.6F)       // front
                .addBox(-4.4F, 10.2F, 1.8F, 8.8F, 1.3F, 0.6F)        // back
                .addBox(-4.6F, 10.2F, -2.4F, 0.6F, 1.3F, 4.8F)       // right side
                .addBox(4.0F, 10.2F, -2.4F, 0.6F, 1.3F, 4.8F)        // left side
                .addBox(2.6F, 10.6F, 2.3F, 1.2F, 2.6F, 0.8F),        // the tip hanging at the back
                PartPose.ZERO);
        ModelPart baked = LayerDefinition.create(mesh, 16, 16).bakeRoot();
        HEAD_PARTS = baked;
        TAIL = baked.getChild(SAIYAN);
        BY_RACE.put(NAMEK, new ModelPart[]{baked.getChild(NAMEK)});
        BY_RACE.put(MAJIN, new ModelPart[]{baked.getChild(MAJIN)});
        BY_RACE.put(FROST, new ModelPart[]{baked.getChild(FROST), baked.getChild(FROST + "_gem")});
        BY_RACE.put(BIO, new ModelPart[]{baked.getChild(BIO)});
        BY_RACE.put(GLIND, new ModelPart[]{baked.getChild(GLIND)});
    }

    /** The race's head parts (null = none), each with its colour (RGB). */
    public static ModelPart[] headParts(String race) { build(); return race == null ? null : BY_RACE.get(race); }

    public static ModelPart tail() { build(); return TAIL; }

    /** The colour of one of a race's head parts. */
    public static int partColor(AbstractClientPlayer p, String race, int i) {
        int skin = skinColor(p, race);
        return switch (race) {
            case FROST -> i == 0 ? 0x2A2830 : accent(p, race);
            case BIO -> darker(skin);
            case GLIND -> 0xF0E8F4;
            default -> skin;
        };
    }

    public static int tailColor(AbstractClientPlayer p) { return 0x5A3A22; }

    private static int darker(int c) {
        return (((c >> 16) & 255) * 70 / 100) << 16 | (((c >> 8) & 255) * 70 / 100) << 8 | (c & 255) * 70 / 100;
    }

    public static RenderType renderType() { return RenderType.entityCutoutNoCull(WHITE); }

    /** Draws a part (already placed on its bone) in a colour. */
    public static void draw(ModelPart part, PoseStack pose, VertexConsumer vc, int light, int overlay, int rgb) {
        part.render(pose, vc, light, overlay, ((rgb >> 16) & 255) / 255F, ((rgb >> 8) & 255) / 255F, (rgb & 255) / 255F, 1F);
        partsDrawn++;
    }

    /** Our renderer: the race's head parts on the head, the Saiyan tail on the body. */
    public static void render(AbstractClientPlayer p, net.minecraft.client.model.PlayerModel<?> parent, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        String race = race(p);
        if (race == null || p.isInvisible()) return;
        VertexConsumer vc = buffers.getBuffer(renderType());
        if (SAIYAN.equals(race)) {
            if (!parent.body.visible) return;
            pose.pushPose();
            parent.body.translateAndRotate(pose);
            draw(tail(), pose, vc, light, overlay, tailColor(p));
            pose.popPose();
            return;
        }
        ModelPart[] parts = headParts(race);
        if (parts == null || !parent.head.visible) return;
        pose.pushPose();
        parent.head.translateAndRotate(pose);
        for (int i = 0; i < parts.length; i++) draw(parts[i], pose, vc, light, overlay, partColor(p, race, i));
        pose.popPose();
    }

    // ------------------------------------------------------------------ transformation hair for everyone

    private static com.xenowarriors.client.boss.SaiyanHairModel FORM_HAIR;
    public static final ResourceLocation FORM_HAIR_TEX = XenoWarriors.id("textures/entity/boss/saiyan_hair.png");
    public static int formHairDraws;

    /**
     * The long hair that grows with a form, for the characters without their own hair system (everyone but Mikiko,
     * Rhalix, Kyrie and Yuri): SSJ3's mane down the back, SSJ4's wild hair - in the form's colour, growing out of their
     * own (recoloured) hair. Null for the other forms (their own hair just changes colour: FormHair). Posed at rest.
     */
    public static com.xenowarriors.client.boss.SaiyanHairModel formHair(AbstractClientPlayer p, boolean ownHair, ModelPart head) {
        CharacterType t = ClientCharacterData.get(p);
        if (ownHair || t == CharacterType.KYRIE || t == CharacterType.YURI || t == CharacterType.NONE || t == CharacterType.YOU) return null;
        if (DmzForms.hairColor(p) < 0) return null;
        int style = DmzForms.hairStyle(p);
        if (style != 3 && style != 4) return null;                         // (SSJ / SSJ2: their own hair just takes the colour - FormHair)
        if (FORM_HAIR == null) FORM_HAIR = new com.xenowarriors.client.boss.SaiyanHairModel(com.xenowarriors.client.boss.SaiyanHairModel.create().bakeRoot());
        FORM_HAIR.maneOnly(style == 3 ? com.xenowarriors.boss.ZombieSaiyanEntity.SSJ3 : com.xenowarriors.boss.ZombieSaiyanEntity.SSJ4);
        return FORM_HAIR;
    }
}

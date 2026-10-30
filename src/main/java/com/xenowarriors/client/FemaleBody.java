package com.xenowarriors.client;

import com.xenowarriors.character.CharacterType;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.LivingEntity;

import java.util.Set;

/**
 * The girls' body, after the DragonMineZ female body: slim 3-pixel arms (3 deep too), a chest piece, a narrower waist
 * (7 wide, 3 deep) and slightly slimmer legs. Girls use the slim player model (their skins are made in the slim layout);
 * the slim mesh gets a chest, waist and bust under the body that only show for them. Their 3D outfits are refit to the
 * same shape when they load (see DetailModel).
 */
public final class FemaleBody {
    private FemaleBody() {}

    /** How far chest logos are brought forward to sit on the bust. */
    public static final float BUST_OUT = 0.65F;
    public static final float WAIST_X = 7F / 8F, WAIST_Z = 0.75F, ARM_Z = 0.75F, ARM_X = 0.75F, LEG_Z = 0.95F;
    /** Where the waist starts and ends on the body (model y, down from the neck). */
    public static final float WAIST_TOP = 6F, WAIST_BOTTOM = 12F;

    private static final Set<CharacterType> GIRLS = Set.of(CharacterType.KAERU, CharacterType.KYRIE, CharacterType.LEXY,
            CharacterType.TECHNICA, CharacterType.AIONI, CharacterType.YURI);
    /** Outfits that are girls' even on a boy (Max and Sam's Royal Capital disguises). */
    private static final Set<String> GIRL_OUTFITS = Set.of("max_royal", "sam_royal");
    /** Detail models (by file name) that get the girls' fit: the girls' own, their outfits, and girl NPCs. */
    private static final Set<String> GIRL_MODELS = new java.util.HashSet<>();
    static {
        for (CharacterType t : GIRLS) GIRL_MODELS.add(t.getId());
        GIRL_MODELS.addAll(GIRL_OUTFITS);
    }

    public static boolean is(CharacterType t, String outfitKey) {
        if (GIRLS.contains(t)) return true;
        return outfitKey != null && !outfitKey.isEmpty() && GIRL_OUTFITS.contains(t.getId() + "_" + outfitKey);
    }

    public static boolean is(AbstractClientPlayer p) {
        CharacterType t = ClientCharacterData.get(p);
        if (t == CharacterType.NONE) return false;
        if (com.xenowarriors.client.kaeru.ChangeClient.silhouette(p.getId())) return false;   // (her Change silhouette is its own shape)
        int o = ClientCharacterData.outfit(p);
        String[] keys = t.outfitKeys();
        return is(t, o > 0 && o < keys.length ? keys[o] : "");
    }

    /** Girl NPCs (by look): townsfolk, the Sky Patrol's Lexy look, and the SR girls. */
    private static final Set<String> GIRL_NPCS = Set.of("woman", "girl", "sky_lexy", "sky_royal", "sr_ava", "sr_ava_demon",
            "sr_persephone", "sr_persephone_demon", "sr_maddie");
    /** Set while a girl NPC is drawn. */
    public static boolean npcGirl;

    public static boolean npc(String look) {
        if (look.startsWith("dark_")) return false;
        return GIRL_NPCS.contains(look);
    }

    /** Should the detail model at this path be refit to the girls' body? */
    public static boolean model(String path) {
        int s = path.lastIndexOf('/');
        String name = path.substring(s + 1).replace(".json", "");
        if (name.endsWith("_cel")) name = name.substring(0, name.length() - 4);
        if (GIRL_MODELS.contains(name)) return true;
        if (path.contains("/detail/npc/")) return GIRL_NPCS.contains(name);
        for (CharacterType t : GIRLS) if (name.startsWith(t.getId() + "_")) return true;   // her outfits
        return false;
    }

    // ------------------------------------------------------------------ the slim player mesh's extra parts

    public static final String CHEST = "xw_chest", WAIST = "xw_waist", BUST = "xw_bust";

    /** Adds the chest / waist / bust under the slim mesh's body (and chest / waist under the jacket). */
    public static void addToSlimMesh(MeshDefinition mesh, CubeDeformation def) {
        PartDefinition root = mesh.getRoot();
        PartDefinition body = root.getChild("body");
        body.addOrReplaceChild(CHEST, CubeListBuilder.create().texOffs(16, 16).addBox(-4F, 0F, -2F, 8F, 6F, 4F, def), PartPose.ZERO);
        body.addOrReplaceChild(WAIST, CubeListBuilder.create().texOffs(16, 22).addBox(-4F, 0F, -2F, 8F, 6F, 4F, def)
                .texOffs(16, 16).addBox(-4F, 6F, -2F, 8F, 0.02F, 4F, def), PartPose.offset(0F, 6F, 0F));
        // the bust: a slab tipped forward off the upper chest, its top face carrying the shirt's chest
        body.addOrReplaceChild(BUST, CubeListBuilder.create().texOffs(16, 20).addBox(-4F, -1F, -2F, 8F, 2F, 4F,
                def.extend(-0.05F, -0.04F, -0.03F)), PartPose.offsetAndRotation(0F, 2.95F, -1.55F, (float) Math.toRadians(64), 0F, 0F));
        PartDefinition jacket = root.getChild("jacket");
        CubeDeformation outer = def.extend(0.25F);
        jacket.addOrReplaceChild(CHEST, CubeListBuilder.create().texOffs(16, 32).addBox(-4F, 0F, -2F, 8F, 6F, 4F, outer), PartPose.ZERO);
        jacket.addOrReplaceChild(WAIST, CubeListBuilder.create().texOffs(16, 38).addBox(-4F, 0F, -2F, 8F, 6F, 4F, outer), PartPose.offset(0F, 6F, 0F));
        // the jacket / lab coat over the bust too (otherwise the shirt's bust pokes out through the coat and cuts it off at the chest)
        jacket.addOrReplaceChild(BUST, CubeListBuilder.create().texOffs(16, 36).addBox(-4F, -1F, -2F, 8F, 2F, 4F,
                outer.extend(-0.05F, -0.04F, -0.03F)), PartPose.offsetAndRotation(0F, 2.95F, -1.55F, (float) Math.toRadians(64), 0F, 0F));
    }

    private static ModelPart child(ModelPart p, String n) { return p.hasChild(n) ? p.getChild(n) : null; }

    /** Every frame (after the animations): girls get the chest/waist/bust and slimmer limbs, everyone else the normal body. */
    public static void pose(PlayerModel<?> m, LivingEntity e) {
        ModelPart chest = child(m.body, CHEST);
        if (chest == null) return;                                   // (the wide model: nothing to do)
        boolean f = npcGirl || (e instanceof AbstractClientPlayer p && is(p));
        ModelPart waist = child(m.body, WAIST), bust = child(m.body, BUST);
        m.body.skipDraw = f;
        chest.visible = f; waist.visible = f; bust.visible = f;
        bust.yScale = 0.92F; bust.zScale = 0.6F;                         // a modest shape: just out past her clothes
        waist.xScale = WAIST_X; waist.zScale = WAIST_Z;
        ModelPart jc = child(m.jacket, CHEST), jw = child(m.jacket, WAIST), jb = child(m.jacket, BUST);
        if (jc != null && jw != null) {
            m.jacket.skipDraw = f;
            jc.visible = f; jw.visible = f;
            if (jb != null) { jb.visible = f; jb.yScale = bust.yScale; jb.zScale = bust.zScale; }
            jw.xScale = WAIST_X; jw.zScale = WAIST_Z;
        }
        float az = f ? ARM_Z : 1F, lz = f ? LEG_Z : 1F;
        m.rightArm.zScale = az; m.leftArm.zScale = az; m.rightSleeve.zScale = az; m.leftSleeve.zScale = az;
        m.rightLeg.zScale = lz; m.leftLeg.zScale = lz; m.rightPants.zScale = lz; m.leftPants.zScale = lz;
    }
}

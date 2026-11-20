package com.xenowarriors.client.you;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.client.ClientCharacterData;
import com.xenowarriors.client.render.DetailModel;
import com.xenowarriors.you.CharacterGear;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;

import java.util.HashMap;
import java.util.Map;

/**
 * One of your own characters in a show character's clothes: the outfit they gave you worn as a look (YouSlot.look:
 * all of it), or their armor (each piece shows its part - the helmet their hair). Drawn with that character's own 3D
 * clothes and hair (CharacterDetailLayer, and on Epic Fight's body) over your character's skin and face.
 */
public final class YouCostume {
    private YouCostume() {}

    /** A look and the parts of it showing. */
    public record Costume(String id, boolean head, boolean body, boolean arms, boolean legs) {
        public DetailModel model() { return YouCostume.model(id); }
        public String modelPath() { return path(id); }
        public ResourceLocation details() { return XenoWarriors.id("textures/entity/character/" + id + "_details.png"); }
    }

    private static final Map<String, DetailModel> MODELS = new HashMap<>();
    static String path(String id) { return "/assets/" + XenoWarriors.MOD_ID + "/models/detail/" + id + ".json"; }

    static DetailModel model(String id) {
        if (!MODELS.containsKey(id)) MODELS.put(id, DetailModel.loadPath(path(id)));   // (its own copy: never the show character's own)
        return MODELS.get(id);
    }

    /** (Tests) costumes drawn. */
    public static int drawn;

    public static Costume of(AbstractClientPlayer p) {
        if (ClientCharacterData.get(p) != CharacterType.YOU) return null;
        var s = YouFeatures.of(p);
        if (s != null && s.look != null && !s.look.isEmpty() && CharacterGear.ownerOf(s.look) != null) return new Costume(s.look, true, true, true, true);
        String head = look(p, EquipmentSlot.HEAD), chest = look(p, EquipmentSlot.CHEST), legs = look(p, EquipmentSlot.LEGS), feet = look(p, EquipmentSlot.FEET);
        String id = chest != null ? chest : legs != null ? legs : head != null ? head : feet;
        if (id == null || CharacterGear.ownerOf(id) == null) return null;
        return new Costume(id, id.equals(head), id.equals(chest), id.equals(chest), id.equals(legs) || id.equals(feet));
    }

    private static String look(AbstractClientPlayer p, EquipmentSlot slot) {
        var st = p.getItemBySlot(slot);
        if (!(st.getItem() instanceof CharacterGear.CharacterArmorItem)) return null;
        String l = CharacterGear.CharacterArmorItem.look(st);
        return l.isEmpty() ? null : l;
    }

    /** Shows only its parts (its model is a copy of its own, see model()). */
    public static void mask(DetailModel m, Costume c) {
        m.head.visible = c.head();
        m.body.visible = c.body();
        m.rightArm.visible = c.arms(); m.leftArm.visible = c.arms();
        m.rightLeg.visible = c.legs(); m.leftLeg.visible = c.legs();
        drawn++;
    }
}

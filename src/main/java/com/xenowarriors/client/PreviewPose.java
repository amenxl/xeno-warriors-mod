package com.xenowarriors.client;

import com.xenowarriors.character.CharacterType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Character previews (the select screen, Journey, the YOU screens) draw the local player as another character. They
 * used to carry over everything the player had on right now - their armor, what's in their hands, the swing, walk,
 * Epic Fight animation and whatever idle the current character was doing - so every character in the list wore your
 * armor and did your current character's animation. While a preview is drawn the player's armor and hands are emptied
 * (put straight back afterwards; nothing is sent anywhere) and the model is set to a clean standing pose with that
 * character's own idle.
 */
public final class PreviewPose {
    private static ItemStack[] saved;          // armor 0-3, main hand, off hand
    private static int savedSlot = -1;
    private static float attack, attackO;
    private static boolean swinging;
    /** (tests) what the last preview frame looked like: items left on, poses applied. */
    public static int lastItemsOn = -1;
    public static String lastIdle = "";

    private PreviewPose() {}

    /** Empties the local player's armor and hands for the preview. */
    static void strip() {
        Player p = Minecraft.getInstance().player;
        if (p == null || saved != null) return;
        Inventory inv = p.getInventory();
        saved = new ItemStack[6];
        for (int i = 0; i < 4; i++) { saved[i] = inv.armor.get(i); inv.armor.set(i, ItemStack.EMPTY); }
        savedSlot = inv.selected;
        if (Inventory.isHotbarSlot(savedSlot)) { saved[4] = inv.items.get(savedSlot); inv.items.set(savedSlot, ItemStack.EMPTY); }
        saved[5] = inv.offhand.get(0); inv.offhand.set(0, ItemStack.EMPTY);
        attack = p.attackAnim; attackO = p.oAttackAnim; swinging = p.swinging;
        p.attackAnim = 0F; p.oAttackAnim = 0F; p.swinging = false;
        int on = 0;
        for (net.minecraft.world.entity.EquipmentSlot s : net.minecraft.world.entity.EquipmentSlot.values())
            if (!p.getItemBySlot(s).isEmpty()) on++;
        lastItemsOn = on;
    }

    /** Puts everything back exactly as it was. */
    static void restore() {
        Player p = Minecraft.getInstance().player;
        ItemStack[] s = saved;
        saved = null;
        if (p == null || s == null) return;
        Inventory inv = p.getInventory();
        for (int i = 0; i < 4; i++) inv.armor.set(i, s[i]);
        if (s[4] != null && Inventory.isHotbarSlot(savedSlot)) inv.items.set(savedSlot, s[4]);
        inv.offhand.set(0, s[5]);
        p.attackAnim = attack; p.oAttackAnim = attackO; p.swinging = swinging;
    }

    /**
     * A clean standing pose (no walk, swing, crouch, ride or held-item arms left over from the player's real state)
     * plus the previewed character's own idle. The head keeps following the mouse.
     */
    public static void apply(HumanoidModel<?> m, Player p, float age) {
        m.crouching = false;
        m.riding = false;
        m.body.setPos(0F, 0F, 0F); m.body.setRotation(0F, 0F, 0F);
        m.head.setPos(0F, 0F, 0F); m.head.zRot = 0F;
        m.hat.copyFrom(m.head);
        m.rightArm.setPos(-5F, 2F, 0F); m.leftArm.setPos(5F, 2F, 0F);
        m.rightLeg.setPos(-1.9F, 12F, 0.1F); m.leftLeg.setPos(1.9F, 12F, 0.1F);
        m.rightLeg.setRotation(0F, 0F, 0F); m.leftLeg.setRotation(0F, 0F, 0F);
        // vanilla's resting arm sway
        float sway = Mth.cos(age * 0.09F) * 0.05F + 0.05F, lift = Mth.sin(age * 0.067F) * 0.05F;
        m.rightArm.setRotation(lift, 0F, sway);
        m.leftArm.setRotation(-lift, 0F, -sway);
        CharacterType t = p instanceof net.minecraft.client.player.AbstractClientPlayer cp ? ClientCharacterData.get(cp) : CharacterType.NONE;
        String idle = "stand";
        if (t == CharacterType.RHALIX && !ClientCharacterData.standStill) {
            // Rhalix stands with his arms folded
            float breathe = Mth.sin(age * 0.08F) * 0.02F;
            m.rightArm.setRotation(-0.95F + breathe, -0.62F, 0.12F);
            m.leftArm.setRotation(-1.05F + breathe, 0.62F, -0.12F);
            idle = "fold";
        } else {
            // everyone else: a slow breath
            m.body.xRot = Mth.sin(age * 0.05F) * 0.012F;
        }
        lastIdle = t.name() + ":" + idle;
    }
}

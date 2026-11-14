package com.xenowarriors.compat.epicfight;

import net.minecraft.world.entity.PathfinderMob;
import yesman.epicfight.world.capabilities.entitypatch.Faction;

/**
 * A foe with its own fighting AI (the Cynic Dancer, the Ancient Swordstress): Epic Fight animates and draws it - its
 * idle, walk and run for what it holds, hit reactions - but its moves are played by its own AI (foes/FoeAnims),
 * not Epic Fight's attack goals.
 */
public class XWFoePatch<T extends PathfinderMob> extends XWHumanPatch<T> {
    public XWFoePatch(Faction faction) {
        super(faction);
    }

    @Override
    protected void initAI() {}

    @Override
    public void setAIAsInfantry(boolean holdingRangedWeapon) {}
}

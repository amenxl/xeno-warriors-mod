package com.xenowarriors.compat;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;

/** DragonMineZ 2.2's melee approval, opened up for Epic Fight's attacks (see DragonMineZMeleeApprovalMixin). */
public final class DmzMelee {
    private DmzMelee() {}

    private static Method approvedM;
    private static boolean looked;

    /** DragonMineZ's own answer: did this hit come in through its attack request? */
    public static boolean approved(Entity target) {
        if (!looked) {
            looked = true;
            try {
                approvedM = Class.forName("com.dragonminez.common.combat.logic.player.TargetHelper")
                        .getMethod("isApprovedMeleeTarget", Entity.class);
            } catch (Throwable ignored) {}
        }
        try { return approvedM != null && (boolean) approvedM.invoke(null, target); }
        catch (Throwable t) { return false; }
    }

    /** With Epic Fight in the pack, its attacks count - except on friends, which DragonMineZ still decides about. */
    public static boolean epicFightHit(Player attacker, Entity target) {
        if (attacker == null || target == null || !ModList.get().isLoaded("epicfight")) return false;
        Entity t = target instanceof net.minecraftforge.entity.PartEntity<?> part ? part.getParent() : target;
        if (t == attacker || attacker.isAlliedTo(t)) return false;
        if (t.getClass().getName().startsWith("com.dragonminez.") && t.getClass().getName().contains("Master")) return false;
        if (t instanceof Player other && com.xenowarriors.party.Parties.sameParty(attacker, other)) return false;
        return true;
    }
}

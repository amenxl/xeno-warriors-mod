package com.xenowarriors.skill.ultima;

import com.xenowarriors.skill.Skill;
import com.xenowarriors.ultima.UltimaOutput;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Output Surge: push his output up one step right now instead of waiting for it to ramp. */
public class OutputSurgeSkill implements Skill {
    @Override public String getId() { return "output_surge"; }
    @Override public int getCooldownTicks() { return 30 * 20; }
    @Override public int getKarmaCost() { return 35; }

    @Override
    public boolean use(ServerPlayer p) {
        if (UltimaOutput.tier(p) >= 3) {
            p.displayClientMessage(Component.literal("Already at 100% output").withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        com.xenowarriors.anim.Poses.play(p, com.xenowarriors.anim.Poses.EL_RAISE, 14);
        UltimaOutput.setTier(p, UltimaOutput.tier(p) + 1);
        return true;
    }
}

package com.xenowarriors.skill.ultima;

import com.xenowarriors.skill.Skill;
import com.xenowarriors.ultima.UltimaOutput;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Energy Blast: one blast at 25%, a burst of three at 45%, a big piercing one at 70%, a barrage of six at 100%. Clashes. */
public class EnergyBlastSkill implements Skill {
    @Override public String getId() { return "energy_blast"; }
    @Override public int getCooldownTicks() { return 4 * 20; }
    @Override public int getKarmaCost() { return 18; }
    @Override public int clashColor() { return 0x3FFF8C; }

    @Override
    public boolean use(ServerPlayer p) {
        Vec3 d = p.getLookAngle();
        com.xenowarriors.anim.Poses.play(p, com.xenowarriors.anim.Poses.EL_THRUST, 10);
        switch (UltimaOutput.overloaded(p) ? 3 : UltimaOutput.tier(p)) {
            case 0 -> UltimaBlasts.blast(p, d, 6F, 1F, false, 0);
            case 1 -> { for (int i = 0; i < 3; i++) UltimaBlasts.blast(p, d, 5F, 1F, false, i * 4); }
            case 2 -> UltimaBlasts.blast(p, d, 11F, 2F, true, 0);
            default -> {
                for (int i = 0; i < 6; i++) {
                    double a = (i - 2.5) * 0.06;
                    Vec3 dd = new Vec3(d.x * Math.cos(a) - d.z * Math.sin(a), d.y + (i % 2 == 0 ? 0.03 : -0.03), d.x * Math.sin(a) + d.z * Math.cos(a)).normalize();
                    UltimaBlasts.blast(p, dd, 6F, 1.2F, false, i * 2);
                }
            }
        }
        return true;
    }
}

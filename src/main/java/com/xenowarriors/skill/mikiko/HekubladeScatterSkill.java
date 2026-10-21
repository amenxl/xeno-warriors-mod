package com.xenowarriors.skill.mikiko;

import com.xenowarriors.ModSounds;
import com.xenowarriors.entity.HekubladeShardEntity;
import com.xenowarriors.network.ModNetwork;
import com.xenowarriors.network.SkillAnimPacket;
import com.xenowarriors.skill.Skill;
import com.xenowarriors.skill.SkillTargeting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Mikiko skill 1 - Hekublade Scatter.
 * Press: copies of the Hekublade appear in the air around him, pointing wherever he looks.
 * Press again: fire one at your target (each press fires the next). Any left after 8 seconds fire on their own.
 */
public class HekubladeScatterSkill implements Skill {
    public static final int BLADES = 6;
    private static final int COOLDOWN = 14 * 20;
    private static final Map<UUID, List<HekubladeShardEntity>> ACTIVE = new HashMap<>();

    @Override
    public String getId() { return "hekublade_scatter"; }

    @Override
    public int getCooldownTicks() { return COOLDOWN; }

    @Override
    public int getKarmaCost() { return 30; }

    @Override
    public boolean tryRecast(ServerPlayer player) {
        List<HekubladeShardEntity> blades = ACTIVE.get(player.getUUID());
        if (blades == null) return false;
        blades.removeIf(b -> b.isRemoved() || b.fired());
        if (blades.isEmpty()) {
            ACTIVE.remove(player.getUUID());
            return false;
        }
        HekubladeShardEntity next = blades.remove(0);
        next.fire(SkillTargeting.homing(player, 40));
        ModNetwork.sendToTrackingAndSelf(new SkillAnimPacket(player.getId(), SkillAnimPacket.BANG, false), player);
        return true;
    }

    @Override
    public boolean use(ServerPlayer player) {
        List<HekubladeShardEntity> blades = new ArrayList<>();
        for (int i = 0; i < BLADES; i++) {
            HekubladeShardEntity b = HekubladeShardEntity.create(player, i);
            player.level().addFreshEntity(b);
            blades.add(b);
        }
        ACTIVE.put(player.getUUID(), blades);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.HEKUBLADE_SUMMON.get(), SoundSource.PLAYERS, 1F, 0.85F);
        ModNetwork.sendToTrackingAndSelf(new SkillAnimPacket(player.getId(), SkillAnimPacket.SCATTER, false), player);
        player.displayClientMessage(Component.translatable("message.xenowarriors.scatter.cast"), true);
        return true;
    }
}

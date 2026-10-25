package com.xenowarriors.skill;

import com.xenowarriors.character.CharacterType;
import com.xenowarriors.character.ModCapabilities;
import com.xenowarriors.karma.Karma;
import com.xenowarriors.network.ModNetwork;
import com.xenowarriors.network.SkillCooldownPacket;
import com.xenowarriors.skill.mikiko.BlazingWindSkill;
import com.xenowarriors.skill.mikiko.FireBlitzSkill;
import com.xenowarriors.skill.mikiko.HekubladeScatterSkill;
import com.xenowarriors.skill.mikiko.KitsuneSkill;
import com.xenowarriors.skill.mikiko.NetsuSkill;
import com.xenowarriors.skill.rhalix.BangSkill;
import com.xenowarriors.skill.rhalix.EarthWaveSkill;
import com.xenowarriors.skill.rhalix.GravityBarrageSkill;
import com.xenowarriors.skill.rhalix.GravityCompressSkill;
import com.xenowarriors.skill.rhalix.KishiSkill;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.EnumMap;
import java.util.Map;

public final class SkillManager {
    public static final int SLOTS = 5;
    public static final int ULTIMATE_SLOT = 4;

    /** Each character's skills by slot (index 0-3 = skills 1-4, index 4 = ultimate). null = not made yet. */
    private static final Map<CharacterType, Skill[]> SKILLS = new EnumMap<>(CharacterType.class);

    static {
        SKILLS.put(CharacterType.MIKIKO, new Skill[]{new HekubladeScatterSkill(), new KitsuneSkill(), new NetsuSkill(), new FireBlitzSkill(), new BlazingWindSkill()});
        SKILLS.put(CharacterType.RHALIX, new Skill[]{new KishiSkill(), new EarthWaveSkill(), new GravityCompressSkill(), new BangSkill(), new GravityBarrageSkill()});
    }

    private SkillManager() {}

    public static Skill get(CharacterType type, int slot) {
        Skill[] skills = SKILLS.get(type);
        return skills == null || slot < 0 || slot >= SLOTS ? null : skills[slot];
    }

    private static long now(ServerPlayer player) {
        return player.server.overworld().getGameTime();
    }

    public static void useSkill(ServerPlayer player, int slot) {
        if (com.xenowarriors.party.Duels.isKnockedOut(player)) return;
        if (com.xenowarriors.item.PhoneItem.onCall(player)) return;          // hang up first      // can't do anything while knocked out
        if (slot < 0 || slot >= SLOTS || !player.isAlive() || player.isSpectator()) return;
        // the team ultimate (sneak + ultimate key with your partner close by), or joining one you were asked to
        if (slot == ULTIMATE_SLOT && com.xenowarriors.party.TeamUltimate.onUltimateKey(player)) return;
        CharacterType type = ModCapabilities.getType(player);
        Skill skill = get(type, slot);
        if (skill == null) {
            player.displayClientMessage(Component.translatable("message.xenowarriors.skill_locked"), true);
            return;
        }
        if (skill.tryRecast(player)) return;

        player.getCapability(ModCapabilities.PLAYER_CHARACTER).ifPresent(data -> {
            long remaining = data.cooldownEnd[slot] - now(player);
            if (remaining > 0) {
                player.displayClientMessage(Component.translatable("message.xenowarriors.cooldown",
                        skill.getName(), (remaining + 19) / 20), true);
                return;
            }
            int cost = skill.getKarmaCost();
            if (!Karma.has(player, cost)) {
                Karma.notEnough(player, cost);
                return;
            }
            if (skill.use(player)) {
                if (!player.isCreative()) Karma.add(player, -cost);
                com.xenowarriors.compat.DmzKiTraining.skillUsed(player);       // (DragonMineZ: trains Ki Power)
                com.xenowarriors.npc.HumanReactions.skillUsed(player);         // (people nearby are amazed)
                int cooldown = skill.getCooldownTicks();
                data.cooldownEnd[slot] = now(player) + cooldown;
                ModNetwork.sendToPlayer(new SkillCooldownPacket(slot, cooldown, cooldown), player);
            }
        });
    }

    public static void resetCooldowns(ServerPlayer player) {
        player.getCapability(ModCapabilities.PLAYER_CHARACTER).ifPresent(data -> {
            for (int i = 0; i < SLOTS; i++) {
                data.cooldownEnd[i] = 0;
                ModNetwork.sendToPlayer(new SkillCooldownPacket(i, 0, 0), player);
            }
        });
    }
}

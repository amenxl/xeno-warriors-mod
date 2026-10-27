package com.xenowarriors.skill;

import com.xenowarriors.character.CharacterType;
import com.xenowarriors.character.ModCapabilities;
import com.xenowarriors.karma.Karma;
import com.xenowarriors.network.ModNetwork;
import com.xenowarriors.network.SkillCooldownPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.EnumMap;
import java.util.Map;

public final class SkillManager {
    public static final int SLOTS = 5;
    public static final int ULTIMATE_SLOT = 4;
    /** Super ultimate: KAs and really big attacks (Ctrl+6). */
    public static final int SUPER_SLOT = 5;

    private static void useSuper(ServerPlayer player) {
        if (!player.isAlive() || player.isSpectator() || ModCapabilities.getType(player) == CharacterType.NONE) return;
        if (com.xenowarriors.kaeru.KaeruChange.mode(player) != com.xenowarriors.kaeru.KaeruChange.NONE) { com.xenowarriors.kaeru.KaeruChange.revert(player, true); return; }
        player.getCapability(ModCapabilities.PLAYER_CHARACTER).ifPresent(data -> {
            long remaining = data.cooldownEnd[SUPER_SLOT] - now(player);
            if (remaining > 0) {
                player.displayClientMessage(Component.literal("KA ready in " + (remaining + 19) / 20 + "s"), true);
                return;
            }
            CharacterType ct = ModCapabilities.getType(player);
            boolean went;
            if (ct == CharacterType.ULTIMA && com.xenowarriors.ka.Kas.of(player) == null) went = com.xenowarriors.ultima.UltimaOutput.overload(player);   // Ultima's super slot: Overload
            else if (ct == CharacterType.MAX && com.xenowarriors.ka.Kas.of(player) == null) went = com.xenowarriors.skill.sp.VampireCut.use(player);   // Max's KA: Vampire Cut
            else if (ct == CharacterType.ZANE && com.xenowarriors.ka.Kas.of(player) == null) went = com.xenowarriors.skill.ffr.ZaneProjection.use(player);   // Zane's KA: Projection
            else if (ct == CharacterType.KAERU && com.xenowarriors.ka.Kas.of(player) == null) went = com.xenowarriors.kaeru.KaeruChange.use(player);   // Kaeru's KA: Change
            else if (ct == CharacterType.RHALIX && com.xenowarriors.lunar.LunarForm.active(player) && !player.isShiftKeyDown()) went = com.xenowarriors.lunar.MoonFall.use(player); // Lunar: Moon Fall
            else went = com.xenowarriors.ka.Kas.usePlayer(player);
            if (went) {
                com.xenowarriors.XwFx.send(player.serverLevel(), com.xenowarriors.network.FxPacket.KA, player.getX(), player.getY(), player.getZ(), ct.getColor(), 1F);
                int cd = com.xenowarriors.ka.Kas.PLAYER_COOLDOWN;
                data.cooldownEnd[SUPER_SLOT] = now(player) + cd;
                ModNetwork.sendToPlayer(new SkillCooldownPacket(SUPER_SLOT, cd, cd), player);
            }
        });
    }

    private SkillManager() {}

    /** The default loadout's skill in a slot (for the HUD before a sync, and the team ultimate). */
    public static Skill get(CharacterType type, int slot) {
        return SkillPool.inSlot(type, SkillPool.defaultLoadout(), slot);
    }

    /** This player's equipped skill in a slot. */
    public static Skill get(ServerPlayer player, int slot) {
        CharacterType type = ModCapabilities.getType(player);
        return player.getCapability(ModCapabilities.PLAYER_CHARACTER).map(d -> SkillPool.inSlot(type, d.loadout(type), slot)).orElse(null);
    }

    /** Equip pool skill {@code index} into slot 0-3 (swapped with wherever it already sits). */
    public static void setSlot(ServerPlayer player, int slot, int index) {
        if (slot < 0 || slot >= ULTIMATE_SLOT) return;
        CharacterType type = ModCapabilities.getType(player);
        SkillPool.Entry e = SkillPool.entry(type, index);
        if (e == null) return;
        int level = com.xenowarriors.stats.Stats.characterLevel(player);
        int season = com.xenowarriors.character.Seasons.highest(player.server);
        if (season < e.season()) {
            player.displayClientMessage(Component.literal("Season " + e.season() + " content - locked (/xeno season unlock " + e.season() + ")").withStyle(net.minecraft.ChatFormatting.RED), true);
            return;
        }
        if (!SkillPool.unlocked(e, level, player.isCreative())) {
            player.displayClientMessage(Component.literal("Unlocks at Character Level " + e.unlockLevel()).withStyle(net.minecraft.ChatFormatting.RED), true);
            return;
        }
        player.getCapability(ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> {
            int[] l = d.loadout(type).clone();
            for (int i = 0; i < l.length; i++) if (l[i] == index) l[i] = l[slot];
            l[slot] = index;
            d.setLoadout(type, l);
            // swapping doesn't skip cooldowns: the new slot waits at least 5 seconds
            for (int i = 0; i < ULTIMATE_SLOT; i++) {
                long until = Math.max(d.cooldownEnd[i], now(player) + 100);
                d.cooldownEnd[i] = until;
                ModNetwork.sendToPlayer(new SkillCooldownPacket(i, (int) (until - now(player)), 100), player);
            }
        });
        syncLoadout(player);
    }

    public static void syncLoadout(ServerPlayer player) {
        CharacterType type = ModCapabilities.getType(player);
        player.getCapability(ModCapabilities.PLAYER_CHARACTER).ifPresent(d ->
                ModNetwork.sendToPlayer(new com.xenowarriors.network.SkillLoadoutPacket(d.loadout(type), com.xenowarriors.stats.Stats.characterLevel(player), d.ka, com.xenowarriors.character.Seasons.highest(player.server)), player));
    }

    /** Last skill someone used near each player, for Mikiko's True Empathy: {skill, game time}. */
    public static final java.util.Map<java.util.UUID, Object[]> WITNESSED = new java.util.HashMap<>();

    private static long now(ServerPlayer player) {
        return player.server.overworld().getGameTime();
    }

    public static void useSkill(ServerPlayer player, int slot) {
        if (com.xenowarriors.party.Duels.isKnockedOut(player)) return;
        if (com.xenowarriors.item.PhoneItem.onCall(player)) return;          // hang up first      // can't do anything while knocked out
        if (com.xenowarriors.ka.Clashes.inClash(player)) return;          // busy clashing
        if (slot == SUPER_SLOT) { useSuper(player); return; }
        if (player.isSpectator() && com.xenowarriors.kaeru.KaeruSoul.inBlade(player)) { com.xenowarriors.kaeru.KaeruSoul.castOut(player); return; }
        if (slot < 0 || slot >= SLOTS || !player.isAlive() || player.isSpectator()) return;
        // the team ultimate (sneak + ultimate key with your partner close by), or joining one you were asked to
        if (slot == ULTIMATE_SLOT && com.xenowarriors.party.TeamUltimate.onUltimateKey(player)) return;
        CharacterType type = ModCapabilities.getType(player);
        Skill skill = get(player, slot);
        if (skill == null) {
            player.displayClientMessage(Component.translatable("message.xenowarriors.skill_locked"), true);
            return;
        }
        // a skill from a season that's locked again can't be used (it stays in the slot)
        if (slot < ULTIMATE_SLOT) {
            int idx = player.getCapability(ModCapabilities.PLAYER_CHARACTER).map(d -> d.loadout(type)[slot]).orElse(-1);
            SkillPool.Entry en = SkillPool.entry(type, idx);
            if (en != null && com.xenowarriors.character.Seasons.highest(player.server) < en.season()) {
                player.displayClientMessage(Component.literal("Season " + en.season() + " content is locked").withStyle(net.minecraft.ChatFormatting.RED), true);
                return;
            }
        }
        if (skill.tryRecast(player)) return;

        player.getCapability(ModCapabilities.PLAYER_CHARACTER).ifPresent(data -> {
            long remaining = data.cooldownEnd[slot] - now(player);
            if (remaining > 0) {
                player.displayClientMessage(Component.translatable("message.xenowarriors.cooldown",
                        skill.getName(), (remaining + 19) / 20), true);
                return;
            }
            int cost = skill.getKarmaCost(player);
            if (!Karma.has(player, cost)) {
                Karma.notEnough(player, cost);
                return;
            }
            // a blast aimed at someone charging their own: CLASH instead
            boolean clash = skill.clashColor() >= 0 && com.xenowarriors.ka.Clashes.tryStart(player, skill.clashColor());
            if (clash || skill.use(player)) {
                com.xenowarriors.XwFx.send(player.serverLevel(), slot == ULTIMATE_SLOT ? com.xenowarriors.network.FxPacket.ULTIMATE : com.xenowarriors.network.FxPacket.SKILL,
                        player.getX(), player.getY(), player.getZ(), type.getColor(), slot == ULTIMATE_SLOT ? 6F : 1F);
                if (!player.isCreative()) Karma.add(player, -cost);
                com.xenowarriors.compat.DmzKiTraining.skillUsed(player);       // (DragonMineZ: trains Ki Power)
                com.xenowarriors.npc.HumanReactions.skillUsed(player);         // (people nearby are amazed)
                if (slot != ULTIMATE_SLOT && !(skill instanceof com.xenowarriors.skill.mikiko.TrueEmpathySkill))
                    for (ServerPlayer o : player.serverLevel().players())
                        if (o != player && o.distanceToSqr(player) < 24 * 24) WITNESSED.put(o.getUUID(), new Object[]{skill, now(player)});
                int cooldown = skill.getCooldownTicks();
                data.cooldownEnd[slot] = now(player) + cooldown;
                ModNetwork.sendToPlayer(new SkillCooldownPacket(slot, cooldown, cooldown), player);
            }
        });
    }

    public static void resetCooldowns(ServerPlayer player) {
        player.getCapability(ModCapabilities.PLAYER_CHARACTER).ifPresent(data -> {
            for (int i = 0; i <= SUPER_SLOT; i++) {
                data.cooldownEnd[i] = 0;
                ModNetwork.sendToPlayer(new SkillCooldownPacket(i, 0, 0), player);
            }
        });
    }
}

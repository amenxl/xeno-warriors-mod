package com.xenowarriors.skill;

import com.xenowarriors.network.SkillAnimPacket;

import com.xenowarriors.character.CharacterType;
import com.xenowarriors.character.ModCapabilities;
import com.xenowarriors.karma.Karma;
import com.xenowarriors.network.ModNetwork;
import com.xenowarriors.network.SkillCooldownPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.EnumMap;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;

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
                player.displayClientMessage(Component.literal((com.xenowarriors.skill.XenoBlast.has(ModCapabilities.getType(player)) ? "Xeno Blast" : "KA") + " ready in " + (remaining + 19) / 20 + "s"), true);
                return;
            }
            CharacterType ct = ModCapabilities.getType(player);
            boolean went;
            if (ct == CharacterType.ULTIMA && com.xenowarriors.ka.Kas.of(player) == null) went = com.xenowarriors.ultima.UltimaOutput.overload(player);   // Ultima's super slot: Overload
            else if (ct == CharacterType.MAX && com.xenowarriors.ka.Kas.of(player) == null) went = com.xenowarriors.skill.sp.VampireCut.use(player);   // Max's KA: Vampire Cut
            else if (ct == CharacterType.LOGAN && com.xenowarriors.ka.Kas.of(player) == null) went = com.xenowarriors.skill.ffr.SrSkills.redirect(player);   // Logan's KA: Redirect
            else if (ct == CharacterType.KEITA && com.xenowarriors.ka.Kas.of(player) == null) went = com.xenowarriors.skill.ffr.SrSkills.believe(player);    // Keita's KA
            else if (ct == CharacterType.ZANE && com.xenowarriors.ka.Kas.of(player) == null) went = com.xenowarriors.skill.ffr.ZaneProjection.use(player);   // Zane's KA: Projection
            else if (ct == CharacterType.KAERU && com.xenowarriors.ka.Kas.of(player) == null) went = com.xenowarriors.kaeru.KaeruChange.use(player);   // Kaeru's KA: Change
            else if (ct == CharacterType.RHALIX && com.xenowarriors.lunar.LunarForm.active(player) && !player.isShiftKeyDown()) went = com.xenowarriors.lunar.MoonFall.use(player); // Lunar: Moon Fall
            else if (com.xenowarriors.skill.XenoBlast.has(ct)) went = com.xenowarriors.skill.XenoBlast.use(player);   // Mikiko / Rhalix: last resort
            else went = com.xenowarriors.ka.Kas.usePlayer(player);
            if (went) {
                com.xenowarriors.journey.Journey.onSkill(player);
                boolean xb = com.xenowarriors.skill.XenoBlast.has(ct) && !(ct == CharacterType.RHALIX && com.xenowarriors.lunar.LunarForm.active(player));
                com.xenowarriors.network.PopupPacket.toParty(player, com.xenowarriors.network.PopupPacket.REQUEST, xb ? "Xeno Blast!" : "KA!",
                        player.getName().getString() + (xb ? " fired a Xeno Blast - their last resort!" : " used their KA!"), "rage");
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
        return player.getCapability(ModCapabilities.PLAYER_CHARACTER).resolve().map(d -> SkillPool.inSlot(player, ultUsable(player, d.ultChoice(type))
                ? d.loadoutWithUlt(type) : d.loadout(type), slot)).orElse(null);   // (resolve(): an empty slot maps to null, which LazyOptional.map throws on)
    }

    /** The picked ultimate is still theirs to use (its level reached - or creative - and its season unlocked); else the default one fires. */
    private static boolean ultUsable(ServerPlayer player, int choice) {
        if (choice <= 0) return false;
        java.util.List<SkillPool.Entry> l = SkillPool.ultimates(player);
        if (choice >= l.size()) return false;
        SkillPool.Entry e = l.get(choice);
        return SkillPool.available(e, com.xenowarriors.stats.Stats.characterLevel(player), player.isCreative(), com.xenowarriors.character.Seasons.highest(player.server));
    }

    /** Put one of the character's ultimates ({@code index} in SkillPool.ultimates) in the ultimate slot. */
    public static void setUltimate(ServerPlayer player, int index) {
        CharacterType type = ModCapabilities.getType(player);
        java.util.List<SkillPool.Entry> l = SkillPool.ultimates(player);
        if (index < 0 || index >= l.size()) return;
        SkillPool.Entry e = l.get(index);
        int level = com.xenowarriors.stats.Stats.characterLevel(player);
        if (com.xenowarriors.character.Seasons.highest(player.server) < e.season()) {
            player.displayClientMessage(Component.literal("Season " + e.season() + " content - locked (/xeno season unlock " + e.season() + ")").withStyle(net.minecraft.ChatFormatting.RED), true);
            return;
        }
        if (!SkillPool.unlocked(e, level, player.isCreative())) {
            player.displayClientMessage(Component.literal("Unlocks at Character Level " + e.unlockLevel()).withStyle(net.minecraft.ChatFormatting.RED), true);
            return;
        }
        player.getCapability(ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> {
            if (d.ultChoice(type) == index) return;
            d.setUltChoice(type, index);
            // swapping doesn't skip the cooldown: the ultimate waits at least 5 seconds
            long until = Math.max(d.cooldownEnd[ULTIMATE_SLOT], now(player) + 100);
            d.cooldownEnd[ULTIMATE_SLOT] = until;
            ModNetwork.sendToPlayer(new SkillCooldownPacket(ULTIMATE_SLOT, (int) (until - now(player)), 100), player);
        });
        syncLoadout(player);
    }

    /** Equip pool skill {@code index} into slot 0-3 (swapped with wherever it already sits). */
    public static void setSlot(ServerPlayer player, int slot, int index) {
        if (slot == ULTIMATE_SLOT) { setUltimate(player, index); return; }      // (the ultimate slot: index into the ultimates)
        if (slot < 0 || slot >= ULTIMATE_SLOT) return;
        CharacterType type = ModCapabilities.getType(player);
        SkillPool.Entry e = SkillPool.entry(player, index);
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
                ModNetwork.sendToPlayer(new com.xenowarriors.network.SkillLoadoutPacket(d.loadoutWithUlt(type), com.xenowarriors.stats.Stats.characterLevel(player), d.ka, com.xenowarriors.character.Seasons.highest(player.server)), player));
        SkillLevels.sync(player);                                             // (this character's ability levels)
    }

    /** Last skill someone used near each player, for Mikiko's True Empathy: {skill, game time}. */
    public static final java.util.Map<java.util.UUID, Object[]> WITNESSED = new java.util.HashMap<>();
    public static void forget(java.util.UUID id) { WITNESSED.remove(id); }

    private static long now(ServerPlayer player) {
        return player.server.overworld().getGameTime();
    }

    /** Playing a character from a season that's locked (again) in this world: says so, and true. */
    private static boolean seasonLocked(ServerPlayer player) {
        CharacterType me = ModCapabilities.getType(player);
        if (me == CharacterType.NONE || com.xenowarriors.character.Seasons.unlocked(player.server, me.season())) return false;
        player.displayClientMessage(Component.literal("Season " + me.season() + " content is locked").withStyle(net.minecraft.ChatFormatting.RED), true);
        return true;
    }

    public static void useSkill(ServerPlayer player, int slot) {
        com.xenowarriors.combat.SkillContext.run(player, () -> useSkillNow(player, slot));   // (everything it does counts as skill damage)
    }

    private static void useSkillNow(ServerPlayer player, int slot) {
        if (com.xenowarriors.party.Duels.cantAct(player)) return;          // down, or a seed
        if (com.xenowarriors.item.PhoneItem.onCall(player)) return;          // hang up first      // can't do anything while knocked out
        if (com.xenowarriors.ka.Clashes.inClash(player)) return;          // busy clashing
        // a character whose season was locked again (by /xeno season lock) while someone played them: no skills, ultimate or KA
        boolean seasonLocked = seasonLocked(player);
        if (slot == SUPER_SLOT) { if (!seasonLocked) useSuper(player); return; }
        if (player.isSpectator() && com.xenowarriors.kaeru.KaeruSoul.inBlade(player)) { com.xenowarriors.kaeru.KaeruSoul.castOut(player); return; }
        if (seasonLocked) return;
        if (slot < 0 || slot >= SLOTS || !player.isAlive() || player.isSpectator()) return;
        // the team ultimate (sneak + ultimate key with your partner close by), or joining one you were asked to
        if (slot == ULTIMATE_SLOT && !UltCharge.firing && com.xenowarriors.party.TeamUltimate.onUltimateKey(player)) return;
        CharacterType type = ModCapabilities.getType(player);
        Skill skill = get(player, slot);
        if (skill == null) {
            player.displayClientMessage(Component.translatable("message.xenowarriors.skill_locked"), true);
            return;
        }
        // a skill from a season that's locked again can't be used (it stays in the slot)
        if (slot < ULTIMATE_SLOT) {
            int idx = player.getCapability(ModCapabilities.PLAYER_CHARACTER).map(d -> d.loadout(type)[slot]).orElse(-1);
            SkillPool.Entry en = SkillPool.entry(player, idx);
            if (en != null && com.xenowarriors.character.Seasons.highest(player.server) < en.season()) {
                player.displayClientMessage(Component.literal("Season " + en.season() + " content is locked").withStyle(net.minecraft.ChatFormatting.RED), true);
                return;
            }
            if (en != null && !SkillPool.unlocked(en, com.xenowarriors.stats.Stats.characterLevel(player), player.isCreative())) {   // (slotted in creative, now in survival)
                player.displayClientMessage(Component.literal("Unlocks at Character Level " + en.unlockLevel()).withStyle(net.minecraft.ChatFormatting.RED), true);
                return;
            }
        }
        com.xenowarriors.toggle.Toggles.Entry asToggle = com.xenowarriors.toggle.Toggles.Entry.byId(skill.getId());
        if (asToggle != null && com.xenowarriors.toggle.Toggles.of(player).contains(asToggle)) {   // a passive / transformation now: its slot switches it (or transforms, or opens the scent picker)
            if (asToggle == com.xenowarriors.toggle.Toggles.Entry.LIVING_WEAPON) com.xenowarriors.toggle.Passives.livingWeapon(player);
            else com.xenowarriors.toggle.Toggles.use(player, asToggle);
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
            int cost = SkillLevels.costFor(player, skill.getId(), skill.getKarmaCost(player));   // (ability level / tier)
            if (com.xenowarriors.party.Synergy.has(player, com.xenowarriors.party.Synergy.OLD_FRIENDS)) cost = Math.round(cost * (1F - Math.min(0.5F, 0.25F * com.xenowarriors.party.Synergy.strength(player, com.xenowarriors.party.Synergy.OLD_FRIENDS))));   // Old Friends: Zane + Mikiko (stronger with the bond)
            if (!Karma.has(player, cost)) {
                Karma.notEnough(player, cost);
                return;
            }
            // a blast aimed at someone charging their own: CLASH instead
            boolean clash = skill.clashColor() >= 0 && skill.clashesOnUse() && com.xenowarriors.ka.Clashes.tryStart(player, skill.clashColor());
            if (clash || com.xenowarriors.combat.SkillContext.callSkill(player, skill.getId(), () -> skill.use(player))) {
                com.xenowarriors.journey.Journey.onSkill(player);
                Integer gen = SkillAnimPacket.GENERIC.get(skill.getId());          // newer skills: a general move to go with them
                if (gen != null && !clash) ModNetwork.sendToTrackingAndSelf(new SkillAnimPacket(player.getId(), gen, !player.onGround()), player);
                com.xenowarriors.XwFx.send(player.serverLevel(), slot == ULTIMATE_SLOT ? com.xenowarriors.network.FxPacket.ULTIMATE : com.xenowarriors.network.FxPacket.SKILL,
                        player.getX(), player.getY(), player.getZ(), type.getColor(), slot == ULTIMATE_SLOT ? 6F : 1F);
                if (!player.isCreative()) Karma.add(player, -cost);
                if (slot == ULTIMATE_SLOT) com.xenowarriors.network.PopupPacket.toParty(player, com.xenowarriors.network.PopupPacket.REQUEST, "Ultimate!", player.getName().getString() + " unleashed " + skill.getName().getString() + "!", "rage");
                com.xenowarriors.compat.DmzKiTraining.skillUsed(player);       // (DragonMineZ: trains Ki Power)
                com.xenowarriors.npc.HumanReactions.skillUsed(player);         // (people nearby are amazed)
                if (slot != ULTIMATE_SLOT && !(skill instanceof com.xenowarriors.skill.mikiko.TrueEmpathySkill))
                    for (ServerPlayer o : player.serverLevel().players())
                        if (o != player && o.distanceToSqr(player) < 24 * 24) WITNESSED.put(o.getUUID(), new Object[]{skill, now(player)});
                int cooldown = SkillLevels.cooldownFor(player, skill.getId(), skill.getCooldownTicks());
                data.cooldownEnd[slot] = now(player) + cooldown;
                ModNetwork.sendToPlayer(new SkillCooldownPacket(slot, cooldown, cooldown), player);
                SkillLevels.used(player, skill);                                // (one use toward its next ability level - after this use's cost and cooldown)
            }
        });
    }

    /** Cooldowns of skills used outside the slots (the right+left click skill, the toggle wheel): player -> skill id -> end. */
    private static final Map<UUID, Map<String, Long>> FREE_CD = new HashMap<>();

    /** Ticks left on a skill used outside the slots (0 = ready) - its slot's cooldown too, when it's also equipped. */
    public static long freeCooldownLeft(ServerPlayer player, Skill skill) {
        long now = now(player), left = Math.max(0, FREE_CD.getOrDefault(player.getUUID(), Map.of()).getOrDefault(skill.getId(), 0L) - now);
        int slot = slotOf(player, skill);
        if (slot >= 0) left = Math.max(left, player.getCapability(ModCapabilities.PLAYER_CHARACTER).map(d -> d.cooldownEnd[slot] - now).orElse(0L));
        return left;
    }

    /** Starts a skill's out-of-slot cooldown (Kaeru's scent picker). */
    public static void setFreeCooldown(ServerPlayer player, Skill skill, int ticks) {
        FREE_CD.computeIfAbsent(player.getUUID(), k -> new HashMap<>()).put(skill.getId(), now(player) + ticks);
    }

    /** The slot (0-3) this skill is equipped in, or -1. */
    public static int slotOf(ServerPlayer player, Skill skill) {
        for (int i = 0; i < ULTIMATE_SLOT; i++) { Skill s = get(player, i); if (s != null && s.getId().equals(skill.getId())) return i; }
        return -1;
    }

    /**
     * Uses a skill that isn't fired from a slot (the right+left click skill, Netsu from the toggle wheel): the same Karma
     * cost, ability levels and checks as from a slot, with its own cooldown times {@code cooldownMult} (shared with its
     * slot when it's equipped as well). True when it went off.
     */
    public static boolean useFree(ServerPlayer player, Skill skill, float cooldownMult) {
        boolean[] ok = {false};
        com.xenowarriors.combat.SkillContext.run(player, () -> ok[0] = useFreeNow(player, skill, cooldownMult));
        return ok[0];
    }

    private static boolean useFreeNow(ServerPlayer player, Skill skill, float cooldownMult) {
        if (com.xenowarriors.party.Duels.cantAct(player) || com.xenowarriors.item.PhoneItem.onCall(player) || com.xenowarriors.ka.Clashes.inClash(player)) return false;
        if (seasonLocked(player) || !player.isAlive() || player.isSpectator()) return false;
        if (skill.tryRecast(player)) return true;
        long left = freeCooldownLeft(player, skill);
        if (left > 0) { player.displayClientMessage(Component.translatable("message.xenowarriors.cooldown", skill.getName(), (left + 19) / 20), true); return false; }
        int cost = SkillLevels.costFor(player, skill.getId(), skill.getKarmaCost(player));
        if (!Karma.has(player, cost)) { Karma.notEnough(player, cost); return false; }
        if (!com.xenowarriors.combat.SkillContext.callSkill(player, skill.getId(), () -> skill.use(player))) return false;
        com.xenowarriors.journey.Journey.onSkill(player);
        Integer gen = SkillAnimPacket.GENERIC.get(skill.getId());
        if (gen != null) ModNetwork.sendToTrackingAndSelf(new SkillAnimPacket(player.getId(), gen, !player.onGround()), player);
        com.xenowarriors.XwFx.send(player.serverLevel(), com.xenowarriors.network.FxPacket.SKILL, player.getX(), player.getY(), player.getZ(), ModCapabilities.getType(player).getColor(), 1F);
        if (!player.isCreative()) Karma.add(player, -cost);
        com.xenowarriors.compat.DmzKiTraining.skillUsed(player);
        com.xenowarriors.npc.HumanReactions.skillUsed(player);
        if (!(skill instanceof com.xenowarriors.skill.mikiko.TrueEmpathySkill))
            for (ServerPlayer o : player.serverLevel().players())
                if (o != player && o.distanceToSqr(player) < 24 * 24) WITNESSED.put(o.getUUID(), new Object[]{skill, now(player)});
        int cooldown = Math.round(SkillLevels.cooldownFor(player, skill.getId(), skill.getCooldownTicks()) * cooldownMult);
        FREE_CD.computeIfAbsent(player.getUUID(), k -> new HashMap<>()).put(skill.getId(), now(player) + cooldown);
        int slot = slotOf(player, skill);
        if (slot >= 0) player.getCapability(ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> {   // (equipped too: its slot waits as well)
            d.cooldownEnd[slot] = Math.max(d.cooldownEnd[slot], now(player) + cooldown);
            ModNetwork.sendToPlayer(new SkillCooldownPacket(slot, cooldown, cooldown), player);
        });
        SkillLevels.used(player, skill);
        return true;
    }

    public static void resetCooldowns(ServerPlayer player) {
        FREE_CD.remove(player.getUUID());
        player.getCapability(ModCapabilities.PLAYER_CHARACTER).ifPresent(data -> {
            for (int i = 0; i <= SUPER_SLOT; i++) {
                data.cooldownEnd[i] = 0;
                ModNetwork.sendToPlayer(new SkillCooldownPacket(i, 0, 0), player);
            }
        });
    }
}

package com.xenowarriors.party;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.bot.PartyBotEntity;
import com.xenowarriors.bot.PartyBots;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.character.ModCapabilities;
import com.xenowarriors.network.ModNetwork;
import com.xenowarriors.network.PartySwapPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashSet;
import java.util.Set;

/**
 * Party Swap (a toggle in the Party menu): when one of your bot teammates is lost - bleeds out without a revive, or
 * dies outright - another of your characters who isn't in use joins as a fresh bot at your side, so the party stays
 * full. The replacement is your highest-level character that isn't you, isn't already in the party, isn't still
 * recovering, is unlocked this season and isn't kept apart from someone in the party (Ai-oni and Rhalix); the usual
 * party limits apply (4 people and bots, 3 bots each). Not on a Journey or in VR. A 10 second cooldown stops it looping.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PartySwap {
    private static final String KEY = "xw_party_swap", LAST = "xw_party_swap_last", REJOIN = "xw_bot_rejoin_";
    public static int COOLDOWN = 10 * 20;

    private PartySwap() {}

    public static boolean on(ServerPlayer p) { return p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getBoolean(KEY); }

    public static void set(ServerPlayer p, boolean on) {
        CompoundTag root = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        root.putBoolean(KEY, on);
        p.getPersistentData().put(Player.PERSISTED_NBT_TAG, root);
        p.displayClientMessage(Component.literal("Party Swap " + (on ? "ON - a lost bot teammate is replaced by another of your characters" : "OFF")).withStyle(on ? ChatFormatting.GREEN : ChatFormatting.GRAY), true);
        sync(p);
    }

    public static void sync(ServerPlayer p) { ModNetwork.sendToPlayer(new PartySwapPacket(on(p)), p); }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) { if (event.getEntity() instanceof ServerPlayer p) sync(p); }

    /** One of this player's bots is gone for good (bled out, or died): a fresh one joins, next tick. */
    public static void botLost(ServerPlayer owner, CharacterType lost) {
        if (owner == null || !on(owner)) return;
        com.xenowarriors.skill.ServerScheduler.later(1, () -> { if (owner.isAlive() && !owner.hasDisconnected()) replace(owner, lost); });
    }

    /** Who would come in for this lost character (null: nobody can). */
    public static CharacterType pick(ServerPlayer o, CharacterType lost) {
        Set<CharacterType> taken = new HashSet<>();
        taken.add(lost);
        for (ServerPlayer m : Parties.members(o)) { taken.add(ModCapabilities.getType(m)); for (PartyBotEntity b : PartyBots.bots(m)) taken.add(b.character()); }
        taken.add(ModCapabilities.getType(o));
        for (PartyBotEntity b : PartyBots.bots(o)) taken.add(b.character());
        long now = o.level().getGameTime();
        CharacterType best = null;
        int bestLv = -1;
        for (CharacterType t : CharacterType.PLAYABLE) {
            if (t == CharacterType.NONE || t == CharacterType.YOU || taken.contains(t)) continue;
            if (com.xenowarriors.character.Seasons.highest(o.server) < t.season()) continue;
            if ((t == CharacterType.AIONI && taken.contains(CharacterType.RHALIX)) || (t == CharacterType.RHALIX && taken.contains(CharacterType.AIONI))) continue;
            if (o.getPersistentData().getLong(REJOIN + t.getId()) > now) continue;          // (still recovering)
            if (!PartyBots.canFollowTo(t, o.level())) continue;
            int lv = PartyBots.levelFor(o, t);
            if (lv > bestLv) { bestLv = lv; best = t; }
        }
        return best;
    }

    private static void replace(ServerPlayer o, CharacterType lost) {
        if (com.xenowarriors.journey.Journey.inLobbyOrRun(o) || com.xenowarriors.vr.Vr.inVr(o)) return;
        long now = o.level().getGameTime();
        long last = o.getPersistentData().getLong(LAST);
        if (last > 0 && now - last < COOLDOWN && now >= last) {
            o.displayClientMessage(Component.literal("Party Swap is recharging").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        if (PartyBots.partySize(o) >= Parties.MAX_SIZE || PartyBots.bots(o).size() >= PartyBots.MAX_BOTS) return;
        CharacterType t = pick(o, lost);
        if (t == null) { o.displayClientMessage(Component.literal("Party Swap: none of your other characters can come in right now").withStyle(ChatFormatting.GRAY), false); return; }
        PartyBotEntity b = PartyBots.spawn(o, t, null);
        if (b == null) return;
        o.getPersistentData().putLong(LAST, now);
        o.sendSystemMessage(Component.literal("Party Swap: ").withStyle(ChatFormatting.GOLD).append(b.label()).append(Component.literal(" takes " + lost.getDisplayName().getString() + "'s place!").withStyle(ChatFormatting.GOLD)));
        o.serverLevel().sendParticles(net.minecraft.core.particles.ParticleTypes.TOTEM_OF_UNDYING, b.getX(), b.getY() + 1, b.getZ(), 20, .3, .6, .3, .2);
        XenoWarriors.LOGGER.info("Party swap: {} lost {}, {} joined (level {})", o.getGameProfile().getName(), lost.getId(), t.getId(), b.botLevel());
    }
}

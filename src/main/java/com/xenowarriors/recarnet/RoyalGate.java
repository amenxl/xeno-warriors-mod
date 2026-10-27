package com.xenowarriors.recarnet;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.character.ModCapabilities;
import com.xenowarriors.economy.Money;
import com.xenowarriors.item.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Who gets into the Royal Capital (Section 1) past the checkpoint at the landing platform:
 * <ul>
 * <li>the Sky Patrol and Queen Kyrie (Rhalix, Kyrie, Lexy, Max, Sam, Technica) - always;</li>
 * <li>other Skylanders - with a Sky ID, which the Royal Guard hands over once you're rich enough (10,000 Money) to
 * count as royalty;</li>
 * <li>everyone else (Mikiko, the Freedom Fighters...) - only as a Skylander's guest: a Skylander in your party (a player
 * or a bot) close by;</li>
 * <li>the Sky Rebellion (Logan, Kill, Keita) - aren't stopped, but the guards fight them: they have to fight their way in.</li>
 * </ul>
 * Anyone else is held at the checkpoint: walk (or fly) past it and the guards put you back.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RoyalGate {
    /** The checkpoint: this far from the landing platform you're still "at the gate". */
    public static final int CHECKPOINT = 9;

    private RoyalGate() {}

    public static boolean skyPatrol(CharacterType t) {
        return t == CharacterType.RHALIX || t == CharacterType.KYRIE || t == CharacterType.LEXY || t == CharacterType.MAX
                || t == CharacterType.SAM || t == CharacterType.TECHNICA;
    }

    public static boolean skyRebellion(CharacterType t) {
        return t == CharacterType.LOGAN || t == CharacterType.KILL || t == CharacterType.KEITA;
    }

    public static boolean skylander(CharacterType t) { return t.skylander() || skyPatrol(t); }

    public static boolean hasId(Player p) {
        for (ItemStack s : p.getInventory().items) if (s.is(ModItems.SKY_ID.get())) return true;
        return p.getOffhandItem().is(ModItems.SKY_ID.get());
    }

    /** The Skylander who's vouching for this player (a party member or party bot nearby), or null. */
    public static Component host(ServerPlayer p) {
        for (ServerPlayer m : com.xenowarriors.party.Parties.members(p)) {
            if (m == p || m.level() != p.level() || m.distanceToSqr(p) > 32 * 32) continue;
            if (skylander(ModCapabilities.getType(m))) return m.getDisplayName();
        }
        for (ServerPlayer m : com.xenowarriors.party.Parties.members(p))
            for (var b : com.xenowarriors.bot.PartyBots.bots(m))
                if (b.level() == p.level() && b.distanceToSqr(p) < 32 * 32 && skylander(b.character())) return b.character().getDisplayName();
        return null;
    }

    public static boolean allowed(ServerPlayer p) {
        if (p.isCreative() || p.isSpectator()) return true;
        CharacterType t = ModCapabilities.getType(p);
        if (skyPatrol(t) || skyRebellion(t)) return true;
        if (skylander(t) && hasId(p)) return true;
        return host(p) != null;
    }

    private static void say(ServerPlayer p, String line) {
        p.sendSystemMessage(Component.literal("Royal Guard: ").withStyle(ChatFormatting.GOLD).append(Component.literal("\"" + line + "\"").withStyle(ChatFormatting.WHITE)));
    }

    /** Talking to a Royal Guard at the checkpoint. */
    public static void talk(ServerPlayer p, RoyalGuardEntity guard) {
        CharacterType t = ModCapabilities.getType(p);
        if (skyRebellion(t)) {
            say(p, "Sky Rebellion! Guards - stop them!");
            for (var g : p.level().getEntitiesOfClass(RoyalGuardEntity.class, p.getBoundingBox().inflate(24))) g.setTarget(p);
            return;
        }
        if (skyPatrol(t)) { say(p, t == CharacterType.RHALIX ? "Welcome home, Your Majesty." : t == CharacterType.KYRIE ? "Welcome home, my Queen." : "Welcome back, Sky Patrol. Go right ahead."); return; }
        if (skylander(t)) {
            if (hasId(p)) { say(p, "Your Sky ID checks out. Enjoy the capital."); return; }
            long m = Money.get(p);
            if (m >= Money.ROYAL) {
                say(p, "My apologies - someone of your means counts as royalty here. Here's your Sky ID.");
                if (!p.getInventory().add(new ItemStack(ModItems.SKY_ID.get()))) p.drop(new ItemStack(ModItems.SKY_ID.get()), false);
                p.level().playSound(null, p.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6F, 1.6F);
            } else {
                say(p, "No Sky ID, no entry. Only Skylanders of means live up here - come back with " + Money.fmt(Money.ROYAL) + " Money. (You have " + Money.fmt(m) + ".)");
            }
            return;
        }
        Component host = host(p);
        if (host != null) say(p, "You're " + host.getString() + "'s guest. Go on in - and behave.");
        else say(p, "Ground folk only get in as a Skylander's guest. Come back with one.");
    }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p.tickCount % 10 != 0) return;
        if (!RoyalCapitalFeature.inCapital(p.level(), p.blockPosition())) return;
        BlockPos at = RoyalCapitalFeature.LANDING;
        double dx = p.getX() - (at.getX() + 0.5), dz = p.getZ() - (at.getZ() + 0.5);
        if (dx * dx + dz * dz <= CHECKPOINT * CHECKPOINT || allowed(p)) return;
        // held at the checkpoint
        p.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        p.setDeltaMovement(0, 0, 0);
        p.hurtMarked = true;
        CharacterType t = ModCapabilities.getType(p);
        p.displayClientMessage(Component.literal(skylander(t) ? "Royal Guard: \"Halt! Show your Sky ID first.\""
                : "Royal Guard: \"Halt! Ground folk only come in as a Skylander's guest.\"").withStyle(ChatFormatting.GOLD), true);
        p.level().playSound(null, p.blockPosition(), SoundEvents.IRON_GOLEM_ATTACK, SoundSource.HOSTILE, 0.6F, 1.2F);
    }
}

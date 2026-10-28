package com.xenowarriors.gov;

import com.xenowarriors.XenoWarriors;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Being caught in a Government facility: breaking things or opening its containers with staff around makes you wanted. */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GovFacilities {
    private static final String WANTED = "xw_gov_wanted_until";

    private GovFacilities() {}

    public static boolean wanted(Player p) {
        return !p.isCreative() && !p.isSpectator() && p.getPersistentData().getLong(WANTED) > p.level().getGameTime();
    }

    public static void makeWanted(Player p, String why) {
        if (p.isCreative() || p.isSpectator()) return;
        if (com.xenowarriors.tower.Tower.is(p.level()) || com.xenowarriors.vr.Vr.inVr(p)) return;     // (Tower and VR fights don't count)
        boolean was = wanted(p);
        p.getPersistentData().putLong(WANTED, p.level().getGameTime() + 20 * 90);
        if (!was && p instanceof ServerPlayer sp0) com.xenowarriors.faction.Reputation.add(sp0, com.xenowarriors.faction.Faction.GOV, -30);
        if (!was && p instanceof ServerPlayer sp)
            sp.sendSystemMessage(Component.literal("You've been caught - you " + why + ". Government guards are after you!").withStyle(ChatFormatting.RED));
    }

    static boolean staffNear(ServerLevel l, BlockPos pos, int r) {
        return !l.getEntitiesOfClass(GovAgentEntity.class, new net.minecraft.world.phys.AABB(pos).inflate(r)).isEmpty();
    }

    public static void alarm(ServerLevel l, BlockPos at, Player p, String msg) {
        makeWanted(p, "were spotted");
        l.playSound(null, at, SoundEvents.BELL_BLOCK, SoundSource.HOSTILE, 1.2F, 1.6F);
        for (GovAgentEntity g : l.getEntitiesOfClass(GovAgentEntity.class, new net.minecraft.world.phys.AABB(at).inflate(32)))
            if (g.guard() && g.getTarget() == null) g.setTarget(p);
        if (p instanceof ServerPlayer sp) sp.displayClientMessage(Component.literal(msg).withStyle(ChatFormatting.RED), true);
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel l) || event.getPlayer() == null) return;
        if (staffNear(l, event.getPos(), 20)) alarm(l, event.getPos(), event.getPlayer(), "Alarm! You broke into a Government facility!");
    }

    @SubscribeEvent
    public static void onOpen(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getLevel() instanceof ServerLevel l)) return;
        if (l.getBlockEntity(event.getPos()) instanceof Container && staffNear(l, event.getPos(), 16) && !event.getEntity().isCrouching())
            alarm(l, event.getPos(), event.getEntity(), "Alarm! Hands off Government property!");
    }
}

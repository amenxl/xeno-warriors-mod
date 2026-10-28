package com.xenowarriors.journey;

import com.xenowarriors.network.ModNetwork;
import com.xenowarriors.network.RequestPopupPacket;
import com.xenowarriors.npc.HumanNpcEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ServerLevelAccessor;

/**
 * The Journey Guide: a townsperson in the Element School's front hall who explains Journey mode and can send you
 * straight into setting one up (an Accept / Cancel card, or the [Start a Journey] link).
 */
public final class JourneyGuide {
    public static final String TAG = "xw_journey_guide";

    private JourneyGuide() {}

    public static boolean is(HumanNpcEntity h) { return h.getPersistentData().getBoolean(TAG); }

    /** Puts a guide here (the school is built with one; older schools get one the first time someone reads the Collection Book). */
    public static HumanNpcEntity place(ServerLevelAccessor l, double x, double y, double z, float yaw) {
        HumanNpcEntity h = com.xenowarriors.npc.ModNpcs.HUMAN.get().create(l.getLevel());
        if (h == null) return null;
        h.moveTo(x, y, z, yaw, 0);
        try { h.finalizeSpawn(l, l.getCurrentDifficultyAt(BlockPos.containing(x, y, z)), MobSpawnType.STRUCTURE, null, null); } catch (Throwable ignored) {}
        h.getPersistentData().putBoolean(TAG, true);
        h.setNoAi(true);
        h.setInvulnerable(true);
        h.setPersistenceRequired();
        h.setYHeadRot(yaw);
        h.setCustomName(Component.literal("Journey Guide").withStyle(ChatFormatting.AQUA));
        h.setCustomNameVisible(true);
        l.addFreshEntity(h);
        return h;
    }

    /** Next to the Collection Book: a guide if there isn't one close by. */
    public static void ensureNear(ServerLevel l, BlockPos book) {
        if (!l.getEntitiesOfClass(HumanNpcEntity.class, new net.minecraft.world.phys.AABB(book).inflate(24), JourneyGuide::is).isEmpty()) return;
        place(l, book.getX() + 3.5, book.getY(), book.getZ() + 0.5, 180F);
    }

    public static void talk(ServerPlayer p, HumanNpcEntity guide) {
        guide.getLookControl().setLookAt(p);
        p.sendSystemMessage(Component.literal("Journey Guide: ").withStyle(ChatFormatting.AQUA)
                .append(Component.literal("\"Ready for a Journey? Pick a party of 8 - or 4 each with one partner - and fight your way along a path of battles, "
                        + "scenarios, events and item panels to a boss. Nothing heals you in a battle, so spend your Journey Coins wisely between steps. "
                        + "Beat each stage to unlock the next, all the way up to Xeno.\"").withStyle(ChatFormatting.GRAY)));
        p.sendSystemMessage(Component.literal("[Start a Journey]").withStyle(s -> s.withColor(ChatFormatting.GREEN).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/xeno journey"))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Set up a Journey now")))));
        ModNetwork.sendToPlayer(new RequestPopupPacket("Journey Guide", "Want to set out on a Journey now?", "/xeno journey", "", 20 * 30, 0x40D0FF), p);
    }
}

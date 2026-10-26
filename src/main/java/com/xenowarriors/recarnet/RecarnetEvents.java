package com.xenowarriors.recarnet;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.Seasons;
import com.xenowarriors.npc.HumanNpcEntity;
import com.xenowarriors.npc.ModNpcs;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Recarnet rules: people instead of villagers, Aurora's barrier while Season 2 is locked, a busy city. */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RecarnetEvents {
    private RecarnetEvents() {}

    /** No Minecraft villagers in Recarnet - whoever would have been there is a person. */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel s) || !Recarnet.is(s) || !(event.getEntity() instanceof AbstractVillager v)) return;
        event.setCanceled(true);
        PENDING.add(new Object[]{s, v.position(), v.getYRot()});            // swapped in on the next server tick (never while a chunk loads)
    }

    private static final java.util.List<Object[]> PENDING = new java.util.ArrayList<>();

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || PENDING.isEmpty()) return;
        var list = new java.util.ArrayList<>(PENDING);
        PENDING.clear();
        for (Object[] o : list) {
            ServerLevel s = (ServerLevel) o[0];
            Vec3 at = (Vec3) o[1];
            HumanNpcEntity h = ModNpcs.HUMAN.get().create(s);
            if (h == null) continue;
            h.moveTo(at.x, at.y, at.z, (Float) o[2], 0);
            h.finalizeSpawn(s, new net.minecraft.world.DifficultyInstance(s.getDifficulty(), s.getDayTime(), 0L, s.getMoonBrightness()), MobSpawnType.CONVERSION, null, null);
            h.setPersistenceRequired();
            s.addFreshEntity(h);
        }
    }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p) || !Recarnet.is(p.level()) || p.tickCount % 10 != 0) return;
        // Aurora's barrier (F1 tech) is up until Season 2 is unlocked
        double d = Recarnet.dist(p.blockPosition());
        if (d < Recarnet.CITY_R + 1 && !Seasons.unlocked(p.server, 2) && !p.isSpectator() && !p.isCreative()) {
            Vec3 out = new Vec3(p.getX(), 0, p.getZ()).normalize().scale(Recarnet.CITY_R + 6);
            if (out.lengthSqr() < 1) out = new Vec3(0, 0, Recarnet.CITY_R + 6);
            int y = p.serverLevel().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) out.x, (int) out.z);
            p.teleportTo(out.x, y + 0.1, out.z);
            p.displayClientMessage(Component.literal("Aurora's barrier is up - the city opens with Season 2").withStyle(ChatFormatting.AQUA), true);
            p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 0.8F, 1.6F);
        }
        // Aurora at night: Sky Haters come out much more often
        if (d < Recarnet.CITY_R && p.tickCount % 400 == 0 && p.level().isNight() && !p.isCreative() && p.getRandom().nextInt(10) == 0)
            com.xenowarriors.npc.SkyHaterRaids.start(p, true);
    }

    /** Where you arrive: just outside Aurora's south gate. */
    public static BlockPos arrival(ServerLevel s) {
        int x = 0, z = Recarnet.CITY_R + 36;
        s.getChunk(x >> 4, z >> 4);
        int y = s.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        return new BlockPos(x, Math.max(y, s.getSeaLevel() + 1), z);
    }

    /** Travel between the overworld and Recarnet (the Recarnet Pass, or /xeno recarnet). */
    public static void travel(ServerPlayer p) {
        var server = p.server;
        if (Recarnet.is(p.level())) {
            ServerLevel ow = server.overworld();
            BlockPos back = p.getPersistentData().contains("xw_recarnet_back") ? BlockPos.of(p.getPersistentData().getLong("xw_recarnet_back")) : ow.getSharedSpawnPos();
            p.teleportTo(ow, back.getX() + 0.5, back.getY(), back.getZ() + 0.5, p.getYRot(), p.getXRot());
            p.displayClientMessage(Component.literal("Back to your world.").withStyle(ChatFormatting.GRAY), true);
        } else {
            ServerLevel rec = server.getLevel(Recarnet.LEVEL);
            if (rec == null) { p.displayClientMessage(Component.literal("Recarnet isn't there (datapack missing?)").withStyle(ChatFormatting.RED), false); return; }
            if (p.level().dimension() == Level.OVERWORLD) p.getPersistentData().putLong("xw_recarnet_back", p.blockPosition().asLong());
            BlockPos at = arrival(rec);
            p.teleportTo(rec, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 180F, 0F);
            p.displayClientMessage(Component.literal("Recarnet").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD), true);
            rec.playSound(null, at, SoundEvents.PORTAL_TRAVEL, SoundSource.PLAYERS, 0.4F, 1.4F);
        }
    }
}

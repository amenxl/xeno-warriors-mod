package com.xenowarriors.fox;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.ModCapabilities;
import com.xenowarriors.network.ModNetwork;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Pets have Karma too (Mikiko's foxes, Rhalix's winged companions): it grows with their level, refills over time, and
 * their special moves spend it (a fox's pounce). Sent to the owner each second for the pet HUD, along with each pet's
 * DragonMineZ Ki if it has any.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PetKarma {
    public static final String KEY = "xw_pet_karma";

    private PetKarma() {}

    public static float max(Entity e) { return 40F + 4F * Math.max(1, e.getPersistentData().getInt("xw_fox_level")); }

    public static float get(Entity e) { return e.getPersistentData().contains(KEY) ? e.getPersistentData().getFloat(KEY) : max(e); }

    public static boolean spend(Entity e, float amount) {
        float k = get(e);
        if (k < amount) return false;
        e.getPersistentData().putFloat(KEY, k - amount);
        return true;
    }

    /** The owner's pets that are out in the world right now (foxes first, then winged companions). */
    public static List<LivingEntity> out(ServerPlayer p) {
        List<LivingEntity> list = new ArrayList<>();
        p.getCapability(ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> {
            for (ListTag tags : new ListTag[]{d.pets, d.wingPets})
                for (int i = 0; i < tags.size() && list.size() < 4; i++) {
                    CompoundTag rec = tags.getCompound(i);
                    if (!rec.hasUUID("uuid") || rec.getBoolean("dead")) continue;
                    UUID id = rec.getUUID("uuid");
                    Entity e = p.serverLevel().getEntity(id);
                    if (e instanceof LivingEntity le && le.isAlive() && le.distanceToSqr(p) < 96 * 96) list.add(le);
                }
        });
        return list;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p) || p.tickCount % 20 != 0) return;
        List<LivingEntity> pets = out(p);
        int n = pets.size();
        int[] ids = new int[n]; float[] karma = new float[n], kmax = new float[n], ki = new float[n], kimax = new float[n];
        for (int i = 0; i < n; i++) {
            LivingEntity e = pets.get(i);
            float m = max(e), k = Math.min(m, get(e) + 2F);          // refills 2 a second
            e.getPersistentData().putFloat(KEY, k);
            ids[i] = e.getId(); karma[i] = k; kmax[i] = m;
            float[] dk = com.xenowarriors.compat.DmzPetKi.ki(e);
            ki[i] = dk[0]; kimax[i] = dk[1];
        }
        ModNetwork.sendToPlayer(new com.xenowarriors.network.PetBarsPacket(ids, karma, kmax, ki, kimax), p);
    }
}

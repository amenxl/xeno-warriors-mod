package com.xenowarriors.npc;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.corrupted.CorruptedVillagerEntity;
import com.xenowarriors.corrupted.HiddenSouls;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * Villages belong to people now: most villagers that turn up become townsfolk (a few villagers stay, and anyone you've
 * traded with, named, cured or who's a baby is left alone). Zombies go after people the way they go after villagers.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID)
public final class HumanVillages {
    private static final String CHECKED = "xw_village_checked";
    private static final float REPLACE = 0.75F;
    private static final List<Villager> QUEUE = new ArrayList<>();

    private HumanVillages() {}

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide) return;
        if (event.getEntity() instanceof Zombie z && !(z instanceof CorruptedVillagerEntity)) {
            z.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(z, HumanNpcEntity.class, true, e -> !((HumanNpcEntity) e).carriesSoul()));
            return;
        }
        if (!(event.getEntity() instanceof Villager v) || v.getType() != EntityType.VILLAGER) return;
        if (v.getTags().contains(CHECKED)) return;
        v.addTag(CHECKED);
        if (v.isBaby() || v.hasCustomName() || v.getVillagerXp() > 0 || HiddenSouls.hidden(v)) return;
        if (v.getGossips().store(net.minecraft.nbt.NbtOps.INSTANCE).getValue() instanceof net.minecraft.nbt.ListTag l && !l.isEmpty()) return;   // cured / has history
        if (v.getRandom().nextFloat() < REPLACE) QUEUE.add(v);     // swapped next tick (not while the chunk is loading)
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || QUEUE.isEmpty()) return;
        List<Villager> todo = new ArrayList<>(QUEUE);
        QUEUE.clear();
        for (Villager v : todo) {
            if (v.isRemoved() || !(v.level() instanceof ServerLevel s)) continue;
            HumanNpcEntity h = ModNpcs.HUMAN.get().create(s);
            if (h == null) continue;
            h.moveTo(v.getX(), v.getY(), v.getZ(), v.getYRot(), 0F);
            h.finalizeSpawn(s, s.getCurrentDifficultyAt(v.blockPosition()), MobSpawnType.STRUCTURE, null, null);
            h.setPersistenceRequired();
            v.discard();
            s.addFreshEntity(h);
        }
    }
}

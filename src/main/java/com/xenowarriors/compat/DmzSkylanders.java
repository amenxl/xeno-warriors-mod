package com.xenowarriors.compat;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.npc.ModNpcs;
import com.xenowarriors.npc.SkylanderEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

/**
 * With DragonMineZ: Skylanders turn up in its worlds - Namek most of all, and also the Otherworld and the Sacred
 * World of the Kai (not the Hyperbolic Time Chamber: that's a training room). They carry our Blaster guns (see
 * {@link DmzGuns}) and shoot them at whatever they fight. They're still Skylanders: friendly unless you've made the
 * Sky faction hate you, and on patrol against the monsters there. Killed, they sometimes drop their gun.
 * Spawned by code near players in those worlds (nothing is added to DragonMineZ's biomes), so it all only exists
 * with DragonMineZ installed.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DmzSkylanders {
    private DmzSkylanders() {}

    private static final int EVERY = 300;
    /** World -> chance per check, and how many may be about. */
    private static final Map<String, float[]> WORLDS = Map.of(
            "dragonminez:namek", new float[]{0.45F, 4},
            "dragonminez:otherworld", new float[]{0.25F, 3},
            "dragonminez:sacredkaiplanet", new float[]{0.25F, 2});

    public static final String TAG = "xw_dmz_patrol";

    /** (For the tests) spawns: "world at gun". */
    public static final List<String> LOG = Collections.synchronizedList(new ArrayList<>());

    public static boolean dmzWorld(ServerLevel l) { return WORLDS.containsKey(l.dimension().location().toString()); }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.getServer().getTickCount() % EVERY != 0 || !DmzGuns.on()) return;
        for (ServerPlayer p : e.getServer().getPlayerList().getPlayers()) {
            if (p.isSpectator() || !(p.level() instanceof ServerLevel level)) continue;
            float[] w = WORLDS.get(level.dimension().location().toString());
            if (w == null || level.random.nextFloat() >= w[0]) continue;
            if (level.getEntitiesOfClass(SkylanderEntity.class, p.getBoundingBox().inflate(96, 128, 96)).size() >= w[1]) continue;
            int n = 1 + (level.random.nextFloat() < 0.35F ? 1 : 0);                  // (sometimes a pair)
            BlockPos base = around(p.blockPosition(), level.random, 24, 48);
            for (int i = 0; i < n; i++) spawn(level, base.offset(level.random.nextInt(5) - 2, 0, level.random.nextInt(5) - 2), false);
        }
    }

    private static BlockPos around(BlockPos c, net.minecraft.util.RandomSource r, int min, int max) {
        double a = r.nextDouble() * Math.PI * 2, d = min + r.nextDouble() * (max - min);
        return c.offset((int) (Math.cos(a) * d), 0, (int) (Math.sin(a) * d));
    }

    /** An armed Skylander on the ground at (the surface by) at. Null if there was nowhere to stand. */
    public static SkylanderEntity spawn(ServerLevel level, BlockPos at, boolean persistent) {
        if (!level.isLoaded(at)) return null;
        BlockPos top = standable(level, at) ? at : level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at);   // (right there if there's floor, else the surface)
        if (top.getY() <= level.getMinBuildHeight() + 1 || !level.getBlockState(top.below()).isFaceSturdy(level, top.below(), net.minecraft.core.Direction.UP)
                || !level.isEmptyBlock(top) || !level.isEmptyBlock(top.above()) || !level.getFluidState(top.below()).isEmpty()) return null;
        SkylanderEntity sk = ModNpcs.SKYLANDER.get().create(level);
        if (sk == null) return null;
        sk.moveTo(top.getX() + 0.5, top.getY(), top.getZ() + 0.5, level.random.nextFloat() * 360F, 0F);
        sk.finalizeSpawn(level, level.getCurrentDifficultyAt(top), MobSpawnType.EVENT, null, null);   // (on their feet)
        sk.setFlying(false);
        ItemStack gun = gunFor(level);
        sk.setItemSlot(EquipmentSlot.MAINHAND, gun);
        sk.setDropChance(EquipmentSlot.MAINHAND, 0.2F);
        sk.getPersistentData().putBoolean(TAG, true);
        sk.getPersistentData().putBoolean("xw_gun_drop_set", true);
        if (persistent) sk.setPersistenceRequired();
        level.addFreshEntity(sk);
        String s = level.dimension().location() + " " + top.toShortString() + " " + net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(gun.getItem());
        if (LOG.size() > 30) LOG.remove(0);
        LOG.add(s);
        XenoWarriors.LOGGER.info("XW dmz skylanders: spawned {}", s);
        return sk;
    }

    private static boolean standable(ServerLevel l, BlockPos at) {
        return l.isEmptyBlock(at) && l.isEmptyBlock(at.above()) && l.getBlockState(at.below()).isFaceSturdy(l, at.below(), net.minecraft.core.Direction.UP);
    }

    /** Mostly rifles; burst cannons often; the odd mortar. */
    private static ItemStack gunFor(ServerLevel level) {
        float r = level.random.nextFloat();
        var ro = r < 0.5F ? com.xenowarriors.item.ModItems.BLASTER_RIFLE : r < 0.85F ? com.xenowarriors.item.ModItems.BURST_CANNON : com.xenowarriors.item.ModItems.BLASTER_MORTAR;
        return ro == null || !ro.isPresent() ? ItemStack.EMPTY : new ItemStack(ro.get());
    }

    /** Patrol Skylanders also go after the monsters in those worlds (as well as the Sky faction's usual enemies). */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onJoin(EntityJoinLevelEvent e) {
        if (e.getLevel().isClientSide || !(e.getEntity() instanceof SkylanderEntity sk) || !sk.getPersistentData().getBoolean(TAG)) return;
        sk.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(sk, Mob.class, 10, true, false,
                m -> m instanceof Enemy && !(m instanceof SkylanderEntity) && !(m instanceof com.xenowarriors.bot.PartyBotEntity)));
    }
}

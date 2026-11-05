package com.xenowarriors.compat;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.bot.PartyBotEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;

/**
 * With DragonMineZ, help with its missions:
 * <ul>
 *   <li>A mission that wants you to defeat something that never turns up by itself (a story fighter - DragonMineZ's
 *       saga characters - on an objective DragonMineZ doesn't spawn for you): a few seconds after the objective is
 *       up, the ones still needed are summoned near you (once per objective), tagged so they count for it.</li>
 *   <li>A mission that wants items: while you still need them, every hostile mob you (or your bot) kill has a 30%
 *       chance to drop one of them.</li>
 * </ul>
 * Reflection against DragonMineZ 2.1.x; nothing happens without it.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DmzQuestHelp {
    private DmzQuestHelp() {}

    public static float ITEM_CHANCE = 0.30F;
    private static final String SUMMONED = "xw_dmz_qsum";
    private static Boolean loaded;
    private static boolean broken;
    private static Class<?> sagaEntity, killObj, itemObj;
    private static final Map<EntityType<?>, Boolean> STORY_ONLY = new HashMap<>();

    /** (For the tests) what happened, newest last. */
    public static final List<String> LOG = Collections.synchronizedList(new ArrayList<>());
    public static int itemRolls, itemDrops;

    private static void note(String s) {
        if (LOG.size() > 60) LOG.remove(0);
        LOG.add(s);
        XenoWarriors.LOGGER.info("XW dmz quests: {}", s);
    }

    private static boolean ready() {
        if (loaded == null) loaded = ModList.get() != null && ModList.get().isLoaded("dragonminez");
        if (!loaded || broken) return false;
        if (killObj != null) return true;
        try {
            sagaEntity = Class.forName("com.dragonminez.common.init.entities.sagas.DBSagasEntity");
            itemObj = Class.forName("com.dragonminez.common.quest.objectives.ItemObjective");
            killObj = Class.forName("com.dragonminez.common.quest.objectives.KillObjective");
            return true;
        } catch (Throwable t) {
            broken = true;
            XenoWarriors.LOGGER.warn("DragonMineZ mission help off (its API changed?)", t);
            return false;
        }
    }

    private static Object m(Object o, String name, Object... args) throws Exception {
        for (var mm : o.getClass().getMethods())
            if (mm.getName().equals(name) && mm.getParameterCount() == args.length) return mm.invoke(o, args);
        throw new NoSuchMethodException(name);
    }

    /** One objective still to do on an accepted mission. */
    record Open(String key, int index, Object quest, Object objective, int progress, int required) {}

    /** The objectives of the player's accepted missions that are up now (unlocked) and not done yet. */
    static List<Open> open(ServerPlayer p) {
        List<Open> out = new ArrayList<>();
        Object data = DmzPerCharacter.live(p);
        if (data == null || !ready() || DmzPerCharacter.viewing(p) != null) return out;
        try {
            Object pqd = m(data, "getPlayerQuestData");
            @SuppressWarnings("unchecked") Set<String> ids = new HashSet<>((Set<String>) m(pqd, "getAcceptedQuestIds"));
            Class<?> service = Class.forName("com.dragonminez.common.quest.QuestService");
            for (String key : ids) {
                if (!"ACCEPTED".equals(String.valueOf(m(pqd, "getQuestStatus", key)))) continue;
                Object rq = service.getMethod("resolveQuest", String.class).invoke(null, key);
                if (rq == null) continue;
                Object q = m(rq, "quest");
                if (q == null) continue;
                List<?> objs = (List<?>) m(q, "getObjectives");
                boolean parallel = (Boolean) m(q, "isParallelObjectives");
                boolean earlierOpen = false;
                for (int i = 0; i < objs.size(); i++) {
                    int prog = (Integer) m(pqd, "getObjectiveProgress", key, i);
                    int req = (Integer) m(q, "getObjectiveRequired", pqd, key, i);
                    boolean done = prog >= req;
                    if (!done && (parallel || !earlierOpen)) out.add(new Open(key, i, q, objs.get(i), prog, req));
                    if (!done) earlierOpen = true;
                }
            }
        } catch (Throwable t) {
            broken = true;
            XenoWarriors.LOGGER.warn("DragonMineZ mission help off (reading missions failed)", t);
        }
        return out;
    }

    /** Never spawns by itself: one of DragonMineZ's story (saga) fighters. */
    static boolean storyOnly(ServerLevel level, EntityType<?> type) {
        return STORY_ONLY.computeIfAbsent(type, t -> {
            try {
                var e = t.create(level);
                boolean s = e != null && sagaEntity.isInstance(e);
                if (e != null) e.discard();
                return s;
            } catch (Throwable ex) { return false; }
        });
    }

    // ------------------------------------------------------------------ summoning story enemies

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p instanceof FakePlayer || p.tickCount % 40 != 17 || !ready()) return;
        if (p.isSpectator() || com.xenowarriors.modes.ModeRules.arena(p.level())) return;
        for (Open o : open(p)) {
            try {
                if (!killObj.isInstance(o.objective)) continue;
                if ((Boolean) m(o.objective, "isTag")) continue;
                if (!"NATURAL".equals(String.valueOf(m(o.objective, "getSpawnMode")))) continue;   // (QUEST mode: DragonMineZ summons them itself)
                EntityType<?> type = (EntityType<?>) m(o.objective, "resolveEntityType");
                if (type == null || !storyOnly(p.serverLevel(), type)) continue;
                CompoundTag done = summoned(p);
                String id = o.key + "#" + o.index;
                if (done.getBoolean(id)) continue;
                done.putBoolean(id, true);
                int n = Math.max(1, Math.min(5, o.required - o.progress));
                double hp = ((Number) m(o.objective, "getHealth")).doubleValue();
                p.displayClientMessage(Component.literal("Your mission's target has sensed your ki... ").append(type.getDescription()).append(" is coming!")
                        .withStyle(ChatFormatting.GOLD), false);
                note("summoning " + n + " x " + ForgeRegistries.ENTITY_TYPES.getKey(type) + " for " + id + " (" + o.progress + "/" + o.required + ") near " + p.getGameProfile().getName());
                for (int k = 0; k < n; k++) {
                    int idx = o.index; String key = o.key;
                    com.xenowarriors.skill.ServerScheduler.later(100 + k * 30, () -> summon(p, type, key, idx, hp));
                }
            } catch (Throwable t) { XenoWarriors.LOGGER.warn("DragonMineZ mission summon failed", t); }
        }
    }

    private static CompoundTag summoned(ServerPlayer p) {
        CompoundTag keep = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (!keep.contains(SUMMONED)) keep.put(SUMMONED, new CompoundTag());
        p.getPersistentData().put(Player.PERSISTED_NBT_TAG, keep);
        return keep.getCompound(SUMMONED);
    }

    /** (For the tests) forget which objectives already had their enemies summoned. */
    public static void resetSummonedForTest(ServerPlayer p) { summoned(p).getAllKeys().clear(); p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).put(SUMMONED, new CompoundTag()); }

    /** A safe spot 6-12 blocks from the player: solid ground, room to stand. */
    public static BlockPos safeSpot(ServerPlayer p, EntityType<?> type) {
        ServerLevel l = p.serverLevel();
        var r = p.getRandom();
        for (int i = 0; i < 24; i++) {
            double a = r.nextDouble() * Math.PI * 2, d = 6 + r.nextDouble() * 6;
            int x = (int) Math.floor(p.getX() + Math.cos(a) * d), z = (int) Math.floor(p.getZ() + Math.sin(a) * d);
            int y = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (Math.abs(y - p.getY()) > 8) y = (int) Math.floor(p.getY());
            BlockPos at = new BlockPos(x, y, z);
            if (!l.getBlockState(at.below()).isSolid() || !l.getFluidState(at).isEmpty()) continue;
            if (l.noCollision(type.getAABB(x + 0.5, y, z + 0.5))) return at;
        }
        return null;
    }

    private static void summon(ServerPlayer p, EntityType<?> type, String key, int index, double hp) {
        if (!p.isAlive() || p.hasDisconnected()) return;
        BlockPos at = safeSpot(p, type);
        if (at == null) at = p.blockPosition();
        var e = type.create(p.serverLevel());
        if (!(e instanceof LivingEntity le)) { if (e != null) e.discard(); return; }
        le.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, p.getRandom().nextFloat() * 360F, 0F);
        if (le instanceof Mob mob) {
            mob.finalizeSpawn(p.serverLevel(), p.serverLevel().getCurrentDifficultyAt(at), MobSpawnType.EVENT, null, null);
            mob.setTarget(p);
            mob.setPersistenceRequired();
        }
        var tag = le.getPersistentData();                                        // (DragonMineZ counts it for this objective)
        tag.putString("dmz_quest_key", key);
        tag.putInt("dmz_quest_objective_index", index);
        tag.putString("dmz_quest_owner", p.getStringUUID());
        tag.putBoolean("xw_quest_summon", true);
        if (hp > 0 && le.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH) != null) {
            le.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(hp);
            le.setHealth((float) hp);
        }
        p.serverLevel().addFreshEntity(le);
        p.serverLevel().sendParticles(net.minecraft.core.particles.ParticleTypes.EXPLOSION, le.getX(), le.getY() + 1, le.getZ(), 2, 0.3, 0.5, 0.3, 0);
        p.serverLevel().playSound(null, le.blockPosition(), net.minecraft.sounds.SoundEvents.LIGHTNING_BOLT_THUNDER, net.minecraft.sounds.SoundSource.HOSTILE, 0.6F, 1.6F);
        note("summoned " + ForgeRegistries.ENTITY_TYPES.getKey(type) + " at " + at.toShortString() + " (" + String.format(Locale.ROOT, "%.1f", Math.sqrt(le.distanceToSqr(p))) + " blocks) hp " + le.getMaxHealth());
    }

    // ------------------------------------------------------------------ item objectives

    /** item -> how many more the player needs, for their open item objectives. */
    public static Map<Item, Integer> needs(ServerPlayer p) {
        Map<Item, Integer> out = new LinkedHashMap<>();
        for (Open o : open(p)) {
            try {
                if (!itemObj.isInstance(o.objective)) continue;
                Item it = ForgeRegistries.ITEMS.getValue(new ResourceLocation(String.valueOf(m(o.objective, "getItemId"))));
                if (it == null || it == net.minecraft.world.item.Items.AIR) continue;
                int have = 0;
                for (ItemStack s : p.getInventory().items) if (s.is(it)) have += s.getCount();
                for (ItemStack s : p.getInventory().offhand) if (s.is(it)) have += s.getCount();
                if (have < o.required) out.merge(it, o.required - have, Math::max);
            } catch (Throwable ignored) {}
        }
        return out;
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onKill(LivingDeathEvent e) {
        LivingEntity dead = e.getEntity();
        if (e.isCanceled() || dead.level().isClientSide || !(dead instanceof Enemy) || dead instanceof Player || !ready()) return;
        ServerPlayer p = e.getSource().getEntity() instanceof ServerPlayer sp && !(sp instanceof FakePlayer) ? sp : null;
        if (p == null) {
            PartyBotEntity b = DmzBots.killerBot(e.getSource().getEntity());
            if (b != null) p = b.owner();
        }
        if (p == null) return;
        Map<Item, Integer> need = needs(p);
        if (need.isEmpty()) return;
        itemRolls++;
        if (dead.getRandom().nextFloat() >= ITEM_CHANCE) return;
        List<Item> list = new ArrayList<>(need.keySet());
        Item it = list.get(dead.getRandom().nextInt(list.size()));
        dead.spawnAtLocation(new ItemStack(it));
        itemDrops++;
        note("mission item dropped: " + ForgeRegistries.ITEMS.getKey(it) + " from " + ForgeRegistries.ENTITY_TYPES.getKey(dead.getType()) + " (" + itemDrops + "/" + itemRolls + " kills)");
    }
}

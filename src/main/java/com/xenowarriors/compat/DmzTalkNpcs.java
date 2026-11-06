package com.xenowarriors.compat;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.bot.PartyBotEntity;
import com.xenowarriors.bot.PartyBots;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;

/**
 * With DragonMineZ: a mission that ends by talking to someone (a "talk to" objective, or handing it in to a quest
 * NPC or master) brings that person to you once everything else is done - a temporary copy appears near you, in any
 * dimension, so you can talk to them right there. Talking to them works exactly like the real one (it's the same kind
 * of entity with the same id); when the mission is done (or abandoned, or finished elsewhere), after 10 minutes, or
 * when you log out, they vanish in a puff of smoke. Not when the real one is already within 32 blocks; one per
 * mission; never saved into the world. Works for your bots' own missions too (they come to you, the owner, as soon as
 * the talk is the bot's next objective - the bot walks over and talks to them itself, see DmzBotTalk, or you can).
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DmzTalkNpcs {
    private DmzTalkNpcs() {}

    public static final String TAG = "xw_temp_npc";
    public static int LIFETIME = 20 * 60 * 10;
    private static final double REAL_NEAR = 32;

    private record Need(String id, String quest, String npc, String bot) {}
    private record Temp(Entity e, String npc, long born) {}

    private static final Map<UUID, Map<String, Temp>> TEMPS = new HashMap<>();
    private static final Map<UUID, Set<String>> EXPIRED = new HashMap<>();
    private static Map<String, EntityType<?>> masters;

    /** (For the tests) what happened, newest last. */
    public static final List<String> LOG = Collections.synchronizedList(new ArrayList<>());

    private static void note(String s) {
        if (LOG.size() > 60) LOG.remove(0);
        LOG.add(s);
        XenoWarriors.LOGGER.info("XW dmz talk npcs: {}", s);
    }

    // ------------------------------------------------------------------ who's waiting to be talked to

    /** The people the player's missions (and their bots' missions) are waiting on: only a talk / hand-in left. */
    private static List<Need> needs(ServerPlayer p) {
        List<Need> out = new ArrayList<>();
        try {
            if (DmzPerCharacter.viewing(p) == null) {
                Object sd = DmzPerCharacter.live(p);
                if (sd != null) needsOf(DmzBotQuests.m(sd, "getPlayerQuestData"), null, out);
            }
            for (PartyBotEntity b : PartyBots.bots(p)) {
                if (b.journey()) continue;
                Object pqd = DmzBotQuests.peek(b);                                   // (its mission data only, cached: not its whole DMZ character)
                if (pqd != null) needsOf(pqd, DmzPerCharacter.botKey(b), out);
            }
        } catch (Throwable t) { XenoWarriors.LOGGER.warn("Couldn't read DragonMineZ missions for their talk objectives", t); }
        return out;
    }

    private static void needsOf(Object pqd, String bot, List<Need> out) throws Exception {
        for (String key : DmzBotQuests.accepted(pqd)) {
            Object q = DmzBotQuests.quest(key);
            if (q == null || (Boolean) DmzBotQuests.m(pqd, "isQuestCompleted", key) || !DmzBotQuests.typeEnabled(q)) continue;
            String npc = DmzBotQuests.waitingOn(pqd, key, q, bot == null);         // (yours: once the talk is all that's left; a bot's: when it's next - it may walk over itself)
            if (npc != null && !npc.isBlank()) out.add(new Need((bot == null ? "" : bot + "|") + key, key, npc, bot));
        }
    }

    // ------------------------------------------------------------------ bringing them over, sending them away

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p instanceof FakePlayer || p.tickCount % 20 != 13 || !DmzBotQuests.ready()) return;
        if (DmzPerCharacter.viewing(p) != null) return;                          // (a bot's menu is open: its data is in yours for now)
        update(p);
    }

    /** Brings over whoever the missions wait on, and sends away those no longer needed. */
    public static void update(ServerPlayer p) {
        Map<String, Temp> temps = TEMPS.computeIfAbsent(p.getUUID(), u -> new HashMap<>());
        Set<String> expired = EXPIRED.computeIfAbsent(p.getUUID(), u -> new HashSet<>());
        List<Need> needs = needs(p);
        Map<String, Need> byId = new HashMap<>();
        for (Need n : needs) byId.put(n.id, n);
        expired.retainAll(byId.keySet());
        long now = p.level().getGameTime();
        for (Iterator<Map.Entry<String, Temp>> it = temps.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, Temp> en = it.next();
            Temp t = en.getValue();
            String why = null;
            boolean puff = true;
            if (t.e.isRemoved() || !t.e.isAlive()) { why = "gone"; puff = false; }
            else if (!byId.containsKey(en.getKey())) why = "mission done";
            else if (now - t.born > LIFETIME || now < t.born) { why = "timed out"; expired.add(en.getKey()); }
            else if (t.e.level() != p.level() || t.e.distanceToSqr(p) > 64 * 64) { why = "left behind"; puff = false; }   // (they come again, near you)
            if (why == null) continue;
            it.remove();
            send(temps, t, puff, why + " (" + en.getKey() + ")");
        }
        for (Need n : needs) {
            if (temps.containsKey(n.id) || expired.contains(n.id)) continue;
            Temp shared = null;                                                       // (you and a bot waiting on the same person: one comes)
            for (Temp t : temps.values()) if (t.npc.equals(n.npc) && t.e.isAlive()) shared = t;
            if (shared != null) { temps.put(n.id, shared); continue; }
            if (realNearby(p, n.npc)) continue;
            Entity e = create(p, n.npc);
            if (e == null) continue;
            temps.put(n.id, new Temp(e, n.npc, now));
            poof(e);
            Component who = e.getDisplayName();
            p.displayClientMessage(Component.empty().append(who).append(" came to see you - talk to them to finish ")
                    .append(n.bot == null ? Component.literal("") : Component.literal(n.bot + "'s "))
                    .append(DmzBotQuests.title(n.quest)).append(".").withStyle(ChatFormatting.GOLD), false);
            note("brought " + n.npc + " (" + ForgeRegistries.ENTITY_TYPES.getKey(e.getType()) + ") to " + p.getGameProfile().getName() + " for " + n.id
                    + String.format(Locale.ROOT, " at %.1f blocks", Math.sqrt(e.distanceToSqr(p))));
        }
    }

    /** Removes a temporary copy (unless another of the player's missions still uses the same one). */
    private static void send(Map<String, Temp> temps, Temp t, boolean puff, String why) {
        for (Temp o : temps.values()) if (o.e == t.e) return;
        if (!t.e.isRemoved()) {
            if (puff) poof(t.e);
            t.e.discard();
        }
        note("sent away " + t.npc + ": " + why);
    }

    private static void poof(Entity e) {
        if (!(e.level() instanceof ServerLevel l)) return;
        l.sendParticles(ParticleTypes.POOF, e.getX(), e.getY() + e.getBbHeight() / 2, e.getZ(), 24, 0.35, 0.6, 0.35, 0.02);
        l.playSound(null, e.blockPosition(), SoundEvents.CHICKEN_EGG, SoundSource.NEUTRAL, 0.8F, 0.6F);
    }

    static String idOf(Entity e) {
        try {
            for (String m : new String[]{"getNpcId", "getMasterName"})
                for (var mm : e.getClass().getMethods()) if (mm.getName().equals(m) && mm.getParameterCount() == 0) return (String) mm.invoke(e);
        } catch (Throwable ignored) {}
        return null;
    }

    /** Is the real one (not a copy) within 32 blocks? */
    private static boolean realNearby(ServerPlayer p, String npc) {
        for (Entity e : p.serverLevel().getEntities((Entity) null, p.getBoundingBox().inflate(REAL_NEAR), x -> !x.getPersistentData().getBoolean(TAG)
                && ForgeRegistries.ENTITY_TYPES.getKey(x.getType()).getNamespace().equals("dragonminez")))
            if (npc.equals(idOf(e))) return true;
        return false;
    }

    /** master name -> its entity type (the masters are each their own kind). */
    private static EntityType<?> master(ServerLevel l, String name) {
        if (masters == null) {
            masters = new HashMap<>();
            for (EntityType<?> t : ForgeRegistries.ENTITY_TYPES) {
                ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(t);
                if (id == null || !id.getNamespace().equals("dragonminez") || !id.getPath().startsWith("master")) continue;
                try {
                    Entity e = t.create(l);
                    if (e == null) continue;
                    String n = idOf(e);
                    e.discard();
                    if (n != null) masters.put(n, t);
                } catch (Throwable ignored) {}
            }
        }
        return masters.get(name);
    }

    /** A copy of the person: from DragonMineZ's own NPC placements (same kind, look and standing), else its master or a quest NPC with that id. */
    private static Entity create(ServerPlayer p, String npc) {
        ServerLevel l = p.serverLevel();
        try {
            EntityType<?> type = null;
            String model = null, texture = null, relation = null;
            Integer alignment = null;
            try {
                var f = Class.forName("com.dragonminez.server.world.npc.NPCPlacementManager").getDeclaredField("placements");
                f.setAccessible(true);
                for (Object pl : (List<?>) f.get(null)) {
                    if (!npc.equals(DmzBotQuests.m(pl, "npcId"))) continue;
                    type = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation((String) DmzBotQuests.m(pl, "entity")));
                    model = (String) DmzBotQuests.m(pl, "model");
                    texture = (String) DmzBotQuests.m(pl, "texture");
                    alignment = (Integer) DmzBotQuests.m(pl, "alignment");
                    relation = (String) DmzBotQuests.m(pl, "relationOverride");
                    break;
                }
            } catch (Throwable ignored) {}
            if (type == null) type = master(l, npc);
            if (type == null) type = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("dragonminez", "quest_npc"));
            if (type == null) return null;
            Entity e = type.create(l);
            if (e == null) return null;
            if (hasSetter(e, "setNpcId")) {
                DmzBotQuests.m(e, "setNpcId", npc);
                DmzBotQuests.m(e, "setNpcModel", model == null ? "" : model);
                DmzBotQuests.m(e, "setNpcTexture", texture == null ? "" : texture);
            }
            if (!npc.equals(idOf(e))) { e.discard(); note("can't make " + npc); return null; }
            if (alignment != null) e.getPersistentData().putInt("DmzNpcAlignment", Math.max(0, Math.min(100, alignment)));
            if (relation != null && !relation.isBlank()) e.getPersistentData().putString("DmzNpcRelationOverride", relation);
            e.getPersistentData().putBoolean(TAG, true);
            e.getPersistentData().putString(TAG + "_owner", p.getStringUUID());
            BlockPos at = DmzQuestHelp.safeSpot(p, type);
            if (at == null) at = p.blockPosition();
            float yaw = (float) Math.toDegrees(Math.atan2(-(p.getX() - (at.getX() + 0.5)), p.getZ() - (at.getZ() + 0.5)));
            e.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, yaw, 0F);
            e.setYHeadRot(yaw);
            e.setInvulnerable(true);
            if (e instanceof Mob mob) { mob.setNoAi(true); mob.setYBodyRot(yaw); }
            if (!l.addFreshEntity(e)) return null;
            return e;
        } catch (Throwable t) {
            XenoWarriors.LOGGER.warn("Couldn't bring a DragonMineZ NPC over", t);
            return null;
        }
    }

    private static boolean hasSetter(Entity e, String name) {
        for (var mm : e.getClass().getMethods()) if (mm.getName().equals(name) && mm.getParameterCount() == 1) return true;
        return false;
    }

    // ------------------------------------------------------------------ never kept

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        Map<String, Temp> temps = TEMPS.remove(p.getUUID());
        EXPIRED.remove(p.getUUID());
        if (temps != null) for (Temp t : new HashSet<>(temps.values())) if (!t.e.isRemoved()) t.e.discard();
    }

    @SubscribeEvent
    public static void onStopping(net.minecraftforge.event.server.ServerStoppingEvent e) {
        for (Map<String, Temp> m : TEMPS.values()) for (Temp t : m.values()) if (!t.e.isRemoved()) t.e.discard();
        TEMPS.clear();
        EXPIRED.clear();
    }

    /** A copy that was saved with its chunk anyway (it got unloaded) never comes back. */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent e) {
        if (e.loadedFromDisk() && e.getEntity().getPersistentData().getBoolean(TAG)) {
            e.setCanceled(true);
            note("dropped a saved temporary " + idOf(e.getEntity()));
        }
    }

    // ------------------------------------------------------------------ for the tests

    /** The copy currently brought over for this mission ("" for the player's own, "botKey|" prefix for a bot's), or null. */
    public static Entity tempFor(ServerPlayer p, String id) {
        Map<String, Temp> m = TEMPS.get(p.getUUID());
        Temp t = m == null ? null : m.get(id);
        return t == null ? null : t.e;
    }

    public static int count(ServerPlayer p) {
        Map<String, Temp> m = TEMPS.get(p.getUUID());
        return m == null ? 0 : (int) m.values().stream().map(Temp::e).distinct().filter(Entity::isAlive).count();
    }
}

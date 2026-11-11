package com.xenowarriors.compat;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.bot.PartyBotEntity;
import com.xenowarriors.bot.PartyBots;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

/**
 * With DragonMineZ: a bot uses the DragonMineZ skills its character has learned (from masters - see
 * {@link DmzBotTechniques}). <b>Fly</b>: a bot whose character knows Fly flies like the bots that fly by themselves do
 * - after a flying owner, up to flying enemies, over gaps and ledges (PartyBotEntity.canFly) - and flying costs it ki
 * (less at higher Fly levels); out of ki it lands until it has some back. <b>Kaioken</b> is used in fights by
 * {@link DmzBotPower}. Passive skills (Ki Boost, Ki Protection, Potential Unlock...) work through its DragonMineZ stats
 * as they would for a player.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DmzBotSkills {
    private DmzBotSkills() {}

    /** Share of its max ki a second of flying costs at Fly level 1 (each level above makes it 15% cheaper). Lands at 5%, flies again at 20%. */
    public static final float FLY_KI = 0.005F, FLY_STOP = 0.05F, FLY_AGAIN = 0.2F;

    private static Boolean loaded;
    private static final Map<UUID, Integer> FLY_LEVEL = new HashMap<>();
    private static final Set<UUID> GROUNDED = new HashSet<>();

    /** (Tests) what happened. */
    public static final List<String> LOG = Collections.synchronizedList(new ArrayList<>());
    private static void note(String s) {
        if (LOG.size() > 60) LOG.remove(0);
        LOG.add(s);
        XenoWarriors.LOGGER.info("XW dmz bot skills: {}", s);
    }

    private static boolean on() {
        if (loaded == null) loaded = ModList.get() != null && ModList.get().isLoaded("dragonminez");
        return loaded;
    }

    /** A skill's level in this saved DragonMineZ character (0 = not learned). */
    public static int level(CompoundTag tag, String skill) {
        if (tag == null) return 0;
        ListTag l = tag.getCompound("Skills").getList("SkillsList", 10);
        for (int i = 0; i < l.size(); i++) if (skill.equalsIgnoreCase(l.getCompound(i).getString("Name"))) return l.getCompound(i).getInt("Level");
        return 0;
    }

    /** Looks at its saved character's Fly level again (after it learns or levels up a skill). */
    static void refresh(PartyBotEntity b) {
        ServerPlayer o = b.owner();
        if (o == null) return;
        int lv = level(DmzPerCharacter.saved(o, DmzPerCharacter.botKey(b)), "fly");
        Integer old = FLY_LEVEL.put(b.getUUID(), lv);
        if (old == null || old != lv) note(b.character().getId() + " Fly level " + lv);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer o) || o instanceof FakePlayer || o.tickCount % 10 != 7 || !on()) return;
        for (PartyBotEntity b : PartyBots.bots(o)) {
            try { tick(b, o); } catch (Throwable t) { XenoWarriors.LOGGER.warn("A bot's DragonMineZ skills", t); }
        }
    }

    /** Every half second, per bot. */
    static void tick(PartyBotEntity b, ServerPlayer o) {
        if (b.tickCount % 100 < 10 || !FLY_LEVEL.containsKey(b.getUUID())) refresh(b);
        int lv = FLY_LEVEL.getOrDefault(b.getUUID(), 0);
        boolean can = lv > 0 && !b.downed();
        if (can && !b.naturalFlier()) {
            float ki = DmzBotPower.kiFraction(b);
            if (ki >= 0) {
                boolean airborne = flying(b);
                if (airborne) DmzBotPower.useKi(b, FLY_KI / 2F / (1F + 0.15F * (lv - 1)));   // (half a second of flight)
                ki = DmzBotPower.kiFraction(b);
                if (ki < FLY_STOP && GROUNDED.add(b.getUUID())) note(b.character().getId() + " lands: out of ki for flying (" + Math.round(ki * 100) + "%)");
                if (ki >= FLY_AGAIN && GROUNDED.remove(b.getUUID())) note(b.character().getId() + " can fly again (" + Math.round(ki * 100) + "% ki)");
                if (GROUNDED.contains(b.getUUID())) can = false;
            }
        }
        if (!can && b.dmzFly() && !b.naturalFlier() && b.isNoGravity() && !b.isVehicle()) {      // (it can't any more: down gently)
            b.setNoGravity(false);
            b.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 100, 0, false, false));
        }
        b.setDmzFly(can);
    }

    /** Up in the air on its own (not standing, riding or swimming) - flying, for a bot that has Fly. */
    public static boolean flying(PartyBotEntity b) { return !b.onGround() && !b.isPassenger() && !b.isInWater() && (b.isNoGravity() || b.fallDistance == 0); }

    /** A bot is gone. */
    public static void forget(UUID bot) { FLY_LEVEL.remove(bot); GROUNDED.remove(bot); }

    /** (Tests) its Fly level as last seen. */
    public static int flyLevelForTest(PartyBotEntity b) { refresh(b); return FLY_LEVEL.getOrDefault(b.getUUID(), 0); }
}

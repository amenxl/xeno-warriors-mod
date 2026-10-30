package com.xenowarriors.scaling;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.ModCapabilities;
import com.xenowarriors.network.EnemyLevelPacket;
import com.xenowarriors.network.ModNetwork;
import com.xenowarriors.stats.Stat;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Enemies and NPCs get a level when they first appear: higher the farther from spawn (danger zones every 1,000 blocks),
 * and pulled toward the level of the nearest player - with a spread, so some are weaker (you feel strong), most are
 * close, and a few are tougher. Level makes them tankier and hit harder (tapering like player stats), raises their
 * Karma, and pays out more XP.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class EnemyScaling {
    private static final String LVL = "xw_lvl";
    private static final UUID HP = UUID.fromString("9d1c5e2a-3f41-4b8a-a0c2-7e5f1d3b9a01"), DMG = UUID.fromString("9d1c5e2a-3f41-4b8a-a0c2-7e5f1d3b9a02"),
            ARM = UUID.fromString("9d1c5e2a-3f41-4b8a-a0c2-7e5f1d3b9a03"), DBZ_HP = UUID.fromString("9d1c5e2a-3f41-4b8a-a0c2-7e5f1d3b9a04"),
            DBZ_DMG = UUID.fromString("9d1c5e2a-3f41-4b8a-a0c2-7e5f1d3b9a05");
    private static final Map<UUID, Integer> ZONE = new HashMap<>();

    private EnemyScaling() {}

    public static boolean scales(LivingEntity e) {
        return e instanceof Enemy || e instanceof com.xenowarriors.npc.HumanNpcEntity || e instanceof com.xenowarriors.npc.SkylanderEntity || e instanceof com.xenowarriors.gov.GovAgentEntity;
    }

    /** Danger from distance: +1 per 1,000 blocks from world spawn (tapering past 10). */
    public static int zone(ServerLevel level, BlockPos pos) {
        BlockPos s = level.getSharedSpawnPos();
        double d = Math.sqrt(s.distSqr(new BlockPos(pos.getX(), s.getY(), pos.getZ())));
        if (level.dimension() != net.minecraft.world.level.Level.OVERWORLD) d = d * 2 + 3000;   // other dimensions are rougher
        double z = d / 1000.0;
        return (int) (z <= 10 ? z : 10 + Math.sqrt(z - 10) * 2);
    }

    public static int level(LivingEntity e) { return e.getPersistentData().getInt(LVL); }

    /** Level as it counts (tapers off like player stats so huge levels don't run away). */
    public static float effective(LivingEntity e) { return Stat.effective(level(e)); }

    /** Damage multiplier for the extra things they do (elements, KAs). */
    public static float damageMult(LivingEntity e) { return (1F + effective(e) * 0.06F) * Math.max(1F, e.getPersistentData().getFloat("xw_dbz_dmg")); }

    private static int playerPower(ServerPlayer p) {
        return p.getCapability(ModCapabilities.PLAYER_CHARACTER).map(d -> (d.statLevel[0] + d.statLevel[1] * 2 + d.statLevel[2]) / 4).orElse(0);
    }

    private static void assign(Mob m) {
        ServerLevel level = (ServerLevel) m.level();
        var r = m.getRandom();
        int zone = zone(level, m.blockPosition());
        ServerPlayer near = (ServerPlayer) level.getNearestPlayer(m, 96);
        int pp = near != null && ModCapabilities.getType(near) != com.xenowarriors.character.CharacterType.NONE ? playerPower(near) : 0;
        // spread: 30% weaker, 55% close, 15% tougher
        float roll = r.nextFloat();
        float pull = roll < 0.3F ? 0.35F + r.nextFloat() * 0.3F : roll < 0.85F ? 0.7F + r.nextFloat() * 0.3F : 1.0F + r.nextFloat() * 0.25F;
        // Journey, Tower and VR enemies are scaled by their mode alone (stacking this on top made Easy impossible, more so with DragonMineZ)
        var tag = m.getPersistentData();
        if (tag.getBoolean("xw_journey") || tag.getBoolean("xw_tower") || com.xenowarriors.modes.ModeRules.arena(level)) {
            tag.putInt(LVL, Math.max(1, pp));
            ModNetwork.sendToTrackingEntityAndSelf(new EnemyLevelPacket(m.getId(), Math.max(1, pp)), m);
            return;
        }
        int lvl = Math.max(0, Math.round(zone * 4 + pp * pull) + r.nextInt(3));
        if (m instanceof com.xenowarriors.corrupted.CSoulBossEntity) lvl += 10 + zone * 2;
        if (m instanceof com.xenowarriors.npc.SkylanderEntity s && s.guardian()) lvl += 5;
        m.getPersistentData().putInt(LVL, lvl);
        float eff = Stat.effective(lvl);
        // level matters a lot (the user's call): a level-50 enemy has ~6x the health and hits ~4x as hard
        mod(m.getAttribute(Attributes.MAX_HEALTH), HP, eff * 0.10, AttributeModifier.Operation.MULTIPLY_BASE);
        mod(m.getAttribute(Attributes.ATTACK_DAMAGE), DMG, eff * 0.06, AttributeModifier.Operation.MULTIPLY_BASE);
        mod(m.getAttribute(Attributes.ARMOR), ARM, Math.min(20, eff * 0.25), AttributeModifier.Operation.ADDITION);
        // with DragonMineZ, players get far stronger (skills follow Ki/strike damage, health follows Vitality): enemies keep up
        if (near != null && net.minecraftforge.fml.ModList.get().isLoaded("dragonminez")) {
            double dm = Math.max(com.xenowarriors.compat.DmzScaling.multiplier(near, true), com.xenowarriors.compat.DmzScaling.multiplier(near, false));
            double hpRatio = Math.max(1.0, near.getMaxHealth() / 20.0);
            double hpBonus = pull * (Math.pow(Math.max(1.0, dm), 0.85) - 1.0);       // so your stronger hits still feel strong, but they don't melt
            double dmgBonus = pull * (Math.pow(hpRatio, 0.6) - 1.0);                // so they can still hurt a Vitality-stacked player
            mod(m.getAttribute(Attributes.MAX_HEALTH), DBZ_HP, hpBonus, AttributeModifier.Operation.MULTIPLY_TOTAL);
            mod(m.getAttribute(Attributes.ATTACK_DAMAGE), DBZ_DMG, dmgBonus, AttributeModifier.Operation.MULTIPLY_TOTAL);
            m.getPersistentData().putFloat("xw_dbz_dmg", (float) (1.0 + dmgBonus));
        }
        m.setHealth(m.getMaxHealth());
        // more Karma to work with
        var t = m.getPersistentData();
        if (t.contains("xw_karma_max")) { float km = t.getFloat("xw_karma_max") * (1F + eff * 0.02F); t.putFloat("xw_karma_max", km); t.putFloat("xw_karma", km); }
        ModNetwork.sendToTrackingEntityAndSelf(new EnemyLevelPacket(m.getId(), lvl), m);
    }

    private static void mod(AttributeInstance a, UUID id, double amount, AttributeModifier.Operation op) {
        if (a == null || amount <= 0) return;
        a.removeModifier(id);
        a.addPermanentModifier(new AttributeModifier(id, "XW level", amount, op));
    }

    @SubscribeEvent
    public static void onTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof Mob m) || m.level().isClientSide || m.tickCount != 5 || !scales(m)) return;
        if (!m.getPersistentData().contains(LVL)) assign(m);
    }

    /** Players coming into view of it learn its level (for the lock-on bar). */
    @SubscribeEvent
    public static void onTrack(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof Mob m && m.getPersistentData().contains(LVL) && event.getEntity() instanceof ServerPlayer p)
            ModNetwork.sendToPlayer(new EnemyLevelPacket(m.getId(), level(m)), p);
    }

    @SubscribeEvent
    public static void onXp(LivingExperienceDropEvent event) {
        if (scales(event.getEntity()) && event.getEntity().getPersistentData().contains(LVL))
            event.setDroppedExperience(Math.round(event.getDroppedExperience() * (1F + effective(event.getEntity()) * 0.04F)));
    }

    /** Tells players when they cross into a more dangerous zone. */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p) || p.tickCount % 40 != 0) return;
        int z = zone(p.serverLevel(), p.blockPosition());
        Integer old = ZONE.put(p.getUUID(), z);
        if (old != null && z != old)
            p.displayClientMessage(Component.literal(z > old ? "The Karma here feels stronger... Danger " + z : "Things feel calmer here. Danger " + z)
                    .withStyle(z > old ? ChatFormatting.RED : ChatFormatting.GREEN), true);
    }
}

package com.xenowarriors.karma;

import com.xenowarriors.character.CharacterType;
import com.xenowarriors.character.ModCapabilities;
import com.xenowarriors.item.HoodieItem;
import com.xenowarriors.network.KarmaSensedPacket;
import com.xenowarriors.network.ModNetwork;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Karma Sensing (default K), which Fox Girl taught Mikiko and Rhalix: reach out and feel every
 * living thing's Karma nearby - they glow through walls for you for a few seconds. People wearing a
 * Karma-reducing hoodie can't be sensed, and characters you sense feel someone tracing their Karma.
 */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = com.xenowarriors.XenoWarriors.MOD_ID, bus = net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus.FORGE)
public final class KarmaSensing {
    public static final float COST = 15F;
    public static final int COOLDOWN = 20 * 20;
    public static final int DURATION = 10 * 20;
    public static final double RADIUS = 48;
    /** Practice: after this many uses Karma Sensing is fully mastered (range 120 blocks, 30 seconds). */
    public static final int MASTERY = 100;
    public static final double MAX_RADIUS = 120;
    public static final int MAX_DURATION = 30 * 20;

    /** At this much practice Karma Sensing also reads how strong (what level) everything is. */
    public static final int READ_LEVELS = 50;

    /**
     * How strong something is, on the same 1-150 scale as Character Level: a player's Character Level, or for
     * anything else a rating from its health, attack and armor (a zombie is about 9, an iron golem 40, the Warden 150).
     */
    public static int power(LivingEntity e) {
        if (e instanceof ServerPlayer p) return Math.max(1, com.xenowarriors.stats.Stats.characterLevel(p));
        double atk = e.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE) != null
                ? e.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE) : 0;
        double score = (e.getMaxHealth() + atk * 4 + e.getArmorValue() * 2) / 4.0;
        return (int) Math.max(1, Math.min(150, Math.round(score)));
    }

    public static int uses(ServerPlayer p) {
        return p.getCapability(ModCapabilities.PLAYER_CHARACTER).map(d -> d.senseUses).orElse(0);
    }

    public static double radius(int uses) {
        return RADIUS + (MAX_RADIUS - RADIUS) * Math.min(uses, MASTERY) / MASTERY;
    }

    public static int duration(int uses) {
        return DURATION + (MAX_DURATION - DURATION) * Math.min(uses, MASTERY) / MASTERY;
    }
    private static final Map<UUID, Long> READY_AT = new HashMap<>();

    private KarmaSensing() {}

    public static void use(ServerPlayer player) {
        use(player, true);
    }

    /** A threat: something hostile, or anything that's hunting you. */
    /** A corrupted soul, or something with one inside it (showing or hidden). */
    public static boolean corrupted(LivingEntity e) {
        return e instanceof com.xenowarriors.corrupted.CorruptedSoulEntity || e instanceof com.xenowarriors.corrupted.CSoulBossEntity
                || e instanceof com.xenowarriors.npc.DarkPossessedEntity
                || com.xenowarriors.mikiko.SoulExtract.hasSoul(e);
    }

    private static boolean threat(LivingEntity e, ServerPlayer player) {
        return e instanceof net.minecraft.world.entity.monster.Enemy
                || (e instanceof net.minecraft.world.entity.Mob m && m.getTarget() == player);
    }

    // ------------------------------------------------------------------ holding the key: focusing

    /** Holding it: drains Focus (Karma) while the reach widens; the longer you hold, the more you practice. */
    public static final float HOLD_DRAIN = 10F;            // Karma per second
    public static final float MAX_GROW = 2.5F;             // up to 2.5x the range
    private static final Map<UUID, Object[]> HOLDING = new HashMap<>();   // {start tick, friendly}

    /**
     * Pressed the key: one sense right away (as before). Keep holding to focus - the range keeps widening and it
     * keeps sensing until you let go or run out of Focus.
     * @param friendly false = the "players and threats only" setting: skip animals, villagers and other harmless life
     */
    public static void use(ServerPlayer player, boolean friendly) {
        if (ModCapabilities.getType(player) == CharacterType.NONE || player.isSpectator()) return;
        long now = player.level().getGameTime();
        long ready = READY_AT.getOrDefault(player.getUUID(), 0L);
        if (now < ready) {
            player.displayClientMessage(Component.translatable("message.xenowarriors.karma_sense.cooldown", (ready - now + 19) / 20), true);
            return;
        }
        if (!Karma.trySpend(player, COST)) return;
        READY_AT.put(player.getUUID(), now + COOLDOWN);
        practice(player);
        int uses = uses(player);
        scan(player, radius(uses), duration(uses), friendly, true);
        HOLDING.put(player.getUUID(), new Object[]{player.tickCount, friendly});
        ModNetwork.sendToTrackingAndSelf(new com.xenowarriors.network.KarmaFocusPacket(player.getId(), true), player);
    }

    /** Let go of the key (or out of Focus). */
    public static void release(ServerPlayer player) {
        if (HOLDING.remove(player.getUUID()) == null) return;
        READY_AT.put(player.getUUID(), player.level().getGameTime() + COOLDOWN);     // cooldown counts from letting go
        ModNetwork.sendToTrackingAndSelf(new com.xenowarriors.network.KarmaFocusPacket(player.getId(), false), player);
    }

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void onTick(net.minecraftforge.event.TickEvent.PlayerTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END || !(event.player instanceof ServerPlayer p)) return;
        Object[] h = HOLDING.get(p.getUUID());
        if (h == null) return;
        if (!p.isAlive() || p.isSpectator()) { release(p); return; }
        int held = p.tickCount - (Integer) h[0];
        if (held <= 0) return;
        if (held % 10 == 0) {
            if (!Karma.trySpend(p, HOLD_DRAIN / 2F)) {
                p.displayClientMessage(Component.literal("Out of focus...").withStyle(net.minecraft.ChatFormatting.GRAY), true);
                release(p);
                return;
            }
        }
        if (held % 40 == 0) practice(p);                                   // holding it is practice too
        if (held % 20 == 0) {
            int uses = uses(p);
            float grow = Math.min(MAX_GROW, 1F + held / 20F * 0.2F);         // +20% range a second
            scan(p, radius(uses) * grow, duration(uses), (Boolean) h[1], false);
        }
    }

    public static boolean holding(ServerPlayer p) {
        return HOLDING.containsKey(p.getUUID());
    }

    /** One more use's worth of practice (with the milestone messages). */
    private static void practice(ServerPlayer player) {
        int uses = uses(player);
        player.getCapability(ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> {
            if (d.senseUses < 1_000_000) d.senseUses++;
        });
        com.xenowarriors.compat.DmzKiTraining.practiced(player);         // (DragonMineZ: trains max Ki too)
        int after = uses + 1;
        if (after == READ_LEVELS) {
            player.sendSystemMessage(Component.literal("Your Karma Sensing is sharp enough to read levels now - sensed players and enemies show theirs.")
                    .withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE));
        }
        if (after <= MASTERY && after % 10 == 0) {
            player.sendSystemMessage(Component.literal(after == MASTERY
                    ? "Karma Sensing mastered! Range " + Math.round(radius(after)) + " blocks, " + duration(after) / 20 + " seconds."
                    : "Your Karma Sensing grows stronger: range " + Math.round(radius(after)) + " blocks, " + duration(after) / 20 + " seconds.")
                    .withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE));
        }
    }

    /** As far as you can see: your render distance (in blocks). */
    private static double viewLimit(ServerPlayer p) {
        int chunks = Math.min(p.server.getPlayerList().getViewDistance(), Math.max(2, p.requestedViewDistance()));
        return chunks * 16.0;
    }

    private static void scan(ServerPlayer player, double RADIUS, int DURATION, boolean friendly, boolean first) {
        int uses = uses(player);
        // never further than you can see; reaching past that, your sense is strong enough to read everyone's stats
        double limit = viewLimit(player);
        boolean overflow = RADIUS > limit;
        RADIUS = Math.min(RADIUS, limit);
        List<LivingEntity> found = player.level().getEntitiesOfClass(LivingEntity.class, new AABB(player.blockPosition()).inflate(RADIUS),
                e -> e != player && e.isAlive() && !(e instanceof ArmorStand) && e.distanceToSqr(player) <= RADIUS * RADIUS
                        && !(e instanceof Player p && (p.isSpectator() || (HoodieItem.wearing(p) && uses < MASTERY)))
                        && (friendly || e instanceof Player || threat(e, player) || corrupted(e)));
        int[] ids = found.stream().mapToInt(LivingEntity::getId).toArray();
        // a fully mastered sense can feel people through a Karma-reducing hoodie - but only faintly, no level (-1)
        int[] levels = found.stream().mapToInt(e -> e instanceof Player p && HoodieItem.wearing(p) ? -1 : power(e)).toArray();
        // anything with a corrupted soul in it (hidden ones too) feels like the soul itself
        int[] souls = found.stream().filter(KarmaSensing::corrupted).mapToInt(LivingEntity::getId).toArray();
        // their stats (shown to you if you're the stronger one): attack damage x10, defense
        int[] damage = found.stream().mapToInt(e -> e.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE) != null
                ? (int) Math.round(e.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE) * 10) : 0).toArray();
        int[] armor = found.stream().mapToInt(LivingEntity::getArmorValue).toArray();
        ModNetwork.sendToPlayer(new KarmaSensedPacket(ids, levels, DURATION, uses >= READ_LEVELS || overflow, souls, damage, armor, first, (float) RADIUS, overflow), player);
        if (first) {
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.8F, 1.6F);
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 0.8F);
        }
        player.displayClientMessage(Component.literal(first ? "" : "Focusing: " + Math.round(RADIUS) + " blocks - ")
                .append(Component.translatable("message.xenowarriors.karma_sense.found", ids.length)), true);
        if (!first) return;
        // "I think someone is trying to trace my karma"
        for (LivingEntity e : found) {
            if (e instanceof ServerPlayer other && ModCapabilities.getType(other) != CharacterType.NONE) {
                other.displayClientMessage(Component.translatable("message.xenowarriors.karma_sense.traced"), true);
            }
        }
    }
}

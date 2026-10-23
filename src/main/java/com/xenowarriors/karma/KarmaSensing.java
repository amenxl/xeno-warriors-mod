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

    /**
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
        int uses = uses(player);
        final double RADIUS = radius(uses);
        final int DURATION = duration(uses);
        player.getCapability(ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> {
            if (d.senseUses < 1_000_000) d.senseUses++;
        });
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

        List<LivingEntity> found = player.level().getEntitiesOfClass(LivingEntity.class, new AABB(player.blockPosition()).inflate(RADIUS),
                e -> e != player && e.isAlive() && !(e instanceof ArmorStand) && e.distanceToSqr(player) <= RADIUS * RADIUS
                        && !(e instanceof Player p && (p.isSpectator() || (HoodieItem.wearing(p) && uses(player) < MASTERY)))
                        && (friendly || e instanceof Player || threat(e, player) || corrupted(e)));
        int[] ids = found.stream().mapToInt(LivingEntity::getId).toArray();
        // a fully mastered sense can feel people through a Karma-reducing hoodie - but only faintly, no level (-1)
        int[] levels = found.stream().mapToInt(e -> e instanceof Player p && HoodieItem.wearing(p) ? -1 : power(e)).toArray();
        // anything with a corrupted soul in it (hidden ones too) feels like the soul itself
        int[] souls = found.stream().filter(KarmaSensing::corrupted).mapToInt(LivingEntity::getId).toArray();
        ModNetwork.sendToPlayer(new KarmaSensedPacket(ids, levels, DURATION, uses >= READ_LEVELS, souls), player);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.8F, 1.6F);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 0.8F);
        player.displayClientMessage(Component.translatable("message.xenowarriors.karma_sense.found", ids.length), true);

        // "I think someone is trying to trace my karma"
        for (LivingEntity e : found) {
            if (e instanceof ServerPlayer other && ModCapabilities.getType(other) != CharacterType.NONE) {
                other.displayClientMessage(Component.translatable("message.xenowarriors.karma_sense.traced"), true);
            }
        }
    }
}

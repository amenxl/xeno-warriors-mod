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
    private static final Map<UUID, Long> READY_AT = new HashMap<>();

    private KarmaSensing() {}

    public static void use(ServerPlayer player) {
        if (ModCapabilities.getType(player) == CharacterType.NONE || player.isSpectator()) return;
        long now = player.level().getGameTime();
        long ready = READY_AT.getOrDefault(player.getUUID(), 0L);
        if (now < ready) {
            player.displayClientMessage(Component.translatable("message.xenowarriors.karma_sense.cooldown", (ready - now + 19) / 20), true);
            return;
        }
        if (!Karma.trySpend(player, COST)) return;
        READY_AT.put(player.getUUID(), now + COOLDOWN);

        List<LivingEntity> found = player.level().getEntitiesOfClass(LivingEntity.class, new AABB(player.blockPosition()).inflate(RADIUS),
                e -> e != player && e.isAlive() && !(e instanceof ArmorStand) && e.distanceToSqr(player) <= RADIUS * RADIUS
                        && !(e instanceof Player p && (p.isSpectator() || HoodieItem.wearing(p))));
        int[] ids = found.stream().mapToInt(LivingEntity::getId).toArray();
        ModNetwork.sendToPlayer(new KarmaSensedPacket(ids, DURATION), player);
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

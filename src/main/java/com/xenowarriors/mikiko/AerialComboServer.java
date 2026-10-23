package com.xenowarriors.mikiko;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.character.ModCapabilities;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Server side of Mikiko's aerial Hekublade combo: the first hit launches the target up, the next ones keep it up in
 * front of him, and the last one spikes it into the ground (unless it blocked) with an impact when it lands.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AerialComboServer {
    private static final List<Object[]> PENDING = new ArrayList<>();             // {target, player, step, last}
    private static final Map<LivingEntity, Object[]> SPIKED = new HashMap<>();    // target -> {player, ticks left}

    private AerialComboServer() {}

    public static void hit(ServerPlayer p, int targetId, int step, boolean last, boolean doHit) {
        CharacterType type = ModCapabilities.getType(p);
        var held = p.getMainHandItem();
        if (p.isSpectator() || (type != CharacterType.MIKIKO && type != CharacterType.RHALIX)) return;
        if (!(held.isEmpty() || Hekublade.isBlade(held) || (type == CharacterType.RHALIX && held.getItem() instanceof net.minecraft.world.item.SwordItem))) return;
        if (!(p.level().getEntity(targetId) instanceof LivingEntity t) || !t.isAlive() || t == p) return;
        if (p.distanceTo(t) > 8.0) return;
        if (doHit) {
            if (!p.hasLineOfSight(t)) return;
            p.attack(t);
        }
        if (!t.isAlive() || t.isInvulnerable()) return;
        PENDING.add(new Object[]{t, p, step, last});
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!PENDING.isEmpty()) {
            for (Object[] o : PENDING) {
                LivingEntity t = (LivingEntity) o[0];
                ServerPlayer p = (ServerPlayer) o[1];
                int step = (Integer) o[2];
                boolean last = (Boolean) o[3];
                if (!t.isAlive() || t.hurtTime <= 0) continue;                    // the blow didn't land
                Vec3 look = p.getViewVector(1F);
                Vec3 flat = new Vec3(look.x, 0, look.z).normalize();
                if (last && !t.isBlocking()) {
                    // spiked straight down
                    t.setDeltaMovement(flat.x * 0.3, -1.8, flat.z * 0.3);
                    SPIKED.put(t, new Object[]{p, 40});
                    p.level().playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1F, 0.6F);
                } else {
                    // up into the air, held in front of him at blade's length
                    Vec3 spot = p.position().add(flat.scale(2.2)).add(0, step == 0 ? 0.6 : 0.2, 0);
                    Vec3 pull = spot.subtract(t.position()).scale(0.25);
                    double up = step == 0 ? 0.62 : Math.max(0.25, pull.y + 0.18);
                    t.setDeltaMovement(pull.x, up, pull.z);
                }
                t.hurtMarked = true;
                t.fallDistance = 0;
            }
            PENDING.clear();
        }
        if (!SPIKED.isEmpty()) {
            SPIKED.entrySet().removeIf(en -> {
                LivingEntity t = en.getKey();
                Object[] o = en.getValue();
                int left = (Integer) o[1] - 1;
                o[1] = left;
                if (!t.isAlive() || left <= 0) return true;
                if (!t.onGround() && !t.isInWater()) return false;
                // crashing down: dust and a shockwave, and it hurts
                if (t.level() instanceof ServerLevel s) {
                    s.sendParticles(ParticleTypes.EXPLOSION, t.getX(), t.getY() + 0.2, t.getZ(), 2, 0.3, 0.1, 0.3, 0);
                    s.sendParticles(ParticleTypes.CLOUD, t.getX(), t.getY() + 0.1, t.getZ(), 20, 0.8, 0.1, 0.8, 0.08);
                }
                t.level().playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.6F, 1.3F);
                ServerPlayer p = (ServerPlayer) o[0];
                float dmg = (float) (p.getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.6);
                t.invulnerableTime = 0;
                t.hurt(p.damageSources().playerAttack(p), dmg);
                return true;
            });
        }
    }
}

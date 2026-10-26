package com.xenowarriors.virus;

import com.xenowarriors.ModEffects;
import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.character.ModCapabilities;
import com.xenowarriors.character.Seasons;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

/**
 * The Aurora virus (Season 2): the lifelong sickness from the show (Kyle, Ava). Coughing fits, blacking out, worse the
 * longer you have it - it doesn't kill you here, but it wears you down. Caught from coughing Aurora residents (and
 * other sick players). Aurora Medicine from Government facilities cures it and keeps it off for a while.
 * Demons can't catch it - and neither can Ultima.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AuroraVirus {
    public static final String CARRIER = "xw_virus";
    public static final int STAGE_TICKS = 20 * 60 * 15;          // gets worse every 15 minutes
    private static final DustParticleOptions COUGH = new DustParticleOptions(new Vector3f(0.75F, 0.8F, 0.82F), 0.7F);

    private AuroraVirus() {}

    public static boolean enabled(LivingEntity e) { return e.level().getServer() != null && Seasons.unlocked(e.level().getServer(), 2); }

    public static boolean has(LivingEntity e) { return e.hasEffect(ModEffects.AURORA_VIRUS.get()); }

    public static boolean immune(LivingEntity e) {
        if (e.hasEffect(ModEffects.VIRUS_IMMUNITY.get())) return true;
        if (e instanceof Player p && (p.isCreative() || p.isSpectator() || ModCapabilities.getType(p) == CharacterType.ULTIMA || ModCapabilities.getType(p) == CharacterType.RHALIX)) return true;   // Rhalix (demigod, Skylander healing) can't catch it
        return e instanceof com.xenowarriors.npc.DarkPossessedEntity;                   // demons are immune to all sickness
    }

    /** Infect (stage 0). Returns true if it took. */
    public static boolean infect(LivingEntity e) {
        if (!enabled(e) || immune(e) || has(e)) return false;
        e.addEffect(new MobEffectInstance(ModEffects.AURORA_VIRUS.get(), -1, 0, false, false, true));
        e.getPersistentData().putLong("xw_virus_since", e.level().getGameTime());
        if (e instanceof ServerPlayer p)
            p.displayClientMessage(Component.literal("Your throat itches... you caught the Aurora virus.").withStyle(ChatFormatting.DARK_AQUA), false);
        return true;
    }

    public static void cure(LivingEntity e, int immuneTicks) {
        e.removeEffect(ModEffects.AURORA_VIRUS.get());
        e.getPersistentData().remove(CARRIER);
        if (immuneTicks > 0) e.addEffect(new MobEffectInstance(ModEffects.VIRUS_IMMUNITY.get(), immuneTicks, 0, false, false, true));
    }

    private static void cough(LivingEntity e, boolean hard) {
        ServerLevel s = (ServerLevel) e.level();
        Vec3 m = e.getEyePosition().add(e.getViewVector(1F).scale(0.4)).add(0, -0.15, 0);
        s.sendParticles(COUGH, m.x, m.y, m.z, hard ? 10 : 5, 0.12, 0.08, 0.12, 0.02);
        s.playSound(null, e.blockPosition(), SoundEvents.PANDA_SNEEZE, SoundSource.NEUTRAL, hard ? 0.9F : 0.6F, 0.55F + e.getRandom().nextFloat() * 0.1F);
        if (e instanceof Player) com.xenowarriors.anim.Poses.play(e, com.xenowarriors.anim.Poses.WIPE, 10);
        // anyone close enough can catch it
        for (LivingEntity o : s.getEntitiesOfClass(LivingEntity.class, e.getBoundingBox().inflate(3), o -> o != e && (o instanceof Player || o instanceof com.xenowarriors.npc.HumanNpcEntity)))
            if (e.getRandom().nextFloat() < (hard ? 0.08F : 0.04F)) {
                if (o instanceof Player) infect(o); else o.getPersistentData().putBoolean(CARRIER, true);
            }
    }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p) || !has(p)) return;
        if (!enabled(p) || immune(p)) { cure(p, 0); return; }
        MobEffectInstance v = p.getEffect(ModEffects.AURORA_VIRUS.get());
        int stage = v == null ? 0 : v.getAmplifier();
        long since = p.getPersistentData().getLong("xw_virus_since");
        if (stage < 2 && p.level().getGameTime() - since > (long) STAGE_TICKS * (stage + 1)) {      // it gets worse
            p.addEffect(new MobEffectInstance(ModEffects.AURORA_VIRUS.get(), -1, stage + 1, false, false, true));
            p.displayClientMessage(Component.literal(stage == 0 ? "The coughing is getting worse..." : "It's getting hard to breathe. You need Aurora Medicine.").withStyle(ChatFormatting.DARK_AQUA), false);
        }
        var r = p.getRandom();
        if (p.tickCount % 20 == 0 && r.nextInt(14 - stage * 4) == 0) cough(p, stage > 0);
        if (stage >= 1 && p.tickCount % 20 == 0 && r.nextInt(stage == 2 ? 70 : 160) == 0) {        // blacking out for a moment
            p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 50, 0, false, false));
            p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2, false, false));
            p.displayClientMessage(Component.literal("You can't breathe...").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), true);
            cough(p, true);
        }
        if (stage >= 2 && p.tickCount % 100 == 0) {
            p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 120, 0, false, false));
            p.causeFoodExhaustion(1.5F);
        }
    }

    /** Some Aurora residents are carriers; they cough now and then. */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof com.xenowarriors.npc.HumanNpcEntity h)) return;
        if (!h.getPersistentData().contains("xw_virus_rolled")) {
            h.getPersistentData().putBoolean("xw_virus_rolled", true);
            if (h.getRandom().nextFloat() < 0.1F) h.getPersistentData().putBoolean(CARRIER, true);
        }
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity e = event.getEntity();
        if (e.level().isClientSide || !(e instanceof com.xenowarriors.npc.HumanNpcEntity) || e.tickCount % 40 != 0) return;
        if (!e.getPersistentData().getBoolean(CARRIER) || !enabled(e)) return;
        if (e.getRandom().nextInt(8) == 0) cough(e, false);
    }
}

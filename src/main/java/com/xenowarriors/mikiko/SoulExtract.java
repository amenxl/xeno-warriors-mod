package com.xenowarriors.mikiko;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.character.ModCapabilities;
import com.xenowarriors.corrupted.*;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Mikiko right-clicks something with a corrupted soul inside it while holding the Hekublade: the soul is torn out
 * and sealed in the blade. Animals and villagers are themselves again (freed); a possessed monster is just a monster
 * again; a body that was only ever the soul's crumbles away. Mikiko gets bonus experience for it.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SoulExtract {
    public static final int XP = 40;
    public static final float BLADE_XP = 25F;

    private SoulExtract() {}

    /** Has a corrupted soul in it - showing or hidden. */
    public static boolean hasSoul(LivingEntity e) {
        return e instanceof CorruptedVillagerEntity || CorruptedAnimals.isCorrupted(e) || HiddenSouls.hidden(e)
                || (e instanceof com.xenowarriors.npc.HumanNpcEntity h && h.carriesSoul());
    }

    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide || event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!(event.getEntity() instanceof ServerPlayer p) || !(event.getTarget() instanceof LivingEntity target)) return;
        if (ModCapabilities.getType(p) != CharacterType.MIKIKO || !Hekublade.holding(p) || !target.isAlive() || !hasSoul(target)) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        extract(p, target);
    }

    private static void extract(ServerPlayer p, LivingEntity target) {
        ServerLevel level = p.serverLevel();
        Vec3 from = target.position().add(0, target.getBbHeight() * 0.6, 0);
        // the soul itself goes in the blade (shows up in the Hekublade's realm like anything else he's sealed)
        CorruptedSoulEntity soul = ModCorrupted.CORRUPTED_SOUL.get().create(level);
        if (soul != null) {
            soul.moveTo(from.x, from.y, from.z);
            HekubladeRealm.seal(p, soul);
        }
        String freed;
        if (target instanceof com.xenowarriors.npc.HumanNpcEntity h) {
            h.free();
            freed = "They're themselves again.";
        } else if (target instanceof CorruptedVillagerEntity cv) {
            if (cv.wasVillager()) {
                Villager v = cv.convertTo(EntityType.VILLAGER, false);
                freed = v != null ? "The villager is free." : null;
            } else {
                cv.discard();                                      // nothing was ever in there but the soul
                level.sendParticles(ParticleTypes.SOUL, from.x, from.y, from.z, 20, 0.3, 0.6, 0.3, 0.02);
                freed = "The empty body crumbles away.";
            }
        } else if (HiddenSouls.hidden(target)) {
            HiddenSouls.clear(target);
            freed = target instanceof Villager ? "The villager is free - nobody even knew." : "They're free - nobody even knew.";
        } else if (target instanceof Animal a) {
            CorruptedAnimals.cleanse(a);
            freed = "The animal is free.";
        } else if (target instanceof Mob m) {
            CorruptedAnimals.cleanseMob(m);
            freed = null;
        } else freed = null;
        // the soul streaming into the blade
        Vec3 to = p.position().add(0, 1.1, 0);
        for (int i = 0; i <= 12; i++) {
            Vec3 at = from.lerp(to, i / 12.0);
            level.sendParticles(ParticleTypes.SOUL, at.x, at.y, at.z, 1, 0.05, 0.05, 0.05, 0.0);
            level.sendParticles(CorruptedFx.BLACK, at.x, at.y, at.z, 1, 0.05, 0.05, 0.05, 0.0);
        }
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.SOUL_ESCAPE, SoundSource.PLAYERS, 1.2F, 0.8F);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), com.xenowarriors.ModSounds.HEKUBLADE_SUMMON.get(), SoundSource.PLAYERS, 0.6F, 1.4F);
        p.giveExperiencePoints(XP);
        BladeLevel.addXp(p, BLADE_XP);
        p.displayClientMessage(Component.literal("Corrupted soul sealed in the Hekublade (+" + XP + " XP)"
                + (freed != null ? "  " + freed : "")).withStyle(ChatFormatting.LIGHT_PURPLE), true);
    }
}

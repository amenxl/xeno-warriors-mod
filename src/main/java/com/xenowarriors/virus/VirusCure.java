package com.xenowarriors.virus;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.bot.PartyBotEntity;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.character.ModCapabilities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/**
 * Yuri and Technica cure the Aurora virus for their party: sneak + right-click (empty hand) an infected party member
 * (a player or a party bot) and after a quick scan-and-treat the virus is gone (and they're immune for a while).
 * Their bots do it by themselves for anyone infected in the party close by. 30 seconds between cures.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class VirusCure {
    public static final int COOLDOWN = 20 * 30, IMMUNE = 20 * 60 * 5, BOT_RANGE = 10;
    private static final String LAST = "xw_cure_last";

    private VirusCure() {}

    public static boolean healer(CharacterType t) { return t == CharacterType.YURI || t == CharacterType.TECHNICA; }

    private static CharacterType type(LivingEntity e) {
        if (e instanceof Player p) return ModCapabilities.getType(p);
        if (e instanceof PartyBotEntity b) return b.character();
        return CharacterType.NONE;
    }

    /** Whose party this is: the player, or the bot's owner. */
    private static UUID partyId(LivingEntity e) {
        if (e instanceof Player p) return p.getUUID();
        if (e instanceof PartyBotEntity b) return b.ownerId();
        return null;
    }

    public static boolean sameParty(LivingEntity a, LivingEntity b) {
        UUID x = partyId(a), y = partyId(b);
        return x != null && y != null && (x.equals(y) || com.xenowarriors.party.Parties.sameParty(x, y));
    }

    public static boolean ready(LivingEntity healer) {
        long last = healer.getPersistentData().getLong(LAST);
        return last == 0 || healer.level().getGameTime() - last >= COOLDOWN || healer.level().getGameTime() < last;
    }

    /** Treats {@code patient}: the pose, the effects and the cure. True if it did. */
    public static boolean cure(LivingEntity healer, LivingEntity patient) {
        if (healer.level().isClientSide || !healer(type(healer)) || patient == healer || !AuroraVirus.has(patient)
                || !sameParty(healer, patient) || !ready(healer)) return false;
        healer.getPersistentData().putLong(LAST, healer.level().getGameTime());
        AuroraVirus.cure(patient, IMMUNE);
        healer.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, patient.getEyePosition());
        com.xenowarriors.anim.Poses.play(healer, com.xenowarriors.anim.Poses.EL_RAISE, 24);
        if (healer.level() instanceof ServerLevel sl) {
            boolean tech = type(healer) == CharacterType.TECHNICA;
            sl.sendParticles(tech ? ParticleTypes.ELECTRIC_SPARK : ParticleTypes.HAPPY_VILLAGER, patient.getX(), patient.getY() + 1, patient.getZ(), 16, 0.35, 0.6, 0.35, 0.05);
            sl.sendParticles(ParticleTypes.END_ROD, patient.getX(), patient.getY() + 1, patient.getZ(), 8, 0.3, 0.5, 0.3, 0.02);
            sl.playSound(null, patient.getX(), patient.getY(), patient.getZ(), SoundEvents.BREWING_STAND_BREW, SoundSource.PLAYERS, 0.8F, 1.4F);
            sl.playSound(null, patient.getX(), patient.getY(), patient.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1F, 1.2F);
        }
        String who = healer.getName().getString();
        if (patient instanceof ServerPlayer sp)
            sp.displayClientMessage(Component.literal(who + " cured your Aurora virus!").withStyle(ChatFormatting.GREEN), false);
        if (healer instanceof ServerPlayer hp)
            hp.displayClientMessage(Component.literal("Cured " + patient.getName().getString() + "'s Aurora virus").withStyle(ChatFormatting.GREEN), true);
        else if (healer instanceof PartyBotEntity b && b.owner() != null && b.owner() != patient)
            b.owner().displayClientMessage(Component.literal(who + " cured " + patient.getName().getString() + "'s Aurora virus").withStyle(ChatFormatting.GREEN), true);
        XenoWarriors.LOGGER.info("XW-CURE {} cured {} (virus now {})", who, patient.getName().getString(), AuroraVirus.has(patient));
        return true;
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        Player p = event.getEntity();
        if (event.getHand() != InteractionHand.MAIN_HAND || !p.isShiftKeyDown() || !p.getMainHandItem().isEmpty()
                || !(event.getTarget() instanceof LivingEntity patient)) return;
        if (p.level().isClientSide) return;
        if (!healer(ModCapabilities.getType(p)) || !AuroraVirus.has(patient) || !sameParty(p, patient)) return;
        if (!ready(p)) {
            long left = (COOLDOWN - (p.level().getGameTime() - p.getPersistentData().getLong(LAST))) / 20;
            p.displayClientMessage(Component.literal("Your medicine kit needs " + left + "s more").withStyle(ChatFormatting.GRAY), true);
        } else cure(p, patient);
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    /** Yuri's and Technica's bots treat anyone infected in the party nearby. */
    @SubscribeEvent
    public static void onBotTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof PartyBotEntity b) || b.level().isClientSide || b.tickCount % 40 != 7) return;
        if (!healer(b.character()) || b.downed() || !ready(b)) return;
        for (LivingEntity e : b.level().getEntitiesOfClass(LivingEntity.class, b.getBoundingBox().inflate(BOT_RANGE),
                e -> e != b && e.isAlive() && (e instanceof Player || e instanceof PartyBotEntity) && AuroraVirus.has(e) && sameParty(b, e))) {
            if (cure(b, e)) return;
        }
    }
}

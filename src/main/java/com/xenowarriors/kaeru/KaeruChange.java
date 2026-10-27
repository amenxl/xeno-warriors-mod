package com.xenowarriors.kaeru;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.character.ModCapabilities;
import com.xenowarriors.network.ChangeFormPacket;
import com.xenowarriors.network.ModNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Kaeru's KA - Change. First she becomes a dark silhouette with glowing white eyes and white streaks, then she turns
 * into what she's changing into:
 *  aim at a creature or person - she looks exactly like them for 60 s;
 *  sneak - she becomes the block she's looking at (or standing on) and holds still, hidden, for 60 s.
 * While changed, mobs lose track of her unless she hits them. Press again to change back.
 * The cost: every Change wears on who she is - changing back leaves her weak and dizzy, worse the more she's used it,
 * and the fifth Change in a short time burns it out for 3 minutes.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class KaeruChange {
    public static final int NONE = 0, SILHOUETTE = 1, CREATURE = 2, BLOCK = 3;
    private static final int SHIFT = 30, LAST = 60 * 20, COST = 30, BURNOUT = 5;

    private record Form(int mode, String id, int target, long until) {}
    private static final Map<UUID, Form> FORMS = new HashMap<>();
    private static final Map<UUID, Long> HIT = new HashMap<>();

    private KaeruChange() {}

    public static int mode(Player p) { Form f = FORMS.get(p.getUUID()); return f == null ? NONE : f.mode; }

    /** The super key. True if it went off (starts the KA cooldown). */
    public static boolean use(ServerPlayer p) {
        if (FORMS.containsKey(p.getUUID())) { revert(p, true); return false; }
        var t = p.getPersistentData();
        long now = p.serverLevel().getGameTime();
        if (t.getLong("xw_change_burn") > now) { p.displayClientMessage(Component.literal("Change is burnt out - she needs to remember who she is first").withStyle(ChatFormatting.DARK_RED), true); return false; }
        if (!p.isCreative() && !com.xenowarriors.karma.Karma.trySpend(p, COST)) return false;
        String id; int kind;
        if (p.isShiftKeyDown()) {
            HitResult hr = p.pick(6, 1F, false);
            BlockPos pos = hr instanceof BlockHitResult bh && hr.getType() == HitResult.Type.BLOCK ? bh.getBlockPos() : p.blockPosition().below();
            BlockState bs = p.level().getBlockState(pos);
            if (bs.isAir()) bs = p.level().getBlockState(p.blockPosition().below());
            if (bs.isAir()) { p.displayClientMessage(Component.literal("Nothing to change into").withStyle(ChatFormatting.GRAY), true); return false; }
            id = BuiltInRegistries.BLOCK.getKey(bs.getBlock()).toString(); kind = BLOCK;
        } else {
            LivingEntity target = com.xenowarriors.skill.SkillTargeting.aimed(p, 20, 15);
            if (target == null) { p.displayClientMessage(Component.literal("KA Change: look at who - or what - to become (sneak for a block)").withStyle(ChatFormatting.GRAY), true); return false; }
            id = target instanceof Player ? "player:" + target.getId() : BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()).toString();
            kind = CREATURE;
        }
        // strain: uses in the last 5 minutes
        if (now - t.getLong("xw_change_last") > 5 * 60 * 20) t.putInt("xw_change_strain", 0);
        int strain = t.getInt("xw_change_strain") + 1;
        t.putInt("xw_change_strain", strain); t.putLong("xw_change_last", now);
        if (strain >= BURNOUT) { t.putLong("xw_change_burn", now + 3 * 60 * 20); t.putInt("xw_change_strain", 0); }

        set(p, new Form(SILHOUETTE, id, kind, now + SHIFT));
        Component line = Component.literal(p.getName().getString() + ": ").withStyle(ChatFormatting.WHITE)
                .append(Component.literal("KA - Change!").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        for (ServerPlayer o : p.serverLevel().players()) if (o.distanceToSqr(p) < 40 * 40) o.displayClientMessage(line, true);
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.ILLUSIONER_PREPARE_MIRROR, SoundSource.PLAYERS, 1F, 1.2F);
        return true;
    }

    /** UI test: put her straight into a form (mode SILHOUETTE/CREATURE/BLOCK) for a while. */
    public static void testForm(ServerPlayer p, int mode, String id) {
        long now = p.serverLevel().getGameTime();
        set(p, new Form(mode, id, mode == SILHOUETTE ? CREATURE : mode, now + (mode == SILHOUETTE ? 100000 : LAST)));
    }

    private static void set(ServerPlayer p, Form f) {
        if (f == null) FORMS.remove(p.getUUID()); else FORMS.put(p.getUUID(), f);
        ModNetwork.sendToTrackingAndSelf(f == null ? new ChangeFormPacket(p.getId(), NONE, "") : new ChangeFormPacket(p.getId(), f.mode, f.id), p);
    }

    public static void revert(ServerPlayer p, boolean announce) {
        Form f = FORMS.get(p.getUUID());
        if (f == null) return;
        set(p, null);
        ServerLevel l = p.serverLevel();
        l.sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1, p.getZ(), 30, 0.3, 0.7, 0.3, 0.06);
        l.sendParticles(ParticleTypes.LARGE_SMOKE, p.getX(), p.getY() + 1, p.getZ(), 10, 0.3, 0.6, 0.3, 0.02);
        l.playSound(null, p.blockPosition(), SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 1F, 1F);
        int strain = p.getPersistentData().getInt("xw_change_strain");
        if (f.mode != SILHOUETTE || true) {
            p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60 + strain * 60, Math.min(2, strain / 2)));
            if (strain >= 2) p.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 40 + strain * 40, 0));
        }
        p.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        if (announce) p.displayClientMessage(Component.literal("Changed back").withStyle(ChatFormatting.GOLD), true);
    }

    private static boolean hidden(Player p) {
        Form f = FORMS.get(p.getUUID());
        if (f == null || f.mode == SILHOUETTE) return false;
        Long hit = HIT.get(p.getUUID());
        return hit == null || p.level().getGameTime() - hit > 100;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p)) return;
        Form f = FORMS.get(p.getUUID());
        if (f == null) return;
        ServerLevel l = p.serverLevel();
        long now = l.getGameTime();
        if (!p.isAlive() || ModCapabilities.getType(p) != CharacterType.KAERU) { revert(p, false); return; }
        if (f.mode == SILHOUETTE) {
            p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 5, 3, false, false));
            if (now % 2 == 0) l.sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1, p.getZ(), 3, 0.35, 0.8, 0.35, 0.03);
            if (now >= f.until) {
                set(p, new Form(f.target, f.id, f.target, now + LAST));
                l.sendParticles(ParticleTypes.FLASH, p.getX(), p.getY() + 1, p.getZ(), 1, 0, 0, 0, 0);
                l.sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1, p.getZ(), 40, 0.4, 0.8, 0.4, 0.12);
                l.playSound(null, p.blockPosition(), SoundEvents.FOX_SCREECH, SoundSource.PLAYERS, 0.6F, 1.4F);
                l.playSound(null, p.blockPosition(), SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 1F, 1.3F);
            }
            return;
        }
        if (f.mode == BLOCK) p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 5, 6, false, false));
        if (now >= f.until) { revert(p, true); return; }
        if (hidden(p) && now % 10 == 0)
            for (Mob m : l.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(32), m -> m.getTarget() == p)) m.setTarget(null);
    }

    @SubscribeEvent
    public static void onTarget(LivingChangeTargetEvent event) {
        if (event.getNewTarget() instanceof Player p && hidden(p)) event.setCanceled(true);
    }

    /** Hitting something gives her away to it for a few seconds. */
    @SubscribeEvent
    public static void onAttack(LivingAttackEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer p && FORMS.containsKey(p.getUUID())) HIT.put(p.getUUID(), p.level().getGameTime());
    }

    @SubscribeEvent
    public static void onTrack(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof ServerPlayer p && event.getEntity() instanceof ServerPlayer viewer) {
            Form f = FORMS.get(p.getUUID());
            if (f != null) ModNetwork.sendToPlayer(new ChangeFormPacket(p.getId(), f.mode, f.id), viewer);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        FORMS.remove(event.getEntity().getUUID()); HIT.remove(event.getEntity().getUUID());
    }
}

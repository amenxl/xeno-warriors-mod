package com.xenowarriors.toggle;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.character.ModCapabilities;
import com.xenowarriors.network.ModNetwork;
import com.xenowarriors.skill.Skill;
import com.xenowarriors.skill.SkillManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

/**
 * The passives that work by themselves (Toggles):
 * <ul>
 *   <li>Max - Lunar Mind: only at night - stronger, faster and healing while the moon is up.</li>
 *   <li>Technica - Gravity Boots: falling far enough to get hurt, they kick in just before she lands (no fall damage),
 *       then need {@value #BOOTS_GAP_S} s to recharge. Visor Scan: every time she locks on to someone (every
 *       {@value #VISOR_GAP_S} s at most). Rescue Platform: two or more of her teammates down nearby - it comes down,
 *       picks them up and heals the party; {@value #RESCUE_GAP_S} s before it can come again.</li>
 *   <li>Ultima - Attack Prediction: like Mikiko's True Empathy - on, his chip reads whoever comes at him by itself
 *       (Data each time); overused (three times in 90 s) it overheats and switches itself off. Living Weapon: his
 *       transformation now, on the transform key.</li>
 *   <li>Kaeru - Scent Tracking: from the toggle wheel, a picker of everything she can smell nearby; pick one and it's
 *       highlighted with its scent trail (ScentScreen / ScentClient).</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Passives {
    private Passives() {}

    public static final int BOOTS_GAP_S = 20, VISOR_GAP_S = 8, RESCUE_GAP_S = 180, PREDICT_GAP_S = 20, SCENT_RANGE = 64, SCENT_TICKS = 45 * 20;
    private static final Map<UUID, Map<String, Long>> NEXT = new HashMap<>();
    /** (Tests) times each kicked in. */
    public static int bootsSaved, visorScans, rescues, predictions;

    private static boolean ready(ServerPlayer p, String k) { return p.level().getGameTime() >= NEXT.getOrDefault(p.getUUID(), Map.of()).getOrDefault(k, 0L); }
    private static void wait(ServerPlayer p, String k, int ticks) { NEXT.computeIfAbsent(p.getUUID(), x -> new HashMap<>()).put(k, p.level().getGameTime() + ticks); }
    /** (Tests) clears the passives' cooldowns. */
    public static void resetForTest(ServerPlayer p) { NEXT.remove(p.getUUID()); }

    private static boolean on(ServerPlayer p, Toggles.Entry e) {
        return Toggles.of(p).contains(e) && Toggles.enabled(p, e) && Toggles.unavailable(p, e) == null;
    }

    private static Skill skill(ServerPlayer p, Toggles.Entry e) { return Toggles.skill(p, e); }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END || !(ev.player instanceof ServerPlayer p) || p instanceof FakePlayer || !p.isAlive() || p.isSpectator()) return;
        CharacterType t = ModCapabilities.getType(p);
        switch (t) {
            case MAX -> { if (p.tickCount % 40 == 3) lunarMind(p); }
            case TECHNICA -> { gravityBoots(p); if (p.tickCount % 10 == 4) rescue(p); }
            case ULTIMA -> { if (p.tickCount % 10 == 6) predict(p); if (p.tickCount % 20 == 9) livingMastery(p); }
            default -> {}
        }
    }

    // ------------------------------------------------------------------ Max

    private static void lunarMind(ServerPlayer p) {
        if (!on(p, Toggles.Entry.LUNAR_MIND) || !com.xenowarriors.skill.sp.MaxSkills.night(p)) return;
        boolean was = p.hasEffect(MobEffects.DAMAGE_BOOST);
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 60, 1, true, false, true));
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 60, 1, true, false, true));
        if (p.tickCount % 120 == 3) p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 50, 0, true, false, true));
        if (!was) {
            p.serverLevel().sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1, p.getZ(), 20, 0.4, 0.8, 0.4, 0.02);
            p.displayClientMessage(Component.literal("Lunar Mind - the moon is with him").withStyle(ChatFormatting.AQUA), true);
        }
    }

    // ------------------------------------------------------------------ Technica

    /** About to land from a fall that would hurt: the boots fire. */
    private static void gravityBoots(ServerPlayer p) {
        if (p.onGround() || p.fallDistance < 3.6F || p.getDeltaMovement().y > -0.3 || p.getAbilities().flying || p.isFallFlying() || p.isInWater()) return;
        if (!on(p, Toggles.Entry.GRAVITY_BOOTS) || !ready(p, "boots")) return;
        double fall = -p.getDeltaMovement().y;
        BlockPos below = p.blockPosition();
        int clear = 0;
        while (clear < 6 && p.level().getBlockState(below.below(clear + 1)).getCollisionShape(p.level(), below.below(clear + 1)).isEmpty()) clear++;
        if (clear > Math.max(2, (int) Math.ceil(fall * 2.5))) return;                 // (not yet: the ground's still a way off)
        p.fallDistance = 0F;
        p.setDeltaMovement(p.getDeltaMovement().multiply(1, 0.15, 1));
        p.hurtMarked = true;
        p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 30, 0, false, false));
        p.serverLevel().sendParticles(com.xenowarriors.skill.sp.TechSkills.gravParticle(), p.getX(), p.getY() + 0.1, p.getZ(), 30, 0.4, 0.05, 0.4, 0);
        p.level().playSound(null, p.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.8F, 1.8F);
        p.displayClientMessage(Component.literal("Gravity Boots!").withStyle(ChatFormatting.LIGHT_PURPLE), true);
        wait(p, "boots", BOOTS_GAP_S * 20);
        bootsSaved++;
    }

    /** She locked on to someone (LockOn tells the server): her visor scans. */
    public static void lockedOn(ServerPlayer p, int targetId) {
        if (ModCapabilities.getType(p) != CharacterType.TECHNICA || !on(p, Toggles.Entry.VISOR_SCAN) || !ready(p, "visor")) return;
        if (!(p.level().getEntity(targetId) instanceof LivingEntity t) || t.distanceTo(p) > 48) return;
        Skill s = skill(p, Toggles.Entry.VISOR_SCAN);
        if (s == null) return;
        wait(p, "visor", VISOR_GAP_S * 20);
        s.use(p);
        p.displayClientMessage(Component.literal("Visor: " + t.getName().getString() + " - " + (int) t.getHealth() + "/" + (int) t.getMaxHealth() + " HP, armor " + t.getArmorValue())
                .withStyle(ChatFormatting.LIGHT_PURPLE), true);
        visorScans++;
    }

    /** Two or more teammates down near her: the platform comes down for them. */
    private static void rescue(ServerPlayer p) {
        if (!on(p, Toggles.Entry.RESCUE_PLATFORM) || !ready(p, "rescue")) return;
        List<LivingEntity> down = new ArrayList<>();
        for (ServerPlayer m : com.xenowarriors.party.Parties.members(p))
            if (m != p && m.level() == p.level() && m.distanceToSqr(p) < 48 * 48 && com.xenowarriors.party.Duels.isDowned(m)) down.add(m);
        for (ServerPlayer m : com.xenowarriors.party.Parties.members(p))
            for (var b : com.xenowarriors.bot.PartyBots.bots(m)) if (b.level() == p.level() && b.distanceToSqr(p) < 48 * 48 && b.downed()) down.add(b);
        if (down.size() < 2) return;
        Skill s = skill(p, Toggles.Entry.RESCUE_PLATFORM);
        if (s == null) return;
        wait(p, "rescue", RESCUE_GAP_S * 20);
        s.use(p);
        for (LivingEntity d : down) {                                            // (picked up off the ground and back on their feet)
            d.teleportTo(p.getX() + p.getRandom().nextGaussian(), p.getY(), p.getZ() + p.getRandom().nextGaussian());
            if (d instanceof com.xenowarriors.bot.PartyBotEntity b) b.revive();
            else if (d instanceof ServerPlayer m) com.xenowarriors.party.Duels.foxRevive(m, p);
        }
        p.displayClientMessage(Component.literal("Rescue Platform! " + down.size() + " teammates picked up").withStyle(ChatFormatting.GOLD), false);
        rescues++;
    }

    // ------------------------------------------------------------------ Ultima

    private static final String STRAIN = "xw_predict_strain";

    private static void predict(ServerPlayer p) {
        if (!on(p, Toggles.Entry.ATTACK_PREDICTION) || !ready(p, "predict") || com.xenowarriors.party.Duels.cantAct(p)) return;
        boolean threat = !p.level().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(8), m -> m.isAlive() && m.getTarget() == p).isEmpty();
        if (!threat) return;
        Skill s = skill(p, Toggles.Entry.ATTACK_PREDICTION);
        if (s == null) return;
        int cost = com.xenowarriors.skill.SkillLevels.costFor(p, s.getId(), s.getKarmaCost(p));
        if (!com.xenowarriors.karma.Karma.has(p, cost)) return;
        if (!s.use(p)) return;
        if (!p.isCreative()) com.xenowarriors.karma.Karma.add(p, -cost);
        com.xenowarriors.skill.SkillLevels.used(p, s);
        wait(p, "predict", PREDICT_GAP_S * 20);
        predictions++;
        long now = p.level().getGameTime();
        long[] uses = Arrays.stream(p.getPersistentData().getLongArray(STRAIN)).filter(x -> now - x < 90 * 20).toArray();
        long[] next = Arrays.copyOf(uses, uses.length + 1); next[uses.length] = now;
        p.getPersistentData().putLongArray(STRAIN, next);
        if (next.length >= 3) {                                                  // overused: his chip overheats - it switches itself off
            p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 160, 1));
            p.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 100, 0));
            Toggles.setEnabled(p, Toggles.Entry.ATTACK_PREDICTION, false);
            p.displayClientMessage(Component.literal("Attack Prediction overused - his chip overheated and switched it off.").withStyle(ChatFormatting.AQUA), false);
            Toggles.send(p);
        }
    }

    /** Ultima's transform key: Living Weapon (its cost, cooldown, and mastery: it lasts longer the more he masters it). */
    public static boolean livingWeapon(ServerPlayer p) {
        if (ModCapabilities.getType(p) != CharacterType.ULTIMA) return false;
        Skill s = skill(p, Toggles.Entry.LIVING_WEAPON);
        if (s == null) return true;
        if (Toggles.active(p, Toggles.Entry.LIVING_WEAPON)) {
            p.displayClientMessage(Component.literal("Living Weapon can't be stopped - wait for it to end").withStyle(ChatFormatting.GREEN), true);
            return true;
        }
        float m = com.xenowarriors.skill.FormMastery.get(p, com.xenowarriors.skill.FormMastery.LIVING);
        if (SkillManager.useFree(p, s, com.xenowarriors.skill.FormMastery.cooldownMult(m))) {
            long until = p.getPersistentData().getLong(com.xenowarriors.skill.ultima.LivingWeaponSkill.UNTIL);
            long now = p.level().getGameTime();
            p.getPersistentData().putLong(com.xenowarriors.skill.ultima.LivingWeaponSkill.UNTIL, now + Math.round((until - now) * com.xenowarriors.skill.FormMastery.durationMult(m)));
        }
        return true;
    }

    private static void livingMastery(ServerPlayer p) {
        if (Toggles.active(p, Toggles.Entry.LIVING_WEAPON)) com.xenowarriors.skill.FormMastery.add(p, com.xenowarriors.skill.FormMastery.LIVING, com.xenowarriors.skill.FormMastery.PER_SECOND);
    }

    // ------------------------------------------------------------------ Kaeru

    private static final Map<UUID, long[]> TRACK = new HashMap<>();               // Kaeru -> {target id, until}

    public static boolean tracking(ServerPlayer p) {
        long[] t = TRACK.get(p.getUUID());
        return t != null && t[1] > p.level().getGameTime();
    }

    /** The scent picker: everything she can smell within range - enemies, animals, people (not her own party). */
    public static void openScent(ServerPlayer p) {
        List<com.xenowarriors.network.ScentPacket.Entry> l = new ArrayList<>();
        for (LivingEntity e : p.serverLevel().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(SCENT_RANGE),
                e -> e != p && e.isAlive() && !(e instanceof net.minecraft.world.entity.decoration.ArmorStand) && !com.xenowarriors.combat.Allies.isAlly(p, e))) {
            int kind = e instanceof net.minecraft.world.entity.monster.Enemy ? 0 : e instanceof net.minecraft.world.entity.player.Player ? 2 : 1;
            l.add(new com.xenowarriors.network.ScentPacket.Entry(e.getId(), e.getName().getString(), kind, (int) e.distanceTo(p)));
        }
        l.sort(Comparator.comparingInt(com.xenowarriors.network.ScentPacket.Entry::kind).thenComparingInt(com.xenowarriors.network.ScentPacket.Entry::distance));
        if (l.size() > 24) l = new ArrayList<>(l.subList(0, 24));
        p.level().playSound(null, p.blockPosition(), SoundEvents.FOX_SNIFF, SoundSource.PLAYERS, 1F, 1.1F);
        ModNetwork.sendToPlayer(new com.xenowarriors.network.ScentPacket(l), p);
    }

    /** She picked one: it glows through walls and its scent trail shows (Karma and the skill's cooldown). */
    public static void pickScent(ServerPlayer p, int id) {
        if (ModCapabilities.getType(p) != CharacterType.KAERU || !(p.level().getEntity(id) instanceof LivingEntity t) || t.distanceTo(p) > SCENT_RANGE + 8) return;
        Skill s = skill(p, Toggles.Entry.SCENT_TRACK);
        if (s == null) return;
        long left = SkillManager.freeCooldownLeft(p, s);
        if (left > 0) { p.displayClientMessage(Component.translatable("message.xenowarriors.cooldown", s.getName(), (left + 19) / 20), true); return; }
        int cost = com.xenowarriors.skill.SkillLevels.costFor(p, s.getId(), s.getKarmaCost(p));
        if (!com.xenowarriors.karma.Karma.trySpend(p, cost)) return;
        SkillManager.setFreeCooldown(p, s, com.xenowarriors.skill.SkillLevels.cooldownFor(p, s.getId(), s.getCooldownTicks()));
        com.xenowarriors.skill.SkillLevels.used(p, s);
        t.addEffect(new MobEffectInstance(MobEffects.GLOWING, SCENT_TICKS, 0, false, false));
        TRACK.put(p.getUUID(), new long[]{id, p.level().getGameTime() + SCENT_TICKS});
        ModNetwork.sendToPlayer(new com.xenowarriors.network.TrackTargetPacket(id, SCENT_TICKS), p);
        ModNetwork.sendToPlayer(new com.xenowarriors.network.ScentPacket(List.of(new com.xenowarriors.network.ScentPacket.Entry(id, "", -1, SCENT_TICKS))), p);   // (the trail)
        p.displayClientMessage(Component.literal("Got their scent: " + t.getName().getString() + " (" + (int) t.distanceTo(p) + " blocks)").withStyle(ChatFormatting.GOLD), true);
        p.level().playSound(null, p.blockPosition(), SoundEvents.FOX_SNIFF, SoundSource.PLAYERS, 1F, 1.3F);
    }
}

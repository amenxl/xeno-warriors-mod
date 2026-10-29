package com.xenowarriors.you;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.character.ModCapabilities;
import com.xenowarriors.character.Race;
import com.xenowarriors.network.ModNetwork;
import com.xenowarriors.network.YouStatePacket;
import com.xenowarriors.skill.Rooting;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/**
 * What a You character's race does in play:
 * Seedlings live on sunlight (a Sun meter instead of hunger; Sunlight refills it at night), heal in water, and on
 * death become a seed that sprouts back at half health after 30 seconds in water - unless an enemy breaks it.
 * Angels and Demons are tougher from level 1. Winged races fly (see RhalixFlight); Demons can hide in a human form.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class YouRaces {
    private YouRaces() {}

    private static final UUID HEALTH_ID = UUID.fromString("7e1c6c2a-5d11-4f1f-9a0b-2b7c9e6d1a01");
    public static final int SUN_MAX = 1200;                    // seconds' worth: a full charge lasts 20 minutes in the dark
    private static final String SUN = "xw_sun", SEED = "xw_seed", SEED_WATER = "xw_seed_water", SEED_TAG = "xw_seed_display",
            FORM = "xw_you_form", FORM_UNTIL = "xw_you_form_until";

    // ------------------------------------------------------------------ stats

    /** Level-1 toughness: Angels far above everyone, Demons and Seedlings a little. Reapplied with the other stats. */
    public static void apply(ServerPlayer p) {
        AttributeInstance hp = p.getAttribute(Attributes.MAX_HEALTH);
        if (hp == null) return;
        hp.removeModifier(HEALTH_ID);
        YouSlot s = You.slot(p);
        if (s == null) return;
        double add = switch (s.race) { case ANGEL -> 40; case DEMON -> 6; case SEEDLING, YOU -> 4; default -> 0; };
        if (add > 0) hp.addPermanentModifier(new AttributeModifier(HEALTH_ID, "You race health", add, AttributeModifier.Operation.ADDITION));
        if (p.getHealth() > p.getMaxHealth()) p.setHealth(p.getMaxHealth());
    }

    /** Damage dealt, by race. */
    public static float damageMult(Player p) {
        YouSlot s = You.slot(p);
        if (s == null) return 1F;
        return switch (s.race) { case ANGEL -> 2F; case DEMON -> s.humanForm ? 1F : 1.15F; default -> 1F; };
    }

    // ------------------------------------------------------------------ forms

    /** A timed form (Lunar/Solar) for the HUD and the wings' glow. */
    public static void form(ServerPlayer p, String name, int ticks) {
        p.getPersistentData().putString(FORM, name);
        p.getPersistentData().putLong(FORM_UNTIL, p.level().getGameTime() + ticks);
        sync(p);
    }

    public static String form(Player p) {
        CompoundTag t = p.getPersistentData();
        return t.getLong(FORM_UNTIL) > p.level().getGameTime() ? t.getString(FORM) : "";
    }

    /** The transform key: a Demon switches between demon and human form. */
    public static boolean toggleHumanForm(ServerPlayer p) {
        YouSlot s = You.slot(p);
        if (s == null || s.race != Race.DEMON) return false;
        s.humanForm = !s.humanForm;
        p.serverLevel().sendParticles(s.humanForm ? ParticleTypes.CLOUD : ParticleTypes.SOUL_FIRE_FLAME, p.getX(), p.getY() + 1, p.getZ(), 30, 0.5, 0.8, 0.5, 0.03);
        p.level().playSound(null, p.blockPosition(), s.humanForm ? SoundEvents.ILLUSIONER_MIRROR_MOVE : SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.8F, s.humanForm ? 1.4F : 0.7F);
        p.displayClientMessage(Component.literal(s.humanForm ? "Human form" : "Demon form"), true);
        com.xenowarriors.character.CharacterManager.syncToTrackingAndSelf(p);
        if (s.humanForm) com.xenowarriors.character.RhalixFlight.land(p);
        return true;
    }

    // ------------------------------------------------------------------ seedlings

    public static int sun(Player p) { return You.slot(p) != null && You.slot(p).race == Race.SEEDLING ? keep(p).getInt(SUN) : -1; }

    private static CompoundTag keep(Player p) {                  // (survives death: Forge keeps this sub-tag)
        CompoundTag root = p.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG)) root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        return root.getCompound(Player.PERSISTED_NBT_TAG);
    }

    public static boolean isSeed(Player p) { return p.getPersistentData().getBoolean(SEED); }

    /** Sunlight (the item): a full charge. */
    public static void giveSun(ServerPlayer p) { keep(p).putInt(SUN, SUN_MAX); sync(p); }

    private static boolean seedling(Player p) { YouSlot s = You.slot(p); return s != null && s.race == Race.SEEDLING; }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p)) return;
        if (isSeed(p)) { seedTick(p); return; }
        if (!seedling(p) || p.isCreative() || p.isSpectator()) return;
        CompoundTag t = keep(p);
        if (!t.contains(SUN)) t.putInt(SUN, SUN_MAX);
        int sun = t.getInt(SUN);
        if (p.tickCount % 20 == 0) {
            boolean inSun = p.level().isDay() && p.level().canSeeSky(p.blockPosition()) && !p.level().isRaining();
            sun = inSun ? Math.min(SUN_MAX, sun + 40) : Math.max(0, sun - 1);
            t.putInt(SUN, sun);
            if (p.tickCount % 60 == 0) sync(p);
            if (sun == 0 && p.tickCount % 200 == 0) p.displayClientMessage(Component.literal("You need sunlight...").withStyle(ChatFormatting.YELLOW), true);
        }
        // sunlight instead of food: the food bar stays put; with no sun you can't sprint or regenerate
        p.getFoodData().setFoodLevel(sun > 0 ? 20 : 4);
        if (p.getFoodData().getSaturationLevel() > 2F) p.getFoodData().setSaturation(2F);
        if (sun > 0 && p.isInWaterOrRain() && p.tickCount % 40 == 0 && p.getHealth() < p.getMaxHealth()) {
            p.heal(1F);
            p.serverLevel().sendParticles(ParticleTypes.HAPPY_VILLAGER, p.getX(), p.getY() + 1, p.getZ(), 3, 0.3, 0.4, 0.3, 0);
        }
    }

    /** A Seedling's death: they become a seed instead (unless something else already handled the death). */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onDeath(LivingDeathEvent e) {
        if (e.isCanceled() || !(e.getEntity() instanceof ServerPlayer p) || !seedling(p) || isSeed(p)) return;
        if (p.getPersistentData().getBoolean("xw_seed_broken")) { p.getPersistentData().remove("xw_seed_broken"); return; }
        if (com.xenowarriors.journey.Journey.inRun(p) || com.xenowarriors.party.Duels.inDuel(p) || com.xenowarriors.party.Duels.isDowned(p)
                || e.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY) || p.level().getLevelData().isHardcore()) return;
        e.setCanceled(true);
        p.setHealth(1F);
        p.removeAllEffects();
        p.getPersistentData().putBoolean(SEED, true);
        p.getPersistentData().putInt(SEED_WATER, 0);
        p.getPersistentData().putDouble("xw_seed_x", p.getX()); p.getPersistentData().putDouble("xw_seed_z", p.getZ());
        p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 20 * 60 * 60, 0, false, false, false));
        placeSeed(p);
        p.displayClientMessage(Component.literal("You've become a seed. Get into water to sprout again...").withStyle(ChatFormatting.GREEN), false);
        p.level().playSound(null, p.blockPosition(), SoundEvents.AZALEA_LEAVES_BREAK, SoundSource.PLAYERS, 1F, 0.6F);
        sync(p);
    }

    /** While a seed: can't move or act; an enemy's hit breaks the seed (a real death); 30 seconds in water sprouts you back. */
    private static void seedTick(ServerPlayer p) {
        CompoundTag t = p.getPersistentData();
        Rooting.hold(p, new Vec3(t.getDouble("xw_seed_x"), p.getY(), t.getDouble("xw_seed_z")));
        if (p.tickCount % 10 == 0) p.serverLevel().sendParticles(ParticleTypes.COMPOSTER, p.getX(), p.getY() + 0.3, p.getZ(), 2, 0.3, 0.2, 0.3, 0);
        if (p.isInWater() || p.isInWaterOrRain()) {
            int w = t.getInt(SEED_WATER) + 1;
            t.putInt(SEED_WATER, w);
            if (w % 20 == 0) p.displayClientMessage(Component.literal("Sprouting in " + (30 - w / 20) + "s").withStyle(ChatFormatting.GREEN), true);
            if (w >= 600) sprout(p);
        } else if (p.tickCount % 40 == 0) p.displayClientMessage(Component.literal("A seed... you need water").withStyle(ChatFormatting.DARK_GREEN), true);
        if (p.isSpectator() || p.isCreative()) sprout(p);
    }

    private static void sprout(ServerPlayer p) {
        CompoundTag t = p.getPersistentData();
        t.remove(SEED); t.remove(SEED_WATER);
        removeSeed(p);
        p.removeEffect(MobEffects.INVISIBILITY);
        p.setHealth(Math.max(1F, p.getMaxHealth() * 0.5F));
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 60, 2));
        p.serverLevel().sendParticles(ParticleTypes.CHERRY_LEAVES, p.getX(), p.getY() + 1, p.getZ(), 40, 0.6, 0.8, 0.6, 0.05);
        p.level().playSound(null, p.blockPosition(), SoundEvents.AZALEA_LEAVES_PLACE, SoundSource.PLAYERS, 1F, 1.2F);
        p.displayClientMessage(Component.literal("You sprouted back!").withStyle(ChatFormatting.GREEN), true);
        sync(p);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttack(LivingAttackEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !isSeed(p)) return;
        boolean enemy = e.getSource().getEntity() instanceof net.minecraft.world.entity.LivingEntity && e.getSource().getEntity() != p;
        if (!enemy && !e.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) e.setCanceled(true);   // fire, falls, drowning: a seed doesn't mind (/kill still works)
    }

    /** An enemy's hit that would finish the seed breaks it: they die for real. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamage(net.minecraftforge.event.entity.living.LivingDamageEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !isSeed(p) || e.isCanceled()) return;
        boolean enemy = e.getSource().getEntity() instanceof net.minecraft.world.entity.LivingEntity && e.getSource().getEntity() != p;
        if (!enemy && !e.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        if (e.getAmount() < p.getHealth()) return;              // (took the hit and lived: still a seed)
        p.getPersistentData().remove(SEED);
        p.getPersistentData().putBoolean("xw_seed_broken", true);
        removeSeed(p);
        p.removeEffect(MobEffects.INVISIBILITY);
        p.displayClientMessage(Component.literal("Your seed was broken!").withStyle(ChatFormatting.RED), false);
    }

    private static void placeSeed(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        CompoundTag t = new CompoundTag();
        t.putString("id", "minecraft:item_display");
        t.put("item", new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BEETROOT_SEEDS).save(new CompoundTag()));
        t.putString("billboard", "center");
        CompoundTag tr = new CompoundTag();
        tr.put("left_rotation", floats(0, 0, 0, 1)); tr.put("right_rotation", floats(0, 0, 0, 1));
        tr.put("translation", floats(0, 0, 0)); tr.put("scale", floats(1.4F, 1.4F, 1.4F));
        t.put("transformation", tr);
        Entity d = EntityType.loadEntityRecursive(t, level, en -> { en.moveTo(p.getX(), p.getY() + 0.4, p.getZ(), 0, 0); return en; });
        if (d != null) { d.addTag(SEED_TAG); d.getPersistentData().putUUID("xw_owner", p.getUUID()); level.addFreshEntity(d); p.getPersistentData().putUUID("xw_seed_disp", d.getUUID()); }
    }

    private static void removeSeed(ServerPlayer p) {
        if (p.getPersistentData().hasUUID("xw_seed_disp")) {
            Entity d = p.serverLevel().getEntity(p.getPersistentData().getUUID("xw_seed_disp"));
            if (d != null) d.discard();
            p.getPersistentData().remove("xw_seed_disp");
        }
    }

    private static net.minecraft.nbt.ListTag floats(float... v) {
        net.minecraft.nbt.ListTag l = new net.minecraft.nbt.ListTag();
        for (float f : v) l.add(net.minecraft.nbt.FloatTag.valueOf(f));
        return l;
    }

    /** On login: a seed display that was unloaded is put back; stale seed displays are cleaned when their owner sprouts. */
    @SubscribeEvent
    public static void onLogin(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        if (isSeed(p)) { removeSeed(p); placeSeed(p); }
        sync(p);
    }

    // ------------------------------------------------------------------ sync

    public static void sync(ServerPlayer p) {
        ModNetwork.sendToPlayer(new YouStatePacket(sun(p), isSeed(p), form(p)), p);
    }

    /** True when this player's character is a You character of a given race (server). */
    public static boolean is(Player p, Race r) { YouSlot s = You.slot(p); return s != null && s.race == r; }

    /** Seedlings don't eat; the food bar is driven by the Sun meter. */
    public static boolean noHunger(Player p) { return seedling(p); }

    public static boolean isYou(Player p) { return ModCapabilities.getType(p) == CharacterType.YOU; }
}

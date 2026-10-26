package com.xenowarriors.boss;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.stats.Stat;
import com.xenowarriors.stats.Stats;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.*;

/**
 * Named mini-bosses: now and then a monster spawns as a named champion ("Grask the Unbroken") with a boss bar, far more
 * health and hits, a glow, and one special power. Beat one for emeralds, rare drops and a big chunk of stat XP.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MiniBosses {
    public static final String TAG = "xw_miniboss", POWER = "xw_mb_power";
    public static final float CHANCE = 0.015F;
    private static final UUID HP = UUID.fromString("a4d7c3e1-2b6f-4c1a-9e88-31f0c2d4b501"), DMG = UUID.fromString("a4d7c3e1-2b6f-4c1a-9e88-31f0c2d4b502"),
            SPD = UUID.fromString("a4d7c3e1-2b6f-4c1a-9e88-31f0c2d4b503"), ARM = UUID.fromString("a4d7c3e1-2b6f-4c1a-9e88-31f0c2d4b504");
    /** The powers: name shown in the title, boss bar color. */
    public enum Power {
        BLAZING("Blazing", BossEvent.BossBarColor.RED), SWIFT("Swift", BossEvent.BossBarColor.YELLOW), IRONHIDE("Ironhide", BossEvent.BossBarColor.WHITE),
        KARMA_EATER("Karma-Eater", BossEvent.BossBarColor.PURPLE), SUMMONER("Summoner", BossEvent.BossBarColor.GREEN), FROST("Frostbound", BossEvent.BossBarColor.BLUE);
        final String title; final BossEvent.BossBarColor color;
        Power(String t, BossEvent.BossBarColor c) { title = t; color = c; }
    }
    private static final String[] FIRST = {"Grask", "Vorn", "Ilka", "Morrow", "Tesk", "Ruun", "Sable", "Krell", "Odra", "Brakka", "Venn", "Zhur", "Ashe", "Dren", "Mokk", "Yira"};
    private static final String[] TITLE = {"the Unbroken", "the Hungry", "the Pale", "Bonebreaker", "the Restless", "of the Deep Dark", "the Howling", "Ironjaw",
            "the Cursed", "Nightstalker", "the Wretched", "Skullsplitter", "the Undying", "the Vile"};
    private static final Map<UUID, ServerBossEvent> BARS = new HashMap<>();

    private MiniBosses() {}

    public static boolean is(Entity e) { return e.getPersistentData().getBoolean(TAG); }

    private static Power power(Entity e) { return Power.values()[Math.floorMod(e.getPersistentData().getInt(POWER), Power.values().length)]; }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide || !(event.getEntity() instanceof Monster m) || m.isPassenger()) return;
        var t = m.getPersistentData();
        if (!t.contains("xw_mb_rolled")) {
            t.putBoolean("xw_mb_rolled", true);
            if (t.getBoolean("xw_tower") || event.loadedFromDisk() || m.getRandom().nextFloat() >= CHANCE || m.getType() == EntityType.CREEPER) return;
            make(m, null);
        }
        if (is(m)) bar(m);
    }

    /** Turns this monster into a named mini-boss (power null = random). */
    public static void make(Mob m, Power p) {
        var r = m.getRandom();
        if (p == null) p = Power.values()[r.nextInt(Power.values().length)];
        var t = m.getPersistentData();
        t.putBoolean(TAG, true);
        t.putBoolean("xw_mb_rolled", true);
        t.putInt(POWER, p.ordinal());
        String name = FIRST[r.nextInt(FIRST.length)] + " " + TITLE[r.nextInt(TITLE.length)];
        m.setCustomName(Component.literal(name).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        m.setCustomNameVisible(true);
        m.setPersistenceRequired();
        mod(m, Attributes.MAX_HEALTH, HP, 3.0, AttributeModifier.Operation.MULTIPLY_TOTAL);
        mod(m, Attributes.ATTACK_DAMAGE, DMG, 0.75, AttributeModifier.Operation.MULTIPLY_TOTAL);
        if (p == Power.SWIFT) mod(m, Attributes.MOVEMENT_SPEED, SPD, 0.45, AttributeModifier.Operation.MULTIPLY_TOTAL);
        if (p == Power.IRONHIDE) { mod(m, Attributes.ARMOR, ARM, 14, AttributeModifier.Operation.ADDITION); mod(m, Attributes.KNOCKBACK_RESISTANCE, ARM, 0.8, AttributeModifier.Operation.ADDITION); }
        if (p == Power.BLAZING) m.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, -1, 0, false, false));
        m.addEffect(new MobEffectInstance(MobEffects.GLOWING, -1, 0, false, false));
        m.setHealth(m.getMaxHealth());
    }

    private static void mod(Mob m, net.minecraft.world.entity.ai.attributes.Attribute a, UUID id, double v, AttributeModifier.Operation op) {
        var inst = m.getAttribute(a);
        if (inst == null) return;
        inst.removeModifier(id);
        inst.addPermanentModifier(new AttributeModifier(id, "XW mini-boss", v, op));
    }

    private static void bar(Mob m) {
        Power p = power(m);
        ServerBossEvent b = new ServerBossEvent(Component.literal("").append(m.getCustomName() == null ? m.getName() : m.getCustomName())
                .append(Component.literal("  [" + p.title + "]").withStyle(ChatFormatting.GRAY)), p.color, BossEvent.BossBarOverlay.NOTCHED_10);
        ServerBossEvent old = BARS.put(m.getUUID(), b);
        if (old != null) old.removeAllPlayers();
    }

    @SubscribeEvent
    public static void onLeave(EntityLeaveLevelEvent event) {
        ServerBossEvent b = BARS.remove(event.getEntity().getUUID());
        if (b != null) b.removeAllPlayers();
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || BARS.isEmpty() || event.getServer().getTickCount() % 5 != 0) return;
        for (ServerLevel l : event.getServer().getAllLevels()) {
            for (Map.Entry<UUID, ServerBossEvent> e : BARS.entrySet()) {
                if (!(l.getEntity(e.getKey()) instanceof Mob m) || !m.isAlive()) continue;
                ServerBossEvent b = e.getValue();
                b.setProgress(Math.max(0F, m.getHealth() / m.getMaxHealth()));
                for (ServerPlayer p : l.players()) { if (p.distanceToSqr(m) < 36 * 36) b.addPlayer(p); else b.removePlayer(p); }
                powers(l, m);
            }
        }
    }

    /** Each power's own tricks (called every 5 ticks). */
    private static void powers(ServerLevel l, Mob m) {
        Power p = power(m);
        LivingEntity t = m.getTarget();
        int tick = m.tickCount;
        switch (p) {
            case BLAZING -> {
                l.sendParticles(ParticleTypes.FLAME, m.getX(), m.getY() + 1, m.getZ(), 3, .35, .5, .35, .01);
                if (t != null && tick % 80 < 5 && m.distanceTo(t) < 4) { t.setSecondsOnFire(4); l.sendParticles(ParticleTypes.LAVA, t.getX(), t.getY() + 1, t.getZ(), 6, .3, .3, .3, 0); }
            }
            case SWIFT -> { if (tick % 10 < 5) l.sendParticles(ParticleTypes.CLOUD, m.getX(), m.getY() + .2, m.getZ(), 2, .2, 0, .2, .01); }
            case IRONHIDE -> l.sendParticles(new DustParticleOptions(new Vector3f(.8F, .8F, .85F), 1F), m.getX(), m.getY() + 1, m.getZ(), 2, .35, .5, .35, 0);
            case KARMA_EATER -> {
                l.sendParticles(new DustParticleOptions(new Vector3f(.55F, .2F, .8F), 1.1F), m.getX(), m.getY() + 1, m.getZ(), 2, .4, .6, .4, 0);
                if (t instanceof ServerPlayer sp && tick % 40 < 5 && m.distanceTo(sp) < 8) {
                    sp.getCapability(com.xenowarriors.character.ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> {
                        d.setKarma(d.getKarma() - 8F);
                        com.xenowarriors.karma.Karma.sync(sp, d);
                    });
                    m.heal(4F);
                    sp.displayClientMessage(Component.literal(m.getName().getString() + " eats your Karma!").withStyle(ChatFormatting.DARK_PURPLE), true);
                }
            }
            case SUMMONER -> {
                if (t != null && tick % 300 < 5 && l.getEntitiesOfClass(Monster.class, m.getBoundingBox().inflate(12), x -> x.getPersistentData().getBoolean("xw_mb_minion")).size() < 4) {
                    for (int i = 0; i < 2; i++) {
                        Mob add = (Mob) m.getType().create(l);
                        if (add == null) break;
                        add.moveTo(m.getX() + m.getRandom().nextInt(5) - 2, m.getY(), m.getZ() + m.getRandom().nextInt(5) - 2, m.getYRot(), 0);
                        add.getPersistentData().putBoolean("xw_mb_rolled", true);
                        add.getPersistentData().putBoolean("xw_mb_minion", true);
                        add.setTarget(t);
                        l.addFreshEntity(add);
                        l.sendParticles(ParticleTypes.SOUL, add.getX(), add.getY() + 1, add.getZ(), 10, .3, .5, .3, .02);
                    }
                    l.playSound(null, m.blockPosition(), SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.HOSTILE, 1F, 1F);
                }
            }
            case FROST -> {
                l.sendParticles(ParticleTypes.SNOWFLAKE, m.getX(), m.getY() + 1, m.getZ(), 2, .4, .6, .4, .01);
                if (t != null && tick % 60 < 5 && m.distanceTo(t) < 6) t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2));
            }
        }
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (event.getSource().getEntity() instanceof Mob m && is(m) && power(m) == Power.BLAZING) event.getEntity().setSecondsOnFire(2);
    }

    /** Beaten: loot and a big chunk of stat XP for whoever brought it down. */
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Mob m) || !is(m) || !(m.level() instanceof ServerLevel l)) return;
        ServerBossEvent b = BARS.remove(m.getUUID());
        if (b != null) b.removeAllPlayers();
        var r = m.getRandom();
        List<ItemStack> loot = new ArrayList<>();
        loot.add(new ItemStack(Items.EMERALD, 6 + r.nextInt(10)));
        loot.add(new ItemStack(Items.GOLD_INGOT, 2 + r.nextInt(4)));
        if (r.nextFloat() < 0.6F) { var k = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(XenoWarriors.id("karma_potion")); if (k != null) loot.add(new ItemStack(k)); }
        if (r.nextFloat() < 0.4F) { var d = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(XenoWarriors.id("data_shard")); if (d != null) loot.add(new ItemStack(d, 1 + r.nextInt(2))); }
        if (r.nextFloat() < 0.35F) {                                        // a piece of enchanted gear
            ItemStack[] gear = {new ItemStack(Items.IRON_SWORD), new ItemStack(Items.DIAMOND_SWORD), new ItemStack(Items.IRON_CHESTPLATE), new ItemStack(Items.BOW), new ItemStack(Items.DIAMOND_PICKAXE)};
            loot.add(EnchantmentHelper.enchantItem(r, gear[r.nextInt(gear.length)], 20 + r.nextInt(15), false));
        }
        if (r.nextFloat() < 0.15F) loot.add(new ItemStack(Items.DIAMOND, 1 + r.nextInt(2)));
        for (ItemStack s : loot) l.addFreshEntity(new ItemEntity(l, m.getX(), m.getY() + .5, m.getZ(), s));
        l.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, m.getX(), m.getY() + 1, m.getZ(), 40, .5, .8, .5, .3);
        l.playSound(null, m.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.HOSTILE, 1F, 1F);
        ServerPlayer p = com.xenowarriors.collection.Collection.credit(event.getSource().getEntity());
        if (p == null) return;
        for (Stat s : Stat.values()) Stats.addXp(p, s, s.needed(Stats.level(p, s)) * 0.5F);
        String name = m.getCustomName() == null ? m.getName().getString() : m.getCustomName().getString();
        for (ServerPlayer o : l.players()) if (o.distanceToSqr(m) < 64 * 64)
            o.sendSystemMessage(Component.literal(p.getName().getString() + " defeated " + name + "!").withStyle(ChatFormatting.GOLD));
    }
}

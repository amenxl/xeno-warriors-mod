package com.xenowarriors.mikiko;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.ModCapabilities;
import com.xenowarriors.character.PlayerCharacter;
import com.xenowarriors.item.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

/**
 * While Mikiko is inside the Hekublade, the blade stays behind where he was standing, floating and slowly turning.
 * Anyone who hits it - a player, or a monster that goes for it - cuts his time inside the blade short.
 * (An invisible stand takes the hits; a display shows the blade.)
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class HekubladeAnchor {
    private static final String TAG = "xw_blade_anchor", OWNER = "xw_blade_owner";
    /** Ticks of blade time lost per point of damage (at least {@link #MIN_LOSS} a hit). */
    private static final int PER_DAMAGE = 30, MIN_LOSS = 60;

    private record Anchor(ServerLevel level, UUID stand, UUID display) {}
    private static final Map<UUID, Anchor> LIVE = new HashMap<>();
    private static final Set<UUID> ALLOWED = new HashSet<>();

    private HekubladeAnchor() {}

    /** (UI test) keep a placed blade even though its owner isn't inside. */
    public static boolean testKeep;

    /** (UI test) the floating blade's hitbox for this owner. */
    public static Mob testStand(ServerPlayer p) { Anchor a = LIVE.get(p.getUUID()); return a == null ? null : a.level().getEntity(a.stand()) instanceof Mob m ? m : null; }

    /** Mikiko just stepped into the blade from {@code level} at x/y/z: leave the blade floating there. */
    public static void place(ServerPlayer p, ServerLevel level, double x, double y, double z) {
        remove(p.getUUID());
        ItemStack blade = ItemStack.EMPTY;
        for (ItemStack s : p.getInventory().items) if (s.is(ModItems.HEKUBLADE.get())) { blade = s.copy(); break; }
        if (blade.isEmpty() && p.getOffhandItem().is(ModItems.HEKUBLADE.get())) blade = p.getOffhandItem().copy();
        if (blade.isEmpty()) blade = new ItemStack(ModItems.HEKUBLADE.get());
        blade.setCount(1);

        // the hitbox: an invisible, frozen husk (so monsters can go for it and every hit is seen); it never takes damage
        var stand = EntityType.HUSK.create(level);
        if (stand == null) return;
        stand.moveTo(x, y + 0.3, z, p.getYRot(), 0);
        stand.setInvisible(true);
        stand.setNoGravity(true);
        stand.setNoAi(true);
        stand.setSilent(true);
        stand.setPersistenceRequired();
        stand.setCanPickUpLoot(false);
        stand.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.INVISIBILITY, -1, 0, false, false));
        stand.setCustomName(Component.literal(p.getGameProfile().getName() + "'s Hekublade").withStyle(ChatFormatting.GOLD));
        stand.setCustomNameVisible(false);
        stand.addTag(TAG);
        stand.getPersistentData().putUUID(OWNER, p.getUUID());
        stand.getPersistentData().putDouble("xw_ax", x); stand.getPersistentData().putDouble("xw_ay", y + 0.3); stand.getPersistentData().putDouble("xw_az", z);
        ALLOWED.add(stand.getUUID());
        level.addFreshEntity(stand);

        CompoundTag t = new CompoundTag();
        t.putString("id", "minecraft:item_display");
        t.put("item", blade.save(new CompoundTag()));
        t.putString("item_display", "fixed");
        t.putString("billboard", "fixed");
        t.putInt("interpolation_duration", 20);
        CompoundTag tr = new CompoundTag();
        tr.put("left_rotation", floats(0, 0, 0.3826834F, 0.9238795F));          // tilted 45 degrees, point down
        tr.put("right_rotation", floats(0, 0, 0, 1));
        tr.put("translation", floats(0, 0, 0));
        tr.put("scale", floats(1.6F, 1.6F, 1.6F));
        t.put("transformation", tr);
        Entity display = EntityType.loadEntityRecursive(t, level, en -> { en.moveTo(x, y + 0.9, z, p.getYRot(), 0); return en; });
        if (display != null) {
            ALLOWED.add(display.getUUID());
            display.addTag(TAG);
            display.getPersistentData().putUUID(OWNER, p.getUUID());
            level.addFreshEntity(display);
        }
        LIVE.put(p.getUUID(), new Anchor(level, stand.getUUID(), display == null ? null : display.getUUID()));
        level.sendParticles(ParticleTypes.END_ROD, x, y + 1.3, z, 20, .3, .6, .3, .02);
    }

    private static ListTag floats(float... v) { ListTag l = new ListTag(); for (float f : v) l.add(FloatTag.valueOf(f)); return l; }

    /** Mikiko came out: the blade's back with him. */
    public static void remove(UUID owner) {
        Anchor a = LIVE.remove(owner);
        if (a == null) return;
        for (UUID id : new UUID[]{a.stand(), a.display()}) {
            if (id == null) continue;
            Entity e = a.level().getEntity(id);
            if (e != null) e.discard();
        }
    }

    /** A hit on the floating blade: no damage to it, but Mikiko loses time inside. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttack(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof Mob stand) || !stand.getTags().contains(TAG)) return;
        event.setCanceled(true);
        if (stand.level().isClientSide || stand.getPersistentData().getLong("xw_blade_hit") > stand.level().getGameTime()) return;
        stand.getPersistentData().putLong("xw_blade_hit", stand.level().getGameTime() + 10);
        Entity by = event.getSource().getEntity();
        if (!(by instanceof Player) && !(by instanceof Enemy)) return;          // (only people and monsters count)
        if (!stand.getPersistentData().hasUUID(OWNER)) return;
        UUID owner = stand.getPersistentData().getUUID(OWNER);
        ServerPlayer mikiko = stand.getServer() == null ? null : stand.getServer().getPlayerList().getPlayer(owner);
        if (mikiko == null || by == mikiko) return;
        int loss = Math.max(MIN_LOSS, Math.round(event.getAmount() * PER_DAMAGE));
        PlayerCharacter c = mikiko.getCapability(ModCapabilities.PLAYER_CHARACTER).resolve().orElse(null);
        if (c == null || !HekubladeRealm.inRealm(mikiko)) return;
        c.realmTicks = Math.max(0, c.realmTicks - loss);
        mikiko.removeEffect(com.xenowarriors.ModEffects.BLADE_BOUND.get());
        if (c.realmTicks > 0) mikiko.addEffect(new net.minecraft.world.effect.MobEffectInstance(com.xenowarriors.ModEffects.BLADE_BOUND.get(), c.realmTicks, 0, false, false, true));
        String who = by == null ? "Something" : by instanceof Player bp ? bp.getGameProfile().getName() : by.getName().getString();
        mikiko.displayClientMessage(Component.literal(who + " is attacking your Hekublade! -" + (loss + 19) / 20 + "s  (" + c.realmTicks / 20 + "s left)").withStyle(ChatFormatting.RED), true);
        mikiko.playNotifySound(SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, 1F, 0.6F);
        ServerLevel l = (ServerLevel) stand.level();
        l.sendParticles(ParticleTypes.CRIT, stand.getX(), stand.getY() + 1.2, stand.getZ(), 12, .3, .5, .3, .2);
        l.playSound(null, stand.blockPosition(), SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, 1F, 0.8F);
    }

    /** The blade turns and glints; monsters nearby with nothing better to do go for it. */
    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || LIVE.isEmpty()) return;
        int tick = event.getServer().getTickCount();
        for (var it = LIVE.entrySet().iterator(); it.hasNext(); ) {
            var en = it.next();
            ServerPlayer owner = event.getServer().getPlayerList().getPlayer(en.getKey());
            Anchor a = en.getValue();
            if (!testKeep && (owner == null || !HekubladeRealm.inRealm(owner))) {               // (out of the blade some other way)
                for (UUID id : new UUID[]{a.stand(), a.display()}) { if (id == null) continue; Entity e = a.level().getEntity(id); if (e != null) e.discard(); }
                it.remove();
                continue;
            }
            Entity stand = a.level().getEntity(a.stand());
            if (stand == null) continue;                                         // (chunk not loaded: nothing can reach it)
            if (tick % 20 == 0 && stand.getPersistentData().contains("xw_ax")) {   // (stays put if something pushes it)
                stand.teleportTo(stand.getPersistentData().getDouble("xw_ax"), stand.getPersistentData().getDouble("xw_ay"), stand.getPersistentData().getDouble("xw_az"));
                stand.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            }
            if (tick % 20 == 0 && a.display() != null && a.level().getEntity(a.display()) instanceof net.minecraft.world.entity.Display.ItemDisplay d) {
                d.setYRot((d.getYRot() + 18F) % 360F);
                a.level().sendParticles(ParticleTypes.END_ROD, d.getX(), d.getY(), d.getZ(), 2, .2, .4, .2, .01);
            }
            if (tick % 40 == 0)
                for (Mob m : a.level().getEntitiesOfClass(Mob.class, new AABB(stand.blockPosition()).inflate(12), m -> m instanceof Enemy && m.isAlive() && !m.getTags().contains(TAG)))
                    if ((m.getTarget() == null || !m.getTarget().isAlive()) && stand instanceof Mob as && m.getRandom().nextInt(3) == 0) m.setTarget(as);
        }
    }

    /** A leftover floating blade from before a restart (its owner isn't in the blade now): gone. */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        Entity e = event.getEntity();
        if (event.getLevel().isClientSide || !e.getTags().contains(TAG)) return;
        if (ALLOWED.remove(e.getUUID())) return;                                // (just placed)
        UUID owner = e.getPersistentData().hasUUID(OWNER) ? e.getPersistentData().getUUID(OWNER) : null;
        Anchor a = owner == null ? null : LIVE.get(owner);
        if (a == null || !(e.getUUID().equals(a.stand()) || e.getUUID().equals(a.display()))) event.setCanceled(true);
    }
}

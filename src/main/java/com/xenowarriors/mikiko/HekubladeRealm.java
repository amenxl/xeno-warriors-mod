package com.xenowarriors.mikiko;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.character.ModCapabilities;
import com.xenowarriors.character.PlayerCharacter;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.Optional;
import java.util.UUID;

/**
 * The world inside the Hekublade. Everything Mikiko defeats is sealed in here. He can step inside (crouch and
 * right-click with the Hekublade, or /xeno blade realm) to walk among the souls he's sealed, frozen in place
 * on a crystal platform - and right-click one to free it, sending it back out into the world where he came from.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class HekubladeRealm {
    public static final ResourceKey<Level> KEY = ResourceKey.create(Registries.DIMENSION, XenoWarriors.id("hekublade"));
    public static final int MAX_SEALED = 120, MAX_SHOWN = 60, Y = 2, RADIUS = 40;
    /** Every 5 minutes, each sealed soul has a 1 in 10,000 chance of breaking out on its own - it almost never happens. */
    public static final float BREAKOUT_CHANCE = 0.0001F;
    public static final int BREAKOUT_INTERVAL = 5 * 60 * 20;
    private static final java.util.Map<UUID, Long> LAST_PLEA = new java.util.HashMap<>();
    private static final String SEALED_TAG = "xw_sealed", SEALED_ID = "xw_sealed_id";

    private HekubladeRealm() {}

    private static Optional<PlayerCharacter> cap(ServerPlayer p) {
        return p.getCapability(ModCapabilities.PLAYER_CHARACTER).resolve();
    }

    public static boolean inRealm(Entity e) {
        return e.level().dimension() == KEY;
    }

    /** Each Mikiko gets his own platform, far from everyone else's. */
    public static BlockPos center(UUID id) {
        long h = id.getMostSignificantBits() ^ id.getLeastSignificantBits();
        int x = (int) ((h & 0x1FFF) - 0x1000) * 1024;
        int z = (int) (((h >> 16) & 0x1FFF) - 0x1000) * 1024;
        return new BlockPos(x, Y, z);
    }

    // ---------------------------------------------------------------- sealing

    public static void seal(ServerPlayer p, LivingEntity victim) {
        if (victim instanceof EnderDragon || inRealm(victim) || victim.getPersistentData().contains(SEALED_TAG)) return;
        cap(p).ifPresent(c -> {
            CompoundTag data = victim.saveWithoutId(new CompoundTag());
            for (String k : new String[]{"UUID", "Passengers", "ActiveEffects", "Pos", "Motion", "Fire", "DeathTime", "HurtTime",
                    "ForgeData", "Leash", "Brain"}) data.remove(k);
            CompoundTag rec = new CompoundTag();
            rec.putInt("id", c.nextSealId++);
            rec.putString("type", EntityType.getKey(victim.getType()).toString());
            rec.putString("name", victim.getName().getString());
            rec.put("data", data);
            rec.putLong("time", p.level().getGameTime());
            c.sealed.add(rec);
            while (c.sealed.size() > MAX_SEALED) c.sealed.remove(0);
        });
    }

    // ---------------------------------------------------------------- going in and out

    public static void toggle(ServerPlayer p) {
        if (ModCapabilities.getType(p) != CharacterType.MIKIKO) return;
        if (isPrisoner(p)) {
            p.displayClientMessage(Component.literal("You're sealed in someone else's blade - ask them to free you."), true);
            return;
        }
        if (inRealm(p)) leave(p);
        else enter(p);
    }

    public static void enter(ServerPlayer p) {
        ServerLevel realm = p.server.getLevel(KEY);
        if (realm == null) return;
        PlayerCharacter c = cap(p).orElse(null);
        if (c == null) return;
        long now = p.server.overworld().getGameTime();
        if (now < c.realmCooldownUntil) {
            p.displayClientMessage(Component.literal("The Hekublade won't take you back yet (" + BladeLevel.fmt((int) (c.realmCooldownUntil - now))
                    + ")").withStyle(ChatFormatting.RED), true);
            return;
        }
        int max = BladeLevel.maxTicks(c.bladeLevel);
        if (c.realmTicks < 0 || c.realmTicks > max) c.realmTicks = max;
        if (c.realmTicks < 200) {
            p.displayClientMessage(Component.literal("You need to rest outside the blade a little longer.").withStyle(ChatFormatting.RED), true);
            return;
        }
        CompoundTag ret = new CompoundTag();
        ret.putString("dim", p.level().dimension().location().toString());
        ret.putDouble("x", p.getX());
        ret.putDouble("y", p.getY());
        ret.putDouble("z", p.getZ());
        ret.putFloat("yaw", p.getYRot());
        c.bladeReturn = ret;

        BlockPos center = center(p.getUUID());
        if (!realm.getBlockState(center.below()).is(Blocks.SEA_LANTERN)) buildPlatform(realm, center);
        BlockPos house = center.offset(0, 0, -24);
        if (!realm.getBlockState(house).is(Blocks.DARK_OAK_PLANKS)) buildHouse(realm, house);
        burst(p.serverLevel(), p.getX(), p.getY() + 1, p.getZ());
        p.teleportTo(realm, center.getX() + 0.5, center.getY() + 1, center.getZ() + 3.5, 180F, 0F);
        p.fallDistance = 0;
        showSealed(p, realm, center, c);
        p.addEffect(new net.minecraft.world.effect.MobEffectInstance(com.xenowarriors.ModEffects.BLADE_BOUND.get(), c.realmTicks, 0, false, false, true));
        realm.playSound(null, center, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.5F, 0.6F);
        realm.playSound(null, center, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1F, 0.7F);
        int n = c.sealed.size();
        p.displayClientMessage(Component.literal(n == 0 ? "The Hekublade is empty... for now."
                : "Inside the Hekublade: " + n + " sealed. Right-click one to free it.").withStyle(ChatFormatting.GOLD), true);
    }

    public static void leave(ServerPlayer p) {
        p.removeEffect(com.xenowarriors.ModEffects.BLADE_BOUND.get());
        ServerLevel realm = p.server.getLevel(KEY);
        if (realm != null) clearSealed(realm, p.getUUID());
        CompoundTag ret = cap(p).map(c -> c.bladeReturn).orElse(new CompoundTag());
        ServerLevel dest = returnLevel(p, ret);
        double x, y, z;
        float yaw = p.getYRot();
        if (ret.contains("x") && dest.dimension().location().toString().equals(ret.getString("dim"))) {
            x = ret.getDouble("x");
            y = ret.getDouble("y");
            z = ret.getDouble("z");
            yaw = ret.getFloat("yaw");
        } else {
            BlockPos spawn = dest.getSharedSpawnPos();
            x = spawn.getX() + 0.5;
            y = spawn.getY();
            z = spawn.getZ() + 0.5;
        }
        p.teleportTo(dest, x, y, z, yaw, p.getXRot());
        p.fallDistance = 0;
        burst(dest, x, y + 1, z);
        dest.playSound(null, x, y, z, SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 1F, 0.8F);
    }

    private static ServerLevel returnLevel(ServerPlayer p, CompoundTag ret) {
        if (ret.contains("dim")) {
            ResourceLocation rl = ResourceLocation.tryParse(ret.getString("dim"));
            if (rl != null) {
                ServerLevel l = p.server.getLevel(ResourceKey.create(Registries.DIMENSION, rl));
                if (l != null && l.dimension() != KEY) return l;
            }
        }
        return p.server.overworld();
    }

    // ---------------------------------------------------------------- the sealed souls on display

    private static void showSealed(ServerPlayer p, ServerLevel realm, BlockPos center, PlayerCharacter c) {
        clearSealed(realm, p.getUUID());
        ListTag list = c.sealed;
        int shown = Math.min(list.size(), MAX_SHOWN);
        for (int i = 0; i < shown; i++) {
            CompoundTag rec = list.getCompound(list.size() - 1 - i);     // newest closest to the middle
            Mob mob = create(realm, rec);
            if (mob == null) continue;
            int ring = i < 10 ? 0 : i < 26 ? 1 : 2;
            int perRing = ring == 0 ? 10 : ring == 1 ? 16 : 34;
            int idx = ring == 0 ? i : ring == 1 ? i - 10 : i - 26;
            double radius = 6 + ring * 5;
            double a = (idx + (ring % 2) * 0.5) / perRing * Math.PI * 2;
            double x = center.getX() + 0.5 + Math.cos(a) * radius, z = center.getZ() + 0.5 + Math.sin(a) * radius;
            float yaw = (float) (Mth.atan2(center.getZ() + 0.5 - z, center.getX() + 0.5 - x) * Mth.RAD_TO_DEG) - 90F;
            mob.moveTo(x, center.getY() + 1, z, yaw, 0);
            mob.setYHeadRot(yaw);
            mob.setYBodyRot(yaw);
            mob.setNoAi(true);
            mob.setInvulnerable(true);
            mob.setSilent(true);
            mob.setPersistenceRequired();
            mob.setCustomName(Component.literal(rec.getString("name")).withStyle(ChatFormatting.GOLD));
            mob.setCustomNameVisible(true);
            mob.getPersistentData().putUUID(SEALED_TAG, p.getUUID());
            mob.getPersistentData().putInt(SEALED_ID, rec.getInt("id"));
            realm.addFreshEntity(mob);
        }
    }

    private static Mob create(ServerLevel level, CompoundTag rec) {
        Optional<EntityType<?>> type = EntityType.byString(rec.getString("type"));
        if (type.isEmpty()) return null;
        Entity e = type.get().create(level);
        if (!(e instanceof Mob mob)) {
            if (e != null) e.discard();
            return null;
        }
        CompoundTag data = rec.getCompound("data").copy();
        if (!data.isEmpty()) mob.load(data);
        mob.deathTime = 0;
        mob.hurtTime = 0;
        mob.clearFire();
        mob.removeAllEffects();
        mob.setHealth(mob.getMaxHealth());
        return mob;
    }

    private static void clearSealed(ServerLevel realm, UUID owner) {
        BlockPos c = center(owner);
        AABB box = new AABB(c).inflate(RADIUS + 16, 40, RADIUS + 16);
        for (Entity e : realm.getEntitiesOfClass(Entity.class, box, e -> e.getPersistentData().hasUUID(SEALED_TAG)
                && owner.equals(e.getPersistentData().getUUID(SEALED_TAG)))) {
            e.discard();
        }
    }

    // ---------------------------------------------------------------- freeing

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        Entity target = event.getTarget();
        if (!target.getPersistentData().hasUUID(SEALED_TAG)) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (event.getLevel().isClientSide || event.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND
                || !(event.getEntity() instanceof ServerPlayer p)) return;
        if (!p.getUUID().equals(target.getPersistentData().getUUID(SEALED_TAG))) return;
        free(p, target);
    }

    private static void free(ServerPlayer p, Entity display) {
        int id = display.getPersistentData().getInt(SEALED_ID);
        PlayerCharacter c = cap(p).orElse(null);
        if (c == null) return;
        CompoundTag rec = null;
        for (int i = 0; i < c.sealed.size(); i++) {
            if (c.sealed.getCompound(i).getInt("id") == id) {
                rec = c.sealed.getCompound(i);
                c.sealed.remove(i);
                break;
            }
        }
        ServerLevel realm = (ServerLevel) display.level();
        realm.sendParticles(ParticleTypes.END_ROD, display.getX(), display.getY() + display.getBbHeight() * 0.5, display.getZ(),
                20, 0.3, 0.5, 0.3, 0.08);
        realm.sendParticles(new DustParticleOptions(new Vector3f(1F, 0.55F, 0.1F), 1.2F), display.getX(),
                display.getY() + display.getBbHeight() * 0.5, display.getZ(), 20, 0.4, 0.6, 0.4, 0);
        realm.playSound(null, display.blockPosition(), SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 1F, 1.2F);
        display.discard();
        if (rec == null) return;

        release(p, c, rec, true);
    }

    /** Put a sealed creature back into the world: where Mikiko entered the blade, or right beside him. */
    private static void release(ServerPlayer p, PlayerCharacter c, CompoundTag rec, boolean freedByHim) {
        boolean nearHim = !freedByHim || !inRealm(p);
        ServerLevel dest = nearHim ? (inRealm(p) ? returnLevel(p, c.bladeReturn) : p.serverLevel()) : returnLevel(p, c.bladeReturn);
        Mob mob = create(dest, rec);
        if (mob == null) return;
        double x, y, z;
        if (nearHim && !inRealm(p)) {
            double a = p.getRandom().nextDouble() * Math.PI * 2;
            x = p.getX() + Math.cos(a) * 3;
            y = p.getY();
            z = p.getZ() + Math.sin(a) * 3;
        } else if (c.bladeReturn.contains("x") && dest.dimension().location().toString().equals(c.bladeReturn.getString("dim"))) {
            double a = p.getRandom().nextDouble() * Math.PI * 2;
            x = c.bladeReturn.getDouble("x") + Math.cos(a) * 2.5;
            y = c.bladeReturn.getDouble("y");
            z = c.bladeReturn.getDouble("z") + Math.sin(a) * 2.5;
        } else {
            BlockPos s = dest.getSharedSpawnPos();
            x = s.getX() + 0.5;
            y = s.getY();
            z = s.getZ() + 0.5;
        }
        mob.moveTo(x, y, z, p.getRandom().nextFloat() * 360F, 0);
        dest.addFreshEntity(mob);
        burst(dest, x, y + 1, z);
        if (freedByHim) {
            p.displayClientMessage(Component.literal(rec.getString("name") + " has been freed from the Hekublade.")
                    .withStyle(ChatFormatting.GOLD), true);
        } else {
            p.sendSystemMessage(Component.literal(rec.getString("name") + " broke out of the Hekublade!").withStyle(ChatFormatting.RED));
            dest.playSound(null, x, y, z, SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 1F, 0.6F);
        }
    }

    // ---------------------------------------------------------------- safety net, entering with the blade

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p)) return;
        if (p.tickCount % BREAKOUT_INTERVAL == 0 && p.tickCount > 0) breakouts(p);
        if (p.tickCount % 20 == 0) timeLimit(p);
        if (p.tickCount % 5 != 0 || !inRealm(p)) return;
        UUID captor = cap(p).map(d -> d.prisonerOf).orElse(null);
        BlockPos c = center(captor != null ? captor : p.getUUID());
        // the blade is Mikiko's own world: inside it he heals fast (2 hearts a second)
        if (captor == null && ModCapabilities.getType(p) == CharacterType.MIKIKO && p.getHealth() < p.getMaxHealth()) {
            p.heal(1F);
            if (p.tickCount % 20 == 0) p.serverLevel().sendParticles(ParticleTypes.HEART, p.getX(), p.getY() + 2, p.getZ(), 1, 0.3, 0.2, 0.3, 0);
        }
        if (p.getY() < c.getY() - 25) {
            p.teleportTo(c.getX() + 0.5, c.getY() + 1, c.getZ() + 0.5);
            p.fallDistance = 0;
            p.setDeltaMovement(0, 0, 0);
        }
        if (p.tickCount % 20 == 0) {
            p.serverLevel().sendParticles(new DustParticleOptions(new Vector3f(0.45F, 0.9F, 1F), 0.8F), p.getX(), p.getY() + 3, p.getZ(),
                    4, 6, 2, 6, 0);
        }
    }

    // (crouch + right-click is the Season 3 throw now; the dimension has its own key - RealmKeyPacket)
    public static void onRightClick(PlayerInteractEvent.RightClickItem event) {
        if (event.getLevel().isClientSide || !(event.getEntity() instanceof ServerPlayer p) || !p.isShiftKeyDown()) return;
        if (!Hekublade.isBlade(event.getItemStack()) || ModCapabilities.getType(p) != CharacterType.MIKIKO) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        toggle(p);
    }

    /** Every 5 minutes each sealed soul gets a 1 in 10,000 chance to break free on its own. */
    private static void breakouts(ServerPlayer p) {
        PlayerCharacter c = cap(p).orElse(null);
        if (c == null || c.sealed.isEmpty() || ModCapabilities.getType(p) != CharacterType.MIKIKO) return;
        for (int i = c.sealed.size() - 1; i >= 0; i--) {
            if (p.getRandom().nextFloat() >= BREAKOUT_CHANCE) continue;
            CompoundTag rec = c.sealed.getCompound(i);
            c.sealed.remove(i);
            if (inRealm(p)) {
                // the frozen copy on the floor vanishes too
                ServerLevel realm = p.serverLevel();
                int id = rec.getInt("id");
                for (Entity e : realm.getEntitiesOfClass(Entity.class, new AABB(center(p.getUUID())).inflate(RADIUS + 16, 40, RADIUS + 16),
                        e -> e.getPersistentData().hasUUID(SEALED_TAG) && e.getPersistentData().getInt(SEALED_ID) == id
                                && p.getUUID().equals(e.getPersistentData().getUUID(SEALED_TAG)))) {
                    e.discard();
                }
            }
            release(p, c, rec, false);
        }
    }

    // ---------------------------------------------------------------- players sealed in the blade

    public static boolean isPrisoner(ServerPlayer p) {
        return cap(p).map(d -> d.prisonerOf != null).orElse(false);
    }

    /** Mikiko defeats a player: instead of dying, they're sealed inside his Hekublade. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerDeath(net.minecraftforge.event.entity.living.LivingDeathEvent event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer victim)) return;
        PlayerCharacter vc = cap(victim).orElse(null);
        if (vc == null) return;
        if (vc.prisonerOf != null) {
            // dying inside (or being refused) lets them go - they respawn as normal
            vc.prisonerOf = null;
            vc.prisonReturn = new CompoundTag();
            return;
        }
        if (!(event.getSource().getEntity() instanceof ServerPlayer mikiko) || mikiko == victim || victim.isCreative()
                || ModCapabilities.getType(mikiko) != CharacterType.MIKIKO || inRealm(victim)) return;
        if (event.getSource().is(net.minecraft.world.damagesource.DamageTypes.GENERIC_KILL)
                || event.getSource().is(net.minecraft.world.damagesource.DamageTypes.FELL_OUT_OF_WORLD)) return;
        ServerLevel realm = victim.server.getLevel(KEY);
        if (realm == null) return;
        event.setCanceled(true);
        CompoundTag ret = new CompoundTag();
        ret.putString("dim", victim.level().dimension().location().toString());
        ret.putDouble("x", victim.getX());
        ret.putDouble("y", victim.getY());
        ret.putDouble("z", victim.getZ());
        vc.prisonerOf = mikiko.getUUID();
        BladeLevel.addXp(mikiko, 40F);
        vc.prisonReturn = ret;
        victim.setHealth(victim.getMaxHealth());
        victim.clearFire();
        victim.removeAllEffects();
        burst(victim.serverLevel(), victim.getX(), victim.getY() + 1, victim.getZ());
        BlockPos center = center(mikiko.getUUID());
        if (!realm.getBlockState(center.below()).is(Blocks.SEA_LANTERN)) buildPlatform(realm, center);
        double a = victim.getRandom().nextDouble() * Math.PI * 2;
        victim.teleportTo(realm, center.getX() + 0.5 + Math.cos(a) * 8, center.getY() + 1, center.getZ() + 0.5 + Math.sin(a) * 8,
                victim.getYRot(), 0F);
        victim.fallDistance = 0;
        mikiko.sendSystemMessage(Component.literal("You sealed " + victim.getGameProfile().getName() + " inside the Hekublade.")
                .withStyle(ChatFormatting.GOLD));
        victim.sendSystemMessage(Component.literal("You've been sealed inside " + mikiko.getGameProfile().getName() + "'s Hekublade. ")
                .withStyle(ChatFormatting.GOLD)
                .append(Component.literal("[Ask to be freed]").withStyle(s -> s.withColor(ChatFormatting.GREEN).withUnderlined(true)
                        .withClickEvent(new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND,
                                "/xeno blade plea")))));
    }

    /** A sealed player begs their Mikiko to let them out. */
    public static void plea(ServerPlayer prisoner) {
        UUID captorId = cap(prisoner).map(d -> d.prisonerOf).orElse(null);
        if (captorId == null) {
            prisoner.displayClientMessage(Component.literal("You're not sealed in a Hekublade."), true);
            return;
        }
        long now = prisoner.level().getGameTime();
        Long last = LAST_PLEA.get(prisoner.getUUID());
        if (last != null && now - last < 600) {
            prisoner.displayClientMessage(Component.literal("Wait a little before asking again (" + (600 - (now - last) + 19) / 20 + "s)."), true);
            return;
        }
        ServerPlayer mikiko = prisoner.server.getPlayerList().getPlayer(captorId);
        if (mikiko == null) {
            prisoner.displayClientMessage(Component.literal("Your captor isn't here to hear you...").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        LAST_PLEA.put(prisoner.getUUID(), now);
        String name = prisoner.getGameProfile().getName();
        mikiko.sendSystemMessage(Component.literal(name + " is begging to be freed from your Hekublade. ").withStyle(ChatFormatting.GOLD)
                .append(button("[Yes]", ChatFormatting.GREEN, "/xeno blade answer " + name + " yes"))
                .append(Component.literal(" "))
                .append(button("[No]", ChatFormatting.RED, "/xeno blade answer " + name + " no")));
        mikiko.playNotifySound(SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1F, 1.2F);
        prisoner.displayClientMessage(Component.literal("You call out to " + mikiko.getGameProfile().getName() + "...")
                .withStyle(ChatFormatting.GOLD), true);
    }

    private static Component button(String text, ChatFormatting color, String command) {
        return Component.literal(text).withStyle(s -> s.withColor(color).withBold(true)
                .withClickEvent(new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND, command)));
    }

    /** Mikiko's answer: yes frees them, no and they die (and respawn as normal). */
    public static void answer(ServerPlayer mikiko, ServerPlayer prisoner, boolean yes) {
        if (!mikiko.getUUID().equals(cap(prisoner).map(d -> d.prisonerOf).orElse(null))) {
            mikiko.displayClientMessage(Component.literal(prisoner.getGameProfile().getName() + " isn't sealed in your blade."), true);
            return;
        }
        if (yes) {
            freePlayer(prisoner);
            mikiko.displayClientMessage(Component.literal("You freed " + prisoner.getGameProfile().getName() + ".").withStyle(ChatFormatting.GOLD), true);
        } else {
            prisoner.sendSystemMessage(Component.literal(mikiko.getGameProfile().getName() + " refused. The blade consumes you...")
                    .withStyle(ChatFormatting.DARK_RED));
            mikiko.displayClientMessage(Component.literal("You refused " + prisoner.getGameProfile().getName() + ".").withStyle(ChatFormatting.RED), true);
            prisoner.kill();
        }
    }

    private static void freePlayer(ServerPlayer prisoner) {
        PlayerCharacter c = cap(prisoner).orElse(null);
        if (c == null) return;
        CompoundTag ret = c.prisonReturn;
        c.prisonerOf = null;
        c.prisonReturn = new CompoundTag();
        burst(prisoner.serverLevel(), prisoner.getX(), prisoner.getY() + 1, prisoner.getZ());
        ServerLevel dest = returnLevel(prisoner, ret);
        double x, y, z;
        if (ret.contains("x") && dest.dimension().location().toString().equals(ret.getString("dim"))) {
            x = ret.getDouble("x");
            y = ret.getDouble("y");
            z = ret.getDouble("z");
        } else {
            BlockPos s = dest.getSharedSpawnPos();
            x = s.getX() + 0.5;
            y = s.getY();
            z = s.getZ() + 0.5;
        }
        prisoner.teleportTo(dest, x, y, z, prisoner.getYRot(), 0F);
        prisoner.fallDistance = 0;
        burst(dest, x, y + 1, z);
        prisoner.sendSystemMessage(Component.literal("You've been freed from the Hekublade.").withStyle(ChatFormatting.GOLD));
    }

    /** Mikiko can also just right-click a sealed player to let them go. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onInteractPlayer(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide || event.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND
                || !(event.getEntity() instanceof ServerPlayer mikiko) || !(event.getTarget() instanceof ServerPlayer prisoner)) return;
        if (!inRealm(mikiko) || !mikiko.getUUID().equals(cap(prisoner).map(d -> d.prisonerOf).orElse(null))) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        freePlayer(prisoner);
    }

    /** Mikiko's time inside the blade runs down while he's in, and refills while he's out. */
    private static void timeLimit(ServerPlayer p) {
        if (ModCapabilities.getType(p) != CharacterType.MIKIKO || isPrisoner(p)) return;
        PlayerCharacter c = cap(p).orElse(null);
        if (c == null) return;
        int max = BladeLevel.maxTicks(c.bladeLevel);
        if (c.realmTicks < 0) c.realmTicks = max;
        long now = p.server.overworld().getGameTime();
        var bound = com.xenowarriors.ModEffects.BLADE_BOUND.get();
        var tired = com.xenowarriors.ModEffects.BLADE_EXHAUSTED.get();
        if (inRealm(p)) {
            c.realmTicks -= 20;
            var fx = p.getEffect(bound);
            if (fx == null || Math.abs(fx.getDuration() - c.realmTicks) > 40) {
                p.removeEffect(bound);
                p.addEffect(new net.minecraft.world.effect.MobEffectInstance(bound, Math.max(1, c.realmTicks), 0, false, false, true));
            }
            if (c.realmTicks == 600 || c.realmTicks == 200) {
                p.displayClientMessage(Component.literal("The Hekublade is pushing you out... " + c.realmTicks / 20 + "s left")
                        .withStyle(ChatFormatting.RED), true);
                p.playNotifySound(SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1F, 0.5F);
            }
            if (c.realmTicks <= 0) {
                c.realmTicks = 0;
                int cd = BladeLevel.cooldownTicks(c.bladeLevel);
                c.realmCooldownUntil = now + cd;
                leave(p);
                p.addEffect(new net.minecraft.world.effect.MobEffectInstance(tired, cd, 0, false, false, true));
                p.sendSystemMessage(Component.literal("You stayed too long - the Hekublade threw you out. You can go back in "
                        + BladeLevel.fmt(cd) + " from now.").withStyle(ChatFormatting.RED));
            }
        } else {
            if (c.realmTicks < max) c.realmTicks = Math.min(max, c.realmTicks + 20);
            if (now < c.realmCooldownUntil && !p.hasEffect(tired)) {
                p.addEffect(new net.minecraft.world.effect.MobEffectInstance(tired, (int) (c.realmCooldownUntil - now), 0, false, false, true));
            }
            if (p.hasEffect(bound)) p.removeEffect(bound);
        }
    }

    /** A little dark-oak house on Mikiko's floor with empty chests for him to keep things in. */
    private static void buildHouse(ServerLevel l, BlockPos h) {
        BlockState planks = Blocks.DARK_OAK_PLANKS.defaultBlockState(),
                log = Blocks.DARK_OAK_LOG.defaultBlockState(),
                pane = Blocks.LIGHT_BLUE_STAINED_GLASS_PANE.defaultBlockState(),
                slab = Blocks.DARK_OAK_SLAB.defaultBlockState(),
                air = Blocks.AIR.defaultBlockState();
        int x0 = h.getX(), y0 = h.getY(), z0 = h.getZ();
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                set(l, x0 + dx, y0, z0 + dz, planks);                                 // floor
                boolean edge = Math.abs(dx) == 3 || Math.abs(dz) == 3;
                boolean corner = Math.abs(dx) == 3 && Math.abs(dz) == 3;
                for (int y = 1; y <= 3; y++) {
                    BlockState st = !edge ? air : corner ? log : planks;
                    if (edge && !corner && y == 2 && (dx == 0 || dz == 0 || Math.abs(dx) == 1 && dz == -3)) st = pane;
                    set(l, x0 + dx, y0 + y, z0 + dz, st);
                }
                set(l, x0 + dx, y0 + 4, z0 + dz, planks);                             // ceiling
            }
        }
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                if (Math.abs(dx) == 4 || Math.abs(dz) == 4) set(l, x0 + dx, y0 + 4, z0 + dz, slab);   // eaves
                if (Math.abs(dx) <= 2 && Math.abs(dz) <= 2) set(l, x0 + dx, y0 + 5, z0 + dz, slab);   // roof cap
            }
        }
        set(l, x0, y0 + 4, z0, Blocks.SHROOMLIGHT.defaultBlockState());                 // a warm light inside
        // the door faces the middle of the floor (south)
        var door = Blocks.DARK_OAK_DOOR.defaultBlockState().setValue(net.minecraft.world.level.block.DoorBlock.FACING, net.minecraft.core.Direction.SOUTH);
        set(l, x0, y0 + 1, z0 + 3, door.setValue(net.minecraft.world.level.block.DoorBlock.HALF,
                net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER));
        set(l, x0, y0 + 2, z0 + 3, door.setValue(net.minecraft.world.level.block.DoorBlock.HALF,
                net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));
        // empty chests along the back wall and one side
        var chest = Blocks.CHEST.defaultBlockState();
        var south = net.minecraft.core.Direction.SOUTH;
        var east = net.minecraft.core.Direction.EAST;
        set(l, x0 - 2, y0 + 1, z0 - 2, chest.setValue(net.minecraft.world.level.block.ChestBlock.FACING, south));
        set(l, x0, y0 + 1, z0 - 2, chest.setValue(net.minecraft.world.level.block.ChestBlock.FACING, south));
        set(l, x0 + 2, y0 + 1, z0 - 2, chest.setValue(net.minecraft.world.level.block.ChestBlock.FACING, south));
        set(l, x0 - 2, y0 + 1, z0, chest.setValue(net.minecraft.world.level.block.ChestBlock.FACING, east));
        set(l, x0 - 2, y0 + 1, z0 + 1, Blocks.BARREL.defaultBlockState());
        set(l, x0 + 2, y0 + 1, z0, Blocks.CRAFTING_TABLE.defaultBlockState());
        set(l, x0 + 2, y0 + 1, z0 + 1, Blocks.LANTERN.defaultBlockState());
        set(l, x0, y0, z0, planks);   // marks the house as built
    }

    /** The floor of the blade can't be broken - not by hand, tools or explosions. */
    @SubscribeEvent
    public static void onBreakFloor(net.minecraftforge.event.level.BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof Level l && l.dimension() == KEY && event.getPos().getY() <= Y) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onExplode(net.minecraftforge.event.level.ExplosionEvent.Detonate event) {
        if (event.getLevel().dimension() == KEY) event.getAffectedBlocks().removeIf(pos -> pos.getY() <= Y);
    }

    @SubscribeEvent
    public static void onBreakSpeed(net.minecraftforge.event.entity.player.PlayerEvent.BreakSpeed event) {
        if (event.getEntity().level().dimension() == KEY && event.getPosition().map(pos -> pos.getY() <= Y).orElse(false)) {
            event.setCanceled(true);   // no crack animation on the floor either
        }
    }

    private static void burst(ServerLevel level, double x, double y, double z) {
        level.sendParticles(new DustParticleOptions(new Vector3f(0.95F, 0.12F, 0.1F), 1.4F), x, y, z, 25, 0.5, 0.8, 0.5, 0);
        level.sendParticles(new DustParticleOptions(new Vector3f(1F, 0.55F, 0.08F), 1.4F), x, y, z, 25, 0.5, 0.8, 0.5, 0);
        level.sendParticles(ParticleTypes.FLASH, x, y, z, 1, 0, 0, 0, 0);
    }

    // ---------------------------------------------------------------- the crystal platform

    private static void set(ServerLevel l, int x, int y, int z, BlockState s) {
        l.setBlock(new BlockPos(x, y, z), s, Block.UPDATE_CLIENTS);
    }

    private static void buildPlatform(ServerLevel l, BlockPos c) {
        // the floor itself is the endless dark glass the world generates; scatter faint glowing points beneath it
        // like the stars reflected in the picture, and mark this spot as built
        BlockState star = Blocks.SEA_LANTERN.defaultBlockState();
        java.util.Random r = new java.util.Random(c.asLong());
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                if (r.nextInt(70) == 0) set(l, c.getX() + dx, c.getY() - 1, c.getZ() + dz, star);
            }
        }
        set(l, c.getX(), c.getY() - 1, c.getZ(), star);
    }
}

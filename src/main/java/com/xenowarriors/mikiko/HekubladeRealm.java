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
    /** Each try at freeing a sealed soul only works 1 time in 100. */
    public static final float FREE_CHANCE = 0.01F;
    private static final java.util.Map<UUID, Long> LAST_TRY = new java.util.HashMap<>();
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
        if (inRealm(p)) leave(p);
        else enter(p);
    }

    public static void enter(ServerPlayer p) {
        ServerLevel realm = p.server.getLevel(KEY);
        if (realm == null) return;
        PlayerCharacter c = cap(p).orElse(null);
        if (c == null) return;
        CompoundTag ret = new CompoundTag();
        ret.putString("dim", p.level().dimension().location().toString());
        ret.putDouble("x", p.getX());
        ret.putDouble("y", p.getY());
        ret.putDouble("z", p.getZ());
        ret.putFloat("yaw", p.getYRot());
        c.bladeReturn = ret;

        BlockPos center = center(p.getUUID());
        if (!realm.getBlockState(center.below()).is(Blocks.SEA_LANTERN)) buildPlatform(realm, center);
        burst(p.serverLevel(), p.getX(), p.getY() + 1, p.getZ());
        p.teleportTo(realm, center.getX() + 0.5, center.getY() + 1, center.getZ() + 3.5, 180F, 0F);
        p.fallDistance = 0;
        showSealed(p, realm, center, c);
        realm.playSound(null, center, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.5F, 0.6F);
        realm.playSound(null, center, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1F, 0.7F);
        int n = c.sealed.size();
        p.displayClientMessage(Component.literal(n == 0 ? "The Hekublade is empty... for now."
                : "Inside the Hekublade: " + n + " sealed. Right-click one to free it.").withStyle(ChatFormatting.GOLD), true);
    }

    public static void leave(ServerPlayer p) {
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
        long now = p.level().getGameTime();
        Long last = LAST_TRY.get(p.getUUID());
        if (last != null && now - last < 20) return;
        LAST_TRY.put(p.getUUID(), now);
        if (p.getRandom().nextFloat() < FREE_CHANCE) {
            free(p, target);
        } else {
            ServerLevel realm = (ServerLevel) target.level();
            realm.sendParticles(new DustParticleOptions(new Vector3f(0.55F, 0.4F, 1F), 1F), target.getX(),
                    target.getY() + target.getBbHeight() * 0.5, target.getZ(), 10, 0.3, 0.4, 0.3, 0);
            realm.playSound(null, target.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1F, 0.6F);
            p.displayClientMessage(Component.literal("The blade's hold on " + target.getCustomName().getString()
                    + " won't break... (1% chance each try)").withStyle(ChatFormatting.GRAY), true);
        }
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

        // it comes back out where Mikiko entered the blade
        ServerLevel dest = returnLevel(p, c.bladeReturn);
        Mob mob = create(dest, rec);
        if (mob == null) return;
        double x, y, z;
        if (c.bladeReturn.contains("x") && dest.dimension().location().toString().equals(c.bladeReturn.getString("dim"))) {
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
        p.displayClientMessage(Component.literal(rec.getString("name") + " has been freed from the Hekublade.")
                .withStyle(ChatFormatting.GOLD), true);
    }

    // ---------------------------------------------------------------- safety net, entering with the blade

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p) || p.tickCount % 5 != 0 || !inRealm(p)) return;
        BlockPos c = center(p.getUUID());
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

    @SubscribeEvent
    public static void onRightClick(PlayerInteractEvent.RightClickItem event) {
        if (event.getLevel().isClientSide || !(event.getEntity() instanceof ServerPlayer p) || !p.isShiftKeyDown()) return;
        if (!Hekublade.isBlade(event.getItemStack()) || ModCapabilities.getType(p) != CharacterType.MIKIKO) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        toggle(p);
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

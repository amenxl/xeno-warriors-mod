package com.xenowarriors.wing;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.character.ModCapabilities;
import com.xenowarriors.character.PlayerCharacter;
import com.xenowarriors.mixin.BatAccessor;
import com.xenowarriors.network.ModNetwork;
import com.xenowarriors.network.OpenNamePacket;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Parrot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Optional;
import java.util.UUID;

/**
 * Rhalix's winged companions: he can tame bats (berries, melon, apples), parrots and chickens (seeds) and
 * bees (flowers) - two at most. They follow him (bats circle around his head), can be named by right-clicking,
 * can't be hurt by him, and if one dies the Fox Whistle calls it back.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WingPets {
    public static final int MAX = 2;
    private static final String OWNER = "xw_wing_owner", GEN = "xw_wing_gen", SKIN = "xw_wing_skin";
    /** Looks a companion can wear (colors live in the client's WingSkinLayer). */
    public static final String[] SKINS = {"Classic", "Shadow", "Rose", "Frost", "Ember", "Golden", "Void", "Namekian", "Sacred Kai"};
    public static final String SKIN_KEY = "xw_wing_skin";
    public static final int BAG_SLOTS = 18;
    private static final int EAT_COOLDOWN = 60;

    private WingPets() {}

    // ---------------------------------------------------------------- lookups

    public static boolean winged(Entity e) {
        return e instanceof Bat || e instanceof Parrot || e instanceof Chicken || e instanceof Bee;
    }

    private static boolean food(Entity e, ItemStack s) {
        if (e instanceof Bat) return s.is(Items.GLOW_BERRIES) || s.is(Items.SWEET_BERRIES) || s.is(Items.MELON_SLICE) || s.is(Items.APPLE);
        if (e instanceof Parrot || e instanceof Chicken) return s.is(Tags.Items.SEEDS);
        if (e instanceof Bee) return s.is(ItemTags.FLOWERS);
        return false;
    }

    public static UUID ownerOf(Entity e) {
        CompoundTag d = e.getPersistentData();
        return d.hasUUID(OWNER) ? d.getUUID(OWNER) : null;
    }

    public static boolean isOwnedBy(Entity e, Player p) {
        return p.getUUID().equals(ownerOf(e));
    }

    public static Player owner(Entity e) {
        UUID id = ownerOf(e);
        return id == null ? null : e.level().getPlayerByUUID(id);
    }

    private static Optional<PlayerCharacter> cap(Player p) {
        return p.getCapability(ModCapabilities.PLAYER_CHARACTER).resolve();
    }

    private static ListTag pets(Player p) {
        return cap(p).map(c -> c.wingPets).orElseGet(ListTag::new);
    }

    private static CompoundTag record(Player p, UUID id) {
        for (Tag t : pets(p)) {
            CompoundTag rec = (CompoundTag) t;
            if (rec.hasUUID("uuid") && rec.getUUID("uuid").equals(id)) return rec;
        }
        return null;
    }

    private static Mob findLoaded(ServerPlayer p, UUID id) {
        return p.serverLevel().getEntity(id) instanceof Mob m && m.isAlive() ? m : null;
    }

    private static void say(ServerPlayer p, String text) {
        p.displayClientMessage(Component.literal(text).withStyle(ChatFormatting.LIGHT_PURPLE), true);
    }

    private static String aName(Entity e) {
        return e instanceof Bat ? "a bat" : e instanceof Parrot ? "a parrot" : e instanceof Chicken ? "a chicken" : "a bee";
    }

    /** What each winged animal can be tamed with (matches food()). */
    private static String foodName(Entity e) {
        if (e instanceof Bat) return "sweet berries, glow berries, a melon slice or an apple";
        if (e instanceof Parrot || e instanceof Chicken) return "seeds";
        return "a flower";
    }

    private static String defaultName(Entity e) {
        return e instanceof Bat ? "Bat" : e instanceof Parrot ? "Parrot" : e instanceof Chicken ? "Chicken" : "Bee";
    }

    // ---------------------------------------------------------------- taming & right-clicking

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide || !winged(event.getTarget()) || !(event.getTarget() instanceof Mob mob)
                || !(event.getEntity() instanceof ServerPlayer p)) return;
        ItemStack stack = p.getItemInHand(event.getHand());
        UUID owner = ownerOf(mob);
        boolean rhalix = ModCapabilities.getType(p) == CharacterType.RHALIX;
        if (owner == null) {
            if (event.getHand() == InteractionHand.MAIN_HAND && !food(mob, stack) && !(mob instanceof Parrot pt && pt.isTame())
                    && ModCapabilities.getType(p) != CharacterType.NONE) {
                say(p, (rhalix ? "To tame " : "Only Rhalix can tame ") + aName(mob) + ", feed it " + foodName(mob) + ".");
            }
            if (!rhalix || !food(mob, stack) || mob instanceof Parrot parrot && parrot.isTame()) return;
            consume(event);
            tryTame(p, mob, stack);
        } else if (owner.equals(p.getUUID())) {
            consume(event);
            if (event.getHand() != InteractionHand.MAIN_HAND) return;
            if (food(mob, stack) && mob.getHealth() < mob.getMaxHealth()) {
                mob.heal(4F);
                if (!p.getAbilities().instabuild) stack.shrink(1);
                p.serverLevel().sendParticles(ParticleTypes.HEART, mob.getX(), mob.getY() + mob.getBbHeight(), mob.getZ(), 2, 0.2, 0.1, 0.2, 0);
            } else {
                CompoundTag rec = record(p, mob.getUUID());
                if (rec != null) openMenu(p, mob, rec);
            }
        }
    }

    private static void consume(PlayerInteractEvent.EntityInteract event) {
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    private static void tryTame(ServerPlayer p, Mob mob, ItemStack stack) {
        if (!com.xenowarriors.party.Parties.petRoom(p)) return;
        if (pets(p).size() >= MAX) {
            say(p, "You can only keep " + MAX + " winged companions.");
            return;
        }
        if (!p.getAbilities().instabuild) stack.shrink(1);
        ServerLevel level = p.serverLevel();
        if (p.getRandom().nextInt(3) != 0) {
            level.sendParticles(ParticleTypes.SMOKE, mob.getX(), mob.getY() + mob.getBbHeight() * 0.5, mob.getZ(), 6, 0.2, 0.2, 0.2, 0.01);
            return;
        }
        CompoundTag rec = new CompoundTag();
        rec.putUUID("uuid", mob.getUUID());
        rec.putString("name", defaultName(mob));
        rec.putString("type", EntityType.getKey(mob.getType()).toString());
        rec.putInt("gen", 0);
        rec.putInt("skin", mob.getPersistentData().getInt(SKIN));      // keeps the look it was born with
        cap(p).ifPresent(c -> c.wingPets.add(rec));
        prepare(p, mob, rec);
        snapshot(mob, rec);
        level.sendParticles(ParticleTypes.HEART, mob.getX(), mob.getY() + mob.getBbHeight(), mob.getZ(), 7, 0.3, 0.3, 0.3, 0);
        level.playSound(null, mob.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.NEUTRAL, 0.5F, 1.8F);
        say(p, "The " + defaultName(mob).toLowerCase() + " trusts you now!");
        ModNetwork.sendToPlayer(new OpenNamePacket(mob.getUUID(), rec.getString("name"), true), p);
    }

    private static void prepare(ServerPlayer p, Mob mob, CompoundTag rec) {
        mob.getPersistentData().putUUID(OWNER, p.getUUID());
        mob.getPersistentData().putInt(GEN, rec.getInt("gen"));
        mob.setPersistenceRequired();
        mob.setCustomName(Component.literal(rec.getString("name")).withStyle(ChatFormatting.LIGHT_PURPLE));
        mob.getPersistentData().putInt(SKIN, rec.getInt("skin"));
        if (mob instanceof Parrot parrot && !parrot.isTame()) parrot.tame(p);
        if (mob instanceof Bee bee) {
            bee.stopBeingAngry();
            bee.setTarget(null);
        }
        if (mob instanceof Bat bat) bat.setResting(false);
        addGoals(mob);
    }

    private static void addGoals(Mob mob) {
        if (!(mob instanceof PathfinderMob pm) || mob instanceof Parrot) return;   // parrots follow like vanilla tamed parrots
        if (pm.goalSelector.getAvailableGoals().stream().anyMatch(w -> w.getGoal() instanceof WingFollowGoal)) return;
        pm.goalSelector.addGoal(1, new WingFollowGoal(pm));
    }

    private static void snapshot(Mob mob, CompoundTag rec) {
        rec.put("data", mob.saveWithoutId(new CompoundTag()));
    }

    public static void rename(ServerPlayer p, UUID id, String raw) {
        CompoundTag rec = record(p, id);
        if (rec == null) return;
        String name = ChatFormatting.stripFormatting(raw == null ? "" : raw).trim();
        if (name.length() > 24) name = name.substring(0, 24);
        if (name.isEmpty()) name = "Buddy";
        rec.putString("name", name);
        Mob mob = findLoaded(p, id);
        if (mob != null) mob.setCustomName(Component.literal(name).withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    // ---------------------------------------------------------------- keeping up with him

    public static void teleportNear(Mob mob, Entity owner) {
        Vec3 o = owner.position();
        double a = mob.getRandom().nextDouble() * Math.PI * 2;
        double y = mob instanceof Bat || mob instanceof Bee || mob instanceof Parrot ? o.y + 1.8 : o.y;
        mob.moveTo(o.x + Math.cos(a) * 2, y, o.z + Math.sin(a) * 2, mob.getYRot(), mob.getXRot());
        mob.getNavigation().stop();
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p)) return;
        ListTag pets = pets(p);
        if (pets.isEmpty()) return;
        for (int i = 0; i < pets.size(); i++) {
            CompoundTag rec = pets.getCompound(i);
            if (rec.getBoolean("dead") || !rec.hasUUID("uuid")) continue;
            if (p.tickCount % 2 != 0 && p.tickCount % 20 != 0) continue;
            Mob mob = findLoaded(p, rec.getUUID("uuid"));
            if (mob == null) continue;
            if (mob instanceof Bat bat) {
                // bats circle around his head
                bat.setResting(false);
                double a = (p.tickCount * 0.07) + i * Math.PI;
                BlockPos target = BlockPos.containing(p.getX() + Math.cos(a) * 2.2, p.getY() + 2.4, p.getZ() + Math.sin(a) * 2.2);
                ((BatAccessor) bat).xenowarriors$setTargetPosition(target);
            }
            if (p.tickCount % 20 == 0) {
                eatFromBag(p, mob, rec);
                if (mob.distanceToSqr(p) > 24 * 24 && (p.onGround() || mob instanceof Bat || mob instanceof Bee)) teleportNear(mob, p);
                if (p.tickCount % 100 == 0) snapshot(mob, rec);
            }
        }
    }

    // ---------------------------------------------------------------- joining the world, safety, death

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide || !winged(event.getEntity()) || !(event.getEntity() instanceof Mob mob)) return;
        UUID ownerId = ownerOf(mob);
        if (ownerId == null) return;
        MinecraftServer server = mob.getServer();
        ServerPlayer owner = server == null ? null : server.getPlayerList().getPlayer(ownerId);
        if (owner != null) {
            CompoundTag rec = record(owner, mob.getUUID());
            if (rec == null || rec.getBoolean("dead") || rec.getInt("gen") != mob.getPersistentData().getInt(GEN)) {
                event.setCanceled(true);     // an old copy of one that has since been called back
                return;
            }
        }
        addGoals(mob);
    }

    @SubscribeEvent
    public static void onAttack(LivingAttackEvent event) {
        if (event.getSource().getEntity() instanceof Player p && isOwnedBy(event.getEntity(), p)) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        if (ownerOf(event.getEntity()) != null && winged(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (dead.level().isClientSide || !winged(dead) || !(dead instanceof Mob mob) || !(owner(mob) instanceof ServerPlayer p)) return;
        CompoundTag rec = record(p, mob.getUUID());
        if (rec == null) return;
        snapshot(mob, rec);
        rec.putBoolean("dead", true);
        p.sendSystemMessage(Component.literal(rec.getString("name") + " flew away! Use the Fox Whistle to call them back.")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    // ---------------------------------------------------------------- the whistle (works for Rhalix's companions too)

    public static void whistle(ServerPlayer p) {
        ListTag pets = pets(p);
        if (pets.isEmpty()) {
            say(p, "You don't have any winged companions yet. Tame a bat, parrot, chicken or bee!");
            return;
        }
        for (Tag t : pets) {
            CompoundTag rec = (CompoundTag) t;
            Mob mob = rec.getBoolean("dead") || !rec.hasUUID("uuid") ? null : findLoaded(p, rec.getUUID("uuid"));
            if (mob != null) {
                if (mob.distanceTo(p) > 4) teleportNear(mob, p);
                continue;
            }
            if (!rec.getBoolean("dead") && rec.hasUUID("uuid")) {
                for (ServerLevel level : p.server.getAllLevels()) {
                    if (level.getEntity(rec.getUUID("uuid")) instanceof Mob old) old.discard();
                }
            }
            boolean wasDead = rec.getBoolean("dead");
            if (recreate(p, rec) && wasDead) {
                p.sendSystemMessage(Component.literal(rec.getString("name") + " heard you and came back!").withStyle(ChatFormatting.LIGHT_PURPLE));
            }
        }
    }

    private static boolean recreate(ServerPlayer p, CompoundTag rec) {
        ServerLevel level = p.serverLevel();
        Optional<EntityType<?>> type = EntityType.byString(rec.getString("type"));
        if (type.isEmpty() || !(type.get().create(level) instanceof Mob mob)) return false;
        CompoundTag data = rec.getCompound("data").copy();
        if (!data.isEmpty()) {
            data.remove("UUID");
            data.remove("Passengers");
            data.remove("ActiveEffects");
            mob.load(data);
        }
        int gen = rec.getInt("gen") + 1;
        rec.putInt("gen", gen);
        rec.putUUID("uuid", mob.getUUID());
        rec.putBoolean("dead", false);
        mob.deathTime = 0;
        mob.hurtTime = 0;
        mob.clearFire();
        mob.removeAllEffects();
        mob.setHealth(mob.getMaxHealth());
        teleportNear(mob, p);
        prepare(p, mob, rec);
        level.addFreshEntity(mob);
        level.sendParticles(ParticleTypes.END_ROD, mob.getX(), mob.getY() + 0.5, mob.getZ(), 12, 0.3, 0.3, 0.3, 0.02);
        level.sendParticles(ParticleTypes.HEART, mob.getX(), mob.getY() + 0.8, mob.getZ(), 3, 0.3, 0.2, 0.3, 0);
        snapshot(mob, rec);
        return true;
    }

    // ---------------------------------------------------------------- the companion menu, bag, skins, eating

    private static void openMenu(ServerPlayer p, Mob mob, CompoundTag rec) {
        ModNetwork.sendToPlayer(new com.xenowarriors.network.OpenWingPetPacket(mob.getUUID(), mob.getId(), rec.getString("name"),
                rec.getInt("skin"), mob.getHealth(), mob.getMaxHealth(), defaultName(mob)), p);
    }

    public static void action(ServerPlayer p, UUID id, int action, int arg) {
        CompoundTag rec = record(p, id);
        Mob mob = rec == null ? null : findLoaded(p, id);
        if (mob == null) return;
        if (action == com.xenowarriors.network.WingPetActionPacket.BAG) {
            openBag(p, mob, rec);
        } else if (action == com.xenowarriors.network.WingPetActionPacket.SKIN) {
            int skin = Math.floorMod(arg, SKINS.length);
            rec.putInt("skin", skin);
            mob.getPersistentData().putInt(SKIN, skin);
            syncSkin(mob);
            p.serverLevel().sendParticles(ParticleTypes.END_ROD, mob.getX(), mob.getY() + mob.getBbHeight() * 0.5, mob.getZ(),
                    8, 0.25, 0.25, 0.25, 0.02);
        }
    }

    private static net.minecraft.world.SimpleContainer bag(CompoundTag rec) {
        net.minecraft.world.SimpleContainer c = new net.minecraft.world.SimpleContainer(BAG_SLOTS);
        ListTag list = rec.getList("inv", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            int slot = t.getByte("Slot") & 255;
            if (slot < BAG_SLOTS) c.setItem(slot, ItemStack.of(t));
        }
        return c;
    }

    private static void saveBag(CompoundTag rec, net.minecraft.world.Container c) {
        ListTag list = new ListTag();
        for (int i = 0; i < c.getContainerSize(); i++) {
            ItemStack s = c.getItem(i);
            if (s.isEmpty()) continue;
            CompoundTag t = s.save(new CompoundTag());
            t.putByte("Slot", (byte) i);
            list.add(t);
        }
        rec.put("inv", list);
    }

    private static void openBag(ServerPlayer p, Mob mob, CompoundTag rec) {
        net.minecraft.world.SimpleContainer c = bag(rec);
        c.addListener(changed -> saveBag(rec, changed));
        p.openMenu(new net.minecraft.world.SimpleMenuProvider((id, inv, pl) -> new net.minecraft.world.inventory.ChestMenu(
                net.minecraft.world.inventory.MenuType.GENERIC_9x2, id, inv, c, 2) {
            @Override
            public boolean stillValid(Player player) {
                return mob.isAlive() && player.distanceToSqr(mob) < 64;
            }

            @Override
            public void removed(Player player) {
                super.removed(player);
                saveBag(rec, c);
            }
        }, Component.literal(rec.getString("name") + "'s Bag")));
        p.serverLevel().playSound(null, mob.blockPosition(), SoundEvents.BUNDLE_DROP_CONTENTS, SoundSource.NEUTRAL, 0.6F, 1.3F);
    }

    /** When a companion is hurt it snacks on food from its bag (its favourite food, or anything edible). */
    private static void eatFromBag(ServerPlayer p, Mob mob, CompoundTag rec) {
        if (mob.getHealth() >= mob.getMaxHealth() * 0.6F || !rec.contains("inv")) return;
        long now = p.serverLevel().getGameTime();
        if (now - rec.getLong("ate") < EAT_COOLDOWN) return;
        net.minecraft.world.SimpleContainer c = bag(rec);
        for (int i = 0; i < c.getContainerSize(); i++) {
            ItemStack s = c.getItem(i);
            if (s.isEmpty() || !(food(mob, s) || s.isEdible())) continue;
            float heal = s.isEdible() && s.getFoodProperties(mob) != null ? Math.max(2, s.getFoodProperties(mob).getNutrition()) : 4F;
            s.shrink(1);
            saveBag(rec, c);
            rec.putLong("ate", now);
            mob.heal(heal);
            ServerLevel level = p.serverLevel();
            level.sendParticles(new net.minecraft.core.particles.ItemParticleOption(ParticleTypes.ITEM, s.isEmpty() ? new ItemStack(Items.APPLE) : s),
                    mob.getX(), mob.getY() + mob.getBbHeight() * 0.7, mob.getZ(), 6, 0.15, 0.1, 0.15, 0.05);
            level.sendParticles(ParticleTypes.HEART, mob.getX(), mob.getY() + mob.getBbHeight(), mob.getZ(), 1, 0.2, 0.1, 0.2, 0);
            level.playSound(null, mob.blockPosition(), SoundEvents.GENERIC_EAT, SoundSource.NEUTRAL, 0.6F, 1.4F);
            return;
        }
    }

    private static void syncSkin(Mob mob) {
        ModNetwork.CHANNEL.send(net.minecraftforge.network.PacketDistributor.TRACKING_ENTITY.with(() -> mob),
                new com.xenowarriors.network.WingSkinPacket(mob.getId(), mob.getPersistentData().getInt(SKIN)));
    }

    @SubscribeEvent
    public static void onStartTracking(net.minecraftforge.event.entity.player.PlayerEvent.StartTracking event) {
        Entity e = event.getTarget();
        if (winged(e) && e.getPersistentData().getInt(SKIN) != 0 && event.getEntity() instanceof ServerPlayer sp) {
            ModNetwork.sendToPlayer(new com.xenowarriors.network.WingSkinPacket(e.getId(), e.getPersistentData().getInt(SKIN)), sp);
        }
    }

    public static int count(Player p) {
        return pets(p).size();
    }
}

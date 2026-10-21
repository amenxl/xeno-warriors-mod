package com.xenowarriors.fox;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.character.ModCapabilities;
import com.xenowarriors.character.PlayerCharacter;
import com.xenowarriors.mixin.FoxAccessor;
import com.xenowarriors.network.BackFoxPacket;
import com.xenowarriors.network.FoxLevelPacket;
import com.xenowarriors.network.FoxSyncPacket;
import com.xenowarriors.network.FoxStatsPacket;
import com.xenowarriors.network.FoxSkinPacket;
import com.xenowarriors.network.FoxFxPacket;
import com.xenowarriors.network.ModNetwork;
import net.minecraft.ChatFormatting;
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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Fox;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Mikiko, the Fox King, and his foxes.
 * <ul>
 *   <li>Feed a wild fox raw or cooked rabbit to tame it (a 1 in 3 chance per piece). He can keep 2.</li>
 *   <li>Right-click your fox to name it. Sneak + right-click to carry it on your back.</li>
 *   <li>Foxes follow him, help him fight and level up from fighting, getting hurt and running with him.</li>
 *   <li>If a fox dies (or gets lost), the Fox Whistle calls it back.</li>
 *   <li>Stand still for a while (40-350 s) and they'll run happy laps around you.</li>
 * </ul>
 * Each fox is a record in the player's data {uuid, name, level, xp, gen, dead, back, data} and the fox
 * entity carries its owner and "gen" so stale copies (left in unloaded chunks) vanish once replaced.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FoxPets {
    public static final int MAX_FOXES = 2;
    private static final String OWNER = "xw_owner", GEN = "xw_gen";
    private static final UUID HEALTH_ID = UUID.fromString("6c0e7d5a-3c64-4d8a-9a6e-1f0b3c6f7a01");
    private static final UUID ATTACK_ID = UUID.fromString("6c0e7d5a-3c64-4d8a-9a6e-1f0b3c6f7a02");
    private static final UUID SPEED_ID = UUID.fromString("6c0e7d5a-3c64-4d8a-9a6e-1f0b3c6f7a03");
    private static final int PLAY_MIN = 40 * 20, PLAY_MAX = 350 * 20;

    private FoxPets() {}

    // ---------------------------------------------------------------- lookups

    public static UUID ownerOf(Fox fox) {
        CompoundTag d = fox.getPersistentData();
        return d.hasUUID(OWNER) ? d.getUUID(OWNER) : null;
    }

    public static boolean isOwnedBy(Entity e, Player p) {
        return e instanceof Fox f && p.getUUID().equals(ownerOf(f));
    }

    /** The fox's owner, if they're in the same world right now. */
    public static Player owner(Fox fox) {
        UUID id = ownerOf(fox);
        return id == null ? null : fox.level().getPlayerByUUID(id);
    }

    private static Optional<PlayerCharacter> cap(Player p) {
        return p.getCapability(ModCapabilities.PLAYER_CHARACTER).resolve();
    }

    private static ListTag pets(Player p) {
        return cap(p).map(c -> c.pets).orElseGet(ListTag::new);
    }

    private static CompoundTag record(Player p, UUID foxId) {
        for (Tag t : pets(p)) {
            CompoundTag rec = (CompoundTag) t;
            if (rec.hasUUID("uuid") && rec.getUUID("uuid").equals(foxId)) return rec;
        }
        return null;
    }

    private static Fox findLoaded(ServerPlayer p, UUID id) {
        return p.serverLevel().getEntity(id) instanceof Fox f && f.isAlive() ? f : null;
    }

    public static void wake(Fox fox) {
        FoxAccessor a = (FoxAccessor) fox;
        if (fox.isSleeping()) a.xenowarriors$setSleeping(false);
        if (fox.isSitting()) a.xenowarriors$setSitting(false);
    }

    private static void say(ServerPlayer p, String text) {
        p.displayClientMessage(Component.literal(text).withStyle(ChatFormatting.GOLD), true);
    }

    private static boolean isRabbit(ItemStack s) {
        return s.is(Items.RABBIT) || s.is(Items.COOKED_RABBIT);
    }

    // ---------------------------------------------------------------- right-clicking foxes

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide || !(event.getTarget() instanceof Fox fox) || !(event.getEntity() instanceof ServerPlayer p)) return;
        ItemStack stack = p.getItemInHand(event.getHand());
        UUID owner = ownerOf(fox);
        if (owner == null) {
            if (!isRabbit(stack) || ModCapabilities.getType(p) != CharacterType.MIKIKO) return;
            consume(event);
            tryTame(p, fox, stack);
        } else if (owner.equals(p.getUUID())) {
            consume(event);
            if (event.getHand() != InteractionHand.MAIN_HAND) return;
            if (p.isShiftKeyDown()) {
                putOnBack(p, fox);
            } else if (isRabbit(stack) && fox.getHealth() < fox.getMaxHealth()) {
                fox.heal(6F);
                if (!p.getAbilities().instabuild) stack.shrink(1);
                fox.playSound(SoundEvents.FOX_EAT, 1F, 1F);
                p.serverLevel().sendParticles(ParticleTypes.HEART, fox.getX(), fox.getY() + 0.8, fox.getZ(), 2, 0.2, 0.1, 0.2, 0);
            } else {
                showStats(p, fox);   // plain right-click: the fox's stat card
            }
        }
    }

    private static void consume(PlayerInteractEvent.EntityInteract event) {
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    private static void tryTame(ServerPlayer p, Fox fox, ItemStack stack) {
        if (pets(p).size() >= MAX_FOXES) {
            say(p, "You can only keep " + MAX_FOXES + " foxes.");
            return;
        }
        if (!p.getAbilities().instabuild) stack.shrink(1);
        ServerLevel level = p.serverLevel();
        fox.playSound(SoundEvents.FOX_EAT, 1F, 1F);
        if (p.getRandom().nextInt(3) != 0) {
            level.sendParticles(ParticleTypes.SMOKE, fox.getX(), fox.getY() + 0.6, fox.getZ(), 6, 0.25, 0.2, 0.25, 0.01);
            return;
        }
        UUID id = fox.getUUID();
        fox.getPersistentData().putUUID(OWNER, p.getUUID());
        fox.getPersistentData().putInt(GEN, 0);
        ((FoxAccessor) fox).xenowarriors$addTrusted(p.getUUID());
        fox.setPersistenceRequired();
        fox.setTarget(null);
        fox.getNavigation().stop();
        wake(fox);

        CompoundTag rec = new CompoundTag();
        rec.putUUID("uuid", id);
        rec.putString("name", "Fox");
        rec.putInt("level", 1);
        rec.putFloat("xp", 0);
        rec.putInt("gen", 0);
        rec.putLong("tamed", p.level().getGameTime());
        cap(p).ifPresent(c -> c.pets.add(rec));
        addGoals(fox);
        applyLevel(fox, 1);
        updateName(fox, rec);
        snapshot(fox, rec);

        level.sendParticles(ParticleTypes.HEART, fox.getX(), fox.getY() + 0.7, fox.getZ(), 7, 0.3, 0.3, 0.3, 0);
        level.playSound(null, fox.blockPosition(), SoundEvents.FOX_AMBIENT, SoundSource.NEUTRAL, 1F, 1.3F);
        say(p, "The fox trusts you now!");
        sync(p, id, "Fox");
        sendSkin(fox);
    }

    // ---------------------------------------------------------------- stat card

    private static void showStats(ServerPlayer p, Fox fox) {
        CompoundTag rec = record(p, fox.getUUID());
        if (rec == null) return;
        int level = rec.getInt("level");
        long days = rec.contains("tamed") ? Math.max(0, (p.level().getGameTime() - rec.getLong("tamed")) / 24000L) : -1;
        ModNetwork.sendToPlayer(new FoxStatsPacket(fox.getId(), fox.getUUID(), rec.getString("name"), level,
                rec.getFloat("xp"), level >= FoxLevels.MAX ? 0 : FoxLevels.xpFor(level), fox.getHealth(), fox.getMaxHealth(),
                (float) fox.getAttributeValue(Attributes.ATTACK_DAMAGE), (float) fox.getAttributeValue(Attributes.MOVEMENT_SPEED),
                rec.getInt("kills"), (int) days, "snow".equals(fox.saveWithoutId(new CompoundTag()).getString("Type")),
                rec.getInt("skin")), p);
        fox.playSound(SoundEvents.FOX_AMBIENT, 0.6F, 1.3F);
    }

    // ---------------------------------------------------------------- naming

    public static void rename(ServerPlayer p, UUID foxId, String raw) {
        CompoundTag rec = record(p, foxId);
        if (rec == null) return;
        String name = ChatFormatting.stripFormatting(raw == null ? "" : raw).trim();
        if (name.length() > 24) name = name.substring(0, 24);
        if (name.isEmpty()) name = "Fox";
        rec.putString("name", name);
        Fox fox = findLoaded(p, foxId);
        if (fox != null) updateName(fox, rec);
        sync(p, null, null);
    }

    private static void updateName(Fox fox, CompoundTag rec) {
        fox.setCustomName(Component.literal(rec.getString("name"))
                .append(Component.literal(" Lv " + rec.getInt("level")).withStyle(ChatFormatting.GOLD)));
    }

    // ---------------------------------------------------------------- levels

    private static void applyLevel(Fox fox, int level) {
        set(fox.getAttribute(Attributes.MAX_HEALTH), HEALTH_ID, FoxLevels.health(level), AttributeModifier.Operation.ADDITION);
        set(fox.getAttribute(Attributes.ATTACK_DAMAGE), ATTACK_ID, FoxLevels.attack(level), AttributeModifier.Operation.ADDITION);
        set(fox.getAttribute(Attributes.MOVEMENT_SPEED), SPEED_ID, FoxLevels.speed(level), AttributeModifier.Operation.MULTIPLY_BASE);
        if (fox.getHealth() > fox.getMaxHealth()) fox.setHealth(fox.getMaxHealth());
    }

    private static void set(AttributeInstance inst, UUID id, double value, AttributeModifier.Operation op) {
        if (inst == null) return;
        inst.removeModifier(id);
        if (value > 0) inst.addPermanentModifier(new AttributeModifier(id, "Fox level", value, op));
    }

    private static void addXp(Fox fox, float amount) {
        if (amount <= 0 || !(owner(fox) instanceof ServerPlayer p)) return;
        CompoundTag rec = record(p, fox.getUUID());
        if (rec == null) return;
        int level = rec.getInt("level");
        if (level >= FoxLevels.MAX) return;
        float xp = rec.getFloat("xp") + amount;
        boolean up = false;
        while (level < FoxLevels.MAX && xp >= FoxLevels.xpFor(level)) {
            xp -= FoxLevels.xpFor(level);
            level++;
            up = true;
        }
        rec.putFloat("xp", xp);
        if (!up) return;
        rec.putInt("level", level);
        applyLevel(fox, level);
        fox.heal(4F);
        updateName(fox, rec);
        ServerLevel lvl = p.serverLevel();
        lvl.sendParticles(ParticleTypes.HAPPY_VILLAGER, fox.getX(), fox.getY() + 0.5, fox.getZ(), 14, 0.35, 0.35, 0.35, 0);
        lvl.sendParticles(ParticleTypes.HEART, fox.getX(), fox.getY() + 0.9, fox.getZ(), 3, 0.2, 0.1, 0.2, 0);
        lvl.playSound(null, fox.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.NEUTRAL, 0.5F, 1.6F);
        ModNetwork.sendToPlayer(new FoxLevelPacket(rec.getString("name"), level), p);
        fx(fox, FoxFxPacket.LEVEL_UP);
    }

    // ---------------------------------------------------------------- goals, joining the world

    private static void addGoals(Fox fox) {
        boolean has = fox.goalSelector.getAvailableGoals().stream().anyMatch(w -> w.getGoal() instanceof FoxFollowOwnerGoal);
        if (has) return;
        fox.goalSelector.addGoal(2, new FoxPlayGoal(fox));
        fox.goalSelector.addGoal(3, new FoxFollowOwnerGoal(fox));
        fox.goalSelector.addGoal(4, new FoxRestGoal(fox));
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide || !(event.getEntity() instanceof Fox fox)) return;
        UUID ownerId = ownerOf(fox);
        if (ownerId == null) return;
        MinecraftServer server = fox.getServer();
        ServerPlayer owner = server == null ? null : server.getPlayerList().getPlayer(ownerId);
        if (owner != null) {
            CompoundTag rec = record(owner, fox.getUUID());
            // an old copy of a fox that has since been called back (or is on his back): it goes away
            if (rec == null || rec.getBoolean("dead") || rec.getBoolean("back") || rec.getInt("gen") != fox.getPersistentData().getInt(GEN)) {
                event.setCanceled(true);
                return;
            }
        }
        addGoals(fox);
    }

    // ---------------------------------------------------------------- combat & xp

    @SubscribeEvent
    public static void onAttack(LivingAttackEvent event) {
        // Mikiko can't hurt his own foxes by accident
        if (event.getEntity() instanceof Fox fox && event.getSource().getEntity() instanceof Player p && isOwnedBy(fox, p)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide) return;
        Entity src = event.getSource().getEntity();
        if (src instanceof Fox fox && ownerOf(fox) != null) addXp(fox, event.getAmount() * 3F);
        if (victim instanceof Fox fox && ownerOf(fox) != null) addXp(fox, event.getAmount() * 1.5F);
        // foxes join in on whatever Mikiko is fighting
        if (src instanceof ServerPlayer p && !pets(p).isEmpty() && !(victim instanceof Player) && !isOwnedBy(victim, p)
                && !(victim instanceof TamableAnimal t && p.getUUID().equals(t.getOwnerUUID()))) {
            for (Fox fox : p.serverLevel().getEntitiesOfClass(Fox.class, p.getBoundingBox().inflate(16), f -> isOwnedBy(f, p))) {
                if (fox.getTarget() == null || !fox.getTarget().isAlive()) {
                    wake(fox);
                    fox.setTarget(victim);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (dead.level().isClientSide) return;
        if (event.getSource().getEntity() instanceof Fox killer && ownerOf(killer) != null) {
            if (owner(killer) instanceof ServerPlayer kp) {
                CompoundTag krec = record(kp, killer.getUUID());
                if (krec != null) krec.putInt("kills", krec.getInt("kills") + 1);
            }
            addXp(killer, 6F + dead.getMaxHealth() / 2F);
        }
        if (dead instanceof Fox fox && owner(fox) instanceof ServerPlayer p) {
            CompoundTag rec = record(p, fox.getUUID());
            if (rec != null) {
                snapshot(fox, rec);
                rec.putBoolean("dead", true);
                p.sendSystemMessage(Component.literal(rec.getString("name") + " ran away! Use the Fox Whistle to call them back.")
                        .withStyle(ChatFormatting.GOLD));
                fx(fox, FoxFxPacket.RUN_AWAY);
            }
        }
        if (dead instanceof ServerPlayer p) putDown(p);   // the fox on his back hops off
    }

    // ---------------------------------------------------------------- per-player upkeep

    private static final class State {
        double x, y, z;
        int still, threshold = roll(), playUntil;
        final Map<UUID, Vec3> lastPos = new HashMap<>();
        final Map<UUID, Long> lastNear = new HashMap<>();
    }

    private static final Map<UUID, State> STATES = new HashMap<>();

    private static int roll() {
        return PLAY_MIN + (int) (Math.random() * (PLAY_MAX - PLAY_MIN + 1));
    }

    /** Whether this player has stood still long enough that the foxes want to play. */
    /** Ticks this player has been standing still (0 when moving). */
    public static int stillTicks(Player p) {
        State s = STATES.get(p.getUUID());
        return s == null ? 0 : s.still;
    }

    public static boolean isPlaying(Player p) {
        State s = STATES.get(p.getUUID());
        return s != null && s.playUntil > 0;
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p)) return;
        ListTag pets = pets(p);
        if (pets.isEmpty()) return;
        State s = STATES.computeIfAbsent(p.getUUID(), k -> new State());

        // standing still: a random 40-350 s, then 30-60 s of play, then the wait starts over
        double dx = p.getX() - s.x, dy = p.getY() - s.y, dz = p.getZ() - s.z;
        if (dx * dx + dy * dy + dz * dz > 0.3 * 0.3 || p.isSpectator()) {
            s.x = p.getX(); s.y = p.getY(); s.z = p.getZ();
            s.still = 0; s.playUntil = 0; s.threshold = roll();
        } else {
            s.still++;
            if (s.playUntil == 0 && s.still >= s.threshold) s.playUntil = s.still + 600 + p.getRandom().nextInt(600);
            if (s.playUntil > 0 && s.still >= s.playUntil) {
                s.still = 0; s.playUntil = 0; s.threshold = roll();
            }
        }

        if (p.tickCount % 20 != 0) return;
        for (Tag t : pets) {
            CompoundTag rec = (CompoundTag) t;
            if (rec.getBoolean("dead") || rec.getBoolean("back") || !rec.hasUUID("uuid")) continue;
            Fox fox = findLoaded(p, rec.getUUID("uuid"));
            if (fox == null) continue;
            // running alongside him counts as training too
            Vec3 last = s.lastPos.put(fox.getUUID(), fox.position());
            if (last != null && last.distanceTo(fox.position()) > 3 && fox.distanceTo(p) < 16) addXp(fox, 1F);
            if (p.tickCount % 100 == 0) snapshot(fox, rec);
            // happy to see him again after a while apart: a little hop and spin
            double d = fox.distanceTo(p);
            Long seen = s.lastNear.get(fox.getUUID());
            if (d < 8 && (seen == null || p.tickCount - seen > 900) && seen != null) {
                fx(fox, FoxFxPacket.GREET);
                p.serverLevel().sendParticles(ParticleTypes.HEART, fox.getX(), fox.getY() + 0.9, fox.getZ(), 3, 0.2, 0.1, 0.2, 0);
                fox.playSound(SoundEvents.FOX_AMBIENT, 1F, 1.4F);
            }
            if (d < 16) s.lastNear.put(fox.getUUID(), (long) p.tickCount);
        }
    }

    private static void snapshot(Fox fox, CompoundTag rec) {
        rec.put("data", fox.saveWithoutId(new CompoundTag()));
    }

    // ---------------------------------------------------------------- on his back

    private static void putOnBack(ServerPlayer p, Fox fox) {
        PlayerCharacter c = cap(p).orElse(null);
        CompoundTag rec = record(p, fox.getUUID());
        if (c == null || rec == null) return;
        if (!c.backFox.isEmpty()) {
            say(p, "You're already carrying a fox.");
            return;
        }
        wake(fox);
        fox.stopRiding();
        fox.ejectPassengers();
        CompoundTag tag = fox.saveWithoutId(new CompoundTag());
        c.backFox = tag;
        rec.putBoolean("back", true);
        rec.put("data", tag.copy());
        fox.discard();
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.ARMOR_EQUIP_LEATHER, SoundSource.PLAYERS, 1F, 1.2F);
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.FOX_SLEEP, SoundSource.NEUTRAL, 0.8F, 1F);
        sendBack(p);
    }

    /** Lets the fox on his back hop down in front of him. */
    public static boolean putDown(ServerPlayer p) {
        PlayerCharacter c = cap(p).orElse(null);
        if (c == null || c.backFox.isEmpty()) return false;
        CompoundTag tag = c.backFox;
        Fox fox = EntityType.FOX.create(p.serverLevel());
        if (fox == null) return false;
        fox.load(tag);
        c.backFox = new CompoundTag();
        CompoundTag rec = record(p, fox.getUUID());
        if (rec == null) {   // shouldn't happen, but never lose the fox
            sendBack(p);
            return false;
        }
        rec.putBoolean("back", false);
        Vec3 look = Vec3.directionFromRotation(0, p.getYRot());
        Vec3 pos = p.position().add(look.scale(1.1));
        fox.moveTo(pos.x, p.getY(), pos.z, p.getYRot() + 180, 0);
        if (!p.level().noCollision(fox)) fox.moveTo(p.getX(), p.getY(), p.getZ(), p.getYRot(), 0);
        fox.fallDistance = 0;
        prepare(p, fox, rec);
        p.serverLevel().addFreshEntity(fox);
        sendBack(p);
        return true;
    }

    private static void prepare(ServerPlayer p, Fox fox, CompoundTag rec) {
        fox.getPersistentData().putUUID(OWNER, p.getUUID());
        fox.getPersistentData().putInt(GEN, rec.getInt("gen"));
        ((FoxAccessor) fox).xenowarriors$addTrusted(p.getUUID());
        fox.setPersistenceRequired();
        wake(fox);
        applyLevel(fox, rec.getInt("level"));
        updateName(fox, rec);
        addGoals(fox);
        fox.getPersistentData().putInt(SKIN, rec.getInt("skin"));
        for (var slot : FoxMenu.ARMOR) fox.setDropChance(slot, 0F);
    }

    // ---------------------------------------------------------------- skins, bag, effects

    private static final String SKIN = "xw_skin";

    public static int skinOf(Fox fox) {
        return fox.getPersistentData().getInt(SKIN);
    }

    private static void sendSkin(Fox fox) {
        ModNetwork.CHANNEL.send(net.minecraftforge.network.PacketDistributor.TRACKING_ENTITY.with(() -> fox),
                new FoxSkinPacket(fox.getId(), skinOf(fox)));
    }

    private static void fx(Fox fox, int kind) {
        ModNetwork.CHANNEL.send(net.minecraftforge.network.PacketDistributor.TRACKING_ENTITY.with(() -> fox),
                new FoxFxPacket(fox.getId(), kind));
    }

    public static void setSkin(ServerPlayer p, UUID foxId, int skin) {
        CompoundTag rec = record(p, foxId);
        if (rec == null || !FoxSkins.unlocked(skin, rec.getInt("level"))) return;
        rec.putInt("skin", skin);
        Fox fox = findLoaded(p, foxId);
        if (fox != null) {
            fox.getPersistentData().putInt(SKIN, skin);
            sendSkin(fox);
            p.serverLevel().sendParticles(ParticleTypes.HAPPY_VILLAGER, fox.getX(), fox.getY() + 0.5, fox.getZ(), 10, 0.3, 0.3, 0.3, 0);
            fox.playSound(SoundEvents.ARMOR_EQUIP_LEATHER, 0.8F, 1.3F);
        }
    }

    /** Opens the fox's bag: its armor and the items it carries for him. */
    public static void openBag(ServerPlayer p, UUID foxId) {
        CompoundTag rec = record(p, foxId);
        Fox fox = findLoaded(p, foxId);
        if (rec == null || fox == null || fox.distanceTo(p) > 10) return;
        int slots = FoxSkins.bagSlots(rec.getInt("level")), rows = slots / 9;
        net.minecraft.world.SimpleContainer storage = new net.minecraft.world.SimpleContainer(slots) {
            @Override
            public void setChanged() {
                super.setChanged();
                net.minecraft.core.NonNullList<ItemStack> items = net.minecraft.core.NonNullList.withSize(getContainerSize(), ItemStack.EMPTY);
                for (int i = 0; i < getContainerSize(); i++) items.set(i, getItem(i));
                CompoundTag inv = new CompoundTag();
                net.minecraft.world.ContainerHelper.saveAllItems(inv, items);
                rec.put("inv", inv);
            }
        };
        net.minecraft.core.NonNullList<ItemStack> items = net.minecraft.core.NonNullList.withSize(27, ItemStack.EMPTY);
        net.minecraft.world.ContainerHelper.loadAllItems(rec.getCompound("inv"), items);
        for (int i = 0; i < slots; i++) storage.setItem(i, items.get(i));
        FoxMenu.ArmorContainer armor = new FoxMenu.ArmorContainer(fox);
        Component title = Component.literal(rec.getString("name") + "'s Bag");
        net.minecraftforge.network.NetworkHooks.openScreen(p, new net.minecraft.world.SimpleMenuProvider(
                (id, inv, pl) -> new FoxMenu(id, inv, storage, armor, fox, rows), title), buf -> {
            buf.writeVarInt(fox.getId());
            buf.writeVarInt(rows);
        });
        fox.playSound(SoundEvents.BUNDLE_DROP_CONTENTS, 0.6F, 1.2F);
    }

    public static int backVariant(Player p) {
        CompoundTag tag = cap(p).map(c -> c.backFox).orElse(null);
        if (tag == null || tag.isEmpty()) return -1;
        int skin = tag.getCompound("ForgeData").getInt(SKIN);
        if (skin > 0) return 10 + skin;
        return "snow".equals(tag.getString("Type")) ? 1 : 0;
    }

    private static void sendBack(ServerPlayer p) {
        ModNetwork.sendToTrackingAndSelf(new BackFoxPacket(p.getUUID(), backVariant(p)), p);
    }

    // ---------------------------------------------------------------- the whistle

    public static void whistle(ServerPlayer p) {
        if (ModCapabilities.getType(p) != CharacterType.MIKIKO) {
            say(p, "Only Mikiko's foxes answer this whistle.");
            return;
        }
        ListTag pets = pets(p);
        if (pets.isEmpty()) {
            say(p, "You don't have any foxes yet. Tame one with rabbit meat!");
            return;
        }
        putDown(p);
        int revived = 0;
        for (Tag t : pets) {
            CompoundTag rec = (CompoundTag) t;
            if (rec.getBoolean("back")) {
                if (!cap(p).map(c -> c.backFox.isEmpty()).orElse(true)) continue;
                rec.putBoolean("back", false);   // on his back with nothing there: lost, call it back
            }
            Fox fox = rec.getBoolean("dead") || !rec.hasUUID("uuid") ? null : findLoaded(p, rec.getUUID("uuid"));
            if (fox != null) {
                wake(fox);
                fox.setTarget(null);
                if (fox.distanceTo(p) > 4) FoxFollowOwnerGoal.teleportNear(fox, p);
                continue;
            }
            // dead, in another dimension, or stuck far away: it comes back to him
            if (!rec.getBoolean("dead") && rec.hasUUID("uuid")) {
                for (ServerLevel level : p.server.getAllLevels()) {
                    if (level.getEntity(rec.getUUID("uuid")) instanceof Fox old) old.discard();
                }
            }
            boolean wasDead = rec.getBoolean("dead");
            if (recreate(p, rec) && wasDead) {
                revived++;
                p.sendSystemMessage(Component.literal(rec.getString("name") + " answers your whistle and comes back!").withStyle(ChatFormatting.GOLD));
            }
        }
        if (revived > 0) {
            p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.FOX_AMBIENT, SoundSource.NEUTRAL, 1F, 1.4F);
        }
        sync(p, null, null);
    }

    private static boolean recreate(ServerPlayer p, CompoundTag rec) {
        ServerLevel level = p.serverLevel();
        Fox fox = EntityType.FOX.create(level);
        if (fox == null) return false;
        CompoundTag data = rec.getCompound("data").copy();
        if (!data.isEmpty()) {
            data.remove("UUID");          // a brand new body (any old copy is gone for good)
            data.remove("HandItems");     // whatever it had in its mouth already dropped
            data.remove("Passengers");
            data.remove("ActiveEffects");
            fox.load(data);
        }
        int gen = rec.getInt("gen") + 1;
        rec.putInt("gen", gen);
        rec.putUUID("uuid", fox.getUUID());
        rec.putBoolean("dead", false);
        rec.putBoolean("back", false);
        prepare(p, fox, rec);
        fox.deathTime = 0;
        fox.hurtTime = 0;
        fox.clearFire();
        fox.removeAllEffects();
        fox.setHealth(fox.getMaxHealth());
        fox.setTarget(null);
        fox.moveTo(p.getX(), p.getY(), p.getZ(), p.getYRot(), 0);
        FoxFollowOwnerGoal.teleportNear(fox, p);
        fox.fallDistance = 0;
        level.addFreshEntity(fox);
        level.sendParticles(ParticleTypes.FLAME, fox.getX(), fox.getY() + 0.4, fox.getZ(), 16, 0.3, 0.3, 0.3, 0.02);
        level.sendParticles(ParticleTypes.HEART, fox.getX(), fox.getY() + 0.8, fox.getZ(), 4, 0.3, 0.2, 0.3, 0);
        snapshot(fox, rec);
        return true;
    }

    // ---------------------------------------------------------------- syncing

    /** Tells the owner which foxes are his (and optionally pops up the naming screen for one). */
    public static void sync(ServerPlayer p, UUID prompt, String promptName) {
        Map<UUID, String> names = new HashMap<>();
        for (Tag t : pets(p)) {
            CompoundTag rec = (CompoundTag) t;
            if (rec.hasUUID("uuid")) names.put(rec.getUUID("uuid"), rec.getString("name"));
        }
        ModNetwork.sendToPlayer(new FoxSyncPacket(names, prompt), p);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            sync(p, null, null);
            sendBack(p);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            sync(p, null, null);
            sendBack(p);
        }
    }

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) sendBack(p);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        STATES.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof ServerPlayer target && event.getEntity() instanceof ServerPlayer viewer) {
            ModNetwork.sendToPlayer(new BackFoxPacket(target.getUUID(), backVariant(target)), viewer);
        }
        if (event.getTarget() instanceof Fox fox && ownerOf(fox) != null && event.getEntity() instanceof ServerPlayer viewer) {
            ModNetwork.sendToPlayer(new FoxSkinPacket(fox.getId(), skinOf(fox)), viewer);
        }
    }
}

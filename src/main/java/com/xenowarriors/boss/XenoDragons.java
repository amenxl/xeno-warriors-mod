package com.xenowarriors.boss;

import com.xenowarriors.XenoWarriors;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.*;

/**
 * The Xeno Dragon (with DragonMineZ installed): when an Ender Dragon is beaten - the first one or a respawned one,
 * killed or /killed - its death plays out as normal (XP, the exit portal, the egg on the first kill), but its power
 * gathers instead of fading, and once it's gone the Xeno Dragon takes shape where it died and glides down onto the
 * island. Only one at a time per world. Without DragonMineZ (or with xenoDragon=false in the config) nothing changes.
 * Also here: its loot, its breath clouds' and ki's damage, and its End Crystals (they never break blocks).
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class XenoDragons {
    private XenoDragons() {}

    public static final String CRYSTAL_TAG = "xw_xeno_crystal";

    public static final RegistryObject<EntityType<XenoDragonEntity>> XENO_DRAGON = com.xenowarriors.entity.ModEntities.ENTITY_TYPES.register(
            "xeno_dragon", () -> EntityType.Builder.<XenoDragonEntity>of(XenoDragonEntity::new, MobCategory.MONSTER)
                    .sized(1.2F, 3.2F).fireImmune().clientTrackingRange(16).build("xeno_dragon"));
    public static final RegistryObject<EntityType<DragonBombEntity>> DRAGON_BOMB = com.xenowarriors.entity.ModEntities.ENTITY_TYPES.register(
            "dragon_bomb", () -> EntityType.Builder.<DragonBombEntity>of(DragonBombEntity::new, MobCategory.MISC)
                    .sized(1F, 1F).noSummon().fireImmune().clientTrackingRange(16).updateInterval(1).build("dragon_bomb"));
    public static final RegistryObject<Item> SPAWN_EGG = com.xenowarriors.item.ModItems.ITEMS.register("xeno_dragon_spawn_egg",
            () -> new net.minecraftforge.common.ForgeSpawnEggItem(XENO_DRAGON, 0x1C1622, 0xC050FF, new Item.Properties()) {
                @Override
                public void appendHoverText(ItemStack st, Level l, List<Component> tip, net.minecraft.world.item.TooltipFlag f) {
                    if (!dmz()) tip.add(Component.literal("A DragonMineZ boss: it only comes from the Ender Dragon with DragonMineZ").withStyle(ChatFormatting.GRAY));
                }
            });
    /** The trophy: scales off a god-ki dragon. */
    public static final RegistryObject<Item> SCALE = com.xenowarriors.item.ModItems.ITEMS.register("xeno_dragon_scale",
            () -> new Item(new Item.Properties().rarity(Rarity.EPIC).fireResistant()) {
                @Override
                public boolean isFoil(ItemStack st) { return true; }

                @Override
                public void appendHoverText(ItemStack st, Level l, List<Component> tip, net.minecraft.world.item.TooltipFlag f) {
                    tip.add(Component.literal("Torn from the Xeno Dragon in Ultra Instinct.").withStyle(ChatFormatting.DARK_PURPLE));
                    tip.add(Component.literal("Proof you beat a dragon with a god's ki.").withStyle(ChatFormatting.GRAY));
                }
            });

    /** Called from the mod constructor so the entries are registered in time. */
    public static void init() {}

    public static boolean dmz() { return ModList.get() != null && ModList.get().isLoaded("dragonminez"); }

    /** On: DragonMineZ is installed and the config allows it. */
    public static boolean enabled() { return dmz() && com.xenowarriors.XwConfig.get(com.xenowarriors.XwConfig.XENO_DRAGON); }

    /** (For the tests) what happened, newest last. */
    public static final List<String> LOG = Collections.synchronizedList(new ArrayList<>());
    /** (For the tests) Ender Dragons that turned into a Xeno Dragon, and loot drops. */
    public static int transformed, lootDrops;

    static void note(String s) {
        if (LOG.size() > 120) LOG.remove(0);
        LOG.add(s);
        XenoWarriors.LOGGER.info("XW xeno dragon: {}", s);
    }

    @Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class XenoDragonAttrs {
        @SubscribeEvent
        public static void xenoDragonAttributes(EntityAttributeCreationEvent event) {
            event.put(XENO_DRAGON.get(), XenoDragonEntity.createAttributes().build());
        }
    }

    // ---------------------------------------------------------------- the Ender Dragon's death

    private static final Set<UUID> DYING = Collections.synchronizedSet(new HashSet<>());
    private static final Set<UUID> DONE = Collections.synchronizedSet(new LinkedHashSet<>());
    private record Pending(ServerLevel level, Vec3 at, UUID dragon) {}
    private static final List<Pending> PENDING = Collections.synchronizedList(new ArrayList<>());

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent e) {
        if (e.isCanceled() || !(e.getEntity() instanceof EnderDragon d) || !(d.level() instanceof ServerLevel level) || !enabled()) return;
        if (DYING.add(d.getUUID())) {
            for (ServerPlayer p : level.players())
                if (p.distanceToSqr(d) < 300 * 300) p.displayClientMessage(Component.literal("The Ender Dragon's power isn't fading... it's changing!").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC), false);
            note("ender dragon dying at " + d.blockPosition().toShortString());
        }
    }

    /** Gone for good (the end of its death, or /kill): the Xeno Dragon takes shape a tick later. */
    @SubscribeEvent
    public static void onLeave(EntityLeaveLevelEvent e) {
        if (!(e.getEntity() instanceof EnderDragon d) || !(e.getLevel() instanceof ServerLevel level)) return;
        DYING.remove(d.getUUID());
        if (d.getRemovalReason() != Entity.RemovalReason.KILLED || !enabled()) return;
        if (!DONE.add(d.getUUID())) return;
        if (DONE.size() > 64) { var it = DONE.iterator(); it.next(); it.remove(); }
        PENDING.add(new Pending(level, d.position(), d.getUUID()));
    }

    /** Every Ender Dragon in a world (weakly held): one /killed in a part of the world that isn't ticking never "leaves" it. */
    private static final Set<EnderDragon> DRAGONS = Collections.newSetFromMap(new WeakHashMap<>());

    @SubscribeEvent
    public static void onJoin(net.minecraftforge.event.entity.EntityJoinLevelEvent e) {
        if (e.getEntity() instanceof EnderDragon d && e.getLevel() instanceof ServerLevel) synchronized (DRAGONS) { DRAGONS.add(d); }
    }

    /** Dragons gone (KILLED) that the leave event didn't report. */
    public static void scanDragons() {
        synchronized (DRAGONS) {
            if (DRAGONS.isEmpty()) return;
            for (Iterator<EnderDragon> it = DRAGONS.iterator(); it.hasNext(); ) {
                EnderDragon d = it.next();
                if (!d.isRemoved()) continue;
                it.remove();
                if (d.getRemovalReason() == Entity.RemovalReason.KILLED && d.level() instanceof ServerLevel level && enabled() && DONE.add(d.getUUID()))
                    PENDING.add(new Pending(level, d.position(), d.getUUID()));
            }
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        scanDragons();
        processPending();
    }

    /** Turns the dragons that just went into Xeno Dragons. */
    public static void processPending() {
        List<Pending> l;
        synchronized (PENDING) { if (PENDING.isEmpty()) return; l = new ArrayList<>(PENDING); PENDING.clear(); }
        for (Pending p : l) {
            try { transform(p.level, p.at); } catch (Throwable t) { XenoWarriors.LOGGER.error("Xeno Dragon didn't appear", t); }
        }
    }

    /** The Xeno Dragon appears here (null: one is already around). */
    public static XenoDragonEntity transform(ServerLevel level, Vec3 at) {
        for (Entity x : level.getAllEntities())
            if (x instanceof XenoDragonEntity xd && xd.isAlive()) { note("a Xeno Dragon is already here (" + xd.blockPosition().toShortString() + "): no second one"); return null; }
        XenoDragonEntity x = XENO_DRAGON.get().create(level);
        if (x == null) return null;
        double y = Math.max(at.y, level.getMinBuildHeight() + 2);
        BlockPos land = landing(level, at);
        x.moveTo(at.x, y, at.z, 0F, 0F);
        x.finalizeSpawn(level, level.getCurrentDifficultyAt(land), MobSpawnType.EVENT, null, null);
        x.setPersistenceRequired();
        x.emerge(land);
        level.addFreshEntity(x);
        transformed++;
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, y + 1, at.z, 2, 1, 1, 1, 0);
        for (ServerPlayer p : level.players())
            if (p.distanceToSqr(x) < 300 * 300) p.displayClientMessage(Component.literal("The Ender Dragon has become the Xeno Dragon!").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD), false);
        note("transformed at " + BlockPos.containing(at).toShortString() + " -> lands at " + land.toShortString() + " (" + level.dimension().location() + ")");
        return x;
    }

    /** Where it lands: in the End, on the main island beside the exit portal (on the side the dragon died); elsewhere the ground below. */
    public static BlockPos landing(ServerLevel level, Vec3 at) {
        double x = at.x, z = at.z;
        if (level.dimension() == Level.END) {
            double d = Math.sqrt(x * x + z * z), r = 14;
            if (d < 1) { x = r; z = 0; } else { x = x / d * r; z = z / d * r; }
        }
        for (int ring = 0; ring <= 24; ring += 2) {
            for (int i = 0; i < Math.max(1, ring * 3); i++) {
                double a = i * Math.PI * 2 / Math.max(1, ring * 3);
                int bx = (int) Math.floor(x + Math.cos(a) * ring), bz = (int) Math.floor(z + Math.sin(a) * ring);
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
                if (y <= level.getMinBuildHeight() + 1) continue;
                BlockPos p = new BlockPos(bx, y, bz);
                if (level.getBlockState(p.below()).isSolid() && level.getFluidState(p).isEmpty()) return p;
            }
        }
        ServerPlayer near = null;
        for (ServerPlayer p : level.players()) if (near == null || p.distanceToSqr(at) < near.distanceToSqr(at)) near = p;
        return near != null ? near.blockPosition() : BlockPos.containing(at);
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.level instanceof ServerLevel level) || DYING.isEmpty() || level.getGameTime() % 2 != 0) return;
        for (UUID id : DYING.toArray(new UUID[0])) {                              // the dying dragon's power gathers instead of fading
            if (!(level.getEntity(id) instanceof EnderDragon d)) continue;
            int t = d.dragonDeathTime;
            double r = Math.max(1.5, 10 - t * 0.045);
            for (int i = 0; i < 8; i++) {
                double a = level.random.nextDouble() * Math.PI * 2, b = (level.random.nextDouble() - .5) * Math.PI;
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, d.getX() + Math.cos(a) * Math.cos(b) * r, d.getY() + 2 + Math.sin(b) * r, d.getZ() + Math.sin(a) * Math.cos(b) * r, 0,
                        -Math.cos(a) * Math.cos(b), -Math.sin(b), -Math.sin(a) * Math.cos(b), 0.5);
            }
            if (t > 120) level.sendParticles(ParticleTypes.DRAGON_BREATH, d.getX(), d.getY() + 2, d.getZ(), 6, 1.5, 1.5, 1.5, 0.02);
        }
    }

    // ---------------------------------------------------------------- its damage

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttack(LivingAttackEvent e) {
        if (e.getEntity() instanceof XenoDragonEntity x && e.getSource().getEntity() == x) e.setCanceled(true);   // (its own breath)
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onHurt(LivingHurtEvent e) {
        if (!(e.getSource().getEntity() instanceof XenoDragonEntity x) || e.getSource().getDirectEntity() == x) return;
        Entity direct = e.getSource().getDirectEntity();
        if (direct == null || direct instanceof DragonBombEntity) return;
        float atk = (float) x.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        if (direct instanceof AreaEffectCloud) { e.setAmount(atk * 0.45F); return; }                 // dragon breath / fireball clouds: each pulse
        if (com.xenowarriors.ka.DmzKi.isKi(direct)) {
            String n = direct.getClass().getSimpleName();
            e.setAmount(atk * (n.contains("Wave") || n.contains("Laser") || n.contains("Beam") ? 2.2F : 1.1F));
        }
    }

    /** Its End Crystals never break blocks when they blow. */
    @SubscribeEvent
    public static void onDetonate(ExplosionEvent.Detonate e) {
        Entity src = e.getExplosion().getExploder();
        if (src != null && src.getTags().contains(CRYSTAL_TAG)) e.getAffectedBlocks().clear();
    }

    // ---------------------------------------------------------------- loot

    private static Item item(String ns, String path) {
        Item it = ForgeRegistries.ITEMS.getValue(new ResourceLocation(ns, path));
        return it == null || it == Items.AIR ? null : it;
    }

    private static void give(List<ItemStack> out, Item it, int n) { if (it != null && n > 0) out.add(new ItemStack(it, n)); }

    /** What it drops (only beaten in Ultra Instinct). */
    public static List<ItemStack> loot(Random r) {
        List<ItemStack> out = new ArrayList<>();
        give(out, SCALE.get(), 4 + r.nextInt(3));
        give(out, Items.DRAGON_HEAD, 1);
        give(out, Items.DRAGON_BREATH, 3 + r.nextInt(4));
        if (r.nextFloat() < 0.5F) give(out, Items.ELYTRA, 1);
        give(out, Items.DIAMOND, 6 + r.nextInt(5));
        give(out, Items.NETHERITE_SCRAP, 2 + r.nextInt(2));
        if (r.nextFloat() < 0.4F) give(out, Items.NETHERITE_INGOT, 1);
        give(out, Items.GOLDEN_APPLE, 2 + r.nextInt(2));
        if (r.nextFloat() < 0.35F) give(out, Items.ENCHANTED_GOLDEN_APPLE, 1);
        give(out, Items.TOTEM_OF_UNDYING, 1);
        give(out, item(XenoWarriors.MOD_ID, "karma_potion"), 2 + r.nextInt(2));
        give(out, item(XenoWarriors.MOD_ID, "karma_crystal"), 2 + r.nextInt(3));
        give(out, item(XenoWarriors.MOD_ID, "data_shard"), 2 + r.nextInt(3));
        give(out, item(XenoWarriors.MOD_ID, "aurora_gem"), 1);
        if (r.nextFloat() < 0.5F) give(out, item(XenoWarriors.MOD_ID, "old_world_fragment"), 1);
        give(out, item("dragonminez", "senzu_bean"), 3 + r.nextInt(3));
        give(out, item("dragonminez", "might_tree_fruit"), 1 + r.nextInt(2));
        give(out, item("dragonminez", "gete_ingot"), 2 + r.nextInt(2));
        give(out, item("dragonminez", "ki_battery"), 1);
        if (r.nextFloat() < 0.6F) { ItemStack g = ZombieSaiyans.gokuArmor(r); if (!g.isEmpty()) out.add(g); }
        return out;
    }

    /** Chance of a +100 Stat Boost on top of the guaranteed +10 (and the usual boss roll). */
    public static float HUNDRED_CHANCE = 0.3F;
    /** (For the tests) where the last loot came down. */
    public static Vec3 lastLootAt;

    static void dropLoot(XenoDragonEntity x, DamageSource src) {
        if (!(x.level() instanceof ServerLevel)) return;
        Random r = new Random(x.getRandom().nextLong());
        List<ItemStack> l = loot(r);
        // the killer's (or the killing bot's owner's) Stat Boosts: a +10 always, a +100 at HUNDRED_CHANCE
        Entity by = src.getEntity();
        if (by instanceof net.minecraftforge.common.util.FakePlayer fp) { var b = com.xenowarriors.bot.PartyBots.botOf(fp); if (b != null) by = b; }
        ServerPlayer owner = by instanceof ServerPlayer p && !(p instanceof net.minecraftforge.common.util.FakePlayer) ? p
                : by instanceof com.xenowarriors.bot.PartyBotEntity b ? b.owner() : null;
        List<ItemStack> boosts = new ArrayList<>();
        if (owner != null) {
            var pool = com.xenowarriors.statboost.StatBoosts.pool(owner);
            boosts.add(com.xenowarriors.statboost.StatBoosts.stack(pool.get(r.nextInt(pool.size())), com.xenowarriors.statboost.BoostTier.TEN));
            if (r.nextFloat() < HUNDRED_CHANCE) boosts.add(com.xenowarriors.statboost.StatBoosts.stack(pool.get(r.nextInt(pool.size())), com.xenowarriors.statboost.BoostTier.HUNDRED));
        }
        // (beaten over the void: the loot lands where it came down on the island instead)
        ServerLevel level = (ServerLevel) x.level();
        if (x.home() != null && level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x.getBlockX(), x.getBlockZ()) <= level.getMinBuildHeight() + 1) {
            x.setPos(x.home().getX() + 0.5, x.home().getY() + 0.5, x.home().getZ() + 0.5);
            note("beaten over the void: loot dropped at " + x.home().toShortString());
        }
        lastLootAt = x.position();
        StringBuilder sb = new StringBuilder();
        for (ItemStack st : l) {
            ItemEntity it = x.spawnAtLocation(st, 0.5F);
            if (it != null) sb.append(st.getCount()).append("x").append(ForgeRegistries.ITEMS.getKey(st.getItem()).getPath()).append(st.isEnchanted() ? "(enchanted)" : "").append(' ');
        }
        for (ItemStack st : boosts) {
            ItemEntity it = x.spawnAtLocation(st, 0.5F);
            if (it == null) continue;
            if (by != owner) { it.setTarget(owner.getUUID()); it.setThrower(owner.getUUID()); }
            sb.append(ForgeRegistries.ITEMS.getKey(st.getItem()).getPath()).append(' ');
        }
        lootDrops++;
        note("defeated in Ultra Instinct by " + (src.getEntity() == null ? "?" : src.getEntity().getName().getString()) + "; drops: " + sb.toString().trim());
    }

    // ---------------------------------------------------------------- dedicated-server self test

    /** A dragon in the End /killed: does a Xeno Dragon appear (only with DragonMineZ)? Then its forms and a Dragon Bomb, server side only. */
    public static String selfTest(MinecraftServer server) {
        ServerLevel end = server.getLevel(Level.END);
        if (end == null) return "no End";
        end.getChunk(0, 0);
        EnderDragon d = EntityType.ENDER_DRAGON.create(end);
        if (d == null) return "no dragon";
        d.moveTo(20, 90, 0, 0, 0);
        end.addFreshEntity(d);
        int before = transformed;
        d.kill();
        scanDragons();
        processPending();
        StringBuilder sb = new StringBuilder("dmz=" + dmz() + " xeno appeared=" + (transformed - before));
        for (Entity e : end.getAllEntities()) {
            if (!(e instanceof XenoDragonEntity x)) continue;
            sb.append(" at ").append(x.blockPosition().toShortString()).append(" lands ").append(x.home() == null ? "-" : x.home().toShortString());
            sb.append(" speeds");
            for (int f = XenoDragonEntity.BASE; f <= XenoDragonEntity.UI; f++) { x.setFormForTest(f); sb.append(' ').append(XenoDragonEntity.fmt(x.speed())); }
            DragonBombEntity b = DRAGON_BOMB.get().create(end);
            if (b != null) { b.moveTo(x.getX(), x.getY() + 6, x.getZ()); end.addFreshEntity(b); b.throwAt(x.position()); b.discard(); }
            sb.append(" bomb ok");
            x.discard();
        }
        return sb.toString();
    }
}

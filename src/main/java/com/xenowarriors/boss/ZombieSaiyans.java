package com.xenowarriors.boss;

import com.xenowarriors.XenoWarriors;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.*;

/**
 * The Zombie Saiyan: registered always (so worlds and commands never break), but it only appears by itself - rarely,
 * at night, in the Overworld, never in the mode arenas - with DragonMineZ installed, and its spawn egg is only in the
 * creative tab then. Its ki hits are set here, and its loot: rare things, and maybe a piece of DragonMineZ's Goku
 * armour carrying every enchantment that fits it, at max level.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ZombieSaiyans {
    private ZombieSaiyans() {}

    public static final RegistryObject<EntityType<ZombieSaiyanEntity>> ZOMBIE_SAIYAN = com.xenowarriors.entity.ModEntities.ENTITY_TYPES.register(
            "zombie_saiyan", () -> EntityType.Builder.<ZombieSaiyanEntity>of(ZombieSaiyanEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F).clientTrackingRange(10).build("zombie_saiyan"));
    public static final RegistryObject<Item> SPAWN_EGG = com.xenowarriors.item.ModItems.ITEMS.register("zombie_saiyan_spawn_egg",
            () -> new net.minecraftforge.common.ForgeSpawnEggItem(ZOMBIE_SAIYAN, 0x6E8A5A, 0xF07818, new Item.Properties()) {
                @Override
                public void appendHoverText(ItemStack st, Level l, List<Component> tip, net.minecraft.world.item.TooltipFlag f) {
                    if (!dmz()) tip.add(Component.literal("Needs DragonMineZ to appear in the world").withStyle(ChatFormatting.GRAY));
                }
            });

    /** Chance per player per 30-second check, at night in the Overworld (rare: about once every 25-30 in-game nights). */
    public static float SPAWN_CHANCE = 1F / 360F;

    /** Called from the mod constructor so the entries are registered in time. */
    public static void init() {}

    public static boolean dmz() { return ModList.get() != null && ModList.get().isLoaded("dragonminez"); }

    /** (For the tests) what happened, newest last. */
    public static final List<String> LOG = Collections.synchronizedList(new ArrayList<>());

    static void note(String s) {
        if (LOG.size() > 80) LOG.remove(0);
        LOG.add(s);
        XenoWarriors.LOGGER.info("XW zombie saiyan: {}", s);
    }

    @Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Attrs {
        @SubscribeEvent
        public static void attributes(EntityAttributeCreationEvent event) {
            event.put(ZOMBIE_SAIYAN.get(), ZombieSaiyanEntity.createAttributes().build());
        }
    }

    // ---------------------------------------------------------------- natural spawns

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.level instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return;
        if (level.getGameTime() % 600 != 123 || !dmz() || !level.isNight() || com.xenowarriors.modes.ModeRules.arena(level)) return;
        if (!level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING)) return;
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator() || p.isCreative() || level.random.nextFloat() >= SPAWN_CHANCE) continue;
            if (!level.getEntitiesOfClass(ZombieSaiyanEntity.class, p.getBoundingBox().inflate(160)).isEmpty()) continue;
            spawnNear(p, 24, 40, false);
        }
    }

    /** One Zombie Saiyan on open ground this far from the player. Null when there's no good spot. */
    public static ZombieSaiyanEntity spawnNear(ServerPlayer p, double min, double max, boolean persistent) {
        ServerLevel level = p.serverLevel();
        var r = p.getRandom();
        for (int i = 0; i < 20; i++) {
            double a = r.nextDouble() * Math.PI * 2, d = min + r.nextDouble() * (max - min);
            int x = (int) Math.floor(p.getX() + Math.cos(a) * d), z = (int) Math.floor(p.getZ() + Math.sin(a) * d);
            if (!level.hasChunkAt(new BlockPos(x, 0, z))) continue;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos at = new BlockPos(x, y, z);
            if (!level.getBlockState(at.below()).isSolid() || !level.getFluidState(at).isEmpty()) continue;
            if (!level.noCollision(ZOMBIE_SAIYAN.get().getAABB(x + 0.5, y, z + 0.5))) continue;
            if (!persistent && !level.canSeeSky(at)) continue;
            ZombieSaiyanEntity z0 = ZOMBIE_SAIYAN.get().create(level);
            if (z0 == null) return null;
            z0.moveTo(x + 0.5, y, z + 0.5, r.nextFloat() * 360F, 0F);
            z0.finalizeSpawn(level, level.getCurrentDifficultyAt(at), persistent ? MobSpawnType.COMMAND : MobSpawnType.EVENT, null, null);
            if (persistent) z0.setPersistenceRequired();
            level.addFreshEntity(z0);
            p.displayClientMessage(Component.literal("You feel a terrible, rotten ki nearby...").withStyle(ChatFormatting.DARK_GREEN), false);
            note("spawned at " + at.toShortString() + " (" + Math.round(Math.sqrt(z0.distanceToSqr(p))) + " blocks from " + p.getGameProfile().getName() + ")");
            return z0;
        }
        return null;
    }

    // ---------------------------------------------------------------- its ki hits

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onHurt(LivingHurtEvent e) {
        if (!(e.getSource().getEntity() instanceof ZombieSaiyanEntity z) || e.getSource().getDirectEntity() == z) return;
        var direct = e.getSource().getDirectEntity();
        if (direct == null) return;
        boolean ki = com.xenowarriors.ka.DmzKi.isKi(direct) || direct.getPersistentData().getBoolean("xw_saiyan_ki");
        if (!ki && !(direct instanceof com.xenowarriors.corrupted.SoulBoltEntity)) return;
        String n = direct.getClass().getSimpleName();
        boolean beam = n.contains("Wave") || n.contains("Laser") || n.contains("Beam");
        if (e.getEntity() instanceof ZombieSaiyanEntity) { e.setCanceled(true); return; }
        e.setAmount(z.kiDamage(beam));
    }

    // ---------------------------------------------------------------- loot

    private static final String[] GOKU = {"goku_armor", "goku_super_armor", "goku_gt_armor"};
    private static final String[] PIECES = {"chestplate", "leggings", "boots"};
    /** Chance of a Goku armour piece. */
    public static float ARMOR_CHANCE = 0.45F;

    private static Item item(String ns, String path) {
        Item it = ForgeRegistries.ITEMS.getValue(new ResourceLocation(ns, path));
        return it == null || it == Items.AIR ? null : it;
    }

    private static void give(List<ItemStack> out, Item it, int n) { if (it != null && n > 0) out.add(new ItemStack(it, n)); }

    /** Every enchantment that fits the stack (no curses), at its max level. */
    public static List<String> enchantAll(ItemStack st) {
        List<String> names = new ArrayList<>();
        for (Enchantment en : ForgeRegistries.ENCHANTMENTS) {
            if (en.isCurse() || !en.canEnchant(st)) continue;
            st.enchant(en, en.getMaxLevel());
            names.add(ForgeRegistries.ENCHANTMENTS.getKey(en).getPath() + " " + en.getMaxLevel());
        }
        return names;
    }

    /** A Goku armour piece from DragonMineZ (any of its Goku sets), fully enchanted. Empty without DragonMineZ. */
    public static ItemStack gokuArmor(Random r) {
        List<Item> all = new ArrayList<>();
        for (String g : GOKU) for (String p : PIECES) { Item it = item("dragonminez", g + "_" + p); if (it != null) all.add(it); }
        if (all.isEmpty()) return ItemStack.EMPTY;
        ItemStack st = new ItemStack(all.get(r.nextInt(all.size())));
        List<String> en = enchantAll(st);
        note("goku armour " + ForgeRegistries.ITEMS.getKey(st.getItem()) + " enchanted with " + en.size() + ": " + en);
        return st;
    }

    /** What it drops (only its final death, as a Super Saiyan 4). */
    public static List<ItemStack> loot(Random r) {
        List<ItemStack> out = new ArrayList<>();
        give(out, Items.DIAMOND, 3 + r.nextInt(4));
        give(out, Items.EMERALD, 4 + r.nextInt(6));
        give(out, Items.GOLDEN_APPLE, 1 + r.nextInt(2));
        if (r.nextFloat() < 0.15F) give(out, Items.ENCHANTED_GOLDEN_APPLE, 1);
        if (r.nextFloat() < 0.35F) give(out, Items.NETHERITE_SCRAP, 1 + r.nextInt(2));
        give(out, item(XenoWarriors.MOD_ID, "karma_potion"), 1 + r.nextInt(2));
        give(out, item(XenoWarriors.MOD_ID, "data_shard"), 1 + r.nextInt(3));
        give(out, item(XenoWarriors.MOD_ID, "karma_crystal"), 1 + r.nextInt(3));
        if (r.nextFloat() < 0.5F) give(out, item(XenoWarriors.MOD_ID, "aurora_gem"), 1);
        if (r.nextFloat() < 0.25F) give(out, item(XenoWarriors.MOD_ID, "old_world_fragment"), 1);
        give(out, item("dragonminez", "senzu_bean"), 1 + r.nextInt(3));
        if (r.nextFloat() < 0.4F) give(out, item("dragonminez", "might_tree_fruit"), 1);
        if (r.nextFloat() < 0.5F) give(out, item("dragonminez", "gete_ingot"), 1 + r.nextInt(2));
        if (r.nextFloat() < 0.3F) give(out, item("dragonminez", "ki_battery"), 1);
        if (r.nextFloat() < ARMOR_CHANCE) { ItemStack g = gokuArmor(r); if (!g.isEmpty()) out.add(g); }
        return out;
    }

    static void dropLoot(ZombieSaiyanEntity z, DamageSource src) {
        if (!(z.level() instanceof ServerLevel level)) return;
        List<ItemStack> l = loot(new Random(z.getRandom().nextLong()));
        StringBuilder sb = new StringBuilder();
        for (ItemStack st : l) {
            // (through the death drops, like its loot table's: LivingDropsEvent sees them - so a Room keeps them, and
            // other mods' drop handling works - where a plain new item entity was dropped by a Room as a copied block's)
            ItemEntity it = z.spawnAtLocation(st, 0.5F);
            if (it == null) continue;
            sb.append(st.getCount()).append("x").append(ForgeRegistries.ITEMS.getKey(st.getItem()).getPath()).append(st.isEnchanted() ? "(enchanted)" : "").append(' ');
        }
        note("defeated as " + ZombieSaiyanEntity.formName(z.form()) + " by " + (src.getEntity() == null ? "?" : src.getEntity().getName().getString()) + "; drops: " + sb.toString().trim());
    }
}

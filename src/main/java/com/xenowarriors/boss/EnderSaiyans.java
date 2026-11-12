package com.xenowarriors.boss;

import com.xenowarriors.XenoWarriors;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.*;

/**
 * The Ender Saiyan: registered always; it appears by itself (with DragonMineZ) rarely at night in the Overworld and
 * now and then in the End, never in the mode arenas. Spawn egg in the creative tab. Its loot: ender things, rare
 * things, and DragonMineZ goodies when that's installed.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class EnderSaiyans {
    private EnderSaiyans() {}

    public static final RegistryObject<EntityType<EnderSaiyanEntity>> ENDER_SAIYAN = com.xenowarriors.entity.ModEntities.ENTITY_TYPES.register(
            "ender_saiyan", () -> EntityType.Builder.<EnderSaiyanEntity>of(EnderSaiyanEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 2.9F).clientTrackingRange(10).build("ender_saiyan"));
    public static final RegistryObject<Item> SPAWN_EGG = com.xenowarriors.item.ModItems.ITEMS.register("ender_saiyan_spawn_egg",
            () -> new net.minecraftforge.common.ForgeSpawnEggItem(ENDER_SAIYAN, 0x161616, 0xB35AF2, new Item.Properties()));

    /** Chance per player per 30-second check: at night in the Overworld, and any time in the End (a bit more often). */
    public static float SPAWN_CHANCE = 1F / 60F, END_CHANCE = 1F / 30F;

    public static void init() {}

    public static boolean dmz() { return ModList.get() != null && ModList.get().isLoaded("dragonminez"); }

    public static final List<String> LOG = Collections.synchronizedList(new ArrayList<>());

    static void note(String s) {
        if (LOG.size() > 80) LOG.remove(0);
        LOG.add(s);
        XenoWarriors.LOGGER.info("XW ender saiyan: {}", s);
    }

    @Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Attrs {
        @SubscribeEvent
        public static void attributes(EntityAttributeCreationEvent event) {
            event.put(ENDER_SAIYAN.get(), EnderSaiyanEntity.createAttributes().build());
        }
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.level instanceof ServerLevel level) || level.getGameTime() % 600 != 287 || !dmz()) return;
        boolean end = level.dimension() == Level.END;
        if (!end && (level.dimension() != Level.OVERWORLD || !level.isNight())) return;
        if (com.xenowarriors.modes.ModeRules.arena(level) || !level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING)) return;
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator() || p.isCreative() || level.random.nextFloat() >= (end ? END_CHANCE : SPAWN_CHANCE)) continue;
            if (!level.getEntitiesOfClass(EnderSaiyanEntity.class, p.getBoundingBox().inflate(160)).isEmpty()) continue;
            spawnNear(p, 20, 36, false);
        }
    }

    public static EnderSaiyanEntity spawnNear(ServerPlayer p, double min, double max, boolean persistent) {
        ServerLevel level = p.serverLevel();
        var r = p.getRandom();
        for (int i = 0; i < 20; i++) {
            double a = r.nextDouble() * Math.PI * 2, d = min + r.nextDouble() * (max - min);
            int x = (int) Math.floor(p.getX() + Math.cos(a) * d), z = (int) Math.floor(p.getZ() + Math.sin(a) * d);
            if (!level.hasChunkAt(new BlockPos(x, 0, z))) continue;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos at = new BlockPos(x, y, z);
            if (y <= level.getMinBuildHeight() + 1 || !level.getBlockState(at.below()).isSolid() || !level.getFluidState(at).isEmpty()) continue;
            if (!level.noCollision(ENDER_SAIYAN.get().getAABB(x + 0.5, y, z + 0.5))) continue;
            EnderSaiyanEntity s = ENDER_SAIYAN.get().create(level);
            if (s == null) return null;
            s.moveTo(x + 0.5, y, z + 0.5, r.nextFloat() * 360F, 0F);
            s.finalizeSpawn(level, level.getCurrentDifficultyAt(at), persistent ? MobSpawnType.COMMAND : MobSpawnType.EVENT, null, null);
            if (persistent) s.setPersistenceRequired();
            level.addFreshEntity(s);
            p.displayClientMessage(Component.literal("The air hums and tears... a warped, terrible ki is close.").withStyle(ChatFormatting.DARK_PURPLE), false);
            note("spawned at " + at.toShortString());
            return s;
        }
        return null;
    }

    private static Item item(String ns, String path) {
        Item it = ForgeRegistries.ITEMS.getValue(new ResourceLocation(ns, path));
        return it == null || it == Items.AIR ? null : it;
    }

    private static void give(List<ItemStack> out, Item it, int n) { if (it != null && n > 0) out.add(new ItemStack(it, n)); }

    /** What it drops (only its final death, in its Void Form). */
    public static List<ItemStack> loot(Random r) {
        List<ItemStack> out = new ArrayList<>();
        give(out, Items.ENDER_PEARL, 6 + r.nextInt(8));
        give(out, Items.ENDER_EYE, 2 + r.nextInt(3));
        give(out, Items.DIAMOND, 3 + r.nextInt(4));
        give(out, Items.CHORUS_FRUIT, 4 + r.nextInt(6));
        if (r.nextFloat() < 0.35F) give(out, Items.SHULKER_SHELL, 1 + r.nextInt(2));
        if (r.nextFloat() < 0.25F) give(out, Items.ELYTRA, 1);
        if (r.nextFloat() < 0.3F) give(out, Items.NETHERITE_SCRAP, 1 + r.nextInt(2));
        give(out, item(XenoWarriors.MOD_ID, "karma_potion"), 1 + r.nextInt(2));
        give(out, item(XenoWarriors.MOD_ID, "karma_crystal"), 1 + r.nextInt(3));
        if (r.nextFloat() < 0.4F) give(out, item(XenoWarriors.MOD_ID, "old_world_fragment"), 1);
        give(out, item("dragonminez", "senzu_bean"), 1 + r.nextInt(3));
        if (r.nextFloat() < 0.4F) give(out, item("dragonminez", "might_tree_fruit"), 1);
        if (r.nextFloat() < 0.3F) give(out, item("dragonminez", "ki_battery"), 1);
        return out;
    }

    static void dropLoot(EnderSaiyanEntity s) {
        if (!(s.level() instanceof ServerLevel)) return;
        StringBuilder sb = new StringBuilder();
        for (ItemStack st : loot(new Random(s.getRandom().nextLong()))) {
            if (s.spawnAtLocation(st, 0.5F) != null) sb.append(st.getCount()).append("x").append(ForgeRegistries.ITEMS.getKey(st.getItem()).getPath()).append(' ');
        }
        note("defeated in its Void Form; drops: " + sb.toString().trim());
    }
}

package com.xenowarriors.gov;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.character.ModCapabilities;
import com.xenowarriors.karma.KarmaLevel;
import com.xenowarriors.network.KarmaReadingPacket;
import com.xenowarriors.network.ModNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

/**
 * The Government's Karma Scanners: visors worn like a helmet. Look at anyone and it takes the Karma Level. Point one at
 * someone far too strong for its tier and it overloads and blows apart (the Omni never does). Yuri (hacking) and Ultima
 * (her sensors) take readings without one, and never break.
 * Tiers: Karma Scanner (50,000, 48 blocks), Advanced (5,000,000, 64), Elite (500,000,000, 96), Omni (no limit, 128).
 * Each can be dyed (crafting grid, like leather): the lens and lights take the colour, the frame shows the tier.
 * Gov guards wear them too (GovScanning).
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class KarmaScanner {
    /** The basic visor overloads above this. */
    public static final long CAPACITY = 50_000;
    /** A tier with this capacity never overloads. */
    public static final long UNLIMITED = Long.MAX_VALUE;
    public static final int VISOR = 0, YURI = 1, ULTIMA = 2, TECHNICA = 3;
    private static final double RANGE = 48;
    private static final Map<UUID, Integer> LAST = new HashMap<>();
    public static void forget(UUID id) { LAST.remove(id); }

    /** The lens colours they come in (creative tab, Gov guards). */
    public static final int GREEN = 0x50FF80, RED = 0xFF4040, BLUE = 0x40A0FF, PURPLE = 0xB060FF, GOLD = 0xFFC830, BLACK = 0x34343C;
    public static final int[] COLORS = {GREEN, RED, BLUE, PURPLE, GOLD, BLACK};
    public static String colorName(int c) {
        return switch (c) { case GREEN -> "Green"; case RED -> "Red"; case BLUE -> "Blue"; case PURPLE -> "Purple"; case GOLD -> "Gold"; case BLACK -> "Black"; default -> "Custom"; };
    }

    private KarmaScanner() {}

    public enum Tier {
        BASIC("karma_scanner", "Karma Scanner", "GOV SCANNER", CAPACITY, 48, GREEN, 165, 1, 0F, 9),
        ADVANCED("advanced_karma_scanner", "Advanced Karma Scanner", "ADVANCED SCANNER", 5_000_000L, 64, BLUE, 330, 2, 0F, 10),
        ELITE("elite_karma_scanner", "Elite Karma Scanner", "ELITE SCANNER", 500_000_000L, 96, RED, 495, 3, 1F, 12),
        OMNI("omni_karma_scanner", "Omni Karma Scanner", "OMNI SCANNER", UNLIMITED, 128, PURPLE, 0, 3, 2F, 15);

        public final String id, title, hud;
        public final long capacity;
        public final double range;
        public final int color;
        public final ArmorMaterial material;

        Tier(String id, String title, String hud, long capacity, double range, int color, int durability, int defense, float toughness, int ench) {
            this.id = id; this.title = title; this.hud = hud; this.capacity = capacity; this.range = range; this.color = color;
            this.material = new ArmorMaterial() {
                @Override public int getDurabilityForType(ArmorItem.Type type) { return durability; }      // (0 = unbreakable: the Omni)
                @Override public int getDefenseForType(ArmorItem.Type type) { return defense; }
                @Override public int getEnchantmentValue() { return ench; }
                @Override public SoundEvent getEquipSound() { return SoundEvents.ARMOR_EQUIP_IRON; }
                @Override public Ingredient getRepairIngredient() { return Ingredient.of(com.xenowarriors.item.ModItems.MOD_CHIP.get()); }
                @Override public String getName() { return "xenowarriors:" + id; }
                @Override public float getToughness() { return toughness; }
                @Override public float getKnockbackResistance() { return 0F; }
            };
        }

        public boolean unlimited() { return capacity == UNLIMITED; }
        /** Would this tier blow apart reading `v`? */
        public boolean overloads(long v) { return !unlimited() && v > capacity; }
        public String capacityText() { return unlimited() ? "NO LIMIT" : KarmaLevel.fmt(capacity); }
    }

    /** (kept for older code) the basic visor's material. */
    public static final ArmorMaterial MATERIAL = Tier.BASIC.material;

    public static class Item extends ArmorItem implements net.minecraft.world.item.DyeableLeatherItem {
        public final Tier tier;
        public Item(Tier tier, Properties p) { super(tier.material, Type.HELMET, p); this.tier = tier; }

        /** Undyed, a tier wears its own lens colour (not leather brown). */
        @Override
        public int getColor(ItemStack stack) {
            var d = stack.getTagElement("display");
            return d != null && d.contains("color", 99) ? d.getInt("color") : tier.color;
        }

        @Override
        public void appendHoverText(ItemStack stack, Level level, List<Component> tip, TooltipFlag flag) {
            tip.add(Component.literal("Government tech: look at anyone for the Karma Level").withStyle(ChatFormatting.GREEN));
            if (tier.unlimited()) tip.add(Component.literal("Capacity: NO LIMIT - reads any Karma Level, never overloads").withStyle(ChatFormatting.LIGHT_PURPLE));
            else tip.add(Component.literal("Capacity: " + KarmaLevel.fmt(tier.capacity) + " (overloads above it)").withStyle(ChatFormatting.RED));
            tip.add(Component.literal("Range: " + (int) tier.range + " blocks").withStyle(ChatFormatting.AQUA));
            tip.add(Component.literal("Lens: " + colorName(getColor(stack)) + " (dye it in a crafting grid)").withStyle(ChatFormatting.GRAY));
        }

        @Override
        public String getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, String type) {
            return XenoWarriors.MOD_ID + ":textures/models/armor/" + tier.id + ("overlay".equals(type) ? "_visor_overlay" : "_visor") + ".png";
        }

        @Override
        public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientItemExtensions> consumer) {
            consumer.accept(new net.minecraftforge.client.extensions.common.IClientItemExtensions() {
                @Override
                public net.minecraft.client.model.HumanoidModel<?> getHumanoidArmorModel(LivingEntity e, ItemStack s, EquipmentSlot slot, net.minecraft.client.model.HumanoidModel<?> original) {
                    return com.xenowarriors.client.render.ScannerVisorModel.get(tier);
                }
            });
        }
    }

    /** A scanner of this tier with this lens colour (the tier's own colour = undyed). */
    public static ItemStack colored(net.minecraft.world.item.Item item, int color) {
        ItemStack s = new ItemStack(item);
        if (item instanceof Item k && color != k.tier.color) k.setColor(s, color);
        return s;
    }

    /** The scanner on this entity's head, or null. */
    public static Item worn(LivingEntity e) {
        return e.getItemBySlot(EquipmentSlot.HEAD).getItem() instanceof Item k ? k : null;
    }

    /** How far this player reads (their visor's tier; 48 for Yuri, Ultima and Technica's built-in ones). */
    public static double range(Player p) {
        Item k = worn(p);
        return k == null ? RANGE : Math.max(RANGE, k.tier.range);
    }

    /** How this player reads Karma: the visor, or built in (Yuri, Ultima); -1 = they can't. */
    public static int mode(Player p) {
        CharacterType t = ModCapabilities.getType(p);
        if (t == CharacterType.YURI) return YURI;
        if (t == CharacterType.ULTIMA) return ULTIMA;
        if (t == CharacterType.TECHNICA && p instanceof ServerPlayer sp && com.xenowarriors.karma.KarmaSensing.holding(sp)) return TECHNICA;   // (her goggles, while she senses)
        return p.getItemBySlot(EquipmentSlot.HEAD).getItem() instanceof Item ? VISOR : -1;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p) || p.tickCount % 5 != 0) return;
        int mode = mode(p);
        if (mode < 0 || p.isSpectator()) { if (LAST.remove(p.getUUID()) != null) ModNetwork.sendToPlayer(new KarmaReadingPacket(-1, 0, mode, false), p); return; }
        LivingEntity target = looked(p);
        int id = target == null ? -1 : target.getId();
        Integer last = LAST.get(p.getUUID());
        if (target == null) {
            if (last != null && last != -1) ModNetwork.sendToPlayer(new KarmaReadingPacket(-1, 0, mode, false), p);
            LAST.put(p.getUUID(), -1);
            return;
        }
        long v = KarmaLevel.of(target);
        Item visor = worn(p);
        boolean broke = mode == VISOR && visor != null && visor.tier.overloads(v);
        if (last == null || last != id || p.tickCount % 20 == 0 || broke) ModNetwork.sendToPlayer(new KarmaReadingPacket(id, v, mode, broke), p);
        LAST.put(p.getUUID(), id);
        if (broke) overload(p, target, v, visor.tier);
    }

    /** Too strong to read: the visor blows apart. */
    private static void overload(ServerPlayer p, LivingEntity target, long v, Tier tier) {
        LAST.remove(p.getUUID());
        blowUp(p, tier);
        p.sendSystemMessage(Component.literal("Your " + tier.title + " couldn't handle " + target.getName().getString() + "'s Karma Level (over "
                + KarmaLevel.fmt(tier.capacity) + ") - it blew apart!").withStyle(ChatFormatting.RED));
    }

    /** The visor on `who` blows apart: gone from their head, a bang, sparks, a sting. Bigger tiers, bigger bang. */
    public static void blowUp(LivingEntity who, Tier tier) {
        who.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        if (!(who.level() instanceof net.minecraft.server.level.ServerLevel l)) return;
        Vec3 eye = who.getEyePosition();
        int big = tier.ordinal();
        l.sendParticles(big >= 2 ? ParticleTypes.EXPLOSION_EMITTER : ParticleTypes.EXPLOSION, eye.x, eye.y, eye.z, 1, 0, 0, 0, 0);
        l.sendParticles(ParticleTypes.ELECTRIC_SPARK, eye.x, eye.y, eye.z, 25 + 15 * big, .3, .3, .3, .2);
        l.sendParticles(ParticleTypes.LARGE_SMOKE, eye.x, eye.y, eye.z, 6 + 4 * big, .2, .2, .2, .02);
        l.playSound(null, who.blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1F, 0.8F);
        l.playSound(null, who.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.4F + 0.2F * big, 1.6F - 0.15F * big);
        who.hurt(who.damageSources().generic(), 1F + big);
    }

    /** Who this player's looking at, within their reading range. */
    public static LivingEntity looked(ServerPlayer p) {
        double RANGE = range(p);
        Vec3 eye = p.getEyePosition(), look = p.getViewVector(1F);
        Vec3 end = eye.add(look.scale(RANGE));
        var block = p.level().clip(new net.minecraft.world.level.ClipContext(eye, end, net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, p));
        if (block.getType() != net.minecraft.world.phys.HitResult.Type.MISS) end = block.getLocation();
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(p, eye, end, new AABB(eye, end).inflate(1.5),
                e -> e instanceof LivingEntity le && le.isAlive() && !e.isSpectator() && !(e.isInvisible() && !e.isCurrentlyGlowing()) && e != p, RANGE * RANGE);
        Entity e = hit == null ? null : hit.getEntity();
        return e instanceof LivingEntity le ? le : null;
    }
}

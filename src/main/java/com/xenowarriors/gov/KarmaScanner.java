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
 * The Government's Karma Scanner: a visor worn like a helmet. Look at anyone and it takes the Karma Level. Point it at
 * someone far too strong and it overloads and blows apart. Yuri (hacking) and Ultima (her sensors) take readings
 * without one, and never break.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class KarmaScanner {
    /** The visor overloads above this. */
    public static final long CAPACITY = 50_000;
    public static final int VISOR = 0, YURI = 1, ULTIMA = 2;
    private static final double RANGE = 48;
    private static final Map<UUID, Integer> LAST = new HashMap<>();

    private KarmaScanner() {}

    public static final ArmorMaterial MATERIAL = new ArmorMaterial() {
        @Override public int getDurabilityForType(ArmorItem.Type type) { return 165; }
        @Override public int getDefenseForType(ArmorItem.Type type) { return 1; }
        @Override public int getEnchantmentValue() { return 9; }
        @Override public SoundEvent getEquipSound() { return SoundEvents.ARMOR_EQUIP_IRON; }
        @Override public Ingredient getRepairIngredient() { return Ingredient.of(com.xenowarriors.item.ModItems.MOD_CHIP.get()); }
        @Override public String getName() { return "xenowarriors:karma_scanner"; }
        @Override public float getToughness() { return 0F; }
        @Override public float getKnockbackResistance() { return 0F; }
    };

    public static class Item extends ArmorItem {
        public Item(Properties p) { super(MATERIAL, Type.HELMET, p); }
        @Override
        public void appendHoverText(ItemStack stack, Level level, List<Component> tip, TooltipFlag flag) {
            tip.add(Component.literal("Government tech: look at anyone for the Karma Level").withStyle(ChatFormatting.GREEN));
            tip.add(Component.literal("Overloads above " + KarmaLevel.fmt(CAPACITY)).withStyle(ChatFormatting.RED));
        }
    }

    /** How this player reads Karma: the visor, or built in (Yuri, Ultima); -1 = they can't. */
    public static int mode(Player p) {
        CharacterType t = ModCapabilities.getType(p);
        if (t == CharacterType.YURI) return YURI;
        if (t == CharacterType.ULTIMA) return ULTIMA;
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
        boolean broke = mode == VISOR && v > CAPACITY;
        if (last == null || last != id || p.tickCount % 20 == 0 || broke) ModNetwork.sendToPlayer(new KarmaReadingPacket(id, v, mode, broke), p);
        LAST.put(p.getUUID(), id);
        if (broke) overload(p, target, v);
    }

    /** Too strong to read: the visor blows apart. */
    private static void overload(ServerPlayer p, LivingEntity target, long v) {
        p.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        LAST.remove(p.getUUID());
        var l = p.serverLevel();
        Vec3 eye = p.getEyePosition();
        l.sendParticles(ParticleTypes.EXPLOSION, eye.x, eye.y, eye.z, 1, 0, 0, 0, 0);
        l.sendParticles(ParticleTypes.ELECTRIC_SPARK, eye.x, eye.y, eye.z, 25, .3, .3, .3, .2);
        l.playSound(null, p.blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1F, 0.8F);
        l.playSound(null, p.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.4F, 1.6F);
        p.hurt(p.damageSources().generic(), 1F);
        p.sendSystemMessage(Component.literal("Your Karma Scanner couldn't handle " + target.getName().getString() + "'s Karma Level (over "
                + KarmaLevel.fmt(CAPACITY) + ") - it blew apart!").withStyle(ChatFormatting.RED));
    }

    private static LivingEntity looked(ServerPlayer p) {
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

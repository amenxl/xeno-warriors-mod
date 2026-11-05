package com.xenowarriors.item;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.character.ModCapabilities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * A hamburger: heals 40% of max health and gives Regeneration II for 5 seconds. Then a wait before the next one -
 * 5 s for Mikiko, 3 s for Rhalix, 10 s for everyone else (shown as the item cooldown on the hotbar).
 * <p>Anyone crafts one (bread, cooked beef and something green). Yuri cooks them: sneak + right-click holding bread
 * or cooked beef and she turns one bread + one cooked beef into two burgers - no greens needed.
 * The tag {@code xenowarriors:hamburger_ingredients} lists what Yuri needs (for her bot to go and find).
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class HamburgerItem extends Item {
    public static final TagKey<Item> INGREDIENTS = TagKey.create(net.minecraft.core.registries.Registries.ITEM, XenoWarriors.id("hamburger_ingredients"));
    public static final float HEAL = 0.4F;
    public static final int REGEN_TICKS = 100, YURI_MAKES = 2;

    public HamburgerItem(Properties props) {
        super(props.food(new FoodProperties.Builder().nutrition(8).saturationMod(0.9F).alwaysEat()
                .effect(() -> new MobEffectInstance(MobEffects.REGENERATION, REGEN_TICKS, 1), 1F).build()));
    }

    /** Ticks before this character can eat another. */
    public static int cooldown(CharacterType t) {
        return t == CharacterType.MIKIKO ? 100 : t == CharacterType.RHALIX ? 60 : 200;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity e) {
        ItemStack r = super.finishUsingItem(stack, level, e);
        eaten(e);
        if (e instanceof Player p) {
            CharacterType t = level.isClientSide ? com.xenowarriors.you.You.clientType.apply(p) : ModCapabilities.getType(p);
            p.getCooldowns().addCooldown(this, cooldown(t));
        }
        return r;
    }

    /** The burger's healing (also for anything else that eats one, like a party bot). */
    public static void eaten(LivingEntity e) {
        if (e.level().isClientSide) return;
        e.heal(e.getMaxHealth() * HEAL);
        if (!e.hasEffect(MobEffects.REGENERATION)) e.addEffect(new MobEffectInstance(MobEffects.REGENERATION, REGEN_TICKS, 1));
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.literal("Heals 40% health + Regeneration II").withStyle(ChatFormatting.GREEN));
        tip.add(Component.literal("Wait: Mikiko 5s, Rhalix 3s, others 10s").withStyle(ChatFormatting.GRAY));
    }

    // ---------------------------------------------------------------- Yuri's cooking

    private static boolean bread(ItemStack s) { return s.is(Items.BREAD); }
    private static boolean beef(ItemStack s) { return s.is(Items.COOKED_BEEF); }

    /** Yuri cooks from a container (a player's inventory or a bot's bag): bread + cooked beef -> two burgers. Burgers made (0 if missing something). */
    public static int cook(Container inv) {
        int b = -1, m = -1;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (b < 0 && bread(s)) b = i;
            if (m < 0 && beef(s)) m = i;
        }
        if (b < 0 || m < 0) return 0;
        inv.removeItem(b, 1);
        inv.removeItem(m, 1);
        return YURI_MAKES;
    }

    private static boolean yuri(Player p) {
        CharacterType t = p.level().isClientSide ? com.xenowarriors.you.You.clientType.apply(p) : ModCapabilities.getType(p);
        return t == CharacterType.YURI;
    }

    @SubscribeEvent
    public static void onUse(PlayerInteractEvent.RightClickItem event) {
        Player p = event.getEntity();
        ItemStack held = event.getItemStack();
        if (!p.isShiftKeyDown() || !(bread(held) || beef(held)) || !yuri(p)) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(p.level().isClientSide));
        if (p.level().isClientSide || p.getCooldowns().isOnCooldown(held.getItem())) return;
        int made = cook(p.getInventory());
        if (made <= 0) {
            p.displayClientMessage(Component.literal("Yuri needs bread and cooked beef for burgers").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        ItemStack out = new ItemStack(ModItems.HAMBURGER.get(), made);
        if (!p.getInventory().add(out)) p.drop(out, false);
        p.getCooldowns().addCooldown(Items.BREAD, 10);
        p.getCooldowns().addCooldown(Items.COOKED_BEEF, 10);
        p.swing(event.getHand(), true);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.FURNACE_FIRE_CRACKLE, SoundSource.PLAYERS, 0.8F, 1.3F);
        if (p.level() instanceof ServerLevel sl) {
            var look = p.getLookAngle();
            sl.sendParticles(ParticleTypes.SMOKE, p.getX() + look.x * 0.6, p.getEyeY() - 0.4, p.getZ() + look.z * 0.6, 5, 0.1, 0.05, 0.1, 0.01);
            sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, p.getX() + look.x * 0.6, p.getEyeY() - 0.3, p.getZ() + look.z * 0.6, 4, 0.2, 0.1, 0.2, 0.01);
        }
        XenoWarriors.LOGGER.info("XW-BURGER {} cooked {} hamburgers", p.getName().getString(), made);
    }
}

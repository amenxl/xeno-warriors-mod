package com.xenowarriors.you;

import com.xenowarriors.item.ModItems;
import com.xenowarriors.ka.Element;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.RegistryObject;
import org.joml.Vector3f;

import java.util.List;

/** Phase 2's drops: Element Crystals (Elementalists) and Demon Essence (Vessels). */
public final class YouItems {
    private YouItems() {}
    public static void init() {}

    public static final RegistryObject<Item> ELEMENT_CRYSTAL = ModItems.ITEMS.register("element_crystal", () -> new CrystalItem(new Item.Properties().stacksTo(16).rarity(Rarity.RARE)));
    public static final RegistryObject<Item> DEMON_ESSENCE = ModItems.ITEMS.register("demon_essence", () -> new EssenceItem(new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON)));

    /** A crystal of this element. */
    public static ItemStack crystal(Element el) {
        ItemStack s = new ItemStack(ELEMENT_CRYSTAL.get());
        s.getOrCreateTag().putString("element", el.id());
        return s;
    }

    public static Element element(ItemStack s) { return s.hasTag() ? Element.byId(s.getTag().getString("element")) : null; }

    /** An Element Crystal: your own character takes it as a second element (its Bolt and Wave join your skills). */
    public static final class CrystalItem extends Item {
        CrystalItem(Properties p) { super(p); }

        @Override public Component getName(ItemStack s) { Element el = element(s); return Component.literal((el == null ? "Element" : el.title()) + " Crystal"); }

        @Override
        public void appendHoverText(ItemStack s, Level l, List<Component> tip, TooltipFlag f) {
            tip.add(Component.literal("Dropped by an Elementalist").withStyle(ChatFormatting.GRAY));
            tip.add(Component.literal("Your own character: use it to take this as a second element").withStyle(ChatFormatting.DARK_AQUA));
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player pl, InteractionHand hand) {
            ItemStack st = pl.getItemInHand(hand);
            if (level.isClientSide || !(pl instanceof ServerPlayer p)) return InteractionResultHolder.sidedSuccess(st, level.isClientSide);
            Element el = element(st);
            YouSlot s = You.slot(p);
            String no = el == null ? "This crystal is empty" : !com.xenowarriors.character.Seasons.unlocked(p.server, com.xenowarriors.foes.Foes.SEASON) ? "Season 3 content - locked" : s == null ? "Only your own characters can take a second element" : s.element2 != null ? s.name + " already has a second element (" + s.element2.title() + ")"
                    : s.element == el ? el.title() + " is already " + s.name + "'s element" : null;
            if (no != null) { p.displayClientMessage(Component.literal(no).withStyle(ChatFormatting.RED), true); return InteractionResultHolder.fail(st); }
            s.element2 = el;
            if (!p.isCreative()) st.shrink(1);
            YouPaths.changed(p);
            int c = el.color;
            p.serverLevel().sendParticles(new DustParticleOptions(new Vector3f((c >> 16 & 255) / 255F, (c >> 8 & 255) / 255F, (c & 255) / 255F), 1.6F), p.getX(), p.getY() + 1, p.getZ(), 50, 0.6, 0.9, 0.6, 0.02);
            p.level().playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1F, 1F);
            p.sendSystemMessage(Component.literal(s.name + " takes " + el.title() + " as a second element! New skills: " + el.title() + " Bolt and " + el.title() + " Wave. "
                    + "On the toggle wheel, \"Bolt: second element\" makes your first Bolt cast it too.").withStyle(st2 -> st2.withColor(c)));
            return InteractionResultHolder.consume(st);
        }
    }

    /** Demon Essence: a demon of yours feeds on it (+5 Devil's Hunger). */
    public static final class EssenceItem extends Item {
        EssenceItem(Properties p) { super(p); }

        @Override
        public void appendHoverText(ItemStack s, Level l, List<Component> tip, TooltipFlag f) {
            tip.add(Component.literal("What's left of a Vessel's demon").withStyle(ChatFormatting.DARK_RED));
            tip.add(Component.literal("Your own demon (Lv 50+): +5 Devil's Hunger").withStyle(ChatFormatting.GRAY));
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player pl, InteractionHand hand) {
            ItemStack st = pl.getItemInHand(hand);
            if (level.isClientSide || !(pl instanceof ServerPlayer p)) return InteractionResultHolder.sidedSuccess(st, level.isClientSide);
            YouSlot s = You.slot(p);
            String no = s == null || !s.demon() ? "Only a demon of your own can feed on it" : s.devil ? s.name + " is a Devil already" : !YouDevil.hungry(p, s) ? "The hunger wakes at Character Level " + YouDevil.LEVEL
                    : s.hunger >= YouDevil.HUNGER_MAX ? "The hunger is full - wait for night" : null;
            if (no != null) { p.displayClientMessage(Component.literal(no).withStyle(ChatFormatting.RED), true); return InteractionResultHolder.fail(st); }
            s.hunger = Math.min(YouDevil.HUNGER_MAX, s.hunger + 5);
            if (!p.isCreative()) st.shrink(1);
            com.xenowarriors.character.CharacterManager.syncToTrackingAndSelf(p);
            p.level().playSound(null, p.blockPosition(), SoundEvents.WITHER_AMBIENT, SoundSource.PLAYERS, 0.5F, 1.4F);
            p.displayClientMessage(Component.literal("Devil's Hunger " + s.hunger + " / " + YouDevil.HUNGER_MAX).withStyle(ChatFormatting.DARK_RED), true);
            if (s.hunger >= YouDevil.HUNGER_MAX) p.sendSystemMessage(Component.literal("Your Devil's Hunger is full. When night falls, your shadow will come for you.").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
            return InteractionResultHolder.consume(st);
        }
    }
}

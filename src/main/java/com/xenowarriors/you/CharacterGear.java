package com.xenowarriors.you;

import com.xenowarriors.bot.PartyBotEntity;
import com.xenowarriors.character.CharacterType;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Characters' gear for your own (You) characters: a character you've a bond of {@value #ARMOR} with gives you their
 * armor when you ask (their bot's menu) - four diamond-strong pieces that only a character you made can wear, which
 * make you look like you're in their clothes (their hair with the helmet) - and their outfits, a look you can put on
 * over any armor (Your Own Warriors > LOOK). Ask again at bond {@value #ENCHANTED} for an enchanted set, and at
 * {@value #MAX} for one with every enchantment it can take. With Season 2 / 3 unlocked, their outfits from those
 * seasons come too.
 */
public final class CharacterGear {
    private CharacterGear() {}

    public static final int ARMOR = 50, ENCHANTED = 75, MAX = 100;
    private static final String GIVEN = "xw_char_gear", LOOKS = "xw_char_looks";
    /** (Tests) sets handed out, by tier (1 plain, 2 enchanted, 3 every enchantment). */
    public static final int[] given = new int[4];

    /** The tier a bond reaches: 0 none, 1 armor + outfits, 2 enchanted, 3 every enchantment. */
    public static int tier(int bond) { return bond >= MAX ? 3 : bond >= ENCHANTED ? 2 : bond >= ARMOR ? 1 : 0; }

    private static CompoundTag keep(Player p) {
        CompoundTag root = p.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG)) root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        return root.getCompound(Player.PERSISTED_NBT_TAG);
    }

    /** Outfit looks you've been given ("mikiko", "mikiko_origins1", "ava_demon"...). */
    public static List<String> looks(Player p) {
        List<String> out = new ArrayList<>();
        ListTag l = keep(p).getList(LOOKS, 8);
        for (int i = 0; i < l.size(); i++) out.add(l.getString(i));
        return out;
    }

    private static void addLook(Player p, String look) {
        ListTag l = keep(p).getList(LOOKS, 8);
        for (int i = 0; i < l.size(); i++) if (l.getString(i).equals(look)) return;
        l.add(StringTag.valueOf(look));
        keep(p).put(LOOKS, l);
    }

    /** A character's main look's id (Ava and Persephone: always their demon look). */
    public static String mainLook(CharacterType t) {
        if ((t == CharacterType.AVA || t == CharacterType.PERSEPHONE) && t.formOutfit() > 0) return t.getId() + "_" + t.outfitKeys()[t.formOutfit()];
        return t.getId();
    }

    /** The looks a character can give: their main one, and their outfits from seasons that are unlocked. */
    public static Map<String, String> giveable(ServerPlayer p, CharacterType t) {
        Map<String, String> out = new LinkedHashMap<>();
        String[] keys = t.outfitKeys(), names = t.outfitNames();
        out.put(mainLook(t), t.getDisplayName().getString() + " (" + (names.length > 0 ? names[0] : "Default") + ")");
        for (int i = 1; i < names.length && i < keys.length; i++) {
            if (!com.xenowarriors.character.Seasons.unlocked(p.server, t.outfitSeason(i))) continue;
            out.put(t.getId() + "_" + keys[i], t.getDisplayName().getString() + " (" + names[i] + ")");
        }
        return out;
    }

    /** The character a look belongs to (null: none of theirs). */
    public static CharacterType ownerOf(String look) {
        if (look == null || look.isEmpty()) return null;
        for (CharacterType t : CharacterType.values()) {
            if (t == CharacterType.NONE || t == CharacterType.YOU) continue;
            if (look.equals(t.getId()) || look.startsWith(t.getId() + "_")) return t;
        }
        return null;
    }

    /** You ask this bot (from its menu) for its character's gear. */
    public static void ask(ServerPlayer p, PartyBotEntity b) {
        CharacterType t = b.character();
        String name = t.getDisplayName().getString();
        if (t == CharacterType.YOU || t == CharacterType.NONE) { p.displayClientMessage(Component.literal("Ask one of the show's characters for their gear.").withStyle(ChatFormatting.GRAY), true); return; }
        if (com.xenowarriors.character.ModCapabilities.getType(p) != CharacterType.YOU) {
            p.displayClientMessage(Component.empty().append(b.label()).append(": \"My gear? It'd only fit someone who made themselves - play your own character and ask me again.\"").withStyle(ChatFormatting.GRAY), false);
            return;
        }
        int bond = com.xenowarriors.party.Bonds.level(p, b.charKey());
        int tier = tier(bond);
        if (tier == 0) { p.displayClientMessage(Component.empty().append(b.label()).append(": \"Get to know me better first.\" (bond " + bond + "/" + ARMOR + ")").withStyle(ChatFormatting.GRAY), false); return; }
        CompoundTag g = keep(p).getCompound(GIVEN);
        int had = g.getInt(t.getId());
        // the outfits always (new seasons' ones turn up as they're unlocked)
        int newLooks = 0;
        List<String> have = looks(p);
        for (var en : giveable(p, t).entrySet()) if (!have.contains(en.getKey())) { addLook(p, en.getKey()); newLooks++; }
        if (tier <= had) {
            String next = had < 2 ? " Get closer (bond " + ENCHANTED + ") and I'll enchant it." : had < 3 ? " Max out our bond and I'll put everything into it." : "";
            p.displayClientMessage(Component.empty().append(b.label()).append(": \"" + (newLooks > 0 ? "Here - a new outfit to try. " : "") + "You've already got my armor." + next + "\"").withStyle(ChatFormatting.AQUA), false);
            if (newLooks > 0) YouSlots.send(p, false);
            return;
        }
        for (EquipmentSlot s : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack it = piece(t, s, tier);
            if (!p.getInventory().add(it)) p.drop(it, false);
        }
        g.putInt(t.getId(), tier);
        keep(p).put(GIVEN, g);
        given[tier]++;
        b.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        p.serverLevel().sendParticles(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER, p.getX(), p.getY() + 1, p.getZ(), 12, 0.4, 0.5, 0.4, 0.05);
        p.level().playSound(null, p.blockPosition(), net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_DIAMOND, net.minecraft.sounds.SoundSource.PLAYERS, 1F, 1F);
        String what = tier == 3 ? "my armor - with everything I could put into it" : tier == 2 ? "my armor, enchanted" : "my armor";
        p.sendSystemMessage(Component.empty().append(b.label()).append(": \"Here - " + what + ". And my outfit" + (giveable(p, t).size() > 1 ? "s" : "") + ", so you can look the part whatever you wear.\"").withStyle(ChatFormatting.GOLD));
        p.sendSystemMessage(Component.literal("Put on an outfit: Character Select > Your own > OUTFIT on your character's card.").withStyle(ChatFormatting.GRAY));
        YouSlots.send(p, false);
    }

    /** A piece of a character's armor: tier 1 plain, 2 enchanted, 3 every enchantment it takes. */
    public static ItemStack piece(CharacterType t, EquipmentSlot slot, int tier) {
        ItemStack it = new ItemStack(switch (slot) {
            case HEAD -> com.xenowarriors.item.ModItems.CHAR_HELMET.get();
            case CHEST -> com.xenowarriors.item.ModItems.CHAR_CHESTPLATE.get();
            case LEGS -> com.xenowarriors.item.ModItems.CHAR_LEGGINGS.get();
            default -> com.xenowarriors.item.ModItems.CHAR_BOOTS.get();
        });
        CharacterArmorItem.setLook(it, mainLook(t));
        Map<Enchantment, Integer> e = new LinkedHashMap<>();
        if (tier == 2) {
            e.put(Enchantments.ALL_DAMAGE_PROTECTION, 3);
            e.put(Enchantments.UNBREAKING, 2);
        } else if (tier >= 3) {
            e.put(Enchantments.ALL_DAMAGE_PROTECTION, 4);
            e.put(Enchantments.UNBREAKING, 3);
            e.put(Enchantments.MENDING, 1);
            e.put(Enchantments.THORNS, 3);
            switch (slot) {
                case HEAD -> { e.put(Enchantments.RESPIRATION, 3); e.put(Enchantments.AQUA_AFFINITY, 1); }
                case LEGS -> e.put(Enchantments.SWIFT_SNEAK, 3);
                case FEET -> { e.put(Enchantments.FALL_PROTECTION, 4); e.put(Enchantments.DEPTH_STRIDER, 3); e.put(Enchantments.SOUL_SPEED, 3); }
                default -> {}
            }
        }
        if (!e.isEmpty()) net.minecraft.world.item.enchantment.EnchantmentHelper.setEnchantments(e, it);
        return it;
    }

    /** (Tests) as if this tier were handed out already (or none: 0). */
    public static void setGivenForTest(Player p, CharacterType t, int tier) { CompoundTag g = keep(p).getCompound(GIVEN); g.putInt(t.getId(), tier); keep(p).put(GIVEN, g); }

    /** The armor item: diamond-strong, one of four pieces; it carries whose look it has. Only your own characters can wear it. */
    public static class CharacterArmorItem extends ArmorItem {
        public static final net.minecraft.world.item.ArmorMaterial MATERIAL = new net.minecraft.world.item.ArmorMaterial() {
            @Override public int getDurabilityForType(Type type) { return new int[]{429, 495, 528, 363}[3 - type.ordinal()]; }
            @Override public int getDefenseForType(Type type) { return switch (type) { case HELMET -> 3; case CHESTPLATE -> 8; case LEGGINGS -> 6; default -> 3; }; }
            @Override public int getEnchantmentValue() { return 15; }
            @Override public net.minecraft.sounds.SoundEvent getEquipSound() { return net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_DIAMOND; }
            @Override public net.minecraft.world.item.crafting.Ingredient getRepairIngredient() { return net.minecraft.world.item.crafting.Ingredient.of(net.minecraft.world.item.Items.DIAMOND); }
            @Override public String getName() { return "xenowarriors:character"; }   // (its armor texture is see-through: the look is the character's clothes, CharacterDetailLayer)
            @Override public float getToughness() { return 2F; }
            @Override public float getKnockbackResistance() { return 0F; }
        };
        private static final String LOOK = "xw_look";

        public CharacterArmorItem(Type type, Properties props) { super(MATERIAL, type, props); }

        public static void setLook(ItemStack s, String look) { s.getOrCreateTag().putString(LOOK, look); }
        public static String look(ItemStack s) { return s.getTag() == null ? "" : s.getTag().getString(LOOK); }

        /** Only a character you made (or a bot of one) can wear it. */
        public static boolean wearer(net.minecraft.world.entity.Entity e) {
            if (e instanceof PartyBotEntity b) return b.character() == CharacterType.YOU;
            if (e instanceof Player p) return p.level().isClientSide ? com.xenowarriors.character.ModCapabilities.getType(p) == CharacterType.YOU || clientYou(p)
                    : com.xenowarriors.character.ModCapabilities.getType(p) == CharacterType.YOU;
            return false;
        }

        private static boolean clientYou(Player p) {
            try { return net.minecraftforge.fml.DistExecutor.unsafeCallWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () -> com.xenowarriors.client.ClientCharacterData.get(p) == CharacterType.YOU); }
            catch (Throwable t) { return false; }
        }

        @Override
        public boolean canEquip(ItemStack stack, EquipmentSlot slot, net.minecraft.world.entity.Entity e) {
            return slot == getEquipmentSlot() && wearer(e);
        }

        @Override
        public Component getName(ItemStack s) {
            CharacterType t = ownerOf(look(s));
            String piece = switch (getType()) { case HELMET -> "Helmet"; case CHESTPLATE -> "Chestplate"; case LEGGINGS -> "Leggings"; default -> "Boots"; };
            return Component.literal(t == null ? "Character " + piece : t.getDisplayName().getString() + "'s " + piece);
        }

        @Override
        public void appendHoverText(ItemStack s, net.minecraft.world.level.Level level, List<Component> tip, net.minecraft.world.item.TooltipFlag flag) {
            tip.add(Component.literal("Only a character you made yourself can wear it").withStyle(ChatFormatting.DARK_AQUA));
            tip.add(Component.literal("You look like you're in their clothes" + (getType() == Type.HELMET ? " (and their hair)" : "")).withStyle(ChatFormatting.GRAY));
        }

        /** Its colour in your inventory: the character's. */
        public static int colour(ItemStack s) {
            CharacterType t = ownerOf(look(s));
            return t == null ? 0xFFFFFF : t.getColor() & 0xFFFFFF;
        }
    }

    /** Every few seconds: armor a character can't wear (not one you made - you switched) comes off into the bag. */
    @net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = com.xenowarriors.XenoWarriors.MOD_ID)
    public static final class Strip {
        @net.minecraftforge.eventbus.api.SubscribeEvent
        public static void onTick(net.minecraftforge.event.TickEvent.PlayerTickEvent e) {
            if (e.phase != net.minecraftforge.event.TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p.tickCount % 40 != 7) return;
            if (com.xenowarriors.character.ModCapabilities.getType(p) == CharacterType.YOU) return;
            for (EquipmentSlot s : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                ItemStack it = p.getItemBySlot(s);
                if (!(it.getItem() instanceof CharacterArmorItem)) continue;
                p.setItemSlot(s, ItemStack.EMPTY);
                if (!p.getInventory().add(it)) p.drop(it, false);
            }
        }
    }
}

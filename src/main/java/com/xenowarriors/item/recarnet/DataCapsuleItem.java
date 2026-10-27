package com.xenowarriors.item.recarnet;

import com.xenowarriors.character.CharacterType;
import com.xenowarriors.character.ModCapabilities;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.UUID;

/** A Data Capsule: Ultima's power-ups. Only he can install one: each adds 2 max hearts and 4% attack (up to 5). */
@Mod.EventBusSubscriber(modid = com.xenowarriors.XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class DataCapsuleItem extends Item {
    public static final int MAX = 5;
    private static final String KEY = "xw_data_capsules";
    private static final UUID HP = UUID.fromString("c4a1e7b2-55d3-4f10-9a2b-7e3d1c0f9a11"), ATK = UUID.fromString("c4a1e7b2-55d3-4f10-9a2b-7e3d1c0f9a12");

    public DataCapsuleItem(Properties p) { super(p); }

    private static CompoundTag root(Player p) { return p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG); }
    public static int installed(Player p) { return root(p).getInt(KEY); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        ItemStack st = p.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.success(st);
        if (ModCapabilities.getType(p) != CharacterType.ULTIMA) { p.displayClientMessage(Component.literal("Only Ultima can read this data.").withStyle(ChatFormatting.GRAY), true); return InteractionResultHolder.fail(st); }
        int n = installed(p);
        if (n >= MAX) { p.displayClientMessage(Component.literal("All " + MAX + " capsules installed.").withStyle(ChatFormatting.GREEN), true); return InteractionResultHolder.fail(st); }
        CompoundTag r = root(p); r.putInt(KEY, n + 1); p.getPersistentData().put(Player.PERSISTED_NBT_TAG, r);
        apply((ServerPlayer) p);
        if (!p.getAbilities().instabuild) st.shrink(1);
        p.displayClientMessage(Component.literal("Data capsule installed (" + (n + 1) + "/" + MAX + ")").withStyle(ChatFormatting.GREEN), true);
        level.playSound(null, p.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1F, 1.6F);
        return InteractionResultHolder.consume(st);
    }

    static void apply(ServerPlayer p) {
        int n = ModCapabilities.getType(p) == CharacterType.ULTIMA ? installed(p) : 0;
        var hp = p.getAttribute(Attributes.MAX_HEALTH); var atk = p.getAttribute(Attributes.ATTACK_DAMAGE);
        if (hp != null) { hp.removeModifier(HP); if (n > 0) hp.addTransientModifier(new AttributeModifier(HP, "Data capsules", 4.0 * n, AttributeModifier.Operation.ADDITION)); }
        if (atk != null) { atk.removeModifier(ATK); if (n > 0) atk.addTransientModifier(new AttributeModifier(ATK, "Data capsules", 0.04 * n, AttributeModifier.Operation.MULTIPLY_BASE)); }
    }

    @SubscribeEvent public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) { if (e.getEntity() instanceof ServerPlayer p) apply(p); }
    @SubscribeEvent public static void onRespawn(PlayerEvent.PlayerRespawnEvent e) { if (e.getEntity() instanceof ServerPlayer p) apply(p); }

    @Override
    public void appendHoverText(ItemStack s, Level l, List<Component> tip, TooltipFlag f) {
        tip.add(Component.literal("Ultima only: +2 max hearts, +4% attack each (up to " + MAX + ")").withStyle(ChatFormatting.GRAY));
    }
}

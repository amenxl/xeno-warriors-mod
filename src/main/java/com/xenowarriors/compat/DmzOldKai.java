package com.xenowarriors.compat;

import com.xenowarriors.XenoWarriors;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

/**
 * With DragonMineZ: Elder Kai unleashes your potential (DragonMineZ's "ultimate" skill) far more easily. DragonMineZ
 * asks for Potential Unlock at level 10 and a very good alignment (over 61); here talking to him is enough with
 * Potential Unlock at level {@link #NEED_UNLOCK} or more and an alignment of at least {@link #NEED_ALIGNMENT}.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DmzOldKai {
    private DmzOldKai() {}

    public static final int NEED_UNLOCK = 3, NEED_ALIGNMENT = 40;
    /** (Tests) potentials unleashed. */
    public static int unleashed;

    private static Object m(Object o, String name, Object... a) throws Exception { return DmzBotQuests.m(o, name, a); }

    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent.EntityInteract e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || p instanceof FakePlayer || e.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND) return;
        if (ModList.get() == null || !ModList.get().isLoaded("dragonminez")) return;
        if (!"oldkai".equals(DmzBotTechniques.masterName(e.getTarget()))) return;
        unleash(p);
    }

    /** Unleashes this player's potential if they qualify. Returns what happened. */
    public static String unleash(ServerPlayer p) {
        try {
            Object sd = DmzPerCharacter.live(p);
            if (sd == null) return "no character";
            Object skills = m(sd, "getSkills");
            if (((Number) m(skills, "getSkillLevel", "ultimate")).intValue() > 0) return "already";
            int unlock = ((Number) m(skills, "getSkillLevel", "potentialunlock")).intValue();
            int align = ((Number) m(m(sd, "getResources"), "getAlignment")).intValue();
            if (unlock < NEED_UNLOCK || align < NEED_ALIGNMENT) {
                p.sendSystemMessage(Component.literal("Elder Kai: \"Come back with Potential Unlock at level " + NEED_UNLOCK + " (you have " + unlock
                        + ") and a good heart - alignment " + NEED_ALIGNMENT + " or more (you have " + align + ").\"").withStyle(ChatFormatting.LIGHT_PURPLE));
                return "not yet " + unlock + "/" + align;
            }
            m(skills, "setSkillLevel", "ultimate", 1);
            DmzPerCharacter.syncStats(p);
            unleashed++;
            p.sendSystemMessage(Component.literal("Elder Kai unleashes your hidden potential!").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
            p.level().playSound(null, p.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.8F, 1.2F);
            return "unleashed";
        } catch (Throwable t) {
            XenoWarriors.LOGGER.warn("Elder Kai couldn't unleash a player's potential", t);
            return "error " + t;
        }
    }
}

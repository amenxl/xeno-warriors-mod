package com.xenowarriors.skill;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.ModCapabilities;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Keeps each player's skills screen and HUD up to date, and announces newly unlocked skills. */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LoadoutSync {
    private static final Map<UUID, String> LAST = new HashMap<>();

    private LoadoutSync() {}

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p) || p.tickCount % 20 != 0) return;
        var type = ModCapabilities.getType(p);
        int level = com.xenowarriors.stats.Stats.characterLevel(p);
        String key = type + ":" + level + ":" + com.xenowarriors.character.Seasons.highest(p.server) + ":" + java.util.Arrays.toString(p.getCapability(ModCapabilities.PLAYER_CHARACTER).map(d -> d.loadout(type)).orElse(new int[0]));
        String old = LAST.put(p.getUUID(), key);
        if (key.equals(old)) return;
        SkillManager.syncLoadout(p);
        // level went up: tell them about anything they just unlocked
        if (old != null && old.startsWith(type + ":")) {
            int was = Integer.parseInt(old.split(":")[1]);
            int season = com.xenowarriors.character.Seasons.highest(p.server);
            for (SkillPool.Entry e : SkillPool.of(type))
                if (e.unlockLevel() > was && e.unlockLevel() <= level && season >= e.season())
                    p.sendSystemMessage(Component.literal("New skill unlocked: ").withStyle(net.minecraft.ChatFormatting.GOLD)
                            .append(e.skill().getName().copy().withStyle(net.minecraft.ChatFormatting.YELLOW))
                            .append(Component.literal(" - equip it in the Skills tab (M)").withStyle(net.minecraft.ChatFormatting.GRAY)));
        }
    }

    @SubscribeEvent
    public static void onLogout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        LAST.remove(event.getEntity().getUUID());
    }
}

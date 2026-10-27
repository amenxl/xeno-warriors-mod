package com.xenowarriors.soul;

import com.xenowarriors.character.CharacterType;
import com.xenowarriors.character.ModCapabilities;
import com.xenowarriors.kaeru.KaeruSoul;
import com.xenowarriors.skill.Skill;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Soul Splitter: split your soul off to fight beside you (see {@link SoulSplit}). Press again to call it back early. */
public class SoulSplitterSkill implements Skill {
    @Override public String getId() { return "soul_splitter"; }
    @Override public int getCooldownTicks() { return 45 * 20; }
    @Override public int getKarmaCost() { return 25; }

    @Override
    public boolean tryRecast(ServerPlayer p) {
        if (SoulSplit.active(p)) { SoulSplit.end(p, "You called your soul back"); return true; }
        return false;
    }

    @Override
    public boolean use(ServerPlayer p) {
        if (ModCapabilities.getType(p) == CharacterType.KAERU && KaeruSoul.state(p) == KaeruSoul.SOUL) {
            p.displayClientMessage(Component.literal("You're already a soul outside the Hekublade").withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        return SoulSplit.start(p);
    }
}

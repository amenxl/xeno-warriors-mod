package com.xenowarriors.you;

import com.xenowarriors.ka.Element;
import com.xenowarriors.toggle.Toggles;
import net.minecraft.server.level.ServerPlayer;

/** A second element (from an Element Crystal): the wheel's "Bolt: second element" makes the first Bolt cast it. */
public final class YouElements {
    private YouElements() {}

    public static Element boltElement(ServerPlayer p, Element first) {
        YouSlot s = You.slot(p);
        return s != null && s.element2 != null && Toggles.enabled(p, Toggles.Entry.SWAP_ELEMENT) ? s.element2 : first;
    }
}

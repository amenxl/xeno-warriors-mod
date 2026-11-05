package com.xenowarriors.client;

import net.minecraft.world.entity.player.Player;

/**
 * Mouse buttons that belong to us even in Epic Fight's battle mode, where its swing / guard / dodge would otherwise
 * take them first (it cancels Minecraft's attack before any click event fires): a summoned gun, a clash (every click
 * pushes), being bound by wires (every click pulls), Rhalix's wires (a click whips, right-click binds), a phone call,
 * and being knocked out (no fighting at all). Read by the Epic Fight mixins in mixin/compat.
 */
public final class OurClicks {
    private OurClicks() {}

    /** (tests) clicks handed back to us. */
    public static int attacksKept, guardsKept, dodgesKept;

    /** Left click: ours (no Epic Fight attack). */
    public static boolean attack(Player p) {
        if (p == null) return false;
        if (com.xenowarriors.client.forms.GunClient.held(p) != null) { com.xenowarriors.client.forms.GunClient.efSwingsBlocked++; return true; }
        boolean ours = busy(p) || p.getMainHandItem().getItem() instanceof com.xenowarriors.forms.WiresItem
                || com.xenowarriors.item.PhoneItem.onCall(p);
        if (ours) attacksKept++;
        return ours;
    }

    /** Right click: ours (no Epic Fight guard). */
    public static boolean guard(Player p) {
        if (p == null) return false;
        if (com.xenowarriors.client.forms.GunClient.held(p) != null) { com.xenowarriors.client.forms.GunClient.efGuardsBlocked++; return true; }
        boolean ours = busy(p) || p.getMainHandItem().getItem() instanceof com.xenowarriors.forms.WiresItem;
        if (ours) guardsKept++;
        return ours;
    }

    /** No Epic Fight dodge / step either. */
    public static boolean dodge(Player p) {
        boolean ours = p != null && busy(p);
        if (ours) dodgesKept++;
        return ours;
    }

    /** Knocked out, in a clash, or held in a wire bind. */
    private static boolean busy(Player p) {
        return ClashClient.active() || com.xenowarriors.client.party.PartyClient.knockedOut(p)
                || com.xenowarriors.client.forms.FormsFx.boundBy(p.getId()) >= 0;
    }
}

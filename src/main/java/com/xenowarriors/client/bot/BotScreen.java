package com.xenowarriors.client.bot;

import com.xenowarriors.bot.PartyBotEntity;
import com.xenowarriors.bot.PartyBots;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.network.BotActionPacket;
import com.xenowarriors.network.ModNetwork;
import com.xenowarriors.network.OpenBotMenuPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;

/** A bot teammate's menu: its bag, its pets (summon them), fly me, spar, its mode, and dismiss. */
public class BotScreen extends Screen {
    private static final int W = 236;
    private static final boolean DMZ = com.xenowarriors.client.compat.DmzMenu.available();
    private static final int H0 = DMZ ? 258 : 236;
    private int H = H0;            // (room for the two hint lines under the buttons)
    /** Button spacing and height (smaller on a short window). */
    private int st = 22, bh = 20;
    private final OpenBotMenuPacket m;
    private final CharacterType type;
    private int mode;
    private boolean showPets;

    public BotScreen(OpenBotMenuPacket m) {
        super(Component.literal(com.xenowarriors.character.PlayerCharacter.typeOfKey(m.character()).getDisplayName().getString()));
        this.m = m;
        this.type = com.xenowarriors.character.PlayerCharacter.typeOfKey(m.character());
        this.mode = m.mode();
        if (m.radar()) H = H0 + 22;
    }

    private int x0() { return (width - W) / 2; }
    private int y0() { return (height - H) / 2; }

    private void send(int action, int arg) { ModNetwork.sendToServer(new BotActionPacket(m.botId(), action, arg)); }

    @Override
    protected void init() {
        if (opened == 0) send(PartyBots.ACT_HOLD, 1);                           // (it stands still while you talk to it)
        // compact on a short window (it was taller than the screen at the smallest GUI size: top and bottom cut off)
        int full = H0 + (m.radar() ? 22 : 0);
        boolean compact = full > height - 6;
        st = compact ? 19 : 22; bh = compact ? 17 : 20;
        H = full - (compact ? 3 * (7 + (m.radar() ? 1 : 0)) + 3 : 0);
        int x = x0() + 96, y = y0() + (compact ? 28 : 30), bw = W - 104;
        if (showPets) {
            if (m.petNames().length == 0) addRenderableWidget(com.xenowarriors.client.menu.Flat.builder(Component.literal("(no pets)"), b -> {}).bounds(x, y, bw, bh).build()).active = false;
            for (int i = 0; i < Math.min(6, m.petNames().length); i++) {
                int id = m.petIds()[i];
                addRenderableWidget(com.xenowarriors.client.menu.Flat.builder(Component.literal("Summon " + m.petNames()[i]), b -> { send(PartyBots.ACT_SUMMON_PET, id); onClose(); }).bounds(x, y + i * st, bw, bh).build());
            }
            addRenderableWidget(com.xenowarriors.client.menu.Flat.builder(Component.literal("Back"), b -> { showPets = false; rebuildWidgets(); }).bounds(x, y0() + H - 28, bw, bh).build());
            return;
        }
        addRenderableWidget(com.xenowarriors.client.menu.Flat.builder(Component.literal("Inventory"), b -> send(PartyBots.ACT_INVENTORY, 0)).bounds(x, y, bw, bh).build());
        addRenderableWidget(com.xenowarriors.client.menu.Flat.builder(Component.literal("Pets (" + m.petNames().length + ")"), b -> { showPets = true; rebuildWidgets(); }).bounds(x, y + 1 * st, bw / 2 - 8, bh).build());
        addRenderableWidget(com.xenowarriors.client.menu.Flat.builder(Component.literal("Ask for gear"), b -> { send(PartyBots.ACT_GEAR, 0); onClose(); })   // (their armor and outfits - your own characters, bond 50+)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("Playing a character you made: ask for their armor and outfits (bond " + com.xenowarriors.you.CharacterGear.ARMOR
                        + "; enchanted at " + com.xenowarriors.you.CharacterGear.ENCHANTED + ", every enchantment at " + com.xenowarriors.you.CharacterGear.MAX + ")")))
                .bounds(x + bw / 2 - 6, y + 1 * st, bw - bw / 2 + 6, bh).build());
        Button fly = addRenderableWidget(com.xenowarriors.client.menu.Flat.builder(Component.literal("Fly me"), b -> { send(PartyBots.ACT_FLY, 0); onClose(); }).bounds(x, y + 2 * st, bw, bh).build());
        fly.active = m.canFly();
        Button carry = addRenderableWidget(com.xenowarriors.client.menu.Flat.builder(Component.literal("Carry them (fly)"), b -> { send(PartyBots.ACT_CARRY, 0); onClose(); }).bounds(x, y + 3 * st, bw, bh).build());
        carry.active = minecraft.player != null && com.xenowarriors.client.ClientCharacterData.winged(minecraft.player);
        addRenderableWidget(com.xenowarriors.client.menu.Flat.builder(Component.literal("Spar (PvP)"), b -> { send(PartyBots.ACT_SPAR, 0); onClose(); }).bounds(x, y + 4 * st, bw / 2 - 1, bh).build());
        addRenderableWidget(com.xenowarriors.client.menu.Flat.builder(Component.literal("Side mission"), b -> { send(PartyBots.ACT_SIDE, 0); onClose(); })   // (ask them for their next side mission)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("Ask them for their next side mission (story characters)")))
                .bounds(x + bw / 2 + 1, y + 4 * st, bw - bw / 2 - 1, bh).build());
        addRenderableWidget(com.xenowarriors.client.menu.Flat.builder(modeLabel(), b -> { mode = (mode + 1) % PartyBotEntity.MODES.length; send(PartyBots.ACT_MODE, mode); b.setMessage(modeLabel()); }).bounds(x, y + 5 * st, bw, bh).build());
        if (DMZ) addRenderableWidget(com.xenowarriors.client.menu.Flat.builder(Component.literal("Dragon Ball"), b -> send(PartyBots.ACT_DMZ, 0)).bounds(x, y + 6 * st, bw - 44, bh).build());   // (its DragonMineZ character: points, missions)
        if (DMZ) addRenderableWidget(com.xenowarriors.client.menu.Flat.builder(Component.literal("Fuse"), b -> { send(PartyBots.ACT_FUSE, 0); onClose(); })    // (Potara earrings in its bag, or it knows the Fusion Dance)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("Fuse with them: they need Potara earrings in their bag, or the Fusion Dance skill")))
                .bounds(x + bw - 42, y + 6 * st, 42, bh).build());
        int oy = y + (DMZ ? 7 : 6) * st;
        if (m.radar()) {                                                         // a Dragon Radar in its bag: send it off to find the Dragon Balls
            addRenderableWidget(com.xenowarriors.client.menu.Flat.builder(Component.literal("Find the Dragon Balls"), b -> { send(PartyBots.ACT_RADAR, 0); onClose(); })
                    .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("They take their Dragon Radar and go looking (about 30 minutes - they leave the team till then)")))
                    .bounds(x, oy, bw, bh).build());
            oy += st;
        }
        Button dress = addRenderableWidget(com.xenowarriors.client.menu.Flat.builder(outfitLabel(), b -> {      // cycles their outfits (yours for that character too)
            String[] names = type.outfitNames();
            if (names.length < 2) return;
            outfit = (outfit + 1) % names.length;
            send(PartyBots.ACT_OUTFIT, outfit);
            b.setMessage(outfitLabel());
        }).tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("Change their outfit - it's also what they wear when you play them")))
                .bounds(x, oy, bw, bh).build());
        dress.active = type.outfitNames().length > 1;
        addRenderableWidget(com.xenowarriors.client.menu.Flat.builder(Component.literal("Leave party"), b -> { send(PartyBots.ACT_DISMISS, 0); onClose(); }).bounds(x, oy + st + 8, bw, bh).build());
    }

    private int missing;

    /** The bot is gone (dismissed, killed, sent home elsewhere): close, so the buttons don't act on nothing. */
    @Override
    public void tick() {
        super.tick();
        var e = minecraft == null || minecraft.level == null ? null : minecraft.level.getEntity(m.botId());
        boolean gone = !(e instanceof PartyBotEntity) || e.isRemoved() || !((PartyBotEntity) e).isAlive();
        missing = gone ? missing + 1 : 0;
        if (missing > 5) { onClose(); return; }
        // kicked out: it got hit (it's back in the fight) or you walked off
        if (e instanceof PartyBotEntity pb && minecraft.player != null && (pb.hurtTime > 0 && opened > 10 || pb.distanceToSqr(minecraft.player) > 8.5 * 8.5)) onClose();
        opened++;
    }

    private int opened;

    @Override
    public void removed() {
        super.removed();
        send(PartyBots.ACT_HOLD, 0);                                             // (done: it can move again - unless you went into its bag)
    }

    private int outfit = -1;

    private Component outfitLabel() {
        if (outfit < 0) outfit = minecraft != null && minecraft.level != null && minecraft.level.getEntity(m.botId()) instanceof PartyBotEntity b ? b.outfit() : 0;
        String[] names = type.outfitNames();
        return Component.literal("Outfit: " + names[Math.max(0, Math.min(names.length - 1, outfit))]);
    }

    private Component modeLabel() { return Component.literal("Mode: " + PartyBotEntity.MODES[mode]); }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        com.xenowarriors.client.menu.Flat.background(this, g);
        int x = x0(), y = y0();
        var ownSlot = minecraft.level != null && minecraft.level.getEntity(m.botId()) instanceof com.xenowarriors.bot.PartyBotEntity pb ? pb.you() : null;   // (one of your own characters)
        int color = ownSlot != null ? 0xFF000000 | ownSlot.color() : type.getColor();
        g.fill(x - 2, y - 2, x + W + 2, y + H + 2, color);
        g.fill(x, y, x + W, y + H, 0xF0101018);
        if (ownSlot != null) g.drawString(font, ownSlot.name + " - " + ownSlot.shown().title + " of " + ownSlot.element.title(), x + 8, y + 8, color);
        else g.drawString(font, type.getFullName(), x + 8, y + 8, type.getColor());
        g.drawString(font, "Lv " + m.level() + "  • BOT", x + 8, y + 18, 0xBBBBBB);
        if (minecraft.level != null && minecraft.level.getEntity(m.botId()) instanceof LivingEntity bot)
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, x + 48, y + 160, 48, (float) (x + 48 - mx), (float) (y + 80 - my), bot);
        String hint = switch (mode) {
            case PartyBotEntity.NEUTRAL -> "Fights what you hit, or what hits you";
            case PartyBotEntity.AGGRESSIVE -> "Attacks any hostile mob near you";
            default -> type == CharacterType.ULTIMA ? "Data Sense: finds and mines ore" : "Karma Sense: finds and mines ore";
        };
        if (!showPets) {                     // both under the buttons, full width (they ran under the Inventory / Leave party buttons)
            g.pose().pushPose(); g.pose().translate(x + 8, y + H - 11, 0); g.pose().scale(0.7F, 0.7F, 1F);
            g.drawString(font, hint, 0, 0, 0x8899AA, false); g.pose().popPose();
            g.pose().pushPose(); g.pose().translate(x + 8, y + H - 19, 0); g.pose().scale(0.7F, 0.7F, 1F);
            g.drawString(font, "Sneak + right-click with armour to dress them", 0, 0, 0x8899AA, false); g.pose().popPose();
        }
        super.render(g, mx, my, pt);
    }

    @Override public boolean isPauseScreen() { return false; }
}

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
    private final OpenBotMenuPacket m;
    private final CharacterType type;
    private int mode;
    private boolean showPets;

    public BotScreen(OpenBotMenuPacket m) {
        super(Component.literal(CharacterType.byId(m.character()).getDisplayName().getString()));
        this.m = m;
        this.type = CharacterType.byId(m.character());
        this.mode = m.mode();
        if (m.radar()) H = H0 + 22;
    }

    private int x0() { return (width - W) / 2; }
    private int y0() { return (height - H) / 2; }

    private void send(int action, int arg) { ModNetwork.sendToServer(new BotActionPacket(m.botId(), action, arg)); }

    @Override
    protected void init() {
        int x = x0() + 96, y = y0() + 30, bw = W - 104;
        if (showPets) {
            if (m.petNames().length == 0) addRenderableWidget(Button.builder(Component.literal("(no pets)"), b -> {}).bounds(x, y, bw, 20).build()).active = false;
            for (int i = 0; i < Math.min(6, m.petNames().length); i++) {
                int id = m.petIds()[i];
                addRenderableWidget(Button.builder(Component.literal("Summon " + m.petNames()[i]), b -> { send(PartyBots.ACT_SUMMON_PET, id); onClose(); }).bounds(x, y + i * 22, bw, 20).build());
            }
            addRenderableWidget(Button.builder(Component.literal("Back"), b -> { showPets = false; rebuildWidgets(); }).bounds(x, y0() + H - 28, bw, 20).build());
            return;
        }
        addRenderableWidget(Button.builder(Component.literal("Inventory"), b -> send(PartyBots.ACT_INVENTORY, 0)).bounds(x, y, bw, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Pets (" + m.petNames().length + ")"), b -> { showPets = true; rebuildWidgets(); }).bounds(x, y + 22, bw, 20).build());
        Button fly = addRenderableWidget(Button.builder(Component.literal("Fly me"), b -> { send(PartyBots.ACT_FLY, 0); onClose(); }).bounds(x, y + 44, bw, 20).build());
        fly.active = m.canFly();
        Button carry = addRenderableWidget(Button.builder(Component.literal("Carry them (fly)"), b -> { send(PartyBots.ACT_CARRY, 0); onClose(); }).bounds(x, y + 66, bw, 20).build());
        carry.active = minecraft.player != null && com.xenowarriors.client.ClientCharacterData.winged(minecraft.player);
        addRenderableWidget(Button.builder(Component.literal("Spar (PvP)"), b -> { send(PartyBots.ACT_SPAR, 0); onClose(); }).bounds(x, y + 88, bw, 20).build());
        addRenderableWidget(Button.builder(modeLabel(), b -> { mode = (mode + 1) % 3; send(PartyBots.ACT_MODE, mode); b.setMessage(modeLabel()); }).bounds(x, y + 110, bw, 20).build());
        if (DMZ) addRenderableWidget(Button.builder(Component.literal("Dragon Ball"), b -> send(PartyBots.ACT_DMZ, 0)).bounds(x, y + 132, bw - 44, 20).build());   // (its DragonMineZ character: points, missions)
        if (DMZ) addRenderableWidget(Button.builder(Component.literal("Fuse"), b -> { send(PartyBots.ACT_FUSE, 0); onClose(); })    // (Potara earrings in its bag, or it knows the Fusion Dance)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("Fuse with them: they need Potara earrings in their bag, or the Fusion Dance skill")))
                .bounds(x + bw - 42, y + 132, 42, 20).build());
        int oy = y + (DMZ ? 154 : 132);
        if (m.radar()) {                                                         // a Dragon Radar in its bag: send it off to find the Dragon Balls
            addRenderableWidget(Button.builder(Component.literal("Find the Dragon Balls"), b -> { send(PartyBots.ACT_RADAR, 0); onClose(); })
                    .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("They take their Dragon Radar and go looking (about 30 minutes - they leave the team till then)")))
                    .bounds(x, oy, bw, 20).build());
            oy += 22;
        }
        Button dress = addRenderableWidget(Button.builder(outfitLabel(), b -> {      // cycles their outfits (yours for that character too)
            String[] names = type.outfitNames();
            if (names.length < 2) return;
            outfit = (outfit + 1) % names.length;
            send(PartyBots.ACT_OUTFIT, outfit);
            b.setMessage(outfitLabel());
        }).tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("Change their outfit - it's also what they wear when you play them")))
                .bounds(x, oy, bw, 20).build());
        dress.active = type.outfitNames().length > 1;
        addRenderableWidget(Button.builder(Component.literal("Leave party"), b -> { send(PartyBots.ACT_DISMISS, 0); onClose(); }).bounds(x, oy + (DMZ ? 30 : 30), bw, 20).build());
    }

    private int missing;

    /** The bot is gone (dismissed, killed, sent home elsewhere): close, so the buttons don't act on nothing. */
    @Override
    public void tick() {
        super.tick();
        var e = minecraft == null || minecraft.level == null ? null : minecraft.level.getEntity(m.botId());
        boolean gone = !(e instanceof PartyBotEntity) || e.isRemoved() || !((PartyBotEntity) e).isAlive();
        missing = gone ? missing + 1 : 0;
        if (missing > 5) onClose();
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
        renderBackground(g);
        int x = x0(), y = y0();
        g.fill(x - 2, y - 2, x + W + 2, y + H + 2, type.getColor());
        g.fill(x, y, x + W, y + H, 0xF0101018);
        g.drawString(font, type.getFullName(), x + 8, y + 8, type.getColor());
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

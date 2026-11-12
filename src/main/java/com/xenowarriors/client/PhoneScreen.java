package com.xenowarriors.client;

import com.xenowarriors.network.ModNetwork;
import com.xenowarriors.network.PhoneActionPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The phone: everyone online to call (with Simple Voice Chat you'll hear each other anywhere), a pretend call, and the
 * Teammates page - your busy off-duty characters, when they'll be done, and a call to ask them to join you then.
 */
public class PhoneScreen extends Screen {
    private static final int W = 150, H = 220;
    private int page;
    /** The Teammates page (null = Contacts). */
    private final com.xenowarriors.network.PhoneTeamPacket team;

    public PhoneScreen() { this(null); }

    public PhoneScreen(com.xenowarriors.network.PhoneTeamPacket team) {
        super(Component.literal("Phone"));
        this.team = team;
    }

    /** The server sent the Teammates page: show it (or refresh it). */
    public static void showTeam(com.xenowarriors.network.PhoneTeamPacket m) {
        Minecraft mc = Minecraft.getInstance();
        int page = mc.screen instanceof PhoneScreen ps && ps.team != null ? ps.page : 0;
        PhoneScreen s = new PhoneScreen(m);
        s.page = page;
        mc.setScreen(s);
    }

    private int left() { return (width - W) / 2; }
    private int top() { return (height - H) / 2; }

    @Override
    protected void init() {
        clearWidgets();
        if (team != null) { initTeam(); return; }
        Minecraft mc = Minecraft.getInstance();
        List<net.minecraft.client.multiplayer.PlayerInfo> people = new ArrayList<>();
        if (mc.getConnection() != null && mc.player != null)
            for (var info : mc.getConnection().getOnlinePlayers()) if (!info.getProfile().getId().equals(mc.player.getUUID())) people.add(info);
        people.sort((a, b) -> a.getProfile().getName().compareToIgnoreCase(b.getProfile().getName()));
        int perPage = 7, x = left() + 12, y = top() + 30;
        int start = page * perPage;
        for (int i = start; i < Math.min(people.size(), start + perPage); i++) {
            var info = people.get(i);
            UUID id = info.getProfile().getId();
            addRenderableWidget(Button.builder(Component.literal("📞 " + info.getProfile().getName()), b -> {
                ModNetwork.sendToServer(new PhoneActionPacket(0, id));
                onClose();
            }).bounds(x, y, W - 24, 18).build());
            y += 21;
        }
        if (people.isEmpty()) addRenderableOnly((g, mx, my, pt) -> g.drawCenteredString(font, "Nobody else online", left() + W / 2, top() + 60, 0x9090A0));
        if (page > 0) addRenderableWidget(Button.builder(Component.literal("<"), b -> { page--; init(); }).bounds(x, top() + 180, 20, 16).build());
        if (start + perPage < people.size()) addRenderableWidget(Button.builder(Component.literal(">"), b -> { page++; init(); }).bounds(left() + W - 32, top() + 180, 20, 16).build());
        addRenderableWidget(Button.builder(Component.literal("Pretend call"), b -> {
            ModNetwork.sendToServer(new PhoneActionPacket(2, new UUID(0, 0)));
            onClose();
        }).bounds(x + 24, top() + 180, W - 72, 16).build());
        addRenderableWidget(Button.builder(Component.literal("👥 Teammates"), b -> ModNetwork.sendToServer(new PhoneActionPacket(3, new UUID(0, 0))))
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("Call a busy teammate and ask them to join you when they're done")))
                .bounds(x + 24, top() + 199, W - 72, 14).build());
    }

    private static final int TEAM_PER_PAGE = 4;

    private void initTeam() {
        int x = left() + 10, y = top() + 28, bw = W - 20;
        var list = team.entries();
        int start = page * TEAM_PER_PAGE;
        for (int i = start; i < Math.min(list.size(), start + TEAM_PER_PAGE); i++) {
            var e = list.get(i);
            var t = com.xenowarriors.character.CharacterType.byId(e.id());
            int yy = y + (i - start) * 36;
            Button b = addRenderableWidget(Button.builder(Component.literal(e.called() ? "✔ Called" : e.ordered() ? "On your job" : "📞 Call"), btn ->
                    ModNetwork.sendToServer(new PhoneActionPacket(4, new UUID(0, t.ordinal()))))
                    .bounds(x + bw - 52, yy + 9, 52, 14).build());
            b.active = !e.called() && !e.ordered();
        }
        if (list.isEmpty()) addRenderableOnly((g, mx, my, pt) -> g.drawCenteredString(font, "Nobody's busy right now", left() + W / 2, top() + 60, 0x9090A0));
        if (page > 0) addRenderableWidget(Button.builder(Component.literal("<"), b -> { page--; init(); }).bounds(x, top() + 180, 20, 16).build());
        if (start + TEAM_PER_PAGE < list.size()) addRenderableWidget(Button.builder(Component.literal(">"), b -> { page++; init(); }).bounds(left() + W - 30, top() + 180, 20, 16).build());
        addRenderableWidget(Button.builder(Component.literal("Contacts"), b -> minecraft.setScreen(new PhoneScreen())).bounds(x + 24, top() + 180, W - 68, 16).build());
    }

    private void renderTeam(GuiGraphics g) {
        int x = left() + 10, y = top() + 28;
        var list = team.entries();
        int start = page * TEAM_PER_PAGE;
        long ms = net.minecraft.Util.getMillis();
        for (int i = start; i < Math.min(list.size(), start + TEAM_PER_PAGE); i++) {
            var e = list.get(i);
            var t = com.xenowarriors.character.CharacterType.byId(e.id());
            int yy = y + (i - start) * 36;
            g.fill(x - 2, yy - 2, x + W - 18, yy + 32, 0x60000000);
            com.xenowarriors.client.journey.JourneyScreen.face(g, t, x, yy, 30,
                    com.xenowarriors.client.bot.OffDutyIcons.face(e.act(), t == com.xenowarriors.character.CharacterType.ULTIMA, ms + t.ordinal() * 1300L));
            g.drawString(font, t.getDisplayName().getString(), x + 33, yy, 0xFF000000 | (t.getColor() & 0xFFFFFF));
            String when = e.ordered() ? "until you call them back" : "done in ~" + e.minutes() + " min (" + e.clock() + ")";
            small(g, e.label(), x + 33, yy + 12, com.xenowarriors.client.bot.OffDutyIcons.color(e.act()), W - 52 - 33 - 22);
            small(g, when, x + 33, yy + 25, e.called() ? 0xFFC080FF : 0xFF9AA0B8, W - 20 - 33);
        }
    }

    /** One line at 0.6 scale, squeezed to fit {@code w}. */
    private void small(GuiGraphics g, String s, int x, int y, int col, int w) {
        float k = Math.min(0.6F, w / (float) Math.max(1, font.width(s)));
        g.pose().pushPose(); g.pose().translate(x, y, 0); g.pose().scale(k, k, 1F);
        g.drawString(font, s, 0, 0, col, false); g.pose().popPose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        int x = left(), y = top();
        g.fill(x - 4, y - 4, x + W + 4, y + H + 4, 0xFF101014);           // the phone's case
        g.fill(x, y, x + W, y + H, 0xFF1A1E2C);                            // screen
        g.fill(x + W / 2 - 14, y - 2, x + W / 2 + 14, y + 2, 0xFF050507);  // notch
        g.drawCenteredString(font, team != null ? "Teammates" : "Contacts", x + W / 2, y + 12, 0xFFFFFF);
        if (team != null) renderTeam(g);
        g.fill(x + W / 2 - 18, y + H - 10, x + W / 2 + 18, y + H - 7, 0xFF404660);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}

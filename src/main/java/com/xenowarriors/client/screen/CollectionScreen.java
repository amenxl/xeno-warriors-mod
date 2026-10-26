package com.xenowarriors.client.screen;

import com.xenowarriors.network.CollectionActionPacket;
import com.xenowarriors.network.CollectionPacket;
import com.xenowarriors.network.ModNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

import java.util.ArrayList;
import java.util.List;

/**
 * The Collection: every hostile enemy (vanilla, modded, DragonMineZ and the show's), how many you've defeated out of
 * 1000, the next stat bonus, and Tower mode.
 */
public class CollectionScreen extends Screen {
    private record Row(String name, int count, String ns) {}
    private final List<Row> all = new ArrayList<>();
    private final List<Row> rows = new ArrayList<>();
    private final CollectionPacket m;
    private int page, filter;                       // filter: 0 all, 1 unfinished, 2 found, 3 complete
    private static final int PER_PAGE = 12, MAX = 1000;
    private static final String[] FILTERS = {"All", "Unfinished", "Found", "Complete"};
    private Button filterBtn;

    public CollectionScreen(CollectionPacket m) {
        super(Component.literal("Collection"));
        this.m = m;
        for (int i = 0; i < m.ids().length; i++) {
            String id = m.ids()[i];
            Component name = EntityType.byString(id).map(EntityType::getDescription).orElse(Component.literal(id));
            all.add(new Row(name.getString(), m.counts()[i], new ResourceLocation(id).getNamespace()));
        }
        applyFilter();
    }

    private void applyFilter() {
        rows.clear();
        for (Row r : all) {
            boolean ok = switch (filter) {
                case 1 -> r.count < MAX;
                case 2 -> r.count > 0;
                case 3 -> r.count >= MAX;
                default -> true;
            };
            if (ok) rows.add(r);
        }
        page = Math.min(page, Math.max(0, (rows.size() - 1) / PER_PAGE));
    }

    private int left() { return width / 2 - 130; }
    private int top() { return height / 2 - 105; }

    @Override
    protected void init() {
        int x = left(), y = top();
        addRenderableWidget(Button.builder(Component.literal("<"), b -> page = Math.max(0, page - 1)).bounds(x + 140, y + 186, 20, 16).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> page = Math.min(Math.max(0, (rows.size() - 1) / PER_PAGE), page + 1)).bounds(x + 232, y + 186, 20, 16).build());
        filterBtn = addRenderableWidget(Button.builder(Component.literal("Show: " + FILTERS[filter]), b -> {
            filter = (filter + 1) % FILTERS.length; applyFilter(); b.setMessage(Component.literal("Show: " + FILTERS[filter]));
        }).bounds(x + 8, y + 146, 112, 16).build());
        boolean inTower = m.floor() > 0;
        addRenderableWidget(Button.builder(Component.literal(inTower ? "Leave Tower" : "Tower Mode"), b -> {
            ModNetwork.sendToServer(new CollectionActionPacket(inTower ? 2 : 1));
            onClose();
        }).bounds(x + 8, y + 166, 112, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        int x = left(), y = top(), total = m.total();
        g.fill(x - 4, y - 4, x + 264, y + 214, 0xFF5A3A22);                 // leather cover
        g.fill(x, y, x + 128, y + 206, 0xFFF2E8D2);                          // pages
        g.fill(x + 132, y, x + 260, y + 206, 0xFFF2E8D2);
        g.fill(x + 128, y, x + 132, y + 206, 0xFFCDBF9F);
        g.drawCenteredString(font, "Collection", x + 64, y + 8, 0xFF7A3A12);
        g.drawString(font, "Defeated: " + total, x + 10, y + 24, 0xFF3A2A1A, false);
        int next = (total / 100 + 1) * 100;
        g.drawString(font, "Next bonus at " + next, x + 10, y + 36, 0xFF6A4A2A, false);
        g.fill(x + 10, y + 48, x + 118, y + 55, 0xFFD8CCB0);
        g.fill(x + 10, y + 48, x + 10 + (int) (108 * ((total % 100) / 100F)), y + 55, 0xFFC88A2A);
        int found = 0, done = 0;
        for (Row r : all) { if (r.count > 0) found++; if (r.count >= MAX) done++; }
        g.drawString(font, "Found " + found + "/" + all.size(), x + 10, y + 64, 0xFF3A2A1A, false);
        g.drawString(font, "Complete " + done + "/" + all.size(), x + 10, y + 76, 0xFF3A2A1A, false);
        g.drawString(font, "Max 1000 per enemy", x + 10, y + 90, 0xFF8A7A5A, false);
        g.drawString(font, "Tower best: floor " + m.best(), x + 10, y + 108, 0xFF2A5A8A, false);
        if (m.floor() > 0) g.drawString(font, "In the Tower: floor " + m.floor(), x + 10, y + 120, 0xFFAA3A2A, false);
        else g.drawString(font, "Saves every 10 floors", x + 10, y + 120, 0xFF8A7A5A, false);
        int start = page * PER_PAGE;
        for (int i = 0; i < PER_PAGE && start + i < rows.size(); i++) {
            Row r = rows.get(start + i);
            int ry = y + 8 + i * 14;
            boolean seen = r.count > 0, complete = r.count >= MAX;
            String n = r.name.length() > 14 ? r.name.substring(0, 13) + "." : r.name;
            int col = complete ? 0xFFB8860B : !seen ? 0xFF9A9080 : r.ns.equals("xenowarriors") ? 0xFF6A2A9A : r.ns.equals("dragonminez") ? 0xFFC85A12 : 0xFF2A2A2A;
            g.drawString(font, (complete ? "★" : "") + n, x + 138, ry, col, false);
            String c = r.count + "/" + MAX;
            g.pose().pushPose();
            g.pose().translate(x + 254 - font.width(c) * 0.75F, ry + 1, 0);
            g.pose().scale(0.75F, 0.75F, 1);
            g.drawString(font, c, 0, 0, complete ? 0xFFB8860B : 0xFF7A3A12, false);
            g.pose().popPose();
            g.fill(x + 138, ry + 10, x + 138 + (int) (116 * Math.min(1F, r.count / (float) MAX)), ry + 11, complete ? 0xFFE0B040 : 0xFFC88A2A);
        }
        if (rows.isEmpty()) g.drawCenteredString(font, "Nothing here", x + 196, y + 90, 0xFF8A7A5A);
        g.drawCenteredString(font, (page + 1) + "/" + Math.max(1, (rows.size() + PER_PAGE - 1) / PER_PAGE), x + 196, y + 190, 0xFF6A4A2A);
        super.render(g, mx, my, pt);
    }

    @Override public boolean isPauseScreen() { return false; }
}

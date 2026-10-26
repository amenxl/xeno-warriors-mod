package com.xenowarriors.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

import java.util.ArrayList;
import java.util.List;

/** The Collection Book: every enemy type you've defeated, most first, with the next bonus. */
public class CollectionScreen extends Screen {
    private final List<String[]> rows = new ArrayList<>();
    private final int total;
    private int page;
    private static final int PER_PAGE = 12;

    public CollectionScreen(String[] ids, int[] counts, int total) {
        super(Component.literal("Collection"));
        this.total = total;
        List<int[]> idx = new ArrayList<>();
        for (int i = 0; i < ids.length; i++) idx.add(new int[]{i, counts[i]});
        idx.sort((a, b) -> b[1] - a[1]);
        for (int[] e : idx) {
            String id = ids[e[0]];
            Component name = EntityType.byString(id).map(EntityType::getDescription).orElse(Component.literal(id));
            rows.add(new String[]{name.getString(), Integer.toString(e[1]), new ResourceLocation(id).getNamespace()});
        }
    }

    private int left() { return width / 2 - 130; }
    private int top() { return height / 2 - 105; }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.literal("<"), b -> page = Math.max(0, page - 1)).bounds(left() + 8, top() + 186, 20, 16).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> page = Math.min(Math.max(0, (rows.size() - 1) / PER_PAGE), page + 1)).bounds(left() + 232, top() + 186, 20, 16).build());
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        int x = left(), y = top();
        g.fill(x - 4, y - 4, x + 264, y + 214, 0xFF5A3A22);                 // leather cover
        g.fill(x, y, x + 128, y + 206, 0xFFF2E8D2);                          // pages
        g.fill(x + 132, y, x + 260, y + 206, 0xFFF2E8D2);
        g.fill(x + 128, y, x + 132, y + 206, 0xFFCDBF9F);
        g.drawCenteredString(font, "Collection", x + 64, y + 8, 0xFF7A3A12);
        g.drawString(font, "Defeated: " + total, x + 10, y + 26, 0xFF3A2A1A, false);
        int next = (total / 100 + 1) * 100;
        g.drawString(font, "Next bonus at " + next, x + 10, y + 38, 0xFF6A4A2A, false);
        g.drawString(font, "Every 100: a random", x + 10, y + 56, 0xFF6A6A6A, false);
        g.drawString(font, "stat boost (and a", x + 10, y + 66, 0xFF6A6A6A, false);
        g.drawString(font, "DragonMineZ stat).", x + 10, y + 76, 0xFF6A6A6A, false);
        g.fill(x + 10, y + 96, x + 118, y + 104, 0xFFD8CCB0);
        g.fill(x + 10, y + 96, x + 10 + (int) (108 * ((total % 100) / 100F)), y + 104, 0xFFC88A2A);
        g.drawString(font, (total % 100) + "/100", x + 46, y + 108, 0xFF6A4A2A, false);
        g.drawString(font, rows.size() + " kinds found", x + 10, y + 130, 0xFF3A2A1A, false);
        int start = page * PER_PAGE;
        for (int i = 0; i < PER_PAGE && start + i < rows.size(); i++) {
            String[] r = rows.get(start + i);
            int ry = y + 10 + i * 14;
            String n = r[0].length() > 16 ? r[0].substring(0, 15) + "." : r[0];
            g.drawString(font, n, x + 140, ry, r[2].equals("xenowarriors") ? 0xFF6A2A9A : 0xFF2A2A2A, false);
            g.drawString(font, "x" + r[1], x + 252 - font.width("x" + r[1]), ry, 0xFF7A3A12, false);
        }
        if (rows.isEmpty()) g.drawCenteredString(font, "Nothing yet - go fight!", x + 196, y + 90, 0xFF8A7A5A);
        g.drawCenteredString(font, (page + 1) + "/" + Math.max(1, (rows.size() + PER_PAGE - 1) / PER_PAGE), x + 196, y + 190, 0xFF6A4A2A);
        super.render(g, mx, my, pt);
    }

    @Override public boolean isPauseScreen() { return false; }
}

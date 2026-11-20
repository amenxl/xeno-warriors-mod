package com.xenowarriors.client.story;

import com.xenowarriors.character.CharacterType;
import com.xenowarriors.client.ClientCharacterData;
import com.xenowarriors.network.StoryActionPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/**
 * A story scene's lines, one at a time at the bottom of the screen: the speaker's portrait and name, the line typing
 * out. Click (or Space / Enter) for the next line; at a choice, pick one. Only the party's host moves the story on -
 * everyone else reads along. Lines by "you" are your own character's (they don't say much).
 */
public class StoryDialogueScreen extends Screen {
    private final CompoundTag tag;
    private final List<CompoundTag> lines = new ArrayList<>();
    private final boolean host;
    private final String ctx;
    private int i;
    private long shown = net.minecraft.Util.getMillis();
    private boolean sent;

    public StoryDialogueScreen(CompoundTag tag) {
        super(Component.literal("Story"));
        this.tag = tag;
        for (Tag t : tag.getList("lines", Tag.TAG_COMPOUND)) lines.add((CompoundTag) t);
        host = tag.getBoolean("host");
        ctx = tag.getString("ctx");
    }

    @Override public boolean isPauseScreen() { return false; }

    private CompoundTag line() { return lines.isEmpty() ? new CompoundTag() : lines.get(Math.min(i, lines.size() - 1)); }
    private boolean last() { return i >= lines.size() - 1; }
    private boolean choosing() { return last() && line().contains("choice") && typed(); }
    private int chars() { return (int) ((net.minecraft.Util.getMillis() - shown) / 18); }
    private boolean typed() { return chars() >= line().getString("t").length(); }

    @Override
    protected void init() {
        clearWidgets();
        if (choosing() && host) {
            ListTag ch = line().getList("choice", Tag.TAG_STRING);
            int bw = Math.min(360, width - 40), y = height - 96 - ch.size() * 24;
            for (int k = 0; k < ch.size(); k++) {
                int kk = k;
                addRenderableWidget(Button.builder(Component.literal((k + 1) + ". " + ch.getString(k)), b -> finish(kk)).bounds((width - bw) / 2, y + k * 24, bw, 20).build());
            }
        }
    }

    /** (Tests) pick a choice / move on. */
    public void testNext(int choice) { if (choosing()) finish(choice); else advance(); }

    private void advance() {
        if (!typed()) { shown = 0; return; }                          // (first click: show the whole line)
        if (!last()) { i++; shown = net.minecraft.Util.getMillis(); init(); return; }
        if (line().contains("choice") && host) return;              // (pick one)
        finish(-1);
    }

    private void finish(int choice) {
        if (sent) return;
        sent = true;
        if (host && !ctx.isEmpty()) StoryClient.act(StoryActionPacket.DONE, ctx, choice);
        if (minecraft != null && minecraft.screen == this) minecraft.setScreen(null);
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (super.mouseClicked(x, y, button)) return true;
        if (!choosing() || !host) advance();
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE || key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER) { if (!choosing()) advance(); return true; }
        if (choosing() && host && key >= org.lwjgl.glfw.GLFW.GLFW_KEY_1 && key <= org.lwjgl.glfw.GLFW.GLFW_KEY_9) {
            int k = key - org.lwjgl.glfw.GLFW.GLFW_KEY_1;
            if (k < line().getList("choice", Tag.TAG_STRING).size()) finish(k);
            return true;
        }
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {                  // (the story waits on the host: Story menu > Resume scene brings the lines back)
            if (host && minecraft.player != null) minecraft.player.displayClientMessage(Component.literal("Story paused - Main Menu > Story > Resume scene").withStyle(net.minecraft.ChatFormatting.GOLD), true);
            onClose();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void tick() {
        if (choosing() && host && children().isEmpty()) init();
    }

    private String speakerName(CompoundTag l) {
        if (l.contains("n")) return l.getString("n");
        String s = l.getString("s");
        if (s.equals("you")) return ClientCharacterData.name(minecraft.player);
        if (s.equals("narrator")) return "Narrator";
        if (s.contains(":")) {                                                            // "lexy:persephone": Persephone in Lexy's body
            CharacterType b = CharacterType.byId(s.substring(0, s.indexOf(':'))), so = CharacterType.byId(s.substring(s.indexOf(':') + 1));
            return b.getDisplayName().getString() + " (" + so.getDisplayName().getString() + ")";
        }
        CharacterType t = CharacterType.byId(s);
        if (t != CharacterType.NONE) return t.getDisplayName().getString();
        return switch (s) { case "fox_girl" -> "Fox Girl"; case "num3602" -> "3-6-0-2"; case "c_soul" -> "C-Soul"; default -> Character.toUpperCase(s.charAt(0)) + s.substring(1); };
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        CompoundTag l = line();
        String s = l.getString("s");
        int bw = Math.min(440, width - 24), bh = 74, bx = (width - bw) / 2, by = height - bh - 12;
        g.fillGradient(0, height - bh - 40, width, height, 0x00000000, 0xC0000000);
        g.fill(bx, by, bx + bw, by + bh, 0xE0141420);
        boolean narr = s.equals("narrator");
        String body = s.contains(":") ? s.substring(0, s.indexOf(':')) : null;            // (someone in another's body: their face, red eyes)
        if (body != null) s = s.substring(s.indexOf(':') + 1);
        CharacterType t = s.equals("you") ? ClientCharacterData.getLocal() : CharacterType.byId(body != null ? body : s);
        int col = narr ? 0xFFB0B0C0 : t != CharacterType.NONE ? 0xFF000000 | ClientCharacterData.color(minecraft.player) : 0xFFE8B030;
        if (!s.equals("you") && t != CharacterType.NONE) col = 0xFF000000 | ((body != null ? CharacterType.byId(s) : t).getColor() & 0xFFFFFF);
        g.fill(bx, by, bx + bw, by + 2, col);
        int tx = bx + 10;
        // the portrait
        if (!narr) {
            int ps = 58, px = bx + 8, py = by + 8;
            ResourceLocation pr = t != CharacterType.NONE && t != CharacterType.YOU ? com.xenowarriors.XenoWarriors.id("textures/gui/hud/portrait_" + t.getId() + (body != null ? "_possessed" : "") + "_normal.png") : null;
            if (pr != null && minecraft.getResourceManager().getResource(pr).isPresent()) g.blit(pr, px, py, 0, 0, ps, ps, ps, ps);
            else if (t != CharacterType.NONE) com.xenowarriors.client.journey.JourneyScreen.face(g, t, px, py, ps);
            else if (minecraft.getResourceManager().getResource(com.xenowarriors.XenoWarriors.id("textures/gui/story/portrait_" + s + ".png")).isPresent())   // (someone who isn't a character: Henry)
                g.blit(com.xenowarriors.XenoWarriors.id("textures/gui/story/portrait_" + s + ".png"), px, py, 0, 0, ps, ps, ps, ps);
            else { g.fill(px, py, px + ps, py + ps, 0xFF2A2A38); g.drawCenteredString(font, speakerName(l).substring(0, 1), px + ps / 2, py + ps / 2 - 4, col); }
            tx = px + ps + 10;
        }
        g.drawString(font, speakerName(l), tx, by + 8, col, true);
        String text = l.getString("t");
        String vis = text.substring(0, Math.min(text.length(), Math.max(0, chars())));
        List<FormattedCharSequence> wrapped = font.split(Component.literal(vis).withStyle(narr ? net.minecraft.ChatFormatting.ITALIC : net.minecraft.ChatFormatting.RESET), bx + bw - tx - 10);
        int ty = by + 22;
        for (FormattedCharSequence w : wrapped) { g.drawString(font, w, tx, ty, narr ? 0xFFC8C8D8 : 0xFFF0F0F0, false); ty += 10; if (ty > by + bh - 10) break; }
        String hint = choosing() ? (host ? "Pick one" : "Waiting for the host to choose...") : !typed() ? "" : last() ? (host ? "Click to continue" : "Click to close - the host moves the story on") : "Click / Space";
        g.drawString(font, hint, bx + bw - font.width(hint) - 6, by + bh - 11, 0xFF8890A0, false);
        if (!tag.getString("place").isEmpty()) g.drawString(font, tag.getString("place"), 8, 8, 0xFFE8B030, true);
        super.render(g, mx, my, pt);
    }
}

package com.xenowarriors.client.party;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.network.ModNetwork;
import com.xenowarriors.network.PartyGamePacket;
import com.xenowarriors.network.PartyGameResultPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.*;

/** The party games with a bot (bot.BotPartyGames): arm wrestling (mash left click) and Memory (flip two, find the pairs). */
public final class PartyGameScreens {
    private PartyGameScreens() {}

    public static void received(PartyGamePacket m) {
        Minecraft mc = Minecraft.getInstance();
        if (m.kind() < 0) { if (mc.screen instanceof Arm || mc.screen instanceof Memory) mc.setScreen(null); return; }
        mc.setScreen(m.kind() == 1 ? new Arm(m) : new Memory(m));
    }

    static ResourceLocation portrait(String id) { return XenoWarriors.id("textures/gui/hud/portrait_" + id + "_normal.png"); }

    static void frame(GuiGraphics g, int x, int y, int w, int h, int edge) {
        g.fill(x - 2, y - 2, x + w + 2, y + h + 2, edge);
        g.fill(x, y, x + w, y + h, 0xF0141420);
    }

    // ================================================================ arm wrestling

    /**
     * Mash left click (anywhere) to push the bar your way; the bot pushes back all the while. All the way over wins; after
     * {@value #TIME} seconds whoever's ahead wins. The arms in the middle tilt with it.
     */
    public static class Arm extends Screen {
        static final int TIME = 20, W = 260, H = 170;
        final int botId; final String name, who; final float push;
        /** 0: their arm is down (you win) .. 1: yours is. */
        float bar = 0.5F, shown = 0.5F;
        int ticks, clicks, closeIn = -1, countdown = 40, outcome = -1;
        public static int seen;

        Arm(PartyGamePacket m) {
            super(Component.literal("Arm wrestling"));
            botId = m.botId(); name = m.name(); push = m.f(); who = m.data();
            seen++;
        }

        @Override public boolean isPauseScreen() { return false; }

        @Override
        public boolean mouseClicked(double x, double y, int button) {
            if (button == 0) mash();
            return true;
        }

        /** One push (a left click). */
        public void mash() {
            if (countdown > 0 || outcome >= 0) return;
            clicks++;
            bar = Math.max(0F, bar - 0.045F);
            if (minecraft != null && minecraft.player != null && clicks % 3 == 0)
                minecraft.player.playSound(net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_WEAK, 0.4F, 0.8F + bar * 0.6F);
        }

        @Override
        public void tick() {
            ticks++;
            if (closeIn > 0) { if (--closeIn == 0) onClose(); return; }
            if (countdown > 0) { countdown--; return; }
            if (outcome >= 0) return;
            float strain = 1F + Mth.sin(ticks * 0.35F) * 0.35F;                    // (it heaves in bursts)
            bar = Math.min(1F, bar + push * strain);
            int played = ticks - 40;
            if (bar <= 0F || bar >= 1F || played >= TIME * 20) finish(bar < 0.5F ? 1 : 0);
        }

        void finish(int youWon) {
            outcome = youWon;
            closeIn = 50;
            ModNetwork.sendToServer(new PartyGameResultPacket(botId, youWon, 1 - youWon));
        }

        @Override
        public void render(GuiGraphics g, int mx, int my, float pt) {
            com.xenowarriors.client.menu.Flat.background(this, g);
            shown = Mth.lerp(0.35F, shown, bar);
            int x = (width - W) / 2, y = (height - H) / 2;
            frame(g, x, y, W, H, 0xFFFFB040);
            g.drawCenteredString(font, "ARM WRESTLING vs " + name.toUpperCase(), width / 2, y + 8, 0xFFE070);
            String sub = countdown > 0 ? (countdown > 26 ? "Ready..." : countdown > 13 ? "Set..." : "GO!") : outcome < 0 ? "MASH LEFT CLICK!" : outcome == 1 ? "YOU WIN!" : name + " wins!";
            g.drawCenteredString(font, sub, width / 2, y + 20, outcome == 1 ? 0x60FF60 : outcome == 0 ? 0xFF7070 : 0xFFFFFF);
            // portraits either side
            var me = minecraft == null ? null : minecraft.player;
            if (me == null || !com.xenowarriors.client.HudPortrait.draw(g, me, com.xenowarriors.client.ClientCharacterData.get(me), x + 10, y + 40, 48, false))
            {
                var t = me == null ? com.xenowarriors.character.CharacterType.NONE : com.xenowarriors.client.ClientCharacterData.get(me);
                if (t != com.xenowarriors.character.CharacterType.NONE && t != com.xenowarriors.character.CharacterType.YOU) g.blit(portrait(t.getId()), x + 10, y + 40, 0, 0, 48, 48, 48, 48);
                else g.fill(x + 10, y + 40, x + 58, y + 88, 0xFF3A4A5A);
            }
            g.drawCenteredString(font, "You", x + 34, y + 92, 0xFFFFFF);
            if (Arrays.asList(com.xenowarriors.bot.BotPartyGames.FACES).contains(who) || who.equals("sam") || who.equals("stevie") || who.equals("zane") || who.equals("technica") || who.equals("yuri") || who.equals("ultima"))
                g.blit(portrait(who), x + W - 58, y + 40, 0, 0, 48, 48, 48, 48);
            else g.fill(x + W - 58, y + 40, x + W - 10, y + 88, 0xFF3A4A5A);
            g.drawCenteredString(font, name, x + W - 34, y + 92, 0xFFFFFF);
            // the arms: two forearms clasped over the table, tilted toward whoever's losing
            int cx = width / 2, cy = y + 100;
            g.fill(cx - 60, cy + 8, cx + 60, cy + 14, 0xFF6A4A2A);                  // (the table)
            float ang = (shown - 0.5F) * 2F * 1.2F;                                 // -1.2 (theirs down) .. 1.2 (yours down)
            var ps = g.pose();
            ps.pushPose();
            ps.translate(cx, cy + 8, 0);
            ps.mulPose(com.mojang.math.Axis.ZP.rotation(ang));
            g.fill(-5, -46, 1, 0, 0xFFE0B090);                                       // your forearm
            g.fill(-1, -46, 5, 0, 0xFFC89070);                                       // theirs
            g.fill(-7, -52, 7, -42, 0xFFD8A880);                                     // the clasped hands
            ps.popPose();
            // the bar
            int bx = x + 20, bw = W - 40, by = y + H - 34;
            g.fill(bx, by, bx + bw, by + 10, 0xFF20262E);
            int split = bx + Math.round(bw * shown);
            g.fill(bx, by, split, by + 10, 0xFF40C060);
            g.fill(split, by, bx + bw, by + 10, 0xFFC04040);
            g.fill(bx + bw / 2, by - 2, bx + bw / 2 + 1, by + 12, 0xFFFFFFFF);
            int left = Math.max(0, TIME - Math.max(0, ticks - 40) / 20);
            g.drawCenteredString(font, outcome < 0 && countdown <= 0 ? left + "s  -  " + clicks + " pushes" : "", width / 2, y + H - 18, 0xA0A8B0);
        }

        /** (Tests) */
        public float barForTest() { return bar; }
        public int outcomeForTest() { return outcome; }
    }

    // ================================================================ Memory

    /**
     * Memory (Concentration, Pairs): 16 cards face down - 8 pairs of the show's characters. Flip two: a pair stays face
     * up and you go again; otherwise they flip back and it's the bot's turn. The bot remembers cards it has seen (not
     * always). Most pairs when they're all found wins.
     */
    public static class Memory extends Screen {
        static final int COLS = 4, ROWS = 4, CW = 40, CH = 50, GAP = 6;
        final int botId; final String name; final float skill;
        final String[] faces = new String[COLS * ROWS];
        final boolean[] up = new boolean[COLS * ROWS], taken = new boolean[COLS * ROWS];
        final int[] owner = new int[COLS * ROWS];          // 1 you, 2 the bot
        final Set<Integer> seenByBot = new HashSet<>();
        final Random rnd;
        int first = -1, second = -1, wait, mine, theirs, closeIn = -1;
        boolean botTurn, done;
        public static int seen;
        /** (Tests) the last game's pairs: yours, the bot's. */
        public static final int[] lastResult = {-1, -1};

        Memory(PartyGamePacket m) {
            super(Component.literal("Memory"));
            botId = m.botId(); name = m.name(); skill = m.f();
            String[] parts = m.data().split(";");
            String[] deck = parts[0].split(",");
            long seed = parts.length > 1 ? Long.parseLong(parts[1]) : 1L;
            rnd = new Random(seed);
            List<String> cards = new ArrayList<>();
            for (String d : deck) { cards.add(d); cards.add(d); }
            while (cards.size() < faces.length) cards.add("mikiko");
            Collections.shuffle(cards, rnd);
            for (int i = 0; i < faces.length; i++) faces[i] = cards.get(i);
            seen++;
        }

        @Override public boolean isPauseScreen() { return false; }

        int x0() { return (width - (COLS * CW + (COLS - 1) * GAP)) / 2; }
        int y0() { return (height - (ROWS * CH + (ROWS - 1) * GAP)) / 2 + 10; }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (button != 0 || botTurn || wait > 0 || done) return true;
            for (int i = 0; i < faces.length; i++) {
                int cx = x0() + (i % COLS) * (CW + GAP), cy = y0() + (i / COLS) * (CH + GAP);
                if (mx >= cx && mx < cx + CW && my >= cy && my < cy + CH) { flip(i); break; }
            }
            return true;
        }

        /** You flip card {@code i}. */
        public void flip(int i) {
            if (botTurn || wait > 0 || done || i < 0 || i >= faces.length || up[i] || taken[i]) return;
            turn(i);
        }

        private void turn(int i) {
            up[i] = true;
            seenByBot.add(i);
            if (minecraft != null && minecraft.player != null) minecraft.player.playSound(net.minecraft.sounds.SoundEvents.BOOK_PAGE_TURN, 0.6F, 1.2F);
            if (first < 0) { first = i; return; }
            second = i;
            wait = 20;                                                          // (a look at both before they go back or stay)
        }

        @Override
        public void tick() {
            if (closeIn > 0) { if (--closeIn == 0) onClose(); return; }
            if (done) return;
            if (wait > 0) {
                if (--wait > 0) return;
                boolean pair = faces[first].equals(faces[second]);
                if (pair) {
                    taken[first] = taken[second] = true;
                    owner[first] = owner[second] = botTurn ? 2 : 1;
                    if (botTurn) theirs++; else mine++;
                    if (minecraft != null && minecraft.player != null) minecraft.player.playSound(net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP, 0.6F, 1.3F);
                } else { up[first] = up[second] = false; botTurn = !botTurn; }
                first = second = -1;
                if (mine + theirs >= faces.length / 2) { done = true; closeIn = 70; lastResult[0] = mine; lastResult[1] = theirs; ModNetwork.sendToServer(new PartyGameResultPacket(botId, mine, theirs)); return; }
                if (botTurn) wait = -12;                                         // (the bot thinks a moment)
                return;
            }
            if (wait < 0) { if (++wait < 0) return; }
            if (botTurn && first < 0) { turn(botPick(-1)); wait = -14; return; }
            if (botTurn && second < 0 && first >= 0) turn(botPick(first));
        }

        /** The bot's pick: a card it remembers that matches (if it remembers), else a card it hasn't seen, else any. */
        private int botPick(int other) {
            List<Integer> open = new ArrayList<>();
            for (int i = 0; i < faces.length; i++) if (!taken[i] && !up[i]) open.add(i);
            if (other >= 0) {
                for (int i : open) if (seenByBot.contains(i) && faces[i].equals(faces[other]) && rnd.nextFloat() < skill) return i;
            } else {
                for (int a : open) for (int b : open) if (a < b && seenByBot.contains(a) && seenByBot.contains(b) && faces[a].equals(faces[b]) && rnd.nextFloat() < skill) return a;
            }
            List<Integer> fresh = new ArrayList<>();
            for (int i : open) if (!seenByBot.contains(i)) fresh.add(i);
            List<Integer> from = fresh.isEmpty() ? open : fresh;
            return from.get(rnd.nextInt(from.size()));
        }

        @Override
        public void render(GuiGraphics g, int mx, int my, float pt) {
            com.xenowarriors.client.menu.Flat.background(this, g);
            int gw = COLS * CW + (COLS - 1) * GAP, gh = ROWS * CH + (ROWS - 1) * GAP;
            frame(g, x0() - 10, y0() - 34, gw + 20, gh + 44, 0xFF60D0FF);
            g.drawCenteredString(font, "MEMORY vs " + name.toUpperCase(), width / 2, y0() - 28, 0x9AE8FF);
            String turn = done ? (mine > theirs ? "YOU WIN!" : mine == theirs ? "A DRAW!" : name + " wins!") : botTurn ? name + "'s turn..." : "Your turn - flip two cards";
            g.drawCenteredString(font, "You " + mine + "  -  " + theirs + " " + name + "     " + turn, width / 2, y0() - 16, done && mine > theirs ? 0x60FF60 : 0xE0E8F0);
            for (int i = 0; i < faces.length; i++) {
                int cx = x0() + (i % COLS) * (CW + GAP), cy = y0() + (i / COLS) * (CH + GAP);
                boolean show = up[i] || taken[i];
                int edge = taken[i] ? (owner[i] == 1 ? 0xFF60D060 : 0xFFD06060) : !botTurn && !done && mx >= cx && mx < cx + CW && my >= cy && my < cy + CH ? 0xFFFFFFFF : 0xFF3A4A5A;
                g.fill(cx - 1, cy - 1, cx + CW + 1, cy + CH + 1, edge);
                if (show) {
                    g.fill(cx, cy, cx + CW, cy + CH, 0xFFF0EEE8);
                    g.blit(portrait(faces[i]), cx + 2, cy + 6, 0, 0, CW - 4, CW - 4, CW - 4, CW - 4);
                } else {
                    g.fill(cx, cy, cx + CW, cy + CH, 0xFF2A3A6A);
                    g.fill(cx + 4, cy + 4, cx + CW - 4, cy + CH - 4, 0xFF3A4E8A);
                    g.drawCenteredString(font, "XW", cx + CW / 2, cy + CH / 2 - 4, 0xFFD24A);
                }
            }
        }

        /** (Tests) where the pair of card {@code i} is (-1: none). */
        public int pairOfForTest(int i) { for (int j = 0; j < faces.length; j++) if (j != i && faces[j].equals(faces[i])) return j; return -1; }
        public boolean botTurnForTest() { return botTurn; }
        public boolean busyForTest() { return wait != 0; }
        public boolean doneForTest() { return done; }
        public int mineForTest() { return mine; }
        public int theirsForTest() { return theirs; }
        public boolean takenForTest(int i) { return taken[i]; }
    }
}

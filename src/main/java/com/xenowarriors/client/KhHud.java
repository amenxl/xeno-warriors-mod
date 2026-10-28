package com.xenowarriors.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.skill.Skill;
import com.xenowarriors.skill.SkillManager;
import com.xenowarriors.skill.SkillPool;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * Kingdom Hearts style HUD: the character portrait gauge bottom right (HP, Karma, food or Ki/stamina with DragonMineZ,
 * level badge, XP, KA orb, potion effects above it), the skill list bottom left (while holding Control, or always),
 * and the speech bubble when you chat.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class KhHud implements IGuiOverlay {
    private static final ResourceLocation BADGE = XenoWarriors.id("textures/gui/hud/badge.png"), ORB = XenoWarriors.id("textures/gui/hud/orb.png");
    private static float shownHp = -1, chip = -1;
    private static int chipDelay;
    private static float menuFade;

    public static boolean active() {
        Minecraft mc = Minecraft.getInstance();
        return ClientSettings.khHud() && mc.player != null && ClientCharacterData.getLocal() != CharacterType.NONE;
    }

    /** Hide what this HUD replaces: vanilla hearts/armor/food (unless asked for), effect icons, DragonMineZ's stat bars. */
    @SubscribeEvent
    public static void onOverlay(RenderGuiOverlayEvent.Pre event) {
        if (!active()) return;
        var id = event.getOverlay().id();
        boolean bars = id.equals(VanillaGuiOverlay.PLAYER_HEALTH.id()) || id.equals(VanillaGuiOverlay.ARMOR_LEVEL.id()) || id.equals(VanillaGuiOverlay.FOOD_LEVEL.id());
        if ((bars && !ClientSettings.vanillaBars()) || id.equals(VanillaGuiOverlay.POTION_ICONS.id())
                || (id.getNamespace().equals("dragonminez") && (id.getPath().equals("xenoversehud") || id.getPath().equals("alternativehud"))))
            event.setCanceled(true);
    }

    // ---------------------------------------------------------------- drawing helpers

    /** A slanted Kingdom Hearts bar that fills from the right (the portrait side). */
    public static void bar(GuiGraphics g, int x, int y, int w, int h, float frac, int color, float chipFrac) {
        int slant = Math.max(2, h / 2 + 1);
        for (int r = -1; r <= h; r++) {
            int off = (h - r) * slant / Math.max(1, h);
            g.fill(x + off - 1, y + r, x + w + off + 1, y + r + 1, 0xFF0A0A10);
        }
        for (int r = 0; r < h; r++) {
            int off = (h - r) * slant / Math.max(1, h);
            g.fill(x + off, y + r, x + w + off, y + r + 1, 0xFF1C1C28);
            if (chipFrac > frac) g.fill(x + off + Math.round(w * (1 - chipFrac)), y + r, x + w + off, y + r + 1, 0xFFFFE27A);
            int c = r < (h + 1) / 3 ? brighten(color) : color;
            g.fill(x + off + Math.round(w * (1 - Mth.clamp(frac, 0, 1))), y + r, x + w + off, y + r + 1, c);
        }
    }

    public static int brighten(int c) {
        int r = Math.min(255, (c >> 16 & 255) + 55), gg = Math.min(255, (c >> 8 & 255) + 55), b = Math.min(255, (c & 255) + 55);
        return 0xFF000000 | r << 16 | gg << 8 | b;
    }

    public static void small(GuiGraphics g, Font f, String s, float x, float y, int color, float scale) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1);
        g.drawString(f, s, 0, 0, color, true);
        g.pose().popPose();
    }

    public static void portrait(GuiGraphics g, ResourceLocation tex, int x, int y, int size, boolean redFlash) {
        RenderSystem.enableBlend();
        if (redFlash) HudFade.color(1F, 0.55F, 0.55F, 1F);
        g.blit(tex, x, y, size, size, 0, 0, 128, 128, 128, 128);
        HudFade.reset();
    }

    // ---------------------------------------------------------------- the HUD

    @Override
    public void render(ForgeGui gui, GuiGraphics g, float partial, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (!active() || mc.options.hideGui || mc.player.isSpectator()) return;
        var p = mc.player;
        Font font = mc.font;
        CharacterType type = ClientCharacterData.getLocal();
        int lift = HudLayout.skillBarLift();
        int size = 52, px = width - size - 6, py = height - size - 6 - lift;
        int col = type.getColor();

        float hp = p.getHealth(), max = Math.max(1F, p.getMaxHealth());
        HudFaces.health(p.getUUID(), hp, max);
        if (shownHp < 0) { shownHp = hp; chip = hp; }
        if (hp < chip) { if (chipDelay-- <= 0) chip = Math.max(hp, chip - max * 0.01F); } else { chip = hp; chipDelay = 12; }
        if (hp < shownHp) chipDelay = 12;
        shownHp = hp;
        boolean low = hp / max <= 0.25F, flash = low && (HudFaces.clock() / 6) % 2 == 0;

        if (ClientSettings.blazingHud()) {
            HudFade.begin();
            try { blazing(g, mc, font, p, type, width, height, lift, hp, max, low, flash, partial); } finally { HudFade.end(); }
            return;
        }
        rest(g, mc, font, p, type, width, height, lift, size, px, py, col, hp, max, low, flash, partial);
    }

    private void blazing(GuiGraphics g, Minecraft mc, Font font, net.minecraft.client.player.LocalPlayer p, CharacterType type,
                         int width, int height, int lift, float hp, float max, boolean low, boolean flash, float partial) {
        {
            boolean dbz0 = DmzHud.read(p);
            int[] at = BlazingHud.render(g, mc, font, type, width, height, lift, hp, max, chip, low, flash, dbz0,
                    HudFaces.face(p.getUUID(), hp, max, HudFaces.allOut(p), false));
            if (type == CharacterType.ULTIMA) {         // his output %, above the emblem
                int pc = UltimaClient.percent(p.getId());
                boolean ov = UltimaClient.overload(p.getId());
                String ot = ov ? "OVERLOAD" : "OUTPUT " + pc + "%";
                int oc = ov ? ((HudFaces.clock() / 3) % 2 == 0 ? 0x9CFFC8 : 0xFFFFFF) : 0x3FFF8C;
                small(g, font, ot, at[0] + at[2] / 2F - font.width(ot) * 0.3F, at[1] - (dbz0 ? 25 : 16), oc, 0.6F);   // (above the DragonMineZ form line)
            }
            var ka0 = ClientSkillState.ka();
            int ox = at[0] + at[2] + 2, oy = at[1] - 10;                   // KA orb: top right of the emblem
            g.blit(ORB, ox, oy, 14, 14, 0, 0, 32, 32, 32, 32);
            String kl0 = ka0 == null ? (type == CharacterType.ULTIMA ? "O" : "?") : ka0.title().substring(0, 1);
            small(g, font, kl0, ox + 7 - font.width(kl0) * 0.3F, oy + 4, ka0 == null ? 0xB070FF : ka0.color, 0.6F);
            effects(g, mc, at[3], at[4] - 14, width - 4);
            bubble(g, font, p, at[0], at[1]);
            commandMenu(g, mc, font, type, height, partial);
        }
    }

    private void rest(GuiGraphics g, Minecraft mc, Font font, net.minecraft.client.player.LocalPlayer p, CharacterType type,
                      int width, int height, int lift, int size, int px, int py, int col, float hp, float max, boolean low, boolean flash, float partial) {
        // bars, left of the portrait
        int hotbarRight = width / 2 + 91;
        int bw = Mth.clamp(px - 10 - hotbarRight - 8, 50, 120);
        int bx = px - 6 - bw, by = py + 8;
        bar(g, bx, by, bw, 7, hp / max, low ? (flash ? 0xFFFF5040 : 0xFFD03A30) : 0xFF46D75A, chip / max);
        small(g, font, Math.round(hp) + " / " + Math.round(max), bx + bw - 2 - font.width(Math.round(hp) + " / " + Math.round(max)) * 0.55F, by + 1.2F, 0xFFFFFF, 0.55F);
        small(g, font, "HP", bx - 11, by, 0xE6E6F0, 0.6F);
        float karma = ClientSkillState.karma(), kmax = Math.max(1F, ClientSkillState.maxKarma());
        int sealed = ClientKarmaLock.secondsLeft();
        bar(g, bx + 6, by + 10, bw - 6, 5, karma / kmax, sealed > 0 ? 0xFF6A5A70 : 0xFF5A96FF, 0);
        if (sealed > 0) small(g, font, "DRAINED " + sealed + "s", bx + 10, by + 10.5F, 0xFF8A8A, 0.5F);
        small(g, font, UltimaAnim.resource().toUpperCase(), bx - (type == CharacterType.ULTIMA ? 14 : 21), by + 10, 0xC8D8FF, 0.5F);
        small(g, font, Math.round(karma) + "", bx + bw - 2 - font.width(Math.round(karma) + "") * 0.5F, by + 10.5F, 0xFFFFFF, 0.5F);
        int y = by + 18;
        boolean dbz = DmzHud.read(p);
        if (dbz) {
            bar(g, bx + 12, y, bw - 12, 5, DmzHud.ki / Math.max(1F, DmzHud.maxKi), 0xFFFFCD3C, 0);
            small(g, font, "KI", bx + 1, y, 0xFFE08A, 0.5F);
            String kt = Math.round(DmzHud.ki) + "/" + Math.round(DmzHud.maxKi);
            small(g, font, kt, bx + bw - 2 - font.width(kt) * 0.5F, y + 0.5F, 0xFFFFFF, 0.5F);
            y += 8;
            bar(g, bx + 18, y, bw - 18, 3, DmzHud.sta / Math.max(1F, DmzHud.maxSta), 0xFF8CE678, 0);
            small(g, font, "STAMINA", bx - 14, y - 1, 0xC0F0B0, 0.45F);
            String fl = DmzHud.form + "  " + DmzHud.powerRelease + "%";
            small(g, font, fl, px + size / 2F - font.width(fl) * 0.275F, py - 7, 0xFFD25A, 0.55F);
        } else if (type != CharacterType.ULTIMA) {                     // Ultima has no hunger
            bar(g, bx + 16, y, bw - 16, 3, BigHungerHud.max() > 20 && BigHungerHud.total() >= 0 ? BigHungerHud.total() / (float) BigHungerHud.max() : p.getFoodData().getFoodLevel() / 20F, 0xFFF0AA50, 0);
            small(g, font, "FOOD", bx - 6, y - 1, 0xF5D2A0, 0.45F);
        }
        // XP toward the next level: a thin cyan line under the bars
        g.fill(bx + 20, y + 6, bx + bw, y + 7, 0xFF203040);
        g.fill(bx + 20, y + 6, bx + 20 + Math.round((bw - 20) * LevelHud.progress()), y + 7, 0xFF78E6FF);

        // portrait + level badge + name
        String face = HudFaces.face(p.getUUID(), hp, max, HudFaces.allOut(p), false);
        if (HudFaces.hasArt(type)) portrait(g, HudFaces.texture(type, face), px, py, size, flash);
        else HudHead.draw(g, p, type, px, py, size, flash);
        g.blit(BADGE, px - 3, py + size - 15, 16, 16, 0, 0, 32, 32, 32, 32);
        String lv = String.valueOf(LevelHud.level());
        small(g, font, lv, px + 5 - font.width(lv) * (lv.length() > 2 ? 0.25F : 0.3F), py + size - 10, 0x201408, lv.length() > 2 ? 0.5F : 0.6F);
        String nm = type.getDisplayName().getString();
        small(g, font, nm, px + size / 2F - font.width(nm) * 0.3F, py + size - 1, col, 0.6F);
        if (type == CharacterType.ULTIMA) {         // his output %, above the portrait
            int pc = UltimaClient.percent(p.getId());
            boolean ov = UltimaClient.overload(p.getId());
            String ot = ov ? "OVERLOAD" : "OUTPUT " + pc + "%";
            int oc = ov ? ((HudFaces.clock() / 3) % 2 == 0 ? 0x9CFFC8 : 0xFFFFFF) : 0x3FFF8C;
            small(g, font, ot, px + size / 2F - font.width(ot) * 0.3F, py - (dbz ? 14 : 7), oc, 0.6F);
            for (int i = 0; i < 4; i++)
                g.fill(px + 6 + i * 11, py - (dbz ? 6 : 1) - 2, px + 15 + i * 11, py - (dbz ? 6 : 1), i <= UltimaClient.tier(p.getId()) || ov ? 0xFF3FFF8C : 0xFF203028);
        }
        // KA orb (super ultimate)
        var ka = ClientSkillState.ka();
        g.blit(ORB, px - 20, py + size - 16, 14, 14, 0, 0, 32, 32, 32, 32);
        String kl = ka == null ? (type == CharacterType.ULTIMA ? "O" : "?") : ka.title().substring(0, 1);
        small(g, font, kl, px - 13 - font.width(kl) * 0.3F, py + size - 12, ka == null ? 0xB070FF : ka.color, 0.6F);

        effects(g, mc, bx, by - 14, px + size);
        bubble(g, font, p, px, py);
        commandMenu(g, mc, font, type, height, partial);
    }

    /** Potion effects in a row above the health bars. */
    private static void effects(GuiGraphics g, Minecraft mc, int left, int y, int right) {
        List<MobEffectInstance> list = new ArrayList<>();
        for (MobEffectInstance e : mc.player.getActiveEffects()) if (e.showIcon()) list.add(e);
        if (list.isEmpty()) return;
        var tex = mc.getMobEffectTextures();
        int x = right - 12;
        for (MobEffectInstance e : list) {
            if (x < left - 10) break;
            g.fill(x - 1, y - 1, x + 11, y + 11, e.getEffect().isBeneficial() ? 0xAA1E3A24 : 0xAA3A1E1E);
            var sprite = tex.get(e.getEffect());
            float alpha = e.endsWithin(200) ? (e.getDuration() / 10 % 2 == 0 ? 0.4F : 1F) : 1F;
            HudFade.color(1F, 1F, 1F, alpha);
            g.blit(x, y, 0, 10, 10, sprite);
            HudFade.reset();
            if (e.getAmplifier() > 0) small(g, mc.font, String.valueOf(e.getAmplifier() + 1), x + 7, y + 6, 0xFFFFFF, 0.5F);
            x -= 13;
        }
    }

    /** A short chat line in a bubble by the portrait's mouth. */
    private static void bubble(GuiGraphics g, Font font, net.minecraft.world.entity.player.Player p, int px, int py) {
        String b = HudFaces.bubble(p.getUUID());
        if (b == null) return;
        float sc = 0.7F;
        int w = Math.round(font.width(b) * sc) + 8, h = 12;
        int x = px - w + 6, y = py - h - 2;
        g.fill(x, y, x + w, y + h, 0xF0FAFAFF);
        g.fill(x + 1, y - 1, x + w - 1, y, 0xF0FAFAFF);
        g.fill(x + 1, y + h, x + w - 1, y + h + 1, 0xF0FAFAFF);
        for (int i = 0; i < 4; i++) g.fill(x + w - 8 + i, y + h + 1 + i, x + w - 4, y + h + 2 + i, 0xF0FAFAFF);   // tail toward his mouth
        g.pose().pushPose();
        g.pose().translate(x + 4, y + 3, 0);
        g.pose().scale(sc, sc, 1);
        g.drawString(font, b, 0, 0, 0x14141E, false);
        g.pose().popPose();
    }

    /** Skill list bottom left - shown while holding Control (or always, in settings). */
    private static void commandMenu(GuiGraphics g, Minecraft mc, Font font, CharacterType type, int height, float partial) {
        boolean show = (ClientSettings.skillsAlways() || ModKeyMappings.ctrlDown()) && mc.screen == null;
        menuFade = Mth.clamp(menuFade + (show ? 0.25F : -0.2F), 0F, 1F);
        if (menuFade <= 0F) return;
        int a = Math.round(menuFade * 255);
        int rowH = 13, w = 118, x = 4 - Math.round((1 - menuFade) * 20);
        int rows = SkillManager.SLOTS + 1;
        int top = height - 34 - rows * (rowH + 2) - 14;
        g.fill(x, top, x + w, top + 12, alpha(0xFF19285A, a));
        g.fill(x, top + 11, x + w, top + 12, alpha(0xFF8CBEFF, a));
        small(g, font, "SKILLS", x + 5, top + 3, alpha(0xC8E1FF, a), 0.65F);
        small(g, font, UltimaAnim.resource(), x + w - 22, top + 3.5F, alpha(0x96BEFF, a), 0.5F);
        int[] loadout = ClientSkillState.loadout();
        int y = top + 14;
        for (int s = 0; s <= SkillManager.SLOTS; s++) {
            boolean ult = s == SkillManager.ULTIMATE_SLOT, sup = s == SkillManager.SUPER_SLOT;
            Skill sk = sup ? null : SkillPool.inSlot(type, loadout, s);
            var ka = ClientSkillState.ka();
            int border = sup ? 0xFF8A3CC8 : ult ? 0xFFFFD24A : 0xFF5A6EA0;
            g.fill(x, y, x + w, y + rowH, alpha(border, a));
            g.fill(x + 1, y + 1, x + w - 1, y + rowH - 1, alpha(0xFF1E1E2E, a));
            float cd = ClientSkillState.cooldownFraction(s);
            if (cd > 0) g.fill(x + 1, y + 1, x + 1 + Math.round((w - 2) * cd), y + rowH - 1, alpha(0xFF000000, a));
            var km = sup ? ModKeyMappings.SUPER_ULT : ModKeyMappings.SKILLS[s];
            String key = km.getKey().getDisplayName().getString();
            if (key.length() > 2) key = key.substring(0, 2);
            g.fill(x + 2, y + 2, x + 13, y + rowH - 2, alpha(0xFF0F0F19, a));
            small(g, font, key, x + 7.5F - font.width(key) * 0.3F, y + 4, alpha(0xDDDDDD, a), 0.6F);
            boolean xb = sup && com.xenowarriors.skill.XenoBlast.has(type);
            String name = xb ? "Xeno Blast (last resort)" : sup ? (ka == null ? (type == CharacterType.ULTIMA ? "Overload (100%)" : "KA - ???") : "KA - " + ka.title()) : sk == null ? "-" : sk.getName().getString() + (ult ? " ★" : "");
            int nx = x + 16;
            if (a > 200 && (xb || sk != null)) {
                if (xb) SkillIcons.draw(g, SkillIcons.xenoBlast(type), nx, y + 1, rowH - 2);
                else if (!SkillIcons.draw(g, sk.getId(), nx, y + 1, rowH - 2)) nx -= rowH;
                nx += rowH;
            }
            small(g, font, name, nx, y + 3.5F, alpha(sup ? 0xC896FF : ult ? 0xFFF0C8 : 0xFFFFFF, a), 0.65F);
            String right = cd > 0 ? (ClientSkillState.cooldownTicks(s) + 19) / 20 + "s" : sk != null ? String.valueOf(sk.getKarmaCost()) : "";
            boolean afford = sk == null || mc.player.isCreative() || ClientSkillState.karma() >= sk.getKarmaCost();
            small(g, font, right, x + w - 4 - font.width(right) * 0.6F, y + 3.5F, alpha(cd > 0 ? 0xFF6E6E : afford ? 0x8CC8FF : 0xFF5555, a), 0.6F);
            y += rowH + 2;
        }
    }

    private static int alpha(int color, int a) { return (Math.min(a, color >>> 24 == 0 ? 255 : color >>> 24) << 24) | (color & 0xFFFFFF); }
}

package com.xenowarriors.mixin;

import com.xenowarriors.client.test.UiTextRecorder;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** (UI tests only) where each piece of text is drawn - for finding text that overlaps. Does nothing unless a test is recording. */
@Mixin(GuiGraphics.class)
public abstract class GuiTextRecordMixin {
    @Inject(method = "enableScissor(IIII)V", at = @At("HEAD"), require = 0)
    private void xw$scissorOn(int x0, int y0, int x1, int y1, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if (UiTextRecorder.on) UiTextRecorder.CLIP.push(new int[]{x0, y0, x1, y1});
    }

    @Inject(method = "disableScissor()V", at = @At("HEAD"), require = 0)
    private void xw$scissorOff(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if (UiTextRecorder.on && !UiTextRecorder.CLIP.isEmpty()) UiTextRecorder.CLIP.pop();
    }

    @Inject(method = "drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)I", at = @At("HEAD"), require = 0)
    private void xw$recordString(Font font, String text, int x, int y, int color, boolean shadow, CallbackInfoReturnable<Integer> cir) {
        if (UiTextRecorder.on && text != null && !text.isBlank()) UiTextRecorder.add((GuiGraphics) (Object) this, font, text, font.width(text), x, y, color);
    }

    @Inject(method = "drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;IIIZ)I", at = @At("HEAD"), require = 0)
    private void xw$recordSeq(Font font, FormattedCharSequence text, int x, int y, int color, boolean shadow, CallbackInfoReturnable<Integer> cir) {
        if (!UiTextRecorder.on || text == null) return;
        StringBuilder sb = new StringBuilder();
        text.accept((i, style, cp) -> { sb.appendCodePoint(cp); return true; });
        if (!sb.toString().isBlank()) UiTextRecorder.add((GuiGraphics) (Object) this, font, sb.toString(), font.width(text), x, y, color);
    }
}

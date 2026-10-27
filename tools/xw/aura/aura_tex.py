"""DBZ-style aura flame texture: 8 looping frames (64x128 each) in one 512x128 strip, white/grey (tinted in game).
A teardrop of energy hugging the body, flame tongues licking up off it into spikes at the top, a bright rim,
softer inside, and thin energy streaks racing upward."""
import numpy as np
from PIL import Image
import os, sys

W, H, F = 64, 128, 8
OUT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(__file__), '../../../src/main/resources/assets/xenowarriors/textures/entity/aura/dbz_aura.png')

def frame(f):
    t = f / F
    xs = (np.arange(W) + 0.5) / W * 2 - 1           # -1..1
    vs = 1 - (np.arange(H) + 0.5) / H               # 0 bottom .. 1 top
    u, v = np.meshgrid(xs, vs)
    au = np.abs(u)
    # body-hugging teardrop: round at the feet, widest around the chest, closing to a point high above the head
    hw = np.where(v < 0.42, 0.52 + 0.26 * np.sqrt(np.clip(1 - ((v - 0.42) / 0.5) ** 2, 0, 1)),
                  0.78 * np.clip(1 - (v - 0.42) / 0.58, 0, 1) ** 0.75)
    hw = np.maximum(hw, 1e-3)
    tp = 2 * np.pi * t
    # flame noise scrolling upward (integer time multiples so the 8 frames loop)
    n = (0.55 * np.sin(u * 9.0 + v * 7.0 - tp * 2 + 1.3) +
         0.35 * np.sin(u * 17.0 - v * 13.0 - tp * 3 + 0.4) +
         0.30 * np.sin(u * 5.0 + v * 21.0 - tp * 4 + 2.2) +
         0.20 * np.sin(u * 27.0 + v * 31.0 - tp * 5))
    # tongues: stronger toward the top so the outline breaks into spikes
    d = au / hw - n * (0.10 + 0.32 * v ** 1.4)
    # jagged DBZ spikes: flame points shooting up off the shoulders and head, swaying as they flicker
    spike = np.maximum(0, np.cos(u * np.pi * 2.6 + 0.35 * np.sin(tp * 2 + v * 6))) ** 4
    d -= 2.2 * np.clip(v - 0.38, 0, 1) * spike * (0.75 + 0.25 * np.sin(tp + u * 3))
    inside = np.clip((1.0 - d) / 0.12, 0, 1)
    rim = np.exp(-((d - 0.88) / 0.11) ** 2)
    body = 0.32 + 0.38 * np.clip(d, 0, 1) ** 2       # dimmer in the middle so the character stays visible
    val = inside * body + rim * 0.75 * (d < 1.15)
    # spikes above the head: separate flame tips flickering off the top
    tips = np.zeros_like(val)
    for k, (cx, ph) in enumerate([(-0.32, 0.0), (0.0, 0.33), (0.3, 0.66), (-0.12, 0.5), (0.17, 0.15)]):
        life = (t + ph) % 1.0
        cy = 0.72 + 0.25 * life
        tw = 0.11 * (1 - life)
        g = np.exp(-((u - cx - 0.05 * np.sin(tp + k)) / max(tw, 1e-3)) ** 2) * np.exp(-((v - cy) / 0.09) ** 2)
        tips += g * (1 - life) * 0.9
    val = np.maximum(val, tips * (v > 0.55))
    # energy streaks racing up inside the aura
    for k, (cx, ph) in enumerate([(-0.45, 0.1), (0.42, 0.6), (-0.15, 0.35), (0.2, 0.85), (0.55, 0.3), (-0.6, 0.7)]):
        head = (t * 2 + ph) % 1.0 * 1.3 - 0.1
        streak = np.exp(-((u - cx) / 0.025) ** 2) * np.clip(1 - (head - v) / 0.28, 0, 1) * (v < head) 
        val += streak * 0.8 * inside
    # fade out at the very bottom and very top
    val *= np.clip(v / 0.06, 0, 1) * np.clip((1.02 - v) / 0.08, 0, 1)
    val = np.clip(val, 0, 1)
    return (val * 255).astype(np.uint8)

img = np.zeros((H, W * F, 4), np.uint8)
for f in range(F):
    a = frame(f)
    img[:, f * W:(f + 1) * W, 0:3] = a[..., None]
    img[:, f * W:(f + 1) * W, 3] = a
os.makedirs(os.path.dirname(OUT), exist_ok=True)
Image.fromarray(img, 'RGBA').save(OUT)
# the same with white colour and the shape in alpha only (for the translucent layer that keeps its colour in daylight)
img2 = img.copy(); img2[..., 0:3] = 255
Image.fromarray(img2, 'RGBA').save(OUT.replace('dbz_aura.png', 'dbz_aura_a.png'))
# preview on dark bg, tinted orange
p = img[..., :3].astype(float) / 255
prev = (np.stack([p[..., 0], p[..., 1] * 0.55, p[..., 2] * 0.15], -1) * 255).astype(np.uint8)
Image.fromarray(prev).resize((W * F * 3, H * 3), Image.NEAREST).save('/tmp/claude-0/-home-claude/b2fe60cb-fd40-5a94-8ee2-7d1a501fd998/scratchpad/aura_prev.png')
print(OUT)

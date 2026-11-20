"""The extra hunger layers' drumstick, drawn to be tinted: a light grey meat mask (row 1) and the dark outline and bone,
never tinted (row 2). Full at x 0, half (bitten) at x 9."""
from PIL import Image
ART = ["....ooo..",
       "...ohhmo.",
       "..ohhmmmo",
       "..ommmmso",
       "..ommmsso",
       ".oommsoo.",
       "obboooo..",
       "obbo.....",
       ".oo......"]
MEAT = {'h': (255, 255, 255), 'm': (215, 215, 215), 's': (150, 150, 150)}
im = Image.new('RGBA', (18, 18), (0, 0, 0, 0)); px = im.load()
for y, row in enumerate(ART):
    for x, c in enumerate(row):
        for half in (0, 1):
            ox = 9 * half
            bitten = half and x + (8 - y) > 9                    # (the half one: its top right eaten)
            if c in MEAT and not bitten: px[ox + x, y] = MEAT[c] + (255,)
            if c == 'o' and not (bitten and y < 5): px[ox + x, 9 + y] = (36, 22, 16, 255)
            if c == 'b': px[ox + x, 9 + y] = (238, 232, 220, 255)
im.save('/home/claude/xeno-warriors-mod/src/main/resources/assets/xenowarriors/textures/gui/hunger_layer.png')

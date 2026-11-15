"""
Faces for DragonMineZ moments, made for every character and every face set (their own, the drawn set's not needed, and
the Super Saiyan sets): each is one face's eyes and brows (rows 0-21) over another face's mouth (rows 22 on).
  powerup    powering up / charging Ki      - fierce eyes, yelling
  beam       firing a Ki attack             - focused eyes, yelling
  strain     the Gravity minigame, gravity  - concentrating eyes, gritted / strained mouth
  meditate   the God Ritual, sleep recovery  - eyes shut, calm mouth
  rhythm     the Rhythm minigame             - focused eyes, humming
  transform  the moment they transform       - raging eyes, screaming
Run: python3 dmz_faces.py
"""
import glob, os
from PIL import Image
CH = '/home/claude/xeno-warriors-mod/src/main/resources/assets/xenowarriors/textures/entity/character/'
RECIPES = {'powerup': ('fierce', 'shout'), 'beam': ('focused', 'shout'), 'strain': ('concentrate', 'strained'),
           'meditate': ('sleep', 'calm'), 'rhythm': ('focused', 'hum'), 'transform': ('rage', 'shout')}
CUT = 22

def face(set_, e):
    p = CH + set_ + '_face_' + e + '.png'
    return Image.open(p).convert('RGBA') if os.path.exists(p) else None

sets = sorted({os.path.basename(f)[:-len('_face.png')] for f in glob.glob(CH + '*_face.png')})
sets = [s for s in sets if not s.endswith('_drawn') and not s.startswith('fusion_') and s not in ('ultima_prototype',)]
n = 0
for s in sets:
    for name, (eyes, mouth) in RECIPES.items():
        a, b = face(s, eyes), face(s, mouth)
        if a is None or b is None or a.size != b.size: continue
        out = a.copy()
        out.paste(b.crop((0, CUT, b.size[0], b.size[1])), (0, CUT))
        out.save(CH + s + '_face_' + name + '.png'); n += 1
print(len(sets), 'sets,', n, 'faces')

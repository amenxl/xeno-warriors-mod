import sys, json
sys.path.insert(0, '/home/claude/xw/outfits'); sys.path.insert(0, '/home/claude/xw/anim')
from kit import *
import clip
def show(outfit, anim, times, out):
    m = json.load(open(RES + f'models/detail/{outfit}.json'))
    skin = Image.open(CHAR + outfit + '.png').convert('RGBA'); det = Image.open(CHAR + outfit + '_details.png').convert('RGBA')
    face = Image.open(CHAR + 'rhalix_face.png').convert('RGBA')
    rows = []
    for t in times:
        p = clip.pose_of(anim, t) if isinstance(anim, str) else anim(t)
        pose = {b: v[0] for b, v in p.items()}
        rows.append(preview(m, skin, det, face, yaws=(0, 50, 90), scale=6, pose=pose))
    W = rows[0].width; H = sum(r.height for r in rows)
    sheet = Image.new('RGB', (W, H)); y = 0
    for r in rows: sheet.paste(r, (0, y)); y += r.height
    sheet.save(out)
if __name__ == '__main__':
    show(sys.argv[1], sys.argv[2], [float(x) for x in sys.argv[3].split(',')], sys.argv[4])

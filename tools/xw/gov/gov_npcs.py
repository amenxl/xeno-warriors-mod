"""Government people: workers (lab coats, tech squad) and C2 guards (helmet with visor, armored vest, pads)."""
import sys, os
sys.path.insert(0, '/home/claude/xw/npcs'); sys.path.insert(0, '/home/claude/xw/outfits'); sys.path.insert(0, '/home/claude/xw/detail'); sys.path.insert(0, '/home/claude/xw/ultima')
import npcs
from npcs import Fit, pack, hair, tee, pants, shoes, shell, clothes, paint_skin, face_img, fringe_locks, crown_spikes, export, TEX, MOD
from PIL import Image, ImageDraw
import ultima as U

W, WD, NAVY, NAVY2, GRY, VIS, BLUE = '#eef0f4', '#c8ccd6', '#1c2336', '#2a344e', '#5a6070', '#3ad8ff', '#2a6cd8'
SPECS = {}
SPECS['gov_worker_1'] = dict(skin='#e9c2a0', eyes='#4a6a8a', hair=['#2a2a30', '#1c1c22'],
    paint=clothes('#d8dce4', 4, '#2e3240', 12, '#1a1a20', 2), mouth=['.kkkkk.'], mouth_x=12, mouth_y=24)
def worker1(f):
    hair(f, '#2a2a30', '#1c1c22', locks=fringe_locks(5, 31, 7, 2.6, 1.4, 0.2))
    shell(f, 'body', [-4, 9.0, -2], [4, 24, 2], 0.42, W, {'front': lambda x, y: '#b8bcc8' if abs(x - .5) < .04 else (BLUE if .15 < x < .3 and .2 < y < .3 else W)})   # lab coat
    f.both('right_arm', [3.5, 13.2, -2.5], [8.5, 24.5, 2.5], W, {'front': lambda x, y: WD if y > .92 else W})
    f.add('body', [-1.2, 23.4, -2.7], [1.2, 24.6, -2.3], '#d8dce4')
    for s in (1, -1): f.add('body', [s * 0.6 if s > 0 else -2.8, 14.5, -2.8], [2.8 if s > 0 else -0.6, 16.0, -2.4], WD)   # pockets
    f.add('head', [-4.3, 28.0, -4.9], [4.3, 28.8, -4.5], '#3a3a44'); f.add('head', [-3.2, 27.2, -5.0], [-0.6, 28.8, -4.7], '#9ad8f0'); f.add('head', [0.6, 27.2, -5.0], [3.2, 28.8, -4.7], '#9ad8f0')   # glasses
    pants(f, '#2e3240', 1.6); shoes(f, '#1a1a20', '#0e0e12')
SPECS['gov_worker_1']['fit'] = worker1
SPECS['gov_worker_2'] = dict(skin='#b9835e', eyes='#3a2a1a', hair=['#5a3a22', '#3e2616'],
    paint=clothes('#d8dce4', 4, '#34384a', 12, '#22222a', 2), mouth=['.kkkkk.'], mouth_x=12, mouth_y=24)
def worker2(f):
    hair(f, '#5a3a22', '#3e2616', back_len=22, side_len=24, locks=fringe_locks(6, 31, 7.5, 3, 1.4, 0.2) + [([s * 4.6, 29, z], [s * 0.2, -1, 0.2], 6, 1.6) for s in (1, -1) for z in (-1, 2)])
    shell(f, 'body', [-4, 9.0, -2], [4, 24, 2], 0.42, W, {'front': lambda x, y: '#b8bcc8' if abs(x - .5) < .04 else W})
    f.both('right_arm', [3.5, 13.2, -2.5], [8.5, 24.5, 2.5], W)
    f.add('body', [1.0, 18.0, -2.8], [3.0, 20.6, -2.4], '#f4f4f8', {'front': lambda x, y: BLUE if y < .3 else '#f4f4f8'})   # ID badge
    f.add('right_arm', [3.6, 12.4, -3.2], [8.4, 13.4, 1.0], '#3a3a44')     # clipboard under the arm
    pants(f, '#34384a', 1.6); shoes(f, '#22222a', '#0e0e12')
SPECS['gov_worker_2']['fit'] = worker2

def guard_base(f, accent):
    # helmet: full shell, a visor across the eyes, ear pieces (like the C2 guards in the manga)
    f.add('head', [-4.7, 27.2, -4.7], [4.7, 33.0, 4.7], NAVY, {'top': lambda x, y: NAVY2 if abs(x - .5) < .08 else NAVY})
    f.add('head', [-4.2, 27.0, -5.05], [4.2, 29.0, -4.6], VIS, {'front': lambda x, y: '#c8f6ff' if x < .25 and y < .5 else VIS})
    for s in (1, -1): f.add('head', [s * 4.6 if s > 0 else -5.2, 26.0, -1.6], [5.2 if s > 0 else -4.6, 29.6, 1.6], GRY)
    f.add('head', [-4.7, 24.8, 3.6], [4.7, 27.4, 4.7], NAVY)
    # armored vest with plates and a collar
    shell(f, 'body', [-4, 12.2, -2], [4, 24, 2], 0.5, NAVY2, {'front': lambda x, y: NAVY if int(y * 6) % 2 == 0 and .15 < x < .85 else NAVY2})
    f.add('body', [-2.8, 15.2, -2.95], [2.8, 21.8, -2.45], NAVY, {'front': lambda x, y: accent if abs(y - .15) < .06 else NAVY})
    f.add('body', [-3.0, 23.2, -2.9], [3.0, 25.0, 2.9], NAVY)
    shell(f, 'body', [-4, 12.0, -2], [4, 13.2, 2], 0.62, '#14161e')
    f.add('body', [-3.6, 12.6, -2.9], [-2.0, 14.2, -2.5], GRY); f.add('body', [2.0, 12.6, -2.9], [3.6, 14.2, -2.5], GRY)     # belt pouches
    f.both('right_arm', [3.3, 20.4, -2.8], [8.7, 24.9, 2.8], NAVY, {'right': lambda x, y: accent if .4 < y < .55 else NAVY})   # shoulder pads
    f.both('right_arm', [3.6, 12.2, -2.4], [8.4, 16.2, 2.4], '#14161e')                       # gloves/forearm guards
    pants(f, '#22283a', 1.6); shoes(f, '#0e1018', '#060608', 2.6)
    f.both('right_leg', [0.2, 4.2, -2.9], [3.8, 7.0, -2.45], NAVY)                            # knee pads
SPECS['gov_guard_1'] = dict(skin='#d9a882', eyes='#2a2a2a', hair=['#1a1a1a', '#121212'], paint=clothes(NAVY2, 12, '#22283a', 12, '#0e1018', 3), mouth=['.kkkkk.'], mouth_x=12, mouth_y=25)
SPECS['gov_guard_1']['fit'] = lambda f: guard_base(f, '#d8c040')
SPECS['gov_guard_2'] = dict(skin='#8a5a3a', eyes='#2a2a2a', hair=['#1a1a1a', '#121212'], paint=clothes(NAVY2, 12, '#22283a', 12, '#0e1018', 3), mouth=['kkkkkk'], mouth_x=13, mouth_y=25)
SPECS['gov_guard_2']['fit'] = lambda f: (guard_base(f, '#d84040'), f.add('body', [-4.8, 14, 2.4], [4.8, 22, 3.6], '#14161e'))   # C2 backpack

if __name__ == '__main__':
    tiles = []
    for key, spec in SPECS.items():
        fit = Fit(); spec['fit'](fit)
        atlas = Image.new('RGBA', (256, 256), (0, 0, 0, 0)); pack(fit, atlas, region=(0, 0, 256, 256))
        skin = paint_skin(spec); face = face_img(spec)
        skin.save(TEX + key + '.png'); atlas.save(TEX + key + '_details.png'); face.save(TEX + key + '_face.png')
        mj = export(fit, MOD + key + '.json')
        im = U.preview(mj, skin, atlas, face, yaws=(20, 180), scale=6)
        ImageDraw.Draw(im).text((6, 6), key, fill=(255, 255, 255)); tiles.append(im)
        print(key, len(fit.cubes))
    tw, th = tiles[0].size
    sheet = Image.new('RGB', (tw * 2, th * 2), (30, 30, 36))
    for i, t in enumerate(tiles): sheet.paste(t, ((i % 2) * tw, (i // 2) * th))
    sheet.save('/mnt/user-data/outputs/gov_people.png')

import sys, json, math
sys.path.insert(0, '/home/claude/xw/detail')
from core import *
import mikiko, rhalix
from PIL import Image

OUT = '/mnt/user-data/outputs/blockbench/'
TEX = '/home/claude/xw/tex/'

def run(mod, keep):
    m = mod.build()
    img, size = pack_and_paint(m.cubes)
    img.save(f'/home/claude/xw/tex/{m.name}_details.png')
    n = write_bbmodel(m, Image.open(TEX + m.name + '.png'), img, OUT + m.name + '.bbmodel', keep,
                      Image.open(TEX + m.name + '_face.png'))
    json.dump({'size': size, 'cubes': m.cubes, 'groups': m.extra_groups, 'keep': keep},
              open(f'/home/claude/xw/detail/{m.name}_spec.json', 'w'))
    print(m.name, 'elements', n, 'detail cubes', len(m.cubes), 'atlas', size)
    return m

if __name__ == '__main__':
    run(mikiko, [])
    run(rhalix, [])

import sys
sys.path.insert(0,'.')
import sp_chars as S
from PIL import Image
key=sys.argv[1]
mj, fit, atlas, skin = S.export(key, f'/tmp/sp_{key}.json')
fc=S.face(S.CHARS[key])
im=S.U.preview(mj, skin, atlas, fc, yaws=(0,30), scale=14, wide=22, tall=46, top=12)
im.save('/tmp/zoom.png'); print(im.size)

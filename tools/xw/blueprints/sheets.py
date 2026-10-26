import json, textwrap
from PIL import Image, ImageDraw, ImageFont
F = '/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'; FB = '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf'
def font(s, b=False):
    try: return ImageFont.truetype(FB if b else F, s)
    except Exception: return ImageFont.load_default()
chars = json.load(open('/tmp/s3_chars.json'))
by = {c['name'].split()[0]: c for c in chars}
IN_MOD = {'Mikiko': {'Netsu': 'Netsu skill', 'Heku': 'Hekublade + Scatter', 'Karma sensing': 'Karma Sense', 'Fox King mode': 'Fox King mode', 'Corrupted state': 'corrupted look'},
          'Rhalix': {'Kshi': 'Kishi skill', 'Gravity element': 'Gravity Barrage/Compress, Bang', 'Flight': 'Rhalix flight', 'Karma sensing': 'Karma Sense'}}
BG, PANEL, INK, DIM, OK, IDEA = (24, 22, 34), (38, 35, 52), (236, 232, 245), (160, 155, 180), (110, 220, 140), (255, 190, 90)

def wrap(d, xy, text, f, fill, width):
    x, y = xy
    for line in textwrap.wrap(text, width):
        d.text((x, y), line, font=f, fill=fill); y += f.size + 4
    return y

def blueprint(key, render, accent):
    c = by[key]; W, H = 1400, 1280
    im = Image.new('RGB', (W, H), BG); d = ImageDraw.Draw(im)
    d.rectangle((0, 0, W, 70), fill=accent)
    d.text((24, 16), f"{c['name']} — Season 3 blueprint", font=font(32, True), fill=(20, 15, 30))
    r = Image.open(render).convert('RGB'); r = r.resize((int(r.width * 1.4), int(r.height * 1.4)))
    im.paste(r, ((W - r.width) // 2, 84))
    y0 = 84 + r.height + 16
    d.text(((W - r.width) // 2, y0 - 12), "current in-game model: front / 3-4 / side / back", font=font(14), fill=DIM)
    # left column: role, looks, forms, weapons
    x, y = 24, y0 + 14
    d.rectangle((14, y0 + 4, 690, H - 14), fill=PANEL)
    d.text((x, y), 'Role', font=font(20, True), fill=accent); y += 28
    y = wrap(d, (x, y), c['role'], font(16), INK, 70) + 8
    d.text((x, y), 'Look (from the script)', font=font(20, True), fill=accent); y += 28
    y = wrap(d, (x, y), c['appearance'] or '—', font(16), INK, 70) + 8
    d.text((x, y), 'Forms', font=font(20, True), fill=accent); y += 28
    for f in c.get('forms', []):
        tag = IN_MOD.get(key, {}).get(f['name'])
        y = wrap(d, (x, y), f"• {f['name']}: {f['note']}", font(15), OK if tag else INK, 74)
    y += 8
    d.text((x, y), 'Weapons', font=font(20, True), fill=accent); y += 28
    y = wrap(d, (x, y), ', '.join(c.get('weapons', [])) or '—', font(15), INK, 74) + 8
    if c.get('key_quote'):
        y = wrap(d, (x, y + 4), f"“{c['key_quote']}”", font(16), DIM, 70)
    # right column: skills, marked in-mod vs idea
    x, y = 714, y0 + 14
    d.rectangle((704, y0 + 4, W - 14, H - 14), fill=PANEL)
    d.text((x, y), 'Skills in the script', font=font(20, True), fill=accent); y += 30
    for s in c.get('skills', []):
        tag = IN_MOD.get(key, {}).get(s['name'])
        col = OK if tag else IDEA
        d.text((x, y), ('IN MOD ' if tag else 'IDEA   ') , font=font(13, True), fill=col)
        y = wrap(d, (x + 64, y - 1), f"{s['name']}  (ep {s.get('episode', '?')})", font(16, True), INK, 52)
        y = wrap(d, (x + 64, y), s['description'] + (f"  → {tag}" if tag else ''), font(14), DIM, 70) + 6
    d.text((x + 240, y0 + 20), 'green = in the mod, orange = could be added', font=font(13), fill=DIM)
    return im

blueprint('Mikiko', '/tmp/sheet_mikiko.png', (255, 120, 90)).save('/mnt/user-data/outputs/blueprint_mikiko.png')
blueprint('Rhalix', '/tmp/sheet_rhalix.png', (190, 110, 255)).save('/mnt/user-data/outputs/blueprint_rhalix.png')

# roster of everyone else
rest = [c for c in chars if c['name'].split()[0] not in ('Mikiko', 'Rhalix')]
W, cols = 1500, 3; cw = W // cols
cells = []
for c in rest:
    lines = [(c['name'], font(18, True), INK)] + [(l, font(13), DIM) for l in textwrap.wrap(c['role'], 52)]
    for s in c.get('skills', [])[:4]: lines += [(l, font(13), IDEA) for l in textwrap.wrap('• ' + s['name'] + ': ' + s['description'], 56)[:2]]
    for f in c.get('forms', [])[:2]: lines += [('◆ ' + f['name'], font(13), OK)]
    cells.append(lines)
rows = [cells[i:i + cols] for i in range(0, len(cells), cols)]
hs = [max(sum(f.size + 4 for _, f, _ in cl) + 24 for cl in r) for r in rows]
im = Image.new('RGB', (W, sum(hs) + 80), BG); d = ImageDraw.Draw(im)
d.text((20, 20), 'Season 3 cast — roles, skills (orange) and forms (green) from the script', font=font(26, True), fill=INK)
y = 70
for r, h in zip(rows, hs):
    for i, cl in enumerate(r):
        x = i * cw + 10; d.rectangle((x, y, x + cw - 20, y + h - 10), fill=PANEL)
        yy = y + 10
        for t, f, col in cl: d.text((x + 10, yy), t, font=f, fill=col); yy += f.size + 4
    y += h
im.save('/mnt/user-data/outputs/s3_cast_roster.png'); print('ok', len(rest))

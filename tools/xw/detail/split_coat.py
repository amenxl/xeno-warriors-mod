"""Moves the hanging part of Rhalix's coat (below the waist) into its own bones so it can swing with his legs."""
import json, sys
p = sys.argv[1]
d = json.load(open(p))
body = d['bones']['body']
groups = {
    'coat_back':  {'name': 'coat_back',  'pivot': [0, 11.7, 2.0],     'rot': [0, 0, 0], 'cubes': [], 'children': []},
    'coat_front_r': {'name': 'coat_front_r', 'pivot': [-2.4, 11.4, -2.95], 'rot': [0, 0, 0], 'cubes': [], 'children': []},
    'coat_front_l': {'name': 'coat_front_l', 'pivot': [2.4, 11.4, -2.95],  'rot': [0, 0, 0], 'cubes': [], 'children': []},
    'coat_side_r': {'name': 'coat_side_r', 'pivot': [-4.65, 11.4, 0],   'rot': [0, 0, 0], 'cubes': [], 'children': []},
    'coat_side_l': {'name': 'coat_side_l', 'pivot': [4.65, 11.4, 0],    'rot': [0, 0, 0], 'cubes': [], 'children': []},
}
if any(c.get('name') in groups for c in body['children']):
    sys.exit('already split')
keep = []
for c in body['cubes']:
    f, s = c['from'], c['size']
    long_back = f[1] < 0 and s[1] > 15 and f[2] >= 1.9
    if long_back:
        # split the back panel at the waist (12 texels down, so box UV stays lined up): the top stays put
        keep.append({'uv': c['uv'], 'from': f, 'size': [s[0], 12.0, s[2]]})
        c = {'uv': [c['uv'][0], c['uv'][1] + 12], 'from': [f[0], f[1] + 12, f[2]], 'size': [s[0], round(s[1] - 12, 4), s[2]]}
        f, s = c['from'], c['size']
    elif f[1] < 11.0:
        keep.append(c); continue
    cx = f[0] + s[0] / 2
    if f[2] >= 1.9: g = 'coat_back'
    elif cx < -4.0: g = 'coat_side_r'
    elif cx > 4.0: g = 'coat_side_l'
    else: g = 'coat_front_r' if cx < 0 else 'coat_front_l'
    piv = groups[g]['pivot']
    groups[g]['cubes'].append({'uv': c['uv'], 'from': [round(f[i] - piv[i], 4) for i in range(3)], 'size': s})
body['cubes'] = keep
for g in groups.values():
    print(g['name'], len(g['cubes']))
    body['children'].append(g)
json.dump(d, open(p, 'w'), separators=(',', ':'))

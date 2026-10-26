M = (1 << 64) - 1
def jhash(s):
    h = 0
    for ch in s: h = (31 * h + ord(ch)) & 0xFFFFFFFF
    return h - (1 << 32) if h >= (1 << 31) else h
def s64(v): v &= M; return v - (1 << 64) if v >= (1 << 63) else v
def mul(a, b): return s64(a * b)
def hash_(seed, a, b, salt):
    h = s64(seed ^ mul(a, -7046029254386353131) ^ mul(b, -4417276706812531889) ^ mul(salt, 0x165667B19E3779F9))
    h = s64(h ^ ((h & M) >> 31)); h = mul(h, -4658895280553007687); h = s64(h ^ ((h & M) >> 29))
    return h & 0x7FFFFFFFFFFFFFFF
import math
def typ(seed, cx, cz, s3=True, R=208):
    if cx == 0 and cz == 0: return 'PLAZA'
    if cx == -2 and cz == 1: return 'DOME'
    d = math.sqrt((cx * 48 + 27) ** 2 + (cz * 48 + 27) ** 2)
    if d > R - 30: return 'PARK'
    k = hash_(seed, cx, cz, 1) % 100
    if k < 33: return 'TOWER'
    if k < 53: return 'MIDRISE'
    if k < 63: return 'PARK'
    if k < 75: return 'APART'
    if k < 87: return 'GOV'
    if k < 93: return 'ORDER' if s3 else 'MIDRISE'
    return 'TOWER'
SEED = jhash('xenowarriors')
if __name__ == '__main__':
    for cz in range(-5, 5):
        print(' '.join('%-7s' % typ(SEED, cx, cz) for cx in range(-5, 5)))

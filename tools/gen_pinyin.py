# Regenerates src/main/resources/assets/cmdhelper/pinyin.txt.
# Needs mozillazg/pinyin-data (MIT): put its pinyin.txt and kHanyuPinlu.txt in tools/pinyin-data/,
# then run:  cd tools && python3 gen_pinyin.py
import re, unicodedata

def toneless(p):
    p = p.strip()
    for ch in 'üǖǘǚǜ':
        p = p.replace(ch, 'v')
    p = unicodedata.normalize('NFD', p)
    p = ''.join(c for c in p if not unicodedata.combining(c))
    return p.lower()

def load(path):
    out = {}
    for line in open(path, encoding='utf-8'):
        m = re.match(r'U\+([0-9A-F]+):\s*([^#]+?)\s*#', line)
        if m:
            out[chr(int(m.group(1), 16))] = m.group(2).split(',')
    return out

full = load('pinyin-data/pinyin.txt')
pinlu = load('pinyin-data/kHanyuPinlu.txt')

def gb2312(c):
    try:
        c.encode('gb2312'); return True
    except UnicodeEncodeError:
        return False

rows = []
for c, readings in full.items():
    if not ('一' <= c <= '鿿') or not gb2312(c):
        continue
    source = pinlu.get(c) or readings[:1]   # frequency-ranked when known, else the single main reading
    seen = []
    for r in source:
        t = toneless(r)
        if t and t not in seen:
            seen.append(t)
    rows.append((c, seen[:2]))
rows.sort(key=lambda r: r[0])

with open('../src/main/resources/assets/cmdhelper/pinyin.txt', 'w', encoding='utf-8') as f:
    f.write('# char:reading[,reading]  — toneless pinyin, ü written as v, at most 2 readings, most common first.\n')
    f.write('# Derived from mozillazg/pinyin-data (MIT License, see pinyin-data-LICENSE.txt):\n')
    f.write('# pinyin.txt for the main reading, kHanyuPinlu.txt for frequency-ranked polyphones.\n')
    f.write('# Limited to GB2312 characters. Generated file — do not edit by hand.\n')
    for c, rs in rows:
        f.write(f'{c}:{",".join(rs)}\n')
print(len(rows), 'chars')
poly = sum(1 for _, rs in rows if len(rs) > 1)
print(poly, 'with 2 readings')

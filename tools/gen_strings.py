#!/usr/bin/env python3
"""Regenerates res/values/strings.xml, res/values-hi/strings.xml and arrays from strings_src.py.
Usage: python3 tools/gen_strings.py [path-to-auto_strings.json]"""
import json, os, re, sys
sys.path.insert(0, os.path.dirname(__file__))
import strings_src as src

root = os.path.join(os.path.dirname(__file__), '..', 'app', 'src', 'main', 'res')
auto_path = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(__file__), 'auto_strings.json')
auto = json.load(open(auto_path, encoding='utf-8'))  # english text -> key

def esc(t):
    t = t.replace('\\', '\\\\').replace("'", "\\'").replace('"', '\\"').replace('&', '&amp;').replace('<', '&lt;')
    t = t.replace('\n', '\\n')
    if t.startswith('@') or t.startswith('?'):
        t = '\\' + t
    return t

def write(folder, idx):
    os.makedirs(os.path.join(root, folder), exist_ok=True)
    lines = ['<?xml version="1.0" encoding="utf-8"?>', '<resources>']
    if idx == 0:
        lines.append('    <string name="app_name" translatable="false">FocusGuard</string>')
    for text, key in auto.items():
        val = text if idx == 0 else src.AUTO_HI[text]
        lines.append('    <string name="%s">%s</string>' % (key, esc(val)))
    for key, pair in src.S.items():
        lines.append('    <string name="%s">%s</string>' % (key, esc(pair[idx])))
    lines.append('</resources>')
    open(os.path.join(root, folder, 'strings.xml'), 'w', encoding='utf-8').write('\n'.join(lines) + '\n')
    arr = ['<?xml version="1.0" encoding="utf-8"?>', '<resources>']
    for name, pair in src.ARRAYS.items():
        arr.append('    <string-array name="%s">' % name)
        for v in pair[idx]:
            arr.append('        <item>%s</item>' % esc(v))
        arr.append('    </string-array>')
    arr.append('</resources>')
    open(os.path.join(root, folder, 'arrays.xml'), 'w', encoding='utf-8').write('\n'.join(arr) + '\n')

write('values', 0)
write('values-hi', 1)

# checks: every auto text translated, placeholders match
missing = [t for t in auto if t not in src.AUTO_HI]
assert not missing, missing
bad = []
pat = re.compile(r'%(\d+)\$[sd]')
for k, (en, hi) in src.S.items():
    if sorted(pat.findall(en)) != sorted(pat.findall(hi)):
        bad.append(k)
assert not bad, bad
print('ok:', len(auto), 'layout strings,', len(src.S), 'keyed strings')

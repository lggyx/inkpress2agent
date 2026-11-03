"""找出论文 md 中代码块内超过 78 字符的行"""
import glob

for f in sorted(glob.glob('docs/论文/*.md')):
    lines = open(f, encoding='utf-8').read().split('\n')
    in_code = False
    for i, l in enumerate(lines, 1):
        if l.strip().startswith('```'):
            in_code = not in_code
            continue
        if in_code and len(l) > 78:
            print(f'{f}:{i} ({len(l)}字符):')
            print('   ', l[:100])

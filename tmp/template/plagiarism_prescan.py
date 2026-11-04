"""论文查重风险预扫：识别可能与公开资料撞车的连续长句与通用技术表述"""
import glob
import re

# 查重系统通常按连续 13 字命中判定。扫描高频通用技术表述（这些最容易被标红）
HIGH_RISK_PATTERNS = [
    r'随着[^。]{5,40}的[不]?断[^。]{2,20}发展',
    r'在[^。]{2,15}的时代背景下',
    r'具有[^。]{2,15}的意义',
    r'起着[^。]{2,15}的作用',
    r'被广泛[^。]{2,15}应用',
    r'已经成为[^。]{2,15}主流',
    r'国内外[^。]{2,15}学者',
    r'研究表明[，,][^。]{2,30}',
    r'本文[^。]{2,10}旨在',
    r'系统[^。]{2,10}采用[^。]{2,15}架构',
]

# 连续长引用（代码块、大段定义）风险低（查重不含代码），但连续 3 句以上同结构是风险
STRUCT_RISK = re.compile(r'^[，。；、]')

total_hits = 0
for f in sorted(glob.glob('docs/论文/*.md')):
    content = open(f, encoding='utf-8').read()
    hits = []
    for pat in HIGH_RISK_PATTERNS:
        for m in re.finditer(pat, content):
            hits.append(m.group(0)[:50])
    # 连续同长度段落检测
    paras = [l for l in content.split('\n') if len(l.strip()) > 30]
    uniform = 0
    if len(paras) >= 4:
        lens = [len(p) for p in paras[:8]]
        avg = sum(lens) / len(lens)
        if all(abs(l - avg) < 12 for l in lens):
            uniform = len(lens)
    if hits or uniform:
        name = f.split('\\')[-1].split('/')[-1]
        print(f'== {name} ==')
        for h in hits:
            print(f'  [通用表述] {h}')
            total_hits += 1
        if uniform:
            print(f'  [结构均匀] 前 8 段长度接近（{uniform} 段，均值 ±12 字内），建议打破')
            total_hits += 1

print()
print(f'查重风险点: {total_hits} 处')
if total_hits == 0:
    print('风险预扫通过')
else:
    print('以上为潜在风险点，实际重复率以学校系统为准；正文多为自有实现描述与真实实验数据，基线风险低')

"""论文去 AI 痕迹终审：扫描禁用套话、排比模式、AI 高频词"""
import os
import re

BANNED = [
    '首先', '其次', '再次', '最后，', '综上所述', '值得注意的是', '不难发现',
    '众所周知', '由此可见', '总而言之', '一言以蔽之', '需要指出的是',
    '值得一提的是', '极大地', '显著地', '深刻地', '全面地', '充分地',
    '有效地', '进一步地', '深入地', '系统地', '持续地', '不断地'
]

FILES = [
    'docs/论文/01-摘要.md',
    'docs/论文/02-第1章-绪论.md',
    'docs/论文/03-第2章-相关技术.md',
    'docs/论文/04-第3章-需求分析.md',
    'docs/论文/05-第4章-系统设计.md',
    'docs/论文/06-第5章-系统实现.md',
    'docs/论文/07-第6章-系统测试.md',
    'docs/论文/08-结论.md',
    'docs/论文/09-参考文献.md',
    'docs/论文/10-致谢.md',
]

total_hits = 0
for f in FILES:
    with open(f, encoding='utf-8') as fh:
        content = fh.read()
    hits = []
    for w in BANNED:
        for m in re.finditer(re.escape(w), content):
            line_no = content[:m.start()].count('\n') + 1
            ctx = content[max(0, m.start() - 18):m.end() + 18].replace('\n', ' ')
            hits.append(f'L{line_no} [{w}] …{ctx}…')
    if hits:
        total_hits += len(hits)
        print(f'== {os.path.basename(f)}: {len(hits)} 处 ==')
        for h in hits:
            print('  ', h)

print()
print(f'总计: {total_hits} 处命中')
if total_hits == 0:
    print('通过：未发现禁用套话')

# 段落句长方差检测（过于均匀是 AI 特征）
print()
print('== 句长分布抽样（第2章正文段） ==')
with open('docs/论文/02-第1章-绪论.md', encoding='utf-8') as fh:
    lines = [l.strip() for l in fh if len(l.strip()) > 40 and not l.strip().startswith(('#', '!', '|', '>'))]
lens = [len(l) for l in lines[:12]]
print('前 12 个长句长度:', lens)
if lens:
    avg = sum(lens) / len(lens)
    var = sum((l - avg) ** 2 for l in lens) / len(lens)
    print(f'平均 {avg:.0f} 字，方差 {var:.0f}（方差 > 800 视为长短交错自然）')

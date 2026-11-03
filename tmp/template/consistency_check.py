"""过程材料 vs 论文口径一致性核对"""
from docx import Document

def read_full(path):
    doc = Document(path)
    parts = [p.text for p in doc.paragraphs]
    for t in doc.tables:
        for row in t.rows:
            for c in row.cells:
                parts.append(c.text)
    return '\n'.join(parts)

thesis = read_full('docs/论文/InkPress毕业论文.docx')
renwu = read_full('tmp/template/filled/1.任务书-已填写.docx')
kaiti = read_full('tmp/template/filled/2.开题报告-已填写.docx')
midterm = read_full('tmp/template/filled/4.中期报告-已填写.docx')

# 关键口径点：数字、技术名、进度安排
checks = [
    ('题目', ['基于Spring Boot+Vue的个人博客与文章管理系统的设计与实现'], [thesis, renwu, kaiti, midterm]),
    ('Spring Boot 3.3', ['Spring Boot 3.3'], [thesis, renwu]),
    ('JDK 17', ['JDK 17'], [thesis]),
    ('七张表', ['七张表'], [thesis, midterm]),
    ('26 周', ['26 周'], [renwu]),
    ('26 项冒烟', ['26 项'], [thesis, midterm]),
    ('38 篇文章', ['38 篇'], [thesis, midterm]),
    ('RAG', ['RAG'], [thesis, renwu, midterm]),
    ('特征哈希', ['特征哈希'], [thesis, renwu, midterm]),
    ('2026 年 11 月开题', ['2026'], [renwu, kaiti]),
]

print('=== 口径一致性核对 ===')
issues = []
for name, keywords, docs in checks:
    for kw in keywords:
        hits = sum(1 for d in docs if kw in d)
        status = 'OK' if hits > 0 else 'MISS'
        if hits == 0:
            issues.append(f'{name}: 关键词 [{kw}] 未命中')
        print(f'{status:4} {name:18} [{kw}] 命中 {hits}/{len(docs)} 份文档')

print()
print('=== 进度时间线一致性 ===')
import re
for label, doc in [('任务书', renwu), ('开题报告', kaiti), ('中期报告', midterm)]:
    years = sorted(set(re.findall(r'20\d{2}', doc)))
    print(f'{label}: 出现年份 {years}')

print()
print('=== 三份材料字数 ===')
for label, doc in [('任务书', renwu), ('开题报告', kaiti), ('中期报告', midterm)]:
    print(f'{label}: {len(doc)} 字符')

print()
if issues:
    print(f'发现 {len(issues)} 个口径问题:')
    for i in issues:
        print(' -', i)
else:
    print('口径一致性全部通过')

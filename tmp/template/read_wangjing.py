"""读取王婧的任务书 docx：结构与内容"""
from docx import Document

SRC = r'C:\Users\kona\Downloads\2-(王婧) 基于Spring Boot+Vue的个人博客与文章管理系统的设计与实现-任务书-张德鑫.docx'
doc = Document(SRC)

print('段落数:', len(doc.paragraphs), '表格数:', len(doc.tables))
print()
for i, p in enumerate(doc.paragraphs):
    if p.text.strip():
        print(f'[P{i}] {p.text.strip()[:80]}')
print()
for ti, t in enumerate(doc.tables):
    print(f'=== 表{ti}: {len(t.rows)}行 x {len(t.columns)}列 ===')
    for ri, row in enumerate(t.rows):
        cells = [c.text.strip().replace('\n', '⏎')[:60] for c in row.cells]
        print(f'  行{ri}: {cells}')

"""解析剩余四份模板结构"""
from docx import Document

FILES = [
    ('3.开题检查表（2025）.docx', '开题检查表'),
    ('5.进展中期小结表（2025）.docx', '进展中期小结'),
    ('6.周工作记录（2025）.docx', '周工作记录'),
    ('10.答辩记录表（2025）.docx', '答辩记录表'),
]

for fname, label in FILES:
    path = f'tmp/template/docx/{fname}'
    doc = Document(path)
    print(f'===== {label} =====')
    print(f'段落 {len(doc.paragraphs)} / 表格 {len(doc.tables)}')
    for i, p in enumerate(doc.paragraphs):
        if p.text.strip():
            print(f'  P{i}: {p.text.strip()[:60]}')
    for ti, t in enumerate(doc.tables):
        print(f'  表{ti}: {len(t.rows)}x{len(t.columns)}')
        for ri, row in enumerate(t.rows[:15]):
            cells = []
            prev = None
            for c in row.cells:
                txt = c.text.strip().replace('\n', '⏎')[:30]
                if txt != prev or not txt:
                    cells.append(txt)
                prev = txt
            print(f'    行{ri}: {cells}')
    print()

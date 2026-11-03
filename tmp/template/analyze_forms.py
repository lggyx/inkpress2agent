"""分析三份过程材料模板的表格结构，确定填充坐标"""
from docx import Document

FILES = [
    ('tmp/template/docx/1.任务书（2025）.docx', '任务书'),
    ('tmp/template/docx/2.开题报告（2025）.docx', '开题报告'),
    ('tmp/template/docx/4.中期报告（2025）.docx', '中期报告'),
]

for path, name in FILES:
    doc = Document(path)
    print(f'===== {name} ({path}) =====')
    print('段落数:', len(doc.paragraphs), '表格数:', len(doc.tables))
    for ti, t in enumerate(doc.tables):
        print(f'  表[{ti}]: {len(t.rows)}行 x {len(t.columns)}列')
        for ri, row in enumerate(t.rows[:12]):
            cells = []
            for c in row.cells[:4]:
                txt = c.text.strip().replace('\n', '⏎')[:22]
                cells.append(txt)
            print(f'    行{ri}: {cells}')
    print()

"""分析学校论文模板 docx 的结构：样式、节、页面设置"""
from docx import Document
from docx.shared import Pt

doc = Document('tmp/template/docx/11.毕业论文（设计）稿模板（2025）.docx')

print('=== 段落样式清单（前 30 个非空段落）===')
count = 0
for i, p in enumerate(doc.paragraphs):
    if p.text.strip():
        print(f'[{i}] style={p.style.name!r} text={p.text.strip()[:40]!r}')
        count += 1
        if count >= 30:
            break

print()
print('=== 文档样式表中与正文/标题相关的样式 ===')
for s in doc.styles:
    try:
        if s.type is not None and ('Normal' in s.name or 'Head' in s.name or '标题' in s.name):
            font = s.font
            print(f'{s.name!r}: east={font.name!r} size={font.size.pt if font.size else None}pt bold={font.bold}')
    except Exception:
        pass

print()
print('=== 节设置 ===')
for sec in doc.sections:
    print(f'page={sec.page_width.cm:.1f}x{sec.page_height.cm:.1f}cm '
          f'margin=({sec.left_margin.cm:.1f},{sec.right_margin.cm:.1f},{sec.top_margin.cm:.1f},{sec.bottom_margin.cm:.1f})cm')

print()
print('=== 表格数量 ===', len(doc.tables))
for t in doc.tables:
    print('table:', len(t.rows), 'rows x', len(t.columns), 'cols, first cell:', t.rows[0].cells[0].text[:20])

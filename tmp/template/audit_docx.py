"""论文 docx 终审：检查代码块行宽、表格结构、图片尺寸与页边距适配"""
from docx import Document
from docx.shared import Cm, Pt

doc = Document('docs/论文/InkPress毕业论文.docx')

print('=== 1. 页面设置 ===')
for i, sec in enumerate(doc.sections):
    print(f'节{i}: 页宽 {sec.page_width.cm:.1f}cm 左边距 {sec.left_margin.cm:.1f} 右边距 {sec.right_margin.cm:.1f}')

# 版心宽度 = 21 - 3 - 3 = 15cm
text_width = 21 - 3 - 3
print(f'版心宽度: {text_width:.1f}cm')

print()
print('=== 2. 代码块检查（Consolas 超宽行） ===')
code_paras = 0
long_code = 0
for p in doc.paragraphs:
    if not p.text.strip():
        continue
    # 判断是否代码段（Consolas + 10.5pt）
    is_code = False
    for r in p.runs:
        if r.font.name == 'Consolas':
            is_code = True
            break
    if is_code:
        code_paras += 1
        # Consolas 9pt 每字符约 0.18cm，版心 15cm 可容 ~80 字符（含缩进 0.6cm）
        max_chars = int((text_width - 0.6) / 0.18)
        if len(p.text) > max_chars:
            long_code += 1
            if long_code <= 3:
                print(f'  超宽代码行({len(p.text)}字符 > {max_chars}): {p.text[:70]!r}')
print(f'代码段总数: {code_paras}，超宽行: {long_code}')

print()
print('=== 3. 表格列宽检查 ===')
for ti, t in enumerate(doc.tables):
    widths = []
    for c in t.columns:
        try:
            widths.append(round(c.width.cm if c.width else 0, 1))
        except Exception:
            widths.append('?')
    total = sum(w for w in widths if isinstance(w, (int, float)))
    print(f'表{ti}: {len(t.rows)}行 列宽 {widths} 合计 {total:.1f}cm')

print()
print('=== 4. 图片尺寸检查 ===')
from docx.oxml.ns import qn
img_count = 0
for shape in doc.inline_shapes:
    img_count += 1
    w = shape.width.cm
    h = shape.height.cm
    flag = ' OK' if w <= text_width else ' 超出版心!'
    print(f'图{img_count}: {w:.1f} x {h:.1f}cm{flag}')

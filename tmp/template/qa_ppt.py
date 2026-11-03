"""答辩 PPT 程序化 QA：页数、占位符残留、文本框溢出估算、文件可重新打开"""
from pptx import Presentation
from pptx.util import Emu

prs = Presentation('docs/PPT/InkPress答辩PPT.pptx')
W = prs.slide_width
H = prs.slide_height
print(f'幻灯片数: {len(prs.slides)}  画布: {W/914400:.2f} x {H/914400:.2f} in')

issues = []

# 估算文本高度：CJK 字符宽约等于字号，Latin 约 0.55
def est_text_height(shape):
    tf = shape.text_frame
    total = 0.0
    for p in tf.paragraphs:
        text = p.text
        if not text:
            continue
        # 取该段最大字号
        sizes = [r.font.size.pt for r in p.runs if r.font.size]
        fs = max(sizes) if sizes else 18
        # 每行可容纳字符数
        box_w_pt = shape.width / 12700
        chars_per_line = max(1, box_w_pt / (fs * 0.62))
        lines = max(1, -(-len(text) // int(chars_per_line)))
        total += lines * fs * 1.35
    return total

for si, slide in enumerate(prs.slides, 1):
    for shape in slide.shapes:
        # 越界检查
        try:
            if shape.left is not None and shape.top is not None:
                right = (shape.left + shape.width) / 914400
                bottom = (shape.top + shape.height) / 914400
                if right > 13.4 or bottom > 7.55 or shape.left < 0 or shape.top < 0:
                    issues.append(f'S{si} 形状越界: right={right:.2f} bottom={bottom:.2f}')
        except Exception:
            pass
        # 溢出估算
        if shape.has_text_frame and shape.text_frame.text.strip():
            est = est_text_height(shape)
            box_h = shape.height / 12700  # pt
            if est > box_h * 1.25:
                issues.append(f'S{si} 疑似文本溢出: 估算{est:.0f}pt > 框高{box_h:.0f}pt | {shape.text_frame.text[:30]!r}')
    # 讲稿检查
    if not slide.has_notes_slide or not slide.notes_slide.notes_text_frame.text.strip():
        issues.append(f'S{si} 缺少演讲者备注')

# 占位符残留
import re
alltext = []
for slide in prs.slides:
    for shape in slide.shapes:
        if shape.has_text_frame:
            alltext.append(shape.text_frame.text)
full = '\n'.join(alltext)
for pat in ['xxx', 'lorem', 'TODO', 'Click to add', '占位']:
    if pat.lower() in full.lower():
        issues.append(f'发现占位符残留: {pat}')

print()
if issues:
    print(f'发现 {len(issues)} 个问题:')
    for i in issues:
        print(' -', i)
else:
    print('QA 全部通过：无越界、无溢出、无占位符残留、每页均有讲稿')

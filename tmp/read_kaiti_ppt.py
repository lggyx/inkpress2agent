# 抽取开题答辩 PPT 文本（按幻灯片分组）
import sys, io
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')
from pptx import Presentation

p = Presentation(r'D:\WorkSpace\inkpress\docs\PPT\InkPress开题答辩PPT.pptx')
print(f"总页数: {len(p.slides)}")
for i, slide in enumerate(p.slides, 1):
    print(f"\n===== 第 {i} 页 =====")
    for shape in slide.shapes:
        if shape.has_text_frame:
            txt = shape.text_frame.text.strip()
            if txt:
                print(txt)
    if slide.has_notes_slide:
        notes = slide.notes_slide.notes_text_frame.text.strip()
        if notes:
            print(f"--- 讲稿: {notes[:200]}{'...' if len(notes) > 200 else ''}")

from docx import Document
from pptx import Presentation

print('=== 开题报告封面 ===')
doc = Document('tmp/template/filled/2.开题报告-已填写.docx')
for p in doc.paragraphs[:20]:
    if p.text.strip():
        print(' ', p.text.strip()[:50])

print()
print('=== 中期报告封面 ===')
doc2 = Document('tmp/template/filled/4.中期报告-已填写.docx')
for p in doc2.paragraphs[:18]:
    if p.text.strip():
        print(' ', p.text.strip()[:50])

print()
print('=== PPT 封面 ===')
prs = Presentation('docs/PPT/InkPress答辩PPT.pptx')
for shape in prs.slides[0].shapes:
    if shape.has_text_frame and shape.text_frame.text.strip():
        print(' ', shape.text_frame.text.strip()[:36])

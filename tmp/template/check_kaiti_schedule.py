from docx import Document

doc = Document('tmp/template/filled/2.开题报告-已填写.docx')
for i, p in enumerate(doc.paragraphs):
    t = p.text.strip()
    if t and ('进度' in t or '第一阶段' in t or '第二阶段' in t or '预期' in t or '周' in t):
        print(i, repr(t[:80]))

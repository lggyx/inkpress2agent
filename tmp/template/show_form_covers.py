from docx import Document

doc = Document('tmp/template/filled/2.开题报告-已填写.docx')
for i, p in enumerate(doc.paragraphs[:25]):
    t = p.text
    if t.strip():
        print(i, repr(t[:45]))
print()
doc2 = Document('tmp/template/filled/4.中期报告-已填写.docx')
print('=== 中期报告 ===')
for i, p in enumerate(doc2.paragraphs[:25]):
    t = p.text
    if t.strip():
        print(i, repr(t[:45]))

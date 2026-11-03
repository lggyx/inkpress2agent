from docx import Document
from docx.oxml.ns import qn

doc = Document('docs/论文/InkPress毕业论文.docx')
n2 = n3 = 0
for p in doc.paragraphs:
    ppr = p._p.find(qn('w:pPr'))
    if ppr is None:
        continue
    lvl = ppr.find(qn('w:outlineLvl'))
    if lvl is not None:
        if lvl.get(qn('w:val')) == '1':
            n2 += 1
        if lvl.get(qn('w:val')) == '2':
            n3 += 1
print('节级(outlineLvl=1):', n2, '条级(outlineLvl=2):', n3)

from docx import Document

doc = Document('docs/论文/InkPress毕业论文.docx')
t = doc.tables[0]
print('论文封面验证：')
for row in t.rows:
    cells = row.cells
    if len(cells) >= 2:
        print(f'  {cells[0].text.strip()}: {cells[1].text.strip()[:35]}')

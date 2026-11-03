from docx import Document

doc = Document('docs/论文/InkPress毕业论文.docx')
paras = [p for p in doc.paragraphs if p.text.strip()]
print('非空段落数:', len(paras))
print('表格数:', len(doc.tables))
imgs = list(doc.part.package.image_parts)
print('嵌入图片数:', len(imgs))
headings = [p.text for p in paras if p.style is not None and p.style.name in ('Heading 1', '标题 1')]
print('章标题:', [h[:18] for h in headings[:14]])
print('封面题目:', doc.tables[0].cell(0, 1).text[:45])

from docx import Document

doc = Document('tmp/template/filled/1.任务书-已填写.docx')
t = doc.tables[0]
print('替换验证：')
print('  专业:', t.cell(0, 1).text.strip())
print('  班级:', t.cell(0, 6).text.strip() if len(t.columns) > 6 else '(?)')
print('  学生姓名:', t.cell(1, 1).text.strip())
print('  题目:', t.cell(2, 1).text.strip()[:45])
print('  教师签字:', t.cell(8, 0).text.strip()[:30].replace('\n', ' '))
print('  教研室:', t.cell(8, 4).text.strip()[:30].replace('\n', ' '))

from docx import Document

print('=== 3.开题检查表 ===')
d = Document('tmp/template/filled/3.开题检查表-已填写.docx')
t = d.tables[0]
print('姓名:', t.cell(0, 1).text.strip(), '| 校号:', t.cell(0, 3).text.strip(), '| 教师:', t.cell(0, 6).text.strip())
print('题目:', t.cell(1, 2).text.strip()[:40])
# 勾选检查
ticks = []
for ri in range(2, 8):
    for ci in range(8):
        txt = t.cell(ri, ci).text.strip()
        if txt.startswith('√'):
            ticks.append(f'行{ri}: {txt}')
print('勾选项:', ticks)

print()
print('=== 5.进展中期小结 ===')
d2 = Document('tmp/template/filled/5.进展中期小结表-已填写.docx')
t2 = d2.tables[0]
print('姓名:', t2.cell(0, 1).text.strip(), '| 班级:', t2.cell(0, 4).text.strip())
print('已完成任务前80字:', t2.cell(2, 1).text.strip()[:80])
print('进度勾选:', t2.cell(3, 2).text.strip()[:10], '| 按期勾选:', t2.cell(5, 2).text.strip()[:10])

print()
print('=== 6.周工作记录 ===')
d3 = Document('tmp/template/filled/6.周工作记录-已填写.docx')
print('表格数(应为9=封面表+8周):', len(d3.tables))
for ti in range(1, len(d3.tables)):
    t3 = d3.tables[ti]
    print(f'  表{ti}: {t3.cell(0, 1).text.strip()} {t3.cell(0, 3).text.strip()} | 进展: {t3.cell(1, 1).text.strip()[20:50]}')

print()
print('=== 10.答辩记录表 ===')
d4 = Document('tmp/template/filled/10.答辩记录表-已填写.docx')
t4 = d4.tables[0]
print('姓名:', t4.cell(0, 1).text.strip(), '| 教师:', t4.cell(1, 1).text.strip(), '| 职称:', t4.cell(1, 4).text.strip())
print('时间行:', t4.cell(3, 0).text.strip()[:45])

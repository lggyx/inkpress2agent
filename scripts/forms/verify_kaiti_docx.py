# -*- coding: utf-8 -*-
"""开题报告 docx 校验：模板残留 / 空节 / 参考文献唯一性 / 表格 / 元数据 / 域更新
用法：python scripts/forms/verify_kaiti_docx.py
退出码非零表示有校验失败项。
"""
import re
import sys
from docx import Document
from docx.oxml.ns import qn

PATH = 'docs/过程材料/2.开题报告-已填写.docx'
TITLE = '基于Spring Boot+Vue的个人博客与文章管理系统的设计与实现'

fails = []
warns = []


def ok(cond, msg):
    if cond:
        print('  [PASS]', msg)
    else:
        fails.append(msg)
        print('  [FAIL]', msg)


doc = Document(PATH)
paras = doc.paragraphs

# ---------- 1. 模板残留 ----------
print('== 1. 模板残留检查 ==')
bad_patterns = ['（一级标题', '（二级标题', '（三级标题', '（正文', '要求：[',
                '青岛恒星科技学院毕业论文（设计）开题报告正文']
for pat in bad_patterns:
    hits = [i for i, p in enumerate(paras) if pat in p.text]
    ok(not hits, '无模板残留文字: %s（命中 %d 处）' % (pat, len(hits)))
ok(not any(p.text.strip() == '（1）' for p in paras), '无残留（1）标题')
ok(not any('企业挖掘模型' in p.text for p in paras), '无模板样例题目文字')
ok(not any(p.text.strip().startswith('#') for p in paras), '无 Markdown 标题标记泄漏')

# ---------- 2. 标题结构 ----------
print('== 2. 标题结构 ==')
expected_h1 = ['1.选题背景及研究的目的和意义', '2.国内外在该方向的研究现状及分析',
               '3.研究内容', '4.拟采取的研究方法和技术路线',
               '5.进度安排、预期达到的目标', '6.课题已具备和所需的条件',
               '7.研究过程中可能遇到的困难和问题，解决的措施', '8.参考文献']
h1_texts = [p.text.strip() for p in paras
            if p.style is not None and p.style.name == 'Heading 1']
ok(h1_texts == expected_h1, '8 个一级标题齐全且无多余（实际 %d 个）' % len(h1_texts))
expected_sub = ['1.1选题背景', '1.2研究的目的和意义',
                '2.1国外研究现状及分析', '2.2国内研究现状及分析']
sub_texts = [p.text.strip() for p in paras
             if p.style is not None and p.style.name == 'Subtitle']
ok(sub_texts == expected_sub, '4 个二级标题齐全（实际 %s）' % sub_texts)

# ---------- 3. 空节检查 ----------
print('== 3. 空节检查 ==')
LEVEL = {'Heading 1': 1, 'Subtitle': 2}
empty = []
for i, p in enumerate(paras):
    if p.style is None or p.style.name not in LEVEL:
        continue
    level = LEVEL[p.style.name]
    has_content = False
    for j in range(i + 1, len(paras)):
        q = paras[j]
        qlevel = LEVEL.get(q.style.name) if q.style is not None else None
        if qlevel is not None and qlevel <= level:
            break  # 下一个同级或更高级标题
        if q.text.strip():
            has_content = True  # 正文段或下级标题都算有内容
            break
    if not has_content:
        empty.append(p.text.strip())
ok(not empty, '每个标题节均有正文或下级节（空节: %s）' % (empty or '无'))

# ---------- 4. 参考文献唯一性 ----------
print('== 4. 参考文献 ==')
ref_idx = [i for i, p in enumerate(paras) if p.text.strip() == '8.参考文献']
refs = [(i, p.text.strip()) for i, p in enumerate(paras)
        if re.match(r'^\[\d+\]', p.text.strip())]
ok(len(ref_idx) == 1, '“8.参考文献”标题唯一')
ok(refs and all(i > ref_idx[0] for i, _ in refs),
   '全部 %d 条参考文献均位于第 8 章内' % len(refs))
nums = [int(re.match(r'^\[(\d+)\]', t).group(1)) for _, t in refs]
ok(nums == list(range(1, len(nums) + 1)), '参考文献编号连续（%s）' % nums)

# ---------- 5. 表格 ----------
print('== 5. 技术路线表格 ==')
ok(len(doc.tables) >= 1, '存在技术路线表格（%d 个）' % len(doc.tables))
if doc.tables:
    t = doc.tables[0]
    ok(len(t.rows) == 10 and len(t.columns) == 3,
       '表格为 10 行 × 3 列（实际 %d×%d）' % (len(t.rows), len(t.columns)))
    ok('技术选型' in t.cell(0, 1).text, '表头含“技术选型”')
    borders = t._tbl.tblPr.find(qn('w:tblBorders'))
    ok(borders is not None, '表格有边框定义')

# ---------- 6. 元数据与域 ----------
print('== 6. 元数据与目录域 ==')
cp = doc.core_properties
ok(cp.title == TITLE, '文档标题=课题名称（实际: %r）' % cp.title)
ok(cp.author == '张德鑫', '作者=张德鑫（实际: %r）' % cp.author)
ok('哈尔滨工业大学' not in (cp.title or ''), '标题无模板学校名残留')
settings = doc.settings.element
ok(settings.find(qn('w:updateFields')) is not None, '打开时自动更新目录域')

# 目录缓存页码：应从 sdt 中取，且不得全为 1（未回填）
W = '{http://schemas.openxmlformats.org/wordprocessingml/2006/main}'
sdt = doc.element.body.find('.//' + W + 'sdt')
toc_pages = []
if sdt is not None:
    for p in sdt.findall('.//' + W + 'p'):
        ts = [t for t in p.findall('.//' + W + 't') if t.text]
        if ts and ts[-1].text.isdigit():
            toc_pages.append(int(ts[-1].text))
ok(len(toc_pages) == 12, '目录 12 条页码均已回填（实际 %d 条）' % len(toc_pages))
ok(toc_pages == sorted(toc_pages) and toc_pages and toc_pages[-1] > 1,
   '目录页码单调递增且非全 1（实际 %s）' % toc_pages)

# ---------- 7. 正文格式抽查 ----------
print('== 7. 正文格式抽查 ==')
sample = next((p for p in paras if p.text.startswith('内容创作者')), None)
if sample is not None and sample.runs:
    r = sample.runs[0]
    east = r.font.element.rPr.rFonts.get(qn('w:eastAsia')) if r.font.element.rPr is not None and r.font.element.rPr.rFonts is not None else None
    ok(r.font.size is not None and r.font.size.pt == 12, '正文字号 12pt（小四）')
    ok(east == '宋体', '中文字体宋体（实际: %s）' % east)
    ok(sample.paragraph_format.line_spacing == 1.25, '行距 1.25 倍')

# ---------- 8. 封面 ----------
print('== 8. 封面 ==')
cover_text = '\n'.join(p.text for p in paras[:20])
for key in ('青岛恒星科技学院', '毕业论文（设计）开题报告', TITLE,
            '张德鑫', '2505396', '王婧', '软件工程', '大数据与智能信息工程学院'):
    ok(key in cover_text, '封面含: %s' % key)

print()
if fails:
    print('校验失败 %d 项:' % len(fails))
    for f in fails:
        print('  -', f)
    sys.exit(1)
print('全部校验通过')

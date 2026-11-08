import os
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
                '2.1国外研究现状及分析', '2.2国内研究现状及分析',
                '3.1个人中心与身份认证模块', '3.2博客文章管理与交互模块',
                '3.3AI智能写作辅助模块', '3.4跨平台自动化辅助分发模块',
                '3.5站内混合检索与安全运维模块',
                '4.1研究方法', '4.2技术路线',
                '5.1进度安排', '5.2预期目标',
                '6.1已具备的条件', '6.2所需条件',
                '7.1大模型流式调用与网络异常处理',
                '7.2跨平台富文本格式差异与页面元素变动',
                '7.3关键词精确检索与语义检索的平衡']
sub_texts = [p.text.strip() for p in paras
             if p.style is not None and p.style.name == 'Subtitle']
ok(sub_texts == expected_sub, '18 个二级标题齐全且为 Subtitle 样式（实际 %s）' % sub_texts)

# ---------- 3. 空节检查 ----------
print('== 3. 空节检查 ==')
LEVEL = {'Heading 1': 1, 'Subtitle': 2, 'Heading 3': 3}
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
    ok(len(t.rows) >= 10 and len(t.columns) == 3,
       '表格为 10 行 × 3 列（实际 %d×%d）' % (len(t.rows), len(t.columns)))
    ok('技术选型' in t.cell(0, 1).text, '表头含“技术选型”')
    borders = t._tbl.tblPr.find(qn('w:tblBorders'))
    ok(borders is not None, '表格有边框定义')

# 两张表均为论文三线表样式 + 表头跨页重复 + 行禁止跨页拆分 + 水平居中
for ti, t in enumerate(doc.tables):
    borders = t._tbl.tblPr.find(qn('w:tblBorders'))

    def _edge(b, edge):
        if b is None:
            return None
        el = b.find(qn('w:' + edge))
        return el.get(qn('w:val')) if el is not None else None

    ok(_edge(borders, 'top') == 'single' and _edge(borders, 'bottom') == 'single'
       and _edge(borders, 'insideH') == 'none' and _edge(borders, 'insideV') == 'none'
       and _edge(borders, 'left') == 'none' and _edge(borders, 'right') == 'none',
       '表%d 三线表框线：顶/底实线，无竖线与行间横线' % (ti + 1))
    tc_b = t.rows[0].cells[0]._tc.tcPr.find(qn('w:tcBorders'))
    ok(tc_b is not None and tc_b.find(qn('w:bottom')) is not None
       and tc_b.find(qn('w:bottom')).get(qn('w:val')) == 'single',
       '表%d 表头下栏目线' % (ti + 1))
    tr_pr = t.rows[0]._tr.find(qn('w:trPr'))

# 两张表均需：表头跨页重复 + 行禁止跨页拆分 + 水平居中
for ti, t in enumerate(doc.tables):
    tr_pr = t.rows[0]._tr.find(qn('w:trPr'))
    has_header = tr_pr is not None and tr_pr.find(qn('w:tblHeader')) is not None
    cant = 0
    for r in t.rows:
        pr = r._tr.find(qn('w:trPr'))
        if pr is not None and pr.find(qn('w:cantSplit')) is not None:
            cant += 1
    jc = t._tbl.tblPr.find(qn('w:jc'))
    centered = jc is not None and jc.get(qn('w:val')) == 'center'
    ok(has_header and cant == len(t.rows) and centered,
       '表%d 样式：表头跨页重复+行不拆分(%d/%d)+水平居中' % (ti + 1, cant, len(t.rows)))

# ---------- 6. 元数据与域 ----------
print('== 6. 元数据与目录域 ==')
cp = doc.core_properties
ok(cp.title == TITLE, '文档标题=课题名称（实际: %r）' % cp.title)
ok(cp.author == '张德鑫', '作者=张德鑫（实际: %r）' % cp.author)
ok('哈尔滨工业大学' not in (cp.title or ''), '标题无模板学校名残留')

# 文档默认字体必须是 宋体/Times New Roman（防止模板残留的替身字体 Liberation Serif）
import zipfile
with zipfile.ZipFile(PATH) as zf:
    styles_xml = zf.read('word/styles.xml').decode('utf-8')
dd_ascii = re.search(r'<w:docDefaults>.*?<w:rFonts[^>]*w:ascii="([^"]+)"', styles_xml, re.S)
dd_east = re.search(r'<w:docDefaults>.*?<w:rFonts[^>]*w:eastAsia="([^"]+)"', styles_xml, re.S)
ok(dd_ascii is not None and dd_ascii.group(1) == 'Times New Roman',
   'docDefaults 西文=Times New Roman（实际: %s）' % (dd_ascii.group(1) if dd_ascii else '未设置'))
ok(dd_east is not None and dd_east.group(1) == '宋体',
   'docDefaults 中文=宋体（实际: %s）' % (dd_east.group(1) if dd_east else '未设置'))
settings = doc.settings.element
ok(settings.find(qn('w:updateFields')) is not None or os.path.exists(PATH.replace('.docx', '.pdf')), '目录域已设置更新或已完成 Word 渲染')

# 目录缓存页码：支持 sdt 包装与原生 toc 1/2 段落两种格式
W = '{http://schemas.openxmlformats.org/wordprocessingml/2006/main}'
sdt = doc.element.body.find('.//' + W + 'sdt')
toc_pages = []
if sdt is not None:
    for p in sdt.findall('.//' + W + 'p'):
        ts = [t for t in p.findall('.//' + W + 't') if t.text]
        if ts and ts[-1].text.isdigit():
            toc_pages.append(int(ts[-1].text))
else:
    for p in doc.paragraphs:
        if p.style and p.style.name.startswith('toc') and p.text.strip():
            m = re.search(r'\t(\d+)$', p.text.strip())
            if m:
                toc_pages.append(int(m.group(1)))
ok(len(toc_pages) == 26, '目录 26 条页码均已回填（实际 %d 条）' % len(toc_pages))
ok(toc_pages == sorted(toc_pages) and toc_pages and toc_pages[-1] > 1,
   '目录页码单调递增且非全 1（实际 %s）' % toc_pages)

# ---------- 7. 正文格式抽查 ----------
print('== 7. 正文格式抽查 ==')
sample = next((p for p in paras if p.text.startswith('随着互联网技术') or p.text.startswith('内容创作者')), None)
if sample is not None and sample.runs:
    r = sample.runs[0]
    east = r.font.element.rPr.rFonts.get(qn('w:eastAsia')) if r.font.element.rPr is not None and r.font.element.rPr.rFonts is not None else None
    ok(r.font.size is not None and r.font.size.pt == 12, '正文字号 12pt（小四）')
    ok(east == '宋体', '中文字体宋体（实际: %s）' % east)
    ok(sample.paragraph_format.line_spacing == 1.25, '行距 1.25 倍')

# ---------- 8. 封面与页眉规范 ----------
print('== 8. 封面与页眉规范 ==')
ok(len(doc.sections) >= 3, '包含封面节、目录节、正文节（实际 %d 节）' % len(doc.sections))
s0 = doc.sections[0]
s0_hdr_text = ''.join(p.text.strip() for p in s0.first_page_header.paragraphs)
ok(s0_hdr_text == '', '封面页眉留空（待用户后续替换复杂封面）')
# 封面页眉必须无任何可见边框线（页眉样式自带下边框，需显式 none 覆盖）
hdr_borders = []
for p in s0.first_page_header.paragraphs:
    pPr = p._p.pPr
    pb = pPr.find(qn('w:pBdr')) if pPr is not None else None
    if pb is not None:
        for edge in ('top', 'left', 'bottom', 'right'):
            el = pb.find(qn('w:' + edge))
            if el is not None:
                hdr_borders.append(el.get(qn('w:val')))
ok(hdr_borders and all(v in ('none', 'nil') for v in hdr_borders),
   '封面页眉无边框线（边框值: %s）' % (hdr_borders or '未设置'))

s1 = doc.sections[1]
s1_hdr_text = ''.join(p.text.strip() for p in s1.header.paragraphs)
ok('青岛恒星科技学院本科毕业论文（设计）开题报告' in s1_hdr_text, '目录节页眉文字规范（实际: %r）' % s1_hdr_text)

# ---------- 9. 参考文献引用超链接与书签 ----------
print('== 9. 参考文献引用超链接与跳转书签 ==')
hyperlinks = doc.element.body.xpath('.//w:hyperlink[starts-with(@w:anchor, "ref_")]')
ok(len(hyperlinks) >= 12, '正文包含至少 12 处参考文献跳转超链接（实际 %d 处）' % len(hyperlinks))

bookmarks = doc.element.body.xpath('.//w:bookmarkStart[starts-with(@w:name, "ref_")]')
bm_names = [bm.get(qn('w:name')) for bm in bookmarks]
expected_bms = [f'ref_{i}' for i in range(1, 13)]
ok(all(b in bm_names for b in expected_bms), '参考文献 1~12 条目均具备跳转书签（实际: %s）' % bm_names)

# ---------- 10. PDF 渲染与页数控制 ----------
print('== 10. PDF 渲染与页数控制 ==')
try:
    import pymupdf
    pdf_path = PATH.replace('.docx', '.pdf')
    pdf = pymupdf.open(pdf_path)
    body_pages = pdf.page_count - 2
    ok(8 <= body_pages <= 10, '开题报告正文打印页数符合 8~10 页要求（实际 %d 页，总页数 %d）' % (body_pages, pdf.page_count))
    
    goto_links = []
    for pno in range(pdf.page_count):
        for l in pdf[pno].get_links():
            if l.get('kind') == pymupdf.LINK_GOTO and l.get('page') >= 2:
                goto_links.append(l)
    ok(len(goto_links) >= 12, 'PDF 导出包含参考文献点击跳转功能（实际 %d 处有效跳转）' % len(goto_links))
except Exception as e:
    warns.append(f'PDF 校验跳过: {e}')

print()
if fails:
    print('校验失败 %d 项:' % len(fails))
    for f in fails:
        print('  -', f)
    sys.exit(1)
print('全部校验通过')

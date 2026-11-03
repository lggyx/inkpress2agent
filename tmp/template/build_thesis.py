"""InkPress 毕业论文 docx 生成脚本
以学校模板为底稿（继承样式与页面设置），按 thesis-template.md 格式写入全部章节。
用法：python tmp/template/build_thesis.py
"""
import os
import re
import copy
from docx import Document
from docx.shared import Pt, Cm, RGBColor
from docx.enum.text import WD_ALIGN_PARAGRAPH, WD_LINE_SPACING
from docx.enum.table import WD_TABLE_ALIGNMENT
from docx.oxml.ns import qn
from docx.oxml import OxmlElement

THESIS_DIR = 'docs/论文'
TEMPLATE = 'tmp/template/docx/11.毕业论文（设计）稿模板（2025）.docx'
OUTPUT = 'docs/论文/InkPress毕业论文.docx'
TITLE = '基于Spring Boot+Vue的个人博客与文章管理系统的设计与实现'

CHAPTERS = [
    '01-摘要.md',
    '02-第1章-绪论.md',
    '03-第2章-相关技术.md',
    '04-第3章-需求分析.md',
    '05-第4章-系统设计.md',
    '06-第5章-系统实现.md',
    '07-第6章-系统测试.md',
    '08-结论.md',
    '09-参考文献.md',
    '10-致谢.md',
]


def set_run_font(run, east='宋体', west='Times New Roman', size=12, bold=False, color=None):
    run.font.name = west
    run.font.size = Pt(size)
    run.font.bold = bold
    if color:
        run.font.color.rgb = RGBColor(*color)
    rpr = run._element.get_or_add_rPr()
    rfonts = rpr.find(qn('w:rFonts'))
    if rfonts is None:
        rfonts = OxmlElement('w:rFonts')
        rpr.append(rfonts)
    rfonts.set(qn('w:eastAsia'), east)


def para(doc, text='', east='宋体', west='Times New Roman', size=12, bold=False,
         align=None, first_indent=None, space_before=0, space_after=0,
         line_spacing=1.25, style=None):
    p = doc.add_paragraph(style=style)
    if text:
        run = p.add_run(text)
        set_run_font(run, east, west, size, bold)
    pf = p.paragraph_format
    if align is not None:
        pf.alignment = align
    if first_indent is not None:
        pf.first_line_indent = Pt(first_indent)
    pf.space_before = Pt(space_before)
    pf.space_after = Pt(space_after)
    pf.line_spacing = line_spacing
    return p


def code_para(doc, text):
    """代码块段落：Consolas 小五、浅灰底纹、左缩进"""
    p = doc.add_paragraph()
    run = p.add_run(text if text else ' ')
    run.font.name = 'Consolas'
    run.font.size = Pt(10.5)
    rpr = run._element.get_or_add_rPr()
    rfonts = OxmlElement('w:rFonts')
    rfonts.set(qn('w:eastAsia'), '宋体')
    rpr.append(rfonts)
    pf = p.paragraph_format
    pf.left_indent = Cm(0.6)
    pf.space_before = Pt(1)
    pf.space_after = Pt(1)
    pf.line_spacing = 1.1
    ppr = p._p.get_or_add_pPr()
    shd = OxmlElement('w:shd')
    shd.set(qn('w:val'), 'clear')
    shd.set(qn('w:fill'), 'F2F2F2')
    ppr.append(shd)
    return p


def add_picture_centered(doc, path, caption):
    if not os.path.exists(path):
        print(f'  [警告] 图片不存在: {path}')
        return
    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    run = p.add_run()
    run.add_picture(path, width=Cm(14.5))
    p.paragraph_format.space_before = Pt(6)
    p.paragraph_format.space_after = Pt(2)
    cap = para(doc, caption, size=10.5, align=WD_ALIGN_PARAGRAPH.CENTER,
               space_after=12, line_spacing=1.15)
    return cap


def set_cell_borders(cell):
    tcpr = cell._tc.get_or_add_tcPr()
    borders = OxmlElement('w:tcBorders')
    for edge in ('top', 'left', 'bottom', 'right'):
        el = OxmlElement(f'w:{edge}')
        el.set(qn('w:val'), 'single')
        el.set(qn('w:sz'), '4')
        el.set(qn('w:color'), '000000')
        borders.append(el)
    tcpr.append(borders)


def add_table(doc, rows):
    """rows: [[cell, ...], ...]，首行为表头"""
    if not rows:
        return
    cols = max(len(r) for r in rows)
    table = doc.add_table(rows=len(rows), cols=cols)
    table.alignment = WD_TABLE_ALIGNMENT.CENTER
    for i, row in enumerate(rows):
        for j in range(cols):
            cell = table.cell(i, j)
            set_cell_borders(cell)
            text = row[j] if j < len(row) else ''
            cell.text = ''
            p = cell.paragraphs[0]
            run = p.add_run(text)
            set_run_font(run, '宋体', 'Times New Roman', 10.5, bold=(i == 0))
            p.paragraph_format.line_spacing = 1.1
            p.paragraph_format.space_after = Pt(0)
            if i == 0:
                p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    para(doc, '', size=6, space_after=6)


IMG_RE = re.compile(r'^!\[(.*?)\]\((.*?)\)\s*$')
TABLE_ROW_RE = re.compile(r'^\|(.+)\|$')
TABLE_SEP_RE = re.compile(r'^\|[\s:\-|]+\|$')


def parse_table_block(lines, start):
    """从 lines[start] 开始解析 markdown 表格，返回 (rows, next_index)"""
    rows = []
    i = start
    while i < len(lines):
        line = lines[i].strip()
        if not TABLE_ROW_RE.match(line):
            break
        if TABLE_SEP_RE.match(line):
            i += 1
            continue
        cells = [c.strip() for c in line.strip('|').split('|')]
        rows.append(cells)
        i += 1
    return rows, i


def add_toc_field(doc):
    """插入 Word 自动目录域（打开后按 F9 或右键更新域生成页码）"""
    p = doc.add_paragraph()
    run = p.add_run()
    fld_begin = OxmlElement('w:fldChar')
    fld_begin.set(qn('w:fldCharType'), 'begin')
    instr = OxmlElement('w:instrText')
    instr.set(qn('xml:space'), 'preserve')
    instr.text = r' TOC \o "1-3" \h \z \u '
    fld_sep = OxmlElement('w:fldChar')
    fld_sep.set(qn('w:fldCharType'), 'separate')
    fld_text = OxmlElement('w:t')
    fld_text.text = '（目录：在 Word 中右键此处选择"更新域"生成）'
    fld_end = OxmlElement('w:fldChar')
    fld_end.set(qn('w:fldCharType'), 'end')
    for el in (fld_begin, instr, fld_sep, fld_text, fld_end):
        run._element.append(el)


def set_outline_level(p, level):
    """给段落设置大纲级别（0=一级），使目录域 TOC 能抓取自定义样式的标题"""
    ppr = p._p.get_or_add_pPr()
    outline = OxmlElement('w:outlineLvl')
    outline.set(qn('w:val'), str(level))
    ppr.append(outline)


def render_markdown(doc, md_path, base_dir):
    with open(md_path, encoding='utf-8') as f:
        lines = f.read().split('\n')

    i = 0
    while i < len(lines):
        raw = lines[i]
        line = raw.strip()

        if not line:
            i += 1
            continue

        # 图片
        m = IMG_RE.match(line)
        if m:
            caption, rel = m.group(1), m.group(2)
            path = os.path.normpath(os.path.join(base_dir, rel))
            add_picture_centered(doc, path, caption)
            i += 1
            continue

        # 标题
        if line.startswith('### '):
            p = para(doc, line[4:].strip(), east='黑体', size=14, bold=False,
                     space_before=9, space_after=6)
            set_outline_level(p, 2)
            i += 1
            continue
        if line.startswith('## '):
            p = para(doc, line[3:].strip(), east='黑体', size=15, bold=True,
                     space_before=9, space_after=6)
            set_outline_level(p, 1)
            i += 1
            continue
        if line.startswith('# '):
            title = line[2:].strip()
            # 摘要/Abstract/结论/参考文献/致谢 与章标题同为 Heading 1 级（进目录）
            centered = title in ('摘 要', '摘  要', 'Abstract', '结 论', '参考文献', '致 谢')
            p = doc.add_paragraph(style='Heading 1')
            run = p.add_run(title)
            set_run_font(run, '黑体', 'Times New Roman', 18, bold=True)
            if centered:
                p.alignment = WD_ALIGN_PARAGRAPH.CENTER
            p.paragraph_format.space_before = Pt(18)
            p.paragraph_format.space_after = Pt(9)
            p.paragraph_format.line_spacing = 1.25
            i += 1
            continue

        # 代码块
        if line.startswith('```'):
            i += 1
            while i < len(lines) and not lines[i].strip().startswith('```'):
                code_para(doc, lines[i].rstrip())
                i += 1
            i += 1
            para(doc, '', size=6, space_after=6)
            continue

        # 表格
        if TABLE_ROW_RE.match(line) and i + 1 < len(lines) and TABLE_SEP_RE.match(lines[i + 1].strip()):
            rows, next_i = parse_table_block(lines, i)
            add_table(doc, rows)
            i = next_i
            continue

        # 表标题（"表 6-1 xxx" 单独一行）
        if re.match(r'^表\s*\d+-\d+', line):
            para(doc, line, east='黑体', size=10.5, align=WD_ALIGN_PARAGRAPH.CENTER,
                 space_before=6, space_after=4, line_spacing=1.15)
            i += 1
            continue

        # 公式行（score = ... 单独成行的展示公式）
        if line.startswith('score ='):
            para(doc, line, size=12, align=WD_ALIGN_PARAGRAPH.CENTER, space_after=6)
            i += 1
            continue

        # 普通正文段
        para(doc, line, size=12, first_indent=24, space_after=0)
        i += 1


def clear_body(doc):
    """清空 body 全部内容（保留 sectPr 页面设置）"""
    body = doc.element.body
    for child in list(body):
        if child.tag == qn('w:sectPr'):
            continue
        body.remove(child)


def main():
    doc = Document(TEMPLATE)
    clear_body(doc)

    # ============ 封面 ============
    para(doc, '', size=24, space_after=0)
    para(doc, '青岛恒星科技学院', east='黑体', size=26, bold=True,
         align=WD_ALIGN_PARAGRAPH.CENTER, space_before=24, space_after=6)
    para(doc, '毕业论文（设计）', east='黑体', size=22, bold=True,
         align=WD_ALIGN_PARAGRAPH.CENTER, space_before=12, space_after=30)
    cover_rows = [
        ('题  目', TITLE),
        ('学  院', ''),
        ('专  业', ''),
        ('学  号', ''),
        ('学  生', ''),
        ('指导教师', ''),
        ('答辩日期', ''),
        ('完成日期', ''),
    ]
    cover = doc.add_table(rows=8, cols=2)
    for idx, (label, value) in enumerate(cover_rows):
        c0, c1 = cover.cell(idx, 0), cover.cell(idx, 1)
        set_cell_borders(c0)
        set_cell_borders(c1)
        c0.text = ''
        run = c0.paragraphs[0].add_run(label)
        set_run_font(run, '黑体', 'Times New Roman', 14, bold=True)
        c0.paragraphs[0].alignment = WD_ALIGN_PARAGRAPH.CENTER
        c1.text = ''
        run = c1.paragraphs[0].add_run(value)
        set_run_font(run, '宋体', 'Times New Roman', 14)
        if idx == 0:
            c1.paragraphs[0].alignment = WD_ALIGN_PARAGRAPH.CENTER

    # 封面校名（模板表格首行是校名大标题，这里单独加）
    # 说明：模板封面为独立大表格，此处用居中标题模拟校名与文书类型
    # ============ 摘要 ============
    doc.add_page_break()
    render_markdown(doc, os.path.join(THESIS_DIR, CHAPTERS[0]), THESIS_DIR)

    # ============ 目录 ============
    doc.add_page_break()
    para(doc, '目  录', east='黑体', size=18, bold=True,
         align=WD_ALIGN_PARAGRAPH.CENTER, space_before=18, space_after=18)
    add_toc_field(doc)

    # ============ 正文章节 ============
    for chapter in CHAPTERS[1:]:
        doc.add_page_break()
        render_markdown(doc, os.path.join(THESIS_DIR, chapter), THESIS_DIR)

    doc.save(OUTPUT)
    size = os.path.getsize(OUTPUT)
    print(f'生成完成: {OUTPUT} ({size/1024:.0f} KB)')


if __name__ == '__main__':
    main()

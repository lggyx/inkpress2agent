"""填充三份过程材料 docx：任务书 / 开题报告 / 中期报告
- 任务书：保留模板表格，替换单元格内容
- 开题报告：段落型模板，按节插入正文（内容复用 docs/开题报告.md）
- 中期报告：段落型模板，按节插入正文（新写）
用法：python scripts/forms/build_forms.py
"""
import os
import re
from docx import Document
from docx.shared import Pt, Cm
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.oxml.ns import qn
from docx.oxml import OxmlElement
from docx.text.paragraph import Paragraph

OUT_DIR = 'docs/过程材料'
TITLE = '基于Spring Boot+Vue的个人博客与文章管理系统的设计与实现'

# 学生信息（来自正式任务书）
STUDENT = {
    '学院': '大数据与智能信息工程学院',
    '专业': '软件工程',
    '校号': '2505396',
    '学生': '张德鑫',
    '指导教师': '王婧',
}

os.makedirs(OUT_DIR, exist_ok=True)


# ---------- 通用工具 ----------

def set_run_font(run, east='宋体', west='Times New Roman', size=12, bold=False):
    run.font.name = west
    run.font.size = Pt(size)
    run.font.bold = bold
    rpr = run._element.get_or_add_rPr()
    rfonts = rpr.find(qn('w:rFonts'))
    if rfonts is None:
        rfonts = OxmlElement('w:rFonts')
        rpr.append(rfonts)
    rfonts.set(qn('w:eastAsia'), east)


def body_para(doc, text, size=12, indent=True, bold=False, east='宋体'):
    p = doc.add_paragraph()
    run = p.add_run(text)
    set_run_font(run, east, 'Times New Roman', size, bold)
    pf = p.paragraph_format
    pf.line_spacing = 1.25
    pf.space_after = Pt(0)
    if indent:
        pf.first_line_indent = Pt(24)
    return p


def insert_paragraphs_before(doc, para, texts, size=12, indent=True):
    """在指定段落之前批量插入正文段（用于在节标题后追加内容）"""
    for text in texts:
        p = para.insert_paragraph_before()
        run = p.add_run(text)
        set_run_font(run, '宋体', 'Times New Roman', size)
        pf = p.paragraph_format
        pf.line_spacing = 1.25
        pf.space_after = Pt(0)
        if indent:
            pf.first_line_indent = Pt(24)


def find_para(doc, keyword):
    for p in doc.paragraphs:
        if keyword in p.text:
            return p
    return None


def fill_cell(cell, text, east='宋体', size=12, bold=False, align=None):
    cell.text = ''
    p = cell.paragraphs[0]
    run = p.add_run(text)
    set_run_font(run, east, 'Times New Roman', size, bold)
    if align is not None:
        p.alignment = align
    p.paragraph_format.line_spacing = 1.25


def fill_cover(doc, date_text):
    """填充报告封面：学院/专业/校号/学生/指导教师/日期"""
    # 填充题目
    p_title = find_para(doc, '题    目')
    if p_title:
        p_title.text = ''
        r_lbl = p_title.add_run('题    目： ')
        set_run_font(r_lbl, '宋体', 'Times New Roman', 16, bold=False)
        r_val = p_title.add_run(TITLE)
        r_val.underline = True
        set_run_font(r_val, '宋体', 'Times New Roman', 16, bold=False)
        p_title.paragraph_format.line_spacing = 1.25

    label_map = {
        '学    院': STUDENT['学院'],
        '专    业': STUDENT['专业'],
        '校    号': STUDENT['校号'],
        '学    生': STUDENT['学生'],
        '指导教师': STUDENT['指导教师'],
        '日    期': date_text,
    }
    for p in doc.paragraphs:
        txt = p.text
        for label, val in label_map.items():
            if txt.startswith(label):
                p.text = ''
                r1 = p.add_run(label + '  ')
                set_run_font(r1, '宋体', 'Times New Roman', 15, bold=False)
                r2 = p.add_run(f'  {val}  ')
                r2.underline = True
                set_run_font(r2, '宋体', 'Times New Roman', 15, bold=False)
                p.paragraph_format.line_spacing = 1.25
                break


# ---------- 1. 任务书 ----------

def build_renwu():
    doc = Document('scripts/forms/templates/docx/1.任务书（2025）.docx')
    table = doc.tables[0]

    # 行0：专业 / 班级
    fill_cell(table.cell(0, 1), '计算机科学与技术')
    # 行1：学生姓名 / 指导教师（留空待填）
    # 行2：题目（跨列合并单元格，写第 1 列）
    fill_cell(table.cell(2, 1), TITLE)
    # 行3：选题类型
    fill_cell(table.cell(3, 1), '☑ 毕业论文')
    # 行4：设计时间
    fill_cell(table.cell(4, 1), '2026 年 11 月 至 2027 年 4 月，共 12 设计周')

    # 行5：专业方向、基本理论及设计内容
    fill_cell(table.cell(5, 1),
              '本设计属于计算机应用技术方向，具体为 Web 应用系统开发。'
              '系统以软件工程方法学为指导，综合运用数据库设计、网络安全认证、缓存与一致性、'
              '自然语言处理中的文本表示与检索排序等理论，设计并实现一个数据自有的个人博客与文章管理系统。'
              '技术栈上，后端采用 Spring Boot 3.3 框架与 MyBatis-Plus 持久层，'
              '数据库为 MySQL 8.0，缓存为 Redis 7，认证采用 Spring Security 与 JWT 无状态方案；'
              '前端采用 Vue 3 组合式 API 与 Vite 构建。'
              '在基础功能之外，本设计将大语言模型能力封装为可插拔的写作辅助模块，'
              '并实现基于特征哈希向量的站内语义检索与检索增强生成（RAG）问答，'
              '涉及文本向量化、余弦相似度计算、混合排序与提示词工程等方法。')
    # 行6：本人具体完成的工作
    fill_cell(table.cell(6, 1),
              '本人独立完成的工作包括：（1）需求分析与总体设计，完成用例梳理、架构设计、'
              '数据库 E-R 设计与七张表的详细设计；（2）后端编码，实现认证授权、文章管理、'
              '分类标签、评论审核、数据看板等模块，以及 AI 写作辅助（润色、摘要、标签、续写、标题）'
              '与智能检索（相关文章推荐、语义搜索、RAG 问答）模块；'
              '（3）前端编码，实现前台阅读页面与后台管理页面，包括目录大纲、阅读进度、'
              '上/下篇导航、3D 视差等交互；（4）测试与文档，编写接口冒烟测试脚本（26 项断言），'
              '完成功能测试、边界测试与检索实验，撰写部署文档、接口文档与毕业论文。')
    # 行7：主要参考文献
    fill_cell(table.cell(7, 1),
              '[1] Fielding R T. Architectural Styles and the Design of Network-based Software Architectures[D]. 2000.\n'
              '[2] Vaswani A, et al. Attention Is All You Need[C]. NeurIPS, 2017.\n'
              '[3] Brown T B, et al. Language Models are Few-Shot Learners[C]. NeurIPS, 2020.\n'
              '[4] Lewis P, et al. Retrieval-Augmented Generation for Knowledge-Intensive NLP Tasks[C]. NeurIPS, 2020.\n'
              '[5] Weinberger K, et al. Feature Hashing for Large Scale Multitask Learning[C]. ICML, 2009.\n'
              '[6] 赵鑫, 等. 大语言模型[M]. 北京: 中国人民大学出版社, 2024.\n'
              '[7] 克雷格·沃斯. Spring 实战（第5版）[M]. 北京: 人民邮电出版社, 2020.\n'
              '[8] 霍春阳. Vue.js 设计与实现[M]. 北京: 人民邮电出版社, 2022.')

    out = os.path.join(OUT_DIR, '1.任务书-已填写.docx')
    doc.save(out)
    print('任务书 ->', out)


# ---------- 2. 开题报告 ----------

KAITI_SECTION_MAP = [
    ('1.选题背景及研究的目的和意义', '1'),
    ('1.1选题背景', '1.1'),
    ('1.2研究的目的和意义', '1.2'),
    ('2.国内外在该方向的研究现状及分析', '2'),
    ('2.1国外研究现状及分析', '2.1'),
    ('2.2国内研究现状及分析', '2.2'),
    ('3.研究内容', '3'),
    ('4.拟采取的研究方法和技术路线', '4'),
    ('5.进度安排、预期达到的目标', '5'),
    ('6.课题已具备和所需的条件', '6'),
    ('7.研究过程中可能遇到的困难和问题，解决的措施', '7'),
    ('8.参考文献', '8'),
]

# 条目型段落的段首引导词：这些词在正文段落开头出现时加粗（冒号一并加粗）
PARA_LABELS = {
    '理论意义', '现实意义', '问题', '解决措施',
    '用户注册与登录验证', '个人信息维护与消息通知',
    'Markdown 在线编辑与发布管理', '文章分类与存储适配', '评论互动与高频访问计数',
    '文本润色与语法纠错', '文章摘要与标签生成', '内容续写与标题推荐',
    '多平台排版格式转换', '草稿自动同步填充', '分发记录与外链保存',
    '关键词与向量混合检索', '访问安全与系统监控',
}


def parse_kaiti_md():
    """解析 docs/开题报告.md，提取结构化元素序列。"""
    with open('docs/开题报告.md', encoding='utf-8') as f:
        content = f.read()

    sections = {}
    current = None

    lines = content.split('\n')
    i = 0
    while i < len(lines):
        raw = lines[i]
        line = raw.strip()
        line = line.replace('**', '')

        if not line or line.startswith('>') or line == '---' or line.startswith('```') or line.startswith('@'):
            i += 1
            continue

        if line.startswith('## '):
            title_text = line[3:].strip()
            num = title_text.split('.')[0].strip()
            current = num
            sections.setdefault(current, [])
            i += 1
            continue

        if line.startswith('### '):
            title_text = line[4:].strip()
            num = title_text.split(' ')[0].rstrip('.')
            if num in ('1.1', '1.2', '2.1', '2.2'):
                current = num
                sections.setdefault(current, [])
            else:
                parent = num.split('.')[0]
                sections.setdefault(parent, []).append({'type': 'heading2', 'text': title_text})
                current = parent
            i += 1
            continue

        if line.startswith('#### '):
            title_text = line[5:].strip()
            if current:
                sections[current].append({'type': 'heading3', 'text': title_text})
            i += 1
            continue

        if line.startswith('!['):
            m = re.search(r'\((.*?)\)', line)
            if m and current:
                sections[current].append({'type': 'image', 'path': m.group(1)})
            i += 1
            continue

        if line.startswith('<center>图'):
            cap = re.sub(r'</?center>', '', line).strip()
            if current:
                sections[current].append({'type': 'figure_caption', 'text': cap})
            i += 1
            continue

        if line.startswith('<center>表'):
            cap = re.sub(r'</?center>', '', line).strip()
            if current:
                sections[current].append({'type': 'table_caption', 'text': cap})
            i += 1
            continue

        if line.startswith('|'):
            table_rows = []
            while i < len(lines) and lines[i].strip().startswith('|'):
                t_line = lines[i].strip()
                if '---' not in t_line:
                    cells = [c.strip() for c in t_line.strip('|').split('|')]
                    table_rows.append(cells)
                i += 1
            if table_rows and current:
                sections[current].append({'type': 'table', 'rows': table_rows})
            continue

        if current:
            clean_text = line
            if clean_text.startswith('- '):
                clean_text = clean_text[2:].strip()
            sections[current].append({'type': 'para', 'text': clean_text})
        i += 1

    return sections


def clean_heading_text(text):
    """去掉模板标题里的格式说明，如 '（一级标题 黑体小二，段前0.5，段后0.5，行间距1.25）'"""
    return re.sub(r'（(一级标题|二级标题|三级标题)[^）]*）', '', text).strip()


def insert_para_after(para, text='', size=12, indent=True, bold=False, east='宋体', before=0, after=0, parse_citations=False, lead_text=None):
    """在 para 之后插入一个正文段，返回新段（用于链式插入）。
    lead_text：段首引导词（如"理论意义："），单独以加粗 run 插入。"""
    new_p = OxmlElement('w:p')
    para._p.addnext(new_p)
    np = Paragraph(new_p, para._parent)
    if lead_text:
        run = np.add_run(lead_text)
        set_run_font(run, east, 'Times New Roman', size, bold=True)
    if text:
        if parse_citations:
            parts = re.split(r'(\[\d+\])', text)
            for part in parts:
                if not part:
                    continue
                m = re.match(r'^\[(\d+)\]$', part)
                if m:
                    ref_id = m.group(1)
                    # 插入可点击跳转到文末对应参考文献的内部超链接（上角标格式）
                    hl = OxmlElement('w:hyperlink')
                    hl.set(qn('w:anchor'), f'ref_{ref_id}')
                    hl.set(qn('w:history'), '1')
                    r = OxmlElement('w:r')
                    rPr = OxmlElement('w:rPr')
                    
                    rFonts = OxmlElement('w:rFonts')
                    rFonts.set(qn('w:ascii'), 'Times New Roman')
                    rFonts.set(qn('w:hAnsi'), 'Times New Roman')
                    rFonts.set(qn('w:eastAsia'), east)
                    rPr.append(rFonts)
                    
                    sz = OxmlElement('w:sz')
                    sz.set(qn('w:val'), str(int(size * 2)))
                    rPr.append(sz)
                    szCs = OxmlElement('w:szCs')
                    szCs.set(qn('w:val'), str(int(size * 2)))
                    rPr.append(szCs)
                    
                    if bold:
                        b = OxmlElement('w:b')
                        rPr.append(b)
                        
                    va = OxmlElement('w:vertAlign')
                    va.set(qn('w:val'), 'superscript')
                    rPr.append(va)
                    
                    t = OxmlElement('w:t')
                    t.text = part
                    r.append(rPr)
                    r.append(t)
                    hl.append(r)
                    np._p.append(hl)
                else:
                    run = np.add_run(part)
                    set_run_font(run, east, 'Times New Roman', size, bold)
        else:
            run = np.add_run(text)
            set_run_font(run, east, 'Times New Roman', size, bold)
    pf = np.paragraph_format
    pf.line_spacing = 1.25
    pf.space_before = Pt(before)
    pf.space_after = Pt(after)
    if indent:
        pf.first_line_indent = Pt(24)
    return np


def insert_heading_para(anchor, text, style_id, before=None, after=None):
    """在 anchor 之后插入真正的标题样式段落（Subtitle/Heading 3），进目录与导航窗格。
    格式镜像模板真实标题段（如 1.1选题背景）：编号 run 用 Times New Roman，
    中文 run 依赖样式的黑体并显式置黑，字号由样式给出（Subtitle 小三 / Heading 3 14pt）。"""
    new_p = OxmlElement('w:p')
    anchor._p.addnext(new_p)
    np = Paragraph(new_p, anchor._parent)
    pPr = np._p.get_or_add_pPr()
    ps = OxmlElement('w:pStyle')
    ps.set(qn('w:val'), style_id)
    pPr.append(ps)
    if before is not None or after is not None:
        sp = OxmlElement('w:spacing')
        if before is not None:
            sp.set(qn('w:before'), str(before))
        if after is not None:
            sp.set(qn('w:after'), str(after))
        pPr.append(sp)

    def add_head_run(part, western=False):
        r = np.add_run(part)
        rpr = r._element.get_or_add_rPr()
        rfonts = rpr.find(qn('w:rFonts'))
        if rfonts is None:
            rfonts = OxmlElement('w:rFonts')
            rpr.insert(0, rfonts)
        if western:
            rfonts.set(qn('w:ascii'), 'Times New Roman')
            rfonts.set(qn('w:hAnsi'), 'Times New Roman')
            rfonts.set(qn('w:cs'), 'Times New Roman')
        rfonts.set(qn('w:hint'), 'eastAsia')
        color = OxmlElement('w:color')
        color.set(qn('w:val'), '000000')
        rpr.append(color)

    m = re.match(r'^(\d+(?:\.\d+)*)[ ]?(.+)$', text)
    if m:
        add_head_run(m.group(1), western=True)
        add_head_run(m.group(2))
    else:
        add_head_run(text)
    return np


def set_table_borders(table):
    """论文规范三线表：顶线/底线 1.5pt（sz=12），表头下栏目线 0.75pt（sz=6），
    无竖线、无行间横线。栏目线写在表头单元格的 tcBorders 上（覆盖 insideH 的 none），
    随 tblHeader 跨页重复时栏目线一并保留。"""
    tbl_pr = table._tbl.tblPr
    borders = OxmlElement('w:tblBorders')
    for edge, val, sz in (('top', 'single', '12'), ('left', 'none', '0'),
                          ('bottom', 'single', '12'), ('right', 'none', '0'),
                          ('insideH', 'none', '0'), ('insideV', 'none', '0')):
        el = OxmlElement('w:' + edge)
        el.set(qn('w:val'), val)
        if val != 'none':
            el.set(qn('w:sz'), sz)
            el.set(qn('w:color'), '000000')
        borders.append(el)
    tbl_pr.append(borders)
    # 栏目线：表头行各单元格下边框；插入位置须在 tcW 之后、vAlign 之前（schema 顺序）
    for cell in table.rows[0].cells:
        tc_pr = cell._tc.get_or_add_tcPr()
        for old in tc_pr.findall(qn('w:tcBorders')):
            tc_pr.remove(old)
        tc_borders = OxmlElement('w:tcBorders')
        bottom = OxmlElement('w:bottom')
        bottom.set(qn('w:val'), 'single')
        bottom.set(qn('w:sz'), '6')
        bottom.set(qn('w:space'), '0')
        bottom.set(qn('w:color'), '000000')
        tc_borders.append(bottom)
        tc_w = tc_pr.find(qn('w:tcW'))
        if tc_w is not None:
            tc_w.addnext(tc_borders)
        else:
            tc_pr.insert(0, tc_borders)


def find_heading(doc, text, styles=('Heading 1', 'Subtitle')):
    """按清洗后的标题文本精确匹配段落"""
    for p in doc.paragraphs:
        if p.style is not None and p.style.name in styles and p.text.strip() == text:
            return p
    return None


def clean_template_junk(doc):
    """清除学校模板自带的格式说明/占位段落/批注/冗余分页符，返回删除条数"""
    removed = 0

    # 彻底清除模板中所有批注与审阅标签（commentRangeStart, commentRangeEnd, commentReference, annotationRef）
    comment_tags = {'commentRangeStart', 'commentRangeEnd', 'commentReference', 'annotationRef'}
    for el in list(doc._element.xpath('//*')):
        local_tag = el.tag.split('}')[-1] if '}' in el.tag else el.tag
        if local_tag in comment_tags:
            parent = el.getparent()
            if parent is not None:
                parent.remove(el)

    # 清除目录末尾冗余的硬分页符与空段（模板自带在 sectPr 段落中又加了 w:br，导致 Word 产生空白页 II）
    for p in list(doc.paragraphs):
        if p._p.xpath('.//w:sectPr') and p._p.xpath('.//w:br[@w:type="page"]'):
            for br in p._p.xpath('.//w:br[@w:type="page"]'):
                r = br.getparent()
                if r is not None:
                    p._p.remove(r)
        # 清除目录末尾在分节符前的纯空行
        if not p.text.strip() and not p._p.xpath('.//w:sectPr') and not p._p.xpath('.//w:drawing'):
            nxt = p._p.getnext()
            if nxt is not None and nxt.xpath('.//w:sectPr'):
                p._p.getparent().remove(p._p)
                removed += 1

    junk_texts = ('青岛恒星科技学院毕业论文（设计）开题报告正文',)
    for p in list(doc.paragraphs):
        t = p.text.strip()
        style = p.style.name if p.style is not None else ''
        if style in ('Heading 1', 'Subtitle') and re.search(r'（(一级标题|二级标题|三级标题)', t):
            full = ''.join(r.text for r in p.runs)
            cleaned = clean_heading_text(full)
            if p.runs:
                p.runs[0].text = cleaned
                for r in p.runs[1:]:
                    r.text = ''
            t = p.text.strip()
        if (t in junk_texts or t.startswith('（正文') or t == '（1）'
                or t.startswith('要求：[') or '企业挖掘模型' in t
                or (style == 'Heading 1' and not t)):
            p._p.getparent().remove(p._p)
            removed += 1
    return removed


def normalize_doc_defaults(doc):
    """把文档默认字体（docDefaults）改为 宋体 / Times New Roman。
    模板 docDefaults 的西文残留 Liberation Serif（LibreOffice 的 TNR 替身字体），
    且 Word COM 保存时会剥掉 run 级 rFonts——默认值若不修正，
    交付文档在未安装该字体的机器上西文将回退到替代渲染。"""
    styles_el = doc.styles.element
    for dd in styles_el.findall(qn('w:docDefaults')):
        rprd = dd.find(qn('w:rPrDefault'))
        if rprd is None:
            continue
        rpr = rprd.find(qn('w:rPr'))
        if rpr is None:
            continue
        rf = rpr.find(qn('w:rFonts'))
        if rf is None:
            rf = OxmlElement('w:rFonts')
            rpr.insert(0, rf)
        rf.set(qn('w:ascii'), 'Times New Roman')
        rf.set(qn('w:hAnsi'), 'Times New Roman')
        rf.set(qn('w:cs'), 'Times New Roman')
        rf.set(qn('w:eastAsia'), '宋体')


def format_headers(doc):
    """
    严格按照青岛恒星科技学院《毕业论文撰写管理细则》规范格式化页眉：
    1. 清除模板残留的错误绘图线条（避免与文字重叠的黑色粗条块）
    2. 第一页（封面）：无页眉与页眉线（留空由用户后续替换复杂封面）
    3. 目录与正文后续页面：页眉文字为“青岛恒星科技学院本科毕业论文（设计）开题报告”，
       小五号（9pt）宋体居中，下边线为标准规范的粗细双线（thickThinMediumGap，0.8mm）。
    """
    for idx, s in enumerate(doc.sections):
        if idx == 0:
            # 封面节：彻底清除页眉文字、图形线条与边框线。
            # 注意：页眉段落引用的样式（Header 链）自带下边框，仅清段落级 pBdr 不够，
            # 必须断开样式引用并显式声明四边无边框来覆盖继承边框。
            s.different_first_page_header_footer = True
            for h in [s.header, s.first_page_header]:
                for p in h.paragraphs:
                    p.text = ''
                    # 清除残留图形线条
                    for dw in p._p.xpath('.//*[local-name()="drawing" or local-name()="pict" or local-name()="AlternateContent"]'):
                        parent = dw.getparent()
                        if parent is not None:
                            parent.remove(dw)
                    pPr = p._p.get_or_add_pPr()
                    # 去掉样式引用，断开样式边框继承链
                    ps = pPr.find(qn('w:pStyle'))
                    if ps is not None:
                        pPr.remove(ps)
                    # 显式声明四边无边框
                    for old_bdr in pPr.findall(qn('w:pBdr')):
                        pPr.remove(old_bdr)
                    pBdr = OxmlElement('w:pBdr')
                    for edge in ('top', 'left', 'bottom', 'right'):
                        el = OxmlElement('w:' + edge)
                        el.set(qn('w:val'), 'none')
                        el.set(qn('w:sz'), '0')
                        el.set(qn('w:space'), '0')
                        pBdr.append(el)
                    pPr.append(pBdr)
            continue

        # 目录节与正文节
        for h in [s.header, s.first_page_header]:
            for p in h.paragraphs:
                # 清除任何残留的 drawing、pict 和 AlternateContent 元素
                drawings = p._p.xpath('.//*[local-name()="drawing" or local-name()="pict" or local-name()="AlternateContent"]')
                for dw in drawings:
                    parent = dw.getparent()
                    if parent is not None:
                        parent.remove(dw)

                # 统一设置页眉文本与格式（小五号宋体居中）
                p.text = '青岛恒星科技学院本科毕业论文（设计）开题报告'
                p.alignment = WD_ALIGN_PARAGRAPH.CENTER
                if p.runs:
                    set_run_font(p.runs[0], '宋体', 'Times New Roman', 9, bold=False)

                # 设置版芯标准粗细双线（Word规范中底边框为 thinThickMediumGap 实现上粗下细，宽0.8mm即sz=18）
                pPr = p._p.get_or_add_pPr()
                for old_bdr in pPr.findall(qn('w:pBdr')):
                    pPr.remove(old_bdr)
                pBdr = OxmlElement('w:pBdr')
                bottom = OxmlElement('w:bottom')
                bottom.set(qn('w:val'), 'thinThickMediumGap')
                bottom.set(qn('w:sz'), '18')
                bottom.set(qn('w:space'), '4')
                bottom.set(qn('w:color'), '000000')
                pBdr.append(bottom)
                pPr.append(pBdr)


def build_kaiti():
    doc = Document('scripts/forms/templates/docx/2.开题报告（2025）.docx')
    md = parse_kaiti_md()
    removed = clean_template_junk(doc)

    # 目录域只收两级：模板默认 \o "1-3" 会把 20 余个三级小节也收进目录导致溢出，
    # 编号二级标题（4.1 等）收进目录，三级小节仅进导航窗格
    for instr in doc.element.xpath('//*[local-name()="instrText"]'):
        if instr.text and 'TOC' in instr.text:
            instr.text = instr.text.replace(r'\o "1-3"', r'\o "1-2"')

    # 按照用户要求：第一页封面由用户后续自行替换复杂格式，本脚本不修改第一页内容

    # 按节插入内容（内容挂在各节标题之下）
    table_count = 0
    image_count = 0

    for heading_text, sec in KAITI_SECTION_MAP:
        h = find_heading(doc, heading_text)
        if h is None:
            print('  [警告] 未找到节标题: %s' % heading_text)
            continue
        items = md.get(sec, [])
        anchor = h

        for item in items:
            itype = item['type']

            if itype == 'heading2':
                # 二级标题（4.1/4.2/5.1/5.2/6.1/6.2）：套用模板 Subtitle 样式，
                # 进目录与导航窗格，格式与模板自带 1.1/2.1 一致（黑体小三）
                text = re.sub(r'^(\d+\.\d+)\s+', r'\1', item['text'])
                anchor = insert_heading_para(anchor, text, 'Subtitle', before=120, after=120)

            elif itype == 'heading3':
                # 三级标题（研究目的、文献研究法、个人中心等）：套用模板 Heading 3 样式（黑体14pt）
                anchor = insert_heading_para(anchor, item['text'], 'Heading3')

            elif itype == 'image':
                # 图片居中段落
                new_p = OxmlElement('w:p')
                anchor._p.addnext(new_p)
                img_p = Paragraph(new_p, anchor._parent)
                img_p.alignment = WD_ALIGN_PARAGRAPH.CENTER
                pf = img_p.paragraph_format
                pf.space_before = Pt(2)
                pf.space_after = Pt(1)
                pf.line_spacing = 1.0
                pf.first_line_indent = Pt(0)
                pf.keep_with_next = True  # 保证图片与下方图题注绑定在同一页，严禁跨页分离

                run = img_p.add_run()
                img_path = item['path']
                if os.path.exists(img_path):
                    # 针对功能模块图和技术路线图设置适中宽度
                    width = Cm(13.2) if '14-功能模块图' in img_path else Cm(9.2)
                    run.add_picture(img_path, width=width)
                    image_count += 1
                else:
                    run.add_text(f'[图片丢失: {img_path}]')
                anchor = img_p

            elif itype == 'figure_caption':
                # 图题注（居中，五号宋体）
                cap_p = insert_para_after(anchor, item['text'], size=10.5, indent=False, bold=False, east='宋体', before=1, after=2)
                cap_p.alignment = WD_ALIGN_PARAGRAPH.CENTER
                anchor = cap_p

            elif itype == 'table_caption':
                # 表题注（居中，五号宋体，与表格锁定同页）
                cap_p = insert_para_after(anchor, item['text'], size=10.5, indent=False, bold=False, east='宋体', before=2, after=1)
                cap_p.alignment = WD_ALIGN_PARAGRAPH.CENTER
                cap_p.paragraph_format.keep_with_next = True
                anchor = cap_p

            elif itype == 'table':
                # 插入表格
                rows = item['rows']
                cols_count = len(rows[0])
                tbl = doc.add_table(rows=len(rows), cols=cols_count)

                # 按列数设置合理列宽（两列进度表、三列选型表）
                if cols_count == 2:
                    col_widths = [Cm(4.8), Cm(10.7)]
                elif cols_count == 3:
                    col_widths = [Cm(3.0), Cm(4.5), Cm(8.0)]
                else:
                    col_widths = None

                for ri, row in enumerate(rows):
                    for ci, cell_text in enumerate(row):
                        cell = tbl.cell(ri, ci)
                        if col_widths and ci < len(col_widths):
                            cell.width = col_widths[ci]
                        cell.text = ''
                        p = cell.paragraphs[0]
                        run = p.add_run(cell_text)
                        set_run_font(run, '黑体' if ri == 0 else '宋体',
                                     'Times New Roman', 10.5, bold=(ri == 0))
                        p.paragraph_format.line_spacing = 1.05
                        p.paragraph_format.space_before = Pt(1)
                        p.paragraph_format.space_after = Pt(1)
                        if ri == 0 or ci == 0:
                            p.alignment = WD_ALIGN_PARAGRAPH.CENTER
                        else:
                            p.alignment = WD_ALIGN_PARAGRAPH.LEFT
                # 表格样式：水平居中；表头行跨页重复；所有行禁止跨页拆分（防止半行文字被劈开）；
                # 单元格文字垂直居中
                tbl_pr = tbl._tbl.tblPr
                jc = OxmlElement('w:jc')
                jc.set(qn('w:val'), 'center')
                tbl_pr.append(jc)
                for ri2, row in enumerate(tbl.rows):
                    tr = row._tr
                    tr_pr = tr.find(qn('w:trPr'))
                    if tr_pr is None:
                        tr_pr = OxmlElement('w:trPr')
                        tr.insert(0, tr_pr)
                    tr_pr.append(OxmlElement('w:cantSplit'))
                    if ri2 == 0:
                        tr_pr.append(OxmlElement('w:tblHeader'))
                    for cell in row.cells:
                        va = OxmlElement('w:vAlign')
                        va.set(qn('w:val'), 'center')
                        cell._tc.get_or_add_tcPr().append(va)
                set_table_borders(tbl)
                anchor._p.addnext(tbl._tbl)

                # 在表格后插入一个过渡定位段落，作为后续内容的锚点
                new_p = OxmlElement('w:p')
                tbl._tbl.addnext(new_p)
                next_p = Paragraph(new_p, doc._body)
                next_p.paragraph_format.line_spacing = 1.25
                next_p.paragraph_format.space_before = Pt(0)
                next_p.paragraph_format.space_after = Pt(0)
                anchor = next_p
                table_count += 1

            elif itype == 'para':
                # 正文段落
                if sec == '8':
                    # 参考文献条目：编号 [n] 左对齐，编号后用制表符把正文对齐到
                    # 悬挂缩进位（24pt），各行正文起始位置统一；紧凑行距避免多余跨页
                    text = item['text']
                    m_ref = re.match(r'^(\[\d+\])\s*(.*)$', text)
                    num_part, body_part = (m_ref.group(1), m_ref.group(2)) if m_ref else (None, text)
                    new_p = OxmlElement('w:p')
                    anchor._p.addnext(new_p)
                    p_ref = Paragraph(new_p, anchor._parent)
                    if num_part:
                        r_num = p_ref.add_run(num_part)
                        set_run_font(r_num, '宋体', 'Times New Roman', 10.5)
                        r_num._element.append(OxmlElement('w:tab'))
                    if body_part:
                        # 页码区间的连字符（数字-数字）用不换行连字符 w:noBreakHyphen，
                        # 避免 "58-"/"63." 之类被劈到两行；普通连字符（如 Few-Shot）不受影响
                        segs = re.split(r'(?<=\d)-(?=\d)', body_part)
                        last_run = None
                        for i, seg in enumerate(segs):
                            if seg:
                                last_run = p_ref.add_run(seg)
                                set_run_font(last_run, '宋体', 'Times New Roman', 10.5)
                            if i < len(segs) - 1:
                                if last_run is None:
                                    last_run = p_ref.add_run('')
                                last_run._element.append(OxmlElement('w:noBreakHyphen'))
                    pf = p_ref.paragraph_format
                    pf.line_spacing = 1.15
                    pf.left_indent = Pt(24)
                    pf.first_line_indent = Pt(-24)
                    pf.tab_stops.add_tab_stop(Pt(24))
                    
                    # 添加内部书签锚点供正文引用跳转
                    m = re.match(r'^\[(\d+)\]', item['text'])
                    if m:
                        ref_id = m.group(1)
                        bm_start = OxmlElement('w:bookmarkStart')
                        bm_start.set(qn('w:id'), str(200 + int(ref_id)))
                        bm_start.set(qn('w:name'), f'ref_{ref_id}')
                        bm_end = OxmlElement('w:bookmarkEnd')
                        bm_end.set(qn('w:id'), str(200 + int(ref_id)))
                        
                        pPr = p_ref._p.pPr
                        if pPr is not None:
                            pPr.addnext(bm_start)
                        else:
                            p_ref._p.insert(0, bm_start)
                        p_ref._p.append(bm_end)

                    anchor = p_ref
                else:
                    # 常规段落：首行缩进 2 字符，自动解析 [1] 上角标并建立跳转超链接；
                    # 条目型段落（PARA_LABELS）把冒号前的引导词加粗
                    text = item['text']
                    lead = None
                    m_label = re.match(r'^([^：]{2,20})：(.*)$', text)
                    if m_label and m_label.group(1) in PARA_LABELS:
                        lead = m_label.group(1) + '：'
                        text = m_label.group(2)
                    anchor = insert_para_after(anchor, text, size=12, indent=True, bold=False, east='宋体', before=0, after=0, parse_citations=True, lead_text=lead)

    # 清除由于表格过渡可能产生的多余空段
    for p in list(doc.paragraphs):
        if not p.text.strip() and not p._element.xpath('.//w:drawing') and p.style is not None and p.style.name == 'Normal':
            prev = p._p.getprevious()
            if prev is not None and prev.tag.endswith('tbl'):
                p._p.getparent().remove(p._p)

    # 清除文档末尾所有模板残留的无用空段落（彻底消除空白尾页）
    while doc.paragraphs and not doc.paragraphs[-1].text.strip() and not doc.paragraphs[-1]._element.xpath('.//w:drawing'):
        last_p = doc.paragraphs[-1]
        last_p._p.getparent().remove(last_p._p)

    # 文档属性：清掉模板残留的学校名/作者，写入课题与学生信息
    cp = doc.core_properties
    cp.title = TITLE
    cp.subject = '毕业论文（设计）开题报告'
    cp.author = '张德鑫'
    cp.last_modified_by = '张德鑫'
    cp.comments = ''

    # 打开时自动更新域（目录页码重算）
    settings = doc.settings.element
    if settings.find(qn('w:updateFields')) is None:
        uf = OxmlElement('w:updateFields')
        uf.set(qn('w:val'), 'true')
        settings.append(uf)

    # 格式化页眉与标准双线；修正文档默认字体
    format_headers(doc)
    normalize_doc_defaults(doc)

    out = os.path.join(OUT_DIR, '2.开题报告-已填写.docx')
    doc.save(out)
    print('开题报告 -> %s（清除模板残留 %d 处，插入图片 %d 张，插入表格 %d 个）'
          % (out, removed, image_count, table_count))


# ---------- 3. 中期报告 ----------

def build_midterm():
    doc = Document('scripts/forms/templates/docx/4.中期报告（2025）.docx')
    removed = clean_template_junk(doc)
    normalize_doc_defaults(doc)

    fill_cover(doc, '2027 年 1 月')

    def insert_after_heading(keyword, texts):
        p = find_para(doc, keyword)
        if p is not None and texts:
            insert_paragraphs_before(doc, p, texts)

    # 1. 论文工作是否按预期进行
    insert_after_heading('1.论文工作是否按预期进行', [
        '论文工作总体按预期进行。本课题毕业设计自 2026 年 11 月开题，按开题时确定的进度安排，'
        '目前（2027 年 1 月，第 8 周）已完成前期的准备与主体研发任务：'
        '选题调研与开题、需求分析与系统设计、系统编码实现与模块联调，进度与计划基本一致。'
        '系统核心功能已全部开发完成并投入试运行，站内已积累 38 篇文章，'
        '为后续测试和论文撰写提供了真实数据。个别任务的完成时间与计划有偏差——'
        '智能检索模块的向量实现比原计划多花了一周，原因是本地特征哈希向量的'
        '维度与归一化细节需要反复调试验证，但该模块的整体方向未变，不影响后续进度。'
    ])
    # 2.1 已完成的研究工作
    insert_after_heading('2.1目前已完成的研究工作', [
        '已完成的工作包括四个方面。'
        '第一，需求分析与设计：梳理了访客、管理员两类角色的用例，完成总体架构设计、'
        '功能模块划分和数据库设计，数据库包含用户、文章、分类、标签、文章标签关联、评论、'
        '操作日志七张表，E-R 图与表结构文档已定稿。'
        '第二，后端实现：基于 Spring Boot 3.3 完成认证授权（Spring Security + JWT）、'
        '文章管理、分类标签、评论审核、数据看板等模块；浏览量采用 Redis 计数加定时回写，'
        '规避了高频写库问题。'
        '第三，AI 能力实现：完成适配层设计，对接 OpenAI 兼容协议，实现润色、摘要、标签、'
        '续写、标题五项写作辅助功能，润色与续写采用 SSE 流式输出；'
        '在此基础上实现站内语义检索、相关文章混合排序推荐与 RAG 知识问答。'
        '第四，前端实现：完成前台阅读页面与后台管理页面，包括目录大纲、阅读进度、'
        '上/下篇导航、3D 卡片视差等交互细节。'
    ])
    # 2.2 已完成的研究结果
    insert_after_heading('2.2目前已完成的研究结果', [
        '阶段性结果有三项。'
        '结果一：系统可运行，后端通过 Maven 编译打包，前端通过 Vite 构建，'
        '已部署在本地 Docker 环境稳定运行。'
        '结果二：质量有量化保障，接口冒烟测试脚本覆盖 26 项断言（认证、文章、评论、'
        '点赞、参数边界、检索推荐、AI 降级），全部通过；功能测试设计用例三十余项，'
        '发现的边界问题（超大分页参数、超长标签名）均已修复并补入回归。'
        '结果三：检索效果有实验支撑，构造五组自然语言查询进行语义检索实验，'
        'Top-1 全部命中预期文章，Top-3 命中率 100%，实验数据已整理成论文表格。'
    ])
    # 3.1 后期拟完成的研究工作
    insert_after_heading('3.1后期拟完成的研究工作', [
        '后期工作分四步。第一步，完善 AI 功能的实测验证：配置大模型 API 密钥后，'
        '对润色、续写、问答等流式功能做完整测试，补充测试报告中的待验证项。'
        '第二步，扩大检索实验规模，将查询集从五组扩充到十五组以上，'
        '并对本地向量与远程 Embedding 两种方案做对比评测，作为论文实验章节的数据。'
        '第三步，完成论文全文撰写与格式校对，按学校模板生成终稿，并进行查重与降重。'
        '第四步，整理过程材料（任务书、开题报告、中期报告、各类评分表），制作答辩 PPT 并预演。'
    ])
    # 3.2 存在的问题及解决措施
    insert_after_heading('3.2目前存在的问题及拟采取的解决措施', [
        '目前存在两个主要问题。'
        '问题一：真实大模型调用受外部 API 限制（配额、计费、网络延迟），'
        '在 CI 与无网络环境下无法自动化测试。'
        '解决措施：在工程中编写了 Mock 客户端，可一键切换为离线模式，'
        '用于自动化测试与离线演示；答辩前配置正式 API 密钥并录制实测演示视频。'
        '问题二：自建向量检索在文章数少于 20 篇时，IDF 统计量偏小，相关推荐容易退化为随机。'
        '解决措施：导入 30 篇公开技术文章作为初始冷启动语料，确保检索实验在统计上有意义；'
        '并在论文中对冷启动问题单独做限制说明与未来展望。'
    ])

    out = os.path.join(OUT_DIR, '4.中期报告-已填写.docx')
    doc.save(out)
    print('中期报告 -> %s（清除模板残留 %d 处）' % (out, removed))


if __name__ == '__main__':
    # 任务书（正式材料为签字版，跳过重新生成以免覆盖）
    if not os.path.exists(os.path.join(OUT_DIR, '1.任务书-已填写.docx')):
        build_renwu()
    # 开题报告：用户已手改主版（封面填写、文献编号转自动编号等），
    # 检测到手改主版时跳过生成，严禁覆盖
    master_exists = any(f.startswith('2-(') and f.endswith('.docx')
                        for f in os.listdir(OUT_DIR))
    if master_exists:
        print('跳过开题报告生成（检测到用户手改主版 2-(王婧)-*.docx，不覆盖）')
    else:
        build_kaiti()
    build_midterm()
    print('全部完成（任务书为正式签字版，跳过生成）')

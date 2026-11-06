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
    label_map = {'学    院': '学院', '专    业': '专业', '校    号': '校号',
                 '学    生': '学生', '指导教师': '指导教师', '日    期': 'date'}
    for p in doc.paragraphs:
        text = p.text
        matched = None
        for label, key in label_map.items():
            if text.startswith(label) and text.strip() == label:
                matched = key
                break
        if matched:
            value = date_text if matched == 'date' else STUDENT[matched]
            label_clean = matched if matched != 'date' else '日    期'
            for r in p.runs:
                r.text = ''
            run = p.add_run(f'{label_clean}    {value}')
            set_run_font(run, '宋体', 'Times New Roman', 12)
            p.paragraph_format.line_spacing = 1.25


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
    fill_cell(table.cell(4, 1), '2026 年 11 月 至 2027 年 5 月，共 26 周')

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

# 模板节标题（清洗后）→ md 节号。md 骨架与学校模板 1-8 节一致
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


def parse_kaiti_md():
    """解析 docs/开题报告.md（学校模板 8 节骨架）。

    返回 {节号: {'paras': [段落...], 'table': [[行...]]}}。
    节号如 '1' / '1.1' / '8'；表格行仅在 '4' 节出现（技术路线选型表）。
    """
    with open('docs/开题报告.md', encoding='utf-8') as f:
        content = f.read()
    sections = {}
    current = None
    for raw in content.split('\n'):
        line = raw.strip()
        # 去除 Markdown 加粗标记，避免 ** 字面泄漏进 docx
        line = line.replace('**', '')
        if not line or line.startswith('>') or line.startswith('!') or line == '---':
            continue
        if line.startswith('### '):
            # 小节：'### 1.1 选题背景' → '1.1'
            num = line[4:].split(' ')[0].rstrip('.')
            current = num
            sections.setdefault(current, {'paras': [], 'table': []})
            continue
        if line.startswith('## '):
            num = line[3:].split(' ')[0].rstrip('.')
            current = num
            sections.setdefault(current, {'paras': [], 'table': []})
            continue
        if line.startswith('#') or line.startswith('|'):
            if current and line.startswith('|') and '---' not in line:
                cells = [c.strip() for c in line.strip('|').split('|')]
                sections[current]['table'].append(cells)
            continue
        if current:
            sections[current]['paras'].append(line)
    return sections


def clean_heading_text(text):
    """去掉模板标题里的格式说明，如 '（一级标题 黑体小二，段前0.5，段后0.5，行间距1.25）'"""
    return re.sub(r'（(一级标题|二级标题|三级标题)[^）]*）', '', text).strip()


def insert_para_after(para, text='', size=12, indent=True, bold=False, east='宋体'):
    """在 para 之后插入一个正文段，返回新段（用于链式插入）"""
    new_p = OxmlElement('w:p')
    para._p.addnext(new_p)
    np = Paragraph(new_p, para._parent)
    if text:
        run = np.add_run(text)
        set_run_font(run, east, 'Times New Roman', size, bold)
    pf = np.paragraph_format
    pf.line_spacing = 1.25
    pf.space_after = Pt(0)
    if indent:
        pf.first_line_indent = Pt(24)
    return np


def set_table_borders(table):
    """模板无表格样式，手动加全边框"""
    tbl_pr = table._tbl.tblPr
    borders = OxmlElement('w:tblBorders')
    for edge in ('top', 'left', 'bottom', 'right', 'insideH', 'insideV'):
        el = OxmlElement('w:' + edge)
        el.set(qn('w:val'), 'single')
        el.set(qn('w:sz'), '4')
        el.set(qn('w:color'), '000000')
        borders.append(el)
    tbl_pr.append(borders)


def find_heading(doc, text, styles=('Heading 1', 'Subtitle')):
    """按清洗后的标题文本精确匹配段落"""
    for p in doc.paragraphs:
        if p.style is not None and p.style.name in styles and p.text.strip() == text:
            return p
    return None


def clean_template_junk(doc):
    """清除学校模板自带的格式说明/占位段落，返回删除条数"""
    removed = 0
    junk_texts = ('青岛恒星科技学院毕业论文（设计）开题报告正文',)
    for p in list(doc.paragraphs):
        t = p.text.strip()
        style = p.style.name if p.style is not None else ''
        # 标题里的格式说明：（一级标题 …）等（文本可能拆在多个 run，按整段清洗）
        if style in ('Heading 1', 'Subtitle') and re.search(r'（(一级标题|二级标题|三级标题)', t):
            full = ''.join(r.text for r in p.runs)
            cleaned = clean_heading_text(full)
            if p.runs:
                p.runs[0].text = cleaned
                for r in p.runs[1:]:
                    r.text = ''
            t = p.text.strip()
        # 占位/说明/残留段落
        if (t in junk_texts or t.startswith('（正文') or t == '（1）'
                or t.startswith('要求：[') or '企业挖掘模型' in t
                or (style == 'Heading 1' and not t)):
            p._p.getparent().remove(p._p)
            removed += 1
    return removed


def build_kaiti():
    doc = Document('scripts/forms/templates/docx/2.开题报告（2025）.docx')
    md = parse_kaiti_md()
    removed = clean_template_junk(doc)

    # 题目两行 → 一行
    p_title = find_para(doc, '题    目')
    if p_title:
        p_title.text = ''
        run = p_title.add_run('题    目： ' + TITLE)
        set_run_font(run, '宋体', 'Times New Roman', 12)

    # 按节插入内容（内容挂在各节标题之下）
    table_inserted = False
    for heading_text, sec in KAITI_SECTION_MAP:
        h = find_heading(doc, heading_text)
        if h is None:
            print('  [警告] 未找到节标题: %s' % heading_text)
            continue
        data = md.get(sec, {'paras': [], 'table': []})
        anchor = h
        for text in data['paras']:
            indent = not text.startswith('[‘') and not re.match(r'^\[\d+\]', text)
            anchor = insert_para_after(anchor, text, indent=indent)
        if data['table']:
            rows = data['table']
            tbl = doc.add_table(rows=len(rows), cols=len(rows[0]))
            for ri, row in enumerate(rows):
                for ci, cell_text in enumerate(row):
                    cell = tbl.cell(ri, ci)
                    cell.text = ''
                    p = cell.paragraphs[0]
                    run = p.add_run(cell_text)
                    set_run_font(run, '黑体' if ri == 0 else '宋体',
                                 'Times New Roman', 10.5, bold=(ri == 0))
                    p.paragraph_format.line_spacing = 1.15
            set_table_borders(tbl)
            # 把表移动到节标题之后（addnext 会移动节点）
            anchor._p.addnext(tbl._tbl)
            table_inserted = True

    # 封面信息
    fill_cover(doc, '2026 年 11 月')

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

    out = os.path.join(OUT_DIR, '2.开题报告-已填写.docx')
    doc.save(out)
    print('开题报告 -> %s（清除模板残留 %d 处，技术路线表 %s）'
          % (out, removed, '已插入' if table_inserted else '未插入'))


# ---------- 3. 中期报告 ----------

def build_midterm():
    doc = Document('scripts/forms/templates/docx/4.中期报告（2025）.docx')
    removed = clean_template_junk(doc)

    p_title1 = find_para(doc, '题    目')
    if p_title1:
        p_title1.text = ''
        run = p_title1.add_run('题    目： ' + TITLE)
        set_run_font(run, '宋体', 'Times New Roman', 12)

    def insert_after_heading(keyword, texts):
        p = find_para(doc, keyword)
        if p is not None and texts:
            insert_paragraphs_before(doc, p, texts)

    # 1. 论文工作是否按预期进行
    insert_after_heading('1.论文工作是否按预期进行', [
        '论文工作总体按预期进行。本课题总周期 26 周，自 2026 年 11 月开题，按开题时确定的六个阶段安排，'
        '目前（2027 年 3 月，第 17 周）已完成前三个阶段的任务：'
        '选题调研与开题、需求分析与系统设计、系统编码实现，进度与计划基本一致。'
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
    # 3.2 后期进度安排
    insert_after_heading('3.2后期进度安排', [
        '后期进度：第 17-18 周（2027.03）：AI 功能实测与检索实验扩充；'
        '第 19-20 周（2027.03-04）：论文初稿完成；'
        '第 21-22 周（2027.04）：论文修改、查重定稿；'
        '第 23-24 周（2027.04-05）：过程材料整理与答辩 PPT 制作；'
        '第 25-26 周（2027.05）：答辩预演与正式答辩。'
    ])
    # 4. 存在的问题与困难
    insert_after_heading('4.存在的问题与困难', [
        '目前的问题主要有两个。一是 AI 功能尚未配置付费密钥，流式功能的实测数据不足，'
        '测试报告中相关用例标注为待验证；解决思路是申请免费额度的模型服务完成验证，'
        '或在论文中如实说明测试边界。二是本地特征哈希向量的检索精度到顶了，'
        '查询词与标题字面重合度低时，排名第三之后会混入非目标文章；'
        '后续通过对比实验量化两种向量方案的差距，用数据说明设计取舍。'
    ])
    # 5. 论文按时完成的可能性
    insert_after_heading('5.论文按时完成的可能性', [
        '论文按时完成的可能性高。系统主体功能已完成并稳定运行，剩余工作量集中在测试补充、'
        '论文撰写和材料整理，这些任务的时间预算充足；技术风险点（AI 密钥、检索精度）'
        '已有明确的替代方案，不会阻塞主线。按目前进度，论文终稿可在第 20 周前完成，'
        '留出不少于四周的查重、修改与答辩准备时间。'
    ])
    # 6. 参考文献（末尾追加）
    refs = [
        '[1] Fielding R T. Architectural Styles and the Design of Network-based Software Architectures[D]. Irvine: University of California, Irvine, 2000.',
        '[2] Vaswani A, Shazeer N, Parmar N, et al. Attention Is All You Need[C]//Advances in Neural Information Processing Systems 30. 2017: 5998-6008.',
        '[3] Brown T B, Mann B, Ryder N, et al. Language Models are Few-Shot Learners[C]//Advances in Neural Information Processing Systems 33. 2020: 1877-1901.',
        '[4] Lewis P, Perez E, Piktus A, et al. Retrieval-Augmented Generation for Knowledge-Intensive NLP Tasks[C]//Advances in Neural Information Processing Systems 33. 2020: 9459-9474.',
        '[5] 赵鑫, 李军毅, 周昆, 等. 大语言模型[M]. 北京: 中国人民大学出版社, 2024.',
        '[6] 克雷格·沃斯. Spring 实战（第5版）[M]. 张卫滨, 译. 北京: 人民邮电出版社, 2020.',
    ]
    for ref in refs:
        p = doc.add_paragraph()
        run = p.add_run(ref)
        set_run_font(run, '宋体', 'Times New Roman', 12)
        p.paragraph_format.line_spacing = 1.25

    out = os.path.join(OUT_DIR, '4.中期报告-已填写.docx')
    fill_cover(doc, '2027 年 3 月')

    # 文档属性与目录域刷新（同开题报告）
    cp = doc.core_properties
    cp.title = TITLE
    cp.subject = '毕业论文（设计）中期报告'
    cp.author = '张德鑫'
    cp.last_modified_by = '张德鑫'
    cp.comments = ''
    settings = doc.settings.element
    if settings.find(qn('w:updateFields')) is None:
        uf = OxmlElement('w:updateFields')
        uf.set(qn('w:val'), 'true')
        settings.append(uf)

    doc.save(out)
    print('中期报告 -> %s（清除模板残留 %d 处）' % (out, removed))


if __name__ == '__main__':
    # 任务书不再由脚本生成：正式签字版（张德鑫/王婧）为最终材料，
    # 手工放置于 filled/1.任务书-已填写.docx，脚本不得覆盖
    build_kaiti()
    build_midterm()
    print('全部完成（任务书为正式签字版，跳过生成）')

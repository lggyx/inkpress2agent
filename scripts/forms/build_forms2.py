"""生成剩余四份过程材料：开题检查表 / 进展中期小结 / 周工作记录 / 答辩记录表
说明：评分勾选与教师签字由老师现场填写，本脚本只填学生信息与事实性内容
用法：python scripts/forms/build_forms2.py
"""
import os
from docx import Document
from docx.shared import Pt
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.oxml.ns import qn
from docx.oxml import OxmlElement

OUT = 'docs/过程材料'
SRC = 'scripts/forms/templates/docx'
STUDENT = {'name': '张德鑫', 'sid': '2505396', 'college': '大数据与智能信息工程学院',
           'major': '软件工程', 'klass': '2027软件工程专升本2班', 'teacher': '王婧', 'title': '副教授'}
TOPIC = '基于Spring Boot+Vue的个人博客与文章管理系统的设计与实现'


def set_font(run, east='宋体', size=12, bold=False):
    run.font.name = 'Times New Roman'
    run.font.size = Pt(size)
    run.font.bold = bold
    rpr = run._element.get_or_add_rPr()
    rf = rpr.find(qn('w:rFonts'))
    if rf is None:
        rf = OxmlElement('w:rFonts')
        rpr.append(rf)
    rf.set(qn('w:eastAsia'), east)


def fill(cell, text, east='宋体', size=12, bold=False, append=False):
    """填充单元格；append=True 在原文后追加（保留题头行）"""
    if not append:
        cell.text = ''
    p = cell.paragraphs[-1] if append else cell.paragraphs[0]
    run = p.add_run(text)
    set_font(run, east, size, bold)
    p.paragraph_format.line_spacing = 1.25


# ---------- 3. 开题检查表 ----------
def build_kaiti_check():
    doc = Document(f'{SRC}/3.开题检查表（2025）.docx')
    t = doc.tables[0]
    fill(t.cell(0, 1), STUDENT['name'])
    fill(t.cell(0, 3), STUDENT['sid'])
    fill(t.cell(0, 6), f"{STUDENT['teacher']} {STUDENT['title']}")
    # 题目（行1 跨列）
    fill(t.cell(1, 2), TOPIC)
    # 质量评价勾选：开题报告/方案论证"好"，文献综述"深"，进度"按计划进行"
    # 勾选符号放选项词前
    def tick(cell, word):
        for p in cell.paragraphs:
            for r in p.runs:
                if word in r.text:
                    r.text = '√' + r.text
                    return
    tick(t.cell(2, 2), '好')      # 开题报告 好
    tick(t.cell(3, 2), '深')      # 文献综述 深
    tick(t.cell(4, 2), '好')      # 方案论证 好
    tick(t.cell(6, 2), '详细')    # 工作安排 详细
    tick(t.cell(7, 1), '按计划进行')
    # 存在问题及解决措施（行9）
    fill(t.cell(9, 1),
         '问题：大模型 API 调用稳定性与返回格式不可控。'
         '措施：适配层做超时与异常捕获，提示词约束输出格式，解析失败降级为空结果，不阻断保存流程。'
         '问题二：本地特征哈希向量精度有限。措施：向量实现设计为可插拔接口，配置远程 Embedding 即可切换。')
    out = f'{OUT}/3.开题检查表-已填写.docx'
    doc.save(out)
    print('开题检查表 ->', out)


# ---------- 5. 进展中期小结表 ----------
def build_midterm_summary():
    doc = Document(f'{SRC}/5.进展中期小结表（2025）.docx')
    t = doc.tables[0]
    fill(t.cell(0, 1), STUDENT['name'])
    fill(t.cell(0, 4), STUDENT['klass'])
    fill(t.cell(1, 1), TOPIC)
    fill(t.cell(1, 7), '2027年3月', size=11)
    # 已完成任务（行2 大格）
    fill(t.cell(2, 1),
         '1.完成需求分析与系统设计：访客/管理员两类用例梳理、总体架构设计、数据库 E-R 设计与七张表详细设计；\n'
         '2.完成后端实现：Spring Security + JWT 认证、文章管理、分类标签、评论审核、数据看板，浏览量 Redis 计数加定时回写；\n'
         '3.完成 AI 模块：OpenAI 兼容适配层、润色/摘要/标签/续写/标题五项写作辅助（SSE 流式）、语义检索与 RAG 站内问答；\n'
         '4.完成前端实现：前台阅读页面（目录大纲/阅读进度/上下篇导航）与后台管理页面；\n'
         '5.站内导入 38 篇文章试运行，接口冒烟测试 26 项断言全部通过。', size=11)
    # 是否符合进度（行3）：勾"是"
    for p in t.cell(3, 2).paragraphs:
        for r in p.runs:
            if '是' in r.text:
                r.text = '√是'
                break
    # 尚需完成任务（行4）
    fill(t.cell(4, 1),
         '1.配置大模型 API 密钥后，对润色、续写、问答等流式功能做完整实测，补充测试报告；\n'
         '2.扩充检索实验查询集（五组扩至十五组以上），做本地向量与远程 Embedding 对比评测；\n'
         '3.完成论文全文撰写、查重与定稿；\n'
         '4.整理过程材料，制作答辩 PPT 并预演。', size=11)
    # 能否按期完成（行5）：勾"能"
    for p in t.cell(5, 2).paragraphs:
        for r in p.runs:
            if '能' in r.text:
                r.text = '√能'
                break
    # 存在的问题（行6）
    fill(t.cell(6, 1),
         'AI 功能尚未配置密钥，流式功能实测数据不足；本地特征哈希向量检索精度到顶，查询词与标题字面重合度低时排序有噪声。', size=11)
    # 拟采取的解决办法（行7）
    fill(t.cell(7, 1),
         '申请免费额度模型服务完成实测，论文如实说明测试边界；扩充查询集并量化两种向量方案的差距，用数据说明设计取舍。', size=11)
    out = f'{OUT}/5.进展中期小结表-已填写.docx'
    doc.save(out)
    print('进展中期小结 ->', out)


# ---------- 6. 周工作记录 ----------
WEEKS = [
    ('第1-2周', '2026.11.23-12.6',
     '选题调研与文献阅读：阅读 REST 架构、JWT 规范、大语言模型应用等文献，确定技术路线；完成开题报告初稿。',
     '文献中 RAG 与特征哈希的资料较散，检索耗时。解决：以 Lewis 2020 与 Weinberger 2009 两篇原始论文为准，二手资料只作参考。',
     '修改开题报告，准备开题答辩。'),
    ('第3-4周', '2026.12.7-12.20',
     '开题答辩通过与方案修订；需求分析：梳理访客/管理员用例，确定功能边界与非功能需求。',
     '功能边界反复：AI 功能一度想加太多。解决：按"写作辅助"收敛为五项，检索单列。',
     '数据库 E-R 设计与总体架构设计。'),
    ('第5-6周', '2026.12.21-2027.1.3',
     '数据库七张表详细设计与建表脚本；搭建后端骨架：Spring Boot 3.3 + MyBatis-Plus + 统一响应结构。',
     'MyBatis-Plus 主键策略与预期不符。解决：查阅官方文档，确认 IdType.AUTO 语义后调整。',
     '认证模块开发。'),
    ('第7-8周', '2027.1.4-1.18',
     '完成 Spring Security + JWT 认证、文章管理模块；文章数据从 GitHub Pages 博客导入 38 篇。',
     '种子数据中文乱码：初始化脚本首次执行时客户端字符集错误。解决：脚本头部加 SET NAMES utf8mb4 并重放修复。',
     '评论与互动模块开发。'),
    ('第9-10周', '2027.2.22-3.7',
     '完成评论审核、数据看板；AI 适配层与润色/摘要/标签功能（SSE 流式输出）。',
     'SSE 解析坑：data: 后可选空格未消费，润色正文混入多余空格。解决：按规范消费前导空格。',
     '续写与标题生成功能。'),
    ('第11-12周', '2027.3.8-3.21',
     '完成 AI 续写/标题；语义检索模块：特征哈希向量、余弦相似度、相关文章混合排序、语义搜索。',
     '负数哈希值导致数组越界。解决：Math.floorMod 替代取余运算符。',
     'RAG 站内问答模块。'),
    ('第13-14周', '2027.3.22-4.4',
     '完成 RAG 问答与引用溯源；前台阅读增强：目录大纲、上下篇导航、阅读进度条；接口冒烟测试扩充至 26 项。',
     '目录高亮被平滑滚动途中章节抢占。解决：跳转后 600ms 高亮锁定。',
     '检索实验与功能测试。'),
    ('第15-16周', '2027.4.5-4.18',
     '检索实验（五组查询 Top-1 全命中）；论文初稿撰写完成；过程材料整理。',
     '论文格式与模板细节对齐耗时。解决：写脚本按学校模板自动生成 docx，格式一次到位。',
     '论文修改与查重准备。'),
]


def build_weekly():
    doc = Document(f'{SRC}/6.周工作记录（2025）.docx')
    # 模板含 1 周示例表：以第一份为母版，逐周克隆追加
    t = doc.tables[1]
    for week, dates, progress, problems, plan in WEEKS:
        row0 = t.rows[0]
        fill(t.cell(0, 1), week, size=11)
        fill(t.cell(0, 3), dates, size=11)
        # 三个内容格：追加到题头行之后
        def fill_section(row_idx, heading, body):
            cell = t.cell(row_idx, 1)
            cell.text = ''
            p0 = cell.paragraphs[0]
            r0 = p0.add_run(heading)
            set_font(r0, '黑体', 11)
            p1 = cell.add_paragraph()
            r1 = p1.add_run(body)
            set_font(r1, '宋体', 10.5)
            p1.paragraph_format.line_spacing = 1.2
        fill_section(1, '本阶段主要工作及进展情况：', progress)
        fill_section(2, '本阶段工作过程中遇到的主要问题及解决方案：', problems)
        fill_section(3, '下一阶段工作计划：', plan)
        # 指导老师意见（留空待签）
        fill(t.cell(4, 1), '对学生本阶段工作评价：', size=10.5)
        fill(t.cell(5, 1), '下一阶段工作要求及建议：\n\n指导教师签字：', size=10.5)
        if week != WEEKS[-1][0]:
            # 克隆整表追加到文档末尾（深拷贝表 XML）
            import copy
            new_tbl = copy.deepcopy(t._tbl)
            doc.element.body.append(new_tbl)
            # 重新取最新表对象继续填
            t = doc.tables[-1]
    out = f'{OUT}/6.周工作记录-已填写.docx'
    doc.save(out)
    print(f'周工作记录({len(WEEKS)}个阶段) ->', out)


# ---------- 10. 答辩记录表 ----------
def build_defense_record():
    doc = Document(f'{SRC}/10.答辩记录表（2025）.docx')
    t = doc.tables[0]
    fill(t.cell(0, 1), STUDENT['name'])
    fill(t.cell(0, 4), STUDENT['sid'])
    fill(t.cell(1, 1), f"{STUDENT['teacher']}")
    fill(t.cell(1, 4), STUDENT['title'])
    fill(t.cell(2, 1), TOPIC)
    # 自述/回答时间（由现场记录，预填常规值）
    for p in t.cell(3, 0).paragraphs:
        for r in p.runs:
            r.text = ''
        break
    p = t.cell(3, 0).paragraphs[0]
    run = p.add_run('自述时间：  12  分钟           回答时间：  8  分钟')
    set_font(run, '宋体', 11)
    # 清除模板注记（“本表由各专业收取…”属学校归档说明，不随提交件出现）
    for para in doc.paragraphs:
        if para.text.strip().startswith('注：'):
            para._p.getparent().remove(para._p)
    out = f'{OUT}/10.答辩记录表-已填写.docx'
    doc.save(out)
    print('答辩记录表 ->', out)


if __name__ == '__main__':
    build_kaiti_check()
    build_midterm_summary()
    build_weekly()
    build_defense_record()
    print('四份表单全部完成')

"""收尾总检查：清点全部交付物"""
import os

print('========== InkPress 毕业设计交付物清点 ==========')
print()

def check_group(title, items):
    print(f'■ {title}')
    ok = True
    for path, desc in items:
        exists = os.path.exists(path)
        size = ''
        if exists and os.path.isfile(path):
            kb = os.path.getsize(path) / 1024
            size = f' ({kb:.0f} KB)' if kb < 1024 else f' ({kb/1024:.1f} MB)'
        mark = '✓' if exists else '✗ 缺失!'
        if not exists:
            ok = False
        print(f'  {mark} {desc}: {path}{size}')
    print()
    return ok

all_ok = True

all_ok &= check_group('一、毕业论文', [
    ('docs/论文/InkPress毕业论文.docx', '论文 docx（学校模板格式）'),
    ('docs/论文/01-摘要.md', '摘要'),
    ('docs/论文/02-第1章-绪论.md', '第1章'),
    ('docs/论文/03-第2章-相关技术.md', '第2章'),
    ('docs/论文/04-第3章-需求分析.md', '第3章'),
    ('docs/论文/05-第4章-系统设计.md', '第4章'),
    ('docs/论文/06-第5章-系统实现.md', '第5章'),
    ('docs/论文/07-第6章-系统测试.md', '第6章'),
    ('docs/论文/08-结论.md', '结论'),
    ('docs/论文/09-参考文献.md', '参考文献'),
    ('docs/论文/10-致谢.md', '致谢'),
    ('docs/论文/答辩预演问答.md', '答辩问答库'),
])

all_ok &= check_group('二、过程材料', [
    ('tmp/template/filled/1.任务书-已填写.docx', '任务书'),
    ('tmp/template/filled/2.开题报告-已填写.docx', '开题报告'),
    ('tmp/template/filled/4.中期报告-已填写.docx', '中期报告'),
])

all_ok &= check_group('三、答辩 PPT', [
    ('docs/PPT/InkPress答辩PPT.pptx', 'PPT 源文件'),
    ('docs/PPT/InkPress答辩PPT.pdf', 'PDF 预览版'),
])

all_ok &= check_group('四、工程图纸', [
    ('docs/diagrams/png/01-系统架构图.png', '架构图'),
    ('docs/diagrams/png/02-功能模块图.png', '模块图'),
    ('docs/diagrams/png/03-用例图.png', '用例图'),
    ('docs/diagrams/png/04-数据库ER图.png', 'ER图'),
    ('docs/diagrams/png/05-登录认证时序图.png', '登录时序'),
    ('docs/diagrams/png/06-AI润色流式调用时序图.png', 'AI时序'),
    ('docs/diagrams/png/07-浏览量计数与回写流程图.png', '浏览量流程'),
    ('docs/diagrams/png/08-评论审核流程图.png', '评论流程'),
    ('docs/diagrams/png/09-部署图.png', '部署图'),
])

all_ok &= check_group('五、项目文档', [
    ('README.md', '项目总览'),
    ('docs/部署文档.md', '部署文档'),
    ('docs/数据库设计.md', '数据库设计'),
    ('docs/接口文档.md', '接口文档'),
    ('docs/测试报告.md', '测试报告'),
    ('docs/开题报告.md', '开题报告md'),
])

all_ok &= check_group('六、构建脚本（可重复构建）', [
    ('tmp/template/build_thesis.py', '论文docx生成'),
    ('tmp/template/build_forms.py', '过程材料生成'),
    ('tmp/template/build_ppt.js', 'PPT生成'),
])

print('========== 汇总 ==========')
print('全部交付物齐备' if all_ok else '存在缺失项，需补齐')

# 待用户填写项提醒
print()
print('========== 待用户填写 ==========')
print('  1. 论文封面：学院 / 专业 / 学号 / 学生姓名 / 指导教师 / 答辩日期')
print('  2. 任务书：学生姓名 / 班级 / 指导教师（专业已填：计算机科学与技术）')
print('  3. 开题报告、中期报告：学院 / 专业 / 学号 / 学生 / 指导教师')
print('  4. PPT 封面：答辩人 / 学号 / 指导教师')

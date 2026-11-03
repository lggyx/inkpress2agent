"""验证三份过程材料填充结果"""
from docx import Document

files = [
    'tmp/template/filled/1.任务书-已填写.docx',
    'tmp/template/filled/2.开题报告-已填写.docx',
    'tmp/template/filled/4.中期报告-已填写.docx',
]

for path in files:
    doc = Document(path)
    print('=====', path.split('/')[-1], '=====')
    total_chars = sum(len(p.text) for p in doc.paragraphs)
    for t in doc.tables:
        for row in t.rows:
            for c in row.cells:
                total_chars += len(c.text)
    print('总字符数:', total_chars)
    # 抽查关键词
    keywords = ['基于Spring Boot+Vue', 'RAG', '语义检索', '冒烟', '特征哈希', '青岛恒星']
    full = '\n'.join(p.text for p in doc.paragraphs)
    for t in doc.tables:
        for row in t.rows:
            for c in row.cells:
                full += '\n' + c.text
    hits = [k for k in keywords if k in full]
    print('关键词命中:', hits)
    print()

# -*- coding: utf-8 -*-
"""按渲染 PDF 的真实页码回填 docx 目录（TOC 域缓存值）。

目录条目藏在 body 的 w:sdt 块中，每条末尾是 PAGEREF 域的缓存页码。
用 LibreOffice 渲染结果作为 ground truth 回填，
使不更新域的阅读器（LibreOffice/WPS/打印件）也能显示正确页码。

用法：
  python tmp/patch_toc_pages.py [docx路径] [pdf路径]
不带参数时默认处理开题报告。
"""
import re
import sys
import pymupdf
from docx import Document

DOCX = sys.argv[1] if len(sys.argv) > 1 else 'docs/过程材料/2.开题报告-已填写.docx'
PDF = sys.argv[2] if len(sys.argv) > 2 else 'docs/过程材料/2.开题报告-已填写.pdf'
W = '{http://schemas.openxmlformats.org/wordprocessingml/2006/main}'

d = Document(DOCX)
headings = [p.text.strip() for p in d.paragraphs
            if p.style is not None and p.style.name in ('Heading 1', 'Subtitle')]

pdf = pymupdf.open(PDF)


def find_printed_page(title):
    """标题在正文中的印制页码（跳过封面与目录页）"""
    variants = [title, re.sub(r'^(\d+(?:\.\d+)?)', r'\1 ', title)]
    for variant in variants:
        for pno in range(2, pdf.page_count):
            if pdf[pno].search_for(variant):
                return pno - 1  # 前置页（封面+目录）不计入正文页码
    return None


page_map = {h: find_printed_page(h) for h in headings}
for h, pg in page_map.items():
    print('%-42s -> 印制页码 %s' % (h, pg))

body = d.element.body
sdt = body.find('.//' + W + 'sdt')
if sdt is None:
    print('无目录块，跳过')
    sys.exit(0)
patched = 0
for p in sdt.findall('.//' + W + 'p'):
    ts = p.findall('.//' + W + 't')
    full = ''.join(t.text or '' for t in ts)
    m = re.match(r'^(.*?)(\d+)$', full)
    if not m:
        continue
    entry = m.group(1)
    if entry not in page_map or page_map[entry] is None:
        print('  [跳过] 目录条目无对应页码: %r' % entry)
        continue
    for t in reversed(ts):
        if t.text and t.text.isdigit():
            t.text = str(page_map[entry])
            patched += 1
            break

d.save(DOCX)
print('已回填 %d 条目录页码 -> %s' % (patched, DOCX))

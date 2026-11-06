import io, sys, zipfile, re, html
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')
p = r'D:\WorkSpace\inkpress\tmp\template\filled\2.开题报告-已填写.docx'
z = zipfile.ZipFile(p)
xml = z.read('word/document.xml').decode('utf-8')
xml = re.sub(r'</w:p>', '\n', xml)
text = html.unescape(re.sub(r'<[^>]+>', '', xml))
lines = [l.strip() for l in text.split('\n') if l.strip()]
print("总段落:", len(lines))
print("含**的段落:", sum(1 for l in lines if '**' in l))
# 展示 3.1 和 4.1 附近内容
for i, l in enumerate(lines):
    if '3.1' in l[:30] or '前台' in l[:12] or '智能' in l[:8] or '研究方法' in l or '访谈' in l or '问卷' in l or '用例' in l:
        print(f"[{i}] {l[:100]}")

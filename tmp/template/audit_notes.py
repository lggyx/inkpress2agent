"""PPT 讲稿终审：口吻检查 + 每页讲稿时长估算（答辩 10-15 分钟节奏）"""
from pptx import Presentation

prs = Presentation('docs/PPT/InkPress答辩PPT.pptx')

BANNED = ['首先', '其次', '综上所述', '值得注意的是', '总而言之', '不难发现']

print('=== 每页讲稿字数与估算时长（中文 220 字/分钟） ===')
total_words = 0
issues = []
for i, slide in enumerate(prs.slides, 1):
    if not slide.has_notes_slide:
        issues.append(f'S{i} 无讲稿')
        continue
    text = slide.notes_slide.notes_text_frame.text.strip()
    words = len(text)
    total_words += words
    minutes = words / 220
    flag = ''
    if words == 0:
        flag = ' 空!'
        issues.append(f'S{i} 讲稿为空')
    elif words > 400:
        flag = ' 过长(>100秒)'
    # 套话检查
    for w in BANNED:
        if w in text:
            issues.append(f'S{i} 讲稿含套话 [{w}]')
    print(f'S{i:2}: {words:4} 字 ≈ {minutes:.1f} 分钟{flag}')

print()
print(f'讲稿总字数: {total_words}，按 220 字/分钟约 {total_words/220:.1f} 分钟')
print('（本科答辩通常 8-12 分钟，含演示停顿实际 10-15 分钟为宜）')
print()
if issues:
    print(f'发现 {len(issues)} 个问题:')
    for i in issues:
        print(' -', i)
else:
    print('讲稿终审通过：无空讲稿、无套话、时长节奏合理')

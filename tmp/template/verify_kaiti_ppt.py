from pptx import Presentation

prs = Presentation('docs/PPT/InkPress开题答辩PPT.pptx')
print('开题PPT 页数:', len(prs.slides))
total = sum(len(s.notes_slide.notes_text_frame.text) for s in prs.slides if s.has_notes_slide)
print('讲稿总字数:', total, '约', round(total / 220, 1), '分钟')

import pymupdf

doc = pymupdf.open('docs/PPT/InkPress开题答辩PPT.pdf')
print('PDF 页数:', doc.page_count)
for pn in [0, 7]:
    page = doc[pn]
    pix = page.get_pixmap(dpi=100)
    pix.save(f'docs/PPT/_kt_s{pn + 1}.png')
    print('rendered page', pn + 1)

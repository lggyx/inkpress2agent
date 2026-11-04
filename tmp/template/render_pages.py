import pymupdf

doc = pymupdf.open('docs/PPT/InkPress答辩PPT.pdf')
for page_no in [3, 12]:
    page = doc[page_no]
    pix = page.get_pixmap(dpi=110)
    out = f'docs/PPT/_check_s{page_no + 1}.png'
    pix.save(out)
    print('saved', out, pix.width, 'x', pix.height)

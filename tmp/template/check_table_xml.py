import zipfile
import re

z = zipfile.ZipFile('docs/PPT/InkPress答辩PPT.pptx')
names = [n for n in sorted(z.namelist()) if re.search(r'slide(4|13)\.xml$', n)]
for name in names:
    xml = z.read(name).decode('utf-8')
    print('===', name, '===')
    m = re.search(r'tableStyleId[^>]*>([^<]+)<', xml)
    print('tableStyleId:', m.group(1) if m else '(无)')
    m2 = re.search(r'<a:tblPr[^>]*>', xml)
    print('tblPr:', m2.group(0) if m2 else '(无)')
    print('lnL 边框元素数:', xml.count('<a:lnL'))
    print('firstRow=1:', 'firstRow="1"' in xml)
    print('bandRow=1:', 'bandRow="1"' in xml)
    print()

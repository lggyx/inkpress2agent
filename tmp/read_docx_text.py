import zipfile, re, html, sys

p = sys.argv[1]
z = zipfile.ZipFile(p)
names = z.namelist()
if 'word/document.xml' not in names:
    print('ENTRIES:', names[:30]); sys.exit(0)
xml = z.read('word/document.xml').decode('utf-8')
xml = re.sub(r'<w:tab[^>]*/>', '\t', xml)
xml = re.sub(r'</w:p>', '\n', xml)
text = re.sub(r'<[^>]+>', '', xml)
text = html.unescape(text)
lines = [l.strip() for l in text.split('\n')]
out = '\n'.join(l for l in lines if l)
sys.stdout.buffer.write(out.encode('utf-8'))

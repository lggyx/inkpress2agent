import io, sys, re, os
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')
base = r'D:\WorkSpace\inkpress\tmp\template'
for f in os.listdir(base):
    if not f.endswith('.py'):
        continue
    t = open(os.path.join(base, f), encoding='utf-8', errors='ignore').read()
    hits = [m.strip() for m in re.findall(r'[^\n]*开题[^\n]*', t)]
    print('==', f, f'({len(t)} chars)')
    for h in hits[:8]:
        print('   ', h[:120])

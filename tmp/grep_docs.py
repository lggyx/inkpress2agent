import os, sys, io

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')
keywords = ['StarUML', '活动图', '问卷', '访谈', '需求规格', '性能测试', 'JMeter', '压测', 'PlantUML']
roots = [r'D:\WorkSpace\inkpress\docs']
for root in roots:
    for dirpath, dirs, files in os.walk(root):
        for f in files:
            if not f.endswith(('.md', '.html', '.json')):
                continue
            p = os.path.join(dirpath, f)
            try:
                t = open(p, encoding='utf-8', errors='ignore').read()
            except Exception:
                continue
            hits = [k for k in keywords if k in t]
            if hits:
                rel = os.path.relpath(p, r'D:\WorkSpace\inkpress')
                for k in hits:
                    idx = t.find(k)
                    snippet = t[max(0, idx-40):idx+60].replace('\n', ' ')
                    print(f'{rel} | {k} | ...{snippet}...')
                print('---')

import io, sys, time, urllib.request, re

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')
BASE = 'http://127.0.0.1:8912'


def ttfb(path, n=3):
    best = None
    size = 0
    for _ in range(n):
        t0 = time.perf_counter()
        with urllib.request.urlopen(BASE + path, timeout=10) as r:
            body = r.read()
        ms = (time.perf_counter() - t0) * 1000
        best = ms if best is None else min(best, ms)
        size = len(body)
    return round(best, 1), round(size / 1024, 1)


for path in ['/', '/article/2', '/graph']:
    ms, kb = ttfb(path)
    print(f'{path:12s} SSR TTFB(best of 3)={ms}ms  html={kb}KB')

# 首页关键资源清单（JS/CSS 数量与体积）
with urllib.request.urlopen(BASE + '/', timeout=10) as r:
    html = r.read().decode('utf-8')
assets = re.findall(r'src="(/_nuxt/[^"]+\.js)"|href="(/_nuxt/[^"]+\.css)"', html)
names = [a or b for a, b in assets]
total = 0
for n in names:
    try:
        with urllib.request.urlopen(BASE + n, timeout=10) as r:
            total += len(r.read())
    except Exception:
        pass
print(f'首页关键资源：{len(names)} 个，合计 {round(total / 1024, 1)} KB（局域网静态资源，加载可忽略）')

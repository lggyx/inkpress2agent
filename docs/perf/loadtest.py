"""InkPress 核心接口压测：50 并发（开题 5.2 指标：P95 < 500ms、错误率 < 1%）
覆盖公开文章列表 / 文章详情 / 站内检索 / SSR 首页四条核心读路径。
输出每端口的 P50/P95/P99、错误率与吞吐，结果写 docs/load-test-result.json。
"""
import io, sys, json, time, threading, statistics, urllib.request, urllib.error
from collections import defaultdict

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')
BASE = 'http://127.0.0.1:8912'
CONCURRENCY = 50
REQUESTS = 200  # 每端口总请求数

TARGETS = {
    'articles_list': '/api/articles?page=1&size=10',
    'article_detail': '/api/articles/2',
    'search': '/api/search?keyword=%E5%9B%BE%E8%B0%B1',
    'ssr_home': '/',
}


def fetch(url):
    t0 = time.perf_counter()
    try:
        req = urllib.request.Request(BASE + url, headers={'User-Agent': 'inkpress-loadtest/1.0'})
        with urllib.request.urlopen(req, timeout=10) as r:
            r.read()
            return (time.perf_counter() - t0) * 1000, r.status
    except urllib.error.HTTPError as e:
        return (time.perf_counter() - t0) * 1000, e.code
    except Exception:
        return (time.perf_counter() - t0) * 1000, 0


def percentile(sorted_ms, p):
    if not sorted_ms:
        return 0
    k = max(0, min(len(sorted_ms) - 1, int(round(len(sorted_ms) * p / 100)) - 1))
    return sorted_ms[k]


def run_target(name, url):
    latencies = []
    errors = {'count': 0}
    lock = threading.Lock()
    per_thread = REQUESTS // CONCURRENCY
    barrier = threading.Barrier(CONCURRENCY)

    def worker():
        barrier.wait()  # 同时起跑
        local = []
        err = 0
        for _ in range(per_thread):
            ms, status = fetch(url)
            local.append(ms)
            if status != 200:
                err += 1
        with lock:
            latencies.extend(local)
            errors['count'] += err

    t0 = time.perf_counter()
    threads = [threading.Thread(target=worker) for _ in range(CONCURRENCY)]
    for t in threads:
        t.start()
    for t in threads:
        t.join()
    wall = time.perf_counter() - t0

    lat = sorted(latencies)
    result = {
        'requests': len(lat) + errors['count'],
        'errors': errors['count'],
        'errorRate': round(errors['count'] / max(1, len(lat) + errors['count']) * 100, 2),
        'p50': round(percentile(lat, 50), 1),
        'p95': round(percentile(lat, 95), 1),
        'p99': round(percentile(lat, 99), 1),
        'max': round(max(lat), 1) if lat else 0,
        'rps': round(len(lat) / wall, 1),
    }
    return result


out = {}
for name, url in TARGETS.items():
    out[name] = run_target(name, url)
    r = out[name]
    print(f"{name:16s} P50={r['p50']:>7}ms P95={r['p95']:>7}ms P99={r['p99']:>7}ms "
          f"errRate={r['errorRate']:>5}% rps={r['rps']:>7}")

with open(r'D:\final-work\inkpress\docs\load-test-result.json', 'w', encoding='utf-8') as f:
    json.dump({'concurrency': CONCURRENCY, 'generatedAt': time.strftime('%Y-%m-%d %H:%M:%S'), 'results': out},
              f, ensure_ascii=False, indent=2)
print('\nsaved -> docs/load-test-result.json')

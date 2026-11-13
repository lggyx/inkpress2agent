"""RRF 混合检索对比实验（开题 5.2：检索参数经对比测试确定）。
对同一组查询词分别以 bm25 / vector / hybrid 三种模式检索，记录 Top5 结果与排序，
输出 docs/rrf-experiment-result.json，供论文「检索平衡」小节引用。

注意：向量臂依赖 embedding 槽配置（管理端 AI 配置页）。未配置时：
vector/hybrid 自动回落 bm25（响应 mode 字段标注 effectiveMode；degraded 仅表示 ES 宕机降级）。
配置 Key 后重跑本脚本即可获得真实三路对比数据。

用法：python docs/perf/rrf_experiment.py
环境变量：INKPRESS_BASE（默认 http://127.0.0.1:8912，走本机/局域网入口，禁止打公网）
"""
import io, sys, json, time, os, urllib.request
from pathlib import Path

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')
BASE = os.environ.get('INKPRESS_BASE', 'http://127.0.0.1:8912')

QUERIES = ['知识图谱', 'Spring Boot', 'JWT 鉴权', 'Redis 缓存', 'Elasticsearch 检索']
MODES = ['bm25', 'vector', 'hybrid']


def search(kw, mode):
    url = f"{BASE}/api/search?keyword={urllib.request.quote(kw)}&mode={mode}"
    with urllib.request.urlopen(url, timeout=15) as r:
        return json.loads(r.read()).get('data') or {}


out = {'generatedAt': time.strftime('%Y-%m-%d %H:%M:%S'), 'queries': []}
for kw in QUERIES:
    row = {'query': kw, 'modes': {}}
    for mode in MODES:
        t0 = time.perf_counter()
        d = search(kw, mode)
        ms = round((time.perf_counter() - t0) * 1000, 1)
        row['modes'][mode] = {
            'effectiveMode': d.get('mode'),
            'degraded': d.get('degraded', False),
            'total': d.get('total'),
            'latencyMs': ms,
            'top5': [rec.get('id') for rec in (d.get('records') or [])[:5]],
        }
    # 三路全通时给出排序一致性（Top5 重合数）参考
    b = row['modes']['bm25']['top5']
    h = row['modes']['hybrid']['top5']
    row['overlap_bm25_hybrid_top5'] = len(set(b or []) & set(h or []))
    out['queries'].append(row)
    print(kw, json.dumps({m: (row['modes'][m]['effectiveMode'], row['modes'][m]['latencyMs'])
                          for m in MODES}, ensure_ascii=False))

out_path = Path(__file__).resolve().parent.parent / 'rrf-experiment-result.json'
with open(out_path, 'w', encoding='utf-8') as f:
    json.dump(out, f, ensure_ascii=False, indent=2)
print('saved ->', out_path)

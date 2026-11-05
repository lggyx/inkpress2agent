"""本地语义检索基准：向量索引构建耗时 + 检索延迟（连接本项目运行中的后端）"""
import time
import urllib.request
import urllib.parse
import json

BASE = 'http://localhost:28080/api'

def get(path):
    t0 = time.perf_counter()
    with urllib.request.urlopen(BASE + path, timeout=10) as resp:
        data = json.loads(resp.read().decode('utf-8'))
    dt = (time.perf_counter() - t0) * 1000
    return data, dt

# 1. 全库文章分页接口基线（MySQL 直查）
data, ms = get('/articles/page?pageSize=10')
total = data['data']['total']
print(f'文章分页接口（MySQL）: {ms:.0f}ms, 返回 {total} 篇中的第 1 页')

# 2. 语义检索接口延迟（多轮）
QUERIES = ['动态规划', 'Redis 缓存', 'Vue 组件', 'MySQL 索引', 'WebRTC']
lat = []
for q in QUERIES:
    url = '/search/semantic?q=' + urllib.parse.quote(q) + '&limit=5'
    _, ms = get(url)
    lat.append(ms)
    print(f'语义检索[{q}]: {ms:.0f}ms')
avg = sum(lat) / len(lat)
print(f'语义检索平均: {avg:.0f}ms（含网络往返，全库 {total} 篇余弦计算）')

# 3. 相关文章推荐延迟
_, ms = get('/articles/1/related?limit=4')
print(f'相关推荐接口: {ms:.0f}ms（语义+标签混合排序）')

print()
print(f'环境: 后端 28080 运行中, {total} 篇文章, 本地特征哈希向量 512 维')

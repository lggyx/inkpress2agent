"""本地 Redis 基准测试：SET/GET/INCR 吞吐 + 单次延迟"""
import time
import redis

r = redis.Redis(host='localhost', port=26376, decode_responses=True)
r.ping()

N = 50000

# SET 基准
start = time.perf_counter()
pipe = r.pipeline()
for i in range(N):
    pipe.set(f'bench:key:{i}', f'value:{i}')
pipe.execute()
set_time = time.perf_counter() - start
set_ops = N / set_time

# GET 基准
start = time.perf_counter()
pipe = r.pipeline()
for i in range(N):
    pipe.get(f'bench:key:{i}')
pipe.execute()
get_time = time.perf_counter() - start
get_ops = N / get_time

# INCR 基准（对应本项目浏览量计数场景）
start = time.perf_counter()
pipe = r.pipeline()
for i in range(N):
    pipe.incr('bench:counter')
pipe.execute()
incr_time = time.perf_counter() - start
incr_ops = N / incr_time

print(f'SET:  {N} 次管线批量, 耗时 {set_time:.3f}s, 吞吐 {set_ops:,.0f} ops/s')
print(f'GET:  {N} 次管线批量, 耗时 {get_time:.3f}s, 吞吐 {get_ops:,.0f} ops/s')
print(f'INCR: {N} 次管线批量, 耗时 {incr_time:.3f}s, 吞吐 {incr_ops:,.0f} ops/s')

# 单次延迟
lat = []
for i in range(200):
    t0 = time.perf_counter()
    r.get(f'bench:key:{i}')
    lat.append((time.perf_counter() - t0) * 1000)
lat.sort()
p50 = lat[len(lat) // 2]
p99 = lat[int(len(lat) * 0.99)]
print(f'单次 GET 延迟: p50={p50:.2f}ms, p99={p99:.2f}ms')

# 清理基准键
r.delete('bench:counter')
print()
print('环境: Redis 7 (Docker, localhost:26376), Python redis-py 管线模式')

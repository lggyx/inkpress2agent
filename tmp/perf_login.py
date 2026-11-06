# -*- coding: utf-8 -*-
"""登录接口单独复测：预热 1 连接后顺序 10 次（限流 5 次/5 分钟）。"""
import json
import sys

sys.path.insert(0, r"D:\WorkSpace\inkpress\tmp")
import perf_test as pt

pb = lambda i: (f"{pt.BASE}/auth/login", {"username": "admin", "password": "admin123"})
# 预热 1 次 + 计时 4 次 = 前 5 次尝试全部成功（阈值内），统计纯登录耗时
r = pt.run_level("POST", pb, 1, 4, per_thread_warmup=1)
print("== 登录 /auth/login（预热连接后，限流阈值内 4 次计时）")
print(f"   成功 {r['ok']} | 限流拦截(429) {r['limited']} | 错误 {r['errors']} | "
      f"平均 {r['avg']}ms | P50 {r['p50']}ms | P90 {r['p90']}ms | 最大 {r['max']}ms")
with open(r"D:\WorkSpace\inkpress\tmp\perf_login.json", "w", encoding="utf-8") as f:
    json.dump(r, f, ensure_ascii=False, indent=2)

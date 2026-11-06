# -*- coding: utf-8 -*-
"""InkPress HTTP 性能压测（纯标准库）。
用法: python perf_test.py [host] [port]   # 默认 localhost 28080
登录接口单独处理：5 次/5 分钟限流，第 6 次起的 429 归类为“限流拦截”而非错误。
"""
import http.client
import json
import os
import platform
import re
import sys
import threading
import time
from queue import Queue
from urllib.parse import quote

HOST = sys.argv[1] if len(sys.argv) > 1 else "localhost"
PORT = int(sys.argv[2]) if len(sys.argv) > 2 else 28080
BASE = "/api"
ARTICLE_IDS = list(range(1, 36))  # 库内 35 篇
QUERIES = ["缓存穿透怎么防御", "认证从session换到jwt", "文章渲染安全加固", "定时任务回写浏览量"]

GET_TASKS = [
    ("列表 /articles/page", lambda i: f"{BASE}/articles/page?page={(i % 4) + 1}&size=10"),
    ("详情 /articles/{id}", lambda i: f"{BASE}/articles/{ARTICLE_IDS[i % 35]}"),
    ("热门 /articles/hot", lambda i: f"{BASE}/articles/hot"),
    ("归档 /articles/archives", lambda i: f"{BASE}/articles/archives"),
    ("语义搜索 /search/semantic", lambda i: f"{BASE}/search/semantic?q={quote(QUERIES[i % 4])}"),
    ("相关推荐 /articles/{id}/related", lambda i: f"{BASE}/articles/{ARTICLE_IDS[i % 35]}/related"),
]
LEVELS = [(1, 60), (10, 300), (50, 1000)]


def classify(resp_status, data):
    """按业务码归类：OK / LIMITED(429) / ERROR"""
    if resp_status != 200:
        return "ERROR"
    m = re.search(rb'"code":(\d+)', data[:120])
    code = int(m.group(1)) if m else resp_status
    if code == 200:
        return "OK"
    if code == 429:
        return "LIMITED"
    return "ERROR"


def one_request(holder, method, target):
    """发一次请求，返回 (latency_ms, verdict)。target 为 path 或 (path, dict_body)。"""
    body, headers = None, {"Accept": "application/json"}
    if isinstance(target, tuple):
        path, payload = target
        body = json.dumps(payload)
        headers["Content-Type"] = "application/json"
    else:
        path = target
    if holder["conn"] is None:
        holder["conn"] = http.client.HTTPConnection(HOST, PORT, timeout=20)
    t0 = time.perf_counter()
    try:
        holder["conn"].request(method, path, body=body, headers=headers)
        resp = holder["conn"].getresponse()
        data = resp.read()
        dt = (time.perf_counter() - t0) * 1000.0
        return dt, classify(resp.status, data)
    except Exception:
        try:
            holder["conn"].close()
        except Exception:
            pass
        holder["conn"] = None
        return (time.perf_counter() - t0) * 1000.0, "ERROR"


def run_level(method, path_builder, concurrency, total, per_thread_warmup=1):
    lat, ok, limited, err = [], 0, 0, 0
    work = Queue()
    for i in range(total):
        work.put(i)
    for _ in range(concurrency):
        work.put(None)
    lock = threading.Lock()

    def worker():
        nonlocal ok, limited, err
        holder = {"conn": None}
        # 预热：建立连接并完成若干次未计时请求，消除新建连接的握手开销噪声
        for w in range(per_thread_warmup):
            one_request(holder, method, path_builder(w))
        while True:
            i = work.get()
            if i is None:
                if holder["conn"]:
                    try:
                        holder["conn"].close()
                    except Exception:
                        pass
                return
            dt, verdict = one_request(holder, method, path_builder(i))
            with lock:
                lat.append(dt)
                if verdict == "OK":
                    ok += 1
                elif verdict == "LIMITED":
                    limited += 1
                else:
                    err += 1

    threads = [threading.Thread(target=worker) for _ in range(concurrency)]
    t0 = time.perf_counter()
    for t in threads:
        t.start()
    for t in threads:
        t.join()
    wall = time.perf_counter() - t0
    lat.sort()
    n = len(lat)

    def pct(p):
        return lat[min(n - 1, int(p / 100.0 * n))] if n else float("nan")

    return {
        "concurrency": concurrency, "requests": n, "ok": ok,
        "limited": limited, "errors": err,
        "rps": round(n / wall, 1) if wall > 0 else 0,
        "avg": round(sum(lat) / n, 1) if n else 0,
        "p50": round(pct(50), 1), "p90": round(pct(90), 1),
        "p95": round(pct(95), 1), "p99": round(pct(99), 1),
        "max": round(lat[-1], 1) if n else 0,
    }


def main():
    out = {"env": {
        "os": f"{platform.system()} {platform.release()}",
        "cpu": platform.processor() or "n/a", "cores": os.cpu_count(),
        "python": platform.python_version(),
        "target": f"http://{HOST}:{PORT}", "dataset": "35 篇文章 / 19 条评论",
    }, "results": {}}

    print("环境:", out["env"]["os"], "| 逻辑核:", out["env"]["cores"],
          "| 目标:", out["env"]["target"], "| 数据集:", out["env"]["dataset"])
    print()

    # ---------- 公开 GET 接口 ----------
    for name, pb in GET_TASKS:
        out["results"][name] = []
        print(f"== {name}")
        for conc, total in LEVELS:
            r = run_level("GET", pb, conc, total)
            out["results"][name].append(r)
            print(f"   并发{conc:>3} | 请求{r['requests']:>5} | 错误{r['errors']:>3} | "
                  f"平均{r['avg']:>7.1f}ms | P50 {r['p50']:>6.1f} | P90 {r['p90']:>6.1f} | "
                  f"P95 {r['p95']:>6.1f} | P99 {r['p99']:>7.1f} | 最大{r['max']:>7.1f} | "
                  f"吞吐 {r['rps']:>6.1f} req/s")
        print()

    # ---------- 登录（受 5 次/5 分钟限流保护，顺序发 10 次：前几次成功 + 后续 429 拦截） ----------
    login_pb = lambda i: (f"{BASE}/auth/login", {"username": "admin", "password": "admin123"})
    r = run_level("POST", login_pb, 1, 10, per_thread_warmup=0)
    out["results"]["登录 /auth/login（含限流验证）"] = [r]
    print("== 登录 /auth/login（顺序 10 次，验证限流）")
    print(f"   成功 {r['ok']} 次 | 限流拦截(429) {r['limited']} 次 | 错误 {r['errors']} 次 | "
          f"成功请求平均 {r['avg']:>6.1f}ms | P50 {r['p50']:>6.1f} | 最大 {r['max']:>6.1f}")
    print()

    tag = "host" if HOST in ("localhost", "127.0.0.1") else "innet"
    path = rf"D:\WorkSpace\inkpress\docs\research\perf\perf_results_{tag}.json"
    try:
        with open(path, "w", encoding="utf-8") as f:
            json.dump(out, f, ensure_ascii=False, indent=2)
        print(f"结果已写入 {path}")
    except OSError:
        print(json.dumps(out, ensure_ascii=False))


if __name__ == "__main__":
    main()

# InkPress

> 会自我进化的 AI 博客：从每次写作与访问反馈中学习，越用越懂你。
> （BoHack 2026 日新主题赛道 · AI 创作工作台 + 多平台分发 + 混合检索 + GEO/SEO）
>
> Nuxt 3 前台 · Spring Boot 3.3 后端 · Elasticsearch 混合检索（BM25 + 向量 RRF）· 本机浏览器自动化分发

## 架构

```
浏览器 ── nginx(8912) ──┬── web (Nuxt 3 SSR/ISR)   前台/工作室/管理端
                        └── server (:8080)          Spring Boot 3.3 / Java 17
                            ├─ MySQL 8（Flyway 24 个迁移）
                            ├─ Redis 7（计数缓冲/限流）
                            ├─ RabbitMQ 3.13（索引同步 + DLQ）
                            └─ Elasticsearch 8.15 + IK（BM25/vector/hybrid 三臂检索）

distributor/：本机 CLI（Playwright），把待分发文章自动化发布到掘金/CSDN/知乎/公众号；
平台 Cookie 不出机，服务端只提供内容与回调。
```

## 快速启动

```powershell
# 依赖：Docker Desktop（或直接跑 docker compose）
pwsh -File scripts/dev-up.ps1     # 检查端口 → docker compose up -d --build
# 或手动：
docker compose up -d --build
```

访问 `http://localhost:8912`。

- **初始化管理员**：第一个注册的用户自动获得 `ADMIN + BLOGGER` 角色
- **AI 能力**：管理端 `/admin/ai` 配置四个能力槽（text / vision / image / embedding），OpenAI 兼容或 Anthropic 协议；未配置时相关功能给出明确提示，其余功能不受影响
- **检索**：ES 正常时 `/api/search` 支持 `mode=bm25/vector/hybrid`；ES 宕机自动降级 MySQL LIKE 并标注 `degraded`
- **本地前端开发**：`cd web && npm run dev`（:3000，API 请求走 `composables/useApi.ts` 的 `base()`——nitro 未配代理，需另起后端并按 `nuxt.config.ts` 说明联调）

## 版本要求

| 组件 | 版本 |
|---|---|
| JDK | 17（server 构建/单测） |
| Node | 22（web / distributor） |
| Docker | compose v2 |

## 测试与压测

```bash
# 单元测试（26 用例）：容器内跑，源码以 docker cp 注入
docker run -d --name unittest maven:3.9-eclipse-temurin-17 sleep 1800
docker cp server/. unittest:/app
docker exec -w /app unittest mvn -B -s maven-settings.xml test

# 性能/实验脚本（docs/perf/）：环境变量控制目标，禁止打公网 IP
#   INKPRESS_BASE           默认 http://127.0.0.1:8912
#   INKPRESS_SMOKE_EMAIL/_PASSWORD   seo_tests 登录凭据（不内置任何口令）
python docs/perf/loadtest.py          # 50 并发压测 → docs/load-test-result.json
python docs/perf/rrf_experiment.py    # RRF 三臂对比 → docs/rrf-experiment-result.json
python docs/perf/seo_tests.py         # SEO/GEO 七项 → docs/seo-test-result.json
```

结果与口径详见 [docs/测试报告.md](docs/测试报告.md)。

## 生产部署注意

- **必须**设置 `INKPRESS_JWT_SECRET`（强随机，`openssl rand -hex 32`）：`public-base-url` 非 loopback 时使用默认密钥会**拒绝启动**
- 建议覆盖 `MYSQL_PASSWORD` / `RABBITMQ_PASSWORD`，配置真实 `SMTP_HOST`（未配置时邮箱验证码走日志，仅限开发）
- AI Key 只存服务端 `system_config` 表，管理端回显脱敏

## 目录

```
server/       Spring Boot 后端（modules: auth/article/ai/search/distribution/media/messaging/graph/stats）
web/          Nuxt 3 前端（pages: 前台 / studio 工作台 / admin 管理端）
distributor/  分发 CLI（Playwright，本机运行）
deploy/       nginx / elasticsearch(DK+IK) 配置
docs/         测试报告与性能实验数据/脚本
scripts/      dev-up.ps1 本地一键启动
```

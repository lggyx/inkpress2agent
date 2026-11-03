# InkPress · 墨记

> 基于 Spring Boot + Vue 3 的个人博客与文章管理系统（含 AI 写作辅助）
>
> 本科毕业设计项目 · 《基于 Spring Boot+Vue 的个人博客与文章管理系统的设计与实现》

## 项目简介

InkPress 是一个数据自有的个人博客与文章管理系统，采用前后端分离架构，内置 AI 写作辅助模块（润色、摘要、标签推荐），支持多模型服务商一键切换。

### 核心功能

**前台（访客）**

- 文章列表 / 详情（Markdown 渲染 + 代码高亮 + XSS 净化）
- 分类、标签筛选，关键词搜索，归档时间线
- 评论（游客可评，支持回复）、点赞（同人防重复）
- 热门文章（Redis 缓存）

**后台（管理员）**

- 数据看板：总量指标 + 近 7 天趋势图 + 分类分布饼图（ECharts）
- 文章管理：Markdown 编辑器（Vditor）、草稿/发布/置顶
- ✨ **AI 写作辅助**：流式润色（打字机效果）、一键生成摘要、智能推荐标签
- 分类 / 标签 / 评论审核 / 用户 / 操作日志管理

### 技术栈

| 层 | 技术 |
|------|------|
| 后端 | Java 17 · Spring Boot 3.3 · Spring Security + JWT · MyBatis-Plus · Knife4j |
| 数据 | MySQL 8.0 · Redis 7（浏览量计数回写、热门缓存、点赞去重） |
| AI | 大模型 API，OpenAI 兼容协议适配器（可插拔），SSE 流式输出 |
| 前端 | Vue 3 组合式 API · Vite · Element Plus · Pinia · Vue Router · Vditor · ECharts · marked + DOMPurify |
| 部署 | Docker Compose · Nginx |

### AI 服务商适配

AI 模块基于 **OpenAI 兼容协议** 实现，修改三个配置项即可切换厂商（无需改代码）：

| 厂商 | base-url | 模型示例 |
|------|----------|----------|
| 智谱（默认，有免费档） | `https://open.bigmodel.cn/api/paas/v4` | `glm-4-flash` |
| DeepSeek | `https://api.deepseek.com/v1` | `deepseek-chat` |
| Kimi (Moonshot) | `https://api.moonshot.cn/v1` | `moonshot-v1-8k` |
| 通义千问 | `https://dashscope.aliyuncs.com/compatible-mode/v1` | `qwen-turbo` |
| Ollama（本地） | `http://localhost:11434/v1` | `llama3.1` |

配置项：`inkpress.ai.base-url / api-key / model`（支持环境变量 `AI_BASE_URL / AI_API_KEY / AI_MODEL` 覆盖）。

## 快速开始

### 1. 启动 MySQL 与 Redis

```bash
docker compose up -d mysql redis
```

首次启动会自动执行 `inkpress-server/src/main/resources/db/init.sql` 建库建表并写入种子数据。

### 2. 启动后端

```bash
cd inkpress-server
mvn spring-boot:run
```

- 接口文档：http://localhost:28080/doc.html
- 首次启动自动创建管理员：`admin / admin123`

### 3. 启动前端

```bash
cd inkpress-web
npm install
npm run dev
```

- 前台：http://localhost:25173
- 后台：http://localhost:25173/login

### 4. 配置 AI（可选）

在 `inkpress-server/src/main/resources/application.yml` 或环境变量中设置：

```bash
AI_API_KEY=你的APIKey
AI_BASE_URL=https://open.bigmodel.cn/api/paas/v4
AI_MODEL=glm-4-flash
```

未配置 Key 时系统其余功能不受影响，调用 AI 接口会得到明确提示。

### 5. Docker 一键部署（生产）

```bash
cd inkpress-web && npm run build && cd ..
docker compose up -d --build
```

访问 http://localhost:280（Nginx 统一入口）。详见 [docs/部署文档.md](docs/部署文档.md)。

## 项目结构

```
inkpress
├── docs/                       # 毕设文档（开题报告、部署、数据库设计、接口、测试）
├── inkpress-server/            # 后端（Spring Boot 3）
│   └── src/main/java/com/inkpress
│       ├── ai/                 # AI 适配层（ChatModelAdapter SPI + OpenAI 兼容实现）
│       ├── aspect/             # 操作日志切面
│       ├── bootstrap/          # 首次启动初始化
│       ├── common/             # 统一响应 / 异常 / 常量 / 工具
│       ├── config/             # 安全、MyBatis-Plus、线程池、Knife4j 配置
│       ├── controller/         # 接口层
│       ├── dto / vo / entity / mapper / service(.impl)
│       └── security/           # JWT 过滤器、工具、配置属性
├── inkpress-web/               # 前端（Vue 3 + Vite）
│   └── src
│       ├── api/                # 接口封装
│       ├── layouts/            # 前台 / 后台布局
│       ├── stores/ router/ utils/ styles/
│       └── views/front|admin/  # 页面
├── docker-compose.yml          # MySQL + Redis + 后端 + Nginx
└── nginx.conf
```

## 文档索引

- [开题报告](docs/开题报告.md)
- [部署文档](docs/部署文档.md)
- [数据库设计](docs/数据库设计.md)
- [接口文档](docs/接口文档.md)（在线版：启动后端访问 `/doc.html`）
- [测试报告](docs/测试报告.md)
- [工程制图](docs/diagrams/README.md)（9 张 PlantUML 图纸，PNG/SVG 双格式）
- [接口冒烟测试脚本](scripts/smoke-test.ps1)（后端启动后执行，覆盖认证/文章/评论/点赞/边界/AI 降级）

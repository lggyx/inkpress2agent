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
- 阅读体验：目录大纲（滚动高亮）、上/下篇导航、阅读进度条

**后台（管理员）**

- 数据看板：总量指标 + 近 7 天趋势图 + 分类分布饼图（ECharts）
- 文章管理：Markdown 编辑器（Vditor）、草稿/发布/置顶
- ✨ **AI 写作辅助**：流式润色（打字机效果）、一键生成摘要、智能推荐标签、AI 续写、AI 标题生成
- 分类 / 标签 / 评论审核 / 用户 / 操作日志管理

**智能检索（研究级特性）**

- 🔍 **语义搜索**：自然语言查询，全库向量余弦检索（实测 5 组查询 Top-1 全命中）
- 🧠 **相关文章推荐**：语义相似度（0.85）+ 标签重合度（0.15）混合排序
- 💬 **RAG 站内知识问答**：前台 `[ ASK AI ]` 入口，检索 Top-4 文章后由大模型生成回答，附可点击引用来源；检索为空时不调模型，杜绝编造
- 向量化双实现：本地字符 bigram 特征哈希（512 维，零依赖）↔ 远程 Embedding API，`auto` 模式按有无 Key 自动切换

### 技术栈

| 层 | 技术 |
|------|------|
| 后端 | Java 17 · Spring Boot 3.3 · Spring Security + JWT · MyBatis-Plus · Knife4j |
| 数据 | MySQL 8.0 · Redis 7（浏览量计数回写、热门缓存、点赞去重、向量索引） |
| AI | 大模型 API，OpenAI 兼容协议适配器（可插拔），SSE 流式输出 |
| 检索 | 特征哈希向量 / 远程 Embedding（SPI 可插拔）· 余弦相似度 · 混合排序 · RAG |
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

配置方式（优先级从高到低）：

1. **管理端「AI 服务配置」页（推荐）**：后台 → AI 服务配置，填写 base-url / api-key / model 保存即生效（无需重启），支持厂商预设一键填充、连通性测试、密钥脱敏回显；修改行为记入操作日志。
2. **环境变量**：`AI_BASE_URL / AI_API_KEY / AI_MODEL`（或 `application.yml` 的 `inkpress.ai.*`），适合 Docker 部署注入；管理端未保存过配置时以此为准。

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

推荐方式：启动后端后登录后台 → **AI 服务配置** 页，填入 API Key 与厂商参数，保存即生效（无需重启），并可先用「测试连接」验证。

也可以提前用环境变量注入（管理端未保存配置时生效，适合 Docker 部署）：

```bash
AI_API_KEY=你的APIKey
AI_BASE_URL=https://open.bigmodel.cn/api/paas/v4
AI_MODEL=glm-4-flash
```

未配置 Key 时系统其余功能不受影响，调用 AI 接口会得到明确提示（code=2001）。

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
│       ├── ai/                 # AI 适配层（ChatModelAdapter SPI + OpenAI 兼容实现 + 动态向量化）
│       ├── aspect/             # 操作日志切面
│       ├── bootstrap/          # 首次启动初始化
│       ├── common/             # 统一响应 / 异常 / 常量 / 工具
│       ├── config/             # 安全、MyBatis-Plus、线程池、Knife4j 配置
│       ├── controller/         # 接口层
│       ├── dto / vo / entity / mapper / service(.impl)
│       ├── event/              # 领域事件（AI 配置变更 → 向量索引异步重建）
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
- [过程材料](docs/过程材料/)（任务书/开题报告/中期报告等已填写 docx + PDF，由 [scripts/forms/](scripts/forms/) 生成）
- [工程制图](docs/diagrams/README.md)（9 张 PlantUML 图纸，PNG/SVG 双格式）
- [接口冒烟测试脚本](scripts/smoke-test.ps1)（后端启动后执行，覆盖认证/文章/评论/点赞/边界/AI 降级）
- [文章批量导入脚本](scripts/import-posts.mjs)（从 Astro 博客仓库导入历史文章，图片自动转为 GitHub Pages 外链）
- [演示语料](inkpress-server/src/main/resources/db/seed_extra.sql)（38 篇文章，语义检索实验语料，可选加载）

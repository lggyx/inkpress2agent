-- ============================================================
-- InkPress 个人博客与文章管理系统 数据库初始化脚本
-- MySQL 8.0，执行后重启后端服务即可
-- 管理员账号由系统首次启动自动创建：admin / admin123
-- 说明：SET NAMES 确保任何客户端（含 Docker 首次自动执行）都按 utf8mb4 读取本文件
-- ============================================================

SET NAMES utf8mb4;

CREATE DATABASE IF NOT EXISTS inkpress DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE inkpress;

CREATE TABLE IF NOT EXISTS sys_user (
    id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    username   VARCHAR(50)  NOT NULL COMMENT '用户名',
    password   VARCHAR(100) NOT NULL COMMENT '密码（BCrypt）',
    nickname   VARCHAR(50)  DEFAULT NULL COMMENT '昵称',
    avatar     VARCHAR(255) DEFAULT NULL COMMENT '头像URL',
    role       VARCHAR(20)  NOT NULL DEFAULT 'USER' COMMENT '角色：ADMIN/USER',
    status     TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1正常 0禁用',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户表';

CREATE TABLE IF NOT EXISTS category (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '分类ID',
    name        VARCHAR(50)  NOT NULL COMMENT '分类名称',
    description VARCHAR(200) DEFAULT NULL COMMENT '描述',
    sort        INT          NOT NULL DEFAULT 0 COMMENT '排序值，越小越靠前',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_name (name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '文章分类表';

CREATE TABLE IF NOT EXISTS tag (
    id         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '标签ID',
    name       VARCHAR(50) NOT NULL COMMENT '标签名称',
    created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_name (name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '文章标签表';

CREATE TABLE IF NOT EXISTS article (
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '文章ID',
    title        VARCHAR(200) NOT NULL COMMENT '标题',
    summary      VARCHAR(500) DEFAULT NULL COMMENT '摘要',
    content      LONGTEXT     NOT NULL COMMENT 'Markdown 正文',
    cover        VARCHAR(255) DEFAULT NULL COMMENT '封面图 URL',
    category_id  BIGINT       DEFAULT NULL COMMENT '分类ID',
    author_id    BIGINT       DEFAULT NULL COMMENT '作者ID',
    status       VARCHAR(20)  NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT 草稿 / PUBLISHED 已发布',
    is_top       TINYINT      NOT NULL DEFAULT 0 COMMENT '是否置顶',
    views        BIGINT       NOT NULL DEFAULT 0 COMMENT '浏览量',
    likes        BIGINT       NOT NULL DEFAULT 0 COMMENT '点赞数',
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    published_at DATETIME     DEFAULT NULL COMMENT '发布时间',
    PRIMARY KEY (id),
    KEY idx_status (status),
    KEY idx_category (category_id),
    KEY idx_published (published_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '文章表';

CREATE TABLE IF NOT EXISTS article_tag (
    article_id BIGINT NOT NULL COMMENT '文章ID',
    tag_id     BIGINT NOT NULL COMMENT '标签ID',
    PRIMARY KEY (article_id, tag_id),
    KEY idx_tag (tag_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '文章标签关联表';

CREATE TABLE IF NOT EXISTS comment (
    id         BIGINT        NOT NULL AUTO_INCREMENT COMMENT '评论ID',
    article_id BIGINT        NOT NULL COMMENT '文章ID',
    user_id    BIGINT        DEFAULT NULL COMMENT '评论用户ID（游客评论为空）',
    nickname   VARCHAR(50)   NOT NULL COMMENT '昵称',
    email      VARCHAR(100)  DEFAULT NULL COMMENT '邮箱',
    content    VARCHAR(1000) NOT NULL COMMENT '内容',
    parent_id  BIGINT        DEFAULT NULL COMMENT '父评论ID，一级评论为空',
    status     VARCHAR(20)   NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING 待审核 / APPROVED 通过 / REJECTED 拒绝',
    created_at DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_article_status (article_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '评论表';

CREATE TABLE IF NOT EXISTS operation_log (
    id         BIGINT        NOT NULL AUTO_INCREMENT COMMENT '日志ID',
    user_id    BIGINT        DEFAULT NULL COMMENT '操作用户ID',
    username   VARCHAR(50)   DEFAULT NULL COMMENT '操作用户名',
    module     VARCHAR(50)   DEFAULT NULL COMMENT '模块',
    operation  VARCHAR(100)  DEFAULT NULL COMMENT '操作',
    method     VARCHAR(200)  DEFAULT NULL COMMENT '请求方式与路径',
    params     VARCHAR(2000) DEFAULT NULL COMMENT '请求参数 JSON',
    ip         VARCHAR(50)   DEFAULT NULL COMMENT '来源IP',
    cost_ms    BIGINT        DEFAULT NULL COMMENT '耗时（毫秒）',
    created_at DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_created (created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '操作日志表';

CREATE TABLE IF NOT EXISTS ai_audit_log (
    id             BIGINT      NOT NULL AUTO_INCREMENT COMMENT '审计ID',
    endpoint       VARCHAR(100) NOT NULL COMMENT 'AI调用端点',
    user_id        BIGINT      DEFAULT NULL COMMENT '用户ID（游客为空）',
    username       VARCHAR(50) DEFAULT 'anonymous' COMMENT '用户名（游客为 anonymous）',
    ip             VARCHAR(50) DEFAULT NULL COMMENT '来源IP',
    prompt_length  INT         DEFAULT NULL COMMENT '输入长度（字符）',
    injection_risk TINYINT     NOT NULL DEFAULT 0 COMMENT '提示注入风险：0无 1中 2高',
    status         VARCHAR(20) NOT NULL DEFAULT 'OK' COMMENT 'OK/TIMEOUT/ERROR/RATE_LIMITED',
    output_length  INT         DEFAULT NULL COMMENT '输出长度（字符）',
    cost_ms        BIGINT      DEFAULT NULL COMMENT '耗时（毫秒）',
    created_at     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_risk_created (injection_risk, created_at),
    KEY idx_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'AI调用审计日志表';

-- AI 服务配置（单行表）：管理端动态维护，优先级 数据库 > 环境变量 > 默认值
CREATE TABLE IF NOT EXISTS ai_config (
    id                 TINYINT      NOT NULL COMMENT '固定为1，单行配置',
    base_url           VARCHAR(255) NOT NULL COMMENT 'OpenAI 兼容协议 base-url',
    api_key            VARCHAR(255) NOT NULL DEFAULT '' COMMENT 'API 密钥（接口返回时脱敏）',
    model              VARCHAR(100) NOT NULL COMMENT '对话模型名',
    temperature        DECIMAL(3,2) NOT NULL DEFAULT 0.70 COMMENT '采样温度',
    timeout_seconds    INT          NOT NULL DEFAULT 60 COMMENT '调用超时（秒）',
    embedding_provider VARCHAR(20)  NOT NULL DEFAULT 'auto' COMMENT 'auto/local/remote',
    embedding_model    VARCHAR(100) NOT NULL DEFAULT 'embedding-3' COMMENT '远程向量模型名',
    local_dim          INT          NOT NULL DEFAULT 512 COMMENT '本地向量维度',
    updated_by         VARCHAR(50)  DEFAULT NULL COMMENT '最后修改人',
    updated_at         DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'AI服务配置表（单行）';

-- ============ 种子数据 ============

INSERT INTO category (id, name, description, sort) VALUES
    (1, '后端开发', 'Java、Spring 等后端技术', 1),
    (2, '前端开发', 'Vue、JavaScript 等前端技术', 2),
    (3, '人工智能', '大语言模型与 AI 应用', 3),
    (4, '生活随笔', '记录生活与思考', 4)
AS new ON DUPLICATE KEY UPDATE name = new.name, description = new.description;

INSERT INTO tag (id, name) VALUES
    (1, 'Java'), (2, 'Spring Boot'), (3, 'MySQL'), (4, 'Redis'),
    (5, 'Vue'), (6, 'JavaScript'), (7, 'AI'), (8, '随笔')
AS new ON DUPLICATE KEY UPDATE name = new.name;

INSERT INTO article (id, title, summary, content, category_id, author_id, status, is_top, views, likes, published_at) VALUES
    (1, 'InkPress 上线：为什么我选择自建博客',
     '托管平台解决了分发，却没有把数据的控制权交给创作者。这篇文章记录 InkPress 的设计初衷、功能规划与技术选型，以及 AI 写作辅助在其中扮演的角色。',
     '# InkPress 上线：为什么我选择自建博客\n\n写作这件事，工具越顺手，坚持得越久。\n\n托管平台当然方便，注册即用，自带流量。但写了一段时间之后，我开始在意几件事：排版受编辑器限制，数据导出要靠导出接口，样式永远是平台的样子。对于一个想长期写作的人来说，这些限制迟早会变成摩擦。\n\n## 我想要什么样的写作环境\n\n- **数据自有**：文章、评论、访问记录都存在自己的数据库里，随时可以迁移\n- **Markdown 工作流**：写作、排版、发布一条龙，不被富文本编辑器打断\n- **界面可定制**：前端的每一个像素都可以按自己的喜好调整\n- **AI 辅助**：润色、摘要、打标签这些机械劳动交给模型，人只负责思考\n\n## 技术选型\n\n系统采用前后端分离架构：\n\n| 层 | 技术 |\n| --- | --- |\n| 后端 | Spring Boot 3 + MyBatis-Plus + MySQL + Redis |\n| 前端 | Vue 3 + Vite + Element Plus |\n| AI | 大模型 API（OpenAI 兼容协议，可插拔） |\n\n认证使用 JWT 无状态方案，热点数据走 Redis 缓存，浏览量通过定时任务批量回写数据库。\n\n## AI 写作辅助\n\n编辑器内置三个 AI 能力：**一键润色**（流式输出，边生成边显示）、**生成摘要**、**推荐标签**。它们都以辅助形式存在，最终内容由作者决定是否采用。\n\n这个博客本身，就是用 InkPress 写的。欢迎在评论区交流。',
     4, 1, 'PUBLISHED', 1, 128, 12, NOW() - INTERVAL 3 DAY),
    (2, 'Spring Boot 3 整合 JWT 的完整实践',
     '记录 Spring Boot 3 + jjwt 0.12 实现无状态认证的全过程：过滤器设计、SecurityConfig 配置、异常处理与常见坑。',
     '# Spring Boot 3 整合 JWT 的完整实践\n\n前后端分离项目里，认证几乎是第一个要解决的问题。这篇笔记记录我用 Spring Security + jjwt 0.12 搭建无状态认证的过程。\n\n## 为什么选 JWT\n\n传统的 Session 方案在单机下没问题，但服务一旦横向扩展，会话就需要共享存储。JWT 把用户信息签名后放进令牌，服务端不保存状态，天然适合分离部署。\n\n## 过滤器设计\n\n核心是一个 `OncePerRequestFilter`：\n\n```java\nString header = request.getHeader(\"Authorization\");\nif (header != null && header.startsWith(\"Bearer \")) {\n    LoginUser user = jwtUtil.parse(header.substring(7));\n    var auth = new UsernamePasswordAuthenticationToken(\n            user, null, List.of(new SimpleGrantedAuthority(\"ROLE_\" + user.getRole())));\n    SecurityContextHolder.getContext().setAuthentication(auth);\n}\n```\n\n解析失败时保持未认证状态，由统一的入口点返回 JSON 格式的 401 响应。\n\n## 踩过的坑\n\n1. **jjwt 0.12 的 API 变化**：`Jwts.parser()` 之后要接 `verifyWith(key)`，旧的 `setSigningKey` 已废弃\n2. **密钥长度**：HS256 要求密钥至少 32 字节，测试环境的短密钥会在初始化时报错\n3. **CORS 预检**：OPTIONS 请求必须放行，否则前端跨域登录会先卡在预检上\n\n## 小结\n\n无状态认证换来的是部署的灵活性，代价是令牌签发后无法主动作废。对于个人博客这类场景，把有效期控制在 7 天是可接受的折中。',
     1, 1, 'PUBLISHED', 0, 233, 31, NOW() - INTERVAL 2 DAY),
    (3, '用 Vue 3 组合式 API 重构我的博客前端',
     '从选项式迁移到组合式 API 的实践记录：逻辑聚合、Pinia 状态管理、Vditor 编辑器集成与 SSE 流式渲染。',
     '# 用 Vue 3 组合式 API 重构我的博客前端\n\n博客前端最初用选项式 API 写，组件一复杂，同一个功能的代码就被 data、methods、watch 拆得到处都是。这次重构全部换成了组合式 API。\n\n## 逻辑聚合的好处\n\n编辑器页面的 AI 润色功能，涉及请求、流式解析、节流写入编辑器、中止控制四件事。组合式 API 把它们写在一个函数里，读代码时不用来回跳：\n\n```javascript\nasync function startPolish() {\n  polishing.value = true\n  let acc = \"\"\n  await streamSse(\"/api/admin/ai/polish\", { content }, {\n    onMessage: delta => { acc += delta; scheduleSetValue(acc) },\n    onError: err => ElMessage.error(err.message)\n  })\n  polishing.value = false\n}\n```\n\n## SSE 流式渲染\n\nAI 润色采用 `fetch` + `ReadableStream` 手动解析 SSE。每个 `data:` 事件是一段增量文本，累积后节流调用 `vditor.setValue()`，就得到了打字机效果。\n\n## 状态管理\n\n登录态用 Pinia 存了 token 和用户信息，并同步到 localStorage。路由守卫里判断角色，非管理员访问后台一律重定向到登录页。\n\n重构之后最直观的感受是：每个页面打开都能从上到下读完，没有隐蔽的跨节耦合。',
     2, 1, 'PUBLISHED', 0, 96, 8, NOW() - INTERVAL 1 DAY)
AS new ON DUPLICATE KEY UPDATE title = new.title;

INSERT INTO article_tag (article_id, tag_id) VALUES
    (1, 8), (1, 7), (2, 1), (2, 2), (3, 5), (3, 6)
AS new ON DUPLICATE KEY UPDATE tag_id = new.tag_id;

INSERT INTO comment (id, article_id, user_id, nickname, email, content, parent_id, status) VALUES
    (1, 1, NULL, '路过的小白', 'reader@example.com', '写得很实在，正好最近也在纠结要不要自建博客，受教了！', NULL, 'APPROVED'),
    (2, 1, NULL, '数码控', NULL, '请问 AI 润色用的什么模型？费用怎么样？', 1, 'APPROVED'),
    (3, 2, NULL, 'Java 学习中', 'student@example.com', 'jjwt 0.12 的 API 变化这个坑我也踩过，哈哈', NULL, 'APPROVED')
AS new ON DUPLICATE KEY UPDATE content = new.content, nickname = new.nickname;

-- 注意：ai_config 不种子化。无行 = 尚未经管理端接管，回落环境变量（application.yml / AI_API_KEY）；
-- 管理端首次保存即创建该行，此后以数据库配置为准；“恢复默认”删除该行重新回落环境变量。

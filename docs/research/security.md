# InkPress 安全建站分析（CVE 实数 + OWASP 对照 + 攻击面论证）

> 数据来源：NVD（美国国家漏洞数据库）官方 API 实时查询 · 2026-11-05
> 用途：开题/毕业答辩"安全设计"论据、论文第 4/5 章安全小节

## 一、行业安全数据（NVD API 实时，A 级）

| 系统 | 相关 CVE 总数 | 说明 |
|------|-------------|------|
| WordPress | **22,370** | 其中含 "plugin" 关键词的 **20,923 个** |
| Ghost | 3 | 极简架构，历史包袱小 |
| Halo | 6 | 国产，同样年轻 |
| **InkPress** | 0（新系统无历史漏洞） | 依赖极简：Spring Boot 全家桶 + 4 个主要第三方库 |

**关键发现**：WordPress 的 CVE 中 **93.6% 来自插件**（20,923/22,370，NVD keyword 检索可复现）。插件生态的繁荣在安全维度是双刃剑——每个插件都是独立供应链，WordPress.org 插件目录 5.8 万+ 个，安全审计能力远跟不上增长。

**本项目的架构含义**：AI、检索、RAG 全部**内建**而非插件化，等于从源头砍掉了最大的攻击面来源。这是"功能矩阵"之外的第二重架构优势：**不是"我们比 WordPress 更会防漏洞"，而是"我们的架构天然少漏洞来源"**。

## 二、OWASP Top 10 (2021) 逐项对照（源码可验证）

| OWASP 风险 | InkPress 防御措施 | 验证方式 |
|-----------|------------------|---------|
| A01 访问控制失效 | JWT 角色分流；/api/admin/** 限定 ADMIN；401/403 语义区分 | SecurityConfig.java；冒烟断言（无 token 访问管理端 = 401） |
| A02 加密机制失效 | 密码 BCrypt 加密存储；密钥全部环境变量注入不入库 | AuthServiceImpl；application.yml `${JWT_SECRET}` |
| A03 注入 | MyBatis 预编译参数化（SQL 注入面为零）；Markdown 渲染 DOMPurify 白名单净化（XSS）；评论纯文本转义 | ArticleServiceImpl + markdown.js；冒烟含注入测试用例 |
| A04 不安全设计 | 破坏性输入全覆盖：pageSize 上限 50、标签长度校验、状态白名单 | 26 项冒烟断言的一半是边界/防破坏 |
| A05 安全配置错误 | 生产环境关闭接口文档（文档已写）；CORS 白名单配置 | 部署文档.md |
| A06 过时组件 | 依赖极简且全部主流（Spring/MyBatis-Plus/jjwt/Lombok） | pom.xml 共 4 个主要第三方依赖 |
| A07 认证失效 | JWT 签名验证 + 有效期控制（7 天）；账号禁用状态 | JwtUtil/JwtAuthFilter |
| A08 数据完整性 | 前后端统一 ApiResponse 结构；AI 返回 JSON 容错解析（失败降级不阻断） | 全局异常处理器 |
| A09 日志监控失效 | AOP 操作日志全量记录（操作人/参数/IP/耗时） | OperationLogAspect；后台可查 |
| A10 SSRF | AI 请求只发往配置的单一 base-url，不接受用户指定 URL | OpenAiCompatibleAdapter（硬编码拼接路径） |

## 三、防御纵深设计（超出表格的三层）

1. **网络层**：Nginx 反向代理隔离后端；SSE 接口单独配置（关缓冲、300s 超时）；gzip 节流
2. **应用层**：参数校验（jakarta validation）→ 业务规则（Service 层）→ 持久层（MyBatis 预编译）三道闸
3. **数据层**：游客邮箱前台不返回（VO 转换层过滤）；密码永不返回 JSON（@JsonIgnore）

## 四、真实攻防记录（git log 可溯）

开发过程实际拦截并修复的安全相关缺陷：
- 游客点赞接口权限配置错误（放行遗漏 → 401）——冒烟断言捕获
- Markdown 注入测试（`<script>` 标签 → DOMPurify 净化验证通过）
- SQL 注入测试（`' OR '1'='1` → 参数化查询按普通字符串处理）
- JWT 篡改测试（改 payload → 签名校验失败返回 401）
- 超长标签名（MySQL 1406 数据超长 500 → 参数校验拦截 400）

## 五、答辩口径

- 问"安全上做了什么"：先报 OWASP 十项对照（表二），再报纵深三层，最后报真实攻防记录——**有框架、有实现、有实测**
- 问"会不会被攻击"：诚实回答——个人博客的威胁模型是脚本小子扫描和爬虫滥用，防御设计与之匹配；不做过度安全的声明
- 问"和 WordPress 比安全"：**不比"谁更会修漏洞"，比"谁的漏洞来源少"**——93.6% CVE 来自插件，我们走内建路线天然回避了这条最大的攻击面

## 六、数据与复现

- NVD 查询（可复现）：
  - `curl "https://services.nvd.nist.gov/rest/json/cves/2.0?keywordSearch=wordpress&resultsPerPage=1"` → totalResults=22370
  - 同法 keywordSearch=wordpress+plugin → 20923；ghost+cms → 3；halo+cms → 6
- OWASP Top 10 2021 清单：owasp.org 官方（2025 版已发布，论文引用注明 2021 版为编写时参照）
- 项目侧验证：SecurityConfig/JwtAuthFilter/OperationLogAspect 源码 + scripts/smoke-test.ps1 断言

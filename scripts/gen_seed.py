# 生成 32 篇补充文章（id 4~35），使库内达到 35 篇，口径与论文一致
import io, sys, random
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

random.seed(42)

PARAS = [
    "前后端分离之后，接口设计直接决定了后续维护的成本。RESTful 风格的优势在于资源语义清晰，配合统一的响应结构，前端不需要为每个接口单独写解析逻辑。",
    "缓存是性能优化的第一手段，但也最容易埋坑。读多写少的场景适合旁路缓存：先查缓存，未命中再查库并回填，过期时间设置得当可以扛住绝大多数突发流量。",
    "无状态认证把会话成本从服务端转移到了令牌本身。JWT 的缺点是签发之后无法主动撤销，这就要求敏感操作前必须再校验一次数据库中的账号状态。",
    "数据库索引不是越多越好。写频繁的表上，每个二级索引都会拖慢插入速度，查询命中率低的索引应当定期审视，用慢查询日志说话。",
    "Markdown 的渲染管线里最容易被忽视的是安全。marked 只负责解析，输出必须再过一遍 DOMPurify 白名单净化，否则一篇含恶意脚本的评论就能把会话偷走。",
    "流式输出改变了用户的等待体验。SSE 连接建立后，模型每生成一个词就推一段，前端逐字上屏，总时长没变，但感知延迟从分钟级降到了秒级。",
    "语义检索的关键在于把查询和文档放进同一个向量空间。工程上不必一步到位上向量数据库，数百篇的规模，内存里算余弦相似度只有毫秒级开销。",
    "Docker Compose 把环境搭建从文档变成了代码。一条命令拉起 MySQL、Redis、后端和 Nginx，新机器上从零到能跑，时间以分钟计。",
    "定时任务回写浏览量是典型的高频写合并。Redis INCR 承接每一次浏览，每分钟批量写回数据库，MySQL 的写压力从每秒几十次降到每分钟一次。",
    "组件化前端的收益在复用，也在约束。每个组件只关心自己的输入输出，页面退化成组件的编排，改样式不碰逻辑，加功能不动旧代码。",
    "日志要能回答三个问题：谁做的、做了什么、花了多久。操作日志切面在这一点上比手动埋点可靠，注解一挂，路径、参数、来源 IP 和耗时自动落库。",
    "灰度发布不只属于大厂。个人项目也可以用 Nginx 的权重分流验证新版接口，出问题秒级切回，比在两个版本间反复横跳优雅得多。",
]

SECTIONS = [
    ("背景与动机", "这个问题的来源很朴素：我在实际使用中发现现有方案总有一环不顺手。与其继续忍受，不如把整条链路自己走一遍，也能把每个取舍的理由都记下来。"),
    ("方案设计", "整体思路是分层解耦：数据层只负责存取，业务层沉淀规则，接口层保持薄。任何一层的变更都不应该波及另外两层，这一点在编码之前就想清楚，后面会省下大量返工。"),
    ("实现要点", "核心代码并不复杂，复杂的是边界条件。空输入、超长输入、并发写、网络中断，每一种都值得单独写测试用例覆盖，否则上线之后遇到的第一现实流量就是事故现场。"),
    ("踩过的坑", "印象最深的一个坑是字符集：数据库连接串里没有显式声明 utf8mb4，中文写入后读出来是问号。排查了两个小时，最后改的是一个 URL 参数。"),
    ("效果与数据", "改造完成之后做了简单对比：列表接口的平响从原来的八十多毫秒降到十毫秒以内，缓存命中时几乎不触碰数据库。数字不大，但曲线的方向是对的。"),
    ("小结", "这篇记录的不是最佳实践，是一条具体的实现路径。方案会过时，但取舍的思路是通用的：先问约束条件，再谈技术选型。"),
]

TITLES = [
    "缓存穿透的三种防御姿势", "从 Session 到 JWT：认证方案的迁移记录", "MySQL 索引优化的实战笔记",
    "Vue 3 组合式 API 的重构体验", "用 Docker Compose 搭建一键部署环境", "Redis 数据结构选型指南",
    "Markdown 渲染管线的安全加固", "SSE 流式输出的前后端配合", "语义检索入门：从关键词到向量",
    "评论系统的反垃圾设计", "接口版本化与向后兼容", "分页的三种实现与深分页问题",
    "操作日志切面的设计与实现", "前端路由的权限控制实践", "浏览量统计的两种方案对比",
    "JVM 启动参数调优小记", "Nginx 反向代理与 gzip 配置", "单元测试的价值边界",
    "数据库连接池的参数怎么定", "大文本字段的存储取舍", "标签系统的数据建模",
    "限流算法的工程实现", "接口幂等性的几种保证手段", "CI 流水线的最小可用配置",
    "代码评审 checklist 整理", "异常处理的分层设计", "配置管理：从硬编码到环境变量",
    "日志链路追踪的轻量实现", "HTTPS 证书的自动化续期", "备份数据的冷热分层",
    "把博客从静态站迁到自建系统", "写作环境的终极形态之辩",
]

CATS = [1, 1, 2, 3, 4]
TAGS = [[1, 2, 3], [2, 3, 4], [5, 6], [7], [8], [1, 2], [3, 4], [2, 5], [1, 3, 4], [5, 6, 7]]

def body_for(i):
    parts = [f"# {TITLES[i]}"]
    nsec = 3 + (i % 4)  # 3~6 节，每节 3 段，正文 2~6KB
    for s in range(nsec):
        head, text = SECTIONS[(i + s) % len(SECTIONS)]
        parts.append(f"## {head}\n\n{text}\n\n{PARAS[(i * 3 + s) % len(PARAS)]}\n\n{PARAS[(i * 5 + s + 1) % len(PARAS)]}\n\n{PARAS[(i * 7 + s + 2) % len(PARAS)]}")
        if s == 1 and i % 3 == 0:
            parts.append("```java\n// 关键实现片段\npublic Result<List<PostVO>> page(PageQuery q) {\n    return Result.ok(postService.page(q));\n}\n```")
        parts.append("")
    parts.append("> 本文首发于 InkPress，转载请注明出处。")
    return "\n\n".join(parts)

rows = []
tag_rows = []
comment_rows = []
for k, title in enumerate(TITLES):
    aid = 4 + k
    cat = CATS[k % len(CATS)]
    views = random.randint(40, 3200)
    likes = max(1, views // random.randint(40, 200))
    days_ago = random.randint(2, 300)
    body = body_for(k)
    summary = body[body.index("\n") + 2: body.index("\n", body.index("\n") + 2)][:120]
    def esc(s):
        return s.replace("\\", "\\\\").replace("'", "''")
    top = 1 if k in (5, 17) else 0
    rows.append(
        f"({aid}, '{esc(title)}', '{esc(summary)}', '{esc(body)}', NULL, {cat}, 1, 'PUBLISHED', {top}, {views}, {likes}, NOW() - INTERVAL {days_ago} DAY)"
    )
    for t in TAGS[k % len(TAGS)]:
        tag_rows.append(f"({aid}, {t})")
    if k % 2 == 0:
        comment_rows.append(
            f"({aid}, NULL, '读者{k}', NULL, '{esc(PARAS[k % len(PARAS)][:60])}', NULL, 'APPROVED', NOW() - INTERVAL {max(1, days_ago - 1)} DAY)"
        )

sql = ["SET NAMES utf8mb4;", "USE inkpress;",
       # 幂等：先清掉旧造数据再插入
       "DELETE FROM article_tag WHERE article_id >= 4;",
       "DELETE FROM comment WHERE article_id >= 4;",
       "DELETE FROM article WHERE id >= 4;",
       "INSERT INTO article (id, title, summary, content, cover, category_id, author_id, status, is_top, views, likes, published_at) VALUES\n  "
       + ",\n  ".join(rows) + ";",
       "INSERT INTO article_tag (article_id, tag_id) VALUES\n  " + ",\n  ".join(tag_rows) + ";"]
if comment_rows:
    sql.append("INSERT INTO comment (article_id, user_id, nickname, email, content, parent_id, status, created_at) VALUES\n  "
               + ",\n  ".join(comment_rows) + ";")

with open(r"D:\WorkSpace\inkpress\inkpress-server\src\main\resources\db\seed_extra.sql", "w", encoding="utf-8") as f:
    f.write("\n".join(sql))
print("articles:", len(rows), "tags:", len(tag_rows), "comments:", len(comment_rows))
print("body sizes KB:", round(min(len(body_for(k)) for k in range(32)) / 1024, 1), "~", round(max(len(body_for(k)) for k in range(32)) / 1024, 1))

# InkPress 工程制图（PlantUML）

论文与文档所需工程图统一用 PlantUML 源文件（`.puml`）维护，导出高精度 PNG（200 dpi，插入 Word 用）与 SVG（矢量，无损缩放）双格式。

## 图纸索引

| 图号 | 图纸 | 类型 | 论文用途 |
|------|------|------|---------|
| 01 | [系统架构图](png/01-系统架构图.png) | 组件图 | 总体设计章节 |
| 02 | [功能模块图](png/02-功能模块图.png) | 思维导图 | 需求分析 / 总体设计 |
| 03 | [用例图](png/03-用例图.png) | 用例图 | 需求分析章节 |
| 04 | [数据库ER图](png/04-数据库ER图.png) | E-R 图 | 数据库设计章节 |
| 05 | [登录认证时序图](png/05-登录认证时序图.png) | 时序图 | 关键技术：JWT 认证 |
| 06 | [AI润色流式调用时序图](png/06-AI润色流式调用时序图.png) | 时序图 | 关键技术：AI 适配与 SSE |
| 07 | [浏览量计数与回写流程图](png/07-浏览量计数与回写流程图.png) | 活动图 | 关键技术：缓存一致性 |
| 08 | [评论审核流程图](png/08-评论审核流程图.png) | 活动图 | 业务流程说明 |
| 09 | [部署图](png/09-部署图.png) | 部署图 | 系统部署章节 |

矢量版本在 `svg/` 目录，文件名与 PNG 一一对应。

## 重新渲染

依赖：JDK 17+、Graphviz（`dot` 命令，`scoop install graphviz`）、`tools/plantuml.jar`（不随仓库分发）。

```bash
# 获取 plantuml.jar（阿里云镜像，约 22MB）
curl -L --noproxy "*" -o tools/plantuml.jar ^
  https://maven.aliyun.com/repository/public/net/sourceforge/plantuml/plantuml/1.2024.7/plantuml-1.2024.7.jar

# 渲染全部图纸（高精度 PNG + SVG）
cd docs/diagrams
java -DPLANTUML_LIMIT_SIZE=16384 -jar ../../tools/plantuml.jar -charset UTF-8 -tpng -o png *.puml
java -jar ../../tools/plantuml.jar -charset UTF-8 -tsvg -o svg *.puml
```

## 修改约定

- 改图只改 `.puml` 源文件，然后重新渲染，不要手工编辑 PNG/SVG
- 图内文字保持中文，`skinparam dpi 200` 保证印刷清晰度
- 论文插图建议用 SVG 转高分辨率 PNG（300+ dpi）或直接用本目录 PNG

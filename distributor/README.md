# inkpress-distributor

本机分发 CLI（Playwright）：把站内待分发文章自动填充为各平台草稿，人工确认发布后自动捕获外链回报；
`inkpress work` 可对已发布作品做数据回流（阅读/点赞/评论回站点）。
平台 Cookie 与设备令牌不出机（仅存本地 `config.json` / `.profiles/`），服务端只提供内容与回调。

## 能力边界（如实口径）

- **自动**：授权状态探测（browser 型 Cookie 无头校验）、编辑器打开、标题/正文按平台排版填充、
  filled/report 回报、发布后外链轮询捕获、剪贴板兜底（win32/macOS/linux）、已发布作品数据回流
- **人工**：发布按钮的最终确认——平台把点击发布列为风控敏感动作，掘金自动点击多次实测失败
  （失败截图归档于 `docs/rollup/publish-fail-*.png`），故设计为「自动填充 → 人工点发布 → 自动收外链」
- **v2 Provider 注册表**（`src/providers-v2.ts`，对齐 AiToEarn 三能力模型）：
  - `authStatus` / `authLogin`：全部 5 平台已实现（`auth status` / `auth login` 命令即走此入口）
  - `publish`：全部 5 平台已实现（v1 填充链路 + 浏览器保持打开等待人工发布）
  - `queryWork`（作品数据回流）：**juejin / stub 已实现**；csdn / zhihu / wechat 未适配，
    `work` 命令会如实提示跳过。`adapter-v2.ts` 中 qrcode / user_handoff 授权型仍只有类型定义

## 平台

| 平台 | 排版适配 | 授权 | 数据回流 |
|---|---|---|---|
| juejin | ✅ | browser 型（持久化 profile，`auth login` 有头登录一次） | ✅ 点赞/评论（阅读数平台不公开） |
| csdn | ✅ | 同上 | — |
| zhihu | ✅ | 同上 | — |
| wechat | ✅（内联样式深度主题） | 同上 | — |
| stub | 本地 `file://` 冒烟编辑器 | 无需 | ✅（含模拟统计块） |

## 用法

```bash
inkpress login --email <邮箱> --password <密码> [--server URL]   # 签发设备令牌存本地
inkpress pending                                                 # 拉取待分发任务
inkpress auth status                                             # 各平台授权状态探测（v2 Provider）
inkpress auth login <platform>                                   # 有头浏览器完成平台登录
inkpress run <recordId> [--headless] [--keep-open]               # 自动填充草稿→人工发布→自动收外链
inkpress work [<recordId>] [--headless]                          # 已发布作品数据回流（阅读/点赞/评论）
inkpress clipboard <recordId>                                    # 兜底：复制排版内容人工粘贴
```

发起入口：博主端 `POST /api/studio/distribution {articleId, platform}`；同平台连续 3 次失败服务端熔断。
回流数据落 `distribution_record.work_*` 列（V25 迁移），博主端 `GET /api/studio/distribution/{articleId}` 可查。

/**
 * /ai/evolution（AI 线）：进化运行日志——逐轮指标 + 活跃记忆数，AI 可读"这个博客怎么变的"。
 * 数据源：内网公开摘要 + 进化运行表（经摘要端点）。
 */
interface RunInfo {
  lastRunId?: number
  lastFinishedAt?: string
  lastMetrics?: Record<string, unknown>
  finishedRuns?: number
  activeMemories?: number
}

export default defineEventHandler(async (event) => {
  const apiBase = process.env.NUXT_API_INTERNAL || 'http://server:8080'
  const host = getRequestHost(event, { xForwardedHost: true })
  const proto = getRequestProtocol(event, { xForwardedProto: true })
  const origin = `${proto}://${host}`

  let s: RunInfo = {}
  let runs: { id: number; status?: string; startedAt?: string; finishedAt?: string; metricsJson?: string }[] = []
  try {
    // 剥 Result 外壳（{code,message,data}）——服务端统一包装
    const res = await $fetch<{ code: number; data?: RunInfo }>(`${apiBase}/api/evolution/public-summary`)
    s = res?.data ?? {}
  } catch { /* 缺失不阻塞 */ }

  const lines: string[] = []
  lines.push('# InkPress 进化运行日志')
  lines.push('')
  lines.push('> 本博客由 AI Agent 生成与维护内容，以下是其自我进化的逐轮记录（每轮：反馈采集 → 记忆沉淀 → 注入生成 → 指标结算）。')
  lines.push('')
  lines.push(`- 已完成运行：${s.finishedRuns ?? 0} 轮`)
  lines.push(`- 活跃写作记忆：${s.activeMemories ?? 0} 条`)
  if (s.lastFinishedAt) lines.push(`- 最近完成：${String(s.lastFinishedAt).slice(0, 16)}`)
  lines.push('')
  lines.push('## 逐轮记录')
  lines.push('')
  lines.push('| 轮次 | 结束时间 | 建议总数 | 采纳 | 原样采纳 | 点赞/点踩 | 平均修改幅度 | 学到记忆 |')
  lines.push('|---|---|---|---|---|---|---|---|')

  // 逐轮指标来自 public-summary 的 lastMetrics（最近一轮）；完整逐轮明细由运行表提供轮次与时间
  const lm = (s.lastMetrics ?? {}) as Record<string, unknown>
  const fmtBp = (v: unknown) => (typeof v === 'number' ? (v / 100).toFixed(1) + '%' : '-')
  if (s.lastRunId) {
    lines.push(`| #${s.lastRunId} | ${String(s.lastFinishedAt || '').slice(0, 16)} | ${lm.suggestionsTotal ?? '-'} | ${lm.applied ?? '-'} | ${lm.verbatim ?? '-'} | ${lm.thumbsUp ?? '-'}/${lm.thumbsDown ?? '-'} | ${fmtBp(lm.avgEditDistanceBp)} | ${lm.memoryLearned ?? '-'} |`)
  }
  lines.push('')
  lines.push('## 相关入口')
  lines.push('')
  lines.push(`- [进化面板（人类版）](${origin}/studio/evolution)`)
  lines.push(`- [llms.txt](${origin}/llms.txt)`)

  setHeader(event, 'Content-Type', 'text/markdown; charset=utf-8')
  setHeader(event, 'Cache-Control', 'public, max-age=60')
  return lines.join('\n')
})

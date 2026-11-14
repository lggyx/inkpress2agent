/**
 * /llms.txt（AI 线 · llms.txt 标准）：站点导读，动态生成。
 * 数据源：内网 API（35 篇索引 + 公开进化摘要），随发布即时更新。
 */
interface ArtItem { id: number; title?: string; updatedAt?: string; publishedAt?: string }

export default defineEventHandler(async (event) => {
  const host = getRequestHost(event, { xForwardedHost: true })
  const proto = getRequestProtocol(event, { xForwardedProto: true })
  const origin = `${proto}://${host}`
  const apiBase = process.env.NUXT_API_INTERNAL || 'http://server:8080'

  const arts: ArtItem[] = []
  let summary: Record<string, unknown> = {}
  try {
    for (let page = 1; page <= 50; page++) {
      const res = await $fetch<{ code: number; data?: { records?: ArtItem[] } }>(
        `${apiBase}/api/articles?page=${page}&size=100`,
      )
      const records = res?.data?.records ?? []
      arts.push(...records)
      if (records.length < 100) break
    }
  } catch { /* 后端不可达时仍输出框架内容 */ }
  try {
    // 剥 Result 外壳（{code,message,data}）
    const sres = await $fetch<{ code: number; data?: Record<string, unknown> }>(
      `${apiBase}/api/evolution/public-summary`)
    summary = sres?.data ?? {}
  } catch { /* 摘要缺失不阻塞 */ }

  const lastMetrics = (summary.lastMetrics ?? {}) as Record<string, unknown>
  const fmt = (n: unknown) => (typeof n === 'number' ? String(n) : String(n ?? '-'))

  const lines: string[] = []
  lines.push('# InkPress')
  lines.push('')
  lines.push(`> 会自我进化的 AI 博客：Agent 从开发轨迹自动成文，GEO/SEO 全自动推流，读者的每一次反馈都让下一篇更好。当前语料 ${arts.length} 篇。`)
  lines.push('')
  lines.push(`本页面由服务端动态生成（${new Date().toISOString().slice(0, 10)}），与站点内容实时同步。`)
  lines.push('')
  lines.push('## 站点导航')
  lines.push('')
  lines.push('- [首页](/):最新文章与系统全景')
  lines.push('- [图谱](/graph):文章知识图谱')
  lines.push('- [检索](/search):BM25 + 向量 RRF 混合检索')
  lines.push('- [进化面板](/studio/evolution):自我进化指标与写作记忆')
  lines.push('')
  lines.push('## 文章索引')
  lines.push('')
  for (const a of arts) {
    const d = (a.updatedAt || a.publishedAt || '').slice(0, 10)
    lines.push(`- [${a.title}](${origin}/ai/article/${a.id})${d ? ` (${d})` : ''}`)
  }
  lines.push('')
  lines.push('## 自我进化（最近一次运行）')
  lines.push('')
  if (summary.lastMetrics) {
    lines.push(`- 采纳率/建议总数: ${fmt(lastMetrics.applied)}+${fmt(lastMetrics.verbatim)} / ${fmt(lastMetrics.suggestionsTotal)}`)
    lines.push(`- 平均修改幅度: ${typeof lastMetrics.avgEditDistanceBp === 'number' ? (lastMetrics.avgEditDistanceBp / 100).toFixed(1) + '%' : '-'}`)
    lines.push(`- 本轮学到记忆: ${fmt(lastMetrics.memoryLearned)} 条`)
  } else {
    lines.push('- 暂无完成的进化运行')
  }
  lines.push(`- 活跃写作记忆: ${fmt(summary.activeMemories)} 条`)
  lines.push(`- 已完成运行: ${fmt(summary.finishedRuns)} 轮`)
  lines.push('')
  lines.push('## 机器可读入口')
  lines.push('')
  lines.push(`- [llms-full.txt](${origin}/llms-full.txt):全部文章全文（markdown）`)
  lines.push(`- [ai/evolution](${origin}/ai/evolution):进化运行日志（逐轮指标）`)
  lines.push('- 单篇文章: /ai/article/<id>（纯 markdown）')
  lines.push('')
  lines.push('## 许可与引用')
  lines.push('')
  lines.push('- 内容可供 AI 搜索引擎与 LLM 引用；引用时请标注来源与原文链接。')

  setHeader(event, 'Content-Type', 'text/plain; charset=utf-8')
  setHeader(event, 'Cache-Control', 'public, max-age=60')
  return lines.join('\n')
})

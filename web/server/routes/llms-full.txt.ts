/**
 * /llms-full.txt：全部已发布文章全文（markdown），动态生成，300s 客户端缓存。
 */
interface ArtItem { id: number; title?: string; updatedAt?: string }

export default defineEventHandler(async (event) => {
  const host = getRequestHost(event, { xForwardedHost: true })
  const proto = getRequestProtocol(event, { xForwardedProto: true })
  const origin = `${proto}://${host}`
  const apiBase = process.env.NUXT_API_INTERNAL || 'http://server:8080'

  const arts: { id: number; title?: string; contentMd?: string; updatedAt?: string }[] = []
  try {
    for (let page = 1; page <= 50; page++) {
      const res = await $fetch<{ code: number; data?: { records?: { id: number; title?: string; contentMd?: string; updatedAt?: string }[] } }>(
        `${apiBase}/api/articles?page=${page}&size=100&fields=contentMd`,
      )
      const records = res?.data?.records ?? []
      arts.push(...records)
      if (records.length < 100) break
    }
  } catch { /* 后端不可达时输出空壳 */ }

  const lines: string[] = []
  lines.push('# InkPress 全部文章全文')
  lines.push('')
  lines.push(`> 共 ${arts.length} 篇，由服务端动态输出（${new Date().toISOString().slice(0, 10)}）。单篇入口见 /llms.txt 与 /ai/article/<id>。`)
  lines.push('')
  for (const a of arts) {
    lines.push(`---`)
    lines.push('')
    lines.push(`## ${a.title || '(无标题)'} [article ${a.id}]`)
    lines.push('')
    lines.push((a.contentMd || '').trim() || '(无内容)')
    lines.push('')
    lines.push(`原文: ${origin}/article/${a.id} | AI 版: ${origin}/ai/article/${a.id}`)
    lines.push('')
  }

  setHeader(event, 'Content-Type', 'text/plain; charset=utf-8')
  setHeader(event, 'Cache-Control', 'public, max-age=300')
  return lines.join('\n')
})

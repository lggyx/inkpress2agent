/**
 * /ai/article/:id（AI 线）：单篇纯 markdown 直出——标题 / 核心结论 / 正文 / 元数据。
 * 数据：内网公开接口实时读取（仅 PUBLISHED 可见）；ETag 随 updatedAt 变化。
 */
interface Detail {
  id: number; title?: string; contentMd?: string; geoSummary?: string;
  seoDescription?: string; categoryName?: string; tags?: string[];
  viewCount?: number; publishedAt?: string; updatedAt?: string;
}

export default defineEventHandler(async (event) => {
  const host = getRequestHost(event, { xForwardedHost: true })
  const proto = getRequestProtocol(event, { xForwardedProto: true })
  const origin = `${proto}://${host}`
  const apiBase = process.env.NUXT_API_INTERNAL || 'http://server:8080'
  const id = getRouterParam(event, 'id') ?? ''

  let a: Detail | null = null
  let etag = ''
  try {
    const res = await $fetch<{ code: number; data?: Detail }>(`${apiBase}/api/articles/${id}`)
    a = res?.data ?? null
    if (a) etag = `"ai-art-${a.id}-${(a.updatedAt || '').slice(0, 19)}"`
  } catch { /* 404 走下方 */ }

  if (!a || a.status !== 'PUBLISHED') {
    setHeader(event, 'Content-Type', 'text/plain; charset=utf-8')
    setResponseStatus(event, 404)
    return `article ${id} not found or not published`
  }

  // 条件请求：内容未变直接 304
  const inm = getHeader(event, 'if-none-match')
  if (etag && inm === etag) {
    setHeader(event, 'ETag', etag)
    setResponseStatus(event, 304)
    return ''
  }

  const lines: string[] = []
  lines.push(`# ${a.title || `(article ${a.id})`}`)
  lines.push('')
  if (a.geoSummary) {
    lines.push(`> 核心结论（GEO 摘要）：${a.geoSummary}`)
    lines.push('')
  }
  lines.push(`- 分类：${a.categoryName || '-'}`)
  lines.push(`- 标签：${(a.tags || []).join('、') || '-'}`)
  lines.push(`- 发布时间：${(a.publishedAt || '').slice(0, 16)}`)
  lines.push(`- 阅读量：${a.viewCount ?? 0}`)
  lines.push(`- 人类阅读版：${origin}/article/${a.id}`)
  lines.push('')
  lines.push('---')
  lines.push('')
  lines.push((a.contentMd || '').trim())

  setHeader(event, 'Content-Type', 'text/markdown; charset=utf-8')
  if (etag) setHeader(event, 'ETag', etag)
  setHeader(event, 'Cache-Control', 'public, max-age=60')
  return lines.join('\n')
})

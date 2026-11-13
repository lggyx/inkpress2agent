/**
 * sitemap.xml（动态生成，开题 4.5 / 论文 6.6）：
 * 首页 + 图谱页 + 全部已发布文章（含 lastmod）。
 * 文章数据经内网直连后端公开接口分页拉取（docker 网络内 server:8080），不引入新依赖。
 */
interface SitemapUrl { loc: string; lastmod?: string }

export default defineEventHandler(async (event) => {
  const host = getRequestHost(event, { xForwardedHost: true })
  const proto = getRequestProtocol(event, { xForwardedProto: true })
  const origin = `${proto}://${host}`
  const apiBase = process.env.NUXT_API_INTERNAL || 'http://server:8080'

  const urls: SitemapUrl[] = [{ loc: '/' }, { loc: '/graph' }]

  // 分页拉取全部已发布文章（上限 50 页兜底）；注意剥 Result 外壳（{code,message,data}）
  try {
    for (let page = 1; page <= 50; page++) {
      const res = await $fetch<{ code: number; data?: { records?: { id: number; updatedAt?: string; publishedAt?: string }[] } }>(
        `${apiBase}/api/articles?page=${page}&size=100`,
      )
      const records = res?.data?.records ?? []
      for (const a of records) {
        urls.push({
          loc: `/article/${a.id}`,
          lastmod: (a.updatedAt || a.publishedAt || '').slice(0, 10) || undefined,
        })
      }
      if (records.length < 100) break
    }
  } catch {
    // 后端不可达时仍输出静态页 URL，不让 sitemap 整体 500
  }

  const esc = (s: string) => s.replace(/&/g, '&amp;').replace(/</g, '&lt;')
  const body = urls
    .map((u) => {
      const lm = u.lastmod ? `<lastmod>${u.lastmod}</lastmod>` : ''
      return `  <url><loc>${origin}${esc(u.loc)}</loc>${lm}</url>`
    })
    .join('\n')
  const xml = `<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n${body}\n</urlset>`

  setHeader(event, 'Content-Type', 'application/xml; charset=utf-8')
  setHeader(event, 'Cache-Control', 'public, max-age=3600')
  return xml
})

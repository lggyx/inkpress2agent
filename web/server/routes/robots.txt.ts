/**
 * robots.txt（动态）：按请求 host 输出绝对 Sitemap 地址，兼容任意部署域名。
 * AI 线：显式欢迎主流 AI 爬虫 + 指路 llms.txt（给 LLM 的站点导读）。
 */
export default defineEventHandler((event) => {
  const host = getRequestHost(event, { xForwardedHost: true })
  const proto = getRequestProtocol(event, { xForwardedProto: true })
  setHeader(event, 'Content-Type', 'text/plain; charset=utf-8')
  setHeader(event, 'Cache-Control', 'public, max-age=86400')
  return [
    '# InkPress — AI-friendly blog (see /llms.txt for LLM-readable index)',
    '',
    'User-agent: *',
    'Allow: /',
    'Disallow: /studio/',
    'Disallow: /admin/',
    '',
    'User-agent: GPTBot',
    'Allow: /',
    '',
    'User-agent: ClaudeBot',
    'Allow: /',
    '',
    'User-agent: PerplexityBot',
    'Allow: /',
    '',
    'User-agent: Google-Extended',
    'Allow: /',
    '',
    `Sitemap: ${proto}://${host}/sitemap.xml`,
    `LLM-Index: ${proto}://${host}/llms.txt`,
    '',
  ].join('\n')
})

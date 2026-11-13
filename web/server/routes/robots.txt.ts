/**
 * robots.txt（动态）：按请求 host 输出绝对 Sitemap 地址，兼容任意部署域名（局域网 IP / 公网域名）。
 */
export default defineEventHandler((event) => {
  const host = getRequestHost(event, { xForwardedHost: true })
  const proto = getRequestProtocol(event, { xForwardedProto: true })
  setHeader(event, 'Content-Type', 'text/plain; charset=utf-8')
  setHeader(event, 'Cache-Control', 'public, max-age=86400')
  return `User-agent: *\nAllow: /\n\nSitemap: ${proto}://${host}/sitemap.xml\n`
})

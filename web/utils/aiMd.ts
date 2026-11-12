/**
 * AI 输出 Markdown 渲染：marked（GFM+换行转 <br>）→ DOMPurify 净化 → 安全 HTML。
 * 服务端无 window 时退化为纯文本转义（AI 气泡均在客户端挂载后才有内容，不影响 SSR）。
 */
import { marked } from 'marked'
import DOMPurify from 'dompurify'

marked.setOptions({ breaks: true, gfm: true })

function escapeHtml(s: string): string {
  return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}

export function renderAiMd(md: string): string {
  if (!md) return ''
  if (import.meta.server) return `<p>${escapeHtml(md)}</p>`
  const html = marked.parse(md, { async: false }) as string
  return DOMPurify.sanitize(html, { ADD_ATTR: ['target'] })
}

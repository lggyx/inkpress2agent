import { marked } from 'marked'
import DOMPurify from 'dompurify'
import hljs from 'highlight.js'

marked.setOptions({ gfm: true, breaks: true })

/**
 * Markdown 渲染：marked 转换 + DOMPurify 白名单净化，防脚本注入
 */
export function renderMarkdown(content) {
  const html = marked.parse(content || '')
  return DOMPurify.sanitize(html, { ADD_ATTR: ['target'] })
}

/** 对容器内的代码块做高亮 */
export function highlightIn(el) {
  el.querySelectorAll('pre code').forEach(block => {
    try {
      hljs.highlightElement(block)
    } catch {
      /* 高亮失败不影响正文展示 */
    }
  })
}

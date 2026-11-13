/**
 * 数学公式渲染（$行内$ / $$块级$$）——KaTeX auto-render，仅客户端执行。
 *
 * 安全边界（重要）：
 * 1. 仅在浏览器运行（import.meta.server 直接返回），不参与 SSR 输出；
 * 2. auto-render 只遍历文本节点，KaTeX 对全部输入做转义，输出中不含可执行脚本，
 *    用户内容不可能通过公式渲染注入 HTML（KaTeX 的 \htmlClass 等指令也只产生属性）；
 * 3. 调用顺序固定为「先净化、后渲染公式」：
 *    - 文章页：服务端 commonmark escapeHtml（原始 HTML 已被转义为文本）→ v-html → 本工具；
 *    - AI 组件：marked → DOMPurify.sanitize → v-html → 本工具；
 *    公式渲染永远不会把未净化的内容插入 DOM；
 * 4. pre / code / textarea 内的 $ 按代码字面量处理（ignoredTags），
 *    代码块中的价格符号（$5）不会被误渲染；
 * 5. KaTeX 对非法公式不抛异常中断页面（throwOnError: false），失败时保留原文。
 */
import katex from 'katex'
import 'katex/dist/katex.min.css'
import renderMathInElement from 'katex/dist/contrib/auto-render'

/** 金额模式：$5 / $1,299.00（$ 后紧跟数字） */
const CURRENCY_RE = /\$\d[\d,]*(?:\.\d+)?/g

/**
 * 货币符号保护：KaTeX auto-render 对成对 $ 一律当公式（无空白启发式），
 * 会把「售价 $5 和 $10」误判为行内公式。渲染前把金额包进 .no-math span，
 * auto-render 的 ignoredClasses 原生跳过，显示文本不变。
 */
function protectCurrency(root: HTMLElement): void {
  const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT)
  const nodes: Text[] = []
  while (walker.nextNode()) {
    const n = walker.currentNode as Text
    if (n.parentElement?.classList.contains('no-math')) continue // 幂等：已保护的不重复包
    CURRENCY_RE.lastIndex = 0
    if (CURRENCY_RE.test(n.data)) nodes.push(n)
  }
  for (const n of nodes) {
    const text = n.data
    const frag = document.createDocumentFragment()
    let last = 0
    CURRENCY_RE.lastIndex = 0
    let m: RegExpExecArray | null
    while ((m = CURRENCY_RE.exec(text)) !== null) {
      if (m.index > last) frag.appendChild(document.createTextNode(text.slice(last, m.index)))
      const span = document.createElement('span')
      span.className = 'no-math'
      span.textContent = m[0]
      frag.appendChild(span)
      last = m.index + m[0].length
    }
    if (last > 0) {
      if (last < text.length) frag.appendChild(document.createTextNode(text.slice(last)))
      n.parentNode?.replaceChild(frag, n)
    }
  }
}

/** 对容器内已净化的 HTML 执行公式渲染（原地替换文本节点） */
export function renderMath(el: HTMLElement | null | undefined): void {
  if (!el || import.meta.server) return
  try {
    protectCurrency(el)
    renderMathInElement(el, {
      delimiters: [
        { left: '$$', right: '$$', display: true },
        { left: '$', right: '$', display: false },
      ],
      ignoredTags: ['script', 'noscript', 'style', 'textarea', 'pre', 'code', 'option'],
      ignoredClasses: ['no-math', 'katex'],
      throwOnError: false,
      strict: false,
    })
  } catch {
    /* 渲染失败保留原文，不影响正文展示 */
  }
}

/** 导出 katex 本体，便于测试或后续扩展（如服务端渲染场景） */
export { katex }

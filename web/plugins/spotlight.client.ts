/**
 * 全站聚光灯（Aceternity UI 招牌效果）：
 * 监听鼠标移动，把光标位置写入可视范围内每张 .cc 颜色卡的 --mx/--my CSS 变量，
 * 卡片上的径向光斑（.cc::after）随之跟随。rAF 节流，仅 client。
 */
export default defineNuxtPlugin(() => {
  if (!process.client) return
  let raf = 0
  const apply = (e: MouseEvent) => {
    raf = 0
    const cards = document.querySelectorAll<HTMLElement>('.cc')
    for (let i = 0; i < cards.length; i++) {
      const el = cards[i]
      const r = el.getBoundingClientRect()
      // 视口外或鼠标远离 120px 的卡片跳过，避免全页重算
      if (e.clientX < r.left - 120 || e.clientX > r.right + 120 ||
          e.clientY < r.top - 120 || e.clientY > r.bottom + 120) continue
      el.style.setProperty('--mx', e.clientX - r.left + 'px')
      el.style.setProperty('--my', e.clientY - r.top + 'px')
    }
  }
  window.addEventListener('mousemove', (e: MouseEvent) => {
    if (!raf) raf = requestAnimationFrame(() => apply(e))
  }, { passive: true })
})

<script setup lang="ts">
// 首次打开的「协作光标重建页面」开场动画。
// 效果概念受 califa/swarm 与 DhvanilPatel/swarm-cursors 启发；两者均未提供开源许可，
// 因此这里是基于公版 boids 群聚算法（Craig Reynolds, 1986）的原创实现，未复制其任何代码。
// 行为：本会话首次进入首页时播放一次（sessionStorage 记忆）；跳过条件：
// prefers-reduced-motion / 窄屏 / 动画中点击·滚轮·Esc。
const SKIP_KEY = 'inkpress.swarm-intro'

interface Agent {
  name: string
  color: string
  x: number
  y: number
  vx: number
  vy: number
  task: Rect | null
  startedAt: number
}
interface Rect { x: number; y: number; w: number; h: number; done: boolean; flash: number; claimed: boolean }

const NAMES = ['墨墨', '排版员', '校对', 'AI 助手', '索引器', '检索员', '审稿人', '分发员', '画师', '书记']
const PALETTE = ['#f28b82', '#f8bb66', '#81c995', '#8ab4f8', '#b7a4f8', '#d9f24f', '#5bc8d4', '#ef9fd0']

onMounted(() => {
  try {
    if (sessionStorage.getItem(SKIP_KEY)) return
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return
    if (window.innerWidth < 720) return
    sessionStorage.setItem(SKIP_KEY, '1')
    requestAnimationFrame(() => run())
  } catch { /* 任何异常都不影响正常页面 */ }
})

function run() {
  const css = getComputedStyle(document.documentElement)
  const bgColor = css.getPropertyValue('--bg').trim() || '#0b0e14'
  const inkColor = css.getPropertyValue('--ink').trim() || '#e8eaed'
  const lineColor = css.getPropertyValue('--line').trim() || 'rgba(255,255,255,.1)'

  const canvas = document.createElement('canvas')
  canvas.style.cssText = 'position:fixed;inset:0;z-index:9999;pointer-events:none;'
  document.body.appendChild(canvas)
  const ctx = canvas.getContext('2d')!
  const dpr = Math.min(window.devicePixelRatio || 1, 2)

  // 锁定滚动：构建期间由动画接管页面滚动
  const prevOverflow = document.body.style.overflow
  document.body.style.overflow = 'hidden'
  window.scrollTo(0, 0)

  // 收集真实内容块（文档坐标）
  const selectors = ['.hero', '.section-row', '.hero-cta', '.post-row', '.hot-row', '.card', '.pager']
  const seen: Rect[] = []
  for (const sel of selectors) {
    document.querySelectorAll(sel).forEach((el) => {
      const r = (el as HTMLElement).getBoundingClientRect()
      const x = r.left + window.scrollX
      const y = r.top + window.scrollY
      if (r.width < 60 || r.height < 26) return
      // 与已收块重叠过多则跳过（父子块去重）
      if (seen.some((s) => {
        const ix = Math.max(0, Math.min(s.x + s.w, x + r.width) - Math.max(s.x, x))
        const iy = Math.max(0, Math.min(s.y + s.h, y + r.height) - Math.max(s.y, y))
        return ix * iy > 0.6 * r.width * r.height
      })) return
      seen.push({ x, y, w: r.width, h: r.height, done: false, flash: 0, claimed: false })
    })
  }
  const tasks = seen.sort((a, b) => a.y - b.y).slice(0, 26)

  let W = window.innerWidth
  let H = window.innerHeight
  const resize = () => {
    W = window.innerWidth
    H = window.innerHeight
    canvas.width = W * dpr
    canvas.height = H * dpr
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
  }
  resize()
  window.addEventListener('resize', resize)

  const agents: Agent[] = Array.from({ length: 12 }, (_, i) => ({
    name: NAMES[i % NAMES.length],
    color: PALETTE[i % PALETTE.length],
    x: W / 2 + Math.cos(i / 12 * Math.PI * 2) * 120,
    y: H * 0.32 + Math.sin(i / 12 * Math.PI * 2) * 70,
    vx: 0,
    vy: 0,
    task: null,
    startedAt: 0,
  }))

  const ORBIT_MS = 900
  const FRAME_MS = 420   // 单块「框选施工」耗时
  const t0 = performance.now()
  let finished = false
  let mouse: { x: number; y: number } | null = null
  let scrollLerp: number | null = null
  let cursorAlpha = 1

  const onMouse = (e: MouseEvent) => { mouse = { x: e.clientX, y: e.clientY } }
  const finish = () => { finished = true }
  const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') finish() }
  window.addEventListener('mousemove', onMouse, { passive: true })
  window.addEventListener('pointerdown', finish, { once: false })
  window.addEventListener('wheel', finish, { passive: true })
  window.addEventListener('keydown', onKey)

  function cleanup() {
    window.removeEventListener('mousemove', onMouse)
    window.removeEventListener('pointerdown', finish)
    window.removeEventListener('wheel', finish)
    window.removeEventListener('keydown', onKey)
    window.removeEventListener('resize', resize)
    document.body.style.overflow = prevOverflow
    canvas.remove()
  }

  function easeOut(t: number): number {
    return 1 - Math.pow(1 - t, 3)
  }

  function assignTasks(now: number) {
    for (const a of agents) {
      if (a.task && a.task.done) a.task = null
      if (a.task) {
        // 框选施工进行中：耗时到点即揭示该块
        if (now - a.startedAt >= FRAME_MS) {
          a.task.done = true
          a.task.flash = now
          a.task.claimed = false
          a.task = null
        }
        continue
      }
      const next = tasks.find((t) => !t.done && !t.claimed)
      if (!next) continue
      next.claimed = true
      a.task = next
      a.startedAt = now
    }
  }

  let lastFrame = performance.now()
  function frame(now: number) {
    if (finished) {
      cleanup()
      return
    }
    const t = now - t0
    const dt = Math.min(0.05, Math.max(0.008, (now - lastFrame) / 1000))
    lastFrame = now
    const vy = window.scrollY

    assignTasks(now)
    const allDone = tasks.every((t2) => t2.done)
    const phase = t < ORBIT_MS ? 'orbit' : allDone ? 'converge' : 'build'

    // 构建期自动滚动：把当前任务块滚进视野
    if (phase === 'build') {
      const active = agents.find((a) => a.task)
      if (active?.task) {
        const targetTop = Math.max(0, active.task.y - H * 0.32)
        if (Math.abs(targetTop - vy) > 8) scrollLerp = targetTop
      }
    }
    if (scrollLerp !== null) {
      const nv = vy + (scrollLerp - vy) * Math.min(1, dt * 6)
      window.scrollTo(0, nv)
      if (Math.abs(scrollLerp - nv) < 2) scrollLerp = null
    }

    // 物理步进
    for (const a of agents) {
      let tx: number
      let ty: number
      if (phase === 'orbit') {
        const idx = agents.indexOf(a)
        const ang = idx / agents.length * Math.PI * 2 + t / 700
        tx = W / 2 + Math.cos(ang) * Math.min(170, W * 0.18)
        ty = H * 0.3 + Math.sin(ang) * 66
      } else if (phase === 'build' && a.task) {
        tx = a.task.x - vy + Math.min(a.task.w * 0.6, 130)
        ty = a.task.y - vy + a.task.h * 0.5
      } else {
        const m = mouse || { x: W / 2, y: H * 0.45 }
        tx = m.x + (agents.indexOf(a) - agents.length / 2) * 8
        ty = m.y + (agents.indexOf(a) - agents.length / 2) * 5
      }
      a.vx += (tx - a.x) * 8 * dt
      a.vy += (ty - a.y) * 8 * dt
      // 分离力：靠太近互相推开
      for (const b of agents) {
        if (b === a) continue
        const dx = a.x - b.x
        const dy = a.y - b.y
        const d2 = dx * dx + dy * dy
        if (d2 > 0 && d2 < 26 * 26) {
          const d = Math.sqrt(d2)
          a.vx += (dx / d) * 60 * dt
          a.vy += (dy / d) * 60 * dt
        }
      }
      const sp = Math.hypot(a.vx, a.vy)
      const maxSp = phase === 'orbit' ? 260 : 480
      if (sp > maxSp) {
        a.vx = a.vx / sp * maxSp
        a.vy = a.vy / sp * maxSp
      }
      a.x += a.vx * dt
      a.y += a.vy * dt
    }

    // 绘制：底色覆盖 → 已揭示块擦除 → 任务框选 → 光标群
    ctx.clearRect(0, 0, W, H)
    ctx.fillStyle = bgColor
    ctx.fillRect(0, 0, W, H)
    for (const t2 of tasks) {
      if (!t2.done) continue
      const alpha = Math.min(1, (now - t2.flash) / 260)
      ctx.save()
      ctx.globalAlpha = alpha
      ctx.clearRect(t2.x - vy + 1, t2.y - vy + 1, t2.w - 2, t2.h - 2)
      ctx.restore()
    }
    for (const a of agents) {
      if (phase !== 'build' || !a.task) continue
      const p = Math.min(1, (now - a.startedAt) / FRAME_MS)
      const e = easeOut(p)
      const rx = a.task.x - vy
      const ry = a.task.y - vy
      ctx.strokeStyle = a.color
      ctx.lineWidth = 1.5
      ctx.setLineDash([5, 4])
      ctx.strokeRect(rx, ry, a.task.w * e, a.task.h * e)
      ctx.setLineDash([])
      // Figma 式尺寸标注
      const label = `${Math.round(a.task.w)} × ${Math.round(a.task.h)}`
      ctx.font = '11px ui-monospace, monospace'
      const tw = ctx.measureText(label).width
      ctx.fillStyle = a.color
      ctx.fillRect(rx + a.task.w * e - tw - 12, ry + a.task.h * e - 22, tw + 10, 17)
      ctx.fillStyle = '#101012'
      ctx.fillText(label, rx + a.task.w * e - tw - 7, ry + a.task.h * e - 9)
    }
    // 光标群淡出：收尾阶段光标聚向鼠标并渐隐（页面已完整揭示）
    if (phase === 'converge' && t > ORBIT_MS + 500) {
      cursorAlpha = Math.max(0, 1 - (t - ORBIT_MS - 500) / 450)
    }
    ctx.save()
    ctx.globalAlpha = cursorAlpha
    for (const a of agents) drawCursor(a)
    ctx.restore()
    if (phase === 'converge' && cursorAlpha <= 0) {
      cleanup()
      return
    }
    requestAnimationFrame(frame)
  }

  function drawCursor(a: Agent) {
    ctx.save()
    ctx.translate(a.x, a.y)
    // 指针箭头
    ctx.beginPath()
    ctx.moveTo(0, 0)
    ctx.lineTo(0, 15.5)
    ctx.lineTo(4, 12)
    ctx.lineTo(7, 18.5)
    ctx.lineTo(9.2, 17.5)
    ctx.lineTo(6.2, 11.4)
    ctx.lineTo(11, 11.2)
    ctx.closePath()
    ctx.fillStyle = a.color
    ctx.strokeStyle = 'rgba(255,255,255,.75)'
    ctx.lineWidth = 1
    ctx.fill()
    ctx.stroke()
    // 名牌
    const label = a.name
    ctx.font = '10px -apple-system, "PingFang SC", sans-serif'
    const tw = ctx.measureText(label).width
    ctx.fillStyle = a.color
    ctx.beginPath()
    ctx.roundRect(11, 14, tw + 12, 17, 6)
    ctx.fill()
    ctx.fillStyle = '#101012'
    ctx.fillText(label, 17, 26)
    ctx.restore()
  }

  requestAnimationFrame(frame)
}
</script>

<template>
  <div aria-hidden="true" style="display:contents" />
</template>

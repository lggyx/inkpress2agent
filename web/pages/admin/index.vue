<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { $api } from '~/composables/useApi'
import { useTheme } from '~/composables/useTheme'

// 管理端仪表盘：一屏总览内容、流量与系统状态
interface TopArticle { articleId: number; cnt: number; title?: string }
interface DailyRow { day: string; cnt: number }

const { theme: themeRef } = useTheme()

const loading = ref(true)
const pv = ref(0)
const uv = ref(0)
const aTotal = ref(0)
const aPublished = ref(0)
const pending = ref(0)
const dlqCount = ref(0)
const aiOk = ref(0)
const aiTotalSlots = 4
const daily = ref<DailyRow[]>([])
const topArticles = ref<TopArticle[]>([])
const outboundTop = ref<{ host?: string; platform?: string; cnt: number }[]>([])
const interaction = ref<{ likes: number; comments: number; published: number }>({ likes: 0, comments: 0, published: 0 })

const today = new Date().toLocaleDateString('zh-CN', { month: 'long', day: 'numeric', weekday: 'long' })

const pvChart = ref<HTMLElement | null>(null)
let chart: any = null
let chartReady = false

function axisColor() {
  return themeRef.value === 'dark' ? '#6b6b72' : '#8a8a90'
}

async function renderChart() {
  if (!pvChart.value) return
  const echarts = await import('echarts')
  if (!chart) chart = echarts.init(pvChart.value)
  // 补齐缺失日（访问为 0 的天也要有柱）
  const map = new Map(daily.value.map((d) => [String(d.day).slice(0, 10), d.cnt]))
  const days: string[] = []
  const vals: number[] = []
  for (let i = 6; i >= 0; i--) {
    const dt = new Date(Date.now() - i * 86400000)
    const key = dt.toISOString().slice(0, 10)
    days.push(key.slice(5).replace('-', '/'))
    vals.push(Number(map.get(key) || 0))
  }
  const lime = '#a3e635'
  chart.setOption({
    grid: { left: 8, right: 8, top: 18, bottom: 4, containLabel: true },
    tooltip: { trigger: 'axis' },
    xAxis: {
      type: 'category', data: days, boundaryGap: false,
      axisLine: { lineStyle: { color: axisColor() } },
      axisLabel: { color: axisColor(), fontSize: 11 },
      axisTick: { show: false },
    },
    yAxis: {
      type: 'value', minInterval: 1,
      splitLine: { lineStyle: { color: themeRef.value === 'dark' ? 'rgba(255,255,255,.05)' : 'rgba(0,0,0,.05)' } },
      axisLabel: { color: axisColor(), fontSize: 11 },
    },
    series: [{
      type: 'line', data: vals, smooth: true, symbol: 'circle', symbolSize: 6,
      lineStyle: { color: lime, width: 2.5 },
      itemStyle: { color: lime, borderColor: themeRef.value === 'dark' ? '#0c0c0e' : '#fff', borderWidth: 2 },
      areaStyle: {
        color: {
          type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
          colorStops: [
            { offset: 0, color: 'rgba(217,242,79,.28)' },
            { offset: 1, color: 'rgba(217,242,79,0)' },
          ],
        },
      },
    }],
  })
  chartReady = true
}

async function load() {
  loading.value = true
  const [ov, dlq, pend, artAll, artPub, ai] = await Promise.allSettled([
    $api<any>('GET', '/admin/stats/overview?days=7'),
    $api<{ count: number }>('GET', '/admin/mail/dlq/count'),
    $api<any[]>('GET', '/studio/comments/pending'),
    $api<{ total: number }>('GET', '/studio/articles?page=1&size=1'),
    $api<{ total: number }>('GET', '/studio/articles?page=1&size=1&status=PUBLISHED'),
    $api<any>('GET', '/admin/ai-config'),
  ])
  if (ov.status === 'fulfilled') {
    pv.value = Number(ov.value.pv || 0)
    uv.value = Number(ov.value.uv || 0)
    daily.value = ov.value.dailyPv || []
    topArticles.value = ov.value.topArticles || []
    outboundTop.value = ov.value.outboundTop || []
    const it = ov.value.interaction || {}
    interaction.value = {
      likes: Number(it.likes || 0),
      comments: Number(it.comments || 0),
      published: Number(it.published || 0),
    }
  } else {
    ElMessage.error((ov.reason as Error).message)
  }
  if (dlq.status === 'fulfilled') dlqCount.value = Number(dlq.value.count || 0)
  if (pend.status === 'fulfilled') pending.value = (pend.value || []).length
  if (artAll.status === 'fulfilled') aTotal.value = Number(artAll.value.total || 0)
  if (artPub.status === 'fulfilled') aPublished.value = Number(artPub.value.total || 0)
  if (ai.status === 'fulfilled') {
    const caps = ai.value?.capabilities || {}
    aiOk.value = Object.values(caps).filter((c: any) => c?.configured).length
  }
  loading.value = false
  nextTick(renderChart)
}

function onResize() { if (chartReady && chart) chart.resize() }

/** 互动构成条宽：以三项最大值为基准的百分比 */
function barW(v: number): string {
  const max = Math.max(interaction.value.likes, interaction.value.comments, interaction.value.published, 1)
  return Math.max(6, Math.round((v / max) * 100)) + '%'
}

onMounted(async () => {
  await load()
  window.addEventListener('resize', onResize)
})
onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  chart?.dispose?.()
  chart = null
})

watch(themeRef, () => { if (chartReady) renderChart() })
</script>

<template>
  <div class="page dash">
    <div class="page-head">
      <div>
        <h2>仪表盘</h2>
        <span class="sub">{{ today }} · 站点运行一屏总览</span>
      </div>
      <div class="head-actions">
        <NuxtLink to="/admin/stats" class="btn ghost">完整画像 →</NuxtLink>
      </div>
    </div>

    <!-- 统计卡 -->
    <div class="stat-grid" v-loading="loading">
      <div class="stat cc cc-violet beam">
        <div class="s-icon">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round"><path d="M3 12h4l2.5-7 5 14 2.5-7H21" /></svg>
        </div>
        <div class="s-body">
          <div class="s-label">访问量 · 7 天</div>
          <div class="s-num mono grad-text">{{ pv }}</div>
          <div class="s-sub">独立访客 {{ uv }}</div>
        </div>
      </div>
      <div class="stat cc cc-cyan beam">
        <div class="s-icon">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><rect x="4.5" y="3" width="15" height="18" rx="2.5" /><path d="M8.5 8h7M8.5 12h7M8.5 16h4.5" /></svg>
        </div>
        <div class="s-body">
          <div class="s-label">文章</div>
          <div class="s-num mono">{{ aTotal }}</div>
          <div class="s-sub">已发布 {{ aPublished }} · 草稿等 {{ aTotal - aPublished }}</div>
        </div>
      </div>
      <div class="stat cc cc-orange beam">
        <div class="s-icon warn">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><path d="M20 11.5a7.5 7.5 0 0 1-7.5 7.5c-1.2 0-2.4-.3-3.4-.8L4 19.5l1.3-4.1A7.5 7.5 0 1 1 20 11.5z" /></svg>
        </div>
        <div class="s-body">
          <div class="s-label">待审评论</div>
          <div class="s-num mono" :class="{ alert: pending > 0 }">{{ pending }}</div>
          <NuxtLink to="/studio/comments" class="s-link">去处理 →</NuxtLink>
        </div>
      </div>
      <div class="stat cc cc-pink beam">
        <div class="s-icon" :class="{ warn: dlqCount > 0 }">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><path d="M3.5 13.5L6 6.5A2 2 0 0 1 7.9 5h8.2a2 2 0 0 1 1.9 1.5l2.5 7v4a1.5 1.5 0 0 1-1.5 1.5H5a1.5 1.5 0 0 1-1.5-1.5v-4z" /><path d="M3.5 13.5h5a3.5 3.5 0 0 0 7 0h5" /></svg>
        </div>
        <div class="s-body">
          <div class="s-label">死信积压</div>
          <div class="s-num mono" :class="{ alert: dlqCount > 0 }">{{ dlqCount }}</div>
          <NuxtLink to="/admin/dlq" class="s-link">去查看 →</NuxtLink>
        </div>
      </div>
    </div>

    <!-- 趋势 + 热读 -->
    <div class="dash-grid">
      <div class="panel-card">
        <div class="c-head">访问趋势 · 近 7 天</div>
        <div ref="pvChart" class="chart" />
      </div>
      <div class="panel-card">
        <div class="c-head">热读文章 Top5 · 7 天</div>
        <div class="top-list">
          <div v-for="(a, i) in topArticles" :key="a.articleId" class="top-row">
            <span class="rank mono" :class="{ hot: i === 0 }">{{ i + 1 }}</span>
            <NuxtLink :to="`/article/${a.articleId}`" target="_blank" class="t-title">{{ a.title || '(已删除)' }}</NuxtLink>
            <span class="mono t-cnt">{{ a.cnt }} 次</span>
          </div>
          <div v-if="!topArticles.length" class="muted" style="padding:14px 4px">暂无访问数据</div>
        </div>
      </div>
    </div>

    <!-- 系统状态 + 出站 -->
    <div class="dash-grid2">
      <div class="panel-card">
        <div class="c-head">系统状态</div>
        <div class="sys-list">
          <div class="sys-row">
            <span>AI 能力槽</span>
            <span class="mono">{{ aiOk }} / {{ aiTotalSlots }} 已配置</span>
            <NuxtLink to="/admin/ai" class="abtn">配置</NuxtLink>
          </div>
          <div class="sys-row">
            <span>死信队列</span>
            <span class="mono" :class="{ 'alert-text': dlqCount > 0 }">{{ dlqCount > 0 ? '积压 ' + dlqCount + ' 条' : '畅通' }}</span>
            <NuxtLink to="/admin/dlq" class="abtn">查看</NuxtLink>
          </div>
          <div class="sys-row">
            <span>评论审核</span>
            <span class="mono">{{ pending > 0 ? '待审 ' + pending + ' 条' : '无待审' }}</span>
            <NuxtLink to="/studio/comments" class="abtn">处理</NuxtLink>
          </div>
          <div class="sys-row">
            <span>每周精选</span>
            <span class="mono">手动触发投递</span>
            <NuxtLink to="/admin/stats" class="abtn">画像</NuxtLink>
          </div>
        </div>
      </div>
      <div class="panel-card">
        <div class="c-head">出站点击 Top · 7 天</div>
        <div class="out-list">
          <div v-for="(o, i) in outboundTop" :key="i" class="out-row">
            <span class="mono o-host">{{ o.host || o.platform || '—' }}</span>
            <span class="mono t-cnt">{{ o.cnt }}</span>
          </div>
          <div v-if="!outboundTop.length" class="muted" style="padding:14px 4px">暂无出站记录</div>
        </div>
      </div>

      <!-- 互动构成（开题模块六）：全站累计点赞/评论/文章 -->
      <div class="panel-card int-card">
        <div class="c-head">互动构成 · 全站累计</div>
        <div class="int-list">
          <div class="int-row">
            <span class="int-label">点赞</span>
            <div class="int-bar"><i :style="{ width: barW(interaction.likes) }" /></div>
            <span class="mono int-num">{{ interaction.likes }}</span>
          </div>
          <div class="int-row">
            <span class="int-label">评论</span>
            <div class="int-bar"><i :style="{ width: barW(interaction.comments) }" /></div>
            <span class="mono int-num">{{ interaction.comments }}</span>
          </div>
          <div class="int-row">
            <span class="int-label">已发布文章</span>
            <div class="int-bar"><i :style="{ width: barW(interaction.published) }" /></div>
            <span class="mono int-num">{{ interaction.published }}</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.dash { padding-bottom: 40px; }

/* 统计卡 */
.stat-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  margin-bottom: 14px;
}
.stat {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  padding: 16px;
  border: 1px solid var(--sline);
  border-radius: 14px;
  background: var(--scard);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  transition: transform .2s cubic-bezier(.2, .7, .3, 1), border-color .2s ease, box-shadow .2s ease;
  animation: s-row-in .3s cubic-bezier(.2, .7, .3, 1) both;
}
.stat:nth-child(2) { animation-delay: .05s; }
.stat:nth-child(3) { animation-delay: .1s; }
.stat:nth-child(4) { animation-delay: .15s; }
.stat:hover { transform: translateY(-2px); border-color: rgba(217, 242, 79, .35); }
.s-icon {
  width: 38px; height: 38px;
  flex-shrink: 0;
  display: grid; place-items: center;
  border-radius: 11px;
  color: var(--sprimary);
  background: rgba(217, 242, 79, .09);
}
.s-icon.warn { color: #f8bb66; background: rgba(248, 187, 102, .1); }
.s-icon svg { width: 19px; height: 19px; }
.s-body { min-width: 0; }
.s-label { font-size: .76em; color: var(--ink-faint); letter-spacing: .5px; }
.s-num { font-size: 1.65em; font-weight: 750; line-height: 1.25; }
.s-num.alert { color: #f8bb66; }
.s-sub { font-size: .76em; color: var(--ink-faint); }
.s-link { font-size: .76em; color: var(--primary); }

/* 面板卡 */
.dash-grid { display: grid; grid-template-columns: 1.7fr 1fr; gap: 12px; margin-bottom: 12px; }
.dash-grid2 { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.panel-card {
  border: 1px solid var(--sline);
  border-radius: 14px;
  background: var(--scard);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  padding: 16px 18px;
  min-width: 0;
}
.c-head { font-size: .8em; font-weight: 700; letter-spacing: 1.5px; color: var(--ink-faint); margin-bottom: 10px; }
.chart { height: 240px; width: 100%; }

/* Top5 */
.top-list { display: flex; flex-direction: column; }
.top-row {
  display: flex; align-items: center; gap: 10px;
  padding: 9px 2px;
  border-bottom: 1px solid var(--sline);
}
.top-row:last-child { border-bottom: 0; }
.rank {
  width: 22px; height: 22px;
  flex-shrink: 0;
  display: grid; place-items: center;
  border-radius: 7px;
  font-size: .78em;
  color: var(--ink-faint);
  background: rgba(128, 128, 128, .1);
}
.rank.hot { color: #101012; background: var(--sprimary); font-weight: 700; }
.t-title {
  flex: 1; min-width: 0;
  color: var(--ink);
  font-size: .9em;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.t-title:hover { color: var(--primary); }
.t-cnt { font-size: .78em; color: var(--ink-faint); white-space: nowrap; }

/* 系统状态 */
.sys-list { display: flex; flex-direction: column; }
.sys-row {
  display: flex; align-items: center; gap: 12px;
  padding: 10px 2px;
  border-bottom: 1px solid var(--sline);
  font-size: .9em;
}
.sys-row:last-child { border-bottom: 0; }
.sys-row > span:first-child { flex: 1; color: var(--ink-soft); }
.sys-row .mono { color: var(--ink-faint); font-size: .88em; }
.sys-row .mono.alert-text { color: #f8bb66; }
.sys-row .abtn { margin-left: auto; }

/* 出站 */
.out-list { display: flex; flex-direction: column; }
.out-row {
  display: flex; align-items: center; justify-content: space-between; gap: 10px;
  padding: 9px 2px;
  border-bottom: 1px solid var(--sline);
}
.out-row:last-child { border-bottom: 0; }
.o-host { color: var(--ink-soft); font-size: .85em; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

/* 互动构成 */
.int-row { display: flex; align-items: center; gap: 12px; padding: 9px 0; }
.int-label { width: 88px; flex-shrink: 0; color: var(--ink-soft); font-size: .86em; }
.int-bar {
  flex: 1;
  height: 10px;
  border-radius: 999px;
  background: rgba(128, 128, 128, .14);
  overflow: hidden;
}
.int-bar i {
  display: block;
  height: 100%;
  border-radius: 999px;
  background: linear-gradient(90deg, var(--sprimary-deep), var(--sprimary));
  transition: width .4s cubic-bezier(.2, .7, .3, 1);
}
.int-num { width: 44px; text-align: right; color: var(--ink); font-size: .85em; }

@media (max-width: 1000px) {
  .stat-grid { grid-template-columns: 1fr 1fr; }
  .dash-grid, .dash-grid2 { grid-template-columns: 1fr; }
}
</style>

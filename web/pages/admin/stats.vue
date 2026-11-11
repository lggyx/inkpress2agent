<script setup lang="ts">
interface Overview {
  days: number
  pv: number
  uv: number
  regions: { region: string; cnt: number }[]
  devices: { device: string; cnt: number }[]
  dailyPv: { day: string; cnt: number }[]
  topArticles: { articleId: number; title: string; cnt: number }[]
  outboundTop: { host: string; cnt: number }[]
}

const days = ref(7)
const data = ref<Overview | null>(null)
const pvBox = ref<HTMLElement | null>(null)
const regionBox = ref<HTMLElement | null>(null)

async function load() {
  const resp = await fetch(`/api/admin/stats/overview?days=${days.value}`, {
    headers: { Authorization: 'Bearer ' + (localStorage.getItem('inkpress.access') ?? '') },
  })
  const json = await resp.json()
  if (json.code === 0) {
    data.value = json.data
    render()
  }
}
onMounted(load)

async function render() {
  if (!data.value) return
  const echarts = await import('echarts')
  if (pvBox.value) {
    echarts.init(pvBox.value).setOption({
      backgroundColor: 'transparent',
      tooltip: { trigger: 'axis' },
      xAxis: { type: 'category', data: data.value.dailyPv.map((d) => d.day), axisLabel: { color: '#9aa3af' } },
      yAxis: { type: 'value', axisLabel: { color: '#9aa3af' }, splitLine: { lineStyle: { color: '#242a36' } } },
      series: [{ type: 'line', data: data.value.dailyPv.map((d) => d.cnt), smooth: true, areaStyle: { opacity: .18 }, lineStyle: { color: '#818cf8' }, itemStyle: { color: '#818cf8' } }],
      grid: { left: 40, right: 20, top: 20, bottom: 30 },
    })
  }
  if (regionBox.value) {
    echarts.init(regionBox.value).setOption({
      backgroundColor: 'transparent',
      tooltip: {},
      xAxis: { type: 'category', data: data.value.regions.map((r) => r.region), axisLabel: { color: '#9aa3af', rotate: 20 } },
      yAxis: { type: 'value', axisLabel: { color: '#9aa3af' }, splitLine: { lineStyle: { color: '#242a36' } } },
      series: [{ type: 'bar', data: data.value.regions.map((r) => r.cnt), itemStyle: { color: '#6366f1', borderRadius: [6, 6, 0, 0] } }],
      grid: { left: 40, right: 20, top: 20, bottom: 50 },
    })
  }
}
</script>

<template>
  <div>
    <div class="head">
      <h2>画像总览</h2>
      <el-select v-model="days" style="width:120px" @change="load">
        <el-option :value="1" label="近 1 天" />
        <el-option :value="7" label="近 7 天" />
        <el-option :value="30" label="近 30 天" />
      </el-select>
    </div>

    <div class="kpis" v-if="data">
      <div class="card kpi"><div class="k">PV</div><div class="v grad-title">{{ data.pv }}</div></div>
      <div class="card kpi"><div class="k">UV</div><div class="v grad-title">{{ data.uv }}</div></div>
      <div class="card kpi"><div class="k">热读文章</div><div class="v">{{ data.topArticles[0]?.title?.slice(0, 12) || '—' }}</div></div>
      <div class="card kpi"><div class="k">Top 出站平台</div><div class="v">{{ data.outboundTop[0]?.host || '—' }}</div></div>
    </div>

    <div class="charts">
      <div class="card"><div class="sec">按天 PV 趋势</div><div ref="pvBox" class="chart-box" /></div>
      <div class="card"><div class="sec">地域分布 Top10</div><div ref="regionBox" class="chart-box" /></div>
    </div>

    <div class="two-col" v-if="data">
      <div class="card">
        <div class="sec">设备分布</div>
        <div v-for="d in data.devices" :key="d.device" class="bar-row">
          <span>{{ d.device }}</span><span class="muted">{{ d.cnt }}</span>
        </div>
        <div v-if="!data.devices.length" class="muted">暂无数据</div>
      </div>
      <div class="card">
        <div class="sec">热读文章 Top5</div>
        <div v-for="a in data.topArticles" :key="a.articleId" class="bar-row">
          <span class="ellip">#{{ a.articleId }} {{ a.title }}</span><span class="muted">{{ a.cnt }}</span>
        </div>
        <div v-if="!data.topArticles.length" class="muted">暂无数据</div>
      </div>
      <div class="card">
        <div class="sec">出站平台 Top10（/r 引流追踪）</div>
        <div v-for="o in data.outboundTop" :key="o.host" class="bar-row">
          <span>{{ o.host }}</span><span class="muted">{{ o.cnt }}</span>
        </div>
        <div v-if="!data.outboundTop.length" class="muted">暂无数据</div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.head h2 { margin: 0; }
.kpis { display: grid; grid-template-columns: repeat(4, 1fr); gap: 14px; margin-bottom: 16px; }
@media (max-width: 900px) { .kpis { grid-template-columns: repeat(2, 1fr); } }
.kpi .k { color: var(--ink-faint); font-size: .85em; }
.kpi .v { font-size: 1.6em; font-weight: 700; margin-top: 4px; }
.charts { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; margin-bottom: 16px; }
@media (max-width: 900px) { .charts { grid-template-columns: 1fr; } }
.chart-box { height: 240px; }
.two-col { display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 14px; }
@media (max-width: 900px) { .two-col { grid-template-columns: 1fr; } }
.sec { font-weight: 600; margin-bottom: 10px; }
.bar-row { display: flex; justify-content: space-between; padding: 6px 0; border-top: 1px dashed var(--line); }
.ellip { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 240px; }
</style>

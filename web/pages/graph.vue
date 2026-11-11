<script setup lang="ts">
interface GraphNode { id: string; type: string; name: string; weight: number }
interface GraphLink { source: string; target: string; weight: number; kind: string }
interface GraphData { nodes: GraphNode[]; links: GraphLink[] }

// ssr:false 路由规则下 echarts 仅在客户端加载
const container = ref<HTMLElement | null>(null)

onMounted(async () => {
  const resp = await fetch('/api/graph')
  const json = await resp.json()
  if (json.code !== 0) return
  render(json.data as GraphData)
})

function render(data: GraphData) {
  import('echarts').then((echarts) => {
    if (!container.value) return
    const chart = echarts.init(container.value)
    // 注意：节点带显式 id 时，echarts 的 links 若按 name 引用会静默丢边——必须按 id 引用（服务端即 a:1/t:2 形式）
    chart.setOption({
      tooltip: {},
      series: [{
        type: 'graph',
        layout: 'force',
        roam: true,
        draggable: true,
        label: { show: true, fontSize: 11, color: '#1c2430' },
        force: { repulsion: 220, edgeLength: [60, 140], gravity: 0.15 },
        lineStyle: { color: '#9aa7b4', curveness: 0.1 },
        data: data.nodes.map((n) => ({
          id: n.id,
          name: n.name,
          symbolSize: n.type === 'tag' ? Math.min(14 + n.weight * 6, 52) : Math.min(18 + n.weight / 20, 40),
          itemStyle: n.type === 'tag'
            ? { color: '#1f5fa8' }
            : { color: '#e0863c' },
        })),
        links: data.links.map((l) => ({
          source: l.source,
          target: l.target,
          lineStyle: l.kind === 'manual'
            ? { type: 'dashed', color: '#e0863c', width: 2.5 }
            : { width: Math.min(1 + l.weight * 0.5, 4) },
        })),
      }],
    })
    window.addEventListener('resize', () => chart.resize())
  })
}
</script>

<template>
  <div>
    <h2 style="margin-top:0">知识图谱</h2>
    <p class="muted">蓝色为标签节点（大小 = 文章挂载数），橙色为文章节点；实线为标签共现（粗细 = 共现次数），橙虚线为作者手动关联。</p>
    <div ref="container" class="graph-box" />
  </div>
</template>

<style scoped>
.graph-box { width: 100%; height: 72vh; background: var(--card); border: 1px solid var(--line); border-radius: var(--radius); }
</style>

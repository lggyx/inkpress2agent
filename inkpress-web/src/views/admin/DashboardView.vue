<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { Document, ChatDotRound, View, Star } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import { stats } from '@/api/dashboard'

const statCards = ref([])
const trendRef = ref(null)
const pieRef = ref(null)
let trendChart = null
let pieChart = null

function initCharts(data) {
  if (trendRef.value) {
    trendChart = echarts.init(trendRef.value)
    trendChart.setOption({
      tooltip: { trigger: 'axis' },
      legend: { data: ['文章', '评论'] },
      grid: { left: 40, right: 20, top: 40, bottom: 30 },
      xAxis: { type: 'category', data: data.trend.map(item => item.date.slice(5)) },
      yAxis: { type: 'value', minInterval: 1 },
      series: [
        { name: '文章', type: 'line', smooth: true, data: data.trend.map(item => item.articles), itemStyle: { color: '#409eff' }, areaStyle: { opacity: 0.12 } },
        { name: '评论', type: 'line', smooth: true, data: data.trend.map(item => item.comments), itemStyle: { color: '#67c23a' }, areaStyle: { opacity: 0.12 } }
      ]
    })
  }
  if (pieRef.value) {
    pieChart = echarts.init(pieRef.value)
    pieChart.setOption({
      tooltip: { trigger: 'item', formatter: '{b}：{c} 篇（{d}%）' },
      legend: { orient: 'vertical', right: 10, top: 'center' },
      series: [
        {
          name: '分类分布',
          type: 'pie',
          radius: ['40%', '68%'],
          center: ['40%', '50%'],
          data: data.categoryDistribution,
          label: { show: false }
        }
      ]
    })
  }
}

function onResize() {
  trendChart?.resize()
  pieChart?.resize()
}

onMounted(async () => {
  const { data } = await stats()
  statCards.value = [
    { label: '文章总数', value: `${data.articleCount}（已发布 ${data.publishedCount}）`, icon: Document, color: '#409eff' },
    { label: '评论总数', value: data.commentCount, icon: ChatDotRound, color: '#67c23a' },
    { label: '总浏览量', value: data.totalViews, icon: View, color: '#e6a23c' },
    { label: '总点赞数', value: data.totalLikes, icon: Star, color: '#f56c6c' }
  ]
  initCharts(data)
  window.addEventListener('resize', onResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  trendChart?.dispose()
  pieChart?.dispose()
})
</script>

<template>
  <div class="dashboard">
    <el-row :gutter="16">
      <el-col :span="6" v-for="card in statCards" :key="card.label">
        <el-card shadow="never" class="stat-card">
          <div class="stat-inner">
            <el-icon :size="34" :color="card.color"><component :is="card.icon" /></el-icon>
            <div>
              <div class="stat-value">{{ card.value }}</div>
              <div class="stat-label">{{ card.label }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" class="chart-row">
      <el-col :span="14">
        <el-card shadow="never">
          <template #header>近 7 天内容趋势</template>
          <div ref="trendRef" class="chart trend-chart"></div>
        </el-card>
      </el-col>
      <el-col :span="10">
        <el-card shadow="never">
          <template #header>分类文章分布</template>
          <div ref="pieRef" class="chart pie-chart"></div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.stat-card :deep(.el-card__body) {
  padding: 20px;
}

.stat-inner {
  display: flex;
  align-items: center;
  gap: 16px;
}

.stat-value {
  font-size: 22px;
  font-weight: 700;
}

.stat-label {
  color: #909399;
  font-size: 13px;
}

.chart-row {
  margin-top: 16px;
}

.chart {
  height: 320px;
}
</style>

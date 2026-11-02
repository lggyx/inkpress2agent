<script setup>
import { ref, onMounted } from 'vue'
import { archives } from '@/api/article'
import { formatDateOnly } from '@/utils/format'

const groups = ref([])

onMounted(async () => {
  const { data } = await archives()
  groups.value = data
})
</script>

<template>
  <div class="archive-page">
    <div class="archive-card">
      <h1 class="page-title">文章归档</h1>
      <p class="page-sub">共 {{ groups.reduce((sum, g) => sum + g.items.length, 0) }} 篇文章</p>

      <el-empty v-if="groups.length === 0" description="暂无文章" />

      <section v-for="group in groups" :key="group.year" class="year-group">
        <h2 class="year-title">{{ group.year }} 年</h2>
        <el-timeline>
          <el-timeline-item
            v-for="item in group.items"
            :key="item.id"
            :timestamp="formatDateOnly(item.publishedAt)"
            placement="left"
          >
            <router-link :to="`/article/${item.id}`" class="archive-link">{{ item.title }}</router-link>
          </el-timeline-item>
        </el-timeline>
      </section>
    </div>
  </div>
</template>

<style scoped>
.archive-page {
  max-width: 820px;
  margin: 0 auto;
}

.archive-card {
  background: #fff;
  border-radius: 10px;
  padding: 32px 36px;
}

.page-title {
  margin: 0;
  font-size: 24px;
}

.page-sub {
  color: #909399;
  font-size: 13px;
  margin: 8px 0 24px;
}

.year-group {
  margin-bottom: 24px;
}

.year-title {
  font-size: 18px;
  margin: 0 0 12px;
}

.archive-link {
  font-size: 15px;
}

.archive-link:hover {
  color: #409eff;
}
</style>

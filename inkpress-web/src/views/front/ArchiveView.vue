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
    <div class="crumb mono">
      <span class="bracket" @click="$router.push('/')">home</span>
      <span class="crumb-sep">//</span>
      <span class="crumb-current">archive</span>
    </div>

    <header class="archive-head">
      <h1 class="archive-title">ARCHIVE<span class="cursor">_</span></h1>
      <p class="archive-sub mono">
        TOTAL {{ groups.reduce((sum, g) => sum + g.items.length, 0) }} POSTS /
        {{ groups.length }} YEARS
      </p>
    </header>

    <el-empty v-if="groups.length === 0" description="NO ARCHIVED POSTS" />

    <section v-for="group in groups" :key="group.year" class="year-group">
      <div class="year-head">
        <span class="year-label mono">{{ group.year }}</span>
        <span class="year-count mono">{{ String(group.items.length).padStart(2, '0') }} POSTS</span>
        <span class="year-line"></span>
      </div>
      <ol class="timeline">
        <li v-for="item in group.items" :key="item.id" class="timeline-item">
          <span class="timeline-date mono">{{ formatDateOnly(item.publishedAt) }}</span>
          <span class="timeline-dot"></span>
          <router-link :to="`/article/${item.id}`" class="timeline-link">{{ item.title }}</router-link>
        </li>
      </ol>
    </section>
  </div>
</template>

<style scoped>
.archive-page {
  max-width: 860px;
  margin: 0 auto;
}

.crumb {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 11px;
  letter-spacing: 0.14em;
  margin-bottom: 30px;
}
.crumb-sep {
  color: var(--acid);
}
.crumb-current {
  color: var(--text-faint);
}

/* ---------- 页头 ---------- */
.archive-head {
  border: 2px solid var(--text);
  background: var(--surface);
  padding: 40px 44px 32px;
  margin-bottom: 44px;
  position: relative;
  overflow: hidden;
}
.archive-head::after {
  content: "+";
  position: absolute;
  top: 14px;
  right: 18px;
  font-family: var(--font-mono);
  font-size: 22px;
  color: var(--acid);
}
.archive-title {
  margin: 0 0 12px;
  font-size: clamp(38px, 5vw, 56px);
  font-weight: 900;
  letter-spacing: 0.02em;
}
.cursor {
  color: var(--acid);
  animation: blink 1.1s steps(2) infinite;
}
@keyframes blink {
  50% { opacity: 0; }
}
.archive-sub {
  margin: 0;
  font-size: 11px;
  letter-spacing: 0.2em;
  color: var(--text-faint);
}

/* ---------- 年份分组 ---------- */
.year-group {
  margin-bottom: 44px;
}
.year-head {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 20px;
}
.year-label {
  font-size: 30px;
  font-weight: 900;
  color: var(--text);
  letter-spacing: 0.02em;
}
.year-count {
  font-size: 10px;
  letter-spacing: 0.16em;
  color: var(--acid);
  border: 1px solid var(--acid);
  padding: 3px 9px;
}
.year-line {
  flex: 1;
  height: 2px;
  background: var(--text);
}

/* ---------- 时间线 ---------- */
.timeline {
  margin: 0;
  padding: 0 0 0 6px;
  list-style: none;
}
.timeline-item {
  display: flex;
  align-items: baseline;
  gap: 18px;
  padding: 9px 0;
  border-bottom: 1px dashed var(--line);
}
.timeline-item:last-child {
  border-bottom: none;
}
.timeline-date {
  font-size: 11px;
  letter-spacing: 0.1em;
  color: var(--text-faint);
  flex-shrink: 0;
  width: 88px;
}
.timeline-dot {
  width: 7px;
  height: 7px;
  background: var(--acid);
  flex-shrink: 0;
  transform: translateY(-1px);
}
.timeline-item:hover .timeline-dot {
  background: var(--pink);
}
.timeline-link {
  font-size: 15px;
  color: var(--text-dim);
  line-height: 1.6;
  transition: color 0.15s;
}
.timeline-item:hover .timeline-link {
  color: var(--acid);
}

@media (max-width: 720px) {
  .archive-head {
    padding: 28px 24px 22px;
  }
  .timeline-item {
    flex-wrap: wrap;
    gap: 8px;
  }
  .timeline-date {
    width: auto;
  }
}
</style>

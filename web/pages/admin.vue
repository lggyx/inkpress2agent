<script setup lang="ts">
import { useAuthUser, hydrateAuthUser } from '~/composables/useApi'

const user = useAuthUser()
onMounted(() => { hydrateAuthUser() })

watchEffect(() => {
  if (process.client && user.value === null && !localStorage.getItem('inkpress.access')) {
    navigateTo('/login')
  }
})
</script>

<template>
  <div class="studio-shell">
    <aside class="side">
      <NuxtLink to="/" class="brand grad-title">InkPress</NuxtLink>
      <div class="side-label">管理员端</div>
      <nav>
        <NuxtLink to="/admin/ai" class="side-item">AI 配置</NuxtLink>
        <NuxtLink to="/admin/templates" class="side-item">提示词模板</NuxtLink>
        <NuxtLink to="/admin/words" class="side-item">敏感词库</NuxtLink>
        <NuxtLink to="/admin/stats" class="side-item">画像总览</NuxtLink>
        <NuxtLink to="/admin/dlq" class="side-item">死信队列</NuxtLink>
        <NuxtLink to="/admin/logs" class="side-item">日志查询</NuxtLink>
        <NuxtLink to="/admin/oss" class="side-item">对象存储</NuxtLink>
        <NuxtLink to="/me" class="side-item">个人中心</NuxtLink>
      </nav>
      <div class="side-foot muted" v-if="user">{{ user.nickname }}</div>
    </aside>
    <main class="studio-main">
      <NuxtPage />
    </main>
  </div>
</template>

<style scoped>
.studio-shell { display: flex; min-height: 100vh; }
.side {
  width: 210px;
  flex-shrink: 0;
  border-right: 1px solid var(--line);
  padding: 22px 14px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  position: sticky;
  top: 0;
  height: 100vh;
}
.brand { font-weight: 800; font-size: 1.15em; padding: 0 10px; }
.side-label { color: var(--ink-faint); font-size: .82em; padding: 14px 10px 6px; letter-spacing: 2px; }
.side-item { display: block; color: var(--ink-soft); padding: 9px 12px; border-radius: 10px; }
.side-item:hover { background: var(--card-soft); color: var(--ink); text-decoration: none; }
.side-item.router-link-active { background: var(--primary-soft); color: var(--primary); }
.side-foot { margin-top: auto; padding: 10px; }
.studio-main { flex: 1; padding: 26px 30px; min-width: 0; }
</style>

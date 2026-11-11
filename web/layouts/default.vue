<script setup lang="ts">
import { useAuthUser, hydrateAuthUser, clearAuth } from '~/composables/useApi'

const user = useAuthUser()
onMounted(() => { hydrateAuthUser() })

function logout() {
  clearAuth()
  navigateTo('/login')
}
</script>

<template>
  <div>
    <header class="site-header">
      <div class="container header-inner">
        <NuxtLink to="/" class="brand">InkPress</NuxtLink>
        <nav class="nav">
          <NuxtLink to="/">首页</NuxtLink>
          <NuxtLink to="/graph">图谱</NuxtLink>
        </nav>
        <div class="header-right">
          <template v-if="user">
            <NuxtLink v-if="user.roles.includes('BLOGGER')" to="/studio" class="muted">博主端</NuxtLink>
            <NuxtLink v-if="user.roles.includes('ADMIN')" to="/admin" class="muted">管理端</NuxtLink>
            <span class="muted">{{ user.nickname }}</span>
            <button class="linkish" @click="logout">退出</button>
          </template>
          <NuxtLink v-else to="/login">登录 / 注册</NuxtLink>
        </div>
      </div>
    </header>
    <main class="container page-body">
      <slot />
    </main>
    <footer class="site-footer">
      <div class="container muted">InkPress · 写作 · 发布 · 分发</div>
    </footer>
  </div>
</template>

<style scoped>
.site-header {
  background: var(--card);
  border-bottom: 1px solid var(--line);
  position: sticky;
  top: 0;
  z-index: 10;
}
.header-inner { display: flex; align-items: center; gap: 24px; height: 56px; }
.brand { font-weight: 700; font-size: 1.2em; color: var(--ink); }
.brand:hover { text-decoration: none; color: var(--primary); }
.nav { display: flex; gap: 16px; }
.nav a { color: var(--ink-soft); }
.nav a:hover, .nav a.router-link-active { color: var(--primary); text-decoration: none; }
.header-right { margin-left: auto; display: flex; align-items: center; gap: 14px; }
.header-right a { color: var(--ink-soft); }
.linkish { border: none; background: none; padding: 0; color: var(--ink-faint); }
.linkish:hover { color: var(--danger); }
.page-body { padding: 28px 20px 48px; min-height: calc(100vh - 140px); }
.site-footer { border-top: 1px solid var(--line); padding: 16px 0; text-align: center; }
</style>

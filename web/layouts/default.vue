<script setup lang="ts">
import { useAuthUser, hydrateAuthUser, clearAuth } from '~/composables/useApi'

const user = useAuthUser()
const { init } = useTheme()
onMounted(() => {
  hydrateAuthUser()
  init()
})

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
          <NuxtLink to="/search">检索</NuxtLink>
          <NuxtLink to="/graph">图谱</NuxtLink>
        </nav>
        <div class="header-right">
          <ThemeToggle />
          <template v-if="user">
            <NuxtLink v-if="user.roles.includes('BLOGGER')" to="/studio" class="muted">博主端</NuxtLink>
            <NuxtLink v-if="user.roles.includes('ADMIN')" to="/admin" class="muted">管理端</NuxtLink>
            <NotificationBell />
            <span class="user-chip">{{ user.nickname }}</span>
            <button class="linkish" @click="logout">退出</button>
          </template>
          <NuxtLink v-else to="/login" class="login-link">登录</NuxtLink>
        </div>
      </div>
    </header>
    <main class="container page-body">
      <slot />
    </main>
    <footer class="site-footer">
      <div class="container footer-inner">
        <span class="muted">© {{ new Date().getFullYear() }} InkPress</span>
        <span class="muted">·</span>
        <a href="/rss.xml" target="_blank" class="muted">RSS</a>
        <span class="muted">·</span>
        <NuxtLink to="/graph" class="muted">图谱</NuxtLink>
        <span class="muted">·</span>
        <NuxtLink to="/me" class="muted">个人中心</NuxtLink>
      </div>
    </footer>
  </div>
</template>

<style scoped>
.site-header {
  background: color-mix(in srgb, var(--bg) 82%, transparent);
  backdrop-filter: blur(10px);
  border-bottom: 1px solid var(--line);
  position: sticky;
  top: 0;
  z-index: 10;
}
.header-inner { display: flex; align-items: center; gap: 26px; height: 58px; }
.brand { font-weight: 800; font-size: 1.18em; color: var(--ink); }
.brand:hover { color: var(--ink); }
.nav { display: flex; gap: 18px; }
.nav a { color: var(--ink-soft); }
.nav a:hover, .nav a.router-link-active { color: var(--ink); }
.header-right { margin-left: auto; display: flex; align-items: center; gap: 16px; }
.header-right a { color: var(--ink-soft); }
.header-right a:hover { color: var(--primary); }
.user-chip {
  background: var(--card-soft);
  border: 1px solid var(--line);
  border-radius: var(--radius-pill);
  padding: 2px 12px;
  font-size: .9em;
}
.login-link {
  border: 1px solid var(--line);
  border-radius: var(--radius-pill);
  padding: 4px 18px;
  color: var(--ink);
}
.login-link:hover { border-color: var(--primary); color: var(--primary); text-decoration: none; }
.linkish { border: none; background: none; padding: 0; color: var(--ink-faint); }
.linkish:hover { color: var(--danger); }
.page-body { padding: 26px 24px 56px; min-height: calc(100vh - 150px); }
.site-footer { border-top: 1px solid var(--line); padding: 18px 0; }
.footer-inner { text-align: center; display: flex; gap: 10px; justify-content: center; align-items: center; }
.footer-inner a:hover { color: var(--primary); }
</style>

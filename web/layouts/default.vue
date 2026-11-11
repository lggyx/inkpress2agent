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
        <NuxtLink to="/" class="brand grad-title">InkPress</NuxtLink>
        <nav class="nav">
          <NuxtLink to="/">首页</NuxtLink>
          <NuxtLink to="/graph">图谱</NuxtLink>
        </nav>
        <div class="header-right">
          <template v-if="user">
            <NotificationBell />
            <NuxtLink v-if="user.roles.includes('BLOGGER')" to="/studio" class="muted">博主端</NuxtLink>
            <NuxtLink v-if="user.roles.includes('ADMIN')" to="/admin" class="muted">管理端</NuxtLink>
            <span class="user-chip">{{ user.nickname }}</span>
            <button class="linkish" @click="logout">退出</button>
          </template>
          <NuxtLink v-else to="/login" class="login-link">登录 / 注册</NuxtLink>
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
  background: rgba(11, 13, 16, .85);
  backdrop-filter: blur(10px);
  border-bottom: 1px solid var(--line);
  position: sticky;
  top: 0;
  z-index: 10;
}
.header-inner { display: flex; align-items: center; gap: 26px; height: 58px; }
.brand { font-weight: 800; font-size: 1.25em; letter-spacing: .5px; }
.brand:hover { text-decoration: none; }
.nav { display: flex; gap: 18px; }
.nav a { color: var(--ink-soft); }
.nav a:hover, .nav a.router-link-active { color: var(--ink); text-decoration: none; }
.header-right { margin-left: auto; display: flex; align-items: center; gap: 14px; }
.header-right a { color: var(--ink-soft); }
.header-right a:hover { color: var(--primary); }
.user-chip {
  background: var(--card-soft);
  border: 1px solid var(--line);
  border-radius: 999px;
  padding: 2px 12px;
  font-size: .9em;
  color: var(--ink);
}
.login-link {
  border: 1px solid var(--line);
  border-radius: 999px;
  padding: 4px 16px;
  color: var(--ink);
}
.login-link:hover { border-color: var(--primary); color: var(--primary); text-decoration: none; }
.linkish { border: none; background: none; padding: 0; color: var(--ink-faint); }
.linkish:hover { color: var(--danger); }
.page-body { padding: 30px 20px 56px; min-height: calc(100vh - 150px); }
.site-footer { border-top: 1px solid var(--line); padding: 18px 0; text-align: center; }
</style>

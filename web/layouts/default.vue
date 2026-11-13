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

// Pure 式导航状态机（对齐 astro-theme-pure Header.astro，按用户要求去掉隐藏行为）：
// not-top：滚过 20px → 胶囊收拢（margin-inline 8%、描边/底色/投影浮现）；导航始终悬浮不消失
const notTop = ref(false)
let preY = 0
let pollId = 0

function update() {
  const y = window.scrollY
  if (Math.abs(y - preY) <= 1) return
  notTop.value = y > 20
  preY = y
}

onMounted(() => {
  window.addEventListener('scroll', update, { passive: true })
  pollId = window.setInterval(update, 200) // 事件不派发的嵌入环境兜底
  update()
})
onBeforeUnmount(() => {
  window.removeEventListener('scroll', update)
  window.clearInterval(pollId)
})
</script>

<template>
  <div>
    <header class="site-header" :class="{ 'not-top': notTop }">
      <div class="container header-inner">
        <NuxtLink to="/" class="brand">InkPress</NuxtLink>
<nav class="nav">
  <NuxtLink to="/">首页</NuxtLink>
  <NuxtLink to="/categories">分类</NuxtLink>
  <NuxtLink to="/tags">标签</NuxtLink>
  <NuxtLink to="/graph">图谱</NuxtLink>
</nav>
        <div class="header-right">
          <NavSearch />
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
        <NuxtLink to="/categories" class="muted">分类</NuxtLink>
        <span class="muted">·</span>
        <NuxtLink to="/tags" class="muted">标签</NuxtLink>
        <span class="muted">·</span>
        <NuxtLink to="/graph" class="muted">图谱</NuxtLink>
        <span class="muted">·</span>
        <NuxtLink to="/me" class="muted">个人中心</NuxtLink>
      </div>
    </footer>
  </div>
</template>

<style scoped>
/* 对齐 astro-theme-pure Header.astro：sticky top-16px、滚过 20px 收拢为胶囊
   （margin-inline 8% 两侧向中间过渡 + padding 0.375rem + 描边/底色/投影浮现），
   滚过 350px 且继续下滚才温和上移隐藏（-5rem），上滑立即回显 */
.site-header {
  position: sticky;
  top: 16px;
  z-index: 50;
  margin-bottom: 3rem;
  border: 1px solid transparent;
  border-radius: 16px;
  background: transparent;
  transition:
    padding 0.3s,
    transform 0.3s,
    margin-inline 0.3s,
    border 0.15s,
    background-color 0.15s;
}
.site-header.not-top {
  border-color: var(--line);
  background: var(--card);
  padding-left: 0.375rem;
  padding-right: 0.375rem;
  box-shadow:
    rgb(255, 255, 255) 0 0 0 0,
    rgb(24 24 27 / 8%) 0 0 0 1px,
    rgb(39 39 42 / 8%) 0 10px 15px -3px,
    rgb(39 39 42 / 8%) 0 4px 6px -4px;
}
@media (min-width: 800px) {
  .site-header.not-top { margin-inline: 8%; }
}

.header-inner { display: flex; align-items: center; justify-content: space-between; gap: 20px; height: 48px; padding: 0 10px; }
.brand { z-index: 30; font-weight: 650; font-size: 1.25em; color: var(--ink); margin-inline-start: 0; transition: margin-inline 0.3s; }
.site-header.not-top .brand { margin-inline-start: 0.5rem; }
@media (min-width: 640px) {
  .site-header.not-top .brand { margin-inline-start: 0.75rem; }
}
.brand:hover { color: var(--ink); }
.nav { z-index: 30; display: flex; gap-x: 12px; gap: 12px; }
.nav a { color: var(--ink-soft); padding: 0 10px; }
.nav a:hover, .nav a.router-link-active { color: var(--primary); }
.header-right { z-index: 30; display: flex; align-items: center; gap: 16px; }
.site-header.not-top .header-right { gap: 10px; transition: gap 0.3s; }
.header-right a { color: var(--ink-soft); }
.header-right a:hover { color: var(--primary); }
.user-chip {
  background: var(--card-soft);
  border: 1px solid var(--line);
  border-radius: 10px;
  padding: 2px 12px;
  font-size: .9em;
}
.login-link {
  border: 1px solid var(--line);
  border-radius: 10px;
  padding: 4px 16px;
  color: var(--ink);
}
.login-link:hover { border-color: var(--primary); color: var(--primary); text-decoration: none; }
.linkish { border: none; background: none; padding: 0; color: var(--ink-faint); }
.linkish:hover { color: var(--danger); }
.page-body { padding: 10px 24px 56px; min-height: calc(100vh - 190px); }
.site-footer { border-top: 1px solid var(--line); padding: 18px 0; }
.footer-inner { text-align: center; display: flex; gap: 10px; justify-content: center; align-items: center; }
.footer-inner a:hover { color: var(--primary); }
</style>

<script setup lang="ts">
import { useTheme } from '~/composables/useTheme'

const { theme, init, toggle } = useTheme()
onMounted(() => { if (!document.documentElement.getAttribute('data-theme')) init() })
</script>

<template>
  <button class="theme-btn" :title="theme === 'dark' ? '切换浅色' : '切换深色'" @click="toggle">
    <svg v-if="theme === 'dark'" viewBox="0 0 24 24" width="17" height="17" fill="none"
         stroke="currentColor" stroke-width="2" stroke-linecap="round">
      <circle cx="12" cy="12" r="4" />
      <path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4" />
    </svg>
    <svg v-else viewBox="0 0 24 24" width="17" height="17" fill="none"
         stroke="currentColor" stroke-width="2" stroke-linecap="round">
      <path d="M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8z" />
    </svg>
  </button>
</template>

<style scoped>
.theme-btn {
  width: 34px;
  height: 34px;
  padding: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 999px;
  background: transparent;
  border: 1px solid var(--line);
  color: var(--ink-soft);
}
.theme-btn:hover { color: var(--primary); border-color: var(--primary); }
</style>

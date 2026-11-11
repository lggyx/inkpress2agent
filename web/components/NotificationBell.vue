<script setup lang="ts">
import { useAuthUser } from '~/composables/useApi'
import { useNotifications } from '~/composables/useNotifications'

const user = useAuthUser()
const { unread } = useNotifications()
</script>

<template>
  <NuxtLink v-if="user" to="/notifications" class="bell" title="通知中心">
    <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2">
      <path d="M18 8a6 6 0 0 0-12 0c0 7-3 9-3 9h18s-3-2-3-9" />
      <path d="M13.7 21a2 2 0 0 1-3.4 0" />
    </svg>
    <span v-if="unread > 0" class="badge">{{ unread > 99 ? '99+' : unread }}</span>
  </NuxtLink>
</template>

<style scoped>
.bell { position: relative; display: inline-flex; color: var(--ink-soft); padding: 4px; }
.bell:hover { color: var(--primary); text-decoration: none; }
.badge {
  position: absolute;
  top: -4px;
  right: -8px;
  background: var(--danger);
  color: #fff;
  border-radius: 999px;
  font-size: .68em;
  padding: 0 5px;
  line-height: 1.5;
}
</style>

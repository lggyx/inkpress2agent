<script setup lang="ts">
// 管理端外壳：与博主端共用 WorkShell（图标侧栏 + 主题切换）
definePageMeta({ layout: 'studio' })
</script>

<template>
  <WorkShell area="admin">
    <NuxtPage />
  </WorkShell>
</template>

<script setup lang="ts">
// 脱离博客默认布局：studio 使用独立 AI 工作台外壳（共享 WorkShell）
definePageMeta({ layout: 'studio' })
</script>

<template>
  <WorkShell area="studio">
    <NuxtPage />
  </WorkShell>
</template>

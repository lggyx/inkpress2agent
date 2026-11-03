<script setup>
/**
 * DecryptedText 解密文字（移植自 React Bits）
 * 文字以随机符号滚动开场，逐字符定格到目标文本；
 * 鼠标悬停可重新播放解密动画。
 */
import { ref, onMounted, onBeforeUnmount } from 'vue'

const props = defineProps({
  text: {
    type: String,
    required: true
  },
  /** 相邻字符的揭示间隔（毫秒） */
  revealInterval: {
    type: Number,
    default: 55
  },
  /** 乱码字符集 */
  scrambleChars: {
    type: String,
    default: '!<>-_\\/[]{}—=+*^?#________'
  }
})

const display = ref('')
let timer = null

function randomChar() {
  const chars = props.scrambleChars + 'ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789'
  return chars[Math.floor(Math.random() * chars.length)]
}

function run() {
  if (timer) {
    clearInterval(timer)
  }
  const target = props.text
  const total = target.length
  if (!total) {
    display.value = ''
    return
  }
  let revealed = 0
  // 初始全乱码
  display.value = Array.from({ length: total }, randomChar).join('')
  timer = setInterval(() => {
    revealed++
    if (revealed > total) {
      clearInterval(timer)
      timer = null
      display.value = target
      return
    }
    // 已揭示部分定格，其余继续随机滚动
    display.value = target.slice(0, revealed)
      + Array.from({ length: total - revealed }, randomChar).join('')
  }, props.revealInterval)
}

function replay() {
  run()
}

onMounted(run)
onBeforeUnmount(() => {
  if (timer) {
    clearInterval(timer)
  }
})

defineExpose({ replay })
</script>

<template>
  <span class="decrypted-text mono" @mouseenter="replay">{{ display }}</span>
</template>

<style scoped>
.decrypted-text {
  cursor: default;
}
</style>

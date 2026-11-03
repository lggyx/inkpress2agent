/**
 * v-tilt 自定义指令：3D 视差倾斜（移植自 React Bits TiltedCard 效果）
 * 用法：<div v-tilt="8">  // 参数为最大倾斜角度
 * 触摸设备（pointer: coarse）自动禁用。
 * 倾斜时同步更新 CSS 变量 --tilt-x / --tilt-y（0%~100%），
 * 供 ::after 光泽层做跟随鼠标的高亮。
 */
export const tilt = {
  mounted(el, binding) {
    const strength = typeof binding.value === 'number' ? binding.value : 8
    if (window.matchMedia('(pointer: coarse)').matches) {
      return
    }

    let raf = null

    function onMove(e) {
      const rect = el.getBoundingClientRect()
      const x = (e.clientX - rect.left) / rect.width
      const y = (e.clientY - rect.top) / rect.height
      const rotateY = (x - 0.5) * 2 * strength
      const rotateX = -(y - 0.5) * 2 * strength
      if (raf) {
        cancelAnimationFrame(raf)
      }
      raf = requestAnimationFrame(() => {
        el.style.transform =
          `perspective(900px) rotateX(${rotateX.toFixed(2)}deg) rotateY(${rotateY.toFixed(2)}deg) scale(1.01)`
        el.style.setProperty('--tilt-x', `${(x * 100).toFixed(1)}%`)
        el.style.setProperty('--tilt-y', `${(y * 100).toFixed(1)}%`)
      })
    }

    function onLeave() {
      if (raf) {
        cancelAnimationFrame(raf)
        raf = null
      }
      el.style.transform = 'perspective(900px) rotateX(0deg) rotateY(0deg) scale(1)'
    }

    el.style.transition = 'transform 0.14s ease-out'
    el.addEventListener('mousemove', onMove)
    el.addEventListener('mouseleave', onLeave)
    el._tiltCleanup = () => {
      if (raf) {
        cancelAnimationFrame(raf)
      }
      el.removeEventListener('mousemove', onMove)
      el.removeEventListener('mouseleave', onLeave)
    }
  },
  unmounted(el) {
    el._tiltCleanup?.()
  }
}

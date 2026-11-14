/** 读者端双主题（light/dark）：data-theme 属性驱动 CSS 变量，localStorage 持久 */
export type Theme = 'light' | 'dark'

const KEY = 'inkpress.theme'

export function useTheme() {
  const theme = useState<Theme>('ink-theme', () => 'light')

  function apply(t: Theme) {
    theme.value = t
    if (process.client) {
      document.documentElement.setAttribute('data-theme', t)
      // Element Plus 组件（studio/admin）跟随主题
      document.documentElement.classList.toggle('dark', t === 'dark')
      localStorage.setItem(KEY, t)
    }
  }

  /** 冷启动：读 localStorage，缺省跟随系统并默认纸白（野兽派白纸为定稿品牌） */
  function init() {
    if (!process.client) return
    const saved = localStorage.getItem(KEY) as Theme | null
    const t: Theme = saved ?? 'light'
    apply(t)
  }

  function toggle() {
    apply(theme.value === 'dark' ? 'light' : 'dark')
  }

  return { theme, apply, init, toggle }
}

import { accessToken } from './useApi'

export interface NotifyItem {
  id: number
  type: string
  title: string
  content: string
  refUrl: string
  createdAt: string
  readAt: string | null
}

/** 全站共享：未读数 + 最近通知（SSE 实时推送驱动） */
export function useNotifications() {
  const unread = useState<number>('notify-unread', () => 0)
  const items = useState<NotifyItem[]>('notify-items', () => [])
  const connected = useState<boolean>('notify-connected', () => false)

  async function refreshList() {
    const [count, list] = await Promise.all([
      $api<{ count: number }>('GET', '/notifications/unread-count'),
      $api<NotifyItem[]>('GET', '/notifications?page=1&size=10'),
    ])
    unread.value = count.count
    items.value = list
  }

  /** 连接 SSE 长连接（fetch-stream，EventSource 无法带 Authorization） */
  async function startStream() {
    if (!process.client || !accessToken()) return
    try {
      const resp = await fetch('/api/notifications/stream', {
        headers: { Authorization: 'Bearer ' + accessToken() },
      })
      if (!resp.ok || !resp.body) throw new Error('SSE HTTP ' + resp.status)
      connected.value = true
      const reader = resp.body.getReader()
      const decoder = new TextDecoder()
      let buf = ''
      const handle = (frame: string) => {
        let event = 'message'
        const dataLines: string[] = []
        for (const line of frame.split('\n')) {
          if (line.startsWith('event:')) event = line.slice(6).trim()
          else if (line.startsWith('data:')) dataLines.push(line.slice(5).trim())
        }
        const data = dataLines.join('\n')
        if (!data) return
        if (event === 'notification') {
          try {
            const n = JSON.parse(data) as NotifyItem
            unread.value++
            items.value = [n, ...items.value].slice(0, 10)
          } catch { /* 忽略脏帧 */ }
        }
        // connected / 心跳注释行忽略
      }
      while (true) {
        const { done, value } = await reader.read()
        if (done) break
        buf += decoder.decode(value, { stream: true })
        let idx: number
        while ((idx = buf.indexOf('\n\n')) >= 0) {
          handle(buf.slice(0, idx))
          buf = buf.slice(idx + 2)
        }
      }
    } catch { /* 断线走重连 */ }
    connected.value = false
    // 断线 5s 重连
    setTimeout(() => { if (accessToken()) startStream() }, 5000)
  }

  async function markRead(ids?: number[]) {
    await $api('POST', '/notifications/read', ids ? { ids } : {})
    await refreshList()
  }

  return { unread, items, connected, refreshList, startStream, markRead }
}

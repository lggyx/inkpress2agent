/**
 * AI 流式输出（OpenAI 兼容 SSE 代理）：fetch 流式读取，逐 token 回调。
 * EventSource 无法带 Authorization 头，一律用 fetch ReadableStream。
 * 服务端事件：token（增量文本）/ done（结束）/ error（失败，data 为文案）。
 */
export async function streamAi(
  category: string,
  content: string,
  onToken: (token: string) => void,
): Promise<string> {
  const resp = await fetch('/api/ai/chat', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream' },
    body: JSON.stringify({ category, content }),
  })
  if (!resp.ok || !resp.body) {
    let message = `HTTP ${resp.status}`
    try { message = (await resp.json()).message || message } catch { /* keep */ }
    throw new Error(message)
  }
  const reader = resp.body.getReader()
  const decoder = new TextDecoder()
  let buf = ''
  let full = ''
  const handleFrame = (frame: string) => {
    let event = 'message'
    const dataLines: string[] = []
    for (const line of frame.split('\n')) {
      if (line.startsWith('event:')) event = line.slice(6).trim()
      else if (line.startsWith('data:')) dataLines.push(line.slice(5).trim())
    }
    const data = dataLines.join('\n')
    if (!data) return
    if (event === 'done' || data === '[DONE]') return
    if (event === 'error') throw new Error(data)
    let token: string | null = null
    try {
      const json = JSON.parse(data)
      token = json?.choices?.[0]?.delta?.content ?? null
    } catch { token = data }
    if (token) full += token
    if (token) onToken(token)
  }
  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    buf += decoder.decode(value, { stream: true })
    let idx: number
    while ((idx = buf.indexOf('\n\n')) >= 0) {
      handleFrame(buf.slice(0, idx))
      buf = buf.slice(idx + 2)
    }
  }
  if (buf.trim()) handleFrame(buf)
  return full
}

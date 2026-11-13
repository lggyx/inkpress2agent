/**
 * AI 流式输出（双协议服务端代理）：fetch 流式读取，逐 token 回调。
 * EventSource 无法带 Authorization 头，一律用 fetch ReadableStream。
 * 服务端事件：token（增量文本）/ done（结束）/ error（失败，data 为文案）。
 */
import { accessToken, refreshTokens, base } from './useApi'

function makeSend(body: unknown) {
  return async () => {
    const token = accessToken()
    if (!token) throw new Error('请先登录')
    return fetch(base() + '/ai/chat', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'text/event-stream',
        Authorization: 'Bearer ' + token,
      },
      body: JSON.stringify(body),
    })
  }
}

/** 发送并读取 SSE 流：401/403 时刷新令牌重试一次；流中断时已生成部分容错返回 */
async function sendAndRead(
  send: () => Promise<Response>,
  onToken: (token: string) => void,
): Promise<string> {
  let resp = await send()
  // access token 过期时安全层回裸 403/401：刷新一次重试
  if (resp.status === 401 || resp.status === 403) {
    const newToken = await refreshTokens()
    if (!newToken) throw new Error('登录已过期，请重新登录')
    resp = await send()
  }
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
  try {
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
  } catch (e) {
    // 流传输中断（代理/连接层抖动）：已生成的部分内容照常返回，不吞结果
    if (!full) throw e
  }
  return full
}

/** 模板模式流式对话：{category, content}（润色/纠错/续写等写作指令） */
export async function streamAi(
  category: string,
  content: string,
  onToken: (token: string) => void,
): Promise<string> {
  // makeSend 返回的即是 () => Promise<Response>，直接传入（勿再包一层箭头——那样返回的是函数对象而非响应）
  return sendAndRead(makeSend({ category, content }), onToken)
}

/** 多轮直传流式对话：messages 历史由前端持有（studio 工作台对话流 / 文章页问答）。
 *  persona: assistant=工作台写作助手（缺省），reader=文章页读者问答助手 */
export async function streamChat(
  messages: { role: string; content: string }[],
  onToken: (token: string) => void,
  persona?: 'assistant' | 'reader',
): Promise<string> {
  return sendAndRead(makeSend({ messages, persona }), onToken)
}

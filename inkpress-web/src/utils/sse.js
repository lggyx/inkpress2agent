/**
 * 基于 fetch 的 SSE 流式请求（AI 润色等场景）。
 * axios 的 XHR 无法逐块读取事件流，这里手动解析 SSE 协议。
 */
export async function streamSse(url, body, { onMessage, onError, onDone, signal } = {}) {
  const token = localStorage.getItem('inkpress_token')
  let response
  try {
    response = await fetch(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {})
      },
      body: JSON.stringify(body),
      signal
    })
  } catch (e) {
    if (e.name !== 'AbortError') {
      onError && onError(new Error('网络异常，请检查后端服务'))
    }
    return
  }

  // 未配置 API Key 等情况下，后端返回 JSON 错误而非事件流
  const contentType = response.headers.get('content-type') || ''
  if (!response.ok || !contentType.includes('text/event-stream')) {
    let message = 'AI 服务调用失败'
    try {
      const data = await response.json()
      if (data.message) {
        message = data.message
      }
    } catch {
      /* 非 JSON 响应，使用默认提示 */
    }
    onError && onError(new Error(message))
    return
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''
  try {
    for (;;) {
      const { done, value } = await reader.read()
      if (done) {
        break
      }
      buffer += decoder.decode(value, { stream: true })
      const events = buffer.split('\n\n')
      buffer = events.pop()
      for (const event of events) {
        const data = event
          .split('\n')
          .filter(line => line.startsWith('data:'))
          // SSE 规范允许 "data:" 后带一个可选空格，需消费掉避免混入正文
          .map(line => (line.startsWith('data: ') ? line.slice(6) : line.slice(5)))
          .join('\n')
        if (data) {
          onMessage && onMessage(data)
        }
      }
    }
    onDone && onDone()
  } catch (e) {
    if (e.name === 'AbortError') {
      onDone && onDone()
    } else {
      onError && onError(e)
    }
  }
}

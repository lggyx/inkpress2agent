import request from '@/utils/request'

// 润色为 SSE 流式，走 utils/sse.js；此处提供摘要与标签接口
export const summary = content => request.post('/admin/ai/summary', { content })

export const suggestTags = content => request.post('/admin/ai/tags', { content })

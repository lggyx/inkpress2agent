import request from '@/utils/request'

// 润色与续写为 SSE 流式，走 utils/sse.js；此处提供摘要、标签、标题接口
export const summary = content => request.post('/admin/ai/summary', { content })

export const suggestTags = content => request.post('/admin/ai/tags', { content })

export const suggestTitles = content => request.post('/admin/ai/titles', { content })

import request from '@/utils/request'

// AI 服务配置（管理端动态配置，密钥只写不回显）
export const getConfig = () => request.get('/admin/ai-config')

export const saveConfig = data => request.put('/admin/ai-config', data)

export const resetConfig = () => request.delete('/admin/ai-config')

export const testConnection = () => request.post('/admin/ai-config/test')

// 厂商预设：选择后自动填充 base-url 与模型名
export const VENDOR_PRESETS = [
  { label: '智谱 GLM（默认，有免费档）', baseUrl: 'https://open.bigmodel.cn/api/paas/v4', model: 'glm-4-flash' },
  { label: 'DeepSeek', baseUrl: 'https://api.deepseek.com/v1', model: 'deepseek-chat' },
  { label: 'Moonshot Kimi', baseUrl: 'https://api.moonshot.cn/v1', model: 'moonshot-v1-8k' },
  { label: '通义千问', baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1', model: 'qwen-turbo' },
  { label: 'Ollama（本地）', baseUrl: 'http://localhost:11434/v1', model: 'llama3.1' }
]

/**
 * API 访问层：
 * - $pub：公开数据（SSR/ISR 直连容器网络或经 Nginx 均可），无凭据
 * - $api：客户端请求，自动带 Bearer accessToken，401 时自动 refresh 后重试一次
 */
export interface ApiResult<T> { code: number; message: string; data: T }

export interface UserInfo {
  id: number
  email: string
  nickname: string
  avatarUrl: string
  roles: string[]
}

export interface AuthData {
  accessToken: string
  refreshToken: string
  accessTokenExpiresIn: number
  user: UserInfo
}

const TOKEN_KEY = 'inkpress.access'
const REFRESH_KEY = 'inkpress.refresh'
const USER_KEY = 'inkpress.user'
const VISITOR_KEY = 'inkpress.visitor'

export function accessToken(): string {
  return process.client ? (localStorage.getItem(TOKEN_KEY) ?? '') : ''
}

export function authed(): boolean {
  return !!accessToken()
}

/** 全站共享的登录用户态：布局顶栏与登录页联动（useState 跨组件响应式） */
export function useAuthUser() {
  return useState<UserInfo | null>('ink-user', () => null)
}

/** 冷启动时从 localStorage 恢复用户态（布局 onMounted 调用） */
export function hydrateAuthUser(): void {
  if (!process.client) return
  useAuthUser().value = cachedUser()
}

export function cachedUser(): UserInfo | null {
  if (!process.client) return null
  const raw = localStorage.getItem(USER_KEY)
  if (!raw) return null
  try { return JSON.parse(raw) as UserInfo } catch { return null }
}

export function saveAuth(data: AuthData): void {
  localStorage.setItem(TOKEN_KEY, data.accessToken)
  localStorage.setItem(REFRESH_KEY, data.refreshToken)
  localStorage.setItem(USER_KEY, JSON.stringify(data.user))
  useAuthUser().value = data.user
}

export function clearAuth(): void {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(REFRESH_KEY)
  localStorage.removeItem(USER_KEY)
  useAuthUser().value = null
}

/**
 * UUID v4 降级生成：crypto.randomUUID 仅在安全上下文（HTTPS/localhost）可用，
 * 纯 HTTP 部署（局域网 IP / 域名 HTTP）下为 undefined，直接调用会让页面挂载崩溃。
 */
function fallbackUuid(): string {
  if (typeof crypto !== 'undefined' && crypto.getRandomValues) {
    const b = crypto.getRandomValues(new Uint8Array(16))
    b[6] = (b[6] & 0x0f) | 0x40
    b[8] = (b[8] & 0x3f) | 0x80
    const hex = Array.from(b, x => x.toString(16).padStart(2, '0')).join('')
    return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`
  }
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, c => {
    const r = Math.random() * 16 | 0
    return (c === 'x' ? r : (r & 0x3 | 0x8)).toString(16)
  })
}

/** 匿名访客 ID（M6 画像聚合），localStorage 持久 */
export function visitorId(): string {
  if (!process.client) return ''
  let id = localStorage.getItem(VISITOR_KEY)
  if (!id) {
    const raw = crypto.randomUUID ? crypto.randomUUID() : fallbackUuid()
    id = 'v-' + raw.replace(/-/g, '').slice(0, 20)
    localStorage.setItem(VISITOR_KEY, id)
  }
  return id
}

async function unwrap<T>(p: Promise<Response>): Promise<T> {
  const resp = await p
  let json: ApiResult<T>
  try {
    json = await resp.json()
  } catch {
    throw new Error(`HTTP ${resp.status} ${resp.statusText}`)
  }
  if (json.code !== 0) throw new Error(json.message || `错误码 ${json.code}`)
  return json.data as T
}

/** API 基址：SSR 容器内直连 server，浏览器走 Nginx 相对路径。
 *  供 fetch 直连场景（SSE 流、上传等 $api 未覆盖的调用）拼路径，禁止硬编码 '/api'——
 *  本地 npm run dev（:3000）下硬编码相对路径不经过 Nitro 代理会全部 404 */
export function base(): string {
  const cfg = useRuntimeConfig()
  return import.meta.server ? (cfg.apiBase as string) : (cfg.public.apiBase as string)
}

/** 公开 GET（SSR 安全）：服务端渲染时无凭据直接请求 */
export async function $pub<T>(path: string): Promise<T> {
  return unwrap<T>(fetch(base() + path, { headers: { Accept: 'application/json' } }))
}

/** 带凭据原始请求：返回响应对象（由调用方决定解析与刷新策略） */
async function rawAuthFetch(path: string, init: RequestInit): Promise<Response> {
  const token = accessToken()
  return fetch(base() + path, {
    ...init,
    headers: { 'Content-Type': 'application/json', Accept: 'application/json', ...(token ? { Authorization: 'Bearer ' + token } : {}) },
  })
}

async function parseJson<T>(resp: Response): Promise<T> {
  const text = await resp.text()
  try {
    return JSON.parse(text) as T
  } catch {
    throw new Error(`HTTP ${resp.status} ${text.slice(0, 60) || '(空响应)'}`)
  }
}

async function authFetch<T>(path: string, init: RequestInit): Promise<T> {
  const token = accessToken()
  return unwrap<T>(fetch(base() + path, {
    ...init,
    headers: { 'Content-Type': 'application/json', Accept: 'application/json', ...(token ? { Authorization: 'Bearer ' + token } : {}) },
  }))
}

/** 刷新令牌旋转：换新 accessToken 并落库用户态；失败（过期/被轮换作废）清空本地态返回 null。
 *  单飞保护：并发调用共享同一次刷新请求，避免同一枚刷新令牌被多处同时旋转而互踢 */
let refreshInflight: Promise<string | null> | null = null

export function refreshTokens(): Promise<string | null> {
  if (!process.client) return Promise.resolve(null)
  if (!refreshInflight) {
    refreshInflight = doRefreshTokens().finally(() => { refreshInflight = null })
  }
  return refreshInflight
}

async function doRefreshTokens(): Promise<string | null> {
  const refresh = localStorage.getItem(REFRESH_KEY)
  if (!refresh) return null
  try {
    const data = await unwrap<AuthData>(fetch(base() + '/auth/refresh', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken: refresh }),
    }))
    saveAuth(data)
    return data.accessToken
  } catch {
    clearAuth()
    return null
  }
}

/** 客户端带凭据请求；401 或 403 空响应（安全层对过期 token 的裸拒绝）自动 refresh 一次后重试 */
export async function $api<T>(method: 'GET' | 'POST' | 'PUT' | 'DELETE', path: string, body?: unknown): Promise<T> {
  const send = () => rawAuthFetch(path, { method, body: body === undefined ? undefined : JSON.stringify(body) })
  let resp = await send()
  // 401 直接刷新；403 需分辨业务权限错误（有 JSON body）与安全层裸拒绝（空 body，过期 token）
  if (resp.status === 401 || resp.status === 403) {
    const bodyText = await resp.clone().text().catch(() => '')
    const isAuthIssue = resp.status === 401
      || (resp.status === 403 && resp.headers.get('content-length') === '0')
      || (resp.status === 403 && !bodyText.trim())
    if (isAuthIssue) {
      const newToken = await refreshTokens()
      if (!newToken) throw new Error('登录已过期，请重新登录')
      resp = await send()
    }
  }
  const json = await parseJson<ApiResult<T>>(resp)
  if (json.code !== 0) throw new Error(json.message || `错误码 ${json.code}`)
  return json.data as T
}

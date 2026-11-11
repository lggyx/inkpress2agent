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

/** 匿名访客 ID（M6 画像聚合），localStorage 持久 */
export function visitorId(): string {
  if (!process.client) return ''
  let id = localStorage.getItem(VISITOR_KEY)
  if (!id) {
    id = 'v-' + crypto.randomUUID().replace(/-/g, '').slice(0, 20)
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

function base(): string {
  const cfg = useRuntimeConfig()
  // SSR 在容器内直连 server；浏览器经 Nginx 相对路径
  return import.meta.server ? (cfg.apiBase as string) : (cfg.public.apiBase as string)
}

/** 公开 GET（SSR 安全）：服务端渲染时无凭据直接请求 */
export async function $pub<T>(path: string): Promise<T> {
  return unwrap<T>(fetch(base() + path, { headers: { Accept: 'application/json' } }))
}

async function authFetch<T>(path: string, init: RequestInit): Promise<T> {
  const token = accessToken()
  return unwrap<T>(fetch(base() + path, {
    ...init,
    headers: { 'Content-Type': 'application/json', Accept: 'application/json', ...(token ? { Authorization: 'Bearer ' + token } : {}) },
  }))
}

/** 刷新令牌旋转：换新 accessToken 并落库用户态；无 refreshToken 返回 null */
export async function refreshTokens(): Promise<string | null> {
  if (!process.client) return null
  const refresh = localStorage.getItem(REFRESH_KEY)
  if (!refresh) return null
  const data = await unwrap<AuthData>(fetch(base() + '/auth/refresh', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ refreshToken: refresh }),
  }))
  saveAuth(data)
  return data.accessToken
}

/** 客户端带凭据请求；401 自动 refresh 一次后重试 */
export async function $api<T>(method: 'GET' | 'POST' | 'PUT' | 'DELETE', path: string, body?: unknown): Promise<T> {
  try {
    return await authFetch<T>(path, { method, body: body === undefined ? undefined : JSON.stringify(body) })
  } catch (e) {
    // unwrap 抛的是服务端业务文案；只有 401 语义才走刷新重试
    const msg = (e as Error).message
    if (!/401|未登录|登录已过期/.test(msg)) throw e
    const newToken = await refreshTokens()
    if (!newToken) throw e
    return authFetch<T>(path, { method, body: body === undefined ? undefined : JSON.stringify(body) })
  }
}

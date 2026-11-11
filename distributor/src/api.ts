import { loadConfig, DistributorConfig } from './config.js';

export interface PendingTask {
  recordId: number;
  platform: string;
  articleId: number;
  title: string;
  contentMd: string;
  contentHtml: string;
  coverUrl: string;
}

/** Result 包装的服务端响应 */
async function call<T>(method: string, path: string, body?: unknown, useAuth = true): Promise<T> {
  const cfg: DistributorConfig = loadConfig();
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  if (useAuth) {
    if (!cfg.deviceToken) {
      throw new Error('尚未登录：先执行 login 命令签发设备令牌');
    }
    headers['Authorization'] = 'Bearer ' + cfg.deviceToken;
  }
  const resp = await fetch(cfg.serverUrl.replace(/\/$/, '') + path, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  let json: { code?: number; message?: string; data?: T };
  try {
    json = await resp.json();
  } catch {
    throw new Error(`HTTP ${resp.status} ${resp.statusText}（非 JSON 响应）`);
  }
  if (json.code !== 0) {
    throw new Error(`服务端错误 [${json.code}] ${json.message}`);
  }
  return json.data as T;
}

export function login(email: string, password: string, deviceName: string) {
  return call<{ deviceToken: string; deviceId: number }>(
    'POST', '/api/auth/device', { email, password, deviceName }, false);
}

export function pending(): Promise<PendingTask[]> {
  return call<PendingTask[]>('GET', '/api/distribution/pending');
}

export function reportFilled(id: number, draftUrl: string, detail: unknown) {
  return call<void>('POST', '/api/distribution/filled', { id, draftUrl, detail: JSON.stringify(detail) });
}

export function report(id: number, success: boolean, publishedUrl: string, detail: unknown) {
  return call<void>('POST', '/api/distribution/report', {
    id, success, publishedUrl, detail: JSON.stringify(detail),
  });
}

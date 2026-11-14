import { loadConfig, DistributorConfig } from './config.js';
import http from 'node:http';

export interface PendingTask {
  recordId: number;
  platform: string;
  articleId: number;
  title: string;
  contentMd: string;
  contentHtml: string;
  coverUrl: string;
}

/** Result 包装的服务端响应（node:http 原生实现，规避 tsx/undici 环境下 fetch 被代理或改写导致 1002 的问题） */
function call<T>(method: string, path: string, body?: unknown, useAuth = true): Promise<T> {
  const cfg: DistributorConfig = loadConfig();
  const headers: Record<string, string | number> = { 'Content-Type': 'application/json' };
  if (useAuth) {
    if (!cfg.deviceToken) {
      throw new Error('尚未登录：先执行 login 命令签发设备令牌');
    }
    headers['Authorization'] = 'Bearer ' + cfg.deviceToken;
  }
  const payload = body === undefined ? undefined : JSON.stringify(body);
  if (payload !== undefined) headers['Content-Length'] = Buffer.byteLength(payload);

  const url = new URL(cfg.serverUrl.replace(/\/$/, '') + path);
  return new Promise((resolve, reject) => {
    const req = http.request({
      host: url.hostname,
      port: url.port || 80,
      path: url.pathname + url.search,
      method,
      headers,
    }, (res) => {
      let data = '';
      res.on('data', (c) => (data += c));
      res.on('end', () => {
        let json: { code?: number; message?: string; data?: T };
        try {
          json = JSON.parse(data);
        } catch {
          return reject(new Error(`HTTP ${res.statusCode} ${res.statusMessage}（非 JSON 响应）`));
        }
        if (json.code !== 0) {
          return reject(new Error(`服务端错误 [${json.code}] ${json.message}`));
        }
        resolve(json.data as T);
      });
    });
    req.on('error', reject);
    if (payload !== undefined) req.write(payload);
    req.end();
  });
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

/** B 档：回报平台授权状态（distributor auth status 检测后调用） */
export function reportAuthStatus(payload: {
  platform: string; authType: string; loggedIn: boolean; note?: string; deviceName?: string;
}) {
  return call<void>('POST', '/api/distribution/auth-status', payload);
}

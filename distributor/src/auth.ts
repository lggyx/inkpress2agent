/**
 * 授权状态管理（B 档核心）：三分法授权的状态持久化 + 检查 + CLI 查询
 * 状态文件：distributor/.auth/status.json（不出机，与 profile 同级）
 */
import { readFileSync, writeFileSync, existsSync, mkdirSync, statSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { chromium } from 'playwright';
import type { AuthStatus, AuthType } from './adapter-v2.js';
import { PROFILES_DIR } from './config.js';

const AUTH_DIR = join(PROFILES_DIR, '..');
const STATUS_PATH = join(AUTH_DIR, '.auth', 'status.json');

interface AuthStore {
  [platform: string]: AuthStatus;
}

function loadStore(): AuthStore {
  if (!existsSync(STATUS_PATH)) return {};
  try { return JSON.parse(readFileSync(STATUS_PATH, 'utf-8')); } catch { return {}; }
}

function saveStore(store: AuthStore): void {
  mkdirSync(dirname(STATUS_PATH), { recursive: true });
  writeFileSync(STATUS_PATH, JSON.stringify(store, null, 2));
}

export function authTypeOf(platform: string): AuthType {
  // 当前 5 平台均为 browser 型（本地 profile 持久登录）；qrcode/handoff 由新平台适配器声明
  return 'browser';
}

/** 检查单平台授权态：browser 型=探测 profile 目录活跃度 + 快速打开验证 Cookie 有效性（headless） */
export async function checkAuth(platform: string, loginUrl: string, loggedInSelector: string): Promise<AuthStatus> {
  const store = loadStore();
  const prev: AuthStatus = store[platform];
  const profileDir = join(PROFILES_DIR, platform);
  const hasProfile = existsSync(profileDir);
  let loggedIn = false;
  let note: string | undefined;

  if (hasProfile) {
    try {
      const pw = await import('playwright');
      const ctx2 = await pw.chromium.launchPersistentContext(profileDir, { headless: true });
      const page = ctx2.pages()[0] ?? (await ctx2.newPage());
      await page.goto(loginUrl, { timeout: 20000 });
      await page.waitForTimeout(2500);
      loggedIn = (await page.locator(loggedInSelector).count()) > 0;
      if (!loggedIn) note = 'Cookie 已失效：执行 inkpress auth login ' + platform + ' 重新登录';
      await ctx2.close();
    } catch (e) {
      note = '探测失败：' + (e as Error).message.slice(0, 120);
    }
  } else {
    note = '尚未登录：执行 inkpress auth login ' + platform;
  }

  const status: AuthStatus = {
    platform,
    authType: authTypeOf(platform),
    loggedIn,
    lastCheckedAt: Date.now(),
    lastLoginAt: prev?.lastLoginAt,
    profilePath: hasProfile ? profileDir : undefined,
    note,
  };
  const store2 = loadStore();
  store2[platform] = status;
  saveStore(store2);
  // 回报服务端（设备令牌鉴权；失败不阻塞本地流程）
  try {
    const { reportAuthStatus } = await import('./api.js');
    const { loadConfig } = await import('./config.js');
    const cfg = loadConfig();
    if (cfg.deviceToken) {
      await reportAuthStatus({
        platform, authType: status.authType, loggedIn: status.loggedIn,
        note: status.note ?? '', deviceName: cfg.deviceName ?? 'inkpress-distributor',
      });
    }
  } catch { /* 服务端不可达时静默，本地状态已留存 */ }
  return status;
}

/** browser 型登录：打开有头浏览器进登录页，人工操作，检测到已登录选择器即关闭并记录 */
export async function authLogin(platform: string, loginUrl: string, loggedInSelector: string, timeoutMs = 180000): Promise<AuthStatus> {
  mkdirSync(join(PROFILES_DIR, platform), { recursive: true });
  const ctx = await chromium.launchPersistentContext(join(PROFILES_DIR, platform), {
    headless: false, viewport: { width: 1280, height: 900 },
  });
  const page = ctx.pages()[0] ?? (await ctx.newPage());
  await page.goto(loginUrl, { timeout: 30000 });
  console.log(`[auth] 请在浏览器中完成 ${platform} 登录（最长等待 ${Math.round(timeoutMs / 1000)}s）…`);
  const deadline = Date.now() + timeoutMs;
  let ok = false;
  while (Date.now() < deadline) {
    await page.waitForTimeout(3000);
    try { if ((await page.locator(loggedInSelector).count()) > 0) { ok = true; break; } } catch { /* 页面跳转中 */ }
  }
  await ctx.close();
  const store = loadStore();
  const status: AuthStatus = {
    platform,
    authType: 'browser',
    loggedIn: ok,
    lastCheckedAt: Date.now(),
    lastLoginAt: ok ? Date.now() : store[platform]?.lastLoginAt,
    profilePath: join(PROFILES_DIR, platform),
    note: ok ? undefined : '登录超时未完成',
  };
  store[platform] = status;
  saveStore(store);
  return status;
}

/** 全量状态导出（CLI auth status 用） */
export function allStatuses(): AuthStatus[] {
  const store = loadStore();
  return Object.values(store).sort((a, b) => a.platform.localeCompare(b.platform));
}

/** profile 目录最近修改时间（辅助判断活跃度） */
export function profileMtime(platform: string): number | undefined {
  const p = join(PROFILES_DIR, platform);
  if (!existsSync(p)) return undefined;
  try { return statSync(p).mtimeMs; } catch { return undefined; }
}

/** 占位引用避免 TS 未使用告警（保留 API 面向未来平台） */
export const _INTERNAL = { STATUS_PATH, AUTH_DIR, dirname };
